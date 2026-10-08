package btk.tools.spatial;

import btk.core.GameState;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpatialBridgeTest {
    @Test void simultaneousCollisionBecomesNeutralAndScoresDeltaOnly() {
        SpatialBridge bridge = bridge(line());
        JsonObject result = bridge.handle(json("{op:'step',envIds:[0],plans:[[{cells:[1]},{cells:[1]}]]}"));
        assertEquals(GameState.NEUTRAL, bridge.environments.get(0).state.owner[1]);
        assertEquals(1, bridge.environments.get(0).state.turn);
        JsonObject env = result.getAsJsonArray("envs").get(0).getAsJsonObject();
        assertEquals(0, env.getAsJsonArray("gained").get(0).getAsInt());
        assertEquals(0, env.getAsJsonArray("rewardGap").get(0).getAsInt());
    }

    @Test void rewardGapUsesOnlyNewCoreScoreAndTerminalBonusIsNotAdded() {
        GameState s = line(); s.score[0] = 10; s.score[1] = 7; s.maxTurns = 1;
        SpatialBridge bridge = bridge(s);
        JsonObject result = bridge.handle(json("{op:'step',envIds:[0],plans:[[{cells:[1]},{cells:[]}]]}"));
        JsonObject env = result.getAsJsonArray("envs").get(0).getAsJsonObject();
        assertEquals(1, env.getAsJsonArray("gained").get(0).getAsInt());
        assertEquals(1, env.getAsJsonArray("rewardGap").get(0).getAsInt());
        assertEquals(-1, env.getAsJsonArray("rewardGap").get(1).getAsInt());
        assertEquals(1, env.getAsJsonArray("terminalSign").get(0).getAsInt());
        assertTrue(env.get("done").getAsBoolean());
        assertThrows(IllegalStateException.class, () -> bridge.handle(json("{op:'step',envIds:[0],plans:[[{},{ }]]}")));
        assertEquals(1, s.turn);
        JsonObject reset = bridge.handle(json("{op:'reset',envIds:[0],seeds:[9812345]}"));
        JsonObject next = reset.getAsJsonArray("envs").get(0).getAsJsonObject();
        assertFalse(next.get("done").getAsBoolean()); assertEquals(0, next.get("turn").getAsInt());
        assertEquals(0, next.getAsJsonArray("score").get(0).getAsInt());
    }

    @Test void bothPlansAreValidatedBeforeAnyMutationIncludingBatchFailure() {
        SpatialBridge bridge = bridge(line()); bridge.environments.add(new SpatialBridge.Environment(line()));
        assertThrows(IllegalArgumentException.class, () -> bridge.handle(json(
                "{op:'step',envIds:[0,1],plans:[[{cells:[1]},{}],[{cells:[1,1]},{}]]}")));
        for (SpatialBridge.Environment env : bridge.environments) {
            assertEquals(0, env.state.turn); assertEquals(GameState.NO_TRACK, env.state.owner[1]);
        }
        assertThrows(IllegalArgumentException.class, () -> bridge.handle(json(
                "{op:'step',envIds:[0],plans:[[{cells:[1]},{cells:[0]}]]}")));
        assertEquals(GameState.NO_TRACK, bridge.environments.get(0).state.owner[1]);
    }

    @Test void prefixPaintComesFromCoreAndBatchSnapshotsAreIndependent() {
        GameState s = line(); s.terrain[1] = GameState.RIVER;
        SpatialBridge bridge = bridge(s);
        JsonObject result = bridge.handle(json(
                "{op:'observe',envIds:[0,0],seats:[0,0],prefixes:[[1],[]],stages:[1,2],stopped:[false,true]}"));
        JsonObject a = result.getAsJsonArray("obs").get(0).getAsJsonObject();
        JsonObject b = result.getAsJsonArray("obs").get(1).getAsJsonObject();
        assertEquals(1f / 3, a.getAsJsonArray("scalars").get(2).getAsFloat());
        assertEquals(1, a.getAsJsonArray("planes").get(13 * 600 + 1).getAsInt());
        assertEquals(0, b.getAsJsonArray("planes").get(13 * 600 + 1).getAsInt());
        assertEquals(GameState.NO_TRACK, s.owner[1]); assertEquals(0, s.turn);
        assertThrows(IllegalArgumentException.class, () -> bridge.handle(json(
                "{op:'observe',envIds:[0],seats:[0],prefixes:[[1]],paints:[2],stages:[1]}")));
    }

    private static SpatialBridge bridge(GameState state) {
        SpatialBridge bridge = new SpatialBridge(); bridge.environments.add(new SpatialBridge.Environment(state)); return bridge;
    }
    private static GameState line() {
        return new GameState(3, 1, new int[]{0,1,2}, new int[3], new int[]{0,2}, new int[]{0,0}, new int[][]{{1},{}});
    }
    private static JsonObject json(String json) { return JsonParser.parseString(json).getAsJsonObject(); }
}
