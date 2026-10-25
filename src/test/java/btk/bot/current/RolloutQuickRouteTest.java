package btk.bot.current;

import btk.core.*;
import btk.tools.CgReplay;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The selected quick-route slot; its rejected variants (early/value/safe, build-turn packing) moved to btk.bot.v10 by prune-v1. */
class RolloutQuickRouteTest {
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
        var p = RolloutTestParams.base(); p.NOISE = 0; p.USE_ROLLOUT_QUICK_ROUTE = on;
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
