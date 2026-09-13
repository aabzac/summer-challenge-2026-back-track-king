package btk.tools;

import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CgPlayerStudyTest {
    @TempDir Path temp;

    private static GameState line() {
        return new GameState(5, 1, new int[]{0, 1, 2, 3, 4}, new int[5],
                new int[]{0, 4}, new int[]{0, 0}, new int[][]{{1}, {}});
    }

    @Test
    void countsAcceptedAutoplaceAndPaintAndDoesNotMutateActualState() {
        GameState s = line();
        var row = CgPlayerStudy.inspectTurn(s,
                List.of(Action.autoplace(0, 0, 4, 0), Action.place(2, 0)),
                List.of(Action.waitAction()), 0);
        assertArrayEquals(new int[]{3, 0}, row.cells);
        assertArrayEquals(new int[]{3, 0}, row.paint);
        assertArrayEquals(new int[]{3, 0}, row.gained);
        assertEquals(-3, row.withoutMyPlacementDelta);
        assertEquals(0, s.turn);
        assertArrayEquals(new int[]{0, 0}, s.score);
        assertEquals(GameState.NO_TRACK, s.owner[2]);
    }

    @Test
    void detectsPlacementHelpingOpponentAndUsesPlayerPerspective() {
        GameState s = line();
        s.owner[1] = s.owner[3] = 1;
        var row = CgPlayerStudy.inspectTurn(s, List.of(Action.place(2, 0)), List.of(Action.waitAction()), 0);
        assertArrayEquals(new int[]{1, 2}, row.gained);
        assertEquals(1, row.withoutMyPlacementDelta);
        s.owner[1] = s.owner[3] = 0;
        row = CgPlayerStudy.inspectTurn(s, List.of(Action.waitAction()), List.of(Action.place(2, 0)), 1);
        assertEquals(1, row.withoutMyPlacementDelta);
    }

    @Test
    void disruptionCounterfactualResolvesBothPlayersSimultaneously() {
        GameState s = line();
        s.owner[1] = s.owner[2] = s.owner[3] = 0;
        Rules.disrupt(s, 2);
        Rules.disrupt(s, 2); // Two more simultaneous disrupts close the corridor.
        Rules.updateConnections(s, new Pathing(s.size));
        var row = CgPlayerStudy.inspectTurn(s, List.of(Action.disrupt(2)), List.of(Action.waitAction()), 1);
        assertEquals(2, row.bestImmediateDisrupt);
        assertEquals(3, row.bestImmediateDisruptDelta);
        assertTrue(row.bestImmediateDisruptGameOver);
        assertFalse(row.gameOver);
        assertEquals(2, s.regionInstability[2]);
        row = CgPlayerStudy.inspectTurn(s, List.of(Action.disrupt(2)), List.of(Action.disrupt(2)), 0);
        assertEquals(3, row.withoutMyDisruptDelta);
        assertEquals(3, row.withoutOpponentDisruptDelta);
        assertTrue(row.gameOver);
        assertFalse(row.withoutMyDisruptGameOver);
    }

    @Test
    void timeoutsIgnoreLateStdoutAndDoNotRecommendActionsForFailedTurns() {
        CgReplay r = new CgReplay();
        r.agentIds = new int[]{42, 17};
        r.turnStdout.add(new String[]{"PLACE_TRACKS 2 0", "WAIT"});
        r.turnFailures.add(new String[]{"timeout", null});
        assertEquals("WAIT", Action.format(CgPlayerStudy.effectiveActions(r, 0, 0)));
        assertEquals(1, CgPlayerStudy.playerIndex(r, 17));
        assertEquals(-1, CgPlayerStudy.playerIndex(r, 99));
        assertFalse(CgPlayerStudy.inspectTurn(line(), CgPlayerStudy.effectiveActions(r, 0, 0),
                CgPlayerStudy.effectiveActions(r, 0, 1), 0, false).counterfactualsAvailable);
        r.agentIds[0] = 17;
        assertThrows(IllegalArgumentException.class, () -> CgPlayerStudy.playerIndex(r, 17));
    }

    @Test
    void refusesEmptyCorpusAndDivergentReplay() {
        assertThrows(IllegalStateException.class, () -> CgPlayerStudy.main(new String[]{temp.toString(), "42"}));
        CgReplay r = new CgReplay();
        r.nicknames = new String[]{"a", "b"};
        r.globalGraphics = "5|1|3|0 0|1 0|2 0|3 0|4 0|2|0 0 0 1|1 4 0 x";
        r.finalScores[0] = 99;
        assertThrows(IllegalStateException.class, () -> CgPlayerStudy.analyze(r, 0));
    }
}
