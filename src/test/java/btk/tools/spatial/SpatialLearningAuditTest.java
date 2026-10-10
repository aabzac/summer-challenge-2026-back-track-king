package btk.tools.spatial;

import btk.bot.Bot;
import btk.bot.BotFactory;
import btk.bot.neural.SpatialNet;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Protocol;
import btk.core.Rules;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Tiny synthetic games only; never the production schedule or a policy benchmark. */
class SpatialLearningAuditTest {
    @TempDir Path temp;

    @Test void fixedScheduleCrossesEveryMapOpponentSeatAndFrozenLearnerExactlyOnce() {
        var schedule = SpatialLearningAudit.schedule(); assertEquals(64, schedule.size());
        Set<String> ids = new HashSet<>();
        for (var spec : schedule) {
            assertTrue(ids.add(spec.id())); assertEquals(22_000_000L + spec.mapIndex(), spec.seed());
            assertTrue(spec.mapIndex() >= 0 && spec.mapIndex() < 4);
        }
        for (int map = 0; map < 4; map++) for (String opponent : SpatialLearningAudit.OPPONENTS)
            for (int seat = 0; seat < 2; seat++) for (String learner : SpatialLearningAudit.LEARNERS)
                assertTrue(schedule.contains(new SpatialLearningAudit.GameSpec(map, 22_000_000L + map, learner, opponent, seat)));
    }

    @Test void exclusionsRejectMalformedDuplicateAndPreviouslyExposedMaps() {
        String hash = "a".repeat(64), other = "b".repeat(64);
        Set<String> excluded = SpatialLearningAudit.exclusions(JsonParser.parseString("{\"mapHashes\":[\"" + hash + "\"]}").getAsJsonObject());
        assertEquals(Set.of(hash), excluded);
        assertThrows(IllegalStateException.class, () -> SpatialLearningAudit.requireFresh(hash, excluded, new HashSet<>()));
        Set<String> used = new HashSet<>(); SpatialLearningAudit.requireFresh(other, excluded, used);
        assertThrows(IllegalStateException.class, () -> SpatialLearningAudit.requireFresh(other, excluded, used));
        for (String bad : List.of("{}", "{\"mapHashes\":[]}", "{\"mapHashes\":[1]}", "{\"mapHashes\":[\"short\"]}",
                "{\"mapHashes\":[\"" + hash + "\",\"" + hash + "\"]}"))
            assertThrows(IllegalStateException.class, () -> SpatialLearningAudit.exclusions(JsonParser.parseString(bad).getAsJsonObject()));
    }

