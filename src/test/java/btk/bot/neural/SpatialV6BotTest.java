package btk.bot.neural;

import btk.bot.Params;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic tactical fixtures; no training, new maps or actual-match measurements. */
class SpatialV6BotTest {
    @Test void searchOffPreservesFrozenV4ConditionalReadoutForBothSeatsAndTurns() {
        for (int me = 0; me < 2; me++) for (int turn : new int[]{0,57}) {
            GameState state = branchBoard(); state.turn = turn; state.score[me] = 173;
            SpatialBot v4 = new SpatialBot(new Params(), new SpatialNet(SpatialV4Weights.DATA), () -> 0);
            SpatialV6Bot v6 = new SpatialV6Bot(new Params(), new SpatialNet(SpatialV4Weights.DATA), () -> 0);
            assertEquals(Action.format(v4.play(state, me, 36)), Action.format(v6.play(state, me, 36)));
            assertEquals(v4.lastPlacements, v6.lastPlacements); assertEquals(v4.lastTarget, v6.lastTarget);
            assertEquals(v4.lastCompletedDecisions, v6.lastCompletedDecisions); assertEquals(v4.lastInterrupted, v6.lastInterrupted);
            assertFalse(v6.lastSearchAttempted); assertEquals(0, v6.lastCandidateEvaluations);
            assertEquals(turn, state.turn);
        }
        Params insufficient = searchParams(); insufficient.SPATIAL_V6_MIN_SEARCH_MS = 1000;
        SpatialV6Bot bot = bot(insufficient, weights());
        assertEquals("PLACE_TRACKS 0 0", Action.format(bot.play(branchBoard(), 0, 36)));
        assertEquals(4, bot.lastNewCell); assertFalse(bot.lastSearchAttempted);
    }

    @Test void lastActualSameCostPlacementCanChangeForBothSeatsWithoutChangingDisruptOrInput() {
        for (int me = 0; me < 2; me++) {
            GameState state = branchBoard(); state.score[me] = 173; state.score[1-me] = 11;
            int[] owner = state.owner.clone(), instability = state.instability.clone(), score = state.score.clone();
            boolean[] inked = state.inked.clone();
            SpatialV6Bot bot = bot(searchParams(), weights());
            List<Action> selected = bot.play(state, me, 36);
            assertEquals("PLACE_TRACKS 1 1", Action.format(selected));
            assertTrue(bot.lastSearchComplete); assertTrue(bot.lastSearchChanged); assertFalse(bot.lastSearchCutoff);
            assertEquals(1, bot.lastPlacements); assertEquals(1, bot.lastBasePlacements); assertEquals(1, bot.lastAlternativePlacements);
            assertEquals(0, bot.lastBranchSlot); assertEquals(0, bot.lastOldCell); assertEquals(4, bot.lastNewCell);
            assertArrayEquals(new int[]{0,-1,-1}, bot.lastBaseCells);
            assertArrayEquals(new int[]{4,-1,-1}, bot.lastAlternativeCells);
            assertEquals(-1, bot.lastBaseTarget); assertEquals(-1, bot.lastAlternativeTarget); assertEquals(-1, bot.lastTarget);
            assertEquals(state.placeCost(bot.lastOldCell), state.placeCost(bot.lastNewCell));
            Rules.TurnResult expanded = new Rules.TurnResult();
            Rules.expand(state, me, selected, new Pathing(state.size), expanded.moves[me], expanded);
            assertNull(expanded.moves[me].error); assertTrue(expanded.moves[me].skipped.isEmpty()); assertEquals(3, expanded.moves[me].cost);
            assertArrayEquals(owner, state.owner); assertArrayEquals(instability, state.instability);
            assertArrayEquals(score, state.score); assertArrayEquals(inked, state.inked); assertEquals(0, state.turn);
        }
    }

