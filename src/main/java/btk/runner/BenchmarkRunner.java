package btk.runner;

import btk.bot.Bot;
import btk.bot.BotFactory;
import btk.core.GameState;
import btk.referee.Referee;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * In-process arena: N games, sides swapped every other game, parallel threads, fresh bot
 * instances per game. Reports win rate with a 95% Wilson interval (draws = half a win),
 * per-side breakdown, average score difference and timing.
 *
 * <p>Args: bot1 bot2 games [mapsDir] [threads]
 */
public final class BenchmarkRunner {

    public static final class Stats {
        public final String a, b;
        public int games, winsA, winsB, draws, errorsA, errorsB;
        public int winsAasP0, gamesAasP0, winsAasP1, gamesAasP1;
        public long sumDiff;
        public long maxMsA, maxMsB, sumMsA, sumMsB;
        public int slowA, slowB;
        public int turns;
        public final List<String> notes = new ArrayList<>();

        Stats(String a, String b) {
            this.a = a;
            this.b = b;
        }

        /** Win rate of A with draws counted as half, in [0,1]. */
        public double rateA() {
            return games == 0 ? 0.5 : (winsA + 0.5 * draws) / games;
        }

        public double[] wilson95() {
            if (games == 0) return new double[]{0, 1};
            double z = 1.96, n = games, p = rateA();
            double denom = 1 + z * z / n;
            double centre = p + z * z / (2 * n);
            double half = z * Math.sqrt(p * (1 - p) / n + z * z / (4 * n * n));
            return new double[]{(centre - half) / denom, (centre + half) / denom};
        }

        public String report() {
            double[] ci = wilson95();
            StringBuilder sb = new StringBuilder();
            sb.append("=".repeat(64)).append('\n');
            sb.append(String.format("  %s  vs  %s   (%d games, sides swapped)%n", a, b, games));
            sb.append("=".repeat(64)).append('\n');
            sb.append(String.format("  A win rate: %.1f%%  [95%% CI %.1f%% .. %.1f%%]   W-L-D %d-%d-%d%n",
                    rateA() * 100, ci[0] * 100, ci[1] * 100, winsA, winsB, draws));
            sb.append(String.format("  A as P0: %d/%d (%.1f%%)   A as P1: %d/%d (%.1f%%)%n",
                    winsAasP0, gamesAasP0, pct(winsAasP0, gamesAasP0), winsAasP1, gamesAasP1, pct(winsAasP1, gamesAasP1)));
            sb.append(String.format("  avg score diff (A-B): %+.1f   avg turns: %.1f%n",
                    games == 0 ? 0 : (double) sumDiff / games, games == 0 ? 0 : (double) turns / games));
            sb.append(String.format("  time/move ms  A avg %.2f max %d slow %d | B avg %.2f max %d slow %d%n",
                    avg(sumMsA), maxMsA, slowA, avg(sumMsB), maxMsB, slowB));
            if (errorsA + errorsB > 0) sb.append(String.format("  ERRORS: A %d, B %d%n", errorsA, errorsB));
            for (String n : notes) sb.append("  ").append(n).append('\n');
            String verdict = ci[0] > 0.5 ? "A significantly better" : ci[1] < 0.5 ? "B significantly better" : "no significant difference";
            sb.append("  verdict: ").append(verdict).append('\n');
            sb.append("=".repeat(64));
            return sb.toString();
        }

        private double avg(long sumMs) {
            return turns == 0 ? 0 : (double) sumMs / turns;
        }

        private static double pct(int w, int n) {
            return n == 0 ? 0 : 100.0 * w / n;
        }
    }

    public static Stats run(String specA, String specB, int games, Maps maps, int threads, long baseSeed) throws Exception {
        Stats st = new Stats(specA, specB);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<Object[]>> futures = new ArrayList<>();
        for (int i = 0; i < games; i++) {
            final int idx = i;
            futures.add(pool.submit(() -> {
                long seed = baseSeed + idx;
                boolean swapped = idx % 2 == 1;
                GameState initial = maps.get(idx, seed);
                Bot a = BotFactory.create(specA), b = BotFactory.create(specB);
                Referee ref = new Referee();
                Referee.Result r = ref.play(initial, swapped ? b : a, swapped ? a : b, seed, false);
                return new Object[]{r, swapped, seed};
            }));
        }
        int done = 0;
        for (Future<Object[]> f : futures) {
            Object[] o = f.get();
            Referee.Result r = (Referee.Result) o[0];
            boolean swapped = (Boolean) o[1];
            long seed = (Long) o[2];
            int pa = swapped ? 1 : 0, pb = 1 - pa;
            st.games++;
            st.turns += r.turns;
            st.sumDiff += r.score[pa] - r.score[pb];
            if (r.winner == pa) st.winsA++;
            else if (r.winner == pb) st.winsB++;
            else st.draws++;
            if (pa == 0) {
                st.gamesAasP0++;
                if (r.winner == pa) st.winsAasP0++;
            } else {
                st.gamesAasP1++;
                if (r.winner == pa) st.winsAasP1++;
            }
            if (r.error[pa] != null) {
                st.errorsA++;
                if (st.notes.size() < 10) st.notes.add("seed " + seed + " A error: " + r.error[pa]);
            }
            if (r.error[pb] != null) {
                st.errorsB++;
                if (st.notes.size() < 10) st.notes.add("seed " + seed + " B error: " + r.error[pb]);
            }
            st.maxMsA = Math.max(st.maxMsA, r.maxMs[pa]);
            st.maxMsB = Math.max(st.maxMsB, r.maxMs[pb]);
            st.sumMsA += r.totalMs[pa];
            st.sumMsB += r.totalMs[pb];
            st.slowA += r.slowMoves[pa];
            st.slowB += r.slowMoves[pb];
            done++;
            if (done % 50 == 0 || done == games) {
                System.out.printf("  %d/%d  A %.1f%%%n", done, games, st.rateA() * 100);
            }
        }
        pool.shutdown();
        return st;
    }

    public static void main(String[] args) throws Exception {
        String a = arg(args, 0, "current"), b = arg(args, 1, "v00");
        int games = Integer.parseInt(arg(args, 2, "200"));
        Maps maps = new Maps(arg(args, 3, ""));
        int threads = Integer.parseInt(arg(args, 4, String.valueOf(Math.max(1, Runtime.getRuntime().availableProcessors() - 1))));
        System.out.printf("bench %s vs %s: %d games, %d threads, maps=%s%n", a, b, games, threads,
                maps.fromReplays() ? maps.poolSize() + " CG maps" : "CG generator (SHA1PRNG)");
        long t0 = System.currentTimeMillis();
        Stats st = run(a, b, games, maps, threads, 1000);
        System.out.println(st.report());
        System.out.printf("  %.1fs%n", (System.currentTimeMillis() - t0) / 1000.0);
    }

    private static String arg(String[] a, int i, String def) {
        return a.length > i && a[i] != null && !a[i].isEmpty() ? a[i] : def;
    }
}
