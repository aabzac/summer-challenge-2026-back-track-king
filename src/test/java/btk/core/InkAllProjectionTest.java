package btk.core;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class InkAllProjectionTest {
    @Test void immediateTerminationPreservesExactlyTheAlreadyEarnedScores() {
        GameState source = TestMaps.of(new String[]{"0...1"}, new int[][]{{1}, {}});
        source.owner[1] = source.owner[2] = 0;
        source.owner[3] = 1;
        source.score[0] = 71;
        source.score[1] = 63;
        source.turn = 20;
        Rules.updateConnections(source, new Pathing(source.size));
        SearchState scratch = new SearchState(source);
        int[] out = new int[2];
        InkProjection.scores(source, scratch, new Pathing(source.size), out);
        assertArrayEquals(new int[]{71, 63}, out);
        assertTrue(Rules.gameOver(scratch.state, new Pathing(source.size)));
        assertFalse(scratch.state.inked[source.townCell[0]]);
        assertFalse(scratch.state.inked[source.townCell[1]]);
        for (int c = 1; c <= 3; c++) assertTrue(scratch.state.inked[c]);
        assertEquals(0, source.owner[1]);
        assertFalse(source.regionInked[2], "projection leaves the original board untouched");
        assertTrue(source.pairActive(0));
        assertArrayEquals(new int[]{71, 63}, source.score);
    }

    @Test void protectedConnectionsEarnExactDirectedPairIncomeUntilTheTurnCap() {
        GameState source = protectedCorridor();
        source.owner[1] = source.owner[2] = 0;
        source.owner[3] = 1;
        Arrays.fill(source.owner, 5, 10, 1);
        source.score[0] = 11;
        source.score[1] = 19;
        source.turn = 8;
        source.maxTurns = 12;
        Rules.updateConnections(source, new Pathing(source.size));
        SearchState scratch = new SearchState(source);
        int[] out = new int[2];
        InkProjection.scores(source, scratch, new Pathing(source.size), out);
        assertArrayEquals(new int[]{27, 27}, out, "two directed links earn 4/2 for four remaining turns");
        assertFalse(Rules.gameOver(scratch.state, new Pathing(source.size)));
        for (int c = 0; c < 5; c++) assertFalse(scratch.state.inked[c], "entire town region is protected");
        for (int c = 5; c < 10; c++) assertTrue(scratch.state.inked[c]);
        // Same total as the actual unchanged-board termination/scoring process.
        GameState frozen = scratch.state.copy();
        Pathing pathing = new Pathing(source.size);
        while (!Rules.gameOver(frozen, pathing)) {
            Rules.scoreTurn(frozen, null);
            frozen.turn++;
        }
        assertArrayEquals(frozen.score, out);
    }

    @Test void remainingPossibleButUnbuiltProtectedRouteHasNoStaticIncome() {
        GameState source = protectedCorridor();
        source.owner[1] = 0;
        source.owner[3] = 1;
        for (int c = 5; c < 10; c++) source.owner[c] = 1;
        source.score[0] = 5;
        source.score[1] = 13;
        source.turn = 3;
        Rules.updateConnections(source, new Pathing(source.size));
        assertTrue(source.pairActive(0), "bottom rails currently connect the towns");
        SearchState scratch = new SearchState(source);
        int[] out = new int[2];
        InkProjection.scores(source, scratch, new Pathing(source.size), out);
        assertFalse(Rules.gameOver(scratch.state, new Pathing(source.size)), "the protected free corridor can still be built");
        assertFalse(scratch.state.pairActive(0));
        assertArrayEquals(new int[]{5, 13}, out, "projection freezes construction after inking");
    }

    @Test void turnCapAndScratchReuseDoNotAddAnotherScoringTurn() {
        GameState source = protectedCorridor();
        source.owner[1] = source.owner[2] = source.owner[3] = 0;
        source.score[0] = 20;
        source.score[1] = 7;
        source.turn = source.maxTurns;
        SearchState scratch = new SearchState(source);
        int[] out = new int[2];
        InkProjection.scores(source, scratch, new Pathing(source.size), out);
        assertArrayEquals(new int[]{20, 7}, out);
        source.turn = source.maxTurns - 1;
        InkProjection.scores(source, scratch, new Pathing(source.size), out);
        assertArrayEquals(new int[]{26, 7}, out);
        assertThrows(IllegalArgumentException.class,
                () -> InkProjection.scores(source, scratch, new Pathing(source.size), source.score));
        assertThrows(IllegalArgumentException.class,
                () -> InkProjection.scores(scratch.state, scratch, new Pathing(source.size), out));
    }

    private static GameState protectedCorridor() {
        return new GameState(5, 2, new int[]{0, 0, 1, 1, 1, 2, 2, 2, 2, 2}, new int[10],
                new int[]{0, 4}, new int[]{0, 0}, new int[][]{{1}, {0}});
    }
}
