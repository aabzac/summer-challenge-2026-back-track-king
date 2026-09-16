package btk.bot.current;

import btk.bot.Params;
import btk.bot.Timer;
import btk.core.*;
import btk.tools.CgReplay;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static btk.bot.current.RolloutDiverseRepliesTest.*;
import static org.junit.jupiter.api.Assertions.*;

class RolloutSecondTargetTest {
    @Test void secondPositiveTargetIsDistinctAndCannotLeakThroughTimeoutOrClosure() throws Exception {
        for (int me = 0; me < 2; me++) {
            int[] regions = new int[21]; for (int i = 0; i < 21; i++) regions[i] = i;
            var s = new GameState(7,3,regions,new int[21],new int[]{0,6,0,6},new int[]{0,0,2,2},
                    new int[][]{{1},{},{3},{}});
            for (int c = 1; c < 6; c++) { s.owner[c] = 1-me; s.owner[c+14] = 1-me; }
            Rules.updateConnections(s,new Pathing(s.size));
            var b = new RolloutBot(new Params("USE_ROLLOUT_SECOND_TARGET=1,NOISE=0")); b.init(s,me);
            ((Timer)field(b,"timer")).start(60_000);
            assertEquals(1,call(b,"chooseTarget",new Class<?>[]{GameState.class,int.class},s,me));
            assertEquals(2,field(b,"secondTarget"));
            ((Timer)field(b,"timer")).start(-1);
            assertEquals(-1,call(b,"chooseTarget",new Class<?>[]{GameState.class,int.class},s,me));
            assertEquals(-1,field(b,"secondTarget"));
            var closed = new GameState(6,1,new int[]{0,1,1,1,1,2},new int[6],
                    new int[]{0,5},new int[2],new int[][]{{1},{}});
            for (int c = 1; c < 5; c++) closed.owner[c] = 1-me;
            for (int i = 0; i < 3; i++) Rules.disrupt(closed,1);
            closed.score[me]=20; Rules.updateConnections(closed,new Pathing(closed.size));
            b.init(closed,me); ((Timer)field(b,"timer")).start(60_000);
            assertEquals(1,call(b,"chooseTarget",new Class<?>[]{GameState.class,int.class},closed,me));
            assertEquals(-1,field(b,"secondTarget"));
        }
    }
    @Test void capturedMiddleGameAddsAtMostOneFullComparisonAfterNormalSearch() throws Exception {
        try (var reader = new InputStreamReader(getClass().getResourceAsStream(
                "/btk/bot/rollout-boss-positions.json"),StandardCharsets.UTF_8)) {
            var fixture = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("positions").get(1).getAsJsonObject();
            var replay = new CgReplay(); replay.globalGraphics = fixture.get("globalGraphics").getAsString();
            var s = replay.toState(); var pathing = new Pathing(s.size); Rules.updateConnections(s,pathing);
            for (var turn : fixture.getAsJsonArray("history")) {
                var a = turn.getAsJsonArray();
                Rules.resolve(s,Action.parseLine(a.get(0).getAsString()),Action.parseLine(a.get(1).getAsString()),pathing);
            }
            int extras = 0;
            for (int me = 0; me < 2; me++) {
                var off = new RolloutBot(new Params("NOISE=0")); off.init(s,me); var before = off.play(s,me,60_000);
                var on = new RolloutBot(new Params("NOISE=0,USE_ROLLOUT_SECOND_TARGET=1")); on.init(s,me);
                int[] owners = s.owner.clone(); var actions = on.play(s,me,60_000);
                assertEquals(off.routeQueries,on.routeQueries); assertEquals(off.selectedRoute,on.selectedRoute);
                assertEquals(off.completedComparisons+on.extraTargetComparisons,on.completedComparisons);
                assertTrue(on.extraTargetComparisons<=1); assertTrue(on.simulatedTurns<=240);
                assertEquals(before.stream().filter(a->a.type==Action.Type.PLACE).map(Action::toString).toList(),
                        actions.stream().filter(a->a.type==Action.Type.PLACE).map(Action::toString).toList());
                assertArrayEquals(owners,s.owner); extras += on.extraTargetComparisons;
                var tr = new Rules.TurnResult(); Rules.expand(s,me,actions,pathing,tr.moves[me],tr);
                assertNull(tr.moves[me].error); assertTrue(tr.moves[me].skipped.isEmpty());
            }
            assertTrue(extras>0,"Recorded middle game must exercise the extra comparison");
        }
    }
}
