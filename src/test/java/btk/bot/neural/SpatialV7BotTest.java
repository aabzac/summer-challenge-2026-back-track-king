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

/** Synthetic mechanism fixtures only; no new generated maps, training or match measurements. */
class SpatialV7BotTest {
    @Test void searchOffMatchesV4AndAdmissionPreservesItsCompletePlan() {
        for (int me = 0; me < 2; me++) for (int turn : new int[]{0,57}) {
            GameState state = branchBoard(); state.turn = turn; state.score[me] = 173;
            SpatialBot v4 = new SpatialBot(new Params(), new SpatialNet(SpatialV4Weights.DATA), () -> 0);
            SpatialV7Bot v7 = new SpatialV7Bot(new Params(), new SpatialNet(SpatialV4Weights.DATA), () -> 0);
            assertEquals(Action.format(v4.play(state, me, 36)), Action.format(v7.play(state, me, 36)));
            assertEquals(v4.lastPlacements, v7.lastPlacements); assertEquals(v4.lastTarget, v7.lastTarget);
            assertEquals(v4.lastCompletedDecisions, v7.lastCompletedDecisions); assertEquals(v4.lastInterrupted, v7.lastInterrupted);
            assertFalse(v7.lastSearchAttempted); assertEquals(turn, state.turn);
        }
        Params insufficient = searchParams(); insufficient.SPATIAL_V7_MIN_SEARCH_MS = 1000;
        SpatialV7Bot bot = bot(insufficient, weights());
        assertEquals("PLACE_TRACKS 0 0", Action.format(bot.play(branchBoard(), 0, 36)));
        assertEquals(4, bot.lastNewCell); assertEquals(0, bot.lastRootSelected);
        assertFalse(bot.lastSearchAttempted); assertFalse(bot.lastAlternativeComplete);
    }

    @Test void sharedRootOpponentAndSimultaneousChildPlansHaveExactScoresForBothSeats() {
        for (int me = 0; me < 2; me++) {
            GameState state = branchBoard(); state.score[me] = 1000; state.score[1-me] = 75;
            GameState original = state.copy(); SpatialV7Bot bot = bot(searchParams(), weights());
            assertEquals("PLACE_TRACKS 1 1", Action.format(bot.play(state, me, 36)));
            assertTrue(bot.lastSearchComplete); assertTrue(bot.lastSearchChanged); assertTrue(bot.lastAlternativeComplete);
            assertFalse(bot.lastSearchCutoff); assertFalse(bot.lastInvalidProposal); assertFalse(bot.lastDuplicate);
            assertArrayEquals(new int[]{0,-1,-1}, bot.lastBaseCells);
            assertArrayEquals(new int[]{4,-1,-1}, bot.lastAlternativeCells);
            assertArrayEquals(new int[]{0,-1,-1}, bot.lastOpponentCells);
            assertEquals(0, bot.lastRootSelected); assertEquals(0, bot.lastOldCell); assertEquals(4, bot.lastNewCell);
            assertEquals(0, bot.lastAlternativeDecisions); assertEquals(0, bot.lastAlternativePaint);
            assertEquals(1, bot.lastPlacements); assertEquals(-1, bot.lastTarget);
            assertEquals(2, bot.lastBaseSteps); assertEquals(2, bot.lastAlternativeSteps);
            assertFalse(bot.lastBaseTerminal); assertFalse(bot.lastAlternativeTerminal);
            assertEquals(0L, bot.lastBaseScoreDelta); assertEquals(2L, bot.lastAlternativeScoreDelta);
            assertEquals(2, bot.lastCandidateEvaluations);
            // Public Rules independently resolve the fixed preturn opponent and both child choices.
            GameState base = state.copy(), alternate = state.copy(); Pathing p = new Pathing(state.size);
            List<Action> oldCell = List.of(Action.place(0,0)), newCell = List.of(Action.place(1,1)), wait = List.of(Action.waitAction());
            resolve(base, me, oldCell, oldCell, p); resolve(base, me, newCell, newCell, p);
            resolve(alternate, me, newCell, oldCell, p); resolve(alternate, me, wait, wait, p);
            assertEquals(GameState.NEUTRAL, base.owner[4]); assertEquals(me, alternate.owner[4]);
            assertEquals(bot.lastBaseScoreDelta, gap(base, me)-gap(state, me));
            assertEquals(bot.lastAlternativeScoreDelta, gap(alternate, me)-gap(state, me));
            assertStateUnchanged(original, state);
        }
    }

