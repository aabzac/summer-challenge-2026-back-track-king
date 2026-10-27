package btk.bot;

import btk.bot.current.RolloutBot;
import btk.core.GameState;
import btk.referee.MapGenerator;
import btk.referee.Referee;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * bypass-routes-v1 (2026-10-27): USE_BYPASS_ROUTES proposes, for each own earning connection that crosses regions at
 * instability >= BYPASS_MIN_INSTABILITY, the cheapest route avoiding them. Default off; the artifact without it is
 * byte-identical (BypassRoutesSubmissionTest).
 */
class BypassRoutesTest {
    static final String B = "current?USE_BYPASS_ROUTES=true";

    private static Referee.Result play(Bot a, String b, long seed) {
        GameState initial = MapGenerator.generate(seed);
        Referee referee = new Referee();
        referee.firstTurnBudgetMs = 486; referee.turnBudgetMs = 36;
        return referee.play(initial, a, BotFactory.create(b), seed, false);
    }

    @Test void defaultsOffAndGuards() {
        Params defaults = new Params();
        assertFalse(defaults.USE_BYPASS_ROUTES);
        assertEquals(2, defaults.BYPASS_MIN_INSTABILITY);
        assertEquals(0.5, defaults.BYPASS_WEIGHT);
        assertTrue(BotFactory.parameters(B).USE_BYPASS_ROUTES);
        assertEquals(3, BotFactory.parameters(B + ",BYPASS_MIN_INSTABILITY=3").BYPASS_MIN_INSTABILITY);
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("v12?USE_BYPASS_ROUTES=true"), "current-only experiment");
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("current?USE_ROLLOUT_POLICY=0,USE_BYPASS_ROUTES=true"), "rollout only");
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create(B + ",BYPASS_MIN_INSTABILITY=4"));
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create(B + ",BYPASS_WEIGHT=1.5"));
        for (String key : new String[]{"USE_BYPASS_ROUTES", "BYPASS_MIN_INSTABILITY", "BYPASS_WEIGHT"})
            assertFalse(BotFactory.configuration("current").containsKey(key), key + " excluded from the configuration while off");
        assertEquals("true", BotFactory.configuration(B).get("USE_BYPASS_ROUTES"));
        assertEquals("2", BotFactory.configuration(B).get("BYPASS_MIN_INSTABILITY"));
        assertEquals("0.5", BotFactory.configuration(B).get("BYPASS_WEIGHT"));
        assertEquals("v12 (modified)", BotFactory.displayName(B));
    }

    @Test void playsLegallyAndProposesBypasses() {
        long proposals = 0;
        for (long seed : new long[]{842001L, 842002L}) {
            RolloutBot bot = (RolloutBot) BotFactory.create(B + ",NOISE=0");
            CountingBot counting = new CountingBot(bot);
            Referee.Result r = play(counting, "v11?NOISE=0", seed);
            assertNull(r.error[0], "bypass seat 0 error on seed " + seed);
            assertNull(r.error[1], "v11 seat 1 error on seed " + seed);
            assertTrue(r.turns > 20 && r.disruptTurns[0] >= 0.8 * r.turns, "boss-like disrupt rate on seed " + seed);
            assertTrue(r.placedCells[0] > 0 && r.paintSpent[0] >= 1.5 * r.turns, "still builds on seed " + seed);
            proposals += counting.proposals;
        }
        assertTrue(proposals > 0, "threatened own connections receive bypass proposals in real games");
    }

    /** Sums the per-turn count of bypass proposals over a game. */
    static final class CountingBot implements Bot {
        final RolloutBot inner; long proposals;
        CountingBot(RolloutBot inner) { this.inner = inner; }
        @Override public String name() { return inner.name(); }
        @Override public void init(GameState s, int myId) { inner.init(s, myId); }
        @Override public java.util.List<btk.core.Action> play(GameState s, int myId, long budgetMs) {
            java.util.List<btk.core.Action> a = inner.play(s, myId, budgetMs); proposals += inner.bypassProposals; return a;
        }
    }
}
