package btk.core;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InkRefreshTest {
    @Test void everyRegionMatchesFullRefreshWithRepeatedDestinationsAndDirectionalTies() {
        GameState source = denseMap();
        Rules.updateConnections(source, new Pathing(source.size));
        GameState snapshot = source.copy();
        for (boolean batched : new boolean[]{false, true}) {
            SearchState expected = new SearchState(source, batched);
            SearchState actual = new SearchState(source, batched);
            for (int region = 0; region < source.regionCount; region++) {
                expected.set(source); actual.set(source);
                Rules.ink(expected.state, region); expected.refresh();
                actual.inkAndRefresh(region);
                assertBoard(expected.state, actual.state);
                assertBoard(snapshot, source);
            }
            actual.set(source); actual.inkAndRefresh(15);
            // Removing the old N-first route leaves the shortest E,N,N,E,E,E route.
            assertArrayEquals(new int[]{20, 21, 16, 11, 12, 13, 14}, path(actual.state, 0));
        }
    }

    @Test void deletionOutsideCanonicalPathsReusesPathsAndDoesNotRunBfs() throws Exception {
        GameState source = TestMaps.of(new String[]{"0.1", "..."}, new int[][]{{1}, {0}});
        TestMaps.track(source, 0, 1, 0);
        TestMaps.track(source, GameState.NEUTRAL, 1, 1);
        Rules.updateConnections(source, new Pathing(source.size));
        for (boolean batched : new boolean[]{false, true}) {
            SearchState actual = new SearchState(source, batched);
            int[][] buffers = actual.state.pairPath.clone();
            int[] distances = internalPathing(actual).lastDist();
            Arrays.fill(distances, 731);
            actual.inkAndRefresh(4);
            for (int p = 0; p < source.pairCount; p++) {
                assertSame(buffers[p], actual.state.pairPath[p]);
                assertArrayEquals(path(source, p), path(actual.state, p));
            }
            for (int distance : distances) assertEquals(731, distance);
            assertTrue(actual.state.inked[4]);
            assertEquals(GameState.NO_TRACK, actual.state.owner[4]);
        }
    }

    @Test void freeCellInkKeepsInactivePathsButStillChangesTerminalReachability() throws Exception {
        GameState source = TestMaps.of(new String[]{"0.1"}, new int[][]{{1}, {0}});
        Rules.updateConnections(source, new Pathing(source.size));
        assertTrue(Rules.anyPairPossible(source, new Pathing(source.size)));
        for (boolean batched : new boolean[]{false, true}) {
            SearchState actual = new SearchState(source, batched);
            Arrays.fill(internalPathing(actual).lastDist(), 731);
            actual.inkAndRefresh(1);
            for (int distance : internalPathing(actual).lastDist()) assertEquals(731, distance);
            for (int p = 0; p < source.pairCount; p++) {
                assertNull(actual.state.pairPath[p]);
                assertEquals(0, actual.state.pairPathLen[p]);
            }
            assertFalse(Rules.anyPairPossible(actual.state, new Pathing(source.size)),
                    "Unchanged active paths do not imply unchanged game-over reachability");
            assertTrue(Rules.gameOver(actual.state, new Pathing(source.size)));
        }
    }

    @Test void townRegionInkPreservesPassableTownButRemovesOtherRegionTracks() {
        for (boolean removeTrack : new boolean[]{false, true}) {
            GameState source = TestMaps.of(new String[]{"0.1", "..."}, new int[][]{{1}, {0}});
            for (int c = 0; c < source.size; c++) source.owner[c] = c % 3;
            if (removeTrack) source.regionId[1] = source.regionId[0];
            Rules.updateConnections(source, new Pathing(source.size));
            assertTrue(source.regionHasTown(0));
            for (boolean batched : new boolean[]{false, true}) {
                SearchState expected = new SearchState(source, batched);
                SearchState actual = new SearchState(source, batched);
                Rules.ink(expected.state, 0); expected.refresh();
                actual.inkAndRefresh(0);
                assertBoard(expected.state, actual.state);
                assertTrue(actual.state.inked[0]);
                assertTrue(actual.state.passable(0));
                assertEquals(GameState.NO_TRACK, actual.state.owner[0]);
                assertTrue(actual.state.pairActive(0));
                assertArrayEquals(removeTrack ? new int[]{0, 3, 4, 5, 2} : new int[]{0, 1, 2},
                        path(actual.state, 0));
            }
        }
    }

    @Test void buffersAndSourcesSurviveDisappearResetRepeatedInkAndSelfSet() throws Exception {
        GameState empty = TestMaps.of(new String[]{"0.1.2"}, new int[][]{{2, 1}, {2, 0}, {0, 1}});
        GameState source = empty.copy();
        source.owner[1] = 0; source.owner[3] = GameState.NEUTRAL;
        Rules.updateConnections(source, new Pathing(source.size));
        GameState snapshot = source.copy();
        for (boolean batched : new boolean[]{false, true}) {
            SearchState actual = new SearchState(source, batched);
            int[][] buffers = actual.state.pairPath.clone();
            int[] distances = internalPathing(actual).lastDist();
            Field cache = Pathing.class.getDeclaredField("townDist"); cache.setAccessible(true);
            for (int iteration = 0; iteration < 8; iteration++) {
                actual.inkAndRefresh(1);
                GameState after = source.copy(); Rules.ink(after, 1);
                Rules.updateConnections(after, new Pathing(source.size));
                assertBoard(after, actual.state);
                actual.inkAndRefresh(1); assertBoard(after, actual.state);
                actual.set(actual.state); actual.inkAndRefresh(1); assertBoard(after, actual.state);
                actual.set(empty); actual.inkAndRefresh(3);
                for (int p = 0; p < actual.state.pairCount; p++) assertNull(actual.state.pairPath[p]);
                actual.set(source);
                for (int p = 0; p < source.pairCount; p++) {
                    assertSame(buffers[p], actual.state.pairPath[p]);
                    assertNotSame(source.pairPath[p], actual.state.pairPath[p]);
                }
                assertSame(distances, internalPathing(actual).lastDist());
                assertNull(cache.get(internalPathing(actual)), "No destination-by-cell cache is allocated");
                assertBoard(snapshot, source);
            }
        }
    }

    @Test void persistentBuildAndInkTransitionsMatchFullRefreshForBothModes() {
        Random random = new Random(261018);
        for (int episode = 0; episode < 12; episode++) {
            GameState source = denseMap();
            for (int c = 0; c < source.size; c++)
                if (!source.isTown(c) && random.nextBoolean()) source.owner[c] = GameState.NO_TRACK;
            Rules.updateConnections(source, new Pathing(source.size));
            for (boolean batched : new boolean[]{false, true}) {
                SearchState expected = new SearchState(source, batched);
                SearchState actual = new SearchState(source, batched);
                for (int turn = 0; turn < 15; turn++) {
                    // Establish a fresh canonical state after legal affordable construction.
                    for (int me = 0; me < 2; me++) {
                        int paint = GameState.PAINT_PER_TURN, start = random.nextInt(source.size);
                        for (int i = 0; i < source.size && paint > 0; i++) {
                            int cell = (start + i) % source.size;
                            if (!expected.state.canBuild(cell) || expected.state.placeCost(cell) > paint) continue;
                            expected.state.owner[cell] = actual.state.owner[cell] = me;
                            paint -= expected.state.placeCost(cell);
                        }
                    }
                    expected.refresh(); actual.refresh();
                    int region = random.nextInt(source.regionCount);
                    while (source.regionHasTown(region)) region = (region + 1) % source.regionCount;
                    Rules.ink(expected.state, region); expected.refresh();
                    actual.inkAndRefresh(region);
                    Rules.scoreTurn(expected.state, null); Rules.scoreTurn(actual.state, null);
                    expected.state.turn++; actual.state.turn++;
                    assertBoard(expected.state, actual.state);
                    assertEquals(Rules.gameOver(expected.state, new Pathing(source.size)),
                            Rules.gameOver(actual.state, new Pathing(source.size)));
                }
            }
        }
    }

    @Test void emptyDesiredSetsAndSelfPairsRetainNullSemantics() {
        for (int[][] desired : new int[][][]{{{}, {}}, {{0, 1}, {1}}}) {
            GameState source = TestMaps.of(new String[]{"0.1"}, desired);
            source.owner[1] = GameState.NEUTRAL;
            for (boolean batched : new boolean[]{false, true}) {
                SearchState expected = new SearchState(source, batched);
                SearchState actual = new SearchState(source, batched);
                Rules.ink(expected.state, 1); expected.refresh(); actual.inkAndRefresh(1);
                assertBoard(expected.state, actual.state);
                for (int p = 0; p < source.pairCount; p++) assertNull(actual.state.pairPath[p]);
            }
        }
    }

    private static GameState denseMap() {
        GameState state = TestMaps.of(new String[]{"..1..", ".~.^.", "2...3", ".^.~.", "0...."},
                new int[][]{{3, 2, 1}, {3, 2, 0}, {3, 0}, {0, 1, 2}});
        for (int c = 0; c < state.size; c++) if (!state.isTown(c)) state.owner[c] = c % 3;
        return state;
    }

    private static Pathing internalPathing(SearchState state) throws Exception {
        Field field = SearchState.class.getDeclaredField("pathing"); field.setAccessible(true);
        return (Pathing) field.get(state);
    }

    private static int[] path(GameState state, int pair) {
        return state.pairPath[pair] == null ? null : Arrays.copyOf(state.pairPath[pair], state.pairPathLen[pair]);
    }

    private static void assertBoard(GameState expected, GameState actual) {
        assertArrayEquals(expected.owner, actual.owner); assertArrayEquals(expected.inked, actual.inked);
        assertArrayEquals(expected.instability, actual.instability);
        assertArrayEquals(expected.regionInstability, actual.regionInstability);
        assertArrayEquals(expected.regionInked, actual.regionInked);
        assertArrayEquals(expected.score, actual.score);
        assertEquals(expected.turn, actual.turn); assertEquals(expected.maxTurns, actual.maxTurns);
        assertArrayEquals(expected.pairPathLen, actual.pairPathLen);
        for (int p = 0; p < expected.pairCount; p++) assertArrayEquals(path(expected, p), path(actual, p));
        int[] expectedIncome = new int[2], actualIncome = new int[2];
        Rules.incomePerTurn(expected, expectedIncome); Rules.incomePerTurn(actual, actualIncome);
        assertArrayEquals(expectedIncome, actualIncome);
    }
}
