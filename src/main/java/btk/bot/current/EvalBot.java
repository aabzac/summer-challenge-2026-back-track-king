package btk.bot.current;

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
 * v05: v04 with a build cutoff reserving time for sabotage on the original turn clock.
 *
 * <p>Build: for every desired pair generate up to two candidate routes (cheapest path using any
 * tracks; cheapest path avoiding opponent tracks = ownership steal). Each candidate is scored by
 * placing its free cells in a scratch state, recomputing connections and comparing both players'
 * income per turn, projected over the turns left after completion, discounted by build time and
 * by region instability along the route. Paint goes to the best candidate; leftovers cascade.
 * When no complete route improves the evaluation, remaining paint extends existing rails through
 * stable nearby cells, preferring permanent-safe town regions.
 *
 * <p>Opponent model: the same candidate search is run from the opponent's point of view first.
 * Cells they are predicted to place this turn get a routing surcharge (a clash makes the track
 * neutral for both) and are placed last; their predicted route counts as future tracks when
 * choosing what to ink.
 *
 * <p>Disrupt: every non-town, non-inked region is scored by simulating its ink (income lost by the
 * opponent minus our own, plus not-yet-earning tracks destroyed), divided by the disrupts still
 * needed. Best positive region gets the free DISRUPT; failing that, the region with the best
 * opponent-vs-own track balance.
 */
public final class EvalBot implements Bot {

    private final Params P;
    private final Timer timer = new Timer();
    private Pathing pathing;
    private GameState sim, sim2;
    private int[] pathBuf, bestPath, extraCost, oppPath;
    private boolean[] blocked, contested, myRoute, regionMark;
    private final int[] inc0 = new int[2], inc1 = new int[2], incA = new int[2], incB = new int[2];
    private int bestLen, bestCost, oppLen, worstRegion;
    private java.util.Random rnd;

    public EvalBot() {
        this(new Params());
    }

    public EvalBot(Params params) {
        this.P = params;
    }

