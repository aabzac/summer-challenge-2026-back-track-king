package btk.submit;

import btk.bot.BotFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import javax.tools.ToolProvider;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class TurnBudgetSubmissionTest {
    private static final String BASE = "current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",NOISE=0.1,USE_BATCHED_REFRESH=true";
    @TempDir Path temp;

    @Test void defaultAndFrozenArtifactsRemainByteIdentical() throws Exception {
        assertEquals("bfe4dd9b3e00fbee6c386411c486621cfb2b0f72b78b2e479a82659b0b3c1e78",
                FileMerger.sha256(FileMerger.merge(btk.bot.LiveTargetExperimentParams.V09_ON_CURRENT)));
        assertEquals("aca0be459116e78b97400ba0a0abfaa151aca7cc97db7804b8466e8cda788fac",
                FileMerger.sha256(FileMerger.merge("v09")));
        assertEquals("719b536606924ad210f087372c57bdad26856475e22b82f0a0cebbb97e4202a3",
                FileMerger.sha256(FileMerger.merge(BASE)));
        assertEquals("035525809026583d912e22143377778473736b73e6b5da8d190ff335986aeda9",
                FileMerger.sha256(FileMerger.merge(BASE + ",TURN_BUDGET_MS=38")));
        assertEquals("3e50e65d5265504eaee86cda87ab05d969c0e67e9090219152c9a91f1a0e7fdd",
                FileMerger.sha256(FileMerger.merge(BASE + ",TURN_BUDGET_MS=40")));
        assertEquals("c204b6c7267b3973cf8bb0ec252950f6bce98ab58026f32b523fe84b2972d9d8",
                FileMerger.sha256(FileMerger.merge(BASE + ",TURN_BUDGET_MS=40,USE_INCREMENTAL_INK_REFRESH=true")));
        for (String spec : new String[]{btk.bot.LiveTargetExperimentParams.V09_ON_CURRENT, "v09", BASE}) {
            String source = FileMerger.merge(spec);
            assertFalse(source.contains("TURN_BUDGET_MS"));
            assertFalse(source.contains("turnBudgetMs()"));
            assertEquals(source, FileMerger.merge(spec + (spec.contains("?") ? "," : "?") + "TURN_BUDGET_MS=36"));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {38, 40, 45})
    void enabledExportCompilesWithDifferentSourceAndConfigurationIdentities(int budgetMs) throws Exception {
        String extra = budgetMs == 45 ? ",USE_INCREMENTAL_INK_REFRESH=true" : "";
        String candidate = BASE + ",TURN_BUDGET_MS=" + budgetMs + extra;
        String otherOverride = BASE + ",TURN_BUDGET_MS=" + (budgetMs == 38 ? 40 : 38) + extra;
        String source = FileMerger.merge(candidate), control = FileMerger.merge(BASE + extra);
        FileMerger.checkSourceSize(source);
        var manifest = FileMerger.manifest(candidate, source);
        assertEquals(String.valueOf(budgetMs), BotFactory.configuration(candidate).get("TURN_BUDGET_MS"));
        assertNotEquals(FileMerger.configSha256(BASE), manifest.get("configSha256"));
        assertNotEquals(FileMerger.configSha256(otherOverride), manifest.get("configSha256"));
        assertNotEquals(FileMerger.sha256(control), manifest.get("sha256"));
        assertThrows(IllegalArgumentException.class, () -> FileMerger.manifest(BASE, source));
        assertThrows(IllegalArgumentException.class, () -> FileMerger.manifest(otherOverride, source));
        assertFalse(source.contains("btk.runner"));
        assertFalse(source.contains("btk.referee"));
        assertFalse(source.contains("btk.tools"));
        Path file = temp.resolve("Player.java"), classes = temp.resolve("classes");
        Files.writeString(file, source); Files.createDirectories(classes);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-encoding", "UTF-8", "--release", "17", "-g:none", "-d", classes.toString(), file.toString()));
        try (var loader = new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()}, null)) {
            Class<?> main = loader.loadClass("Player$MainBot");
            var constructor = main.getDeclaredConstructor(); constructor.setAccessible(true);
            var method = main.getDeclaredMethod("turnBudgetMs"); method.setAccessible(true);
            Object instance = constructor.newInstance();
            assertEquals((long) budgetMs, method.invoke(instance));
            if (budgetMs == 45) {
                var field = main.getDeclaredField("params"); field.setAccessible(true);
                Object params = field.get(instance);
                var ink = params.getClass().getDeclaredField("USE_INCREMENTAL_INK_REFRESH"); ink.setAccessible(true);
                assertTrue(ink.getBoolean(params), "the requested combined ink45 recipe is compiled");
                assertFalse(source.contains("deadlinePending"));
                assertFalse(source.contains("USE_FAST_CHEAPEST_PATH"));
            }
            Class<?> runner = loader.loadClass("Player$CGRunner");
            for (var entry : java.util.Map.of("TURN_MS", 50L, "MARGIN_MS", 14L,
                    "FIRST_TURN_MS", 1000L, "WARMUP_MS", 500L).entrySet()) {
                var field = runner.getDeclaredField(entry.getKey()); field.setAccessible(true);
                assertEquals(entry.getValue().longValue(), field.getLong(null), entry.getKey());
            }
        }
    }
}
