package btk.tools;

import btk.bot.Params;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import com.google.gson.GsonBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Sequential prediction study only. Never feeds forecast decisions back into the recorded game. */
public final class CgForecastStudy {
    public static final class Metric {
        public int n, hits, targetTurns, targetHits;
        public double logLoss, brier;
        void add(double[] prediction, int target) {
            int y = target < 0 ? prediction.length - 1 : target;
            n++;
            boolean hit = SabotageForecast.top(prediction) == target;
            if (hit) hits++;
            if (target >= 0) { targetTurns++; if (hit) targetHits++; }
            logLoss -= Math.log(prediction[y]);
            for (int i = 0; i < prediction.length; i++) {
                double error = prediction[i] - (i == y ? 1 : 0);
                brier += error * error;
            }
        }
        public double meanLoss() { return logLoss / Math.max(1, n); }
        double accuracy() { return hits / (double) Math.max(1, n); }
    }
    public static final class Turn {
        public int turn, inferred, truth, fixedTop, adaptiveTop;
        public int[] experts;
        public double fixedProbability, adaptiveProbability, forecastMs;
        public double[] weightsBefore;
    }
    public static final class Game {
        public long gameId;
        public int observer, opponentAgent, unknown, terminalSkipped;
        public String opponent, split, source;
        public Metric adaptive = new Metric(), fixed = new Metric();
        public Metric[] experts = new Metric[SabotageForecast.EXPERTS.length];
        public List<Turn> turns = new ArrayList<>();
        Game() { Arrays.setAll(experts, i -> new Metric()); }
    }
    public static final class Report {
        public int schemaVersion = 1, observerAgent, bestDevelopmentExpert;
        public String method = "Predict before resolving the recorded turn; learn ONLY from next-state "
                + "instability/ink and our known accepted disruption. Opponent stdout is truth-audit only. "
                + "Reset each match; SHA-256 forecast-v1:opponentAgent bucket 0 of 3 is held out. "
                + "Existing previously inspected corpus: diagnostic holdout, not pristine confirmation. "
                + "Bootstrap intervals resample opponent groups; no evidence of bot win-rate improvement.";
        public Params params;
        public String[] expertNames = SabotageForecast.EXPERTS;
        public List<Game> games = new ArrayList<>();
        public double[] holdoutImprovementVsMixture, holdoutImprovementVsBestExpert;
    }

    public static void main(String[] args) throws Exception {
        Path input = ReplayPaths.resolve(Path.of(args.length > 0 ? args[0] : "replays/cg"));
        if (args.length < 2 || args[1].isBlank()) throw new IllegalArgumentException("Requires -Pagent=<arena agent>");
        int observerAgent = Integer.parseInt(args[1]);
        if (observerAgent <= 0) throw new IllegalArgumentException("Positive agent required");
        Report report = new Report(); report.observerAgent = observerAgent;
        report.params = new Params(args.length > 3 ? args[3] : "");
        for (Path file : Files.isDirectory(input) ? CgReplay.listReplays(input) : List.of(input)) {
            CgReplay replay = CgReplay.load(file);
            int observer = CgPlayerStudy.playerIndex(replay, observerAgent);
            if (observer < 0 || replay.isBossGame()) continue;
            var validation = CgValidate.validate(replay, false);
            if (validation.firstDivergenceTurn >= 0) throw new IllegalStateException("Invalid replay " + replay.gameId);
            Game game = analyze(replay, observer, report.params);
            game.source = file.toString();
            report.games.add(game);
        }
        if (report.games.isEmpty()) throw new IllegalStateException("No matching validated games");
        report.bestDevelopmentExpert = bestExpert(report.games);
        report.holdoutImprovementVsMixture = improvement(report.games, -1);
        report.holdoutImprovementVsBestExpert = improvement(report.games, report.bestDevelopmentExpert);
        Path out = Path.of(args.length > 2 && !args[2].isBlank() ? args[2] : "build/analysis/sabotage-forecast.json");
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        for (String split : List.of("development", "holdout")) {
            List<Game> games = report.games.stream().filter(g -> g.split.equals(split)).toList();
            int turns = games.stream().mapToInt(g -> g.adaptive.n).sum();
            System.out.printf(Locale.ROOT, "%s: %d games, %d opponents, %d observed turns%n", split, games.size(),
                    games.stream().map(g -> g.opponentAgent).distinct().count(), turns);
            for (int variant = -2; variant < SabotageForecast.EXPERTS.length; variant++) {
                final int v = variant;
                double loss = games.stream().mapToDouble(g -> metric(g, v).meanLoss()).average().orElseThrow();
                double hit = games.stream().mapToDouble(g -> metric(g, v).accuracy()).average().orElseThrow();
                System.out.printf(Locale.ROOT, "  %-10s macro log-loss %.4f top1 %.1f%%%n",
                        variant == -2 ? "adaptive" : variant == -1 ? "fixed" : SabotageForecast.EXPERTS[variant], loss, 100 * hit);
            }
        }
        System.out.println("Development-selected expert: " + SabotageForecast.EXPERTS[report.bestDevelopmentExpert]);
        System.out.println("Holdout loss improvement [mean, opponent-bootstrap 95% low, high]:");
        System.out.println("  vs fixed mixture " + Arrays.toString(report.holdoutImprovementVsMixture));
        System.out.println("  vs selected expert " + Arrays.toString(report.holdoutImprovementVsBestExpert));
        System.out.println("Report: " + out);
    }

