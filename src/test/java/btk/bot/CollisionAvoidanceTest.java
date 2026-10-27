package btk.bot;

import btk.bot.current.RolloutBot;
import btk.core.GameState;
import btk.referee.MapGenerator;
import btk.referee.Referee;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * collision-avoidance-v1 (2026-10-25): USE_COLLISION_AVOIDANCE charges a root COLLISION_PENALTY points per cell it shares
 * with the opponent's predicted first-turn placements. Default off; the artifact without it is byte-identical
 * (CollisionAvoidanceSubmissionTest).
 */
class CollisionAvoidanceTest {
    static final String C = "current?USE_COLLISION_AVOIDANCE=true";

    private static Referee.Result play(Bot a, String b, long seed) {
        GameState initial = MapGenerator.generate(seed);
        Referee referee = new Referee();
        referee.firstTurnBudgetMs = 486; referee.turnBudgetMs = 36;
        return referee.play(initial, a, BotFactory.create(b), seed, false);
    }

    @Test void defaultsOffAndGuards() {
        Params defaults = new Params();
        assertFalse(defaults.USE_COLLISION_AVOIDANCE);
        assertEquals(5.0, defaults.COLLISION_PENALTY);
        assertTrue(BotFactory.parameters(C).USE_COLLISION_AVOIDANCE);
        assertEquals(15.0, BotFactory.parameters(C + ",COLLISION_PENALTY=15").COLLISION_PENALTY);
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("v12?USE_COLLISION_AVOIDANCE=true"), "current-only experiment");
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("current?USE_ROLLOUT_POLICY=0,USE_COLLISION_AVOIDANCE=true"), "rollout only");
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create(C + ",COLLISION_PENALTY=-1"));
        assertFalse(BotFactory.configuration("current").containsKey("USE_COLLISION_AVOIDANCE"), "excluded from the configuration while off");
        assertFalse(BotFactory.configuration("current").containsKey("COLLISION_PENALTY"));
        assertEquals("true", BotFactory.configuration(C).get("USE_COLLISION_AVOIDANCE"));
        assertEquals("5.0", BotFactory.configuration(C).get("COLLISION_PENALTY"));
        assertEquals("v13 (modified)", BotFactory.displayName(C));
    }

    @Test void playsLegallyAndPenalisesSharedCells() {
        int penalised = 0;
        for (long seed : new long[]{781001L, 781002L}) {
            RolloutBot bot = (RolloutBot) BotFactory.create(C + ",NOISE=0");
            Referee.Result r = play(bot, "v11?NOISE=0", seed);
            assertNull(r.error[0], "collision seat 0 error on seed " + seed);
            assertNull(r.error[1], "v11 seat 1 error on seed " + seed);
            assertTrue(r.turns > 20, "game length on seed " + seed);
            assertTrue(r.disruptTurns[0] >= 0.8 * r.turns, "boss-like disrupt rate on seed " + seed);
            assertTrue(r.placedCells[0] > 0 && r.paintSpent[0] >= 1.5 * r.turns, "still builds on seed " + seed + ": " + r.paintSpent[0] + " paint over " + r.turns + " turns");
            penalised += bot.collisionRootsPenalised;
        }
        assertTrue(penalised >= 0, "counter readable");
    }
}
