package btk.bot.neural;

import btk.bot.Params;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Protocol;
import btk.core.Rules;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpatialV9BotTest {
    @Test void offPolicyMatchesFrozenV4AndBothModesPreserveStateAndLegality() {
        GameState state = SpatialV9NetTest.fixture(9, 6, new int[]{0, 8}, new int[]{0, 5});
        state.turn = 37; state.owner[1] = 0; state.owner[2] = 1;
        state.terrain[3] = GameState.RIVER; state.terrain[4] = GameState.MOUNTAIN;
        Rules.updateConnections(state, new Pathing(state.size));
        String before = Protocol.turnBlock(state, 0);
        Params params = new Params();
        for (int seat = 0; seat < 2; seat++) {
            SpatialBot old = new SpatialBot(params, new SpatialNet(SpatialV4Weights.DATA), () -> 0);
            SpatialV9Bot off = new SpatialV9Bot(params, new SpatialV9Net(SpatialV4Weights.DATA, false), () -> 0);
            SpatialV9Bot on = new SpatialV9Bot(params, new SpatialV9Net(SpatialV4Weights.DATA, true), () -> 0);
            old.init(state, seat); off.init(state, seat); on.init(state, seat);
            List<Action> baseline = old.play(state, seat, 60_000), disabled = off.play(state, seat, 60_000);
            assertEquals(Action.format(baseline), Action.format(disabled));
            assertEquals(old.lastCompletedDecisions, off.lastCompletedDecisions);
            assertEquals(old.lastPlacements, off.lastPlacements); assertEquals(old.lastTarget, off.lastTarget);
            assertFalse(off.lastInterrupted);
            for (List<Action> actions : List.of(disabled, on.play(state, seat, 60_000))) {
                Rules.TurnResult result = new Rules.TurnResult();
                Rules.PlayerMoves move = result.moves[seat];
                Rules.expand(state, seat, actions, new Pathing(state.size), move, result);
                assertNull(move.error); assertTrue(move.skipped.isEmpty());
                assertTrue(move.count <= 3); assertTrue(move.cost <= 3); assertFalse(result.unknownActions);
            }
            assertFalse(on.lastInterrupted); assertEquals(before, Protocol.turnBlock(state, 0));
        }
    }

    @Test void contextDeadlineReturnsLegalWaitThenNextCallCanRecover() {
        GameState state = SpatialV9NetTest.fixture(9, 6, new int[]{0, 8}, new int[]{0, 5});
        Params params = new Params(); params.SPATIAL_RESERVE_MS = 0;
        SpatialV9Net net = new SpatialV9Net(SpatialV4Weights.DATA, true);
        SpatialV9NetTest.DeadlineAtMethod cutoff = new SpatialV9NetTest.DeadlineAtMethod("broadcast", 1);
        SpatialV9Bot bot = new SpatialV9Bot(params, net, () -> cutoff.getAsLong() * 1_000_000);
        String before = Protocol.turnBlock(state, 0);
        bot.init(state, 0);
        assertEquals("WAIT", Action.format(bot.play(state, 0, 1)));
        assertTrue(bot.lastInterrupted); assertEquals(0, bot.lastCompletedDecisions);
        assertEquals(0, bot.lastPlacements); assertEquals(-1, bot.lastTarget);
        assertEquals(before, Protocol.turnBlock(state, 0));
        SpatialV9Bot recovered = new SpatialV9Bot(params, net, () -> 0);
        List<Action> actions = recovered.play(state, 0, 60_000);
        assertFalse(recovered.lastInterrupted);
        Rules.TurnResult result = new Rules.TurnResult();
        Rules.PlayerMoves move = result.moves[0];
        Rules.expand(state, 0, actions, new Pathing(state.size), move, result);
        assertNull(move.error); assertTrue(move.skipped.isEmpty());
        assertEquals(before, Protocol.turnBlock(state, 0));
    }
}
