package btk.bot.v02;

import btk.bot.Bot;
import btk.bot.Params;
import btk.bot.Timer;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;

import java.util.ArrayList;
import java.util.List;

/**
 * FROZEN v02 ? reference opponent for the arena, do not edit.
 *
 * <p>Simulation-evaluated greedy.
 *
 * <p>Build: for every desired pair generate up to two candidate routes (cheapest path using any
 * tracks; cheapest path avoiding opponent tracks = ownership steal). Each candidate is scored by
 * placing its free cells in a scratch state, recomputing connections and comparing both players'
 * income per turn, projected over the turns left after completion, discounted by build time and
 * by region instability along the route. Paint goes to the best candidate; leftovers cascade.
 *
 * <p>Disrupt: every non-town, non-inked region is scored by simulating its ink (income lost by the
 * opponent minus our own, plus not-yet-earning tracks destroyed), divided by the disrupts still
 * needed. Best positive region gets the free DISRUPT.
 */
public final class EvalBotV02 implements Bot {

    private final Params P;
    private final Timer timer = new Timer();
    private Pathing pathing;
    private GameState sim;
    private int[] pathBuf, bestPath, extraCost;
    private boolean[] blocked;
    private final int[] inc0 = new int[2], inc1 = new int[2];
    private int bestLen, bestCost;
    private java.util.Random rnd;

    public EvalBotV02() {
        this(new Params());
    }

    public EvalBotV02(Params params) {
        this.P = params;
    }

    @Override
    public String name() {
        String d = P.diff();
        return d.isEmpty() ? "v02" : "v02?" + d;
    }

    @Override
    public void init(GameState s, int myId) {
        ensureBuffers(s);
        rnd = new java.util.Random(P.SEED * 1_000_003L + myId * 7919L);
    }

    private double noise() {
        if (P.NOISE <= 0) return 1.0;
        if (rnd == null) rnd = new java.util.Random(P.SEED);
        return 1.0 + P.NOISE * (rnd.nextDouble() - 0.5);
    }

    private void ensureBuffers(GameState s) {
        if (pathing == null || pathBuf == null || pathBuf.length != s.size) {
            pathing = new Pathing(s.size);
            pathBuf = new int[s.size];
            bestPath = new int[s.size];
            extraCost = new int[s.size];
            blocked = new boolean[s.size];
            sim = s.copy();
        }
    }

    @Override
    public List<Action> play(GameState s, int myId, long budgetMs) {
        timer.start(budgetMs);
        ensureBuffers(s);
        List<Action> out = new ArrayList<>(4);
        for (int c = 0; c < s.size; c++) {
            extraCost[c] = (int) Math.round(P.UNSTABLE_COST * s.regionInstability[s.regionId[c]]);
        }

        int budget = GameState.PAINT_PER_TURN;
        int guard = 0;
        while (budget > 0 && guard++ < 6 && !timer.up()) {
            if (!chooseCandidate(s, myId, budget)) break;
            int before = budget;
            for (int i = 0; i < bestLen && budget > 0; i++) {
                int c = bestPath[i];
                if (!s.canBuild(c)) continue;
                int cost = s.placeCost(c);
                if (cost > budget) continue;
                budget -= cost;
                out.add(Action.place(s.x(c), s.y(c)));
                s.owner[c] = myId;
            }
            if (budget == before) break;
            Rules.updateConnections(s, pathing);
        }

        if (P.USE_DISRUPT) {
            int region = chooseDisrupt(s, myId);
            if (region >= 0) out.add(Action.disrupt(region));
        }
        if (P.MESSAGE) out.add(Action.message("t" + s.turn + " " + timer.elapsedMs() + "ms"));
        if (out.isEmpty()) out.add(Action.waitAction());
        return out;
    }

    // ------------------------------------------------------------------ build

