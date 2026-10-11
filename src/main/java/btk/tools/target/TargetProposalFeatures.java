package btk.tools.target;

import btk.core.FeaturePaths;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.core.SearchState;
import java.util.Arrays;

/** Offline target-only extraction of the frozen V3 sabotage inputs. No building or rule implementation.
 * A complete pass preserves the frozen feature values and candidate order. Incomplete passes must be
 * discarded. Path queries use core FeaturePaths with a fresh search and the original cost/cell tie order.
 * Public-board history must be observed every turn; record only the target actually emitted by the caller.
 * One instance belongs to one map and player. No hot-path allocations after construction. */
public final class TargetProposalFeatures {
    public interface Deadline { boolean expired(); }
    public static final int SABOTAGE_F = 29;
    public static final String[] SABOTAGE_NAMES = {
            "instab", "ready", "oppCells", "ownCells", "neutralCells", "oppActive", "ownActive",
            "dOpp", "dOwn", "oppPlacedPrev", "oppPlacedPrev2", "ownPlacedPrev",
            "routesAny", "minCostAny", "routesOppOwn", "oppTraffic", "routesMeOwn",
            "freeShare", "regionSize", "adjTownRegion", "hitPrevByMe", "hitPrevByOpp", "closes",
            "turn", "scoreDiff", "incomeMe", "incomeOpp", "turnsLeft", "inactivePairs"};

    private final Pathing paths;
    private final FeaturePaths featurePaths;
    private final SearchState scratch;
    private final GameState sim;
    private final int size, regionCount;
    private final boolean[] regionTown, regionAdjTown, onActive, regionSeen, blocked;
    private final int[] placedTurn, prevOwner, prevInstab, lastHitMe, lastHitOpp;
    private int observedTurn = -1, myLastTarget = -1;
    private final int[] rOwn, rOpp, rNeutral, rOwnActive, rOppActive, rFree, rSize;
    private final int[] rRoutesAny, rMinCostAny, rRoutesOppOwn, rRoutesMeOwn, rOppPrev, rOppPrev2, rOwnPrev;
    private final double[] rOppTraffic;
    private final int[] path, path0, income = new int[2], income2 = new int[2];
    private int inactivePairs;
    public final boolean[] closes, winsIfClosed;
    public boolean prepared, candidatesComplete;

    public TargetProposalFeatures(GameState s) {
        size = s.size; regionCount = s.regionCount;
        paths = new Pathing(size); featurePaths = new FeaturePaths(s.width, s.height);
        scratch = new SearchState(s); sim = scratch.state;
        regionTown = new boolean[regionCount]; regionAdjTown = new boolean[regionCount];
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
        placedTurn = new int[size]; prevOwner = new int[size]; prevInstab = new int[regionCount];
        lastHitMe = new int[regionCount]; lastHitOpp = new int[regionCount];
        Arrays.fill(placedTurn, -99); Arrays.fill(prevOwner, GameState.NO_TRACK);
        Arrays.fill(lastHitMe, -99); Arrays.fill(lastHitOpp, -99);
        rOwn = new int[regionCount]; rOpp = new int[regionCount]; rNeutral = new int[regionCount];
        rOwnActive = new int[regionCount]; rOppActive = new int[regionCount]; rFree = new int[regionCount];
        rSize = new int[regionCount]; rRoutesAny = new int[regionCount]; rMinCostAny = new int[regionCount];
        rRoutesOppOwn = new int[regionCount]; rRoutesMeOwn = new int[regionCount];
        rOppPrev = new int[regionCount]; rOppPrev2 = new int[regionCount]; rOwnPrev = new int[regionCount];
        rOppTraffic = new double[regionCount]; onActive = new boolean[size];
        regionSeen = new boolean[regionCount]; blocked = new boolean[size];
        closes = new boolean[regionCount]; winsIfClosed = new boolean[regionCount];
        path = new int[size]; path0 = new int[size];
        for (int c = 0; c < size; c++) rSize[s.regionId[c]]++;
    }

    private static boolean expired(Deadline deadline) { return deadline != null && deadline.expired(); }

