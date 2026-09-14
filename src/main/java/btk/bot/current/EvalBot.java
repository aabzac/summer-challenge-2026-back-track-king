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

    private static final int MAX_BUILD_BEAM = 4;
    private static final int MAX_OPP_REPLIES = 2;
    private static final int OWN_BEAM_RESERVE_MS = 14;
    private static final int OPP_BEAM_RESERVE_MS = 10;
    private static final int OPP_DISRUPT_RESERVE_MS = 8;

    private final Params P;
    private final Timer timer = new Timer();
    private Pathing pathing;
    private GameState sim, sim2;
    private int[] pathBuf, bestPath, extraCost, oppPath;
    private int[] oppTracks, myTracks;
    private double[] oppRouteByRegion, myPlanByRegion;
    private int[][] buildBeamPath, replyBeamPath, buildBeamPrefix, replyBeamPrefix;
    private int[] buildBeamLen, buildBeamCost, buildBeamMaxInst, buildBeamPrefixLen;
    private int[] replyBeamLen, replyBeamCost, replyBeamMaxInst, replyBeamPrefixLen;
    private int[] replyBaselineScoreDiff;
    private int[] replyDisrupt;
    private double[] buildBeamValue, replyBeamValue, replyBaselineValue;
    private int[] prefixBuf;
    private boolean[] blocked, contested, myRoute, regionMark;
    private final int[] inc0 = new int[2], inc1 = new int[2], incA = new int[2], incB = new int[2];
    private int bestLen, bestCost, oppLen, worstRegion;
    private boolean disruptSearchComplete;
    private GameState replyState, completionState;
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
            oppTracks = new int[s.regionCount];
            myTracks = new int[s.regionCount];
            oppRouteByRegion = new double[s.regionCount];
            myPlanByRegion = new double[s.regionCount];
            buildBeamPath = new int[MAX_BUILD_BEAM][s.size];
            replyBeamPath = new int[MAX_OPP_REPLIES][s.size];
            buildBeamPrefix = new int[MAX_BUILD_BEAM][GameState.PAINT_PER_TURN];
            replyBeamPrefix = new int[MAX_OPP_REPLIES][GameState.PAINT_PER_TURN];
            buildBeamLen = new int[MAX_BUILD_BEAM];
            buildBeamCost = new int[MAX_BUILD_BEAM];
            buildBeamMaxInst = new int[MAX_BUILD_BEAM];
            buildBeamPrefixLen = new int[MAX_BUILD_BEAM];
            replyBeamLen = new int[MAX_OPP_REPLIES];
            replyBeamCost = new int[MAX_OPP_REPLIES];
            replyBeamMaxInst = new int[MAX_OPP_REPLIES];
            replyBeamPrefixLen = new int[MAX_OPP_REPLIES];
            replyBaselineScoreDiff = new int[MAX_OPP_REPLIES];
            replyDisrupt = new int[MAX_OPP_REPLIES];
            buildBeamValue = new double[MAX_BUILD_BEAM];
            replyBeamValue = new double[MAX_OPP_REPLIES];
            replyBaselineValue = new double[MAX_OPP_REPLIES];
            prefixBuf = new int[GameState.PAINT_PER_TURN];
            sim = s.copy();
            sim2 = s.copy();
            replyState = s.copy();
            completionState = s.copy();
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

        // A cheap, deterministic fallback is saved before route search can consume the turn.
        // Only regions that one DISRUPT can ink are considered; normal post-build selection below
        // remains authoritative whenever it completes.
        int earlyDisrupt = P.USE_DISRUPT && P.EARLY_DISRUPT_FALLBACK
                ? chooseEarlyDisrupt(s, myId) : -1;

        // --- build ---
        int budget = GameState.PAINT_PER_TURN;
        int guard = 0;
        boolean builtAny = false;
        boolean replyRerankPending = P.USE_REPLY_RERANK;
        while (budget > 0 && guard++ < 6 && !buildTimeUp()) {
            boolean found = replyRerankPending
                    ? chooseCandidateWithReplies(s, myId, budget)
                    : chooseCandidate(s, myId, budget);
            replyRerankPending = false;
            if (!found) break;
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
                    builtAny = true;
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
                builtAny = true;
                extended = true;
            }
            if (extended) Rules.updateConnections(s, pathing);
        }

        // --- disrupt ---
        if (P.USE_DISRUPT) {
            int safeEarly = earlyDisrupt;
            if (safeEarly >= 0 && builtAny && !earlyDisruptStillGood(s, myId, safeEarly)) safeEarly = -1;
            int region = chooseDisrupt(s, myId);
            if (region < 0 && !disruptSearchComplete) region = safeEarly;
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

    /** Keeps several strong prefixes, then reranks them against likely simultaneous replies. */
    private boolean chooseCandidateWithReplies(GameState s, int me, int budgetNow) {
        int buildLimit = Math.max(1, Math.min(MAX_BUILD_BEAM, P.BUILD_BEAM_WIDTH));
        int buildCount = collectCandidates(s, me, budgetNow, buildLimit, OWN_BEAM_RESERVE_MS, true,
                buildBeamPath, buildBeamLen, buildBeamCost, buildBeamMaxInst,
                buildBeamPrefix, buildBeamPrefixLen, buildBeamValue);
        if (buildCount == 0) return false;
        copyBuildCandidateToBest(0);
        if (timer.up(OPP_BEAM_RESERVE_MS)) return true;

        int replyLimit = Math.max(1, Math.min(MAX_OPP_REPLIES, P.OPP_REPLY_WIDTH));
        int opp = 1 - me;
        int replyCount = collectCandidates(s, opp, GameState.PAINT_PER_TURN, replyLimit,
                OPP_BEAM_RESERVE_MS, false, replyBeamPath, replyBeamLen, replyBeamCost,
                replyBeamMaxInst, replyBeamPrefix, replyBeamPrefixLen, replyBeamValue);
        if (replyCount == 0) {
            replyBeamLen[0] = 0;
            replyBeamPrefixLen[0] = 0;
            replyCount = 1;
        } else if (replyCount < replyLimit) {
            // Keep a no-build alternative when only one plausible route survived the filter.
            replyBeamLen[replyCount] = 0;
            replyBeamPrefixLen[replyCount] = 0;
            replyCount++;
        }
        for (int j = 0; j < replyCount; j++) {
            replyDisrupt[j] = predictDisrupt(s, opp, replyBeamPath[j], replyBeamLen[j],
                    OPP_DISRUPT_RESERVE_MS);
        }

        int usableReplies = 0;
        for (int j = 0; j < replyCount && !buildTimeUp(); j++) {
            simulatePredictedTurn(s, me, null, 0, replyBeamPrefix[j], replyBeamPrefixLen[j],
                    replyDisrupt[j], replyState);
            replyBaselineValue[j] = evalState(replyState, me);
            replyBaselineScoreDiff[j] = replyState.score[me] - replyState.score[opp];
            usableReplies++;
        }
        if (usableReplies == 0) return true;

        int selected = 0;
        double selectedWorst = Double.NEGATIVE_INFINITY;
        boolean evaluatedAny = false;
        for (int i = 0; i < buildCount && !buildTimeUp(); i++) {
            double worst = Double.POSITIVE_INFINITY;
            int evaluated = 0;
            for (int j = 0; j < usableReplies && !buildTimeUp(); j++) {
                double value = replyAwareValue(s, me, i, j, replyDisrupt[j]);
                if (value < worst) worst = value;
                evaluated++;
            }
            if (evaluated != usableReplies) break;
            if (!evaluatedAny || worst > selectedWorst) {
                evaluatedAny = true;
                selectedWorst = worst;
                selected = i;
            }
        }
        if (evaluatedAny) copyBuildCandidateToBest(selected);
        return true;
    }

    /** Candidate enumeration equivalent to the normal route scorer, retaining distinct prefixes. */
    private int collectCandidates(GameState s, int me, int budgetNow, int limit, int reserveMs,
                                  boolean noisy, int[][] paths, int[] lens, int[] costs,
                                  int[] maxInstability, int[][] prefixes, int[] prefixLens,
                                  double[] values) {
        if (timer.up(reserveMs)) return 0;
        double base = evalState(s, me);
        int threatened = worstRegion;
        int count = 0;
        int opp = 1 - me;
        for (int c = 0; c < s.size; c++) blocked[c] = s.owner[c] == opp;
        int turnsLeft = s.turnsLeft();
        for (int p = 0; p < s.pairCount && !timer.up(reserveMs); p++) {
            int from = s.townCell[s.pairFrom[p]], to = s.townCell[s.pairTo[p]];
            for (int variant = 0; variant < 3 && !timer.up(reserveMs); variant++) {
                if (variant == 1 && !P.USE_STEAL) continue;
                boolean[] blk = null;
                if (variant == 1) {
                    blk = blocked;
                } else if (variant == 2) {
                    if (!P.USE_BACKUP || threatened < 0 || !s.pairActive(p)) break;
                    boolean crosses = false;
                    for (int i = 0; i < s.pairPathLen[p] && !crosses; i++) {
                        crosses = s.regionId[s.pairPath[p][i]] == threatened;
                    }
                    if (!crosses) break;
                    for (int c = 0; c < s.size; c++) blocked[c] = s.regionId[c] == threatened;
                    blk = blocked;
                }
                int n = pathing.cheapestPath(s, from, to, pathBuf, blk, extraCost);
                if (variant == 2) {
                    for (int c = 0; c < s.size; c++) blocked[c] = s.owner[c] == opp;
                }
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
                double value = (gain * earning * risk - P.W_COST * paint) / (turnsToFinish + 1);
                if (noisy) value *= noise();
                int prefixLen = fillAffordablePrefix(s, budgetNow, pathBuf, n, prefixBuf);
                if (prefixLen == 0) continue;
                count = insertCandidate(limit, count, value, paint, maxInst, pathBuf, n,
                        prefixBuf, prefixLen, paths, lens, costs, maxInstability,
                        prefixes, prefixLens, values);
            }
        }
        return count;
    }

    private int fillAffordablePrefix(GameState s, int budget, int[] route, int len, int[] out) {
        int count = 0;
        for (int pass = 0; pass < 2 && budget > 0; pass++) {
            for (int i = 0; i < len && budget > 0; i++) {
                int c = route[i];
                if (!s.canBuild(c) || contested[c] != (pass == 1)) continue;
                int cost = s.placeCost(c);
                if (cost > budget) continue;
                budget -= cost;
                out[count++] = c;
            }
        }
        return count;
    }

    private int insertCandidate(int limit, int count, double value, int cost, int maxInst,
                                int[] route, int routeLen, int[] prefix, int prefixLen,
                                int[][] paths, int[] lens, int[] costs, int[] maxInstability,
                                int[][] prefixes, int[] prefixLens, double[] values) {
        for (int i = 0; i < count; i++) {
            if (!samePrefix(prefixes[i], prefixLens[i], prefix, prefixLen)) continue;
            if (value <= values[i]) return count;
            for (int j = i; j + 1 < count; j++) {
                copyCandidateSlot(j + 1, j, paths, lens, costs, maxInstability,
                        prefixes, prefixLens, values);
            }
            count--;
            break;
        }
        int position = 0;
        while (position < count && values[position] >= value) position++;
        if (position >= limit) return count;
        int newCount = Math.min(limit, count + 1);
        for (int i = newCount - 1; i > position; i--) {
            copyCandidateSlot(i - 1, i, paths, lens, costs, maxInstability,
                    prefixes, prefixLens, values);
        }
        System.arraycopy(route, 0, paths[position], 0, routeLen);
        System.arraycopy(prefix, 0, prefixes[position], 0, prefixLen);
        lens[position] = routeLen;
        costs[position] = cost;
        maxInstability[position] = maxInst;
        prefixLens[position] = prefixLen;
        values[position] = value;
        return newCount;
    }

    private static boolean samePrefix(int[] a, int an, int[] b, int bn) {
        if (an != bn) return false;
        for (int i = 0; i < an; i++) if (a[i] != b[i]) return false;
        return true;
    }

    private static void copyCandidateSlot(int from, int to, int[][] paths, int[] lens,
                                          int[] costs, int[] maxInstability, int[][] prefixes,
                                          int[] prefixLens, double[] values) {
        System.arraycopy(paths[from], 0, paths[to], 0, lens[from]);
        System.arraycopy(prefixes[from], 0, prefixes[to], 0, prefixLens[from]);
        lens[to] = lens[from];
        costs[to] = costs[from];
        maxInstability[to] = maxInstability[from];
        prefixLens[to] = prefixLens[from];
        values[to] = values[from];
    }

    private void copyBuildCandidateToBest(int candidate) {
        bestLen = buildBeamLen[candidate];
        bestCost = buildBeamCost[candidate];
        System.arraycopy(buildBeamPath[candidate], 0, bestPath, 0, bestLen);
    }

    private int predictDisrupt(GameState s, int player, int[] plannedRoute, int plannedLen,
                               int reserveMs) {
        prepareDisruptData(s, player);
        for (int i = 0; i < plannedLen; i++) {
            int c = plannedRoute[i];
            if (s.canBuild(c)) myPlanByRegion[s.regionId[c]] += 1;
        }
        double bestValue = P.DISRUPT_MIN;
        int best = -1, fallback = -1;
        double fallbackBest = 0;
        for (int r = 0; r < s.regionCount && !timer.up(reserveMs); r++) {
            if (s.regionInked[r] || s.regionHasTown(r)) continue;
            int needed = Math.max(1, GameState.INK_THRESHOLD - s.regionInstability[r]);
            int earning = s.turnsLeft() - needed;
            if (earning <= 0) continue;
            double balance = P.W_FUTURE_TRACK * oppTracks[r] + P.W_OPP_ROUTE * oppRouteByRegion[r]
                    - P.W_FUTURE_OWN * myTracks[r];
            if (oppTracks[r] == 0 && myTracks[r] == 0) continue;
            double value = disruptValue(s, player, r, false);
            if (value > bestValue) {
                bestValue = value;
                best = r;
            }
            if (value > fallbackBest && value > 0) {
                fallbackBest = value;
                fallback = r;
            }
        }
        return best >= 0 ? best : P.ALWAYS_DISRUPT ? fallback : -1;
    }

    private double replyAwareValue(GameState s, int me, int buildCandidate, int reply,
                                   int replyDisrupt) {
        simulatePredictedTurn(s, me, buildBeamPrefix[buildCandidate],
                buildBeamPrefixLen[buildCandidate], replyBeamPrefix[reply],
                replyBeamPrefixLen[reply], replyDisrupt, sim);
        int opp = 1 - me;
        double immediate = (sim.score[me] - sim.score[opp])
                - replyBaselineScoreDiff[reply];

        completionState.copyFrom(sim);
        int remainingPaint = 0, maxInst = buildBeamMaxInst[buildCandidate];
        for (int i = 0; i < buildBeamLen[buildCandidate]; i++) {
            int c = buildBeamPath[buildCandidate][i];
            if (completionState.inked[c]) return Double.NEGATIVE_INFINITY;
            if (completionState.canBuild(c)) {
                remainingPaint += completionState.placeCost(c);
                maxInst = Math.max(maxInst,
                        completionState.regionInstability[completionState.regionId[c]]);
                completionState.owner[c] = me;
            }
        }
        Rules.updateConnections(completionState, pathing);
        double gain = evalState(completionState, me) - replyBaselineValue[reply];
        int turnsToFinish = (remainingPaint + GameState.PAINT_PER_TURN - 1) / GameState.PAINT_PER_TURN;
        int earning = Math.min(sim.turnsLeft(), P.HORIZON) - turnsToFinish;
        if (earning <= 0) return immediate;
        double risk = Math.max(0, 1 - P.W_RISK * maxInst / GameState.INK_THRESHOLD);
        return immediate + (gain * earning * risk - P.W_COST * remainingPaint) / (turnsToFinish + 1);
    }

    private void simulatePredictedTurn(GameState before, int me, int[] ownCells, int ownCount,
                                       int[] replyCells, int replyCount, int replyDisrupt,
                                       GameState target) {
        target.copyFrom(before);
        int[] empty = prefixBuf;
        int[] mine = ownCells == null ? empty : ownCells;
        if (me == 0) Rules.applyPlacements(target, mine, ownCount, replyCells, replyCount);
        else Rules.applyPlacements(target, replyCells, replyCount, mine, ownCount);
        if (replyDisrupt >= 0) Rules.disrupt(target, replyDisrupt);
        Rules.inkUnstable(target);
        Rules.updateConnections(target, pathing);
        Rules.scoreTurn(target, null);
        target.turn++;
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

    /** Selects a deterministic, immediately effective fallback without consuming strategy RNG. */
    private int chooseEarlyDisrupt(GameState s, int myId) {
        prepareDisruptData(s, myId);
        double bestValue = P.DISRUPT_MIN;
        int best = -1;
        for (int r = 0; r < s.regionCount; r++) {
            if (s.regionInstability[r] != GameState.INK_THRESHOLD - 1) continue;
            if (oppTracks[r] == 0 && myTracks[r] == 0) continue;
            double value = disruptValue(s, myId, r, false);
            if (value > bestValue) {
                bestValue = value;
                best = r;
            }
        }
        return best;
    }

    /** Placements can change income and whether this ink would end the game, so score it again. */
    private boolean earlyDisruptStillGood(GameState s, int myId, int region) {
        prepareDisruptData(s, myId);
        return disruptValue(s, myId, region, false) > P.DISRUPT_MIN;
    }

    private void prepareDisruptData(GameState s, int myId) {
        int opp = 1 - myId;
        Rules.incomePerTurn(s, inc0);
        java.util.Arrays.fill(oppTracks, 0);
        java.util.Arrays.fill(myTracks, 0);
        java.util.Arrays.fill(oppRouteByRegion, 0);
        java.util.Arrays.fill(myPlanByRegion, 0);
        for (int c = 0; c < s.size; c++) {
            int region = s.regionId[c];
            if (s.owner[c] == opp) oppTracks[region]++;
            else if (s.owner[c] == myId) myTracks[region]++;
            if (myRoute[c]) myPlanByRegion[region] += 1;
        }
        for (int i = 0; i < oppLen; i++) {
            int c = oppPath[i];
            if (s.canBuild(c)) oppRouteByRegion[s.regionId[c]] += 1;
        }
    }

    private double disruptValue(GameState s, int myId, int region, boolean noisy) {
        if (region < 0 || region >= s.regionCount || s.regionInked[region] || s.regionHasTown(region)) {
            return Double.NEGATIVE_INFINITY;
        }
        int needed = Math.max(1, GameState.INK_THRESHOLD - s.regionInstability[region]);
        int earning = s.turnsLeft() - needed;
        if (earning <= 0) return Double.NEGATIVE_INFINITY;
        double balance = P.W_FUTURE_TRACK * oppTracks[region] + P.W_OPP_ROUTE * oppRouteByRegion[region]
                - P.W_FUTURE_OWN * myTracks[region] - P.W_FUTURE_OWN * myPlanByRegion[region];
        sim.copyFrom(s);
        Rules.ink(sim, region);
        Rules.updateConnections(sim, pathing);
        Rules.incomePerTurn(sim, inc1);
        int opp = 1 - myId;
        double oppLoss = inc0[opp] - inc1[opp];
        double myLoss = inc0[myId] - inc1[myId];
        double value;
        if (P.USE_CLOSE && needed == 1 && !Rules.anyPairPossible(sim, pathing)) {
            double lead = (s.score[myId] + inc1[myId]) - (s.score[opp] + inc1[opp]);
            if (lead <= 0) return Double.NEGATIVE_INFINITY;
            value = 1e6 + lead;
        } else {
            value = ((oppLoss - P.W_SELF_LOSS * myLoss) * earning + balance) / needed;
        }
        return noisy ? value * noise() : value;
    }

    private int chooseDisrupt(GameState s, int myId) {
        prepareDisruptData(s, myId);
        int turnsLeft = s.turnsLeft();
        double bestValue = P.DISRUPT_MIN;
        int best = -1;
        double fallbackBest = 0;
        int fallback = -1;
        int r = 0;
        for (; r < s.regionCount && !timer.up(); r++) {
            if (s.regionInked[r] || s.regionHasTown(r)) continue;
            int needed = Math.max(1, GameState.INK_THRESHOLD - s.regionInstability[r]);
            int earning = turnsLeft - needed;
            if (earning <= 0) continue;
            double balance = P.W_FUTURE_TRACK * oppTracks[r] + P.W_OPP_ROUTE * oppRouteByRegion[r]
                    - P.W_FUTURE_OWN * myTracks[r] - P.W_FUTURE_OWN * myPlanByRegion[r];
            if (oppTracks[r] == 0 && myTracks[r] == 0) {
                double fb = balance / needed;
                if (fb > fallbackBest) {
                    fallbackBest = fb;
                    fallback = r;
                }
                continue;
            }
            double value = disruptValue(s, myId, r, true);
            if (value > bestValue) {
                bestValue = value;
                best = r;
            }
            if (value > fallbackBest && value > 0) {
                fallbackBest = value;
                fallback = r;
            }
        }
        disruptSearchComplete = r == s.regionCount;
        if (best < 0 && P.ALWAYS_DISRUPT) return fallback;
        return best;
    }
}
