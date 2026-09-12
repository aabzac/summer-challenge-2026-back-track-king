package btk.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RulesTest {

    private static GameState line5() {
        // 0 . . . 1  towns at (0,0) and (4,0); 0 wants 1
        return TestMaps.of(new String[]{"0...1"}, new int[][]{{1}, {}});
    }

    @Test
    void noConnectionWithoutTracks() {
        GameState s = line5();
        Pathing p = new Pathing(s.size);
        Rules.updateConnections(s, p);
        assertFalse(s.pairActive(0));
    }

    @Test
    void connectionScoresOwnersOnly() {
        GameState s = line5();
        TestMaps.track(s, 0, 1, 0);
        TestMaps.track(s, 1, 2, 0);
        TestMaps.track(s, GameState.NEUTRAL, 3, 0);
        Pathing p = new Pathing(s.size);
        Rules.updateConnections(s, p);
        assertTrue(s.pairActive(0));
        assertEquals(5, s.pairPathLen[0]);
        int[] gained = new int[2];
        Rules.scoreTurn(s, gained);
        assertArrayEquals(new int[]{1, 1}, gained);
        assertEquals(1, s.score[0]);
        assertEquals(1, s.score[1]);
    }

    @Test
    void placementCostsAndBudget() {
        GameState s = TestMaps.of(new String[]{"0.~^1"}, new int[][]{{1}, {}});
        Pathing p = new Pathing(s.size);
        Rules.PlayerMoves m = new Rules.PlayerMoves();
        Rules.expand(s, 0, List.of(Action.place(1, 0), Action.place(2, 0)), p, m, null);
        assertNull(m.error);
        assertEquals(3, m.cost);
        // CG referee (Game.doActions): impossible actions are skipped, the line stays valid
        Rules.expand(s, 0, List.of(Action.place(2, 0), Action.place(3, 0)), p, m, null);
        assertNull(m.error);
        assertEquals(1, m.skipped.size(), "river + mountain = 5 > 3: mountain skipped");
        assertEquals(2, m.cost);
        Rules.expand(s, 0, List.of(Action.place(0, 0), Action.place(1, 0)), p, m, null);
        assertNull(m.error);
        assertEquals(1, m.skipped.size(), "town cell skipped");
        assertEquals(1, m.count);
        TestMaps.track(s, 1, 1, 0);
        Rules.expand(s, 0, List.of(Action.place(1, 0)), p, m, null);
        assertEquals(1, m.skipped.size(), "occupied");
        assertEquals(0, m.count);
        Rules.expand(s, 0, List.of(Action.place(9, 0)), p, m, null);
        assertEquals(1, m.skipped.size(), "out of bounds");
        Rules.expand(s, 0, List.of(), p, m, null);
        assertNotNull(m.error, "empty line disqualifies");
    }

    @Test
    void simultaneousSameCellBecomesNeutral() {
        GameState s = line5();
        Pathing p = new Pathing(s.size);
        Rules.TurnResult tr = Rules.resolve(s,
                List.of(Action.place(1, 0), Action.place(2, 0)),
                List.of(Action.place(2, 0), Action.place(3, 0)), p);
        assertNull(tr.moves[0].error);
        assertNull(tr.moves[1].error);
        assertEquals(0, s.owner[s.cell(1, 0)]);
        assertEquals(GameState.NEUTRAL, s.owner[s.cell(2, 0)]);
        assertEquals(1, s.owner[s.cell(3, 0)]);
        assertTrue(s.pairActive(0));
        assertEquals(1, s.score[0]);
        assertEquals(1, s.score[1]);
        assertEquals(1, s.turn);
    }

    @Test
    void autoplaceExpandsCheapestPathWithinBudget() {
        // Straight line through a river costs 2+1+1=4; detour over plains costs 1*5=5 -> straight wins on cost.
        GameState s = TestMaps.of(new String[]{
                ".....",
                "0.~.1",
                "....."}, new int[][]{{1}, {}});
        Pathing p = new Pathing(s.size);
        Rules.PlayerMoves m = new Rules.PlayerMoves();
        Rules.expand(s, 0, List.of(Action.autoplace(0, 1, 4, 1)), p, m, null);
        assertNull(m.error);
        assertEquals(3, m.cost, "1 plain + 1 river fill the budget; the rest waits");
        assertEquals(2, m.count);
        assertEquals(s.cell(1, 1), m.cells[0]);
        assertEquals(s.cell(2, 1), m.cells[1]);
    }

    @Test
    void autoplaceDoesNothingWhenAlreadyConnected() {
        GameState s = line5();
        TestMaps.track(s, 0, 1, 0, 2, 0, 3, 0);
        Pathing p = new Pathing(s.size);
        Rules.PlayerMoves m = new Rules.PlayerMoves();
        Rules.expand(s, 0, List.of(Action.autoplace(0, 0, 4, 0)), p, m, null);
        assertNull(m.error);
        assertEquals(0, m.count);
    }

    @Test
    void duplicatePlacementIsSkippedNotFatal() {
        GameState s = line5();
        Pathing p = new Pathing(s.size);
        Rules.TurnResult tr = Rules.resolve(s,
                List.of(Action.place(1, 0), Action.place(1, 0), Action.place(2, 0)),
                List.of(Action.place(3, 0)), p);
        assertNull(tr.moves[0].error);
        assertEquals(1, tr.moves[0].skipped.size());
        assertEquals(0, s.owner[s.cell(1, 0)]);
        assertEquals(0, s.owner[s.cell(2, 0)]);
        assertEquals(1, s.owner[s.cell(3, 0)]);
    }

    @Test
    void actionsRoundTrip() {
        List<Action> a = Action.parseLine("PLACE_TRACKS 3 4;AUTOPLACE 1 2 3 4;DISRUPT 7;MESSAGE hi there;WAIT");
        assertEquals(5, a.size());
        assertEquals("PLACE_TRACKS 3 4;AUTOPLACE 1 2 3 4;DISRUPT 7;MESSAGE hi there;WAIT", Action.format(a));
        assertThrows(IllegalArgumentException.class, () -> Action.parse("JUMP 1 2"));
    }
}
