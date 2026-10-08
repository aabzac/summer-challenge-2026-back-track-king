package btk.tools.spatial;

import btk.bot.Bot;
import btk.bot.BotFactory;
import btk.bot.neural.SpatialBot;
import btk.bot.neural.SpatialInitialWeights;
import btk.bot.neural.SpatialNet;
import btk.bot.neural.SpatialObservation;
import btk.bot.neural.SpatialV2Weights;
import btk.bot.v09.RolloutBotV09;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Rules;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * V3 fixed-pool adapter; the frozen SpatialBridge owns observations and simultaneous transitions.
 * Initial reset requires seeds[8]. Subsequent reset(envIds,seeds) requires actual terminal states.
 * Slots 0/1, 2/3, 4/5, 6/7 face initializer, v2, v01, v09 respectively; learnerSeat=id%2.
 * opponentPlans(envIds) returns cached plans and diagnostics, independent of learner prefixes.
 * Call it before learner sampling. step(envIds,learnerPlans) uses only those cached opponent plans.
 * observe/prefix/loadWeights/forward retain the original bridge protocol; observe is learner-only.
 * stats() returns cumulative native call counts, failures and timing per opponent across resets.
 * All opponent calls are serial, with 60000 ms and no warmup. A failed call is retained, never retried.
 */
public final class SpatialPoolBridge {
    static final int ENVIRONMENTS = 8;
    static final long OPPONENT_BUDGET_MS = 60_000;
    private static final String[] SPECS = {"spatial-ppo-v2-init", "spatial-ppo-v2", "v01", "v09"};
    private static final Gson JSON = new GsonBuilder().serializeNulls().create();
    private final SpatialBridge bridge;
    private final Function<String, Bot> factory;
    private final LongSupplier clock;
    private final Slot[] slots = new Slot[ENVIRONMENTS];
    private final Stats[] totals = {new Stats(), new Stats(), new Stats(), new Stats()};

    public SpatialPoolBridge() { this(new SpatialBridge(), SpatialPoolBridge::opponent, System::nanoTime); }

    /** Package-only injection for fixtures: no alternate game rules or production policy. */
    SpatialPoolBridge(SpatialBridge bridge, Function<String, Bot> factory, LongSupplier clock) {
        this.bridge = bridge; this.factory = factory; this.clock = clock;
        if (!bridge.environments.isEmpty() && bridge.environments.size() != ENVIRONMENTS)
            throw new IllegalArgumentException("exactly eight environments required");
        for (int id = 0; id < bridge.environments.size(); id++) initialize(id);
    }

