package btk.runner;

import btk.bot.Bot;
import btk.core.Action;
import btk.core.GameState;
import btk.referee.Referee;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BehaviorMetricsTest {
    @Test
    void countsAcceptedMovesNotSkippedRequestsAndTracksClosure() {
        GameState state = new GameState(4, 1, new int[]{0, 1, 1, 2}, new int[4],
                new int[]{0, 3}, new int[]{0, 0}, new int[][]{{1}, {}});
        state.instability[1] = state.instability[2] = 3; state.syncRegionsFromCells();
        Bot moves = bot(List.of(Action.place(1, 0), Action.place(1, 0), Action.place(-1, 0),
                Action.disrupt(1), Action.disrupt(1)));
        var result = new Referee().play(state, moves, bot(List.of(Action.waitAction())), 1, false);
        assertEquals(1, result.turns); assertTrue(result.closedEarly);
        assertArrayEquals(new int[]{1, 0}, result.placedCells);
        assertArrayEquals(new int[]{1, 0}, result.paintSpent);
        assertArrayEquals(new int[]{1, 0}, result.placementTurns);
        assertArrayEquals(new int[]{1, 0}, result.disruptTurns);
        assertNull(result.error[0]);
    }

    private static Bot bot(List<Action> actions) {
        return new Bot() {
            public String name() { return "test"; }
            public void init(GameState s, int me) {}
            public List<Action> play(GameState s, int me, long budget) { return actions; }
        };
    }
}
