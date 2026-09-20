package btk.bot.neural;

import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;

import java.util.Arrays;

/**
 * Engine-grounded candidate features shared by the training exporter ({@code CgNeuralExport})
 * and the shipped {@link NeuralBot}. Both sides must call the same methods in the same order, so
 * every feature is defined exactly once here: exact income deltas, cheapest completion routes,
 * bypass and take-over routes all come from {@link Rules}/{@link Pathing} on scratch states.
 *
 * <p>Per-game memory (which cells appeared when, who hit which region) is reconstructed from the
 * observed public board only, so the replay export and the live bot see identical inputs.
 *
 * <p>Not thread-safe; one instance per bot. Hot path is allocation-free after {@code init}.
 */
public final class NeuralFeatures {

    public static final String[] SABOTAGE_NAMES = {
            "instab", "ready", "oppCells", "ownCells", "neutralCells", "oppActive", "ownActive",
            "dOpp", "dOwn", "oppPlacedPrev", "oppPlacedPrev2", "ownPlacedPrev",
            "routesAny", "minCostAny", "routesOppOwn", "oppTraffic", "routesMeOwn",
            "freeShare", "regionSize", "adjTownRegion", "hitPrevByMe", "hitPrevByOpp", "closes",
            "turn", "scoreDiff", "incomeMe", "incomeOpp", "turnsLeft", "inactivePairs"};
    public static final String[] BUILD_NAMES = {
            "cost", "usesAllPaint", "regionInstab", "regionTown", "isTarget", "regionOpp", "regionOwn",
            "regionOppActive", "regionFreeShare", "nOwn4", "nOpp4", "nNeutral4", "nTown4", "adjPassable",
            "own8", "routeAny", "routeOwn", "routeOppOwn", "minCostAny", "onNoRoute", "bestRatioAny",
            "bestGainOwn", "bypass", "bypassIncome", "takeover", "takeoverGain", "dIncMe", "dIncOpp",
            "completes", "adjOwnRecent", "adjOppActive", "townDist", "regionHitPrevByOpp", "regionOppPlacedPrev",
            "placementIndex", "paintLeft", "turn", "scoreDiff", "incomeMe", "incomeOpp", "inactivePairs",
            "adjPlacedThisTurn"};
    public static final int SABOTAGE_F = SABOTAGE_NAMES.length;
    public static final int BUILD_F = BUILD_NAMES.length;

    /** Regions at or above this instability are treated as threats when looking for bypass routes. */
    public static final int BYPASS_THREAT = 2;
    /** Candidate cells must lie within this Manhattan distance of an own track (or on a route / next to rails). */
    public static final int NEAR_OWN = 2;

    private final Pathing paths;
    private final GameState sim;
    private final int size, regionCount, pairCount, width, height;
    private final boolean[] regionTown, regionAdjTown;
    private final int[] townDist;

    // ---- memory (public board only) ----
    private final int[] placedTurn, prevOwner, prevInstab;
    private final int[] lastHitMe, lastHitOpp;
    private int observedTurn = -1, myLastTarget = -1;
    private final boolean[] placedThisTurn;

    // ---- per-turn region aggregates ----
    private final int[] rOwn, rOpp, rNeutral, rOwnActive, rOppActive, rFree, rSize;
    private final int[] rRoutesAny, rMinCostAny, rRoutesOppOwn, rRoutesMeOwn, rOppPrev, rOppPrev2, rOwnPrev;
    private final double[] rOppTraffic;
    private final boolean[] onActive, regionSeen;
    /** Filled by {@link #sabotageCandidates}: inking the region now would end the game / leave us ahead. */
    public final boolean[] closes, winsIfClosed;

