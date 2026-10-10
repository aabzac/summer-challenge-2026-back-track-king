package btk.bot.neural;

import btk.bot.Params;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Fixed synthetic networks and states; no learned weights or real-game experiments. */
class SpatialV5BotTest {
    @Test void disabledOrInsufficientSearchTimeReturnsExactUnchangedGreedyConstructionAndActions() {
        GameState state = mixedBoard(); float[] weights = weights();
        SpatialBot original = new SpatialBot(new Params(), new SpatialNet(weights), () -> 0);
        List<Action> expected = original.play(state, 0, 36);
        for (boolean disabled : new boolean[]{true, false}) {
            Params params = searchParams(); params.USE_SPATIAL_V5_SEARCH = !disabled;
            if (!disabled) params.SPATIAL_V5_MIN_SEARCH_MS = 1000;
            SpatialV5Bot bot = new SpatialV5Bot(params, new SpatialNet(weights), () -> 0);
            assertEquals(Action.format(expected), Action.format(bot.play(state, 0, 36)));
            assertEquals(original.lastPlacements, bot.lastPlacements);
            assertEquals(original.lastTarget, bot.lastTarget);
            assertEquals(original.lastCompletedDecisions, bot.lastCompletedDecisions);
            assertEquals(original.lastInterrupted, bot.lastInterrupted);
            assertFalse(bot.lastSearchAttempted); assertFalse(bot.lastSearchComplete);
            assertEquals(0, bot.lastLeafEvaluations);
        }
        SpatialV5Bot exhausted = new SpatialV5Bot(searchParams(), new SpatialNet(weights), () -> 0);
        assertEquals("WAIT", Action.format(exhausted.play(state, 0, 2)));
        assertTrue(exhausted.lastInterrupted); assertFalse(exhausted.lastSearchAttempted);
    }

    @Test void samePreTurnOpponentPlanLetsBothSeatsKeepIncomeByChoosingStopInsteadOfMutualInk() {
        for (int me = 0; me < 2; me++) {
            GameState state = connectedLine(me, 2); state.score[me] = 100;
            int[] owner = state.owner.clone(), instability = state.instability.clone(), score = state.score.clone();
            SpatialV5Bot bot = new SpatialV5Bot(searchParams(), new SpatialNet(weights()), () -> 0);
            assertEquals("WAIT", Action.format(bot.play(state, me, 36)));
            assertTrue(bot.lastSearchAttempted); assertTrue(bot.lastSearchComplete); assertTrue(bot.lastSearchChanged);
            assertFalse(bot.lastSearchCutoff); assertFalse(bot.lastInterrupted);
            assertEquals(1, bot.lastBaseTarget); assertEquals(-1, bot.lastAlternativeTarget); assertEquals(-1, bot.lastTarget);
            assertEquals(0, bot.lastPlacements); assertEquals(0, bot.lastOpponentPlacements); assertEquals(1, bot.lastOpponentTarget);
            assertEquals(2, bot.lastLeafEvaluations);
            assertEquals(0f, bot.lastBaseValue); assertEquals(0.0001f, bot.lastAlternativeValue, 1e-9f);
            assertArrayEquals(owner, state.owner); assertArrayEquals(instability, state.instability); assertArrayEquals(score, state.score);
            assertEquals(0, state.turn); assertFalse(state.regionInked[1]);
        }
    }

    @Test void stopCanBeTheGreedyChoiceAndOpponentDoesNotReactToOurAlternativeDisruption() {
        GameState state = connectedLine(1, 3);
        float[] weights = weights(); weights[SpatialNet.NOINK_B] = 2;
        SpatialV5Bot bot = new SpatialV5Bot(searchParams(), new SpatialNet(weights), () -> 0);
        assertEquals("DISRUPT 1", Action.format(bot.play(state, 0, 36)));
        assertEquals(-1, bot.lastBaseTarget); assertEquals(1, bot.lastAlternativeTarget); assertEquals(1, bot.lastTarget);
        assertEquals(-1, bot.lastOpponentTarget); assertEquals(0, bot.lastOpponentPlacements);
        assertTrue(bot.lastSearchChanged); assertTrue(bot.lastSearchComplete);
        assertEquals(-0.0001f, bot.lastBaseValue, 1e-9f); assertEquals(0f, bot.lastAlternativeValue);
        assertEquals(3, state.regionInstability[1]); assertEquals(1, state.owner[1]);
    }

