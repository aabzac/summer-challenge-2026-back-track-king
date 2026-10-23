package btk.bot.current;

import btk.bot.Params;
import btk.bot.Timer;
import btk.core.*;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static btk.bot.current.RolloutDiverseRepliesTest.*;
import static org.junit.jupiter.api.Assertions.*;

class RolloutNetworkAfterBaseTest {
    private static final String BASE = "USE_ROLLOUT_QUICK_ROUTE=1,NOISE=0,USE_DISRUPT=0";

    private static GameState chord(int owner) {
        int[] regions = new int[63];
        for (int c = 0; c < regions.length; c++) regions[c] = c;
        GameState s = new GameState(9, 7, regions, new int[63],
                new int[]{1, 7}, new int[]{3, 3}, new int[][]{{1}, {}});
        for (int c : new int[]{29, 30, 21, 22, 23, 32, 33}) s.owner[c] = owner;
        s.turn = 10;
        Rules.updateConnections(s, new Pathing(s.size));
        return s;
    }

    private static GameState corridor() {
        GameState s = new GameState(5, 1, new int[]{0, 1, 2, 3, 4}, new int[5],
                new int[]{0, 4}, new int[]{0, 0}, new int[][]{{1}, {}});
        Rules.updateConnections(s, new Pathing(s.size));
        return s;
    }

    private static RolloutBot bot(GameState s, int me, boolean afterBase) {
        RolloutBot b = new RolloutBot(RolloutTestParams.base(BASE + (afterBase ? ",USE_ROLLOUT_NETWORK_AFTER_BASE=1" : "")));
        b.init(s, me);
        return b;
    }

    private static String savedOrdinaryAction(GameState s, RolloutBot b) {
        var actions = new ArrayList<Action>();
        for (int i = 0; i < b.ordinarySelectedCount; i++) {
            int cell = b.ordinarySelectedCells[i];
            actions.add(Action.place(s.x(cell), s.y(cell)));
        }
        if (b.ordinarySelectedTarget >= 0) actions.add(Action.disrupt(b.ordinarySelectedTarget));
        if (actions.isEmpty()) actions.add(Action.waitAction());
        return Action.format(actions);
    }

    private static void restoreOrdinarySelection(RolloutBot b) throws Exception {
        // Recreate the boundary before the late phase from its actual saved ordinary result.
        var best = RolloutBot.class.getDeclaredField("best");
        best.setAccessible(true);
        best.setDouble(b, b.ordinarySelectedValue);
        var count = RolloutBot.class.getDeclaredField("bestCount");
        count.setAccessible(true);
        count.setInt(b, b.ordinarySelectedCount);
        System.arraycopy(b.ordinarySelectedCells, 0, (int[]) field(b, "bestCells"), 0, 3);
        b.selectedValue = b.ordinarySelectedValue;
        b.selectedRoute = b.ordinarySelectedRoute;
        b.selectedTarget = b.ordinarySelectedTarget;
        b.selectedNetwork = false;
        b.selectedNetworkCell = -1;
        b.networkComparisons = 0;
        b.completedComparisons = b.ordinaryComparisons;
        Arrays.fill((boolean[]) field(b, "networkCompared"), false);
    }

    private static void assertOrdinarySelectionRetained(RolloutBot b) throws Exception {
        assertEquals(b.ordinarySelectedValue, b.selectedValue);
        assertEquals(b.ordinarySelectedValue, (double) field(b, "best"));
        assertEquals(b.ordinarySelectedRoute, b.selectedRoute);
        assertEquals(b.ordinarySelectedTarget, b.selectedTarget);
        assertEquals(b.ordinarySelectedCount, (int) field(b, "bestCount"));
        assertArrayEquals(b.ordinarySelectedCells, (int[]) field(b, "bestCells"));
        assertFalse(b.selectedNetwork);
        assertEquals(-1, b.selectedNetworkCell);
    }

