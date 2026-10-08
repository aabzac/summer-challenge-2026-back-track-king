package btk.tools.spatial;

import btk.bot.neural.SpatialNet;
import btk.bot.neural.SpatialObservation;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Protocol;
import btk.core.Rules;
import btk.referee.MapGenerator;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Persistent JSON-lines, batched exact-engine bridge. No stdout logging outside responses. */
public final class SpatialBridge {
    private static final Gson GSON = new Gson();
    final List<Environment> environments = new ArrayList<>();
    private SpatialNet net;

    public static void main(String[] args) throws Exception {
        SpatialBridge bridge = new SpatialBridge();
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

    /** reset(seeds[,envIds]), observe(envIds,seats[,prefixes,paints,stages,stopped]), step(envIds,plans). */
    public JsonObject handle(JsonObject request) {
        String op = request.get(request.has("op") ? "op" : "cmd").getAsString();
        return switch (op) {
            case "reset" -> reset(request);
            case "observe", "prefix" -> observe(request);
            case "step" -> step(request);
            case "loadWeights" -> {
                net = new SpatialNet(request.get("weights").getAsString());
                JsonObject result = new JsonObject(); result.addProperty("loaded", true); yield result;
            }
            case "forward" -> forward(request);
            default -> throw new IllegalArgumentException("unknown operation " + op);
        };
    }

    private JsonObject reset(JsonObject request) {
        JsonArray seeds = request.has("seeds") ? request.getAsJsonArray("seeds") : array(request.get("seed"));
        JsonArray ids = request.has("envIds") ? request.getAsJsonArray("envIds") : null;
        if (ids != null && ids.size() != seeds.size()) throw new IllegalArgumentException("seed/id count mismatch");
        if (ids == null) environments.clear();
        JsonArray envs = new JsonArray();
        for (int i = 0; i < seeds.size(); i++) {
            int id = ids == null ? i : ids.get(i).getAsInt();
            if (id < 0 || id > environments.size()) throw new IllegalArgumentException("invalid reset env id " + id);
            long seed = seeds.get(i).getAsLong();
            Environment env = new Environment(MapGenerator.generate(seed), seed);
            if (id == environments.size()) environments.add(env); else environments.set(id, env);
            envs.add(snapshot(id, env, new int[2]));
        }
        JsonObject response = new JsonObject(); response.add("envs", envs); return response;
    }

    private JsonObject observe(JsonObject request) {
        boolean batch = request.has("envIds");
        JsonArray ids = batch ? request.getAsJsonArray("envIds") : array(number(request, "envId", 0));
        JsonArray observations = new JsonArray();
        for (int i = 0; i < ids.size(); i++) {
            Environment env = environment(ids.get(i).getAsInt());
            int me = batch ? request.getAsJsonArray("seats").get(i).getAsInt() : integer(request, "me", 0);
            int[] cells = batch ? intArrayAt(request, "prefixes", i) : ints(request.get("cells"));
            int stage = batch ? intAt(request, "stages", i, 0) : integer(request, "stage", 0);
            boolean stopped = batch ? boolAt(request, "stopped", i, false) : bool(request, "stopped", false);
            int paint = env.remainingPaint(cells);
            int suppliedPaint = batch ? intAt(request, "paints", i, paint) : integer(request, "paint", paint);
            SpatialObservation obs = env.observation(me);
            obs.prefix(cells, cells.length, suppliedPaint, stage, stopped);
            observations.add(observationJson(obs));
        }
        JsonObject response = new JsonObject(); response.add("obs", observations); return response;
    }

    private JsonObject step(JsonObject request) {
        JsonArray ids = request.has("envIds") ? request.getAsJsonArray("envIds") : array(number(request, "envId", 0));
        JsonArray plans = request.getAsJsonArray("plans");
        boolean batch = request.has("envIds");
        if (batch && plans.size() != ids.size()) throw new IllegalArgumentException("plan/id count mismatch");
        List<List<List<Action>>> actions = new ArrayList<>();
        // Validate the entire batch against unchanged states before mutating any environment.
        for (int i = 0; i < ids.size(); i++) {
            for (int j = 0; j < i; j++) if (ids.get(j).getAsInt() == ids.get(i).getAsInt())
                throw new IllegalArgumentException("duplicate environment in step batch");
            Environment env = environment(ids.get(i).getAsInt());
            actions.add(env.validatePlans(batch ? plans.get(i).getAsJsonArray() : plans));
        }
        JsonArray envs = new JsonArray();
        for (int i = 0; i < ids.size(); i++) {
            int id = ids.get(i).getAsInt(); Environment env = environment(id);
            Rules.TurnResult turn = Rules.resolve(env.state, actions.get(i).get(0), actions.get(i).get(1), env.pathing);
            env.preparedTurn = -1;
            envs.add(snapshot(id, env, turn.gained));
        }
        JsonObject response = new JsonObject(); response.add("envs", envs); return response;
    }

    private JsonObject forward(JsonObject request) {
        if (request.has("weights")) net = new SpatialNet(request.get("weights").getAsString());
        if (net == null) throw new IllegalStateException("load weights before forward");
        SpatialObservation obs = new SpatialObservation(new GameState(1, 1, new int[]{0}, new int[]{0},
                new int[0], new int[0], new int[0][]));
        JsonArray outputs = new JsonArray();
        for (JsonElement element : request.getAsJsonArray("observations")) {
            JsonObject src = element.getAsJsonObject();
            copyFloats(src, "planes", obs.planes); copyFloats(src, "towns", obs.towns);
            copyFloats(src, "scalars", obs.scalars); copyInts(src, "townCells", obs.townCells);
            copyInts(src, "regionIds", obs.regionIds); copyBooleans(src, "buildMask", obs.buildMask);
            copyBooleans(src, "disruptMask", obs.disruptMask);
            long started = System.nanoTime(); net.forward(obs); long elapsed = System.nanoTime() - started;
            JsonObject result = new JsonObject();
            result.add("buildLogits", GSON.toJsonTree(net.buildLogits));
            result.add("disruptLogits", GSON.toJsonTree(net.disruptLogits));
            result.addProperty("value", net.value); result.addProperty("inferenceNanos", elapsed);
            outputs.add(result);
        }
        JsonObject response = new JsonObject(); response.add("outputs", outputs); return response;
    }

    private Environment environment(int id) {
        if (id < 0 || id >= environments.size()) throw new IllegalArgumentException("unknown environment " + id);
        return environments.get(id);
    }

    private static JsonObject snapshot(int id, Environment env, int[] gained) {
        boolean done = Rules.gameOver(env.state, env.pathing);
        int terminal = done ? Integer.compare(env.state.score[0], env.state.score[1]) : 0;
        JsonObject result = new JsonObject();
        result.addProperty("id", id); result.addProperty("turn", env.state.turn);
        result.addProperty("mapHash", env.mapHash);
        result.addProperty("mapSha256", env.mapHash);
        if (env.seed != 0) result.addProperty("seed", env.seed);
        result.addProperty("width", env.state.width); result.addProperty("height", env.state.height);
        result.addProperty("done", done); result.add("score", GSON.toJsonTree(env.state.score));
        result.add("gained", GSON.toJsonTree(gained));
        result.add("rewardGap", GSON.toJsonTree(new int[]{gained[0] - gained[1], gained[1] - gained[0]}));
        result.add("terminalSign", GSON.toJsonTree(new int[]{terminal, -terminal}));
        JsonArray obs = new JsonArray();
        for (int me = 0; me < 2; me++) {
            SpatialObservation observation = env.observation(me);
            observation.prefix(null, 0, GameState.PAINT_PER_TURN, 0, false);
            obs.add(observationJson(observation));
        }
        result.add("obs", obs); return result;
    }

    /** Snapshot arrays now: later requests may reuse and change the same observation object. */
    private static JsonObject observationJson(SpatialObservation observation) {
        JsonObject result = new JsonObject();
        result.add("planes", GSON.toJsonTree(observation.planes));
        result.add("towns", GSON.toJsonTree(observation.towns));
        result.add("scalars", GSON.toJsonTree(observation.scalars));
        result.add("townCells", GSON.toJsonTree(observation.townCells));
        result.add("regionIds", GSON.toJsonTree(observation.regionIds));
        result.add("buildMask", GSON.toJsonTree(observation.buildMask));
        result.add("disruptMask", GSON.toJsonTree(observation.disruptMask)); return result;
    }

    static final class Environment {
        final GameState state;
        final Pathing pathing;
        final String mapHash;
        final long seed;
        final SpatialObservation[] observations;
        int preparedTurn = -1;
        Environment(GameState state) { this(state, 0); }
        Environment(GameState state, long seed) {
            this.seed = seed;
            this.state = state; pathing = new Pathing(state.size); Rules.updateConnections(state, pathing);
            try {
                mapHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(Protocol.initBlock(state, 0).getBytes(StandardCharsets.UTF_8)));
            } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
            observations = new SpatialObservation[]{new SpatialObservation(state), new SpatialObservation(state)};
        }
        SpatialObservation observation(int me) {
            if (me < 0 || me > 1) throw new IllegalArgumentException("invalid player");
            if (preparedTurn != state.turn) {
                for (int p = 0; p < 2; p++) observations[p].prepare(state, p);
                preparedTurn = state.turn;
            }
            return observations[me];
        }
        int remainingPaint(int[] cells) {
            int paint = GameState.PAINT_PER_TURN;
            for (int padded : cells) paint -= state.placeCost(observations[0].nativeCell(padded));
            return paint;
        }
        List<List<Action>> validatePlans(JsonArray plans) {
            if (Rules.gameOver(state, pathing)) throw new IllegalStateException("reset terminal environment before step");
            if (plans.size() != 2) throw new IllegalArgumentException("both simultaneous plans required");
            List<List<Action>> result = new ArrayList<>(2);
            for (int me = 0; me < 2; me++) {
                JsonObject plan = plans.get(me).getAsJsonObject();
                int[] cells = ints(plan.get("cells"));
                if (cells.length > 3) throw new IllegalArgumentException("too many placements");
                List<Action> actions = new ArrayList<>(4);
                for (int padded : cells) {
                    int c = observations[me].nativeCell(padded);
                    actions.add(Action.place(state.x(c), state.y(c)));
                }
                int target = integer(plan, "disrupt", -1);
                if (target != -1 && target != SpatialObservation.STOP) actions.add(Action.disrupt(target));
                if (actions.isEmpty()) actions.add(Action.waitAction());
                Rules.TurnResult validation = new Rules.TurnResult();
                Rules.expand(state, me, actions, pathing, validation.moves[me], validation);
                if (validation.moves[me].error != null || !validation.moves[me].skipped.isEmpty())
                    throw new IllegalArgumentException("illegal plan P" + me + ": " + validation.moves[me].error
                            + " " + validation.moves[me].skipped);
                result.add(actions);
            }
            return result;
        }
    }

