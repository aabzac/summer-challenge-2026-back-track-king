package btk.bot.v11;

import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.core.TacticalPathing;

import java.util.Arrays;

/**
 * Boss-style sabotage chooser fitted on the Silver boss's 855 canonical replays
 * (docs/boss-proxy-v1.md): a conditional logit over candidate-region features, a target held
 * until it inks unless another region beats it by a margin, winning one-hit closures taken and
 * losing ones refused. Reads only the public board through the core. Used by the
 * {@code USE_BOSS_SABOTAGE} experiment and as a boss-like opponent model.
 */
public final class BossSabotageV11 {
    // feature order: dOpp, dOwn, oppCells, oppActive, ownCells, oppPlacedPrev2, instab,
    //                oppRouteTraffic, routesAny, adjTownRegion, closes, neutralCells, log1p(freeCells)
    static final double[] W = {
        +0.137316, -0.111814, +0.775046, -0.003927, -0.282833, +0.291303, +0.884541,
        +0.371747, -0.001751, +0.368378, -3.437223, +0.155573, +0.148325
    };
    private final double holdMargin;
    private final Pathing paths;
    private final TacticalPathing tactical;
    private final GameState sim;
    private final int[] route, entry, placedTurn, prevOwner;
    private final int[] oppCells, ownCells, neutralCells, oppActive, oppPrev2, freeCells, routesAny;
    private final double[] oppTraffic;
    private final boolean[] adjTown, onActive, seen;
    private final int[] before = new int[2], after = new int[2];
    private int target = -1;

    public BossSabotageV11(GameState s, double holdMargin) {
        this.holdMargin = holdMargin;
        paths = new Pathing(s.size); tactical = new TacticalPathing(s.size); sim = s.copy();
        route = new int[s.size]; entry = new int[s.size]; placedTurn = new int[s.size]; prevOwner = new int[s.size];
        Arrays.fill(placedTurn, -99); Arrays.fill(prevOwner, GameState.NO_TRACK);
        oppCells = new int[s.regionCount]; ownCells = new int[s.regionCount]; neutralCells = new int[s.regionCount];
        oppActive = new int[s.regionCount]; oppPrev2 = new int[s.regionCount]; freeCells = new int[s.regionCount];
        routesAny = new int[s.regionCount]; oppTraffic = new double[s.regionCount];
        adjTown = new boolean[s.regionCount]; onActive = new boolean[s.size]; seen = new boolean[s.regionCount];
    }

    /** Current committed target, or -1. */
    public int target() { return target; }

    /** Record when each track appeared; call once per real turn before {@link #choose}. */
    public void observe(GameState s) {
        for (int c = 0; c < s.size; c++) {
            if (s.owner[c] != prevOwner[c] && s.owner[c] != GameState.NO_TRACK) placedTurn[c] = s.turn;
            prevOwner[c] = s.owner[c];
        }
    }

    /** Boss-style target for {@code me} on board {@code s}; updates the held target. */
    public int choose(GameState s, int me) {
        target = chooseInternal(s, me, target);
        return target;
    }

    /** Stateless variant for simulated opponents: {@code held} is the region committed to so far. */
    public int chooseHeld(GameState s, int me, int held) {
        return chooseInternal(s, me, held);
    }

