package btk.tools.spatial;

import btk.bot.Bot;
import btk.bot.BotFactory;
import btk.bot.Params;
import btk.bot.neural.SpatialBot;
import btk.bot.neural.SpatialNet;
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
 * New v2 diagnostic: fixed v09 trajectories are captured once before either candidate is queried.
 * Candidate ample and nominal timing run in separate serial phases on the identical saved states.
 * This tool neither trains nor evaluates a win-rate gate, and never replaces missing late slots.
 */
public final class SpatialLatePanel {
    private static final Gson JSON = new GsonBuilder().serializeNulls().create();
    private static final String SPEC = "spatial-ppo-v2";
    private static final long FIRST_SEED = 10_100_000L;
    private static final int MAPS = 4;
    private static final int[] TURNS = {0, 25, 50, 75, 99};
    private static final long REFERENCE_MS = 60_000, AMPLE_MS = 60_000, NOMINAL_MS = 36, WARMUP_MS = 100;

    private SpatialLatePanel() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException(
                "Usage: SpatialLatePanel initial-weights.base64 final-weights.base64 fresh-output-directory");
        String[] labels = {"initial", "final"};
        String[] payloads = {Files.readString(Path.of(args[0])).trim(), Files.readString(Path.of(args[1])).trim()};
        for (String payload : payloads) new SpatialNet(payload); // Validate before creating any output.
        Path output = Path.of(args[2]).toAbsolutePath();
        Files.createDirectories(output.getParent());
        Files.createDirectory(output); // Write-once complete diagnostic, never an accidental rerun.
        Params candidateParams = BotFactory.parameters(SPEC);
        List<Position> positions = new ArrayList<>();
        List<Map<String, Object>> maps = new ArrayList<>(), trajectory = new ArrayList<>();
        long started = System.nanoTime();
        for (int map = 0; map < MAPS; map++) {
            long seed = FIRST_SEED + map;
            GameState state = MapGenerator.generate(seed);
            Bot[] reference = {BotFactory.create("v09"), BotFactory.create("v09")};
            for (int me = 0; me < 2; me++) reference[me].init(state, me);
            Capture captured = capture(seed, state, reference, TURNS, REFERENCE_MS);
            positions.addAll(captured.positions);
            maps.add(captured.map);
            trajectory.addAll(captured.trajectory);
        }
        Map<String, Object> panel = new LinkedHashMap<>();
        panel.put("schemaVersion", 1); panel.put("experiment", SPEC);
        panel.put("firstSeed", FIRST_SEED); panel.put("plannedMaps", MAPS); panel.put("requestedTurns", TURNS);
        panel.put("reference", "v09/v09"); panel.put("referenceParams", BotFactory.configuration("v09"));
        panel.put("referenceBudgetMsPerDecision", REFERENCE_MS);
        panel.put("referenceWarmup", "none: bounded full-search completion checked per actual decision");
        panel.put("method", "Each v09 seat receives a separate copy of one unchanged pre-turn state. "
                + "Both plans validate before one shared-core resolve. Stop on actual terminal or once turn99 is captured. "
                + "No replacement seeds or forced post-terminal states. All candidate evaluation occurs after these trajectories are fixed.");
        panel.put("maps", maps); panel.put("trajectory", trajectory);
        List<Map<String, Object>> snapshots = new ArrayList<>();
        for (Position position : positions) snapshots.add(position.snapshot());
        panel.put("positions", snapshots); panel.put("positionsCount", positions.size());
        panel.put("trajectoryDurationMs", millis(started));
        writeNew(output.resolve("panel.json"), panel);

