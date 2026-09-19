package btk.bot.current;

import btk.bot.Params;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProtectedOpeningTest {
    private static Params params() {
        var p = new Params();
        p.USE_PROTECTED_OPENING = true;
        return p;
    }

    /** Three towns on one row, with two protected approaches at the middle town. */
    private static GameState row(int[] terrain, int[][] desired) {
        return new GameState(9, 1, new int[]{0,0,1,2,2,2,3,4,4}, terrain,
                new int[]{0,4,8}, new int[]{0,0,0}, desired);
    }

    private static void observeAll(ProtectedOpening opening, GameState s) {
        var pathing = new Pathing(s.size);
        int[] path = new int[s.size];
        for (int pair = 0; pair < s.pairCount; pair++) {
            int n = pathing.cheapestPath(s, s.townCell[s.pairFrom[pair]], s.townCell[s.pairTo[pair]], path);
            opening.observe(s, pair, path, n);
        }
    }

    private static void assertLegal(GameState s, int me, int[] cells, int count) {
        var actions = new ArrayList<Action>();
        for (int i = 0; i < count; i++) actions.add(Action.place(s.x(cells[i]), s.y(cells[i])));
        var result = new Rules.TurnResult();
        Rules.expand(s, me, actions, new Pathing(s.size), result.moves[me], result);
        assertNull(result.moves[me].error);
        assertTrue(result.moves[me].skipped.isEmpty());
        assertEquals(count, result.moves[me].count);
    }

    @Test void claimsSeveralProtectedApproachesBeforeAnyRouteScoresOnBothSeats() {
        for (int me = 0; me < 2; me++) {
            var s = row(new int[9], new int[][]{{1,2},{2},{}});
            var before = s.copy();
            var opening = new ProtectedOpening(s, params());
            opening.reset(s, me);
            observeAll(opening, s);
            int[] cells = new int[3];
            int count = opening.build(s, cells);
            assertEquals(3, count);
            assertEquals(3, opening.observedPairs);
            assertEquals(4, opening.eligibleCells);
            assertEquals(3, opening.selectedPaint);
            for (int i = 0; i < count; i++) assertTrue(s.regionHasTown(s.regionId[cells[i]]));
            assertTrue(Arrays.stream(cells).anyMatch(c -> c == 1));
            assertTrue(Arrays.stream(cells).anyMatch(c -> c == 7));
            assertEquals(1, Arrays.stream(cells).filter(c -> c == 3 || c == 5).count());
            assertLegal(s, me, cells, count);
            assertArrayEquals(before.owner, s.owner);
            assertArrayEquals(before.inked, s.inked);
            assertArrayEquals(before.instability, s.instability);
            assertArrayEquals(before.regionInstability, s.regionInstability);
            assertArrayEquals(before.regionInked, s.regionInked);
            assertArrayEquals(before.score, s.score);
            assertArrayEquals(before.pairPathLen, s.pairPathLen);
            assertEquals(before.turn, s.turn);
            var projected = s.copy();
            var actions = new ArrayList<Action>();
            for (int i = 0; i < count; i++) actions.add(Action.place(s.x(cells[i]), s.y(cells[i])));
            Rules.resolve(projected, me == 0 ? actions : List.of(Action.waitAction()),
                    me == 1 ? actions : List.of(Action.waitAction()), new Pathing(s.size));
            int[] income = new int[2];
            Rules.incomePerTurn(projected, income);
            assertArrayEquals(new int[2], income, "opening ownership can have zero immediate income");
        }
    }

    @Test void actualTerrainPackingAndZeroTrafficCellsRemainExcluded() {
        var s = row(new int[]{0,1,0,1,0,0,0,0,0}, new int[][]{{1},{},{}});
        var opening = new ProtectedOpening(s, params());
        opening.reset(s, 0);
        observeAll(opening, s);
        int[] cells = new int[3];
        assertEquals(1, opening.build(s, cells));
        assertEquals(1, cells[0]);
        assertEquals(2, opening.selectedPaint);
        // Another two-paint approach does not fit; a zero-demand plains cell is not filler.
        assertLegal(s, 0, cells, 1);
    }

    @Test void duplicateGeometryAndDuplicateDirectedPairsDoNotInflateTraffic() {
        var s = row(new int[9], new int[][]{{1,1},{2},{}});
        var base = new ProtectedOpening(s, params());
        var duplicate = new ProtectedOpening(s, params());
        base.reset(s, 0);
        duplicate.reset(s, 0);
        int[] first = {0,1,2,3,4}, reverse = {4,3,2,1,0}, second = {4,5,6,7,8};
        base.observe(s, 0, first, first.length);
        base.observe(s, 2, second, second.length);
        duplicate.observe(s, 0, first, first.length);
        duplicate.observe(s, 0, reverse, reverse.length);
        duplicate.observe(s, 1, first, first.length);
        duplicate.observe(s, 2, second, second.length);
        int[] expected = new int[3], actual = new int[3];
        assertEquals(base.build(s, expected), duplicate.build(s, actual));
        assertArrayEquals(expected, actual);
        assertEquals(2, duplicate.observedPairs);
        assertEquals(4, duplicate.eligibleCells);
    }

    @Test void sharedDirectedPairTrafficChangesPriorityAndTrueReverseDemandCounts() {
        var s = row(new int[9], new int[][]{{1,2},{},{0}});
        var opening = new ProtectedOpening(s, params());
        opening.reset(s, 1);
        observeAll(opening, s);
        int[] cells = new int[3];
        assertEquals(3, opening.build(s, cells));
        assertEquals(3, opening.observedPairs);
        // Left approaches carry short 0->1 plus both directed long routes; the right
        // approach carries only the two long routes, overcoming P1's high-cell tie order.
        assertEquals(3, cells[0]);
        assertEquals(1, cells[1]);
        assertEquals(7, cells[2]);
    }

    @Test void tiesBreakBySeatAndPredictedCollisionDiscountCanChangeSelectedApproach() {
        var s = row(new int[9], new int[][]{{1},{2},{}});
        for (int me = 0; me < 2; me++) {
            var opening = new ProtectedOpening(s, params());
            opening.reset(s, me);
            observeAll(opening, s);
            int[] cells = new int[3];
            assertEquals(3, opening.build(s, cells));
            assertEquals(me == 0 ? 1 : 7, cells[0]);
            opening.markOpponentPrefix(new int[]{3,5}, 1);
            assertEquals(3, opening.build(s, cells));
            assertFalse(Arrays.stream(cells).anyMatch(c -> c == 3));
            assertTrue(Arrays.stream(cells).anyMatch(c -> c == 5));
        }
    }

    @Test void cityQuotaChargesAllAdjacentTownsNotOnlyOneAssignedTown() {
        var s = new GameState(5, 3, new int[15], new int[15],
                new int[]{0,2,4}, new int[]{1,1,1}, new int[][]{{1,2},{2},{}});
        var opening = new ProtectedOpening(s, params());
        opening.reset(s, 0);
        observeAll(opening, s);
        int[] cells = new int[3];
        assertEquals(1, opening.build(s, cells));
        assertEquals(6, cells[0]);
        assertEquals(1, opening.selectedPaint);
        // Cell 6 touches towns 0 and 1; cell 8 also touches town 1 and is excluded.
    }

    @Test void occupiedInkedUnprotectedAndNonadjacentCellsCannotBeClaimed() {
        var s = row(new int[9], new int[][]{{1,2},{2},{}});
        s.owner[1] = 1;
        s.inked[7] = true;
        var opening = new ProtectedOpening(s, params());
        opening.reset(s, 0);
        // A route from an earlier snapshot cannot make stale cells buildable.
        opening.observe(s, 1, new int[]{0,1,2,3,4,5,6,7,8}, 9);
        int[] cells = new int[3];
        assertEquals(1, opening.build(s, cells));
        assertEquals(3, cells[0]);
        assertLegal(s, 0, cells, 1);
        var noProtection = new GameState(5,1,new int[]{0,1,2,3,4},new int[5],
                new int[]{0,4},new int[]{0,0},new int[][]{{1},{}});
        opening = new ProtectedOpening(noProtection, params());
        opening.reset(noProtection, 0);
        observeAll(opening, noProtection);
        assertEquals(0, opening.build(noProtection, cells));
        var allProtected = new GameState(7,1,new int[7],new int[7],
                new int[]{0,6},new int[]{0,0},new int[][]{{1},{}});
        opening = new ProtectedOpening(allProtected, params());
        opening.reset(allProtected, 0);
        observeAll(opening, allProtected);
        assertEquals(2, opening.build(allProtected, cells));
        assertArrayEquals(new int[]{1,5}, Arrays.copyOf(cells, 2));
        assertEquals(2, opening.eligibleCells, "protected interior cells are not town approaches");
    }

    @Test void noCandidatesDisabledAndEndedWindowReturnFallbackAndResetTelemetry() {
        var s = row(new int[9], new int[][]{{1},{2},{}});
        var opening = new ProtectedOpening(s, params());
        opening.reset(s, 0);
        int[] cells = new int[3];
        assertEquals(0, opening.build(s, cells));
        for (int turn = 0; turn < 3; turn++) {
            s.turn = turn;
            opening.reset(s, 0);
            observeAll(opening, s);
            assertTrue(opening.build(s, cells) > 0);
        }
        s.turn = 3;
        opening.reset(s, 0);
        observeAll(opening, s);
        assertEquals(0, opening.build(s, cells));
        assertEquals(0, opening.observedPairs);
        assertEquals(0, opening.eligibleCells);
        assertEquals(0, opening.selectedPaint);
        s.turn = 0;
        opening = new ProtectedOpening(s, new Params());
        opening.reset(s, 0);
        observeAll(opening, s);
        assertEquals(0, opening.build(s, cells));
    }

    @Test void boundsRejectInvalidOpeningConfiguration() {
        var s = row(new int[9], new int[][]{{1},{2},{}});
        for (int turns : new int[]{0,4}) {
            var p = params(); p.OPENING_TURNS = turns;
            assertThrows(IllegalArgumentException.class, () -> new ProtectedOpening(s, p));
        }
        for (int quota : new int[]{0,4}) {
            var p = params(); p.OPENING_CITY_QUOTA = quota;
            assertThrows(IllegalArgumentException.class, () -> new ProtectedOpening(s, p));
        }
        for (double factor : new double[]{-0.1,1.1,Double.NaN,Double.POSITIVE_INFINITY}) {
            var p = params(); p.OPENING_COLLISION_FACTOR = factor;
            assertThrows(IllegalArgumentException.class, () -> new ProtectedOpening(s, p));
        }
    }
}
