package btk.tools;

import btk.bot.Bot;
import btk.bot.BotFactory;
import btk.bot.Params;
import btk.bot.Timer;
import btk.bot.current.RolloutBot;
import btk.bot.v09.RolloutBotV09;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Protocol;
import btk.core.Rules;
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

/** Offline recorded-proposal diagnostic; no neural inference or new played trajectories.
 * All candidate values call the unchanged rollout. Common values use retained own route 0,
 * not future replanning and not the recorded neural/opponent continuation. */
public final class NeuralProposalStudy {
    static final long AMPLE_MS = 60_000;
    static final int[] TURNS = {3, 10, 20, 30, 40, 50, 60, 70};
    static final String BUILD = "9158eae9029544f3b1984d543c36c1e5d3bd73bfbcbb5ba3a4f633a77da13267";
    static final String SOURCE = "2aee3ffec84a06dd314179cdf6d97475e87cf3dc8bd01aab81f27291a6bc563f";
    private static final Class<?>[] ROLLOUT = {GameState.class, int.class, int.class, int.class, int.class};
    record Plan(int[] cells, int target, String action) {
        String constructionKey() { int[] copy = cells.clone(); Arrays.sort(copy); return Arrays.toString(copy); }
        String key() { return constructionKey() + ":" + target; }
    }
    record Score(double value, double[] replyValues) {}
    record Tuple(int order, int root, Plan raw, Plan filled, Score nativeRaw,
                 Score commonRaw, Score commonFilled) {}
    record Suppressed(int order, int root, Plan raw, Plan filled) {}
    record Position(int slot, long gameId, String seed, String mapSha256, int me,
                    GameState state, String recordedAction) {}
    record Loaded(List<Position> positions, List<Map<String, Object>> omissions,
                  List<Map<String, Object>> games) {}

    static void require(boolean ok, String message) {
        if (!ok) throw new IllegalStateException(message);
    }
    static Object field(RolloutBot bot, String name) throws Exception { return CgDisruptStudy.field(bot, name); }
    private static Object call(RolloutBot bot, String name, Class<?>[] types, Object... args) throws Exception {
        return CgDisruptStudy.call(bot, name, types, args);
    }
    private static long timerField(Timer timer, String name, Long replacement) throws Exception {
        var f = Timer.class.getDeclaredField(name); f.setAccessible(true);
        long old = f.getLong(timer); if (replacement != null) f.setLong(timer, replacement); return old;
    }
    static Plan accepted(GameState state, int me, String command) {
        require(command != null, "Missing recorded command");
        var tr = new Rules.TurnResult();
        Rules.expand(state, me, Action.parseLine(command), new Pathing(state.size), tr.moves[me], tr);
        var m = tr.moves[me];
        require(!tr.unknownActions && m.error == null && m.skipped.isEmpty() && m.disrupts.size() <= 1,
                "Noncanonical diagnostic command: " + command + " error=" + m.error + " skipped=" + m.skipped);
        return canonical(state, Arrays.copyOf(m.cells, m.count), m.disrupts.isEmpty() ? -1 : m.disrupts.get(0));
    }
    private static Plan canonical(GameState state, int[] cells, int target) {
        List<Action> actions = new ArrayList<>();
        for (int c : cells) actions.add(Action.place(state.x(c), state.y(c)));
        if (target >= 0) actions.add(Action.disrupt(target));
        if (actions.isEmpty()) actions.add(Action.waitAction());
        return new Plan(cells.clone(), target, Action.format(actions));
    }
    static Plan legal(GameState state, int me, int[] cells, int target) {
        Plan p = canonical(state, cells, target), checked = accepted(state, me, p.action);
        require(Arrays.equals(cells, checked.cells) && target == checked.target, "Plan expansion changed");
        return p;
    }
    static boolean suppressed(int root, int ordinary, int[][] roots, int[] counts) {
        for (int j = 0; j < root && root < ordinary; j++)
            if (ReplyProbe.sameCells(Arrays.copyOf(roots[root], counts[root]), Arrays.copyOf(roots[j], counts[j]))) return true;
        return false;
    }
    static Plan filled(RolloutBot bot, GameState state, int me, Plan raw) throws Exception {
        Timer timer = (Timer) field(bot, "timer");
        long start = timerField(timer, "startNs", null), deadline = timerField(timer, "deadlineNs", null);
        try {
            int[] cells = RolloutFillStudy.filled(bot, state, me, raw.cells, raw.target);
            require(!timer.up(), "Ample target-specific fill incomplete");
            return legal(state, me, cells, raw.target);
        } finally { timerField(timer, "startNs", start); timerField(timer, "deadlineNs", deadline); }
    }

