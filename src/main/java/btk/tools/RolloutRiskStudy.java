package btk.tools;

import btk.bot.BotFactory;
import btk.bot.Params;
import btk.bot.Timer;
import btk.bot.current.RolloutBot;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.core.SearchState;
import btk.core.TacticalPathing;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bounded offline candidate-coverage probe. No recorded future action is consulted. */
public final class RolloutRiskStudy {
    private static final int MAX_STATES = 64, MAX_PAIRS = 64;
    private static final long AMPLE_MS = 60_000;
    private static final int[] EMPTY = new int[0];
    private static final Class<?>[] PACK = {GameState.class, int[].class, int.class, int[].class, int.class, int.class};
    private static final Class<?>[] INSERT = {GameState.class, int.class, int.class, double.class};
    private static final Class<?>[] FILL = {GameState.class, int.class, int.class, int[].class};
    private static final Class<?>[] SEARCH = {GameState.class, int.class, int.class, int.class, int.class};
    static final class Candidate {
        String kind, direction, routeSha256, rejection;
        int pair, routeLength, paint, estimatedBuildTurns, retainedRank = -1;
        int[] cells, actualFilledRootCells, completedIncome, afterHypotheticalInkIncome;
        boolean prefixAbsentFromBaselineRootSets, newVsBaselineRoutePrefixes, newActualFilledRoot,
                newVsMatchedEmptyPrefixes, proposalPass;
        double rawGain, protectedGain, blendedGain;
        Double proposalValue;
        transient int[] route;
    }
    private static final class Portfolio {
        final int[][] routes;
        final int[] lengths;
        final double[] values;
        final int count;
        Portfolio(RolloutBot bot, int me) throws Exception {
            routes = ((int[][][]) field(bot, "routes"))[me];
            int[][] copied = new int[routes.length][];
            for (int i = 0; i < routes.length; i++) copied[i] = routes[i].clone();
            // Keep independently owned buffers, not the bot's mutable route rows.
            this.saved = copied;
            lengths = ((int[][]) field(bot, "lengths"))[me].clone();
            values = ((double[][]) field(bot, "values"))[me].clone();
            count = ((int[]) field(bot, "counts"))[me];
        }
        final int[][] saved;
        void restore(RolloutBot bot, int me) throws Exception {
            for (int i = 0; i < routes.length; i++) System.arraycopy(saved[i], 0, routes[i], 0, saved[i].length);
            System.arraycopy(lengths, 0, ((int[][]) field(bot, "lengths"))[me], 0, lengths.length);
            System.arraycopy(values, 0, ((double[][]) field(bot, "values"))[me], 0, values.length);
            ((int[]) field(bot, "counts"))[me] = count;
        }
    }

