package btk.tools;

import btk.bot.BotFactory;
import btk.bot.Params;
import btk.bot.current.RolloutBot;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static btk.tools.NeuralProposalStudy.*;

/** Fixed saved-state crossover. Components are never refilled, retargeted or repaired.
 * The arithmetic describes the existing rollout proxy, including its robust aggregation;
 * it does not estimate causal effects on real game outcomes. */
public final class NeuralComponentStudy {
    static final double EPS = 1e-9;
    static final Path PRIOR_SUMMARY = Path.of("docs/evidence/neural-proposal-audit-v1.json");
    record Cross(Plan plan, String invalidReason) { boolean valid() { return invalidReason == null; } }
    record Result(Plan plan, boolean valid, String invalidReason, Score score) {}

    /** Core alone decides legality. Even a placement in the disrupted region is retained. */
    static Cross compose(GameState state, int me, int[] cells, int target) {
        List<Action> actions = new ArrayList<>();
        for (int cell : cells) actions.add(Action.place(state.x(cell), state.y(cell)));
        if (target >= 0) actions.add(Action.disrupt(target));
        if (actions.isEmpty()) actions.add(Action.waitAction());
        Plan attempt = new Plan(cells.clone(), target, Action.format(actions));
        Rules.TurnResult tr = new Rules.TurnResult();
        Rules.expand(state, me, actions, new Pathing(state.size), tr.moves[me], tr);
        var moves = tr.moves[me];
        if (tr.unknownActions || moves.error != null || !moves.skipped.isEmpty())
            return new Cross(attempt, "core error=" + moves.error + "; skipped=" + moves.skipped + "; unknown=" + tr.unknownActions);
        return new Cross(legal(state, me, cells, target), null);
    }
    private static String snapshot(RolloutBot bot) throws Exception {
        Map<String, Object> state = new LinkedHashMap<>();
        for (String name : List.of("routes", "lengths", "values", "counts", "roots", "rootCounts", "rootTargets",
                "bestCells", "bestCount", "best")) state.put(name, field(bot, name));
        state.put("selectedRoute", bot.selectedRoute); state.put("selectedTarget", bot.selectedTarget);
        state.put("selectedValue", bot.selectedValue); state.put("comparisons", bot.completedComparisons);
        state.put("simulatedTurns", bot.simulatedTurns); state.put("fillCells", bot.fillCells);
        // Include the unchanged bot's private parameter copy and exact timer state, not wall-clock time left.
        state.put("parameters", field(bot, "p")); state.put("timer", field(bot, "timer"));
        return ReplyStudy.JSON.toJson(state);
    }
    static Map<String, Result> evaluateFour(RolloutBot bot, GameState state, int me, Plan emitted, Plan neural,
                                           int replies, Params params) throws Exception {
        return evaluateFour(bot, state, me, emitted, neural, replies, params, null, null);
    }
    private static Map<String, Result> evaluateFour(RolloutBot bot, GameState state, int me, Plan emitted, Plan neural,
                                                   int replies, Params params, Score expectedE, Score expectedN) throws Exception {
        String saved = snapshot(bot), savedParams = ReplyStudy.JSON.toJson(params); GameState before = state.copy();
        int root = ((int[]) field(bot, "counts"))[me];
        Map<String, Cross> crosses = new LinkedHashMap<>();
        crosses.put("E", compose(state, me, emitted.cells(), emitted.target()));
        crosses.put("N", compose(state, me, neural.cells(), neural.target()));
        crosses.put("C", compose(state, me, neural.cells(), emitted.target()));
        crosses.put("D", compose(state, me, emitted.cells(), neural.target()));
        require(crosses.get("E").valid() && crosses.get("N").valid(), "Original E/N action became invalid");
        Map<String, Result> results = new LinkedHashMap<>();
        for (var entry : crosses.entrySet()) {
            Cross cross = entry.getValue(); Score score = null;
            if (cross.valid()) {
                score = evaluate(bot, state, me, cross.plan, root, replies, params);
                require(Double.isFinite(score.value()) && score.replyValues().length == replies, "Invalid complete score");
                for (double value : score.replyValues()) require(Double.isFinite(value), "Nonfinite reply");
                if (entry.getKey().equals("E") && expectedE != null) sameScore(score, expectedE, "Reproduced E before crossover");
                if (entry.getKey().equals("N") && expectedN != null) sameScore(score, expectedN, "Reproduced N before crossover");
                for (Result other : results.values()) if (other.valid && other.plan.key().equals(cross.plan.key()))
                    sameScore(score, other.score, "Identical complete crossover actions");
            }
            results.put(entry.getKey(), new Result(cross.plan, cross.valid(), cross.invalidReason, score));
        }
        require(saved.equals(snapshot(bot)) && savedParams.equals(ReplyStudy.JSON.toJson(params)),
                "Crossover modified retained context/parameters/selected diagnostics");
        ReplyProbe.sameState(before, state);
        Map<String, Result> ordered = new LinkedHashMap<>();
        for (String key : List.of("E", "C", "D", "N")) ordered.put(key, results.get(key));
        return ordered;
    }
    static void sameScore(Score a, Score b, String message) {
        require(a != null && b != null && a.value() == b.value() && Arrays.equals(a.replyValues(), b.replyValues()), message + " score mismatch");
    }
    static Map<String, Double> effects(Double e, Double c, Double d, Double n) {
        Map<String, Double> out = new LinkedHashMap<>();
        out.put("buildAtBaselineTarget", c == null || e == null ? null : c - e);
        out.put("buildAtNeuralTarget", n == null || d == null ? null : n - d);
        out.put("targetAtBaselineBuild", d == null || e == null ? null : d - e);
        out.put("targetAtNeuralBuild", n == null || c == null ? null : n - c);
        out.put("interaction", n == null || c == null || d == null || e == null ? null : n - c - d + e);
        return out;
    }
    static String thresholdCategory(Double c, Double d, double h) {
        if (c == null || d == null) return "unavailable";
        boolean cb = c - h > EPS, db = d - h > EPS;
        return cb ? (db ? "bothSeparatelyExceedH" : "onlyCExceedsH") : (db ? "onlyDExceedsH" : "neitherSeparatelyExceedsH");
    }
    private static Double value(Result r) { return r.score == null ? null : r.score.value(); }
    private static Score priorScore(JsonObject prior, String name) { return ReplyStudy.JSON.fromJson(prior.get(name), Score.class); }
    private static JsonObject json(Path p) throws Exception { return JsonParser.parseString(Files.readString(p)).getAsJsonObject(); }
    static void identicalPriorRow(JsonObject prior, Map<String, Object> reconstructed) {
        require(prior.equals(ReplyStudy.JSON.toJsonTree(reconstructed)), "Entire prior row reconstruction differs");
    }