    @Test void conditionalSuffixUsesItsOwnPrefixRatherThanGreedyOrRootLogits() {
        for (int me = 0; me < 2; me++) {
            GameState state = wideBoard(); GameState original = state.copy();
            SpatialV7Bot bot = bot(searchParams(), prefixWeights()); bot.play(state, me, 36);
            // A pending rail boosts its right neighbour. Fresh alternative prefixes move
            // along 1->2->3; copying old/root logits would instead reselect cell0.
            assertArrayEquals(new int[]{0,1,2}, bot.lastBaseCells);
            assertArrayEquals(new int[]{1,2,3}, bot.lastAlternativeCells);
            assertEquals(1, bot.lastNewCell); assertEquals(0, bot.lastBranchSlot);
            assertEquals(2, bot.lastAlternativeDecisions); assertEquals(0, bot.lastAlternativePaint);
            assertFalse(bot.lastAlternativeStopped); assertTrue(bot.lastAlternativeComplete);
            assertFalse(bot.lastDuplicate); assertTrue(bot.lastSearchComplete);
            assertEquals(1f, bot.ownBuildPrior[0]); assertEquals(1f, bot.ownBuildPrior[3]);
            assertEquals(bot.lastBaseTarget, bot.lastAlternativeTarget);
            assertLegal(state, me, bot.lastBaseCells, bot.lastBasePlacements, bot.lastBaseTarget);
            assertLegal(state, me, bot.lastAlternativeCells, bot.lastAlternativePlacements, bot.lastAlternativeTarget);
            assertStateUnchanged(original, state);
        }
    }

    @Test void rootCanChangeCostAndCountWhileSuffixHonorsPaintAndLegalMasks() {
        for (int me = 0; me < 2; me++) {
            GameState state = branchBoard(); state.terrain[4] = GameState.PLAINS;
            state.inked[1] = false; state.regionInked[1] = false;
            SpatialV7Bot bot = bot(searchParams(), weights());
            assertEquals("PLACE_TRACKS 1 0;PLACE_TRACKS 1 1", Action.format(bot.play(state, me, 36)));
            assertArrayEquals(new int[]{0,-1,-1}, bot.lastBaseCells);
            assertArrayEquals(new int[]{1,4,-1}, bot.lastAlternativeCells);
            assertEquals(3, state.placeCost(bot.lastOldCell)); assertEquals(1, state.placeCost(bot.lastNewCell));
            assertEquals(1, bot.lastBasePlacements); assertEquals(2, bot.lastAlternativePlacements);
            assertEquals(2, bot.lastPlacements, "returned-count diagnostic follows the chosen alternative");
            assertEquals(2, bot.lastAlternativeDecisions); assertEquals(1, bot.lastAlternativePaint);
            assertTrue(bot.lastAlternativeStopped); assertTrue(bot.lastSearchChanged);
            assertEquals(3, assertLegal(state, me, bot.lastBaseCells, 1, bot.lastBaseTarget));
            assertEquals(2, assertLegal(state, me, bot.lastAlternativeCells, 2, bot.lastAlternativeTarget));
        }
    }

    @Test void originalStopCanForceABuildAndConditionalStopKeepsUnusedPaint() {
        GameState state = branchBoard(); state.inked[0] = true; state.regionInked[0] = true;
        float[] stopping = weights(); stopping[SpatialNet.STOP_B] = 2;
        SpatialV7Bot forced = bot(searchParams(), stopping);
        assertEquals("PLACE_TRACKS 1 1", Action.format(forced.play(state, 0, 36)));
        assertEquals(SpatialObservation.STOP, forced.lastRootSelected); assertEquals(-1, forced.lastOldCell);
        assertEquals(4, forced.lastNewCell); assertEquals(0, forced.lastBasePlacements);
        assertEquals(1, forced.lastAlternativePlacements); assertEquals(1, forced.lastPlacements);
        assertTrue(forced.lastSearchChanged); assertEquals(0, forced.lastOpponentPlacements);
        assertEquals(0, forced.lastAlternativeDecisions); assertEquals(0, forced.lastAlternativePaint);
        GameState cheap = branchBoard(); cheap.terrain[0] = cheap.terrain[4] = GameState.PLAINS;
        float[] stageStop = weights(); stageStop[SpatialNet.STOP_B] = -0.5f;
        stageStop[SpatialNet.STOP_W + SpatialNet.CHANNELS + 1] = 3f;
        SpatialV7Bot suffix = bot(searchParams(), stageStop); suffix.play(cheap, 0, 36);
        assertEquals(1, suffix.lastBasePlacements); assertEquals(1, suffix.lastAlternativePlacements);
        assertEquals(1, suffix.lastAlternativeDecisions); assertEquals(2, suffix.lastAlternativePaint);
        assertTrue(suffix.lastAlternativeStopped); assertTrue(suffix.lastAlternativeComplete);
        assertArrayEquals(new int[]{4,-1,-1}, suffix.lastAlternativeCells);
    }

