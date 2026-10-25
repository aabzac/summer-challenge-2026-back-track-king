package btk.bot.v10;

import btk.bot.current.RolloutTestParams;
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

import static btk.bot.v10.RolloutDiverseRepliesTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** Evaluate the emitted first-turn fill without changing the later rollout policy. */
/** Moved from btk.bot.current by prune-v1 (2026-10-25): the mechanism under test now lives only in frozen v10. */
class RolloutRootFillTest {
    private static final Gson JSON = new Gson();

    private static Params parameters(boolean enabled) {
        return RolloutTestParams.base("NOISE=0,USE_DISRUPT=0,USE_ROLLOUT_FILL=1,ROLLOUT_DEPTH=1")
                .apply("USE_ROLLOUT_ROOT_FILL=" + enabled);
    }

    private static GameState board(int me) {
        var s = new GameState(5, 2, new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9}, new int[10],
                new int[]{0, 4}, new int[2], new int[][]{{1}, {}});
        s.owner[1] = s.owner[2] = me;
        s.turn = 10;
        Rules.updateConnections(s, new Pathing(s.size));
        return s;
    }

    private static void set(RolloutBotV10 b, String name, Object value) throws Exception {
        var field = RolloutBotV10.class.getDeclaredField(name); field.setAccessible(true); field.set(b, value);
    }

    /** One known affordable connection prefix, with no enemy construction or later-turn ambiguity. */
    private static RolloutBotV10 prepared(GameState s, int me, int target) throws Exception {
        var b = new RolloutBotV10(parameters(true)); b.init(s, me);
        pool(b, me, new int[]{0, 1, 2, 3, 4});
        ((int[][]) field(b, "roots"))[0][0] = 3;
        ((int[]) field(b, "rootCounts"))[0] = 1;
        ((int[]) field(b, "rootTargets"))[0] = target;
        set(b, "ordinaryRootSize", 2); set(b, "best", -Double.MAX_VALUE);
        set(b, "bestCount", 1); ((int[]) field(b, "bestCells"))[0] = 3;
        b.selectedRoute = 0; b.selectedTarget = target;
        ((Timer) field(b, "timer")).start(60_000);
        return b;
    }

    private static boolean search(RolloutBotV10 b, GameState s, int me) throws Exception {
        return (boolean) call(b, "searchRoots",
                new Class<?>[]{GameState.class, int.class, int.class, int.class, int.class}, s, me, 0, 1, 1);
    }

    @SuppressWarnings("unchecked")
    private static List<Action> actions(RolloutBotV10 b, GameState s) throws Exception {
        return (List<Action>) call(b, "actions", new Class<?>[]{GameState.class}, s);
    }

    private static int[] cells(GameState s, List<Action> actions) {
        return actions.stream().filter(a -> a.type == Action.Type.PLACE).mapToInt(a -> s.cell(a.x, a.y)).toArray();
    }

    private static void legal(GameState s, int me, List<Action> actions) {
        var result = new Rules.TurnResult();
        Rules.expand(s, me, actions, new Pathing(s.size), result.moves[me], result);
        assertNull(result.moves[me].error); assertTrue(result.moves[me].skipped.isEmpty());
    }

    private static GameState resolve(GameState s, int me, List<Action> actions) {
        GameState after = s.copy(); var wait = List.of(Action.waitAction());
        Rules.resolve(after, me == 0 ? actions : wait, me == 1 ? actions : wait, new Pathing(s.size));
        return after;
    }

    private static double oneTurnValue(GameState s, int me) {
        int[] income = new int[2]; Rules.incomePerTurn(s, income);
        return s.score[me] - s.score[1 - me]
                + Math.min(parameters(true).ROLLOUT_TAIL, s.turnsLeft()) * (income[me] - income[1 - me]);
    }

    @Test void completeFirstTurnContainsTheSameFilledCellsInEvaluationAndEmissionOnBothSeats() throws Exception {
        for (int me = 0; me < 2; me++) {
            GameState s = board(me); String before = JSON.toJson(s);
            RolloutBotV10 b = prepared(s, me, -1);
            assertTrue(search(b, s, me)); assertEquals(1, b.completedComparisons);
            GameState simulated = ((SearchState) field(b, "work")).state;
            List<Action> emitted = actions(b, s);
            assertArrayEquals(new int[]{3, 6, 5}, cells(s, emitted));
            assertEquals(2, b.fillCells, "emission must not replace the evaluated fill with a second fill");
            GameState actual = resolve(s, me, emitted);
            assertArrayEquals(actual.owner, simulated.owner);
            assertArrayEquals(actual.score, simulated.score); assertEquals(actual.turn, simulated.turn);
            assertEquals(oneTurnValue(actual, me), b.selectedValue);
            legal(s, me, emitted); assertEquals(before, JSON.toJson(s));
        }
    }

    @Test void attackAndWaitGetTheirOwnFillAndEarlierWinningCellsSurviveLaterScratchReuse() throws Exception {
        for (int me = 0; me < 2; me++) {
            GameState s = board(me); String before = JSON.toJson(s);
            RolloutBotV10 b = prepared(s, me, 6);
            assertTrue(search(b, s, me)); assertEquals(2, b.completedComparisons);
            // Both branches have the same connection income. The first (attack) wins the tie,
            // while the last simulated waiting branch may safely fill the target region.
            assertEquals(6, b.selectedTarget);
            assertArrayEquals(new int[]{3, 6, 5}, Arrays.copyOf((int[]) field(b, "filledRoot"), 3));
            List<Action> emitted = actions(b, s);
            assertArrayEquals(new int[]{3, 7, 8}, cells(s, emitted));
            for (int c : cells(s, emitted)) assertNotEquals(b.selectedTarget, s.regionId[c]);
            assertEquals(2, b.fillCells);
            assertEquals(oneTurnValue(resolve(s, me, emitted), me), b.selectedValue);
            legal(s, me, emitted); assertEquals(before, JSON.toJson(s));
        }
    }

    @Test void expiredComparisonPreservesCompletedWinnerAndAnUnscoredFallbackStillUsesTheUsualFill() throws Exception {
        for (int me = 0; me < 2; me++) {
            GameState s = board(me); RolloutBotV10 b = prepared(s, me, -1);
            assertTrue(search(b, s, me));
            String winner = Action.format(actions(b, s)); double value = b.selectedValue;
            ((Timer) field(b, "timer")).start(-1);
            assertFalse(search(b, s, me));
            assertEquals(1, b.completedComparisons); assertEquals(value, b.selectedValue);
            assertEquals(winner, Action.format(actions(b, s))); assertEquals(2, b.fillCells);

            RolloutBotV10 fallback = prepared(s, me, -1);
            ((Timer) field(fallback, "timer")).start(-1);
            assertFalse(search(fallback, s, me)); assertEquals(0, fallback.completedComparisons);
            assertFalse((boolean) field(fallback, "selectedRootFilled"));
            // Isolate fallback emission from an exhausted wall clock: it must retain the
            // ordinary filler, unlike a scored winner whose exact cells are already fixed.
            ((Timer) field(fallback, "timer")).start(60_000);
            List<Action> emitted = actions(fallback, s);
            assertArrayEquals(new int[]{3, 6, 5}, cells(s, emitted)); legal(s, me, emitted);
            assertEquals("WAIT", Action.format(fallback.play(s, me, 0)));
            assertEquals(0, fallback.fillCells); assertFalse((boolean) field(fallback, "selectedRootFilled"));
        }
    }

    @Test void fullPaintAndUnavailableFillKeepTheExistingMoveAndScratchCanBeReused() throws Exception {
        for (int me = 0; me < 2; me++) for (boolean fullPaint : new boolean[]{false, true}) {
            GameState s = board(me);
            if (fullPaint) s.owner[1] = s.owner[2] = GameState.NO_TRACK;
            else for (int c = 5; c < s.size; c++) {
                s.instability[c] = 1; s.regionInstability[c] = 1;
            }
            Rules.updateConnections(s, new Pathing(s.size)); String before = JSON.toJson(s);
            var off = new RolloutBotV10(parameters(false)); var on = new RolloutBotV10(parameters(true));
            off.init(s, me); on.init(s, me);
            Object marks = field(on, "fillMarks"), root = field(on, "filledRoot");
            for (int repeat = 0; repeat < 2; repeat++) {
                List<Action> expected = off.play(s, me, 60_000), actual = on.play(s, me, 60_000);
                assertEquals(Action.format(expected), Action.format(actual));
                assertEquals(off.selectedValue, on.selectedValue); assertEquals(0, on.fillCells);
                assertSame(marks, field(on, "fillMarks")); assertSame(root, field(on, "filledRoot"));
                legal(s, me, actual); assertEquals(before, JSON.toJson(s));
            }
        }
    }

    @Test void independentFlagStartsOffAndCannotSilentlyCombineWithRiskOrDisableTheFiller() {
        assertFalse(new Params().USE_ROLLOUT_ROOT_FILL);
        assertFalse(BotFactory.parameters("v09").USE_ROLLOUT_ROOT_FILL);
        assertDoesNotThrow(() -> BotFactory.create("v10?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",USE_ROLLOUT_ROOT_FILL=1"));
        assertThrows(IllegalArgumentException.class,
                () -> BotFactory.create("v10?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",USE_ROLLOUT_ROOT_FILL=1,USE_ROLLOUT_ALL_CELL_RISK=1"));
        assertThrows(IllegalArgumentException.class,
                () -> BotFactory.create("v10?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",USE_ROLLOUT_ROOT_FILL=1,USE_ROLLOUT_FILL=0"));
    }
}
