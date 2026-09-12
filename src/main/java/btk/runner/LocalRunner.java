package btk.runner;

import btk.bot.Bot;
import btk.bot.BotFactory;
import btk.core.GameState;
import btk.referee.Referee;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** One local game with a replay written to replays/local/. Args: bot0 bot1 [seed] [mapDirOrReplay]. */
public final class LocalRunner {

    public static void main(String[] args) throws Exception {
        String spec0 = args.length > 0 && !args[0].isEmpty() ? args[0] : "current";
        String spec1 = args.length > 1 && !args[1].isEmpty() ? args[1] : "v00";
        long seed = args.length > 2 && !args[2].isEmpty() ? Long.parseLong(args[2]) : System.nanoTime() % 1_000_000;
        Maps maps = new Maps(args.length > 3 ? args[3] : "");

        GameState initial = maps.get((int) (seed % Math.max(1, maps.poolSize())), seed);
        Bot b0 = BotFactory.create(spec0);
        Bot b1 = BotFactory.create(spec1);
        Referee ref = new Referee();
        Referee.Result r = ref.play(initial, b0, b1, seed, true);
        System.out.printf("%s vs %s on %s (%dx%d, %d towns, %d pairs)%n", b0.name(), b1.name(),
                maps.name((int) (seed % Math.max(1, maps.poolSize())), seed),
                initial.width, initial.height, initial.townCount, initial.pairCount);
        System.out.println(r.summary());
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path file = r.replay.write(Path.of("replays/local", "game_" + ts + "_" + seed + ".json"));
        System.out.println("replay: " + file);
    }
}