    // ---- per-cell route info ----
    private final int[] cRouteAny, cRouteOwn, cRouteOppOwn, cMinCostAny, cBypass, cTakeover;
    private final double[] cBestRatio, cBestGainOwn, cBypassIncome, cTakeoverGain;
    private final int[] path, path0;
    private final boolean[] blocked, nearOwn;
    private final int[] queue, depth, component;
    // Feature-side Dijkstra scratch (see cheapestCostPath).
    private final int[] fCost, fParent, fHeap, fHeapPos;
    private static final int INF = Integer.MAX_VALUE / 4;
    private final int[] income = new int[2], income2 = new int[2];
    private int inactivePairs;
    /** Wall-clock accounting for the exporter (nanoseconds spent in prepare/candidates). */
    public long prepareNanos, sabotageNanos, buildNanos;

    public NeuralFeatures(GameState s) {
        size = s.size;
        width = s.width;
        height = s.height;
        regionCount = s.regionCount;
        pairCount = s.pairCount;
        paths = new Pathing(size);
        sim = s.copy();
        regionTown = new boolean[regionCount];
        regionAdjTown = new boolean[regionCount];
        for (int r = 0; r < regionCount; r++) regionTown[r] = s.regionHasTown(r);
        for (int c = 0; c < size; c++) {
            int r = s.regionId[c], x = s.x(c), y = s.y(c);
            for (int d = 0; d < 4; d++) {
                int nx = x + GameState.DX[d], ny = y + GameState.DY[d];
                if (!s.inBounds(nx, ny)) continue;
                int nr = s.regionId[s.cell(nx, ny)];
                if (nr != r && regionTown[nr]) regionAdjTown[r] = true;
            }
        }
        townDist = new int[size];
        for (int c = 0; c < size; c++) {
            int best = Integer.MAX_VALUE;
            for (int t = 0; t < s.townCount; t++)
                best = Math.min(best, Math.abs(s.x(c) - s.townX[t]) + Math.abs(s.y(c) - s.townY[t]));
            townDist[c] = best;
        }
        placedTurn = new int[size];
        prevOwner = new int[size];
        prevInstab = new int[regionCount];
        lastHitMe = new int[regionCount];
        lastHitOpp = new int[regionCount];
        placedThisTurn = new boolean[size];
        Arrays.fill(placedTurn, -99);
        Arrays.fill(prevOwner, GameState.NO_TRACK);
        Arrays.fill(lastHitMe, -99);
        Arrays.fill(lastHitOpp, -99);
        rOwn = new int[regionCount]; rOpp = new int[regionCount]; rNeutral = new int[regionCount];
        rOwnActive = new int[regionCount]; rOppActive = new int[regionCount]; rFree = new int[regionCount];
        rSize = new int[regionCount]; rRoutesAny = new int[regionCount]; rMinCostAny = new int[regionCount];
        rRoutesOppOwn = new int[regionCount]; rRoutesMeOwn = new int[regionCount];
        rOppPrev = new int[regionCount]; rOppPrev2 = new int[regionCount]; rOwnPrev = new int[regionCount];
        rOppTraffic = new double[regionCount];
        onActive = new boolean[size];
        regionSeen = new boolean[regionCount];
        closes = new boolean[regionCount];
        winsIfClosed = new boolean[regionCount];
        cRouteAny = new int[size]; cRouteOwn = new int[size]; cRouteOppOwn = new int[size];
        cMinCostAny = new int[size]; cBypass = new int[size]; cTakeover = new int[size];
        cBestRatio = new double[size]; cBestGainOwn = new double[size];
        cBypassIncome = new double[size]; cTakeoverGain = new double[size];
        path = new int[size];
        path0 = new int[size];
        blocked = new boolean[size];
        nearOwn = new boolean[size];
        queue = new int[size];
        depth = new int[size];
        component = new int[size];
        fCost = new int[size];
        fParent = new int[size];
        fHeap = new int[size];
        fHeapPos = new int[size];
        for (int c = 0; c < size; c++) rSize[s.regionId[c]]++;
    }

    // ------------------------------------------------------------------ memory