    private static Object field(RolloutBot bot, String name) throws Exception { return CgDisruptStudy.field(bot, name); }
    private static Object call(RolloutBot bot, String name, Class<?>[] types, Object... args) throws Exception {
        return CgDisruptStudy.call(bot, name, types, args);
    }
    private static void set(RolloutBot bot, String name, Object value) throws Exception {
        var f = RolloutBot.class.getDeclaredField(name); f.setAccessible(true); f.set(bot, value);
    }
    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
    private static String setKey(int[] cells) { int[] sorted = cells.clone(); Arrays.sort(sorted); return Arrays.toString(sorted); }
    private static int[] income(GameState state) { int[] result = new int[2]; Rules.incomePerTurn(state, result); return result; }
    private static int difference(int[] income, int me) { return income[me] - income[1 - me]; }
    static boolean eligiblePair(GameState state, int me, int pair) {
        boolean earns = false, unstable = false;
        for (int i = 0; i < state.pairPathLen[pair]; i++) {
            int c = state.pairPath[pair][i], r = state.regionId[c];
            earns |= state.owner[c] == me;
            unstable |= !state.regionHasTown(r) && state.regionInstability[r] > 0;
        }
        return earns && unstable;
    }
    private static List<Integer> qualifying(GameState state, int me) {
        List<Integer> result = new ArrayList<>();
        for (int pair = 0; pair < state.pairCount; pair++) if (eligiblePair(state, me, pair)) result.add(pair);
        return result;
    }
    static int[] entryCosts(GameState state, Params params, boolean allCells) {
        int[] costs = new int[state.size];
        for (int c = 0; c < state.size; c++) costs[c] = (state.passable(c) ? 0 : state.placeCost(c))
                + (allCells || !state.passable(c) ? (int) Math.round(params.UNSTABLE_COST * state.instability[c]) : 0);
        return costs;
    }
    private static int[] prefix(RolloutBot bot, GameState state, int[] route) throws Exception {
        int[] result = new int[GameState.PAINT_PER_TURN];
        int n = (int) call(bot, "pack", PACK, state, route, route.length, result, 0, GameState.PAINT_PER_TURN);
        return Arrays.copyOf(result, n);
    }
    private static void legal(GameState state, int me, List<Action> actions) {
        Rules.PlayerMoves moves = new Rules.PlayerMoves();
        Rules.expand(state, me, actions, new Pathing(state.size), moves, new Rules.TurnResult());
        require(moves.error == null && moves.skipped.isEmpty(), "Illegal diagnostic action: " + moves.error + " " + moves.skipped);
    }
    private static List<Action> actions(GameState state, int[] cells, int target) {
        List<Action> result = new ArrayList<>();
        for (int c : cells) result.add(Action.place(state.x(c), state.y(c)));
        if (target >= 0) result.add(Action.disrupt(target));
        return result.isEmpty() ? List.of(Action.waitAction()) : result;
    }
    private static void place(SearchState board, int me, int[] cells) {
        Rules.applyPlacements(board.state, me == 0 ? cells : EMPTY, me == 0 ? cells.length : 0,
                me == 1 ? cells : EMPTY, me == 1 ? cells.length : 0);
        board.refresh();
    }
    private static int insertRank(RolloutBot bot, GameState state, int me, Portfolio baseline, Candidate row) throws Exception {
        baseline.restore(bot, me);
        try {
            System.arraycopy(row.route, 0, (int[]) field(bot, "path"), 0, row.route.length);
            call(bot, "insert", INSERT, state, me, row.route.length, row.proposalValue);
            int count = ((int[]) field(bot, "counts"))[me];
            int[][] routes = ((int[][][]) field(bot, "routes"))[me];
            int[] lengths = ((int[][]) field(bot, "lengths"))[me];
            for (int i = 0; i < count; i++) if (Arrays.equals(row.route, Arrays.copyOf(routes[i], lengths[i]))) {
                int[] cells = new int[GameState.PAINT_PER_TURN];
                int n = (int) call(bot, "fillBuild", FILL, state, me, i, cells);
                row.actualFilledRootCells = Arrays.copyOf(cells, n);
                legal(state, me, actions(state, row.actualFilledRootCells, -1));
                return i;
            }
            return -1;
        } finally { baseline.restore(bot, me); }
    }
    private static Candidate candidate(RolloutBot bot, GameState state, int me, Params params, Portfolio baseline,
                                       int pair, int[] route, String kind, String direction, Set<String> roots,
                                       Set<String> routePrefixes, Set<String> controls, int[] before, int[] destroyed, int threat) throws Exception {
        Candidate row = new Candidate(); row.kind = kind; row.direction = direction; row.pair = pair; row.route = route;
        row.routeLength = route.length; row.routeSha256 = ReplyStudy.hash(Arrays.toString(route).getBytes(StandardCharsets.UTF_8));
        row.cells = prefix(bot, state, route); legal(state, me, actions(state, row.cells, -1));
        row.prefixAbsentFromBaselineRootSets = row.cells.length > 0 && !roots.contains(setKey(row.cells));
        row.newVsBaselineRoutePrefixes = row.cells.length > 0 && !routePrefixes.contains(setKey(row.cells));
        row.newVsMatchedEmptyPrefixes = kind.equals("all-cell-risk") && !controls.contains(setKey(row.cells));
        int[] builds = Arrays.stream(route).filter(state::canBuild).toArray();
        for (int c : builds) row.paint += state.placeCost(c);
        SearchState completed = new SearchState(state); place(completed, me, builds);
        row.completedIncome = income(completed.state); row.rawGain = difference(row.completedIncome, me) - difference(before, me);
        if (threat >= 0) { Rules.ink(completed.state, threat); completed.refresh(); }
        row.afterHypotheticalInkIncome = income(completed.state);
        row.protectedGain = difference(row.afterHypotheticalInkIncome, me) - difference(destroyed, me);
        row.blendedGain = threat < 0 ? row.rawGain
                : (1 - params.ROLLOUT_THREAT_WEIGHT) * row.rawGain + params.ROLLOUT_THREAT_WEIGHT * row.protectedGain;
        row.estimatedBuildTurns = (row.paint + GameState.PAINT_PER_TURN - 1) / GameState.PAINT_PER_TURN;
        double earning = Math.min(params.ROLLOUT_ROUTE_HORIZON, state.turnsLeft()) - row.estimatedBuildTurns + 1;
        // The existing proposal's strategy filter/value, not a second scoring implementation.
        row.rejection = row.paint == 0 ? "NO_BUILD" : earning <= 0 ? "NO_EARNING_HORIZON"
                : row.blendedGain <= 0 ? "NONPOSITIVE_EXISTING_GAIN" : null;
        row.proposalPass = row.rejection == null;
        if (row.proposalPass) {
            row.proposalValue = row.blendedGain * earning / row.estimatedBuildTurns; // NOISE=0
            row.retainedRank = insertRank(bot, state, me, baseline, row);
            row.newActualFilledRoot = row.retainedRank >= 0 && !roots.contains(setKey(row.actualFilledRootCells));
            if (row.retainedRank < 0) row.rejection = "EXISTING_PREFIX_OR_WIDTH_PRUNING";
        }
        return row;
    }

