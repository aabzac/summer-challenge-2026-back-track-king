package btk.tools.spatial;

import btk.bot.BotFactory;
import btk.bot.Params;
import btk.bot.neural.SpatialBot;
import btk.bot.neural.SpatialNet;
import btk.bot.neural.SpatialV4Weights;
import btk.bot.neural.SpatialV10Weights;
import btk.tools.spatial.SpatialV9Panel.Position;
import btk.tools.spatial.SpatialLatePanel.Trace;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Protocol;
import btk.core.Rules;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
 * V10 diagnostic: replay immutable saved v4 reference trajectories, then query the unchanged V4 architecture with initial/final weights.
 * Candidate ample and nominal timing run in separate serial phases on the identical saved states.
 * This tool neither trains nor evaluates a win-rate gate, and never replaces missing late slots.
 */
public final class SpatialV10Panel {
    private static final Gson JSON = new GsonBuilder().serializeNulls().create();
    private static final String SPEC = "spatial-ppo-v10";
    private static final long FIRST_SEED = 10_100_000L;
    private static final int MAPS = 4;
    private static final int[] TURNS = {0, 25, 50, 75, 99};
    private static final long REFERENCE_MS = 60_000, AMPLE_MS = 60_000, NOMINAL_MS = 36, WARMUP_MS = 100;

    private SpatialV10Panel() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException(
                "Usage: SpatialV10Panel initial-weights.base64 final-weights.base64 fresh-output-directory saved-v4-panel.json");
        String[] labels = {"initial", "final"};
        String[] payloads = {Files.readString(Path.of(args[0])).trim(), Files.readString(Path.of(args[1])).trim()};
        for (String payload : payloads) new SpatialNet(payload); // Validate before creating any output.
        Path output = Path.of(args[2]).toAbsolutePath();
        Files.createDirectories(output.getParent());
        Files.createDirectory(output); // Write-once complete diagnostic, never an accidental rerun.
        require(payloads[0].equals(SpatialV4Weights.DATA), "Initial panel payload must be frozen V4");
        require(payloads[1].equals(SpatialV10Weights.DATA), "Final panel payload must match generated V10 candidate");
        Params candidateParams = BotFactory.parameters(SPEC);
        List<Position> positions = new ArrayList<>();
        List<Map<String, Object>> maps = new ArrayList<>(), trajectory = new ArrayList<>();
        long started = System.nanoTime();
        byte[] savedBytes = Files.readAllBytes(Path.of(args[3]));
        JsonObject saved = JsonParser.parseString(new String(savedBytes, StandardCharsets.UTF_8)).getAsJsonObject();
        load(saved, positions, maps, trajectory);
        Map<String, Object> panel = new LinkedHashMap<>();
        panel.put("schemaVersion", 1); panel.put("experiment", SPEC);
        panel.put("firstSeed", FIRST_SEED); panel.put("plannedMaps", MAPS); panel.put("requestedTurns", TURNS);
        panel.put("reference", "v09/v09"); panel.put("referenceParams", BotFactory.configuration("v09"));
        panel.put("referenceBudgetMsPerDecision", REFERENCE_MS);
        panel.put("referenceWarmup", "historical; no new reference bot calls");
        panel.put("savedPanelSha256", sha256(savedBytes));
        panel.put("method", "Replay existing frozen v4 late-panel reference actions through btk.core and match every saved snapshot. No new games/decisions; preserve actual terminal omissions. Then trace and measure both v10 experiment payloads on identical states.");
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
                    Trace trace = SpatialLatePanel.trace(position.state, me, net);
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

    /** Reuse the frozen core replayer and enforce this experiment's exact captured-slot schedule. */
    static void load(JsonObject saved, List<Position> positions, List<Map<String, Object>> maps,
                     List<Map<String, Object>> trajectory) throws Exception {
        require(positions.isEmpty() && maps.isEmpty() && trajectory.isEmpty(), "Expected empty output buffers");
        SpatialV9Panel.loadSaved(saved, positions, maps, trajectory);
        require(positions.size() == 16, "Expected exactly 16 saved live positions; no replacements");
        int at = 0;
        for (int mapIndex = 0; mapIndex < MAPS; mapIndex++) {
            JsonObject map = saved.getAsJsonArray("maps").get(mapIndex).getAsJsonObject();
            int end = map.get("endTurn").getAsInt(); boolean terminal = map.get("terminal").getAsBoolean();
            for (int turn : TURNS) {
                if (turn > end || turn == end && terminal) continue;
                require(at < positions.size(), "Missing scheduled live position");
                Position position = positions.get(at++);
                require(position.seed == FIRST_SEED + mapIndex && position.state.turn == turn,
                        "Saved positions differ from fixed live map/turn slots");
            }
        }
        require(at == positions.size(), "Extra saved position outside live schedule");
    }

    private static List<Measurement> measure(List<Position> positions, String payload, Params params, long budgetMs) {
        List<Measurement> result = new ArrayList<>(); long lastSeed = Long.MIN_VALUE;
        SpatialBot[] bots = null;
        for (Position position : positions) {
            if (position.seed != lastSeed) {
                lastSeed = position.seed;
                bots = new SpatialBot[]{new SpatialBot(params, new SpatialNet(payload), System::nanoTime),
                        new SpatialBot(params, new SpatialNet(payload), System::nanoTime)};
                for (int me = 0; me < 2; me++) {
                    String before = stateIdentity(position.state);
                    bots[me].init(position.state, me); bots[me].warmup(position.state, me, WARMUP_MS);
                    require(before.equals(stateIdentity(position.state)), "Warmup mutated saved state");
                }
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
    private static String stateIdentity(GameState state) { return SpatialLabelAudit.semanticHash(state); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static double millis(long start) { return (System.nanoTime() - start) / 1_000_000.0; }
    private static String sha256(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static void writeNew(Path path, Object value) throws Exception {
        Files.writeString(path, JSON.toJson(value), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }
    private record Measurement(double ms, String actions, boolean interrupted, int completedDecisions,
                               int paint, int placements, String error, List<String> skipped) {}
}
