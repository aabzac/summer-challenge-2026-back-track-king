package btk.tools;

import btk.bot.BotFactory;
import btk.bot.current.RolloutBot;
import btk.core.*;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.*;

/** Shared recorded states: candidate coverage, choice and one-turn oracle diagnostics, never strength. */
public final class QuickRouteStudy {
    public static void main(String[] args) throws Exception {
        Path input = Path.of(args[0]), output = Path.of(args[2]);
        if (Files.exists(output)) throw new IllegalArgumentException("Preserve existing report");
        String spec = args[1] + ",NOISE=0";
        var cohort = JsonParser.parseString(Files.readString(input)).getAsJsonObject();
        List<Map<String,Object>> rows = new ArrayList<>();
        for (var item : cohort.getAsJsonArray("games")) {
            var entry = item.getAsJsonObject();
            if (!entry.get("userReportedVersion").getAsString().equals("v06")) continue;
            var game = entry.getAsJsonObject("game");
            Path file = Path.of(game.get("file").getAsString());
            if (!ReplyStudy.hash(Files.readAllBytes(file)).equals(entry.get("sha256").getAsString()))
                throw new IllegalStateException("Replay changed");
            var replay = CgReplay.load(file);
            if (CgValidate.validate(replay, false).firstDivergenceTurn >= 0)
                throw new IllegalStateException("Replay does not validate");
            GameState s = replay.toState(); Pathing pathing = new Pathing(s.size);
            Rules.updateConnections(s, pathing);
            int me = game.get("me").getAsInt();
            for (int t = 0; t < Math.min(20, replay.turns()); t++) {
                if (replay.failure(t, 0) != null || replay.failure(t, 1) != null) break;
                var a0 = CgPlayerStudy.effectiveActions(replay, t, 0);
                var a1 = CgPlayerStudy.effectiveActions(replay, t, 1);
                int[] income = new int[2]; Rules.incomePerTurn(s, income);
                if (income[me] == 0) {
                    String before = ReplyStudy.stateHash(s);
                    var row = new LinkedHashMap<String,Object>();
                    row.put("gameId", replay.gameId); row.put("outcome", game.get("outcome").getAsString());
                    row.put("turn", t); row.put("me", me); row.put("stateSha256", before);
                    row.put("nominal", inspect(s, me, spec, t == 0 ? 486 : 36, me == 0 ? a1 : a0));
                    row.put("ample", inspect(s, me, spec, 60_000, me == 0 ? a1 : a0));
                    if (!before.equals(ReplyStudy.stateHash(s))) throw new IllegalStateException("Input mutated");
                    rows.add(row);
                }
                Rules.resolve(s, a0, a1, pathing);
            }
        }
        var report = new LinkedHashMap<String,Object>();
        report.put("spec", spec); report.put("params", BotFactory.configuration(spec));
        report.put("inputSha256", ReplyStudy.hash(Files.readAllBytes(input)));
        report.put("classSha256", ReplyStudy.hash(Files.readAllBytes(Path.of("build/classes/java/main/btk/bot/current/RolloutBot.class"))));
        report.put("method", "All own-zero-income pre-turn states in turns 0..19 of all 14 user-labelled v06 boss games, wins and losses. "
                + "Fresh noise-free instances, nominal 486/36 ms vs 60000 ms, init outside clock. This is not original arena RNG/timing. "
                + "Coverage reconstructed from completed comparisons and actual search order; a completed comparison covers every opponent reply. "
                + "Recorded opponent actions are used only AFTER choosing for independent one-turn oracle counterfactuals. "
                + "Never sum these as recoverable match points. No strength or process readiness claim.");
        report.put("samples", rows);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, ReplyStudy.JSON.toJson(report), StandardOpenOption.CREATE_NEW);
        System.out.println("Saved " + rows.size() + " shared states to " + output);
    }

    private static Map<String,Object> inspect(GameState s, int me, String spec, long budget,
                                              List<Action> recordedEnemy) throws Exception {
        var bot = (RolloutBot) BotFactory.create(spec); bot.init(s, me);
        long start = System.nanoTime(); var actions = bot.play(s, me, budget);
        double elapsed = (System.nanoTime() - start) / 1e6;
        int[] counts = (int[]) CgDisruptStudy.field(bot, "counts");
        int[][] roots = (int[][]) CgDisruptStudy.field(bot, "roots");
        int[] sizes = (int[]) CgDisruptStudy.field(bot, "rootCounts");
        int[] targets = (int[]) CgDisruptStudy.field(bot, "rootTargets");
        int quick = (int) CgDisruptStudy.field(bot, "quickSlot");
        int remaining = bot.completedComparisons, covered = 0, expected = 0;
        for (int order = 0; order <= counts[me]; order++) {
            int root = (int) CgDisruptStudy.call(bot, "searchRoot", new Class<?>[]{int.class}, order);
            boolean duplicate = false;
            for (int j = 0; j < root; j++) if (same(roots[root], sizes[root], roots[j], sizes[j])) duplicate = true;
            if (duplicate) continue;
            int comparisons = targets[root] < 0 ? 1 : 2;
            if (root == quick) { expected = comparisons; covered = Math.min(comparisons, Math.max(0, remaining)); }
            remaining -= comparisons;
        }
        GameState after = s.copy();
        var resolved = Rules.resolve(after, me == 0 ? actions : recordedEnemy,
                me == 1 ? actions : recordedEnemy, new Pathing(s.size));
        if (resolved.hasError(me) || !resolved.moves[me].skipped.isEmpty())
            throw new IllegalStateException("Illegal candidate action");
        int[] income = new int[2]; Rules.incomePerTurn(after, income);
        var result = new LinkedHashMap<String,Object>();
        result.put("budgetMs", budget); result.put("elapsedMs", elapsed);
        result.put("quickSlot", quick); result.put("quickPaint", CgDisruptStudy.field(bot, "quickCost"));
        result.put("quickGain", CgDisruptStudy.field(bot, "quickGain"));
        result.put("quickExpectedComparisons", expected); result.put("quickCompletedComparisons", covered);
        result.put("selectedQuick", quick >= 0 && bot.selectedRoute == quick);
        result.put("completedComparisons", bot.completedComparisons); result.put("routeQueries", bot.routeQueries);
        result.put("selectedRoute", bot.selectedRoute); result.put("action", Action.format(actions));
        result.put("oracleOneTurnScoreDifferenceGain", after.score[me] - s.score[me] - after.score[1-me] + s.score[1-me]);
        result.put("oracleIncome", income); return result;
    }

    private static boolean same(int[] a, int n, int[] b, int m) {
        if (n != m) return false;
        int[] x = Arrays.copyOf(a, n), y = Arrays.copyOf(b, m); Arrays.sort(x); Arrays.sort(y);
        return Arrays.equals(x, y);
    }
}
