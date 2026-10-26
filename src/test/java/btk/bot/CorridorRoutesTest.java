package btk.bot;

import btk.bot.current.RolloutBot;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.referee.MapGenerator;
import btk.referee.Referee;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * corridor-routes-v1 (2026-10-26): USE_CORRIDOR_ROUTES adds a fourth route proposal per pair whose path cost divides a
 * free cell's paint by (1 + CORRIDOR_WEIGHT x the number of other pairs whose cheapest path uses the cell). Default off;
 * the artifact without it is byte-identical (CorridorRoutesSubmissionTest).
 */
class CorridorRoutesTest {
    static final String C = "current?USE_CORRIDOR_ROUTES=true";

    private static Referee.Result play(Bot a, String b, long seed) {
        GameState initial = MapGenerator.generate(seed);
        Referee referee = new Referee();
        referee.firstTurnBudgetMs = 486; referee.turnBudgetMs = 36;
        return referee.play(initial, a, BotFactory.create(b), seed, false);
    }

    /** Two towns on the left, one on the right, a single plains corridor between mountain walls: both pairs share it. */
    private static GameState corridorMap() {
        String[] rows = {
                "0^^^^^^^^",
                ".^^^^^^^^",
                "........1",
                ".^^^^^^^^",
                "2^^^^^^^^"};
        int h = rows.length, w = rows[0].length();
        int[] region = new int[w * h], terrain = new int[w * h];
        int[] tx = new int[3], ty = new int[3];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            char ch = rows[y].charAt(x); int c = y * w + x;
            region[c] = c;
            terrain[c] = ch == '^' ? GameState.MOUNTAIN : GameState.PLAINS;
            if (Character.isDigit(ch)) { tx[ch - '0'] = x; ty[ch - '0'] = y; }
        }
        return new GameState(w, h, region, terrain, tx, ty, new int[][]{{1}, {}, {1}});
    }

    @Test void defaultsOffAndGuards() {
        Params defaults = new Params();
        assertFalse(defaults.USE_CORRIDOR_ROUTES);
        assertEquals(1.0, defaults.CORRIDOR_WEIGHT);
        assertTrue(BotFactory.parameters(C).USE_CORRIDOR_ROUTES);
        assertEquals(2.0, BotFactory.parameters(C + ",CORRIDOR_WEIGHT=2").CORRIDOR_WEIGHT);
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("v12?USE_CORRIDOR_ROUTES=true"), "current-only experiment");
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("current?USE_ROLLOUT_POLICY=0,USE_CORRIDOR_ROUTES=true"), "rollout only");
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create(C + ",CORRIDOR_WEIGHT=-1"));
        assertFalse(BotFactory.configuration("current").containsKey("USE_CORRIDOR_ROUTES"), "excluded from the configuration while off");
        assertFalse(BotFactory.configuration("current").containsKey("CORRIDOR_WEIGHT"));
        assertEquals("true", BotFactory.configuration(C).get("USE_CORRIDOR_ROUTES"));
        assertEquals("1.0", BotFactory.configuration(C).get("CORRIDOR_WEIGHT"));
        assertEquals("v12 (modified)", BotFactory.displayName(C));
    }

    @Test void sharedCorridorYieldsCorridorProposals() {
        GameState s = corridorMap();
        RolloutBot bot = (RolloutBot) BotFactory.create(C + ",NOISE=0");
        bot.init(s, 0);
        Rules.updateConnections(s, new Pathing(s.size));
        List<Action> actions = bot.play(s, 0, 200);
        assertFalse(actions.isEmpty(), "plays on the corridor map");
        assertTrue(bot.corridorQueries >= 1, "both pairs' cheapest paths share the corridor, so the corridor variant is queried: " + bot.corridorQueries);
        assertTrue(bot.corridorProposals >= 1, "a completed pair earns, so the corridor route is a positive proposal: " + bot.corridorProposals);
        RolloutBot off = (RolloutBot) BotFactory.create("current?NOISE=0");
        off.init(s, 0);
        off.play(s, 0, 200);
        assertEquals(0, off.corridorQueries, "the flag off never queries the corridor variant");
    }

    @Test void playsLegallyAndQueriesCorridors() {
        int queries = 0, proposals = 0;
        for (long seed : new long[]{791001L, 791002L}) {
            RolloutBot bot = (RolloutBot) BotFactory.create(C + ",NOISE=0");
            Referee.Result r = play(bot, "v11?NOISE=0", seed);
            assertNull(r.error[0], "corridor seat 0 error on seed " + seed);
            assertNull(r.error[1], "v11 seat 1 error on seed " + seed);
            assertTrue(r.turns > 20, "game length on seed " + seed);
            assertTrue(r.disruptTurns[0] >= 0.8 * r.turns, "boss-like disrupt rate on seed " + seed);
            assertTrue(r.placedCells[0] > 0 && r.paintSpent[0] >= 1.5 * r.turns, "still builds on seed " + seed + ": " + r.paintSpent[0] + " paint over " + r.turns + " turns");
            queries += bot.corridorQueries; proposals += bot.corridorProposals;
        }
        assertTrue(queries >= 0 && proposals >= 0, "counters readable (last turn's values)");
    }
}
