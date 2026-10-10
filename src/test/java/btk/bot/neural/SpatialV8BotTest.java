package btk.bot.neural;

import btk.bot.Params;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.core.SearchState;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic core fixtures only. No new maps, teacher data or match measurements. */
class SpatialV8BotTest {
    @Test void searchOffMatchesV4AndDepthTwoMatchesV7ForBothSeatsAndConditionalPlans() {
        for (int me = 0; me < 2; me++) for (int kind = 0; kind < 5; kind++) {
            GameState state = kind == 0 ? branchBoard() : kind == 1 ? delayedBoard() : wideBoard();
            float[] w = kind == 3 ? prefixWeights() : weights();
            if (kind == 4) w[SpatialNet.STOP_B] = 2;
            GameState original = state.copy();
            SpatialBot frozen = new SpatialBot(new Params(), new SpatialNet(SpatialV4Weights.DATA), () -> 0);
            SpatialV8Bot off = new SpatialV8Bot(new Params(), new SpatialNet(SpatialV4Weights.DATA), () -> 0);
            assertEquals(Action.format(frozen.play(state, me, 36)), Action.format(off.play(state, me, 36)));
            assertEquals(frozen.lastCompletedDecisions, off.lastCompletedDecisions);
            assertFalse(off.lastSearchAttempted); assertEquals(0, off.lastCommittedHorizon);
            Params p7 = new Params(); p7.USE_SPATIAL_V7_SEARCH = true;
            SpatialV7Bot v7 = new SpatialV7Bot(p7, new SpatialNet(w), () -> 0);
            SpatialV8Bot v8 = bot(searchParams(2), w);
            assertEquals(Action.format(v7.play(state, me, 36)), Action.format(v8.play(state, me, 36)));
            assertArrayEquals(v7.lastBaseCells, v8.lastBaseCells); assertArrayEquals(v7.lastAlternativeCells, v8.lastAlternativeCells);
            assertEquals(v7.lastBasePlacements, v8.lastBasePlacements); assertEquals(v7.lastAlternativePlacements, v8.lastAlternativePlacements);
            assertEquals(v7.lastDuplicate, v8.lastDuplicate); assertEquals(v7.lastSearchComplete, v8.lastSearchComplete);
            assertEquals(v7.lastBaseScoreDelta, v8.lastBaseScoreDelta); assertEquals(v7.lastAlternativeScoreDelta, v8.lastAlternativeScoreDelta);
            assertEquals(v7.lastBaseSteps, v8.lastBaseSteps); assertEquals(v7.lastAlternativeSteps, v8.lastAlternativeSteps);
            assertEquals(v7.lastBaseTerminal, v8.lastBaseTerminal); assertEquals(v7.lastAlternativeTerminal, v8.lastAlternativeTerminal);
            assertStateUnchanged(original, state);
        }
    }