    @Test void terminalLeafIgnoresCriticAndUsesOnlyOneNewIncomeDeltaWithStableExactTie() {
        GameState state = connectedLine(0, 0); state.maxTurns = 1;
        state.score[0] = 137; state.score[1] = 13;
        float[] weights = weights(); weights[SpatialNet.VALUE_B] = 100;
        SpatialV5Bot bot = new SpatialV5Bot(searchParams(), new SpatialNet(weights), () -> 0);
        assertEquals("DISRUPT 1", Action.format(bot.play(state, 0, 36)));
        assertTrue(bot.lastSearchComplete); assertFalse(bot.lastSearchChanged); assertEquals(2, bot.lastLeafEvaluations);
        assertEquals(0.0001f, bot.lastBaseValue, 1e-9f); assertEquals(bot.lastBaseValue, bot.lastAlternativeValue);
        assertEquals(137, state.score[0]); assertEquals(13, state.score[1]); assertEquals(0, state.turn);
    }

    @Test void cachedOpponentPackingHonorsCoreCostsAndSimultaneousCollisionWithoutChangingOwnConstruction() {
        GameState state = mixedBoard(); float[] weights = weights();
        SpatialBot original = new SpatialBot(new Params(), new SpatialNet(weights), () -> 0);
        String base = Action.format(original.play(state, 0, 36));
        SpatialV5Bot bot = new SpatialV5Bot(searchParams(), new SpatialNet(weights), () -> 0);
        List<Action> actual = bot.play(state, 0, 36);
        assertEquals(base, Action.format(actual)); assertTrue(bot.lastSearchComplete); assertFalse(bot.lastSearchChanged);
        assertEquals(2, bot.lastPlacements); assertEquals(2, bot.lastOpponentPlacements);
        assertArrayEquals(new int[]{0,2,-1}, bot.lastBaseCells);
        assertArrayEquals(new int[]{0,2,-1}, bot.lastOpponentCells);
        List<Action> opponent = new ArrayList<>();
        for (int i = 0; i < bot.lastOpponentPlacements; i++) {
            int c = bot.lastOpponentCells[i]; opponent.add(Action.place(state.x(c), state.y(c)));
        }
        opponent.add(Action.disrupt(bot.lastOpponentTarget));
        GameState resolved = state.copy();
        Rules.TurnResult result = Rules.resolve(resolved, actual, opponent, new Pathing(state.size));
        assertEquals(List.of(0,2), result.neutralised);
        for (int me = 0; me < 2; me++) { assertNull(result.moves[me].error); assertTrue(result.moves[me].skipped.isEmpty()); }
        for (int owner : state.owner) assertEquals(GameState.NO_TRACK, owner);
        assertEquals(0, state.turn);
        float[] stoppedWeights = weights(); stoppedWeights[SpatialNet.STOP_B] = 2;
        SpatialV5Bot stopped = new SpatialV5Bot(searchParams(), new SpatialNet(stoppedWeights), () -> 0);
        stopped.play(state, 0, 36);
        assertEquals(0, stopped.lastPlacements); assertEquals(0, stopped.lastOpponentPlacements);
        assertEquals(0, stopped.lastOpponentTarget, "STOP does not suppress independent disruption");
    }

    @Test void deadlineBeforePredictionOrAfterOnlyOneCompletedLeafAlwaysReturnsTheOriginalPlan() {
        for (boolean afterLeaf : new boolean[]{false, true}) {
            GameState state = connectedLine(0, 2); SpatialV5Bot[] ref = new SpatialV5Bot[1];
            LongSupplier clock = () -> ref[0] != null && (afterLeaf ? ref[0].lastLeafEvaluations > 0 : ref[0].lastSearchAttempted)
                    ? 1_000_000_000L : 0L;
            SpatialV5Bot bot = new SpatialV5Bot(searchParams(), new SpatialNet(weights()), clock); ref[0] = bot;
            assertEquals("DISRUPT 1", Action.format(bot.play(state, 0, 36)));
            assertTrue(bot.lastSearchAttempted); assertTrue(bot.lastSearchCutoff);
            assertFalse(bot.lastSearchComplete); assertFalse(bot.lastSearchChanged); assertFalse(bot.lastInterrupted);
            assertEquals(afterLeaf ? 1 : 0, bot.lastLeafEvaluations);
            assertTrue(Float.isNaN(bot.lastAlternativeValue)); assertEquals(1, bot.lastTarget);
            assertEquals(0, state.turn); assertEquals(2, state.regionInstability[1]);
        }
    }

