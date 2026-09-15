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
        assertThrows(IllegalArgumentException.class, () -> FileMerger.merge("exp-income-guard"));
    }

    @Test void explicitCandidateParametersAreEmbeddedWithoutChangingDefaults() throws Exception {
        String source = FileMerger.merge("current?INTERRUPT_VULN_EVAL=1");
        FileMerger.checkSourceSize(source);
        assertTrue(source.contains("INTERRUPT_VULN_EVAL=true"));
        assertTrue(source.contains("PROFILE_SEARCH=false"));
        assertFalse(new btk.bot.Params().INTERRUPT_VULN_EVAL);
    }

    @Test void jointCandidateFitsAndShipsNoArchive() throws Exception {
        String source = FileMerger.merge("current?USE_REPLY_RERANK=1,BUILD_BEAM_WIDTH=2,OPP_REPLY_WIDTH=1,USE_JOINT_ACTION=1");
        FileMerger.checkSourceSize(source);
        assertTrue(source.contains("USE_JOINT_ACTION=true"));
        assertTrue(source.contains("chooseJointAction"));
        assertFalse(source.contains("class RetainedEngine"));
        assertFalse(source.contains("experiment-engine-v1-classes"));
        assertFalse(new btk.bot.Params().USE_JOINT_ACTION);
    }

    @Test void restoredV05CompilesAndMatchesFrozenDecisions() throws Exception {
        checkFrozenDecisions("v05", false);
    }

    @Test void mergedV05WarmupIsIsolatedAndUsesTheFrozenStrategy() throws Exception {
        checkFrozenDecisions("v05?ISOLATED_ROUTE_WARMUP=1", true);
    }

    @Test void heuristicSubmissionCompilesMatchesAndWarmsOnlySelectedPolicy() throws Exception {
        String spec = "current?USE_HEURISTIC_POLICY=1,HEURISTIC_EMERGENCY_STOP=0";
        String source = FileMerger.merge(spec);
        assertTrue(source.contains("chooseTarget"));
        assertFalse(source.contains("chooseJointAction"));
        assertFalse(source.contains("evalState"));
        assertFalse(source.contains("new HeuristicBot"));
        assertFalse(FileMerger.merge("current").contains("private int chooseTarget"));
        checkFrozenDecisions(spec, true);
    }

    private void checkFrozenDecisions(String spec, boolean warmup) throws Exception {
        String source = FileMerger.merge(spec);
        FileMerger.checkSourceSize(source);
        if (!spec.contains("USE_DEFENSE_PRIORITY=1")) assertFalse(source.contains("chooseCandidateWithReplies"));
        assertFalse(source.contains("btk.bot.v05"));
        assertFalse(source.contains("class PressureBot"));
        assertFalse(source.contains("class BenchPool"));
        assertFalse(source.contains("class BenchmarkSuite"));
        assertFalse(source.contains("class BotCatalog"));
        assertFalse(source.contains("class ReplyProbe"));
        assertFalse(source.contains("class ReplyStudy"));
        assertFalse(source.contains("class JointReplyStudy"));
        assertFalse(source.contains("class JointActionCheck"));
        assertFalse(source.contains("class RetainedEngine"));
        assertFalse(source.contains("experiment-engine-v1"));
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
                if (spec.contains("USE_HEURISTIC_DETOUR=1")) {
                    int[] regions = new int[15];
                    for (int c = 0; c < regions.length; c++) regions[c] = c;
                    s = new GameState(5, 3, regions, new int[15], new int[]{0, 4}, new int[]{1, 1}, new int[][]{{1}, {0}});
                    for (int x = 1; x < 4; x++) s.owner[s.cell(x, 1)] = 1 - me;
                    btk.core.Rules.updateConnections(s, new btk.core.Pathing(s.size));
                }
                if (spec.contains("USE_DEFENSE_PRIORITY=1")) {
                    s = new GameState(5,3,new int[]{3,3,3,3,3,0,1,1,1,2,4,4,4,4,4},new int[15],
                            new int[]{0,4},new int[]{1,1},new int[][]{{1},{0}});
                    for(int x=1;x<4;x++) s.owner[s.cell(x,1)] = me;
                    btk.core.Rules.disrupt(s,1); btk.core.Rules.disrupt(s,1);
                    btk.core.Rules.updateConnections(s,new btk.core.Pathing(s.size));
                }
                Object generatedState = ctor.newInstance(s.width, s.height, s.regionId, s.terrain,
                        s.townX, s.townY, s.desired);
                var owners = stateClass.getField("owner"); owners.setAccessible(true);
                System.arraycopy(s.owner, 0, owners.get(generatedState), 0, s.size);
                for(String field : new String[]{"instability", "regionInstability"}) {
                    var f = stateClass.getField(field); f.setAccessible(true);
                    int[] values = field.equals("instability") ? s.instability : s.regionInstability;
                    System.arraycopy(values,0,f.get(generatedState),0,values.length);
                }
                Class<?> pathClass = loader.loadClass("Player$Pathing");
                var pathCtor = pathClass.getDeclaredConstructor(int.class); pathCtor.setAccessible(true);
                var update = loader.loadClass("Player$Rules").getMethod("updateConnections", stateClass, pathClass);
                update.setAccessible(true); update.invoke(null, generatedState, pathCtor.newInstance(s.size));
                Object generatedBot = mainCtor.newInstance();
                init.invoke(generatedBot, generatedState, me);
                if (warmup) {
                    var warm = mainClass.getMethod("warmup", stateClass, int.class, long.class);
                    warm.setAccessible(true); warm.invoke(generatedBot, generatedState, me, 150L);
                    var calls = mainClass.getField("warmupCalls"); calls.setAccessible(true);
                    assertTrue(calls.getInt(generatedBot) > 0);
                }
                Bot frozen = BotFactory.create(spec.startsWith("current?") ? spec : "v05"); frozen.init(s, me);
                assertEquals(Action.format(frozen.play(s.copy(), me, 60_000)),
                        format.invoke(null, play.invoke(generatedBot, generatedState, me, 60_000L)));
            }
        }
    }

    @Test void detourSubmissionMatchesOnActiveRoutesIncludingItsWarmup() throws Exception {
        checkFrozenDecisions("current?USE_HEURISTIC_POLICY=1,USE_HEURISTIC_DETOUR=1,HEURISTIC_EMERGENCY_STOP=0", true);
    }
    @Test void defenseSubmissionMatchesOnDamagedActiveRouteIncludingWarmup() throws Exception {
        checkFrozenDecisions("current?USE_DEFENSE_PRIORITY=1",true);
    }
}
