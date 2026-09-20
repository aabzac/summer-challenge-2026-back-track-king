package btk.tools;

import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Exports the decisions of one named player (and of their opponents) as feature tables, replaying
 * every game of a replay directory through the core engine. Nothing here re-implements rules:
 * incomes, paths and ink effects come from {@link Rules} and {@link Pathing} on copied states.
 *
 * <p>Args: replayDir playerNickname outDir. Writes
 * <ul>
 *   <li>{@code sabotage.csv}: one row per candidate region per player turn (both players), with
 *       exact income deltas of inking it, track counts, route traffic and whether it was chosen;</li>
 *   <li>{@code build.csv}: one row per player turn with the placed cells classified against the
 *       cheapest completion routes of every inactive desired pair;</li>
 *   <li>{@code hits.csv}: every DISRUPT with the turn the region was eventually inked;</li>
 *   <li>{@code games.csv}: end-of-game facts (length, closer, lead at closure).</li>
 * </ul>
 */
public final class CgLeaderStudy {

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args.length > 0 && !args[0].isEmpty() ? args[0] : "replays/cg-indrill");
        String player = args.length > 1 && !args[1].isEmpty() ? args[1] : "Indrill";
        Path out = Path.of(args.length > 2 && !args[2].isEmpty() ? args[2] : "build/analysis/leader-study");
        Files.createDirectories(out);
        try (BufferedWriter sab = Files.newBufferedWriter(out.resolve("sabotage.csv"));
             BufferedWriter build = Files.newBufferedWriter(out.resolve("build.csv"));
             BufferedWriter hits = Files.newBufferedWriter(out.resolve("hits.csv"));
             BufferedWriter games = Files.newBufferedWriter(out.resolve("games.csv"))) {
            sab.write("game,turn,leader,player,region,chosen,oppChosen,instab,cells,oppCells,ownCells,neutralCells,"
                    + "oppActive,ownActive,dOpp,dOwn,oppPlacedPrev,oppPlacedPrev2,ownPlacedPrev,"
                    + "routesAny,routeMinCostAny,routesOppOwn,routeMinCostOppOwn,routesMeOwn,"
                    + "oppRouteTraffic,freeCells,regionSize,closes,adjTownRegion,hitPrevByMe,hitPrevByOpp\n");
            build.write("game,turn,leader,player,count,paint,inactivePairs,servedPairs,onMinCostAny,onMinCostOwn,"
                    + "onMaxGainAny,onMaxRatioAny,onMaxRatioOwn,offRoute,adjOwn,adjTown,protectedCells,hostageCells,"
                    + "sharedRegionCells,instab1Cells,instab2Cells,distinctRegions,continuesPrev,"
                    + "minCostAny,minCostOwn,chosenCostAny,chosenCostOwn,chosenGainOwn,incomeMe,incomeOpp,"
                    + "completesNow,ownIncomeGainNow,oppIncomeChangeNow,cellsOnOppActivePath\n");
            hits.write("game,turn,leader,player,region,instabBefore,inkedTurn,lastHitter,hitsByMe,hitsByOpp,"
                    + "oppCellsAtHit,ownCellsAtHit,oppActiveAtHit\n");
            games.write("game,leader,opponent,turns,scoreLeader,scoreOpp,leaderWon,lastInkTurn,lastInkBy,"
                    + "leaderAheadAtLastInk,closedGame,leaderP0\n");
            int n = 0;
            for (Path f : CgReplay.listReplays(dir)) {
                CgReplay r = CgReplay.load(f);
                int me = r.nicknames[0].equals(player) ? 0 : r.nicknames[1].equals(player) ? 1 : -1;
                if (me < 0) continue;
                new GameRun(r, me).run(sab, build, hits, games);
                n++;
            }
            System.out.println("games processed: " + n + " -> " + out);
        }
    }

    static final class GameRun {
        final CgReplay r;
        final int me;
        final GameState s;
        final Pathing pathing;
        final int[] placedTurn, placedBy;
        final int[][] hitTurns; // [player][region] = last turn player hit region
        final int[][] hitCounts;
        final List<int[]> pendingHits = new ArrayList<>(); // game-local rows awaiting ink turn
        final int[] inkedTurn;
        final int[] lastHitter;
        final int[] inc = new int[2], inc2 = new int[2];
        final int[] path;
        final boolean[] blocked;
        final int[] prevServed = new int[2];

        GameRun(CgReplay r, int me) {
            this.r = r;
            this.me = me;
            s = r.toState();
            pathing = new Pathing(s.size);
            placedTurn = new int[s.size];
            placedBy = new int[s.size];
            Arrays.fill(placedTurn, -99);
            Arrays.fill(placedBy, -1);
            hitTurns = new int[2][s.regionCount];
            hitCounts = new int[2][s.regionCount];
            for (int[] a : hitTurns) Arrays.fill(a, -99);
            inkedTurn = new int[s.regionCount];
            lastHitter = new int[s.regionCount];
            Arrays.fill(inkedTurn, -1);
            Arrays.fill(lastHitter, -1);
            path = new int[s.size];
            blocked = new boolean[s.size];
            Arrays.fill(prevServed, -1);
        }

        void run(BufferedWriter sab, BufferedWriter build, BufferedWriter hits, BufferedWriter games) throws IOException {
            Rules.updateConnections(s, pathing);
            int lastInkTurn = -1, lastInkBy = -1;
            boolean leaderAheadAtLastInk = false;
            for (int t = 0; t < r.turns(); t++) {
                String[] lines = r.turnStdout.get(t);
                @SuppressWarnings("unchecked")
                List<Action>[] acts = new List[2];
                for (int p = 0; p < 2; p++) {
                    try {
                        acts[p] = lines[p] == null || r.failure(t, p) != null
                                ? List.of(Action.waitAction()) : Action.parseLine(lines[p]);
                    } catch (RuntimeException e) {
                        acts[p] = List.of(Action.waitAction());
                    }
                }
                int[] target = {-1, -1};
                for (int p = 0; p < 2; p++) {
                    for (Action a : acts[p]) if (a.type == Action.Type.DISRUPT && target[p] < 0) {
                        int region = a.disruptRegion(s);
                        if (region >= 0 && region < s.regionCount && !s.regionInked[region] && !s.regionHasTown(region))
                            target[p] = region;
                    }
                }
                GameState pre = s.copy();
                Rules.TurnResult tr = Rules.resolve(s, acts[0], acts[1], pathing);
                for (int p = 0; p < 2; p++) {
                    writeSabotage(sab, pre, t, p, target[p], target[1 - p]);
                    writeBuild(build, pre, t, p, tr.moves[p].cells, tr.moves[p].count);
                }
                for (int p = 0; p < 2; p++) {
                    if (target[p] < 0) continue;
                    int region = target[p];
                    int oppCells = 0, ownCells = 0, oppActive = 0;
                    for (int c = 0; c < pre.size; c++) {
                        if (pre.regionId[c] != region) continue;
                        if (pre.owner[c] == 1 - p) { oppCells++; if (onActivePath(pre, c)) oppActive++; }
                        else if (pre.owner[c] == p) ownCells++;
                    }
                    hitCounts[p][region]++;
                    pendingHits.add(new int[]{t, p, region, pre.regionInstability[region], hitCounts[p][region],
                            hitCounts[1 - p][region], oppCells, ownCells, oppActive});
                    hitTurns[p][region] = t;
                    lastHitter[region] = p;
                }
                for (int p = 0; p < 2; p++) {
                    for (int i = 0; i < tr.moves[p].count; i++) {
                        int c = tr.moves[p].cells[i];
                        placedTurn[c] = t;
                        placedBy[c] = s.owner[c];
                    }
                }
                for (int region : tr.inkedRegions) {
                    inkedTurn[region] = t;
                    lastInkTurn = t;
                    lastInkBy = -1;
                    for (int p = 0; p < 2; p++) if (target[p] == region) lastInkBy = lastInkBy < 0 ? p : 2;
                    leaderAheadAtLastInk = s.score[me] > s.score[1 - me];
                }
            }
            for (int[] h : pendingHits) {
                int region = h[2];
                hits.write(r.gameId + "," + h[0] + "," + me + "," + h[1] + "," + region + "," + h[3] + ","
                        + inkedTurn[region] + "," + lastHitter[region] + "," + h[4] + "," + h[5] + ","
                        + h[6] + "," + h[7] + "," + h[8] + "\n");
            }
            boolean closed = r.turns() < GameState.DEFAULT_MAX_TURNS;
            games.write(r.gameId + "," + me + "," + r.nicknames[1 - me].replace(',', ' ') + "," + r.turns() + ","
                    + s.score[me] + "," + s.score[1 - me] + "," + (s.score[me] > s.score[1 - me] ? 1 : 0) + ","
                    + lastInkTurn + "," + (lastInkBy < 0 ? -1 : lastInkBy == 2 ? 2 : lastInkBy == me ? 1 : 0) + ","
                    + (leaderAheadAtLastInk ? 1 : 0) + "," + (closed ? 1 : 0) + "," + (me == 0 ? 1 : 0) + "\n");
        }

        static boolean onActivePath(GameState s, int cell) {
            for (int p = 0; p < s.pairCount; p++) {
                if (!s.pairActive(p)) continue;
                for (int i = 0; i < s.pairPathLen[p]; i++) if (s.pairPath[p][i] == cell) return true;
            }
            return false;
        }

        /** Route traffic per region from the cheapest completion path of every inactive pair (core Pathing). */
        void routeTraffic(GameState pre, int player, boolean ownOnly, int[] routes, int[] minCost, double[] traffic) {
            Arrays.fill(routes, 0);
            Arrays.fill(minCost, Integer.MAX_VALUE);
            if (traffic != null) Arrays.fill(traffic, 0);
            boolean[] block = null;
            if (ownOnly) {
                for (int c = 0; c < pre.size; c++) blocked[c] = pre.owner[c] == 1 - player;
                block = blocked;
            }
            boolean[] seen = new boolean[pre.regionCount];
            for (int pair = 0; pair < pre.pairCount; pair++) {
                if (pre.pairActive(pair)) continue;
                int n = pathing.cheapestPath(pre, pre.townCell[pre.pairFrom[pair]], pre.townCell[pre.pairTo[pair]], path, block);
                if (n <= 0) continue;
                int cost = 0;
                for (int i = 0; i < n; i++) if (pre.canBuild(path[i])) cost += pre.placeCost(path[i]);
                if (cost == 0) continue;
                Arrays.fill(seen, false);
                for (int i = 0; i < n; i++) {
                    int region = pre.regionId[path[i]];
                    if (seen[region] || pre.regionHasTown(region)) continue;
                    seen[region] = true;
                    routes[region]++;
                    minCost[region] = Math.min(minCost[region], cost);
                    if (traffic != null) traffic[region] += 1.0 / cost;
                }
            }
        }

        void writeSabotage(BufferedWriter w, GameState pre, int t, int player, int chosen, int oppChosen) throws IOException {
            int opp = 1 - player;
            int[] routesAny = new int[pre.regionCount], minAny = new int[pre.regionCount];
            int[] routesOppOwn = new int[pre.regionCount], minOppOwn = new int[pre.regionCount];
            int[] routesMeOwn = new int[pre.regionCount], minMeOwn = new int[pre.regionCount];
            double[] oppTraffic = new double[pre.regionCount];
            routeTraffic(pre, player, false, routesAny, minAny, null);
            routeTraffic(pre, opp, true, routesOppOwn, minOppOwn, oppTraffic);
            routeTraffic(pre, player, true, routesMeOwn, minMeOwn, null);
            int[] cells = new int[pre.regionCount], oppCells = new int[pre.regionCount], ownCells = new int[pre.regionCount];
            int[] neutral = new int[pre.regionCount], oppActive = new int[pre.regionCount], ownActive = new int[pre.regionCount];
            int[] free = new int[pre.regionCount], oppPrev = new int[pre.regionCount], oppPrev2 = new int[pre.regionCount];
            int[] ownPrev = new int[pre.regionCount];
            boolean[] adjTown = new boolean[pre.regionCount];
            for (int c = 0; c < pre.size; c++) {
                int region = pre.regionId[c];
                cells[region]++;
                if (pre.canBuild(c)) free[region]++;
                int o = pre.owner[c];
                boolean active = o != GameState.NO_TRACK && onActivePath(pre, c);
                if (o == opp) { oppCells[region]++; if (active) oppActive[region]++; }
                else if (o == player) { ownCells[region]++; if (active) ownActive[region]++; }
                else if (o == GameState.NEUTRAL) neutral[region]++;
                if (o == opp && placedTurn[c] == t - 1) oppPrev[region]++;
                if (o == opp && placedTurn[c] >= t - 2 && placedTurn[c] <= t - 1) oppPrev2[region]++;
                if (o == player && placedTurn[c] == t - 1) ownPrev[region]++;
                int x = pre.x(c), y = pre.y(c);
                for (int d = 0; d < 4; d++) {
                    int nx = x + GameState.DX[d], ny = y + GameState.DY[d];
                    if (!pre.inBounds(nx, ny)) continue;
                    int nr = pre.regionId[pre.cell(nx, ny)];
                    if (nr != region && pre.regionHasTown(nr)) adjTown[region] = true;
                }
            }
            Rules.incomePerTurn(pre, inc);
            for (int region = 0; region < pre.regionCount; region++) {
                if (pre.regionInked[region] || pre.regionHasTown(region)) continue;
                boolean interesting = cells[region] - free[region] > 0 || routesAny[region] > 0 || routesOppOwn[region] > 0
                        || routesMeOwn[region] > 0 || pre.regionInstability[region] > 0 || region == chosen || region == oppChosen;
                if (!interesting) continue;
                int dOpp = 0, dOwn = 0, closes = 0;
                if (oppCells[region] + ownCells[region] + neutral[region] > 0 || pre.regionInstability[region] == 3) {
                    GameState c = pre.copy();
                    Rules.ink(c, region);
                    Rules.updateConnections(c, pathing);
                    Rules.incomePerTurn(c, inc2);
                    dOpp = inc[opp] - inc2[opp];
                    dOwn = inc[player] - inc2[player];
                    if (pre.regionInstability[region] == 3 && !Rules.anyPairPossible(c, pathing)) closes = 1;
                }
                w.write(r.gameId + "," + t + "," + me + "," + player + "," + region + "," + (region == chosen ? 1 : 0) + ","
                        + (region == oppChosen ? 1 : 0) + "," + pre.regionInstability[region] + "," + cells[region] + ","
                        + oppCells[region] + "," + ownCells[region] + "," + neutral[region] + ","
                        + oppActive[region] + "," + ownActive[region] + "," + dOpp + "," + dOwn + ","
                        + oppPrev[region] + "," + oppPrev2[region] + "," + ownPrev[region] + ","
                        + routesAny[region] + "," + (minAny[region] == Integer.MAX_VALUE ? -1 : minAny[region]) + ","
                        + routesOppOwn[region] + "," + (minOppOwn[region] == Integer.MAX_VALUE ? -1 : minOppOwn[region]) + ","
                        + routesMeOwn[region] + "," + String.format(Locale.ROOT, "%.4f", oppTraffic[region]) + ","
                        + free[region] + "," + cells[region] + "," + closes + "," + (adjTown[region] ? 1 : 0) + ","
                        + (hitTurns[player][region] == t - 1 ? 1 : 0) + "," + (hitTurns[opp][region] == t - 1 ? 1 : 0) + "\n");
            }
        }

        void writeBuild(BufferedWriter w, GameState pre, int t, int player, int[] placed, int count) throws IOException {
            int opp = 1 - player;
            Rules.incomePerTurn(pre, inc);
            int paint = 0;
            for (int i = 0; i < count; i++) paint += pre.placeCost(placed[i]);
            // Cheapest completion per inactive pair, any tracks free (shared) and own-only (avoid enemy rails).
            int inactive = 0;
            int minCostAny = Integer.MAX_VALUE, minCostOwn = Integer.MAX_VALUE, maxGainAny = Integer.MIN_VALUE;
            double maxRatioAny = -1, maxRatioOwn = -1;
            int minCostAnyPair = -1, minCostOwnPair = -1, maxGainAnyPair = -1, maxRatioAnyPair = -1, maxRatioOwnPair = -1;
            boolean[][] onAny = new boolean[pre.pairCount][], onOwn = new boolean[pre.pairCount][];
            int[] costAny = new int[pre.pairCount], costOwn = new int[pre.pairCount], gainOwnRoute = new int[pre.pairCount];
            Arrays.fill(costAny, -1);
            Arrays.fill(costOwn, -1);
            for (int c = 0; c < pre.size; c++) blocked[c] = pre.owner[c] == opp;
            for (int pair = 0; pair < pre.pairCount; pair++) {
                if (pre.pairActive(pair)) continue;
                inactive++;
                for (int variant = 0; variant < 2; variant++) {
                    int n = pathing.cheapestPath(pre, pre.townCell[pre.pairFrom[pair]], pre.townCell[pre.pairTo[pair]], path,
                            variant == 0 ? null : blocked);
                    if (n <= 0) continue;
                    int cost = 0;
                    boolean[] on = new boolean[pre.size];
                    GameState c = pre.copy();
                    for (int i = 0; i < n; i++) {
                        if (pre.canBuild(path[i])) { cost += pre.placeCost(path[i]); on[path[i]] = true; c.owner[path[i]] = player; }
                    }
                    if (cost == 0) continue;
                    Rules.updateConnections(c, pathing);
                    Rules.incomePerTurn(c, inc2);
                    int gain = inc2[player] - inc[player] - (inc2[opp] - inc[opp]);
                    double ratio = (double) gain / cost;
                    if (variant == 0) {
                        onAny[pair] = on; costAny[pair] = cost;
                        if (cost < minCostAny) { minCostAny = cost; minCostAnyPair = pair; }
                        if (gain > maxGainAny) { maxGainAny = gain; maxGainAnyPair = pair; }
                        if (ratio > maxRatioAny) { maxRatioAny = ratio; maxRatioAnyPair = pair; }
                    } else {
                        onOwn[pair] = on; costOwn[pair] = cost; gainOwnRoute[pair] = gain;
                        if (cost < minCostOwn) { minCostOwn = cost; minCostOwnPair = pair; }
                        if (ratio > maxRatioOwn) { maxRatioOwn = ratio; maxRatioOwnPair = pair; }
                    }
                }
            }
            int onMinAny = 0, onMinOwn = 0, onMaxGain = 0, onMaxRatioA = 0, onMaxRatioO = 0, off = 0;
            int adjOwn = 0, adjTown = 0, prot = 0, hostage = 0, shared = 0, instab1 = 0, instab2 = 0, onOppActive = 0;
            boolean[] regionSeen = new boolean[pre.regionCount];
            int distinctRegions = 0;
            int[] servedCount = new int[pre.pairCount];
            for (int i = 0; i < count; i++) {
                int c = placed[i];
                boolean any = false;
                for (int pair = 0; pair < pre.pairCount; pair++) {
                    boolean a = onAny[pair] != null && onAny[pair][c], o = onOwn[pair] != null && onOwn[pair][c];
                    if (a || o) { any = true; servedCount[pair]++; }
                }
                if (!any) off++;
                if (minCostAnyPair >= 0 && onAny[minCostAnyPair][c]) onMinAny++;
                if (minCostOwnPair >= 0 && onOwn[minCostOwnPair][c]) onMinOwn++;
                if (maxGainAnyPair >= 0 && onAny[maxGainAnyPair][c]) onMaxGain++;
                if (maxRatioAnyPair >= 0 && onAny[maxRatioAnyPair][c]) onMaxRatioA++;
                if (maxRatioOwnPair >= 0 && onOwn[maxRatioOwnPair][c]) onMaxRatioO++;
                int region = pre.regionId[c];
                if (!regionSeen[region]) { regionSeen[region] = true; distinctRegions++; }
                if (pre.regionHasTown(region)) prot++;
                if (pre.regionInstability[region] >= 1) instab1++;
                if (pre.regionInstability[region] >= 2) instab2++;
                boolean oppInRegion = false, oppActiveInRegion = false;
                for (int q = 0; q < pre.size; q++) {
                    if (pre.regionId[q] != region || pre.owner[q] != opp) continue;
                    oppInRegion = true;
                    if (onActivePath(pre, q)) { oppActiveInRegion = true; break; }
                }
                if (oppInRegion) shared++;
                if (oppActiveInRegion) hostage++;
                if (onActivePath(pre, c)) onOppActive++;
                int x = pre.x(c), y = pre.y(c);
                boolean aOwn = false, aTown = false;
                for (int d = 0; d < 4; d++) {
                    int nx = x + GameState.DX[d], ny = y + GameState.DY[d];
                    if (!pre.inBounds(nx, ny)) continue;
                    int nc = pre.cell(nx, ny);
                    if (pre.owner[nc] == player) aOwn = true;
                    if (pre.isTown(nc)) aTown = true;
                }
                if (aOwn) adjOwn++;
                if (aTown) adjTown++;
            }
            int served = 0, bestServed = -1;
            for (int pair = 0; pair < pre.pairCount; pair++) {
                if (servedCount[pair] > 0) served++;
                if (bestServed < 0 || servedCount[pair] > servedCount[bestServed]) bestServed = pair;
            }
            if (bestServed >= 0 && servedCount[bestServed] == 0) bestServed = -1;
            int continues = bestServed >= 0 && bestServed == prevServed[player] ? 1 : 0;
            if (count > 0) prevServed[player] = bestServed;
            // Immediate effect of the actual placements alone (no opponent, no sabotage): completes a pair now?
            GameState c = pre.copy();
            for (int i = 0; i < count; i++) c.owner[placed[i]] = player;
            Rules.updateConnections(c, pathing);
            Rules.incomePerTurn(c, inc2);
            int completes = 0;
            for (int pair = 0; pair < pre.pairCount; pair++) if (!pre.pairActive(pair) && c.pairActive(pair)) completes++;
            w.write(r.gameId + "," + t + "," + me + "," + player + "," + count + "," + paint + "," + inactive + "," + served + ","
                    + onMinAny + "," + onMinOwn + "," + onMaxGain + "," + onMaxRatioA + "," + onMaxRatioO + "," + off + ","
                    + adjOwn + "," + adjTown + "," + prot + "," + hostage + "," + shared + "," + instab1 + "," + instab2 + ","
                    + distinctRegions + "," + continues + ","
                    + (minCostAny == Integer.MAX_VALUE ? -1 : minCostAny) + "," + (minCostOwn == Integer.MAX_VALUE ? -1 : minCostOwn) + ","
                    + (bestServed < 0 ? -1 : costAny[bestServed]) + "," + (bestServed < 0 ? -1 : costOwn[bestServed]) + ","
                    + (bestServed < 0 ? 0 : gainOwnRoute[bestServed]) + "," + inc[player] + "," + inc[opp] + ","
                    + completes + "," + (inc2[player] - inc[player]) + "," + (inc2[opp] - inc[opp]) + "," + onOppActive + "\n");
        }
    }
}
