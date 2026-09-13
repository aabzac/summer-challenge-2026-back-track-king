package btk.bot.current;

import btk.bot.Params;
import btk.bot.BotFactory;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvalBotTest {

    @Test
    void zeroReservePreservesV04ActionsWhenSearchFinishes() {
        assertEquals(0, new Params().DISRUPT_RESERVE_MS, "unpromoted experiment stays off");
        GameState state = activeStraightConnection();
        var current = BotFactory.create("current?NOISE=0,DISRUPT_RESERVE_MS=0");
        var frozen = BotFactory.create("v04?NOISE=0");
        current.init(state.copy(), 0);
        frozen.init(state.copy(), 0);
        assertEquals(Action.format(frozen.play(state.copy(), 0, 60_000)),
                Action.format(current.play(state.copy(), 0, 60_000)));
    }

    @Test
    void reservedSabotageStillRefusesToCloseMapWhenBehind() {
        GameState state = new GameState(3, 1, new int[]{0, 1, 2}, new int[3],
                new int[]{0, 2}, new int[]{0, 0}, new int[][]{{1}, {}});
        state.owner[1] = 1;
        state.instability[1] = 3;
        state.syncRegionsFromCells();
        Rules.updateConnections(state, new Pathing(state.size));
        Params params = new Params();
        params.NOISE = 0;
        params.DISRUPT_RESERVE_MS = 60_000;
        EvalBot bot = new EvalBot(params);
        bot.init(state.copy(), 0);
        state.score[1] = 100;
        assertFalse(bot.play(state.copy(), 0, 60_000).stream()
                .anyMatch(a -> a.type == Action.Type.DISRUPT));
        state.score[0] = 200;
        assertTrue(bot.play(state.copy(), 0, 60_000).stream()
                .anyMatch(a -> a.type == Action.Type.DISRUPT && a.regionId == 1));
    }

    @Test
    void reserveStopsBuildingButLeavesSabotageOnOriginalTimer() {
        GameState state = activeStraightConnection();
        for (int x = 1; x <= 3; x++) state.owner[state.cell(x, 1)] = 1;
        Rules.updateConnections(state, new Pathing(state.size));
        Params params = new Params();
        params.NOISE = 0;
        // Deterministic phase cutoff; no assertion depends on millisecond CPU speed.
        params.DISRUPT_RESERVE_MS = 60_000;
        EvalBot bot = new EvalBot(params);
        bot.init(state.copy(), 0);
        List<Action> actions = bot.play(state, 0, 60_000);
        assertFalse(actions.stream().anyMatch(a -> a.type == Action.Type.PLACE));
        assertTrue(actions.stream().anyMatch(a -> a.type == Action.Type.DISRUPT && a.regionId == 1));
    }

    @Test
    void disabledSabotageDoesNotReserveBuildTime() {
        Params params = new Params();
        params.NOISE = 0;
        params.USE_DISRUPT = false;
        params.DISRUPT_RESERVE_MS = 60_000;
        GameState state = activeStraightConnection();
        EvalBot bot = new EvalBot(params);
        bot.init(state.copy(), 0);
        List<Action> actions = bot.play(state, 0, 60_000);
        assertTrue(actions.stream().anyMatch(a -> a.type == Action.Type.PLACE));
        assertFalse(actions.stream().anyMatch(a -> a.type == Action.Type.DISRUPT));
    }

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
