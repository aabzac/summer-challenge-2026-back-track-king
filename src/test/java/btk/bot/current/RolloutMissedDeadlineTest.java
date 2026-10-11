package btk.bot.current;

import btk.bot.BotFactory;
import btk.bot.Params;
import btk.bot.Timer;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.core.SearchState;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic policy-state tests only; no development-panel outcomes or recorded histories. */
class RolloutMissedDeadlineTest {
    private static final Gson JSON = new Gson();

    private static Params parameters(boolean enabled) {
        return new Params("NOISE=0,USE_ROLLOUT_MISSED_DEADLINE=" + enabled);
    }
    private static GameState filler(int me) {
        int[] regions = {0,1,2,3,4,5,6,7,7,7,7,7,7,7,8,9,9,9,9,9,10};
        GameState s = new GameState(7, 3, regions, new int[21], new int[]{0,6,0,6},
                new int[]{0,0,2,2}, new int[][]{{1},{},{},{}});
        for (int cell = 1; cell <= 5; cell++) s.owner[cell] = me;
        s.turn = 10; Rules.updateConnections(s, new Pathing(s.size)); return s;
    }
    private static RolloutBot initialized(GameState s, int me) {
        RolloutBot b = new RolloutBot(parameters(true)); b.init(s, me); return b;
    }
    private static GameState mixed(int me, boolean roomForFiller) {
        int size = roomForFiller ? 10 : 5;
        int[] regions = new int[size]; for (int i = 0; i < size; i++) regions[i] = i;
        GameState s = new GameState(5, roomForFiller ? 2 : 1, regions, new int[size],
                new int[]{0,4}, new int[2], new int[][]{{1},{}});
        s.owner[1] = me; s.owner[2] = s.owner[3] = 1 - me; s.score[me] = 100; s.turn = 10;
        Rules.updateConnections(s, new Pathing(s.size)); return s;
    }
    private static RolloutBot prepared(GameState s, int me, int depth, int... fullCells) throws Exception {
        RolloutBot b = new RolloutBot(parameters(true).apply("ROLLOUT_DEPTH=" + depth)); b.init(s, me);
        set(b, "ordinaryRootSize", 1); set(b, "bestCount", fullCells.length);
        System.arraycopy(fullCells, 0, ints(b, "bestCells"), 0, fullCells.length);
        ints(b, "rootTargets")[0] = 2;
        b.selectedRoute = -1; b.selectedTarget = -1; b.selectedValue = 100;
        b.ordinarySearchComplete = b.networkPhaseComplete = true; b.completedComparisons = 1;
        b.deadlineBegin(s.turn - 2, me); b.deadlineObserve(s.turn - 2, me, 2, s.turn - 1);
        b.deadlineBegin(s.turn - 1, me); b.deadlineObserve(s.turn - 1, me, 2, s.turn);
        b.deadlineBegin(s.turn, me);
        ((Timer) field(b, "timer")).start(60_000); return b;
    }
    private static Object field(RolloutBot b, String name) throws Exception {
        var f = RolloutBot.class.getDeclaredField(name); f.setAccessible(true); return f.get(b);
    }
    private static void set(RolloutBot b, String name, Object value) throws Exception {
        var f = RolloutBot.class.getDeclaredField(name); f.setAccessible(true); f.set(b, value);
    }
    private static int[] ints(RolloutBot b, String name) throws Exception { return (int[]) field(b, name); }
    private static double[] doubles(RolloutBot b, String name) throws Exception { return (double[]) field(b, name); }
    private static void safeScores(RolloutBot b) throws Exception {
        set(b, "deadlineWCompleted", 2); set(b, "deadlineACompleted", 2); set(b, "deadlineWr0Completed", 2);
        Arrays.fill(doubles(b, "deadlineWValues"), 100);
        Arrays.fill(doubles(b, "deadlineAValues"), 10);
        Arrays.fill(doubles(b, "deadlineWr0Values"), 9);
        for (String name : List.of("deadlineWTerminal", "deadlineATerminal", "deadlineWr0Terminal"))
            Arrays.fill(ints(b, name), 0);
    }
    private static int[] cells(GameState s, List<Action> actions) {
        return actions.stream().filter(a -> a.type == Action.Type.PLACE)
                .mapToInt(a -> s.cell(a.x, a.y)).toArray();
    }
    private static void legal(GameState s, int me, List<Action> actions) {
        var result = new Rules.TurnResult();
        Rules.expand(s, me, actions, new Pathing(s.size), result.moves[me], result);
        assertNull(result.moves[me].error); assertTrue(result.moves[me].skipped.isEmpty());
        assertFalse(result.unknownActions);
    }