    @Test void ordinarySearchMatchesOffBeforeLateChordImprovementOnBothSides() {
        for (int me = 0; me < 2; me++) {
            GameState s = chord(1 - me);
            int[] owners = s.owner.clone();
            RolloutBot off = bot(s, me, false);
            String baseline = Action.format(off.play(s, me, 60_000));
            RolloutBot on = bot(s, me, true);
            var actions = on.play(s, me, 60_000);
            assertEquals("WAIT", baseline);
            assertTrue(on.ordinarySearchComplete);
            assertEquals(off.completedComparisons, on.ordinaryComparisons);
            assertEquals(off.routeQueries, on.routeQueries);
            assertEquals(off.selectedRoute, on.ordinarySelectedRoute);
            assertEquals(off.selectedTarget, on.ordinarySelectedTarget);
            assertEquals(off.selectedValue, on.ordinarySelectedValue);
            assertEquals(baseline, savedOrdinaryAction(s, on));
            assertTrue(on.networkSearchStarted);
            assertTrue(on.networkComparisons > 0);
            assertTrue(on.selectedNetwork);
            assertEquals(31, on.selectedNetworkCell);
            assertEquals("PLACE_TRACKS 4 3", Action.format(actions));
            assertTrue(on.selectedValue > on.ordinarySelectedValue);
            assertEquals(on.ordinaryComparisons + on.networkComparisons, on.completedComparisons);
            assertTrue(on.networkEvaluated <= 24);
            assertTrue(on.networkRoots <= 2);
            assertArrayEquals(owners, s.owner);
            assertArrayEquals(new int[2], s.score);
            assertEquals(10, s.turn);
            var result = new Rules.TurnResult();
            Rules.expand(s, me, actions, new Pathing(s.size), result.moves[me], result);
            assertNull(result.moves[me].error);
            assertTrue(result.moves[me].skipped.isEmpty());
        }
    }

    @Test void noAvailableNetworkMovePreservesCompletedOrdinaryDecision() {
        for (int me = 0; me < 2; me++) {
            GameState s = corridor();
            RolloutBot off = bot(s, me, false);
            String baseline = Action.format(off.play(s, me, 60_000));
            RolloutBot on = bot(s, me, true);
            String result = Action.format(on.play(s, me, 60_000));
            assertTrue(on.ordinarySearchComplete);
            assertEquals(off.completedComparisons, on.ordinaryComparisons);
            assertTrue(on.ordinaryComparisons >= 2, "both ordinary construction and WAIT are compared");
            assertEquals(off.selectedValue, on.ordinarySelectedValue);
            assertEquals(baseline, savedOrdinaryAction(s, on));
            assertEquals(baseline, result);
            assertEquals(on.ordinarySelectedValue, on.selectedValue);
            assertEquals(0, on.networkEligible);
            assertEquals(0, on.networkRoots);
            assertEquals(0, on.networkComparisons);
            assertFalse(on.selectedNetwork);
        }
    }

    @Test void rootOrderKeepsEveryOrdinaryRootAheadOfBothNetworkRoots() throws Exception {
        RolloutBot b = bot(chord(1), 0, true);
        var size = RolloutBot.class.getDeclaredField("ordinaryRootSize");
        size.setAccessible(true);
        size.setInt(b, 4);
        b.networkRoots = 2;
        int[] order = new int[6];
        for (int i = 0; i < order.length; i++)
            order[i] = (int) call(b, "searchRoot", new Class<?>[]{int.class}, i);
        assertArrayEquals(new int[]{0, 1, 2, 3, 4, 5}, order);
    }

    @Test void promotedFlagCanBeDisabledAndRejectsMixedNetworkPolicies() throws Exception {
        assertTrue(btk.bot.LiveTargetExperimentParams.v09Params().USE_ROLLOUT_NETWORK_AFTER_BASE);
        RolloutBot off = bot(chord(1), 0, false);
        assertNull(field(off, "networkCells"));
        for (String incompatible : new String[]{"USE_ROLLOUT_NETWORK=1", "USE_ROLLOUT_SPREAD=1",
                "USE_ROLLOUT_REPLAN=1", "USE_ROLLOUT_SECOND_TARGET=1", "USE_ROLLOUT_DIVERSE_REPLIES=1",
                "USE_ROLLOUT_QUICK_EARLY=1", "USE_ROLLOUT_QUICK_VALUE=1", "USE_ROLLOUT_QUICK_SAFE=1"}) {
            assertThrows(IllegalArgumentException.class, () -> new RolloutBot(
                    RolloutTestParams.base(BASE + ",USE_ROLLOUT_NETWORK_AFTER_BASE=1," + incompatible)));
        }
    }

