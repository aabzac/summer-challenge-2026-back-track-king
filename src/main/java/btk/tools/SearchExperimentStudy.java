package btk.tools;

import btk.bot.Bot;
import btk.bot.BotFactory;
import btk.core.*;
import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Fixed public-state mechanism/cost diagnostics and core-derived cached-boss map exclusions. */
public final class SearchExperimentStudy {
    private static final String[] SPECS = {"v07?NOISE=0", "current?NOISE=0,USE_PROTECTED_OPENING=0",
            "current?NOISE=0,USE_ROLLOUT_RHEA=1,USE_PROTECTED_OPENING=0", "current?NOISE=0,USE_PROTECTED_OPENING=1"};
    private static final String[] FIELDS = {"routeQueries", "completedComparisons", "simulatedTurns",
            "ordinarySearchComplete", "ordinaryComparisons", "networkPhaseComplete",
            "rheaAttempts", "rheaComparisons", "rheaImprovements", "rheaDistinctRoots", "rheaSearchStarted", "selectedRhea",
            "openingPairs", "openingEligible", "openingPaint", "selectedOpening"};

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("cache|study input output");
        Path input = Path.of(args[1]), output = Path.of(args[2]);
        if (Files.exists(output)) throw new IllegalStateException("Preserve existing report: " + output);
        Object result = switch (args[0]) {
            case "cache" -> cache(input);
            case "study" -> study(input);
            default -> throw new IllegalArgumentException("Expected cache or study");
        };
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, ReplyStudy.JSON.toJson(result), StandardOpenOption.CREATE_NEW);
        System.out.println("Saved " + output);
    }

    private static Map<String, Object> cache(Path source) throws Exception {
        JsonObject plan = JsonParser.parseString(Files.readString(source)).getAsJsonObject();
        List<Object> maps = new ArrayList<>(); Set<String> distinct = new HashSet<>();
        Map<String, Integer> gamesPerMap = new TreeMap<>();
        for (JsonElement element : plan.getAsJsonArray("slots")) {
            JsonObject slot = element.getAsJsonObject();
            if (!slot.get("arm").getAsString().equals("control")) continue;
            Path recordPath = source.getParent().resolve(String.format("%04d", slot.get("slot").getAsInt()))
                    .resolve("result.json");
            JsonObject record = JsonParser.parseString(Files.readString(recordPath)).getAsJsonObject();
            JsonObject capture = record.getAsJsonObject("capture");
            Path raw = Path.of(capture.get("path").getAsString());
            if (!sha(raw).equals(capture.get("rawSha256").getAsString()))
                throw new IllegalStateException("Changed canonical replay " + raw);
            CgReplay replay = CgReplay.load(raw);
            if (CgValidate.validate(replay, false).firstDivergenceTurn >= 0)
                throw new IllegalStateException("Core divergence " + raw);
            String mapHash = ReplyStudy.hash(Protocol.initBlock(replay.toState(), 0).getBytes(StandardCharsets.UTF_8));
            if (!distinct.add(mapHash)) throw new IllegalStateException("Repeated cached map");
            gamesPerMap.put(mapHash, 1);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("mapHash", mapHash); item.put("seed", replay.mapSeed().orElseThrow()); item.put("gameId", replay.gameId);
            item.put("rawPath", raw.toString()); item.put("rawSha256", sha(raw));
            item.put("resultPath", recordPath.toString()); item.put("resultSha256", sha(recordPath));
            maps.add(item);
        }
        if (maps.size() != 40) throw new IllegalStateException("Expected exactly 40 control maps");
        return Map.of("sourcePlanSha256", sha(source), "sourcePlan", source.toString(),
                "hashScheme", "sha256-init-block-player-0", "maps", maps, "gamesPerMap", gamesPerMap,
                "method", "All 40 canonical cached-control maps validated by CgValidate; SHA256 of core Protocol.initBlock(state,0), exactly as BenchmarkRunner.");
    }

    private static Map<String, Object> study(Path capture) throws Exception {
        var positions = new ArrayList<>(ReplyStudy.loadPositions(capture));
        Path openingSource = Path.of("docs/evidence/boss-v07-summary.json");
        JsonArray games = JsonParser.parseString(Files.readString(openingSource)).getAsJsonObject().getAsJsonArray("games");
        List<Object> openingInputs = new ArrayList<>();
        // First six fixed historical diagnostic games, never the cached confirmation boss maps.
        for (int index = 0; index < 6; index++) {
            JsonObject game = games.get(index).getAsJsonObject(); long id = game.get("gameId").getAsLong();
            Path raw = ReplayPaths.resolve(Path.of("replays/cg/bot/" + id + ".json"));
            if (!sha(raw).equals(game.get("rawSha256").getAsString())) throw new IllegalStateException("Changed opening input");
            CgReplay replay = CgReplay.load(raw);
            if (CgValidate.validate(replay, false).firstDivergenceTurn >= 0) throw new IllegalStateException("Replay divergence");
            openingInputs.add(Map.of("gameId", id, "path", raw.toString(), "sha256", sha(raw)));
            GameState s = replay.toState(); Pathing pathing = new Pathing(s.size); Rules.updateConnections(s, pathing);
            for (int turn = 0; turn < 4 && turn < replay.turns(); turn++) {
                for (int me = 0; me < 2; me++) positions.add(new ReplyStudy.Position("cg:" + id,
                        replay.mapSeed().orElseThrow(), me, "recorded-public-state", s.copy(), "", ""));
                Rules.resolve(s, CgPlayerStudy.effectiveActions(replay, turn, 0),
                        CgPlayerStudy.effectiveActions(replay, turn, 1), pathing);
            }
        }
        // Fixed serial warmup; no outcomes are used to tune or select the candidate.
        for (int i = 0; i < Math.min(8, positions.size()); i++) {
            var sample = positions.get(i);
            for (String spec : SPECS) {
                Bot bot = BotFactory.create(spec); bot.init(sample.state(), sample.me()); bot.play(sample.state(), sample.me(), 36);
            }
        }
        List<Object> rows = new ArrayList<>(); Map<String, Object> summaries = new LinkedHashMap<>();
        Map<String, long[]> counts = new LinkedHashMap<>(); Map<String, double[]> times = new LinkedHashMap<>();
        int parity = 0;
        for (int i = 0; i < positions.size(); i++) {
            var sample = positions.get(i); GameState s = sample.state(); String before = ReplyStudy.stateHash(s);
            List<Map<String, Object>> ample = new ArrayList<>(), nominal = new ArrayList<>();
            for (String spec : SPECS) ample.add(decision(spec, s, sample.me(), 60_000, before));
            if (!ample.get(0).get("actions").equals(ample.get(1).get("actions")))
                throw new IllegalStateException("Disabled current differs from frozen v07 at " + sample.game() + ":" + s.turn);
            parity++;
            for (int order = 0; order < 3; order++) {
                int specIndex = 1 + (order + i) % 3; String spec = SPECS[specIndex];
                var chosen = decision(spec, s, sample.me(), s.turn == 0 ? 486 : 36, before); nominal.add(chosen);
                long[] n = counts.computeIfAbsent(spec, key -> new long[4]); n[0]++;
                if (!ample.get(specIndex).get("actions").equals(ample.get(1).get("actions"))) n[1]++;
                Map<?, ?> telemetry = (Map<?, ?>) chosen.get("telemetry");
                if (Boolean.TRUE.equals(telemetry.get("selectedRhea")) || Boolean.TRUE.equals(telemetry.get("selectedOpening"))) n[2]++;
                double elapsed = (Double) chosen.get("elapsedMs");
                if (elapsed > (s.turn == 0 ? 486 : 36)) n[3]++;
                double[] t = times.computeIfAbsent(spec, key -> new double[2]); t[0] += elapsed; t[1] = Math.max(t[1], elapsed);
            }
            rows.add(Map.of("game", sample.game(), "turn", s.turn, "me", sample.me(), "stateSha256", before,
                    "ample", ample, "nominal", nominal));
        }
        for (String spec : counts.keySet()) {
            long[] n = counts.get(spec); double[] t = times.get(spec);
            summaries.put(spec, Map.of("states", n[0], "ampleActionChangesVsCurrent", n[1],
                    "nominalExperimentalSelections", n[2], "nominalSearchOverruns", n[3],
                    "meanNominalMs", t[0] / n[0], "maxNominalMs", t[1]));
        }
        Map<String, Object> configurations = new LinkedHashMap<>();
        for (String spec : SPECS) configurations.put(spec, BotFactory.configuration(spec));
        System.out.println(ReplyStudy.JSON.toJson(summaries));
        return Map.of("stateCount", positions.size(), "disabledParity", parity, "summary", summaries,
                "params", configurations, "states", rows, "openingInputs", openingInputs,
                "captureManifestSha256", sha(capture.resolve("manifest.json")),
                "method", "All 110 saved reply-study positions plus t0,1,2,3 both seats from first six historical boss-v07 study games; fresh bots, NOISE=0, exact legality and input isolation. Ample 60000ms for frozen/default parity and bounded mechanism; nominal486/36ms rotating order after eight-state warmup. No future actions supplied. This is mechanism/host-cost evidence, not strength or process deadlines.");
    }

    private static Map<String, Object> decision(String spec, GameState s, int me, long budget, String before) throws Exception {
        Bot bot = BotFactory.create(spec); bot.init(s, me);
        long start = System.nanoTime(); var actions = bot.play(s, me, budget);
        double elapsed = (System.nanoTime() - start) / 1e6;
        var tr = new Rules.TurnResult(); Rules.expand(s, me, actions, new Pathing(s.size), tr.moves[me], tr);
        if (tr.hasError(me) || !tr.moves[me].skipped.isEmpty()) throw new IllegalStateException("Illegal output: " + spec);
        if (!ReplyStudy.stateHash(s).equals(before)) throw new IllegalStateException("Input mutation: " + spec);
        Map<String, Object> telemetry = new LinkedHashMap<>();
        for (String field : FIELDS) try { telemetry.put(field, bot.getClass().getField(field).get(bot)); }
        catch (NoSuchFieldException ignored) { /* frozen policy lacks experiment counters */ }
        return Map.of("spec", spec, "budgetMs", budget, "elapsedMs", elapsed,
                "actions", Action.format(actions), "telemetry", telemetry);
    }

    private static String sha(Path path) throws Exception { return ReplyStudy.hash(Files.readAllBytes(path)); }
}
