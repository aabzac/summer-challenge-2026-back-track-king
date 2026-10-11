package btk.tools.target;

import btk.bot.BotFactory;
import btk.bot.Params;
import btk.bot.neural.NeuralNet;
import btk.bot.neural.NeuralV2Weights;
import btk.bot.neural.NeuralV3Bot;
import btk.bot.neural.NeuralV3Features;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TargetProposalFeaturesTest {
    private static final TargetProposalFeatures.Deadline NEVER = () -> false;

    private static final class Counting implements TargetProposalFeatures.Deadline {
        final int limit;
        int calls;
        Counting(int limit) { this.limit = limit; }
        public boolean expired() { return ++calls >= limit; }
    }

    /** Mixed active rails, terrain costs, neutral rails and incomplete pairs in both directions. */
    private static GameState rich() {
        int[] regions = new int[45], terrain = new int[45];
        for (int c = 0; c < regions.length; c++) { regions[c] = c % 9; terrain[c] = c % 7 == 0 ? 2 : c % 4 == 0 ? 1 : 0; }
        GameState s = new GameState(9, 5, regions, terrain, new int[]{0, 8, 0, 8},
                new int[]{1, 1, 3, 3}, new int[][]{{1, 3}, {2}, {3}, {0}});
        for (int x = 1; x < 8; x++) s.owner[s.cell(x, 1)] = x % 2;
        for (int x = 2; x < 5; x++) s.owner[s.cell(x, 3)] = x % 2;
        s.owner[s.cell(4, 1)] = GameState.NEUTRAL;
        Rules.disrupt(s, 3); Rules.disrupt(s, 3);
        Rules.disrupt(s, 6); Rules.disrupt(s, 6); Rules.disrupt(s, 6);
        s.turn = 12; s.score[0] = 80; s.score[1] = 91;
        Rules.updateConnections(s, new Pathing(s.size));
        assertTrue(s.pairActive(0));
        assertTrue(Arrays.stream(s.pairPathLen).anyMatch(n -> n == 0));
        return s;
    }

    private static GameState closing() {
        GameState s = new GameState(5, 1, new int[]{0, 1, 2, 3, 4}, new int[5],
                new int[]{0, 4}, new int[]{0, 0}, new int[][]{{1}, {0}});
        s.owner[1] = 0; s.owner[2] = 1; s.owner[3] = 0;
        for (int r = 1; r <= 3; r++) for (int i = 0; i < 3; i++) Rules.disrupt(s, r);
        s.turn = 30; s.score[0] = 100; s.score[1] = 80;
        Rules.updateConnections(s, new Pathing(s.size));
        return s;
    }

    private static void unchanged(GameState expected, GameState actual) {
        assertEquals(expected.turn, actual.turn); assertEquals(expected.maxTurns, actual.maxTurns);
        assertArrayEquals(expected.score, actual.score); assertArrayEquals(expected.owner, actual.owner);
        assertArrayEquals(expected.instability, actual.instability); assertArrayEquals(expected.inked, actual.inked);
        assertArrayEquals(expected.regionInstability, actual.regionInstability);
        assertArrayEquals(expected.regionInked, actual.regionInked); assertArrayEquals(expected.pairPathLen, actual.pairPathLen);
        for (int p = 0; p < actual.pairCount; p++) assertArrayEquals(expected.pairPath[p], actual.pairPath[p]);
    }

    private static final class Fixture {
        final GameState s;
        final int me;
        final TargetProposalFeatures candidate;
        final NeuralV3Features reference;
        final int[] ids, expectedIds;
        final float[][] rows, expectedRows;
        Fixture(GameState s, int me) {
            this.s = s; this.me = me;
            candidate = new TargetProposalFeatures(s); reference = new NeuralV3Features(s);
            ids = new int[s.regionCount]; expectedIds = new int[s.regionCount];
            rows = new float[s.regionCount][29]; expectedRows = new float[s.regionCount][29];
            observe();
        }
        void observe() { candidate.observe(s, me); reference.observe(s, me); }
        int compare() {
            GameState before = s.copy();
            assertTrue(reference.prepare(s, me, true, null));
            assertTrue(candidate.prepare(s, me, NEVER));
            int n = reference.sabotageCandidates(s, me, expectedIds, expectedRows, null);
            assertEquals(n, candidate.candidates(s, me, ids, rows, NEVER));
            assertTrue(candidate.prepared); assertTrue(candidate.candidatesComplete);
            assertArrayEquals(reference.closes, candidate.closes);
            assertArrayEquals(reference.winsIfClosed, candidate.winsIfClosed);
            for (int i = 0; i < n; i++) {
                assertEquals(expectedIds[i], ids[i]);
                for (int j = 0; j < 29; j++) assertEquals(Float.floatToRawIntBits(expectedRows[i][j]),
                        Float.floatToRawIntBits(rows[i][j]), "row=" + i + " feature=" + j);
            }
            unchanged(before, s);
            return n;
        }
    }

    @Test void exactRowsAndClosingFlagsMatchAcrossBothSeatsAndDisconnectedBoards() {
        assertArrayEquals(NeuralV3Features.SABOTAGE_NAMES, TargetProposalFeatures.SABOTAGE_NAMES);
        for (int me = 0; me < 2; me++) {
            Fixture f = new Fixture(rich(), me);
            assertTrue(f.compare() > 3);
            // Region-wide ink disconnects pairs and makes reusable scratch paths disappear/reappear.
            Rules.ink(f.s, 4); Rules.updateConnections(f.s, new Pathing(f.s.size));
            assertTrue(f.compare() > 2);
            new Fixture(closing(), me).compare();
        }
    }

    @Test void publicHistoryUsesActualFallbackAndObservationIsIdempotent() {
        GameState s = rich();
        Arrays.fill(s.instability, 0); Arrays.fill(s.regionInstability, 0);
        TargetProposer proposal = new TargetProposer(s, true);
        NeuralV3Features reference = new NeuralV3Features(s);
        reference.observe(s, 0);
        int suggested = proposal.propose(s, 0, NEVER);
        assertTrue(suggested >= 0);
        int actual = suggested == 1 ? 2 : 1;
        proposal.recordTarget(actual); reference.recordTarget(actual);
        int recentCell = s.cell(actual, 0);
        assertTrue(s.canBuild(recentCell)); s.owner[recentCell] = 0;
        Rules.disrupt(s, actual); Rules.disrupt(s, suggested); s.turn++;
        Rules.updateConnections(s, new Pathing(s.size));
        proposal.observe(s, 0); proposal.observe(s, 0); reference.observe(s, 0); reference.observe(s, 0);
        assertTrue(reference.prepare(s, 0, true, null));
        int[] ids = new int[s.regionCount]; float[][] rows = new float[s.regionCount][29];
        int n = reference.sabotageCandidates(s, 0, ids, rows, null);
        assertNotEquals(TargetProposer.ABORTED, proposal.propose(s, 0, NEVER));
        assertEquals(n, proposal.lastCount);
        for (int i = 0; i < n; i++) {
            assertEquals(ids[i], proposal.regions[i]); assertArrayEquals(rows[i], proposal.rows[i]);
            if (ids[i] == actual) { assertEquals(1f, rows[i][20]); assertEquals(1f, rows[i][11]); }
            if (ids[i] == suggested) { assertEquals(0f, rows[i][20]); assertEquals(1f, rows[i][21]); }
        }
    }

    @Test void everyPrepareCutoffDiscardsScratchAndRetryRecoversWithoutInputMutation() {
        Fixture f = new Fixture(rich(), 1);
        Counting count = new Counting(Integer.MAX_VALUE);
        assertTrue(f.candidate.prepare(f.s, f.me, count)); assertTrue(count.calls > 5);
        GameState before = f.s.copy();
        for (int i = 1; i <= count.calls; i++) {
            Counting stop = new Counting(i);
            assertFalse(f.candidate.prepare(f.s, f.me, stop), "cutoff=" + i);
            assertEquals(i, stop.calls); assertFalse(f.candidate.prepared);
            assertFalse(f.candidate.candidatesComplete);
            assertThrows(IllegalStateException.class, () -> f.candidate.candidates(f.s, f.me, f.ids, f.rows, NEVER));
            unchanged(before, f.s); f.compare();
        }
    }

    @Test void everyCandidateCutoffDiscardsPartialRowsAndRetryRecovers() {
        Fixture f = new Fixture(rich(), 0); f.compare();
        Counting count = new Counting(Integer.MAX_VALUE);
        assertTrue(f.candidate.candidates(f.s, 0, f.ids, f.rows, count) > 0);
        assertTrue(count.calls > f.s.regionCount);
        GameState before = f.s.copy();
        for (int i = 1; i <= count.calls; i++) {
            Counting stop = new Counting(i);
            assertEquals(-1, f.candidate.candidates(f.s, 0, f.ids, f.rows, stop));
            assertEquals(i, stop.calls); assertFalse(f.candidate.candidatesComplete);
            unchanged(before, f.s); f.compare();
        }
        Arrays.fill(f.ids, -123); for (float[] row : f.rows) Arrays.fill(row, -321f);
        assertEquals(-1, f.candidate.candidates(f.s, 0, f.ids, f.rows, () -> true));
        for (int id : f.ids) assertEquals(-123, id);
        for (float[] row : f.rows) for (float value : row) assertEquals(-321f, value);
    }

    @Test void deterministicProposalAndAvailableScoresMatchFrozenV3WithZeroNoise() {
        for (boolean close : new boolean[]{false, true}) for (int me = 0; me < 2; me++)
            for (GameState state : new GameState[]{rich(), closing()}) {
                Params p = BotFactory.parameters("neural-v3?NOISE=0"); p.USE_CLOSE = close;
                NeuralV3Bot reference = new NeuralV3Bot(p, () -> 0);
                reference.init(state, me); reference.play(state, me, 60_000);
                TargetProposer actual = new TargetProposer(state, close);
                GameState before = state.copy();
                assertEquals(reference.lastTarget, actual.propose(state, me, NEVER));
                assertTrue(actual.lastComplete); assertEquals(0, actual.lastAbortedPhase);
                NeuralNet net = new NeuralNet(NeuralV2Weights.SABOTAGE_SIZES, NeuralV2Weights.SABOTAGE);
                for (int i = 0; i < actual.lastCount; i++) if (actual.scoreAvailable[i])
                    assertEquals(Float.floatToRawIntBits(net.score(actual.rows[i])), Float.floatToRawIntBits(actual.scores[i]));
                unchanged(before, state);
            }
    }

    @Test void closingGuardAndNoCandidateHaveDistinctCompletedResults() {
        GameState s = closing();
        assertEquals(1, new TargetProposer(s, true).propose(s, 0, NEVER));
        TargetProposer behind = new TargetProposer(s, true);
        assertEquals(-1, behind.propose(s, 1, NEVER)); assertTrue(behind.lastComplete);
        assertEquals(3, behind.lastCount);
        for (int i = 0; i < behind.lastCount; i++) assertFalse(behind.scoreAvailable[i]);
        for (int r = 1; r <= 3; r++) Rules.ink(s, r);
        Rules.updateConnections(s, new Pathing(s.size));
        TargetProposer empty = new TargetProposer(s, true);
        assertEquals(-1, empty.propose(s, 0, NEVER)); assertTrue(empty.lastComplete);
        assertEquals(0, empty.lastCount);
    }

    @Test void proposerRejectsPartialWorkInEveryPhaseAndNeverRecordsSuggestion() {
        for (int phase : new int[]{TargetProposer.PREPARE, TargetProposer.CANDIDATES, TargetProposer.SCORES}) {
            GameState s = rich(); TargetProposer p = new TargetProposer(s, true);
            TargetProposalFeatures.Deadline stop = () -> p.lastPhase == phase;
            GameState before = s.copy();
            assertEquals(TargetProposer.ABORTED, p.propose(s, 0, stop));
            assertFalse(p.lastComplete); assertEquals(-1, p.lastTarget); assertEquals(phase, p.lastAbortedPhase);
            unchanged(before, s);
            assertTrue(p.propose(s, 0, NEVER) >= 0);
            // With no recordTarget call, a future observed hit is attributed to the opponent.
            int proposed = p.lastTarget;
            if (s.regionInstability[proposed] == GameState.INK_THRESHOLD - 1) continue;
            Rules.disrupt(s, proposed); s.turn++;
            assertNotEquals(TargetProposer.ABORTED, p.propose(s, 0, NEVER));
            for (int i = 0; i < p.lastCount; i++) if (p.regions[i] == proposed) {
                assertEquals(0f, p.rows[i][20]); assertEquals(1f, p.rows[i][21]);
            }
        }
    }
}
