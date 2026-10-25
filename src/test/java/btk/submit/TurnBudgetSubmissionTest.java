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
        assertEquals("3fe0370e56a6e8471f97a9d2f8c9f248c95c68fbd0e9963dd97222365ef0f641",
                FileMerger.sha256(FileMerger.merge(btk.bot.LiveTargetExperimentParams.V09_ON_CURRENT)));
        assertEquals("aca0be459116e78b97400ba0a0abfaa151aca7cc97db7804b8466e8cda788fac",
                FileMerger.sha256(FileMerger.merge("v09")));
        assertEquals("7fa98f9a04fd620ea8dc669b36aad464235e78020b2c903064c644127d4bdd22",
                FileMerger.sha256(FileMerger.merge(BASE)));
        assertEquals("82741e35c3c23b7ef0de02f9f6d27ae785ce2a1e590f3f15140492f7ed95a23f",
                FileMerger.sha256(FileMerger.merge(BASE + ",TURN_BUDGET_MS=38")));
        assertEquals("29c83f91da52414fed09d139b977af9bab7e1a553a844a76c39cb4d63352d432",
                FileMerger.sha256(FileMerger.merge(BASE + ",TURN_BUDGET_MS=40")));
        assertEquals("1e69495a45aef198188a0d3b321c9c7d8de69855cad4fb5723f15d677f96b876",
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
