package btk.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PathingTest {

    @Test
    void tieBreakPrefersNorthThenEast() {
        // Two equal shortest paths from 0 (bottom-left) to 1 (top-right): up-then-right vs right-then-up.
        // N before E => go up first.
        GameState s = TestMaps.of(new String[]{
                ".1",
                "0."}, new int[][]{{1}, {}});
        TestMaps.track(s, 0, 0, 0); // up cell
        TestMaps.track(s, 1, 1, 1); // right cell
        Pathing p = new Pathing(s.size);
        int[] path = new int[s.size];
        int n = p.shortestPath(s, 0, 1, path);
        assertEquals(3, n);
        assertEquals(s.cell(0, 1), path[0]);
        assertEquals(s.cell(0, 0), path[1], "NORTH first");
        assertEquals(s.cell(1, 0), path[2]);
    }

    @Test
    void tieBreakEastBeforeSouthThroughAnotherTown() {
        // Statement example 2: from 0 two equal paths to 1, EAST beats SOUTH; path may pass through town 2.
        GameState s = TestMaps.of(new String[]{
                "0.2",
                "...",
                "..1"}, new int[][]{{1, 2}, {}, {}});
        TestMaps.track(s, 0, 1, 0);       // east of 0
        TestMaps.track(s, 0, 2, 1);       // south of town 2
        TestMaps.track(s, 1, 0, 1, 0, 2, 1, 2); // south route
        Pathing p = new Pathing(s.size);
        int[] path = new int[s.size];
        int n = p.shortestPath(s, 0, 1, path);
        assertEquals(5, n);
        assertEquals(s.cell(1, 0), path[1], "EAST first");
        assertEquals(s.cell(2, 0), path[2], "through town 2");
    }

    @Test
    void shortestPathIsLexicographicAtEveryStep() {
        // Grid of tracks; from 0 at (0,2) to 1 at (2,0): shortest length 5; N,N,E,E is lexicographically first.
        GameState s = TestMaps.of(new String[]{
                "..1",
                "...",
                "0.."}, new int[][]{{1}, {}});
        for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) if (!s.isTown(s.cell(x, y))) s.owner[s.cell(x, y)] = 0;
        Pathing p = new Pathing(s.size);
        int[] path = new int[s.size];
        int n = p.shortestPath(s, 0, 1, path);
        assertEquals(5, n);
        assertEquals(s.cell(0, 1), path[1]);
        assertEquals(s.cell(0, 0), path[2]);
        assertEquals(s.cell(1, 0), path[3]);
        assertEquals(s.cell(2, 0), path[4]);
    }

    @Test
    void cheapestPathUsesExistingTracksForFree() {
        GameState s = TestMaps.of(new String[]{
                "0^^^1",
                "....."}, new int[][]{{1}, {}});
        Pathing p = new Pathing(s.size);
        int[] path = new int[s.size];
        int n = p.cheapestPath(s, s.townCell[0], s.townCell[1], path);
        assertEquals(7, n, "detour over plains");
        assertEquals(5, p.lastCost(s.townCell[1]));
        TestMaps.track(s, 1, 1, 0, 2, 0, 3, 0);
        n = p.cheapestPath(s, s.townCell[0], s.townCell[1], path);
        assertEquals(5, n);
        assertEquals(0, p.lastCost(s.townCell[1]));
    }

    @Test
    void unreachableReturnsMinusOne() {
        GameState s = TestMaps.of(new String[]{"0...1"}, new int[][]{{1}, {}});
        Pathing p = new Pathing(s.size);
        assertEquals(-1, p.shortestPath(s, 0, 1, new int[s.size]));
    }
}
