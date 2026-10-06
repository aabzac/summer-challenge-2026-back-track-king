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
import btk.core.Rules;
import btk.core.SearchState;
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

/** Saved-state root/action diagnostic. Reuses the exact filler and rollout, without new proposals. */
public final class RolloutFillStudy {
    private static final long AMPLE_MS = 60_000;
    private static final int[] EMPTY = new int[0];
    private static final Class<?>[] STATE_PLAYER = {GameState.class, int.class};
    private static final Class<?>[] ROLLOUT = {GameState.class, int.class, int.class, int.class, int.class};
    record Tuple(int root, int target, int[] rawCells, int[] filledCells, String rawAction, String filledAction,
                 double rawValue, double filledValue, int[] rawIncomeVsWait, int[] filledIncomeVsWait) {}

    private static Object field(RolloutBot bot, String name) throws Exception { return CgDisruptStudy.field(bot, name); }
    private static Object call(RolloutBot bot, String name, Class<?>[] types, Object... args) throws Exception {
        return CgDisruptStudy.call(bot, name, types, args);
    }
    private static void set(RolloutBot bot, String name, Object value) throws Exception {
        var f = RolloutBot.class.getDeclaredField(name); f.setAccessible(true); f.set(bot, value);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    private static String setKey(int[] cells) { int[] copy = cells.clone(); Arrays.sort(copy); return Arrays.toString(copy); }
    private static String actionKey(String action) { String[] parts = action.split(";"); Arrays.sort(parts); return String.join(";", parts); }
    private static List<Action> actions(GameState state, int[] cells, int target) {
        List<Action> result = new ArrayList<>();
        for (int cell : cells) result.add(Action.place(state.x(cell), state.y(cell)));
        if (target >= 0) result.add(Action.disrupt(target));
        return result.isEmpty() ? List.of(Action.waitAction()) : result;
    }
    private static String legalAction(GameState state, int me, int[] cells, int target) {
        List<Action> actions = actions(state, cells, target); var check = new Rules.TurnResult();
        Rules.expand(state, me, actions, new Pathing(state.size), check.moves[me], check);
        require(check.moves[me].error == null && check.moves[me].skipped.isEmpty(), "Illegal diagnostic action");
        return Action.format(actions);
    }

    /** Target-specific fill; restore the real selected action even if the reflective call fails. */
    static int[] filled(RolloutBot bot, GameState state, int me, int[] raw, int target) throws Exception {
        int[] bestCells = (int[]) field(bot, "bestCells"), saved = bestCells.clone();
        int savedCount = (int) field(bot, "bestCount"), savedTarget = bot.selectedTarget, savedFill = bot.fillCells;
        try {
            System.arraycopy(raw, 0, bestCells, 0, raw.length); set(bot, "bestCount", raw.length); bot.selectedTarget = target;
            ((Timer) field(bot, "timer")).start(AMPLE_MS); call(bot, "fill", STATE_PLAYER, state, me);
            return Arrays.copyOf(bestCells, (int) field(bot, "bestCount"));
        } finally {
            System.arraycopy(saved, 0, bestCells, 0, saved.length); set(bot, "bestCount", savedCount);
            bot.selectedTarget = savedTarget; bot.fillCells = savedFill;
        }
    }

    private static double value(RolloutBot bot, GameState state, int me, int root, int target,
                                int[] cells, int replies, Params params) throws Exception {
        int[][] roots = (int[][]) field(bot, "roots"); int[] counts = (int[]) field(bot, "rootCounts");
        int[] saved = roots[root].clone(); int savedCount = counts[root];
        try {
            System.arraycopy(cells, 0, roots[root], 0, cells.length); counts[root] = cells.length;
            ((Timer) field(bot, "timer")).start(AMPLE_MS); double sum = 0, worst = Double.MAX_VALUE;
            for (int reply = 0; reply < replies; reply++) {
                double v = (double) call(bot, "rollout", ROLLOUT, state, me, root, target, reply);
                require(Double.isFinite(v), "Ample tuple rollout did not finish"); sum += v; worst = Math.min(worst, v);
            }
            return (1 - params.ROLLOUT_ROBUSTNESS) * sum / replies + params.ROLLOUT_ROBUSTNESS * worst;
        } finally { System.arraycopy(saved, 0, roots[root], 0, saved.length); counts[root] = savedCount; }
    }
    private static int[] incomeVsWait(GameState state, int me, int[] cells, int target) {
        SearchState board = new SearchState(state);
        board.resolve(me == 0 ? cells : EMPTY, me == 0 ? cells.length : 0, me == 0 ? target : -1,
                me == 1 ? cells : EMPTY, me == 1 ? cells.length : 0, me == 1 ? target : -1);
        int[] income = new int[2]; Rules.incomePerTurn(board.state, income); return income;
    }
    private static Map<String, Object> inspect(ReplyStudy.Position position, Params params) throws Exception {
        GameState state = position.state().copy(), saved = state.copy(); int me = position.me();
        RolloutBot bot = new RolloutBot(params); bot.init(state.copy(), me);
        String emitted = Action.format(bot.play(state, me, AMPLE_MS));
        require(bot.ordinarySearchComplete && bot.networkPhaseComplete, "Ample baseline search incomplete");
        require(!bot.selectedOpening && !params.USE_ROLLOUT_ALL_CELL_RISK, "Expected ordinary v09 with risk OFF");
        Bot frozen = BotFactory.create("v09?NOISE=0"); frozen.init(state.copy(), me);
        String frozenEmitted = Action.format(frozen.play(state, me, AMPLE_MS));
        RolloutBotV09 control = (RolloutBotV09) frozen;
        require(emitted.equals(frozenEmitted) && bot.selectedRoute == control.selectedRoute
                && bot.selectedTarget == control.selectedTarget && bot.selectedValue == control.selectedValue,
                "Current/v09 ample parity mismatch");
        ReplyProbe.sameState(saved, state);
        int ordinary = (int) field(bot, "ordinaryRootSize"), size = ordinary + bot.networkRoots;
        int[] portfolioCounts = (int[]) field(bot, "counts");
        int replies = Math.max(1, Math.min(params.ROLLOUT_REPLIES, portfolioCounts[1 - me]));
        int[][] roots = (int[][]) field(bot, "roots"); int[] counts = (int[]) field(bot, "rootCounts");
        int[] targets = (int[]) field(bot, "rootTargets");
        int baselineRoot = bot.selectedRoute < 0 ? portfolioCounts[me] : bot.selectedRoute;
        int baselineTarget = bot.selectedTarget, baselineComparisons = bot.completedComparisons;
        double baselineValue = bot.selectedValue; List<Tuple> tuples = new ArrayList<>(); int duplicates = 0;
        for (int order = 0; order < size; order++) {
            int root = (int) call(bot, "searchRoot", new Class<?>[]{int.class}, order);
            int[] raw = Arrays.copyOf(roots[root], counts[root]); boolean duplicate = false;
            for (int j = 0; j < root && root < ordinary; j++)
                if (setKey(raw).equals(setKey(Arrays.copyOf(roots[j], counts[j])))) duplicate = true;
            if (duplicate) { duplicates++; continue; }
            for (int attack = 0; attack < 2; attack++) {
                int target = attack == 0 ? targets[root] : -1; if (attack == 1 && targets[root] < 0) continue;
                int[] full = filled(bot, state, me, raw, target);
                tuples.add(new Tuple(root, target, raw, full, legalAction(state, me, raw, target), legalAction(state, me, full, target),
                        value(bot, state, me, root, target, raw, replies, params),
                        value(bot, state, me, root, target, full, replies, params),
                        incomeVsWait(state, me, raw, target), incomeVsWait(state, me, full, target)));
            }
        }
        require(tuples.size() == baselineComparisons, "Diagnostic did not reconstruct baseline comparisons");
        int rawBest = 0, fullBest = 0, filledTuples = 0, changedValues = 0, incomeChanges = 0, rankChanges = 0;
        for (int i = 0; i < tuples.size(); i++) {
            Tuple row = tuples.get(i);
            if (row.rawValue > tuples.get(rawBest).rawValue) rawBest = i;
            if (row.filledValue > tuples.get(fullBest).filledValue) fullBest = i;
            if (row.filledCells.length > row.rawCells.length) filledTuples++;
            if (Math.abs(row.filledValue - row.rawValue) > 1e-9) changedValues++;
            if (!Arrays.equals(row.rawIncomeVsWait, row.filledIncomeVsWait)) incomeChanges++;
            for (int j = 0; j < i; j++) if (Double.compare(row.rawValue, tuples.get(j).rawValue)
                    != Double.compare(row.filledValue, tuples.get(j).filledValue)) rankChanges++;
        }
        Tuple raw = tuples.get(rawBest), full = tuples.get(fullBest);
        require(raw.root == baselineRoot && raw.target == baselineTarget && Math.abs(raw.rawValue - baselineValue) < 1e-9,
                "Raw reranking did not reproduce baseline selection");
        require(raw.filledAction.equals(emitted), "Diagnostic filler did not reproduce real emitted action");
        ReplyProbe.sameState(saved, state); ReplyProbe.sameState(saved, position.state());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("game", position.game()); row.put("seed", position.seed()); row.put("me", me); row.put("sourcePolicy", position.policy());
        row.put("turn", state.turn); row.put("stateSha256", ReplyStudy.stateHash(state)); row.put("replies", replies);
        row.put("baselineAction", emitted); row.put("baselineValue", baselineValue); row.put("baselineSelectedTuple", rawBest);
        row.put("filledSelectedTuple", fullBest); row.put("baselineSelectedRawCells", raw.rawCells);
        row.put("baselineSelectedFilledCells", raw.filledCells); row.put("filledSelectedCells", full.filledCells);
        row.put("baselineSelectedAddsFill", raw.filledCells.length > raw.rawCells.length); row.put("selectedTupleChanged", rawBest != fullBest);
        row.put("fullActionChanged", !actionKey(emitted).equals(actionKey(full.filledAction)));
        row.put("selectedFilledValueGain", full.filledValue - raw.filledValue); row.put("rawDuplicateRootsSkipped", duplicates);
        row.put("filledTuples", filledTuples); row.put("changedValueTuples", changedValues); row.put("immediateIncomeChangedTuples", incomeChanges);
        row.put("pairwiseRankingChangesIncludingTies", rankChanges); row.put("tuples", tuples); return row;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("captureDir reportPath");
        Path capture = Path.of(args[0]), out = Path.of(args[1]); require(!Files.exists(out), "Choose a new report path");
        List<ReplyStudy.Position> positions = ReplyStudy.loadPositions(capture); require(positions.size() == 110, "Expected all 110 saved states");
        Params params = BotFactory.parameters("v09?NOISE=0");
        require(params.USE_ROLLOUT_FILL && !params.USE_ROLLOUT_ALL_CELL_RISK && !params.USE_ROLLOUT_DIVERSE_REPLIES
                && !params.USE_ROLLOUT_SECOND_TARGET && !params.USE_ROLLOUT_SPREAD && !params.USE_ROLLOUT_NETWORK_CHORDS,
                "Unexpected v09 configuration");
        List<Map<String, Object>> samples = new ArrayList<>(); Map<String, Integer> summary = new LinkedHashMap<>();
        for (ReplyStudy.Position position : positions) {
            Map<String, Object> row = inspect(position, params); samples.add(row);
            for (String key : List.of("baselineSelectedAddsFill", "selectedTupleChanged", "fullActionChanged"))
                summary.merge(key, Boolean.TRUE.equals(row.get(key)) ? 1 : 0, Integer::sum);
            for (String key : List.of("filledTuples", "changedValueTuples", "immediateIncomeChangedTuples", "pairwiseRankingChangesIncludingTies"))
                summary.merge(key, (Integer) row.get(key), Integer::sum);
            summary.merge("positionsWithAnyFilledTuple", (Integer) row.get("filledTuples") > 0 ? 1 : 0, Integer::sum);
            summary.merge("positionsWithRankingChange", (Integer) row.get("pairwiseRankingChangesIncludingTies") > 0 ? 1 : 0, Integer::sum);
            System.out.printf("Fill study %d/%d%n", samples.size(), positions.size());
        }
        Map<String, String> hashes = new LinkedHashMap<>();
        for (Path directory : List.of(capture, Path.of("src/main/java/btk/core"))) try (var files = Files.list(directory)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) hashes.put(file.toString(), ReplyStudy.hash(Files.readAllBytes(file)));
        }
        for (String name : List.of("tools/RolloutFillStudy.java", "tools/ReplyStudy.java", "tools/ReplyProbe.java", "tools/CgDisruptStudy.java",
                "bot/current/RolloutBot.java", "bot/current/ProtectedOpening.java", "bot/v09/RolloutBotV09.java", "bot/v09/ProtectedOpeningV09.java",
                "bot/Params.java", "bot/BotFactory.java", "bot/FrozenParams.java", "bot/Timer.java")) {
            Path file = Path.of("src/main/java/btk").resolve(name); hashes.put(file.toString(), ReplyStudy.hash(Files.readAllBytes(file)));
        }
        Map<String, Object> report = new LinkedHashMap<>(); report.put("schema", "rollout-fill-root-study-v1"); report.put("complete", true);
        report.put("createdAt", Instant.now().toString()); report.put("javaVersion", System.getProperty("java.version")); report.put("hashes", hashes);
        report.put("params", params); report.put("positions", samples.size()); report.put("ampleMsPerPlayOrTuple", AMPLE_MS);
        report.put("frozenV09ParityAndRawChoiceReconstruction", true); report.put("inputsUnchangedAndOutputsLegal", true);
        report.put("summary", summary); report.put("samples", samples);
        report.put("scope", "All 110 existing development positions in original capture order, on old v05/reply-probe trajectories; not a current-arena sample. Fresh current and frozen v09 with NOISE=0 and risk OFF. Existing baseline root/target tuples and duplicate pruning only; each target gets the unchanged filler. Only own turn-zero cells change in existing rollouts, preserving root continuation, enemy replies, depth and evaluation. Income after own action against WAIT is a mechanism counterfactual, not an opponent prediction. No recorded future actions/outcomes consulted, no live games, no strength or timing claim.");
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, ReplyStudy.JSON.toJson(report), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        System.out.println(ReplyStudy.JSON.toJson(summary));
    }
}