    /**
     * Observe the public board at the start of our turn: new tracks are dated, instability rises
     * are attributed to our own recorded target or otherwise to the opponent. Idempotent per turn.
     */
    public void observe(GameState s, int me) {
        if (s.turn == observedTurn) return;
        if (observedTurn >= 0) {
            for (int c = 0; c < size; c++) {
                if (s.owner[c] != GameState.NO_TRACK && prevOwner[c] == GameState.NO_TRACK) placedTurn[c] = s.turn - 1;
            }
            for (int r = 0; r < regionCount; r++) {
                int rise = s.regionInstability[r] - prevInstab[r];
                if (rise <= 0) continue;
                if (r == myLastTarget) {
                    lastHitMe[r] = s.turn - 1;
                    rise--;
                }
                if (rise > 0) lastHitOpp[r] = s.turn - 1;
            }
        }
        System.arraycopy(s.owner, 0, prevOwner, 0, size);
        System.arraycopy(s.regionInstability, 0, prevInstab, 0, regionCount);
        observedTurn = s.turn;
        myLastTarget = -1;
        Arrays.fill(placedThisTurn, false);
    }

    /** Our accepted DISRUPT of this turn (-1 for none); used to attribute next turn's instability rise. */
    public void recordTarget(int region) {
        myLastTarget = region;
    }

    /** A cell we placed earlier this turn (already applied to the work state). */
    public void markPlaced(int cell) {
        placedThisTurn[cell] = true;
    }

    // ------------------------------------------------------------------ per-turn preparation

