package btk.bot;

import btk.bot.neural.NeuralFeatures;
import btk.bot.neural.NeuralV3Features;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Protocol;
import btk.core.Rules;
import btk.referee.MapGenerator;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class NeuralV3FeaturesTest {
    private static final NeuralV3Features.Deadline NEVER = () -> false;

    private static final class CountingDeadline implements NeuralV3Features.Deadline {
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
        final NeuralFeatures original;
        final NeuralV3Features deadline;
        final int[] originalIds, deadlineIds;
        final float[][] originalSabotage, deadlineSabotage, originalBuild, deadlineBuild;
        Fixture(GameState s, int me) {
            this.s = s; this.me = me;
            original = new NeuralFeatures(s); deadline = new NeuralV3Features(s);
            int max = Math.max(s.regionCount, s.size);
            originalIds = new int[max]; deadlineIds = new int[max];
            originalSabotage = new float[s.regionCount][NeuralFeatures.SABOTAGE_F];
            deadlineSabotage = new float[s.regionCount][NeuralFeatures.SABOTAGE_F];
            originalBuild = new float[s.size][NeuralFeatures.BUILD_F];
            deadlineBuild = new float[s.size][NeuralFeatures.BUILD_F];
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
        assertArrayEquals(NeuralFeatures.SABOTAGE_NAMES, NeuralV3Features.SABOTAGE_NAMES);
        assertArrayEquals(NeuralFeatures.BUILD_NAMES, NeuralV3Features.BUILD_NAMES);
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
