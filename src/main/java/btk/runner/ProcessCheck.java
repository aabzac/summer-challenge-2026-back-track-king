package btk.runner;

import btk.bot.Bot;
import btk.bot.BotFactory;
import btk.core.GameState;
import btk.referee.Referee;
import btk.submit.FileMerger;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Pre-submit check: merges Player.java (if needed), then plays it as an external process against
 * an in-process bot on real CG maps when available. Fails loudly on timeouts, invalid commands,
 * or connection-model mismatches printed by the process.
 *
 * <p>Args: opponentSpec games
 */
public final class ProcessCheck {

    public static void main(String[] args) throws Exception {
        String opp = args.length > 0 && !args[0].isEmpty() ? args[0] : "v00";
        int games = args.length > 1 && !args[1].isEmpty() ? Integer.parseInt(args[1]) : 3;
        Path classes = Path.of("build/submit/classes/Player.class");
        if (!Files.exists(classes)) FileMerger.main(new String[0]);
        Maps maps = new Maps(Files.isDirectory(Path.of("replays/cg")) ? "replays/cg" : "");
        int wins = 0, problems = 0;
        for (int g = 0; g < games; g++) {
            long seed = 7000 + g;
            GameState initial = maps.get(g, seed);
            ProcessBot pb = ProcessBot.javaPlayer("build/submit/classes");
            Bot other = BotFactory.create(opp);
            boolean asP0 = g % 2 == 0;
            Referee ref = new Referee();
            ref.turnBudgetMs = 50;
            Referee.Result r = ref.play(initial, asP0 ? pb : other, asP0 ? other : pb, seed, false);
            pb.close();
            int me = asP0 ? 0 : 1;
            if (r.winner == me) wins++;
            boolean bad = pb.timeouts > 0 || r.error[me] != null;
            if (bad) problems++;
            System.out.printf("game %d as P%d: %s | proc first turn %d ms, max later %d ms, timeouts %d%s%n", g, me,
                    r.summary(), pb.firstTurnMs, pb.maxResponseMs, pb.timeouts, bad ? "  <-- PROBLEM" : "");
        }
        System.out.printf("Player.java: %d/%d wins vs %s, %d problem games%n", wins, games, opp, problems);
        if (problems > 0) System.exit(1);
    }
}
