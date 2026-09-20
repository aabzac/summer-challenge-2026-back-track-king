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
 * Local-only {@code leader-v1} reference: a fast heuristic calibrated on the arena leader's
 * replays (Indrill, Legend #1, 2026-09-20; see docs/indrill-study-v1.md). It reproduces the
 * behavioral signature that the v08 self-play mirror does not: full paint every turn, a DISRUPT
 * every turn aimed at the opponent's <em>unfinished</em> construction, sticky follow-through
 * until a region inks, and a protected town-approach opening.
 *
 * <p>This is a screening opponent, not a submission and not a copy of any strategy class. Its
 * constants are frozen from the study's fitted target valuation and are never retuned; only
 * {@code NOISE}/{@code SEED} (symmetry breaking) and {@code UNSTABLE_COST} (routing) come from
 * {@link Params}. The valuation uses only the public board through {@link Rules}; it never reads
 * the opponent's actual or predicted command.
 */
public final class LeaderBot implements Bot {
    // Study-derived, frozen. Units match RolloutBot.chooseTarget: income deltas are scaled by HORIZON.
    private static final double OPP_TRACK_VALUE = 5.0;   // per opponent track in the region (unfinished project)
    private static final double OWN_TRACK_VALUE = 6.0;   // penalty per own track at risk
    private static final double INSTAB_FOLLOW = 8.0;     // per instability level already reached (four-hit commitment)
    private static final double SELF_LOSS_WEIGHT = 0.5;  // own income loss weighted half of the opponent's
    private static final double STICKINESS = 0.7;        // multiplicative bonus for repeating the previous target
    private static final double CORRIDOR_WEIGHT = 2.0;   // early empty-region pre-emption on town approaches
    private static final int HORIZON = 8;                // income projection turns
    private static final int OPENING_TURNS = 3;          // protected town-approach opening
    private static final double OPENING_TOWN_BONUS = 1.5;

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

    public LeaderBot(Params params) {
        p = params.copy();
        if (!Double.isFinite(p.UNSTABLE_COST) || p.UNSTABLE_COST < 0
                || !Double.isFinite(p.NOISE) || p.NOISE < 0) {
            throw new IllegalArgumentException("Invalid leader reference parameters");
        }
    }

    public String name() { return "leader-v1"; }

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
            int length = chooseRoute(work, me, paint, s.turn);
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

    /** Ink the region where the opponent still has to build; sticky until it inks; never a losing close. */
    private int chooseTarget(GameState s, int me) {
        Arrays.fill(own, 0); Arrays.fill(opponent, 0); Arrays.fill(corridors, 0);
        for (int c = 0; c < s.size; c++) {
            if (s.owner[c] == me) own[s.regionId[c]]++;
            else if (s.owner[c] == 1 - me) opponent[s.regionId[c]]++;
        }
        // Public cheapest corridors of still-inactive pairs: where the opponent must still lay track.
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
                continue; // refuse a closure that does not win
            }
            double benefit = (before[1 - me] - after[1 - me]) - SELF_LOSS_WEIGHT * (before[me] - after[me]);
            double value = (benefit * Math.min(HORIZON, s.turnsLeft())
                    + OPP_TRACK_VALUE * opponent[r] - OWN_TRACK_VALUE * own[r]
                    + INSTAB_FOLLOW * s.regionInstability[r]
                    + CORRIDOR_WEIGHT * corridors[r]) / needed;
            if (r == target) value *= 1 + STICKINESS;
            value *= noise();
            // Full-paint pressure: keep the least harmful legal target even when nothing scores positive.
            if (after[me] >= before[me] && opponent[r] >= own[r] && value > forced) {
                forced = value; fallback = r;
            }
            if (value > best) { best = value; chosen = r; }
        }
        return chosen >= 0 ? chosen : fallback;
    }

    /** Spend all paint on the most valuable affordable route, biased to town approaches while opening. */
    private int chooseRoute(GameState s, int me, int paint, int turn) {
        Rules.incomePerTurn(s, before);
        double best = 0; int bestLength = -1;
        for (int pair = 0; pair < s.pairCount && !timer.up(); pair++) {
            int n = paths.cheapestPath(s, s.townCell[s.pairFrom[pair]], s.townCell[s.pairTo[pair]],
                    route, blocked, extra);
            if (n < 0) continue;
            sim.copyFrom(s); int cost = 0; boolean touchesTownRegion = false;
            for (int i = 0; i < n; i++) {
                int c = route[i];
                if (!s.canBuild(c)) continue;
                cost += s.placeCost(c); sim.owner[c] = me;
                if (s.regionHasTown(s.regionId[c])) touchesTownRegion = true;
            }
            if (cost == 0) continue;
            int delay = (Math.max(0, cost - paint) + GameState.PAINT_PER_TURN - 1) / GameState.PAINT_PER_TURN;
            int earning = Math.min(HORIZON, s.turnsLeft()) - delay;
            if (earning <= 0) continue;
            Rules.updateConnections(sim, paths); Rules.incomePerTurn(sim, after);
            double value = ((after[me] - before[me]) - (after[1 - me] - before[1 - me]))
                    * (double) earning / (delay + 1);
            if (turn < OPENING_TURNS && touchesTownRegion) value *= OPENING_TOWN_BONUS;
            value *= noise();
            if (value > best) {
                best = value; bestLength = n; System.arraycopy(route, 0, bestRoute, 0, n);
            }
        }
        return bestLength;
    }

    private double noise() { return p.NOISE == 0 ? 1 : 1 + p.NOISE * (random.nextDouble() - 0.5); }
}
