package btk.submit;

import btk.bot.BotFactory;
import btk.bot.Params;
import btk.bot.current.RolloutBot;
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

class IncrementalInkRefreshSubmissionTest {
    private static final String FLAG = "USE_INCREMENTAL_INK_REFRESH";
    private static final String BASE = "current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",NOISE=0.1,USE_BATCHED_REFRESH=true";
    @TempDir Path temp;

    @Test void optInChangesOnlyTheExplicitFlagAndRejectsOtherPolicies() {
        assertTrue(new Params().USE_INCREMENTAL_INK_REFRESH, "selected default since the v10 promotion");
        for (String spec : new String[]{btk.bot.LiveTargetExperimentParams.V09_ON_CURRENT, BASE, "v09", "neural-v1", "spatial-ppo-v10"}) {
            assertFalse(BotFactory.parameters(spec).USE_INCREMENTAL_INK_REFRESH);
            assertFalse(BotFactory.configuration(spec).containsKey(FLAG));
        }
        for (String base : new String[]{"current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",NOISE=0.1", BASE}) {
            String spec = base + "," + FLAG + "=true";
            assertInstanceOf(RolloutBot.class, BotFactory.create(spec));
            var expected = new TreeMap<>(BotFactory.configuration(base));
            expected.put(FLAG, "true");
            assertEquals(expected, BotFactory.configuration(spec));
            assertEquals(36, BotFactory.parameters(spec).TURN_BUDGET_MS);
            assertFalse(BotFactory.parameters(spec).USE_ROLLOUT_MISSED_DEADLINE);
        }
        for (String spec : new String[]{
                "v09?" + FLAG + "=true", "v08?" + FLAG + "=true",
                "main?" + FLAG + "=true", "neural-v1?" + FLAG + "=true",
                "spatial-ppo-v10?" + FLAG + "=true", "indrill-q-v2?" + FLAG + "=true",
                "current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + "," + FLAG + "=true,USE_ROLLOUT_POLICY=false",
                "current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + "," + FLAG + "=true,USE_HEURISTIC_POLICY=true",
                "current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + "," + FLAG + "=true,USE_NEURAL_TARGET_PROPOSAL=true"}) {
            assertThrows(IllegalArgumentException.class, () -> BotFactory.create(spec), spec);
            assertThrows(IllegalArgumentException.class, () -> FileMerger.merge(spec), spec);
        }
    }

    @Test void disabledFlagPreservesPriorSourceBytesAndOmitsItsHelpers() throws Exception {
        assertEquals("9ab8ebac55e185d829f4603f2605dbf531181d1d5e8cae49a1f2e1ec3999c792",
                FileMerger.sha256(FileMerger.merge(btk.bot.LiveTargetExperimentParams.V09_ON_CURRENT)));
        assertEquals("aca0be459116e78b97400ba0a0abfaa151aca7cc97db7804b8466e8cda788fac",
                FileMerger.sha256(FileMerger.merge("v09")));
        assertEquals("d3b3c7cdb3acb2cb99ed5070b47e15d5e01eb4b28ada9cc8793a51ac434cac21",
                FileMerger.sha256(FileMerger.merge(BASE)));
        for (String spec : new String[]{btk.bot.LiveTargetExperimentParams.V09_ON_CURRENT, BASE, "v09", "neural-v1", "spatial-ppo-v10"}) {
            String source = FileMerger.merge(spec);
            assertEquals(source, FileMerger.merge(spec + (spec.contains("?") ? "," : "?") + FLAG + "=false"));
            for (String absent : new String[]{FLAG, "INK_REFRESH_BEGIN", "INK_REFRESH_END",
                    "inkAndRefresh", "updateReusableConnectionsAfterInk", "pathLostConnector"})
                assertFalse(source.contains(absent), spec + ": " + absent);
        }
    }

    @Test void bothBatchModesCompileWithinLimitWithIdenticalCompactedExecutableBytes() throws Exception {
        for (boolean batched : new boolean[]{false, true}) {
            String spec = "current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",NOISE=0.1," + FLAG + "=true,USE_BATCHED_REFRESH=" + batched;
            String plain = FileMerger.mergeUncompressed(spec), compact = FileMerger.merge(spec);
            FileMerger.checkSourceSize(compact);
            assertTrue(compact.contains(FLAG + "=true"));
            assertTrue(compact.contains("inkAndRefresh"));
            assertTrue(compact.contains("updateReusableConnectionsAfterInk"));
            assertEquals(batched, compact.contains("batchedRefresh"));
            if (!batched) assertTrue(compact.contains("updateReusableConnectionsAfterInk(state, paths, false)"));
            for (String absent : new String[]{"INK_REFRESH_BEGIN", "INK_REFRESH_END", "BATCHED_REFRESH_BEGIN",
                    "BATCHED_REFRESH_END", "btk.tools", "btk.referee", "btk.runner", "deadlinePending"})
                assertFalse(compact.contains(absent), absent);
            assertDoesNotThrow(() -> FileMerger.manifest(spec, compact));
            assertNotEquals(FileMerger.configSha256(BASE), FileMerger.configSha256(spec));
            assertThrows(IllegalArgumentException.class, () -> FileMerger.manifest(BASE, compact));
            String a = FileMerger.normalizeMarker(plain), b = FileMerger.normalizeMarker(compact);
            assertEquals(JavaSourceCompactor.tokens(a), JavaSourceCompactor.tokens(b));
            String name = batched ? "batch" : "single";
            Map<String, String> expected = compile(a, name + "-plain");
            assertEquals(expected, compile(b, name + "-compact"));
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
        assertFalse(result.isEmpty());
        return result;
    }
}