    public static void main(String[] args) throws Exception {
        SpatialPoolBridge bridge = new SpatialPoolBridge();
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
                out.println(serializeResponse(response));
            }
        }
    }

    /** Same serializer as JSON-lines output, including explicit null identity fields. */
    static String serializeResponse(JsonObject response) { return JSON.toJson(response); }

    public JsonObject handle(JsonObject request) {
        String op = request.get(request.has("op") ? "op" : "cmd").getAsString();
        return switch (op) {
            case "reset" -> reset(request);
            case "opponentPlans" -> opponentPlans(request);
            case "step" -> step(request);
            case "stats" -> stats();
            case "observe", "prefix" -> {
                if (request.has("envIds")) {
                    JsonArray ids = request.getAsJsonArray("envIds"), seats = request.getAsJsonArray("seats");
                    if (ids.size() != seats.size()) throw new IllegalArgumentException("seat/id count mismatch");
                    for (int i = 0; i < ids.size(); i++) requireLearner(ids.get(i).getAsInt(), seats.get(i).getAsInt());
                } else requireLearner(request.has("envId") ? request.get("envId").getAsInt() : 0,
                        request.has("me") ? request.get("me").getAsInt() : 0);
                yield bridge.handle(request);
            }
            case "loadWeights", "forward" -> bridge.handle(request);
            default -> throw new IllegalArgumentException("unknown pool operation " + op);
        };
    }

    private JsonObject reset(JsonObject request) {
        JsonArray seeds = request.getAsJsonArray("seeds");
        if (seeds == null) throw new IllegalArgumentException("seeds array required");
        int[] ids;
        if (bridge.environments.isEmpty()) {
            if (request.has("envIds") || seeds.size() != ENVIRONMENTS)
                throw new IllegalArgumentException("initial reset requires exactly eight seeds and no envIds");
            ids = new int[ENVIRONMENTS];
            for (int i = 0; i < ids.length; i++) ids[i] = i;
        } else {
            ids = ids(request);
            if (ids.length != seeds.size()) throw new IllegalArgumentException("seed/id count mismatch");
            for (int id : ids) {
                SpatialBridge.Environment env = environment(id);
                if (!Rules.gameOver(env.state, env.pathing)) throw new IllegalStateException("only terminal environments may reset");
            }
        }
        for (int i = 0; i < seeds.size(); i++) seeds.get(i).getAsLong();
        JsonObject result = bridge.handle(request);
        for (int id : ids) initialize(id);
        return identities(result);
    }

    private JsonObject opponentPlans(JsonObject request) {
        int[] ids = ids(request);
        for (int id : ids) {
            SpatialBridge.Environment env = environment(id);
            if (Rules.gameOver(env.state, env.pathing)) throw new IllegalStateException("reset terminal environment before opponentPlans");
        }
        JsonArray plans = new JsonArray(), diagnostics = new JsonArray();
        for (int id : ids) {
            SpatialBridge.Environment env = environment(id); Slot slot = slots[id];
            boolean cached = slot.turn == env.state.turn;
            if (!cached) choose(id, slot, env);
            plans.add(slot.plan == null ? null : slot.plan.deepCopy());
            JsonObject diagnostic = slot.diagnostic.deepCopy(); diagnostic.addProperty("cached", cached);
            diagnostics.add(diagnostic);
        }
        JsonObject response = new JsonObject(); response.add("plans", plans); response.add("diagnostics", diagnostics);
        return response;
    }

    private void choose(int id, Slot slot, SpatialBridge.Environment env) {
        slot.turn = env.state.turn; slot.plan = null;
        JsonObject diagnostic = new JsonObject(); slot.diagnostic = diagnostic;
        diagnostic.addProperty("envId", id); diagnostic.addProperty("turn", env.state.turn);
        diagnostic.addProperty("opponentSpec", spec(id)); diagnostic.addProperty("learnerSeat", id % 2);
        diagnostic.addProperty("opponentSeat", 1 - id % 2); diagnostic.addProperty("budgetMs", OPPONENT_BUDGET_MS);
        diagnostic.addProperty("complete", false); diagnostic.addProperty("legal", false);
        long started = clock.getAsLong();
        try {
            List<Action> actions = slot.bot.play(env.state.copy(), 1 - id % 2, OPPONENT_BUDGET_MS);
            double elapsed = (clock.getAsLong() - started) / 1_000_000.0;
            diagnostic.addProperty("elapsedMs", elapsed);
            boolean completed = true;
            String evidence = "finite greedy algorithm without timer exits";
            if (slot.bot instanceof SpatialBot spatial) {
                completed = !spatial.lastInterrupted; evidence = "SpatialBot.lastInterrupted=false";
            } else if (slot.bot instanceof RolloutBotV09 v09) {
                completed = v09.ordinarySearchComplete || v09.selectedOpening;
                evidence = "v09 ordinarySearchComplete or selectedOpening";
                diagnostic.addProperty("selectedOpening", v09.selectedOpening);
            }
            diagnostic.addProperty("completionEvidence", evidence);
            Rules.TurnResult validation = new Rules.TurnResult(); int me = 1 - id % 2;
            Rules.expand(env.state, me, actions, env.pathing, validation.moves[me], validation);
            Rules.PlayerMoves moves = validation.moves[me];
            boolean legal = moves.error == null && moves.skipped.isEmpty();
            diagnostic.addProperty("legal", legal); diagnostic.addProperty("actions", Action.format(actions));
            if (!legal) throw new IllegalStateException("illegal opponent plan: " + moves.error + " " + moves.skipped);
            if (!completed || elapsed >= OPPONENT_BUDGET_MS)
                throw new IllegalStateException("opponent did not complete within its ample budget");
            JsonObject plan = new JsonObject(); JsonArray cells = new JsonArray();
            for (int i = 0; i < moves.count; i++)
                cells.add(env.state.y(moves.cells[i]) * SpatialObservation.W + env.state.x(moves.cells[i]));
            plan.add("cells", cells); plan.addProperty("disrupt", moves.disrupts.isEmpty() ? -1 : moves.disrupts.get(0));
            slot.plan = plan; diagnostic.addProperty("complete", true);
        } catch (RuntimeException ex) {
            if (!diagnostic.has("elapsedMs")) diagnostic.addProperty("elapsedMs", (clock.getAsLong() - started) / 1_000_000.0);
            diagnostic.addProperty("failure", ex.getClass().getSimpleName() + ": " + ex.getMessage());
        } finally {
            Stats total = totals[id / 2]; double elapsed = diagnostic.get("elapsedMs").getAsDouble();
            total.calls++; total.elapsedMs += elapsed; total.maxElapsedMs = Math.max(total.maxElapsedMs, elapsed);
            if (diagnostic.get("complete").getAsBoolean()) total.completed++; else total.failed++;
        }
    }

    private JsonObject stats() {
        JsonArray opponents = new JsonArray();
        for (int group = 0; group < SPECS.length; group++) {
            Stats total = totals[group]; JsonObject row = new JsonObject();
            row.addProperty("opponentSpec", SPECS[group]); row.addProperty("calls", total.calls);
            row.addProperty("completed", total.completed); row.addProperty("failed", total.failed);
            row.addProperty("elapsedMsTotal", total.elapsedMs); row.addProperty("maxElapsedMs", total.maxElapsedMs);
            row.addProperty("budgetMs", OPPONENT_BUDGET_MS); row.addProperty("warmup", false);
            row.add("params", JSON.toJsonTree(BotFactory.configuration(SPECS[group])));
            opponents.add(row);
        }
        JsonObject result = new JsonObject(); result.add("opponentStats", opponents); return result;
    }

    private JsonObject step(JsonObject request) {
        int[] ids = ids(request); JsonArray learner = request.getAsJsonArray("learnerPlans");
        if (learner == null || learner.size() != ids.length || request.has("plans"))
            throw new IllegalArgumentException("only learnerPlans with one plan per envId accepted");
        JsonArray pairs = new JsonArray();
        for (int i = 0; i < ids.length; i++) {
            int id = ids[i]; SpatialBridge.Environment env = environment(id); Slot slot = slots[id];
            if (slot.turn != env.state.turn || slot.plan == null || !slot.diagnostic.get("complete").getAsBoolean())
                throw new IllegalStateException("complete cached opponent plan required for current turn");
            JsonArray pair = new JsonArray();
            pair.add(id % 2 == 0 ? learner.get(i) : slot.plan);
            pair.add(id % 2 == 1 ? learner.get(i) : slot.plan); pairs.add(pair);
        }
        JsonObject delegated = new JsonObject(); delegated.addProperty("op", "step");
        delegated.add("envIds", request.getAsJsonArray("envIds")); delegated.add("plans", pairs);
        // The original bridge validates the whole batch before its single resolve per environment.
        return identities(bridge.handle(delegated));
    }

    private JsonObject identities(JsonObject result) {
        for (var element : result.getAsJsonArray("envs")) {
            JsonObject env = element.getAsJsonObject(); int id = env.get("id").getAsInt();
            env.addProperty("learnerSeat", id % 2); env.addProperty("opponentSeat", 1 - id % 2);
            env.addProperty("opponentSpec", spec(id));
            env.add("opponentParams", JSON.toJsonTree(BotFactory.configuration(spec(id))));
            env.addProperty("opponentWeightsSha256", slots[id].weightHash);
        }
        return result;
    }

    private void initialize(int id) {
        Slot slot = new Slot(); slots[id] = slot; slot.bot = factory.apply(spec(id));
        slot.bot.init(environment(id).state.copy(), 1 - id % 2);
        String payload = id < 2 ? SpatialInitialWeights.DATA : id < 4 ? SpatialV2Weights.DATA : null;
        slot.weightHash = payload == null ? null : payloadHash(payload);
    }

    private static Bot opponent(String spec) {
        if (spec.equals(SPECS[0]) || spec.equals(SPECS[1]))
            return new SpatialBot(BotFactory.parameters(spec), new SpatialNet(spec.equals(SPECS[0])
                    ? SpatialInitialWeights.DATA : SpatialV2Weights.DATA), System::nanoTime);
        return BotFactory.create(spec);
    }

    private static String payloadHash(String payload) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Base64.getDecoder().decode(payload))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private int[] ids(JsonObject request) {
        JsonArray input = request.getAsJsonArray("envIds");
        if (input == null || input.isEmpty()) throw new IllegalArgumentException("nonempty envIds required");
        int[] ids = new int[input.size()]; boolean[] seen = new boolean[ENVIRONMENTS];
        for (int i = 0; i < ids.length; i++) {
            int id = input.get(i).getAsInt(); environment(id);
            if (seen[id]) throw new IllegalArgumentException("duplicate environment");
            seen[id] = true; ids[i] = id;
        }
        return ids;
    }

    private SpatialBridge.Environment environment(int id) {
        if (id < 0 || id >= bridge.environments.size()) throw new IllegalArgumentException("unknown environment " + id);
        return bridge.environments.get(id);
    }

    private void requireLearner(int id, int me) {
        environment(id);
        if (me != id % 2) throw new IllegalArgumentException("observe the fixed learner seat only");
    }

    private static String spec(int id) { return SPECS[id / 2]; }
    private static final class Slot {
        Bot bot; int turn = -1; String weightHash; JsonObject plan, diagnostic;
    }
    private static final class Stats {
        long calls, completed, failed; double elapsedMs, maxElapsedMs;
    }
}
