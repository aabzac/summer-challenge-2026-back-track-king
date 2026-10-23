package btk.bot;

import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.referee.MapGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** leader-v2: leader-v1 sabotage, never-idle construction (per-cell study, 2026-09-20). */
class LeaderBot2Test {
    private static Rules.PlayerMoves expand(GameState s, int me, List<Action> actions) {
        var moves = new Rules.PlayerMoves();
        Rules.expand(s, me, actions, new Pathing(s.size), moves, new Rules.TurnResult());
        assertNull(moves.error);
        assertTrue(moves.skipped.isEmpty(), "skipped: " + moves.skipped);
        return moves;
    }

    @Test void frozenReferenceLoadsWithFixedParameters() {
        Params params = new Params(LiveTargetExperimentParams.V10_OFF + ",NOISE=0.9,UNSTABLE_COST=9");
        FrozenParams.applyTo("leader-v2", params);
        assertEquals(0.1, params.NOISE);
        assertEquals(1.0, params.UNSTABLE_COST);
        assertEquals("leader-v2", BotFactory.create("leader-v2").name());
        assertEquals("local_only", BotCatalog.describe("leader-v2").status());
        assertEquals("WAIT", Action.format(BotFactory.create("leader-v2").play(MapGenerator.generate(11), 0, 0)));
    }

    @Test void spendsFullPaintAndDisruptsOnAFreshMapDeterministically() {
        GameState state = MapGenerator.generate(10101);
        Rules.updateConnections(state, new Pathing(state.size));
        Bot first = BotFactory.create("leader-v2?NOISE=0"), second = BotFactory.create("leader-v2?NOISE=0");
        first.init(state, 0); second.init(state, 0);
        var actions = first.play(state, 0, 60_000);
        assertTrue(actions.stream().anyMatch(a -> a.type == Action.Type.DISRUPT));
        assertEquals(GameState.PAINT_PER_TURN, expand(state, 0, actions).cost);
        assertEquals(Action.format(actions), Action.format(second.play(state, 0, 60_000)));
        for (int o : state.owner) assertEquals(GameState.NO_TRACK, o);
    }

    @Test void buildsTheCheapestRemainingCompletionEvenWhenItIsUnprofitableInsideTheHorizon() {
        // 21x1 strip: towns at both ends, 19 plains cells split between the two town regions (so no
        // region can be inked or targeted); the pair pays 19 x 1 per turn once complete but needs 7
        // turns of paint, so leader-v1 (horizon 8, delay 6) valued it at almost nothing while
        // leader-v2 must still build toward it rather than wait.
        int[] regions = new int[21];
        for (int c = 10; c <= 20; c++) regions[c] = 1;
        GameState s = new GameState(21, 1, regions, new int[21], new int[]{0, 20}, new int[]{0, 0}, new int[][]{{1}, {}});
        s.turn = 90; // ten turns left: the finished route earns only three turns
        Rules.updateConnections(s, new Pathing(s.size));
        Bot bot = BotFactory.create("leader-v2?NOISE=0"); bot.init(s, 0);
        var actions = bot.play(s, 0, 60_000);
        assertEquals(GameState.PAINT_PER_TURN, expand(s, 0, actions).cost, "never idle: " + actions);
        assertEquals(3, actions.stream().filter(a -> a.type == Action.Type.PLACE).count());
    }

    @Test void leftoverPaintGrowsTheOwnEarningNetworkWhenNothingIsLeftToComplete() {
        for (int me = 0; me < 2; me++) {
            // 7x3: top row towns 0 and 1 joined by our five tracks; the middle row shares the top row's
            // regions (town regions 0/6 and stable regions 1..5 holding our rails, so none is a target);
            // the only desired pair is already active, so nothing completes and region 9 gets the hit.
            int[] regions = {0, 1, 2, 3, 4, 5, 6,
                             0, 1, 2, 3, 4, 5, 6,
                             8, 9, 9, 9, 9, 9, 10};
            var s = new GameState(7, 3, regions, new int[21], new int[]{0, 6, 0, 6}, new int[]{0, 0, 2, 2},
                    new int[][]{{1}, {}, {}, {}});
            for (int c = 1; c <= 5; c++) s.owner[c] = me;
            Rules.updateConnections(s, new Pathing(s.size));
            Bot bot = BotFactory.create("leader-v2?NOISE=0"); bot.init(s, me);
            var actions = bot.play(s, me, 60_000);
            var moves = expand(s, me, actions);
            assertEquals(GameState.PAINT_PER_TURN, moves.cost, "filler spends the paint: " + actions);
            for (int i = 0; i < moves.count; i++) {
                int c = moves.cells[i];
                assertEquals(1, s.y(c), "filler cells lie in the row under our earning rails");
            }
        }
    }
}
