package btk.tools.spatial;

import btk.bot.Bot;
import btk.bot.BotFactory;
import btk.bot.Params;
import btk.bot.neural.SpatialV9Bot;
import btk.bot.neural.SpatialV9Net;
import btk.bot.neural.SpatialObservation;
import btk.bot.v09.RolloutBotV09;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Protocol;
import btk.core.Rules;
import btk.referee.MapGenerator;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import com.google.gson.stream.JsonWriter;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * V9 diagnostic: replay immutable saved v4 reference trajectories, then query the new model.
 * Candidate ample and nominal timing run in separate serial phases on the identical saved states.
 * This tool neither trains nor evaluates a win-rate gate, and never replaces missing late slots.
 */
public final class SpatialV9Panel {
    private static final Gson JSON = new GsonBuilder().serializeNulls().create();
    private static final String SPEC = "spatial-ppo-v9";
    private static final long FIRST_SEED = 10_100_000L;
    private static final int MAPS = 4;
    private static final int[] TURNS = {0, 25, 50, 75, 99};
    private static final long REFERENCE_MS = 60_000, AMPLE_MS = 60_000, NOMINAL_MS = 36, WARMUP_MS = 100;

    private SpatialV9Panel() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException(
                "Usage: SpatialV9Panel initial-weights.base64 final-weights.base64 fresh-output-directory saved-v4-panel.json");
        String[] labels = {"initial", "final"};
        String[] payloads = {Files.readString(Path.of(args[0])).trim(), Files.readString(Path.of(args[1])).trim()};
        for (String payload : payloads) new SpatialV9Net(payload, true); // Validate before creating any output.
        Path output = Path.of(args[2]).toAbsolutePath();
        Files.createDirectories(output.getParent());
        Files.createDirectory(output); // Write-once complete diagnostic, never an accidental rerun.
        Params candidateParams = BotFactory.parameters(SPEC);
        List<Position> positions = new ArrayList<>();
        List<Map<String, Object>> maps = new ArrayList<>(), trajectory = new ArrayList<>();
        long started = System.nanoTime();
        byte[] savedBytes = Files.readAllBytes(Path.of(args[3]));
        JsonObject saved = JsonParser.parseString(new String(savedBytes, StandardCharsets.UTF_8)).getAsJsonObject();
        loadSaved(saved, positions, maps, trajectory);
        writeNew(output.resolve("mechanism.json"), mechanism(payloads, labels));
        Map<String, Object> panel = new LinkedHashMap<>();
        panel.put("schemaVersion", 1); panel.put("experiment", SPEC);
        panel.put("firstSeed", FIRST_SEED); panel.put("plannedMaps", MAPS); panel.put("requestedTurns", TURNS);
        panel.put("reference", "v09/v09"); panel.put("referenceParams", BotFactory.configuration("v09"));
        panel.put("referenceBudgetMsPerDecision", REFERENCE_MS);
        panel.put("referenceWarmup", "historical; no new reference bot calls");
        panel.put("savedPanelSha256", sha256(savedBytes));
        panel.put("method", "Replay existing frozen v4 late-panel reference actions through btk.core and match every saved snapshot. No new games/decisions; preserve actual terminal omissions. Then trace and measure both v9 payloads on identical states.");
        panel.put("maps", maps); panel.put("trajectory", trajectory);
        List<Map<String, Object>> snapshots = new ArrayList<>();
        for (Position position : positions) snapshots.add(position.snapshot());
        panel.put("positions", snapshots); panel.put("positionsCount", positions.size());
        panel.put("trajectoryDurationMs", millis(started));
        writeNew(output.resolve("panel.json"), panel);