    /**
     * Region aggregates, active-path membership and every route-derived quantity for the given
     * board. Call with {@code full} on the pre-turn state (sabotage + first placement) and without
     * it after every own placement: cheapest routes change once a placed cell costs nothing, but
     * the opponent's own-only completion routes (a sabotage-side quantity) are then reused from the
     * full call to save one Dijkstra per inactive pair.
     */
    public void prepare(GameState s, int me, boolean full) {
        long t0 = System.nanoTime();
        int opp = 1 - me;
        Arrays.fill(rOwn, 0); Arrays.fill(rOpp, 0); Arrays.fill(rNeutral, 0);
        Arrays.fill(rOwnActive, 0); Arrays.fill(rOppActive, 0); Arrays.fill(rFree, 0);
        Arrays.fill(rRoutesAny, 0); Arrays.fill(rMinCostAny, Integer.MAX_VALUE);
        Arrays.fill(rRoutesMeOwn, 0);
        Arrays.fill(rOppPrev, 0); Arrays.fill(rOppPrev2, 0); Arrays.fill(rOwnPrev, 0);
        if (full) {
            Arrays.fill(rRoutesOppOwn, 0);
            Arrays.fill(rOppTraffic, 0);
            Arrays.fill(cRouteOppOwn, 0);
        }
        Arrays.fill(onActive, false);
        Arrays.fill(cRouteAny, 0); Arrays.fill(cRouteOwn, 0);
        Arrays.fill(cMinCostAny, Integer.MAX_VALUE); Arrays.fill(cBypass, 0); Arrays.fill(cTakeover, 0);
        Arrays.fill(cBestRatio, 0); Arrays.fill(cBestGainOwn, 0);
        Arrays.fill(cBypassIncome, 0); Arrays.fill(cTakeoverGain, 0);
        for (int p = 0; p < pairCount; p++) {
            if (!s.pairActive(p)) continue;
            int[] pp = s.pairPath[p];
            for (int i = 0; i < s.pairPathLen[p]; i++) onActive[pp[i]] = true;
        }
        for (int c = 0; c < size; c++) {
            int r = s.regionId[c], o = s.owner[c];
            if (s.canBuild(c)) rFree[r]++;
            if (o == me) {
                rOwn[r]++;
                if (onActive[c]) rOwnActive[r]++;
                if (placedTurn[c] == s.turn - 1) rOwnPrev[r]++;
            } else if (o == opp) {
                rOpp[r]++;
                if (onActive[c]) rOppActive[r]++;
                if (placedTurn[c] == s.turn - 1) rOppPrev[r]++;
                if (placedTurn[c] >= s.turn - 2 && placedTurn[c] <= s.turn - 1) rOppPrev2[r]++;
            } else if (o == GameState.NEUTRAL) {
                rNeutral[r]++;
            }
        }
        Rules.incomePerTurn(s, income);
        // Cells within NEAR_OWN steps of an own track (candidate filter for filler placements).
        Arrays.fill(nearOwn, false);
        int head = 0, tail = 0;
        for (int c = 0; c < size; c++) if (s.owner[c] == me) { nearOwn[c] = true; queue[tail++] = c; }
        for (int c = 0; c < size; c++) depth[c] = nearOwn[c] ? 0 : -1;
        while (head < tail) {
            int c = queue[head++];
            if (depth[c] >= NEAR_OWN) continue;
            int x = c % width, y = c / width;
            for (int d = 0; d < 4; d++) {
                int nx = x + GameState.DX[d], ny = y + GameState.DY[d];
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                int nc = ny * width + nx;
                if (depth[nc] >= 0) continue;
                depth[nc] = depth[c] + 1;
                nearOwn[nc] = true;
                queue[tail++] = nc;
            }
        }

        labelComponents(s);
        inactivePairs = 0;
        for (int p = 0; p < pairCount; p++) {
            int from = s.townCell[s.pairFrom[p]], to = s.townCell[s.pairTo[p]];
            if (s.pairActive(p)) {
                activePairRoutes(s, me, p, from, to);
                continue;
            }
            inactivePairs++;
            if (component[from] != component[to]) continue; // separated by ink: no completion exists
            // Shared cheapest completion (any tracks free).
            int n0 = cheapestCostPath(s, from, to, path, null);
            if (n0 <= 0) continue;
            int cost0 = 0;
            boolean anyHasOpp = false, anyHasOwn = false;
            for (int i = 0; i < n0; i++) {
                int c = path[i];
                if (s.canBuild(c)) cost0 += s.placeCost(c);
                else if (s.owner[c] == opp) anyHasOpp = true;
                else if (s.owner[c] == me) anyHasOwn = true;
            }
            if (cost0 == 0) continue;
            System.arraycopy(path, 0, path0, 0, n0);
            double net0 = completionGain(s, me, opp, path0, n0);
            Arrays.fill(regionSeen, false);
            for (int i = 0; i < n0; i++) {
                int c = path0[i], r = s.regionId[c];
                if (s.canBuild(c)) {
                    cRouteAny[c]++;
                    cMinCostAny[c] = Math.min(cMinCostAny[c], cost0);
                    cBestRatio[c] = Math.max(cBestRatio[c], net0 / cost0);
                }
                if (!regionSeen[r] && !regionTown[r]) {
                    regionSeen[r] = true;
                    rRoutesAny[r]++;
                    rMinCostAny[r] = Math.min(rMinCostAny[r], cost0);
                }
            }
            // Our own-only completion (avoid enemy rails): identical to the shared one unless it used them.
            if (!anyHasOpp) {
                markOwnRoute(s, path0, n0, net0);
            } else {
                for (int c = 0; c < size; c++) blocked[c] = s.owner[c] == opp;
                int n1 = cheapestCostPath(s, from, to, path, blocked);
                if (n1 > 0 && buildCost(s, path, n1) > 0) markOwnRoute(s, path, n1, completionGain(s, me, opp, path, n1));
            }
            // The opponent's own-only completion (where it must still lay track); sabotage-side, full calls only.
            if (full) {
                if (!anyHasOwn) {
                    markOppRoute(s, path0, n0, cost0);
                } else {
                    for (int c = 0; c < size; c++) blocked[c] = s.owner[c] == me;
                    int n2 = cheapestCostPath(s, from, to, path, blocked);
                    int cost2 = n2 > 0 ? buildCost(s, path, n2) : 0;
                    if (cost2 > 0) markOppRoute(s, path, n2, cost2);
                }
            }
        }
        prepareNanos += System.nanoTime() - t0;
    }