    /** Only an actually retained route is evaluated; no expanded arrays or WAIT/root aliases. */
    private static Map<String, Object> rolloutProbe(RolloutBot bot, GameState state, int me, Portfolio baseline,
                                                   Candidate chosen, double baselineValue) throws Exception {
        Map<String, Object> report = new LinkedHashMap<>();
        if (chosen == null) { report.put("status", "NO_RETAINED_NOVEL_RISK_SPECIFIC_ROOT"); return report; }
        report.put("candidate", chosen); report.put("baselineFullPlayValue", baselineValue);
        if (chosen.retainedRank < 0) { report.put("status", "PRUNED_BEFORE_ROLLOUT"); return report; }
        int[][] roots = (int[][]) field(bot, "roots"); int[][] savedRoots = Arrays.stream(roots).map(int[]::clone).toArray(int[][]::new);
        int[] rootCounts = (int[]) field(bot, "rootCounts"), rootTargets = (int[]) field(bot, "rootTargets");
        int[] savedCounts = rootCounts.clone(), savedTargets = rootTargets.clone();
        int ordinary = (int) field(bot, "ordinaryRootSize");
        baseline.restore(bot, me);
        try {
            ((Timer) field(bot, "timer")).start(AMPLE_MS);
            System.arraycopy(chosen.route, 0, (int[]) field(bot, "path"), 0, chosen.route.length);
            call(bot, "insert", INSERT, state, me, chosen.route.length, chosen.proposalValue);
            int[] counts = (int[]) field(bot, "counts"); int size = counts[me] + 1, root = chosen.retainedRank;
            require(root < counts[me], "Candidate aliases WAIT");
            set(bot, "ordinaryRootSize", size);
            SearchState target = new SearchState(state);
            for (int i = 0; i < size; i++) {
                rootCounts[i] = i == counts[me] ? 0 : (int) call(bot, "fillBuild", FILL, state, me, i, roots[i]);
                target.set(state); place(target, me, Arrays.copyOf(roots[i], rootCounts[i]));
                rootTargets[i] = (int) call(bot, "chooseTarget", CgDisruptStudy.STATE_PLAYER, target.state, me);
            }
            int[] cells = Arrays.copyOf(roots[root], rootCounts[root]); legal(state, me, actions(state, cells, rootTargets[root]));
            report.put("actualFilledRootCells", cells); report.put("routeRank", root);
            set(bot, "best", -Double.MAX_VALUE); bot.selectedValue = Double.NaN; bot.selectedRoute = -1;
            int before = bot.completedComparisons;
            boolean complete = (boolean) call(bot, "searchRoots", SEARCH, state, me, root, root + 1,
                    Math.max(1, Math.min(BotFactory.parameters("v09").ROLLOUT_REPLIES, counts[1 - me])));
            int comparisons = bot.completedComparisons - before;
            report.put("complete", complete); report.put("comparisons", comparisons);
            report.put("status", !complete ? "INCOMPLETE" : comparisons == 0 ? "DUPLICATE_FILLED_ROOT" : "EVALUATED_EXISTING_ROLLOUT");
            report.put("rolloutValue", Double.isFinite(bot.selectedValue) ? bot.selectedValue : null);
            report.put("exceedsSavedBaselineValue", complete && comparisons > 0 && bot.selectedValue > baselineValue);
            report.put("selectedDisrupt", comparisons > 0 ? bot.selectedTarget : null);
            report.put("scope", "Isolated retained root; same enemy portfolio and private rollout. Portfolio insertion may displace a continuation; no full-policy selection claim.");
        } finally {
            baseline.restore(bot, me); set(bot, "ordinaryRootSize", ordinary);
            for (int i = 0; i < roots.length; i++) System.arraycopy(savedRoots[i], 0, roots[i], 0, roots[i].length);
            System.arraycopy(savedCounts, 0, rootCounts, 0, rootCounts.length); System.arraycopy(savedTargets, 0, rootTargets, 0, rootTargets.length);
        }
        return report;
    }

