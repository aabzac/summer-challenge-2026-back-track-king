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
    private static final String BASE = "current?NOISE=0.1,USE_BATCHED_REFRESH=true";
    @TempDir Path temp;

    @Test void defaultAndFrozenArtifactsRemainByteIdentical() throws Exception {
        assertEquals("ab0954a4545f25222fce6bccf8fa11866d276266e96b4728b502c83996d847e5",
                FileMerger.sha256(FileMerger.merge("current")));
        assertEquals("aca0be459116e78b97400ba0a0abfaa151aca7cc97db7804b8466e8cda788fac",
                FileMerger.sha256(FileMerger.merge("v09")));
        assertEquals("2cb3a6a254fd8f37e0c9f07e2aa36565f4d61027ee44f508a5f64a93d547d61a",
                FileMerger.sha256(FileMerger.merge(BASE)));
        assertEquals("979cbac253699737deb01dc8ce44c99265d5362170db29f8b0e7e79edbfd1992",
                FileMerger.sha256(FileMerger.merge(BASE + ",TURN_BUDGET_MS=38")));
        for (String spec : new String[]{"current", "v09", BASE}) {
            String source = FileMerger.merge(spec);
            assertFalse(source.contains("TURN_BUDGET_MS"));
            assertFalse(source.contains("turnBudgetMs()"));
            assertEquals(source, FileMerger.merge(spec + (spec.contains("?") ? "," : "?") + "TURN_BUDGET_MS=36"));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {38, 40})
    void enabledExportCompilesWithDifferentSourceAndConfigurationIdentities(int budgetMs) throws Exception {
        String candidate = BASE + ",TURN_BUDGET_MS=" + budgetMs;
        String otherOverride = BASE + ",TURN_BUDGET_MS=" + (budgetMs == 38 ? 40 : 38);
        String source = FileMerger.merge(candidate), control = FileMerger.merge(BASE);
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
            assertEquals((long) budgetMs, method.invoke(constructor.newInstance()));
            Class<?> runner = loader.loadClass("Player$CGRunner");
            for (var entry : java.util.Map.of("TURN_MS", 50L, "MARGIN_MS", 14L,
                    "FIRST_TURN_MS", 1000L, "WARMUP_MS", 500L).entrySet()) {
                var field = runner.getDeclaredField(entry.getKey()); field.setAccessible(true);
                assertEquals(entry.getValue().longValue(), field.getLong(null), entry.getKey());
            }
        }
    }
}