    @Test void unorderedDuplicateSkipsWithoutThirdProposalAndTieOrNoAlternativeKeepsGreedy() {
        GameState wide = wideBoard(); float[] w = weights(); w[SpatialNet.NOINK_B] = -1;
        SpatialV7Bot duplicate = bot(searchParams(), w);
        String output = Action.format(duplicate.play(wide, 0, 36));
        assertEquals("PLACE_TRACKS 0 0;PLACE_TRACKS 1 0;PLACE_TRACKS 2 0;DISRUPT 0", output);
        assertArrayEquals(new int[]{0,1,2}, duplicate.lastBaseCells);
        assertArrayEquals(new int[]{1,0,2}, duplicate.lastAlternativeCells);
        assertEquals(0, duplicate.lastBaseTarget); assertEquals(0, duplicate.lastAlternativeTarget);
        assertTrue(duplicate.lastDuplicate); assertTrue(duplicate.lastAlternativeComplete);
        assertTrue(duplicate.lastSearchAttempted); assertFalse(duplicate.lastOpponentComplete);
        assertFalse(duplicate.lastSearchComplete); assertFalse(duplicate.lastSearchChanged);
        assertEquals(0, duplicate.lastCandidateEvaluations); assertEquals(SpatialV7Bot.NONE, duplicate.lastCutoffPhase);
        GameState tie = branchBoard(); tie.owner[4] = GameState.NEUTRAL;
        tie.inked[1] = false; tie.regionInked[1] = false; tie.terrain[1] = GameState.MOUNTAIN;
        Rules.updateConnections(tie, new Pathing(tie.size));
        SpatialV7Bot tied = bot(searchParams(), weights());
        assertEquals("PLACE_TRACKS 0 0", Action.format(tied.play(tie, 0, 36)));
        assertTrue(tied.lastSearchComplete); assertFalse(tied.lastSearchChanged);
        assertEquals(0L, tied.lastBaseScoreDelta); assertEquals(0L, tied.lastAlternativeScoreDelta);
        GameState only = branchBoard(); only.inked[4] = true; only.regionInked[4] = true;
        SpatialV7Bot noAlternative = bot(searchParams(), weights()); noAlternative.play(only, 0, 36);
        assertEquals(-1, noAlternative.lastNewCell); assertFalse(noAlternative.lastSearchAttempted);
    }

