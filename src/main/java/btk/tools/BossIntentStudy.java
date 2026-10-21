package btk.tools;

import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.core.TacticalPathing;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Descriptive study of the arena boss's announced intents ({@code MESSAGE build a>b}, {@code steal},
 * {@code block}, {@code fortify}) against the exact core state. Replays every boss game of a replay
 * directory through {@link Rules}; nothing re-implements rules. Local diagnostic only.
 *
 * <p>Args: replayDir bossNickname outDir. Writes {@code turns.csv} (one row per boss turn) and
 * {@code cells.csv} (one row per accepted boss placement).
 */
public final class BossIntentStudy {

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args.length > 0 && !args[0].isEmpty() ? args[0] : "replays/cg/boss");
        String boss = args.length > 1 && !args[1].isEmpty() ? args[1] : "Boss 4";
        Path out = Path.of(args.length > 2 && !args[2].isEmpty() ? args[2] : "build/analysis/boss-intent");
        Files.createDirectories(out);
        try (BufferedWriter turns = Files.newBufferedWriter(out.resolve("turns.csv"));
             BufferedWriter cells = Files.newBufferedWriter(out.resolve("cells.csv"));
             BufferedWriter pairs = Files.newBufferedWriter(out.resolve("pairs.csv"))) {
            writeHeaders(turns, cells);
            pairs.write("game,turn,pair,chosen,active,costM6,gainM6,costAny,gainAny,samePairAsPrev,turnsLeft,paintNow\n");
            int n = 0;
            for (Path f : CgReplay.listReplays(dir)) {
                CgReplay r = CgReplay.load(f);
                int me = r.nicknames[0].equals(boss) ? 0 : r.nicknames[1].equals(boss) ? 1 : -1;
                if (me < 0) continue;
                GameRun run = new GameRun(of(r), me);
                run.pairs = pairs;
                run.run(turns, cells);
                n++;
            }
            System.out.println("boss games processed: " + n + " -> " + out);
        }
    }

    static void writeHeaders(BufferedWriter turns, BufferedWriter cells) throws IOException {
            turns.write("game,turn,boss,oppName,intent,a,b,pairIdx,pairDir,placed,paint,target,sameTarget,prevTargetInked,"
                    + "targetInstab,targetOppCells,targetOwnCells,targetOppActive,targetLive2,targetNeeded,"
                    + "inactivePairs,pairCostAny,pairCostOwn,pairRankCostAny,pairRankGainAny,pairRankRatioAny,pairRankCostOwn,"
                    + "minCostAny,cellsOnAny,cellsOnOwn,cellsOnAnyU2,samePairAsPrev,pairActiveBefore,pairActiveAfterOwn,"
                    + "incomeBoss,incomeOpp,scoreBoss,scoreOpp,ownGainNow,oppChangeNow,turnsLeft,reachableModels,onM0,onM1,onM2,onM3,onM4,onM5,onM6,onM7\n");
            cells.write("game,turn,intent,cell,x,y,terrain,cost,regionInstab,townRegion,adjOwn,adjTown,onOppActive,onOwnActive,"
                    + "sharedRegion,oppActiveInRegion,optMask\n");
    }

    /** Minimal replay source shared by canonical CG replays and local referee games. */
    interface Source {
        GameState initialState();
        int turns();
        String[] lines(int turn);
        String failure(int turn, int player);
        String gameId();
        String nickname(int player);
    }

    static Source of(CgReplay r) {
        return new Source() {
            public GameState initialState() { return r.toState(); }
            public int turns() { return r.turns(); }
            public String[] lines(int turn) { return r.turnStdout.get(turn); }
            public String failure(int turn, int player) { return r.failure(turn, player); }
            public String gameId() { return String.valueOf(r.gameId); }
            public String nickname(int player) { return r.nicknames[player]; }
        };
    }

    static final class GameRun {
        final Source r;
        final int me;
        final GameState s;
        final Pathing pathing;
        final int[] placedTurn;
        final int[] path, extra;
        final boolean[] blocked;
        final int[] inc = new int[2], inc2 = new int[2];
        final TacticalPathing tactical;
        final int[] entry, dA, dB;
        static final int MODELS = 8;
        int prevTarget = -1, prevPair = -1;
        BufferedWriter pairs;
        boolean prevTargetInked = false;

        GameRun(Source r, int me) {
            this.r = r;
            this.me = me;
            s = r.initialState();
            pathing = new Pathing(s.size);
            placedTurn = new int[s.size];
            Arrays.fill(placedTurn, -99);
            path = new int[s.size];
            extra = new int[s.size];
            blocked = new boolean[s.size];
            tactical = new TacticalPathing(s.size);
            entry = new int[s.size]; dA = new int[s.size]; dB = new int[s.size];
        }

        /** Entry-cost model m for player me; see BossIntentStudy docs. */
        void entryCosts(GameState pre, int m, int me) {
            int opp = 1 - me;
            for (int c = 0; c < pre.size; c++) {
                int r = pre.regionId[c];
                boolean town = pre.regionHasTown(r);
                int inst = town ? 0 : pre.regionInstability[r];
                int cost;
                if (pre.inked[c]) cost = TacticalPathing.INF;
                else if (pre.isTown(c)) cost = 0;
                else if (pre.owner[c] == me) cost = 0;
                else if (pre.owner[c] == GameState.NEUTRAL) cost = (m == 7) ? TacticalPathing.INF : 0;
                else if (pre.owner[c] == opp) cost = (m == 1 || m == 6 || m == 7) ? TacticalPathing.INF : 0;
                else {
                    int paint = pre.placeCost(c);
                    switch (m) {
                        case 2: cost = paint + inst; break;
                        case 3: case 6: cost = inst >= 2 ? TacticalPathing.INF : paint; break;
                        case 4: cost = paint + 2 * inst; break;
                        case 5: cost = 1; break;
                        default: cost = paint;
                    }
                }
                entry[c] = cost;
            }
        }

        /** Bitmask of models under which free cell c lies on some optimal A->B route. */
        int optimalMask(GameState pre, int me, int pairIdx, int[] cellsArr, int count, int[] maskOut) {
            int from = pre.townCell[pre.pairFrom[pairIdx]], to = pre.townCell[pre.pairTo[pairIdx]];
            int reachable = 0;
            Arrays.fill(maskOut, 0, count, 0);
            for (int m = 0; m < MODELS; m++) {
                entryCosts(pre, m, me);
                tactical.weightedDistances(pre, from, entry, false, dA);
                tactical.weightedDistances(pre, to, entry, true, dB);
                int total = dA[to];
                if (total >= TacticalPathing.INF) continue;
                reachable |= 1 << m;
                for (int i = 0; i < count; i++) {
                    int c = cellsArr[i];
                    if (dA[c] < TacticalPathing.INF && dB[c] < TacticalPathing.INF && dA[c] + dB[c] == total) maskOut[i] |= 1 << m;
                }
            }
            return reachable;
        }

        void run(BufferedWriter turns, BufferedWriter cells) throws IOException {
            Rules.updateConnections(s, pathing);
            for (int t = 0; t < r.turns(); t++) {
                String[] lines = r.lines(t);
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
                int target = -1;
                String message = "";
                for (Action a : acts[me]) {
                    if (a.type == Action.Type.DISRUPT && target < 0) {
                        int region = a.disruptRegion(s);
                        if (region >= 0 && region < s.regionCount && !s.regionInked[region] && !s.regionHasTown(region))
                            target = region;
                    }
                    if (a.type == Action.Type.MESSAGE) message = a.text == null ? "" : a.text.trim();
                }
                GameState pre = s.copy();
                Rules.TurnResult tr = Rules.resolve(s, acts[0], acts[1], pathing);
                writeTurn(turns, cells, pre, t, target, message, tr.moves[me].cells, tr.moves[me].count);
                for (int p = 0; p < 2; p++) for (int i = 0; i < tr.moves[p].count; i++) placedTurn[tr.moves[p].cells[i]] = t;
                prevTargetInked = target >= 0 && tr.inkedRegions.contains(target);
                prevTarget = target;
            }
        }

        static boolean onActivePathOf(GameState s, int cell, int owner) {
            for (int p = 0; p < s.pairCount; p++) {
                if (!s.pairActive(p)) continue;
                for (int i = 0; i < s.pairPathLen[p]; i++) if (s.pairPath[p][i] == cell) {
                    if (owner < 0) return true;
                    // the cell itself belongs to `owner`
                    return s.owner[cell] == owner;
                }
            }
            return false;
        }

        int findPair(int a, int b) {
            for (int p = 0; p < s.pairCount; p++) if (s.pairFrom[p] == a && s.pairTo[p] == b) return p;
            return -1;
        }

        void writeTurn(BufferedWriter w, BufferedWriter cw, GameState pre, int t, int target, String message,
                       int[] placed, int count) throws IOException {
            int opp = 1 - me;
            String intent = "none";
            int a = -1, b = -1;
            if (!message.isEmpty()) {
                String[] parts = message.split("\\s+");
                intent = parts[0].toLowerCase(Locale.ROOT);
                if (parts.length > 1 && parts[1].contains(">")) {
                    String[] ab = parts[1].split(">");
                    try { a = Integer.parseInt(ab[0]); b = Integer.parseInt(ab[1]); } catch (NumberFormatException e) { a = -1; b = -1; }
                }
            }
            int pairIdx = -1, pairDir = 0;
            if (a >= 0 && b >= 0 && a < pre.townCount && b < pre.townCount) {
                pairIdx = findPair(a, b);
                if (pairIdx >= 0) pairDir = 1; else { pairIdx = findPair(b, a); if (pairIdx >= 0) pairDir = -1; }
            }
            int paint = 0;
            for (int i = 0; i < count; i++) paint += pre.placeCost(placed[i]);
            // target features
            int tInstab = -1, tOpp = 0, tOwn = 0, tOppActive = 0, tLive = 0, tNeeded = -1;
            if (target >= 0) {
                tInstab = pre.regionInstability[target];
                tNeeded = GameState.INK_THRESHOLD - tInstab;
                for (int c = 0; c < pre.size; c++) {
                    if (pre.regionId[c] != target) continue;
                    if (pre.owner[c] == opp) { tOpp++; if (onActivePathOf(pre, c, -1)) tOppActive++; if (placedTurn[c] >= t - 2) tLive++; }
                    else if (pre.owner[c] == me) tOwn++;
                }
            }
            // pair ranking among inactive pairs
            Rules.incomePerTurn(pre, inc);
            int inactive = 0;
            int[] costAny = new int[pre.pairCount], costOwn = new int[pre.pairCount], gainAny = new int[pre.pairCount];
            double[] ratioAny = new double[pre.pairCount];
            boolean[][] onAny = new boolean[pre.pairCount][], onOwn = new boolean[pre.pairCount][], onAnyU2 = new boolean[pre.pairCount][];
            Arrays.fill(costAny, -1); Arrays.fill(costOwn, -1);
            for (int c = 0; c < pre.size; c++) {
                blocked[c] = pre.owner[c] == opp;
                extra[c] = pre.regionInstability[pre.regionId[c]] >= 2 && !pre.regionHasTown(pre.regionId[c]) ? 1000 : 0;
            }
            int minCostAny = Integer.MAX_VALUE;
            for (int pair = 0; pair < pre.pairCount; pair++) {
                if (pre.pairActive(pair)) continue;
                inactive++;
                for (int variant = 0; variant < 3; variant++) {
                    int n = pathing.cheapestPath(pre, pre.townCell[pre.pairFrom[pair]], pre.townCell[pre.pairTo[pair]], path,
                            variant == 1 ? blocked : null, variant == 2 ? extra : null);
                    if (n <= 0) continue;
                    int cost = 0;
                    boolean[] on = new boolean[pre.size];
                    GameState c = pre.copy();
                    for (int i = 0; i < n; i++) if (pre.canBuild(path[i])) { cost += pre.placeCost(path[i]); on[path[i]] = true; c.owner[path[i]] = me; }
                    if (variant == 2 && cost >= 1000) continue;
                    if (variant == 0) {
                        costAny[pair] = cost; onAny[pair] = on;
                        Rules.updateConnections(c, pathing); Rules.incomePerTurn(c, inc2);
                        gainAny[pair] = (inc2[me] - inc[me]) - (inc2[opp] - inc[opp]);
                        ratioAny[pair] = cost == 0 ? 0 : (double) gainAny[pair] / cost;
                        if (cost > 0) minCostAny = Math.min(minCostAny, cost);
                    } else if (variant == 1) { costOwn[pair] = cost; onOwn[pair] = on; }
                    else onAnyU2[pair] = on;
                }
            }
            if (pairs != null && intent.equals("build") && pairIdx >= 0) {
                for (int pair = 0; pair < pre.pairCount; pair++) {
                    int costM6 = -1, gainM6 = 0;
                    entryCosts(pre, 6, me);
                    int n = tactical.weightedPath(pre, pre.townCell[pre.pairFrom[pair]], pre.townCell[pre.pairTo[pair]], entry, path);
                    if (n > 0) {
                        GameState c = pre.copy(); costM6 = 0;
                        for (int i = 0; i < n; i++) if (pre.canBuild(path[i])) { costM6 += pre.placeCost(path[i]); c.owner[path[i]] = me; }
                        if (costM6 > 0) {
                            Rules.updateConnections(c, pathing); Rules.incomePerTurn(c, inc2);
                            gainM6 = (inc2[me] - inc[me]) - (inc2[opp] - inc[opp]);
                        }
                    }
                    pairs.write(r.gameId() + "," + t + "," + pair + "," + (pair == pairIdx ? 1 : 0) + "," + (pre.pairActive(pair) ? 1 : 0) + ","
                            + costM6 + "," + gainM6 + "," + costAny[pair] + "," + gainAny[pair] + "," + (pair == prevPair ? 1 : 0) + ","
                            + pre.turnsLeft() + "," + GameState.PAINT_PER_TURN + "\n");
                }
            }
            int rankCostAny = -1, rankGainAny = -1, rankRatioAny = -1, rankCostOwn = -1;
            boolean pairActiveBefore = pairIdx >= 0 && pre.pairActive(pairIdx);
            if (pairIdx >= 0 && !pairActiveBefore && costAny[pairIdx] >= 0) {
                rankCostAny = 1; rankGainAny = 1; rankRatioAny = 1; rankCostOwn = 1;
                for (int pair = 0; pair < pre.pairCount; pair++) {
                    if (pair == pairIdx || pre.pairActive(pair) || costAny[pair] < 0) continue;
                    if (costAny[pair] < costAny[pairIdx]) rankCostAny++;
                    if (gainAny[pair] > gainAny[pairIdx]) rankGainAny++;
                    if (ratioAny[pair] > ratioAny[pairIdx]) rankRatioAny++;
                    if (costOwn[pair] >= 0 && costOwn[pairIdx] >= 0 && costOwn[pair] < costOwn[pairIdx]) rankCostOwn++;
                }
            }
            int cellsOnAny = 0, cellsOnOwn = 0, cellsOnAnyU2 = 0;
            for (int i = 0; i < count; i++) {
                int c = placed[i];
                if (pairIdx >= 0 && onAny[pairIdx] != null && onAny[pairIdx][c]) cellsOnAny++;
                if (pairIdx >= 0 && onOwn[pairIdx] != null && onOwn[pairIdx][c]) cellsOnOwn++;
                if (pairIdx >= 0 && onAnyU2[pairIdx] != null && onAnyU2[pairIdx][c]) cellsOnAnyU2++;
            }
            int[] masks = new int[Math.max(1, count)];
            int reachableModels = 0;
            if (pairIdx >= 0 && count > 0) reachableModels = optimalMask(pre, me, pairIdx, placed, count, masks);
            int[] onModel = new int[GameRun.MODELS];
            for (int i = 0; i < count; i++) for (int m = 0; m < GameRun.MODELS; m++) if ((masks[i] >> m & 1) != 0) onModel[m]++;
            // immediate effect of own placements alone
            GameState c2 = pre.copy();
            for (int i = 0; i < count; i++) c2.owner[placed[i]] = me;
            Rules.updateConnections(c2, pathing); Rules.incomePerTurn(c2, inc2);
            boolean pairActiveAfterOwn = pairIdx >= 0 && c2.pairActive(pairIdx);
            w.write(r.gameId() + "," + t + "," + me + "," + r.nickname(opp).replace(',', ' ') + "," + intent + "," + a + "," + b + ","
                    + pairIdx + "," + pairDir + "," + count + "," + paint + "," + target + ","
                    + (target >= 0 && target == prevTarget ? 1 : 0) + "," + (prevTargetInked ? 1 : 0) + ","
                    + tInstab + "," + tOpp + "," + tOwn + "," + tOppActive + "," + tLive + "," + tNeeded + ","
                    + inactive + "," + (pairIdx >= 0 ? costAny[pairIdx] : -1) + "," + (pairIdx >= 0 ? costOwn[pairIdx] : -1) + ","
                    + rankCostAny + "," + rankGainAny + "," + rankRatioAny + "," + rankCostOwn + ","
                    + (minCostAny == Integer.MAX_VALUE ? -1 : minCostAny) + "," + cellsOnAny + "," + cellsOnOwn + "," + cellsOnAnyU2 + ","
                    + (pairIdx >= 0 && pairIdx == prevPair ? 1 : 0) + "," + (pairActiveBefore ? 1 : 0) + "," + (pairActiveAfterOwn ? 1 : 0) + ","
                    + inc[me] + "," + inc[opp] + "," + pre.score[me] + "," + pre.score[opp] + ","
                    + (inc2[me] - inc[me]) + "," + (inc2[opp] - inc[opp]) + "," + pre.turnsLeft() + "," + reachableModels
                    + "," + onModel[0] + "," + onModel[1] + "," + onModel[2] + "," + onModel[3] + "," + onModel[4] + "," + onModel[5] + "," + onModel[6] + "," + onModel[7] + "\n");
            if (pairIdx >= 0) prevPair = pairIdx;
            for (int i = 0; i < count; i++) {
                int c = placed[i];
                int region = pre.regionId[c];
                boolean adjOwn = false, adjTown = false, shared = false, oppActiveInRegion = false;
                int x = pre.x(c), y = pre.y(c);
                for (int d = 0; d < 4; d++) {
                    int nx = x + GameState.DX[d], ny = y + GameState.DY[d];
                    if (!pre.inBounds(nx, ny)) continue;
                    int nc = pre.cell(nx, ny);
                    if (pre.owner[nc] == me) adjOwn = true;
                    if (pre.isTown(nc)) adjTown = true;
                }
                for (int q = 0; q < pre.size; q++) {
                    if (pre.regionId[q] != region || pre.owner[q] != opp) continue;
                    shared = true;
                    if (onActivePathOf(pre, q, -1)) { oppActiveInRegion = true; break; }
                }
                boolean onOppActive = false, onOwnActive = false;
                for (int p = 0; p < pre.pairCount; p++) {
                    if (!pre.pairActive(p)) continue;
                    // cell is free, so it cannot be ON a path; check adjacency to an active path instead
                    for (int k = 0; k < pre.pairPathLen[p]; k++) {
                        int pc = pre.pairPath[p][k];
                        if (Math.abs(pre.x(pc) - x) + Math.abs(pre.y(pc) - y) == 1) {
                            if (pre.owner[pc] == opp) onOppActive = true;
                            if (pre.owner[pc] == me) onOwnActive = true;
                        }
                    }
                }
                cw.write(r.gameId() + "," + t + "," + intent + "," + c + "," + x + "," + y + "," + pre.terrain[c] + "," + pre.placeCost(c) + ","
                        + pre.regionInstability[region] + "," + (pre.regionHasTown(region) ? 1 : 0) + "," + (adjOwn ? 1 : 0) + ","
                        + (adjTown ? 1 : 0) + "," + (onOppActive ? 1 : 0) + "," + (onOwnActive ? 1 : 0) + "," + (shared ? 1 : 0) + ","
                        + (oppActiveInRegion ? 1 : 0) + "," + masks[i] + "\n");
            }
        }
    }
}
