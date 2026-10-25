package btk.bot.v10;

import btk.bot.current.RolloutTestParams;
import btk.bot.Params;
import btk.bot.Timer;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import java.util.List;
import org.junit.jupiter.api.Test;
import static btk.bot.v10.RolloutDiverseRepliesTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** Leader-calibrated track valuation and corridor fallback (Indrill study, 2026-09-20). */
/** Moved from btk.bot.current by prune-v1 (2026-10-25): the mechanism under test now lives only in frozen v10. */
class RolloutTrackValueTest {
    /**
     * 7x3 board. Top row: towns 0 (0,0) and 1 (6,0), cells 1..5 in separate regions 1..5.
     * Middle row: one corridor region 7. Bottom row: towns 2 (0,2) and 3 (6,2), cells 15..19 in
     * one construction region 9. Pairs 0->1 and 2->3.
     */
    private static GameState board() {
        int[] regions = {0, 1, 2, 3, 4, 5, 6,
                         7, 7, 7, 7, 7, 7, 7,
                         8, 9, 9, 9, 9, 9, 10};
        var s = new GameState(7, 3, regions, new int[21], new int[]{0, 6, 0, 6}, new int[]{0, 0, 2, 2},
                new int[][]{{1}, {}, {3}, {}});
        Rules.updateConnections(s, new Pathing(s.size));
        return s;
    }

    private static int target(RolloutBotV10 bot, GameState s, int me) throws Exception {
        ((Timer) field(bot, "timer")).start(60_000);
        return (int) call(bot, "chooseTarget", new Class<?>[]{GameState.class, int.class}, s, me);
    }

    @Test void unfinishedOpponentProjectOutranksSmallActiveIncomeOnlyWithTheFlag() throws Exception {
        for (int me = 0; me < 2; me++) {
            var s = board();
            // Active 0-1 connection: one opponent track (region 3) plus neutral rails: opponent income 1.
            for (int c = 1; c <= 5; c++) s.owner[c] = c == 3 ? 1 - me : GameState.NEUTRAL;
            // Unfinished opponent project toward town 3: three tracks in region 9, not earning yet.
            for (int c = 15; c <= 17; c++) s.owner[c] = 1 - me;
            Rules.updateConnections(s, new Pathing(s.size));
            int[] income = new int[2];
            Rules.incomePerTurn(s, income);
            assertEquals(1, income[1 - me]);
            assertEquals(0, income[me]);

            var off = new RolloutBotV10(RolloutTestParams.base("NOISE=0"));
            var on = new RolloutBotV10(RolloutTestParams.base("NOISE=0,USE_ROLLOUT_TRACK_VALUE=1"));
            off.init(s, me); on.init(s, me);
            assertEquals(3, target(off, s, me), "v08 valuation: the earning region is worth 1 income x 5 turns + 0.4");
            assertEquals(9, target(on, s, me), "three unfinished tracks x 5 outrank 5 + 5 for the earning region");

            // SELF_ONLY: our chooser uses the new term, the simulated opponent keeps the v08 term.
            var self = new RolloutBotV10(RolloutTestParams.base("NOISE=0,USE_ROLLOUT_TRACK_VALUE=1,ROLLOUT_TRACK_VALUE_SELF_ONLY=1"));
            self.init(s, me);
            assertEquals(9, target(self, s, me));
            var mirror = s.copy();
            for (int c = 0; c < mirror.size; c++)
                if (mirror.owner[c] == 0 || mirror.owner[c] == 1) mirror.owner[c] = 1 - mirror.owner[c];
            Rules.updateConnections(mirror, new Pathing(mirror.size));
            assertEquals(target(off, mirror, 1 - me), target(self, mirror, 1 - me), "opponent model unchanged");
            assertEquals(9, target(on, mirror, 1 - me), "without SELF_ONLY both choosers change");

            // Own tracks in the same region cost more than opponent tracks earn: back to the active region.
            s.owner[18] = me; s.owner[19] = me;
            Rules.updateConnections(s, new Pathing(s.size));
            assertEquals(3, target(on, s, me), "3 x 5 - 2 x 6 = 3 < 10");
            assertEquals(3, target(off, s, me));
        }
    }

    @Test void flagOffKeepsTheExactV08TrackTermAndDefaultsStayOff() {
        var p = new Params();
        assertFalse(p.USE_ROLLOUT_TRACK_VALUE);
        assertEquals(5.0, p.ROLLOUT_OPP_TRACK_VALUE);
        assertEquals(6.0, p.ROLLOUT_OWN_TRACK_VALUE);
        assertThrows(IllegalArgumentException.class,
                () -> new RolloutBotV10(RolloutTestParams.base("ROLLOUT_OPP_TRACK_VALUE=-1")));
        assertThrows(IllegalArgumentException.class,
                () -> new RolloutBotV10(RolloutTestParams.base("ROLLOUT_OWN_TRACK_VALUE=NaN")));
    }

    @Test void flagOnStillEmitsLegalActionsAndHitsEveryTurnWhileOpponentTracksExist() {
        for (int me = 0; me < 2; me++) {
            var s = board();
            for (int c = 15; c <= 17; c++) s.owner[c] = 1 - me;
            Rules.updateConnections(s, new Pathing(s.size));
            var on = new RolloutBotV10(RolloutTestParams.base("NOISE=0,USE_ROLLOUT_TRACK_VALUE=1"));
            on.init(s, me);
            List<Action> actions = on.play(s, me, 60_000);
            assertTrue(actions.stream().anyMatch(a -> a.type == Action.Type.DISRUPT && a.disruptRegion(s) == 9),
                    "unfinished opponent tracks are hit even without income at stake: " + actions);
            var result = new Rules.TurnResult();
            Rules.expand(s, me, actions, new Pathing(s.size), result.moves[me], result);
            assertNull(result.moves[me].error);
            assertTrue(result.moves[me].skipped.isEmpty());
        }
    }
}