    @Test void everySearchPhaseCutoffKeepsTheWholeGreedyPlanIncludingPartialSuffixOrOneScoredBranch() {
        for (int phase : new int[]{SpatialV7Bot.ALTERNATIVE, SpatialV7Bot.OPPONENT, SpatialV7Bot.BASE, SpatialV7Bot.CANDIDATE}) {
            SpatialV7Bot[] ref = new SpatialV7Bot[1];
            LongSupplier clock = () -> ref[0] != null && ref[0].lastSearchPhase == phase ? 1_000_000_000L : 0L;
            SpatialV7Bot bot = new SpatialV7Bot(searchParams(), new SpatialNet(weights()), clock); ref[0] = bot;
            assertEquals("PLACE_TRACKS 0 0", Action.format(bot.play(branchBoard(), 0, 36)));
            assertTrue(bot.lastSearchCutoff); assertEquals(phase, bot.lastCutoffPhase);
            assertFalse(bot.lastSearchComplete); assertFalse(bot.lastSearchChanged); assertFalse(bot.lastInterrupted);
            assertFalse(bot.lastInvalidProposal); assertEquals(phase == SpatialV7Bot.CANDIDATE ? 1 : 0, bot.lastCandidateEvaluations);
            assertEquals(Long.MIN_VALUE, bot.lastAlternativeScoreDelta);
        }
        for (int phase : new int[]{SpatialV7Bot.ALTERNATIVE, SpatialV7Bot.BASE, SpatialV7Bot.CANDIDATE}) {
            SpatialV7Bot[] ref = new SpatialV7Bot[1];
            LongSupplier clock = () -> {
                SpatialV7Bot b = ref[0];
                boolean cut = b != null && (phase == SpatialV7Bot.ALTERNATIVE ? b.lastAlternativeDecisions > 0
                        : phase == SpatialV7Bot.BASE ? b.lastBaseSteps > 0 : b.lastAlternativeSteps > 0);
                return cut ? 1_000_000_000L : 0L;
            };
            boolean suffix = phase == SpatialV7Bot.ALTERNATIVE;
            GameState state = suffix ? wideBoard() : branchBoard(); GameState original = state.copy();
            float[] w = suffix ? prefixWeights() : weights();
            SpatialV7Bot bot = new SpatialV7Bot(searchParams(), new SpatialNet(w), clock); ref[0] = bot;
            String greedy = Action.format(new SpatialBot(new Params(), new SpatialNet(w), () -> 0).play(state, 0, 36));
            assertEquals(greedy, Action.format(bot.play(state, 0, 36)));
            assertTrue(bot.lastSearchCutoff); assertEquals(phase, bot.lastCutoffPhase); assertFalse(bot.lastSearchChanged);
            if (suffix) { assertEquals(2, bot.lastAlternativePlacements); assertFalse(bot.lastAlternativeComplete); }
            assertStateUnchanged(original, state);
        }
    }

    @Test void terminalAndDepthOneScoreOnlyActualTurnsAndNeverUseTheLearnedValue() {
        for (boolean terminal : new boolean[]{true,false}) {
            Params p = searchParams(); if (!terminal) p.SPATIAL_V7_DEPTH = 1;
            GameState state = branchBoard(); if (terminal) state.maxTurns = 1; state.score[0] = 1_000_000;
            float[] w = weights(); w[SpatialNet.VALUE_W + 13] = Float.MAX_VALUE;
            SpatialNet net = new SpatialNet(w); SpatialV7Bot bot = new SpatialV7Bot(p, net, () -> 0);
            assertEquals("PLACE_TRACKS 1 1", Action.format(bot.play(state, 0, 36)));
            assertEquals(1, bot.lastBaseSteps); assertEquals(1, bot.lastAlternativeSteps);
            assertEquals(terminal, bot.lastBaseTerminal); assertEquals(terminal, bot.lastAlternativeTerminal);
            assertEquals(0L, bot.lastBaseScoreDelta); assertEquals(1L, bot.lastAlternativeScoreDelta);
            assertFalse(Float.isFinite(net.value)); assertTrue(bot.lastSearchChanged);
        }
    }

    @Test void nonfiniteConditionalProposalCannotReplaceTheCompletedGreedyPlan() {
        float[] w = prefixWeights(); w[SpatialNet.STEM_W + 13*9 + 3] = Float.MAX_VALUE;
        w[SpatialNet.BUILD_W] = 2;
        GameState state = wideBoard();
        String baseline = Action.format(new SpatialBot(new Params(), new SpatialNet(w), () -> 0).play(state, 0, 36));
        SpatialV7Bot bot = bot(searchParams(), w);
        assertEquals(baseline, Action.format(bot.play(state, 0, 36)));
        assertTrue(bot.lastSearchAttempted); assertTrue(bot.lastInvalidProposal);
        assertFalse(bot.lastSearchChanged); assertFalse(bot.lastSearchComplete); assertFalse(bot.lastAlternativeComplete);
        assertFalse(bot.lastSearchCutoff); assertEquals(SpatialV7Bot.ALTERNATIVE, bot.lastSearchPhase);
    }