    private static JsonArray array(JsonElement value) { JsonArray result = new JsonArray(); result.add(value); return result; }
    private static JsonElement number(JsonObject o, String key, int fallback) { return GSON.toJsonTree(integer(o, key, fallback)); }
    private static int integer(JsonObject o, String key, int fallback) { return o.has(key) ? o.get(key).getAsInt() : fallback; }
    private static boolean bool(JsonObject o, String key, boolean fallback) { return o.has(key) ? o.get(key).getAsBoolean() : fallback; }
    private static int intAt(JsonObject o, String key, int i, int fallback) { return o.has(key) ? o.getAsJsonArray(key).get(i).getAsInt() : fallback; }
    private static boolean boolAt(JsonObject o, String key, int i, boolean fallback) { return o.has(key) ? o.getAsJsonArray(key).get(i).getAsBoolean() : fallback; }
    private static int[] intArrayAt(JsonObject o, String key, int i) { return o.has(key) ? ints(o.getAsJsonArray(key).get(i)) : new int[0]; }
    private static int[] ints(JsonElement value) {
        if (value == null || value.isJsonNull()) return new int[0];
        JsonArray array = value.getAsJsonArray(); int[] result = new int[array.size()];
        for (int i = 0; i < result.length; i++) result[i] = array.get(i).getAsInt(); return result;
    }
    private static JsonArray checkedArray(JsonObject src, String key, int expected) {
        JsonArray array = src.getAsJsonArray(key);
        if (array.size() != expected) throw new IllegalArgumentException("wrong " + key + " length"); return array;
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