    /** Connected components of non-inked cells (towns count as open), for skipping impossible pairs. */
    private void labelComponents(GameState s) {
        Arrays.fill(component, -1);
        int next = 0;
        for (int start = 0; start < size; start++) {
            if (component[start] >= 0 || s.inked[start]) continue;
            int head = 0, tail = 0;
            queue[tail++] = start;
            component[start] = next;
            while (head < tail) {
                int c = queue[head++];
                int x = c % width, y = c / width;
                for (int d = 0; d < 4; d++) {
                    int nx = x + GameState.DX[d], ny = y + GameState.DY[d];
                    if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                    int nc = ny * width + nx;
                    if (component[nc] >= 0 || s.inked[nc]) continue;
                    component[nc] = next;
                    queue[tail++] = nc;
                }
            }
            next++;
        }
    }

    /**
     * Feature-side cheapest path in paint points (tracks and towns cost 0, free cells their terrain
     * cost, inked and {@code blocked} cells impassable). Same optimal cost as the referee-exact
     * {@link Pathing#cheapestPath}, but a plain Dijkstra with (cost, cell) ordering: it does not
     * reproduce the referee's AutobuildAStar tie-break and is several times faster. Only used to
     * derive policy features, never to emit AUTOPLACE or to resolve rules.
     *
     * @return cells incl. both endpoints in {@code out}, or -1 when unreachable
     */
    public int cheapestCostPath(GameState s, int from, int to, int[] out, boolean[] block) {
        Arrays.fill(fCost, INF);
        Arrays.fill(fHeapPos, -1);
        int heapSize = 0;
        fCost[from] = 0;
        fParent[from] = -1;
        heapSize = fPush(heapSize, from);
        while (heapSize > 0) {
            int c = fHeap[0];
            heapSize = fPop(heapSize);
            if (c == to) break;
            int base = fCost[c];
            int x = c % width, y = c / width;
            for (int d = 0; d < 4; d++) {
                int nx = x + GameState.DX[d], ny = y + GameState.DY[d];
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                int nc = ny * width + nx;
                if (s.inked[nc] || (block != null && block[nc])) continue;
                int cost = base + (s.passable(nc) ? 0 : s.placeCost(nc));
                if (cost < fCost[nc]) {
                    fCost[nc] = cost;
                    fParent[nc] = c;
                    if (fHeapPos[nc] >= 0) fSiftUp(fHeapPos[nc]);
                    else heapSize = fPush(heapSize, nc);
                }
            }
        }
        if (fCost[to] >= INF) return -1;
        int n = 0;
        for (int c = to; c != -1; c = fParent[c]) out[n++] = c;
        for (int i = 0, j = n - 1; i < j; i++, j--) {
            int t = out[i];
            out[i] = out[j];
            out[j] = t;
        }
        return n;
    }

    /** Paint cost of the last {@link #cheapestCostPath} to {@code cell} (INF-ish when unreached). */
    public int lastCost(int cell) {
        return fCost[cell];
    }

    private boolean fLess(int a, int b) {
        return fCost[a] < fCost[b] || (fCost[a] == fCost[b] && a < b);
    }

    private int fPush(int n, int c) {
        fHeap[n] = c;
        fHeapPos[c] = n;
        fSiftUp(n);
        return n + 1;
    }

    private int fPop(int n) {
        fHeapPos[fHeap[0]] = -1;
        n--;
        if (n > 0) {
            fHeap[0] = fHeap[n];
            fHeapPos[fHeap[0]] = 0;
            fSiftDown(0, n);
        }
        return n;
    }

    private void fSiftUp(int i) {
        int c = fHeap[i];
        while (i > 0) {
            int p = (i - 1) >> 1;
            if (!fLess(c, fHeap[p])) break;
            fHeap[i] = fHeap[p];
            fHeapPos[fHeap[i]] = i;
            i = p;
        }
        fHeap[i] = c;
        fHeapPos[c] = i;
    }

