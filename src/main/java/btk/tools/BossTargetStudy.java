package btk.tools;

import btk.bot.Bot;
import btk.bot.BotFactory;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * corridor-routes-v1 follow-up (2026-10-26): where does the boss's DISRUPT go, measured against the opponent's
 * (our) construction, and where would a local model of the boss send it from the same public state? One row per
 * boss turn with a disrupt: the boss's region and the bot's region, each with the opponent's track count, the
 * opponent's earning tracks (on an active shortest path), the instability and the rank of the region among the
 * disruptable regions by the opponent's track count (1 = most tracks). Diagnostic only; nothing re-implements rules.
 *
 * <p>Args: replayDir bossNickname botSpec outCsv [maxGames]
 */
public final class BossTargetStudy {

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        String boss = args[1], spec = args[2];
        Path out = Path.of(args[3]);
        int maxGames = args.length > 4 && !args[4].isEmpty() ? Integer.parseInt(args[4]) : Integer.MAX_VALUE;
        Files.createDirectories(out.toAbsolutePath().getParent());
        int games = 0, rows = 0, same = 0;
        try (BufferedWriter w = Files.newBufferedWriter(out)) {
            w.write("game,turn,gameTurns,bossSeat,bossTarget,botTarget,same,oppTracksBoss,oppTracksBot,oppEarningBoss,oppEarningBot,"
                    + "instabBoss,instabBot,rankBoss,rankBot,maxOppTracks,regionsAtMax,disruptable,oppTracksTotal,bossTracksBoss,bossTracksBot\n");
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
                int opp = 1 - me;
                int[] oppTracks = new int[s.regionCount], oppEarning = new int[s.regionCount], bossTracks = new int[s.regionCount];
                boolean[] townRegion = new boolean[s.regionCount];
                for (int c = 0; c < s.size; c++) if (s.townAtCell[c] >= 0) townRegion[s.regionId[c]] = true;
                int turns = r.turns();
                for (int t = 0; t < turns; t++) {
                    String[] lines = r.turnStdout.get(t);
                    @SuppressWarnings("unchecked")
                    List<Action>[] acts = new List[2];
                    for (int p = 0; p < 2; p++) {
                        try {
                            acts[p] = lines[p] == null || r.failure(t, p) != null ? List.of(Action.waitAction()) : Action.parseLine(lines[p]);
                        } catch (RuntimeException e) { acts[p] = List.of(Action.waitAction()); }
                    }
                    GameState pre = s.copy();
                    int bossT = -1;
                    for (Action a : acts[me]) if (a.type == Action.Type.DISRUPT && bossT < 0) bossT = a.disruptRegion(pre);
                    if (bossT >= 0) {
                        List<Action> botActs = bot.play(s.copy(), me, t == 0 ? 486 : 36);
                        int botT = -1;
                        for (Action a : botActs) if (a.type == Action.Type.DISRUPT && botT < 0) botT = a.disruptRegion(pre);
                        java.util.Arrays.fill(oppTracks, 0); java.util.Arrays.fill(oppEarning, 0); java.util.Arrays.fill(bossTracks, 0);
                        for (int c = 0; c < pre.size; c++) {
                            if (pre.owner[c] == opp) oppTracks[pre.regionId[c]]++;
                            else if (pre.owner[c] == me) bossTracks[pre.regionId[c]]++;
                        }
                        for (int p = 0; p < pre.pairCount; p++) {
                            int[] path = pre.pairPath[p];
                            if (path == null) continue;
                            for (int i = 0; i < pre.pairPathLen[p]; i++) if (pre.owner[path[i]] == opp) oppEarning[pre.regionId[path[i]]]++;
                        }
                        int maxOpp = 0, atMax = 0, disruptable = 0, oppTotal = 0;
                        for (int g = 0; g < pre.regionCount; g++) {
                            oppTotal += oppTracks[g];
                            if (townRegion[g] || pre.regionInked[g]) continue;
                            disruptable++;
                            if (oppTracks[g] > maxOpp) { maxOpp = oppTracks[g]; atMax = 1; }
                            else if (oppTracks[g] == maxOpp) atMax++;
                        }
                        int rankBoss = rank(pre, townRegion, oppTracks, bossT), rankBot = botT >= 0 ? rank(pre, townRegion, oppTracks, botT) : -1;
                        rows++;
                        if (botT == bossT) same++;
                        w.write(String.format(Locale.ROOT, "%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d%n",
                                r.gameId, t, turns, me, bossT, botT, botT == bossT ? 1 : 0,
                                oppTracks[bossT], botT >= 0 ? oppTracks[botT] : -1, oppEarning[bossT], botT >= 0 ? oppEarning[botT] : -1,
                                pre.regionInstability[bossT], botT >= 0 ? pre.regionInstability[botT] : -1, rankBoss, rankBot,
                                maxOpp, atMax, disruptable, oppTotal, bossTracks[bossT], botT >= 0 ? bossTracks[botT] : -1));
                    }
                    Rules.resolve(s, acts[0], acts[1], pathing);
                }
            }
        }
        System.out.printf(Locale.ROOT, "%s: %d boss games, %d boss disrupt turns, same region %.3f -> %s%n", spec, games, rows, same / (double) Math.max(1, rows), out);
    }

    /** 1 + the number of disruptable regions holding strictly more of the opponent's tracks than {@code region}. */
    private static int rank(GameState s, boolean[] townRegion, int[] oppTracks, int region) {
        int above = 0;
        for (int g = 0; g < s.regionCount; g++) {
            if (g == region || townRegion[g] || s.regionInked[g]) continue;
            if (oppTracks[g] > oppTracks[region]) above++;
        }
        return 1 + above;
    }
}
