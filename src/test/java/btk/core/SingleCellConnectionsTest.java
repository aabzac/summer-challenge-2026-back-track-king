package btk.core;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SingleCellConnectionsTest {
    private static int[] collect(GameState s) {
        int[] out = new int[s.size];
        int n = NetworkCells.collectSingleCellConnections(s, out);
        return Arrays.copyOf(out, n);
    }

    private static boolean contains(int[] cells, int wanted) {
        for (int c : cells) if (c == wanted) return true;
        return false;
    }

    @Test void handlesCornersAndSingleRowOrColumnWithoutWrapping() {
        GameState corners = TestMaps.of(new String[]{".0", "1."}, new int[][]{{1}, {}});
        assertArrayEquals(new int[]{0, 3}, collect(corners));
        GameState row = TestMaps.of(new String[]{"0.1"}, new int[][]{{1}, {}});
        assertArrayEquals(new int[]{1}, collect(row));
        GameState column = TestMaps.of(new String[]{"0", ".", "1"}, new int[][]{{1}, {}});
        assertArrayEquals(new int[]{1}, collect(column));
        GameState wrap = TestMaps.of(new String[]{".0..", "....", "...1"}, new int[][]{{1}, {}});
        TestMaps.track(wrap, 0, 2, 0, 0, 1);
        assertFalse(contains(collect(wrap), wrap.cell(3, 0)), "next row is not an eastern neighbor");
        TestMaps.track(wrap, 1, 3, 1);
        assertFalse(contains(collect(wrap), wrap.cell(0, 2)), "previous row is not a western neighbor");
    }

    @Test void includesAllAffordableTerrainAndConnectorOwnersButOnlyLegalEmptyCells() {
        GameState s = TestMaps.of(new String[]{"0.....1", "..~^...", "......."}, new int[][]{{1}, {}});
        for (int x = 0; x < s.width; x++) {
            if (!s.isTown(s.cell(x, 0))) s.owner[s.cell(x, 0)] = x % 2;
            s.owner[s.cell(x, 2)] = GameState.NEUTRAL;
        }
        Rules.ink(s, s.regionId[s.cell(4, 1)]);
        s.owner[s.cell(5, 1)] = 0;
        s.owner[s.cell(6, 1)] = 1;
        int[] beforeOwners = s.owner.clone();
        boolean[] beforeInk = s.inked.clone();
        assertArrayEquals(new int[]{7, 8, 9, 10}, collect(s));
        assertEquals(2, s.placeCost(9));
        assertEquals(GameState.PAINT_PER_TURN, s.placeCost(10));
        assertArrayEquals(beforeOwners, s.owner);
        assertArrayEquals(beforeInk, s.inked);
    }

    @Test void excludesIsolatedCellsAndDeadEndExtensions() {
        GameState s = TestMaps.of(new String[]{"0....", ".....", "....1"}, new int[][]{{1}, {}});
        assertArrayEquals(new int[0], collect(s));
        TestMaps.track(s, 0, 1, 1);
        assertFalse(contains(collect(s), s.cell(2, 1)), "one neighboring rail cannot change a town path");
        assertFalse(contains(collect(s), s.cell(3, 1)), "isolated empty cell cannot change a town path");
    }

    @Test void sumsOnlyOrthogonalConnectorWeightsAtCornersEdgesAndCenter() {
        GameState s = TestMaps.of(new String[]{"0..", "...", "..1"}, new int[][]{{1}, {}});
        TestMaps.track(s, 0, 1, 0, 1, 2);
        TestMaps.track(s, 1, 0, 1);
        TestMaps.track(s, GameState.NEUTRAL, 2, 1);
        int[] weights = {1, 2, 1000, 4, 1000, 8, 1000, 16, 32};
        assertEquals(6, NetworkCells.adjacentConnectorWeight(s, 0, weights));
        assertEquals(10, NetworkCells.adjacentConnectorWeight(s, 2, weights), "no wrapping into the next row");
        assertEquals(20, NetworkCells.adjacentConnectorWeight(s, 6, weights), "no wrapping into the previous row");
        assertEquals(24, NetworkCells.adjacentConnectorWeight(s, 8, weights));
        assertEquals(30, NetworkCells.adjacentConnectorWeight(s, 4, weights));
        assertEquals(1, NetworkCells.adjacentConnectorWeight(s, 1, weights), "town counts, empty neighbors do not");
        assertEquals(32, NetworkCells.adjacentConnectorWeight(s, 5, weights));
        assertArrayEquals(new int[]{1, 2, 1000, 4, 1000, 8, 1000, 16, 32}, weights);
    }

    @Test void retainsShortcutWithinAnAlreadyConnectedRailComponent() {
        GameState s = TestMaps.of(new String[]{".....", "0...1", "....."}, new int[][]{{1}, {}});
        TestMaps.track(s, 1, 1, 1, 1, 0, 2, 0, 3, 0, 3, 1);
        Pathing p = new Pathing(s.size);
        Rules.updateConnections(s, p);
        assertTrue(s.pairActive(0));
        int beforeLength = s.pairPathLen[0];
        int chord = s.cell(2, 1);
        assertTrue(contains(collect(s), chord));
        SearchState changed = new SearchState(s);
        changed.state.owner[chord] = 0;
        changed.refresh();
        assertTrue(changed.state.pairPathLen[0] < beforeLength);
        int[] income = new int[2];
        Rules.incomePerTurn(changed.state, income);
        assertArrayEquals(new int[]{1, 2}, income);
    }

    @Test void retainsEqualLengthAlternativeThatWinsDirectionalTieBreak() {
        GameState s = TestMaps.of(new String[]{"0.", ".1"}, new int[][]{{1}, {}});
        TestMaps.track(s, 1, 0, 1);
        Rules.updateConnections(s, new Pathing(s.size));
        int beforeLength = s.pairPathLen[0];
        int candidate = s.cell(1, 0);
        assertTrue(contains(collect(s), candidate));
        SearchState changed = new SearchState(s);
        changed.state.owner[candidate] = 0;
        changed.refresh();
        assertEquals(beforeLength, changed.state.pairPathLen[0]);
        assertEquals(candidate, changed.state.pairPath[0][1], "EAST wins over SOUTH");
        int[] income = new int[2];
        Rules.incomePerTurn(changed.state, income);
        assertArrayEquals(new int[]{1, 0}, income);
    }

    @Test void everyBeneficialSinglePlacementIsCollectedAcrossAllSmallBoardStates() {
        // Four variable cells, each empty, P0, P1, neutral or inked: exhaust all 5^4 states.
        int[] variable = {1, 2, 3, 4};
        int positiveCases = 0;
        for (int encoding = 0; encoding < 625; encoding++) {
            GameState s = TestMaps.of(new String[]{"0~^", "~^1"}, new int[][]{{1}, {0}});
            int rest = encoding;
            for (int c : variable) {
                int state = rest % 5;
                rest /= 5;
                if (state == 4) Rules.ink(s, s.regionId[c]);
                else s.owner[c] = state - 1;
            }
            Rules.updateConnections(s, new Pathing(s.size));
            int[] candidates = collect(s);
            int[] before = new int[2];
            Rules.incomePerTurn(s, before);
            SearchState changed = new SearchState(s);
            for (int me = 0; me < 2; me++) {
                for (int c : variable) {
                    if (!s.canBuild(c)) continue;
                    changed.set(s);
                    changed.state.owner[c] = me;
                    changed.refresh();
                    int[] after = new int[2];
                    Rules.incomePerTurn(changed.state, after);
                    boolean ownIncomeImproves = after[me] > before[me];
                    boolean differenceImproves = after[me] - after[1 - me] > before[me] - before[1 - me];
                    if (ownIncomeImproves || differenceImproves) {
                        positiveCases++;
                        assertTrue(contains(candidates, c), "missed state=" + encoding + " player=" + me + " cell=" + c);
                    }
                }
            }
        }
        assertTrue(positiveCases > 0, "property must exercise beneficial placements");
    }
}
