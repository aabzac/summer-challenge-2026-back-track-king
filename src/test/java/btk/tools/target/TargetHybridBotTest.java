package btk.tools.target;

import btk.bot.BotFactory;
import btk.bot.Params;
import btk.bot.Timer;
import btk.bot.current.RolloutBot;
import btk.bot.neural.NeuralV3Features;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Protocol;
import btk.core.Rules;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Small synthetic states; no saved-position panel, full games or strength measurement. */
class TargetHybridBotTest {
    private static final long AMPLE = 60_000;
    private static final TargetProposalFeatures.Deadline NEVER = () -> false;

    private static GameState board(int me) {
        GameState s = new GameState(5, 2, new int[]{0,1,2,3,4,5,6,7,8,9}, new int[10],
                new int[]{0,4}, new int[2], new int[][]{{1},{}});
        s.owner[1] = s.owner[2] = me; s.turn = 10;
        Rules.updateConnections(s, new Pathing(s.size)); return s;
    }
    private static GameState openingBoard() {
        // ProtectedOpening only claims free route cells in a town-containing region next to that
        // town. The ordinary fixture gives every cell its own region, so even turn0 has no such cell.
        GameState s = new GameState(5, 2, new int[]{0,0,2,4,4,5,6,7,8,9}, new int[10],
                new int[]{0,4}, new int[2], new int[][]{{1},{}});
        Rules.updateConnections(s, new Pathing(s.size)); return s;
    }
    private static Params params(boolean enabled) {
        Params p = new Params(); p.USE_NEURAL_TARGET_PROPOSAL = enabled; return p;
    }
    private static Object field(Object object, String name) throws Exception {
        Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object);
    }
    private static void set(Object object, String name, Object value) throws Exception {
        Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); f.set(object, value);
    }
    private static long deadline(TargetHybridBot b) throws Exception { return (long) field(field(b.baseline, "timer"), "deadlineNs"); }
    private static int target(GameState s, List<Action> actions) {
        int result = -1;
        for (Action a : actions) if (a.type == Action.Type.DISRUPT) result = a.disruptRegion(s);
        return result;
    }
    private static int[] cells(GameState s, List<Action> actions) {
        return actions.stream().filter(a -> a.type == Action.Type.PLACE).mapToInt(a -> s.cell(a.x, a.y)).toArray();
    }
    private static void unchanged(GameState before, GameState after) {
        assertEquals(Protocol.initBlock(before, 0), Protocol.initBlock(after, 0));
        assertEquals(Protocol.turnBlock(before, 0), Protocol.turnBlock(after, 0));
        assertEquals(Protocol.turnBlock(before, 1), Protocol.turnBlock(after, 1));
        assertArrayEquals(before.regionInked, after.regionInked); assertArrayEquals(before.regionInstability, after.regionInstability);
        assertArrayEquals(before.pairPathLen, after.pairPathLen);
        for (int i = 0; i < before.pairCount; i++) assertArrayEquals(before.pairPath[i], after.pairPath[i]);
    }

    @Test void disabledWrapperMatchesFrozenV09AndEnabledKeepsItsExactEmittedConstructionBothSeats() throws Exception {
        for (int me = 0; me < 2; me++) for (int turn : new int[]{0,10}) {
            GameState s = turn == 0 ? openingBoard() : board(me); s.turn = turn; GameState before = s.copy();
            var frozen = BotFactory.create("v09"); frozen.init(s, me);
            List<Action> expected = frozen.play(s, me, AMPLE);
            TargetHybridBot off = new TargetHybridBot(params(false)); off.init(s, me);
            assertEquals(Action.format(expected), Action.format(off.play(s, me, AMPLE)));
            TargetHybridBot on = new TargetHybridBot(params(true)); on.init(s, me);
            List<Action> actual = on.play(s, me, AMPLE);
            assertArrayEquals(cells(s, expected), cells(s, actual));
            assertEquals(target(s, actual), on.lastEmittedTarget);
            assertEquals(0.1, ((Params) field(on.baseline, "p")).NOISE);
            Timer timer = (Timer) field(on.baseline, "timer");
            assertEquals(AMPLE * 1_000_000L, (long) field(timer, "deadlineNs") - (long) field(timer, "startNs"),
                    "the baseline receives its original allowance and its deadline is restored");
            if (turn == 0) {
                assertTrue(on.baseline.openingEligible > 0); assertTrue(on.baseline.selectedOpening);
                assertEquals(0, on.proposalAttempts);
            }
            assertEquals(target(s, actual), field(on.proposer.features, "myLastTarget"));
            assertEquals(1, on.diagnostics().get("turns")); unchanged(before, s);
        }
    }

    private static void pool(TargetHybridBot b, int me, int[]... paths) throws Exception {
        ((int[]) field(b.baseline, "counts"))[me] = paths.length;
        int[][][] routes = (int[][][]) field(b.baseline, "routes"); int[][] lengths = (int[][]) field(b.baseline, "lengths");
        for (int i = 0; i < paths.length; i++) {
            System.arraycopy(paths[i], 0, routes[me][i], 0, paths[i].length); lengths[me][i] = paths[i].length;
        }
    }
    private static TargetHybridBot prepared(GameState s, int me, LongSupplier clock) throws Exception {
        TargetHybridBot b = new TargetHybridBot(params(true), clock); b.init(s, me);
        pool(b, me, new int[]{0,1,2,3,4}, new int[]{0,5,6,7,8,9,4});
        pool(b, 1 - me, new int[]{0,1,2,3,4}, new int[]{0,5,6,7,8,9,4});
        int root = ((int[]) field(b.baseline, "counts"))[me];
        int[][] roots = (int[][]) field(b.baseline, "roots");
        System.arraycopy(new int[]{9,8,7}, 0, roots[root], 0, 3);
        ((int[]) field(b.baseline, "rootCounts"))[root] = 2;
        b.baseline.selectedRoute = 1; b.baseline.selectedTarget = -1; b.baseline.selectedValue = 123;
        b.baseline.simulatedTurns = 17; b.baseline.completedComparisons = 8;
        ((Timer) field(b.baseline, "timer")).start(AMPLE); b.lastDeadlineNanos = deadline(b);
        return b;
    }

    /** Independent existing offline adapter, including its own inject/restore/aggregation code. */
    private static double[] offline(TargetHybridBot b, GameState s, int me, int[] cells, int target) throws Exception {
        Class<?> planClass = Class.forName("btk.tools.NeuralProposalStudy$Plan");
        var ctor = planClass.getDeclaredConstructor(int[].class, int.class, String.class); ctor.setAccessible(true);
        Object plan = ctor.newInstance(cells, target, "synthetic");
        var evaluate = Class.forName("btk.tools.NeuralProposalStudy").getDeclaredMethod("evaluate",
                RolloutBot.class, GameState.class, int.class, planClass, int.class, int.class, Params.class);
        evaluate.setAccessible(true);
        int[] counts = (int[]) field(b.baseline, "counts"); int replies = Math.max(1, Math.min(2, counts[1 - me]));
        Object score = evaluate.invoke(null, b.baseline, s, me, plan, counts[me], replies, params(true));
        var value = score.getClass().getDeclaredMethod("value"); value.setAccessible(true);
        var replyValues = score.getClass().getDeclaredMethod("replyValues"); replyValues.setAccessible(true);
        double[] result = new double[replies + 1]; result[0] = (double) value.invoke(score);
        System.arraycopy((double[]) replyValues.invoke(score), 0, result, 1, replies); return result;
    }
    private static void restored(TargetHybridBot b, String roots, int[] rootCounts, long deadline) throws Exception {
        assertEquals(roots, Arrays.deepToString((int[][]) field(b.baseline, "roots")));
        assertArrayEquals(rootCounts, (int[]) field(b.baseline, "rootCounts")); assertEquals(deadline, deadline(b));
        assertEquals(17, b.baseline.simulatedTurns); assertEquals(8, b.baseline.completedComparisons);
        assertEquals(123, b.baseline.selectedValue); assertEquals(1, b.baseline.selectedRoute); assertEquals(-1, b.baseline.selectedTarget);
    }

    @Test void fullFilledPairMatchesOfflineCommonScoringAndRestoresBorrowedWaitRootBothSeats() throws Exception {
        for (int me = 0; me < 2; me++) {
            GameState s = board(me), before = s.copy(); TargetHybridBot b = prepared(s, me, System::nanoTime);
            String roots = Arrays.deepToString((int[][]) field(b.baseline, "roots"));
            int[] rootCounts = ((int[]) field(b.baseline, "rootCounts")).clone(); long deadline = deadline(b);
            int[] full = {3,6,5};
            double[] e = offline(b, s, me, full, -1), d = offline(b, s, me, full, 6);
            assertTrue(b.compareTargets(s, me, full, full.length, -1, 6));
            assertEquals(2, b.lastEReplies); assertEquals(2, b.lastDReplies);
            assertEquals(e[0], b.lastEScore); assertEquals(d[0], b.lastDScore);
            assertArrayEquals(Arrays.copyOfRange(e, 1, 3), b.lastEReplyValues);
            assertArrayEquals(Arrays.copyOfRange(d, 1, 3), b.lastDReplyValues);
            restored(b, roots, rootCounts, deadline); unchanged(before, s);
        }
    }

    private static final class CutClock implements LongSupplier {
        TargetHybridBot bot;
        int phase, afterReplies;
        public long getAsLong() {
            boolean enough = bot != null && (phase != TargetHybridBot.EVALUATE_D || bot.lastDReplies >= afterReplies);
            return bot != null && bot.lastPhase == phase && enough ? Long.MAX_VALUE : System.nanoTime();
        }
    }
    @Test void cutoffBeforeEAfterEAndPartwayThroughDDiscardsPairAndRestoresEverything() throws Exception {
        for (int me = 0; me < 2; me++) for (int cut : new int[]{0,1,2}) {
            GameState s = board(me), before = s.copy(); CutClock clock = new CutClock();
            TargetHybridBot b = prepared(s, me, clock); clock.bot = b;
            clock.phase = cut == 0 ? TargetHybridBot.EVALUATE_E : TargetHybridBot.EVALUATE_D;
            clock.afterReplies = cut == 2 ? 1 : 0;
            String roots = Arrays.deepToString((int[][]) field(b.baseline, "roots"));
            int[] rootCounts = ((int[]) field(b.baseline, "rootCounts")).clone(); long deadline = deadline(b);
            assertFalse(b.compareTargets(s, me, new int[]{3,6,5}, 3, -1, 6));
            assertFalse(b.lastPairComplete); assertTrue(b.lastComparisonCutoff);
            assertEquals(cut == 0 ? 0 : 2, b.lastEReplies); assertEquals(cut == 2 ? 1 : 0, b.lastDReplies);
            assertEquals(1, b.comparisonCutoffs); assertEquals(0, b.pairsCompleted);
            List<Action> emitted = List.of(Action.place(3,0), Action.place(1,1), Action.place(0,1));
            assertSame(emitted, b.chooseComparedTarget(emitted, 6));
            restored(b, roots, rootCounts, deadline); unchanged(before, s);
        }
    }

    @Test void nonfiniteAndThrownRolloutsRestoreStorageAndDoNotPublishAnyPair() throws Exception {
        GameState s = board(0); TargetHybridBot b = prepared(s, 0, System::nanoTime);
        String roots = Arrays.deepToString((int[][]) field(b.baseline, "roots"));
        int[] rootCounts = ((int[]) field(b.baseline, "rootCounts")).clone(); long deadline = deadline(b);
        var nan = MethodHandles.dropArguments(MethodHandles.constant(double.class, Double.NaN), 0,
                GameState.class, int.class, int.class, int.class, int.class);
        set(b, "rollout", nan);
        assertFalse(b.compareTargets(s, 0, new int[]{3,6,5}, 3, -1, 6));
        assertTrue(b.lastInvalidComparison); assertEquals(1, b.nonfiniteComparisons); assertEquals(0, b.pairsCompleted);
        restored(b, roots, rootCounts, deadline);
        var fail = MethodHandles.throwException(double.class, IllegalStateException.class).bindTo(new IllegalStateException("synthetic"));
        fail = MethodHandles.dropArguments(fail, 0, GameState.class, int.class, int.class, int.class, int.class);
        set(b, "rollout", fail);
        assertThrows(IllegalStateException.class, () -> b.compareTargets(s, 0, new int[]{3}, 1, -1, 6));
        assertFalse(b.lastPairComplete); restored(b, roots, rootCounts, deadline);
    }

    @Test void zeroOwnPortfolioUsesWaitRootZeroAndEqualTargetsRemainTied() throws Exception {
        for (int me = 0; me < 2; me++) {
            GameState s = board(me); TargetHybridBot b = prepared(s, me, System::nanoTime);
            pool(b, me); pool(b, 1 - me);
            double[] expected = offline(b, s, me, new int[]{3,6,5}, -1);
            assertTrue(b.compareTargets(s, me, new int[]{3,6,5}, 3, -1, -1));
            assertEquals(1, b.lastReplyCount); assertEquals(expected[0], b.lastEScore);
            assertEquals(b.lastEScore, b.lastDScore);
            List<Action> emitted = List.of(Action.place(3,0), Action.place(1,1), Action.place(0,1));
            assertSame(emitted, b.chooseComparedTarget(emitted, -1)); assertFalse(b.lastAccepted);
        }
    }

    @Test void strictImprovementOnlyAndFinalCutoffPreserveOriginalListWhileWaitIsValidAlternative() throws Exception {
        GameState s = board(0); CutClock clock = new CutClock(); TargetHybridBot b = prepared(s, 0, clock); clock.bot = b;
        List<Action> emitted = List.of(Action.place(3,0), Action.place(1,1), Action.place(0,1), Action.disrupt(6));
        b.lastPairComplete = true; b.lastEScore = 1; b.lastDScore = 1 + TargetHybridBot.EPS / 2;
        assertSame(emitted, b.chooseComparedTarget(emitted, -1)); assertFalse(b.lastAccepted);
        b.lastDScore = 2; clock.phase = TargetHybridBot.COMMIT;
        assertSame(emitted, b.chooseComparedTarget(emitted, -1)); assertFalse(b.lastAccepted);
        clock.phase = 0;
        List<Action> result = b.chooseComparedTarget(emitted, -1);
        assertTrue(b.lastAccepted); assertEquals(-1, target(s, result)); assertArrayEquals(new int[]{3,6,5}, cells(s, result));
        for (int i = 0; i < 3; i++) assertSame(emitted.get(i), result.get(i), "reuse exact PLACE actions; never refill");
        assertTrue(TargetHybridBot.validTarget(s, -1)); assertFalse(TargetHybridBot.validTarget(s, -2));
        assertFalse(TargetHybridBot.validTarget(s, 0)); assertFalse(TargetHybridBot.validTarget(s, s.regionCount));
        Rules.ink(s, 6); assertFalse(TargetHybridBot.validTarget(s, 6));
        assertEquals(Action.Type.WAIT, TargetHybridBot.retarget(List.of(Action.disrupt(7)), -1).get(0).type);
    }

    @Test void featureCutoffAndBudgetZeroObserveEveryTurnAndRecordActualFallbackHistory() throws Exception {
        for (int me = 0; me < 2; me++) {
            GameState s = board(me); CutClock clock = new CutClock();
            TargetHybridBot b = new TargetHybridBot(params(true), clock); b.init(s, me); clock.bot = b;
            NeuralV3Features reference = new NeuralV3Features(s); reference.observe(s, me);
            clock.phase = TargetHybridBot.FEATURES;
            List<Action> emitted = b.play(s, me, AMPLE); int actual = target(s, emitted);
            assertTrue(b.lastEligible); assertEquals(1, b.proposalAttempts); assertEquals(1, b.featureCutoffs);
            assertEquals(TargetProposer.ABORTED, b.lastProposedTarget); assertFalse(b.lastAccepted);
            assertEquals(actual, field(b.proposer.features, "myLastTarget")); reference.recordTarget(actual);
            if (me == 0) Rules.resolve(s, emitted, List.of(Action.waitAction()), new Pathing(s.size));
            else Rules.resolve(s, List.of(Action.waitAction()), emitted, new Pathing(s.size));
            reference.observe(s, me); clock.phase = 0;
            List<Action> fallback = b.play(s, me, 0);
            assertEquals("WAIT", Action.format(fallback)); assertEquals(-1, b.lastEmittedTarget); reference.recordTarget(-1);
            assertEquals(2, b.turns); assertEquals(1, b.proposalAttempts);
            assertTrue(reference.prepare(s, me, true, null)); assertTrue(b.proposer.features.prepare(s, me, NEVER));
            int[] ids = new int[s.regionCount], expectedIds = new int[s.regionCount];
            float[][] rows = new float[s.regionCount][29], expectedRows = new float[s.regionCount][29];
            int n = reference.sabotageCandidates(s, me, expectedIds, expectedRows, null);
            assertEquals(n, b.proposer.features.candidates(s, me, ids, rows, NEVER));
            for (int i = 0; i < n; i++) { assertEquals(expectedIds[i], ids[i]); assertArrayEquals(expectedRows[i], rows[i]); }
            b.init(board(me), me);
            assertEquals(0, b.diagnostics().get("turns")); assertEquals(0, b.diagnostics().get("remainingSamples"));
            assertEquals(0, b.diagnostics().get("remainingNanosMin"));
        }
    }

    @Test void rejectsConflictingExperimentsAndKeepsFlagDefaultOff() {
        assertFalse(new Params().USE_NEURAL_TARGET_PROPOSAL);
        for (String query : new String[]{"USE_DISRUPT=0", "USE_ROLLOUT_ROOT_FILL=1", "USE_ROLLOUT_ALL_CELL_RISK=1",
                "USE_ROLLOUT_LIVE_TARGET=1", "USE_ROLLOUT_ROUTE_DEPENDENCIES=1", "USE_SPATIAL_V8_SEARCH=1"}) {
            assertThrows(IllegalArgumentException.class, () -> new TargetHybridBot(new Params(query)));
        }
        assertEquals(0.1, params(true).NOISE);
    }
}
