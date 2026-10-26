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
 * Route-choice export (2026-10-27, construction line): for every placement turn of a target player in a replay directory,
 * the candidate routes our bot's proposal step would consider — per desired pair the paint-cheapest completion over any
 * tracks (variant 0) and over own tracks only, avoiding the opponent's (variant 1) — with features on the pre-turn board and
 * the label "chosen" = the candidate whose free cells overlap the cells the player actually placed that turn the most
 * (ties: the cheaper). Active pairs contribute their own-only route as a takeover candidate. Diagnostic / training data only.
 *
 * <p>Args: replayDir target outCsv [fromTurn] [toTurn]. {@code target} is a nickname, or "!nick" for the other player.
 */
public final class RouteChoiceExport {

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]); String target = args[1]; Path out = Path.of(args[2]);
        int from = args.length > 3 ? Integer.parseInt(args[3]) : 1, to = args.length > 4 ? Integer.parseInt(args[4]) : 60;
        boolean invert = target.startsWith("!"); String nick = invert ? target.substring(1) : target;
        Files.createDirectories(out.toAbsolutePath().getParent());
        int games = 0; long sets = 0, labeled = 0;
        try (BufferedWriter w = Files.newBufferedWriter(out)) {
            w.write("game,turn,gameTurns,player,nick,set,pair,variant,active,chosen,overlap,placed,cost,turns,dIncMe,dIncOpp,gainDiff,ourValue,"
                    + "pairsActivated,pathLen,ownOnPath,oppOnPath,ownShare,instabFree,maxInstabFree,unstableRegionsPath,adjOwn,touchOwn,completesNow,turnsLeft\n");
            for (Path f : CgReplay.listReplays(dir)) {
                CgReplay r = CgReplay.load(f);
                int named = r.nicknames[0].equals(nick) ? 0 : r.nicknames[1].equals(nick) ? 1 : -1;
                if (named < 0) continue;
                int p = invert ? 1 - named : named;
                games++;
                GameState s = r.toState();
                Pathing pathing = new Pathing(s.size);
                Rules.updateConnections(s, pathing);
                int[] path = new int[s.size]; boolean[] blocked = new boolean[s.size];
                int[] inc0 = new int[2], inc1 = new int[2];
                int turns = r.turns();
                for (int t = 0; t < turns; t++) {
                    String[] lines = r.turnStdout.get(t);
                    @SuppressWarnings("unchecked")
                    List<Action>[] acts = new List[2];
                    for (int q = 0; q < 2; q++) {
                        try {
                            acts[q] = lines[q] == null || r.failure(t, q) != null ? List.of(Action.waitAction()) : Action.parseLine(lines[q]);
                        } catch (RuntimeException e) { acts[q] = List.of(Action.waitAction()); }
                    }
                    GameState pre = s.copy();
                    Rules.resolve(s, acts[0], acts[1], pathing);
                    if (t < from || t > to) continue;
                    // cells the player placed this turn (own or neutralised by a same-cell collision)
                    boolean[] placed = new boolean[pre.size]; int nPlaced = 0;
                    for (int c = 0; c < pre.size; c++)
                        if (pre.owner[c] == GameState.NO_TRACK && (s.owner[c] == p || s.owner[c] == GameState.NEUTRAL) && !pre.inked[c]) {
                            // a neutral cell is attributed when the player's command touched it; approximate: own placements only for neutral
                            if (s.owner[c] == p || placedBy(acts[p], pre, c)) { placed[c] = true; nPlaced++; }
                        }
                    if (nPlaced == 0) continue;
                    Rules.incomePerTurn(pre, inc0);
                    int activeBefore = 0;
                    for (int q = 0; q < pre.pairCount; q++) if (pre.pairActive(q)) activeBefore++;
                    int turnsLeft = Math.max(1, pre.maxTurns - pre.turn);
                    StringBuilder rows = new StringBuilder();
                    int best = -1, bestOverlap = 0, bestCost = Integer.MAX_VALUE, n = 0;
                    int[] costs = new int[pre.pairCount * 2]; int[] overlaps = new int[pre.pairCount * 2];
                    String[] lineOf = new String[pre.pairCount * 2];
                    for (int pair = 0; pair < pre.pairCount; pair++) {
                        boolean active = pre.pairActive(pair);
                        for (int variant = 0; variant < 2; variant++) {
                            if (active && variant == 0) continue; // an active pair's shared route is the current path
                            for (int c = 0; c < pre.size; c++) blocked[c] = variant == 1 && pre.owner[c] == 1 - p;
                            int len = pathing.cheapestPath(pre, pre.townCell[pre.pairFrom[pair]], pre.townCell[pre.pairTo[pair]], path, variant == 0 ? null : blocked, null);
                            if (len <= 0) continue;
                            GameState after = pre.copy();
                            int cost = 0, overlap = 0, instab = 0, maxInstab = 0, adj = 0, ownOn = 0, oppOn = 0;
                            boolean touch = false;
                            boolean[] seenRegion = new boolean[pre.regionCount];
                            for (int i = 0; i < len; i++) {
                                int c = path[i];
                                if (pre.owner[c] == p) ownOn++; else if (pre.owner[c] == 1 - p) oppOn++;
                                if (pre.canBuild(c)) {
                                    cost += pre.placeCost(c); after.owner[c] = p;
                                    if (placed[c]) overlap++;
                                    int reg = pre.regionId[c];
                                    if (!seenRegion[reg]) { seenRegion[reg] = true; instab += pre.regionInstability[reg]; maxInstab = Math.max(maxInstab, pre.regionInstability[reg]); }
                                    if (adjacentOwn(pre, c, p)) adj++;
                                } else if (pre.owner[c] == p) touch = true;
                            }
                            if (cost == 0) continue;
                            Rules.updateConnections(after, pathing); Rules.incomePerTurn(after, inc1);
                            int activeAfter = 0;
                            for (int q = 0; q < after.pairCount; q++) if (after.pairActive(q)) activeAfter++;
                            int unstable = 0, ownAfter = 0, lenAfter = 0;
                            if (after.pairPath[pair] != null) {
                                boolean[] seen2 = new boolean[pre.regionCount];
                                lenAfter = after.pairPathLen[pair];
                                for (int i = 0; i < lenAfter; i++) {
                                    int c = after.pairPath[pair][i];
                                    if (after.owner[c] == p) ownAfter++;
                                    int reg = after.regionId[c];
                                    if (!seen2[reg] && !after.regionHasTown(reg) && after.regionInstability[reg] > 0) { seen2[reg] = true; unstable++; }
                                }
                            }
                            int turnsNeed = (cost + GameState.PAINT_PER_TURN - 1) / GameState.PAINT_PER_TURN;
                            int dMe = inc1[p] - inc0[p], dOpp = inc1[1 - p] - inc0[1 - p];
                            double earning = Math.min(16, turnsLeft) - turnsNeed + 1;
                            double ourValue = earning > 0 ? (dMe - dOpp) * earning / turnsNeed : 0;
                            int idx = pair * 2 + variant; costs[idx] = cost; overlaps[idx] = overlap;
                            lineOf[idx] = String.format(Locale.ROOT, ",%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.3f,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d%n",
                                    pair, variant, active ? 1 : 0, -1, overlap, nPlaced, cost, turnsNeed, dMe, dOpp, dMe - dOpp, ourValue,
                                    activeAfter - activeBefore, lenAfter, ownAfter, oppOn, lenAfter > 0 ? ownAfter * 1000 / lenAfter : 0,
                                    instab, maxInstab, unstable, adj, touch ? 1 : 0, cost <= GameState.PAINT_PER_TURN ? 1 : 0, turnsLeft);
                            n++;
                            if (overlap > bestOverlap || overlap == bestOverlap && overlap > 0 && cost < bestCost) { bestOverlap = overlap; bestCost = cost; best = idx; }
                        }
                    }
                    if (n == 0) continue;
                    sets++;
                    if (best >= 0) labeled++;
                    String head = String.format(Locale.ROOT, "%d,%d,%d,%d,%s,%d", r.gameId, t, turns, p, r.nicknames[p], sets);
                    for (int idx = 0; idx < lineOf.length; idx++) {
                        if (lineOf[idx] == null) continue;
                        String body = lineOf[idx].replaceFirst(",-1,", "," + (idx == best ? 1 : 0) + ",");
                        // replaceFirst targets the chosen placeholder: pair,variant,active,-1 -> the 4th field
                        w.write(head + body);
                    }
                }
            }
        }
        System.out.printf(Locale.ROOT, "%d games, %d placement turns with candidates, %d labeled (%.1f %%) -> %s%n", games, sets, labeled, 100.0 * labeled / Math.max(1, sets), out);
    }

    static boolean adjacentOwn(GameState s, int c, int p) {
        int x = c % s.width, y = c / s.width;
        for (int d = 0; d < 4; d++) {
            int nx = x + GameState.DX[d], ny = y + GameState.DY[d];
            if (nx < 0 || ny < 0 || nx >= s.width || ny >= s.height) continue;
            if (s.owner[ny * s.width + nx] == p) return true;
        }
        return false;
    }

    static boolean placedBy(List<Action> acts, GameState pre, int c) {
        for (Action a : acts) if (a.type == Action.Type.PLACE && pre.cell(a.x, a.y) == c) return true;
        return false;
    }
}
