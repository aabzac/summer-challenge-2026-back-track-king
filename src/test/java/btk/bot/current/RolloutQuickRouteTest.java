package btk.bot.current;

import btk.bot.Params;
import btk.core.*;
import btk.tools.CgReplay;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RolloutQuickRouteTest {
    @Test void safeFilterKeepsLongerRouteWhenPredictedInkDestroysCheapestIncome() throws Exception {
        for (int me = 0; me < 2; me++) {
            int[] regions = new int[24];
            for (int i = 0; i < regions.length; i++) regions[i] = i;
            var s = new GameState(8,3,regions,new int[24],new int[]{0,5,0,7},new int[]{0,0,2,2},
                    new int[][]{{1},{},{3},{}});
            s.owner[2] = me;
            Rules.updateConnections(s, new Pathing(s.size));
            var old = bot(s, me, true); old.play(s, me, 60_000);
            var p = new Params(); p.NOISE = 0; p.USE_ROLLOUT_QUICK_ROUTE = true; p.USE_ROLLOUT_QUICK_SAFE = true;
            var safe = new RolloutBot(p); safe.init(s, me); safe.play(s, me, 60_000);
            assertEquals(2, ((int[]) field(safe, "threats"))[me]);
            assertEquals(3, field(old, "quickCost")); assertEquals(6, field(safe, "quickCost"));
            assertEquals(old.routeQueries, safe.routeQueries);
            assertEquals(me, s.owner[2]); assertFalse(s.inked[2]); assertEquals(0, s.turn);
            p.USE_DISRUPT = false;
            var noThreat = new RolloutBot(p); noThreat.init(s, me); noThreat.play(s, me, 60_000);
            assertEquals(3, field(noThreat, "quickCost"));
        }
    }
    @Test void valueChoosesMoreIncomeWhenTwoRoutesBothTakeTwoTurns() throws Exception {
        int[] regions = new int[24];
        for (int i = 0; i < regions.length; i++) regions[i] = i;
        var s = new GameState(8,3,regions,new int[24],new int[]{0,5,0,7},new int[]{0,0,2,2},
                new int[][]{{1},{},{3},{}});
        Rules.updateConnections(s, new Pathing(s.size));
        for (int me = 0; me < 2; me++) {
            var old = bot(s, me, true); old.play(s, me, 60_000);
            var p = new Params(); p.NOISE = 0; p.USE_ROLLOUT_QUICK_ROUTE = true; p.USE_ROLLOUT_QUICK_VALUE = true;
            var value = new RolloutBot(p); value.init(s, me); value.play(s, me, 60_000);
            assertEquals(4, field(old, "quickCost")); assertEquals(4, field(old, "quickGain"));
            assertEquals(6, field(value, "quickCost")); assertEquals(6, field(value, "quickGain"));
            assertEquals(old.routeQueries, value.routeQueries);
            assertEquals(0, s.turn); assertArrayEquals(new int[2], s.score);
        }
    }

    @Test void actualConstructionTurnsAccountForUnspentPaintOnRivers() throws Exception {
        var s = new GameState(5,1,new int[]{0,1,2,3,4},new int[]{0,1,1,1,0},
                new int[]{0,4},new int[]{0,0},new int[][]{{1},{}});
        Rules.updateConnections(s, new Pathing(s.size));
        for (int me = 0; me < 2; me++) {
            var b = bot(s, me, true);
            int[] path = (int[]) field(b, "path");
            for (int i = 0; i < 5; i++) path[i] = i;
            var method = RolloutBot.class.getDeclaredMethod("quickBuildTurns", GameState.class, int.class, int.class);
            method.setAccessible(true);
            assertEquals(3, method.invoke(b, s, me, 5)); // Six paint, but only one river fits each turn.
            assertEquals(GameState.NO_TRACK, s.owner[1]);
            assertEquals(GameState.NO_TRACK, s.owner[2]);
            assertEquals(GameState.NO_TRACK, s.owner[3]);
        }
    }
    @Test void earlyComparisonVisitsEveryRootOnceAndPreservesFirstFallback() throws Exception {
        for (boolean enabled : new boolean[]{false, true}) for (int slot = -1; slot < 6; slot++) {
            var p = new Params(); p.USE_ROLLOUT_QUICK_EARLY = enabled;
            var b = new RolloutBot(p);
            var f = RolloutBot.class.getDeclaredField("quickSlot"); f.setAccessible(true); f.set(b, slot);
            var m = RolloutBot.class.getDeclaredMethod("searchRoot", int.class); m.setAccessible(true);
            int[] order = new int[7];
            for (int i = 0; i < 7; i++) order[i] = (int) m.invoke(b, i);
            assertEquals(0, order[0]);
            if (enabled && slot > 0) assertEquals(slot, order[1]);
            else assertArrayEquals(new int[]{0,1,2,3,4,5,6}, order);
            java.util.Arrays.sort(order); assertArrayEquals(new int[]{0,1,2,3,4,5,6}, order);
        }
    }
    private static Object field(RolloutBot bot, String name) throws Exception {
        var f = RolloutBot.class.getDeclaredField(name); f.setAccessible(true); return f.get(bot);
    }
    private static int cheapestRetained(RolloutBot bot, GameState s, int me) throws Exception {
        int[][][] routes = (int[][][]) field(bot, "routes");
        int[][] lengths = (int[][]) field(bot, "lengths");
        int count = ((int[]) field(bot, "counts"))[me], min = Integer.MAX_VALUE;
        for (int r = 0; r < count; r++) {
            int cost = 0;
            for (int i = 0; i < lengths[me][r]; i++)
                if (s.canBuild(routes[me][r][i])) cost += s.placeCost(routes[me][r][i]);
            min = Math.min(min, cost);
        }
        return min;
    }
    private static RolloutBot bot(GameState s, int me, boolean on) {
        var p = new Params(); p.NOISE = 0; p.USE_ROLLOUT_QUICK_ROUTE = on;
        var b = new RolloutBot(p); b.init(s, me); return b;
    }

    @Test void capturedOpeningKeepsFourPaintIncomeCandidateWithinExistingWorkBounds() throws Exception {
        try (var reader = new InputStreamReader(getClass().getResourceAsStream(
                "/btk/bot/rollout-boss-positions.json"), StandardCharsets.UTF_8)) {
            var fixture = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("positions").get(0).getAsJsonObject();
            assertEquals(904148181, fixture.get("sourceGameId").getAsInt());
            var replay = new CgReplay(); replay.globalGraphics = fixture.get("globalGraphics").getAsString();
            for (int me = 0; me < 2; me++) {
                var s = replay.toState(); Rules.updateConnections(s, new Pathing(s.size));
                var off = bot(s, me, false); off.play(s, me, 60_000);
                var on = bot(s, me, true); var actions = on.play(s, me, 60_000);
                assertEquals(4, (int) field(on, "quickCost"));
                assertEquals(4, cheapestRetained(on, s, me));
                assertEquals(off.routeQueries, on.routeQueries);
                assertTrue(((int[]) field(on, "counts"))[me] <= 6);
                assertTrue(on.completedComparisons <= 14); assertTrue(on.simulatedTurns <= 224);
                if (me == 0) assertTrue(cheapestRetained(off, s, me) >= 18);
                assertEquals(0, s.turn); assertArrayEquals(new int[2], s.score);
                var check = Rules.resolve(s.copy(), me == 0 ? actions : java.util.List.of(Action.waitAction()),
                        me == 1 ? actions : java.util.List.of(Action.waitAction()), new Pathing(s.size));
                assertNull(check.moves[me].error);
            }
        }
    }

    @Test void connectedEnemyRevenueAndUnaffordableOrInkedPathsAreNotQuickIncome() throws Exception {
        for (int me = 0; me < 2; me++) for (int kind = 0; kind < 3; kind++) {
            var s = new GameState(5, 1, new int[]{0,1,2,3,4}, new int[5],
                    new int[]{0,4}, new int[]{0,0}, new int[][]{{1},{}});
            if (kind == 0) s.owner[1] = s.owner[2] = 1 - me; // 1 own vs 2 enemy income
            if (kind == 1) s.terrain[1] = s.terrain[2] = s.terrain[3] = GameState.MOUNTAIN;
            if (kind == 2) Rules.ink(s, 2);
            Rules.updateConnections(s, new Pathing(s.size));
            var b = bot(s, me, true); b.play(s, me, 60_000);
            assertEquals(0, (int) field(b, "quickLength"));
        }
    }

    @Test void eligibilityResetsWhenIncomeStartsAndExpiredTurnKeepsLegalFallback() throws Exception {
        var s = new GameState(5, 1, new int[]{0,1,2,3,4}, new int[5],
                new int[]{0,4}, new int[]{0,0}, new int[][]{{1},{}});
        Rules.updateConnections(s, new Pathing(s.size));
        var b = bot(s, 0, true); b.play(s, 0, 60_000);
        assertEquals(3, (int) field(b, "quickCost"));
        s.owner[1] = s.owner[2] = s.owner[3] = 0;
        Rules.updateConnections(s, new Pathing(s.size)); b.play(s, 0, 60_000);
        assertEquals(-1, (int) field(b, "quickPlayer")); assertEquals(0, (int) field(b, "quickLength"));
        assertEquals("WAIT", Action.format(b.play(s, 0, 0)));
        assertEquals(0, (int) field(b, "quickLength"));
    }
}
