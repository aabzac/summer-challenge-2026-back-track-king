package btk.bot;

import btk.bot.current.RolloutBot;
import btk.core.GameState;
import btk.referee.MapGenerator;
import btk.referee.Referee;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * opp-hold-v1 (2026-10-26): BOSS_OPP_HOLD_MARGIN is the hold margin of the lite chooser when it plays the simulated
 * opponent; negative (the default) inherits BOSS_HOLD_MARGIN. The artifact at the default is byte-identical
 * (OppHoldSubmissionTest).
 */
class OppHoldTest {
    static final String O0 = "current?BOSS_OPP_HOLD_MARGIN=0";

    private static Referee.Result play(Bot a, String b, long seed) {
        GameState initial = MapGenerator.generate(seed);
        Referee referee = new Referee();
        referee.firstTurnBudgetMs = 486; referee.turnBudgetMs = 36;
        return referee.play(initial, a, BotFactory.create(b), seed, false);
    }

    @Test void defaultInheritsAndGuards() {
        assertEquals(-1.0, new Params().BOSS_OPP_HOLD_MARGIN);
        assertEquals(0.0, BotFactory.parameters(O0).BOSS_OPP_HOLD_MARGIN);
        assertEquals(2.0, BotFactory.parameters("current?BOSS_OPP_HOLD_MARGIN=2").BOSS_OPP_HOLD_MARGIN);
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("v12?BOSS_OPP_HOLD_MARGIN=0"), "current-only experiment");
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("current?USE_BOSS_OPPONENT_MODEL=0,BOSS_OPP_HOLD_MARGIN=0"), "needs the opponent model");
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("current?USE_ROLLOUT_POLICY=0,BOSS_OPP_HOLD_MARGIN=0"), "rollout only");
        assertFalse(BotFactory.configuration("current").containsKey("BOSS_OPP_HOLD_MARGIN"), "excluded from the configuration at the default");
        assertEquals("0.0", BotFactory.configuration(O0).get("BOSS_OPP_HOLD_MARGIN"));
        assertEquals("1.0", BotFactory.configuration(O0).get("BOSS_HOLD_MARGIN"), "the own margin is untouched");
        assertEquals("v12 (modified)", BotFactory.displayName(O0));
    }

    @Test void opponentModelUsesItsOwnMarginAndPlaysLegally() {
        int calls = 0;
        for (long seed : new long[]{821001L, 821002L}) {
            RolloutBot bot = (RolloutBot) BotFactory.create(O0 + ",NOISE=0");
            Referee.Result r = play(bot, "v11?NOISE=0", seed);
            assertNull(r.error[0], "opp-hold seat 0 error on seed " + seed);
            assertNull(r.error[1], "v11 seat 1 error on seed " + seed);
            assertTrue(r.turns > 20 && r.disruptTurns[0] >= 0.8 * r.turns, "boss-like disrupt rate on seed " + seed);
            assertTrue(r.placedCells[0] > 0 && r.paintSpent[0] >= 1.5 * r.turns, "still builds on seed " + seed);
            calls += bot.oppHoldCalls;
        }
        assertTrue(calls > 0, "the simulated opponent's choices go through the separate margin");
        RolloutBot off = (RolloutBot) BotFactory.create("current?NOISE=0");
        play(off, "v11?NOISE=0", 821001L);
        assertEquals(0, off.oppHoldCalls, "the default never takes the separate branch");
    }
}