    @Test void completeBothSeatTraceRecordsPreturnValuesAndCoreIncomeThenReplaysExactly() throws Exception {
        for (int learnerSeat = 0; learnerSeat < 2; learnerSeat++) {
            GameState state = line(4); state.maxTurns = 2;
            JsonObject game = game(state, learnerSeat);
            StubBot learner = new StubBot(List.of(Action.place(1, 0)), false);
            StubBot opponent = new StubBot(List.of(Action.place(2, 0)), false);
            SpatialLearningAudit.playGame(state, "spatial-ppo-v4", "v01", learnerSeat,
                    learner, opponent, learnerValue(), criticValue(), () -> 0, game);
            finish(game, state);
            assertEquals(2, learner.calls); assertEquals(2, opponent.calls);
            assertEquals(learnerSeat, learner.seat); assertEquals(1 - learnerSeat, opponent.seat);
            assertArrayEquals(new int[]{2,2}, state.score);
            JsonObject first = game.getAsJsonArray("turns").get(0).getAsJsonObject();
            JsonObject second = game.getAsJsonArray("turns").get(1).getAsJsonObject();
            assertEquals(7f, first.get("learnerValueRaw").getAsFloat());
            assertEquals(8f, second.get("learnerValueRaw").getAsFloat());
            assertEquals(-2f, first.get("v5ValueRaw").getAsFloat());
            assertEquals(JsonParser.parseString("[0,0]"), first.get("incomeBefore"));
            assertEquals(JsonParser.parseString("[1,1]"), first.get("incomeAfter"));
            assertEquals(JsonParser.parseString("[1,1]"), first.get("gained"));
            assertEquals(JsonParser.parseString("[1,1]"), first.getAsJsonObject("metricsAfter").get("ownedRails"));
            assertEquals(JsonParser.parseString("[1,1]"), first.getAsJsonObject("metricsAfter").get("ownedDesiredPaths"));
            assertEquals(1, first.getAsJsonObject("metricsAfter").get("activeDesiredPaths").getAsInt());
            assertEquals(2, first.get("learnerUnusedPaint").getAsInt()); assertTrue(first.get("learnerStoppedWithPaint").getAsBoolean());
            assertFalse(first.get("terminalAfter").getAsBoolean()); assertTrue(second.get("terminalAfter").getAsBoolean());
            assertTrue(SpatialLearningAudit.verifyGame(game, learnerValue(), criticValue()).get("passed").getAsBoolean());
            // Each corrupted trace must fail without any bot replay or new games.
            JsonObject wrongScore = game.deepCopy(); wrongScore.getAsJsonArray("turns").get(0).getAsJsonObject().add("scoreAfter", JsonParser.parseString("[9,1]"));
            assertThrows(IllegalStateException.class, () -> SpatialLearningAudit.verifyGame(wrongScore, learnerValue(), criticValue()));
            JsonObject wrongValue = game.deepCopy(); wrongValue.getAsJsonArray("turns").get(0).getAsJsonObject().addProperty("learnerValueRaw", 8f);
            assertThrows(IllegalStateException.class, () -> SpatialLearningAudit.verifyGame(wrongValue, learnerValue(), criticValue()));
            JsonObject wrongPlan = game.deepCopy(); wrongPlan.getAsJsonArray("turns").get(0).getAsJsonObject().getAsJsonArray("accepted").get(0).getAsJsonObject().addProperty("cost", 2);
            assertThrows(IllegalStateException.class, () -> SpatialLearningAudit.verifyGame(wrongPlan, learnerValue(), criticValue()));
        }
    }

    @Test void bothPoliciesReceiveSeparateCopiesOfSamePreturnAndCollisionsKeepAcceptedPlans() throws Exception {
        GameState state = line(3); state.maxTurns = 1; JsonObject game = game(state, 1);
        StubBot learner = new StubBot(List.of(Action.place(1,0)), true), opponent = new StubBot(List.of(Action.place(1,0)), true);
        SpatialLearningAudit.playGame(state, "spatial-ppo-v4", "v01", 1, learner, opponent,
                learnerValue(), criticValue(), () -> 0, game); finish(game, state);
        assertEquals(GameState.NO_TRACK, learner.ownerSeen); assertEquals(GameState.NO_TRACK, opponent.ownerSeen);
        assertEquals(GameState.NEUTRAL, state.owner[1]); assertArrayEquals(new int[]{0,0}, state.score);
        JsonObject turn = game.getAsJsonArray("turns").get(0).getAsJsonObject();
        assertEquals(JsonParser.parseString("[1]"), turn.get("neutralised"));
        assertEquals(turn.get("expanded"), turn.get("accepted"));
        assertTrue(SpatialLearningAudit.verifyGame(game, learnerValue(), criticValue()).get("passed").getAsBoolean());
    }

    @Test void rejectedOrLateCallsPreserveFailureBeforeAnySharedTransition() throws Exception {
        for (boolean late : new boolean[]{false,true}) {
            GameState state = line(3); JsonObject game = game(state, 0);
            StubBot learner = new StubBot(List.of(late ? Action.waitAction() : Action.place(0,0)), false);
            StubBot opponent = new StubBot(List.of(Action.waitAction()), false); AtomicLong time = new AtomicLong();
            assertThrows(IllegalStateException.class, () -> SpatialLearningAudit.playGame(state, "spatial-ppo-v4", "v01", 0,
                    learner, opponent, learnerValue(), criticValue(), () -> time.getAndAdd(late ? 60_000_000_000L : 0), game));
            assertEquals(0, state.turn); assertEquals(0, opponent.calls);
            JsonObject call = game.getAsJsonArray("turns").get(0).getAsJsonObject().getAsJsonArray("calls").get(0).getAsJsonObject();
            assertTrue(call.has("failure")); assertFalse(call.get("complete").getAsBoolean());
            assertEquals(late, call.get("legal").getAsBoolean());
        }
    }

