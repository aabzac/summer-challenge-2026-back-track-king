package btk.bot.current;

import btk.bot.Params;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Never-idle leftover paint (USE_ROLLOUT_FILL; boss and leader cell study, 2026-09-20). */
class RolloutFillTest {
    /**
     * 7x3 board. Top row: towns 0 (0,0) and 1 (6,0), cells 1..5 in separate regions 1..5.
     * Middle row: one region 7. Bottom row: towns 2 (0,2) and 3 (6,2), cells 15..19 in region 9.
     * Pair 0->1 only, so once it is connected nothing is left to complete.
     */
    private static GameState board() {
        int[] regions = {0, 1, 2, 3, 4, 5, 6,
                         7, 7, 7, 7, 7, 7, 7,
                         8, 9, 9, 9, 9, 9, 10};
        var s = new GameState(7, 3, regions, new int[21], new int[]{0, 6, 0, 6}, new int[]{0, 0, 2, 2},
                new int[][]{{1}, {}, {}, {}});
        Rules.updateConnections(s, new Pathing(s.size));
        return s;
    }

    private static int paint(GameState s, List<Action> actions) {
        int paint = 0;
        for (Action a : actions) if (a.type == Action.Type.PLACE) paint += s.placeCost(s.cell(a.x, a.y));
        return paint;
    }

    private static void assertLegal(GameState s, int me, List<Action> actions) {
        var result = new Rules.TurnResult();
        Rules.expand(s, me, actions, new Pathing(s.size), result.moves[me], result);
        assertNull(result.moves[me].error);
        assertTrue(result.moves[me].skipped.isEmpty(), "skipped: " + result.moves[me].skipped);
    }

    @Test void selectedDefaultIsOnAndFlagOffLeavesPaintUnusedWhenNothingCompletes() {
        assertTrue(btk.bot.LiveTargetExperimentParams.v09Params().USE_ROLLOUT_FILL, "selected v09 default");
        assertEquals(0, btk.bot.LiveTargetExperimentParams.v09Params().ROLLOUT_FILL_MAX_INSTABILITY);
        assertFalse(RolloutTestParams.base().USE_ROLLOUT_FILL, "historical fixtures opt out");
        for (int me = 0; me < 2; me++) {
            var s = board();
            for (int c = 1; c <= 5; c++) s.owner[c] = me; // the only pair is already ours
            Rules.updateConnections(s, new Pathing(s.size));
            var off = new RolloutBot(RolloutTestParams.base("NOISE=0"));
            off.init(s, me);
            List<Action> actions = off.play(s, me, 60_000);
            assertEquals(0, paint(s, actions), "v08 has nothing to build here: " + actions);
        }
    }

    @Test void flagOnSpendsAllPaintNextToOwnEarningRailsOutsideUnstableAndTargetRegions() {
        for (int me = 0; me < 2; me++) {
            var s = board();
            for (int c = 1; c <= 5; c++) s.owner[c] = me;
            s.regionInstability[7] = 1; // corridor threatened: the filler must avoid it
            for (int c = 7; c <= 13; c++) s.instability[c] = 1;
            Rules.updateConnections(s, new Pathing(s.size));
            var on = new RolloutBot(RolloutTestParams.base("NOISE=0,USE_ROLLOUT_FILL=1"));
            on.init(s, me);
            List<Action> actions = on.play(s, me, 60_000);
            assertLegal(s, me, actions);
            // Every free cell adjacent to our rails lies in the unstable corridor: nothing legal to fill.
            assertEquals(0, paint(s, actions), "no stable cell touches our network: " + actions);
            assertEquals(0, on.fillCells);

            var relaxed = new RolloutBot(RolloutTestParams.base("NOISE=0,USE_ROLLOUT_FILL=1,ROLLOUT_FILL_MAX_INSTABILITY=1"));
            relaxed.init(s, me);
            actions = relaxed.play(s, me, 60_000);
            assertLegal(s, me, actions);
            assertEquals(GameState.PAINT_PER_TURN, paint(s, actions), "leftover paint is spent: " + actions);
            assertEquals(GameState.PAINT_PER_TURN, relaxed.fillCells);
            for (Action a : actions) {
                if (a.type != Action.Type.PLACE) continue;
                int c = s.cell(a.x, a.y);
                assertEquals(7, s.regionId[c], "filler grows the own network into the corridor");
                assertNotEquals(relaxed.selectedTarget, s.regionId[c], "never inside the region we are inking");
                boolean adjacentOwn = false;
                for (int d = 0; d < 4; d++) {
                    int nx = a.x + GameState.DX[d], ny = a.y + GameState.DY[d];
                    if (s.inBounds(nx, ny) && s.owner[s.cell(nx, ny)] == me) adjacentOwn = true;
                }
                assertTrue(adjacentOwn || actions.stream().anyMatch(b -> b.type == Action.Type.PLACE
                        && Math.abs(b.x - a.x) + Math.abs(b.y - a.y) == 1), "adjacent to own or newly placed rail: " + a);
            }
        }
    }

    @Test void flagOnDoesNotChangeTheSearchedConstructionWhenPaintIsAlreadyFull() {
        for (int me = 0; me < 2; me++) {
            var s = board();
            var off = new RolloutBot(RolloutTestParams.base("NOISE=0"));
            var on = new RolloutBot(RolloutTestParams.base("NOISE=0,USE_ROLLOUT_FILL=1"));
            off.init(s, me); on.init(s, me);
            List<Action> a = off.play(s, me, 60_000), b = on.play(s, me, 60_000);
            assertEquals(GameState.PAINT_PER_TURN, paint(s, a), "opening spends full paint: " + a);
            assertEquals(a.toString(), b.toString(), "filler has nothing to add");
            assertEquals(0, on.fillCells);
        }
    }
}
