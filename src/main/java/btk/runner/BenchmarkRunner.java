package btk.runner;

import btk.bot.Bot;
import btk.bot.BotFactory;
import btk.core.GameState;
import btk.core.Protocol;
import btk.referee.Referee;
import com.google.gson.GsonBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * Two games on each map, A as P0 and P1, with fresh bots for each game.
 * Map pairs run in parallel. Draws count as half a win; uncertainty is grouped by map.
 * Args: bot1 bot2 games [mapsDir] [threads] [baseSeed] [reportJson]
 */
public final class BenchmarkRunner {
    /** Scores inside each result are indexed by player position, not by A/B. */
    public record PairResult(int index, long seed, String map, String mapHash,
                             Referee.Result aAsP0, Referee.Result aAsP1) {}

    public static final class Stats {
        public final String a, b;
        public final Map<String, String> paramsA, paramsB;
        public final String startedAt = Instant.now().toString();
        public final String javaVersion = System.getProperty("java.version");
        public long baseSeed, elapsedMs;
        public int threads;
        public String mapsInput = "";
        public int games, winsA, winsB, draws, errorsA, errorsB;
        public int winsAasP0, gamesAasP0, winsAasP1, gamesAasP1;
        public long sumDiff;
        public long maxMsA, maxMsB, sumMsA, sumMsB;
        public int slowA, slowB, turns;
        public final List<String> notes = new ArrayList<>();
        public final List<PairResult> pairs = new ArrayList<>();
        public final Map<String, Integer> gamesPerMap = new LinkedHashMap<>();

        Stats(String a, String b) {
            this.a = a;
            this.b = b;
            paramsA = BotFactory.configuration(a);
            paramsB = BotFactory.configuration(b);
        }

        /** Win rate of A with draws counted as half, in [0,1]. */
        public double rateA() {
            return games == 0 ? 0.5 : (winsA + 0.5 * draws) / games;
        }

        /** Legacy per-game approximation; diagnostic only because games on one map are paired. */
        public double[] wilson95() {
            if (games == 0) return new double[]{0, 1};
            double z = 1.96, n = games, p = rateA();
            double denom = 1 + z * z / n;
            double centre = p + z * z / (2 * n);
            double half = z * Math.sqrt(p * (1 - p) / n + z * z / (4 * n * n));
            return new double[]{(centre - half) / denom, (centre + half) / denom};
        }

        /**
         * Conservative 95% Hoeffding bound with independent map groups and scores in [0,1].
         * Each map's weight is gamesOnMap / totalGames. Reusing a map, including both sides,
         * does not add independent evidence: epsilon = sqrt(log(40) * sum(weight^2) / 2).
         */
        public double[] confidence95() {
            if (games == 0 || gamesPerMap.isEmpty()) return new double[]{0, 1};
            double squaredWeights = 0;
            for (int count : gamesPerMap.values()) {
                double weight = (double) count / games;
                squaredWeights += weight * weight;
            }
            double half = Math.sqrt(Math.log(40) * squaredWeights / 2);
            return new double[]{Math.max(0, rateA() - half), Math.min(1, rateA() + half)};
        }

        public boolean promotionGatePassed() {
            return games >= 200 && errorsA == 0 && errorsB == 0
                    && rateA() >= 0.55 && confidence95()[0] > 0.5;
        }

        public String report() {
            double[] ci = confidence95(), legacy = wilson95();
            StringBuilder sb = new StringBuilder();
            sb.append("=".repeat(64)).append('\n');
            sb.append(String.format("  %s vs %s (%d games, %d pairs, %d distinct maps)%n",
                    a, b, games, pairs.size(), gamesPerMap.size()));
            sb.append(String.format("  A win rate: %.1f%%  W-L-D %d-%d-%d%n", rateA() * 100, winsA, winsB, draws));
            sb.append(String.format("  map-grouped 95%% CI (Hoeffding): %.1f%% .. %.1f%%%n", ci[0] * 100, ci[1] * 100));
            sb.append(String.format("  legacy per-game Wilson (diagnostic only): %.1f%% .. %.1f%%%n", legacy[0] * 100, legacy[1] * 100));
            sb.append(String.format("  A as P0: %d/%d (%.1f%%)   A as P1: %d/%d (%.1f%%)%n",
                    winsAasP0, gamesAasP0, pct(winsAasP0, gamesAasP0), winsAasP1, gamesAasP1, pct(winsAasP1, gamesAasP1)));
            sb.append(String.format("  avg score diff (A-B): %+.1f   avg turns: %.1f%n",
                    games == 0 ? 0 : (double) sumDiff / games, games == 0 ? 0 : (double) turns / games));
            sb.append(String.format("  time/move ms  A avg %.2f max %d slow %d | B avg %.2f max %d slow %d%n",
                    avg(sumMsA), maxMsA, slowA, avg(sumMsB), maxMsB, slowB));
            if (errorsA + errorsB > 0) sb.append(String.format("  ERRORS: A %d, B %d%n", errorsA, errorsB));
            for (String note : notes) sb.append("  ").append(note).append('\n');
            sb.append("  statistical promotion gate: ").append(promotionGatePassed() ? "PASS" : "NOT PASSED").append('\n');
            sb.append("  Process timing and an older opponent still need separate checks.\n");
            return sb.append("=".repeat(64)).toString();
        }

        public void write(Path file) throws Exception {
            Files.createDirectories(file.toAbsolutePath().getParent());
            var gson = new GsonBuilder().setPrettyPrinting().create();
            var json = gson.toJsonTree(this).getAsJsonObject();
            json.addProperty("schemaVersion", 1);
            json.addProperty("confidenceMethod", "map-grouped Hoeffding 95%; assumes independent representative maps");
            json.add("confidence95", gson.toJsonTree(confidence95()));
            json.addProperty("promotionGatePassed", promotionGatePassed());
            Files.writeString(file, gson.toJson(json));
        }