    @Test void historicalWrapperDiagnosticsAreAccessibleAndExistingOutputsCannotBeReused() throws Exception {
        for (String spec : SpatialLearningAudit.LEARNERS) assertNotNull(SpatialLearningAudit.spatialDelegate(BotFactory.create(spec)), spec);
        Path file = temp.resolve("intent.json"); JsonObject value = new JsonObject(); value.addProperty("marker", "original");
        SpatialLearningAudit.writeNew(file, value);
        assertThrows(java.nio.file.FileAlreadyExistsException.class, () -> SpatialLearningAudit.writeNew(file, new JsonObject()));
        assertEquals("original", JsonParser.parseString(Files.readString(file)).getAsJsonObject().get("marker").getAsString());
        assertThrows(java.nio.file.FileAlreadyExistsException.class, () -> SpatialLearningAudit.main(new String[]{temp.toString(), temp.resolve("absent.json").toString()}));
    }

    private static SpatialNet learnerValue() {
        float[] weights = new float[SpatialNet.WEIGHT_COUNT]; int value = weights.length - 15;
        weights[value + 8] = 2; weights[value + 9] = 100; weights[value + 10] = 3; weights[weights.length - 1] = 4;
        return new SpatialNet(weights);
    }
    private static SpatialNet criticValue() {
        float[] weights = new float[SpatialNet.WEIGHT_COUNT]; weights[weights.length - 1] = -2; return new SpatialNet(weights);
    }
    private static GameState line(int width) {
        int[] regions = new int[width]; for (int i = 0; i < width; i++) regions[i] = i;
        return new GameState(width, 1, regions, new int[width], new int[]{0,width - 1}, new int[]{0,0}, new int[][]{{1},{}});
    }
    private static JsonObject game(GameState state, int seat) throws Exception {
        Rules.updateConnections(state, new Pathing(state.size)); JsonObject game = new JsonObject();
        String init = Protocol.initBlock(state, 0); game.addProperty("initBlockP0", init);
        game.addProperty("mapHash", SpatialLearningAudit.sha(init.getBytes(StandardCharsets.UTF_8)));
        game.addProperty("learnerSeat", seat); game.addProperty("learnerSpec", "spatial-ppo-v4");
        game.addProperty("opponentSpec", "v01"); game.addProperty("maxTurns", state.maxTurns); return game;
    }
    private static void finish(JsonObject game, GameState state) throws Exception {
        game.addProperty("status", "complete"); game.addProperty("terminal", Rules.gameOver(state, new Pathing(state.size)));
        game.addProperty("turnCount", state.turn); game.add("score", JsonParser.parseString(java.util.Arrays.toString(state.score)));
        game.addProperty("finalStateSha256", SpatialLearningAudit.stateHash(state));
    }
    private static final class StubBot implements Bot {
        private final List<Action> first; private final boolean mutate; int calls, seat = -1, ownerSeen;
        StubBot(List<Action> first, boolean mutate) { this.first = first; this.mutate = mutate; }
        public String name() { return "synthetic-fixture"; }
        public void init(GameState state, int me) { seat = me; if (mutate) state.score[me] = 999; }
        public List<Action> play(GameState state, int me, long budget) {
            assertEquals(SpatialLearningAudit.BUDGET_MS, budget); assertEquals(seat, me);
            ownerSeen = state.owner[1]; if (mutate) state.score[me] = 999;
            return calls++ == 0 ? first : List.of(Action.waitAction());
        }
    }
}
