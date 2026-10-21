package btk.tools;

import btk.bot.Bot;
import btk.bot.BotFactory;
import btk.core.GameState;
import btk.referee.Referee;
import btk.referee.Replay;
import btk.runner.Maps;
import btk.runner.TimingProfile;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Plays paired local games between a reference ("boss-like") spec and an opponent spec at CG
 * timing and writes the same per-turn tables as {@link BossIntentStudy}, with the reference in the
 * boss seat, so a local proxy's behavioural fingerprint can be compared with the real boss corpus.
 * Diagnostic only; not a strength benchmark.
 *
 * <p>Args: bossSpec oppSpec games baseSeed outDir [threads]
 */
public final class ProxyBehaviorStudy {

    public static void main(String[] args) throws Exception {
        String bossSpec = args[0], oppSpec = args[1];
        int games = Integer.parseInt(args[2]);
        long baseSeed = Long.parseLong(args[3]);
        Path out = Path.of(args[4]);
        int threads = args.length > 5 ? Integer.parseInt(args[5]) : 2;
        if (games % 2 != 0) throw new IllegalArgumentException("even game count required (paired seats)");
        Files.createDirectories(out);
        Maps maps = new Maps("");
        TimingProfile timing = TimingProfile.CG;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<Replay>> futures = new ArrayList<>();
        int[] wins = new int[3];
        for (int g = 0; g < games; g++) {
            final int pairIndex = g / 2, seat = g % 2;
            futures.add(pool.submit(() -> {
                GameState initial = maps.get(pairIndex, baseSeed + pairIndex);
                Bot b0 = BotFactory.create(seat == 0 ? bossSpec : oppSpec);
                Bot b1 = BotFactory.create(seat == 0 ? oppSpec : bossSpec);
                Referee referee = new Referee();
                timing.apply(referee, seat == 0 ? bossSpec : oppSpec, seat == 0 ? oppSpec : bossSpec);
                Referee.Result res = referee.play(initial, b0, b1, baseSeed + pairIndex, true);
                res.replay.meta.gameId = "local-" + (baseSeed + pairIndex) + "-" + seat;
                return res.replay;
            }));
        }
        try (BufferedWriter turns = Files.newBufferedWriter(out.resolve("turns.csv"));
             BufferedWriter cells = Files.newBufferedWriter(out.resolve("cells.csv"))) {
            BossIntentStudy.writeHeaders(turns, cells);
            int g = 0;
            for (Future<Replay> f : futures) {
                Replay replay = f.get();
                int seat = g % 2; g++;
                int bossSeat = seat == 0 ? 0 : 1;
                int w = replay.meta.winner;
                wins[w < 0 ? 2 : w == bossSeat ? 0 : 1]++;
                new BossIntentStudy.GameRun(source(replay, maps.get((g - 1) / 2, baseSeed + (g - 1) / 2)), bossSeat).run(turns, cells);
            }
        } finally {
            pool.shutdownNow();
        }
        System.out.printf("%s in boss seat vs %s: %d games, boss W-L-D %d-%d-%d -> %s%n", bossSpec, oppSpec, games,
                wins[0], wins[1], wins[2], out);
    }

    static BossIntentStudy.Source source(Replay replay, GameState initial) {
        return new BossIntentStudy.Source() {
            public GameState initialState() { return initial.copy(); }
            public int turns() { return replay.turns.size(); }
            public String[] lines(int turn) { return replay.turns.get(turn).actions; }
            public String failure(int turn, int player) { return null; }
            public String gameId() { return replay.meta.gameId; }
            public String nickname(int player) { return replay.meta.bots[player]; }
        };
    }
}
