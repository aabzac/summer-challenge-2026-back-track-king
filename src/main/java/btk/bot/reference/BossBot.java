package btk.bot.reference;

import btk.bot.Bot;
import btk.bot.Params;
import btk.bot.Timer;
import btk.core.Action;
import btk.core.GameState;
import btk.core.NetworkCells;
import btk.core.Pathing;
import btk.core.Rules;
import btk.core.TacticalPathing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Local-only {@code boss-v1} reference: an imitation of the Silver arena boss ("Boss 4") fitted on
 * its canonical replays against our own bots (docs/boss-proxy-v1.md). It is a screening opponent
 * that reproduces the boss meta the self-play mirror does not: a DISRUPT on almost every turn,
 * a target held until the region inks, own-only construction that never enters a region at
 * instability two or more, full paint whenever a profitable completion exists, single-cell steals
 * of active connections, and idling once nothing profitable remains.
 *
 * <p>Constants are frozen from the fit; only {@code NOISE}/{@code SEED} come from {@link Params}.
 * The valuation reads only the public board through {@link Rules}; it never reads the opponent's
 * command. Not a submission and not a copy of any strategy class.
 */
public final class BossBot implements Bot {
    // ---- sabotage: conditional-logit weights on candidate-region features (fit, see docs) ----
    // features: dOpp, dOwn, oppCells, oppActive, ownCells, oppPlacedPrev2, instab,
    //           oppRouteTraffic, routesAny, adjTownRegion, closes, neutralCells, log1p(freeCells)
    static final double[] W = BossWeights.TARGET;
    /** A held target is abandoned only when another region beats it by this logit margin. */
    static final double ABANDON_MARGIN = BossWeights.ABANDON_MARGIN;
    // ---- construction ----
    private static final int BUILD_HORIZON = 24;
    private static final double ROUTE_STICKINESS = BossWeights.ROUTE_STICKINESS;
    private static final int OPENING_TURNS = 3;
    private static final double OPENING_TOWN_BONUS = 1.5;
    private static final double COST_PREFERENCE = BossWeights.COST_PREFERENCE;

    private final Params p;
    private final Timer timer = new Timer();
    private Pathing paths;
    private TacticalPathing tactical;
    private GameState work, sim;
    private int[] route, bestRoute, entry, dist, placedTurn, prevOwner, cellBuf;
    private int[] oppCells, ownCells, neutralCells, oppActive, oppPrev2, freeCells, routesAny;
    private double[] oppTraffic, score;
    private boolean[] adjTown, onActive, blockedRegion;
    private final int[] before = new int[2], after = new int[2];
    private Random random;
    private int target = -1, lastPair = -1, bestPair = -1, bestLength = -1;

    public BossBot(Params params) {
        p = params.copy();
        if (!Double.isFinite(p.NOISE) || p.NOISE < 0) throw new IllegalArgumentException("Invalid boss reference parameters");
    }

    public String name() { return "boss-v1"; }

    public void init(GameState s, int me) {
        paths = new Pathing(s.size); tactical = new TacticalPathing(s.size);
        work = s.copy(); sim = s.copy();
        route = new int[s.size]; bestRoute = new int[s.size]; entry = new int[s.size]; dist = new int[s.size];
        placedTurn = new int[s.size]; prevOwner = new int[s.size]; cellBuf = new int[s.size];
        Arrays.fill(placedTurn, -99); Arrays.fill(prevOwner, GameState.NO_TRACK);
        oppCells = new int[s.regionCount]; ownCells = new int[s.regionCount]; neutralCells = new int[s.regionCount];
        oppActive = new int[s.regionCount]; oppPrev2 = new int[s.regionCount]; freeCells = new int[s.regionCount];
        routesAny = new int[s.regionCount]; oppTraffic = new double[s.regionCount]; score = new double[s.regionCount];
        adjTown = new boolean[s.regionCount]; onActive = new boolean[s.size]; blockedRegion = new boolean[s.regionCount];
        random = new Random(p.SEED * 1_000_003L + me * 7919L + 17);
        target = -1; lastPair = -1;
    }

    public List<Action> play(GameState s, int me, long budgetMs) {
        timer.start(budgetMs);
        List<Action> actions = new ArrayList<>(4);
        if (budgetMs <= 0) { actions.add(Action.waitAction()); return actions; }
        observe(s);
        target = chooseTarget(s, me);
        work.copyFrom(s);
        if (target >= 0) {
            actions.add(Action.disrupt(target));
            Rules.disrupt(work, target);
            Rules.updateConnections(work, paths);
        }
        int paint = GameState.PAINT_PER_TURN;
        int chosenPair = -1;
        while (paint > 0 && !timer.up()) {
            int length = chooseProject(work, me, paint, s.turn);
            if (length < 0) break;
            chosenPair = bestPair;
            int previous = paint;
            for (int i = 0; i < length && paint > 0; i++) {
                int c = bestRoute[i];
                if (!work.canBuild(c) || work.placeCost(c) > paint) continue;
                paint -= work.placeCost(c); work.owner[c] = me;
                actions.add(Action.place(work.x(c), work.y(c)));
            }
            if (previous == paint) break;
            Rules.updateConnections(work, paths);
        }
        if (chosenPair >= 0) lastPair = chosenPair;
        // Steal: single connector cells that raise the income difference right now.
        while (paint > 0 && !timer.up()) {
            int c = stealCell(work, me, paint);
            if (c < 0) break;
            paint -= work.placeCost(c); work.owner[c] = me;
            actions.add(Action.place(work.x(c), work.y(c)));
            Rules.updateConnections(work, paths);
        }
        if (actions.isEmpty()) actions.add(Action.waitAction());
        return actions;
    }

