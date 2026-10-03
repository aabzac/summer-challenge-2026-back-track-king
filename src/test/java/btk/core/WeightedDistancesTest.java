package btk.core;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class WeightedDistancesTest {
    @Test void reversePaysDepartureCostAndSourceCostsNothing() {
        GameState s = TestMaps.of(new String[]{"0..1"}, new int[][]{{1}, {}});
        int[] cost = {7, 2, 3, 5}, forward = new int[s.size], reverse = new int[s.size];
        TacticalPathing p = new TacticalPathing(s.size);
        p.weightedDistances(s, 0, cost, false, forward);
        p.weightedDistances(s, 3, cost, true, reverse);
        assertArrayEquals(new int[]{0, 2, 5, 10}, forward);
        assertArrayEquals(new int[]{10, 8, 5, 0}, reverse);
        for (int c = 0; c < s.size; c++) assertEquals(10, forward[c] + reverse[c]);
    }

    @Test void alternateInkPenaltyMakesStableDetourCheaper() {
        GameState s = TestMaps.of(new String[]{"0.1", "..."}, new int[][]{{1}, {}});
        int[] plain = {0, 1, 0, 1, 1, 1}, alternate = plain.clone();
        alternate[1] += 2 * 2; // feature-side risk cost, not a game-rule change
        TacticalPathing p = new TacticalPathing(s.size);
        int[] out = new int[s.size];
        p.weightedDistances(s, 0, plain, false, out);
        assertEquals(1, out[2]);
        p.weightedDistances(s, 0, alternate, false, out);
        assertEquals(3, out[2]);
        assertEquals(5, out[1], "the unstable entry itself always pays its supplied cost");
    }

    @Test void zeroCostCyclesTerminateAndInkedBarrierIsImpassable() {
        GameState s = TestMaps.of(new String[]{"0..1", "...."}, new int[][]{{1}, {}});
        TacticalPathing p = new TacticalPathing(s.size);
        int[] out = new int[s.size], cost = new int[s.size];
        p.weightedDistances(s, 0, cost, false, out);
        assertArrayEquals(new int[s.size], out);
        s.inked[1] = s.inked[5] = true;
        s.owner[1] = 1; // even malformed surviving rails cannot cross ink
        p.weightedDistances(s, 0, cost, false, out);
        assertEquals(0, out[4]);
        assertEquals(TacticalPathing.INF, out[1]);
        assertEquals(TacticalPathing.INF, out[3]);
        p.weightedDistances(s, 1, cost, true, out);
        for (int value : out) assertEquals(TacticalPathing.INF, value);
    }

    @Test void prioritizedWeightedPathUsesBestEdgesAndCanonicalDirectionOrder() {
        GameState s = TestMaps.of(new String[]{"..1", "...", "0.."}, new int[][]{{1}, {}});
        TacticalPathing p = new TacticalPathing(s.size);
        int[] path = new int[s.size], cost = new int[s.size];
        int n = p.weightedPath(s, s.townCell[0], s.townCell[1], cost, path);
        assertEquals(5, n, "N/N/E/E is the first simple optimal path even with zero-cost cycles");
        assertArrayEquals(new int[]{6, 3, 0, 1, 2}, Arrays.copyOf(path, n));
        s.inked[3] = true;
        n = p.weightedPath(s, s.townCell[0], s.townCell[1], cost, path);
        assertTrue(n > 0);
        for (int i = 0; i < n; i++) assertFalse(s.inked[path[i]]);
        s.inked[7] = true;
        assertEquals(-1, p.weightedPath(s, s.townCell[0], s.townCell[1], cost, path));
    }

    @Test void unreachableAndLargeEntryCostsSaturateWithoutOverflow() {
        GameState s = TestMaps.of(new String[]{"0..1"}, new int[][]{{1}, {}});
        TacticalPathing p = new TacticalPathing(s.size);
        int[] out = new int[s.size], cost = {Integer.MAX_VALUE, TacticalPathing.INF - 1, 10, 0};
        p.weightedDistances(s, 0, cost, false, out);
        assertEquals(0, out[0], "source entry cost is not paid");
        assertEquals(TacticalPathing.INF - 1, out[1]);
        assertEquals(TacticalPathing.INF, out[2]);
        assertEquals(TacticalPathing.INF, out[3]);
        assertThrows(IllegalArgumentException.class,
                () -> p.weightedDistances(s, 0, new int[]{0, -1, 0, 0}, false, out));
        assertThrows(IllegalArgumentException.class,
                () -> p.weightedDistances(s, 0, out, false, out));
    }

    @Test void weightedPaintCostsMatchRefereeCheapestCostOnMixedBoards() {
        GameState s = TestMaps.of(new String[]{"0.~.^1", "......", "^~..^."}, new int[][]{{1}, {}});
        TacticalPathing p = new TacticalPathing(s.size);
        int[] path = new int[s.size], out = new int[s.size], cost = new int[s.size];
        Random random = new Random(821);
        for (int sample = 0; sample < 40; sample++) {
            for (int c = 0; c < s.size; c++) {
                s.owner[c] = !s.isTown(c) && random.nextInt(3) == 0 ? random.nextInt(3) : GameState.NO_TRACK;
                s.inked[c] = !s.isTown(c) && random.nextInt(8) == 0;
                if (s.inked[c]) s.owner[c] = GameState.NO_TRACK;
                cost[c] = s.passable(c) ? 0 : s.placeCost(c);
            }
            Pathing canonical = new Pathing(s.size);
            int n = canonical.cheapestPath(s, s.townCell[0], s.townCell[1], path);
            int expected = n < 0 ? TacticalPathing.INF : canonical.lastCost(s.townCell[1]);
            p.weightedDistances(s, s.townCell[0], cost, false, out);
            assertEquals(expected, out[s.townCell[1]], "sample " + sample);
        }
    }

    @Test void forwardReverseAndLinkSlackMatchIndependentDirectedGraphOracle() {
        GameState s = TestMaps.of(new String[]{"0...", "....", "...1"}, new int[][]{{1}, {}});
        TacticalPathing p = new TacticalPathing(s.size);
        int[] cost = new int[s.size], forward = new int[s.size], reverse = new int[s.size];
        Random random = new Random(6381);
        for (int sample = 0; sample < 40; sample++) {
            for (int c = 0; c < s.size; c++) {
                cost[c] = random.nextInt(7);
                s.inked[c] = !s.isTown(c) && random.nextInt(5) == 0;
            }
            int[][] oracle = new int[s.size][s.size];
            for (int a = 0; a < s.size; a++) {
                Arrays.fill(oracle[a], TacticalPathing.INF);
                if (s.inked[a]) continue;
                oracle[a][a] = 0;
                for (int b = 0; b < s.size; b++)
                    if (!s.inked[b] && Math.abs(s.x(a) - s.x(b)) + Math.abs(s.y(a) - s.y(b)) == 1)
                        oracle[a][b] = cost[b];
            }
            for (int k = 0; k < s.size; k++) for (int a = 0; a < s.size; a++) for (int b = 0; b < s.size; b++)
                oracle[a][b] = Math.min(oracle[a][b], oracle[a][k] + oracle[k][b]);
            for (int source = 0; source < s.size; source++) {
                p.weightedDistances(s, source, cost, false, forward);
                p.weightedDistances(s, source, cost, true, reverse);
                for (int c = 0; c < s.size; c++) {
                    assertEquals(oracle[source][c], forward[c], "forward sample=" + sample);
                    assertEquals(oracle[c][source], reverse[c], "reverse sample=" + sample);
                }
                int[] path = new int[s.size];
                int target = s.townCell[1];
                int length = p.weightedPath(s, source, target, cost, path);
                if (oracle[source][target] >= TacticalPathing.INF) assertEquals(-1, length);
                else {
                    assertTrue(length > 0 && length <= s.size);
                    assertEquals(source, path[0]);
                    assertEquals(target, path[length - 1]);
                    boolean[] visited = new boolean[s.size];
                    int sum = 0;
                    for (int i = 0; i < length; i++) {
                        assertFalse(visited[path[i]], "weighted route must be simple");
                        visited[path[i]] = true;
                        if (i == 0) continue;
                        assertEquals(1, Math.abs(s.x(path[i]) - s.x(path[i - 1]))
                                + Math.abs(s.y(path[i]) - s.y(path[i - 1])));
                        sum += cost[path[i]];
                    }
                    assertEquals(oracle[source][target], sum, "weighted route cost");
                }
            }
            p.weightedDistances(s, s.townCell[0], cost, false, forward);
            p.weightedDistances(s, s.townCell[1], cost, true, reverse);
            int direct = forward[s.townCell[1]];
            for (int b = 0; b < s.size; b++)
                if (direct < TacticalPathing.INF && forward[b] < TacticalPathing.INF && reverse[b] < TacticalPathing.INF)
                    assertTrue(forward[b] + reverse[b] - direct >= 0, "link slack is nonnegative");
        }
    }
}