    @Test void fixedRootOpponentAndFreshSimultaneousChildPackingProduceExactTwoTurnScores() {
        GameState state = branchBoard(); state.score[0] = 1000; state.score[1] = 75;
        SpatialV6Bot bot = bot(searchParams(), weights()); bot.play(state, 0, 36);
        assertTrue(bot.lastOwnPriorComplete); assertTrue(bot.lastOpponentComplete);
        assertArrayEquals(new int[]{0,-1,-1}, bot.lastOpponentCells); assertEquals(1, bot.lastOpponentPlacements);
        assertEquals(-1, bot.lastOpponentTarget); assertEquals(2, bot.lastCandidateEvaluations);
        assertEquals(2, bot.lastBaseSteps); assertEquals(2, bot.lastAlternativeSteps);
        assertFalse(bot.lastBaseTerminal); assertFalse(bot.lastAlternativeTerminal);
        assertEquals(0L, bot.lastBaseScoreDelta, "both players must select the same remaining cell before either applies it");
        assertEquals(2L, bot.lastAlternativeScoreDelta, "one owned connecting cell earns once per actual core turn");
        // Independently replay the two expected trajectories using the regular shared core.
        GameState base = state.copy(), alternative = state.copy();
        List<Action> oldCell = List.of(Action.place(0,0)), newCell = List.of(Action.place(1,1)), wait = List.of(Action.waitAction());
        Pathing p = new Pathing(state.size);
        Rules.resolve(base, oldCell, oldCell, p); Rules.resolve(base, newCell, newCell, p);
        Rules.resolve(alternative, newCell, oldCell, p); Rules.resolve(alternative, wait, wait, p);
        assertEquals(GameState.NEUTRAL, base.owner[4]); assertEquals(0, alternative.owner[4]);
        assertEquals(0, (base.score[0]-base.score[1])-(1000-75));
        assertEquals(2, (alternative.score[0]-alternative.score[1])-(1000-75));
        assertEquals(1000, state.score[0]); assertEquals(75, state.score[1]);
    }

    @Test void terminalStopsAtOneTurnAndDepthOneIsValidWithoutAnyLearnedValue() {
        for (boolean terminal : new boolean[]{true, false}) {
            Params p = searchParams(); if (!terminal) p.SPATIAL_V6_DEPTH = 1;
            GameState state = branchBoard(); if (terminal) state.maxTurns = 1;
            state.score[0] = 1_000_000;
            float[] weights = weights(); weights[SpatialNet.VALUE_W + 13] = Float.MAX_VALUE;
            SpatialNet net = new SpatialNet(weights); SpatialV6Bot bot = new SpatialV6Bot(p, net, () -> 0);
            assertEquals("PLACE_TRACKS 1 1", Action.format(bot.play(state, 0, 36)));
            assertTrue(bot.lastSearchComplete); assertTrue(bot.lastSearchChanged);
            assertEquals(1, bot.lastBaseSteps); assertEquals(1, bot.lastAlternativeSteps);
            assertEquals(terminal, bot.lastBaseTerminal); assertEquals(terminal, bot.lastAlternativeTerminal);
            assertEquals(0L, bot.lastBaseScoreDelta); assertEquals(1L, bot.lastAlternativeScoreDelta);
            assertFalse(Float.isFinite(net.value), "the unused value head deliberately overflows in this fixture");
        }
    }

    @Test void exactScoreTieKeepsWholeGreedyPlanAndDoesNotUseAnyEarlierAvailableBranch() {
        GameState tie = branchBoard();
        // Both towns share a neutral route already; neither choice changes its income.
        tie.owner[4] = GameState.NEUTRAL; tie.inked[1] = false; tie.regionInked[1] = false;
        tie.terrain[1] = GameState.MOUNTAIN;
        Rules.updateConnections(tie, new Pathing(tie.size));
        SpatialV6Bot bot = bot(searchParams(), weights());
        assertEquals("PLACE_TRACKS 0 0", Action.format(bot.play(tie, 0, 36)));
        assertTrue(bot.lastSearchComplete); assertFalse(bot.lastSearchChanged);
        assertEquals(0L, bot.lastBaseScoreDelta); assertEquals(0L, bot.lastAlternativeScoreDelta);
        int[] regions = {0,1,2,3,4,5,6,7};
        int[] terrain = new int[8]; terrain[5] = GameState.MOUNTAIN; terrain[6] = GameState.MOUNTAIN;
        GameState three = new GameState(4,2,regions,terrain,new int[]{0,3},new int[]{1,1},new int[][]{{1},{}});
        three.inked[3] = true; three.regionInked[3] = true;
        SpatialV6Bot noLastAlternative = bot(searchParams(), weights());
        assertEquals("PLACE_TRACKS 0 0;PLACE_TRACKS 1 0;PLACE_TRACKS 2 0", Action.format(noLastAlternative.play(three, 0, 36)));
        assertEquals(3, noLastAlternative.lastPlacements); assertEquals(2, noLastAlternative.lastBranchSlot);
        assertEquals(-1, noLastAlternative.lastNewCell);
        assertFalse(noLastAlternative.lastSearchAttempted, "earlier slots had alternatives, but the final actual placement did not");
    }