    private void fSiftDown(int i, int n) {
        int c = fHeap[i];
        while (true) {
            int l = 2 * i + 1, r = l + 1, m = i;
            int mc = c;
            if (l < n && fLess(fHeap[l], mc)) { m = l; mc = fHeap[l]; }
            if (r < n && fLess(fHeap[r], mc)) { m = r; mc = fHeap[r]; }
            if (m == i) break;
            fHeap[i] = mc;
            fHeapPos[mc] = i;
            i = m;
        }
        fHeap[i] = c;
        fHeapPos[c] = i;
    }

    private static int buildCost(GameState s, int[] route, int n) {
        int cost = 0;
        for (int i = 0; i < n; i++) if (s.canBuild(route[i])) cost += s.placeCost(route[i]);
        return cost;
    }

    /** Exact net income change (ours minus theirs) once every free cell of the route is ours. */
    private double completionGain(GameState s, int me, int opp, int[] route, int n) {
        sim.copyFrom(s);
        for (int i = 0; i < n; i++) if (s.canBuild(route[i])) sim.owner[route[i]] = me;
        Rules.updateConnections(sim, paths);
        Rules.incomePerTurn(sim, income2);
        return (income2[me] - income[me]) - (income2[opp] - income[opp]);
    }

    private void markOwnRoute(GameState s, int[] route, int n, double net) {
        Arrays.fill(regionSeen, false);
        for (int i = 0; i < n; i++) {
            int c = route[i], r = s.regionId[c];
            if (s.canBuild(c)) {
                cRouteOwn[c]++;
                cBestGainOwn[c] = Math.max(cBestGainOwn[c], net);
            }
            if (!regionSeen[r] && !regionTown[r]) {
                regionSeen[r] = true;
                rRoutesMeOwn[r]++;
            }
        }
    }

    private void markOppRoute(GameState s, int[] route, int n, int cost) {
        Arrays.fill(regionSeen, false);
        for (int i = 0; i < n; i++) {
            int c = route[i], r = s.regionId[c];
            if (s.canBuild(c)) cRouteOppOwn[c]++;
            if (!regionSeen[r] && !regionTown[r]) {
                regionSeen[r] = true;
                rRoutesOppOwn[r]++;
                rOppTraffic[r] += 1.0 / cost;
            }
        }
    }

    /** Bypass around threatened regions and take-over of opponent-majority connections. */
    private void activePairRoutes(GameState s, int me, int p, int from, int to) {
        int opp = 1 - me;
        int[] pp = s.pairPath[p];
        int len = s.pairPathLen[p];
        int mine = 0, theirs = 0;
        boolean threatened = false;
        for (int i = 0; i < len; i++) {
            int c = pp[i];
            if (s.owner[c] == me) mine++;
            else if (s.owner[c] == opp) theirs++;
            if (s.regionInstability[s.regionId[c]] >= BYPASS_THREAT && !regionTown[s.regionId[c]]) threatened = true;
        }
        if (threatened && mine > 0) {
            for (int c = 0; c < size; c++) blocked[c] = s.regionInstability[s.regionId[c]] >= BYPASS_THREAT && !regionTown[s.regionId[c]];
            int n = cheapestCostPath(s, from, to, path, blocked);
            if (n > 0) {
                for (int i = 0; i < n; i++) {
                    int c = path[i];
                    if (!s.canBuild(c)) continue;
                    cBypass[c]++;
                    cBypassIncome[c] += mine;
                }
            }
        }
        if (theirs > mine) {
            for (int c = 0; c < size; c++) blocked[c] = s.owner[c] == opp;
            int n = cheapestCostPath(s, from, to, path, blocked);
            if (n > 0 && n <= len) {
                for (int i = 0; i < n; i++) {
                    int c = path[i];
                    if (!s.canBuild(c)) continue;
                    cTakeover[c]++;
                    cTakeoverGain[c] += theirs;
                }
            }
        }
    }

    // ------------------------------------------------------------------ sabotage candidates

