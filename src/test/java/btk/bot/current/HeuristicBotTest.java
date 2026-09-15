package btk.bot.current;

import btk.bot.BotFactory;
import btk.bot.MainBot;
import btk.bot.Params;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.referee.MapGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HeuristicBotTest {
    private static final List<Action> WAIT = List.of(Action.waitAction());
    private static Params fixed() {
        return new Params("USE_HEURISTIC_POLICY=1,HEURISTIC_EMERGENCY_STOP=0");
    }

    @Test void explicitIndependentPolicyLeavesDefaultAndFrozenBotsAlone() {
        assertInstanceOf(EvalBot.class, BotFactory.create("current"));
        assertInstanceOf(HeuristicBot.class, BotFactory.create("current?USE_HEURISTIC_POLICY=1"));
        assertInstanceOf(HeuristicBot.class, MainBot.newStrategy(fixed()));
        assertEquals("v05", BotFactory.create("v05").name());
        assertFalse(new Params().USE_HEURISTIC_POLICY);
        for (String query : new String[]{"HEURISTIC_MAX_ROUTES=0", "HEURISTIC_TARGET_STICKINESS=NaN",
                "HEURISTIC_AVOID_INSTABILITY=5", "USE_REPLY_RERANK=1", "USE_JOINT_ACTION=1"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> BotFactory.create("current?USE_HEURISTIC_POLICY=1," + query));
        }
    }

    @Test void fixedWorkProducesIdenticalLegalSequencesWithoutMutatingInput() {
        for (int seed = 24000; seed < 24003; seed++) for (int me = 0; me < 2; me++) {
            GameState s = MapGenerator.generate(seed);
            HeuristicBot shortBudget = new HeuristicBot(fixed()), ample = new HeuristicBot(fixed());
            shortBudget.init(s, me); ample.init(s, me);
            Pathing paths = new Pathing(s.size);
            for (int t = 0; t < 15; t++) {
                GameState before = s.copy();
                List<Action> a = shortBudget.play(s, me, 0), b = ample.play(s, me, 60_000);
                assertEquals(Action.format(a), Action.format(b));
                assertArrayEquals(before.owner, s.owner);
                assertArrayEquals(before.score, s.score);
                assertArrayEquals(before.regionInstability, s.regionInstability);
                assertArrayEquals(before.pairPathLen, s.pairPathLen);
                assertEquals(before.turn, s.turn);
                assertTrue(shortBudget.lastRouteQueries <= fixed().HEURISTIC_MAX_ROUTES);
                assertEquals(0, shortBudget.emergencyStops);
                legal(s, me, a);
                Rules.resolve(s, me == 0 ? a : WAIT, me == 1 ? a : WAIT, paths);
            }
        }
    }

    @Test void routeCapAndEmergencyAreIndependentAndObservable() {
        GameState s = MapGenerator.generate(24005);
        Params p = fixed(); p.HEURISTIC_MAX_ROUTES = 1;
        HeuristicBot capped = new HeuristicBot(p); capped.init(s, 0);
        legal(s, 0, capped.play(s, 0, 0));
        assertEquals(1, capped.lastRouteQueries);
        p.HEURISTIC_EMERGENCY_STOP = true;
        HeuristicBot timed = new HeuristicBot(p); timed.init(s, 0);
        assertEquals("WAIT", Action.format(timed.play(s, 0, 0)));
        assertTrue(timed.lastEmergency);
        assertEquals(1, timed.emergencyStops);
        assertEquals(0, timed.lastRouteQueries);
    }

    @Test void persistsUsefulSabotageAndBuildsAroundItsRegion() {
        for (int me = 0; me < 2; me++) {
            GameState s = grid(5, 3, 1);
            int target = s.cell(2, 1); s.owner[target] = 1 - me;
            HeuristicBot bot = new HeuristicBot(fixed()); bot.init(s, me);
            for (int t = 0; t < 3; t++) {
                var a = bot.play(s, me, 0);
                var moves = legal(s, me, a);
                assertEquals(List.of(target), moves.disrupts);
                if (t == 0) assertTrue(moves.count > 0);
                for (int i = 0; i < moves.count; i++) assertNotEquals(target, s.regionId[moves.cells[i]]);
                Rules.resolve(s, me == 0 ? a : WAIT, me == 1 ? a : WAIT, new Pathing(s.size));
            }
        }
    }

    @Test void losingClosureIsRejectedButWinningFinalTurnHitIsAllowed() {
        for (int me = 0; me < 2; me++) for (boolean winning : new boolean[]{false, true}) {
            GameState s = grid(3, 1, 0);
            s.owner[1] = 1 - me; s.instability[1] = 3; s.syncRegionsFromCells();
            Rules.updateConnections(s, new Pathing(s.size));
            s.score[me] = winning ? 200 : 0; s.score[1 - me] = 100;
            s.turn = s.maxTurns - 1;
            HeuristicBot bot = new HeuristicBot(fixed()); bot.init(s, me);
            var moves = legal(s, me, bot.play(s, me, 0));
            assertEquals(winning ? List.of(1) : List.of(), moves.disrupts);
        }
    }

    @Test void isolatedWarmupDoesNotChangeLiveHeuristicMemory() {
        GameState s = MapGenerator.generate(24006);
        MainBot warm = new MainBot(fixed()), cold = new MainBot(fixed());
        warm.init(s, 0); cold.init(s, 0); warm.warmup(s, 0, 150);
        assertTrue(warm.warmupCalls > 0);
        for (int t = 0; t < 5; t++) {
            var expected = cold.play(s, 0, 0);
            assertEquals(Action.format(expected), Action.format(warm.play(s, 0, 0)));
            Rules.resolve(s, expected, WAIT, new Pathing(s.size));
        }
    }

    private static Rules.PlayerMoves legal(GameState s, int me, List<Action> actions) {
        var moves = new Rules.PlayerMoves();
        Rules.expand(s, me, actions, new Pathing(s.size), moves, new Rules.TurnResult());
        assertNull(moves.error);
        assertTrue(moves.skipped.isEmpty(), moves.skipped.toString());
        assertTrue(moves.cost <= GameState.PAINT_PER_TURN);
        return moves;
    }

    private static GameState grid(int width, int height, int townY) {
        int[] regions = new int[width * height];
        for (int c = 0; c < regions.length; c++) regions[c] = c;
        return new GameState(width, height, regions, new int[regions.length],
                new int[]{0, width - 1}, new int[]{townY, townY}, new int[][]{{1}, {0}});
    }
}