    @Test void sameFirstFutureTargetIsRequiredInEveryReplyAndLatestAbsoluteDueWins() throws Exception {
        GameState s = filler(0); RolloutBot b = initialized(s, 0);
        b.deadlineBegin(s.turn, 0); set(b, "deadlineWCompleted", 2);
        Arrays.fill(doubles(b, "deadlineWValues"), 100);
        int[] target = ints(b, "deadlineFirstTarget"), due = ints(b, "deadlineFirstDue");
        target[0] = target[1] = 7; due[0] = 11; due[1] = 13;
        assertTrue(b.deadlinePromisesMatch(2, 7));
        assertEquals(13, b.deadlinePromisedDue(s, 2));
        target[1] = 9;
        assertFalse(b.deadlinePromisesMatch(2, 7), "Another reply's first attack is not the same promise");
        target[1] = -1;
        assertFalse(b.deadlinePromisesMatch(2, 7), "Missing future attack cannot create a deadline");
        target[1] = 7; due[1] = s.turn;
        assertFalse(b.deadlinePromisesMatch(2, 7), "The promise must concern a later real turn");
        due[1] = 13; set(b, "deadlineWCompleted", 1);
        assertFalse(b.deadlinePromisesMatch(2, 7), "A partial reply panel cannot arm memory");
    }

    @Test void guardsPreserveTerminalNonLossAndRequireStrictGainAgainstRegionSpecificAblationInEveryReply() throws Exception {
        RolloutBot b = initialized(filler(0), 0); safeScores(b);
        assertTrue(b.deadlineScoresAccept(2), "A may remain below W; it must beat Wr0 rather than erase other future attacks");
        for (int terminal : new int[]{1, 2}) {
            safeScores(b); ints(b, "deadlineWTerminal")[1] = terminal;
            assertFalse(b.deadlineScoresAccept(2), "A single W terminal win or draw protects the native action");
        }
        safeScores(b); ints(b, "deadlineATerminal")[1] = -1;
        assertFalse(b.deadlineScoresAccept(2), "One losing attack reply is enough to veto");
        safeScores(b); doubles(b, "deadlineAValues")[1] = 9;
        assertFalse(b.deadlineScoresAccept(2), "Ties must not trigger");
        safeScores(b); doubles(b, "deadlineAValues")[1] = 8;
        assertFalse(b.deadlineScoresAccept(2), "A positive other reply cannot hide one regression");
        for (String name : List.of("deadlineWValues", "deadlineAValues", "deadlineWr0Values")) {
            safeScores(b); doubles(b, name)[1] = Double.NaN;
            assertFalse(b.deadlineScoresAccept(2), "Incomplete numerical result must veto: " + name);
        }
        for (String name : List.of("deadlineWCompleted", "deadlineACompleted", "deadlineWr0Completed")) {
            safeScores(b); set(b, name, 1);
            assertFalse(b.deadlineScoresAccept(2), "Every alternative must cover every reply: " + name);
        }
    }

    @Test void originalAbsoluteDeadlineDoesNotRollForwardAndOnlyAnAlreadyMissedDeadlineTriggers() {
        for (int me = 0; me < 2; me++) {
            RolloutBot b = initialized(filler(me), me);
            b.deadlineBegin(10, me); assertFalse(b.deadlineObserve(10, me, 7, 11));
            assertEquals(7, b.deadlinePendingTarget()); assertEquals(11, b.deadlinePendingDue());
            b.deadlineBegin(11, me); assertFalse(b.deadlineObserve(11, me, 7, 12));
            assertEquals(11, b.deadlinePendingDue(), "Being on the deadline is not an observed past miss");
            b.deadlineBegin(12, me); assertTrue(b.deadlineObserve(12, me, 7, 13));
            assertEquals(11, b.deadlinePendingDue(), "The new forecast cannot defer the original obligation");
        }
    }

