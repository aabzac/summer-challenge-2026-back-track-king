package btk.bot.current;

import btk.bot.Params;
import btk.core.*;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static btk.bot.current.RolloutDiverseRepliesTest.*;
import static org.junit.jupiter.api.Assertions.*;

class RolloutRouteDependenciesTest {
    private static GameState bridge(int width, int owner) {
        int[] regions = new int[width];
        for (int c = 0; c < width; c++) regions[c] = c;
        regions[2] = 1;
        var s = new GameState(width, 1, regions, new int[width],
                new int[]{0, width - 1}, new int[]{0, 0}, new int[][]{{1}, {}});
        s.owner[1] = s.owner[2] = owner;
        Rules.updateConnections(s, new Pathing(s.size));
        return s;
    }

    private static GameState twoCorridors(int me, boolean firstShort) {
        int[] regions = new int[30];
        for (int c = 0; c < 30; c++) regions[c] = c;
        var s = new GameState(10, 3, regions, new int[30],
                new int[]{0, firstShort ? 4 : 9, 0, 9}, new int[]{0, 0, 2, 2},
                new int[][]{{1}, {}, {3}, {}});
        for (int c = 10; c < 20; c++) Rules.ink(s, c);
        s.owner[1] = me;
        if (!firstShort) s.owner[2] = me;
        for (int c = 21; c <= (firstShort ? 22 : 25); c++) s.owner[c] = me;
        Rules.updateConnections(s, new Pathing(s.size));
        return s;
    }

    private static RolloutBot bot(GameState s, int me, boolean enabled, String extra) throws Exception {
        var p = RolloutTestParams.base("NOISE=0,USE_DISRUPT=0," + extra);
        p.USE_ROLLOUT_ROUTE_DEPENDENCIES = enabled;
        var b = new RolloutBot(p);
        b.init(s, me);
        Arrays.fill((int[]) field(b, "threats"), -1);
        setInt(b, "quickPlayer", -1);
        return b;
    }

    private static void propose(RolloutBot b, GameState s, int me, int pair) throws Exception {
        call(b, "propose", new Class<?>[]{GameState.class, int.class, int.class, int.class}, s, me, pair, 0);
    }

    private static int[] build(RolloutBot b, GameState s, int me, int route) throws Exception {
        int[] cells = new int[3];
        int n = (int) call(b, "fillBuild", new Class<?>[]{GameState.class, int.class, int.class, int[].class},
                s, me, route, cells);
        return Arrays.copyOf(cells, n);
    }

    private static void setInt(RolloutBot b, String name, int value) throws Exception {
        var f = RolloutBot.class.getDeclaredField(name);
        f.setAccessible(true);
        f.setInt(b, value);
    }

    @Test void omittedExistingBridgeStopsSimulatedContinuationForEitherOwnershipAndSide() throws Exception {
        for (int me = 0; me < 2; me++) for (int owner = 0; owner < 2; owner++) {
            var s = bridge(7, owner);
            var off = bot(s, me, false, "");
            var on = bot(s, me, true, "");
            propose(off, s, me, 0);
            propose(on, s, me, 0);
            assertArrayEquals(new int[]{3, 4, 5}, build(on, s, me, 0));
            assertArrayEquals(build(off, s, me, 0), build(on, s, me, 0));
            assertEquals(1, ((int[]) field(on, "counts"))[me]);
            int[][][] routes = (int[][][]) field(on, "routes");
            int n = ((int[][]) field(on, "lengths"))[me][0];
            assertArrayEquals(new int[]{0, 3, 4, 5, 6}, Arrays.copyOf(routes[me][0], n));

            Rules.ink(s, 1);
            assertArrayEquals(new int[]{3, 4, 5}, build(off, s, me, 0), "old continuation misses omitted rails");
            assertArrayEquals(new int[0], build(on, s, me, 0));
            var completed = s.copy();
            for (int cell : build(off, s, me, 0)) completed.owner[cell] = me;
            Rules.updateConnections(completed, new Pathing(s.size));
            assertEquals(0, completed.pairPathLen[0], "the old continuation cannot complete its intended pair");
            assertEquals(GameState.NO_TRACK, s.owner[3], "packing must not mutate its input");
        }
    }

    @Test void disabledModeAllocatesNoDependencyBuffersAndNewDefaultStaysOff() throws Exception {
        assertFalse(new Params().USE_ROLLOUT_ROUTE_DEPENDENCIES);
        var b = bot(bridge(7, 0), 0, false, "USE_ROLLOUT_QUICK_ROUTE=1");
        for (String name : new String[]{"routeDependencies", "dependencyCounts", "proposalDependencies", "quickDependencies"})
            assertNull(field(b, name));
    }

