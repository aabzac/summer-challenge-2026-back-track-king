package btk.referee;

import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class ReplayTest {

    @Test
    @SuppressWarnings("unchecked")
    void recordsRegionSabotageAndInkStatePerTurn() {
        GameState state = new GameState(2, 1, new int[]{0, 0}, new int[]{0, 0},
                new int[0], new int[0], new int[0][]);
        Replay replay = new Replay(state, 1L, "p0", "p1");
        Pathing pathing = new Pathing(state.size);

        for (int turn = 1; turn <= GameState.INK_THRESHOLD; turn++) {
            List<Action> disrupt = List.of(Action.disrupt(0));
            List<Action> wait = List.of(Action.waitAction());
            Rules.TurnResult result = Rules.resolve(state, disrupt, wait, pathing);
            replay.addTurn(state, new List[]{disrupt, wait}, result);

            assertArrayEquals(new int[]{turn}, replay.turns.get(turn - 1).regionInstability);
            assertArrayEquals(new boolean[]{turn == GameState.INK_THRESHOLD},
                    replay.turns.get(turn - 1).regionInked);
        }
    }
}
