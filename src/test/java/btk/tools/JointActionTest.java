package btk.tools;

import btk.bot.BotCatalog;
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
    static final String SPEC = "current?USE_REPLY_RERANK=1,BUILD_BEAM_WIDTH=2,OPP_REPLY_WIDTH=1,USE_JOINT_ACTION=1,NOISE=0";

    @Test void capturedCrossProductSelectsAndEmitsTheActualBestPair() throws Exception {
        try (var reader = new InputStreamReader(getClass().getResourceAsStream("/btk/bot/joint-reply-study.json"), StandardCharsets.UTF_8)) {
            int sample = 0;
            for (var item : JsonParser.parseReader(reader).getAsJsonArray()) {
                var f = item.getAsJsonObject(); int me = f.get("me").getAsInt();
                GameState state = MapGenerator.generate(f.get("seed").getAsLong());
                Pathing pathing = new Pathing(state.size); Rules.updateConnections(state, pathing);
                for (var h : f.getAsJsonArray("history")) {
                    Rules.resolve(state, Action.parseLine(h.getAsJsonArray().get(0).getAsString()),
                            Action.parseLine(h.getAsJsonArray().get(1).getAsString()), pathing);
                }
                assertEquals(f.get("stateSha256").getAsString(), ReplyStudy.stateHash(state));
                var prior = ReplyProbe.inspect(state, me, List.of(Action.waitAction()), false);
                var control = BotFactory.create(SPEC.replace("USE_JOINT_ACTION=1", "USE_JOINT_ACTION=0"));
                control.init(state.copy(), me);
                assertEquals(prior.ample().actions(), Action.format(control.play(state.copy(), me, 60_000)));
                var bot = (EvalBot) BotFactory.create(SPEC); bot.init(state.copy(), me);
                var output = bot.play(state.copy(), me, 60_000);
                assertTrue((boolean) CgDisruptStudy.field(bot, "jointSearchComplete"));
                assertEquals(0, CgDisruptStudy.field(bot, "jointBuildSelected"));
                int target = new int[]{45, -1, 29}[sample++];
                assertEquals(target, CgDisruptStudy.field(bot, "jointSelectedTarget"));
                var accepted = ReplyProbe.accepted(state, me, output);
                assertArrayEquals(prior.choices().get(0).prefix(), accepted.cells());
                assertEquals(target, accepted.disrupt());
                assertTrue((int) CgDisruptStudy.field(bot, "jointPairsEvaluated") <= 8);

                // Every crossed target/prefix agrees with the prior tools-only extension of the formula.
                var reference = ReplyProbe.prepare(state, me);
                for (int ownTarget : new int[]{-1, 45, 9, 29}) {
                    if (ownTarget >= 0 && (ownTarget >= state.regionCount || state.regionInked[ownTarget] || state.regionHasTown(ownTarget))) continue;
                    var expected = ReplyProbe.scenario(reference.bot(), state, me, 2, prior.predicted(), new int[]{ownTarget, ownTarget});
                    for (int b = 0; b < 2; b++) {
                        double actual = (double) CgDisruptStudy.call(bot, "replyAwareValue",
                                new Class<?>[]{GameState.class, int.class, int.class, int.class, int.class, int.class},
                                state, me, b, 0, prior.predicted().disrupt(), ownTarget);
                        assertEquals(ReplyProbe.number(expected[b]), actual, 1e-8);
                    }
                }
            }
            assertEquals(3, sample);
        }
    }

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

    @Test void inactiveFeatureAllocatesNoJointBuffersAndPreservesOrdinaryPolicy() throws Exception {
        assertFalse(new Params().USE_JOINT_ACTION);
        for (int me = 0; me < 2; me++) {
            GameState state = MapGenerator.generate(20000); Rules.updateConnections(state, new Pathing(state.size));
            var current = (EvalBot) BotFactory.create("current?NOISE=0"); current.init(state.copy(), me);
            var old = BotCatalog.createExperiment("exp-income-guard?GUARD_BUILD_INCOME=0,NOISE=0"); old.init(state.copy(), me);
            assertNull(CgDisruptStudy.field(current, "jointState"));
            assertNull(CgDisruptStudy.field(current, "jointTargets"));
            for (int t = 0; t < 8; t++) {
                var actions = current.play(state.copy(), me, 60_000);
                assertEquals(Action.format(old.play(state.copy(), me, 60_000)), Action.format(actions));
                Rules.resolve(state, me == 0 ? actions : List.of(Action.waitAction()),
                        me == 1 ? actions : List.of(Action.waitAction()), new Pathing(state.size));
            }
        }
    }

    @Test void invalidCombinationsAreRejectedAndNewSimulationMatchesCore() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new EvalBot(new Params("USE_JOINT_ACTION=1")));
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
