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

/**
 * Per-cell construction study: one row per track placed by either player, with the routes the
 * cell lay on at placement time and what the cell did afterwards (first earning turn, which pairs
 * it served and how they were classified at placement time, peak simultaneous pairs, lifetime
 * points, loss turn). Rules, incomes and connections come from {@code btk.core}; the route
 * hypotheses (completion / steal / bypass / lengthening) are analysis-only BFS over the public
 * board and never feed a bot.
 *
 * <p>Args: replayDir playerNickname outDir. Writes {@code cells.csv}.
 */
public final class CgCellStudy {

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args.length > 0 && !args[0].isEmpty() ? args[0] : "replays/cg-indrill");
        String player = args.length > 1 && !args[1].isEmpty() ? args[1] : "Indrill";
        Path out = Path.of(args.length > 2 && !args[2].isEmpty() ? args[2] : "build/analysis/leader-study");
        Files.createDirectories(out);
        try (BufferedWriter w = Files.newBufferedWriter(out.resolve("cells.csv"))) {
            w.write("game,gameTurns,turn,leader,player,cell,x,y,region,cost,townRegion,instab,oppInRegion,ownInRegion,"
                    + "adjOwn,adjOpp,adjTown,adjAny,"
                    + "onCompletionAny,onCompletionOwn,onShortestCells,onSteal,onBypass,onExtend,"
                    + "slackAny,slackOwn,slackSafe,stealSlack,bypassSlack,slackAnyPairs0,"
                    + "completionRouteLen,activePairsAtT,inactivePairsAtT,"
                    + "firstEarnTurn,delay,pairsAtFirstEarn,pairsInactiveAtT,pairsStolen,pairsBypassed,pairsOther,"
                    + "firstEarnPathLen,firstEarnOwnCells,firstEarnOppCells,"
                    + "maxPairs,totalPoints,lostTurn,lifetime,neutralised\n");
            int n = 0;
            for (Path f : CgReplay.listReplays(dir)) {
                CgReplay r = CgReplay.load(f);
                int me = r.nicknames[0].equals(player) ? 0 : r.nicknames[1].equals(player) ? 1 : -1;
                if (me < 0) continue;
                new GameRun(r, me).run(w);
                n++;
            }
            System.out.println("games processed: " + n + " -> " + out.resolve("cells.csv"));
        }
    }

    /** One placed track being followed until it is lost. */
    static final class Track {
        int game, turn, player, cell;
        // placement-time facts
        int cost, region, instab, oppInRegion, ownInRegion;
        boolean townRegion, adjOwn, adjOpp, adjTown;
        boolean onCompletionAny, onCompletionOwn, onShortestCells, onSteal, onBypass, onExtend;
        int completionRouteLen, activePairsAtT, inactivePairsAtT;
        /** Paint (or cell) excess of the best route through this cell over the optimum, per hypothesis; -1 = none. */
        int slackAny = -1, slackOwn = -1, slackSafe = -1, stealSlack = 99, bypassSlack = 99, slackAnyPairs0;
        boolean[] pairActiveAtT, pairOppMajorityAtT, pairUnstableAtT;
        int[] pairPathLenAtT;
        // life
        int firstEarnTurn = -1, pairsAtFirstEarn, pairsInactiveAtT, pairsStolen, pairsBypassed, pairsOther;
        int firstEarnPathLen, firstEarnOwnCells, firstEarnOppCells;
        int maxPairs, totalPoints, lostTurn = -1;
        boolean neutralised, done;
    }

    static final class GameRun {
        final CgReplay r;
        final int me;
        final GameState s;
        final Pathing pathing;
        final int[] path, path2, bfsParent, bfsQueue;
        final boolean[] blocked, allow;
        final int[] inc = new int[2];
        final List<Track> live = new ArrayList<>();
        final List<Track> all = new ArrayList<>();

        GameRun(CgReplay r, int me) {
            this.r = r;
            this.me = me;
            s = r.toState();
            pathing = new Pathing(s.size);
            path = new int[s.size];
            path2 = new int[s.size];
            bfsParent = new int[s.size];
            bfsQueue = new int[s.size];
            blocked = new boolean[s.size];
            allow = new boolean[s.size];
        }

        void run(BufferedWriter w) throws IOException {
            Rules.updateConnections(s, pathing);
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
                GameState pre = s.copy();
                Rules.TurnResult tr = Rules.resolve(s, acts[0], acts[1], pathing);
                for (int p = 0; p < 2; p++) {
                    // Placement-time classification against the board both players saw.
                    boolean[][] completionAny = new boolean[pre.pairCount][];
                    boolean[][] completionOwn = new boolean[pre.pairCount][];
                    boolean[][] shortestCells = new boolean[pre.pairCount][];
                    boolean[][] steal = new boolean[pre.pairCount][];
                    boolean[][] bypass = new boolean[pre.pairCount][];
                    int[] completionLen = new int[pre.pairCount];
                    computeRoutes(pre, p, completionAny, completionOwn, shortestCells, steal, bypass, completionLen);
                    int[] slackAny = new int[pre.size], slackOwn = new int[pre.size], slackSafe = new int[pre.size];
                    int[] stealSlack = new int[pre.size], bypassSlack = new int[pre.size], slackAnyPairs0 = new int[pre.size];
                    if (tr.moves[p].count > 0)
                        computeSlack(pre, p, slackAny, slackOwn, slackSafe, stealSlack, bypassSlack, slackAnyPairs0);
                    for (int i = 0; i < tr.moves[p].count; i++) {
                        int c = tr.moves[p].cells[i];
                        Track k = new Track();
                        k.slackAny = slackAny[c]; k.slackOwn = slackOwn[c]; k.slackSafe = slackSafe[c];
                        k.stealSlack = stealSlack[c]; k.bypassSlack = bypassSlack[c]; k.slackAnyPairs0 = slackAnyPairs0[c];
                        k.game = 0; k.turn = t; k.player = p; k.cell = c;
                        k.cost = pre.placeCost(c); k.region = pre.regionId[c]; k.instab = pre.regionInstability[k.region];
                        k.townRegion = pre.regionHasTown(k.region);
                        for (int q = 0; q < pre.size; q++) {
                            if (pre.regionId[q] != k.region) continue;
                            if (pre.owner[q] == 1 - p) k.oppInRegion++;
                            else if (pre.owner[q] == p) k.ownInRegion++;
                        }
                        int x = pre.x(c), y = pre.y(c);
                        for (int d = 0; d < 4; d++) {
                            int nx = x + GameState.DX[d], ny = y + GameState.DY[d];
                            if (!pre.inBounds(nx, ny)) continue;
                            int nc = pre.cell(nx, ny);
                            if (pre.owner[nc] == p) k.adjOwn = true;
                            if (pre.owner[nc] == 1 - p) k.adjOpp = true;
                            if (pre.isTown(nc)) k.adjTown = true;
                        }
                        k.pairActiveAtT = new boolean[pre.pairCount];
                        k.pairOppMajorityAtT = new boolean[pre.pairCount];
                        k.pairUnstableAtT = new boolean[pre.pairCount];
                        k.pairPathLenAtT = new int[pre.pairCount];
                        k.completionRouteLen = -1;
                        for (int pair = 0; pair < pre.pairCount; pair++) {
                            boolean active = pre.pairActive(pair);
                            k.pairActiveAtT[pair] = active;
                            if (active) {
                                k.activePairsAtT++;
                                int own = 0, opp = 0;
                                for (int j = 0; j < pre.pairPathLen[pair]; j++) {
                                    int o = pre.owner[pre.pairPath[pair][j]];
                                    if (o == p) own++; else if (o == 1 - p) opp++;
                                    int rg = pre.regionId[pre.pairPath[pair][j]];
                                    if (pre.regionInstability[rg] >= 1 && !pre.regionHasTown(rg)) k.pairUnstableAtT[pair] = true;
                                }
                                k.pairOppMajorityAtT[pair] = opp > own;
                                k.pairPathLenAtT[pair] = pre.pairPathLen[pair];
                                if (steal[pair] != null && steal[pair][c]) k.onSteal = true;
                                if (bypass[pair] != null && bypass[pair][c]) k.onBypass = true;
                            } else {
                                k.inactivePairsAtT++;
                                if (completionAny[pair] != null && completionAny[pair][c]) {
                                    k.onCompletionAny = true;
                                    if (k.completionRouteLen < 0 || completionLen[pair] < k.completionRouteLen)
                                        k.completionRouteLen = completionLen[pair];
                                }
                                if (completionOwn[pair] != null && completionOwn[pair][c]) k.onCompletionOwn = true;
                                if (shortestCells[pair] != null && shortestCells[pair][c]) k.onShortestCells = true;
                            }
                        }
                        // Extension: adjacent to an own track that is already earning (grows an active own network).
                        for (int d = 0; d < 4 && !k.onExtend; d++) {
                            int nx = x + GameState.DX[d], ny = y + GameState.DY[d];
                            if (!pre.inBounds(nx, ny)) continue;
                            int nc = pre.cell(nx, ny);
                            if (pre.owner[nc] == p && onActivePath(pre, nc)) k.onExtend = true;
                        }
                        live.add(k);
                        all.add(k);
                    }
                }
                // Life update on the post-turn board (this turn's scoring already used it).
                int[] served = new int[s.size];
                for (int pair = 0; pair < s.pairCount; pair++) {
                    if (!s.pairActive(pair)) continue;
                    for (int j = 0; j < s.pairPathLen[pair]; j++) served[s.pairPath[pair][j]]++;
                }
                for (Track k : live) {
                    if (k.done) continue;
                    int o = s.owner[k.cell];
                    if (o == GameState.NEUTRAL && k.turn == t) { k.neutralised = true; k.done = true; k.lostTurn = t; continue; }
                    if (o != k.player) { k.lostTurn = t; k.done = true; continue; }
                    int n = served[k.cell];
                    if (n > 0) {
                        k.totalPoints += n;
                        k.maxPairs = Math.max(k.maxPairs, n);
                        if (k.firstEarnTurn < 0) {
                            k.firstEarnTurn = t;
                            k.pairsAtFirstEarn = n;
                            for (int pair = 0; pair < s.pairCount; pair++) {
                                if (!s.pairActive(pair) || !contains(s.pairPath[pair], s.pairPathLen[pair], k.cell)) continue;
                                k.firstEarnPathLen += s.pairPathLen[pair];
                                for (int j = 0; j < s.pairPathLen[pair]; j++) {
                                    int oo = s.owner[s.pairPath[pair][j]];
                                    if (oo == k.player) k.firstEarnOwnCells++; else if (oo == 1 - k.player) k.firstEarnOppCells++;
                                }
                                if (!k.pairActiveAtT[pair]) k.pairsInactiveAtT++;
                                else if (k.pairOppMajorityAtT[pair]) k.pairsStolen++;
                                else if (k.pairUnstableAtT[pair]) k.pairsBypassed++;
                                else k.pairsOther++;
                            }
                        }
                    }
                }
                live.removeIf(k -> k.done);
            }
            int end = r.turns();
            for (Track k : all) {
                int lost = k.lostTurn;
                int lifetime = (lost < 0 ? end : lost) - k.turn;
                w.write(r.gameId + "," + end + "," + k.turn + "," + me + "," + k.player + "," + k.cell + "," + s.x(k.cell) + "," + s.y(k.cell) + ","
                        + k.region + "," + k.cost + "," + b(k.townRegion) + "," + k.instab + "," + k.oppInRegion + "," + k.ownInRegion + ","
                        + b(k.adjOwn) + "," + b(k.adjOpp) + "," + b(k.adjTown) + "," + b(k.adjOwn || k.adjOpp || k.adjTown) + ","
                        + b(k.onCompletionAny) + "," + b(k.onCompletionOwn) + "," + b(k.onShortestCells) + "," + b(k.onSteal) + ","
                        + b(k.onBypass) + "," + b(k.onExtend) + ","
                        + k.slackAny + "," + k.slackOwn + "," + k.slackSafe + "," + k.stealSlack + "," + k.bypassSlack + "," + k.slackAnyPairs0 + ","
                        + k.completionRouteLen + "," + k.activePairsAtT + "," + k.inactivePairsAtT + ","
                        + k.firstEarnTurn + "," + (k.firstEarnTurn < 0 ? -1 : k.firstEarnTurn - k.turn) + "," + k.pairsAtFirstEarn + ","
                        + k.pairsInactiveAtT + "," + k.pairsStolen + "," + k.pairsBypassed + "," + k.pairsOther + ","
                        + k.firstEarnPathLen + "," + k.firstEarnOwnCells + "," + k.firstEarnOppCells + ","
                        + k.maxPairs + "," + k.totalPoints + "," + lost + "," + lifetime + "," + b(k.neutralised) + "\n");
            }
        }

        static String b(boolean v) { return v ? "1" : "0"; }

        static boolean contains(int[] a, int n, int v) {
            for (int i = 0; i < n; i++) if (a[i] == v) return true;
            return false;
        }

        static boolean onActivePath(GameState s, int cell) {
            for (int p = 0; p < s.pairCount; p++) {
                if (!s.pairActive(p)) continue;
                if (contains(s.pairPath[p], s.pairPathLen[p], cell)) return true;
            }
            return false;
        }

        /**
         * Route sets on the pre-turn board for player p:
         * completionAny/Own = paint-cheapest completion of each inactive pair (shared / avoiding enemy rails);
         * shortestCells = fewest-cells completion of each inactive pair over own rails, towns and free cells;
         * steal = for an active pair, an own-only path (own rails, towns, free cells) not longer than the
         *         current connection, i.e. one that would take over the connection when built;
         * bypass = for an active pair whose connection crosses an unstable non-town region, the shortest
         *          path over any rails, towns and free cells that avoids every unstable region.
         */
        void computeRoutes(GameState pre, int p, boolean[][] completionAny, boolean[][] completionOwn, boolean[][] shortestCells,
                           boolean[][] steal, boolean[][] bypass, int[] completionLen) {
            int opp = 1 - p;
            for (int c = 0; c < pre.size; c++) blocked[c] = pre.owner[c] == opp || pre.owner[c] == GameState.NEUTRAL;
            for (int pair = 0; pair < pre.pairCount; pair++) {
                int from = pre.townCell[pre.pairFrom[pair]], to = pre.townCell[pre.pairTo[pair]];
                if (!pre.pairActive(pair)) {
                    int n = pathing.cheapestPath(pre, from, to, path);
                    if (n > 0) { completionAny[pair] = freeCells(pre, path, n); completionLen[pair] = n; }
                    n = pathing.cheapestPath(pre, from, to, path, blocked);
                    if (n > 0) completionOwn[pair] = freeCells(pre, path, n);
                    for (int c = 0; c < pre.size; c++) allow[c] = !pre.inked[c] && (pre.isTown(c) || pre.owner[c] == p || pre.canBuild(c));
                    n = bfs(pre, from, to, allow, path);
                    if (n > 0) shortestCells[pair] = freeCells(pre, path, n);
                    continue;
                }
                int curLen = pre.pairPathLen[pair];
                for (int c = 0; c < pre.size; c++) allow[c] = !pre.inked[c] && (pre.isTown(c) || pre.owner[c] == p || pre.canBuild(c));
                int n = bfs(pre, from, to, allow, path);
                if (n > 0 && n <= curLen) {
                    boolean[] cells = freeCells(pre, path, n);
                    if (cells != null) steal[pair] = cells;
                }
                boolean unstable = false;
                for (int j = 0; j < curLen; j++) {
                    int rg = pre.regionId[pre.pairPath[pair][j]];
                    if (pre.regionInstability[rg] >= 1 && !pre.regionHasTown(rg)) { unstable = true; break; }
                }
                if (unstable) {
                    for (int c = 0; c < pre.size; c++) {
                        int rg = pre.regionId[c];
                        boolean unstableRegion = pre.regionInstability[rg] >= 1 && !pre.regionHasTown(rg);
                        allow[c] = !pre.inked[c] && !unstableRegion && (pre.isTown(c) || pre.owner[c] != GameState.NO_TRACK || pre.canBuild(c));
                    }
                    n = bfs(pre, from, to, allow, path);
                    if (n > 0) bypass[pair] = freeCells(pre, path, n);
                }
            }
        }

        /**
         * For every free cell, the excess of the best route THROUGH the cell over the optimal route, minimised
         * over pairs: paint excess for completing an inactive pair over any rails (slackAny), over own rails only
         * (slackOwn) and avoiding unstable non-town regions (slackSafe); cell-count excess of an own-only path
         * over the current connection of an active pair (stealSlack; <= 0 means the path would take over), and
         * cell-count excess over the shortest unstable-region-avoiding path for active pairs crossing an unstable
         * region (bypassSlack). slackAnyPairs0 counts inactive pairs for which the cell is on some optimal route.
         * Unlike a single tie-broken path, this recognises every optimal and near-optimal alternative.
         */
        void computeSlack(GameState pre, int p, int[] slackAny, int[] slackOwn, int[] slackSafe,
                          int[] stealSlack, int[] bypassSlack, int[] slackAnyPairs0) {
            int opp = 1 - p;
            Arrays.fill(slackAny, -1); Arrays.fill(slackOwn, -1); Arrays.fill(slackSafe, -1);
            Arrays.fill(stealSlack, 99); Arrays.fill(bypassSlack, 99); Arrays.fill(slackAnyPairs0, 0);
            int[] dFrom = new int[pre.size], dTo = new int[pre.size];
            boolean[] pass = new boolean[pre.size];
            for (int pair = 0; pair < pre.pairCount; pair++) {
                int from = pre.townCell[pre.pairFrom[pair]], to = pre.townCell[pre.pairTo[pair]];
                if (!pre.pairActive(pair)) {
                    for (int variant = 0; variant < 3; variant++) {
                        for (int c = 0; c < pre.size; c++) {
                            boolean ok = !pre.inked[c];
                            if (variant == 1 && (pre.owner[c] == opp || pre.owner[c] == GameState.NEUTRAL)) ok = false;
                            if (variant == 2 && pre.regionInstability[pre.regionId[c]] >= 1 && !pre.regionHasTown(pre.regionId[c]) && !pre.isTown(c)) ok = false;
                            pass[c] = ok;
                        }
                        dijkstra(pre, from, pass, dFrom);
                        dijkstra(pre, to, pass, dTo);
                        int opt = dFrom[to];
                        if (opt >= INF) continue;
                        int[] target = variant == 0 ? slackAny : variant == 1 ? slackOwn : slackSafe;
                        for (int c = 0; c < pre.size; c++) {
                            if (!pre.canBuild(c) || !pass[c] || dFrom[c] >= INF || dTo[c] >= INF) continue;
                            int through = dFrom[c] + dTo[c] - pre.placeCost(c) - opt;
                            if (target[c] < 0 || through < target[c]) target[c] = through;
                            if (variant == 0 && through == 0) slackAnyPairs0[c]++;
                        }
                    }
                    continue;
                }
                int curLen = pre.pairPathLen[pair];
                for (int c = 0; c < pre.size; c++) pass[c] = !pre.inked[c] && (pre.isTown(c) || pre.owner[c] == p || pre.canBuild(c));
                if (bfsAll(pre, from, pass, dFrom) && bfsAll(pre, to, pass, dTo) && dFrom[to] < INF) {
                    for (int c = 0; c < pre.size; c++) {
                        if (!pre.canBuild(c) || dFrom[c] >= INF || dTo[c] >= INF) continue;
                        int through = dFrom[c] + dTo[c] + 1 - curLen; // cells incl. both towns
                        if (through < stealSlack[c]) stealSlack[c] = through;
                    }
                }
                boolean unstable = false;
                for (int j = 0; j < curLen; j++) {
                    int rg = pre.regionId[pre.pairPath[pair][j]];
                    if (pre.regionInstability[rg] >= 1 && !pre.regionHasTown(rg)) { unstable = true; break; }
                }
                if (!unstable) continue;
                for (int c = 0; c < pre.size; c++) {
                    int rg = pre.regionId[c];
                    boolean bad = pre.regionInstability[rg] >= 1 && !pre.regionHasTown(rg) && !pre.isTown(c);
                    pass[c] = !pre.inked[c] && !bad && (pre.isTown(c) || pre.owner[c] != GameState.NO_TRACK || pre.canBuild(c));
                }
                if (bfsAll(pre, from, pass, dFrom) && bfsAll(pre, to, pass, dTo) && dFrom[to] < INF) {
                    int opt = dFrom[to] + 1;
                    for (int c = 0; c < pre.size; c++) {
                        if (!pre.canBuild(c) || !pass[c] || dFrom[c] >= INF || dTo[c] >= INF) continue;
                        int through = dFrom[c] + dTo[c] + 1 - opt;
                        if (through < bypassSlack[c]) bypassSlack[c] = through;
                    }
                }
            }
        }

        static final int INF = Integer.MAX_VALUE / 4;

        /** Paint-cost distance to every cell (entering a free cell costs its terrain, rails/towns cost 0). */
        boolean dijkstra(GameState pre, int src, boolean[] pass, int[] d) {
            Arrays.fill(d, INF);
            d[src] = 0;
            java.util.PriorityQueue<Long> pq = new java.util.PriorityQueue<>();
            pq.add((long) src);
            while (!pq.isEmpty()) {
                long e = pq.poll();
                int dist = (int) (e >>> 20), c = (int) (e & 0xFFFFF);
                if (dist > d[c]) continue;
                int cx = pre.x(c), cy = pre.y(c);
                for (int dir = 0; dir < 4; dir++) {
                    int nx = cx + GameState.DX[dir], ny = cy + GameState.DY[dir];
                    if (!pre.inBounds(nx, ny)) continue;
                    int nc = pre.cell(nx, ny);
                    if (!pass[nc]) continue;
                    int step = pre.passable(nc) ? 0 : pre.placeCost(nc);
                    if (dist + step < d[nc]) {
                        d[nc] = dist + step;
                        pq.add(((long) d[nc] << 20) | nc);
                    }
                }
            }
            return true;
        }

        /** Cell-count distance (number of steps) to every cell over allowed cells. */
        boolean bfsAll(GameState pre, int src, boolean[] pass, int[] d) {
            Arrays.fill(d, INF);
            int head = 0, tail = 0;
            d[src] = 0;
            bfsQueue[tail++] = src;
            while (head < tail) {
                int c = bfsQueue[head++];
                int cx = pre.x(c), cy = pre.y(c);
                for (int dir = 0; dir < 4; dir++) {
                    int nx = cx + GameState.DX[dir], ny = cy + GameState.DY[dir];
                    if (!pre.inBounds(nx, ny)) continue;
                    int nc = pre.cell(nx, ny);
                    if (d[nc] < INF || !pass[nc]) continue;
                    d[nc] = d[c] + 1;
                    bfsQueue[tail++] = nc;
                }
            }
            return true;
        }

        /** Marks the still-free cells of a path; null when the path needs no construction. */
        boolean[] freeCells(GameState pre, int[] cells, int n) {
            boolean[] on = null;
            for (int i = 0; i < n; i++) {
                if (!pre.canBuild(cells[i])) continue;
                if (on == null) on = new boolean[pre.size];
                on[cells[i]] = true;
            }
            return on;
        }

        /** Plain BFS (fewest cells) over allowed cells; returns the path length incl. endpoints or -1. */
        int bfs(GameState pre, int from, int to, boolean[] allow, int[] out) {
            Arrays.fill(bfsParent, -2);
            int head = 0, tail = 0;
            bfsParent[from] = -1;
            bfsQueue[tail++] = from;
            while (head < tail) {
                int c = bfsQueue[head++];
                if (c == to) break;
                int cx = pre.x(c), cy = pre.y(c);
                for (int d = 0; d < 4; d++) {
                    int nx = cx + GameState.DX[d], ny = cy + GameState.DY[d];
                    if (!pre.inBounds(nx, ny)) continue;
                    int nc = pre.cell(nx, ny);
                    if (bfsParent[nc] != -2 || !allow[nc]) continue;
                    bfsParent[nc] = c;
                    bfsQueue[tail++] = nc;
                }
            }
            if (bfsParent[to] == -2) return -1;
            int n = 0;
            for (int c = to; c != -1; c = bfsParent[c]) out[n++] = c;
            return n;
        }
    }
}