    /**
     * Every legal DISRUPT target with its feature row. Requires a {@code full} {@link #prepare} on
     * the same board.
     *
     * @return number of candidates written to {@code regions}/{@code x}
     */
    public int sabotageCandidates(GameState s, int me, int[] regions, float[][] x) {
        long t0 = System.nanoTime();
        int opp = 1 - me;
        int n = 0;
        int turnsLeft = s.turnsLeft();
        for (int r = 0; r < regionCount; r++) {
            closes[r] = false;
            winsIfClosed[r] = false;
            if (s.regionInked[r] || regionTown[r]) continue;
            int needed = GameState.INK_THRESHOLD - s.regionInstability[r];
            int dOpp = 0, dOwn = 0;
            if (rOwn[r] + rOpp[r] + rNeutral[r] > 0 || needed == 1) {
                sim.copyFrom(s);
                Rules.ink(sim, r);
                Rules.updateConnections(sim, paths);
                Rules.incomePerTurn(sim, income2);
                dOpp = income[opp] - income2[opp];
                dOwn = income[me] - income2[me];
                if (needed == 1 && !Rules.anyPairPossible(sim, paths)) {
                    closes[r] = true;
                    winsIfClosed[r] = s.score[me] + income2[me] > s.score[opp] + income2[opp];
                }
            }
            float[] f = x[n];
            int i = 0;
            f[i++] = s.regionInstability[r];
            f[i++] = needed == 1 ? 1 : 0;
            f[i++] = rOpp[r];
            f[i++] = rOwn[r];
            f[i++] = rNeutral[r];
            f[i++] = rOppActive[r];
            f[i++] = rOwnActive[r];
            f[i++] = dOpp;
            f[i++] = dOwn;
            f[i++] = rOppPrev[r];
            f[i++] = rOppPrev2[r];
            f[i++] = rOwnPrev[r];
            f[i++] = rRoutesAny[r];
            f[i++] = rMinCostAny[r] == Integer.MAX_VALUE ? 0 : rMinCostAny[r];
            f[i++] = rRoutesOppOwn[r];
            f[i++] = (float) rOppTraffic[r];
            f[i++] = rRoutesMeOwn[r];
            f[i++] = (float) rFree[r] / rSize[r];
            f[i++] = rSize[r];
            f[i++] = regionAdjTown[r] ? 1 : 0;
            f[i++] = lastHitMe[r] == s.turn - 1 ? 1 : 0;
            f[i++] = lastHitOpp[r] == s.turn - 1 ? 1 : 0;
            f[i++] = closes[r] ? 1 : 0;
            f[i++] = s.turn;
            f[i++] = s.score[me] - s.score[opp];
            f[i++] = income[me];
            f[i++] = income[opp];
            f[i++] = turnsLeft;
            f[i++] = inactivePairs;
            regions[n++] = r;
        }
        sabotageNanos += System.nanoTime() - t0;
        return n;
    }

    // ------------------------------------------------------------------ placement candidates