        @SuppressWarnings("unchecked") List<String>[] expected = new List[]{new ArrayList<>(), new ArrayList<>()};
        for (int model = 0; model < labels.length; model++) {
            SpatialV9Net net = new SpatialV9Net(payloads[model], true);
            try (BufferedWriter file = Files.newBufferedWriter(output.resolve(labels[model] + "-parity.json"),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                 JsonWriter writer = new JsonWriter(file)) {
                writer.beginObject(); writer.name("label").value(labels[model]);
                writer.name("weightsSha256").value(sha256(Base64.getDecoder().decode(payloads[model])));
                writer.name("rows").beginArray();
                for (Position position : positions) for (int me = 0; me < 2; me++) {
                    String before = stateIdentity(position.state);
                    Trace trace = trace(position.state, me, net);
                    if (!before.equals(stateIdentity(position.state))) throw new IllegalStateException("Trace mutated saved state");
                    expected[model].add(Action.format(trace.actions));
                    for (JsonObject row : trace.rows) {
                        row.addProperty("seed", position.seed); row.addProperty("mapHash", position.mapHash);
                        row.addProperty("turn", position.state.turn); row.addProperty("me", me);
                        JSON.toJson(row, writer);
                    }
                }
                writer.endArray(); writer.endObject();
            }
        }

        @SuppressWarnings("unchecked") List<Measurement>[] ample = new List[labels.length], nominal = new List[labels.length];
        // All ample measurements finish before any nominal measurement; no candidate affects the trajectory.
        for (int model = 0; model < labels.length; model++)
            ample[model] = measure(positions, payloads[model], candidateParams, AMPLE_MS);
        for (int model = 0; model < labels.length; model++)
            nominal[model] = measure(positions, payloads[model], candidateParams, NOMINAL_MS);
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("schemaVersion", 1); profile.put("experiment", SPEC);
        profile.put("candidateParams", BotFactory.configuration(SPEC));
        profile.put("plannedMaps", MAPS); profile.put("requestedTurns", TURNS);
        profile.put("capturedPositions", positions.size());
        profile.put("warmupMsPerSeatPerMapPerPhase", WARMUP_MS);
        profile.put("ampleBudgetMs", AMPLE_MS); profile.put("nominalBudgetMs", NOMINAL_MS);
        profile.put("method", "All ample initial, ample final, nominal initial, nominal final calls run serially in that order. "
                + "Each model/map/seat/phase gets a fresh bot and 100ms unmeasured warmup. "
                + "Play timing includes observation preparation, excludes validation/serialization, and does not measure protocol IO. "
                + "This local profile is not an exact-artifact process check or an arena deadline guarantee.");
        profile.put("javaVersion", System.getProperty("java.version")); profile.put("os", System.getProperty("os.name"));
        List<Map<String, Object>> summaries = new ArrayList<>(), samples = new ArrayList<>();
        for (int model = 0; model < labels.length; model++) {
            List<Double> ampleTimes = new ArrayList<>(), nominalTimes = new ArrayList<>();
            int matches = 0, uninterruptedMismatches = 0, parityMismatches = 0, cuts = 0, ampleCuts = 0, over = 0, errors = 0, skipped = 0;
            for (int i = 0; i < ample[model].size(); i++) {
                Position position = positions.get(i / 2); int me = i % 2;
                Measurement a = ample[model].get(i), n = nominal[model].get(i);
                boolean same = a.actions.equals(n.actions), paritySame = a.actions.equals(expected[model].get(i));
                if (same) matches++;
                if (!same && !a.interrupted && !n.interrupted) uninterruptedMismatches++;
                if (!paritySame) parityMismatches++;
                if (a.interrupted) ampleCuts++;
                if (n.interrupted) cuts++;
                if (n.ms > NOMINAL_MS) over++;
                errors += (a.error == null ? 0 : 1) + (n.error == null ? 0 : 1);
                skipped += a.skipped.size() + n.skipped.size();
                ampleTimes.add(a.ms); nominalTimes.add(n.ms);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("label", labels[model]); row.put("seed", position.seed); row.put("mapHash", position.mapHash);
                row.put("turn", position.state.turn); row.put("me", me);
                row.put("ample", a); row.put("nominal", n);
                row.put("actionsMatch", same); row.put("ampleMatchesParityTrace", paritySame);
                samples.add(row);
            }
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("label", labels[model]); summary.put("weightsSha256", sha256(Base64.getDecoder().decode(payloads[model])));
            summary.put("seatSamples", ample[model].size()); summary.put("actionMatches", matches);
            summary.put("actionMismatches", ample[model].size() - matches);
            summary.put("uninterruptedActionMismatches", uninterruptedMismatches);
            summary.put("ampleParityTraceMismatches", parityMismatches);
            summary.put("ampleInterrupted", ampleCuts); summary.put("nominalInterrupted", cuts);
            summary.put("nominalCallsOver36Ms", over); summary.put("errors", errors); summary.put("skippedActions", skipped);
            summary.put("amplePlayMs", timing(ampleTimes)); summary.put("nominalPlayMs", timing(nominalTimes));
            summaries.add(summary);
        }
        profile.put("summary", summaries); profile.put("samples", samples);
        writeNew(output.resolve("profile.json"), profile);
        System.out.println("Late panel positions=" + positions.size() + " of " + (MAPS * TURNS.length)
                + "; conditional rows/model=" + positions.size() * 2 * 4 + "; " + output);
    }