    @Test void delayedPayoffReversesPreferenceAndCheckpointsNeverAddTheirReturnsTogether() {
        for (int me = 0; me < 2; me++) {
            GameState root = delayedBoard(); root.score[me] = 1000; root.score[1-me] = 75;
            GameState original = root.copy(); SpatialV8Bot shallow = bot(searchParams(2), weights());
            assertEquals("PLACE_TRACKS 5 0", Action.format(shallow.play(root, me, 36)));
            assertEquals(2L, shallow.lastAlternativeScoreDelta); assertTrue(shallow.lastSearchChanged);
            SpatialV8Bot deep = bot(searchParams(8), weights());
            assertEquals("PLACE_TRACKS 1 0", Action.format(deep.play(root, me, 36)));
            assertTrue(deep.lastSearchComplete); assertTrue(deep.lastReachedEnd); assertFalse(deep.lastSearchChanged);
            assertEquals(8, deep.lastCommittedHorizon); assertEquals(4, deep.lastCheckpointCount);
            assertArrayEquals(new int[]{2,4,6,8}, deep.checkpointHorizon);
            assertArrayEquals(new long[]{0,0,0,0}, deep.baseCheckpointScoreDelta);
            assertArrayEquals(new long[]{2,-4,-10,-16}, deep.alternativeCheckpointScoreDelta);
            assertEquals(-16L, deep.lastAlternativeScoreDelta); assertEquals(0L, deep.lastBaseScoreDelta);
            assertEquals(8, deep.lastBaseWorkSteps); assertEquals(8, deep.lastAlternativeWorkSteps);
            assertEquals(8, deep.lastCandidateEvaluations);
            assertArrayEquals(new int[]{1,-1,-1}, deep.lastOpponentCells);
            // Independent regular-core histories: both sides pack the same current free
            // cell before applying either plan, so every continuation rail is neutral.
            GameState base = root.copy(), alternate = root.copy(); Pathing p = new Pathing(root.size);
            int[] baseRoute = {1,5,8,15,-1,-1,-1,-1};
            int[] alternativeRoute = {5,8,15,-1,-1,-1,-1,-1};
            for (int turn = 0; turn < 8; turn++) {
                resolve(base, me, plan(root, baseRoute[turn]), plan(root, baseRoute[turn]), p);
                resolve(alternate, me, plan(root, alternativeRoute[turn]), plan(root, turn == 0 ? 1 : alternativeRoute[turn]), p);
                if (turn % 2 == 1) {
                    int at = turn / 2;
                    assertEquals(deep.baseCheckpointScoreDelta[at], gap(base, me)-gap(root, me));
                    assertEquals(deep.alternativeCheckpointScoreDelta[at], gap(alternate, me)-gap(root, me));
                    assertEquals(turn+1, deep.baseCheckpointSteps[at]); assertEquals(turn+1, deep.alternativeCheckpointSteps[at]);
                    assertFalse(deep.baseCheckpointTerminal[at]); assertFalse(deep.alternativeCheckpointTerminal[at]);
                }
            }
            assertEquals(GameState.NEUTRAL, base.owner[1]); assertEquals(1-me, alternate.owner[1]);
            assertEquals(me, alternate.owner[5]); assertStateUnchanged(original, root);
        }
    }

    @Test void cutoffAfterPartialDeeperWorkRetainsOnlyTheLastPairedDecision() {
        for (int nextTurn : new int[]{3,5}) for (boolean cutAfterBase : new boolean[]{true,false}) {
            SpatialV8Bot[] ref = new SpatialV8Bot[1];
            LongSupplier clock = () -> {
                SpatialV8Bot b = ref[0];
                boolean cut = b != null && b.lastBaseWorkSteps >= nextTurn
                        && (cutAfterBase || b.lastSearchPhase == SpatialV8Bot.CANDIDATE);
                return cut ? 1_000_000_000L : 0L;
            };
            SpatialV8Bot bot = new SpatialV8Bot(searchParams(8), new SpatialNet(weights()), clock); ref[0] = bot;
            GameState root = delayedBoard(); GameState original = root.copy();
            assertEquals(nextTurn == 3 ? "PLACE_TRACKS 5 0" : "PLACE_TRACKS 1 0", Action.format(bot.play(root, 0, 36)));
            assertTrue(bot.lastSearchCutoff); assertTrue(bot.lastSearchComplete); assertFalse(bot.lastReachedEnd);
            assertEquals(cutAfterBase ? SpatialV8Bot.BASE : SpatialV8Bot.CANDIDATE, bot.lastCutoffPhase);
            assertEquals(nextTurn-1, bot.lastCommittedHorizon); assertEquals(nextTurn, bot.lastAttemptedHorizon);
            assertEquals(nextTurn, bot.lastBaseWorkSteps); assertEquals(nextTurn-1, bot.lastAlternativeWorkSteps);
            assertEquals(nextTurn-1, bot.lastBaseSteps); assertEquals(nextTurn-1, bot.lastAlternativeSteps);
            assertEquals(nextTurn == 3 ? 2L : -4L, bot.lastAlternativeScoreDelta);
            assertEquals(0L, bot.lastBaseScoreDelta); assertEquals(nextTurn == 3, bot.lastSearchChanged);
            assertStateUnchanged(original, root);
        }
    }

