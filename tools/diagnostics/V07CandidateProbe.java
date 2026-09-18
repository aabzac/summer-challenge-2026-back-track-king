package btk.tools;

import btk.bot.Bot;
import btk.bot.BotFactory;
import btk.core.*;
import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Fixed public-state mechanism audit, not a strength test or a replay counterfactual rollout. */
public final class V07CandidateProbe {
    static final Gson GSON = new Gson();
    static final String[] SPECS = {"v07?NOISE=0", "current?NOISE=0",
        "current?NOISE=0,USE_ROLLOUT_ROUTE_DEPENDENCIES=1",
        "current?NOISE=0,USE_ROLLOUT_NETWORK_CHORDS=1"};
    static final int[] TURNS = {0, 5, 10, 20, 30, 40, 60, 80};
    static final String[] FIELDS = {"routeQueries", "completedComparisons", "simulatedTurns",
        "selectedRoute", "selectedTarget", "selectedValue", "ordinarySearchComplete",
        "ordinaryComparisons", "ordinarySelectedValue", "networkEligible", "networkEvaluated",
        "networkRoots", "networkComparisons", "selectedNetwork", "selectedNetworkCell",
        "chordEligible", "chordEvaluated", "chordRoots", "chordComparisons", "selectedChord",
        "networkPhaseComplete", "chordSearchStarted"};

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("summary.json output.json");
        Path output = Path.of(args[1]);
        if (Files.exists(output)) throw new IllegalStateException("Preserve " + output);
        JsonObject summary = JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
        List<Object> rows = new ArrayList<>();
        int parity = 0, dependencyChanged = 0, chordChanged = 0, chordSelected = 0;
        for (JsonElement entry : summary.getAsJsonArray("games")) {
            long id = entry.getAsJsonObject().get("gameId").getAsLong();
            Path raw = Path.of("replays/cg/bot/" + id + ".json");
            String rawHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(raw)));
            if (!rawHash.equals(entry.getAsJsonObject().get("rawSha256").getAsString()))
                throw new IllegalStateException("Changed replay " + id);
            CgReplay replay = CgReplay.load(raw);
            var valid = CgValidate.validate(replay, false);
            if (valid.firstDivergenceTurn >= 0) throw new IllegalStateException("Replay divergence " + id);
            GameState s = replay.toState();
            Pathing pathing = new Pathing(s.size);
            Rules.updateConnections(s, pathing);
            for (int t = 0; t < replay.turns(); t++) {
                boolean ordinary = Arrays.binarySearch(TURNS, t) >= 0;
                boolean special = id == 904554568 && t == 26 || id == 904555424 && t == 28;
                if (ordinary || special) {
                    int me = special ? 1 : 0;
                    String hash = stateHash(s, me);
                    List<Map<String, Object>> choices = new ArrayList<>();
                    for (String spec : SPECS) {
                        Bot bot = BotFactory.create(spec);
                        bot.init(s, me);
                        long start = System.nanoTime();
                        var actions = bot.play(s, me, 60_000);
                        double millis = (System.nanoTime() - start) / 1_000_000.0;
                        if (!stateHash(s, me).equals(hash)) throw new IllegalStateException("Input mutated " + spec);
                        var result = new Rules.TurnResult();
                        Rules.expand(s, me, actions, pathing, result.moves[me], result);
                        if (result.moves[me].error != null || !result.moves[me].skipped.isEmpty())
                            throw new IllegalStateException("Illegal action " + spec + " " + id + "@" + t);
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("spec", spec); row.put("action", Action.format(actions));
                        row.put("elapsedMsDiagnosticOnly", millis);
                        Map<String, Object> telemetry = new LinkedHashMap<>();
                        for (String name : FIELDS) {
                            try { telemetry.put(name, bot.getClass().getField(name).get(bot)); }
                            catch (NoSuchFieldException ignored) { /* frozen v07 has no new telemetry */ }
                        }
                        row.put("telemetry", telemetry); choices.add(row);
                    }
                    if (!choices.get(0).get("action").equals(choices.get(1).get("action")))
                        throw new IllegalStateException("Disabled v07 mismatch " + id + "@" + t);
                    parity++;
                    if (!choices.get(1).get("action").equals(choices.get(2).get("action"))) dependencyChanged++;
                    if (!choices.get(1).get("action").equals(choices.get(3).get("action"))) chordChanged++;
                    @SuppressWarnings("unchecked")
                    Map<String,Object> chordStats = (Map<String,Object>) choices.get(3).get("telemetry");
                    if (Boolean.TRUE.equals(chordStats.get("selectedChord"))) chordSelected++;
                    rows.add(Map.of("gameId", id, "turn", t, "player", me, "stateSha256", hash,
                        "exactBossChordCase", special, "choices", choices));
                }
                Rules.resolve(s, CgPlayerStudy.effectiveActions(replay, t, 0),
                    CgPlayerStudy.effectiveActions(replay, t, 1), pathing);
            }
        }
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("method", "20 exact-validated boss games, fixed t0,5,10,20,30,40,60,80 when present; "
            + "plus two fixed boss-side multi-cell examples. Public pre-turn states only, NOISE=0, fresh bot per state, "
            + "60000ms ample budget. No recorded opponent actions are supplied to candidate decisions. "
            + "Every action legal and input unchanged; disabled current must equal frozen v07. "
            + "Timings include host/JIT effects and are not nominal-budget or process-readiness evidence. "
            + "Action changes are mechanism observations, not strength results.");
        result.put("stateCount", rows.size()); result.put("disabledParity", parity);
        result.put("dependencyActionChanges", dependencyChanged); result.put("chordActionChanges", chordChanged);
        result.put("chordSelectedStates", chordSelected); result.put("states", rows);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(result));
        System.out.println("states=" + rows.size() + " parity=" + parity + " dependency changes="
            + dependencyChanged + " chord changes=" + chordChanged + " chord selected=" + chordSelected);
    }

    static String stateHash(GameState s, int me) throws Exception {
        String protocol = me + ":" + GSON.toJson(s);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(protocol.getBytes(StandardCharsets.UTF_8)));
    }
}
