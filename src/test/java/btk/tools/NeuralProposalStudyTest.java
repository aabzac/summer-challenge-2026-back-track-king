package btk.tools;

import btk.bot.BotFactory;
import btk.bot.Params;
import btk.bot.Timer;
import btk.bot.current.RolloutBot;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.core.SearchState;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static btk.tools.NeuralProposalStudy.*;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic core fixtures only. No saved panel or teacher/model inference is run by these tests. */
class NeuralProposalStudyTest {
    @TempDir Path temporary;

    private static GameState board(int me) {
        var s = new GameState(5, 2, new int[]{0,1,2,3,4,5,6,7,8,9}, new int[10],
                new int[]{0,4}, new int[2], new int[][]{{1},{}});
        s.owner[1] = s.owner[2] = me; s.turn = 10;
        Rules.updateConnections(s, new Pathing(s.size)); return s;
    }
    private static Params params(int depth) {
        return new Params(btk.bot.LiveTargetExperimentParams.V10_OFF + ",USE_PROTECTED_OPENING=0,USE_ROLLOUT_QUICK_ROUTE=0,USE_ROLLOUT_NETWORK=0,"
                + "USE_ROLLOUT_NETWORK_AFTER_BASE=0,USE_ROLLOUT_FILL=1,USE_DISRUPT=0,NOISE=0,ROLLOUT_DEPTH=" + depth);
    }
    private static void set(RolloutBot b, String name, Object value) throws Exception {
        var f = RolloutBot.class.getDeclaredField(name); f.setAccessible(true); f.set(b, value);
    }
    private static void pool(RolloutBot b, int me, int[]... paths) throws Exception {
        ((int[]) field(b, "counts"))[me] = paths.length;
        int[][][] routes = (int[][][]) field(b, "routes"); int[][] lengths = (int[][]) field(b, "lengths");
        for (int i = 0; i < paths.length; i++) {
            System.arraycopy(paths[i], 0, routes[me][i], 0, paths[i].length); lengths[me][i] = paths[i].length;
        }
    }
    private static RolloutBot prepared(GameState s, int me, Params p) throws Exception {
        var b = new RolloutBot(p); b.init(s, me); pool(b, me, new int[]{0,1,2,3,4});
        ((int[][]) field(b, "roots"))[0][0] = 3; ((int[]) field(b, "rootCounts"))[0] = 1;
        ((int[][]) field(b, "roots"))[1][0] = 9; ((int[]) field(b, "rootCounts"))[1] = 1;
        ((int[]) field(b, "rootTargets"))[0] = -1;
        set(b, "ordinaryRootSize", 2); set(b, "bestCount", 1); ((int[]) field(b, "bestCells"))[0] = 3;
        b.selectedRoute = 0; b.selectedTarget = -1; b.selectedValue = 123; b.simulatedTurns = 17;
        ((Timer) field(b, "timer")).start(1234); return b;
    }
    private static long timerValue(Timer timer, String field) throws Exception {
        var f = Timer.class.getDeclaredField(field); f.setAccessible(true); return f.getLong(timer);
    }
    @Test void coreExpansionCanonicalizesTargetAndRejectsSkippedConstruction() {
        for (int me = 0; me < 2; me++) {
            final int player = me;
            GameState s = board(me);
            Plan a = accepted(s, me, Action.format(List.of(Action.place(3,0), Action.place(1,1), Action.disruptAt(2,1))));
            Plan b = accepted(s, me, Action.format(List.of(Action.place(1,1), Action.place(3,0), Action.disrupt(7))));
            assertEquals(a.key(), b.key()); assertEquals(7, a.target());
            assertNotEquals(a.key(), legal(s, me, a.cells(), -1).key());
            assertThrows(IllegalStateException.class, () -> accepted(s, player,
                    Action.format(List.of(Action.place(3,0), Action.place(3,0)))));
            assertThrows(IllegalStateException.class, () -> accepted(s, player,
                    Action.format(List.of(Action.disrupt(0)))));
        }
    }
    @Test void coverageSeparatesFillTargetOnlyNoveltyAndSuppressedGeneratedRoots() throws Exception {
        GameState s = board(0); RolloutBot bot = prepared(s, 0, params(1));
        Timer timer = (Timer) field(bot, "timer"); long deadline = timerValue(timer, "deadlineNs");
        Plan raw = legal(s, 0, new int[]{3}, -1), full = filled(bot, s, 0, raw);
        assertArrayEquals(new int[]{3,6,5}, full.cells());
        Plan attacked = filled(bot, s, 0, legal(s, 0, new int[]{3}, 6));
        assertArrayEquals(new int[]{3,7,8}, attacked.cells());
        assertEquals(deadline, timerValue(timer, "deadlineNs")); assertEquals(-1, bot.selectedTarget);
        assertEquals(1, field(bot, "bestCount")); assertEquals(3, ((int[]) field(bot, "bestCells"))[0]);
        Score score = new Score(0, new double[]{0});
        List<Tuple> tuples = List.of(new Tuple(0, 0, raw, full, score, score, score));
        var fillCoverage = coverage(full, tuples, List.of());
        assertEquals(true, fillCoverage.get("coveredOnlyAfterFill")); assertEquals(false, fillCoverage.get("novelFullPlan"));
        Plan targetOnly = legal(s, 0, full.cells(), 7);
        var targetCoverage = coverage(targetOnly, tuples, List.of(new Suppressed(1, 1, targetOnly, targetOnly)));
        assertEquals(false, targetCoverage.get("targetOnlyNovelty")); assertEquals(false, targetCoverage.get("novelConstruction"));
        assertEquals(false, targetCoverage.get("novelFullPlan"), "a generated but suppressed action is not a new neural proposal");
        assertEquals(1, targetCoverage.get("suppressedFilledFullPlanMatches"));
        assertEquals(1, targetCoverage.get("suppressedFilledConstructionMatches"));
        assertEquals(0, targetCoverage.get("filledFullPlanMatches"), "suppressed roots are not searched candidates");
        var newTarget = coverage(legal(s, 0, full.cells(), 8), tuples, List.of(new Suppressed(1, 1, targetOnly, targetOnly)));
        assertEquals(true, newTarget.get("targetOnlyNovelty")); assertEquals(false, newTarget.get("novelConstruction"));
        assertTrue(suppressed(1, 3, new int[][]{{3,6,5},{5,3,6},{7,8}}, new int[]{3,3,2}));
        assertFalse(suppressed(1, 1, new int[][]{{3,6,5},{5,3,6}}, new int[]{3,3}), "network tail is not ordinary dedup");
    }
    @Test void identicalFullActionsHaveIdenticalCommonValuesAndRestoreBorrowedStorageBothSeats() throws Exception {
        for (int me = 0; me < 2; me++) {
            GameState s = board(me), before = s.copy(); Params p = params(2); RolloutBot bot = prepared(s, me, p);
            int[][] roots = (int[][]) field(bot, "roots"); String rootsBefore = Arrays.deepToString(roots);
            int[] countsBefore = ((int[]) field(bot, "rootCounts")).clone();
            Timer timer = (Timer) field(bot, "timer"); long start = timerValue(timer, "startNs"), deadline = timerValue(timer, "deadlineNs");
            Plan a = legal(s, me, new int[]{3,5,6}, -1), b = legal(s, me, new int[]{6,3,5}, -1);
            Score first = evaluate(bot, s, me, a, 1, 1, p), second = evaluate(bot, s, me, b, 1, 1, p);
            assertEquals(first.value(), second.value()); assertArrayEquals(first.replyValues(), second.replyValues());
            assertEquals(rootsBefore, Arrays.deepToString(roots)); assertArrayEquals(countsBefore, (int[]) field(bot, "rootCounts"));
            assertEquals(start, timerValue(timer, "startNs")); assertEquals(deadline, timerValue(timer, "deadlineNs"));
            assertEquals(17, bot.simulatedTurns); assertEquals(123, bot.selectedValue); assertEquals(0, bot.selectedRoute);
            ReplyProbe.sameState(before, s);
        }
    }
    @Test void arbitraryPlanUsesRetainedRouteZeroRatherThanItsNativeRouteContinuation() throws Exception {
        for (int me = 0; me < 2; me++) {
            GameState s = board(me); s.maxTurns = 12; Params p = params(2); RolloutBot bot = prepared(s, me, p);
            pool(bot, me, new int[]{0,1,2,3,4}, new int[]{0,5,6,7,8,9,4});
            Plan plan = legal(s, me, new int[]{5}, -1);
            Score common = evaluate(bot, s, me, plan, 2, 1, p);
            GameState commonEnd = ((SearchState) field(bot, "work")).state.copy();
            Score nativeSecond = evaluate(bot, s, me, plan, 1, 1, p);
            GameState nativeEnd = ((SearchState) field(bot, "work")).state;
            assertEquals(me, commonEnd.owner[3]); assertEquals(GameState.NO_TRACK, nativeEnd.owner[3]);
            assertTrue(common.value() > nativeSecond.value(), "different continuation can change the proxy; keep native scores separate");
        }
    }
    @Test void opponentReplyAndInjectedActionResolveSimultaneouslyThroughCore() throws Exception {
        for (int me = 0; me < 2; me++) {
            GameState s = board(me); s.maxTurns = 11; Params p = params(1); RolloutBot bot = prepared(s, me, p);
            pool(bot, 1 - me, new int[]{0,1,2,3,4});
            Plan plan = legal(s, me, new int[]{3}, -1);
            evaluate(bot, s, me, plan, 1, 1, p);
            GameState expected = s.copy();
            Rules.resolve(expected, List.of(Action.place(3,0)), List.of(Action.place(3,0)), new Pathing(s.size));
            GameState actual = ((SearchState) field(bot, "work")).state;
            assertEquals(GameState.NEUTRAL, actual.owner[3]); ReplyProbe.sameState(expected, actual);
            assertEquals(GameState.NO_TRACK, s.owner[3]);
        }
    }
    @Test void completeSyntheticPanelReconstructsFrozenBaselineAndRecognizesItsFilledAction() throws Exception {
        for (int me = 0; me < 2; me++) {
            GameState state = board(me); Params p = BotFactory.parameters("v09?NOISE=0");
            var baseline = BotFactory.create("v09?NOISE=0"); baseline.init(state.copy(), me);
            String action = Action.format(baseline.play(state, me, AMPLE_MS));
            var result = inspect(new Position(1, 0, "synthetic", "synthetic", me, state, action), p);
            assertEquals(true, result.get("frozenV09Parity")); assertEquals(true, result.get("inputAndPortfolioUnchanged"));
            assertEquals(0.0, result.get("neuralMinusEmittedBaseline")); assertEquals(false, result.get("novelModelPreferred"));
            @SuppressWarnings("unchecked") var coverage = (java.util.Map<String,Object>) result.get("coverage");
            assertEquals(false, coverage.get("novelFullPlan"));
            assertTrue((Integer) result.get("nativeCompletedComparisons") > 0);
        }
    }
    @Test void loaderFailsClosedOnChangedScheduleOrPinnedFileCorruptionBeforeBotCalls() throws Exception {
        JsonObject selection = new JsonObject(); selection.addProperty("schema", "neural-proposal-selection-v1");
        selection.addProperty("policy", "neural-v3"); selection.addProperty("buildId", BUILD); selection.addProperty("sourceSha256", SOURCE);
        selection.add("turns", ReplyStudy.JSON.toJsonTree(new int[]{0,10,20,30,40,50,60,70}));
        Path file = temporary.resolve("selection.json"); Files.writeString(file, selection.toString());
        assertTrue(assertThrows(IllegalStateException.class, () -> load(file)).getMessage().contains("turn schedule"));
        selection.add("turns", ReplyStudy.JSON.toJsonTree(TURNS)); Path source = temporary.resolve("plan.json"); Files.writeString(source, "{}");
        selection.addProperty("sourcePlanPath", source.toString()); selection.addProperty("sourcePlanSha256", "0".repeat(64));
        Files.writeString(file, selection.toString());
        assertTrue(assertThrows(IllegalStateException.class, () -> load(file)).getMessage().contains("SHA256 mismatch"));
    }
}