    @Test void cutoffBeforeFirstPublishedPairNeverUsesOneSidedOrOddDepthScores() {
        for (int phase : new int[]{SpatialV8Bot.ALTERNATIVE, SpatialV8Bot.OPPONENT, SpatialV8Bot.BASE, SpatialV8Bot.CANDIDATE}) {
            SpatialV8Bot[] ref = new SpatialV8Bot[1];
            LongSupplier clock = () -> {
                SpatialV8Bot b = ref[0];
                boolean cut = b != null && (phase <= SpatialV8Bot.OPPONENT ? b.lastSearchPhase == phase
                        : b.lastSearchPhase == phase && b.lastAttemptedHorizon == 2);
                return cut ? 1_000_000_000L : 0L;
            };
            SpatialV8Bot bot = new SpatialV8Bot(searchParams(8), new SpatialNet(weights()), clock); ref[0] = bot;
            assertEquals("PLACE_TRACKS 1 0", Action.format(bot.play(delayedBoard(), 0, 36)));
            assertTrue(bot.lastSearchCutoff); assertFalse(bot.lastSearchComplete); assertFalse(bot.lastSearchChanged);
            assertEquals(phase, bot.lastCutoffPhase); assertEquals(0, bot.lastCheckpointCount);
            assertEquals(0, bot.lastCommittedHorizon); assertEquals(0, bot.lastBaseSteps); assertEquals(0, bot.lastAlternativeSteps);
            assertEquals(Long.MIN_VALUE, bot.lastBaseScoreDelta); assertEquals(Long.MIN_VALUE, bot.lastAlternativeScoreDelta);
        }
    }

    @Test void bothTerminalAtOddTurnsPublishesOnceAndDoesNotAwardFurtherIncome() {
        for (int limit : new int[]{1,3}) {
            GameState state = delayedBoard(); state.maxTurns = limit;
            SpatialV8Bot bot = bot(searchParams(8), weights()); bot.play(state, 0, 36);
            assertEquals(limit, bot.lastCommittedHorizon); assertEquals(limit, bot.lastAttemptedHorizon);
            assertTrue(bot.lastReachedEnd); assertTrue(bot.lastBaseTerminal); assertTrue(bot.lastAlternativeTerminal);
            assertEquals(limit, bot.lastBaseWorkSteps); assertEquals(limit, bot.lastAlternativeWorkSteps);
            assertEquals(limit == 1 ? 1 : 2, bot.lastCheckpointCount);
            assertArrayEquals(limit == 1 ? new int[]{1} : new int[]{2,3}, Arrays.copyOf(bot.checkpointHorizon, bot.lastCheckpointCount));
            assertEquals(limit == 1 ? 1L : -1L, bot.lastAlternativeScoreDelta);
            assertEquals(0L, bot.lastBaseScoreDelta); assertEquals(limit == 1, bot.lastSearchChanged);
            assertEquals(0, state.turn);
        }
    }

    @Test void syntheticAsymmetricTerminalIsAbsorbingWhileTheOtherBranchAdvances() {
        // Fixed V8 disruptions produce equal ink histories, so natural terminals match.
        // This test-only fault injection exercises generic absorbing bookkeeping, not
        // a claimed reachable policy trajectory: give the two private states different limits.
        SpatialV8Bot[] ref = new SpatialV8Bot[1]; AtomicBoolean injected = new AtomicBoolean();
        LongSupplier clock = () -> {
            SpatialV8Bot b = ref[0];
            if (b != null && b.lastAttemptedHorizon == 1 && injected.compareAndSet(false, true)) {
                searchState(b, "baseSimulation").state.maxTurns = 1;
                searchState(b, "alternativeSimulation").state.maxTurns = 5;
            }
            return 0;
        };
        SpatialV8Bot bot = new SpatialV8Bot(searchParams(8), new SpatialNet(weights()), clock); ref[0] = bot;
        GameState root = branchBoard(); GameState original = root.copy();
        assertEquals("PLACE_TRACKS 1 1", Action.format(bot.play(root, 0, 36)));
        assertTrue(injected.get()); assertTrue(bot.lastReachedEnd); assertEquals(3, bot.lastCheckpointCount);
        assertArrayEquals(new int[]{2,4,5}, Arrays.copyOf(bot.checkpointHorizon, 3));
        assertArrayEquals(new int[]{1,1,1}, Arrays.copyOf(bot.baseCheckpointSteps, 3));
        assertArrayEquals(new int[]{2,4,5}, Arrays.copyOf(bot.alternativeCheckpointSteps, 3));
        assertArrayEquals(new long[]{0,0,0}, Arrays.copyOf(bot.baseCheckpointScoreDelta, 3));
        assertArrayEquals(new long[]{2,4,5}, Arrays.copyOf(bot.alternativeCheckpointScoreDelta, 3));
        assertArrayEquals(new boolean[]{true,true,true}, Arrays.copyOf(bot.baseCheckpointTerminal, 3));
        assertArrayEquals(new boolean[]{false,false,true}, Arrays.copyOf(bot.alternativeCheckpointTerminal, 3));
        assertEquals(1, bot.lastBaseWorkSteps); assertEquals(5, bot.lastAlternativeWorkSteps);
        assertStateUnchanged(original, root);
    }