    @Test void targetChangesTurnGapsRepeatedTurnsSeatChangesAndInitCannotReuseAnOldDeadline() {
        GameState s = filler(0); RolloutBot b = initialized(s, 0);
        b.deadlineBegin(10, 0); b.deadlineObserve(10, 0, 7, 11);
        b.deadlineBegin(11, 0); b.deadlineObserve(11, 0, 7, 12);
        b.deadlineBegin(12, 0);
        assertFalse(b.deadlineObserve(12, 0, 9, 13), "A new target is a new promise");
        assertNotEquals(11, b.deadlinePendingDue());
        b.deadlineBegin(15, 0); assertFalse(b.deadlineObserve(15, 0, 9, 16));
        assertEquals(16, b.deadlinePendingDue(), "Skipped observations break the chain");
        b.deadlineBegin(15, 0); assertFalse(b.deadlineObserve(15, 0, 9, 17));
        assertEquals(17, b.deadlinePendingDue(), "Evaluating the same state twice is not another actual turn");
        b.deadlineBegin(16, 1); assertFalse(b.deadlineObserve(16, 1, 9, 18));
        assertEquals(18, b.deadlinePendingDue(), "Player memories cannot mix");
        b.init(s, 0); assertEquals(-1, b.deadlinePendingTarget()); assertEquals(-1, b.deadlinePendingDue());
        b.deadlineBegin(10, 0); assertFalse(b.deadlineObserve(10, 0, 7, 11));
        b.clearDeadlineCommitment(); assertEquals(-1, b.deadlinePendingTarget()); assertEquals(-1, b.deadlinePendingDue());
    }

    @Test void coldStartPreservesNativeNonemptyFullConstructionOnBothSeats() throws Exception {
        for (int me = 0; me < 2; me++) {
            GameState s = filler(me); String before = JSON.toJson(s);
            RolloutBot off = new RolloutBot(parameters(false)), on = initialized(s, me); off.init(s, me);
            List<Action> nativeAction = off.play(s, me, 60_000), candidateAction = on.play(s, me, 60_000);
            assertEquals(3, cells(s, nativeAction).length);
            assertArrayEquals(cells(s, nativeAction), cells(s, candidateAction));
            assertEquals(Action.format(nativeAction), Action.format(candidateAction), "A fresh bot has no observed missed promise");
            legal(s, me, candidateAction); assertEquals(before, JSON.toJson(s));
        }
    }

    @Test void pinnedRegionAblationPreservesOtherFutureAttacksAndTheCompleteRootOnBothSeats() throws Exception {
        for (int me = 0; me < 2; me++) {
            GameState s = mixed(me, true); String before = JSON.toJson(s);
            RolloutBot b = prepared(s, me, 3, 6, 7, 8);
            int[][] roots = (int[][]) field(b, "roots"); roots[0][0] = 5; ints(b, "rootCounts")[0] = 1;
            int[] originalRoot = roots[0].clone(), full = ints(b, "bestCells").clone();
            // Suppressing region7 must retain the different future region2 hit.
            assertTrue(Double.isFinite(b.deadlineRollout(s, me, 0, -1, 0, 7)));
            assertEquals(2, field(b, "deadlineProbeFirstTarget"));
            assertEquals(11, field(b, "deadlineProbeFirstDue"));
            GameState after = ((SearchState) field(b, "work")).state;
            for (int cell : new int[]{6,7,8}) assertEquals(me, after.owner[cell]);
            // Wr0 removes only later hits on2; an explicit hit at the root survives.
            assertTrue(Double.isFinite(b.deadlineRollout(s, me, 0, 2, 0, 2)));
            after = ((SearchState) field(b, "work")).state;
            assertEquals(1, after.regionInstability[2]);
            assertEquals(-1, field(b, "deadlineProbeFirstTarget"));
            assertArrayEquals(originalRoot, roots[0]); assertEquals(1, ints(b, "rootCounts")[0]);
            assertArrayEquals(full, ints(b, "bestCells")); assertEquals(3, field(b, "bestCount"));
            assertFalse((boolean) field(b, "deadlineProbeActive")); assertEquals(-1, field(b, "deadlineSuppressedRegion"));
            assertEquals(before, JSON.toJson(s));
        }
    }

