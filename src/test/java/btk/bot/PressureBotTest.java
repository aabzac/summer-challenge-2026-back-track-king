package btk.bot;

import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.referee.MapGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PressureBotTest {
    @Test
    void consumedReferenceParametersAreFixedAndOverridesAreIndependent() {
        Params params = new Params(LiveTargetExperimentParams.V10_OFF + ",PRESSURE_HORIZON=999,PRESSURE_STICKINESS=99,NOISE=0.9");
        FrozenParams.applyTo("pressure-v1", params);
        assertEquals(8, params.PRESSURE_HORIZON); assertEquals(0.25, params.PRESSURE_STICKINESS);
        assertEquals(0.1, params.NOISE);
        assertEquals(0.5, BotFactory.parameters("pressure-v1?PRESSURE_STICKINESS=0.5").PRESSURE_STICKINESS);
        assertEquals(0.25, BotFactory.parameters("pressure-v1").PRESSURE_STICKINESS);
        assertEquals("pressure-v1", BotFactory.create("pressure-v1").name());
    }

    @Test
    void deterministicPressureStartsOnEmptyCorridorsWithoutMutatingInput() {
        GameState state = MapGenerator.generate(10101);
        Rules.updateConnections(state, new Pathing(state.size));
        Bot first = BotFactory.create("pressure-v1?NOISE=0"), second = BotFactory.create("pressure-v1?NOISE=0");
        first.init(state, 0); second.init(state, 0);
        var actions = first.play(state, 0, 60_000);
        assertTrue(actions.stream().anyMatch(a -> a.type == Action.Type.DISRUPT));
        assertEquals(Action.format(actions), Action.format(second.play(state, 0, 60_000)));
        for (int o : state.owner) assertEquals(GameState.NO_TRACK, o);
        assertEquals(0, state.turn);
        var moves = new Rules.PlayerMoves();
        Rules.expand(state, 0, actions, new Pathing(state.size), moves, new Rules.TurnResult());
        assertNull(moves.error); assertTrue(moves.skipped.isEmpty());
        assertTrue(moves.cost <= GameState.PAINT_PER_TURN);
    }

    @Test
    void pressureRefusesALosingCloseAndTakesWinningCloseForBothSides() {
        for (int me = 0; me < 2; me++) {
            GameState state = new GameState(3, 1, new int[]{0, 1, 2}, new int[3],
                    new int[]{0, 2}, new int[]{0, 0}, new int[][]{{1}, {}});
            state.owner[1] = 1 - me; state.instability[1] = 3; state.syncRegionsFromCells();
            Rules.updateConnections(state, new Pathing(state.size));
            Bot bot = BotFactory.create("pressure-v1?NOISE=0"); bot.init(state, me);
            state.score[1 - me] = 100;
            assertFalse(bot.play(state, me, 60_000).stream().anyMatch(a -> a.type == Action.Type.DISRUPT));
            state.score[me] = 200;
            var actions = bot.play(state, me, 60_000);
            assertTrue(actions.stream().anyMatch(a -> a.type == Action.Type.DISRUPT));
            var result = Rules.resolve(state, me == 0 ? actions : List.of(Action.waitAction()),
                    me == 1 ? actions : List.of(Action.waitAction()), new Pathing(state.size));
            assertTrue(result.moves[me].skipped.isEmpty());
            assertTrue(Rules.gameOver(state, new Pathing(state.size)));
        }
    }

    @Test
    void noBudgetReturnsLegalWaitAndInvalidConfigurationFails() {
        GameState state = MapGenerator.generate(11);
        Bot bot = BotFactory.create("pressure-v1"); bot.init(state, 0);
        assertEquals("WAIT", Action.format(bot.play(state, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("pressure-v1?PRESSURE_HORIZON=0"));
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("pressure-v1?PRESSURE_STICKINESS=NaN"));
    }
}
