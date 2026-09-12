package btk.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** DISRUPT / ink rules inferred from 107 Gold replays (see docs/rules/inferred_gold.md). */
class InkTest {

    /** 0 . . . 1 on one row; cells (1,0),(2,0),(3,0) form region 7, towns have own regions. */
    private static GameState line() {
        GameState s = TestMaps.of(new String[]{"0...1"}, new int[][]{{1}, {}});
        for (int x = 1; x <= 3; x++) s.regionId[s.cell(x, 0)] = 7;
        return new GameState(s.width, s.height, s.regionId, s.terrain, s.townX, s.townY, s.desired);
    }

    @Test
    void fourDisruptsInkRegionRemoveTracksAndBreakConnection() {
        GameState s = line();
        TestMaps.track(s, 0, 1, 0, 2, 0, 3, 0);
        Pathing p = new Pathing(s.size);
        Rules.updateConnections(s, p);
        assertTrue(s.pairActive(0));
        for (int i = 0; i < 3; i++) {
            Rules.TurnResult tr = Rules.resolve(s, List.of(Action.disrupt(7)), List.of(Action.waitAction()), p);
            assertTrue(tr.inkedRegions.isEmpty());
            assertEquals(i + 1, s.regionInstability[7]);
            assertEquals(3, tr.gained[0], "still scoring while unstable");
        }
        Rules.TurnResult tr = Rules.resolve(s, List.of(Action.disruptAt(2, 0)), List.of(Action.waitAction()), p);
        assertEquals(List.of(7), tr.inkedRegions);
        assertTrue(s.regionInked[7]);
        assertTrue(s.inked[s.cell(1, 0)]);
        assertEquals(GameState.NO_TRACK, s.owner[s.cell(2, 0)]);
        assertEquals(4, s.instability[s.cell(3, 0)]);
        assertFalse(s.pairActive(0));
        assertEquals(0, tr.gained[0], "ink happens before scoring");
        assertEquals(9, s.score[0]);
    }

    @Test
    void bothPlayersDisruptingCountTwice() {
        GameState s = line();
        Pathing p = new Pathing(s.size);
        Rules.resolve(s, List.of(Action.disrupt(7)), List.of(Action.disrupt(7)), p);
        assertEquals(2, s.regionInstability[7]);
        Rules.TurnResult tr = Rules.resolve(s, List.of(Action.disrupt(7)), List.of(Action.disrupt(7)), p);
        assertEquals(List.of(7), tr.inkedRegions);
    }

    @Test
    void inkedCellsCannotBeBuiltOrPathed() {
        GameState s = line();
        Pathing p = new Pathing(s.size);
        Rules.ink(s, 7);
        Rules.PlayerMoves m = new Rules.PlayerMoves();
        Rules.expand(s, 0, List.of(Action.place(2, 0)), p, m, null);
        assertNotNull(m.error);
        assertEquals(-1, p.cheapestPath(s, s.townCell[0], s.townCell[1], new int[s.size]));
        assertFalse(Rules.anyPairPossible(s, p));
        assertTrue(Rules.gameOver(s, p), "no pair connectable -> game over");
    }

    @Test
    void disruptRulesValidation() {
        GameState s = line();
        Pathing p = new Pathing(s.size);
        Rules.PlayerMoves m = new Rules.PlayerMoves();
        Rules.expand(s, 0, List.of(Action.disrupt(s.regionId[s.townCell[0]])), p, m, null);
        assertNotNull(m.error, "town region");
        Rules.expand(s, 0, List.of(Action.disrupt(7), Action.disrupt(7)), p, m, null);
        assertNotNull(m.error, "two disrupts");
        Rules.expand(s, 0, List.of(Action.disrupt(999)), p, m, null);
        assertNotNull(m.error, "unknown region");
        Rules.expand(s, 0, List.of(Action.place(1, 0), Action.disrupt(7)), p, m, null);
        assertNull(m.error, "disrupt costs no paint");
        assertEquals(1, m.cost);
    }

    @Test
    void disruptActionParsesBothForms() {
        assertEquals("DISRUPT 5", Action.parse("DISRUPT 5").toCommand());
        assertEquals("DISRUPT 3 4", Action.parse("DISRUPT 3 4").toCommand());
        GameState s = line();
        assertEquals(7, Action.parse("DISRUPT 2 0").disruptRegion(s));
        assertEquals(-1, Action.parse("DISRUPT 9 9").disruptRegion(s));
    }
}