    private static Map<String, Object> inspect(ReplyStudy.Position position, Params params) throws Exception {
        GameState state = position.state(), unchanged = state.copy(); int me = position.me();
        RolloutBot bot = new RolloutBot(params); bot.init(state.copy(), me);
        long started = System.nanoTime(); List<Action> action = bot.play(state, me, AMPLE_MS);
        double elapsed = (System.nanoTime() - started) / 1e6; legal(state, me, action); ReplyProbe.sameState(unchanged, state);
        require(bot.ordinarySearchComplete && Double.isFinite(bot.selectedValue), "Ample baseline did not finish");
        Portfolio baseline = new Portfolio(bot, me); double baselineValue = bot.selectedValue;
        Set<String> roots = new HashSet<>(), routePrefixes = new HashSet<>();
        List<int[]> rootSets = new ArrayList<>(), baselinePrefixes = new ArrayList<>();
        for (int i = 0; i < baseline.count; i++) {
            int[] cells = prefix(bot, state, Arrays.copyOf(baseline.saved[i], baseline.lengths[i]));
            routePrefixes.add(setKey(cells)); baselinePrefixes.add(cells);
        }
        int[][] rootCells = (int[][]) field(bot, "roots"); int[] rootCounts = (int[]) field(bot, "rootCounts");
        int rootSize = (int) field(bot, "ordinaryRootSize") + bot.networkRoots + bot.chordRoots;
        for (int i = 0; i < rootSize; i++) { int[] cells = Arrays.copyOf(rootCells[i], rootCounts[i]); roots.add(setKey(cells)); rootSets.add(cells); }
        int[] before = income(state); int threat = ((int[]) field(bot, "threats"))[me];
        SearchState inked = new SearchState(state); if (threat >= 0) { Rules.ink(inked.state, threat); inked.refresh(); }
        int[] destroyed = income(inked.state);
        List<Candidate> proposals = new ArrayList<>(); List<Integer> pairs = qualifying(state, me);
        TacticalPathing tactical = new TacticalPathing(state.size); int[] scratch = new int[state.size];
        int[][] costs = {entryCosts(state, params, false), entryCosts(state, params, true)};
        int queries = 0, unreachable = 0; Candidate best = null;
        for (int pair : pairs.subList(0, Math.min(MAX_PAIRS, pairs.size()))) {
            Set<String> control = new HashSet<>();
            for (int kind = 0; kind < 2; kind++) {
                int n = tactical.weightedPath(state, state.townCell[state.pairFrom[pair]], state.townCell[state.pairTo[pair]], costs[kind], scratch);
                queries++; if (n < 0) { unreachable++; continue; }
                int[] route = Arrays.copyOf(scratch, n);
                for (int direction = 0; direction < 2; direction++) {
                    Candidate row = candidate(bot, state, me, params, baseline, pair, route.clone(),
                            kind == 0 ? "empty-only-control" : "all-cell-risk", direction == 0 ? "forward" : "reverse",
                            roots, routePrefixes, control, before, destroyed, threat);
                    proposals.add(row); if (kind == 0) control.add(setKey(row.cells));
                    if (kind == 1 && row.newVsBaselineRoutePrefixes && row.newActualFilledRoot
                            && row.newVsMatchedEmptyPrefixes && row.retainedRank >= 0
                            && (best == null || row.proposalValue > best.proposalValue)) best = row;
                    for (int a = 0, b = route.length - 1; a < b; a++, b--) { int c = route[a]; route[a] = route[b]; route[b] = c; }
                }
            }
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("game", position.game()); report.put("seed", position.seed()); report.put("me", me);
        report.put("sourcePolicy", position.policy()); report.put("turn", state.turn); report.put("stateSha256", ReplyStudy.stateHash(state));
        report.put("qualifyingPairs", pairs.size()); report.put("queriedPairs", Math.min(MAX_PAIRS, pairs.size())); report.put("pathQueries", queries);
        report.put("emptyOnlyQueries", queries / 2); report.put("allCellRiskQueries", queries / 2);
        report.put("unreachable", unreachable); report.put("baselineActions", Action.format(action)); report.put("baselineRootSets", rootSets);
        report.put("baselineRoutePrefixes", baselinePrefixes);
        report.put("baselineValue", baselineValue); report.put("baselineAmpleMs", elapsed); report.put("baselineIncome", before);
        report.put("predictedThreatRegion", threat); report.put("incomeAfterHypotheticalDestruction", destroyed);
        report.put("candidates", proposals); report.put("rolloutProbe", rolloutProbe(bot, state, me, baseline, best, baselineValue));
        ReplyProbe.sameState(unchanged, state); report.put("inputUnchanged", true); report.put("legalBaselineAndPrefixes", true);
        return report;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("captureDir reportPath");
        Path capture = Path.of(args[0]), out = Path.of(args[1]); require(!Files.exists(out), "Choose a new report path");
        Params params = BotFactory.parameters("v09"); params.NOISE = 0;
        require(!params.USE_ROLLOUT_ROUTE_DEPENDENCIES && !params.USE_ROLLOUT_SPREAD && !params.USE_ROLLOUT_REPLAN
                && !params.USE_ROLLOUT_QUICK_EARLY && !params.USE_ROLLOUT_NETWORK && !params.USE_ROLLOUT_DIVERSE_REPLIES
                && !params.USE_ROLLOUT_SECOND_TARGET, "Probe requires the unmodified v09 portfolio semantics");
        List<ReplyStudy.Position> all = ReplyStudy.loadPositions(capture), eligible = new ArrayList<>();
        for (var position : all) if (!qualifying(position.state(), position.me()).isEmpty()) eligible.add(position);
        List<Map<String, Object>> samples = new ArrayList<>();
        Map<String, Integer> summary = new LinkedHashMap<>();
        for (int i = 0; i < Math.min(MAX_STATES, eligible.size()); i++) {
            var sample = inspect(eligible.get(i), params); samples.add(sample);
            @SuppressWarnings("unchecked") List<Candidate> candidates = (List<Candidate>) sample.get("candidates");
            for (String kind : List.of("empty-only-control", "all-cell-risk")) {
                Set<String> novel = new HashSet<>(), survived = new HashSet<>(), distinct = new HashSet<>();
                for (Candidate row : candidates) if (row.kind.equals(kind) && row.newVsBaselineRoutePrefixes) {
                    novel.add(setKey(row.cells));
                    if (row.newActualFilledRoot && row.retainedRank >= 0) {
                        survived.add(setKey(row.actualFilledRootCells));
                        if (row.newVsMatchedEmptyPrefixes) distinct.add(setKey(row.actualFilledRootCells));
                    }
                }
                summary.merge(kind + ".novelSets", novel.size(), Integer::sum); summary.merge(kind + ".retainedNovelSets", survived.size(), Integer::sum);
                summary.merge(kind + ".positionsWithRetainedNovelSet", survived.isEmpty() ? 0 : 1, Integer::sum);
                if (kind.equals("all-cell-risk")) summary.merge("riskSpecificRetainedSets", distinct.size(), Integer::sum);
            }
            System.out.printf("Risk study %d/%d%n", i + 1, Math.min(MAX_STATES, eligible.size()));
        }
        Map<String, String> hashes = new LinkedHashMap<>();
        try (var files = Files.list(capture)) {
            for (Path p : files.filter(Files::isRegularFile).sorted().toList()) hashes.put(p.toString(), ReplyStudy.hash(Files.readAllBytes(p)));
        }
        for (String name : List.of("tools/RolloutRiskStudy.java", "tools/ReplyStudy.java", "tools/ReplyProbe.java", "tools/CgDisruptStudy.java",
                "bot/current/RolloutBot.java", "bot/current/ProtectedOpening.java", "bot/Params.java", "bot/Timer.java", "bot/BotFactory.java", "bot/FrozenParams.java",
                "core/Rules.java", "core/GameState.java", "core/Pathing.java", "core/TacticalPathing.java", "core/SearchState.java")) {
            Path p = Path.of("src/main/java/btk").resolve(name); hashes.put(p.toString(), ReplyStudy.hash(Files.readAllBytes(p)));
        }
        for (String name : List.of("src/main/resources/btk/bot/versions/v09.properties", "docs/rollout-risk-v1.md")) {
            Path p = Path.of(name); hashes.put(name, ReplyStudy.hash(Files.readAllBytes(p)));
        }
        Map<String, Object> report = new LinkedHashMap<>(); report.put("schema", "rollout-risk-study-v1"); report.put("complete", true);
        report.put("createdAt", Instant.now().toString()); report.put("javaVersion", System.getProperty("java.version")); report.put("hashes", hashes);
        report.put("params", params); report.put("availablePositions", all.size()); report.put("eligiblePositions", eligible.size());
        report.put("selectedPositions", samples.size()); report.put("maxPairsPerPosition", MAX_PAIRS); report.put("summary", summary); report.put("samples", samples);
        report.put("scope", "Past-state-only development probe; current RolloutBot with v09 parameters and NOISE=0. Matched tactical empty-only control separates routing geometry from risk on existing rails. Hypothetical complete-route placement/ink uses core, not an actual predicted reply. No games, outcome selection, strength or process timing claims.");
        Files.createDirectories(out.toAbsolutePath().getParent()); Files.writeString(out, ReplyStudy.JSON.toJson(report), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        System.out.println(ReplyStudy.JSON.toJson(summary));
    }
}
