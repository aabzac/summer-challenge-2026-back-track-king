package btk.bot.reference;

import btk.bot.Bot;
import btk.bot.Params;
import btk.bot.Timer;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Local-only {@code leader-v2} reference: {@link LeaderBot}'s sabotage unchanged, construction
 * rebuilt from the per-cell study of the leader's replays (docs/indrill-study-v1.md, follow-up
 * 2026-09-20). The study found no hidden routing model: 87% of the leader's cells through turn 45
 * lie within one paint point of a paint-optimal completion route (shared or enemy-avoiding), on a
 * take-over path for an active pair, or on a bypass of an unstable region; what differs from the
 * search bots is that he spends every paint point every turn and finishes what he starts. So this
 * reference never idles: it builds the most valuable completion route over a long horizon, falls
 * back to the cheapest remaining completion when nothing is profitable inside the horizon, and
 * spends leftover paint next to its own earning rails when no desired pair is left to complete.
 *
 * <p>Constants are frozen; only NOISE/SEED/UNSTABLE_COST come from {@link Params}. Screening
 * opponent, not a submission.
 */
public final class LeaderBot2 implements Bot {
    // Sabotage constants: identical to LeaderBot so the two references differ only in construction.
    private static final double OPP_TRACK_VALUE = 5.0;
    private static final double OWN_TRACK_VALUE = 6.0;
    private static final double INSTAB_FOLLOW = 8.0;
    private static final double SELF_LOSS_WEIGHT = 0.5;
    private static final double STICKINESS = 0.7;
    private static final double CORRIDOR_WEIGHT = 2.0;
    private static final int HORIZON = 8;
    // Construction constants from the cell study.
    private static final int BUILD_HORIZON = 24;          // routes up to ~8 turns of paint still count
    private static final double ROUTE_STICKINESS = 0.2;   // finish what was started
    private static final int OPENING_TURNS = 3;
    private static final double OPENING_TOWN_BONUS = 1.5;
    private static final double PROTECTED_BONUS = 1.15;   // uninkable town regions earn 1.6x per cell in the corpus

    private final Params p;
    private final Timer timer = new Timer();
    private Pathing paths;
    private GameState work, sim;
    private int[] route, bestRoute, extra, own, opponent;
    private double[] corridors;
    private boolean[] blocked, blockedOpp;
    private final int[] before = new int[2], after = new int[2];
    private Random random;
    private int target = -1, lastPair = -1;

    public LeaderBot2(Params params) {
        p = params.copy();
        if (!Double.isFinite(p.UNSTABLE_COST) || p.UNSTABLE_COST < 0
                || !Double.isFinite(p.NOISE) || p.NOISE < 0) {
            throw new IllegalArgumentException("Invalid leader reference parameters");
        }
    }

    public String name() { return "leader-v2"; }

    public void init(GameState s, int me) {
        paths = new Pathing(s.size); work = s.copy(); sim = s.copy();
        route = new int[s.size]; bestRoute = new int[s.size]; extra = new int[s.size];
        blocked = new boolean[s.size]; blockedOpp = new boolean[s.size];
        own = new int[s.regionCount]; opponent = new int[s.regionCount];
        corridors = new double[s.regionCount];
        random = new Random(p.SEED * 1_000_003L + me * 7919L); target = -1; lastPair = -1;
    }