    @Test void reverseBuildOrdersRetainSameBridgeWithoutChangingProposalsOrValues() throws Exception {
        var s = bridge(10, 0);
        var on = bot(s, 0, true, "");
        var off = bot(s, 0, false, "");
        propose(on, s, 0, 0);
        propose(off, s, 0, 0);
        assertEquals(2, ((int[]) field(on, "counts"))[0]);
        int[][][] a = (int[][][]) field(on, "routes"), b = (int[][][]) field(off, "routes");
        for (int route = 0; route < 2; route++) {
            assertArrayEquals(a[0][route], b[0][route]);
            assertArrayEquals(build(on, s, 0, route), build(off, s, 0, route));
        }
        assertArrayEquals(((double[][]) field(on, "values"))[0], ((double[][]) field(off, "values"))[0]);
        assertArrayEquals(new int[]{3, 4, 5}, build(on, s, 0, 0));
        assertArrayEquals(new int[]{8, 7, 6}, build(on, s, 0, 1));
        Rules.ink(s, 1);
        assertArrayEquals(new int[0], build(on, s, 0, 0));
        assertArrayEquals(new int[0], build(on, s, 0, 1));
    }

    @Test void dependenciesAreCapturedBeforeThreatSimulationInksTheBridge() throws Exception {
        var s = bridge(7, 0);
        var b = bot(s, 0, true, "");
        ((int[]) field(b, "threats"))[0] = 1;
        propose(b, s, 0, 0);
        assertEquals(1, ((int[]) field(b, "counts"))[0]);
        assertFalse(s.regionInked[1]);
        Rules.ink(s, 1);
        assertArrayEquals(new int[0], build(b, s, 0, 0));
    }

    @Test void higherRankedInsertionAndDuplicatePromotionKeepOtherRouteDependencies() throws Exception {
        var s = twoCorridors(0, false);
        var b = bot(s, 0, true, "");
        propose(b, s, 0, 0);
        propose(b, s, 0, 1); // higher-ranked second corridor shifts both first-corridor directions
        assertArrayEquals(new int[]{26, 27, 28}, build(b, s, 0, 0));
        int[][][] routes = (int[][][]) field(b, "routes");
        int[][] lengths = (int[][]) field(b, "lengths");
        int oldFirst = -1;
        for (int r = 0; r < ((int[]) field(b, "counts"))[0]; r++) if (routes[0][r][0] == 0) oldFirst = r;
        assertTrue(oldFirst > 0);
        // Raising the first project's heuristic rank exercises duplicate replacement and shifts.
        ((int[]) field(b, "baseDiff"))[0] = -100;
        propose(b, s, 0, 0);
        assertArrayEquals(new int[]{3, 4, 5}, build(b, s, 0, 0));
        assertTrue(lengths[0][0] > 0);
        Rules.ink(s, 1);
        assertArrayEquals(new int[]{26, 27, 28}, build(b, s, 0, 0), "skip broken first project, retain intact shifted second");
        Rules.ink(s, 21);
        assertArrayEquals(new int[0], build(b, s, 0, 0));
    }

    @Test void quickRouteReplacementRestoresItsOwnDependencyAfterLaterProposals() throws Exception {
        var s = twoCorridors(0, true);
        var b = bot(s, 0, true, "USE_ROLLOUT_QUICK_ROUTE=1,ROLLOUT_WIDTH=1,ROLLOUT_REPLIES=1");
        setInt(b, "quickPlayer", 0);
        propose(b, s, 0, 0); // cheaper short route is retained as the quick candidate
        propose(b, s, 0, 1); // ordinary ranking prefers this longer, higher-income route
        assertArrayEquals(new int[]{23, 24, 25}, build(b, s, 0, 0));
        call(b, "retainQuickRoute", new Class<?>[]{GameState.class, int.class}, s, 0);
        assertArrayEquals(new int[]{2, 3}, build(b, s, 0, 0));
        var unrelatedInk = s.copy();
        Rules.ink(unrelatedInk, 21);
        assertArrayEquals(new int[]{2, 3}, build(b, unrelatedInk, 0, 0), "later proposal must not leak its dependencies");
        Rules.ink(s, 1);
        assertArrayEquals(new int[0], build(b, s, 0, 0));
    }
}
