package btk.core;

import java.lang.reflect.Field;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HypotheticalIncomeTest {
    @Test void exhaustiveSmallBoardsAndConstructionSubsetsMatchMaterializedConnections() {
        // Four variable cells: empty, either player, neutral, or empty and inked (5^4 states).
        GameState source = TestMaps.of(new String[]{"0~^", ".~1"}, new int[][]{{1, 1, 0}, {0, 1}});
        HypotheticalIncome projection = new HypotheticalIncome(source);
        GameState expected = source.copy();
        Pathing reference = new Pathing(source.size);
        int[] variable = {1, 2, 3, 4}, route = new int[4];
        int[] actualIncome = new int[2], expectedIncome = new int[2];
        for (int encoding = 0; encoding < 625; encoding++) {
            Arrays.fill(source.owner, GameState.NO_TRACK);
            Arrays.fill(source.inked, false);
            int rest = encoding;
            for (int c : variable) {
                int state = rest % 5;
                rest /= 5;
                if (state == 4) source.inked[c] = true;
                else source.owner[c] = state - 1;
            }
            for (int subset = 0; subset < 16; subset++) {
                int n = 0;
                for (int i = 0; i < variable.length; i++) if ((subset & (1 << i)) != 0) route[n++] = variable[i];
                for (int me = 0; me < 2; me++) {
                    expected.copyFrom(source);
                    for (int i = 0; i < n; i++) if (source.canBuild(route[i])) expected.owner[route[i]] = me;
                    Rules.updateConnections(expected, reference);
                    Rules.incomePerTurn(expected, expectedIncome);
                    projection.afterBuild(source, me, route, n, actualIncome);
                    assertArrayEquals(expectedIncome, actualIncome,
                            "state=" + encoding + " subset=" + subset + " player=" + me);
                }
            }
        }
    }

    @Test void directionalTiesAreResolvedFromEachRequestingTown() {
        GameState source = TestMaps.of(new String[]{"0.", ".1"}, new int[][]{{1, 1, 0}, {0, 1}});
        source.owner[1] = 0;
        source.owner[2] = 1;
        HypotheticalIncome projection = new HypotheticalIncome(source);
        int[] result = new int[2];
        projection.afterBuild(source, 0, new int[0], 0, result);
        // 0->1 chooses EAST twice (duplicate request), 1->0 chooses NORTH once. Self-pairs earn nothing.
        assertArrayEquals(new int[]{3, 0}, result);
        assertMatchesReference(source, projection, 0, new int[0]);

        GameState reverse = TestMaps.of(new String[]{".1", "0."}, new int[][]{{1}, {0}});
        reverse.owner[0] = 0;
        reverse.owner[3] = 1;
        // 0->1 starts NORTH through P0; 1->0 starts SOUTH through P1 (SOUTH precedes WEST).
        assertMatchesReference(reverse, new HypotheticalIncome(reverse), 0, new int[0]);
        new HypotheticalIncome(reverse).afterBuild(reverse, 0, new int[0], 0, result);
        assertArrayEquals(new int[]{1, 1}, result);
    }

    @Test void routeCanChangeOtherPairsAndReplaceAnAlreadyActivePath() {
        GameState source = TestMaps.of(new String[]{"0.1.2", "....."}, new int[][]{{1, 2}, {2}, {0}});
        TestMaps.track(source, 1, 0, 1, 1, 1, 2, 1, 3, 1, 4, 1);
        Rules.updateConnections(source, new Pathing(source.size));
        assertTrue(source.pairActive(0));
        HypotheticalIncome projection = new HypotheticalIncome(source);
        int[] route = {0, 1, 2, 3, 4};
        assertMatchesReference(source, projection, 0, route);
        int[] result = new int[2];
        projection.afterBuild(source, 0, route, route.length, result);
        assertArrayEquals(new int[]{6, 0}, result);
    }

    @Test void routePreservesTownsOccupiedNeutralAndInkedCellsIncludingSyntheticConnectors() {
        GameState source = TestMaps.of(new String[]{"0.....1"}, new int[][]{{1}, {0}});
        source.owner[0] = 1; // Synthetic owned town: passability and raw-owner scoring must match the core.
        source.owner[1] = GameState.NEUTRAL;
        source.owner[2] = 1;
        source.inked[2] = true; // Existing tracks remain passable even with a synthetic ink flag.
        source.inked[3] = true; // An empty inked cell must not be built.
        source.owner[6] = 0;
        source.inked[6] = true; // A town remains passable too.
        int[] route = {0, 1, 2, 3, 4, 4, 5, 6};
        HypotheticalIncome projection = new HypotheticalIncome(source);
        assertMatchesReference(source, projection, 0, route);
        int[] result = new int[2];
        projection.afterBuild(source, 0, route, route.length, result);
        assertArrayEquals(new int[]{0, 0}, result);
        source.owner[3] = 0; // Now passable despite ink; duplicate additions at cell 4 count only once per path.
        assertMatchesReference(source, projection, 0, route);
        projection.afterBuild(source, 0, route, route.length, result);
        assertArrayEquals(new int[]{8, 4}, result);
    }

    @Test void repeatedQueriesDoNotMutateSourceRouteOrLeakPreviousConstruction() {
        GameState source = TestMaps.of(new String[]{"0.1", "..."}, new int[][]{{1}, {0}});
        source.owner[3] = 1;
        source.owner[4] = 1;
        source.owner[5] = 1;
        source.turn = 27;
        source.maxTurns = 90;
        source.score[0] = 113;
        source.score[1] = 97;
        Rules.disrupt(source, source.regionId[1]);
        Rules.updateConnections(source, new Pathing(source.size));
        GameState snapshot = source.copy();
        int[][] originalPaths = source.pairPath.clone();
        int[] route = {0, 1, 1, 2}, originalRoute = route.clone();
        HypotheticalIncome projection = new HypotheticalIncome(source);
        for (int i = 0; i < 8; i++) {
            assertMatchesReference(source, projection, i % 2, route);
            assertMatchesReference(source, projection, 1 - i % 2, new int[0]);
            assertArrayEquals(originalRoute, route);
            assertUnchanged(snapshot, originalPaths, source);
        }
        GameState copy = source.copy();
        copy.owner[1] = 0;
        assertMatchesReference(copy, projection, 1, route);
        assertMatchesReference(source, projection, 0, new int[0]);
        assertUnchanged(snapshot, originalPaths, source);
    }

    @Test void stampRolloverClearsOldBuilds() throws ReflectiveOperationException {
        GameState source = TestMaps.of(new String[]{"0.1"}, new int[][]{{1}, {0}});
        HypotheticalIncome projection = new HypotheticalIncome(source);
        int[] result = new int[2];
        projection.afterBuild(source, 0, new int[]{1}, 1, result);
        assertArrayEquals(new int[]{2, 0}, result);
        Field epoch = HypotheticalIncome.class.getDeclaredField("epoch");
        epoch.setAccessible(true);
        epoch.setInt(projection, Integer.MAX_VALUE);
        projection.afterBuild(source, 1, new int[0], 0, result);
        assertArrayEquals(new int[]{0, 0}, result);
        assertMatchesReference(source, projection, 1, new int[]{1});
    }

    @Test void thinBoardsUnreachableAndNoDesiredPairsDoNotWrapOrRetainIncome() {
        GameState row = TestMaps.of(new String[]{"0..1"}, new int[][]{{1}, {0}});
        assertMatchesReference(row, new HypotheticalIncome(row), 1, new int[]{1, 2});
        GameState column = TestMaps.of(new String[]{"0", ".", ".", "1"}, new int[][]{{1}, {0}});
        assertMatchesReference(column, new HypotheticalIncome(column), 0, new int[]{1, 2});
        GameState wrap = TestMaps.of(new String[]{"..0", "1.."}, new int[][]{{1}, {0}});
        wrap.owner[2] = 0;
        wrap.owner[3] = 1;
        // These towns are consecutive array entries, but not horizontally adjacent.
        assertMatchesReference(wrap, new HypotheticalIncome(wrap), 0, new int[0]);
        GameState noPairs = TestMaps.of(new String[]{"0.1"}, new int[][]{{}, {}});
        int[] result = {99, 99};
        new HypotheticalIncome(noPairs).afterBuild(noPairs, 0, new int[]{1}, 1, result);
        assertArrayEquals(new int[]{0, 0}, result);
    }

    private static void assertMatchesReference(GameState source, HypotheticalIncome projection,
                                               int me, int[] route) {
        GameState expected = source.copy();
        for (int c : route) if (source.canBuild(c)) expected.owner[c] = me;
        Rules.updateConnections(expected, new Pathing(source.size));
        int[] expectedIncome = new int[2], actualIncome = new int[2];
        Rules.incomePerTurn(expected, expectedIncome);
        projection.afterBuild(source, me, route, route.length, actualIncome);
        assertArrayEquals(expectedIncome, actualIncome);
    }

    private static void assertUnchanged(GameState snapshot, int[][] originalPaths, GameState actual) {
        assertArrayEquals(snapshot.owner, actual.owner);
        assertArrayEquals(snapshot.inked, actual.inked);
        assertArrayEquals(snapshot.instability, actual.instability);
        assertArrayEquals(snapshot.regionInstability, actual.regionInstability);
        assertArrayEquals(snapshot.regionInked, actual.regionInked);
        assertArrayEquals(snapshot.score, actual.score);
        assertEquals(snapshot.turn, actual.turn);
        assertEquals(snapshot.maxTurns, actual.maxTurns);
        assertArrayEquals(snapshot.pairPathLen, actual.pairPathLen);
        for (int p = 0; p < actual.pairCount; p++) {
            assertSame(originalPaths[p], actual.pairPath[p]);
            assertArrayEquals(snapshot.pairPath[p], actual.pairPath[p]);
        }
    }
}
