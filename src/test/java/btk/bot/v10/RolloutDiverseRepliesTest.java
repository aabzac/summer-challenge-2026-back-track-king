package btk.bot.v10;

import btk.bot.current.RolloutTestParams;
import btk.core.*;
import btk.tools.CgReplay;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Moved from btk.bot.current by prune-v1 (2026-10-25): the mechanism under test now lives only in frozen v10. */
class RolloutDiverseRepliesTest {
    static Object field(RolloutBotV10 bot, String name) throws Exception {
        var f = RolloutBotV10.class.getDeclaredField(name); f.setAccessible(true); return f.get(bot);
    }
    static Object call(RolloutBotV10 bot, String name, Class<?>[] types, Object... args) throws Exception {
        var m = RolloutBotV10.class.getDeclaredMethod(name, types); m.setAccessible(true); return m.invoke(bot, args);
    }
    static void pool(RolloutBotV10 bot, int player, int[]... paths) throws Exception {
        int[][][] routes = (int[][][]) field(bot, "routes");
        int[][] lengths = (int[][]) field(bot, "lengths");
        ((int[]) field(bot, "counts"))[player] = paths.length;
        for (int i = 0; i < paths.length; i++) {
            System.arraycopy(paths[i], 0, routes[player][i], 0, paths[i].length);
            lengths[player][i] = paths[i].length;
        }
    }
    @Test void repliesPreferAnotherTownPairThenDetourThenReverseWithoutDuplicates() throws Exception {
        var s = new GameState(8,1,new int[8],new int[8],new int[]{0,4,7},new int[3],new int[][]{{1},{2},{}});
        for (int enemy = 0; enemy < 2; enemy++) {
            var p = RolloutTestParams.base("USE_ROLLOUT_DIVERSE_REPLIES=1,ROLLOUT_REPLIES=4");
            var b = new RolloutBotV10(p); b.init(s, 1-enemy);
            pool(b,enemy,new int[]{0,1,2,4},new int[]{4,2,1,0},new int[]{4,5,6,7},new int[]{0,3,4});
            call(b,"selectReplyRoutes",new Class<?>[]{int.class,int.class},enemy,4);
            assertArrayEquals(new int[]{0,2,3,1},(int[])field(b,"replyRoutes"));
            pool(b,enemy,new int[]{0,1,2,4},new int[]{4,2,1,0});
            call(b,"selectReplyRoutes",new Class<?>[]{int.class,int.class},enemy,2);
            assertArrayEquals(new int[]{0,1},Arrays.copyOf((int[])field(b,"replyRoutes"),2));
            pool(b,enemy);
            call(b,"selectReplyRoutes",new Class<?>[]{int.class,int.class},enemy,1);
            assertEquals(0,((int[])field(b,"replyRoutes"))[0]);
        }
    }
    @Test void capturedOpeningKeepsWorkBoundsInputAndLegalActionsOnBothSides() throws Exception {
        try (var reader = new InputStreamReader(getClass().getResourceAsStream(
                "/btk/bot/rollout-boss-positions.json"),StandardCharsets.UTF_8)) {
            var fixture = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("positions").get(0).getAsJsonObject();
            var replay = new CgReplay(); replay.globalGraphics = fixture.get("globalGraphics").getAsString();
            for (int me = 0; me < 2; me++) {
                var s = replay.toState(); Rules.updateConnections(s,new Pathing(s.size));
                var off = new RolloutBotV10(RolloutTestParams.base("NOISE=0")); off.init(s,me); off.play(s,me,60_000);
                var on = new RolloutBotV10(RolloutTestParams.base("NOISE=0,USE_ROLLOUT_DIVERSE_REPLIES=1")); on.init(s,me);
                int[] owners = s.owner.clone(); var actions = on.play(s,me,60_000);
                assertEquals(off.routeQueries,on.routeQueries); assertTrue(on.completedComparisons<=14);
                assertTrue(on.simulatedTurns<=224); assertArrayEquals(owners,s.owner); assertEquals(0,s.turn);
                var tr = new Rules.TurnResult(); Rules.expand(s,me,actions,new Pathing(s.size),tr.moves[me],tr);
                assertNull(tr.moves[me].error); assertTrue(tr.moves[me].skipped.isEmpty());
            }
        }
    }
}
