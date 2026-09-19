package btk.bot.current;

import btk.bot.Timer;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static btk.bot.current.RolloutDiverseRepliesTest.*;
import static org.junit.jupiter.api.Assertions.*;

class RolloutLiveTargetTest {
    /** 7x3 board, one region per cell; pairs 0->1 along the top row and 2->3 along the bottom row. */
    private static GameState board() {
        int[] regions = new int[21];
        for (int i = 0; i < 21; i++) regions[i] = i;
        var s = new GameState(7, 3, regions, new int[21], new int[]{0, 6, 0, 6}, new int[]{0, 0, 2, 2},
                new int[][]{{1}, {}, {3}, {}});
        Rules.updateConnections(s, new Pathing(s.size));
        return s;
    }

    private static int target(RolloutBot bot, GameState s, int me) throws Exception {
        ((Timer) field(bot, "timer")).start(60_000);
        return (int) call(bot, "chooseTarget", new Class<?>[]{GameState.class, int.class}, s, me);
    }

    @Test void liveOpponentConstructionOutranksStaleTracksAndExpiresAfterTheWindow() throws Exception {
        for (int me = 0; me < 2; me++) {
            var s = board();
            for (int c = 7; c <= 11; c++) s.owner[c] = 1 - me; // stale middle-row segment, present at init
            var off = new RolloutBot(RolloutTestParams.base("NOISE=0"));
            var on = new RolloutBot(RolloutTestParams.base("NOISE=0,USE_ROLLOUT_LIVE_TARGET=1"));
            off.init(s, me); on.init(s, me);
            off.play(s, me, 60_000); on.play(s, me, 60_000);
            assertEquals(target(off, s, me), target(on, s, me), "no observed placement: identical choice");
            assertEquals(0, on.liveTargets);

            s.turn = 1; s.owner[15] = 1 - me; s.owner[16] = 1 - me; // the opponent's live bottom-row project
            Rules.updateConnections(s, new Pathing(s.size));
            off.play(s, me, 60_000); on.play(s, me, 60_000);
            int stale = target(off, s, me), live = target(on, s, me);
            assertFalse(Set.of(15, 16).contains(stale), "equal per-track weights keep the stale segment first");
            assertTrue(Set.of(15, 16).contains(live), "live project region chosen: " + live);
            assertTrue(on.liveTargets > 0);

            s.turn = 4; // no further opponent placements: the window closes
            off.play(s, me, 60_000); on.play(s, me, 60_000);
            assertEquals(target(off, s, me), target(on, s, me));
            assertEquals(0, on.liveTargets);
        }
    }

    @Test void alwaysDisruptSpendsThePointOnlyWhenNothingElseQualifiesAndStaysLegal() throws Exception {
        for (int me = 0; me < 2; me++) {
            var s = board();
            var off = new RolloutBot(RolloutTestParams.base("NOISE=0"));
            var on = new RolloutBot(RolloutTestParams.base("NOISE=0,ROLLOUT_ALWAYS_DISRUPT=1"));
            off.init(s, me); on.init(s, me);
            assertEquals(-1, target(off, s, me), "empty board: default waits");
            int forced = target(on, s, me);
            assertTrue(forced >= 0 && !s.regionHasTown(forced), "forced target " + forced);
            assertEquals(1, on.forcedTargets);

            List<Action> actions = on.play(s, me, 60_000);
            assertTrue(actions.stream().anyMatch(a -> a.type == Action.Type.DISRUPT));
            var result = new Rules.TurnResult();
            Rules.expand(s, me, actions, new Pathing(s.size), result.moves[me], result);
            assertNull(result.moves[me].error);
            assertTrue(result.moves[me].skipped.isEmpty());

            // A losing one-hit closure is still refused: only region 1 remains between the towns.
            var closed = new GameState(6, 1, new int[]{0, 1, 1, 1, 1, 2}, new int[6],
                    new int[]{0, 5}, new int[2], new int[][]{{1}, {}});
            for (int c = 1; c < 5; c++) closed.owner[c] = me;
            for (int i = 0; i < 3; i++) Rules.disrupt(closed, 1);
            closed.score[1 - me] = 20;
            Rules.updateConnections(closed, new Pathing(closed.size));
            on.init(closed, me);
            assertEquals(-1, target(on, closed, me));
        }
    }
}
