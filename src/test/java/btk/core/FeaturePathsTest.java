package btk.core;

import btk.bot.neural.NeuralV5Features;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FeaturePathsTest {
    @Test void exhaustiveSmallBoardsMatchFrozenFreshSearchInDifferentTargetOrders() {
        GameState s = TestMaps.of(new String[]{"0~^", ".~1"}, new int[][]{{1}, {0}});
        FeaturePaths actual = new FeaturePaths(s.width, s.height);
        NeuralV5Features reference = new NeuralV5Features(s);
        int[] variable = {1, 2, 3, 4}, targets = {0, 5, 2, 4, 1, 3, 3, 0, 5};
        boolean[] block = new boolean[s.size];
        for (int encoding = 0; encoding < 625; encoding++) {
            Arrays.fill(s.owner, GameState.NO_TRACK);
            Arrays.fill(s.inked, false);
            int rest = encoding;
            for (int c : variable) {
                int value = rest % 5;
                rest /= 5;
                if (value == 4) s.inked[c] = true;
                else s.owner[c] = value - 1;
            }
            for (int mode = 0; mode < 4; mode++) {
                for (int c = 0; c < s.size; c++)
                    block[c] = mode == 1 ? s.owner[c] == 0 : mode == 2 ? s.owner[c] == 1 : c % 2 == 0;
                boolean[] mask = mode == 0 ? null : block;
                actual.reset();
                for (int from = 0; from < s.size; from++) {
                    // Rotations include every destination, repeats and targets settled by earlier requests.
                    for (int to : targets) assertRoute(reference, actual, s, from, (to + encoding) % s.size, mask);
                }
            }
        }
    }

    @Test void randomBoardsMasksAndQueryPermutationsMatchFrozenSearch() {
        Random random = new Random(0x6fe47eL);
        for (int iteration = 0; iteration < 100; iteration++) {
            int width = 1 + random.nextInt(7), height = 1 + random.nextInt(8);
            GameState s = board(width, height);
            boolean[] block = new boolean[s.size];
            for (int c = 0; c < s.size; c++) {
                s.terrain[c] = random.nextInt(3);
                s.owner[c] = random.nextInt(4) - 1;
                s.inked[c] = random.nextInt(6) == 0;
                block[c] = random.nextInt(5) == 0;
            }
            FeaturePaths actual = new FeaturePaths(width, height);
            NeuralV5Features reference = new NeuralV5Features(s);
            for (int source = 0; source < 3; source++) {
                int from = random.nextInt(s.size);
                for (int query = 0; query < 3 * s.size; query++)
                    assertRoute(reference, actual, s, from, random.nextInt(s.size), block);
                // Every destination is also checked after the search has run as far as random queries needed.
                for (int to = s.size - 1; to >= 0; to--) assertRoute(reference, actual, s, from, to, block);
            }
        }
    }

    @Test void pendingTargetExpandsWhenHeapEmptyAndSurvivesQueriesForSettledTargets() {
        GameState s = board(5, 1);
        FeaturePaths actual = new FeaturePaths(5, 1);
        NeuralV5Features reference = new NeuralV5Features(s);
        for (int to : new int[]{0, 0, 1, 0, 1, 2, 0, 4, 2, 4})
            assertRoute(reference, actual, s, 0, to, null);

        actual.reset();
        s.inked[3] = true;
        for (int to : new int[]{1, 0, 4, 2, 4, 0}) assertRoute(reference, actual, s, 0, to, null);

        GameState column = board(1, 5);
        FeaturePaths vertical = new FeaturePaths(1, 5);
        NeuralV5Features verticalReference = new NeuralV5Features(column);
        for (int to : new int[]{4, 3, 4, 0, 2})
            assertRoute(verticalReference, vertical, column, 4, to, null);
    }

    @Test void equalCostAndZeroCostCycleRoutesKeepExactParentChoices() {
        GameState s = board(3, 3);
        FeaturePaths actual = new FeaturePaths(3, 3);
        NeuralV5Features reference = new NeuralV5Features(s);
        int[] route = new int[s.size];
        assertEquals(3, actual.path(s, 4, 0, route, null));
        assertArrayEquals(new int[]{4, 1, 0}, Arrays.copyOf(route, 3));
        for (int to : new int[]{8, 2, 6, 0, 4}) assertRoute(reference, actual, s, 4, to, null);

        Arrays.fill(s.owner, GameState.NEUTRAL);
        actual.reset();
        assertEquals(5, actual.path(s, 8, 0, route, null));
        assertArrayEquals(new int[]{8, 5, 2, 1, 0}, Arrays.copyOf(route, 5));
        for (int to : new int[]{8, 4, 3, 6, 2, 0, 7, 5, 1})
            assertRoute(reference, actual, s, 8, to, null);
    }

    @Test void sourceIsAdmittedEvenWhenBlockedOrInkedButOtherSuchCellsAreNot() {
        GameState s = TestMaps.of(new String[]{"0..1", "...."}, new int[][]{{1}, {0}});
        boolean[] block = new boolean[s.size];
        block[0] = block[3] = true;
        s.inked[0] = s.inked[4] = true;
        s.owner[4] = 0; // Synthetic inked track must still be impassable to the feature Dijkstra.
        FeaturePaths actual = new FeaturePaths(s.width, s.height);
        NeuralV5Features reference = new NeuralV5Features(s);
        for (int from : new int[]{0, 4, 3, 0})
            for (int to : new int[]{from, 2, 0, 4, 3, 7})
                assertRoute(reference, actual, s, from, to, block);
    }

    @Test void resetInvalidatesSameSourceAfterBoardMaskAndStateObjectChanges() {
        GameState s = board(4, 2);
        boolean[] block = new boolean[s.size];
        FeaturePaths actual = new FeaturePaths(s.width, s.height);
        NeuralV5Features reference = new NeuralV5Features(s);
        assertRoute(reference, actual, s, 0, 7, block);
        Arrays.fill(s.owner, 1);
        actual.reset();
        assertRoute(reference, actual, s, 0, 7, block);
        block[1] = block[2] = true;
        actual.reset();
        assertRoute(reference, actual, s, 0, 3, block);
        s.inked[4] = true;
        actual.reset();
        assertRoute(reference, actual, s, 0, 7, block);
        GameState copy = s.copy();
        copy.inked[4] = false;
        copy.owner[4] = GameState.NO_TRACK;
        actual.reset();
        assertRoute(reference, actual, copy, 0, 7, block);
        actual.reset();
        assertRoute(reference, actual, copy, 0, 3, null);
        // Switching the source itself resets automatically, including switching back.
        for (int from : new int[]{7, 2, 0, 7}) assertRoute(reference, actual, copy, from, 5, null);
    }

    @Test void freshQueriesRetainAllTentativeCostsExactlyWithoutMutatingInputs() {
        GameState s = TestMaps.of(new String[]{"0~..", ".^^.", "...1"}, new int[][]{{1}, {0}});
        s.owner[1] = 1;
        s.owner[9] = GameState.NEUTRAL;
        s.inked[6] = true;
        boolean[] block = new boolean[s.size];
        block[5] = true;
        GameState snapshot = s.copy();
        boolean[] originalMask = block.clone();
        FeaturePaths actual = new FeaturePaths(s.width, s.height);
        NeuralV5Features reference = new NeuralV5Features(s);
        for (int from = 0; from < s.size; from++) {
            for (int to = 0; to < s.size; to++) {
                actual.reset();
                assertRoute(reference, actual, s, from, to, block);
                for (int cell = 0; cell < s.size; cell++)
                    assertEquals(reference.lastCost(cell), actual.cost(cell),
                            "from=" + from + " to=" + to + " tentative cell=" + cell);
            }
        }
        assertArrayEquals(snapshot.owner, s.owner);
        assertArrayEquals(snapshot.terrain, s.terrain);
        assertArrayEquals(snapshot.inked, s.inked);
        assertArrayEquals(snapshot.regionId, s.regionId);
        assertArrayEquals(originalMask, block);
    }

    private static GameState board(int width, int height) {
        return new GameState(width, height, new int[width * height], new int[width * height],
                new int[0], new int[0], new int[0][]);
    }

    private static void assertRoute(NeuralV5Features reference, FeaturePaths actual, GameState s,
                                    int from, int to, boolean[] mask) {
        int[] expected = new int[s.size], result = new int[s.size];
        int expectedSize = reference.cheapestCostPath(s, from, to, expected, mask);
        int size = actual.path(s, from, to, result, mask);
        assertEquals(expectedSize, size, "route length from=" + from + " to=" + to);
        assertEquals(reference.lastCost(to), actual.cost(to), "target cost from=" + from + " to=" + to);
        for (int i = 0; i < size; i++) assertEquals(expected[i], result[i],
                "route cell=" + i + " from=" + from + " to=" + to);
    }
}