    static void loadSaved(JsonObject saved, List<Position> positions, List<Map<String, Object>> maps,
                          List<Map<String, Object>> trajectory) throws Exception {
        if (saved.get("firstSeed").getAsLong() != FIRST_SEED || saved.get("plannedMaps").getAsInt() != MAPS
                || saved.get("positionsCount").getAsInt() != 16 || saved.getAsJsonArray("maps").size() != MAPS)
            throw new IllegalStateException("Wrong saved v4 panel");
        int matched = 0, replayed = 0;
        for (int mapIndex = 0; mapIndex < MAPS; mapIndex++) {
            JsonObject map = saved.getAsJsonArray("maps").get(mapIndex).getAsJsonObject();
            long seed = map.get("seed").getAsLong(); String hash = map.get("mapHash").getAsString();
            String init = map.get("initBlockP0").getAsString();
            if (seed != FIRST_SEED + mapIndex || !sha256(init.getBytes(StandardCharsets.UTF_8)).equals(hash))
                throw new IllegalStateException("Saved map identity mismatch");
            GameState state = Protocol.readInit(new Protocol.TokenReader(new ByteArrayInputStream(init.getBytes(StandardCharsets.UTF_8)))).state;
            Pathing pathing = new Pathing(state.size); Rules.updateConnections(state, pathing);
            int endTurn = map.get("endTurn").getAsInt();
            for (int turn = 0; turn <= endTurn; turn++) {
                for (var item : saved.getAsJsonArray("positions")) {
                    JsonObject snapshot = item.getAsJsonObject();
                    if (snapshot.get("seed").getAsLong() != seed || snapshot.get("turn").getAsInt() != turn) continue;
                    if (Rules.gameOver(state, pathing) || !snapshot.get("mapHash").getAsString().equals(hash))
                        throw new IllegalStateException("Recorded position is not live/on this map");
                    Position p = new Position(seed, hash, state.copy());
                    if (!JSON.toJsonTree(p.snapshot()).equals(snapshot)) throw new IllegalStateException("Saved snapshot differs from core replay");
                    positions.add(p); matched++;
                }
                if (turn == endTurn) break;
                JsonObject row = null;
                for (var item : saved.getAsJsonArray("trajectory")) {
                    JsonObject candidate = item.getAsJsonObject();
                    if (candidate.get("seed").getAsLong() == seed && candidate.get("turn").getAsInt() == turn) {
                        if (row != null) throw new IllegalStateException("Duplicate reference turn"); row = candidate;
                    }
                }
                if (row == null || Rules.gameOver(state, pathing) || !row.get("mapHash").getAsString().equals(hash))
                    throw new IllegalStateException("Missing or post-terminal saved reference turn");
                var seats = row.getAsJsonArray("seats");
                Rules.TurnResult tr = Rules.resolve(state, Action.parseLine(seats.get(0).getAsJsonObject().get("actions").getAsString()),
                        Action.parseLine(seats.get(1).getAsJsonObject().get("actions").getAsString()), pathing);
                if (tr.unknownActions || tr.hasError(0) || tr.hasError(1) || !tr.moves[0].skipped.isEmpty() || !tr.moves[1].skipped.isEmpty()
                        || !JSON.toJsonTree(tr.gained).equals(row.get("gained")) || !JSON.toJsonTree(state.score).equals(row.get("scoreAfter")))
                    throw new IllegalStateException("Saved reference transition differs from core");
                replayed++;
            }
            if (state.turn != endTurn || Rules.gameOver(state, pathing) != map.get("terminal").getAsBoolean()
                    || !JSON.toJsonTree(state.score).equals(map.get("scoreAtStop"))) throw new IllegalStateException("Saved stop mismatch");
            maps.add(JSON.fromJson(map, Map.class));
        }
        if (matched != 16 || matched != saved.getAsJsonArray("positions").size()
                || replayed != saved.getAsJsonArray("trajectory").size()) throw new IllegalStateException("Saved panel count mismatch");
        Map<String, Object> audit = new LinkedHashMap<>(); audit.put("replayedReferenceTurns", replayed);
        audit.put("matchedSavedPositions", matched); audit.put("newReferenceCalls", 0); trajectory.add(audit);
    }

