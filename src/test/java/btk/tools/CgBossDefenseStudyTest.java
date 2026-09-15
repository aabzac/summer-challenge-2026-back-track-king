package btk.tools;

import btk.core.*;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CgBossDefenseStudyTest {
    @Test void identityUsesExplicitBossMarkerAndInGameIndex() {
        var raw = JsonParser.parseString("{agents:[{index:1,agentId:6740207,arenaboss:{nickname:'Boss 4'}},{index:0,agentId:-1}]}").getAsJsonObject();
        assertEquals(new CgBossDefenseStudy.Boss(1, 6740207, "Boss 4"), CgBossDefenseStudy.identifyBoss(raw));
        assertThrows(IllegalArgumentException.class, () -> CgBossDefenseStudy.identifyBoss(
                JsonParser.parseString("{agents:[{index:0,agentId:12}]}").getAsJsonObject()));
    }
    @Test void bypassPlacementsReduceAvoidingCostAndInputIsUntouched() {
        for (int boss = 0; boss < 2; boss++) {
            GameState s = grid(); s.owner[4] = boss;
            Rules.updateConnections(s, new Pathing(s.size)); int[] before = s.owner.clone();
            Rules.PlayerMoves moves = new Rules.PlayerMoves();
            Rules.expand(s, boss, List.of(Action.place(0, 0), Action.place(1, 0), Action.place(2, 0)),
                    new Pathing(s.size), moves, new Rules.TurnResult());
            var rows = CgBossDefenseStudy.probe(s, boss, 4, moves);
            assertEquals(1, rows.size()); assertEquals(3, rows.get(0).before());
            assertEquals(0, rows.get(0).after()); assertTrue(rows.get(0).improves());
            assertArrayEquals(before, s.owner);
            s.owner[0] = s.owner[1] = s.owner[2] = GameState.NEUTRAL;
            var ready = CgBossDefenseStudy.probe(s, boss, 4, new Rules.PlayerMoves()).get(0);
            assertEquals(0, ready.before()); assertFalse(ready.improves());
        }
    }
    @Test void partialBypassIsProgressNotCompletionAndWaitIsNotProgress() {
        GameState s = grid(); s.owner[4] = 1;
        Rules.updateConnections(s, new Pathing(s.size));
        var moves = new Rules.PlayerMoves();
        Rules.expand(s, 1, List.of(Action.place(1, 0)), new Pathing(s.size), moves, new Rules.TurnResult());
        var partial = CgBossDefenseStudy.probe(s, 1, 4, moves).get(0);
        assertEquals(3, partial.before()); assertEquals(2, partial.after()); assertTrue(partial.improves());
        var wait = CgBossDefenseStudy.probe(s, 1, 4, new Rules.PlayerMoves()).get(0);
        assertEquals(3, wait.after()); assertFalse(wait.improves());
    }
    @Test void reverseCanonicalRouteIsCheckedBeforeUndirectedDeduplication() {
        GameState s = new GameState(3, 3, new int[]{0,1,2,3,4,5,6,7,8}, new int[9],
                new int[]{2,0}, new int[]{0,2}, new int[][]{{1},{0}});
        for (int c = 0; c < s.size; c++) if (s.canBuild(c)) s.owner[c] = GameState.NEUTRAL;
        s.owner[3] = 1;
        Rules.updateConnections(s, new Pathing(s.size));
        // 0->1 goes down the right edge; 1->0 goes up the left edge through the boss track.
        var rows = CgBossDefenseStudy.probe(s, 1, 3, new Rules.PlayerMoves());
        assertEquals(1, rows.size()); assertEquals(1, rows.get(0).from());
        assertEquals(0, rows.get(0).to()); assertEquals(1, rows.get(0).bossTracks());
        assertEquals(0, rows.get(0).before()); assertFalse(rows.get(0).improves());
    }
    private static GameState grid() {
        return new GameState(3, 3, new int[]{0,1,2,3,4,5,6,7,8}, new int[9],
                new int[]{0,2}, new int[]{1,1}, new int[][]{{1},{0}});
    }
}