        @SuppressWarnings("unchecked") List<String>[] expected = new List[]{new ArrayList<>(), new ArrayList<>()};
        for (int model = 0; model < labels.length; model++) {
            SpatialNet net = new SpatialNet(payloads[model]);
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

    /** Exposed within this package for synthetic terminal/collision tests, not an alternate game engine. */
    static Capture capture(long seed, GameState state, Bot[] reference, int[] requested, long budgetMs) throws Exception {
        if (reference.length != 2 || requested.length == 0 || budgetMs <= 0) throw new IllegalArgumentException("invalid trajectory setup");
        for (int i = 0; i < requested.length; i++) if (requested[i] < 0 || i > 0 && requested[i] <= requested[i - 1])
            throw new IllegalArgumentException("requested turns must increase");
        String init = Protocol.initBlock(state, 0), hash = sha256(init.getBytes(StandardCharsets.UTF_8));
        Pathing pathing = new Pathing(state.size); Rules.updateConnections(state, pathing);
        Capture capture = new Capture();
        int next = 0;
        while (!Rules.gameOver(state, pathing)) {
            if (next < requested.length && state.turn == requested[next]) {
                capture.positions.add(new Position(seed, hash, state.copy())); next++;
            }
            if (next == requested.length) break;
            String before = stateIdentity(state);
            List<List<Action>> plans = new ArrayList<>(2);
            Map<String, Object> turn = new LinkedHashMap<>();
            turn.put("seed", seed); turn.put("mapHash", hash); turn.put("turn", state.turn);
            List<Map<String, Object>> seats = new ArrayList<>();
            for (int me = 0; me < 2; me++) {
                long started = System.nanoTime();
                List<Action> plan = reference[me].play(state.copy(), me, budgetMs);
                double ms = millis(started);
                Rules.PlayerMoves validated = validate(state, me, plan, pathing);
                if (validated.error != null || !validated.skipped.isEmpty())
                    throw new IllegalStateException("Invalid reference plan at " + seed + "/" + state.turn + "/" + me);
                boolean opening = reference[me] instanceof RolloutBotV09 v09 && v09.selectedOpening;
                boolean complete = !(reference[me] instanceof RolloutBotV09 v09) || v09.ordinarySearchComplete || opening;
                if (!complete || ms >= budgetMs) throw new IllegalStateException("Reference search did not complete at " + seed + "/" + state.turn + "/" + me);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("me", me); row.put("budgetMs", budgetMs); row.put("elapsedMs", ms);
                row.put("actions", Action.format(plan)); row.put("selectedOpening", opening);
                row.put("ordinarySearchCompleteOrOpening", complete); seats.add(row); plans.add(plan);
            }
            if (!before.equals(stateIdentity(state))) throw new IllegalStateException("Reference changed pre-turn state");
            turn.put("seats", seats);
            Rules.TurnResult outcome = Rules.resolve(state, plans.get(0), plans.get(1), pathing);
            turn.put("gained", outcome.gained.clone()); turn.put("scoreAfter", state.score.clone());
            capture.trajectory.add(turn);
        }
        boolean terminal = Rules.gameOver(state, pathing);
        List<Map<String, Object>> missing = new ArrayList<>();
        for (int i = next; i < requested.length; i++) {
            Map<String, Object> row = new LinkedHashMap<>(); row.put("turn", requested[i]);
            row.put("reason", terminal ? "actual terminal reached before this playable slot" : "not reached");
            row.put("endTurn", state.turn); missing.add(row);
        }
        List<Integer> reached = new ArrayList<>(); for (Position position : capture.positions) reached.add(position.state.turn);
        capture.map.put("seed", seed); capture.map.put("mapHash", hash); capture.map.put("initBlockP0", init);
        capture.map.put("width", state.width); capture.map.put("height", state.height); capture.map.put("townCount", state.townCount);
        capture.map.put("requestedTurns", requested.clone()); capture.map.put("capturedTurns", reached);
        capture.map.put("skippedSlots", missing); capture.map.put("endTurn", state.turn);
        capture.map.put("terminal", terminal); capture.map.put("scoreAtStop", state.score.clone());
        capture.map.put("stopReason", terminal ? "actual terminal" : "last requested slot captured; no further turn played");
        return capture;
    }

    /** Records all four conditional inputs, including singleton STOP stages skipped by production for speed. */
    static Trace trace(GameState state, int me, SpatialNet net) {
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
        SpatialBot[] bots = null;
        for (Position position : positions) {
            if (position.seed != lastSeed) {
                lastSeed = position.seed;
                bots = new SpatialBot[]{new SpatialBot(params, new SpatialNet(payload), System::nanoTime),
                        new SpatialBot(params, new SpatialNet(payload), System::nanoTime)};
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