    @Test void invalidLaterContinuationDiscardsEvenAPreviouslyWinningCheckpoint() {
        SpatialV8Bot[] ref = new SpatialV8Bot[1]; AtomicBoolean injected = new AtomicBoolean();
        LongSupplier clock = () -> {
            SpatialV8Bot b = ref[0];
            // Deliberate buffer corruption tests the rejection contract; real frozen priors do not change.
            if (b != null && b.lastCheckpointCount == 1 && injected.compareAndSet(false, true))
                b.ownBuildPrior[SpatialObservation.STOP] = Float.NaN;
            return 0;
        };
        SpatialV8Bot bot = new SpatialV8Bot(searchParams(8), new SpatialNet(weights()), clock); ref[0] = bot;
        assertEquals("PLACE_TRACKS 1 0", Action.format(bot.play(delayedBoard(), 0, 36)));
        assertTrue(injected.get()); assertTrue(bot.lastInvalidProposal); assertFalse(bot.lastSearchCutoff);
        assertTrue(bot.lastSearchComplete); assertFalse(bot.lastSearchChanged); assertFalse(bot.lastReachedEnd);
        assertEquals(2, bot.lastCommittedHorizon); assertEquals(2L, bot.lastAlternativeScoreDelta);
        assertEquals(0L, bot.lastBaseScoreDelta); assertEquals(1, bot.lastCheckpointCount);
        assertEquals(2, bot.lastBaseWorkSteps); assertEquals(2, bot.lastAlternativeWorkSteps);
    }

    @Test void tiesDuplicateProposalsAdmissionWarmupAndResetKeepTheirContracts() {
        GameState tied = branchBoard(); tied.owner[4] = GameState.NEUTRAL;
        tied.inked[1] = false; tied.regionInked[1] = false; tied.terrain[1] = GameState.MOUNTAIN;
        Rules.updateConnections(tied, new Pathing(tied.size)); SpatialV8Bot tie = bot(searchParams(8), weights());
        assertEquals("PLACE_TRACKS 0 0", Action.format(tie.play(tied, 0, 36)));
        assertEquals(4, tie.lastCheckpointCount); assertTrue(tie.lastReachedEnd); assertFalse(tie.lastSearchChanged);
        assertArrayEquals(new long[]{0,0,0,0}, tie.baseCheckpointScoreDelta);
        assertArrayEquals(new long[]{0,0,0,0}, tie.alternativeCheckpointScoreDelta);
        SpatialV8Bot duplicate = bot(searchParams(8), weights()); duplicate.play(wideBoard(), 0, 36);
        assertTrue(duplicate.lastDuplicate); assertFalse(duplicate.lastSearchComplete); assertEquals(0, duplicate.lastCheckpointCount);
        Params admission = searchParams(8); admission.SPATIAL_V8_MIN_SEARCH_MS = 1000;
        SpatialV8Bot skipped = bot(admission, weights()); skipped.play(branchBoard(), 0, 36);
        assertFalse(skipped.lastSearchAttempted); assertEquals(0, skipped.lastBaseWorkSteps);
        GameState root = branchBoard(); GameState original = root.copy(); AtomicLong time = new AtomicLong();
        SpatialV8Bot warming = new SpatialV8Bot(searchParams(8), new SpatialNet(weights()), () -> time.getAndAdd(500_000));
        warming.warmup(root, 0, 1); assertStateUnchanged(original, root);
        SpatialV8Bot bot = bot(searchParams(8), weights()); bot.play(root, 0, 36); assertEquals(4, bot.lastCheckpointCount);
        assertEquals("WAIT", Action.format(bot.play(root, 0, 2)));
        assertTrue(bot.lastInterrupted); assertFalse(bot.lastSearchComplete); assertFalse(bot.lastReachedEnd);
        assertEquals(0, bot.lastCheckpointCount); assertEquals(0, bot.lastCommittedHorizon); assertEquals(0, bot.lastAttemptedHorizon);
        assertEquals(0, bot.lastBaseWorkSteps); assertEquals(0, bot.lastAlternativeWorkSteps);
        assertEquals(Long.MIN_VALUE, bot.lastBaseScoreDelta); assertEquals(Long.MIN_VALUE, bot.lastAlternativeScoreDelta);
        for (int depth : new int[]{0,1,3,5,7,9,10})
            assertThrows(IllegalArgumentException.class, () -> bot(searchParams(depth), weights()));
        for (int depth : new int[]{2,4,6,8}) assertDoesNotThrow(() -> bot(searchParams(depth), weights()));
    }