    static JsonObject mechanism(String[] payloads, String[] labels) {
        var fixture = SpatialLearningMechanism.fixture();
        JsonObject result = new JsonObject(); result.addProperty("role", "Exposed delayed-route representation probe, not strength or optimal-action evidence");
        result.addProperty("investmentCell", fixture.investment()); result.addProperty("alternativeCell", fixture.alternative());
        com.google.gson.JsonArray rows = new com.google.gson.JsonArray(); result.add("rows", rows);
        for (int model = 0; model < payloads.length; model++) for (int me = 0; me < 2; me++) {
            SpatialObservation obs = new SpatialObservation(fixture.root()); obs.prepare(fixture.root(), me);
            btk.bot.neural.SpatialNet old = new btk.bot.neural.SpatialNet(payloads[model]); old.forward(obs);
            for (boolean enabled : new boolean[]{false, true}) {
                SpatialV9Net net = new SpatialV9Net(payloads[model], enabled); net.forward(obs);
                if (!enabled && (!java.util.Arrays.equals(old.buildLogits, net.buildLogits)
                        || !java.util.Arrays.equals(old.disruptLogits, net.disruptLogits) || old.value != net.value))
                    throw new IllegalStateException("Context-off changed historical network output");
                JsonObject row = new JsonObject(); row.addProperty("label", labels[model]); row.addProperty("seat", me);
                row.addProperty("contextEnabled", enabled); row.addProperty("investmentLogit", net.buildLogits[fixture.investment()]);
                row.addProperty("alternativeLogit", net.buildLogits[fixture.alternative()]);
                row.addProperty("difference", (double) net.buildLogits[fixture.investment()] - net.buildLogits[fixture.alternative()]);
                row.addProperty("greedyCell", argmax(net.buildLogits, obs.buildMask)); rows.add(row);
            }
        }
        return result;
    }

    /** Records all four conditional inputs, including singleton STOP stages skipped by production for speed. */
    static Trace trace(GameState state, int me, SpatialV9Net net) {
        SpatialObservation observation = new SpatialObservation(state); observation.prepare(state, me);
        Trace trace = new Trace(); int[] prefix = new int[3]; int count = 0, paint = GameState.PAINT_PER_TURN;
        boolean stopped = false;
        for (int stage = 0; stage < 4; stage++) {
            observation.prefix(prefix, count, paint, stage, stopped);
            net.forward(observation);
            int selected = argmax(stage == 3 ? net.disruptLogits : net.buildLogits,
                    stage == 3 ? observation.disruptMask : observation.buildMask);
            JsonObject row = new JsonObject(); row.addProperty("stage", stage);
            row.add("observation", observationJson(observation));
            JsonObject java = new JsonObject(); java.add("buildLogits", JSON.toJsonTree(net.buildLogits));
            java.add("disruptLogits", JSON.toJsonTree(net.disruptLogits)); java.addProperty("value", net.value);
            java.addProperty("selected", selected); row.add("java", java); trace.rows.add(row);
            if (stage < 3) {
                if (selected == SpatialObservation.STOP) stopped = true;
                else {
                    int c = observation.nativeCell(selected); prefix[count++] = selected;
                    paint -= state.placeCost(c); trace.actions.add(Action.place(state.x(c), state.y(c)));
                }
            } else if (selected != SpatialObservation.STOP) trace.actions.add(Action.disrupt(selected));
        }
        if (trace.actions.isEmpty()) trace.actions.add(Action.waitAction());
        return trace;
    }

    private static List<Measurement> measure(List<Position> positions, String payload, Params params, long budgetMs) {
        List<Measurement> result = new ArrayList<>(); long lastSeed = Long.MIN_VALUE;
        SpatialV9Bot[] bots = null;
        for (Position position : positions) {
            if (position.seed != lastSeed) {
                lastSeed = position.seed;
                bots = new SpatialV9Bot[]{new SpatialV9Bot(params, new SpatialV9Net(payload, true), System::nanoTime),
                        new SpatialV9Bot(params, new SpatialV9Net(payload, true), System::nanoTime)};
                for (int me = 0; me < 2; me++) { bots[me].init(position.state, me); bots[me].warmup(position.state, me, WARMUP_MS); }
            }
            Pathing pathing = new Pathing(position.state.size);
            for (int me = 0; me < 2; me++) {
                String before = stateIdentity(position.state);
                long started = System.nanoTime(); List<Action> actions = bots[me].play(position.state, me, budgetMs);
                double ms = millis(started);
                if (!before.equals(stateIdentity(position.state))) throw new IllegalStateException("Candidate mutated saved state");
                Rules.PlayerMoves moves = validate(position.state, me, actions, pathing);
                result.add(new Measurement(ms, Action.format(actions), bots[me].lastInterrupted,
                        bots[me].lastCompletedDecisions, moves.cost, moves.count, moves.error, new ArrayList<>(moves.skipped)));
            }
        }
        return result;
    }

