package btk.tools;

import btk.bot.Bot;
import btk.bot.BotFactory;
import btk.core.GameState;
import btk.referee.MapGenerator;
import btk.referee.Referee;

/**
 * Local diagnostic: one game in this JVM (run one process per game for the CodinGame-like cold
 * figure) on a generated map, CG budgets 486/36 ms; prints each seat's mean and maximum play()
 * wall time. Usage: ColdTurnTiming specA specB seed
 */
public class ColdTurnTiming {
    public static void main(String[] a) {
        GameState initial = MapGenerator.generate(Long.parseLong(a[2]));
        Bot b0 = BotFactory.create(a[0]), b1 = BotFactory.create(a[1]);
        Referee referee = new Referee();
        referee.firstTurnBudgetMs = 486; referee.turnBudgetMs = 36; referee.slowMoveMs = 50;
        Referee.Result r = referee.play(initial, b0, b1, Long.parseLong(a[2]), false);
        System.out.printf("seed %s %dx%d pairs %d turns %d winner %d | A %s: mean %.1f max %d slow %d | B %s: mean %.1f max %d slow %d%n",
                a[2], initial.width, initial.height, initial.pairCount, r.turns, r.winner,
                a[0], r.totalMs[0] / (double) r.turns, r.maxMs[0], r.slowMoves[0],
                a[1], r.totalMs[1] / (double) r.turns, r.maxMs[1], r.slowMoves[1]);
    }
}
