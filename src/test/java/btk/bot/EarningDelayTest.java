package btk.bot;

import btk.bot.current.RolloutBot;
import btk.core.GameState;
import btk.referee.MapGenerator;
import btk.referee.Referee;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * earning-delay-v1 (2026-10-26): USE_QUICK_ALWAYS keeps the cheapest positive completion among the root routes every
 * turn; DELAY_POWER raises the turns-to-complete divisor of a proposal's value to a power. Defaults off; the artifact
 * without them is byte-identical (EarningDelaySubmissionTest).
 */
class EarningDelayTest {
    static final String Q = "current?USE_QUICK_ALWAYS=true";
    static final String D2 = "current?DELAY_POWER=2";

    private static Referee.Result play(Bot a, String b, long seed) {
        GameState initial = MapGenerator.generate(seed);
        Referee referee = new Referee();
        referee.firstTurnBudgetMs = 486; referee.turnBudgetMs = 36;
        return referee.play(initial, a, BotFactory.create(b), seed, false);
    }

    @Test void defaultsOffAndGuards() {
        Params defaults = new Params();
        assertFalse(defaults.USE_QUICK_ALWAYS);
        assertEquals(1.0, defaults.DELAY_POWER);
        assertTrue(BotFactory.parameters(Q).USE_QUICK_ALWAYS);
        assertEquals(2.0, BotFactory.parameters(D2).DELAY_POWER);
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("v12?USE_QUICK_ALWAYS=true"), "current-only experiment");
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("v12?DELAY_POWER=2"), "current-only experiment");
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("current?USE_ROLLOUT_POLICY=0,USE_QUICK_ALWAYS=true"), "rollout only");
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("current?USE_ROLLOUT_QUICK_ROUTE=0,USE_QUICK_ALWAYS=true"), "needs the quick slot");
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("current?DELAY_POWER=0"));
        assertFalse(BotFactory.configuration("current").containsKey("USE_QUICK_ALWAYS"), "excluded from the configuration while off");
        assertFalse(BotFactory.configuration("current").containsKey("DELAY_POWER"), "excluded at its default");
        assertEquals("true", BotFactory.configuration(Q).get("USE_QUICK_ALWAYS"));
        assertFalse(BotFactory.configuration(Q).containsKey("DELAY_POWER"));
        assertEquals("2.0", BotFactory.configuration(D2).get("DELAY_POWER"));
        assertFalse(BotFactory.configuration(D2).containsKey("USE_QUICK_ALWAYS"));
        assertEquals("v13 (modified)", BotFactory.displayName(Q));
        assertEquals("v13 (modified)", BotFactory.displayName(D2));
    }

    @Test void bothArmsPlayLegally() {
        for (String spec : new String[]{Q, D2}) {
            for (long seed : new long[]{811001L, 811002L}) {
                RolloutBot bot = (RolloutBot) BotFactory.create(spec + ",NOISE=0");
                Referee.Result r = play(bot, "v11?NOISE=0", seed);
                assertNull(r.error[0], spec + " seat 0 error on seed " + seed);
                assertNull(r.error[1], "v11 seat 1 error on seed " + seed);
                assertTrue(r.turns > 20, "game length on seed " + seed);
                assertTrue(r.disruptTurns[0] >= 0.8 * r.turns, "boss-like disrupt rate on seed " + seed);
                assertTrue(r.placedCells[0] > 0 && r.paintSpent[0] >= 1.5 * r.turns, spec + " still builds on seed " + seed);
            }
        }
    }
}
