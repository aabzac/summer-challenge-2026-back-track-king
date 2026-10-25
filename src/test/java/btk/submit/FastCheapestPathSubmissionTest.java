package btk.submit;

import btk.bot.BotFactory;
import btk.bot.MainBot;
import btk.bot.Params;
import btk.bot.current.RolloutBot;
import btk.core.Pathing;
import btk.core.SearchState;
import btk.referee.MapGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.tools.ToolProvider;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import static org.junit.jupiter.api.Assertions.*;

class FastCheapestPathSubmissionTest {
    private static final String FLAG = "USE_FAST_CHEAPEST_PATH";
    private static final String BASE = "current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",NOISE=0.1,USE_BATCHED_REFRESH=true";
    private static final String GUARD = "current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",NOISE=0,USE_ROLLOUT_MISSED_DEADLINE=true";
    @TempDir Path temp;

    @Test void optInChangesOnlyItsFlagAndRejectsNonNativeOrFrozenPolicies() {
        assertTrue(new Params().USE_FAST_CHEAPEST_PATH, "selected default since the v10 promotion");
        for (String base : new String[]{btk.bot.LiveTargetExperimentParams.V09_ON_CURRENT, BASE, GUARD}) {
            assertFalse(BotFactory.configuration(base).containsKey(FLAG));
            String spec = base + (base.contains("?") ? "," : "?") + FLAG + "=true";
            assertInstanceOf(RolloutBot.class, BotFactory.create(spec));
            var expected = new TreeMap<>(BotFactory.configuration(base)); expected.put(FLAG, "true");
            assertEquals(expected, BotFactory.configuration(spec));
            assertEquals(36, BotFactory.parameters(spec).TURN_BUDGET_MS);
        }
        for (String spec : new String[]{"v09?", "v08?", "main?", "neural-v1?", "spatial-ppo-v10?",
                "indrill-q-v2?", "current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",USE_ROLLOUT_POLICY=false,",
                "current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",USE_HEURISTIC_POLICY=true,", "current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",USE_NEURAL_TARGET_PROPOSAL=true,"}) {
            String enabled = spec + FLAG + "=true";
            assertThrows(IllegalArgumentException.class, () -> BotFactory.create(enabled), enabled);
            assertThrows(IllegalArgumentException.class, () -> FileMerger.merge(enabled), enabled);
        }
        for (String incompatible : new String[]{"USE_ROLLOUT_POLICY=false", "USE_HEURISTIC_POLICY=true",
                "USE_NEURAL_TARGET_PROPOSAL=true"}) {
            Params params = new Params().apply(FLAG + "=true," + incompatible);
            assertThrows(IllegalArgumentException.class, () -> new MainBot(params), incompatible);
        }
    }

    @Test void disabledArtifactsRetainExactHistoricalBytes() throws Exception {
        Map<String, String> historical = Map.of(
                btk.bot.LiveTargetExperimentParams.V09_ON_CURRENT, "82f166f30abba1fee25f5706559946064ddff9599fb8c75fe89bc56b34bfb18a",
                "v09", "aca0be459116e78b97400ba0a0abfaa151aca7cc97db7804b8466e8cda788fac",
                BASE, "fa52a7d76df3775892aecebf25c5cb1f926906730c80e2707113ecda217002a4",
                GUARD, "2c8cc252a47c233c4b337ac19465d5321842a97679674bdd18c090b7660b48e9",
                BASE + ",USE_INCREMENTAL_INK_REFRESH=true",
                "712581648535fa01ea21c6aaf332579c6c77df61c4a64a2c1984129ed5c7aa07");
        for (var entry : historical.entrySet()) {
            String spec = entry.getKey(), source = FileMerger.merge(spec);
            assertEquals(entry.getValue(), FileMerger.sha256(source), spec);
            assertEquals(source, FileMerger.merge(spec + (spec.contains("?") ? "," : "?") + FLAG + "=false"));
            for (String absent : new String[]{FLAG, "FAST_CHEAPEST_PATH", "pathNeighbors",
                    "enableFastCheapestPath", "prepareCheapestNeighbors"}) assertFalse(source.contains(absent), absent);
        }
    }

