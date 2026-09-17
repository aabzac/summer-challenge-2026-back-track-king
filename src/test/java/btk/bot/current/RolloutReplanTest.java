package btk.bot.current;

import btk.bot.Timer;
import btk.core.*;
import btk.tools.CgReplay;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static btk.bot.current.RolloutDiverseRepliesTest.*;
import static org.junit.jupiter.api.Assertions.*;

class RolloutReplanTest {
    @Test void remainingConstructionCanReverseProjectPriorityWithoutNewPaths() throws Exception {
        for (int me = 0; me < 2; me++) {
            int[] regions = new int[24], terrain = new int[24];
            for (int c = 0; c < 24; c++) regions[c] = c;
            for (int c = 17; c <= 20; c++) terrain[c] = GameState.RIVER;
            var s = new GameState(8,3,regions,terrain,new int[]{0,7,0,5},new int[]{0,0,2,2},
                    new int[][]{{1},{},{3},{}});
            var b = new RolloutBot(RolloutTestParams.base("USE_ROLLOUT_REPLAN=1,NOISE=0,USE_DISRUPT=0")); b.init(s,me);
            pool(b,me,new int[]{0,1,2,3,4,5,6,7},new int[]{16,17,18,19,20,21});
            ((Timer)field(b,"timer")).start(60_000);
            assertEquals(0,call(b,"reconsider",new Class<?>[]{GameState.class,int.class,int.class},s,me,0));
            for (int c = 17; c < 20; c++) s.owner[c] = me;
            Rules.updateConnections(s,new Pathing(s.size)); int[] owners = s.owner.clone();
            assertEquals(1,call(b,"reconsider",new Class<?>[]{GameState.class,int.class,int.class},s,me,0));
            assertEquals(0,b.routeQueries); assertArrayEquals(owners,s.owner); assertEquals(1,b.replanChanges);
            Rules.ink(s,20); Rules.updateConnections(s,new Pathing(s.size));
            assertEquals(0,call(b,"reconsider",new Class<?>[]{GameState.class,int.class,int.class},s,me,1));
            ((Timer)field(b,"timer")).start(-1);
            assertEquals(-2,call(b,"reconsider",new Class<?>[]{GameState.class,int.class,int.class},s,me,1));
        }
    }
    @Test void capturedDecisionsReplanWithinBoundedRolloutsAndRemainLegal() throws Exception {
        try (var reader = new InputStreamReader(getClass().getResourceAsStream(
                "/btk/bot/rollout-boss-positions.json"),StandardCharsets.UTF_8)) {
            var fixture = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("positions").get(0).getAsJsonObject();
            var replay = new CgReplay(); replay.globalGraphics = fixture.get("globalGraphics").getAsString();
            for (int me = 0; me < 2; me++) {
                var s = replay.toState(); Rules.updateConnections(s,new Pathing(s.size));
                var off = new RolloutBot(RolloutTestParams.base("NOISE=0")); off.init(s,me); off.play(s,me,60_000);
                var on = new RolloutBot(RolloutTestParams.base("NOISE=0,USE_ROLLOUT_REPLAN=1")); on.init(s,me);
                int[] owners = s.owner.clone(); var actions = on.play(s,me,60_000);
                assertEquals(off.routeQueries,on.routeQueries); assertEquals(0,off.replanCalls);
                assertTrue(on.replanCalls>0); assertTrue(on.replanCalls<=28);
                assertTrue(on.simulatedTurns<=224); assertArrayEquals(owners,s.owner); assertEquals(0,s.turn);
                var tr = new Rules.TurnResult(); Rules.expand(s,me,actions,new Pathing(s.size),tr.moves[me],tr);
                assertNull(tr.moves[me].error); assertTrue(tr.moves[me].skipped.isEmpty());
            }
        }
    }
}
