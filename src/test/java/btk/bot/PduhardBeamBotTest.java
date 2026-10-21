package btk.bot;

import btk.bot.pduhard.PduhardBeamBot;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.referee.MapGenerator;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PduhardBeamBotTest {
    @Test void factoryLeavesTheSelectedBotUntouched() {
        assertEquals("pduhard-beam-v1", BotFactory.create("pduhard-beam-v1").name());
        assertInstanceOf(PduhardBeamBot.class, BotFactory.create("pduhard-beam-v1"));
        assertEquals("candidate", BotCatalog.describe("pduhard-beam-v1").category());
        assertEquals("local_only", BotCatalog.describe("pduhard-beam-v1").status());
        assertEquals("v09", BotFactory.create("v09").name());
        assertInstanceOf(btk.bot.current.RolloutBot.class, BotFactory.create("current"));
        assertEquals("WAIT", Action.format(BotFactory.create("pduhard-beam-v1").play(MapGenerator.generate(11), 0, 0)));
    }

    @Test void shortCorridorIsFilledFromTheDijkstraPath() {
        GameState s = corridor();
        PduhardBeamBot bot = (PduhardBeamBot) BotFactory.create("pduhard-beam-v1");
        bot.init(s, 0);
        List<Action> actions = bot.play(s.copy(), 0, 60_000);
        assertEquals(2, bot.depthReached);
        assertEquals(-1, bot.chosenDisrupt);
        assertEquals(3, bot.chosenCount);
        int paint = 0;
        for (int c : bot.chosenCells) {
            assertTrue(c >= 6 && c <= 8, "cell " + c);
            paint += s.placeCost(c);
        }
        assertEquals(3, paint);
        assertLegal(s, 0, actions);
    }

    @Test void readyInkRemovesOpponentIncome() {
        GameState s = new GameState(7, 1, regions(7, 1), plains(7),
                new int[]{0, 6}, new int[]{0, 0}, new int[][]{{1}, {}});
        for (int c = 1; c <= 5; c++) {
            s.owner[c] = 1;
            s.instability[c] = 3;
        }
        s.regionInstability[1] = 3;
        Rules.updateConnections(s, new Pathing(s.size));
        PduhardBeamBot defender = (PduhardBeamBot) BotFactory.create("pduhard-beam-v1");
        defender.init(s, 0);
        List<Action> defense = defender.play(s.copy(), 0, 60_000);
        assertEquals(1, defender.chosenDisrupt);
        assertTrue(defense.stream().anyMatch(a -> a.type == Action.Type.DISRUPT && a.regionId == 1));
        assertLegal(s, 0, defense);

        PduhardBeamBot owner = (PduhardBeamBot) BotFactory.create("pduhard-beam-v1");
        owner.init(s, 1);
        List<Action> keep = owner.play(s.copy(), 1, 60_000);
        assertEquals(-1, owner.chosenDisrupt);
        assertEquals("WAIT", Action.format(keep));
    }

    @Test void sameStateIsDeterministic() {
        GameState s = MapGenerator.generate(88011);
        Rules.updateConnections(s, new Pathing(s.size));
        Bot a = BotFactory.create("pduhard-beam-v1");
        Bot b = BotFactory.create("pduhard-beam-v1");
        a.init(s, 0);
        b.init(s, 0);
        assertEquals(Action.format(a.play(s.copy(), 0, 60_000)), Action.format(b.play(s.copy(), 0, 60_000)));
    }

    @Test void laterTurnsFitTheNominalSearchBudget() {
        long worst = 0;
        for (int seed = 1; seed <= 6; seed++) {
            GameState s = MapGenerator.generate(88100 + seed);
            Pathing pathing = new Pathing(s.size);
            Rules.updateConnections(s, pathing);
            Bot bot = BotFactory.create("pduhard-beam-v1");
            bot.init(s, 0);
            bot.play(s.copy(), 0, 60_000);
            for (int turn = 0; turn < 4 && !Rules.gameOver(s, pathing); turn++) {
                long start = System.nanoTime();
                List<Action> actions = bot.play(s.copy(), 0, 36);
                worst = Math.max(worst, System.nanoTime() - start);
                assertLegal(s, 0, actions);
                Rules.resolve(s, actions, List.of(Action.waitAction()), pathing);
            }
        }
        assertTrue(worst < 36_000_000L, "slowest later turn " + (worst / 1_000_000.0) + " ms");
    }

    @Test void bothSeatsStayLegalWhileRegionsInk() {
        GameState s = MapGenerator.generate(4242);
        Pathing pathing = new Pathing(s.size);
        Rules.updateConnections(s, pathing);
        Bot[] bots = {BotFactory.create("pduhard-beam-v1"), BotFactory.create("pduhard-beam-v1")};
        bots[0].init(s, 0);
        bots[1].init(s, 1);
        for (int turn = 0; turn < 16 && !Rules.gameOver(s, pathing); turn++) {
            List<Action> a0 = bots[0].play(s.copy(), 0, 60_000);
            List<Action> a1 = bots[1].play(s.copy(), 1, 60_000);
            Rules.TurnResult result = Rules.resolve(s, a0, a1, pathing);
            for (int p = 0; p < 2; p++) {
                assertNull(result.moves[p].error, result.moves[p].error);
                assertTrue(result.moves[p].skipped.isEmpty(), result.moves[p].skipped.toString());
                assertTrue(result.moves[p].cost <= 3);
                assertTrue(result.moves[p].disrupts.size() <= 1);
            }
        }
    }

    private static void assertLegal(GameState s, int player, List<Action> actions) {
        var moves = new Rules.PlayerMoves();
        Rules.expand(s, player, actions, new Pathing(s.size), moves, new Rules.TurnResult());
        assertNull(moves.error, moves.error);
        assertTrue(moves.skipped.isEmpty(), moves.skipped.toString());
        assertTrue(moves.cost <= 3);
        assertTrue(moves.disrupts.size() <= 1);
    }

    private static GameState corridor() {
        GameState s = new GameState(5, 3, new int[15], plains(15),
                new int[]{0, 4}, new int[]{1, 1}, new int[][]{{1}, {}});
        Rules.updateConnections(s, new Pathing(s.size));
        return s;
    }

    private static int[] regions(int width, int middle) {
        int[] region = new int[width];
        Arrays.fill(region, middle);
        if (middle != 0) {
            region[0] = 0;
            region[width - 1] = 0;
        }
        return region;
    }

    private static int[] plains(int n) {
        return new int[n];
    }
}
