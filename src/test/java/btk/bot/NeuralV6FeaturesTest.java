package btk.bot;

import btk.bot.neural.NeuralV5Features;
import btk.bot.neural.NeuralV6Features;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Protocol;
import btk.core.Rules;
import btk.referee.MapGenerator;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class NeuralV6FeaturesTest {
    private static final NeuralV6Features.Deadline NEVER = () -> false;

    private static final class CountingDeadline implements NeuralV6Features.Deadline {
        final int limit;
        int calls;
        CountingDeadline(int limit) { this.limit = limit; }
        public boolean expired() { return ++calls >= limit; }
    }

    /** Active mixed-ownership route, threatened bypass, and three incomplete town pairs. */
    private static GameState richState() {
        int[] regions = new int[45];
        for (int c = 0; c < regions.length; c++) regions[c] = c % 9;
        GameState s = new GameState(9, 5, regions, new int[45], new int[]{0, 8, 0, 8},
                new int[]{1, 1, 3, 3}, new int[][]{{1, 3}, {2}, {3}, {0}});
        for (int x = 1; x < 8; x++) s.owner[s.cell(x, 1)] = x % 2;
        for (int x = 2; x < 5; x++) s.owner[s.cell(x, 3)] = x % 2;
        Rules.disrupt(s, 3); Rules.disrupt(s, 3);
        Rules.disrupt(s, 6); Rules.disrupt(s, 6); Rules.disrupt(s, 6);
        s.turn = 12; s.score[0] = 80; s.score[1] = 91;
        Rules.updateConnections(s, new Pathing(s.size));
        assertTrue(s.pairActive(0));
        return s;
    }

    private static final class Fixture {
        final GameState s;
        final int me;
        final NeuralV5Features original;
        final NeuralV6Features deadline;
        final int[] originalIds, deadlineIds;
        final float[][] originalSabotage, deadlineSabotage, originalBuild, deadlineBuild;
        Fixture(GameState s, int me) {
            this.s = s; this.me = me;
            original = new NeuralV5Features(s); deadline = new NeuralV6Features(s);
            int max = Math.max(s.regionCount, s.size);
            originalIds = new int[max]; deadlineIds = new int[max];
            originalSabotage = new float[s.regionCount][NeuralV5Features.SABOTAGE_F];
            deadlineSabotage = new float[s.regionCount][NeuralV5Features.SABOTAGE_F];
            originalBuild = new float[s.size][NeuralV5Features.BUILD_F];
            deadlineBuild = new float[s.size][NeuralV5Features.BUILD_F];
            original.observe(s, me); deadline.observe(s, me);
        }
        void prepare(boolean full) {
            original.prepare(s, me, full);
            assertTrue(deadline.prepare(s, me, full, NEVER));
            assertEquals(original.inactivePairs(), deadline.inactivePairs());
        }
        void sabotage() {
            int n = original.sabotageCandidates(s, me, originalIds, originalSabotage);
            assertEquals(n, deadline.sabotageCandidates(s, me, deadlineIds, deadlineSabotage, NEVER));
            rows(n, originalSabotage, deadlineSabotage);
            assertArrayEquals(original.closes, deadline.closes);
            assertArrayEquals(original.winsIfClosed, deadline.winsIfClosed);
        }
        int build(int paint, int target, int k) {
            int n = original.buildCandidates(s, me, paint, target, k, originalIds, originalBuild);
            assertEquals(n, deadline.buildCandidates(s, me, paint, target, k, deadlineIds, deadlineBuild, NEVER));
            rows(n, originalBuild, deadlineBuild);
            return n;
        }
        void rows(int n, float[][] a, float[][] b) {
            for (int i = 0; i < n; i++) {
                assertEquals(originalIds[i], deadlineIds[i], "candidate order " + i);
                assertArrayEquals(a[i], b[i], "candidate features " + i);
            }
        }
        void place(int cell) {
            s.owner[cell] = me; original.markPlaced(cell); deadline.markPlaced(cell);
            Rules.updateConnections(s, new Pathing(s.size));
        }
    }

    @Test void neverExpiredMatchesFrozenFeaturesForBothSeatsAndSequentialPlacements() {
        assertArrayEquals(NeuralV5Features.SABOTAGE_NAMES, NeuralV6Features.SABOTAGE_NAMES);
        assertArrayEquals(NeuralV5Features.BUILD_NAMES, NeuralV6Features.BUILD_NAMES);
        for (int me = 0; me < 2; me++) for (GameState s : new GameState[]{richState(), MapGenerator.generate(2209000)}) {
            Fixture f = new Fixture(s, me);
            f.prepare(true); f.sabotage();
            int target = f.originalIds[0];
            f.original.recordTarget(target); f.deadline.recordTarget(target);
            int paint = 3;
            for (int k = 0; k < 3 && paint > 0; k++) {
                if (k > 0) f.prepare(false);
                int n = f.build(paint, target, k);
                if (n == 0) break;
                int cell = f.originalIds[n / 2];
                paint -= s.placeCost(cell); f.place(cell);
            }
            // Next observation tests retained placement/target memory, not just stateless rows.
            Rules.disrupt(s, target); s.turn++;
            f.original.observe(s, me); f.deadline.observe(s, me);
            f.prepare(true); f.sabotage(); f.build(3, -1, 0);
        }
    }

    @Test void everyPrepareCheckpointAbortsAndFullRetryRestoresExactOutputsWithoutChangingInput() {
        for (int me = 0; me < 2; me++) {
            Fixture f = new Fixture(richState(), me);
            CountingDeadline count = new CountingDeadline(Integer.MAX_VALUE);
            assertTrue(f.deadline.prepare(f.s, me, true, count));
            assertTrue(count.calls > 20, "fixture must exercise nested route operations");
            String before = Protocol.turnBlock(f.s, me);
            for (int cutoff = 1; cutoff <= count.calls; cutoff++) {
                CountingDeadline stop = new CountingDeadline(cutoff);
                assertFalse(f.deadline.prepare(f.s, me, true, stop), "cutoff=" + cutoff);
                assertEquals(cutoff, stop.calls, "must stop at the first expired checkpoint");
                assertEquals(before, Protocol.turnBlock(f.s, me), "input mutated at cutoff=" + cutoff);
                f.prepare(true); f.sabotage(); f.build(3, -1, 0);
            }
        }
    }

    @Test void everyCandidateCheckpointDiscardsPartialPassAndCanBeRetried() {
        Fixture f = new Fixture(richState(), 0);
        f.prepare(true);
        String before = Protocol.turnBlock(f.s, 0);
        CountingDeadline sabCount = new CountingDeadline(Integer.MAX_VALUE);
        assertTrue(f.deadline.sabotageCandidates(f.s, 0, f.deadlineIds, f.deadlineSabotage, sabCount) > 1);
        assertTrue(sabCount.calls > f.s.regionCount, "fixture must include ink simulations");
        for (int cutoff = 1; cutoff <= sabCount.calls; cutoff++) {
            CountingDeadline stop = new CountingDeadline(cutoff);
            assertEquals(-1, f.deadline.sabotageCandidates(f.s, 0, f.deadlineIds, f.deadlineSabotage, stop));
            assertEquals(cutoff, stop.calls);
            assertEquals(before, Protocol.turnBlock(f.s, 0));
            f.sabotage();
        }
        CountingDeadline buildCount = new CountingDeadline(Integer.MAX_VALUE);
        assertTrue(f.deadline.buildCandidates(f.s, 0, 3, -1, 0, f.deadlineIds, f.deadlineBuild, buildCount) > 1);
        assertTrue(buildCount.calls > f.s.size + 2, "fixture must include per-cell connection simulations");
        for (int cutoff = 1; cutoff <= buildCount.calls; cutoff++) {
            CountingDeadline stop = new CountingDeadline(cutoff);
            assertEquals(-1, f.deadline.buildCandidates(f.s, 0, 3, -1, 0, f.deadlineIds, f.deadlineBuild, stop));
            assertEquals(cutoff, stop.calls);
            assertEquals(before, Protocol.turnBlock(f.s, 0));
            f.build(3, -1, 0);
        }
    }

    @Test void abortedIncrementalPreparationPreservesOpponentCacheAndPlacementMemoryOnRetry() {
        Fixture f = new Fixture(richState(), 1);
        f.prepare(true); f.sabotage();
        int n = f.build(3, -1, 0);
        assertTrue(n > 0);
        int cell = f.originalIds[n / 2];
        int paint = 3 - f.s.placeCost(cell);
        f.place(cell);
        CountingDeadline count = new CountingDeadline(Integer.MAX_VALUE);
        assertTrue(f.deadline.prepare(f.s, 1, false, count));
        String before = Protocol.turnBlock(f.s, 1);
        for (int cutoff = 1; cutoff <= count.calls; cutoff++) {
            assertFalse(f.deadline.prepare(f.s, 1, false, new CountingDeadline(cutoff)));
            assertEquals(before, Protocol.turnBlock(f.s, 1));
            f.prepare(false); f.build(paint, -1, 1);
        }
    }

    @Test void repeatedSourcesAndAllMasksStayExactAcrossBoardAndPerspectiveChanges() throws Exception {
        GameState base = richState();
        GameState s = new GameState(base.width, base.height, base.regionId, base.terrain,
                base.townX, base.townY, new int[][]{{1, 3, 2, 1, 0}, {3, 0, 2, 1}, {0, 1, 3, 0}, {2, 0, 1, 3}});
        System.arraycopy(base.owner, 0, s.owner, 0, s.size);
        Rules.disrupt(s, 3); Rules.disrupt(s, 3);
        Rules.disrupt(s, 6); Rules.disrupt(s, 6); Rules.disrupt(s, 6);
        s.turn = base.turn;
        Pathing paths = new Pathing(s.size);
        Rules.updateConnections(s, paths);
        NeuralV5Features old = new NeuralV5Features(s);
        NeuralV6Features now = new NeuralV6Features(s);
        int[] a = new int[s.size], b = new int[s.size];
        float[][] ax = new float[s.size][NeuralV5Features.BUILD_F];
        float[][] bx = new float[s.size][NeuralV6Features.BUILD_F];
        float[][] as = new float[s.regionCount][NeuralV5Features.SABOTAGE_F];
        float[][] bs = new float[s.regionCount][NeuralV6Features.SABOTAGE_F];
        boolean shared = false, ownOnly = false, opponentOnly = false, threatened = false;
        int mine = 0, theirs = 0;
        assertTrue(s.pairActive(0));
        for (int i = 0; i < s.pairPathLen[0]; i++) {
            int owner = s.owner[s.pairPath[0][i]];
            if (owner == 0) mine++;
            if (owner == 1) theirs++;
        }
        assertTrue(mine > 0 && theirs > mine, "initial active pair must attempt a take-over and threatened bypass");
        for (int turn = 0; turn < 8; turn++) {
            // Same objects survive perspective changes: semantic masks must be invalidated.
            int me = turn % 2;
            old.observe(s, me); now.observe(s, me);
            for (int k = 0, paint = 3; k < 3 && paint > 0; k++) {
                old.prepare(s, me, k == 0); now.prepare(s, me, k == 0);
                // Even a correctly attempted mask query may find no path or no buildable cells.
                // Verify query coverage separately from the complete output parity below.
                shared |= queried(now, "anyPaths");
                ownOnly |= queried(now, "ownPaths");
                opponentOnly |= queried(now, "opponentPaths");
                threatened |= queried(now, "threatPaths");
                if (k == 0) {
                    int n = old.sabotageCandidates(s, me, a, as);
                    assertEquals(n, now.sabotageCandidates(s, me, b, bs));
                    for (int i = 0; i < n; i++) { assertEquals(a[i], b[i]); assertArrayEquals(as[i], bs[i]); }
                }
                int n = old.buildCandidates(s, me, paint, -1, k, a, ax);
                assertEquals(n, now.buildCandidates(s, me, paint, -1, k, b, bx));
                for (int i = 0; i < n; i++) {
                    assertEquals(a[i], b[i]); assertArrayEquals(ax[i], bx[i]);
                }
                if (n == 0) break;
                int cell = a[n / 2];
                paint -= s.placeCost(cell);
                s.owner[cell] = me; old.markPlaced(cell); now.markPlaced(cell);
                Rules.updateConnections(s, paths);
            }
            s.turn++;
        }
        assertTrue(shared && ownOnly && opponentOnly, "fixture must query all shared and ownership masks");
        assertTrue(threatened, "fixture must query the threatened-region mask");
    }

    private static boolean queried(NeuralV6Features features, String name) throws Exception {
        var field = NeuralV6Features.class.getDeclaredField(name);
        field.setAccessible(true);
        Object helper = field.get(features);
        var source = helper.getClass().getDeclaredField("source");
        source.setAccessible(true);
        return source.getInt(helper) >= 0;
    }

    @Test void publicFreshSearchPreservesTentativeCostsDespitePriorQueriesAndPreparation() {
        GameState s = richState();
        NeuralV5Features old = new NeuralV5Features(s);
        NeuralV6Features now = new NeuralV6Features(s);
        int[] a = new int[s.size], b = new int[s.size];
        boolean[] block = new boolean[s.size];
        for (int iteration = 0; iteration < 3; iteration++) {
            old.prepare(s, iteration % 2, true); now.prepare(s, iteration % 2, true);
            for (int from : s.townCell) for (int to = s.size - 1; to >= 0; to--) {
                for (int c = 0; c < s.size; c++) block[c] = (c + to + iteration) % 7 == 0;
                boolean[] mask = to % 3 == 0 ? null : block;
                int n = old.cheapestCostPath(s, from, to, a, mask);
                assertEquals(n, now.cheapestCostPath(s, from, to, b, mask));
                if (n > 0) assertArrayEquals(Arrays.copyOf(a, n), Arrays.copyOf(b, n));
                for (int c = 0; c < s.size; c++) assertEquals(old.lastCost(c), now.lastCost(c),
                        "tentative cell=" + c + " from=" + from + " to=" + to);
            }
            int cell = 0;
            while (cell < s.size && !s.canBuild(cell)) cell++;
            if (cell < s.size) s.owner[cell] = iteration % 2;
            Rules.updateConnections(s, new Pathing(s.size));
        }
    }

    @Test void expiredAtEntryLeavesOutputBuffersUntouched() {
        Fixture f = new Fixture(richState(), 0);
        f.prepare(true);
        Arrays.fill(f.deadlineIds, -123);
        for (float[] row : f.deadlineSabotage) Arrays.fill(row, -321f);
        for (float[] row : f.deadlineBuild) Arrays.fill(row, -321f);
        assertEquals(-1, f.deadline.sabotageCandidates(f.s, 0, f.deadlineIds, f.deadlineSabotage, () -> true));
        assertEquals(-1, f.deadline.buildCandidates(f.s, 0, 3, -1, 0, f.deadlineIds, f.deadlineBuild, () -> true));
        for (int id : f.deadlineIds) assertEquals(-123, id);
        for (float[] row : f.deadlineSabotage) for (float value : row) assertEquals(-321f, value);
        for (float[] row : f.deadlineBuild) for (float value : row) assertEquals(-321f, value);
    }
}
