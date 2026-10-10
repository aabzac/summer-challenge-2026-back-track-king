package btk.tools.spatial;

import btk.bot.neural.SpatialNet;
import btk.bot.neural.SpatialObservation;
import btk.bot.neural.SpatialV9Net;
import btk.core.GameState;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/** V9 numerical bridge. All game state operations delegate to the unchanged exact-core bridge. */
public final class SpatialV9Bridge {
    private static final Gson GSON = new Gson();
    private final SpatialBridge games;
    private SpatialV9Net net;
    private SpatialNet legacyNet;
    private boolean townContext = true, legacy;

    public SpatialV9Bridge() { this(new SpatialBridge()); }
    SpatialV9Bridge(SpatialBridge games) { this.games = games; }

    public static void main(String[] args) throws Exception {
        SpatialV9Bridge bridge = new SpatialV9Bridge();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(System.out, true, StandardCharsets.UTF_8)) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) continue;
                JsonObject response;
                try { response = bridge.handle(JsonParser.parseString(line).getAsJsonObject()); }
                catch (RuntimeException ex) {
                    response = new JsonObject();
                    response.addProperty("error", ex.getClass().getSimpleName() + ": " + ex.getMessage());
                }
                out.println(GSON.toJson(response));
            }
        }
    }

    /** forward/loadWeights accept weights, townContext (default true), legacy (default false). */
    public JsonObject handle(JsonObject request) {
        String op = request.get(request.has("op") ? "op" : "cmd").getAsString();
        if (!op.equals("forward") && !op.equals("loadWeights")) return games.handle(request);
        if (op.equals("loadWeights") || request.has("weights")) load(request);
        else {
            if (net == null && legacyNet == null) throw new IllegalStateException("load weights before forward");
            if (request.has("townContext") && request.get("townContext").getAsBoolean() != townContext
                    || request.has("legacy") && request.get("legacy").getAsBoolean() != legacy)
                throw new IllegalArgumentException("supply weights when changing inference mode");
        }
        if (op.equals("loadWeights")) {
            JsonObject result = new JsonObject(); result.addProperty("loaded", true);
            result.addProperty("townContext", townContext); result.addProperty("legacy", legacy); return result;
        }
        return forward(request);
    }

    private void load(JsonObject request) {
        boolean context = !request.has("townContext") || request.get("townContext").getAsBoolean();
        boolean old = request.has("legacy") && request.get("legacy").getAsBoolean();
        if (old && context) throw new IllegalArgumentException("legacy mode requires townContext=false");
        String weights = request.get("weights").getAsString();
        // Validate completely before replacing a previously loaded model.
        SpatialV9Net next = old ? null : new SpatialV9Net(weights, context);
        SpatialNet nextLegacy = old ? new SpatialNet(weights) : null;
        net = next; legacyNet = nextLegacy; townContext = context; legacy = old;
    }

    private JsonObject forward(JsonObject request) {
        SpatialObservation obs = new SpatialObservation(new GameState(1, 1, new int[]{0}, new int[]{0},
                new int[0], new int[0], new int[0][]));
        JsonArray outputs = new JsonArray();
        for (JsonElement element : request.getAsJsonArray("observations")) {
            JsonObject src = element.getAsJsonObject();
            copyFloats(src, "planes", obs.planes); copyFloats(src, "towns", obs.towns);
            copyFloats(src, "scalars", obs.scalars); copyInts(src, "townCells", obs.townCells);
            copyInts(src, "regionIds", obs.regionIds); copyBooleans(src, "buildMask", obs.buildMask);
            copyBooleans(src, "disruptMask", obs.disruptMask);
            long started = System.nanoTime();
            if (legacy) legacyNet.forward(obs); else net.forward(obs);
            long elapsed = System.nanoTime() - started;
            JsonObject result = new JsonObject();
            result.add("buildLogits", GSON.toJsonTree(legacy ? legacyNet.buildLogits : net.buildLogits));
            result.add("disruptLogits", GSON.toJsonTree(legacy ? legacyNet.disruptLogits : net.disruptLogits));
            result.addProperty("value", legacy ? legacyNet.value : net.value);
            result.addProperty("inferenceNanos", elapsed); outputs.add(result);
        }
        JsonObject response = new JsonObject(); response.add("outputs", outputs); return response;
    }

    private static JsonArray checkedArray(JsonObject src, String key, int expected) {
        JsonArray array = src.getAsJsonArray(key);
        if (array == null || array.size() != expected) throw new IllegalArgumentException("wrong " + key + " length");
        return array;
    }
    private static void copyFloats(JsonObject src, String key, float[] target) {
        JsonArray array = checkedArray(src, key, target.length);
        for (int i = 0; i < target.length; i++) {
            target[i] = array.get(i).getAsFloat();
            if (!Float.isFinite(target[i])) throw new IllegalArgumentException("non-finite " + key);
        }
    }
    private static void copyInts(JsonObject src, String key, int[] target) {
        JsonArray array = checkedArray(src, key, target.length);
        for (int i = 0; i < target.length; i++) target[i] = array.get(i).getAsInt();
    }
    private static void copyBooleans(JsonObject src, String key, boolean[] target) {
        JsonArray array = checkedArray(src, key, target.length);
        for (int i = 0; i < target.length; i++) target[i] = array.get(i).getAsBoolean();
    }
}
