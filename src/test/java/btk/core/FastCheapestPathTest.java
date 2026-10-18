package btk.core;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class FastCheapestPathTest {
    @Test void defaultPathingDoesNotAllocateTheOptionalTable() throws Exception {
        GameState s = board(6, 4);
        Pathing original = new Pathing(s.size), fast = new Pathing(s.size);
        assertNull(neighbors(original));
        fast.enableFastCheapestPath(s);
        compare(s, original, fast, 0, s.size - 1, null, null);
        assertNull(neighbors(original));
    }

    @Test void equalCostRoutesRailBlocksAndAllFourEdgesKeepTheCanonicalOrder() {
        GameState s = TestMaps.of(new String[]{"0..^..1", ".~...~.", "...^...", "2.....3"},
                new int[][]{{1, 2, 3}, {0, 2}, {0, 3}, {0, 1}});
        TestMaps.track(s, 0, 1, 0, 2, 0, 2, 1, 2, 2, 3, 2, 4, 2);
        TestMaps.track(s, 1, 3, 0, 4, 0, 4, 1, 5, 2, 5, 3, 6, 3);
        Pathing original = new Pathing(s.size), fast = enabled(s);
        for (int from : s.townCell) for (int to = 0; to < s.size; to++)
            compare(s, original, fast, from, to, null, null);
    }

    @Test void blockedConnectorsAndRailFringeGoalRetainExistingSemantics() {
        GameState s = TestMaps.of(new String[]{"0.1", "..."}, new int[][]{{1}, {0}});
        Pathing original = new Pathing(s.size), fast = enabled(s);
        boolean[] blocked = new boolean[s.size];
        blocked[1] = true;
        compare(s, original, fast, 0, 1, blocked, null);
        assertEquals(0, fast.lastCost(1), "existing rail-fringe goal convention is preserved");
        TestMaps.track(s, 1, 1, 0);
        for (int from = 0; from < s.size; from++) for (int to = 0; to < s.size; to++)
            compare(s, original, fast, from, to, blocked, null);
        blocked[0] = blocked[2] = true;
        for (int from = 0; from < s.size; from++)
            compare(s, original, fast, from, 2, blocked, null);
    }

    @Test void sameCellUnreachableAndInkedStartRetainCostsAndScratchDistances() {
        GameState s = TestMaps.of(new String[]{"0...1"}, new int[][]{{1}, {0}});
        s.inked[2] = true;
        Pathing original = new Pathing(s.size), fast = enabled(s);
        assertEquals(-1, compare(s, original, fast, 0, 4, null, null));
        for (int from = 0; from < s.size; from++) {
            compare(s, original, fast, from, from, null, null);
            compare(s, original, fast, from, 4, null, null);
        }
    }

    @Test void repeatedQueriesObserveBoardMasksAndSurchargesWithoutCachingThem() {
        GameState s = board(8, 5);
        Pathing original = new Pathing(s.size), fast = enabled(s);
        Random random = new Random(381018L);
        boolean[] blocked = new boolean[s.size]; int[] extra = new int[s.size];
        for (int state = 0; state < 48; state++) {
            for (int c = 0; c < s.size; c++) {
                s.terrain[c] = random.nextInt(3);
                s.owner[c] = s.isTown(c) ? GameState.NO_TRACK : random.nextInt(4) - 1;
                s.inked[c] = !s.isTown(c) && random.nextInt(9) == 0;
                if (s.inked[c]) s.owner[c] = GameState.NO_TRACK;
                blocked[c] = random.nextInt(5) == 0;
                extra[c] = random.nextInt(8);
            }
            for (int query = 0; query < 12; query++)
                compare(s, original, fast, random.nextInt(s.size), random.nextInt(s.size),
                        query % 3 == 0 ? null : blocked, query % 4 == 0 ? null : extra);
        }
    }

    @Test void shapeChangesAndDegenerateDimensionsRefillTheSameBuffer() throws Exception {
        Pathing original = new Pathing(24), fast = new Pathing(24);
        GameState first = board(6, 4); fast.enableFastCheapestPath(first);
        Object table = neighbors(fast);
        for (int width : new int[]{6, 4, 1, 24, 8, 3, 6}) {
            GameState s = board(width, 24 / width);
            for (int from : new int[]{0, 1, 12, 23}) for (int to = 0; to < s.size; to++)
                compare(s, original, fast, from, to, null, null);
            fast.enableFastCheapestPath(s);
            assertSame(table, neighbors(fast));
        }
        GameState small = board(1, 1);
        compare(small, original, fast, 0, 0, null, null);
        assertSame(table, neighbors(fast));
    }

    @Test void otherPathingOperationsCanShareScratchBuffersBetweenQueries() {
        GameState s = board(6, 4);
        for (int c = 0; c < s.size; c++) if (!s.isTown(c)) s.owner[c] = c % 3 == 0 ? 0 : 1;
        Pathing original = new Pathing(s.size), fast = enabled(s);
        for (int c = 1; c < s.size - 1; c++) {
            int[] a = new int[s.size], b = new int[s.size];
            assertEquals(original.shortestPath(s, 0, 1, a), fast.shortestPath(s, 0, 1, b));
            assertArrayEquals(a, b);
            compare(s, original, fast, 0, s.size - 1, null, null);
            assertEquals(original.reachableAvoidingInk(s, 0, s.size - 1),
                    fast.reachableAvoidingInk(s, 0, s.size - 1));
            compare(s, original, fast, s.size - 1, 0, null, null);
            s.owner[c] = GameState.NO_TRACK; s.inked[c] = true;
        }
    }

    @Test void floodEpochWrapPreservesPathsAndDoesNotReplaceTheGeometryTable() throws Exception {
        GameState s = board(5, 4);
        Pathing original = new Pathing(s.size), fast = enabled(s);
        var epoch = Pathing.class.getDeclaredField("floodEpoch"); epoch.setAccessible(true);
        epoch.setInt(original, -1); epoch.setInt(fast, -1);
        Object table = neighbors(fast);
        compare(s, original, fast, 0, s.size - 1, null, null);
        assertSame(table, neighbors(fast));
    }

    private static Pathing enabled(GameState s) {
        Pathing result = new Pathing(s.size); result.enableFastCheapestPath(s); return result;
    }

    private static Object neighbors(Pathing paths) throws Exception {
        var field = Pathing.class.getDeclaredField("pathNeighbors"); field.setAccessible(true);
        return field.get(paths);
    }

    private static int compare(GameState s, Pathing original, Pathing fast, int from, int to,
                               boolean[] blocked, int[] extra) {
        int[] a = new int[s.size], b = new int[s.size]; Arrays.fill(a, -7); Arrays.fill(b, -7);
        String context = s.width + "x" + s.height + ", from=" + from + ", to=" + to;
        int n = original.cheapestPath(s, from, to, a, blocked, extra);
        assertEquals(n, fast.cheapestPath(s, from, to, b, blocked, extra), context);
        assertArrayEquals(a, b, "ordered path and untouched tail: " + context);
        assertArrayEquals(original.lastDist(), fast.lastDist(), "scratch distances: " + context);
        for (int c = 0; c < s.size; c++)
            assertEquals(original.lastCost(c), fast.lastCost(c), "cost " + c + ": " + context);
        return n;
    }

    private static GameState board(int width, int height) {
        int size = width * height; int[] regions = new int[size], terrain = new int[size];
        for (int c = 0; c < size; c++) regions[c] = c;
        return new GameState(width, height, regions, terrain,
                new int[]{0, width - 1}, new int[]{0, height - 1}, new int[][]{{1}, {0}});
    }
}