    @Test void expiredPlayClearsOrdinaryAndNetworkDiagnosticsIncludingSavedCells() throws Exception {
        GameState s = corridor();
        RolloutBot b = bot(s, 0, true);
        b.play(s, 0, 60_000);
        assertTrue(b.ordinarySelectedCount > 0);
        assertFalse(Arrays.equals(new int[3], b.ordinarySelectedCells));
        assertEquals("WAIT", Action.format(b.play(s, 0, 0)));
        assertFalse(b.ordinarySearchComplete);
        assertFalse(b.networkSearchStarted);
        assertEquals(0, b.ordinaryComparisons);
        assertEquals(0, b.ordinarySelectedCount);
        assertEquals(-1, b.ordinarySelectedRoute);
        assertEquals(-1, b.ordinarySelectedTarget);
        assertTrue(Double.isNaN(b.ordinarySelectedValue));
        assertArrayEquals(new int[3], b.ordinarySelectedCells);
        assertEquals(0, b.networkEligible);
        assertEquals(0, b.networkEvaluated);
        assertEquals(0, b.networkRoots);
        assertEquals(0, b.networkComparisons);
        assertFalse(b.selectedNetwork);
        assertEquals(-1, b.selectedNetworkCell);
    }

    @Test void latePhaseCannotStartAfterIncompleteOrdinarySearchOrAnExpiredTimer() throws Exception {
        GameState s = chord(1);
        RolloutBot b = bot(s, 0, true);
        b.play(s, 0, 60_000);
        assertTrue(b.selectedNetwork);
        restoreOrdinarySelection(b);
        b.networkSearchStarted = false;
        b.ordinarySearchComplete = false;
        ((Timer) field(b, "timer")).start(60_000);
        call(b, "searchNetworkAfterBase", new Class<?>[]{GameState.class, int.class, int.class, long.class},
                s, 0, 1, 60_000L);
        assertFalse(b.networkSearchStarted);
        assertEquals(0, b.networkComparisons);
        assertOrdinarySelectionRetained(b);

        b.ordinarySearchComplete = true;
        ((Timer) field(b, "timer")).start(-1);
        call(b, "searchNetworkAfterBase", new Class<?>[]{GameState.class, int.class, int.class, long.class},
                s, 0, 1, 36L);
        assertFalse(b.networkSearchStarted);
        assertEquals(0, b.networkComparisons);
        assertOrdinarySelectionRetained(b);
    }

    @Test void unfinishedNetworkComparisonCannotReplaceTheSavedOrdinaryWinner() throws Exception {
        for (int me = 0; me < 2; me++) {
            GameState s = chord(1 - me);
            RolloutBot b = bot(s, me, true);
            b.play(s, me, 60_000);
            assertTrue(b.selectedNetwork, "the same new action wins when evaluation completes");
            int first = (int) field(b, "ordinaryRootSize");
            int end = first + b.networkRoots;
            assertTrue(end > first);
            restoreOrdinarySelection(b);
            ((Timer) field(b, "timer")).start(-1);
            boolean complete = (boolean) call(b, "searchRoots",
                    new Class<?>[]{GameState.class, int.class, int.class, int.class, int.class},
                    s, me, first, end, 2);
            assertFalse(complete);
            assertEquals(0, b.networkComparisons);
            assertEquals(b.ordinaryComparisons, b.completedComparisons);
            for (int i = first; i < end; i++) assertFalse(((boolean[]) field(b, "networkCompared"))[i]);
            assertOrdinarySelectionRetained(b);
        }
    }
}
