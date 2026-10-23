package btk.bot;

import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.referee.MapGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LeaderBotTest {
    private static int disruptRegion(List<Action> actions, GameState s) {
        for (Action a : actions) if (a.type == Action.Type.DISRUPT) return a.disruptRegion(s);
        return -1;
    }

    @Test void frozenReferenceLoadsWithFixedParametersAndIndependentOverrides() {
        Params params = new Params(LiveTargetExperimentParams.V10_OFF + ",NOISE=0.9,UNSTABLE_COST=9");
        FrozenParams.applyTo("leader-v1", params);
        assertEquals(0.1, params.NOISE);
        assertEquals(1.0, params.UNSTABLE_COST);
        assertEquals(0.5, BotFactory.parameters("leader-v1?NOISE=0.5").NOISE);
        assertEquals("leader-v1", BotFactory.create("leader-v1").name());
        assertEquals("local_only", BotCatalog.describe("leader-v1").status());
    }

    @Test void spendsFullPaintAndDisruptsEveryTurnWithoutMutatingInput() {
        GameState state = MapGenerator.generate(10101);
        Rules.updateConnections(state, new Pathing(state.size));
        Bot first = BotFactory.create("leader-v1?NOISE=0"), second = BotFactory.create("leader-v1?NOISE=0");
        first.init(state, 0); second.init(state, 0);
        var actions = first.play(state, 0, 60_000);
        assertTrue(actions.stream().anyMatch(a -> a.type == Action.Type.DISRUPT), "disrupts every turn");
        var moves = new Rules.PlayerMoves();
        Rules.expand(state, 0, actions, new Pathing(state.size), moves, new Rules.TurnResult());
        assertNull(moves.error);
        assertTrue(moves.skipped.isEmpty());
        assertEquals(GameState.PAINT_PER_TURN, moves.cost, "spends the whole paint budget");
        assertEquals(Action.format(actions), Action.format(second.play(state, 0, 60_000)), "deterministic at NOISE=0");
        for (int o : state.owner) assertEquals(GameState.NO_TRACK, o);
        assertEquals(0, state.turn);
    }

    @Test void inksUnfinishedOpponentConstructionOverStaleAndOwnTrackRegions() {
        for (int me = 0; me < 2; me++) {
            // Top row: towns 0 (0,0) and 1 (6,0); cells 1..5 separate regions. Bottom row region 9
            // holds a fresh unfinished opponent segment; region 8/10 hold our own tracks.
            int[] regions = {0, 1, 2, 3, 4, 5, 6,
                             7, 7, 7, 7, 7, 7, 7,
                             8, 9, 9, 9, 9, 9, 10};
            var s = new GameState(7, 3, regions, new int[21], new int[]{0, 6, 0, 6}, new int[]{0, 0, 2, 2},
                    new int[][]{{1}, {}, {3}, {}});
            for (int c = 15; c <= 17; c++) s.owner[c] = 1 - me; // opponent building toward town 3
            s.owner[1] = me; s.owner[2] = me;                    // our own tracks in regions 1,2
            Rules.updateConnections(s, new Pathing(s.size));
            Bot bot = BotFactory.create("leader-v1?NOISE=0"); bot.init(s, me);
            int region = disruptRegion(bot.play(s, me, 60_000), s);
            assertEquals(9, region, "hits the opponent's unfinished construction region");
        }
    }

    @Test void refusesALosingCloseAndTakesAWinningClose() {
        for (int me = 0; me < 2; me++) {
            GameState state = new GameState(3, 1, new int[]{0, 1, 2}, new int[3],
                    new int[]{0, 2}, new int[]{0, 0}, new int[][]{{1}, {}});
            state.owner[1] = 1 - me; state.instability[1] = 3; state.syncRegionsFromCells();
            Rules.updateConnections(state, new Pathing(state.size));
            Bot bot = BotFactory.create("leader-v1?NOISE=0"); bot.init(state, me);
            state.score[1 - me] = 100;
            assertFalse(bot.play(state, me, 60_000).stream().anyMatch(a -> a.type == Action.Type.DISRUPT),
                    "a losing one-hit closure is refused");
            state.score[me] = 200;
            var actions = bot.play(state, me, 60_000);
            assertTrue(actions.stream().anyMatch(a -> a.type == Action.Type.DISRUPT), "a winning closure is taken");
            var result = Rules.resolve(state, me == 0 ? actions : List.of(Action.waitAction()),
                    me == 1 ? actions : List.of(Action.waitAction()), new Pathing(state.size));
            assertTrue(result.moves[me].skipped.isEmpty());
            assertTrue(Rules.gameOver(state, new Pathing(state.size)));
        }
    }

    @Test void opensOnProtectedTownRegions() {
        GameState state = MapGenerator.generate(20202);
        Rules.updateConnections(state, new Pathing(state.size));
        Bot bot = BotFactory.create("leader-v1?NOISE=0"); bot.init(state, 0);
        var actions = bot.play(state, 0, 60_000);
        boolean protectedCell = actions.stream().filter(a -> a.type == Action.Type.PLACE)
                .anyMatch(a -> state.regionHasTown(state.regionId[state.cell(a.x, a.y)]));
        assertTrue(protectedCell, "the opening prefers protected town regions");
    }

    @Test void noBudgetReturnsLegalWait() {
        GameState state = MapGenerator.generate(11);
        Bot bot = BotFactory.create("leader-v1"); bot.init(state, 0);
        assertEquals("WAIT", Action.format(bot.play(state, 0, 0)));
    }
}
