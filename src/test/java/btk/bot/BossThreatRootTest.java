package btk.bot;

import btk.bot.current.RolloutBot;
import btk.core.GameState;
import btk.referee.MapGenerator;
import btk.referee.Referee;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stage 3–4 consistency flags (2026-10-25): USE_BOSS_ROOT_TARGET (our first simulated turn disrupts the real-turn lite
 * target, no no-disrupt alternative; selected since v11) and USE_BOSS_THREAT_MODEL (the route proposals' predicted
 * opponent target from the two-stage boss chooser; parked, pruned from current by prune-v2 on 2026-10-27 and still
 * runnable on v11/v12/v13).
 */
class BossThreatRootTest {
    // Since the v12 promotion the specs carry the exposure flag off, to stay the configurations the screen measured.
    static final String A = "current?USE_BOSS_THREAT_MODEL=true,USE_EXPOSURE_INCOME=0";
    static final String B = "current?USE_BOSS_ROOT_TARGET=true,USE_EXPOSURE_INCOME=0";

    private static Referee.Result play(Bot a, String b, long seed) {
        GameState initial = MapGenerator.generate(seed);
        Referee referee = new Referee();
        referee.firstTurnBudgetMs = 486; referee.turnBudgetMs = 36;
        return referee.play(initial, a, BotFactory.create(b), seed, false);
    }

    @Test void defaultsOffAndGuards() {
        Params defaults = new Params();
        assertFalse(defaults.USE_BOSS_THREAT_MODEL, "parked after the screen");
        assertTrue(defaults.USE_BOSS_ROOT_TARGET, "selected default since the v11 promotion (2026-10-25)");
        assertFalse(BotFactory.parameters(LiveTargetExperimentParams.V10_ON_CURRENT).USE_BOSS_ROOT_TARGET);
        assertFalse(BotFactory.parameters(LiveTargetExperimentParams.V09_ON_CURRENT).USE_BOSS_ROOT_TARGET, "the lite chooser off drops the inherited default");
        assertTrue(BotFactory.parameters(B).USE_BOSS_ROOT_TARGET);
        // prune-v2: the parked threat model left current; the frozen copies that carry it still run it.
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create(A));
        assertDoesNotThrow(() -> BotFactory.create("v13?USE_BOSS_THREAT_MODEL=true"));
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("current?USE_BOSS_OPPONENT_MODEL=0,USE_BOSS_THREAT_MODEL=true"));
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("current?" + LiveTargetExperimentParams.V10_OFF + ",USE_BOSS_THREAT_MODEL=true"));
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("current?" + LiveTargetExperimentParams.V10_OFF + ",USE_BOSS_ROOT_TARGET=true"));
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("v09?USE_BOSS_THREAT_MODEL=true"));
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("v10?USE_BOSS_ROOT_TARGET=true"));
        // The frozen v10 copy never had the fields; the proxy spec (lite off) keeps rejecting them.
        assertFalse(BotFactory.parameters("v10").USE_BOSS_THREAT_MODEL);
        assertFalse(BotFactory.parameters("v10").USE_BOSS_ROOT_TARGET);
    }

    @Test void rootTargetPlaysLegallyAndKeepsTheBossLikeProfile() {
        for (long seed : new long[]{779003L, 779004L}) {
            RolloutBot bot = (RolloutBot) BotFactory.create(B + ",NOISE=0");
            Referee.Result r = play(bot, "v09?NOISE=0", seed);
            assertNull(r.error[0], "root-target seat 0 error on seed " + seed);
            assertNull(r.error[1], "v09 seat 1 error on seed " + seed);
            assertTrue(r.turns > 20, "game length on seed " + seed);
            assertTrue(r.disruptTurns[0] >= 0.8 * r.turns, "boss-like disrupt rate on seed " + seed);
            // 1.5 paint/turn: the time-bounded search builds less under the full suite's parallel load (seed 779004 dipped below 2.0 once).
            assertTrue(r.placedCells[0] > 0 && r.paintSpent[0] >= 1.5 * r.turns, "still builds on seed " + seed + ": " + r.paintSpent[0] + " paint over " + r.turns + " turns");
            assertTrue(bot.completedComparisons > 0, "the last turn still completed comparisons");
        }
    }
}