        private void add(PairResult pair) {
            pairs.add(pair);
            gamesPerMap.merge(pair.mapHash(), 2, Integer::sum);
            addGame(pair, pair.aAsP0(), 0);
            addGame(pair, pair.aAsP1(), 1);
        }

        private void addGame(PairResult pair, Referee.Result result, int pa) {
            int pb = 1 - pa;
            games++;
            turns += result.turns;
            sumDiff += result.score[pa] - result.score[pb];
            if (result.winner == pa) winsA++;
            else if (result.winner == pb) winsB++;
            else draws++;
            if (pa == 0) {
                gamesAasP0++;
                if (result.winner == pa) winsAasP0++;
            } else {
                gamesAasP1++;
                if (result.winner == pa) winsAasP1++;
            }
            if (result.error[pa] != null) {
                errorsA++;
                if (notes.size() < 10) notes.add(pair.map() + " A as P" + pa + " error: " + result.error[pa]);
            }
            if (result.error[pb] != null) {
                errorsB++;
                if (notes.size() < 10) notes.add(pair.map() + " B as P" + pb + " error: " + result.error[pb]);
            }
            maxMsA = Math.max(maxMsA, result.maxMs[pa]);
            maxMsB = Math.max(maxMsB, result.maxMs[pb]);
            sumMsA += result.totalMs[pa];
            sumMsB += result.totalMs[pb];
            slowA += result.slowMoves[pa];
            slowB += result.slowMoves[pb];
        }

        private double avg(long sumMs) { return turns == 0 ? 0 : (double) sumMs / turns; }
        private static double pct(int w, int n) { return n == 0 ? 0 : 100.0 * w / n; }
    }

    public static Stats run(String specA, String specB, int games, Maps maps, int threads, long baseSeed) throws Exception {
        return run(specA, specB, games, maps, threads, baseSeed, BotFactory::create);
    }

    // Factory injection lets tests observe the actual states and player positions.
    static Stats run(String specA, String specB, int games, Maps maps, int threads, long baseSeed,
                     Function<String, Bot> bots) throws Exception {
        if (games < 2 || games % 2 != 0) throw new IllegalArgumentException("games must be positive and even (two per map)");
        if (threads < 1) throw new IllegalArgumentException("threads must be positive");
        long lastSeed = Math.addExact(baseSeed, games / 2L - 1);
        if (!maps.fromReplays() && baseSeed <= 0 && lastSeed >= 0) {
            throw new IllegalArgumentException("generated seed range must not contain zero");
        }
        Stats stats = new Stats(specA, specB); // Validate specs before starting workers.
        stats.baseSeed = baseSeed;
        stats.threads = threads;
        long started = System.nanoTime();
        var pool = Executors.newFixedThreadPool(threads);
        List<Future<PairResult>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < games / 2; i++) {
                final int pairIndex = i;
                futures.add(pool.submit(() -> {
                    long seed = baseSeed + pairIndex;
                    GameState initial = maps.get(pairIndex, seed);
                    String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(Protocol.initBlock(initial, 0).getBytes(StandardCharsets.UTF_8)));
                    Referee referee = new Referee();
                    Referee.Result first = referee.play(initial, bots.apply(specA), bots.apply(specB), seed, false);
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException("benchmark cancelled");
                    Referee.Result second = referee.play(initial, bots.apply(specB), bots.apply(specA), seed, false);
                    return new PairResult(pairIndex, seed, maps.name(pairIndex, seed), hash, first, second);
                }));
            }
            for (Future<PairResult> future : futures) {
                stats.add(future.get());
                if (stats.games % 50 == 0 || stats.games == games) {
                    System.out.printf("  %d/%d  A %.1f%%%n", stats.games, games, stats.rateA() * 100);
                }
            }
        } finally {
            for (Future<PairResult> future : futures) if (!future.isDone()) future.cancel(true);
            pool.shutdownNow();
        }
        stats.elapsedMs = (System.nanoTime() - started) / 1_000_000L;
        return stats;
    }

    public static void main(String[] args) throws Exception {
        String a = arg(args, 0, "current"), b = arg(args, 1, "v00");
        int games = Integer.parseInt(arg(args, 2, "200"));
        String mapsInput = arg(args, 3, "");
        Maps maps = new Maps(mapsInput);
        int threads = Integer.parseInt(arg(args, 4, String.valueOf(Math.max(1, Runtime.getRuntime().availableProcessors() - 1))));
        long baseSeed = Long.parseLong(arg(args, 5, "1000"));
        System.out.printf("bench %s vs %s: %d games, %d threads, maps=%s, base seed=%d%n", a, b, games, threads,
                maps.fromReplays() ? maps.poolSize() + " CG maps" : "CG generator (SHA1PRNG)", baseSeed);
        Stats stats = run(a, b, games, maps, threads, baseSeed);
        stats.mapsInput = mapsInput;
        System.out.println(stats.report());
        System.out.printf("  %.1fs%n", stats.elapsedMs / 1000.0);
        Path report = Path.of(arg(args, 6, "build/bench/bench_" + System.currentTimeMillis() + ".json"));
        stats.write(report);
        System.out.println("report: " + report);
        if (stats.errorsA + stats.errorsB > 0) throw new IllegalStateException("benchmark has bot errors; see report");
    }

    private static String arg(String[] args, int i, String def) {
        return args.length > i && args[i] != null && !args[i].isEmpty() ? args[i] : def;
    }
}
