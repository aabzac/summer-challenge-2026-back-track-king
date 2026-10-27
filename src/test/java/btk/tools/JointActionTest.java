package btk.tools;

import btk.bot.BotFactory;
import btk.bot.Params;
import btk.bot.Timer;
import btk.bot.current.EvalBot;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.referee.MapGenerator;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class JointActionTest {
    static final String SPEC = "current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",USE_ROLLOUT_POLICY=0,USE_REPLY_RERANK=1,BUILD_BEAM_WIDTH=2,OPP_REPLY_WIDTH=1,USE_JOINT_ACTION=1,NOISE=0";

    @Test void expiredClockRetainsFallbackAndRestoresTargetScanState() throws Exception {
        GameState state = MapGenerator.generate(1000); Rules.updateConnections(state, new Pathing(state.size));
        var bot = (EvalBot) BotFactory.create(SPEC.replace("NOISE=0", "NOISE=0.1")); bot.init(state.copy(), 0);
        bot.play(state.copy(), 0, 60_000);
        var timer = (Timer) CgDisruptStudy.field(bot, "timer"); timer.start(0);
        double incomplete = (double) CgDisruptStudy.call(bot, "jointPairValue",
                new Class<?>[]{GameState.class, int.class, int.class, int.class, int.class}, state, 0, 0, -1, 1);
        assertTrue(Double.isNaN(incomplete));
        CgDisruptStudy.call(bot, "jointTarget", new Class<?>[]{GameState.class, int.class, int.class}, state, 0, 0);
        assertFalse((boolean) CgDisruptStudy.field(bot, "disruptSearchComplete"));
        assertFalse((boolean) CgDisruptStudy.field(bot, "jointTargetScan"));
        assertEquals(0.1, ((Params) CgDisruptStudy.field(bot, "P")).NOISE);
        for (boolean marked : (boolean[]) CgDisruptStudy.field(bot, "myRoute")) assertFalse(marked);
        assertEquals("WAIT", Action.format(bot.play(state.copy(), 0, 0)));
        assertEquals(-2, CgDisruptStudy.field(bot, "jointSelectedTarget"));
    }

    @Test void invalidCombinationsAreRejectedAndNewSimulationMatchesCore() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new EvalBot(new Params(btk.bot.LiveTargetExperimentParams.V10_OFF + ",USE_JOINT_ACTION=1")));
        GameState state = new GameState(5, 1, new int[]{0,1,2,3,4}, new int[5],
                new int[]{0,4}, new int[]{0,0}, new int[][]{{1},{}});
        state.instability[2] = 2; state.syncRegionsFromCells(); Rules.updateConnections(state, new Pathing(state.size));
        for (int me = 0; me < 2; me++) {
            var bot = (EvalBot) BotFactory.create(SPEC); bot.init(state, me);
            int[] own = {1,2}; var reply = new ReplyProbe.Reply(new int[]{2,3}, 2);
            GameState actual = state.copy(), expected = ReplyProbe.resolve(state, me, own, 2, reply);
            CgDisruptStudy.call(bot, "simulatePredictedTurn", new Class<?>[]{GameState.class,int.class,int[].class,
                    int.class,int[].class,int.class,int.class,int.class,GameState.class},
                    state, me, own, 2, reply.cells(), 2, 2, 2, actual);
            ReplyProbe.sameState(expected, actual);
            assertTrue(actual.inked[2]); assertEquals(4, actual.regionInstability[2]);
        }
    }
}