    /** All prior rows are independently reproduced in main before this method is invoked. */
    static JsonObject study(Position position, JsonObject prior, Params params) throws Exception {
        GameState state = position.state().copy(), before = state.copy(); int me = position.me();
        Plan e = ReplyStudy.JSON.fromJson(prior.get("baselinePlan"), Plan.class);
        Plan n = accepted(state, me, position.recordedAction());
        require(ReplyStudy.JSON.toJsonTree(n).equals(prior.get("neuralPlan")), "Recorded action identity mismatch");
        RolloutBot bot = new RolloutBot(params); bot.init(state.copy(), me);
        String action = Action.format(bot.play(state, me, AMPLE_MS));
        require(action.equals(e.action()) && bot.ordinarySearchComplete && bot.networkPhaseComplete && !bot.selectedOpening,
                "Fresh baseline play differs/incomplete");
        require(bot.selectedValue == prior.get("nativeSelectedValue").getAsDouble()
                && bot.completedComparisons == prior.get("nativeCompletedComparisons").getAsInt(), "Fresh native selection mismatch");
        List<Tuple> tuples = Arrays.asList(ReplyStudy.JSON.fromJson(prior.get("tuples"), Tuple[].class));
        List<Suppressed> skipped = Arrays.asList(ReplyStudy.JSON.fromJson(prior.get("suppressedTuples"), Suppressed[].class));
        Tuple chosen = tuples.get(prior.get("nativeSelectedTuple").getAsInt());
        int[] counts = (int[]) field(bot, "counts");
        require(bot.selectedRoute == (chosen.root() == counts[me] ? -1 : chosen.root())
                && bot.selectedTarget == e.target()
                && Arrays.equals(counts, ReplyStudy.JSON.fromJson(prior.get("portfolioCounts"), int[].class)), "Fresh route/portfolio mismatch");
        Map<String, Object> portfolio = new LinkedHashMap<>();
        for (String name : List.of("routes", "lengths", "values", "counts", "roots", "rootCounts", "rootTargets"))
            portfolio.put(name, field(bot, name));
        require(ReplyStudy.hash(ReplyStudy.JSON.toJson(portfolio).getBytes(StandardCharsets.UTF_8))
                .equals(prior.get("portfolioSha256").getAsString()), "Fresh complete portfolio differs from pinned H/reply context");
        int replies = Math.max(1, Math.min(params.ROLLOUT_REPLIES, counts[1 - me]));
        require(replies == prior.get("replies").getAsInt(), "Reply count changed");
        Map<String, Result> results = evaluateFour(bot, state, me, e, n, replies, params,
                priorScore(prior, "baselineCommonScore"), priorScore(prior, "neuralCommonScore"));
        sameScore(results.get("E").score, priorScore(prior, "baselineCommonScore"), "Reproduced E");
        sameScore(results.get("N").score, priorScore(prior, "neuralCommonScore"), "Reproduced N");
        Score h = priorScore(prior, "bestCommonFilledScore");
        sameScore(h, tuples.get(prior.get("bestCommonFilledTuple").getAsInt()).commonFilled(), "Pinned H");
        JsonObject row = new JsonObject();
        for (String key : List.of("slot", "gameId", "seed", "mapSha256", "me", "turn", "stateSha256")) row.add(key, prior.get(key).deepCopy());
        require(ReplyStudy.stateHash(state).equals(prior.get("stateSha256").getAsString()), "State identity changed");
        row.addProperty("priorRowSha256", ReplyStudy.hash(ReplyStudy.JSON.toJson(prior).getBytes(StandardCharsets.UTF_8)));
        row.addProperty("originalPositive", prior.get("novelModelPreferred").getAsBoolean());
        row.addProperty("replies", replies); row.addProperty("priorRowExactlyReproduced", true);
        row.addProperty("inputAndContextUnchanged", true);
        row.add("bestExistingH", ReplyStudy.JSON.toJsonTree(tuples.get(prior.get("bestCommonFilledTuple").getAsInt()).filled()));
        row.add("bestExistingHScore", ReplyStudy.JSON.toJsonTree(h));
        JsonObject actions = new JsonObject();
        for (var entry : results.entrySet()) {
            Result result = entry.getValue(); JsonObject item = ReplyStudy.JSON.toJsonTree(result).getAsJsonObject();
            Double v = value(result);
            item.addProperty("gapFromEmittedE", v == null ? null : v - results.get("E").score.value());
            item.addProperty("gapFromBestExistingH", v == null ? null : v - h.value());
            item.add("coverage", result.valid ? ReplyStudy.JSON.toJsonTree(coverage(result.plan, tuples, skipped)) : com.google.gson.JsonNull.INSTANCE);
            actions.add(entry.getKey(), item);
        }
        row.add("actions", actions);
        row.add("effects", ReplyStudy.JSON.toJsonTree(effects(value(results.get("E")), value(results.get("C")), value(results.get("D")), value(results.get("N")))));
        List<Map<String, Double>> replyEffects = new ArrayList<>();
        for (int r = 0; r < replies; r++) {
            Double[] values = new Double[4]; int i = 0;
            for (String key : List.of("E", "C", "D", "N")) {
                Score score = results.get(key).score; values[i++] = score == null ? null : score.replyValues()[r];
            }
            replyEffects.add(effects(values[0], values[1], values[2], values[3]));
        }
        row.add("perReplyEffects", ReplyStudy.JSON.toJsonTree(replyEffects));
        row.addProperty("thresholdCategory", thresholdCategory(value(results.get("C")), value(results.get("D")), h.value()));
        ReplyProbe.sameState(before, state); ReplyProbe.sameState(before, position.state()); return row;
    }
    static Map<String, Object> summarize(List<JsonObject> rows) {
        Map<String, Object> out = new LinkedHashMap<>(); out.put("positions", rows.size());
        for (String action : List.of("C", "D")) for (String reference : List.of("EmittedE", "BestExistingH")) {
            int better = 0, equal = 0, worse = 0, missing = 0;
            for (JsonObject row : rows) {
                JsonElement value = row.getAsJsonObject("actions").getAsJsonObject(action).get("gapFrom" + reference);
                if (value.isJsonNull()) missing++;
                else if (value.getAsDouble() > EPS) better++;
                else if (value.getAsDouble() < -EPS) worse++;
                else equal++;
            }
            out.put(action + "Vs" + reference, Map.of("better", better, "equal", equal, "worse", worse, "missing", missing));
        }
        Map<String, Integer> categories = new LinkedHashMap<>();
        for (String key : List.of("onlyCExceedsH", "onlyDExceedsH", "bothSeparatelyExceedH", "neitherSeparatelyExceedsH", "unavailable")) categories.put(key, 0);
        for (JsonObject row : rows) categories.merge(row.get("thresholdCategory").getAsString(), 1, Integer::sum);
        out.put("thresholdCategories", categories); return out;
    }
    static JsonObject pinnedPrior(Path selection, Path priorFile, Path summaryFile) throws Exception {
        JsonObject summary = json(summaryFile);
        checkedHash(selection, summary.getAsJsonObject("inputs").get("selectionSha256").getAsString());
        checkedHash(priorFile, summary.getAsJsonObject("fullReport").get("sha256").getAsString());
        JsonObject prior = json(priorFile);
        require("neural-proposal-audit-v1".equals(prior.get("schema").getAsString())
                && "complete".equals(prior.get("status").getAsString()) && prior.get("complete").getAsBoolean()
                && prior.get("selectionSha256").getAsString().equals(hash(selection))
                && prior.get("positions").getAsInt() == 51 && prior.getAsJsonArray("samples").size() == 51
                && prior.getAsJsonArray("omissions").size() == 13 && prior.get("requestedPositions").getAsInt() == 64,
                "Wrong prior completed panel");
        int positives = 0;
        for (JsonElement row : prior.getAsJsonArray("samples")) if (row.getAsJsonObject().get("novelModelPreferred").getAsBoolean()) positives++;
        require(positives == 8, "Wrong original positive subgroup"); return prior;
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("selection.json prior-results.json fresh-output.json");
        Path selection = Path.of(args[0]), priorFile = Path.of(args[1]), out = Path.of(args[2]);
        require(!Files.exists(out), "Choose a fresh report path");
        Map<String, Object> report = new LinkedHashMap<>(); List<JsonObject> rows = new ArrayList<>(); int reconstructed = 0;
        report.put("schema", "neural-component-audit-v1"); report.put("createdAt", Instant.now().toString());
        report.put("javaVersion", System.getProperty("java.version")); report.put("ampleMsPerPlayOrTuple", AMPLE_MS);
        report.put("scope", "All 51 prior saved states; E/C/D/N share unchanged retained route-0 continuation and replies. No refill, retargeting, inference or new games. Effects and interaction are rollout-proxy arithmetic, not causal strength or real-point estimates. Original eight positives are a conditional subgroup.");
        try {
            JsonObject prior = pinnedPrior(selection, priorFile, PRIOR_SUMMARY);
            report.put("selectionSha256", hash(selection)); report.put("priorReportSha256", hash(priorFile));
            report.put("priorSummarySha256", hash(PRIOR_SUMMARY));
            Loaded loaded = load(selection);
            require(loaded.positions().size() == 51 && loaded.omissions().size() == 13
                    && ReplyStudy.JSON.toJsonTree(loaded.games()).equals(prior.get("games"))
                    && ReplyStudy.JSON.toJsonTree(loaded.omissions()).equals(prior.get("omissions")), "Prior sample/replay identities differ");
            Params params = BotFactory.parameters("v09?NOISE=0");
            require(ReplyStudy.JSON.toJsonTree(params).equals(prior.get("params")), "Prior full configuration changed");
            report.put("params", params); report.put("games", loaded.games()); report.put("omissions", loaded.omissions());
            report.put("requestedPositions", 64);
            // Complete this phase for ALL rows before any new crossover is evaluated.
            for (int i = 0; i < 51; i++) {
                identicalPriorRow(prior.getAsJsonArray("samples").get(i).getAsJsonObject(), inspect(loaded.positions().get(i), params));
                reconstructed++;
                System.out.printf("Prior reconstruction %d/51%n", reconstructed);
            }
            for (int i = 0; i < 51; i++) {
                rows.add(study(loaded.positions().get(i), prior.getAsJsonArray("samples").get(i).getAsJsonObject(), params));
                System.out.printf("Component study %d/51%n", rows.size());
            }
            List<JsonObject> positive = rows.stream().filter(r -> r.get("originalPositive").getAsBoolean()).toList();
            require(positive.size() == 8, "Original subgroup accounting mismatch");
            report.put("allPositions", summarize(rows)); report.put("originalEightPositiveSubgroup", summarize(positive));
            List<Map<String, Object>> perGame = new ArrayList<>();
            for (Map<String, Object> game : loaded.games()) {
                long id = ((Number) game.get("gameId")).longValue();
                Map<String, Object> summary = new LinkedHashMap<>(summarize(rows.stream().filter(r -> r.get("gameId").getAsLong() == id).toList()));
                summary.put("gameId", id); perGame.add(summary);
            }
            report.put("perGame", perGame); report.put("status", "complete"); report.put("complete", true);
        } catch (Exception e) {
            report.put("status", "failed"); report.put("complete", false); report.put("error", e.toString()); throw e;
        } finally {
            report.put("priorRowsExactlyReproduced", reconstructed); report.put("positions", rows.size()); report.put("samples", rows);
            Files.createDirectories(out.toAbsolutePath().getParent());
            Files.writeString(out, ReplyStudy.JSON.toJson(report), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        }
    }
}