    /** Common continuation is obtained by borrowing the existing WAIT root (index counts[me]).
     * That index selects ownFirst=0 in the actual private rollout. Native reconstruction instead
     * supplies its original root index. The original reply portfolio is never regenerated. */
    static Score evaluate(RolloutBot bot, GameState state, int me, Plan plan, int root,
                          int replies, Params params) throws Exception {
        legal(state, me, plan.cells, plan.target);
        require(replies > 0 && !params.USE_ROLLOUT_ROOT_FILL && !params.USE_ROLLOUT_REPLAN
                && !params.USE_ROLLOUT_SPREAD && !params.USE_ROLLOUT_DIVERSE_REPLIES,
                "Unsupported diagnostic continuation");
        int[][] roots = (int[][]) field(bot, "roots"); int[] counts = (int[]) field(bot, "rootCounts");
        int[] saved = roots[root].clone(); int savedCount = counts[root], simulated = bot.simulatedTurns;
        Timer timer = (Timer) field(bot, "timer");
        long start = timerField(timer, "startNs", null), deadline = timerField(timer, "deadlineNs", null);
        GameState before = state.copy();
        try {
            System.arraycopy(plan.cells, 0, roots[root], 0, plan.cells.length); counts[root] = plan.cells.length;
            require(Arrays.equals(plan.cells, Arrays.copyOf(roots[root], counts[root])), "Injected root readback mismatch");
            timer.start(AMPLE_MS); double sum = 0, worst = Double.MAX_VALUE;
            double[] values = new double[replies];
            for (int reply = 0; reply < replies; reply++) {
                values[reply] = (double) call(bot, "rollout", ROLLOUT, state, me, root, plan.target, reply);
                require(Double.isFinite(values[reply]) && !timer.up(), "Incomplete/nonfinite ample rollout");
                sum += values[reply]; worst = Math.min(worst, values[reply]);
            }
            return new Score((1 - params.ROLLOUT_ROBUSTNESS) * sum / replies + params.ROLLOUT_ROBUSTNESS * worst, values);
        } finally {
            System.arraycopy(saved, 0, roots[root], 0, saved.length); counts[root] = savedCount;
            timerField(timer, "startNs", start); timerField(timer, "deadlineNs", deadline); bot.simulatedTurns = simulated;
            require(Arrays.equals(saved, roots[root]) && counts[root] == savedCount, "Root restoration mismatch");
            ReplyProbe.sameState(before, state);
        }
    }
    private static Score common(RolloutBot bot, GameState state, int me, Plan plan, int replies, Params params,
                                Map<String, Score> cache) throws Exception {
        Score score = cache.get(plan.key());
        if (score == null) {
            score = evaluate(bot, state, me, plan, ((int[]) field(bot, "counts"))[me], replies, params);
            cache.put(plan.key(), score);
        }
        return score;
    }