    private int chooseInternal(GameState s, int me, int held) {
        int opp = 1 - me;
        if (held >= 0 && s.regionInked[held]) held = -1;
        Arrays.fill(oppCells, 0); Arrays.fill(ownCells, 0); Arrays.fill(neutralCells, 0);
        Arrays.fill(oppActive, 0); Arrays.fill(oppPrev2, 0); Arrays.fill(freeCells, 0);
        Arrays.fill(routesAny, 0); Arrays.fill(oppTraffic, 0); Arrays.fill(adjTown, false);
        Arrays.fill(onActive, false);
        for (int pair = 0; pair < s.pairCount; pair++) {
            if (!s.pairActive(pair)) continue;
            for (int i = 0; i < s.pairPathLen[pair]; i++) onActive[s.pairPath[pair][i]] = true;
        }
        for (int c = 0; c < s.size; c++) {
            int r = s.regionId[c];
            if (s.canBuild(c)) freeCells[r]++;
            int o = s.owner[c];
            if (o == opp) { oppCells[r]++; if (onActive[c]) oppActive[r]++; if (placedTurn[c] >= s.turn - 2) oppPrev2[r]++; }
            else if (o == me) ownCells[r]++;
            else if (o == GameState.NEUTRAL) neutralCells[r]++;
            int x = c % s.width, y = c / s.width;
            for (int d = 0; d < 4; d++) {
                int nx = x + GameState.DX[d], ny = y + GameState.DY[d];
                if (nx < 0 || ny < 0 || nx >= s.width || ny >= s.height) continue;
                int nr = s.regionId[ny * s.width + nx];
                if (nr != r && s.regionHasTown(nr)) adjTown[r] = true;
            }
        }
        for (int pair = 0; pair < s.pairCount; pair++) {
            if (s.pairActive(pair)) continue;
            int from = s.townCell[s.pairFrom[pair]], to = s.townCell[s.pairTo[pair]];
            int n = paths.cheapestPath(s, from, to, route);
            if (n > 0 && routeCost(s, route, n) > 0) mark(s, route, n, routesAny, null, 0);
            for (int c = 0; c < s.size; c++) {
                int r = s.regionId[c];
                if (s.inked[c]) entry[c] = TacticalPathing.INF;
                else if (s.isTown(c) || s.owner[c] == opp || s.owner[c] == GameState.NEUTRAL) entry[c] = 0;
                else if (s.owner[c] == me) entry[c] = TacticalPathing.INF;
                else if (!s.regionHasTown(r) && s.regionInstability[r] >= 2) entry[c] = TacticalPathing.INF;
                else entry[c] = s.placeCost(c);
            }
            n = tactical.weightedPath(s, from, to, entry, route);
            if (n > 0) { int cost = routeCost(s, route, n); if (cost > 0) mark(s, route, n, null, oppTraffic, 1.0 / cost); }
        }
        Rules.incomePerTurn(s, before);
        int best = -1; double bestScore = -Double.MAX_VALUE, heldScore = -Double.MAX_VALUE;
        for (int r = 0; r < s.regionCount; r++) {
            if (s.regionInked[r] || s.regionHasTown(r)) continue;
            boolean candidate = oppCells[r] > 0 || ownCells[r] > 0 || neutralCells[r] > 0 || s.regionInstability[r] > 0
                    || routesAny[r] > 0 || oppTraffic[r] > 0 || r == held;
            if (!candidate) continue;
            int needed = GameState.INK_THRESHOLD - s.regionInstability[r];
            int dOpp = 0, dOwn = 0;
            if (oppCells[r] + ownCells[r] + neutralCells[r] > 0 || needed == 1) {
                sim.copyFrom(s); Rules.ink(sim, r); Rules.updateConnections(sim, paths); Rules.incomePerTurn(sim, after);
                dOpp = before[opp] - after[opp]; dOwn = before[me] - after[me];
                if (needed == 1 && !Rules.anyPairPossible(sim, paths)) {
                    if (s.score[me] + after[me] > s.score[opp] + after[opp]) return r;
                    continue;
                }
            }
            double v = W[0] * dOpp + W[1] * dOwn + W[2] * oppCells[r] + W[3] * oppActive[r] + W[4] * ownCells[r]
                    + W[5] * oppPrev2[r] + W[6] * s.regionInstability[r] + W[7] * oppTraffic[r] + W[8] * routesAny[r]
                    + W[9] * (adjTown[r] ? 1 : 0) + W[11] * neutralCells[r] + W[12] * Math.log1p(freeCells[r]);
            if (r == held) heldScore = v;
            if (v > bestScore) { bestScore = v; best = r; }
        }
        if (best < 0) return -1;
        if (held >= 0 && heldScore > -Double.MAX_VALUE && bestScore < heldScore + holdMargin) return held;
        if (oppCells[best] == 0 && s.regionInstability[best] == 0 && oppTraffic[best] == 0 && routesAny[best] == 0) return -1;
        return best;
    }

    private static int routeCost(GameState s, int[] cells, int n) {
        int cost = 0;
        for (int i = 0; i < n; i++) if (s.canBuild(cells[i])) cost += s.placeCost(cells[i]);
        return cost;
    }

    private void mark(GameState s, int[] cells, int n, int[] counts, double[] traffic, double weight) {
        Arrays.fill(seen, false);
        for (int i = 0; i < n; i++) {
            int r = s.regionId[cells[i]];
            if (seen[r] || s.regionHasTown(r)) continue;
            seen[r] = true;
            if (counts != null) counts[r]++;
            if (traffic != null) traffic[r] += weight;
        }
    }
}
