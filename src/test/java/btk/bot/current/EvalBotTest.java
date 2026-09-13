package btk.bot.current;

import btk.bot.Params;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvalBotTest {

    @Test
    void extendsExistingNetworkInsteadOfIdling() {
        GameState state = activeStraightConnection();

        List<Action> without = play(state.copy(), false);
        assertFalse(without.stream().anyMatch(a -> a.type == Action.Type.PLACE));

        List<Action> with = play(state.copy(), true);
        assertTrue(with.stream().anyMatch(a -> a.type == Action.Type.PLACE));
    }

    private static List<Action> play(GameState state, boolean idleExtend) {
        Params params = new Params();
        params.NOISE = 0;
        params.USE_BACKUP = false;
        params.USE_DISRUPT = false;
        params.USE_IDLE_EXTEND = idleExtend;
        EvalBot bot = new EvalBot(params);
        bot.init(state.copy(), 0);
        return bot.play(state, 0, 1000);
    }

    private static GameState activeStraightConnection() {
        int width = 5, height = 3;
        int[] regions = {
                3, 3, 3, 3, 3,
                0, 1, 1, 1, 2,
                4, 4, 4, 4, 4
        };
        GameState state = new GameState(width, height, regions, new int[width * height],
                new int[]{0, 4}, new int[]{1, 1}, new int[][]{{1}, {}});
        for (int x = 1; x <= 3; x++) state.owner[state.cell(x, 1)] = 0;
        Rules.updateConnections(state, new Pathing(state.size));
        assertTrue(state.pairActive(0));
        return state;
    }
}
