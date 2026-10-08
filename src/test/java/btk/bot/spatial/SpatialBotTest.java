package btk.bot.neural;

import btk.bot.Params;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.*;

class SpatialBotTest {
    private static GameState board() {
        int[] regions = new int[15], terrain = new int[15];
        for (int i = 0; i < regions.length; i++) regions[i] = i;
        terrain[5] = GameState.RIVER;
        GameState state = new GameState(5, 3, regions, terrain, new int[]{0, 4}, new int[]{0, 2},
                new int[][]{{1}, {0}});
        for (int cell = 1; cell < 5; cell++) state.owner[cell] = 1;
        Rules.updateConnections(state, new Pathing(state.size));
        return state;
    }

    private static SpatialNet zeroNet() { return new SpatialNet(new float[SpatialNet.WEIGHT_COUNT]); }

    @Test void greedyPlanUsesNativeCoordinatesAndOnlyPendingPrefixBeforeSimultaneousResolve() {
        GameState state = board();
        int[] original = state.owner.clone();
        SpatialBot bot = new SpatialBot(new Params(), zeroNet(), () -> 0);
        bot.init(state, 0);
        List<Action> plan = bot.play(state, 0, 50);
        assertEquals("PLACE_TRACKS 0 1;PLACE_TRACKS 1 1;DISRUPT 1", Action.format(plan));
        assertEquals(2, bot.lastPlacements);
        assertEquals(1, bot.lastTarget);
        assertFalse(bot.lastInterrupted);
        assertArrayEquals(original, state.owner, "the policy must not apply its prefix to the shared board");
        var result = Rules.resolve(state, plan, plan, new Pathing(state.size));
        assertNull(result.moves[0].error);
        assertNull(result.moves[1].error);
        assertTrue(result.moves[0].skipped.isEmpty());
        assertTrue(result.moves[1].skipped.isEmpty());
        assertEquals(GameState.NEUTRAL, state.owner[5]);
        assertEquals(GameState.NEUTRAL, state.owner[6]);
    }

    @Test void stopDoesNotSuppressTheFinalDisruptionDecision() {
        float[] weights = new float[SpatialNet.WEIGHT_COUNT];
        weights[SpatialNet.STOP_B] = 1;
        SpatialBot bot = new SpatialBot(new Params(), new SpatialNet(weights), () -> 0);
        assertEquals("DISRUPT 1", Action.format(bot.play(board(), 0, 50)));
        assertEquals(0, bot.lastPlacements);
        assertEquals(2, bot.lastCompletedDecisions);
        weights[SpatialNet.NOINK_B] = 1;
        SpatialBot waiting = new SpatialBot(new Params(), new SpatialNet(weights), () -> 0);
        assertEquals("WAIT", Action.format(waiting.play(board(), 0, 50)));
        assertEquals(-1, waiting.lastTarget);
        assertFalse(waiting.lastInterrupted);
    }

    @Test void elapsedBudgetReturnsWaitWithoutStartingInference() {
        SpatialBot bot = new SpatialBot(new Params(), zeroNet(), () -> 0);
        for (long budget : new long[]{-1, 0, 2}) {
            assertEquals("WAIT", Action.format(bot.play(board(), 0, budget)));
            assertEquals(0, bot.lastCompletedDecisions);
            assertTrue(bot.lastInterrupted);
        }
    }

    @Test void cutoffInsideNextConvolutionRetainsExactlyTheCompletedPrefix() {
        final class PrefixClock implements LongSupplier {
            SpatialBot bot;
            int reads;
            public long getAsLong() {
                return bot != null && bot.lastPlacements >= 1 && ++reads >= 10 ? 48_000_000L : 0;
            }
        }
        PrefixClock clock = new PrefixClock();
        SpatialBot bot = new SpatialBot(new Params(), zeroNet(), clock);
        clock.bot = bot;
        GameState state = board();
        List<Action> plan = bot.play(state, 0, 50);
        assertEquals("PLACE_TRACKS 0 1", Action.format(plan));
        assertEquals(1, bot.lastPlacements);
        assertEquals(-1, bot.lastTarget);
        assertEquals(1, bot.lastCompletedDecisions);
        assertTrue(bot.lastInterrupted);
        var result = Rules.resolve(state, plan, List.of(Action.waitAction()), new Pathing(state.size));
        assertNull(result.moves[0].error);
        assertTrue(result.moves[0].skipped.isEmpty());
    }

    @Test void negativeReserveIsRejected() {
        Params params = new Params();
        params.SPATIAL_RESERVE_MS = -1;
        assertThrows(IllegalArgumentException.class, () -> new SpatialBot(params, zeroNet(), () -> 0));
    }
}