    @Override
    public String name() {
        String d = P.diff();
        return d.isEmpty() ? "current" : "current?" + d;
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
            oppPath = new int[s.size];
            extraCost = new int[s.size];
            blocked = new boolean[s.size];
            contested = new boolean[s.size];
            myRoute = new boolean[s.size];
            regionMark = new boolean[s.regionCount];
            sim = s.copy();
            sim2 = s.copy();
        }
    }

    /**
     * Value of a state for {@code me}: income difference now, minus the damage of the opponent's
     * most profitable single ink (regions on active paths that hold my tracks), discounted by the
     * disrupts it still needs. Sets {@link #worstRegion} to that region (-1 if none).
     */
    private double evalState(GameState st, int me) {
        int opp = 1 - me;
        Rules.incomePerTurn(st, incA);
        double imm = incA[me] - P.W_OPP_INCOME * incA[opp];
        worstRegion = -1;
        if (P.W_VULN <= 0) return imm;
        java.util.Arrays.fill(regionMark, false);
        for (int p = 0; p < st.pairCount; p++) {
            if (!st.pairActive(p)) continue;
            int[] path = st.pairPath[p];
            for (int i = 0; i < st.pairPathLen[p]; i++) {
                int c = path[i];
                if (st.owner[c] == me) regionMark[st.regionId[c]] = true;
            }
        }
        double worst = 0;
        for (int r = 0; r < st.regionCount; r++) {
            if (!regionMark[r] || st.regionInked[r] || st.regionHasTown(r)) continue;
            int needed = Math.max(1, GameState.INK_THRESHOLD - st.regionInstability[r]);
            sim2.copyFrom(st);
            Rules.ink(sim2, r);
            Rules.updateConnections(sim2, pathing);
            Rules.incomePerTurn(sim2, incB);
            double myLoss = incA[me] - incB[me];
            double theirLoss = incA[opp] - incB[opp];
            double threat = (myLoss - P.W_THEIR_LOSS * theirLoss) / needed;
            if (threat > worst) {
                worst = threat;
                worstRegion = r;
            }
        }
        return imm - P.W_VULN * worst;
    }

    @Override
    public List<Action> play(GameState s, int myId, long budgetMs) {
        timer.start(budgetMs);
        ensureBuffers(s);
        int opp = 1 - myId;
        List<Action> out = new ArrayList<>(4);
        for (int c = 0; c < s.size; c++) {
            extraCost[c] = (int) Math.round(P.UNSTABLE_COST * s.regionInstability[s.regionId[c]]);
            contested[c] = false;
            myRoute[c] = false;
        }

        // --- opponent prediction (their best route with our algorithm, no collision penalty) ---
        oppLen = 0;
        if (P.USE_OPP_MODEL && chooseCandidate(s, opp, GameState.PAINT_PER_TURN)) {
            oppLen = bestLen;
            System.arraycopy(bestPath, 0, oppPath, 0, oppLen);
            int paint = 0;
            for (int i = 0; i < oppLen && paint < GameState.PAINT_PER_TURN; i++) {
                int c = oppPath[i];
                if (!s.canBuild(c)) continue;
                paint += s.placeCost(c);
                if (paint <= GameState.PAINT_PER_TURN) {
                    contested[c] = true;
                    extraCost[c] += (int) Math.round(P.COLLISION_COST);
                }
            }
        }

        // --- build ---
        int budget = GameState.PAINT_PER_TURN;
        int guard = 0;
        while (budget > 0 && guard++ < 6 && !buildTimeUp()) {
            if (!chooseCandidate(s, myId, budget)) break;
            int before = budget;
            // Keep the selected long-term route; only compare the cells affordable this turn.
            // Complete routes have identical resulting ownership in either direction.
            boolean reverse = P.CHOOSE_BUILD_DIRECTION && bestCost > budget && !buildTimeUp()
                    && preferReverseBuild(s, myId, budget, bestPath, bestLen);
            for (int i = 0; i < bestLen; i++) if (s.canBuild(bestPath[i])) myRoute[bestPath[i]] = true;
            // uncontested cells first, then contested ones if paint is left
            for (int pass = 0; pass < 2 && budget > 0; pass++) {
                for (int i = 0; i < bestLen && budget > 0; i++) {
                    int c = bestPath[reverse ? bestLen - 1 - i : i];
                    if (!s.canBuild(c) || contested[c] != (pass == 1)) continue;
                    int cost = s.placeCost(c);
                    if (cost > budget) continue;
                    budget -= cost;
                    out.add(Action.place(s.x(c), s.y(c)));
                    s.owner[c] = myId;
                }
            }
            if (budget == before) break;
            Rules.updateConnections(s, pathing);
        }

        // Keep useful local momentum when every complete route has zero immediate evaluation
        // gain. Prefer cells adjoining our network, especially in permanent-safe town regions.
        if (P.USE_IDLE_EXTEND) {
            boolean extended = false;
            while (budget > 0 && !buildTimeUp()) {
                int c = chooseIdleExtension(s, myId, budget);
                if (c < 0) break;
                budget -= s.placeCost(c);
                myRoute[c] = true;
                out.add(Action.place(s.x(c), s.y(c)));
                s.owner[c] = myId;
                extended = true;
            }
            if (extended) Rules.updateConnections(s, pathing);
        }

        // --- disrupt ---
        if (P.USE_DISRUPT) {
            int region = chooseDisrupt(s, myId);
            if (region >= 0) out.add(Action.disrupt(region));
        }
        if (P.MESSAGE) out.add(Action.message("t" + s.turn + " " + timer.elapsedMs() + "ms"));
        if (out.isEmpty()) out.add(Action.waitAction());
        return out;
    }

    // ------------------------------------------------------------------ build

    /** A phase cutoff, not a new timer: sabotage still uses the original turn deadline. */
    private boolean buildTimeUp() {
        return P.USE_DISRUPT && P.DISRUPT_RESERVE_MS > 0
                ? timer.up(P.DISRUPT_RESERVE_MS) : timer.up();
    }

    /** Fills bestPath/bestLen/bestCost with the best candidate for {@code me}; false when nothing is worth building. */
    private boolean chooseCandidate(GameState s, int me, int budgetNow) {
        if (P.USE_DISRUPT && P.DISRUPT_RESERVE_MS > 0 && buildTimeUp()) return false;
        double base = evalState(s, me);
        int threatened = worstRegion;
        double bestValue = 1e-9;
        bestLen = -1;
        int opp = 1 - me;
        for (int c = 0; c < s.size; c++) blocked[c] = s.owner[c] == opp;
        int turnsLeft = s.turnsLeft();
        for (int p = 0; p < s.pairCount && !buildTimeUp(); p++) {
            int from = s.townCell[s.pairFrom[p]], to = s.townCell[s.pairTo[p]];
            for (int variant = 0; variant < 3 && !buildTimeUp(); variant++) {
                if (variant == 1 && !P.USE_STEAL) continue;
                boolean[] blk = null;
                if (variant == 1) {
                    blk = blocked;
                } else if (variant == 2) {
                    // backup route: active pair whose path crosses the most threatened region
                    if (!P.USE_BACKUP || threatened < 0 || !s.pairActive(p)) break;
                    boolean crosses = false;
                    for (int i = 0; i < s.pairPathLen[p] && !crosses; i++) crosses = s.regionId[s.pairPath[p][i]] == threatened;
                    if (!crosses) break;
                    for (int c = 0; c < s.size; c++) blocked[c] = s.regionId[c] == threatened;
                    blk = blocked;
                }
                int n = pathing.cheapestPath(s, from, to, pathBuf, blk, extraCost);
                if (variant == 2) for (int c = 0; c < s.size; c++) blocked[c] = s.owner[c] == opp;
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
                if (variant != 2 && s.pairActive(p) && n > s.pairPathLen[p]) continue;
                int remaining = Math.max(0, paint - budgetNow);
                int turnsToFinish = (remaining + GameState.PAINT_PER_TURN - 1) / GameState.PAINT_PER_TURN;
                int earning = Math.min(turnsLeft, P.HORIZON) - turnsToFinish;
                if (earning <= 0) continue;

                sim.copyFrom(s);
                for (int i = 0; i < n; i++) {
                    int c = pathBuf[i];
                    if (sim.canBuild(c)) sim.owner[c] = me;
                }
                Rules.updateConnections(sim, pathing);
                double gain = evalState(sim, me) - base;
                if (gain <= 1e-9) continue;
                double risk = Math.max(0, 1 - P.W_RISK * maxInst / GameState.INK_THRESHOLD);
                double value = gain * earning * risk - P.W_COST * paint;
                value /= (turnsToFinish + 1);
                value *= noise();
                if (value > bestValue) {
                    if (P.GUARD_BUILD_INCOME && !buildPrefixNonLosing(s, me, budgetNow, n)) continue;
                    bestValue = value;
                    bestLen = n;
                    bestCost = paint;
                    System.arraycopy(pathBuf, 0, bestPath, 0, n);
                }
            }
        }
        return bestLen > 0;
    }

    /** Strictly better raw income wins; ties preserve the historical direction, never vetoing. */
    boolean preferReverseBuild(GameState s, int me, int budget, int[] route, int len) {
        int forward = prefixIncome(s, me, budget, route, len, false);
        int reverse = prefixIncome(s, me, budget, route, len, true);
        return reverse > forward;
    }

    /** Scratch simulation uses the same cost skipping and uncontested-first order as execution. */
    int prefixIncome(GameState s, int me, int budget, int[] route, int len, boolean reverse) {
        sim.copyFrom(s);
        for (int pass = 0; pass < 2 && budget > 0; pass++) {
            for (int i = 0; i < len && budget > 0; i++) {
                int c = route[reverse ? len - 1 - i : i];
                if (!sim.canBuild(c) || contested[c] != (pass == 1)) continue;
                int cost = sim.placeCost(c);
                if (cost > budget) continue;
                budget -= cost;
                sim.owner[c] = me;
            }
        }
        Rules.updateConnections(sim, pathing);
        Rules.incomePerTurn(sim, inc1);
        return inc1[me] - inc1[1 - me];
    }

    /** Simulate exactly the affordable placement order, not the fully completed route. */
    private boolean buildPrefixNonLosing(GameState s, int me, int budget, int len) {
        sim.copyFrom(s);
        for (int pass = 0; pass < 2 && budget > 0; pass++) {
            for (int i = 0; i < len && budget > 0; i++) {
                int c = pathBuf[i];
                if (!sim.canBuild(c) || contested[c] != (pass == 1)) continue;
                int cost = sim.placeCost(c);
                if (cost > budget) continue;
                budget -= cost;
                sim.owner[c] = me;
            }
        }
        return buildIncomeNonLosing(s, me);
    }

    /** The core computes incomes; this is only a strategy veto, without future opponent moves. */
    private boolean buildIncomeNonLosing(GameState before, int me) {
        Rules.updateConnections(sim, pathing);
        Rules.incomePerTurn(before, inc0);
        Rules.incomePerTurn(sim, inc1);
        return inc1[me] - inc1[1 - me] >= inc0[me] - inc0[1 - me];
    }

    /** Best cheap cell next to our network; -1 when no safe extension fits the paint left. */
    private int chooseIdleExtension(GameState s, int me, int budgetNow) {
        double bestValue = 1e-9;
        int best = -1;
        for (int c = 0; c < s.size; c++) {
            if (!s.canBuild(c) || s.placeCost(c) > budgetNow) continue;
            int region = s.regionId[c];
            boolean townRegion = s.regionHasTown(region);
            if (!townRegion && s.regionInstability[region] >= 2) continue;
            int x = s.x(c), y = s.y(c), ownAdjacent = 0, townAdjacent = 0;
            for (int d = 0; d < 4; d++) {
                int nx = x + GameState.DX[d], ny = y + GameState.DY[d];
                if (!s.inBounds(nx, ny)) continue;
                int n = s.cell(nx, ny);
                if (s.owner[n] == me) ownAdjacent++;
                if (s.isTown(n)) townAdjacent++;
            }
            if (ownAdjacent == 0 && townAdjacent == 0) continue;
            double value = ownAdjacent * 10.0 + townAdjacent * 6.0 + (townRegion ? 4.0 : 0.0)
                    - s.regionInstability[region] * 3.0 - s.placeCost(c) * 2.0;
            // Stable deterministic tie-break that differs between players without consuming the
            // candidate-evaluation RNG used by the main search.
            value += ((c * 31 + me * 17 + P.SEED) & 255) * 1e-6;
            if (value > bestValue) {
                if (P.GUARD_BUILD_INCOME) {
                    sim.copyFrom(s);
                    sim.owner[c] = me;
                    if (!buildIncomeNonLosing(s, me)) continue;
                }
                bestValue = value;
                best = c;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ disrupt

    private int chooseDisrupt(GameState s, int myId) {
        int opp = 1 - myId;
        Rules.incomePerTurn(s, inc0);
        int turnsLeft = s.turnsLeft();
        double bestValue = P.DISRUPT_MIN;
        int best = -1;
        int[] oppTracks = new int[s.regionCount], myTracks = new int[s.regionCount];
        double[] oppRoute = new double[s.regionCount], myPlan = new double[s.regionCount];
        for (int c = 0; c < s.size; c++) {
            if (s.owner[c] == opp) oppTracks[s.regionId[c]]++;
            else if (s.owner[c] == myId) myTracks[s.regionId[c]]++;
            if (myRoute[c]) myPlan[s.regionId[c]] += 1;
        }
        for (int i = 0; i < oppLen; i++) {
            int c = oppPath[i];
            if (s.canBuild(c)) oppRoute[s.regionId[c]] += 1;
        }
        double fallbackBest = 0;
        int fallback = -1;
        for (int r = 0; r < s.regionCount && !timer.up(); r++) {
            if (s.regionInked[r] || s.regionHasTown(r)) continue;
            int needed = Math.max(1, GameState.INK_THRESHOLD - s.regionInstability[r]);
            int earning = turnsLeft - needed;
            if (earning <= 0) continue;
            double balance = P.W_FUTURE_TRACK * oppTracks[r] + P.W_OPP_ROUTE * oppRoute[r]
                    - P.W_FUTURE_OWN * myTracks[r] - P.W_FUTURE_OWN * myPlan[r];
            if (oppTracks[r] == 0 && myTracks[r] == 0) {
                double fb = balance / needed;
                if (fb > fallbackBest) {
                    fallbackBest = fb;
                    fallback = r;
                }
                continue;
            }
            sim.copyFrom(s);
            Rules.ink(sim, r);
            Rules.updateConnections(sim, pathing);
            Rules.incomePerTurn(sim, inc1);
            double oppLoss = inc0[opp] - inc1[opp];
            double myLoss = inc0[myId] - inc1[myId];
            double value;
            if (P.USE_CLOSE && needed == 1 && !Rules.anyPairPossible(sim, pathing)) {
                // this disrupt ends the game after this turn's scoring (with the post-ink connections)
                double lead = (s.score[myId] + inc1[myId]) - (s.score[opp] + inc1[opp]);
                if (lead <= 0) continue;
                value = 1e6 + lead;
            } else {
                value = (oppLoss - P.W_SELF_LOSS * myLoss) * earning + balance;
                value /= needed;
            }
            value *= noise();
            if (value > bestValue) {
                bestValue = value;
                best = r;
            }
            if (value > fallbackBest && value > 0) {
                fallbackBest = value;
                fallback = r;
            }
        }
        if (best < 0 && P.ALWAYS_DISRUPT) return fallback;
        return best;
    }
}