    @Test void warmupAndResetPreserveInputAndRejectInvalidBounds() {
        GameState state = branchBoard(); GameState original = state.copy(); AtomicLong time = new AtomicLong();
        SpatialV7Bot warming = new SpatialV7Bot(searchParams(), new SpatialNet(weights()), () -> time.getAndAdd(500_000));
        warming.warmup(state, 0, 1); assertStateUnchanged(original, state);
        SpatialV7Bot bot = bot(searchParams(), weights()); bot.play(state, 0, 36); assertTrue(bot.lastSearchChanged);
        assertEquals("WAIT", Action.format(bot.play(state, 0, 2)));
        assertTrue(bot.lastInterrupted); assertFalse(bot.lastSearchAttempted); assertFalse(bot.lastAlternativeComplete);
        assertFalse(bot.lastDuplicate); assertFalse(bot.lastOwnPriorComplete); assertFalse(bot.lastOpponentComplete);
        assertEquals(-1, bot.lastRootSelected); assertEquals(0, bot.lastAlternativeDecisions);
        assertEquals(SpatialV7Bot.NONE, bot.lastCutoffPhase); assertEquals(Long.MIN_VALUE, bot.lastAlternativeScoreDelta);
        assertArrayEquals(new int[]{-1,-1,-1}, bot.lastAlternativeCells);
        for (int depth : new int[]{0,3}) {
            Params p = searchParams(); p.SPATIAL_V7_DEPTH = depth;
            assertThrows(IllegalArgumentException.class, () -> bot(p, weights()));
        }
        Params p = searchParams(); p.SPATIAL_V7_MIN_SEARCH_MS = -1;
        assertThrows(IllegalArgumentException.class, () -> bot(p, weights()));
    }

    private static SpatialV7Bot bot(Params p, float[] w) { return new SpatialV7Bot(p, new SpatialNet(w), () -> 0); }
    private static Params searchParams() { Params p = new Params(); p.USE_SPATIAL_V7_SEARCH = true; return p; }
    private static float[] weights() { float[] w = new float[SpatialNet.WEIGHT_COUNT]; w[SpatialNet.NOINK_B] = 1; return w; }
    private static float[] prefixWeights() {
        float[] w = weights(); w[SpatialNet.STEM_B] = 1; w[SpatialNet.STEM_W + 13*9 + 3] = 10;
        w[SpatialNet.BUILD_W] = 1; w[SpatialNet.STOP_B] = -1; return w;
    }
    private static GameState branchBoard() {
        int[] terrain = new int[6]; terrain[0] = terrain[4] = GameState.MOUNTAIN;
        GameState state = new GameState(3,2,new int[]{0,1,2,3,4,5},terrain,new int[]{0,2},new int[]{1,1},new int[][]{{1},{}});
        for (int c : new int[]{1,2}) { state.inked[c] = true; state.regionInked[c] = true; }
        Rules.updateConnections(state, new Pathing(state.size)); return state;
    }
    private static GameState wideBoard() {
        int[] regions = new int[12]; Arrays.setAll(regions, i -> i);
        GameState state = new GameState(6,2,regions,new int[12],new int[]{0,5},new int[]{1,1},new int[][]{{1},{}});
        for (int c = 7; c < 11; c++) { state.inked[c] = true; state.regionInked[c] = true; }
        Rules.updateConnections(state, new Pathing(state.size)); return state;
    }
    private static int assertLegal(GameState s, int me, int[] cells, int count, int target) {
        java.util.ArrayList<Action> actions = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) actions.add(Action.place(s.x(cells[i]), s.y(cells[i])));
        if (target >= 0) actions.add(Action.disrupt(target));
        Rules.TurnResult r = new Rules.TurnResult(); Rules.expand(s, me, actions, new Pathing(s.size), r.moves[me], r);
        assertNull(r.moves[me].error); assertTrue(r.moves[me].skipped.isEmpty());
        assertEquals(count, r.moves[me].count); assertTrue(r.moves[me].cost <= 3); return r.moves[me].cost;
    }
    private static long gap(GameState s, int me) { return (long) s.score[me] - s.score[1-me]; }
    private static void resolve(GameState s, int me, List<Action> own, List<Action> opponent, Pathing p) {
        if (me == 0) Rules.resolve(s, own, opponent, p); else Rules.resolve(s, opponent, own, p);
    }
    private static void assertStateUnchanged(GameState before, GameState after) {
        assertEquals(before.turn, after.turn); assertArrayEquals(before.owner, after.owner);
        assertArrayEquals(before.score, after.score); assertArrayEquals(before.inked, after.inked);
        assertArrayEquals(before.instability, after.instability); assertArrayEquals(before.regionInstability, after.regionInstability);
        assertArrayEquals(before.regionInked, after.regionInked); assertArrayEquals(before.pairPathLen, after.pairPathLen);
        for (int i = 0; i < before.pairCount; i++) assertArrayEquals(before.pairPath[i], after.pairPath[i]);
    }
}