    public List<Action> play(GameState s, int me, long budgetMs) {
        timer.start(budgetMs);
        List<Action> actions = new ArrayList<>(4);
        if (budgetMs <= 0) { actions.add(Action.waitAction()); return actions; }
        target = chooseTarget(s, me);
        work.copyFrom(s);
        if (target >= 0) {
            actions.add(Action.disrupt(target));
            Rules.disrupt(work, target);
            Rules.updateConnections(work, paths);
        }
        for (int c = 0; c < s.size; c++) {
            blocked[c] = s.regionId[c] == target;
            blockedOpp[c] = blocked[c] || s.owner[c] == 1 - me || s.owner[c] == GameState.NEUTRAL;
            extra[c] = (int) Math.round(p.UNSTABLE_COST * work.regionInstability[s.regionId[c]]);
        }
        int paint = GameState.PAINT_PER_TURN;
        int chosenPair = -1;
        while (paint > 0 && !timer.up()) {
            int length = chooseRoute(work, me, paint, s.turn);
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
        // Never idle: leftover paint goes next to own earning rails, protected regions first.
        while (paint > 0 && !timer.up()) {
            int c = fillerCell(work, me, paint);
            if (c < 0) break;
            paint -= work.placeCost(c); work.owner[c] = me;
            actions.add(Action.place(work.x(c), work.y(c)));
        }
        if (actions.isEmpty()) actions.add(Action.waitAction());
        return actions;
    }

    /** Identical to LeaderBot.chooseTarget. */
    private int chooseTarget(GameState s, int me) {
        Arrays.fill(own, 0); Arrays.fill(opponent, 0); Arrays.fill(corridors, 0);
        for (int c = 0; c < s.size; c++) {
            if (s.owner[c] == me) own[s.regionId[c]]++;
            else if (s.owner[c] == 1 - me) opponent[s.regionId[c]]++;
        }
        for (int pair = 0; pair < s.pairCount && !timer.up(); pair++) {
            if (s.pairActive(pair)) continue;
            int n = paths.cheapestPath(s, s.townCell[s.pairFrom[pair]], s.townCell[s.pairTo[pair]], route);
            if (n <= 0) continue;
            for (int i = 0; i < n; i++) corridors[s.regionId[route[i]]] += 1.0 / n;
        }
        Rules.incomePerTurn(s, before);
        double best = 0, forced = -Double.MAX_VALUE;
        int chosen = -1, fallback = -1;
        for (int r = 0; r < s.regionCount && !timer.up(); r++) {
            if (s.regionInked[r] || s.regionHasTown(r)) continue;
            int needed = GameState.INK_THRESHOLD - s.regionInstability[r];
            sim.copyFrom(s); Rules.ink(sim, r); Rules.updateConnections(sim, paths);
            Rules.incomePerTurn(sim, after);
            if (needed == 1 && !Rules.anyPairPossible(sim, paths)) {
                if (s.score[me] + after[me] > s.score[1 - me] + after[1 - me]) return r;
                continue;
            }
            double benefit = (before[1 - me] - after[1 - me]) - SELF_LOSS_WEIGHT * (before[me] - after[me]);
            double value = (benefit * Math.min(HORIZON, s.turnsLeft())
                    + OPP_TRACK_VALUE * opponent[r] - OWN_TRACK_VALUE * own[r]
                    + INSTAB_FOLLOW * s.regionInstability[r]
                    + CORRIDOR_WEIGHT * corridors[r]) / needed;
            if (r == target) value *= 1 + STICKINESS;
            value *= noise();
            if (after[me] >= before[me] && opponent[r] >= own[r] && value > forced) {
                forced = value; fallback = r;
            }
            if (value > best) { best = value; chosen = r; }
        }
        return chosen >= 0 ? chosen : fallback;
    }

    private int bestPair = -1;

    /**
     * Most valuable completion route: for every inactive pair the paint-cheapest shared route and the
     * enemy-avoiding route, valued by the exact income change after completion times the turns it will
     * earn inside BUILD_HORIZON. When nothing is profitable inside the horizon, the cheapest remaining
     * completion is built anyway (paint does not carry over), so the bot only stops when no desired
     * pair can be completed at all.
     */
    private int chooseRoute(GameState s, int me, int paint, int turn) {
        Rules.incomePerTurn(s, before);
        double best = 0; int bestLength = -1; bestPair = -1;
        int cheapest = Integer.MAX_VALUE, cheapestLength = -1, cheapestPair = -1;
        for (int pair = 0; pair < s.pairCount && !timer.up(); pair++) {
            if (s.pairActive(pair)) continue;
            for (int variant = 0; variant < 2; variant++) {
                int n = paths.cheapestPath(s, s.townCell[s.pairFrom[pair]], s.townCell[s.pairTo[pair]],
                        route, variant == 0 ? blocked : blockedOpp, extra);
                if (n < 0) continue;
                sim.copyFrom(s); int cost = 0; boolean touchesTownRegion = false; int protectedCells = 0, freeCells = 0;
                for (int i = 0; i < n; i++) {
                    int c = route[i];
                    if (!s.canBuild(c)) continue;
                    cost += s.placeCost(c); sim.owner[c] = me; freeCells++;
                    if (s.regionHasTown(s.regionId[c])) { touchesTownRegion = true; protectedCells++; }
                }
                if (cost == 0) continue;
                int delay = (Math.max(0, cost - paint) + GameState.PAINT_PER_TURN - 1) / GameState.PAINT_PER_TURN;
                if (cost < cheapest) {
                    cheapest = cost; cheapestLength = n; cheapestPair = pair;
                    System.arraycopy(route, 0, extraRoute(), 0, n);
                }
                int earning = Math.min(BUILD_HORIZON, s.turnsLeft()) - delay;
                if (earning <= 0) continue;
                Rules.updateConnections(sim, paths); Rules.incomePerTurn(sim, after);
                double value = ((after[me] - before[me]) - (after[1 - me] - before[1 - me]))
                        * (double) earning / (delay + 1);
                if (turn < OPENING_TURNS && touchesTownRegion) value *= OPENING_TOWN_BONUS;
                if (protectedCells * 2 >= freeCells) value *= PROTECTED_BONUS;
                if (pair == lastPair) value *= 1 + ROUTE_STICKINESS;
                value *= noise();
                if (value > best) {
                    best = value; bestLength = n; bestPair = pair; System.arraycopy(route, 0, bestRoute, 0, n);
                }
            }
        }
        if (bestLength < 0 && cheapestLength > 0) {
            bestLength = cheapestLength; bestPair = cheapestPair;
            System.arraycopy(extraRoute(), 0, bestRoute, 0, cheapestLength);
        }
        return bestLength;
    }

    private int[] cheapestRoute;

    private int[] extraRoute() {
        if (cheapestRoute == null || cheapestRoute.length < route.length) cheapestRoute = new int[route.length];
        return cheapestRoute;
    }

    /**
     * Filler when no desired pair can be completed: a free cell adjacent to an own track that is on an
     * active connection, preferring uninkable town regions, then stable regions, then cheap terrain.
     * In the corpus 62% of the leader's late cells are adjacent to own rails; they earn rarely, but
     * unused paint earns nothing.
     */
    private int fillerCell(GameState s, int me, int paint) {
        int bestCell = -1; double bestScore = -1;
        for (int c = 0; c < s.size && !timer.up(); c++) {
            if (!s.canBuild(c) || s.placeCost(c) > paint || blocked[c]) continue;
            int region = s.regionId[c];
            if (!s.regionHasTown(region) && s.regionInstability[region] >= 2) continue;
            boolean adjacentEarning = false;
            int x = s.x(c), y = s.y(c);
            for (int d = 0; d < 4 && !adjacentEarning; d++) {
                int nx = x + GameState.DX[d], ny = y + GameState.DY[d];
                if (!s.inBounds(nx, ny)) continue;
                int nc = s.cell(nx, ny);
                if (s.owner[nc] == me && onActivePath(s, nc)) adjacentEarning = true;
            }
            if (!adjacentEarning) continue;
            double score = (s.regionHasTown(region) ? 4 : 2 - s.regionInstability[region]) - 0.5 * s.placeCost(c);
            score *= noise();
            if (score > bestScore) { bestScore = score; bestCell = c; }
        }
        return bestCell;
    }

    private static boolean onActivePath(GameState s, int cell) {
        for (int pair = 0; pair < s.pairCount; pair++) {
            if (!s.pairActive(pair)) continue;
            int[] path = s.pairPath[pair];
            for (int i = 0; i < s.pairPathLen[pair]; i++) if (path[i] == cell) return true;
        }
        return false;
    }

    private double noise() { return p.NOISE == 0 ? 1 : 1 + p.NOISE * (random.nextDouble() - 0.5); }
}
