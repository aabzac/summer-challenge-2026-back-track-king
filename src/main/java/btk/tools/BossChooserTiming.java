package btk.tools;

import btk.bot.Bot;
import btk.bot.BotFactory;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.referee.MapGenerator;
import btk.bot.current.BossSabotage;

import java.util.List;

/** Local diagnostic: wall time of the real-turn BossSabotage.choose on v09-vs-v09 positions, per map (docs/stage1-opponent-model-v1.md). */
public class BossChooserTiming {
    public static void main(String[] a) {
        int maps = Integer.parseInt(a[0]); long seed = Long.parseLong(a[1]);
        double worstAll = 0; long calls = 0; double sum = 0; int over10 = 0, over20 = 0;
        for (int m = 0; m < maps; m++) {
            GameState s = MapGenerator.generate(seed + m);
            Pathing pathing = new Pathing(s.size);
            Rules.updateConnections(s, pathing);
            Bot b0 = BotFactory.create("v09"), b1 = BotFactory.create("v09");
            b0.init(s.copy(), 0); b1.init(s.copy(), 1);
            BossSabotage chooser = new BossSabotage(s, 1.0);
            double worst = 0, first = 0; int worstTurn = -1;
            while (!Rules.gameOver(s, pathing)) {
                chooser.observe(s);
                long t0 = System.nanoTime();
                chooser.choose(s, 0);
                double ms = (System.nanoTime() - t0) / 1e6;
                if (s.turn == 0) first = ms; else {
                    calls++; sum += ms; if (ms > 10) over10++; if (ms > 20) over20++;
                    if (ms > worst) { worst = ms; worstTurn = s.turn; }
                }
                List<Action> a0 = b0.play(s.copy(), 0, 36), a1 = b1.play(s.copy(), 1, 36);
                Rules.resolve(s, a0, a1, pathing);
            }
            worstAll = Math.max(worstAll, worst);
            System.out.printf("map %d %dx%d regions %d pairs %d: first %.1f ms, worst later %.1f ms (turn %d), turns %d%n",
                    m, s.width, s.height, s.regionCount, s.pairCount, first, worst, worstTurn, s.turn);
        }
        System.out.printf("later-turn calls %d: mean %.2f ms, >10 ms %d, >20 ms %d, max %.1f ms%n", calls, sum / calls, over10, over20, worstAll);
    }
}