    private static SpatialV8Bot bot(Params p, float[] w) { return new SpatialV8Bot(p, new SpatialNet(w), () -> 0); }
    private static Params searchParams(int depth) { Params p = new Params(); p.USE_SPATIAL_V8_SEARCH = true; p.SPATIAL_V8_DEPTH = depth; return p; }
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
    private static GameState delayedBoard() {
        // A=1, B=5, C=8, D=15. B joins one short route immediately. A joins
        // four directed long routes only after C/D arrive, reversing the earlier gain.
        int[] regions = new int[28], terrain = new int[28]; Arrays.setAll(regions, i -> i);
        Arrays.fill(terrain, GameState.MOUNTAIN);
        GameState s = new GameState(7,4,regions,terrain,new int[]{0,0,2,4,6},new int[]{0,3,3,0,0},
                new int[][]{{1,2},{0},{0},{4},{}});
        Arrays.fill(s.inked, true); Arrays.fill(s.regionInked, true);
        for (int cell : new int[]{0,1,4,5,6,8,15,21,22,23}) { s.inked[cell] = false; s.regionInked[cell] = false; }
        s.owner[22] = GameState.NEUTRAL;
        Rules.updateConnections(s, new Pathing(s.size)); return s;
    }
    private static GameState wideBoard() {
        int[] regions = new int[12]; Arrays.setAll(regions, i -> i);
        GameState s = new GameState(6,2,regions,new int[12],new int[]{0,5},new int[]{1,1},new int[][]{{1},{}});
        for (int c = 7; c < 11; c++) { s.inked[c] = true; s.regionInked[c] = true; }
        Rules.updateConnections(s, new Pathing(s.size)); return s;
    }
    private static SearchState searchState(SpatialV8Bot bot, String field) {
        try { Field f = SpatialV8Bot.class.getDeclaredField(field); f.setAccessible(true); return (SearchState) f.get(bot); }
        catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }
    private static List<Action> plan(GameState s, int cell) {
        return List.of(cell < 0 ? Action.waitAction() : Action.place(s.x(cell), s.y(cell)));
    }
    private static void resolve(GameState s, int me, List<Action> own, List<Action> enemy, Pathing p) {
        if (me == 0) Rules.resolve(s, own, enemy, p); else Rules.resolve(s, enemy, own, p);
    }
    private static long gap(GameState s, int me) { return (long) s.score[me] - s.score[1-me]; }
    private static void assertStateUnchanged(GameState a, GameState b) {
        assertEquals(a.turn, b.turn); assertEquals(a.maxTurns, b.maxTurns); assertArrayEquals(a.score, b.score);
        assertArrayEquals(a.owner, b.owner); assertArrayEquals(a.inked, b.inked); assertArrayEquals(a.instability, b.instability);
        assertArrayEquals(a.regionInked, b.regionInked); assertArrayEquals(a.regionInstability, b.regionInstability);
        assertArrayEquals(a.pairPathLen, b.pairPathLen);
        for (int i = 0; i < a.pairCount; i++) assertArrayEquals(a.pairPath[i], b.pairPath[i]);
    }
}
