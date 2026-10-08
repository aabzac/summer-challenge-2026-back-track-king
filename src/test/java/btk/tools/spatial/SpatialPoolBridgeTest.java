package btk.tools.spatial;

import btk.bot.Bot;
import btk.core.Action;
import btk.core.GameState;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpatialPoolBridgeTest {
    @Test void jsonLinesRetainExplicitNullWeightIdentityForNativeOpponents() {
        Fixture f = fixture(); f.pool.handle(json("{op:'opponentPlans',envIds:[0,4,6]}"));
        JsonObject response = f.pool.handle(json("{op:'step',envIds:[0,4,6],learnerPlans:[{},{},{}]}"));
        JsonObject serialized = json(SpatialPoolBridge.serializeResponse(response));
        assertEquals(64, env(serialized, 0).get("opponentWeightsSha256").getAsString().length());
        for (int index : new int[]{1,2}) {
            assertTrue(env(serialized, index).has("opponentWeightsSha256"));
            assertTrue(env(serialized, index).get("opponentWeightsSha256").isJsonNull());
        }
    }

    @Test void fixedPoolHasBothLearnerSeatsAndRecreatesOnlyTerminalOpponent() {
        Fixture f = fixture();
        assertEquals(List.of("spatial-ppo-v2-init", "spatial-ppo-v2-init", "spatial-ppo-v2", "spatial-ppo-v2",
                "v01", "v01", "v09", "v09"), f.specs);
        for (int id = 0; id < 8; id++) assertEquals(1 - id % 2, f.bots.get(id).seat);
        assertThrows(IllegalStateException.class, () -> f.pool.handle(json("{op:'reset',envIds:[0],seeds:[11234567]}")));
        assertThrows(IllegalArgumentException.class, () -> f.pool.handle(json("{op:'reset',seeds:[1,2,3,4,5,6,7,8]}")));
        assertEquals(8, f.bots.size());
        f.bridge.environments.get(0).state.maxTurns = 1;
        f.pool.handle(json("{op:'opponentPlans',envIds:[0]}"));
        JsonObject terminal = env(f.pool.handle(json("{op:'step',envIds:[0],learnerPlans:[{cells:[1]}]}")), 0);
        assertTrue(terminal.get("done").getAsBoolean());
        JsonObject reset = env(f.pool.handle(json("{op:'reset',envIds:[0],seeds:[11234567]}")), 0);
        assertFalse(reset.get("done").getAsBoolean()); assertEquals(0, reset.get("turn").getAsInt());
        assertEquals("spatial-ppo-v2-init", reset.get("opponentSpec").getAsString());
        assertEquals(0, reset.get("learnerSeat").getAsInt()); assertEquals(1, reset.get("opponentSeat").getAsInt());
        assertEquals(64, reset.get("opponentWeightsSha256").getAsString().length());
        assertTrue(reset.has("opponentParams")); assertEquals(9, f.bots.size());
        assertEquals("spatial-ppo-v2-init", f.specs.get(8)); assertEquals(1, f.bots.get(8).seat);
        assertThrows(IllegalStateException.class, () -> f.pool.handle(json("{op:'step',envIds:[0],learnerPlans:[{}]}")));
        assertEquals(1, f.bots.get(0).calls);
        JsonObject stats = f.pool.handle(json("{op:'stats'}")).getAsJsonArray("opponentStats").get(0).getAsJsonObject();
        assertEquals(1, stats.get("calls").getAsInt()); assertEquals(1, stats.get("completed").getAsInt());
    }

    @Test void initialResetRequiresExactlyEightAndCannotUsePartialIds() {
        SpatialPoolBridge pool = new SpatialPoolBridge();
        assertThrows(IllegalArgumentException.class, () -> pool.handle(json("{op:'reset',seeds:[1]}")));
        assertThrows(IllegalArgumentException.class, () -> pool.handle(json("{op:'reset',envIds:[0],seeds:[1]}")));
    }

    @Test void opponentNeverSeesLearnerPrefixAndIsCalledOncePerTurnEvenWhenReturnedPlanIsChanged() {
        Fixture f = fixture(); f.bots.get(0).mutateCopy = true;
        f.pool.handle(json("{op:'observe',envIds:[0],seats:[0],prefixes:[[1]],stages:[1]}"));
        JsonObject first = f.pool.handle(json("{op:'opponentPlans',envIds:[0]}"));
        assertFalse(diagnostic(first).get("cached").getAsBoolean());
        assertTrue(diagnostic(first).get("complete").getAsBoolean());
        assertEquals(GameState.NO_TRACK, f.bots.get(0).ownerSeen);
        assertEquals(GameState.NO_TRACK, f.bridge.environments.get(0).state.owner[1]);
        assertEquals(0, f.bridge.environments.get(0).state.score[0]);
        first.getAsJsonArray("plans").get(0).getAsJsonObject().getAsJsonArray("cells").set(0, JsonParser.parseString("0"));
        JsonObject second = f.pool.handle(json("{op:'opponentPlans',envIds:[0]}"));
        assertEquals(1, f.bots.get(0).calls); assertTrue(diagnostic(second).get("cached").getAsBoolean());
        assertEquals(1, second.getAsJsonArray("plans").get(0).getAsJsonObject().getAsJsonArray("cells").get(0).getAsInt());
        assertThrows(IllegalArgumentException.class, () -> f.pool.handle(json("{op:'observe',envIds:[0],seats:[1]}")));
    }

    @Test void simultaneousCollisionAndLearnerSeatMappingUseUnchangedCoreTransition() {
        Fixture f = fixture();
        f.pool.handle(json("{op:'opponentPlans',envIds:[0,1]}"));
        JsonObject result = f.pool.handle(json("{op:'step',envIds:[0,1],learnerPlans:[{cells:[1]},{cells:[]}]}"));
        assertEquals(GameState.NEUTRAL, f.bridge.environments.get(0).state.owner[1]);
        assertEquals(0, f.bridge.environments.get(1).state.owner[1]);
        assertEquals(1, f.bridge.environments.get(0).state.turn);
        assertEquals(1, env(result, 1).get("learnerSeat").getAsInt());
        assertEquals(-1, env(result, 1).getAsJsonArray("rewardGap").get(1).getAsInt());
        assertThrows(IllegalStateException.class, () -> f.pool.handle(json("{op:'step',envIds:[0],learnerPlans:[{}]}")));
        f.pool.handle(json("{op:'opponentPlans',envIds:[0]}"));
        assertEquals(2, f.bots.get(0).calls);
    }

    @Test void entireLearnerBatchIsValidatedBeforeEitherStateMutates() {
        Fixture f = fixture();
        assertThrows(IllegalArgumentException.class, () -> f.pool.handle(json("{op:'opponentPlans',envIds:[0,8]}")));
        assertEquals(0, f.bots.get(0).calls);
        f.pool.handle(json("{op:'opponentPlans',envIds:[0,1]}"));
        assertThrows(IllegalArgumentException.class, () -> f.pool.handle(json(
                "{op:'step',envIds:[0,1],learnerPlans:[{cells:[1]},{cells:[1,1]}]}")));
        for (int id = 0; id < 2; id++) {
            assertEquals(0, f.bridge.environments.get(id).state.turn);
            assertEquals(GameState.NO_TRACK, f.bridge.environments.get(id).state.owner[1]);
        }
        assertThrows(IllegalArgumentException.class, () -> f.pool.handle(json("{op:'step',envIds:[0,0],learnerPlans:[{},{}]}")));
        assertThrows(IllegalArgumentException.class, () -> f.pool.handle(json("{op:'step',envIds:[0],learnerPlans:[{}],plans:[{},{}]}")));
        f.pool.handle(json("{op:'step',envIds:[0,1],learnerPlans:[{cells:[1]},{}]}"));
        assertEquals(1, f.bots.get(0).calls); assertEquals(1, f.bots.get(1).calls);
    }

    @Test void invalidOpponentAndBudgetFailureAreRetainedWithoutRetryOrStep() {
        Fixture illegal = fixture(); illegal.bots.get(0).actions = List.of(Action.place(0, 0));
        JsonObject failure = diagnostic(illegal.pool.handle(json("{op:'opponentPlans',envIds:[0]}")));
        assertFalse(failure.get("complete").getAsBoolean()); assertFalse(failure.get("legal").getAsBoolean());
        assertTrue(failure.get("failure").getAsString().contains("illegal opponent"));
        illegal.pool.handle(json("{op:'opponentPlans',envIds:[0]}"));
        assertEquals(1, illegal.bots.get(0).calls);
        assertThrows(IllegalStateException.class, () -> illegal.pool.handle(json("{op:'step',envIds:[0],learnerPlans:[{}]}")));
        Fixture slow = fixture(); slow.clockStep = 60_000_000_000L;
        JsonObject late = diagnostic(slow.pool.handle(json("{op:'opponentPlans',envIds:[0]}")));
        assertFalse(late.get("complete").getAsBoolean()); assertEquals(60000.0, late.get("elapsedMs").getAsDouble());
        slow.pool.handle(json("{op:'opponentPlans',envIds:[0]}")); assertEquals(1, slow.bots.get(0).calls);
        assertEquals(0, slow.bridge.environments.get(0).state.turn);
        JsonObject stats = slow.pool.handle(json("{op:'stats'}")).getAsJsonArray("opponentStats").get(0).getAsJsonObject();
        assertEquals(1, stats.get("calls").getAsInt()); assertEquals(1, stats.get("failed").getAsInt());
        assertEquals(60000.0, stats.get("maxElapsedMs").getAsDouble());
    }

    @Test void nativeAutoplaceExpansionConvertsToPaddedCellsWithoutReimplementingPathing() {
        Fixture f = fixture();
        GameState s = new GameState(4, 2, new int[]{0,1,2,3,4,5,6,7}, new int[8],
                new int[]{0,3}, new int[]{1,1}, new int[][]{{1},{}});
        f.bridge.environments.set(0, new SpatialBridge.Environment(s));
        f.bots.get(0).actions = List.of(Action.autoplace(0, 1, 3, 1), Action.disruptAt(1, 1));
        JsonObject result = f.pool.handle(json("{op:'opponentPlans',envIds:[0]}"));
        assertTrue(diagnostic(result).get("complete").getAsBoolean());
        JsonObject plan = result.getAsJsonArray("plans").get(0).getAsJsonObject();
        assertEquals(json("{cells:[31,32],disrupt:5}"), plan);
        f.pool.handle(json("{op:'step',envIds:[0],learnerPlans:[{}]}"));
        assertEquals(1, s.owner[5]); assertEquals(1, s.owner[6]); assertEquals(1, s.regionInstability[5]);
    }

    private static Fixture fixture() {
        Fixture f = new Fixture();
        for (int id = 0; id < 8; id++) f.bridge.environments.add(new SpatialBridge.Environment(line()));
        f.pool = new SpatialPoolBridge(f.bridge, spec -> {
            RecordingBot bot = new RecordingBot(); f.specs.add(spec); f.bots.add(bot); return bot;
        }, () -> f.clock.getAndAdd(f.clockStep));
        return f;
    }
    private static GameState line() {
        return new GameState(3, 1, new int[]{0,1,2}, new int[3], new int[]{0,2}, new int[]{0,0}, new int[][]{{1},{}});
    }
    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    private static JsonObject diagnostic(JsonObject response) { return response.getAsJsonArray("diagnostics").get(0).getAsJsonObject(); }
    private static JsonObject env(JsonObject response, int index) { return response.getAsJsonArray("envs").get(index).getAsJsonObject(); }
    private static final class Fixture {
        final SpatialBridge bridge = new SpatialBridge();
        final List<String> specs = new ArrayList<>();
        final List<RecordingBot> bots = new ArrayList<>();
        final AtomicLong clock = new AtomicLong();
        long clockStep;
        SpatialPoolBridge pool;
    }
    private static final class RecordingBot implements Bot {
        int calls, seat = -1, ownerSeen;
        boolean mutateCopy;
        List<Action> actions = List.of(Action.place(1, 0));
        public String name() { return "fixture"; }
        public void init(GameState state, int me) { seat = me; state.score[0] = 12345; }
        public List<Action> play(GameState state, int me, long budgetMs) {
            assertEquals(seat, me); assertEquals(60000, budgetMs);
            calls++; ownerSeen = state.owner[1];
            if (mutateCopy) { state.owner[1] = me; state.score[0] = 999; }
            return actions;
        }
    }
}