    /** Fills bestPath/bestLen/bestCost with the best candidate; false when nothing is worth building. */
    private boolean chooseCandidate(GameState s, int myId, int budgetNow) {
        Rules.incomePerTurn(s, inc0);
        double bestValue = 1e-9;
        bestLen = -1;
        int opp = 1 - myId;
        for (int c = 0; c < s.size; c++) blocked[c] = s.owner[c] == opp;
        int turnsLeft = s.turnsLeft();
        for (int p = 0; p < s.pairCount && !timer.up(); p++) {
            int from = s.townCell[s.pairFrom[p]], to = s.townCell[s.pairTo[p]];
            for (int variant = 0; variant < 2; variant++) {
                if (variant == 1 && !P.USE_STEAL) break;
                int n = pathing.cheapestPath(s, from, to, pathBuf, variant == 1 ? blocked : null, extraCost);
                if (n < 0) continue;
                int paint = 0, maxInst = 0;
                for (int i = 0; i < n; i++) {
                    int c = pathBuf[i];
                    if (s.canBuild(c)) {
                        paint += s.placeCost(c);
                        maxInst = Math.max(maxInst, s.regionInstability[s.regionId[c]]);
                    }
                }
                if (paint == 0) continue;
                if (s.pairActive(p) && n > s.pairPathLen[p]) continue;
                int remaining = Math.max(0, paint - budgetNow);
                int turnsToFinish = (remaining + GameState.PAINT_PER_TURN - 1) / GameState.PAINT_PER_TURN;
                int earning = turnsLeft - turnsToFinish;
                if (earning <= 0) continue;

                sim.copyFrom(s);
                for (int i = 0; i < n; i++) {
                    int c = pathBuf[i];
                    if (sim.canBuild(c)) sim.owner[c] = myId;
                }
                Rules.updateConnections(sim, pathing);
                Rules.incomePerTurn(sim, inc1);
                double gain = (inc1[myId] - inc0[myId]) - P.W_OPP_INCOME * (inc1[opp] - inc0[opp]);
                if (gain <= 0) continue;
                double risk = Math.max(0, 1 - P.W_RISK * maxInst / GameState.INK_THRESHOLD);
                double value = gain * earning * risk - P.W_COST * paint;
                value /= (turnsToFinish + 1);
                value *= noise();
                if (value > bestValue) {
                    bestValue = value;
                    bestLen = n;
                    bestCost = paint;
                    System.arraycopy(pathBuf, 0, bestPath, 0, n);
                }
            }
        }
        return bestLen > 0;
    }

    // ------------------------------------------------------------------ disrupt

    private int chooseDisrupt(GameState s, int myId) {
        int opp = 1 - myId;
        Rules.incomePerTurn(s, inc0);
        int turnsLeft = s.turnsLeft();
        double bestValue = P.DISRUPT_MIN;
        int best = -1;
        int[] oppTracks = new int[s.regionCount], myTracks = new int[s.regionCount];
        for (int c = 0; c < s.size; c++) {
            if (s.owner[c] == opp) oppTracks[s.regionId[c]]++;
            else if (s.owner[c] == myId) myTracks[s.regionId[c]]++;
        }
        for (int r = 0; r < s.regionCount && !timer.up(); r++) {
            if (s.regionInked[r] || s.regionHasTown(r)) continue;
            if (oppTracks[r] == 0 && myTracks[r] == 0) continue;
            int needed = Math.max(1, GameState.INK_THRESHOLD - s.regionInstability[r]);
            int earning = turnsLeft - needed;
            if (earning <= 0) continue;
            sim.copyFrom(s);
            Rules.ink(sim, r);
            Rules.updateConnections(sim, pathing);
            Rules.incomePerTurn(sim, inc1);
            double oppLoss = inc0[opp] - inc1[opp];
            double myLoss = inc0[myId] - inc1[myId];
            double value = (oppLoss - P.W_SELF_LOSS * myLoss) * earning
                    + P.W_FUTURE_TRACK * oppTracks[r] - P.W_FUTURE_OWN * myTracks[r];
            value /= needed;
            value *= noise();
            if (value > bestValue) {
                bestValue = value;
                best = r;
            }
        }
        return best;
    }
}
