package btk.submit;

import btk.bot.Bot;
import btk.bot.BotFactory;
import btk.core.Action;
import btk.core.GameState;
import btk.referee.MapGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileMergerTest {
    @TempDir Path temp;

    @Test void characterLimitIsCheckedBeforePublishing() {
        assertDoesNotThrow(() -> FileMerger.checkSourceSize("a".repeat(100_000)));
        assertThrows(IllegalStateException.class, () -> FileMerger.checkSourceSize("a".repeat(100_001)));
        assertThrows(IllegalArgumentException.class, () -> FileMerger.merge("v99"));
    }

    @Test void explicitCandidateParametersAreEmbeddedWithoutChangingDefaults() throws Exception {
        String source = FileMerger.merge("current?INTERRUPT_VULN_EVAL=1");
        FileMerger.checkSourceSize(source);
        assertTrue(source.contains("INTERRUPT_VULN_EVAL=true"));
        assertTrue(source.contains("PROFILE_SEARCH=false"));
        assertFalse(new btk.bot.Params().INTERRUPT_VULN_EVAL);
    }

    @Test void restoredV05CompilesAndMatchesFrozenDecisions() throws Exception {
        checkFrozenDecisions("v05", false);
    }

    @Test void mergedV05WarmupIsIsolatedAndUsesTheFrozenStrategy() throws Exception {
        checkFrozenDecisions("v05?ISOLATED_ROUTE_WARMUP=1", true);
    }

    private void checkFrozenDecisions(String spec, boolean warmup) throws Exception {
        String source = FileMerger.merge(spec);
        FileMerger.checkSourceSize(source);
        assertFalse(source.contains("chooseCandidateWithReplies"));
        assertFalse(source.contains("btk.bot.v05"));
        assertFalse(source.contains("class PressureBot"));
        assertFalse(source.contains("class BenchPool"));
        assertFalse(source.contains("class BenchmarkSuite"));
        Path file = temp.resolve("Player.java");
        Files.writeString(file, source);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-encoding", "UTF-8", "-d", temp.toString(), file.toString()));
        try (var loader = new URLClassLoader(new java.net.URL[]{temp.toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            Class<?> stateClass = loader.loadClass("Player$GameState");
            var ctor = stateClass.getDeclaredConstructor(int.class, int.class, int[].class, int[].class,
                    int[].class, int[].class, int[][].class);
            ctor.setAccessible(true);
            Class<?> mainClass = loader.loadClass("Player$MainBot");
            var mainCtor = mainClass.getDeclaredConstructor(); mainCtor.setAccessible(true);
            var init = mainClass.getMethod("init", stateClass, int.class); init.setAccessible(true);
            var play = mainClass.getMethod("play", stateClass, int.class, long.class); play.setAccessible(true);
            Class<?> actionClass = loader.loadClass("Player$Action");
            var format = actionClass.getMethod("format", java.util.List.class); format.setAccessible(true);
            for (int me = 0; me < 2; me++) {
                GameState s = MapGenerator.generate(1000);
                Object generatedState = ctor.newInstance(s.width, s.height, s.regionId, s.terrain,
                        s.townX, s.townY, s.desired);
                Object generatedBot = mainCtor.newInstance();
                init.invoke(generatedBot, generatedState, me);
                if (warmup) {
                    var warm = mainClass.getMethod("warmup", stateClass, int.class, long.class);
                    warm.setAccessible(true); warm.invoke(generatedBot, generatedState, me, 150L);
                    var calls = mainClass.getField("warmupCalls"); calls.setAccessible(true);
                    assertTrue(calls.getInt(generatedBot) > 0);
                }
                Bot frozen = BotFactory.create("v05"); frozen.init(s, me);
                assertEquals(Action.format(frozen.play(s.copy(), me, 60_000)),
                        format.invoke(null, play.invoke(generatedBot, generatedState, me, 60_000L)));
            }
        }
    }
}