    @Test void initializationAllocatesOnlyTheEnabledProposalTable() throws Exception {
        var proposal = RolloutBot.class.getDeclaredField("pathing"); proposal.setAccessible(true);
        var table = Pathing.class.getDeclaredField("pathNeighbors"); table.setAccessible(true);
        var scratch = SearchState.class.getDeclaredField("pathing"); scratch.setAccessible(true);
        for (boolean enabled : new boolean[]{false, true}) {
            Params params = new Params(); params.USE_FAST_CHEAPEST_PATH = enabled;
            RolloutBot bot = new RolloutBot(params); bot.init(MapGenerator.generate(381018L), 0);
            assertEquals(enabled, table.get(proposal.get(bot)) != null);
            for (String name : new String[]{"work", "completion", "targetBoard", "inkBoard"}) {
                var field = RolloutBot.class.getDeclaredField(name); field.setAccessible(true);
                assertNull(table.get(scratch.get(field.get(bot))), name);
            }
        }
    }

    @Test void mainAndGuardArtifactsFitAndCompactionKeepsEveryExecutableClass() throws Exception {
        for (String base : new String[]{BASE, GUARD}) {
            String spec = base + "," + FLAG + "=true", name = base.equals(BASE) ? "main" : "guard";
            String plain = FileMerger.mergeUncompressed(spec), compact = FileMerger.merge(spec);
            FileMerger.checkSourceSize(compact);
            assertTrue(compact.contains(FLAG + "=true"));
            assertTrue(compact.contains("enableFastCheapestPath"));
            assertTrue(compact.contains("prepareCheapestNeighbors"));
            assertTrue(compact.contains("pathNeighbors == null ?"), "safe original fallback stays available");
            for (String absent : new String[]{"FAST_CHEAPEST_PATH_BEGIN", "FAST_CHEAPEST_PATH_OLD_BEGIN",
                    "btk.tools", "btk.referee", "btk.runner"}) assertFalse(compact.contains(absent), absent);
            assertDoesNotThrow(() -> FileMerger.manifest(spec, compact));
            assertThrows(IllegalArgumentException.class, () -> FileMerger.manifest(base, compact));
            String a = FileMerger.normalizeMarker(plain), b = FileMerger.normalizeMarker(compact);
            assertEquals(JavaSourceCompactor.tokens(a), JavaSourceCompactor.tokens(b));
            assertEquals(compile(a, name + "-plain"), compile(b, name + "-compact"));
            try (var loader = new URLClassLoader(new java.net.URL[]{
                    temp.resolve(name + "-compact/classes").toUri().toURL()}, null)) {
                Class<?> main = loader.loadClass("Player$MainBot");
                var constructor = main.getDeclaredConstructor(); constructor.setAccessible(true);
                Object bot = constructor.newInstance();
                var paramsField = main.getDeclaredField("params"); paramsField.setAccessible(true);
                Object params = paramsField.get(bot);
                var flag = params.getClass().getField(FLAG); flag.setAccessible(true);
                assertTrue(flag.getBoolean(params));
                Class<?> runner = loader.loadClass("Player$CGRunner");
                for (var entry : Map.of("TURN_MS", 50L, "MARGIN_MS", 14L,
                        "FIRST_TURN_MS", 1000L, "WARMUP_MS", 500L).entrySet()) {
                    var field = runner.getDeclaredField(entry.getKey()); field.setAccessible(true);
                    assertEquals(entry.getValue().longValue(), field.getLong(null), entry.getKey());
                }
            }
        }
    }

    private Map<String, String> compile(String source, String name) throws Exception {
        Path directory = temp.resolve(name), classes = directory.resolve("classes");
        Files.createDirectories(classes);
        Path file = directory.resolve("Player.java"); Files.writeString(file, source, StandardCharsets.UTF_8);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-encoding", "UTF-8", "--release", "17", "-g:none", "-d", classes.toString(), file.toString()));
        Map<String, String> result = new TreeMap<>();
        try (var files = Files.walk(classes)) {
            for (Path generated : files.filter(Files::isRegularFile).toList())
                result.put(classes.relativize(generated).toString(), HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(generated))));
        }
        assertFalse(result.isEmpty()); return result;
    }
}
