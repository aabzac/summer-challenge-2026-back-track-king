package btk.bot;

import btk.bot.current.IndrillNet;
import btk.bot.forum.IndrillForumBot;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.referee.MapGenerator;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class IndrillForumBotTest {
    @Test void factoryServesTheHandReadoutWithoutTouchingTheSelectedBot() {
        assertEquals("indrill-forum-v1", BotFactory.create("indrill-forum-v1").name());
        assertInstanceOf(IndrillForumBot.class, BotFactory.create("indrill-forum-v1"));
        assertEquals("candidate", BotCatalog.describe("indrill-forum-v1").category());
        assertEquals("local_only", BotCatalog.describe("indrill-forum-v1").status());
        assertEquals("v09", BotFactory.create("v09").name());
        assertEquals("WAIT", Action.format(BotFactory.create("indrill-forum-v1").play(MapGenerator.generate(11), 0, 0)));
    }

    @Test void quotaKnapsackMatchesTheEarlierExactBuilder() {
        Random random = new Random(20261006);
        float[] q = new float[12];
        int[] costs = new int[12];
        int[] ours = new int[3], theirs = new int[3];
        for (int trial = 0; trial < 200; trial++) {
            for (int i = 0; i < q.length; i++) {
                q[i] = random.nextInt(21) - 5;
                costs[i] = random.nextInt(4);
            }
            int n = IndrillForumBot.selectBuild(q, costs, ours);
            int m = IndrillNet.selectBuild(q, costs, theirs);
            assertEquals(m, n);
            assertArrayEquals(theirs, ours);
        }
    }

    @Test void openingExtendsThePriorityPathAndInksTheCorridor() {
        int[] terrain = new int[15];
        terrain[7] = GameState.RIVER;
        int[] regions = new int[15];
        Arrays.fill(regions, 1);
        regions[5] = 0;
        regions[9] = 2;
        GameState s = new GameState(5, 3, regions, terrain, new int[]{0, 4}, new int[]{1, 1}, new int[][]{{1}, {}});
        Rules.updateConnections(s, new Pathing(s.size));
        IndrillForumBot bot = (IndrillForumBot) BotFactory.create("indrill-forum-v1");
        bot.init(s, 0);
        List<Action> actions = bot.play(s, 0, 60_000);
        assertEquals(1, bot.selectedInk);
        assertTrue(actions.stream().anyMatch(a -> a.type == Action.Type.DISRUPT && a.regionId == 1));
        int paint = 0;
        Set<Integer> placed = new HashSet<>();
        for (Action action : actions) {
            if (action.type != Action.Type.PLACE) continue;
            int cell = s.cell(action.x, action.y);
            placed.add(cell);
            paint += s.placeCost(cell);
            assertTrue(s.canBuild(cell));
            assertTrue(nextToTown(s, cell) || nextTo(placed, s, cell), action.toString());
        }
        assertTrue(paint >= 1 && paint <= 3);
        assertTrue(placed.contains(6) || placed.contains(8));
        var moves = new Rules.PlayerMoves();
        Rules.expand(s, 0, actions, new Pathing(s.size), moves, new Rules.TurnResult());
        assertNull(moves.error);
        assertTrue(moves.skipped.isEmpty(), moves.skipped.toString());
    }

    @Test void laterTurnsFitTheNominalSearchBudget() {
        long worst = 0;
        for (int seed = 1; seed <= 6; seed++) {
            GameState s = MapGenerator.generate(88000 + seed);
            Pathing pathing = new Pathing(s.size);
            Rules.updateConnections(s, pathing);
            Bot bot = BotFactory.create("indrill-forum-v1");
            bot.init(s, 0);
            bot.play(s.copy(), 0, 60_000);
            for (int turn = 0; turn < 4 && !Rules.gameOver(s, pathing); turn++) {
                GameState view = s.copy();
                long start = System.nanoTime();
                List<Action> actions = bot.play(view, 0, 36);
                worst = Math.max(worst, System.nanoTime() - start);
                Rules.resolve(s, actions, List.of(Action.waitAction()), pathing);
            }
        }
        assertTrue(worst < 36_000_000L, "slowest later turn " + (worst / 1_000_000.0) + " ms");
    }

    @Test void bothSeatsStayLegalWhileRegionsInk() {
        GameState s = MapGenerator.generate(4242);
        Pathing pathing = new Pathing(s.size);
        Rules.updateConnections(s, pathing);
        Bot[] bots = {BotFactory.create("indrill-forum-v1"), BotFactory.create("indrill-forum-v1")};
        bots[0].init(s, 0);
        bots[1].init(s, 1);
        for (int turn = 0; turn < 16 && !Rules.gameOver(s, pathing); turn++) {
            List<Action> a0 = bots[0].play(s.copy(), 0, 60_000);
            List<Action> a1 = bots[1].play(s.copy(), 1, 60_000);
            Rules.TurnResult result = Rules.resolve(s, a0, a1, pathing);
            for (int p = 0; p < 2; p++) {
                assertNull(result.moves[p].error);
                assertTrue(result.moves[p].skipped.isEmpty(), result.moves[p].skipped.toString());
                assertTrue(result.moves[p].cost <= 3);
                assertTrue(result.moves[p].disrupts.size() <= 1);
            }
        }
    }

    private static boolean nextToTown(GameState s, int cell) {
        for (int d = 0; d < 4; d++) {
            int x = s.x(cell) + GameState.DX[d], y = s.y(cell) + GameState.DY[d];
            if (s.inBounds(x, y) && s.isTown(s.cell(x, y))) return true;
        }
        return false;
    }

    private static boolean nextTo(Set<Integer> placed, GameState s, int cell) {
        for (int other : placed) {
            if (other == cell) continue;
            if (Math.abs(s.x(other) - s.x(cell)) + Math.abs(s.y(other) - s.y(cell)) == 1) return true;
        }
        return false;
    }
}
