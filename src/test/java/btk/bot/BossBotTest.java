package btk.bot;

import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.referee.MapGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** boss-v1: local imitation of the Silver boss (docs/boss-proxy-v1.md). */
class BossBotTest {
    private static Rules.PlayerMoves expand(GameState s, int me, List<Action> actions) {
        var moves = new Rules.PlayerMoves();
        Rules.expand(s, me, actions, new Pathing(s.size), moves, new Rules.TurnResult());
        assertNull(moves.error);
        assertTrue(moves.skipped.isEmpty(), "skipped: " + moves.skipped);
        return moves;
    }

    @Test void frozenReferenceLoadsWithFixedParameters() {
        Params params = new Params(LiveTargetExperimentParams.V10_OFF + ",NOISE=0.9");
        FrozenParams.applyTo("boss-v1", params);
        assertEquals(0.1, params.NOISE);
        assertEquals("boss-v1", BotFactory.create("boss-v1").name());
        assertEquals("local_only", BotCatalog.describe("boss-v1").status());
        assertEquals("reference", BotCatalog.describe("boss-v1").category());
        assertEquals("WAIT", Action.format(BotFactory.create("boss-v1").play(MapGenerator.generate(11), 0, 0)));
    }

    @Test void spendsFullPaintAndDisruptsOnAFreshMapDeterministically() {
        GameState state = MapGenerator.generate(10101);
        Rules.updateConnections(state, new Pathing(state.size));
        Bot first = BotFactory.create("boss-v1?NOISE=0"), second = BotFactory.create("boss-v1?NOISE=0");
        first.init(state, 0); second.init(state, 0);
        var actions = first.play(state, 0, 60_000);
        assertTrue(actions.stream().anyMatch(a -> a.type == Action.Type.DISRUPT), "boss disrupts from turn 0: " + actions);
        assertEquals(GameState.PAINT_PER_TURN, expand(state, 0, actions).cost);
        assertEquals(Action.format(actions), Action.format(second.play(state, 0, 60_000)));
        for (int o : state.owner) assertEquals(GameState.NO_TRACK, o);
    }

    @Test void neverBuildsInANonTownRegionAtInstabilityTwoOrMore() {
        // 9x1 strip: towns at both ends; the middle cell's region is at instability 2, so the only
        // completion route crosses it. The boss leaves that cell alone and does not build the dead project.
        int[] regions = {0, 1, 1, 1, 2, 3, 3, 3, 4};
        GameState s = new GameState(9, 1, regions, new int[9], new int[]{0, 8}, new int[]{0, 0}, new int[][]{{1}, {}});
        s.regionInstability[2] = 2; s.instability[4] = 2;
        Rules.updateConnections(s, new Pathing(s.size));
        Bot bot = BotFactory.create("boss-v1?NOISE=0"); bot.init(s, 0);
        var actions = bot.play(s, 0, 60_000);
        var moves = expand(s, 0, actions);
        for (int i = 0; i < moves.count; i++) assertNotEquals(4, moves.cells[i], "no track in the unstable region: " + actions);
    }

    @Test void holdsTheCommittedTargetUntilItInks() {
        // 7x2: our pair 0->1 across the top row is active through the opponent's three tracks in region 2;
        // region 2 is the obvious target and must stay the target on consecutive turns while it is not inked.
        int[] regions = {0, 1, 2, 2, 2, 3, 4,
                         0, 1, 5, 5, 5, 3, 4};
        GameState s = new GameState(7, 2, regions, new int[14], new int[]{0, 6}, new int[]{0, 0}, new int[][]{{1}, {}});
        for (int c = 1; c <= 5; c++) s.owner[c] = 1; // opponent owns the whole connection
        Rules.updateConnections(s, new Pathing(s.size));
        Bot bot = BotFactory.create("boss-v1?NOISE=0"); bot.init(s, 0);
        int first = -1;
        for (int turn = 0; turn < 3; turn++) {
            s.turn = turn;
            var actions = bot.play(s, 0, 60_000);
            int target = actions.stream().filter(a -> a.type == Action.Type.DISRUPT).mapToInt(a -> a.disruptRegion(s)).findFirst().orElse(-1);
            assertTrue(target >= 0, "boss attacks: " + actions);
            if (first < 0) first = target; else assertEquals(first, target, "target held on turn " + turn);
            Rules.disrupt(s, target);
            s.score[0] += 10; // irrelevant to the hold rule
        }
    }

    @Test void refusesALosingOneHitClosureAndTakesAWinningOne() {
        // 5x1: towns at both ends, the middle region 1 (cells 1..3) holds the opponent's connection and is
        // at instability 3. Inking it ends the game. Behind on score the boss must not close; ahead it must.
        int[] regions = {0, 1, 1, 1, 2};
        for (int ahead = 0; ahead < 2; ahead++) {
            GameState s = new GameState(5, 1, regions, new int[5], new int[]{0, 4}, new int[]{0, 0}, new int[][]{{1}, {}});
            for (int c = 1; c <= 3; c++) s.owner[c] = 1;
            s.regionInstability[1] = 3; for (int c = 1; c <= 3; c++) s.instability[c] = 3;
            s.score[0] = ahead == 1 ? 100 : 0; s.score[1] = 50;
            Rules.updateConnections(s, new Pathing(s.size));
            Bot bot = BotFactory.create("boss-v1?NOISE=0"); bot.init(s, 0);
            var actions = bot.play(s, 0, 60_000);
            boolean closes = actions.stream().anyMatch(a -> a.type == Action.Type.DISRUPT && a.disruptRegion(s) == 1);
            assertEquals(ahead == 1, closes, "closure decision when ahead=" + ahead + ": " + actions);
        }
    }
}