    static Game analyze(CgReplay replay, int observer, Params params) throws Exception {
        Game game = new Game(); game.gameId = replay.gameId; game.observer = observer;
        game.opponentAgent = replay.agentIds[1 - observer]; game.opponent = replay.nicknames[1 - observer];
        game.split = split(game.opponentAgent);
        GameState state = replay.toState();
        Pathing pathing = new Pathing(state.size); Rules.updateConnections(state, pathing);
        SabotageForecast model = new SabotageForecast(state, params);
        for (int t = 0; t < replay.turns(); t++) {
            if (replay.failure(t, 0) != null || replay.failure(t, 1) != null) { game.terminalSkipped++; break; }
            GameState before = state.copy();
            Turn row = new Turn(); row.turn = t; row.weightsBefore = model.weights.clone();
            long start = System.nanoTime();
            model.predict(before, 1 - observer);
            row.forecastMs = (System.nanoTime() - start) / 1e6;
            row.fixedTop = SabotageForecast.top(model.fixed); row.adaptiveTop = SabotageForecast.top(model.adaptive);
            row.experts = model.choices.clone();
            // Only the harness advances using both command lines. The model never receives them.
            List<Action> own = CgPlayerStudy.effectiveActions(replay, t, observer);
            var ownMoves = new Rules.PlayerMoves();
            Rules.expand(before, observer, own, pathing, ownMoves, new Rules.TurnResult());
            int ownDisrupt = ownMoves.error == null && !ownMoves.disrupts.isEmpty() ? ownMoves.disrupts.get(0) : -1;
            var result = Rules.resolve(state, CgPlayerStudy.effectiveActions(replay, t, 0),
                    CgPlayerStudy.effectiveActions(replay, t, 1), pathing);
            row.inferred = model.inferObservation(before, state, ownDisrupt);
            var opp = result.moves[1 - observer];
            row.truth = opp.error == null && !opp.disrupts.isEmpty() ? opp.disrupts.get(0) : -1;
            if (row.truth >= 0 && before.regionInked[row.truth]) row.truth = -1;
            if (row.inferred == SabotageForecast.UNKNOWN) game.unknown++;
            else {
                if (row.inferred != row.truth) throw new IllegalStateException("Observation mismatch " + replay.gameId + " t" + t);
                int category = row.inferred < 0 ? state.regionCount : row.inferred;
                row.fixedProbability = model.fixed[category]; row.adaptiveProbability = model.adaptive[category];
                game.fixed.add(model.fixed, row.inferred); game.adaptive.add(model.adaptive, row.inferred);
                for (int e = 0; e < game.experts.length; e++) game.experts[e].add(model.probabilities[e], row.inferred);
            }
            model.observe(row.inferred);
            game.turns.add(row);
        }
        return game;
    }

    static String split(int opponentAgent) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(
                ("forecast-v1:" + opponentAgent).getBytes(StandardCharsets.UTF_8));
        return Byte.toUnsignedInt(hash[0]) % 3 == 0 ? "holdout" : "development";
    }
    private static Metric metric(Game g, int variant) { return variant == -2 ? g.adaptive : variant == -1 ? g.fixed : g.experts[variant]; }
    private static int bestExpert(List<Game> games) {
        int best = -1; double bestLoss = Double.POSITIVE_INFINITY;
        for (int e = 0; e < SabotageForecast.EXPERTS.length; e++) {
            final int expert = e;
            double loss = games.stream().filter(g -> g.split.equals("development") && g.adaptive.n > 0)
                    .mapToDouble(g -> g.experts[expert].meanLoss()).average().orElseThrow();
            if (loss < bestLoss) { bestLoss = loss; best = e; }
        }
        return best;
    }
    /** Mean per-opponent improvement; bootstrap opponents, not correlated turns. */
    static double[] improvement(List<Game> games, int baseline) {
        Map<Integer, List<Double>> grouped = new TreeMap<>();
        for (Game g : games) if (g.split.equals("holdout") && g.adaptive.n > 0) {
            grouped.computeIfAbsent(g.opponentAgent, k -> new ArrayList<>())
                    .add(metric(g, baseline).meanLoss() - g.adaptive.meanLoss());
        }
        double[] deltas = grouped.values().stream().mapToDouble(v -> v.stream().mapToDouble(x -> x).average().orElseThrow()).toArray();
        if (deltas.length < 2) throw new IllegalStateException("Need at least two holdout opponents");
        double[] bootstrap = new double[2000]; Random rng = new Random(15092026);
        for (int b = 0; b < bootstrap.length; b++) {
            for (int i = 0; i < deltas.length; i++) bootstrap[b] += deltas[rng.nextInt(deltas.length)] / deltas.length;
        }
        Arrays.sort(bootstrap);
        return new double[]{Arrays.stream(deltas).average().orElseThrow(), bootstrap[49], bootstrap[1949]};
    }
}
