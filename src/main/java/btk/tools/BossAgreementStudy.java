package btk.tools;

import btk.bot.Bot;
import btk.bot.BotFactory;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Runs a bot in the boss's seat over every public state of the boss replay corpus and measures
 * agreement with the boss's actual decisions: DISRUPT region, placed cells, paint spent, and
 * whether the bot builds when the boss builds / idles when the boss idles. Diagnostic only.
 *
 * <p>Args: replayDir bossNickname botSpec [maxGames]
 */
public final class BossAgreementStudy {

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        String boss = args[1], spec = args[2];
        int maxGames = args.length > 3 && !args[3].isEmpty() ? Integer.parseInt(args[3]) : Integer.MAX_VALUE;
        long turns = 0, bothDisrupt = 0, sameTarget = 0, bossDisrupt = 0, botDisrupt = 0;
        long bossCells = 0, matchedCells = 0, botCells = 0, bossPaint = 0, botPaint = 0;
        long bossBuilds = 0, botBuildsWhenBossBuilds = 0, bossIdles = 0, botIdlesWhenBossIdles = 0;
        long[] phaseTurns = new long[4], phaseBossPaint = new long[4], phaseBotPaint = new long[4], phaseCellMatch = new long[4], phaseBossCells = new long[4];
        int games = 0;
        long bossGainSum = 0, botGainSum = 0, bossCompletes = 0, botCompletes = 0; int[] inc0 = new int[2], inc1 = new int[2];
        for (Path f : CgReplay.listReplays(dir)) {
            if (games >= maxGames) break;
            CgReplay r = CgReplay.load(f);
            int me = r.nicknames[0].equals(boss) ? 0 : r.nicknames[1].equals(boss) ? 1 : -1;
            if (me < 0) continue;
            games++;
            GameState s = r.toState();
            Pathing pathing = new Pathing(s.size);
            Rules.updateConnections(s, pathing);
            Bot bot = BotFactory.create(spec);
            bot.init(s.copy(), me);
            for (int t = 0; t < r.turns(); t++) {
                String[] lines = r.turnStdout.get(t);
                @SuppressWarnings("unchecked")
                List<Action>[] acts = new List[2];
                for (int p = 0; p < 2; p++) {
                    try {
                        acts[p] = lines[p] == null || r.failure(t, p) != null ? List.of(Action.waitAction()) : Action.parseLine(lines[p]);
                    } catch (RuntimeException e) { acts[p] = List.of(Action.waitAction()); }
                }
                GameState pre = s.copy();
                List<Action> botActs = bot.play(s.copy(), me, 60_000);
                var bossMoves = new Rules.PlayerMoves(); var botMoves = new Rules.PlayerMoves();
                Rules.expand(pre, me, acts[me], pathing, bossMoves, new Rules.TurnResult());
                Rules.expand(pre, me, botActs, pathing, botMoves, new Rules.TurnResult());
                int bossT = -1, botT = -1;
                for (Action a : acts[me]) if (a.type == Action.Type.DISRUPT && bossT < 0) bossT = a.disruptRegion(pre);
                for (Action a : botActs) if (a.type == Action.Type.DISRUPT && botT < 0) botT = a.disruptRegion(pre);
                turns++;
                int phase = t < 11 ? 0 : t < 21 ? 1 : t < 46 ? 2 : 3;
                phaseTurns[phase]++;
                if (bossT >= 0) bossDisrupt++;
                if (botT >= 0) botDisrupt++;
                if (bossT >= 0 && botT >= 0) { bothDisrupt++; if (bossT == botT) sameTarget++; }
                int bp = 0, tp = 0;
                for (int i = 0; i < bossMoves.count; i++) bp += pre.placeCost(bossMoves.cells[i]);
                for (int i = 0; i < botMoves.count; i++) tp += pre.placeCost(botMoves.cells[i]);
                bossPaint += bp; botPaint += tp; phaseBossPaint[phase] += bp; phaseBotPaint[phase] += tp;
                bossCells += bossMoves.count; botCells += botMoves.count; phaseBossCells[phase] += bossMoves.count;
                for (int i = 0; i < bossMoves.count; i++) for (int j = 0; j < botMoves.count; j++)
                    if (bossMoves.cells[i] == botMoves.cells[j]) { matchedCells++; phaseCellMatch[phase]++; break; }
                if (bossMoves.count > 0 || botMoves.count > 0) {
                    Rules.incomePerTurn(pre, inc0);
                    GameState c = pre.copy();
                    for (int i = 0; i < bossMoves.count; i++) c.owner[bossMoves.cells[i]] = me;
                    Rules.updateConnections(c, pathing); Rules.incomePerTurn(c, inc1);
                    bossGainSum += (inc1[me] - inc0[me]) - (inc1[1 - me] - inc0[1 - me]);
                    for (int pp = 0; pp < pre.pairCount; pp++) if (!pre.pairActive(pp) && c.pairActive(pp)) bossCompletes++;
                    c = pre.copy();
                    for (int i = 0; i < botMoves.count; i++) c.owner[botMoves.cells[i]] = me;
                    Rules.updateConnections(c, pathing); Rules.incomePerTurn(c, inc1);
                    botGainSum += (inc1[me] - inc0[me]) - (inc1[1 - me] - inc0[1 - me]);
                    for (int pp = 0; pp < pre.pairCount; pp++) if (!pre.pairActive(pp) && c.pairActive(pp)) botCompletes++;
                }
                if (bossMoves.count > 0) { bossBuilds++; if (botMoves.count > 0) botBuildsWhenBossBuilds++; }
                else { bossIdles++; if (botMoves.count == 0) botIdlesWhenBossIdles++; }
                Rules.resolve(s, acts[0], acts[1], pathing);
            }
        }
        System.out.printf(Locale.ROOT, "%s on %d boss games, %d boss turns%n", spec, games, turns);
        System.out.printf(Locale.ROOT, "  disrupt: boss %.3f, bot %.3f, same region when both %.3f (%d)%n",
                bossDisrupt / (double) turns, botDisrupt / (double) turns, sameTarget / (double) Math.max(1, bothDisrupt), bothDisrupt);
        System.out.printf(Locale.ROOT, "  cells: boss %d, bot %d, boss cells also chosen by bot %.3f; paint/turn boss %.2f bot %.2f%n",
                bossCells, botCells, matchedCells / (double) Math.max(1, bossCells), bossPaint / (double) turns, botPaint / (double) turns);
        System.out.printf(Locale.ROOT, "  bot builds when boss builds %.3f (%d); bot idles when boss idles %.3f (%d)%n",
                botBuildsWhenBossBuilds / (double) Math.max(1, bossBuilds), bossBuilds, botIdlesWhenBossIdles / (double) Math.max(1, bossIdles), bossIdles);
        System.out.printf(Locale.ROOT, "  immediate income-diff gain per turn (own cells only): boss %.3f bot %.3f; pairs completed now boss %d bot %d%n",
                bossGainSum / (double) turns, botGainSum / (double) turns, bossCompletes, botCompletes);
        String[] names = {"t0-10", "t11-20", "t21-45", "t46+"};
        for (int ph = 0; ph < 4; ph++)
            System.out.printf(Locale.ROOT, "  %s: turns %d, paint boss %.2f bot %.2f, cell match %.3f%n", names[ph], phaseTurns[ph],
                    phaseBossPaint[ph] / (double) Math.max(1, phaseTurns[ph]), phaseBotPaint[ph] / (double) Math.max(1, phaseTurns[ph]),
                    phaseCellMatch[ph] / (double) Math.max(1, phaseBossCells[ph]));
    }
}
