package btk.tools;

import btk.bot.BotFactory;
import btk.bot.current.RolloutBot;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.core.SearchState;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fixed shared-state network-cell coverage study; no strength games or future-action inputs. */
public final class NetworkCellStudy {
    private static final int SAMPLE_COUNT = 133;
    private static final int GAME_COUNT = 14;
    private static final int MAX_EVALUATED = 24;
    private static final int MAX_ROOTS = 2;
    private static final String BASELINE = "current?USE_ROLLOUT_QUICK_ROUTE=1,USE_ROLLOUT_NETWORK=0,USE_ROLLOUT_NETWORK_AFTER_BASE=0";
    private static final String CANDIDATE = BASELINE + ",USE_ROLLOUT_NETWORK=0,USE_ROLLOUT_NETWORK_AFTER_BASE=1";
    private static final Path OPPORTUNITIES = Path.of("docs/evidence/network-control-single-cell-v1.json");
    private static final Path BOT_CLASS = Path.of("build/classes/java/main/btk/bot/current/RolloutBot.class");

    private NetworkCellStudy() {}

    /** Args: fixedSampleJson candidateSpec outputJson [baselineSpec] [opportunityEvidenceJson]. */
    public static void main(String[] args) throws Exception {
        if (args.length < 3 || args.length > 5) {
            throw new IllegalArgumentException("fixedSampleJson candidateSpec outputJson [baselineSpec] [opportunityEvidenceJson]");
        }
        Path input = Path.of(args[0]), output = Path.of(args[2]);
        Path evidencePath = args.length > 4 && !args[4].isBlank() ? Path.of(args[4]) : OPPORTUNITIES;
        if (Files.exists(output)) throw new IllegalArgumentException("Preserve existing report: " + output);
        String candidate = noiseFree(args[1].isBlank() ? CANDIDATE : args[1]);
        String baseline = noiseFree(args.length > 3 && !args[3].isBlank() ? args[3] : BASELINE);
        byte[] inputBytes = Files.readAllBytes(input), evidenceBytes = Files.readAllBytes(evidencePath);
        JsonObject fixed = JsonParser.parseString(new String(inputBytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject evidence = JsonParser.parseString(new String(evidenceBytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        Map<String, String> candidateParams = BotFactory.configuration(candidate);
        Map<String, String> baselineParams = BotFactory.configuration(baseline);
        validateConfiguration(candidateParams, baselineParams, fixed.getAsJsonObject("baselineParams"));
        boolean afterBase = Boolean.parseBoolean(candidateParams.get("USE_ROLLOUT_NETWORK_AFTER_BASE"));
        String inputHash = ReplyStudy.hash(inputBytes), evidenceHash = ReplyStudy.hash(evidenceBytes);
        if (!inputHash.equals(evidence.get("inputSha256").getAsString())) {
            throw new IllegalArgumentException("Opportunity evidence belongs to a different fixed sample");
        }
        Map<String, JsonObject> sampleById = indexSamples(fixed.getAsJsonArray("samples"));
        Map<String, JsonObject> opportunityById = indexSamples(evidence.getAsJsonArray("samples"));
        if (sampleById.size() != SAMPLE_COUNT || !sampleById.keySet().equals(opportunityById.keySet())) {
            throw new IllegalArgumentException("Require the same complete 133-state sample");
        }
        Map<Long, List<JsonObject>> byGame = new LinkedHashMap<>();
        for (JsonObject sample : sampleById.values()) {
            JsonObject opportunity = opportunityById.get(identity(sample));
            if (!sample.get("stateSha256").equals(opportunity.get("stateSha256"))
                    || !sample.get("outcome").equals(opportunity.get("outcome"))) {
                throw new IllegalArgumentException("Sample identity differs from opportunity evidence");
            }
            byGame.computeIfAbsent(sample.get("gameId").getAsLong(), ignored -> new ArrayList<>()).add(sample);
        }
        if (byGame.size() != GAME_COUNT) throw new IllegalArgumentException("Require all 14 original games");
        Map<Long, JsonObject> replayEvidence = new LinkedHashMap<>();
        for (var item : evidence.getAsJsonArray("games")) {
            JsonObject game = item.getAsJsonObject();
            Path file = Path.of(game.get("file").getAsString().replace('\\', '/'));
            String name = file.getFileName().toString();
            long id = Long.parseLong(name.substring(0, name.length() - ".json".length()));
            if (replayEvidence.put(id, game) != null) throw new IllegalArgumentException("Duplicate replay evidence");
        }
        if (!byGame.keySet().equals(replayEvidence.keySet())) throw new IllegalArgumentException("Replay set differs");
        String classHash = ReplyStudy.hash(Files.readAllBytes(BOT_CLASS));
        List<Map<String, Object>> rows = new ArrayList<>(), games = new ArrayList<>();
        for (var entry : byGame.entrySet()) {
            JsonObject savedGame = replayEvidence.get(entry.getKey());
            Path file = ReplayPaths.resolve(Path.of(savedGame.get("file").getAsString().replace('\\', '/')));
            String replayHash = ReplyStudy.hash(Files.readAllBytes(file));
            if (!replayHash.equals(savedGame.get("sha256").getAsString())) {
                throw new IllegalStateException("Replay changed: " + file);
            }
            CgReplay replay = CgReplay.load(file);
            if (replay.gameId != entry.getKey()) throw new IllegalStateException("Wrong replay game id");
            CgValidate.Outcome validation = CgValidate.validate(replay, false);
            if (validation.firstDivergenceTurn >= 0) throw new IllegalStateException("Replay does not validate: " + file);
            var gameRow = new LinkedHashMap<String, Object>();
            gameRow.put("gameId", replay.gameId); gameRow.put("file", file.toString());
            gameRow.put("sha256", replayHash); gameRow.put("validated", true); games.add(gameRow);
            GameState state = replay.toState(); Pathing pathing = new Pathing(state.size);
            Rules.updateConnections(state, pathing);
            int previous = -1;
            for (JsonObject sample : entry.getValue()) {
                int targetTurn = sample.get("turn").getAsInt();
                if (targetTurn <= previous || targetTurn >= replay.turns()) {
                    throw new IllegalArgumentException("Sample turns must be increasing and inside the replay");
                }
                // Only past recorded actions reconstruct the next public observation.
                while (state.turn < targetTurn) {
                    requireSuccessfulTurn(replay, state.turn);
                    Rules.resolve(state, CgPlayerStudy.effectiveActions(replay, state.turn, 0),
                            CgPlayerStudy.effectiveActions(replay, state.turn, 1), pathing);
                }
                requireSuccessfulTurn(replay, targetTurn);
                String before = ReplyStudy.stateHash(state);
                if (!before.equals(sample.get("stateSha256").getAsString())) throw new IllegalStateException("State hash mismatch");
                int me = sample.get("me").getAsInt();
                JsonObject opportunity = opportunityById.get(identity(sample));
                var row = new LinkedHashMap<String, Object>();
                row.put("gameId", replay.gameId); row.put("turn", targetTurn); row.put("me", me);
                row.put("outcome", sample.get("outcome").getAsString()); row.put("stateSha256", before);
                row.put("knownOpportunity", knownOpportunity(opportunity));
                // Alternate policy order while keeping nominal/ample separate, with a fresh instance each time.
                for (String spec : rows.size() % 2 == 0 ? new String[]{baseline, candidate} : new String[]{candidate, baseline}) {
                    boolean isCandidate = spec.equals(candidate);
                    var result = new LinkedHashMap<String, Object>();
                    result.put("nominal", inspect(state, me, spec, targetTurn == 0 ? 486 : 36, opportunity, isCandidate, afterBase));
                    Map<String, Object> ample = inspect(state, me, spec, 60_000, opportunity, isCandidate, afterBase);
                    if (!isCandidate && !ample.get("action").equals(sample.getAsJsonObject("baseline")
                            .getAsJsonObject("ample").get("action").getAsString())) {
                        throw new IllegalStateException("Baseline ample action differs from the original fixed sample");
                    }
                    result.put("ample", ample); row.put(isCandidate ? "candidate" : "baseline", result);
                }
                if (afterBase) row.put("ordinaryVsBaseline", compareOrdinary(row));
                if (!before.equals(ReplyStudy.stateHash(state))) throw new IllegalStateException("Input mutated");
                rows.add(row); previous = targetTurn;
            }
        }
        if (rows.size() != SAMPLE_COUNT || !classHash.equals(ReplyStudy.hash(Files.readAllBytes(BOT_CLASS)))) {
            throw new IllegalStateException("Incomplete sample or compiled strategy changed during the study");
        }
        var report = new LinkedHashMap<String, Object>();
        report.put("schemaVersion", 2); report.put("spec", candidate); report.put("params", candidateParams);
        report.put("networkMode", afterBase ? "after_base" : "interleaved");
        report.put("baselineSpec", baseline); report.put("baselineParams", baselineParams);
        report.put("input", input.toString()); report.put("inputSha256", inputHash);
        report.put("opportunityEvidence", evidencePath.toString()); report.put("opportunityEvidenceSha256", evidenceHash);
        report.put("classSha256", classHash); report.put("validatedReplays", games.size()); report.put("sampleCount", rows.size());
        report.put("baselineAmpleActionsMatchOriginalSample", true);
        if (afterBase) report.put("candidateOrdinaryAmpleMatchesBaseline", true);
        report.put("method", "The same complete 133 pre-turn observations from all 14 original boss games, including wins and losses. "
                + "Replay and state hashes checked; each replay validated first. Fresh NOISE=0 instances, init outside clock, "
                + "alternating baseline/candidate order; nominal 486/36 ms and ample 60000 ms. "
                + "Only past recorded actions reconstruct observations. No current/future recorded enemy actions enter either policy "
                + "or the placement-only income checks. Known useful cells come from the previously saved exhaustive own-placement "
                + "probe without sabotage or enemy construction. Candidate availability, exact cell evaluation, complete scenario "
                + "comparison and eventual choice are separate. One completed attack comparison includes every modeled enemy reply; "
                + "it need not include both own sabotage alternatives. Repeated observations are not independent trials. "
                + "The after_base mode also records the ordinary answer before late network work and its completion marker: "
                + "ample ordinary action, cells, selected route/target/value and completed comparisons must equal baseline; "
                + "nominal differences are descriptive clock-sensitive observations. Nonfinite selected values are saved as null. "
                + "This is mechanism evidence, not original arena timing/RNG, a strength test, recoverable match points or process readiness.");
        report.put("summary", summarize(rows, afterBase)); report.put("games", games); report.put("samples", rows);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, ReplyStudy.JSON.toJson(report), StandardOpenOption.CREATE_NEW);
        System.out.println("Saved " + rows.size() + " network-cell states from " + games.size() + " validated games to " + output);
        System.out.println(ReplyStudy.JSON.toJson(report.get("summary")));
    }

    private static String noiseFree(String spec) { return spec + (spec.contains("?") ? "," : "?") + "NOISE=0"; }

    private static String identity(JsonObject sample) {
        return sample.get("gameId").getAsLong() + ":" + sample.get("turn").getAsInt() + ":" + sample.get("me").getAsInt();
    }

    private static Map<String, JsonObject> indexSamples(JsonArray samples) {
        Map<String, JsonObject> result = new LinkedHashMap<>();
        for (var item : samples) {
            JsonObject sample = item.getAsJsonObject();
            if (result.put(identity(sample), sample) != null) throw new IllegalArgumentException("Duplicate sample");
        }
        return result;
    }

    private static void validateConfiguration(Map<String, String> candidate, Map<String, String> baseline,
                                               JsonObject archivedBaseline) {
        boolean interleaved = "true".equals(candidate.get("USE_ROLLOUT_NETWORK"));
        boolean afterBase = "true".equals(candidate.get("USE_ROLLOUT_NETWORK_AFTER_BASE"));
        if (interleaved == afterBase) throw new IllegalArgumentException("Enable exactly one independent network mode");
        for (String flag : new String[]{"USE_ROLLOUT_NETWORK", "USE_ROLLOUT_NETWORK_AFTER_BASE"}) {
            if (!"false".equals(baseline.get(flag))) {
                throw new IllegalArgumentException("Both network modes must be OFF in the historical baseline");
            }
        }
        if (!Integer.toString(MAX_EVALUATED).equals(candidate.get("ROLLOUT_NETWORK_MAX_CELLS"))
                || !Integer.toString(MAX_ROOTS).equals(candidate.get("ROLLOUT_NETWORK_ROOTS"))
                || !"12".equals(candidate.get("ROLLOUT_NETWORK_RESERVE_MS"))) {
            throw new IllegalArgumentException("Fixed network study requires maxCells=24, roots=2, reserveMs=12");
        }
        Map<String, String> expected = new java.util.TreeMap<>(baseline);
        expected.put(afterBase ? "USE_ROLLOUT_NETWORK_AFTER_BASE" : "USE_ROLLOUT_NETWORK", "true");
        if (!candidate.equals(expected)) throw new IllegalArgumentException("Candidate/base must differ only in the enabled network mode");
        for (var entry : archivedBaseline.entrySet()) {
            if (!entry.getValue().getAsString().equals(baseline.get(entry.getKey()))) {
                throw new IllegalArgumentException("Historical QuickIncome parameter changed: " + entry.getKey());
            }
        }
    }

    private static void requireSuccessfulTurn(CgReplay replay, int turn) {
        if (replay.failure(turn, 0) != null || replay.failure(turn, 1) != null) {
            throw new IllegalStateException("Fixed sample reached a failed-server turn; do not change or silently shrink the sample");
        }
    }

    private static Map<String, Object> knownOpportunity(JsonObject saved) {
        int missing = saved.get("bestOutsideRouteCell").getAsInt();
        int cell = missing >= 0 ? missing : saved.get("bestSingleCell").getAsInt();
        var result = new LinkedHashMap<String, Object>();
        result.put("cell", cell); result.put("fromOutsideOriginalRetainedRoutes", missing >= 0);
        result.put("bestSingleGain", saved.get("bestSingleGain").getAsInt());
        result.put("bestOutsideRouteGain", saved.get("bestOutsideRouteGain").getAsInt());
        result.put("bestOrdinaryRootGain", saved.get("bestRootGain").getAsInt());
        result.put("missingCellBeatsEveryOriginalRoot", saved.get("bestOutsideRouteGain").getAsInt() > saved.get("bestRootGain").getAsInt());
        result.put("positiveCellCount", saved.getAsJsonArray("positiveCells").size());
        for (var item : saved.getAsJsonArray("positiveCells")) {
            JsonObject positive = item.getAsJsonObject();
            if (positive.get("cell").getAsInt() == cell) {
                for (String key : new String[]{"x", "y", "paint", "gain"}) result.put(key, positive.get(key).getAsInt());
            }
        }
        return result;
    }

    private static Map<String, Object> inspect(GameState state, int me, String spec, long budget,
                                                JsonObject saved, boolean candidate, boolean afterBase) throws Exception {
        String before = ReplyStudy.stateHash(state);
        RolloutBot bot = (RolloutBot) BotFactory.create(spec); bot.init(state, me);
        long start = System.nanoTime(); List<Action> actions = bot.play(state, me, budget);
        double elapsed = (System.nanoTime() - start) / 1e6;
        if (!before.equals(ReplyStudy.stateHash(state))) throw new IllegalStateException("Policy mutated input");
        Rules.TurnResult tr = new Rules.TurnResult();
        Rules.expand(state, me, actions, new Pathing(state.size), tr.moves[me], tr);
        if (tr.moves[me].error != null || !tr.moves[me].skipped.isEmpty()) throw new IllegalStateException("Illegal policy output");
        if (bot.networkEligible < 0 || bot.networkEvaluated < 0 || bot.networkEvaluated > MAX_EVALUATED
                || bot.networkEvaluated > bot.networkEligible || bot.networkRoots < 0 || bot.networkRoots > MAX_ROOTS
                || bot.networkRoots > bot.networkEvaluated || bot.networkComparisons < 0
                || bot.networkComparisons > 2 * bot.networkRoots) {
            throw new IllegalStateException("Network work bounds violated");
        }
        if (!candidate && (bot.networkEligible != 0 || bot.networkEvaluated != 0 || bot.networkRoots != 0
                || bot.networkComparisons != 0 || bot.selectedNetwork || bot.selectedNetworkCell != -1)) {
            throw new IllegalStateException("Default-off network diagnostics must stay empty");
        }
        int[] counts = (int[]) CgDisruptStudy.field(bot, "counts");
        int[][] roots = (int[][]) CgDisruptStudy.field(bot, "roots");
        int[] rootCounts = (int[]) CgDisruptStudy.field(bot, "rootCounts");
        int[] targets = (int[]) CgDisruptStudy.field(bot, "rootTargets");
        int ordinary = (int) CgDisruptStudy.field(bot, "ordinaryRootSize");
        int[] cells = (int[]) CgDisruptStudy.field(bot, "networkCells");
        int[] gains = (int[]) CgDisruptStudy.field(bot, "networkGains");
        boolean[] compared = (boolean[]) CgDisruptStudy.field(bot, "networkCompared");
        int known = (int) knownOpportunity(saved).get("cell");
        boolean knownEligible = contains(cells, bot.networkEligible, known);
        boolean knownEvaluated = contains(cells, bot.networkEvaluated, known);
        int remaining = bot.completedComparisons, networkComplete = 0, knownRoot = -1, knownCovered = 0, knownExpected = 0;
        int[] rootCovered = new int[ordinary + bot.networkRoots];
        for (int order = 0; order < rootCovered.length; order++) {
            // Late mode finishes the ordinary order first, then visits appended network roots.
            int root = candidate && afterBase ? order
                    : (int) CgDisruptStudy.call(bot, "searchRoot", new Class<?>[]{int.class}, order);
            boolean duplicate = false;
            if (root < ordinary) for (int j = 0; j < root; j++) {
                if (sameSet(roots[root], rootCounts[root], roots[j], rootCounts[j])) duplicate = true;
            }
            if (duplicate) continue;
            int expected = targets[root] < 0 ? 1 : 2;
            int covered = Math.min(expected, Math.max(0, remaining)); remaining -= covered;
            rootCovered[root] = covered;
            if (root >= ordinary) {
                networkComplete += covered;
                if (rootCounts[root] != 1 || compared[root] != (covered > 0)) {
                    throw new IllegalStateException("Network root/comparison diagnostics inconsistent");
                }
                if (roots[root][0] == known) { knownRoot = root; knownCovered = covered; knownExpected = expected; }
            }
        }
        if (remaining != 0 || networkComplete != bot.networkComparisons) throw new IllegalStateException("Comparison coverage mismatch");
        if (candidate && afterBase && bot.networkSearchStarted && !bot.ordinarySearchComplete) {
            throw new IllegalStateException("Late network search started before ordinary comparison completed");
        }
        if (candidate && afterBase && !bot.networkSearchStarted && (bot.networkEligible != 0 || bot.networkEvaluated != 0
                || bot.networkRoots != 0 || bot.networkComparisons != 0 || bot.selectedNetwork)) {
            throw new IllegalStateException("Late network counters exist before networkSearchStarted");
        }
        if ((!candidate || afterBase) && bot.ordinaryComparisons != bot.completedComparisons - bot.networkComparisons) {
            throw new IllegalStateException("Ordinary comparison snapshot is inconsistent");
        }
        if (bot.selectedNetwork && (bot.selectedRoute < ordinary || bot.selectedRoute >= ordinary + bot.networkRoots
                || rootCovered[bot.selectedRoute] == 0 || tr.moves[me].count != 1
                || tr.moves[me].cells[0] != bot.selectedNetworkCell
                || roots[bot.selectedRoute][0] != bot.selectedNetworkCell)) {
            throw new IllegalStateException("Selected network cell does not match the legal fully-compared singleton root");
        }
        if (!bot.selectedNetwork && bot.selectedNetworkCell != -1) throw new IllegalStateException("Stale selected cell");
        String ordinaryAction = null;
        if (!candidate || afterBase) {
            if (bot.ordinarySelectedCount < 0 || bot.ordinarySelectedCount > GameState.PAINT_PER_TURN) {
                throw new IllegalStateException("Invalid saved ordinary placement count");
            }
            List<Action> ordinaryActions = new ArrayList<>(4);
            for (int i = 0; i < bot.ordinarySelectedCount; i++) {
                int cell = bot.ordinarySelectedCells[i];
                ordinaryActions.add(Action.place(state.x(cell), state.y(cell)));
            }
            if (bot.ordinarySelectedTarget >= 0) ordinaryActions.add(Action.disrupt(bot.ordinarySelectedTarget));
            if (ordinaryActions.isEmpty()) ordinaryActions.add(Action.waitAction());
            Rules.TurnResult ordinaryCheck = new Rules.TurnResult();
            Rules.expand(state, me, ordinaryActions, new Pathing(state.size), ordinaryCheck.moves[me], ordinaryCheck);
            if (ordinaryCheck.moves[me].error != null || !ordinaryCheck.moves[me].skipped.isEmpty()) {
                throw new IllegalStateException("Saved ordinary action is not legal on the same input");
            }
            ordinaryAction = Action.format(ordinaryActions);
            if (!bot.selectedNetwork && (!ordinaryAction.equals(Action.format(actions))
                    || bot.ordinarySelectedRoute != bot.selectedRoute || bot.ordinarySelectedTarget != bot.selectedTarget
                    || Double.compare(bot.ordinarySelectedValue, bot.selectedValue) != 0)) {
                throw new IllegalStateException("Final ordinary answer differs from its saved snapshot");
            }
            if (bot.selectedNetwork && !(bot.selectedValue > bot.ordinarySelectedValue)) {
                throw new IllegalStateException("Late network replacement did not strictly improve saved ordinary value");
            }
        }
        List<Map<String, Object>> evaluated = new ArrayList<>(), networkRoots = new ArrayList<>();
        for (int i = 0; i < bot.networkEvaluated; i++) {
            int cell = cells[i];
            var row = new LinkedHashMap<String, Object>(); row.put("cell", cell); row.put("gain", gains[cell]);
            row.put("positiveInArchivedProbe", archivedGain(saved, cell) > 0);
            if ((gains[cell] > 0 || archivedGain(saved, cell) > 0) && gains[cell] != archivedGain(saved, cell)) {
                throw new IllegalStateException("Exact network gain differs from archived core calculation");
            }
            evaluated.add(row);
        }
        for (int root = ordinary; root < ordinary + bot.networkRoots; root++) {
            var row = new LinkedHashMap<String, Object>(); row.put("index", root); row.put("cell", roots[root][0]);
            row.put("target", targets[root]); row.put("expectedComparisons", targets[root] < 0 ? 1 : 2);
            row.put("completedComparisons", rootCovered[root]); row.put("gain", gains[roots[root][0]]); networkRoots.add(row);
        }
        int[] income = new int[2]; Rules.incomePerTurn(state, income); int oldDiff = income[me] - income[1 - me];
        SearchState placementOnly = new SearchState(state); int[] empty = new int[0];
        Rules.applyPlacements(placementOnly.state, me == 0 ? tr.moves[me].cells : empty, me == 0 ? tr.moves[me].count : 0,
                me == 1 ? tr.moves[me].cells : empty, me == 1 ? tr.moves[me].count : 0);
        placementOnly.refresh(); Rules.incomePerTurn(placementOnly.state, income);
        var result = new LinkedHashMap<String, Object>();
        result.put("budgetMs", budget); result.put("elapsedMs", elapsed); result.put("action", Action.format(actions));
        result.put("selectedValue", finiteOrNull(bot.selectedValue));
        result.put("selectedCount", tr.moves[me].count);
        result.put("selectedCells", Arrays.copyOf(tr.moves[me].cells, tr.moves[me].count));
        result.put("ordinarySnapshotAvailable", !candidate || afterBase);
        result.put("ordinarySearchComplete", bot.ordinarySearchComplete);
        result.put("networkSearchStarted", bot.networkSearchStarted);
        result.put("ordinaryComparisons", bot.ordinaryComparisons);
        result.put("ordinarySelectedAction", ordinaryAction);
        result.put("ordinarySelectedValue", finiteOrNull(bot.ordinarySelectedValue));
        result.put("ordinarySelectedRoute", bot.ordinarySelectedRoute);
        result.put("ordinarySelectedTarget", bot.ordinarySelectedTarget);
        result.put("ordinarySelectedCount", bot.ordinarySelectedCount);
        result.put("ordinarySelectedCells", !candidate || afterBase
                ? Arrays.copyOf(bot.ordinarySelectedCells, bot.ordinarySelectedCount) : null);
        result.put("completedComparisons", bot.completedComparisons); result.put("routeQueries", bot.routeQueries);
        result.put("simulatedTurns", bot.simulatedTurns); result.put("selectedRoute", bot.selectedRoute);
        result.put("selectedTarget", bot.selectedTarget); result.put("enemyRouteCount", counts[1 - me]);
        result.put("ownRouteCount", counts[me]); result.put("networkEligible", bot.networkEligible);
        result.put("networkEvaluated", bot.networkEvaluated); result.put("networkRoots", bot.networkRoots);
        result.put("networkComparisons", bot.networkComparisons); result.put("selectedNetwork", bot.selectedNetwork);
        result.put("selectedNetworkCell", bot.selectedNetworkCell); result.put("networkEvaluatedCells", evaluated);
        result.put("networkRootDetails", networkRoots); result.put("knownCellEligible", knownEligible);
        result.put("knownCellEvaluated", knownEvaluated); result.put("knownCellExactGain", knownEvaluated ? gains[known] : null);
        result.put("knownCellRoot", knownRoot); result.put("knownCellExpectedComparisons", knownExpected);
        result.put("knownCellCompletedComparisons", knownCovered); result.put("knownCellCompared", knownCovered > 0);
        result.put("knownCellAllAttackComparisonsCompleted", knownRoot >= 0 && knownCovered == knownExpected);
        result.put("knownCellSelectedAsNetwork", known >= 0 && bot.selectedNetwork && bot.selectedNetworkCell == known);
        result.put("knownCellInOutput", contains(tr.moves[me].cells, tr.moves[me].count, known));
        result.put("placementOnlyNetIncomeGain", income[me] - income[1 - me] - oldDiff);
        return result;
    }

    private static int archivedGain(JsonObject saved, int cell) {
        for (var item : saved.getAsJsonArray("positiveCells")) {
            JsonObject positive = item.getAsJsonObject();
            if (positive.get("cell").getAsInt() == cell) return positive.get("gain").getAsInt();
        }
        return 0; // Archive enumerates every positive cell, but does not distinguish zero from negative.
    }

    private static boolean contains(int[] cells, int count, int target) {
        if (cells == null || target < 0) return false;
        for (int i = 0; i < count; i++) if (cells[i] == target) return true;
        return false;
    }

    private static boolean sameSet(int[] a, int n, int[] b, int m) {
        if (n != m) return false;
        int[] x = Arrays.copyOf(a, n), y = Arrays.copyOf(b, m); Arrays.sort(x); Arrays.sort(y);
        return Arrays.equals(x, y);
    }

    private static Double finiteOrNull(double value) { return Double.isFinite(value) ? value : null; }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> compareOrdinary(Map<String, Object> row) {
        var result = new LinkedHashMap<String, Object>();
        for (String mode : new String[]{"nominal", "ample"}) {
            Map<String, Object> baseline = (Map<String, Object>) ((Map<?, ?>) row.get("baseline")).get(mode);
            Map<String, Object> candidate = (Map<String, Object>) ((Map<?, ?>) row.get("candidate")).get(mode);
            var comparison = new LinkedHashMap<String, Object>();
            comparison.put("actionMatches", candidate.get("ordinarySelectedAction").equals(baseline.get("action")));
            comparison.put("cellsMatch", Arrays.equals((int[]) candidate.get("ordinarySelectedCells"), (int[]) baseline.get("selectedCells")));
            comparison.put("countMatches", candidate.get("ordinarySelectedCount").equals(baseline.get("selectedCount")));
            comparison.put("routeMatches", candidate.get("ordinarySelectedRoute").equals(baseline.get("selectedRoute")));
            comparison.put("targetMatches", candidate.get("ordinarySelectedTarget").equals(baseline.get("selectedTarget")));
            comparison.put("valueMatches", java.util.Objects.equals(candidate.get("ordinarySelectedValue"), baseline.get("selectedValue")));
            comparison.put("comparisonsMatch", candidate.get("ordinaryComparisons").equals(baseline.get("completedComparisons")));
            comparison.put("routeQueriesMatch", candidate.get("routeQueries").equals(baseline.get("routeQueries")));
            boolean exact = true;
            for (Object match : comparison.values()) if (!Boolean.TRUE.equals(match)) exact = false;
            comparison.put("allMatch", exact);
            comparison.put("candidateOrdinarySearchComplete", candidate.get("ordinarySearchComplete"));
            comparison.put("baselineOrdinarySearchComplete", baseline.get("ordinarySearchComplete"));
            if (mode.equals("ample") && (!exact || !Boolean.TRUE.equals(candidate.get("ordinarySearchComplete"))
                    || !Boolean.TRUE.equals(baseline.get("ordinarySearchComplete")))) {
                throw new IllegalStateException("Ample ordinary search differs from baseline at " + row.get("gameId") + " t" + row.get("turn"));
            }
            result.put(mode, comparison);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> summarize(List<Map<String, Object>> rows, boolean afterBase) {
        var summary = new LinkedHashMap<String, Object>();
        for (String mode : new String[]{"nominal", "ample"}) {
            var totals = new LinkedHashMap<String, Object>();
            int eligible = 0, evaluated = 0, roots = 0, compared = 0, selected = 0, changed = 0;
            int known = 0, knownEligible = 0, knownEvaluated = 0, knownRetained = 0, knownCompared = 0, knownSelected = 0;
            int baseComparisons = 0, candidateComparisons = 0, extraComparisons = 0, baseQueries = 0, candidateQueries = 0;
            int baseOrdinaryComplete = 0, candidateOrdinaryComplete = 0, lateStarted = 0, lateStartedUseful = 0;
            int ordinaryActionDifferences = 0, ordinaryComparisonDifferences = 0, ordinaryValueDifferences = 0;
            int ordinarySnapshotDifferences = 0, candidateOrdinaryComparisons = 0;
            for (Map<String, Object> row : rows) {
                Map<String, Object> base = (Map<String, Object>) ((Map<?, ?>) row.get("baseline")).get(mode);
                Map<String, Object> candidate = (Map<String, Object>) ((Map<?, ?>) row.get("candidate")).get(mode);
                if ((int) candidate.get("networkEligible") > 0) eligible++;
                if ((int) candidate.get("networkEvaluated") > 0) evaluated++;
                if ((int) candidate.get("networkRoots") > 0) roots++;
                if ((int) candidate.get("networkComparisons") > 0) compared++;
                if ((boolean) candidate.get("selectedNetwork")) selected++;
                if (!candidate.get("action").equals(base.get("action"))) changed++;
                if ((int) ((Map<?, ?>) row.get("knownOpportunity")).get("cell") >= 0) known++;
                if ((boolean) candidate.get("knownCellEligible")) knownEligible++;
                if ((boolean) candidate.get("knownCellEvaluated")) knownEvaluated++;
                if ((int) candidate.get("knownCellRoot") >= 0) knownRetained++;
                if ((boolean) candidate.get("knownCellCompared")) knownCompared++;
                if ((boolean) candidate.get("knownCellSelectedAsNetwork")) knownSelected++;
                baseComparisons += (int) base.get("completedComparisons"); candidateComparisons += (int) candidate.get("completedComparisons");
                extraComparisons += (int) candidate.get("networkComparisons");
                baseQueries += (int) base.get("routeQueries"); candidateQueries += (int) candidate.get("routeQueries");
                if (afterBase) {
                    if ((boolean) base.get("ordinarySearchComplete")) baseOrdinaryComplete++;
                    if ((boolean) candidate.get("ordinarySearchComplete")) candidateOrdinaryComplete++;
                    if ((boolean) candidate.get("networkSearchStarted")) {
                        lateStarted++;
                        if ((int) ((Map<?, ?>) row.get("knownOpportunity")).get("cell") >= 0) lateStartedUseful++;
                    }
                    Map<String, Object> comparison = (Map<String, Object>) ((Map<?, ?>) row.get("ordinaryVsBaseline")).get(mode);
                    if (!(boolean) comparison.get("actionMatches")) ordinaryActionDifferences++;
                    if (!(boolean) comparison.get("comparisonsMatch")) ordinaryComparisonDifferences++;
                    if (!(boolean) comparison.get("valueMatches")) ordinaryValueDifferences++;
                    if (!(boolean) comparison.get("allMatch")) ordinarySnapshotDifferences++;
                    candidateOrdinaryComparisons += (int) candidate.get("ordinaryComparisons");
                }
            }
            totals.put("statesWithEligibleCells", eligible); totals.put("statesWithEvaluatedCells", evaluated);
            totals.put("statesWithNetworkRoots", roots); totals.put("statesWithNetworkComparisons", compared);
            totals.put("statesSelectingNetwork", selected); totals.put("changedActions", changed);
            totals.put("statesWithKnownUsefulCell", known); totals.put("knownCellEligible", knownEligible);
            totals.put("knownCellEvaluated", knownEvaluated); totals.put("knownCellRetained", knownRetained);
            totals.put("knownCellCompared", knownCompared); totals.put("knownCellSelected", knownSelected);
            totals.put("baselineComparisons", baseComparisons); totals.put("candidateComparisons", candidateComparisons);
            totals.put("networkComparisons", extraComparisons); totals.put("baselineRouteQueries", baseQueries);
            totals.put("candidateRouteQueries", candidateQueries);
            if (afterBase) {
                totals.put("baselineOrdinaryCompleteStates", baseOrdinaryComplete);
                totals.put("candidateOrdinaryCompleteStates", candidateOrdinaryComplete);
                totals.put("statesStartingLateNetworkSearch", lateStarted);
                totals.put("knownUsefulStatesStartingLateNetworkSearch", lateStartedUseful);
                totals.put("candidateOrdinaryComparisons", candidateOrdinaryComparisons);
                totals.put("ordinaryActionDifferencesVsBaseline", ordinaryActionDifferences);
                totals.put("ordinaryComparisonCountDifferencesVsBaseline", ordinaryComparisonDifferences);
                totals.put("ordinaryValueDifferencesVsBaseline", ordinaryValueDifferences);
                totals.put("ordinarySnapshotDifferencesVsBaseline", ordinarySnapshotDifferences);
            }
            summary.put(mode, totals);
        }
        return summary;
    }
}
