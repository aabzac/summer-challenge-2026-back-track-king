package btk.tools;

import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Ink rescue (2026-10-27, late-game-v1): does a player's earning connection survive the ink of a region on its path?
 * Replays every game of a directory through {@code btk.core} and writes, per player and desired pair whose active path
 * holds at least one of the player's tracks:
 * <ul>
 * <li>{@code risk} rows (pre-turn board): the path crosses a non-town region one disrupt from ink (instability 3); each
 * such region is inked on a copy and the connection recomputed; {@code ownAfter} = the player's tracks on the pair's new
 * path after the worst of those inks (0 = the connection would stop paying the player), {@code activeAfter} = the pair
 * stays connected for anyone;</li>
 * <li>{@code hit} rows: a region of the path was really inked this turn; {@code ownAfter} = the player's tracks on the
 * pair's path after the turn, {@code prepared} = those of them placed before this turn (a bypass built in advance).</li>
 * </ul>
 * Diagnostic only. Args: replayDir playerNickname outCsv
 */
public final class InkRescueStudy {

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]); String nick = args[1]; Path out = Path.of(args[2]);
        Files.createDirectories(out.toAbsolutePath().getParent());
        int games = 0; long rows = 0;
        try (BufferedWriter w = Files.newBufferedWriter(out)) {
            w.write("game,gameTurns,ours,player,turn,kind,pair,ownPre,ownAfter,activeAfter,riskRegions,prepared\n");
            for (Path f : CgReplay.listReplays(dir)) {
                CgReplay r = CgReplay.load(f);
                int us = r.nicknames[0].equals(nick) ? 0 : r.nicknames[1].equals(nick) ? 1 : -1;
                if (us < 0) continue;
                games++;
                GameState s = r.toState();
                Pathing pathing = new Pathing(s.size);
                Rules.updateConnections(s, pathing);
                int turns = r.turns();
                int[] placeTurn = new int[s.size];
                Arrays.fill(placeTurn, -1);
                GameState sim = s.copy();
                int[][][] ownAfterInk = new int[s.regionCount][][];
                boolean[][] activeAfterInk = new boolean[s.regionCount][];
                for (int t = 0; t < turns; t++) {
                    GameState pre = s.copy();
                    // risk: every non-town region one disrupt from ink that lies on some earning path, inked once on a copy
                    Arrays.fill(ownAfterInk, null);
                    for (int pair = 0; pair < pre.pairCount; pair++) {
                        int[] path = pre.pairPath[pair];
                        if (path == null) continue;
                        for (int i = 0; i < pre.pairPathLen[pair]; i++) {
                            int reg = pre.regionId[path[i]];
                            if (ownAfterInk[reg] != null || pre.regionInked[reg] || pre.regionHasTown(reg)
                                    || pre.regionInstability[reg] != GameState.INK_THRESHOLD - 1) continue;
                            sim.copyFrom(pre); Rules.ink(sim, reg); Rules.updateConnections(sim, pathing);
                            ownAfterInk[reg] = new int[2][pre.pairCount];
                            activeAfterInk[reg] = new boolean[pre.pairCount];
                            for (int q = 0; q < pre.pairCount; q++) {
                                activeAfterInk[reg][q] = sim.pairPath[q] != null;
                                for (int p = 0; p < 2; p++) ownAfterInk[reg][p][q] = ConnectionSurvivalStudy.ownOnPath(sim, q, p);
                            }
                        }
                    }
                    for (int pair = 0; pair < pre.pairCount; pair++) for (int p = 0; p < 2; p++) {
                        int own = ConnectionSurvivalStudy.ownOnPath(pre, pair, p);
                        if (own == 0) continue;
                        int risk = 0, worst = Integer.MAX_VALUE; boolean active = true;
                        int[] path = pre.pairPath[pair];
                        boolean[] seen = new boolean[pre.regionCount];
                        for (int i = 0; i < pre.pairPathLen[pair]; i++) {
                            int reg = pre.regionId[path[i]];
                            if (seen[reg] || ownAfterInk[reg] == null) continue;
                            seen[reg] = true; risk++;
                            if (ownAfterInk[reg][p][pair] < worst) { worst = ownAfterInk[reg][p][pair]; active = activeAfterInk[reg][pair]; }
                        }
                        if (risk == 0) continue;
                        w.write(String.format(Locale.ROOT, "%d,%d,%d,%d,%d,risk,%d,%d,%d,%d,%d,-1%n", r.gameId, turns, p == us ? 1 : 0, p, t,
                                pair, own, worst, active ? 1 : 0, risk));
                        rows++;
                    }
                    String[] lines = r.turnStdout.get(t);
                    @SuppressWarnings("unchecked")
                    List<Action>[] acts = new List[2];
                    for (int p = 0; p < 2; p++) {
                        try {
                            acts[p] = lines[p] == null || r.failure(t, p) != null ? List.of(Action.waitAction()) : Action.parseLine(lines[p]);
                        } catch (RuntimeException e) { acts[p] = List.of(Action.waitAction()); }
                    }
                    Rules.resolve(s, acts[0], acts[1], pathing);
                    for (int c = 0; c < s.size; c++)
                        if (pre.owner[c] == GameState.NO_TRACK && (s.owner[c] == 0 || s.owner[c] == 1)) placeTurn[c] = t;
                    for (int pair = 0; pair < pre.pairCount; pair++) {
                        int[] path = pre.pairPath[pair];
                        if (path == null) continue;
                        boolean hit = false;
                        for (int i = 0; i < pre.pairPathLen[pair] && !hit; i++) {
                            int reg = pre.regionId[path[i]];
                            hit = !pre.regionInked[reg] && s.regionInked[reg];
                        }
                        if (!hit) continue;
                        for (int p = 0; p < 2; p++) {
                            int own = ConnectionSurvivalStudy.ownOnPath(pre, pair, p);
                            if (own == 0) continue;
                            int after = 0, prepared = 0;
                            int[] np = s.pairPath[pair];
                            if (np != null) for (int i = 0; i < s.pairPathLen[pair]; i++) {
                                int c = np[i];
                                if (s.owner[c] != p) continue;
                                after++;
                                if (placeTurn[c] < t) prepared++;
                            }
                            w.write(String.format(Locale.ROOT, "%d,%d,%d,%d,%d,hit,%d,%d,%d,%d,0,%d%n", r.gameId, turns, p == us ? 1 : 0, p, t,
                                    pair, own, after, np != null ? 1 : 0, prepared));
                            rows++;
                        }
                    }
                }
            }
        }
        System.out.printf(Locale.ROOT, "%d games, %d rows -> %s%n", games, rows, out);
    }
}
