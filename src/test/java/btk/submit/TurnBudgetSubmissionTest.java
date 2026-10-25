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
        assertEquals("a10433473e010e47ed909492a09939f45700d6e74407340123071e2baf8544e0",
                FileMerger.sha256(FileMerger.merge(btk.bot.LiveTargetExperimentParams.V09_ON_CURRENT)));
        assertEquals("aca0be459116e78b97400ba0a0abfaa151aca7cc97db7804b8466e8cda788fac",
                FileMerger.sha256(FileMerger.merge("v09")));
        assertEquals("f8595a4a7a2b8780bdba4151f2700934ca3c50c6627a69c5c9d1679b93cf12c1",
                FileMerger.sha256(FileMerger.merge(BASE)));
        assertEquals("77fb8cb9ee5eb764786ec2ab8c7e1d9eb8e6bf47ba750599276d0d26b38f2bcd",
                FileMerger.sha256(FileMerger.merge(BASE + ",TURN_BUDGET_MS=38")));
        assertEquals("bb40cea35b79cf6e424ec5a16bd4fa259d99bbf03a46aaa6fb5135fa75a37d26",
                FileMerger.sha256(FileMerger.merge(BASE + ",TURN_BUDGET_MS=40")));
        assertEquals("572aa79b4742736c25a2873886e0c405646582862e74a21554b95725357e0e72",
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
