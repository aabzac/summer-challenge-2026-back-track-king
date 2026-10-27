package btk.bot;

import btk.bot.current.RolloutBot;
import btk.core.GameState;
import btk.referee.MapGenerator;
import btk.referee.Referee;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * route-commit-v1 (2026-10-27): USE_ROUTE_COMMIT gives a root that places a cell on the unbuilt part of last turn's chosen
 * route COMMIT_BONUS rollout-value points. Default off; the artifact without it is byte-identical (RouteCommitSubmissionTest).
 */
class RouteCommitTest {
    static final String C = "current?USE_ROUTE_COMMIT=true";

    private static Referee.Result play(Bot a, String b, long seed) {
        GameState initial = MapGenerator.generate(seed);
        Referee referee = new Referee();
        referee.firstTurnBudgetMs = 486; referee.turnBudgetMs = 36;
        return referee.play(initial, a, BotFactory.create(b), seed, false);
    }

    @Test void defaultsOffAndGuards() {
        Params defaults = new Params();
        assertFalse(defaults.USE_ROUTE_COMMIT);
        assertEquals(10.0, defaults.COMMIT_BONUS);
        assertTrue(BotFactory.parameters(C).USE_ROUTE_COMMIT);
        assertEquals(25.0, BotFactory.parameters(C + ",COMMIT_BONUS=25").COMMIT_BONUS);
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("v12?USE_ROUTE_COMMIT=true"), "current-only experiment");
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("current?USE_ROLLOUT_POLICY=0,USE_ROUTE_COMMIT=true"), "rollout only");
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create(C + ",COMMIT_BONUS=-1"));
        assertFalse(BotFactory.configuration("current").containsKey("USE_ROUTE_COMMIT"), "excluded from the configuration while off");
        assertFalse(BotFactory.configuration("current").containsKey("COMMIT_BONUS"));
        assertEquals("true", BotFactory.configuration(C).get("USE_ROUTE_COMMIT"));
        assertEquals("10.0", BotFactory.configuration(C).get("COMMIT_BONUS"));
        assertEquals("v13 (modified)", BotFactory.displayName(C));
    }

    @Test void playsLegallyAndBonusesContinuations() {
        long bonused = 0;
        for (long seed : new long[]{841001L, 841002L}) {
            RolloutBot bot = (RolloutBot) BotFactory.create(C + ",NOISE=0") ;
            CountingBot counting = new CountingBot(bot);
            Referee.Result r = play(counting, "v11?NOISE=0", seed);
            assertNull(r.error[0], "commit seat 0 error on seed " + seed);
            assertNull(r.error[1], "v11 seat 1 error on seed " + seed);
            assertTrue(r.turns > 20 && r.disruptTurns[0] >= 0.8 * r.turns, "boss-like disrupt rate on seed " + seed);
            assertTrue(r.placedCells[0] > 0 && r.paintSpent[0] >= 1.5 * r.turns, "still builds on seed " + seed);
            bonused += counting.bonused;
        }
        assertTrue(bonused > 0, "continuations of a half-built route receive the bonus in real games");
    }

    /** Sums the per-turn counter of bonused roots over a game. */
    static final class CountingBot implements Bot {
        final RolloutBot inner; long bonused;
        CountingBot(RolloutBot inner) { this.inner = inner; }
        @Override public String name() { return inner.name(); }
        @Override public void init(GameState s, int myId) { inner.init(s, myId); }
        @Override public java.util.List<btk.core.Action> play(GameState s, int myId, long budgetMs) {
            java.util.List<btk.core.Action> a = inner.play(s, myId, budgetMs); bonused += inner.commitRootsBonused; return a;
        }
    }
}
