package btk.tools.spatial;

import btk.bot.neural.SpatialV9Net;
import btk.core.GameState;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpatialV9BridgeTest {
    @Test void gameOperationsRemainIdenticalToTheFrozenBridgeAndResolveSimultaneously() {
        SpatialBridge original = games(), delegate = games();
        SpatialV9Bridge bridge = new SpatialV9Bridge(delegate);
        JsonObject observe = json("{op:'observe',envIds:[0,0],seats:[0,1],prefixes:[[1],[]],stages:[1,0]}");
        assertEquals(original.handle(observe), bridge.handle(observe));
        JsonObject invalid = json("{op:'step',envIds:[0],plans:[[{cells:[1]},{cells:[1,1]}]]}");
        assertThrows(IllegalArgumentException.class, () -> bridge.handle(invalid));
        assertEquals(0, delegate.environments.get(0).state.turn);
        JsonObject step = json("{op:'step',envIds:[0],plans:[[{cells:[1]},{cells:[1]}]]}");
        assertEquals(original.handle(step), bridge.handle(step));
        assertEquals(GameState.NEUTRAL, delegate.environments.get(0).state.owner[1]);
        assertEquals(original.handle(json("{cmd:'observe',me:1}")), bridge.handle(json("{cmd:'observe',me:1}")));
    }

    @Test void modesUseTheSameExplicitPayloadAndDisabledContextMatchesOriginalExactly() {
        SpatialBridge original = games();
        JsonObject observation = original.handle(json("{op:'observe',me:0}")).getAsJsonArray("obs").get(0).getAsJsonObject();
        SpatialV9Bridge bridge = new SpatialV9Bridge();
        JsonObject request = request(observation); request.addProperty("townContext", false);
        JsonObject expected = output(original.handle(request));
        JsonObject off = output(bridge.handle(request));
        assertEquals(expected, off);
        request.addProperty("legacy", true);
        assertEquals(expected, output(bridge.handle(request)));
        request.remove("legacy"); request.remove("townContext");
        JsonObject on = output(bridge.handle(request));
        assertEquals(0f, off.getAsJsonArray("buildLogits").get(4).getAsFloat());
        assertEquals(.4f, on.getAsJsonArray("buildLogits").get(4).getAsFloat(), 1e-7f);
        request.remove("weights");
        assertEquals(on, output(bridge.handle(request)), "weightless requests retain the loaded context mode");
        request.addProperty("townContext", false);
        assertThrows(IllegalArgumentException.class, () -> bridge.handle(request));
        request.remove("townContext");
        assertEquals(on, output(bridge.handle(request)));
    }

    @Test void badWeightsAndObservationsFailWithoutSilentlyChangingTheLoadedNetwork() {
        SpatialV9Bridge bridge = new SpatialV9Bridge();
        JsonObject observation = games().handle(json("{op:'observe',me:0}")).getAsJsonArray("obs").get(0).getAsJsonObject();
        JsonObject request = request(observation);
        JsonObject noWeights = request.deepCopy(); noWeights.remove("weights");
        assertThrows(IllegalStateException.class, () -> bridge.handle(noWeights));
        JsonObject load = json("{op:'loadWeights'}"); load.addProperty("weights", payload());
        assertTrue(bridge.handle(load).get("loaded").getAsBoolean());
        JsonObject expected = output(bridge.handle(noWeights));
        load.addProperty("weights", "AA==");
        assertThrows(IllegalArgumentException.class, () -> bridge.handle(load));
        assertEquals(expected, output(bridge.handle(noWeights)));
        load.addProperty("weights", payload()); load.addProperty("legacy", true);
        assertThrows(IllegalArgumentException.class, () -> bridge.handle(load), "legacy cannot claim town context");
        JsonObject badShape = noWeights.deepCopy();
        badShape.getAsJsonArray("observations").get(0).getAsJsonObject().add("planes", new JsonArray());
        assertThrows(IllegalArgumentException.class, () -> bridge.handle(badShape));
        JsonObject nonfinite = noWeights.deepCopy();
        nonfinite.getAsJsonArray("observations").get(0).getAsJsonObject().getAsJsonArray("scalars")
                .set(0, new JsonPrimitive("NaN"));
        assertThrows(IllegalArgumentException.class, () -> bridge.handle(nonfinite));
        assertEquals(expected, output(bridge.handle(noWeights)));
    }

    private static JsonObject output(JsonObject response) {
        JsonObject result = response.getAsJsonArray("outputs").get(0).getAsJsonObject();
        assertTrue(result.remove("inferenceNanos").getAsLong() >= 0); return result;
    }
    private static JsonObject request(JsonObject observation) {
        JsonObject request = json("{op:'forward'}"); request.addProperty("weights", payload());
        JsonArray observations = new JsonArray(); observations.add(observation.deepCopy());
        request.add("observations", observations); return request;
    }
    private static String payload() {
        float[] weights = new float[SpatialV9Net.WEIGHT_COUNT];
        weights[2128] = 2; // Existing town embedding bias, channel zero.
        weights[3376] = 1; // Existing BUILD head reads channel zero.
        ByteBuffer buffer = ByteBuffer.allocate(weights.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float weight : weights) buffer.putFloat(weight);
        return Base64.getEncoder().encodeToString(buffer.array());
    }
    private static SpatialBridge games() {
        SpatialBridge bridge = new SpatialBridge();
        bridge.environments.add(new SpatialBridge.Environment(new GameState(9, 1, new int[9], new int[9],
                new int[]{0, 8}, new int[]{0, 0}, new int[][]{{1}, {}})));
        return bridge;
    }
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
}
