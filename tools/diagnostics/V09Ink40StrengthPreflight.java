package btk.tools;

import btk.bot.BotFactory;
import btk.bot.Params;
import btk.core.GameState;
import btk.core.Protocol;
import btk.referee.MapGenerator;
import btk.runner.TimingProfile;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Prospective shared-core map/configuration audit. Never creates bots or plays games. */
public final class V09Ink40StrengthPreflight {
    private static final String EXPERIMENT = "v09-ink40-strength-v1";
    private static final String CANDIDATE =
            "current?NOISE=0.1,USE_BATCHED_REFRESH=true,TURN_BUDGET_MS=40,USE_INCREMENTAL_INK_REFRESH=true";
    private static final String CONTROL = "current?NOISE=0.1,USE_BATCHED_REFRESH=true,TURN_BUDGET_MS=40";
    private static final String FLAG = "USE_INCREMENTAL_INK_REFRESH";
    private static final int[] COUNTS = {150, 100, 250};
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalArgumentException(message);
    }

    private static JsonObject read(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static String sha(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private static Map<String, Object> pin(Path path) throws Exception {
        return Map.of("path", path.toAbsolutePath().normalize().toString(),
                "sha256", sha(Files.readAllBytes(path)));
    }

    public static void main(String[] args) throws Exception {
        require(args.length == 5, "exclusions.json pool1.json pool2.json pool3.json NEW_OUTPUT.json");
        Path output = Path.of(args[4]);
        require(!Files.exists(output), "Preserve existing preflight output");

        Map<String, String> candidate = BotFactory.configuration(CANDIDATE);
        Map<String, String> control = BotFactory.configuration(CONTROL);
        Map<String, String> withoutFlag = new TreeMap<>(candidate);
        require("true".equals(withoutFlag.remove(FLAG)) && !control.containsKey(FLAG)
                && withoutFlag.equals(control), "Configurations differ beyond incremental ink refresh");
        Params candidateParams = BotFactory.parameters(CANDIDATE);
        Params controlParams = BotFactory.parameters(CONTROL);
        for (Params params : List.of(candidateParams, controlParams)) {
            require(params.TURN_BUDGET_MS == 40 && params.NOISE == 0.1
                    && params.USE_BATCHED_REFRESH && params.USE_ROLLOUT_POLICY
                    && !params.USE_ROLLOUT_MISSED_DEADLINE && !params.USE_NEURAL_TARGET_PROPOSAL
                    && !params.USE_HEURISTIC_POLICY, "Expected unchanged native batched40 policy");
            // The retained pre-fast-path runtime may not declare this later flag.
            try {
                require(!Params.class.getField("USE_FAST_CHEAPEST_PATH").getBoolean(params),
                        "Fast cheapest path must remain OFF");
            } catch (NoSuchFieldException absentInHistoricalRuntime) {
                // This optimization cannot be enabled when the retained runtime predates it.
            }
        }
        require(candidateParams.USE_INCREMENTAL_INK_REFRESH && !controlParams.USE_INCREMENTAL_INK_REFRESH,
                "Incremental refresh must differ between the two arms");
        // Include all public fields, even fields omitted from the historical configuration schema.
        for (var field : Params.class.getFields()) {
            if (!field.getName().equals(FLAG)) {
                require(field.get(candidateParams).equals(field.get(controlParams)),
                        "Unreported parameter difference: " + field.getName());
            }
        }
        TimingProfile timing = TimingProfile.CG;
        long laterA = timing.turnBudget(CANDIDATE), laterB = timing.turnBudget(CONTROL);
        require(timing.firstSearchMs == 486 && timing.turnSearchMs == 36 && laterA == 40 && laterB == 40,
                "Expected actual nominal search budgets 486/40 in both arms");

        Path exclusionPath = Path.of(args[0]);
        JsonObject exclusion = read(exclusionPath).getAsJsonObject("gamesPerMap");
        require(exclusion != null && !exclusion.isEmpty(), "Nonempty prior-map exclusions required");
        Set<String> excluded = new HashSet<>();
        for (var entry : exclusion.entrySet()) {
            require(entry.getKey().matches("[0-9a-f]{64}")
                    && entry.getValue().getAsBigDecimal().intValueExact() > 0,
                    "Invalid prior-map identity/count");
            excluded.add(entry.getKey());
        }
        Set<String> seen = new HashSet<>(), ids = new HashSet<>();
        List<Map<String, Object>> pools = new ArrayList<>();
        List<Map<String, Object>> inputs = new ArrayList<>();
        inputs.add(pin(exclusionPath));
        for (int block = 0; block < COUNTS.length; block++) {
            Path poolPath = Path.of(args[block + 1]);
            JsonObject pool = read(poolPath);
            String id = pool.get("id").getAsString();
            int pairs = pool.get("pairs").getAsBigDecimal().intValueExact();
            long start = pool.get("seedStart").getAsBigDecimal().longValueExact();
            long end = Math.addExact(start, pairs - 1L);
            require(pool.get("schemaVersion").getAsBigDecimal().intValueExact() == 1
                    && "cg-sha1prng".equals(pool.get("generator").getAsString())
                    && "confirmation".equals(pool.get("purpose").getAsString())
                    && pairs == COUNTS[block] && !id.isBlank() && ids.add(id)
                    && !(start <= 0 && end >= 0), "Invalid fixed confirmation pool at block " + (block + 1));
            List<Map<String, Object>> maps = new ArrayList<>();
            for (int index = 0; index < pairs; index++) {
                long seed = Math.addExact(start, index);
                GameState state = MapGenerator.generate(seed);
                String hash = sha(Protocol.initBlock(state, 0).getBytes(StandardCharsets.UTF_8));
                require(!excluded.contains(hash) && seen.add(hash),
                        "Prospective map overlaps prior/other prospective map: " + id + "/" + seed);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("index", index);
                row.put("pair", index + 1);
                row.put("seed", seed);
                row.put("coreMapHash", hash);
                row.put("width", state.width);
                row.put("height", state.height);
                row.put("townCount", state.townCount);
                row.put("pairCount", state.pairCount);
                maps.add(row);
            }
            Map<String, Object> manifestPin = pin(poolPath);
            inputs.add(manifestPin);
            pools.add(Map.of("manifest", manifestPin, "id", id, "seedStart", start,
                    "pairs", pairs, "games", 2 * pairs, "maps", maps));
        }
        require(seen.size() == 500, "Expected all 500 prospective map identities");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("experiment", EXPERIMENT);
        result.put("complete", true);
        result.put("passed", true);
        result.put("newGames", 0);
        result.put("botCalls", 0);
        result.put("hashScheme", "sha256-utf8-Protocol.initBlock(initialState,0)");
        result.put("inputs", inputs);
        result.put("excludedMaps", excluded.size());
        result.put("distinctProspectiveMaps", seen.size());
        result.put("pools", pools);
        result.put("candidate", Map.of("spec", CANDIDATE, "params", candidate));
        result.put("control", Map.of("spec", CONTROL, "params", control));
        result.put("onlyParameterDifference", FLAG);
        result.put("timingProfile", timing.name());
        result.put("firstSearchBudgetMsA", timing.firstSearchMs);
        result.put("firstSearchBudgetMsB", timing.firstSearchMs);
        result.put("turnSearchBudgetMsA", laterA);
        result.put("turnSearchBudgetMsB", laterB);
        result.put("javaVersion", System.getProperty("java.version"));
        result.put("javaVendor", System.getProperty("java.vendor"));
        result.put("localOnly", true);
        result.put("promotion", false);
        Files.writeString(output, JSON.toJson(result) + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW);
        System.out.println("PASS: 500 prospective maps distinct from " + excluded.size()
                + " exclusions; only incremental-refresh flag differs; 486/40 ms both arms; zero games/bot calls.");
    }
}
