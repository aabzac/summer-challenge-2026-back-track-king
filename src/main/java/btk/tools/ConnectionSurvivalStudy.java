package btk.tools;

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
 * Connection survival (2026-10-27, arena-refresh-v12): replays every game of a directory through {@code btk.core} and, for
 * each desired pair and each player, writes one row per earning episode — the turns during which the pair's active path
 * held at least one of the player's tracks — with the cause of its end: {@code inkOpp} / {@code inkOwn} / {@code inkBoth}
 * (a region on the path was inked that turn by the opponent's / the player's own / both disrupts), {@code reroute} (the
 * pair stays active but its new shortest path holds none of the player's tracks; {@code takeover} when the new path holds
 * tracks the other player placed that turn), {@code neutral} (the player's tracks on the path were neutralised by a
 * collision), {@code other}, or {@code end} (still paying when the game ended). Diagnostic only.
 *
 * <p>Args: replayDir playerNickname outCsv
 */
public final class ConnectionSurvivalStudy {

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]); String nick = args[1]; Path out = Path.of(args[2]);
        Files.createDirectories(out.toAbsolutePath().getParent());
        int games = 0; long rows = 0;
        try (BufferedWriter w = Files.newBufferedWriter(out)) {
            w.write("game,gameTurns,ours,player,pair,start,end,turns,cause,cellsAtStart,pathLenAtStart,maxCells\n");
            for (Path f : CgReplay.listReplays(dir)) {
                CgReplay r = CgReplay.load(f);
                int us = r.nicknames[0].equals(nick) ? 0 : r.nicknames[1].equals(nick) ? 1 : -1;
                if (us < 0) continue;
                games++;
                GameState s = r.toState();
                Pathing pathing = new Pathing(s.size);
                Rules.updateConnections(s, pathing);
                int turns = r.turns();
                int[][] start = new int[2][s.pairCount], cellsAtStart = new int[2][s.pairCount], lenAtStart = new int[2][s.pairCount], maxCells = new int[2][s.pairCount];
                for (int p = 0; p < 2; p++) java.util.Arrays.fill(start[p], -1);
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
                    int[] disrupt = {-1, -1};
                    for (int p = 0; p < 2; p++) for (Action a : acts[p]) if (a.type == Action.Type.DISRUPT && disrupt[p] < 0) disrupt[p] = a.disruptRegion(pre);
                    // episodes open on the pre-turn board (the board that pays at the end of the previous turn)
                    for (int pair = 0; pair < pre.pairCount; pair++) for (int p = 0; p < 2; p++) {
                        int own = ownOnPath(pre, pair, p);
                        if (own > 0 && start[p][pair] < 0) { start[p][pair] = t; cellsAtStart[p][pair] = own; lenAtStart[p][pair] = pre.pairPathLen[pair]; maxCells[p][pair] = own; }
                        if (own > 0) maxCells[p][pair] = Math.max(maxCells[p][pair], own);
                    }
                    Rules.resolve(s, acts[0], acts[1], pathing);
                    boolean last = t == turns - 1;
                    for (int pair = 0; pair < pre.pairCount; pair++) for (int p = 0; p < 2; p++) {
                        if (start[p][pair] < 0) continue;
                        int ownAfter = s.pairPath[pair] == null ? 0 : ownOnPath(s, pair, p);
                        String cause = null;
                        if (ownAfter == 0) cause = cause(pre, s, pair, p, disrupt);
                        else if (last) cause = "end";
                        if (cause == null) continue;
                        w.write(String.format(Locale.ROOT, "%d,%d,%d,%d,%d,%d,%d,%d,%s,%d,%d,%d%n", r.gameId, turns, p == us ? 1 : 0, p, pair,
                                start[p][pair], t + 1, t + 1 - start[p][pair], cause, cellsAtStart[p][pair], lenAtStart[p][pair], maxCells[p][pair]));
                        rows++; start[p][pair] = -1;
                    }
                }
            }
        }
        System.out.printf(Locale.ROOT, "%d games, %d earning episodes -> %s%n", games, rows, out);
    }

    static int ownOnPath(GameState s, int pair, int p) {
        int[] path = s.pairPath[pair];
        if (path == null) return 0;
        int n = 0;
        for (int i = 0; i < s.pairPathLen[pair]; i++) if (s.owner[path[i]] == p) n++;
        return n;
    }

    /** Why player p stopped earning from {@code pair} between {@code pre} and {@code post}. */
    static String cause(GameState pre, GameState post, int pair, int p, int[] disrupt) {
        int[] path = pre.pairPath[pair];
        boolean inkedOpp = false, inkedOwn = false, neutral = false;
        if (path != null) {
            for (int i = 0; i < pre.pairPathLen[pair]; i++) {
                int c = path[i], reg = pre.regionId[c];
                if (!pre.regionInked[reg] && post.regionInked[reg]) {
                    if (disrupt[1 - p] == reg) inkedOpp = true;
                    if (disrupt[p] == reg) inkedOwn = true;
                    if (disrupt[1 - p] != reg && disrupt[p] != reg) inkedOpp = inkedOpp || false;
                }
                if (pre.owner[c] == p && post.owner[c] == GameState.NEUTRAL) neutral = true;
            }
        }
        if (inkedOpp && inkedOwn) return "inkBoth";
        if (inkedOpp) return "inkOpp";
        if (inkedOwn) return "inkOwn";
        if (post.pairPath[pair] == null) {
            // inactive without an ink on the old path: an ink elsewhere cannot cut the old path; neutralised cells can
            return neutral ? "neutral" : "other";
        }
        int[] np = post.pairPath[pair];
        for (int i = 0; i < post.pairPathLen[pair]; i++) {
            int c = np[i];
            if (post.owner[c] == 1 - p && pre.owner[c] != 1 - p) return "takeover";
        }
        return neutral ? "neutral" : "reroute";
    }
}
