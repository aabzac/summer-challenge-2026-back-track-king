package btk.bot;

import btk.core.GameState;
import btk.referee.MapGenerator;
import btk.referee.Referee;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * boss-v4 (2026-10-26): USE_BOSS_NO_CLOSE makes the BossSabotage proxy skip one-hit closures (the live boss declines them
 * 94-98 % of the time) instead of taking the winning ones at once. Default off; the artifacts without it are
 * byte-identical (BossNoCloseSubmissionTest), and the current helper still hashes to the v10-v12 freeze with the block
 * stripped (V10/V11/V12PromotionTest).
 */
class BossNoCloseTest {
    static final String PROXY = "current?USE_BOSS_SABOTAGE=1,USE_BOSS_LITE=0,USE_BOSS_OPPONENT_MODEL=0,USE_BOSS_OWN_MODEL=0,USE_BOSS_LITE_ROUTES=0,"
            + "USE_INCREMENTAL_INK_REFRESH=0,USE_FAST_CHEAPEST_PATH=0,USE_BATCHED_REFRESH=0,USE_EXPOSURE_INCOME=0,USE_BYPASS_ROUTES=0";
    static final String NC = PROXY + ",USE_BOSS_NO_CLOSE=true";

    private static Referee.Result play(String a, String b, long seed) {
        GameState initial = MapGenerator.generate(seed);
        Referee referee = new Referee();
        referee.firstTurnBudgetMs = 486; referee.turnBudgetMs = 36;
        return referee.play(initial, BotFactory.create(a), BotFactory.create(b), seed, false);
    }

    @Test void defaultOffAndGuards() {
        assertFalse(new Params().USE_BOSS_NO_CLOSE);
        assertTrue(BotFactory.parameters(NC).USE_BOSS_NO_CLOSE);
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("current?USE_BOSS_NO_CLOSE=true"), "needs the BossSabotage proxy");
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("v12?USE_BOSS_NO_CLOSE=true"), "current-only");
        assertFalse(BotFactory.configuration(PROXY).containsKey("USE_BOSS_NO_CLOSE"), "excluded from the configuration while off");
        assertEquals("true", BotFactory.configuration(NC).get("USE_BOSS_NO_CLOSE"));
        assertNotEquals(BotFactory.configuration(PROXY), BotFactory.configuration(NC));
    }

    @Test void noCloseProxyPlaysLegallyLikeABoss() {
        for (long seed : new long[]{831001L, 831002L}) {
            Referee.Result r = play(NC, "v11?NOISE=0", seed);
            assertNull(r.error[0], "no-close proxy seat 0 error on seed " + seed);
            assertNull(r.error[1], "v11 seat 1 error on seed " + seed);
            assertTrue(r.turns > 20 && r.disruptTurns[0] >= 0.8 * r.turns, "boss-like disrupt rate on seed " + seed);
        }
    }
}
