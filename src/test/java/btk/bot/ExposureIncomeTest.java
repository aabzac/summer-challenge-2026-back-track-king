package btk.bot;

import btk.bot.current.RolloutBot;
import btk.core.GameState;
import btk.referee.MapGenerator;
import btk.referee.Referee;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * exposure-income-v1 (2026-10-25, Stage 4 second half): USE_EXPOSURE_INCOME weights every connection's income by the
 * survivability of the regions its path crosses (1 - weight * instability / threshold, town regions exempt) in the rollout's
 * tail and in the route proposals' completion gain. Default off; the artifact without it is byte-identical (ExposureIncomeSubmissionTest).
 */
class ExposureIncomeTest {
    static final String E = "current?USE_EXPOSURE_INCOME=true";

    private static Referee.Result play(Bot a, String b, long seed) {
        GameState initial = MapGenerator.generate(seed);
        Referee referee = new Referee();
        referee.firstTurnBudgetMs = 486; referee.turnBudgetMs = 36;
        return referee.play(initial, a, BotFactory.create(b), seed, false);
    }

    @Test void defaultsOffAndGuards() {
        Params defaults = new Params();
        assertTrue(defaults.USE_EXPOSURE_INCOME, "selected default since the v12 promotion (2026-10-25 night)");
        assertEquals(1.0, defaults.EXPOSURE_INCOME_WEIGHT);
        assertFalse(BotFactory.parameters(LiveTargetExperimentParams.V11_ON_CURRENT).USE_EXPOSURE_INCOME);
        assertFalse(BotFactory.parameters("v11").USE_EXPOSURE_INCOME);
        assertTrue(BotFactory.parameters(E).USE_EXPOSURE_INCOME);
        assertEquals(0.5, BotFactory.parameters(E + ",EXPOSURE_INCOME_WEIGHT=0.5").EXPOSURE_INCOME_WEIGHT);
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("v11?USE_EXPOSURE_INCOME=true"), "frozen v11 never had the flag");
        assertDoesNotThrow(() -> BotFactory.create("v12?USE_EXPOSURE_INCOME=true"), "the frozen v12 copy carries it");
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("v10?USE_EXPOSURE_INCOME=true"));
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("current?USE_ROLLOUT_POLICY=0,USE_EXPOSURE_INCOME=true"), "rollout only");
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create(E + ",EXPOSURE_INCOME_WEIGHT=1.5"), "weight in [0, 1]");
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create(E + ",EXPOSURE_INCOME_WEIGHT=-0.1"));
        assertFalse(BotFactory.configuration(LiveTargetExperimentParams.V11_ON_CURRENT).containsKey("USE_EXPOSURE_INCOME"), "excluded from the configuration while off");
        assertFalse(BotFactory.configuration(LiveTargetExperimentParams.V11_ON_CURRENT).containsKey("EXPOSURE_INCOME_WEIGHT"));
        assertFalse(BotFactory.parameters("current?USE_ROLLOUT_POLICY=0").USE_EXPOSURE_INCOME, "non-rollout policies drop the inherited default");
        assertEquals("true", BotFactory.configuration(E).get("USE_EXPOSURE_INCOME"));
        assertEquals("1.0", BotFactory.configuration(E).get("EXPOSURE_INCOME_WEIGHT"));
        assertEquals("v12", BotFactory.displayName(E));
        assertEquals("v12 (modified)", BotFactory.displayName(LiveTargetExperimentParams.V11_ON_CURRENT));
    }

    @Test void playsLegallyAndUsesTheDiscountedIncome() {
        for (long seed : new long[]{780001L, 780002L}) {
            RolloutBot bot = (RolloutBot) BotFactory.create(E + ",NOISE=0");
            Referee.Result r = play(bot, "v10?NOISE=0", seed);
            assertNull(r.error[0], "exposure seat 0 error on seed " + seed);
            assertNull(r.error[1], "v10 seat 1 error on seed " + seed);
            assertTrue(r.turns > 20, "game length on seed " + seed);
            assertTrue(bot.exposureIncomeCalls > 10 * r.turns, "discounted income evaluated inside the rollout: " + bot.exposureIncomeCalls);
            assertTrue(r.disruptTurns[0] >= 0.8 * r.turns, "boss-like disrupt rate on seed " + seed);
            assertTrue(r.placedCells[0] > 0 && r.paintSpent[0] >= 1.5 * r.turns, "still builds on seed " + seed + ": " + r.paintSpent[0] + " paint over " + r.turns + " turns");
        }
    }
}