    /** Idempotent in a turn, including after a failed proposal. Does not infer unobserved own intentions. */
    public void observe(GameState s, int me) {
        if (s.turn == observedTurn) return;
        if (observedTurn >= 0) {
            for (int c = 0; c < size; c++)
                if (s.owner[c] != GameState.NO_TRACK && prevOwner[c] == GameState.NO_TRACK)
                    placedTurn[c] = s.turn - 1;
            for (int r = 0; r < regionCount; r++) {
                int rise = s.regionInstability[r] - prevInstab[r];
                if (rise <= 0) continue;
                if (r == myLastTarget) { lastHitMe[r] = s.turn - 1; rise--; }
                if (rise > 0) lastHitOpp[r] = s.turn - 1;
            }
        }
        System.arraycopy(s.owner, 0, prevOwner, 0, size);
        System.arraycopy(s.regionInstability, 0, prevInstab, 0, regionCount);
        observedTurn = s.turn; myLastTarget = -1;
        prepared = false; candidatesComplete = false;
    }

    /** Record the caller's actual emitted disruption, including a retained non-neural fallback. */
    public void recordTarget(int region) { myLastTarget = region; }

    /** Full pre-turn preparation only. False invalidates all scratch outputs; a fresh call can recover. */
    public boolean prepare(GameState s, int me, Deadline deadline) {
        prepared = false; candidatesComplete = false;
        if (expired(deadline)) return false;
        int opp = 1 - me;
        Arrays.fill(rOwn, 0); Arrays.fill(rOpp, 0); Arrays.fill(rNeutral, 0);
        Arrays.fill(rOwnActive, 0); Arrays.fill(rOppActive, 0); Arrays.fill(rFree, 0);
        Arrays.fill(rRoutesAny, 0); Arrays.fill(rMinCostAny, Integer.MAX_VALUE);
        Arrays.fill(rRoutesMeOwn, 0); Arrays.fill(rRoutesOppOwn, 0); Arrays.fill(rOppTraffic, 0);
        Arrays.fill(rOppPrev, 0); Arrays.fill(rOppPrev2, 0); Arrays.fill(rOwnPrev, 0);
        Arrays.fill(onActive, false);
        for (int p = 0; p < s.pairCount; p++) if (s.pairActive(p))
            for (int i = 0; i < s.pairPathLen[p]; i++) onActive[s.pairPath[p][i]] = true;
        for (int c = 0; c < size; c++) {
            int r = s.regionId[c], owner = s.owner[c];
            if (s.canBuild(c)) rFree[r]++;
            if (owner == me) {
                rOwn[r]++; if (onActive[c]) rOwnActive[r]++;
                if (placedTurn[c] == s.turn - 1) rOwnPrev[r]++;
            } else if (owner == opp) {
                rOpp[r]++; if (onActive[c]) rOppActive[r]++;
                if (placedTurn[c] == s.turn - 1) rOppPrev[r]++;
                if (placedTurn[c] >= s.turn - 2 && placedTurn[c] <= s.turn - 1) rOppPrev2[r]++;
            } else if (owner == GameState.NEUTRAL) rNeutral[r]++;
        }
        if (expired(deadline)) return false;
        Rules.incomePerTurn(s, income);
        inactivePairs = 0;
        for (int p = 0; p < s.pairCount; p++) {
            if (expired(deadline)) return false;
            if (s.pairActive(p)) continue;
            inactivePairs++;
            int from = s.townCell[s.pairFrom[p]], to = s.townCell[s.pairTo[p]];
            // No duplicate connectivity implementation: the core query also handles disconnected pairs.
            int n0 = route(s, from, to, path, null);
            if (expired(deadline)) return false;
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
            Arrays.fill(regionSeen, false);
            for (int i = 0; i < n0; i++) {
                int r = s.regionId[path0[i]];
                if (!regionSeen[r] && !regionTown[r]) {
                    regionSeen[r] = true; rRoutesAny[r]++;
                    rMinCostAny[r] = Math.min(rMinCostAny[r], cost0);
                }
            }
            if (!anyHasOpp) markOwn(s, path0, n0);
            else {
                for (int c = 0; c < size; c++) blocked[c] = s.owner[c] == opp;
                if (expired(deadline)) return false;
                int n1 = route(s, from, to, path, blocked);
                if (expired(deadline)) return false;
                if (n1 > 0 && buildCost(s, path, n1) > 0) markOwn(s, path, n1);
            }
            if (!anyHasOwn) markOpp(s, path0, n0, cost0);
            else {
                for (int c = 0; c < size; c++) blocked[c] = s.owner[c] == me;
                if (expired(deadline)) return false;
                int n2 = route(s, from, to, path, blocked);
                if (expired(deadline)) return false;
                int cost2 = n2 > 0 ? buildCost(s, path, n2) : 0;
                if (cost2 > 0) markOpp(s, path, n2, cost2);
            }
        }
        if (expired(deadline)) return false;
        prepared = true;
        return true;
    }