    static Map<String, Object> coverage(Plan neural, List<Tuple> tuples, List<Suppressed> skipped) {
        int raw = 0, full = 0, rawBuild = 0, fullBuild = 0, suppressedRaw = 0, suppressedFull = 0, suppressedBuild = 0;
        for (Tuple t : tuples) {
            if (t.raw.key().equals(neural.key())) raw++;
            if (t.filled.key().equals(neural.key())) full++;
            if (t.raw.constructionKey().equals(neural.constructionKey())) rawBuild++;
            if (t.filled.constructionKey().equals(neural.constructionKey())) fullBuild++;
        }
        for (Suppressed t : skipped) {
            if (t.raw.key().equals(neural.key())) suppressedRaw++;
            if (t.filled.key().equals(neural.key())) suppressedFull++;
            if (t.filled.constructionKey().equals(neural.constructionKey())) suppressedBuild++;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rawFullPlanMatches", raw); out.put("filledFullPlanMatches", full);
        out.put("rawConstructionMatches", rawBuild); out.put("filledConstructionMatches", fullBuild);
        out.put("suppressedRawFullPlanMatches", suppressedRaw); out.put("suppressedFilledFullPlanMatches", suppressedFull);
        out.put("suppressedFilledConstructionMatches", suppressedBuild);
        out.put("novelFullPlan", full == 0 && suppressedFull == 0);
        out.put("novelConstruction", fullBuild == 0 && suppressedBuild == 0);
        out.put("targetOnlyNovelty", full == 0 && suppressedFull == 0 && fullBuild + suppressedBuild > 0);
        out.put("coveredOnlyAfterFill", raw == 0 && full > 0); return out;
    }

    static Map<String, Object> inspect(Position position, Params params) throws Exception {
        GameState state = position.state.copy(), before = state.copy(); int me = position.me;
        Plan neural = accepted(state, me, position.recordedAction);
        RolloutBot bot = new RolloutBot(params); bot.init(state.copy(), me);
        String emitted = Action.format(bot.play(state, me, AMPLE_MS));
        require(bot.ordinarySearchComplete && bot.networkPhaseComplete && !bot.selectedOpening,
                "Expected complete ordinary baseline, after its protected opening");
        Plan actual = accepted(state, me, emitted);
        Bot reference = BotFactory.create("v09?NOISE=0"); reference.init(state.copy(), me);
        String frozenAction = Action.format(reference.play(state, me, AMPLE_MS));
        RolloutBotV09 frozen = (RolloutBotV09) reference;
        require(emitted.equals(frozenAction) && bot.selectedRoute == frozen.selectedRoute
                && bot.selectedTarget == frozen.selectedTarget && bot.selectedValue == frozen.selectedValue
                && frozen.ordinarySearchComplete && frozen.networkPhaseComplete && !frozen.selectedOpening,
                "Current/frozen v09 parity failed");
        int[] portfolios = (int[]) field(bot, "counts");
        int replies = Math.max(1, Math.min(params.ROLLOUT_REPLIES, portfolios[1 - me]));
        int ordinary = (int) field(bot, "ordinaryRootSize"), size = ordinary + bot.networkRoots;
        int[][] roots = (int[][]) field(bot, "roots"); int[] counts = (int[]) field(bot, "rootCounts");
        int[] targets = (int[]) field(bot, "rootTargets");
        String portfolioBefore = portfolioIdentity(bot);
        int selectedRoot = bot.selectedRoute < 0 ? portfolios[me] : bot.selectedRoute;
        int selectedTarget = bot.selectedTarget, comparisons = bot.completedComparisons;
        double selectedValue = bot.selectedValue;
        List<Tuple> tuples = new ArrayList<>(); List<Suppressed> skipped = new ArrayList<>();
        Map<String, Score> cache = new LinkedHashMap<>();
        for (int order = 0; order < size; order++) {
            // prune-v1: searchRoot left current with the quick-early/spread/network experiments; the v09
            // baseline reproduced here never reordered roots, so the search order is the root index.
            int root = order;
            int[] cells = Arrays.copyOf(roots[root], counts[root]);
            boolean duplicate = suppressed(root, ordinary, roots, counts);
            for (int attack = 0; attack < 2; attack++) {
                int target = attack == 0 ? targets[root] : -1;
                if (attack == 1 && targets[root] < 0) continue;
                Plan raw = legal(state, me, cells, target), full = filled(bot, state, me, raw);
                if (duplicate) { skipped.add(new Suppressed(order, root, raw, full)); continue; }
                Score nativeScore = evaluate(bot, state, me, raw, root, replies, params);
                tuples.add(new Tuple(order, root, raw, full, nativeScore,
                        common(bot, state, me, raw, replies, params, cache),
                        common(bot, state, me, full, replies, params, cache)));
            }
        }
        require(tuples.size() == comparisons && !tuples.isEmpty(), "Native comparison count mismatch");
        int winner = 0, bestFull = 0;
        for (int i = 1; i < tuples.size(); i++) {
            if (tuples.get(i).nativeRaw.value > tuples.get(winner).nativeRaw.value) winner = i;
            if (tuples.get(i).commonFilled.value > tuples.get(bestFull).commonFilled.value) bestFull = i;
        }
        Tuple selected = tuples.get(winner);
        require(selected.root == selectedRoot && selected.raw.target == selectedTarget
                && Math.abs(selected.nativeRaw.value - selectedValue) < 1e-9,
                "Native raw winner reconstruction failed");
        require(selected.filled.action.equals(emitted), "Native target-specific fill/emitted action mismatch");
        // Independently evaluate actual and neural actions even when their identity is already cached.
        Score actualScore = evaluate(bot, state, me, actual, portfolios[me], replies, params);
        Score neuralScore = evaluate(bot, state, me, neural, portfolios[me], replies, params);
        require(Arrays.equals(actualScore.replyValues, selected.commonFilled.replyValues), "Actual baseline comparison mismatch");
        if (cache.containsKey(neural.key())) require(Arrays.equals(cache.get(neural.key()).replyValues, neuralScore.replyValues),
                "Equal complete actions received unequal common scores");
        require(portfolioBefore.equals(portfolioIdentity(bot)), "Probe changed the retained portfolio/root storage");
        require(bot.selectedRoute == (selectedRoot == portfolios[me] ? -1 : selectedRoot)
                && bot.selectedTarget == selectedTarget && bot.selectedValue == selectedValue
                && bot.completedComparisons == comparisons, "Probe changed native selected diagnostics");
        ReplyProbe.sameState(before, state); ReplyProbe.sameState(before, position.state);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("slot", position.slot); row.put("gameId", position.gameId); row.put("seed", position.seed);
        row.put("mapSha256", position.mapSha256); row.put("me", me); row.put("turn", state.turn);
        row.put("stateSha256", ReplyStudy.stateHash(state)); row.put("recordedCommand", position.recordedAction);
        row.put("neuralPlan", neural); row.put("baselinePlan", actual);
        row.put("nativeSelectedTuple", winner); row.put("nativeSelectedValue", selectedValue);
        row.put("bestCommonFilledTuple", bestFull); row.put("baselineCommonScore", actualScore);
        row.put("neuralCommonScore", neuralScore); row.put("bestCommonFilledScore", tuples.get(bestFull).commonFilled);
        row.put("neuralMinusEmittedBaseline", neuralScore.value - actualScore.value);
        row.put("neuralMinusBestFilled", neuralScore.value - tuples.get(bestFull).commonFilled.value);
        Map<String, Object> coverage = coverage(neural, tuples, skipped); row.put("coverage", coverage);
        row.put("novelModelPreferred", Boolean.TRUE.equals(coverage.get("novelFullPlan"))
                && neuralScore.value - tuples.get(bestFull).commonFilled.value > 1e-9);
        row.put("replies", replies); row.put("portfolioCounts", portfolios.clone());
        row.put("portfolioSha256", ReplyStudy.hash(portfolioBefore.getBytes(StandardCharsets.UTF_8)));
        row.put("ordinaryRoots", ordinary); row.put("networkRoots", bot.networkRoots);
        row.put("nativeCompletedComparisons", comparisons); row.put("tuples", tuples); row.put("suppressedTuples", skipped);
        row.put("uniqueRawActions", tuples.stream().map(t -> t.raw.key()).distinct().count());
        row.put("uniqueFilledActions", tuples.stream().map(t -> t.filled.key()).distinct().count());
        row.put("frozenV09Parity", true); row.put("inputAndPortfolioUnchanged", true); return row;
    }
    private static String portfolioIdentity(RolloutBot bot) throws Exception {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (String name : List.of("routes", "lengths", "values", "counts", "roots", "rootCounts", "rootTargets"))
            snapshot.put(name, field(bot, name));
        return ReplyStudy.JSON.toJson(snapshot);
    }
    private static JsonObject json(Path p) throws Exception { return JsonParser.parseString(Files.readString(p)).getAsJsonObject(); }
    static String hash(Path p) throws Exception { return ReplyStudy.hash(Files.readAllBytes(p)); }
    static void checkedHash(Path p, String expected) throws Exception { require(hash(p).equals(expected), "SHA256 mismatch: " + p.getFileName()); }

    /** Entire input validation completes before main invokes inspect even once. No outcome filtering. */
    static Loaded load(Path selectionPath) throws Exception {
        JsonObject selection = json(selectionPath);
        require(selection.get("schema").getAsString().equals("neural-proposal-selection-v1")
                && selection.get("policy").getAsString().equals("neural-v3")
                && selection.get("buildId").getAsString().equals(BUILD)
                && selection.get("sourceSha256").getAsString().equals(SOURCE), "Wrong recorded policy/build");
        int[] turns = ReplyStudy.JSON.fromJson(selection.get("turns"), int[].class);
        require(Arrays.equals(turns, TURNS), "Changed predeclared turn schedule");
        Path sourcePath = Path.of(selection.get("sourcePlanPath").getAsString());
        checkedHash(sourcePath, selection.get("sourcePlanSha256").getAsString());
        JsonObject source = json(sourcePath), build = source.getAsJsonObject("builds").getAsJsonObject("candidate");
        require(BUILD.equals(build.get("buildId").getAsString()) && SOURCE.equals(build.get("sourceSha256").getAsString())
                && "neural-v3".equals(build.get("strategy").getAsString()), "Historical build mismatch");
        List<JsonObject> fixed = new ArrayList<>(); int[] seats = new int[2];
        for (var item : source.getAsJsonArray("slots")) {
            JsonObject slot = item.getAsJsonObject(); int me = slot.get("seat").getAsInt();
            if ("candidate".equals(slot.get("arm").getAsString()) && seats[me] < 4) { fixed.add(slot); seats[me]++; }
        }
        require(fixed.size() == 8 && selection.getAsJsonArray("games").size() == 8, "Expected first four games per seat");
        List<Position> positions = new ArrayList<>(); List<Map<String, Object>> omissions = new ArrayList<>(), games = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            JsonObject item = selection.getAsJsonArray("games").get(i).getAsJsonObject(), slot = fixed.get(i);
            int slotId = item.get("slot").getAsInt(), me = item.get("me").getAsInt();
            String seed = item.get("seed").getAsString(); long gameId = item.get("gameId").getAsLong();
            require(slotId == slot.get("slot").getAsInt() && me == slot.get("seat").getAsInt()
                    && seed.equals(slot.get("seed").getAsString()), "Selection differs from original schedule order");
            Path file = Path.of(item.get("file").getAsString()); checkedHash(file, item.get("sha256").getAsString());
            Path resultFile = sourcePath.toAbsolutePath().getParent().resolve(String.format("%04d/result.json", slotId));
            JsonObject result = json(resultFile), capture = result.getAsJsonObject("capture"), attribution = capture.getAsJsonObject("attribution");
            require(result.get("planId").getAsString().equals(source.get("planId").getAsString())
                    && capture.get("ok").getAsBoolean() && capture.get("expectedBuildMatches").getAsBoolean()
                    && capture.get("gameId").getAsLong() == gameId && capture.get("player").getAsInt() == me
                    && capture.get("rawSha256").getAsString().equals(item.get("sha256").getAsString())
                    && attribution.get("retainedArtifactVerified").getAsBoolean()
                    && BUILD.equals(attribution.get("buildId").getAsString())
                    && SOURCE.equals(attribution.get("artifactSha256").getAsString())
                    && "marker-matches-local-artifact".equals(attribution.get("status").getAsString()),
                    "Historical capture attribution mismatch");
            CgReplay replay = CgReplay.load(file);
            require(replay.gameId == gameId && replay.mapSeed().isPresent()
                    && Long.toString(replay.mapSeed().getAsLong()).equals(seed) && replay.arenaBosses[1 - me], "Replay identity mismatch");
            for (int t = 0; t < replay.turns(); t++) for (int p = 0; p < 2; p++)
                require(replay.failure(t, p) == null && replay.turnStdout.get(t)[p] != null, "Failed/missing replay action");
            CgValidate.Outcome validation = CgValidate.validate(replay, false);
            require(validation.firstDivergenceTurn < 0, "CG reconstruction failed: " + validation.reason);
            GameState state = replay.toState(); state.maxTurns = Math.max(state.maxTurns, replay.turns());
            Pathing pathing = new Pathing(state.size); Rules.updateConnections(state, pathing);
            String mapHash = ReplyStudy.hash(Protocol.initBlock(state, 0).getBytes(StandardCharsets.UTF_8));
            int next = 0, skippedHistoricalActions = 0;
            for (int t = 0; t < replay.turns(); t++) {
                require(!Rules.gameOver(state, pathing), "Recorded actions after terminal");
                if (next < turns.length && t == turns[next]) {
                    accepted(state, me, replay.turnStdout.get(t)[me]); // fail closed before ANY policy calls
                    positions.add(new Position(slotId, gameId, seed, mapHash, me, state.copy(), replay.turnStdout.get(t)[me])); next++;
                }
                var tr = Rules.resolve(state, Action.parseLine(replay.turnStdout.get(t)[0]),
                        Action.parseLine(replay.turnStdout.get(t)[1]), pathing);
                require(!tr.unknownActions && tr.moves[0].error == null && tr.moves[1].error == null, "Historical command error");
                skippedHistoricalActions += tr.moves[0].skipped.size() + tr.moves[1].skipped.size();
            }
            require(Rules.gameOver(state, pathing) && Arrays.equals(state.score, replay.finalScores), "Replay terminal/final score mismatch");
            while (next < turns.length) omissions.add(Map.of("slot", slotId, "gameId", gameId, "me", me,
                    "turn", turns[next++], "terminalTurn", state.turn, "reason", "recorded game already terminal"));
            Map<String, Object> game = new LinkedHashMap<>();
            game.put("slot", slotId); game.put("gameId", gameId); game.put("seed", seed); game.put("me", me);
            game.put("mapSha256", mapHash); game.put("replaySha256", item.get("sha256").getAsString());
            game.put("historicalCaptureSha256", hash(resultFile)); game.put("turns", replay.turns());
            game.put("historicalSkippedActions", skippedHistoricalActions); game.put("cgValidated", true);
            game.put("executionIdentityIndependentlyVerified", false); games.add(game);
        }
        require(positions.size() + omissions.size() == 64, "Incomplete requested panel accounting");
        return new Loaded(positions, omissions, games);
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("selection.json fresh-report.json");
        Path selection = Path.of(args[0]), out = Path.of(args[1]); require(!Files.exists(out), "Choose a new report path");
        Map<String, Object> report = new LinkedHashMap<>(); List<Map<String, Object>> samples = new ArrayList<>();
        report.put("schema", "neural-proposal-audit-v1"); report.put("createdAt", Instant.now().toString());
        report.put("selectionSha256", hash(selection)); report.put("buildId", BUILD); report.put("sourceSha256", SOURCE);
        report.put("javaVersion", System.getProperty("java.version")); report.put("ampleMsPerPlayOrTuple", AMPLE_MS);
        report.put("scope", "Saved neural-v3 trajectories; shared retained route-0 continuation and original reply portfolio. Native root-specific raw scores are separate. No future recorded actions enter comparison. Historical marker attribution is not independent proof of remote executed source. No neural inference, new games, strength or runtime claim.");
        try {
            Loaded loaded = load(selection); Params params = BotFactory.parameters("v09?NOISE=0");
            require(params.NOISE == 0 && params.USE_ROLLOUT_FILL && params.USE_PROTECTED_OPENING && params.OPENING_TURNS == 3
                    && params.USE_ROLLOUT_NETWORK_AFTER_BASE && !params.USE_ROLLOUT_NETWORK && !params.USE_ROLLOUT_ROOT_FILL
                    && !params.USE_ROLLOUT_ALL_CELL_RISK && !params.USE_ROLLOUT_DIVERSE_REPLIES && !params.USE_ROLLOUT_SECOND_TARGET
                    && !params.USE_ROLLOUT_REPLAN && !params.USE_ROLLOUT_SPREAD && !params.USE_ROLLOUT_NETWORK_CHORDS,
                    "Unexpected v09 configuration");
            report.put("params", params); report.put("games", loaded.games); report.put("omissions", loaded.omissions);
            report.put("requestedPositions", 64);
            for (Position p : loaded.positions) {
                samples.add(inspect(p, params));
                System.out.printf("Neural proposal study %d/%d game=%d turn=%d%n", samples.size(), loaded.positions.size(), p.gameId, p.state.turn);
            }
            Map<String, Object> summary = new LinkedHashMap<>(); List<Map<String, Object>> perGame = new ArrayList<>();
            for (Map<String, Object> game : loaded.games) {
                long id = ((Number) game.get("gameId")).longValue(); int n = 0, novel = 0, preferred = 0, targetOnly = 0, afterFill = 0;
                for (Map<String, Object> row : samples) if (((Number) row.get("gameId")).longValue() == id) {
                    n++; @SuppressWarnings("unchecked") Map<String, Object> c = (Map<String, Object>) row.get("coverage");
                    if (Boolean.TRUE.equals(c.get("novelFullPlan"))) novel++;
                    if (Boolean.TRUE.equals(row.get("novelModelPreferred"))) preferred++;
                    if (Boolean.TRUE.equals(c.get("targetOnlyNovelty"))) targetOnly++;
                    if (Boolean.TRUE.equals(c.get("coveredOnlyAfterFill"))) afterFill++;
                }
                perGame.add(Map.of("gameId", id, "positions", n, "omissions", 8 - n, "novelFullPlans", novel,
                        "novelModelPreferred", preferred, "targetOnlyNovelty", targetOnly, "coveredOnlyAfterFill", afterFill));
            }
            for (String key : List.of("novelFullPlans", "novelModelPreferred", "targetOnlyNovelty", "coveredOnlyAfterFill")) {
                int sum = 0; for (Map<String, Object> game : perGame) sum += ((Number) game.get(key)).intValue(); summary.put(key, sum);
            }
            report.put("summary", summary); report.put("perGame", perGame);
            report.put("status", "complete"); report.put("complete", true);
        } catch (Exception e) {
            report.put("status", "failed"); report.put("complete", false); report.put("error", e.toString()); throw e;
        } finally {
            report.put("positions", samples.size()); report.put("samples", samples);
            Files.createDirectories(out.toAbsolutePath().getParent());
            Files.writeString(out, ReplyStudy.JSON.toJson(report), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        }
    }
}