    /** Remember when each opponent track appeared (public board only). */
    private void observe(GameState s) {
        for (int c = 0; c < s.size; c++) {
            if (s.owner[c] != prevOwner[c] && s.owner[c] != GameState.NO_TRACK) placedTurn[c] = s.turn;
            prevOwner[c] = s.owner[c];
        }
    }

    // ------------------------------------------------------------------ sabotage

    private int chooseTarget(GameState s, int me) {
        int opp = 1 - me;
        if (target >= 0 && s.regionInked[target]) target = -1;
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
            int x = s.x(c), y = s.y(c);
            for (int d = 0; d < 4; d++) {
                int nx = x + GameState.DX[d], ny = y + GameState.DY[d];
                if (!s.inBounds(nx, ny)) continue;
                int nr = s.regionId[s.cell(nx, ny)];
                if (nr != r && s.regionHasTown(nr)) adjTown[r] = true;
            }
        }
        // Route traffic: cheapest completion of every inactive pair (shared), and the opponent's own-only one.
        for (int pair = 0; pair < s.pairCount && !timer.up(); pair++) {
            if (s.pairActive(pair)) continue;
            int from = s.townCell[s.pairFrom[pair]], to = s.townCell[s.pairTo[pair]];
            int n = paths.cheapestPath(s, from, to, route);
            if (n > 0) {
                int cost = routeCost(s, route, n);
                if (cost > 0) markRegions(s, route, n, routesAny, null, 0);
            }
            entryCosts(s, opp, false);
            n = tactical.weightedPath(s, from, to, entry, route);
            if (n > 0) {
                int cost = routeCost(s, route, n);
                if (cost > 0) markRegions(s, route, n, null, oppTraffic, 1.0 / cost);
            }
        }
        Rules.incomePerTurn(s, before);
        int best = -1; double bestScore = -Double.MAX_VALUE;
        double heldScore = -Double.MAX_VALUE;
        for (int r = 0; r < s.regionCount && !timer.up(); r++) {
            score[r] = -Double.MAX_VALUE;
            if (s.regionInked[r] || s.regionHasTown(r)) continue;
            boolean candidate = oppCells[r] > 0 || ownCells[r] > 0 || neutralCells[r] > 0 || s.regionInstability[r] > 0
                    || routesAny[r] > 0 || oppTraffic[r] > 0 || r == target;
            if (!candidate) continue;
            int needed = GameState.INK_THRESHOLD - s.regionInstability[r];
            int dOpp = 0, dOwn = 0, closes = 0;
            if (oppCells[r] + ownCells[r] + neutralCells[r] > 0 || needed == 1) {
                sim.copyFrom(s); Rules.ink(sim, r); Rules.updateConnections(sim, paths); Rules.incomePerTurn(sim, after);
                dOpp = before[opp] - after[opp]; dOwn = before[me] - after[me];
                if (needed == 1 && !Rules.anyPairPossible(sim, paths)) {
                    closes = 1;
                    // One-hit map closure: take it only when it wins outright, never when it loses.
                    if (s.score[me] + after[me] > s.score[opp] + after[opp]) return r;
                    continue;
                }
            }
            double v = W[0] * dOpp + W[1] * dOwn + W[2] * oppCells[r] + W[3] * oppActive[r] + W[4] * ownCells[r]
                    + W[5] * oppPrev2[r] + W[6] * s.regionInstability[r] + W[7] * oppTraffic[r] + W[8] * routesAny[r]
                    + W[9] * (adjTown[r] ? 1 : 0) + W[10] * closes + W[11] * neutralCells[r] + W[12] * Math.log1p(freeCells[r]);
            v += p.NOISE * (random.nextDouble() - 0.5);
            score[r] = v;
            if (r == target) heldScore = v;
            if (v > bestScore) { bestScore = v; best = r; }
        }
        if (best < 0) return -1;
        // Hold the committed region until it inks unless a clearly better region appeared.
        if (target >= 0 && heldScore > -Double.MAX_VALUE && bestScore < heldScore + ABANDON_MARGIN) return target;
        // Do not spend the point on a region that has nothing of the opponent's and no prospect.
        if (oppCells[best] == 0 && s.regionInstability[best] == 0 && oppTraffic[best] == 0 && routesAny[best] == 0) return -1;
        return best;
    }

    private int routeCost(GameState s, int[] cells, int n) {
        int cost = 0;
        for (int i = 0; i < n; i++) if (s.canBuild(cells[i])) cost += s.placeCost(cells[i]);
        return cost;
    }

    private void markRegions(GameState s, int[] cells, int n, int[] counts, double[] traffic, double weight) {
        Arrays.fill(blockedRegion, false);
        for (int i = 0; i < n; i++) {
            int r = s.regionId[cells[i]];
            if (blockedRegion[r] || s.regionHasTown(r)) continue;
            blockedRegion[r] = true;
            if (counts != null) counts[r]++;
            if (traffic != null) traffic[r] += weight;
        }
    }

    // ------------------------------------------------------------------ construction

    /**
     * Entry costs of the boss's routing model: own tracks, towns and neutral tracks are free,
     * opponent tracks are impassable, free cells cost their paint, and non-town regions at
     * instability two or more are impassable. With {@code shared}, opponent tracks are free too.
     */
    private void entryCosts(GameState s, int player, boolean shared) {
        for (int c = 0; c < s.size; c++) {
            int r = s.regionId[c];
            int cost;
            if (s.inked[c]) cost = TacticalPathing.INF;
            else if (s.isTown(c) || s.owner[c] == player || s.owner[c] == GameState.NEUTRAL) cost = 0;
            else if (s.owner[c] == 1 - player) cost = shared ? 0 : TacticalPathing.INF;
            else if (!s.regionHasTown(r) && (s.regionInstability[r] >= 2 || r == target)) cost = TacticalPathing.INF;
            else cost = s.placeCost(c);
            entry[c] = cost;
        }
    }

    /**
     * Most valuable project: for every desired pair, the own-only unstable-avoiding route (shared
     * fallback for inactive pairs), valued by the exact income-difference change after completion,
     * the turns it will earn inside the horizon, and a preference for near-complete projects.
     */
    private int chooseProject(GameState s, int me, int paint, int turn) {
        Rules.incomePerTurn(s, before);
        double best = 0; bestLength = -1; bestPair = -1;
        for (int pair = 0; pair < s.pairCount && !timer.up(); pair++) {
            int from = s.townCell[s.pairFrom[pair]], to = s.townCell[s.pairTo[pair]];
            for (int variant = 0; variant < 2; variant++) {
                if (variant == 1 && s.pairActive(pair)) break;
                entryCosts(s, me, variant == 1);
                int n = tactical.weightedPath(s, from, to, entry, route);
                if (n <= 0) continue;
                sim.copyFrom(s);
                int cost = 0, protectedCells = 0, free = 0;
                boolean touchesTown = false;
                for (int i = 0; i < n; i++) {
                    int c = route[i];
                    if (!s.canBuild(c)) continue;
                    cost += s.placeCost(c); sim.owner[c] = me; free++;
                    if (s.regionHasTown(s.regionId[c])) { touchesTown = true; protectedCells++; }
                }
                if (cost == 0) break;
                int delay = (Math.max(0, cost - paint) + GameState.PAINT_PER_TURN - 1) / GameState.PAINT_PER_TURN;
                int earning = Math.min(BUILD_HORIZON, s.turnsLeft()) - delay;
                if (earning <= 0) continue;
                Rules.updateConnections(sim, paths); Rules.incomePerTurn(sim, after);
                int gain = (after[me] - before[me]) - (after[1 - me] - before[1 - me]);
                if (gain <= 0) continue;
                double value = gain * (double) earning / (delay + 1) / Math.pow(cost, COST_PREFERENCE);
                if (turn < OPENING_TURNS && touchesTown) value *= OPENING_TOWN_BONUS;
                if (pair == lastPair) value *= 1 + ROUTE_STICKINESS;
                value *= noise();
                if (value > best) {
                    best = value; bestLength = n; bestPair = pair; System.arraycopy(route, 0, bestRoute, 0, n);
                }
                if (variant == 0) break; // own-only route exists: the shared fallback is not considered
            }
        }
        return bestLength;
    }

    /** One affordable connector cell outside unstable regions whose placement raises the income difference. */
    private int stealCell(GameState s, int me, int paint) {
        Rules.incomePerTurn(s, before);
        int base = before[me] - before[1 - me];
        int count = NetworkCells.collectSingleCellConnections(s, cellBuf);
        int bestCell = -1; double bestGain = 0;
        for (int i = 0; i < count && !timer.up(); i++) {
            int c = cellBuf[i];
            int r = s.regionId[c];
            if (s.placeCost(c) > paint || r == target) continue;
            if (!s.regionHasTown(r) && s.regionInstability[r] >= 2) continue;
            sim.copyFrom(s); sim.owner[c] = me;
            Rules.updateConnections(sim, paths); Rules.incomePerTurn(sim, after);
            double gain = (after[me] - after[1 - me] - base) * noise();
            if (gain > bestGain) { bestGain = gain; bestCell = c; }
        }
        return bestCell;
    }

    private double noise() { return p.NOISE == 0 ? 1 : 1 + p.NOISE * (random.nextDouble() - 0.5); }
}