    private int route(GameState s, int from, int to, int[] out, boolean[] block) {
        featurePaths.reset();
        return featurePaths.path(s, from, to, out, block);
    }

    private static int buildCost(GameState s, int[] route, int n) {
        int cost = 0;
        for (int i = 0; i < n; i++) if (s.canBuild(route[i])) cost += s.placeCost(route[i]);
        return cost;
    }

    private void markOwn(GameState s, int[] route, int n) {
        Arrays.fill(regionSeen, false);
        for (int i = 0; i < n; i++) {
            int r = s.regionId[route[i]];
            if (!regionSeen[r] && !regionTown[r]) { regionSeen[r] = true; rRoutesMeOwn[r]++; }
        }
    }

    private void markOpp(GameState s, int[] route, int n, int cost) {
        Arrays.fill(regionSeen, false);
        for (int i = 0; i < n; i++) {
            int r = s.regionId[route[i]];
            if (!regionSeen[r] && !regionTown[r]) {
                regionSeen[r] = true; rRoutesOppOwn[r]++; rOppTraffic[r] += 1.0 / cost;
            }
        }
    }

    /** Ascending legal-region rows; -1 means the whole pass must be discarded (including closing flags). */
    public int candidates(GameState s, int me, int[] regions, float[][] rows, Deadline deadline) {
        candidatesComplete = false;
        if (expired(deadline)) return -1;
        if (!prepared) throw new IllegalStateException("Complete preparation required");
        int opp = 1 - me, n = 0, turnsLeft = s.turnsLeft();
        for (int r = 0; r < regionCount; r++) {
            if (expired(deadline)) return -1;
            closes[r] = false; winsIfClosed[r] = false;
            if (s.regionInked[r] || regionTown[r]) continue;
            int needed = GameState.INK_THRESHOLD - s.regionInstability[r], dOpp = 0, dOwn = 0;
            if (rOwn[r] + rOpp[r] + rNeutral[r] > 0 || needed == 1) {
                scratch.set(s); Rules.ink(sim, r);
                if (expired(deadline)) return -1;
                scratch.refresh();
                if (expired(deadline)) return -1;
                Rules.incomePerTurn(sim, income2);
                dOpp = income[opp] - income2[opp]; dOwn = income[me] - income2[me];
                if (expired(deadline)) return -1;
                if (needed == 1 && !Rules.anyPairPossible(sim, paths)) {
                    closes[r] = true;
                    winsIfClosed[r] = s.score[me] + income2[me] > s.score[opp] + income2[opp];
                }
                if (expired(deadline)) return -1;
            }
            float[] f = rows[n];
            int i = 0;
            f[i++] = s.regionInstability[r]; f[i++] = needed == 1 ? 1 : 0;
            f[i++] = rOpp[r]; f[i++] = rOwn[r]; f[i++] = rNeutral[r];
            f[i++] = rOppActive[r]; f[i++] = rOwnActive[r]; f[i++] = dOpp; f[i++] = dOwn;
            f[i++] = rOppPrev[r]; f[i++] = rOppPrev2[r]; f[i++] = rOwnPrev[r];
            f[i++] = rRoutesAny[r]; f[i++] = rMinCostAny[r] == Integer.MAX_VALUE ? 0 : rMinCostAny[r];
            f[i++] = rRoutesOppOwn[r]; f[i++] = (float) rOppTraffic[r]; f[i++] = rRoutesMeOwn[r];
            f[i++] = (float) rFree[r] / rSize[r]; f[i++] = rSize[r]; f[i++] = regionAdjTown[r] ? 1 : 0;
            f[i++] = lastHitMe[r] == s.turn - 1 ? 1 : 0; f[i++] = lastHitOpp[r] == s.turn - 1 ? 1 : 0;
            f[i++] = closes[r] ? 1 : 0; f[i++] = s.turn; f[i++] = s.score[me] - s.score[opp];
            f[i++] = income[me]; f[i++] = income[opp]; f[i++] = turnsLeft; f[i++] = inactivePairs;
            regions[n++] = r;
        }
        if (expired(deadline)) return -1;
        candidatesComplete = true;
        return n;
    }
}