    @Test void ineligibleDecisionsAndExpiredExtraWorkClearMemoryWithoutChangingNativeConstruction() throws Exception {
        for (int me = 0; me < 2; me++) for (String reason : List.of(
                "native-attack", "own-portfolio", "timeout", "incomplete-native-search", "invalid-target")) {
            GameState s = mixed(me, true); String before = JSON.toJson(s);
            RolloutBot b = prepared(s, me, 3, 6, 7, 8);
            if (reason.equals("native-attack")) b.selectedTarget = 3;
            if (reason.equals("own-portfolio")) ints(b, "counts")[me] = 1;
            if (reason.equals("timeout")) ((Timer) field(b, "timer")).start(-1);
            if (reason.equals("incomplete-native-search")) b.ordinarySearchComplete = false;
            if (reason.equals("invalid-target")) ints(b, "rootTargets")[0] = 0; // Town-containing region.
            int nativeTarget = b.selectedTarget;
            b.correctMissedDeadline(s);
            assertEquals(reason, b.deadlineStatus); assertFalse(b.deadlineChanged);
            assertEquals(nativeTarget, b.selectedTarget);
            assertArrayEquals(new int[]{6,7,8}, Arrays.copyOf(ints(b, "bestCells"), 3));
            assertEquals(-1, b.deadlinePendingTarget()); assertEquals(-1, b.deadlinePendingDue());
            assertEquals(before, JSON.toJson(s));
        }
    }

    @Test void actualCoreForecastTerminalWinProtectsAnOverdueCommitmentOnBothSeats() throws Exception {
        for (int me = 0; me < 2; me++) {
            GameState s = mixed(me, false); RolloutBot b = prepared(s, me, 8);
            b.correctMissedDeadline(s);
            assertEquals(1, b.deadlineWCompleted); assertEquals(1, b.deadlineWTerminal[0]);
            assertEquals("unsafe-or-changed-W", b.deadlineStatus);
            assertEquals(-1, b.selectedTarget); assertFalse(b.deadlineChanged);
            assertEquals(-1, b.deadlinePendingTarget());
        }
    }

    @Test void warmupCannotCreateOrConsumeLiveHistoryAndZeroBudgetClearsCommitment() throws Exception {
        GameState s = filler(0); RolloutBot b = initialized(s, 0); String before = JSON.toJson(s);
        b.deadlineBegin(10, 0); b.deadlineObserve(10, 0, 7, 11);
        b.warmup(s, 0, 1);
        assertEquals(7, b.deadlinePendingTarget()); assertEquals(11, b.deadlinePendingDue());
        assertEquals(10, field(b, "deadlineLastTurn")); assertEquals(before, JSON.toJson(s));
        s.turn = 11;
        assertEquals("WAIT", Action.format(b.play(s, 0, 0)));
        assertEquals("timeout", b.deadlineStatus); assertEquals(-1, b.deadlinePendingTarget());
        assertFalse(b.deadlineChanged);
    }

    @Test void featureDefaultsOffAndFrozenV09ParametersDoNotOptIn() {
        assertFalse(new Params().USE_ROLLOUT_MISSED_DEADLINE);
        assertFalse(BotFactory.parameters("v09").USE_ROLLOUT_MISSED_DEADLINE);
        assertDoesNotThrow(() -> BotFactory.create("current?USE_ROLLOUT_MISSED_DEADLINE=1"));
    }
}