    @Test void cutoffBeforePredictionDuringTrajectoryOrAfterOneCandidateAlwaysKeepsOriginalPlan() {
        for (int phase = 0; phase < 3; phase++) {
            int selectedPhase = phase; SpatialV6Bot[] ref = new SpatialV6Bot[1];
            LongSupplier clock = () -> {
                SpatialV6Bot b = ref[0];
                boolean cut = b != null && (selectedPhase == 0 ? b.lastSearchAttempted
                        : selectedPhase == 1 ? b.lastBaseSteps > 0 : b.lastCandidateEvaluations > 0);
                return cut ? 1_000_000_000L : 0L;
            };
            SpatialV6Bot bot = new SpatialV6Bot(searchParams(), new SpatialNet(weights()), clock); ref[0] = bot;
            GameState state = branchBoard();
            assertEquals("PLACE_TRACKS 0 0", Action.format(bot.play(state, 0, 36)));
            assertTrue(bot.lastSearchAttempted); assertTrue(bot.lastSearchCutoff);
            assertFalse(bot.lastSearchComplete); assertFalse(bot.lastSearchChanged); assertFalse(bot.lastInterrupted);
            assertEquals(phase == 2 ? 1 : 0, bot.lastCandidateEvaluations);
            assertEquals(phase == 0 ? 0 : phase == 1 ? 1 : 2, bot.lastBaseSteps);
            assertEquals(0, bot.lastAlternativeSteps); assertEquals(Long.MIN_VALUE, bot.lastAlternativeScoreDelta);
            assertEquals(phase == 2 ? 0L : Long.MIN_VALUE, bot.lastBaseScoreDelta);
            assertEquals(0, state.turn); assertEquals(GameState.NO_TRACK, state.owner[0]); assertEquals(GameState.NO_TRACK, state.owner[4]);
        }
    }

    @Test void stopAfterActualPlacementKeepsThatPlacementsOriginalConditionalRunnerUp() {
        GameState state = branchBoard(); state.terrain[0] = GameState.PLAINS; state.terrain[4] = GameState.PLAINS;
        float[] weights = weights();
        weights[SpatialNet.STOP_B] = -0.5f;
        weights[SpatialNet.STOP_W + SpatialNet.CHANNELS + 1] = 3f;
        // Stage0: two tied legal cells beat STOP=-0.5. Stage1: STOP=+0.5 wins,
        // despite two paint remaining. The later STOP pass must not replace the branch.
        SpatialBot original = new SpatialBot(new Params(), new SpatialNet(weights), () -> 0);
        assertEquals("PLACE_TRACKS 0 0", Action.format(original.play(state, 0, 36)));
        SpatialV6Bot bot = bot(searchParams(), weights);
        assertEquals("PLACE_TRACKS 1 1", Action.format(bot.play(state, 0, 36)));
        assertEquals(3, bot.lastCompletedDecisions); assertEquals(1, bot.lastBasePlacements);
        assertEquals(1, bot.lastAlternativePlacements); assertEquals(0, bot.lastBranchSlot);
        assertEquals(0, bot.lastOldCell); assertEquals(4, bot.lastNewCell);
        assertArrayEquals(new int[]{0,-1,-1}, bot.lastBaseCells);
        assertArrayEquals(new int[]{4,-1,-1}, bot.lastAlternativeCells);
        assertTrue(bot.lastSearchComplete); assertTrue(bot.lastSearchChanged);
        assertEquals(-2L, bot.lastBaseScoreDelta); assertEquals(0L, bot.lastAlternativeScoreDelta);
    }

