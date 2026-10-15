package btk.core;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BatchedSearchStateTest {
    @Test void repeatedNoncontiguousDestinationsPreserveExactDirectionalPathsAndIncome() {
        GameState source = denseMap();
        Rules.updateConnections(source, new Pathing(source.size));
        GameState before = source.copy();
        SearchState legacy = new SearchState(source), explicitLegacy = new SearchState(source, false);
        SearchState batched = new SearchState(source, true);
        legacy.refresh(); explicitLegacy.refresh(); batched.refresh();
        assertBoard(legacy.state, explicitLegacy.state);
        assertBoard(legacy.state, batched.state);
        assertBoard(before, source);
        // Pair 0 is bottom-left town 0 -> middle-right town 3. N,N,E,E,E,E beats E-first.
        assertArrayEquals(new int[]{20, 15, 10, 11, 12, 13, 14}, path(batched.state, 0));
        // Pair 8 is town 3 -> town 0. S,S,W,W,W,W beats W-first (S precedes W).
        assertArrayEquals(new int[]{14, 19, 24, 23, 22, 21, 20}, path(batched.state, 8));
        for (int p = 0; p < source.pairCount; p++) {
            assertNotSame(source.pairPath[p], batched.state.pairPath[p]);
            assertNotSame(legacy.state.pairPath[p], batched.state.pairPath[p]);
            for (int me = 0; me < 2; me++)
                assertEquals(SearchState.connectionIncomeDifference(legacy.state, p, me),
                        SearchState.connectionIncomeDifference(batched.state, p, me));
        }
    }

    @Test void buffersSurviveDirectMutationsSetAndDisconnectedReconnectedCycles() throws Exception {
        GameState empty = TestMaps.of(new String[]{"0.1.2"}, new int[][]{{2, 1}, {2, 0}, {0, 1}});
        SearchState batched = new SearchState(empty, true), independent = new SearchState(empty, true);
        Pathing internal = internalPathing(batched);
        int[] dist = internal.lastDist();
        Field cache = Pathing.class.getDeclaredField("townDist"); cache.setAccessible(true);
        for (int p = 0; p < empty.pairCount; p++) assertNull(batched.state.pairPath[p]);
        batched.state.owner[1] = 0; batched.state.owner[3] = GameState.NEUTRAL;
        batched.refresh();
        GameState connected = batched.state.copy();
        int[][] buffers = batched.state.pairPath.clone();
        for (int iteration = 0; iteration < 8; iteration++) {
            batched.state.owner[1] = batched.state.owner[3] = GameState.NO_TRACK;
            batched.refresh();
            assertBoard(empty, batched.state);
            batched.state.owner[1] = 0; batched.state.owner[3] = GameState.NEUTRAL;
            batched.refresh();
            assertBoard(connected, batched.state);
            for (int p = 0; p < empty.pairCount; p++) assertSame(buffers[p], batched.state.pairPath[p]);
            batched.set(empty); batched.refresh();
            assertBoard(empty, batched.state);
            batched.set(connected); batched.refresh();
            for (int p = 0; p < empty.pairCount; p++) assertSame(buffers[p], batched.state.pairPath[p]);
            batched.set(batched.state); batched.refresh();
            assertBoard(connected, batched.state);
            assertSame(dist, internal.lastDist());
            assertNull(cache.get(internal), "Batched search must not allocate a destination-by-cell cache");
            assertBoard(empty, independent.state);
        }
        assertArrayEquals(new int[]{-1, -1, -1, -1, -1}, empty.owner);
    }

    @Test void refreshedDistancesNeverLeakAcrossInkRemovalAndBoardCopies() {
        GameState source = denseMap();
        Rules.updateConnections(source, new Pathing(source.size));
        GameState snapshot = source.copy();
        SearchState legacy = new SearchState(source), batched = new SearchState(source, true);
        // Same destinations, different removed connectors: a cached distance map would become stale.
        for (int removed = 0; removed < source.size; removed++) {
            if (source.isTown(removed)) continue;
            legacy.set(source); batched.set(source);
            Rules.ink(legacy.state, source.regionId[removed]);
            Rules.ink(batched.state, source.regionId[removed]);
            legacy.refresh(); batched.refresh();
            assertBoard(legacy.state, batched.state);
            assertEquals(Rules.gameOver(legacy.state, new Pathing(source.size)),
                    Rules.gameOver(batched.state, new Pathing(source.size)));
            assertBoard(snapshot, source);
        }
    }

    @Test void simultaneousPlacementsCollisionsAndDoubleHitsMatchReferenceIncludingClosingInk() {
        for (int instability : new int[]{0, 2}) {
            GameState reference = TestMaps.of(new String[]{"0...1"}, new int[][]{{1}, {0}});
            TestMaps.track(reference, 0, 1, 0);
            TestMaps.track(reference, 1, 3, 0);
            for (int i = 0; i < instability; i++) Rules.disrupt(reference, 2);
            Rules.updateConnections(reference, new Pathing(reference.size));
            SearchState legacy = new SearchState(reference), batched = new SearchState(reference, true);
            int[] cell = {2};
            boolean oldEnd = legacy.resolve(cell, 1, 2, cell, 1, 2);
            boolean newEnd = batched.resolve(cell, 1, 2, cell, 1, 2);
            Rules.resolve(reference, List.of(Action.place(2, 0), Action.disrupt(2)),
                    List.of(Action.place(2, 0), Action.disrupt(2)), new Pathing(reference.size));
            assertBoard(reference, legacy.state); assertBoard(reference, batched.state);
            assertEquals(oldEnd, newEnd);
            assertEquals(instability == 2, newEnd);
            assertEquals(instability == 2 ? GameState.NO_TRACK : GameState.NEUTRAL, batched.state.owner[2]);
            assertEquals(instability == 0, batched.state.pairActive(0));
        }
    }

    @Test void persistentLegalTransitionsMatchLegacyAndCoreForBothPlayers() {
        Random random = new Random(241015);
        for (int episode = 0; episode < 12; episode++) {
            GameState reference = denseMap();
            for (int c = 0; c < reference.size; c++)
                if (!reference.isTown(c) && random.nextBoolean()) reference.owner[c] = GameState.NO_TRACK;
            reference.score[0] = episode * 3; reference.score[1] = episode;
            reference.maxTurns = 15;
            Pathing referencePathing = new Pathing(reference.size);
            Rules.updateConnections(reference, referencePathing);
            SearchState legacy = new SearchState(reference), batched = new SearchState(reference, true);
            for (int turn = 0; turn < reference.maxTurns; turn++) {
                int[][] cells = new int[2][GameState.PAINT_PER_TURN];
                int[] counts = new int[2], targets = {-1, -1};
                List<List<Action>> actions = List.of(new ArrayList<>(), new ArrayList<>());
                for (int me = 0; me < 2; me++) {
                    int paint = GameState.PAINT_PER_TURN, start = random.nextInt(reference.size);
                    for (int i = 0; i < reference.size && paint > 0; i++) {
                        int c = (start + i) % reference.size;
                        if (!reference.canBuild(c) || reference.placeCost(c) > paint) continue;
                        cells[me][counts[me]++] = c; paint -= reference.placeCost(c);
                        actions.get(me).add(Action.place(reference.x(c), reference.y(c)));
                    }
                    start = random.nextInt(reference.regionCount);
                    for (int i = 0; i < reference.regionCount; i++) {
                        int r = (start + i) % reference.regionCount;
                        if (reference.regionInked[r] || reference.regionHasTown(r)) continue;
                        targets[me] = r; actions.get(me).add(Action.disrupt(r)); break;
                    }
                    if (actions.get(me).isEmpty()) actions.get(me).add(Action.waitAction());
                }
                boolean oldEnd = legacy.resolve(cells[0], counts[0], targets[0], cells[1], counts[1], targets[1]);
                boolean newEnd = batched.resolve(cells[0], counts[0], targets[0], cells[1], counts[1], targets[1]);
                Rules.TurnResult result = Rules.resolve(reference, actions.get(0), actions.get(1), referencePathing);
                assertNull(result.moves[0].error); assertNull(result.moves[1].error);
                assertEquals(oldEnd, newEnd);
                assertEquals(Rules.gameOver(reference, referencePathing), newEnd);
                assertBoard(reference, legacy.state); assertBoard(reference, batched.state);
                if (newEnd) break;
            }
        }
    }

    @Test void emptyDesiredSetsAndSelfPairsKeepLegacyNullSemantics() {
        for (int[][] desired : new int[][][]{{{}, {}}, {{0, 1}, {1}}}) {
            GameState source = TestMaps.of(new String[]{"0.1"}, desired);
            source.owner[1] = GameState.NEUTRAL;
            SearchState legacy = new SearchState(source), batched = new SearchState(source, true);
            legacy.refresh(); batched.refresh();
            assertBoard(legacy.state, batched.state);
            for (int p = 0; p < source.pairCount; p++)
                if (source.pairFrom[p] == source.pairTo[p]) assertNull(batched.state.pairPath[p]);
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