    /**
     * Affordable placement candidates on the work board (own earlier placements of this turn
     * already applied) with their feature rows. Requires {@link #prepare} on the same board
     * ({@code full} for the first placement, incremental afterwards).
     *
     * @param paint  paint left this turn
     * @param target our DISRUPT region this turn, or -1
     * @param k      index of this placement within the turn (0, 1, 2)
     * @return number of candidates written to {@code cells}/{@code x}
     */
    public int buildCandidates(GameState s, int me, int paint, int target, int k, int[] cells, float[][] x) {
        long t0 = System.nanoTime();
        int opp = 1 - me;
        int n = 0;
        for (int c = 0; c < size; c++) {
            if (!s.canBuild(c)) continue;
            int cost = s.placeCost(c);
            if (cost > paint) continue;
            int cx = c % width, cy = c / width;
            int nOwn = 0, nOpp = 0, nNeutral = 0, nTown = 0, nOppActive = 0, nRecent = 0, nPlaced = 0;
            boolean adjPassable = false;
            for (int d = 0; d < 4; d++) {
                int nx = cx + GameState.DX[d], ny = cy + GameState.DY[d];
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                int nc = ny * width + nx;
                int o = s.owner[nc];
                if (s.isTown(nc)) { nTown++; adjPassable = true; }
                else if (o == me) { nOwn++; adjPassable = true; if (placedTurn[nc] >= s.turn - 2) nRecent++; if (placedThisTurn[nc]) nPlaced++; }
                else if (o == opp) { nOpp++; adjPassable = true; if (onActive[nc]) nOppActive++; }
                else if (o == GameState.NEUTRAL) { nNeutral++; adjPassable = true; }
            }
            boolean onRoute = cRouteAny[c] > 0 || cRouteOwn[c] > 0 || cBypass[c] > 0 || cTakeover[c] > 0;
            if (!adjPassable && !onRoute && !nearOwn[c]) continue;
            int own8 = nOwn;
            for (int dx = -1; dx <= 1; dx += 2) for (int dy = -1; dy <= 1; dy += 2) {
                int nx = cx + dx, ny = cy + dy;
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                if (s.owner[ny * width + nx] == me) own8++;
            }
            int dMe = 0, dOpp = 0, completes = 0;
            // A path can only run through a cell with at least two passable neighbours; otherwise the
            // placement cannot change any connection and the exact simulation would return zeros.
            if (nOwn + nOpp + nNeutral + nTown >= 2) {
                sim.copyFrom(s);
                sim.owner[c] = me;
                Rules.updateConnections(sim, paths);
                Rules.incomePerTurn(sim, income2);
                dMe = income2[me] - income[me];
                dOpp = income2[opp] - income[opp];
                for (int p = 0; p < pairCount; p++) if (!s.pairActive(p) && sim.pairActive(p)) completes++;
            }
            int r = s.regionId[c];
            float[] f = x[n];
            int i = 0;
            f[i++] = cost;
            f[i++] = cost == paint ? 1 : 0;
            f[i++] = s.regionInstability[r];
            f[i++] = regionTown[r] ? 1 : 0;
            f[i++] = r == target ? 1 : 0;
            f[i++] = rOpp[r];
            f[i++] = rOwn[r];
            f[i++] = rOppActive[r];
            f[i++] = (float) rFree[r] / rSize[r];
            f[i++] = nOwn;
            f[i++] = nOpp;
            f[i++] = nNeutral;
            f[i++] = nTown;
            f[i++] = adjPassable ? 1 : 0;
            f[i++] = own8;
            f[i++] = cRouteAny[c];
            f[i++] = cRouteOwn[c];
            f[i++] = cRouteOppOwn[c];
            f[i++] = cMinCostAny[c] == Integer.MAX_VALUE ? 0 : cMinCostAny[c];
            f[i++] = cRouteAny[c] == 0 && cRouteOwn[c] == 0 ? 1 : 0;
            f[i++] = (float) cBestRatio[c];
            f[i++] = (float) cBestGainOwn[c];
            f[i++] = cBypass[c];
            f[i++] = (float) cBypassIncome[c];
            f[i++] = cTakeover[c];
            f[i++] = (float) cTakeoverGain[c];
            f[i++] = dMe;
            f[i++] = dOpp;
            f[i++] = completes;
            f[i++] = nRecent > 0 ? 1 : 0;
            f[i++] = nOppActive;
            f[i++] = townDist[c];
            f[i++] = lastHitOpp[r] == s.turn - 1 ? 1 : 0;
            f[i++] = rOppPrev[r];
            f[i++] = k;
            f[i++] = paint;
            f[i++] = s.turn;
            f[i++] = s.score[me] - s.score[opp];
            f[i++] = income[me];
            f[i++] = income[opp];
            f[i++] = inactivePairs;
            f[i++] = nPlaced > 0 ? 1 : 0;
            cells[n++] = c;
        }
        buildNanos += System.nanoTime() - t0;
        return n;
    }

    public int inactivePairs() {
        return inactivePairs;
    }
}