    private static JsonObject observationJson(SpatialObservation o) {
        JsonObject json = new JsonObject(); json.add("planes", JSON.toJsonTree(o.planes));
        json.add("towns", JSON.toJsonTree(o.towns)); json.add("scalars", JSON.toJsonTree(o.scalars));
        json.add("townCells", JSON.toJsonTree(o.townCells)); json.add("regionIds", JSON.toJsonTree(o.regionIds));
        json.add("buildMask", JSON.toJsonTree(o.buildMask)); json.add("disruptMask", JSON.toJsonTree(o.disruptMask)); return json;
    }
    private static int argmax(float[] logits, boolean[] mask) {
        int best = -1;
        for (int i = 0; i < logits.length; i++) if (mask[i] && (best < 0 || logits[i] > logits[best])) best = i;
        if (best < 0) throw new IllegalStateException("Empty mask"); return best;
    }
    private static Rules.PlayerMoves validate(GameState state, int me, List<Action> actions, Pathing pathing) {
        Rules.TurnResult result = new Rules.TurnResult();
        Rules.expand(state, me, actions, pathing, result.moves[me], result); return result.moves[me];
    }
    private static Map<String, Object> timing(List<Double> values) {
        Map<String, Object> result = new LinkedHashMap<>(); result.put("samples", values.size());
        if (values.isEmpty()) return result;
        List<Double> sorted = new ArrayList<>(values); sorted.sort(Comparator.naturalOrder());
        double total = 0; for (double value : values) total += value;
        result.put("min", sorted.get(0)); result.put("mean", total / sorted.size());
        result.put("p95", sorted.get((int) Math.ceil(.95 * sorted.size()) - 1)); result.put("max", sorted.get(sorted.size() - 1)); return result;
    }
    private static String stateIdentity(GameState state) { return state.turn + "\n" + Protocol.turnBlock(state, 0); }
    private static double millis(long start) { return (System.nanoTime() - start) / 1_000_000.0; }
    private static String sha256(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static void writeNew(Path path, Object value) throws Exception {
        Files.writeString(path, JSON.toJson(value), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }
    static final class Capture {
        final List<Position> positions = new ArrayList<>();
        final List<Map<String, Object>> trajectory = new ArrayList<>();
        final Map<String, Object> map = new LinkedHashMap<>();
    }
    static final class Position {
        final long seed; final String mapHash; final GameState state;
        Position(long seed, String mapHash, GameState state) { this.seed = seed; this.mapHash = mapHash; this.state = state; }
        Map<String, Object> snapshot() {
            Map<String, Object> row = new LinkedHashMap<>(); row.put("seed", seed); row.put("mapHash", mapHash);
            row.put("turn", state.turn); row.put("maxTurns", state.maxTurns);
            row.put("turnBlockP0", Protocol.turnBlock(state, 0)); row.put("turnBlockP1", Protocol.turnBlock(state, 1));
            row.put("score", state.score); row.put("owner", state.owner); row.put("instability", state.instability);
            row.put("inked", state.inked); row.put("regionInstability", state.regionInstability); row.put("regionInked", state.regionInked);
            int tracks = 0, connections = 0; for (int c = 0; c < state.size; c++) if (state.hasTrack(c)) tracks++;
            for (int pair = 0; pair < state.pairCount; pair++) if (state.pairActive(pair)) connections++;
            row.put("trackCells", tracks); row.put("activeConnections", connections); return row;
        }
    }
    static final class Trace {
        final List<JsonObject> rows = new ArrayList<>(); final List<Action> actions = new ArrayList<>();
    }
    private record Measurement(double ms, String actions, boolean interrupted, int completedDecisions,
                               int paint, int placements, String error, List<String> skipped) {}
}
