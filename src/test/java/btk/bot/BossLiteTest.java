package btk.bot;

import btk.core.GameState;
import btk.referee.MapGenerator;
import btk.referee.Referee;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** boss-sabotage-v2: the two-stage chooser plays legal, boss-like, deterministic games at CG budgets. */
class BossLiteTest {
    private static final String LITE = "current?NOISE=0,USE_BOSS_LITE=true";

    private static Referee.Result play(String a, String b, long seed) {
        GameState initial = MapGenerator.generate(seed);
        Referee referee = new Referee();
        referee.firstTurnBudgetMs = 486; referee.turnBudgetMs = 36;
        return referee.play(initial, BotFactory.create(a), BotFactory.create(b), seed, true);
    }

    /** Time-bounded search is not replay-deterministic, so only legality and the boss-like profile are checked. */
    @Test void legalAndBossLikeAgainstV09AtCgBudgets() {
        for (long seed : new long[]{777000L, 778003L}) {
            Referee.Result r = play(LITE, "v09?NOISE=0", seed);
            assertNull(r.error[0], "lite seat 0 error on seed " + seed);
            assertNull(r.error[1], "v09 seat 1 error on seed " + seed);
            assertTrue(r.turns > 20, "game length on seed " + seed);
            assertTrue(r.disruptTurns[0] >= 0.8 * r.turns,
                    "boss-like disrupt rate on seed " + seed + ": " + r.disruptTurns[0] + "/" + r.turns);
            assertTrue(r.placedCells[0] > 0, "lite still builds on seed " + seed);
            assertTrue(r.paintSpent[0] >= 2.0 * r.turns, "lite keeps spending paint (" + r.paintSpent[0] + " over " + r.turns + " turns) on seed " + seed);
        }
    }

    @Test void flagIsRejectedOutsideTheCurrentRolloutAndTogetherWithV1() {
        assertFalse(new Params().USE_BOSS_LITE);
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("v09?USE_BOSS_LITE=true"));
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("current?USE_BOSS_LITE=true,USE_BOSS_SABOTAGE=true"));
        Params frozen = BotFactory.parameters("v09");
        assertFalse(frozen.USE_BOSS_LITE);
        assertTrue(BotFactory.parameters(LITE).USE_BOSS_LITE);
    }

    @Test void opponentModelPlaysLegallyAndCallsTheChooserInsideTheRollout() {
        GameState initial = MapGenerator.generate(778003L);
        Referee referee = new Referee();
        referee.firstTurnBudgetMs = 486; referee.turnBudgetMs = 36;
        var bot = (btk.bot.current.RolloutBot) BotFactory.create(LITE + ",USE_BOSS_OPPONENT_MODEL=true,USE_BOSS_OWN_MODEL=true");
        Referee.Result r = referee.play(initial, bot, BotFactory.create("v09?NOISE=0"), 778003L, false);
        assertNull(r.error[0]); assertNull(r.error[1]);
        assertTrue(r.turns > 20);
        assertTrue(bot.bossModelCalls > 10 * r.turns, "simulated opponent used the boss chooser: " + bot.bossModelCalls);
        assertTrue(r.placedCells[0] > 0);
    }
}