    @Test void nonfiniteContinuationCannotReplaceGreedyAndLaterCallsResetSearchDiagnostics() {
        GameState state = connectedLine(0, 0); state.score[0] = 1_000_000;
        float[] weights = weights(); weights[SpatialNet.VALUE_W + 13] = Float.MAX_VALUE;
        SpatialV5Bot bot = new SpatialV5Bot(searchParams(), new SpatialNet(weights), () -> 0);
        assertEquals("DISRUPT 1", Action.format(bot.play(state, 0, 36)));
        assertTrue(bot.lastSearchAttempted); assertTrue(bot.lastSearchCutoff);
        assertFalse(bot.lastSearchComplete); assertFalse(bot.lastSearchChanged); assertEquals(0, bot.lastLeafEvaluations);
        assertTrue(Float.isNaN(bot.lastBaseValue)); assertEquals(1_000_000, state.score[0]);
        assertEquals("WAIT", Action.format(bot.play(state, 0, 2)));
        assertTrue(bot.lastInterrupted); assertFalse(bot.lastSearchAttempted); assertFalse(bot.lastSearchCutoff);
        assertEquals(0, bot.lastLeafEvaluations); assertEquals(-1, bot.lastTarget); assertEquals(-1, bot.lastBaseTarget);
        assertArrayEquals(new int[]{-1,-1,-1}, bot.lastOpponentCells);
    }

    @Test void warmupLeavesInputUntouchedAndAProtectedOnlyMapHasNoAlternativeToSearch() {
        GameState state = new GameState(3, 1, new int[]{0,0,0}, new int[3], new int[]{0,2}, new int[]{0,0}, new int[][]{{1},{}});
        AtomicLong time = new AtomicLong();
        SpatialV5Bot warming = new SpatialV5Bot(searchParams(), new SpatialNet(weights()), () -> time.getAndAdd(500_000));
        warming.warmup(state, 0, 1);
        assertEquals(0, state.turn); assertArrayEquals(new int[]{-1,-1,-1}, state.owner);
        SpatialV5Bot bot = new SpatialV5Bot(searchParams(), new SpatialNet(weights()), () -> 0);
        assertEquals("PLACE_TRACKS 1 0", Action.format(bot.play(state, 0, 36)));
        assertEquals(-1, bot.lastTarget); assertFalse(bot.lastSearchAttempted); assertEquals(0, bot.lastLeafEvaluations);
        Params invalid = searchParams(); invalid.SPATIAL_V5_MIN_SEARCH_MS = -1;
        assertThrows(IllegalArgumentException.class, () -> new SpatialV5Bot(invalid, new SpatialNet(weights()), () -> 0));
    }

    private static Params searchParams() { Params p = new Params(); p.USE_SPATIAL_V5_SEARCH = true; return p; }
    private static float[] weights() { float[] result = new float[SpatialNet.WEIGHT_COUNT]; result[SpatialNet.INK_B] = 1; return result; }
    private static GameState connectedLine(int owner, int instability) {
        GameState state = new GameState(3, 1, new int[]{0,1,2}, new int[3], new int[]{0,2}, new int[]{0,0}, new int[][]{{1},{}});
        state.owner[1] = owner; state.regionInstability[1] = instability; state.instability[1] = instability;
        Rules.updateConnections(state, new Pathing(state.size)); return state;
    }
    private static GameState mixedBoard() {
        int[] regions = new int[10]; Arrays.setAll(regions, i -> i);
        int[] terrain = new int[10]; terrain[0] = GameState.RIVER; terrain[1] = GameState.MOUNTAIN;
        return new GameState(5, 2, regions, terrain, new int[]{0,4}, new int[]{1,1}, new int[][]{{1},{}});
    }
}
