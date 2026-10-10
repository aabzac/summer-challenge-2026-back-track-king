package btk.tools.spatial;

import btk.bot.Bot;
import btk.bot.BotFactory;
import btk.bot.Params;
import btk.bot.neural.SpatialNet;
import btk.bot.neural.SpatialObservation;
import btk.bot.neural.SpatialV5Bot;
import btk.bot.neural.SpatialV5Weights;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Protocol;
import btk.core.Rules;
import btk.core.SearchState;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One write-once v5 diagnostic on exact saved v4 trajectories, never newly played games. */
public final class SpatialV5Panel {
    private static final Gson JSON = new GsonBuilder().serializeNulls().setPrettyPrinting().create();
    private static final int[] TURNS = {0, 25, 50, 75, 99};
    private record Position(long seed, String mapHash, GameState state) {}
    private SpatialV5Panel() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("SpatialV5Panel v4-corpus fresh-output-directory");
        Path corpus = Path.of(args[0]), out = Path.of(args[1]);
        Files.createDirectories(out.toAbsolutePath().getParent()); Files.createDirectory(out);
        Map<String, Object> panel = new LinkedHashMap<>();
        panel.put("experiment", "spatial-ppo-v5"); panel.put("status", "running");
        panel.put("weightsSha256", sha(Base64.getDecoder().decode(SpatialV5Weights.DATA)));
        panel.put("params", BotFactory.configuration("spatial-ppo-v5"));
        panel.put("sourceManifestSha256", sha(Files.readAllBytes(corpus.resolve("manifest.json"))));
        panel.put("requestedTurns", TURNS); panel.put("requestedPositions", 20);
        panel.put("role", "reused training trajectories; mechanism/timing only, no strength evidence");
        write(out.resolve("intent.json"), panel);
        try {
            List<Map<String, Object>> maps = new ArrayList<>();
            List<Position> positions = load(corpus, maps);
            panel.put("maps", maps); panel.put("positions", positions.size());
            List<Map<String, Object>> rows = new ArrayList<>();
            List<String> base = measure(positions, "spatial-ppo-v4", "baseline", 60000, rows, null);
            List<String> off = measure(positions, "spatial-ppo-v5?USE_SPATIAL_V5_SEARCH=0", "off", 60000, rows, base);
            if (!base.equals(off)) throw new IllegalStateException("Search-off baseline mismatch");
            List<String> ample = measure(positions, "spatial-ppo-v5", "ample", 60000, rows, base);
            int nominalStart = rows.size();
            List<String> nominal = measure(positions, "spatial-ppo-v5", "nominal", 36, rows, base);
            for (int i = 0; i < nominal.size(); i++) if (Boolean.TRUE.equals(rows.get(nominalStart+i).get("complete"))
                    && !nominal.get(i).equals(ample.get(i))) throw new IllegalStateException("Complete nominal search differs from ample");
            panel.put("measurements", rows); panel.put("summary", summaries(rows));
            // Independent leaf parity is collected only after every timing phase.
            JsonObject parity = leafParity(positions, rows);
            write(out.resolve("leaf-parity.json"), parity);
            panel.put("status", "complete");
            write(out.resolve("panel.json"), panel);
            System.out.println("v5 saved panel: " + positions.size() + "/20 positions; " + rows.size() + " calls");
            System.out.println(JSON.toJson(panel.get("summary")));
        } catch (Exception failure) {
            panel.put("status", "failed"); panel.put("failure", failure.toString());
            write(out.resolve("failure.json"), panel); throw failure;
        }
    }

    private static List<Position> load(Path corpus, List<Map<String, Object>> maps) throws Exception {
        JsonObject manifest = JsonParser.parseString(Files.readString(corpus.resolve("manifest.json"))).getAsJsonObject();
        if (!manifest.get("status").getAsString().equals("complete")) throw new IllegalStateException("Incomplete corpus");
        List<Position> positions = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            JsonObject map = null, game = null;
            for (var item : manifest.getAsJsonArray("maps")) {
                JsonObject candidate = item.getAsJsonObject();
                if (candidate.get("split").getAsString().equals("train") && candidate.get("mapIndex").getAsInt() == index) map = candidate;
            }
            for (var item : manifest.getAsJsonArray("games")) {
                JsonObject candidate = item.getAsJsonObject();
                if (candidate.get("split").getAsString().equals("train") && candidate.get("mapIndex").getAsInt() == index
                        && candidate.get("teacherSeat").getAsInt() == 0) game = candidate;
            }
            if (map == null || game == null) throw new IllegalStateException("Missing fixed game");
            String init = map.get("initBlockP0").getAsString(), hash = map.get("mapHash").getAsString();
            if (!sha(init.getBytes(StandardCharsets.UTF_8)).equals(hash)) throw new IllegalStateException("Map hash");
            GameState state = Protocol.readInit(new Protocol.TokenReader(new ByteArrayInputStream(init.getBytes(StandardCharsets.UTF_8)))).state;
            Pathing pathing = new Pathing(state.size); Rules.updateConnections(state, pathing);
            long seed = map.get("seed").getAsLong();
            byte[] metadata = Files.readAllBytes(corpus.resolve(game.get("metadata").getAsString()));
            if (!sha(metadata).equals(game.get("metadataSha256").getAsString())) throw new IllegalStateException("Metadata hash");
            JsonObject data = JsonParser.parseString(new String(metadata, StandardCharsets.UTF_8)).getAsJsonObject();
            List<Integer> captured = new ArrayList<>();
            for (var item : data.getAsJsonArray("turns")) {
                JsonObject turn = item.getAsJsonObject();
                if (Rules.gameOver(state, pathing) || state.turn != turn.get("turn").getAsInt()
                        || !identity(state).equals(turn.get("preStateSha256").getAsString())) throw new IllegalStateException("Replay pre-state mismatch");
                for (int requested : TURNS) if (requested == state.turn) {
                    positions.add(new Position(seed, hash, state.copy())); captured.add(requested);
                }
                var tr = Rules.resolve(state, Action.parseLine(turn.get("teacherActions").getAsString()),
                        Action.parseLine(turn.get("opponentActions").getAsString()), pathing);
                for (int p = 0; p < 2; p++) if (tr.hasError(p) || !tr.moves[p].skipped.isEmpty()
                        || state.score[p] != turn.getAsJsonArray("scoreAfter").get(p).getAsInt()
                        || tr.gained[p] != turn.getAsJsonArray("gained").get(p).getAsInt()) throw new IllegalStateException("Replay outcome mismatch");
            }
            if (!Rules.gameOver(state, pathing)) throw new IllegalStateException("Recorded game is not terminal");
            Map<String, Object> row = new LinkedHashMap<>(); row.put("seed", seed); row.put("mapHash", hash);
            row.put("game", game.get("id").getAsString()); row.put("capturedTurns", captured);
            row.put("terminalTurn", state.turn); row.put("score", state.score.clone()); row.put("initBlockP0", init); maps.add(row);
        }
        return positions;
    }

    private static List<String> measure(List<Position> positions, String spec, String phase, long budget,
                                       List<Map<String, Object>> rows, List<String> base) throws Exception {
        List<String> result = new ArrayList<>(); long seed = -1; Bot[] bots = null;
        for (Position position : positions) {
            if (position.seed != seed) {
                seed = position.seed; bots = new Bot[]{BotFactory.create(spec), BotFactory.create(spec)};
                for (int me = 0; me < 2; me++) { bots[me].init(position.state, me); bots[me].warmup(position.state, me, 100); }
            }
            for (int me = 0; me < 2; me++) {
                GameState state = position.state; String before = identity(state);
                long start = System.nanoTime(); List<Action> actions = bots[me].play(state, me, budget);
                double ms = (System.nanoTime() - start) / 1e6;
                if (!before.equals(identity(state))) throw new IllegalStateException("Input mutated");
                Rules.TurnResult tr = new Rules.TurnResult();
                Rules.expand(state, me, actions, new Pathing(state.size), tr.moves[me], tr);
                if (tr.hasError(me) || !tr.moves[me].skipped.isEmpty()) throw new IllegalStateException("Invalid candidate plan");
                String text = Action.format(actions); int sample = result.size(); result.add(text);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("phase", phase); row.put("seed", seed); row.put("mapHash", position.mapHash); row.put("turn", state.turn);
                row.put("me", me); row.put("budgetMs", budget); row.put("elapsedMs", ms); row.put("actions", text);
                row.put("preStateSha256", before); row.put("paint", tr.moves[me].cost);
                if (bots[me] instanceof SpatialV5Bot bot) {
                    row.put("greedyInterrupted", bot.lastInterrupted); row.put("attempted", bot.lastSearchAttempted);
                    row.put("complete", bot.lastSearchComplete); row.put("changed", bot.lastSearchChanged); row.put("cutoff", bot.lastSearchCutoff);
                    row.put("leafEvaluations", bot.lastLeafEvaluations); row.put("baseTarget", bot.lastBaseTarget);
                    row.put("alternativeTarget", bot.lastAlternativeTarget); row.put("target", bot.lastTarget);
                    row.put("baseValue", Float.isFinite(bot.lastBaseValue) ? bot.lastBaseValue : null);
                    row.put("alternativeValue", Float.isFinite(bot.lastAlternativeValue) ? bot.lastAlternativeValue : null);
                    row.put("baseCells", Arrays.copyOf(bot.lastBaseCells, bot.lastPlacements));
                    row.put("opponentCells", Arrays.copyOf(bot.lastOpponentCells, bot.lastOpponentPlacements));
                    row.put("opponentTarget", bot.lastOpponentTarget);
                    if (bot.lastSearchChanged && (!bot.lastSearchComplete || bot.lastLeafEvaluations != 2)
                            || bot.lastSearchComplete && bot.lastLeafEvaluations != 2) throw new IllegalStateException("Partial comparison accepted");
                    if (!Arrays.equals(Arrays.copyOf(tr.moves[me].cells, tr.moves[me].count),
                            Arrays.copyOf(bot.lastBaseCells, bot.lastPlacements))) throw new IllegalStateException("Construction changed");
                    if (!bot.lastSearchChanged && tr.moves[me].disrupts.stream().findFirst().orElse(-1) != bot.lastBaseTarget)
                        throw new IllegalStateException("Fallback changed disruption");
                    if (budget == 60000 && bot.lastInterrupted) throw new IllegalStateException("Ample greedy interrupted");
                }
                if (base != null) row.put("matchesV4Ample", text.equals(base.get(sample)));
                rows.add(row);
            }
        }
        return result;
    }

    private static JsonObject leafParity(List<Position> positions, List<Map<String, Object>> measurements) throws Exception {
        JsonObject panel = new JsonObject(); panel.addProperty("label", "v5-search-leaves");
        panel.addProperty("weightsSha256", sha(Base64.getDecoder().decode(SpatialV5Weights.DATA))); JsonArray rows = new JsonArray();
        SpatialNet net = new SpatialNet(SpatialV5Weights.DATA);
        for (Map<String, Object> measurement : measurements) if (measurement.get("phase").equals("ample") && Boolean.TRUE.equals(measurement.get("complete"))) {
            Position position = positions.stream().filter(p -> p.seed == (long) measurement.get("seed") && p.state.turn == (int) measurement.get("turn")).findFirst().orElseThrow();
            int me = (int) measurement.get("me"); int[] own = (int[]) measurement.get("baseCells"), opp = (int[]) measurement.get("opponentCells");
            for (String choice : new String[]{"base", "alternative"}) {
                SearchState simulation = new SearchState(position.state); int target = (int) measurement.get(choice + "Target");
                int opponentTarget = (int) measurement.get("opponentTarget");
                boolean terminal = me == 0 ? simulation.resolve(own, own.length, target, opp, opp.length, opponentTarget)
                        : simulation.resolve(opp, opp.length, opponentTarget, own, own.length, target);
                JsonObject row = SpatialLatePanel.trace(simulation.state, me, net).rows.get(0);
                row.addProperty("seed", position.seed); row.addProperty("turn", simulation.state.turn); row.addProperty("me", me);
                row.addProperty("choice", choice); row.addProperty("terminal", terminal);
                float immediate = (simulation.state.score[me] - simulation.state.score[1-me]
                        - position.state.score[me] + position.state.score[1-me]) / 10000f;
                float expected = immediate + (terminal ? 0 : row.getAsJsonObject("java").get("value").getAsFloat());
                float measured = ((Number) measurement.get(choice + "Value")).floatValue();
                if (Math.abs(expected - measured) > 1e-5f) throw new IllegalStateException("Leaf accounting mismatch");
                row.addProperty("expectedActionValue", expected); row.addProperty("measuredActionValue", measured); rows.add(row);
            }
        }
        panel.add("rows", rows); return panel;
    }

    private static List<Map<String, Object>> summaries(List<Map<String, Object>> rows) {
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (String phase : new String[]{"baseline", "off", "ample", "nominal"}) {
            List<Double> times = new ArrayList<>(); Map<String, Object> summary = new LinkedHashMap<>(); summary.put("phase", phase);
            int attempted=0, complete=0, changed=0, cutoff=0, interrupted=0;
            for (var row : rows) if (row.get("phase").equals(phase)) {
                times.add((Double) row.get("elapsedMs")); attempted += Boolean.TRUE.equals(row.get("attempted")) ? 1 : 0;
                complete += Boolean.TRUE.equals(row.get("complete")) ? 1 : 0; changed += Boolean.TRUE.equals(row.get("changed")) ? 1 : 0;
                cutoff += Boolean.TRUE.equals(row.get("cutoff")) ? 1 : 0; interrupted += Boolean.TRUE.equals(row.get("greedyInterrupted")) ? 1 : 0;
            }
            times.sort(Double::compare); summary.put("calls", times.size()); summary.put("attempted", attempted);
            summary.put("complete", complete); summary.put("changed", changed); summary.put("cutoff", cutoff); summary.put("greedyInterrupted", interrupted);
            summary.put("meanMs", times.stream().mapToDouble(Double::doubleValue).average().orElseThrow());
            summary.put("p95Ms", times.get((int)Math.ceil(times.size()*.95)-1)); summary.put("maxMs", times.get(times.size()-1)); summaries.add(summary);
        }
        return summaries;
    }
    private static String identity(GameState s) throws Exception { return sha((Protocol.initBlock(s,0)+s.turn+"\n"+s.maxTurns+"\n"+Protocol.turnBlock(s,0)).getBytes(StandardCharsets.UTF_8)); }
    private static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static void write(Path path, Object value) throws Exception { Files.writeString(path, JSON.toJson(value), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); }
}
