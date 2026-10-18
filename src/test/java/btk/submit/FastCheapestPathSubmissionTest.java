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
    private static final String BASE = "current?NOISE=0.1,USE_BATCHED_REFRESH=true";
    private static final String GUARD = "current?NOISE=0,USE_ROLLOUT_MISSED_DEADLINE=true";
    @TempDir Path temp;

    @Test void optInChangesOnlyItsFlagAndRejectsNonNativeOrFrozenPolicies() {
        assertFalse(new Params().USE_FAST_CHEAPEST_PATH);
        for (String base : new String[]{"current", BASE, GUARD}) {
            assertFalse(BotFactory.configuration(base).containsKey(FLAG));
            String spec = base + (base.contains("?") ? "," : "?") + FLAG + "=true";
            assertInstanceOf(RolloutBot.class, BotFactory.create(spec));
            var expected = new TreeMap<>(BotFactory.configuration(base)); expected.put(FLAG, "true");
            assertEquals(expected, BotFactory.configuration(spec));
            assertEquals(36, BotFactory.parameters(spec).TURN_BUDGET_MS);
        }
        for (String spec : new String[]{"v09?", "v08?", "main?", "neural-v1?", "spatial-ppo-v10?",
                "indrill-q-v2?", "current?USE_ROLLOUT_POLICY=false,",
                "current?USE_HEURISTIC_POLICY=true,", "current?USE_NEURAL_TARGET_PROPOSAL=true,"}) {
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
                "current", "ab0954a4545f25222fce6bccf8fa11866d276266e96b4728b502c83996d847e5",
                "v09", "aca0be459116e78b97400ba0a0abfaa151aca7cc97db7804b8466e8cda788fac",
                BASE, "2cb3a6a254fd8f37e0c9f07e2aa36565f4d61027ee44f508a5f64a93d547d61a",
                GUARD, "3bd070b3802744778473e93cebed8148ba07a7b243bd744c8ac486076d304a2c",
                BASE + ",USE_INCREMENTAL_INK_REFRESH=true",
                "6217083e6319f69a29619f83c7de43d99876153b1dc9d043ca3376f5bccf8124");
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
            assertTrue(compact.contains("pathNeighbors==null?"), "safe original fallback stays available");
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