    @Test void changedPlanPreservesEarlierPlacementsAndNeverSubstitutesStopOrDifferentCost() {
        // With one paint left, the river is skipped. Only the final cheap selection
        // changes; both earlier cells and the original disruption remain fixed.
        int[] regions = new int[10]; Arrays.setAll(regions, i -> i);
        int[] terrain = new int[10]; terrain[2] = GameState.RIVER;
        GameState state = new GameState(5,2,regions,terrain,new int[]{0,4},new int[]{1,1},new int[][]{{1},{}});
        float[] weights = weights();
        SpatialV6Bot bot = bot(searchParams(), weights); bot.play(state, 0, 36);
        assertEquals(3, bot.lastBasePlacements); assertEquals(2, bot.lastBranchSlot);
        assertArrayEquals(new int[]{0,1,3}, Arrays.copyOf(bot.lastBaseCells,3));
        assertEquals(4, bot.lastNewCell); assertEquals(state.placeCost(3), state.placeCost(4));
        assertArrayEquals(new int[]{0,1,4}, Arrays.copyOf(bot.lastAlternativeCells,3));
        assertEquals(bot.lastBaseTarget, bot.lastAlternativeTarget);
        GameState onlyDifferentCost = branchBoard(); onlyDifferentCost.terrain[4] = GameState.RIVER;
        SpatialV6Bot noReplacement = bot(searchParams(), weights);
        noReplacement.play(onlyDifferentCost, 0, 36);
        assertEquals(1, noReplacement.lastPlacements); assertEquals(-1, noReplacement.lastNewCell);
        assertFalse(noReplacement.lastSearchAttempted);
        weights[SpatialNet.STOP_B] = 2;
        SpatialV6Bot stopping = bot(searchParams(), weights);
        assertEquals("WAIT", Action.format(stopping.play(branchBoard(), 0, 36)));
        assertEquals(0, stopping.lastPlacements); assertEquals(-1, stopping.lastBranchSlot); assertFalse(stopping.lastSearchAttempted);
    }

    @Test void warmupDoesNotMutateInputDiagnosticsResetAndBoundsAreChecked() {
        GameState state = branchBoard(); AtomicLong time = new AtomicLong();
        SpatialV6Bot warming = new SpatialV6Bot(searchParams(), new SpatialNet(weights()), () -> time.getAndAdd(500_000));
        warming.warmup(state, 0, 1);
        assertEquals(0, state.turn); for (int owner : state.owner) assertEquals(GameState.NO_TRACK, owner);
        SpatialV6Bot bot = bot(searchParams(), weights()); bot.play(state, 0, 36);
        assertTrue(bot.lastSearchChanged);
        assertEquals("WAIT", Action.format(bot.play(state, 0, 2)));
        assertTrue(bot.lastInterrupted); assertFalse(bot.lastSearchAttempted); assertFalse(bot.lastOwnPriorComplete); assertFalse(bot.lastOpponentComplete);
        assertEquals(0, bot.lastBaseSteps); assertEquals(0, bot.lastCandidateEvaluations); assertEquals(-1, bot.lastBranchSlot);
        assertEquals(Long.MIN_VALUE, bot.lastBaseScoreDelta); assertArrayEquals(new int[]{-1,-1,-1}, bot.lastAlternativeCells);
        for (int depth : new int[]{0,3}) {
            Params p = searchParams(); p.SPATIAL_V6_DEPTH = depth;
            assertThrows(IllegalArgumentException.class, () -> bot(p, weights()));
        }
        Params negative = searchParams(); negative.SPATIAL_V6_MIN_SEARCH_MS = -1;
        assertThrows(IllegalArgumentException.class, () -> bot(negative, weights()));
    }

    private static SpatialV6Bot bot(Params params, float[] weights) { return new SpatialV6Bot(params, new SpatialNet(weights), () -> 0); }
    private static Params searchParams() { Params p = new Params(); p.USE_SPATIAL_V6_SEARCH = true; return p; }
    private static float[] weights() {
        float[] result = new float[SpatialNet.WEIGHT_COUNT]; result[SpatialNet.NOINK_B] = 1; return result;
    }
    private static GameState branchBoard() {
        int[] terrain = new int[6]; terrain[0] = GameState.MOUNTAIN; terrain[4] = GameState.MOUNTAIN;
        GameState state = new GameState(3,2,new int[]{0,1,2,3,4,5},terrain,new int[]{0,2},new int[]{1,1},new int[][]{{1},{}});
        for (int c : new int[]{1,2}) { state.inked[c] = true; state.regionInked[c] = true; }
        Rules.updateConnections(state, new Pathing(state.size)); return state;
    }
}
