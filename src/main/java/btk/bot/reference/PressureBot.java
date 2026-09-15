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

/** Local-only pressure-v1 reference: sabotage first, sticky targets, short-horizon construction.
 * Not a copy of EvalBot and not a claim to reproduce a particular arena player.
 */
public final class PressureBot implements Bot {
    private final Params p;
    private final Timer timer = new Timer();
    private Pathing paths;
    private GameState work, sim;
    private int[] route, bestRoute, extra, own, opponent;
    private double[] corridors;
    private boolean[] blocked;
    private final int[] before = new int[2], after = new int[2];
    private Random random;
    private int target = -1;

    public PressureBot(Params params) {
        p = params.copy();
        if (p.PRESSURE_HORIZON < 1 || p.PRESSURE_BUILD_RESERVE_MS < 0
                || !Double.isFinite(p.PRESSURE_TRACK_WEIGHT) || p.PRESSURE_TRACK_WEIGHT < 0
                || !Double.isFinite(p.PRESSURE_CORRIDOR_WEIGHT) || p.PRESSURE_CORRIDOR_WEIGHT < 0
                || !Double.isFinite(p.PRESSURE_STICKINESS) || p.PRESSURE_STICKINESS < 0) {
            throw new IllegalArgumentException("Invalid pressure reference parameters");
        }
    }

    public String name() { return "pressure-v1"; }

    public void init(GameState s, int me) {
        paths = new Pathing(s.size); work = s.copy(); sim = s.copy();
        route = new int[s.size]; bestRoute = new int[s.size]; extra = new int[s.size];
        blocked = new boolean[s.size]; own = new int[s.regionCount]; opponent = new int[s.regionCount];
        corridors = new double[s.regionCount];
        random = new Random(p.SEED * 1_000_003L + me * 7919L); target = -1;
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
            extra[c] = (int) Math.round(p.UNSTABLE_COST * work.regionInstability[s.regionId[c]]);
        }
        int paint = GameState.PAINT_PER_TURN;
        while (paint > 0 && !timer.up()) {
            int length = chooseRoute(work, me, paint);
            if (length < 0) break;
            int previous = paint;
            for (int i = 0; i < length; i++) {
                int c = bestRoute[i];
                if (!work.canBuild(c) || work.placeCost(c) > paint) continue;
                paint -= work.placeCost(c); work.owner[c] = me;
                actions.add(Action.place(work.x(c), work.y(c)));
            }
            if (previous == paint) break;
            Rules.updateConnections(work, paths);
        }
        if (actions.isEmpty()) actions.add(Action.waitAction());
        return actions;
    }

    private int chooseTarget(GameState s, int me) {
        Arrays.fill(own, 0); Arrays.fill(opponent, 0); Arrays.fill(corridors, 0);
        for (int c = 0; c < s.size; c++) {
            if (s.owner[c] == me) own[s.regionId[c]]++;
            if (s.owner[c] == 1 - me) opponent[s.regionId[c]]++;
        }
        // Public-board cheapest corridors, not the opponent's actual or predicted command.
        for (int pair = 0; pair < s.pairCount && !timer.up(p.PRESSURE_BUILD_RESERVE_MS); pair++) {
            int n = paths.cheapestPath(s, s.townCell[s.pairFrom[pair]], s.townCell[s.pairTo[pair]], route);
            int mine = 0, theirs = 0;
            for (int i = 0; i < n; i++) {
                if (s.owner[route[i]] == me) mine++;
                if (s.owner[route[i]] == 1 - me) theirs++;
            }
            if (theirs < mine) continue;
            for (int i = 0; i < n; i++) corridors[s.regionId[route[i]]] += 1.0 / Math.max(1, n);
        }
        Rules.incomePerTurn(s, before);
        double best = 0; int chosen = -1;
        for (int r = 0; r < s.regionCount && !timer.up(p.PRESSURE_BUILD_RESERVE_MS); r++) {
            if (s.regionInked[r] || s.regionHasTown(r)) continue;
            int needed = GameState.INK_THRESHOLD - s.regionInstability[r];
            sim.copyFrom(s); Rules.ink(sim, r); Rules.updateConnections(sim, paths);
            Rules.incomePerTurn(sim, after);
            if (needed == 1 && !Rules.anyPairPossible(sim, paths)) {
                if (s.score[me] + after[me] > s.score[1 - me] + after[1 - me]) return r;
                continue;
            }
            double benefit = before[1 - me] - after[1 - me] - before[me] + after[me];
            double value = (benefit * Math.min(p.PRESSURE_HORIZON, s.turnsLeft())
                    + p.PRESSURE_TRACK_WEIGHT * (opponent[r] - own[r])
                    + p.PRESSURE_CORRIDOR_WEIGHT * corridors[r]) / needed;
            if (r == target) value *= 1 + p.PRESSURE_STICKINESS;
            value *= noise();
            if (value > best) { best = value; chosen = r; }
        }
        return chosen;
    }

    private int chooseRoute(GameState s, int me, int paint) {
        Rules.incomePerTurn(s, before);
        double best = 0; int bestLength = -1;
        for (int pair = 0; pair < s.pairCount && !timer.up(); pair++) {
            int n = paths.cheapestPath(s, s.townCell[s.pairFrom[pair]], s.townCell[s.pairTo[pair]],
                    route, blocked, extra);
            if (n < 0) continue;
            sim.copyFrom(s); int cost = 0;
            for (int i = 0; i < n; i++) if (s.canBuild(route[i])) {
                cost += s.placeCost(route[i]); sim.owner[route[i]] = me;
            }
            if (cost == 0) continue;
            int delay = (Math.max(0, cost - paint) + GameState.PAINT_PER_TURN - 1) / GameState.PAINT_PER_TURN;
            int earning = Math.min(p.PRESSURE_HORIZON, s.turnsLeft()) - delay;
            if (earning <= 0) continue;
            Rules.updateConnections(sim, paths); Rules.incomePerTurn(sim, after);
            double value = ((after[me] - before[me]) - (after[1 - me] - before[1 - me]))
                    * (double) earning / (delay + 1) * noise();
            if (value > best) {
                best = value; bestLength = n; System.arraycopy(route, 0, bestRoute, 0, n);
            }
        }
        return bestLength;
    }

    private double noise() { return p.NOISE == 0 ? 1 : 1 + p.NOISE * (random.nextDouble() - 0.5); }
}
