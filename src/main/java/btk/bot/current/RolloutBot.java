package btk.bot.current;

import btk.bot.Bot;
import btk.bot.Params;
import btk.bot.Timer;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.NetworkCells;
import btk.core.Rules;
import btk.core.SearchState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** Independent portfolio rollouts: score affordable, simultaneous turns instead of a finished route. */
public final class RolloutBot implements Bot {
    private final Params p;
    private final Timer timer = new Timer();
    private Pathing pathing;
    private SearchState work, completion, targetBoard, inkBoard;
    private int[][][] routes;
    private int[][] lengths;
    private double[][] values;
    private final int[] counts = new int[2], income = new int[2];
    private final int[] baseDiff = new int[2], threatDiff = new int[2], threats = new int[2];
    private final int[][] moves = new int[2][GameState.PAINT_PER_TURN];
    private final int[] moveCounts = new int[2], targets = new int[2];
    private int[] path, extra, trackBalance, shortlist, bestCells, prefix;
    private int realPlayer;
    private boolean[] blocked, marked;
    private double[] priority;
    private int[][] roots;
    private int[] rootCounts, rootTargets;
    private Random random;
    private int[] quickPath;
    private int quickPlayer, quickLength, quickCost, quickGain;
    public int routeQueries, completedComparisons, simulatedTurns, selectedRoute, selectedTarget;
    public double selectedValue;
    private int ordinaryRootSize;
    private int[] networkCells, networkWeights, networkPriority, networkGains, networkBest;
    private boolean[] networkCompared;
    public int networkEligible, networkEvaluated, networkRoots, networkComparisons;
    public boolean selectedNetwork;
    public int selectedNetworkCell = -1;
    public boolean networkPhaseComplete;
    private int bestCount;
    private double best;
    public boolean ordinarySearchComplete, networkSearchStarted;
    public int ordinaryComparisons, ordinarySelectedRoute, ordinarySelectedTarget, ordinarySelectedCount;
    public double ordinarySelectedValue;
    public final int[] ordinarySelectedCells = new int[GameState.PAINT_PER_TURN];
    private ProtectedOpening opening;
    private int openingPlayer;
    public boolean selectedOpening;
    public int openingCount, openingPairs, openingEligible, openingPaint;
    // MISSED_DEADLINE_BEGIN
    private int deadlinePendingTarget = -1, deadlinePendingDue = -1;
    private int deadlineLastTurn = -1, deadlineSeat = -1;
    private int[] deadlineSavedRoot;
    private boolean deadlineProbeActive;
    private int deadlineSuppressedRegion = -1;
    private int deadlineProbeFirstTarget = -1, deadlineProbeFirstDue = -1, deadlineProbeTerminal;
    public String deadlineStatus = "disabled";
    public int deadlineNativeTarget = -1, deadlineNativeCount, deadlineRoot = -1, deadlineNominee = -1;
    public int deadlineReplies, deadlineInterventionTarget = -1, deadlineExtraTurns;
    public int deadlineReusedBoards;
    public int deadlineCommittedTarget = -1, deadlineCommittedDue = -1;
    public int deadlineWCompleted, deadlineACompleted, deadlineWr0Completed;
    public boolean deadlineChanged;
    public int[] deadlineNativeCells, deadlineWTerminal, deadlineATerminal, deadlineWr0Terminal;
    public int[] deadlineFirstTarget, deadlineFirstDue;
    public double[] deadlineWValues, deadlineAValues, deadlineWr0Values;
    // MISSED_DEADLINE_END
    public RolloutBot(Params params) {
        p = params.copy();
        // LOCAL_ONLY_BEGIN
        // prune-v1 (2026-10-25): the rejected rollout experiments left current together with their Params
        // fields in the artifact; frozen v10 still runs every one of them ("v10?FLAG=1"). RHEA left in v09.
        if (p.USE_ROLLOUT_RHEA || p.USE_ROLLOUT_ALL_CELL_RISK || p.USE_ROLLOUT_ROOT_FILL || p.USE_ROLLOUT_QUICK_EARLY
                || p.USE_ROLLOUT_QUICK_VALUE || p.USE_ROLLOUT_QUICK_SAFE || p.USE_ROLLOUT_DIVERSE_REPLIES
                || p.USE_ROLLOUT_REPLAN || p.USE_ROLLOUT_SECOND_TARGET || p.USE_ROLLOUT_SPREAD || p.USE_ROLLOUT_NETWORK
                || p.USE_ROLLOUT_ROUTE_DEPENDENCIES || p.USE_ROLLOUT_NETWORK_CHORDS || p.USE_ROLLOUT_LIVE_TARGET
                || p.ROLLOUT_ALWAYS_DISRUPT || p.USE_ROLLOUT_TRACK_VALUE || p.ROLLOUT_TRACK_VALUE_SELF_ONLY)
            throw new IllegalArgumentException("Pruned from current (prune-v1): run this rollout experiment on v10");
        if (p.USE_OWN_BUILD_PLAN || p.USE_REPLY_RERANK || p.USE_JOINT_ACTION
                || p.USE_DEFENSE_PRIORITY || p.USE_OPP_MODEL || p.GUARD_BUILD_INCOME
                || p.CHOOSE_BUILD_DIRECTION || p.EARLY_DISRUPT_FALLBACK || p.INTERRUPT_VULN_EVAL)
            throw new IllegalArgumentException("Rollout requires an independent policy");
        // LOCAL_ONLY_END
        if (p.USE_HEURISTIC_POLICY
                || p.ROLLOUT_WIDTH < 1 || p.ROLLOUT_WIDTH > 8 || p.ROLLOUT_REPLIES < 1
                || p.ROLLOUT_REPLIES > p.ROLLOUT_WIDTH || p.ROLLOUT_DEPTH < 1 || p.ROLLOUT_DEPTH > 12
                || p.ROLLOUT_MAX_ROUTES < 1 || p.ROLLOUT_MAX_ROUTES > 128
                || p.ROLLOUT_QUICK_MAX_PAINT < 1 || p.ROLLOUT_QUICK_MAX_PAINT > 12
                || p.ROLLOUT_NETWORK_MAX_CELLS < 1 || p.ROLLOUT_NETWORK_MAX_CELLS > 64
                || p.ROLLOUT_NETWORK_ROOTS < 1 || p.ROLLOUT_NETWORK_ROOTS > 2
                || p.ROLLOUT_NETWORK_RESERVE_MS < 0 || p.ROLLOUT_NETWORK_RESERVE_MS > 36
                || p.OPENING_TURNS < 1 || p.OPENING_TURNS > 3
                || p.OPENING_CITY_QUOTA < 1 || p.OPENING_CITY_QUOTA > 3
                || !unit(p.OPENING_COLLISION_FACTOR)
                || p.USE_PROTECTED_OPENING && (!p.USE_ROLLOUT_NETWORK_AFTER_BASE || !p.USE_ROLLOUT_QUICK_ROUTE)
                || p.ROLLOUT_RESERVE_MS < 0 || p.ROLLOUT_TARGETS < 1 || p.ROLLOUT_TARGETS > 8
                || !finitePositive(p.ROLLOUT_TARGET_HORIZON) || !finitePositive(p.ROLLOUT_ROUTE_HORIZON)
                || !finitePositive(p.ROLLOUT_WIN_VALUE)
                || !unit(p.ROLLOUT_ROBUSTNESS) || !unit(p.ROLLOUT_THREAT_WEIGHT)
                || !Double.isFinite(p.ROLLOUT_TAIL) || p.ROLLOUT_TAIL < 0
                || !Double.isFinite(p.ROLLOUT_TRACK_WEIGHT) || p.ROLLOUT_TRACK_WEIGHT < 0) {
            throw new IllegalArgumentException("Rollout requires an independent policy and bounded valid parameters");
        }
    }

    private static boolean finitePositive(double v) { return Double.isFinite(v) && v > 0; }
    private static boolean unit(double v) { return Double.isFinite(v) && v >= 0 && v <= 1; }
    public String name() { return "current?" + p.diff(); }

    // BOSS_SABOTAGE_BEGIN
    private BossSabotage bossSabotage;
    private int bossTarget = -1;
    // BOSS_SABOTAGE_END
    // BOSS_LITE_BEGIN
    /** Two-stage boss chooser (docs/stage1-opponent-model-v1.md): fitted logit weights without the route terms. */
    private static final double LITE_D_OPP = 0.137316, LITE_D_OWN = -0.111814, LITE_OPP_CELLS = 0.775046,
            LITE_OPP_ACTIVE = -0.003927, LITE_OWN_CELLS = -0.282833, LITE_OPP_PREV2 = 0.291303, LITE_INSTAB = 0.884541,
            LITE_ADJ_TOWN = 0.368378, LITE_NEUTRAL = 0.155573, LITE_LOG_FREE = 0.148325;
    private int liteHeld = -1;
    private int[] litePlacedTurn, litePrevOwner, liteOppCells, liteOwnCells, liteNeutral, liteOppActive, liteOppPrev2,
            liteFree, liteShort;
    private boolean[] liteAdjTown, liteOnActive;
    private double[] liteCheap;
    private int[] litePlaced;
    // BOSS_LITE_END
    // BOSS_MODEL_BEGIN
    /** Stage 1 opponent model: the opponent's observed held target, last real instabilities, simulated placement turns. */
    private int liteOppHeld = -1, liteLastRealTarget = -1;
    private int[] liteLastInstability, liteSimPlaced;
    public int bossModelCalls;
    // BOSS_MODEL_END
    // BOSS_ROUTES_BEGIN
    /** Per player: sum over that player's previous-turn portfolio routes of 1/paint-cost, per region crossed. */
    private double[][] liteRoutes;
    // BOSS_ROUTES_END

    public void init(GameState s, int me) {
        pathing = new Pathing(s.size);
        // FAST_CHEAPEST_PATH_BEGIN
        if (p.USE_FAST_CHEAPEST_PATH) pathing.enableFastCheapestPath(s);
        // FAST_CHEAPEST_PATH_END
        // BATCHED_REFRESH_BEGIN
        if (p.USE_BATCHED_REFRESH) {
            work = new SearchState(s, true); completion = new SearchState(s, true);
            targetBoard = new SearchState(s, true); inkBoard = new SearchState(s, true);
        } else {
        // BATCHED_REFRESH_END
        work = new SearchState(s); completion = new SearchState(s);
        targetBoard = new SearchState(s); inkBoard = new SearchState(s);
        // BATCHED_REFRESH_BEGIN
        }
        // BATCHED_REFRESH_END
        routes = new int[2][p.ROLLOUT_WIDTH][s.size];
        lengths = new int[2][p.ROLLOUT_WIDTH]; values = new double[2][p.ROLLOUT_WIDTH];
        path = new int[s.size]; extra = new int[s.size]; blocked = new boolean[s.size];
        trackBalance = new int[s.regionCount]; marked = new boolean[s.regionCount];
        priority = new double[s.regionCount]; shortlist = new int[p.ROLLOUT_TARGETS];
        roots = new int[p.ROLLOUT_WIDTH + 1 + (p.USE_ROLLOUT_NETWORK_AFTER_BASE ? p.ROLLOUT_NETWORK_ROOTS : 0)][GameState.PAINT_PER_TURN];
        rootCounts = new int[roots.length]; rootTargets = new int[roots.length];
        prefix = new int[GameState.PAINT_PER_TURN]; bestCells = new int[GameState.PAINT_PER_TURN];
        random = new Random(p.SEED * 1_000_003L + me * 7919L);
        realPlayer = me;
        // MISSED_DEADLINE_BEGIN
        if (p.USE_ROLLOUT_MISSED_DEADLINE) {
            deadlineSavedRoot = new int[GameState.PAINT_PER_TURN];
            deadlineNativeCells = new int[GameState.PAINT_PER_TURN];
            deadlineWValues = new double[p.ROLLOUT_REPLIES];
            deadlineAValues = new double[p.ROLLOUT_REPLIES];
            deadlineWr0Values = new double[p.ROLLOUT_REPLIES];
            deadlineWTerminal = new int[p.ROLLOUT_REPLIES];
            deadlineATerminal = new int[p.ROLLOUT_REPLIES];
            deadlineWr0Terminal = new int[p.ROLLOUT_REPLIES];
            deadlineFirstTarget = new int[p.ROLLOUT_REPLIES];
            deadlineFirstDue = new int[p.ROLLOUT_REPLIES];
            clearDeadlineCommitment();
            deadlineLastTurn = deadlineSeat = -1;
            deadlineProbeActive = false; deadlineSuppressedRegion = -1;
            resetDeadlineDiagnostics();
        }
        // MISSED_DEADLINE_END
        if (p.USE_PROTECTED_OPENING) { opening = new ProtectedOpening(s, p); openingPlayer = me; }
        // BOSS_SABOTAGE_BEGIN
        if (p.USE_BOSS_SABOTAGE) bossSabotage = new BossSabotage(s, p.BOSS_HOLD_MARGIN);
        bossTarget = -1;
        // BOSS_SABOTAGE_END
        // BOSS_LITE_BEGIN
        liteHeld = -1;
        if (p.USE_BOSS_LITE) {
            litePlacedTurn = new int[s.size]; litePrevOwner = new int[s.size];
            Arrays.fill(litePlacedTurn, -99); Arrays.fill(litePrevOwner, GameState.NO_TRACK);
            liteOppCells = new int[s.regionCount]; liteOwnCells = new int[s.regionCount]; liteNeutral = new int[s.regionCount];
            liteOppActive = new int[s.regionCount]; liteOppPrev2 = new int[s.regionCount]; liteFree = new int[s.regionCount];
            liteShort = new int[p.ROLLOUT_TARGETS]; liteCheap = new double[s.regionCount];
            liteAdjTown = new boolean[s.regionCount]; liteOnActive = new boolean[s.size];
            litePlaced = litePlacedTurn;
            // BOSS_ROUTES_BEGIN
            if (p.USE_BOSS_LITE_ROUTES) liteRoutes = new double[2][s.regionCount];
            // BOSS_ROUTES_END
            // BOSS_MODEL_BEGIN
            liteOppHeld = -1; liteLastRealTarget = -1;
            if (p.USE_BOSS_OPPONENT_MODEL || p.USE_BOSS_OWN_MODEL) {
                liteLastInstability = s.regionInstability.clone(); liteSimPlaced = new int[s.size];
            }
            // BOSS_MODEL_END
            for (int c = 0; c < s.size; c++) {
                for (int d = 0; d < 4; d++) {
                    int x = s.x(c) + GameState.DX[d], y = s.y(c) + GameState.DY[d];
                    if (!s.inBounds(x, y)) continue;
                    int nr = s.regionId[s.cell(x, y)];
                    if (nr != s.regionId[c] && s.regionHasTown(nr)) liteAdjTown[s.regionId[c]] = true;
                }
            }
        }
        // BOSS_LITE_END
        if (p.USE_ROLLOUT_QUICK_ROUTE) quickPath = new int[s.size];
        if (p.USE_ROLLOUT_NETWORK_AFTER_BASE) {
            networkCells = new int[s.size]; networkWeights = new int[s.size];
            networkPriority = new int[s.size]; networkGains = new int[s.size];
            networkBest = new int[p.ROLLOUT_NETWORK_ROOTS]; networkCompared = new boolean[roots.length];
        }
    }

    public List<Action> play(GameState s, int me, long budgetMs) {
        timer.start(budgetMs);
        // MISSED_DEADLINE_BEGIN
        if (p.USE_ROLLOUT_MISSED_DEADLINE) {
            deadlineBegin(s.turn, me);
            if (budgetMs <= 0) deadlineReject("timeout");
        }
        // MISSED_DEADLINE_END
        routeQueries = completedComparisons = simulatedTurns = 0;
        networkEligible = networkEvaluated = networkRoots = networkComparisons = 0;
        selectedNetwork = false; selectedNetworkCell = -1;
        networkPhaseComplete = false;
        ordinarySearchComplete = networkSearchStarted = false;
        ordinaryComparisons = ordinarySelectedCount = 0;
        ordinarySelectedRoute = ordinarySelectedTarget = -1;
        ordinarySelectedValue = Double.NaN;
        Arrays.fill(ordinarySelectedCells, 0);
        selectedOpening = false; openingCount = openingPairs = openingEligible = openingPaint = 0;
        if (opening != null) opening.reset(s, me);
        selectedRoute = selectedTarget = -1; selectedValue = Double.NaN;
        quickPlayer = -1; quickLength = quickCost = quickGain = 0;
        if (budgetMs <= 0) return List.of(Action.waitAction());
        Arrays.fill(counts, 0);
        // A usable sabotage answer exists before route enumeration and all speculative work.
        int fallbackTarget = chooseTarget(s, me);
        // BOSS_SABOTAGE_BEGIN
        if (p.USE_BOSS_SABOTAGE) {
            bossSabotage.observe(s); bossTarget = bossSabotage.choose(s, me); fallbackTarget = bossTarget;
        }
        // BOSS_SABOTAGE_END
        // BOSS_LITE_BEGIN
        if (p.USE_BOSS_LITE) {
            liteObserve(s); litePlaced = litePlacedTurn;
            // BOSS_MODEL_BEGIN
            if (p.USE_BOSS_OPPONENT_MODEL || p.USE_BOSS_OWN_MODEL) liteObserveOpponent(s);
            // BOSS_MODEL_END
            liteHeld = chooseBossLite(s, me, liteHeld); fallbackTarget = liteHeld;
        }
        // BOSS_LITE_END
        threats[1 - me] = fallbackTarget;
        threats[me] = chooseTarget(s, 1 - me);
        Rules.incomePerTurn(s, income);
        if (p.USE_ROLLOUT_QUICK_ROUTE && income[me] == 0) quickPlayer = me;
        for (int player = 0; player < 2; player++) {
            baseDiff[player] = income[player] - income[1 - player];
            threatDiff[player] = baseDiff[player];
            if (threats[player] >= 0) {
                completion.set(s); Rules.ink(completion.state, threats[player]); completion.refresh();
                Rules.incomePerTurn(completion.state, income);
                threatDiff[player] = income[player] - income[1 - player];
                Rules.incomePerTurn(s, income);
            }
        }
        // Interleave both players so a deadline cannot spend the whole budget on one portfolio.
        int start = s.pairCount == 0 ? 0 : (s.turn * 7 + me * 13) % s.pairCount;
        int reserve = (int) Math.min(p.ROLLOUT_RESERVE_MS, budgetMs / 2);
        for (int k = 0; k < s.pairCount && routeQueries < 2 * p.ROLLOUT_MAX_ROUTES; k++) {
            int pair = (start + k) % s.pairCount;
            for (int variant = 0; variant < 3; variant++) {
                if (timer.up(reserve) || routeQueries >= 2 * p.ROLLOUT_MAX_ROUTES) break;
                propose(s, me, pair, variant);
                if (!timer.up(reserve)) propose(s, 1 - me, pair, variant);
            }
            if (timer.up(reserve)) break;
        }
        if (quickLength > 0) retainQuickRoute(s, me);
        if (opening != null) openingPairs = opening.observedPairs;
        if (opening != null && s.turn < p.OPENING_TURNS && !timer.up()) {
            int predicted = fillBuild(s, 1 - me, 0, moves[1 - me]);
            opening.markOpponentPrefix(moves[1 - me], predicted);
            openingCount = opening.build(s, bestCells);
            openingEligible = opening.eligibleCells; openingPaint = opening.selectedPaint;
            if (openingCount > 0) {
                selectedOpening = true; bestCount = openingCount;
                targetBoard.set(s); placeOwn(targetBoard, me, bestCells, bestCount);
                selectedTarget = timer.up() ? fallbackTarget : chooseTarget(targetBoard.state, me);
                return actions(s);
            }
        }
        int rootSize = counts[me] + 1; // last choice waits for construction this turn only
        ordinaryRootSize = rootSize;
        for (int i = 0; i < rootSize; i++) {
            rootCounts[i] = i == counts[me] ? 0 : fillBuild(s, me, i, roots[i]);
            targetBoard.set(s);
            placeOwn(targetBoard, me, roots[i], rootCounts[i]);
            rootTargets[i] = timer.up() ? fallbackTarget : chooseTarget(targetBoard.state, me);
        }
        bestCount = rootCounts[0];
        System.arraycopy(roots[0], 0, bestCells, 0, bestCount);
        selectedTarget = rootTargets[0];
        selectedRoute = counts[me] == 0 ? -1 : 0;
        best = -Double.MAX_VALUE;
        int replies = Math.max(1, Math.min(p.ROLLOUT_REPLIES, counts[1 - me]));
        boolean complete = searchRoots(s, me, 0, rootSize, replies);
        saveOrdinaryChoice(complete);
        if (p.USE_ROLLOUT_NETWORK_AFTER_BASE) searchNetworkAfterBase(s, me, replies, budgetMs);
        return actions(s);
    }

    private List<Action> actions(GameState s) {
        // BOSS_SABOTAGE_BEGIN
        if (p.USE_BOSS_SABOTAGE) selectedTarget = bossTarget;
        // BOSS_SABOTAGE_END
        // BOSS_LITE_BEGIN
        if (p.USE_BOSS_LITE) selectedTarget = liteHeld;
        // BOSS_LITE_END
        // BOSS_ROUTES_BEGIN
        if (p.USE_BOSS_LITE_ROUTES) liteRecordRoutes(s);
        // BOSS_ROUTES_END
        // BOSS_MODEL_BEGIN
        if (p.USE_BOSS_LITE) liteLastRealTarget = selectedTarget;
        // BOSS_MODEL_END
        if (p.USE_ROLLOUT_FILL) fill(s, realPlayer);
        // MISSED_DEADLINE_BEGIN
        if (p.USE_ROLLOUT_MISSED_DEADLINE) correctMissedDeadline(s);
        // MISSED_DEADLINE_END
        List<Action> result = new ArrayList<>(4);
        for (int i = 0; i < bestCount; i++) result.add(Action.place(s.x(bestCells[i]), s.y(bestCells[i])));
        if (selectedTarget >= 0) result.add(Action.disrupt(selectedTarget));
        if (result.isEmpty()) result.add(Action.waitAction());
        return result;
    }

    // MISSED_DEADLINE_BEGIN
    public int deadlinePendingTarget() { return deadlinePendingTarget; }
    public int deadlinePendingDue() { return deadlinePendingDue; }

    /** Synthetic warm-up decisions must never establish or spend a live commitment. */
    @Override public void warmup(GameState s, int me, long ms) {
        if (!p.USE_ROLLOUT_MISSED_DEADLINE) { Bot.super.warmup(s, me, ms); return; }
        if (ms <= 0) return;
        RolloutBot disposable = new RolloutBot(p);
        disposable.init(s, me);
        disposable.warmupDiscard(s, me, ms);
    }

    private void warmupDiscard(GameState s, int me, long ms) { Bot.super.warmup(s, me, ms); }

    void clearDeadlineCommitment() { deadlinePendingTarget = deadlinePendingDue = -1; }

    void resetDeadlineDiagnostics() {
        deadlineStatus = "not-evaluated";
        deadlineNativeTarget = deadlineRoot = deadlineNominee = deadlineInterventionTarget = -1;
        deadlineCommittedTarget = deadlineCommittedDue = -1;
        deadlineNativeCount = deadlineReplies = deadlineExtraTurns = 0;
        deadlineReusedBoards = 0;
        deadlineWCompleted = deadlineACompleted = deadlineWr0Completed = 0;
        deadlineChanged = false;
        Arrays.fill(deadlineNativeCells, -1);
        Arrays.fill(deadlineWValues, Double.NaN); Arrays.fill(deadlineAValues, Double.NaN);
        Arrays.fill(deadlineWr0Values, Double.NaN);
        Arrays.fill(deadlineWTerminal, 0); Arrays.fill(deadlineATerminal, 0);
        Arrays.fill(deadlineWr0Terminal, 0);
        Arrays.fill(deadlineFirstTarget, -1); Arrays.fill(deadlineFirstDue, -1);
    }

    /** Called only for real play calls; even an ineligible decision breaks stale promises. */
    void deadlineBegin(int turn, int seat) {
        resetDeadlineDiagnostics();
        if (deadlineLastTurn < 0 || turn != deadlineLastTurn + 1 || seat != deadlineSeat)
            clearDeadlineCommitment();
        deadlineLastTurn = turn; deadlineSeat = seat;
    }

    private void deadlineReject(String reason) {
        clearDeadlineCommitment(); deadlineStatus = reason;
    }

    /** Observe a complete current promise; never move an existing deadline into the future. */
    boolean deadlineObserve(int turn, int seat, int target, int promisedDue) {
        if (turn != deadlineLastTurn || seat != deadlineSeat || target < 0 || promisedDue <= turn) {
            deadlineReject("invalid-promise"); return false;
        }
        if (deadlinePendingTarget < 0) {
            deadlinePendingTarget = target; deadlinePendingDue = promisedDue;
            deadlineCommittedTarget = target; deadlineCommittedDue = promisedDue;
            deadlineStatus = "armed"; return false;
        }
        if (target != deadlinePendingTarget) {
            deadlineReject("target-changed"); return false;
        }
        deadlineCommittedTarget = deadlinePendingTarget; deadlineCommittedDue = deadlinePendingDue;
        deadlineStatus = turn > deadlinePendingDue ? "overdue" : "waiting";
        return turn > deadlinePendingDue;
    }

    boolean deadlinePromisesMatch(int replies, int target) {
        if (replies < 1 || replies > deadlineWValues.length || deadlineWCompleted != replies || target < 0)
            return false;
        for (int i = 0; i < replies; i++) {
            if (!Double.isFinite(deadlineWValues[i]) || deadlineWTerminal[i] == 1 || deadlineWTerminal[i] == 2
                    || deadlineFirstTarget[i] != target || deadlineFirstDue[i] <= deadlineLastTurn) return false;
        }
        return true;
    }

    int deadlinePromisedDue(GameState s, int replies) {
        int due = s.turn;
        for (int i = 0; i < replies; i++) due = Math.max(due, deadlineFirstDue[i]);
        return due;
    }

    boolean deadlineScoresAccept(int replies) {
        if (replies < 1 || replies > deadlineWValues.length || deadlineWCompleted != replies
                || deadlineACompleted != replies || deadlineWr0Completed != replies) return false;
        for (int i = 0; i < replies; i++) {
            if (!Double.isFinite(deadlineWValues[i]) || !Double.isFinite(deadlineAValues[i])
                    || !Double.isFinite(deadlineWr0Values[i]) || deadlineWTerminal[i] == 1
                    || deadlineWTerminal[i] == 2 || deadlineATerminal[i] == -1
                    || !(deadlineAValues[i] > deadlineWr0Values[i])) return false;
        }
        return true;
    }

    /** Native root identity and portfolios survive; only its first-turn cells are temporarily replaced. */
    double deadlineRollout(GameState original, int me, int root, int target, int reply, int suppressedRegion) {
        int count = rootCounts[root], turns = simulatedTurns;
        System.arraycopy(roots[root], 0, deadlineSavedRoot, 0, GameState.PAINT_PER_TURN);
        System.arraycopy(bestCells, 0, roots[root], 0, bestCount);
        rootCounts[root] = bestCount;
        deadlineProbeFirstTarget = deadlineProbeFirstDue = -1; deadlineProbeTerminal = 0;
        deadlineProbeActive = true; deadlineSuppressedRegion = suppressedRegion;
        try {
            return rollout(original, me, root, target, reply);
        } finally {
            deadlineProbeActive = false; deadlineSuppressedRegion = -1;
            rootCounts[root] = count;
            System.arraycopy(deadlineSavedRoot, 0, roots[root], 0, GameState.PAINT_PER_TURN);
            deadlineExtraTurns += simulatedTurns - turns; simulatedTurns = turns;
        }
    }

    /** Changes only sabotage after the native construction and all native search have finished. */
    void correctMissedDeadline(GameState s) {
        deadlineNativeTarget = selectedTarget; deadlineNativeCount = bestCount;
        System.arraycopy(bestCells, 0, deadlineNativeCells, 0, bestCount);
        if (selectedTarget >= 0) { deadlineReject("native-attack"); return; }
        if (deadlineSeat != realPlayer || deadlineLastTurn != s.turn) { deadlineReject("seat-or-turn"); return; }
        if (counts[realPlayer] != 0) { deadlineReject("own-portfolio"); return; }
        if (timer.up()) { deadlineReject("timeout"); return; }
        if (selectedOpening || !ordinarySearchComplete
                || p.USE_ROLLOUT_NETWORK_AFTER_BASE && !networkPhaseComplete
                || completedComparisons == 0 || !Double.isFinite(selectedValue)) {
            deadlineReject("incomplete-native-search"); return;
        }
        int root = selectedRoute < 0 ? counts[realPlayer] : selectedRoute;
        if (root < 0 || root >= ordinaryRootSize + networkRoots || root >= roots.length
                || selectedNetwork != (root >= ordinaryRootSize)) {
            deadlineReject("unresolved-root"); return;
        }
        deadlineRoot = root; deadlineNominee = rootTargets[root];
        int target = deadlineNominee;
        if (target < 0 || target >= s.regionCount || s.regionInked[target] || s.regionHasTown(target)) {
            deadlineReject("invalid-target"); return;
        }
        if (deadlinePendingTarget >= 0 && target != deadlinePendingTarget) {
            deadlineReject("target-changed"); return;
        }
        int replies = Math.max(1, Math.min(p.ROLLOUT_REPLIES, counts[1 - realPlayer]));
        deadlineReplies = replies;
        for (int reply = 0; reply < replies; reply++) {
            double value = deadlineRollout(s, realPlayer, root, -1, reply, -1);
            if (!Double.isFinite(value) || timer.up()) { deadlineReject("incomplete-W"); return; }
            deadlineWValues[reply] = value; deadlineWTerminal[reply] = deadlineProbeTerminal;
            deadlineFirstTarget[reply] = deadlineProbeFirstTarget; deadlineFirstDue[reply] = deadlineProbeFirstDue;
            deadlineWCompleted++;
        }
        if (!deadlinePromisesMatch(replies, target)) { deadlineReject("unsafe-or-changed-W"); return; }
        if (!deadlineObserve(s.turn, realPlayer, target, deadlinePromisedDue(s, replies))) return;
        for (int reply = 0; reply < replies; reply++) {
            double value = deadlineRollout(s, realPlayer, root, target, reply, -1);
            if (!Double.isFinite(value) || timer.up()) { deadlineReject("incomplete-A"); return; }
            deadlineAValues[reply] = value; deadlineATerminal[reply] = deadlineProbeTerminal;
            deadlineACompleted++;
        }
        for (int reply = 0; reply < replies; reply++) {
            double value = deadlineRollout(s, realPlayer, root, -1, reply, target);
            if (!Double.isFinite(value) || timer.up()) { deadlineReject("incomplete-Wr0"); return; }
            deadlineWr0Values[reply] = value; deadlineWr0Terminal[reply] = deadlineProbeTerminal;
            deadlineWr0Completed++;
        }
        if (!deadlineScoresAccept(replies)) { deadlineReject("counterfactual-veto"); return; }
        if (timer.up()) { deadlineReject("timeout"); return; }
        selectedTarget = target;
        deadlineChanged = true; deadlineInterventionTarget = target;
        clearDeadlineCommitment(); deadlineStatus = "intervened";
    }
    // MISSED_DEADLINE_END
    public int fillCells;

    /** USE_ROLLOUT_FILL: leftover paint on stable/protected free cells next to own rails; search and target untouched. */
    private void fill(GameState s, int me) {
        int count = bestCount;
        bestCount = fill(s, me, bestCells, count, selectedTarget);
        fillCells = bestCount - count;
    }

    private int fill(GameState s, int me, int[] cells, int count, int target) {
        int paint = GameState.PAINT_PER_TURN, n = s.size;
        for (int i = 0; i < count; i++) paint -= s.placeCost(cells[i]);
        if (paint <= 0) return count;
        byte[] m = new byte[n]; // 1 own, 3 earning, 4 proposed route
        for (int c = 0; c < n; c++) if (s.owner[c] == me) m[c] = 1;
        for (int q = 0; q < s.pairCount; q++)
            for (int i = 0; s.pairPath[q] != null && i < s.pairPathLen[q]; i++) if (m[s.pairPath[q][i]] == 1) m[s.pairPath[q][i]] = 3;
        for (int r = 0; r < counts[me]; r++)
            for (int i = 0; i < lengths[me][r]; i++) if (m[routes[me][r][i]] == 0) m[routes[me][r][i]] = 4;
        for (int i = 0; i < count; i++) m[cells[i]] = 1;
        while (paint > 0 && count < GameState.PAINT_PER_TURN && !timer.up()) {
            int bestCell = -1, bestScore = 0;
            for (int c = 0; c < n; c++) {
                int g = s.regionId[c], adj = 0;
                if ((m[c] & 1) != 0 || !s.canBuild(c) || s.placeCost(c) > paint || g == target
                        || !s.regionHasTown(g) && s.regionInstability[g] > p.ROLLOUT_FILL_MAX_INSTABILITY) continue;
                for (int d = 0; d < 4; d++) {
                    int x = s.x(c) + GameState.DX[d], y = s.y(c) + GameState.DY[d];
                    if (s.inBounds(x, y) && (m[s.cell(x, y)] & 1) != 0) adj = Math.max(adj, m[s.cell(x, y)]);
                }
                if (adj == 0) continue;
                int score = (adj + (m[c] == 4 ? 4 : 0) + (s.regionHasTown(g) ? 2 : 0)) * 4 - s.placeCost(c);
                if (score > bestScore) { bestScore = score; bestCell = c; }
            }
            if (bestCell < 0) break;
            cells[count++] = bestCell; paint -= s.placeCost(bestCell); m[bestCell] = 1;
        }
        return count;
    }

    /** Complete comparisons only; preserves the saved answer when a reply cannot finish. */
    private boolean searchRoots(GameState s, int me, int first, int end, int replies) {
        for (int root = first; root < end; root++) {
            boolean duplicate = false;
            for (int j = 0; j < root && root < ordinaryRootSize; j++) {
                if (sameSet(roots[root], rootCounts[root], roots[j], rootCounts[j])) duplicate = true;
            }
            if (duplicate) continue;
            for (int attack = 0; attack < 2; attack++) {
                int target = attack == 0 ? rootTargets[root] : -1;
                if (attack == 1 && rootTargets[root] < 0) continue;
                double sum = 0, worst = Double.MAX_VALUE;
                for (int reply = 0; reply < replies; reply++) {
                    double value = rollout(s, me, root, target, reply);
                    if (!Double.isFinite(value)) return false; // discard ALL partial scenario coverage
                    sum += value; worst = Math.min(worst, value);
                }
                completedComparisons++;
                if (p.USE_ROLLOUT_NETWORK_AFTER_BASE && root >= ordinaryRootSize) {
                    networkComparisons++; networkCompared[root] = true;
                }
                double value = (1 - p.ROLLOUT_ROBUSTNESS) * sum / replies + p.ROLLOUT_ROBUSTNESS * worst;
                if (value > best) {
                    best = value; selectedValue = value;
                    selectedRoute = root == counts[me] ? -1 : root; selectedTarget = target;
                    selectedNetwork = p.USE_ROLLOUT_NETWORK_AFTER_BASE && root >= ordinaryRootSize;
                    selectedNetworkCell = selectedNetwork ? roots[root][0] : -1;
                    bestCount = rootCounts[root];
                    System.arraycopy(roots[root], 0, bestCells, 0, bestCount);
                }
            }
        }
        return true;
    }

    private void saveOrdinaryChoice(boolean complete) {
        ordinarySearchComplete = complete;
        ordinaryComparisons = completedComparisons - networkComparisons;
        ordinarySelectedCount = bestCount;
        ordinarySelectedRoute = selectedRoute;
        ordinarySelectedTarget = selectedTarget;
        ordinarySelectedValue = selectedValue;
        System.arraycopy(bestCells, 0, ordinarySelectedCells, 0, bestCount);
    }

    /** No new network work runs until every ordinary root/reply comparison is complete. */
    private void searchNetworkAfterBase(GameState s, int me, int replies, long budgetMs) {
        if (!ordinarySearchComplete || timer.up()) return;
        networkSearchStarted = true;
        Arrays.fill(networkCompared, false); Arrays.fill(networkGains, Integer.MIN_VALUE);
        int end = addNetworkRoots(s, me, ordinaryRootSize,
                (int) Math.min(p.ROLLOUT_NETWORK_RESERVE_MS, budgetMs / 3));
        networkPhaseComplete = searchRoots(s, me, ordinaryRootSize, end, replies);
    }

    private void propose(GameState s, int me, int pair, int variant) {
        if (variant == 2 && threats[me] < 0) return;
        for (int c = 0; c < s.size; c++) {
            extra[c] = (int) Math.round(p.UNSTABLE_COST * s.instability[c]);
            blocked[c] = variant == 1 ? s.owner[c] == 1 - me
                    : variant == 2 && s.regionId[c] == threats[me];
        }
        int n = pathing.cheapestPath(s, s.townCell[s.pairFrom[pair]], s.townCell[s.pairTo[pair]],
                path, variant == 0 ? null : blocked, extra);
        routeQueries++;
        if (n <= 0) return;
        if (opening != null && s.turn < p.OPENING_TURNS && me == openingPlayer && variant == 0)
            opening.observe(s, pair, path, n);
        completion.set(s); int cost = 0;
        for (int i = 0; i < n; i++) if (s.canBuild(path[i])) {
            cost += s.placeCost(path[i]); completion.state.owner[path[i]] = me;
        }
        if (cost == 0) return;
        int turns = (cost + GameState.PAINT_PER_TURN - 1) / GameState.PAINT_PER_TURN;
        double earning = Math.min(p.ROLLOUT_ROUTE_HORIZON, s.turnsLeft()) - turns + 1;
        if (earning <= 0) return;
        completion.refresh(); Rules.incomePerTurn(completion.state, income);
        double gain = income[me] - income[1 - me] - baseDiff[me];
        // Reuse the bounded proposal pool: no extra path query or random draw.
        // Current own income is zero, so a positive completed own income is also a gain.
        if (me == quickPlayer && cost <= p.ROLLOUT_QUICK_MAX_PAINT && income[me] > 0 && gain > 0)
            considerQuick(n, cost, gain);
        if (threats[me] >= 0) {
            Rules.ink(completion.state, threats[me]); completion.refresh();
            Rules.incomePerTurn(completion.state, income);
            double protectedGain = income[me] - income[1 - me] - threatDiff[me];
            gain = (1 - p.ROLLOUT_THREAT_WEIGHT) * gain + p.ROLLOUT_THREAT_WEIGHT * protectedGain;
        }
        if (gain <= 0) return;
        double value = gain * earning / turns * (1 + p.NOISE * (random.nextDouble() - 0.5));
        insert(s, me, n, value);
        // Different ends of a route are genuinely different affordable first actions.
        for (int i = 0, j = n - 1; i < j; i++, j--) { int c = path[i]; path[i] = path[j]; path[j] = c; }
        insert(s, me, n, value);
    }

    private void insert(GameState s, int me, int length, double value) {
        int n = pack(s, path, length, prefix, 0, GameState.PAINT_PER_TURN);
        for (int i = 0; i < counts[me]; i++) {
            int other = pack(s, routes[me][i], lengths[me][i], moves[me], 0, GameState.PAINT_PER_TURN);
            if (!sameSet(prefix, n, moves[me], other)) continue;
            if (value <= values[me][i]) return;
            for (int j = i; j + 1 < counts[me]; j++) {
                System.arraycopy(routes[me][j + 1], 0, routes[me][j], 0, lengths[me][j + 1]);
                lengths[me][j] = lengths[me][j + 1]; values[me][j] = values[me][j + 1];
            }
            counts[me]--; break;
        }
        int slot = counts[me];
        for (int i = 0; i < counts[me]; i++) if (value > values[me][i]) { slot = i; break; }
        if (slot >= p.ROLLOUT_WIDTH) return;
        counts[me] = Math.min(p.ROLLOUT_WIDTH, counts[me] + 1);
        for (int i = counts[me] - 1; i > slot; i--) {
            System.arraycopy(routes[me][i - 1], 0, routes[me][i], 0, lengths[me][i - 1]);
            lengths[me][i] = lengths[me][i - 1]; values[me][i] = values[me][i - 1];
        }
        System.arraycopy(path, 0, routes[me][slot], 0, length);
        lengths[me][slot] = length; values[me][slot] = value;
    }

    private void considerQuick(int n, int cost, double gain) {
        if (quickLength == 0 || cost < quickCost || cost == quickCost && gain > quickGain) {
            System.arraycopy(path, 0, quickPath, 0, n);
            quickLength = n; quickCost = cost; quickGain = (int) gain;
        }
    }

    /** Preserve one cheap completion, replacing a duplicate prefix or the last existing slot. */
    private void retainQuickRoute(GameState s, int me) {
        int n = pack(s, quickPath, quickLength, prefix, 0, GameState.PAINT_PER_TURN);
        int slot = -1;
        for (int i = 0; i < counts[me]; i++) {
            int other = pack(s, routes[me][i], lengths[me][i], moves[me], 0, GameState.PAINT_PER_TURN);
            if (sameSet(prefix, n, moves[me], other)) { slot = i; break; }
        }
        if (slot < 0) {
            slot = Math.min(counts[me], p.ROLLOUT_WIDTH - 1);
            counts[me] = Math.min(counts[me] + 1, p.ROLLOUT_WIDTH);
        }
        System.arraycopy(quickPath, 0, routes[me][slot], 0, quickLength);
        lengths[me][slot] = quickLength;
        // Values are only used during proposal insertion, which has finished.
        values[me][slot] = 0;
    }

    /** New first actions only: exact whole-network income, unchanged rollout continuation. */
    private int addNetworkRoots(GameState s, int me, int size, int reserve) {
        networkEligible = NetworkCells.collectSingleCellConnections(s, networkCells);
        Arrays.fill(networkWeights, 0);
        for (int pair = 0; pair < s.pairCount; pair++)
            for (int i = 0; i < s.pairPathLen[pair]; i++) networkWeights[s.pairPath[pair][i]]++;
        for (int i = 0; i < networkEligible; i++) {
            int cell = networkCells[i];
            networkPriority[cell] = NetworkCells.adjacentConnectorWeight(s, cell, networkWeights);
        }
        Arrays.fill(networkBest, -1);
        Rules.incomePerTurn(s, income);
        int before = income[me] - income[1 - me];
        int limit = Math.min(networkEligible, p.ROLLOUT_NETWORK_MAX_CELLS);
        for (int i = 0; i < limit && !timer.up(reserve); i++) {
            // Select only the bounded prefix; avoid allocations and sorting the whole board.
            int best = i;
            for (int j = i + 1; j < networkEligible; j++)
                if (networkPriorityBefore(s, networkCells[j], networkCells[best])) best = j;
            int cell = networkCells[best]; networkCells[best] = networkCells[i]; networkCells[i] = cell;
            completion.set(s); completion.state.owner[cell] = me; completion.refresh();
            Rules.incomePerTurn(completion.state, income);
            int gain = income[me] - income[1 - me] - before;
            networkGains[cell] = gain; networkEvaluated++;
            if (gain <= 0) continue;
            boolean duplicate = false;
            for (int root = 0; root < ordinaryRootSize; root++)
                if (rootCounts[root] == 1 && roots[root][0] == cell) duplicate = true;
            if (duplicate) continue;
            for (int slot = 0; slot < networkBest.length; slot++) {
                int other = networkBest[slot];
                if (other >= 0 && (gain < networkGains[other]
                        || gain == networkGains[other] && !networkCheaper(s, cell, other))) continue;
                for (int j = networkBest.length - 1; j > slot; j--) networkBest[j] = networkBest[j - 1];
                networkBest[slot] = cell; break;
            }
        }
        for (int cell : networkBest) {
            if (cell < 0 || timer.up()) break;
            roots[size][0] = cell;
            targetBoard.set(s); placeOwn(targetBoard, me, roots[size], 1);
            int target = chooseTarget(targetBoard.state, me);
            if (timer.up()) break;
            rootCounts[size] = 1; rootTargets[size] = target;
            size++; networkRoots++;
        }
        return size;
    }

    private boolean networkPriorityBefore(GameState s, int a, int b) {
        return networkPriority[a] > networkPriority[b]
                || networkPriority[a] == networkPriority[b] && networkCheaper(s, a, b);
    }

    private static boolean networkCheaper(GameState s, int a, int b) {
        return s.placeCost(a) < s.placeCost(b) || s.placeCost(a) == s.placeCost(b) && a < b;
    }

    /** Pack real paint costs, including leftovers across projects; never mutate the pre-turn board. */
    private int fillBuild(GameState s, int me, int first, int[] out) {
        int count = 0, paint = GameState.PAINT_PER_TURN;
        for (int pass = -1; pass < counts[me] && paint > 0; pass++) {
            int route = pass < 0 ? first : pass;
            if (route < 0 || route >= counts[me] || pass == first) continue;
            boolean valid = true;
            for (int i = 0; i < lengths[me][route]; i++) if (s.inked[routes[me][route][i]]) { valid = false; break; }
            if (!valid) continue;
            int next = pack(s, routes[me][route], lengths[me][route], out, count, paint);
            for (int i = count; i < next; i++) paint -= s.placeCost(out[i]);
            count = next;
        }
        return count;
    }

    private static int pack(GameState s, int[] route, int n, int[] out, int count, int paint) {
        for (int i = 0; i < n && paint > 0; i++) {
            int c = route[i];
            if (!s.canBuild(c) || s.placeCost(c) > paint) continue;
            boolean duplicate = false;
            for (int j = 0; j < count; j++) if (out[j] == c) duplicate = true;
            if (duplicate) continue;
            out[count++] = c; paint -= s.placeCost(c);
        }
        return count;
    }

    private static boolean sameSet(int[] a, int n, int[] b, int m) {
        if (n != m) return false;
        for (int i = 0; i < n; i++) {
            boolean found = false;
            for (int j = 0; j < m; j++) if (a[i] == b[j]) found = true;
            if (!found) return false;
        }
        return true;
    }

    private static void placeOwn(SearchState board, int me, int[] cells, int count) {
        // Every cell is already checked against this pre-turn state; no score or turn advance.
        for (int i = 0; i < count; i++) board.state.owner[cells[i]] = me;
        board.refresh();
    }

    private double rollout(GameState original, int me, int root, int ownTarget, int reply) {
        work.set(original);
        int depth = Math.min(p.ROLLOUT_DEPTH, original.turnsLeft());
        int ownFirst = root < counts[me] ? root : 0;
        // BOSS_MODEL_BEGIN
        boolean bossOpp = p.USE_BOSS_OPPONENT_MODEL && reply < p.BOSS_OPPONENT_REPLIES, bossOwn = p.USE_BOSS_OWN_MODEL;
        int simOppHeld = liteOppHeld, simOwnHeld = ownTarget >= 0 ? ownTarget : liteHeld;
        if (bossOpp || bossOwn) { System.arraycopy(litePlacedTurn, 0, liteSimPlaced, 0, liteSimPlaced.length); litePlaced = liteSimPlaced; }
        // BOSS_MODEL_END
        for (int turn = 0; turn < depth; turn++) {
            if (timer.up()) return Double.NaN;
            GameState s = work.state;
            for (int player = 0; player < 2; player++) {
                if (turn == 0 && player == me) {
                    moveCounts[player] = rootCounts[root];
                    System.arraycopy(roots[root], 0, moves[player], 0, moveCounts[player]);
                    targets[player] = ownTarget;
                } else {
                    int first = player == me ? ownFirst : reply;
                    moveCounts[player] = fillBuild(s, player, first, moves[player]);
                    // MISSED_DEADLINE_BEGIN
                    if (deadlineProbeActive && moveCounts[player] == 0) {
                        // The resolved pre-turn board already has the same canonical paths.
                        deadlineReusedBoards++;
                        targets[player] = chooseTarget(s, player);
                    } else {
                    // MISSED_DEADLINE_END
                    targetBoard.set(s);
                    placeOwn(targetBoard, player, moves[player], moveCounts[player]);
                    // BOSS_MODEL_BEGIN
                    if (player != me && bossOpp) {
                        bossModelCalls++;
                        targets[player] = simOppHeld = chooseBossLite(targetBoard.state, player, simOppHeld);
                    } else if (player == me && bossOwn) {
                        targets[player] = simOwnHeld = chooseBossLite(targetBoard.state, player, simOwnHeld);
                    } else
                    // BOSS_MODEL_END
                    targets[player] = chooseTarget(targetBoard.state, player);
                    // MISSED_DEADLINE_BEGIN
                    }
                    if (deadlineProbeActive && player == me && targets[player] == deadlineSuppressedRegion)
                        targets[player] = -1;
                    // MISSED_DEADLINE_END
                }
            }
            if (timer.up()) return Double.NaN;
            // MISSED_DEADLINE_BEGIN
            if (deadlineProbeActive && turn > 0 && deadlineProbeFirstTarget < 0 && targets[me] >= 0) {
                deadlineProbeFirstTarget = targets[me]; deadlineProbeFirstDue = s.turn;
            }
            // MISSED_DEADLINE_END
            boolean terminal = work.resolve(moves[0], moveCounts[0], targets[0], moves[1], moveCounts[1], targets[1]);
            simulatedTurns++;
            // BOSS_MODEL_BEGIN
            if (bossOpp || bossOwn) {
                for (int player = 0; player < 2; player++)
                    for (int i = 0; i < moveCounts[player]; i++) liteSimPlaced[moves[player][i]] = s.turn - 1;
            }
            // BOSS_MODEL_END
            if (terminal) {
                int diff = s.score[me] - s.score[1 - me];
                // MISSED_DEADLINE_BEGIN
                if (deadlineProbeActive) deadlineProbeTerminal = diff > 0 ? 1 : diff < 0 ? -1 : 2;
                // MISSED_DEADLINE_END
                return Math.signum(diff) * p.ROLLOUT_WIN_VALUE + diff;
            }
        }
        Rules.incomePerTurn(work.state, income);
        return work.state.score[me] - work.state.score[1 - me]
                + Math.min(p.ROLLOUT_TAIL, work.state.turnsLeft()) * (income[me] - income[1 - me]);
    }

    /** Cheap exposure shortlist, then exact core ink evaluation. No actual future enemy action is read. */
    private int chooseTarget(GameState s, int me) {
        if (!p.USE_DISRUPT) return -1;
        Arrays.fill(trackBalance, 0); Arrays.fill(priority, 0);
        for (int c = 0; c < s.size; c++) {
            if (s.owner[c] == 1 - me) trackBalance[s.regionId[c]]++;
            else if (s.owner[c] == me) trackBalance[s.regionId[c]]--;
        }
        for (int pair = 0; pair < s.pairCount; pair++) {
            int exposure = -SearchState.connectionIncomeDifference(s, pair, me);
            if (exposure == 0) continue;
            Arrays.fill(marked, false);
            for (int i = 0; i < s.pairPathLen[pair]; i++) {
                int r = s.regionId[s.pairPath[pair][i]];
                if (!marked[r]) { priority[r] += exposure; marked[r] = true; }
            }
        }
        Arrays.fill(shortlist, -1);
        for (int r = 0; r < s.regionCount; r++) {
            if (s.regionInked[r] || s.regionHasTown(r)) continue;
            priority[r] = (priority[r] * p.ROLLOUT_TARGET_HORIZON + trackBalance[r] * p.ROLLOUT_TRACK_WEIGHT)
                    / (GameState.INK_THRESHOLD - s.regionInstability[r]);
            for (int i = 0; i < shortlist.length; i++) {
                if (shortlist[i] < 0 || priority[r] > priority[shortlist[i]]) {
                    for (int j = shortlist.length - 1; j > i; j--) shortlist[j] = shortlist[j - 1];
                    shortlist[i] = r; break;
                }
            }
        }
        Rules.incomePerTurn(s, income);
        int before = income[me] - income[1 - me], bestTarget = -1;
        double best = 0;
        for (int i = 0; i < shortlist.length && !timer.up(); i++) {
            int r = shortlist[i]; if (r < 0) break;
            int needed = GameState.INK_THRESHOLD - s.regionInstability[r];
            // INK_REFRESH_BEGIN
            if (p.USE_INCREMENTAL_INK_REFRESH) { inkBoard.set(s); inkBoard.inkAndRefresh(r); }
            else {
            // INK_REFRESH_END
            inkBoard.set(s); Rules.ink(inkBoard.state, r); inkBoard.refresh();
            // INK_REFRESH_BEGIN
            }
            // INK_REFRESH_END
            Rules.incomePerTurn(inkBoard.state, income);
            int after = income[me] - income[1 - me];
            if (needed == 1 && !Rules.anyPairPossible(inkBoard.state, pathing)) {
                if (s.score[me] - s.score[1 - me] + after > 0) return r;
                continue;
            }
            double value = ((after - before) * Math.max(0, Math.min(p.ROLLOUT_TARGET_HORIZON, s.turnsLeft()) - needed + 1)
                    + trackBalance[r] * p.ROLLOUT_TRACK_WEIGHT) / needed;
            if (value > best) { best = value; bestTarget = r; }
        }
        return bestTarget;
    }

    // BOSS_LITE_BEGIN
    /** Record when each track appeared (for the opponent-placed-recently term); once per real turn. */
    private void liteObserve(GameState s) {
        for (int c = 0; c < s.size; c++) {
            if (s.owner[c] != litePrevOwner[c] && s.owner[c] != GameState.NO_TRACK) litePlacedTurn[c] = s.turn;
            litePrevOwner[c] = s.owner[c];
        }
    }

    // BOSS_LITE_END
    // BOSS_MODEL_BEGIN
    /**
     * The opponent's current held target, read from the public board: a region whose instability
     * rose since our last real turn by more than our own hit on it was hit by the opponent.
     */
    private void liteObserveOpponent(GameState s) {
        int hit = -1;
        for (int r = 0; r < s.regionCount; r++) {
            int rise = s.regionInstability[r] - liteLastInstability[r];
            if (r == liteLastRealTarget) rise--;
            if (rise > 0 && !s.regionInked[r]) hit = r;
            liteLastInstability[r] = s.regionInstability[r];
        }
        if (hit >= 0) liteOppHeld = hit;
        else if (liteOppHeld >= 0 && s.regionInked[liteOppHeld]) liteOppHeld = -1;
    }
    // BOSS_MODEL_END
    // BOSS_LITE_BEGIN

    /**
     * Boss-style DISRUPT for {@code me}: the cheap part of the fitted logit ranks every region in
     * O(cells); the top ROLLOUT_TARGETS regions get one ink simulation for the income terms and the
     * full score. {@code held} is the region committed to so far (kept unless beaten by the margin);
     * winning one-hit closures are taken, losing ones refused. Returns -1 for WAIT.
     */
    private int chooseBossLite(GameState s, int me, int held) {
        int opp = 1 - me;
        if (held >= 0 && s.regionInked[held]) held = -1;
        Arrays.fill(liteOppCells, 0); Arrays.fill(liteOwnCells, 0); Arrays.fill(liteNeutral, 0);
        Arrays.fill(liteOppActive, 0); Arrays.fill(liteOppPrev2, 0); Arrays.fill(liteFree, 0);
        Arrays.fill(liteOnActive, false);
        for (int pair = 0; pair < s.pairCount; pair++) {
            if (!s.pairActive(pair)) continue;
            for (int i = 0; i < s.pairPathLen[pair]; i++) liteOnActive[s.pairPath[pair][i]] = true;
        }
        for (int c = 0; c < s.size; c++) {
            int r = s.regionId[c], o = s.owner[c];
            if (s.canBuild(c)) liteFree[r]++;
            if (o == opp) {
                liteOppCells[r]++;
                if (liteOnActive[c]) liteOppActive[r]++;
                if (litePlaced[c] >= s.turn - 2) liteOppPrev2[r]++;
            } else if (o == me) liteOwnCells[r]++;
            else if (o == GameState.NEUTRAL) liteNeutral[r]++;
        }
        Arrays.fill(liteShort, -1);
        boolean heldListed = false;
        for (int r = 0; r < s.regionCount; r++) {
            if (s.regionInked[r] || s.regionHasTown(r)) continue;
            if (liteOppCells[r] == 0 && liteOwnCells[r] == 0 && liteNeutral[r] == 0 && s.regionInstability[r] == 0
                    && r != held
                    // BOSS_ROUTES_BEGIN
                    && !(p.USE_BOSS_LITE_ROUTES && liteRoutes[opp][r] > 0)
                    // BOSS_ROUTES_END
                    ) continue;
            liteCheap[r] = LITE_OPP_CELLS * liteOppCells[r] + LITE_OPP_ACTIVE * liteOppActive[r]
                    + LITE_OWN_CELLS * liteOwnCells[r] + LITE_OPP_PREV2 * liteOppPrev2[r]
                    + LITE_INSTAB * s.regionInstability[r] + (liteAdjTown[r] ? LITE_ADJ_TOWN : 0)
                    + LITE_NEUTRAL * liteNeutral[r] + LITE_LOG_FREE * Math.log1p(liteFree[r]);
            // BOSS_ROUTES_BEGIN
            if (p.USE_BOSS_LITE_ROUTES) liteCheap[r] += p.BOSS_LITE_ROUTE_WEIGHT * liteRoutes[opp][r];
            // BOSS_ROUTES_END
            for (int i = 0; i < liteShort.length; i++) {
                if (liteShort[i] < 0 || liteCheap[r] > liteCheap[liteShort[i]]) {
                    if (liteShort[liteShort.length - 1] == held) heldListed = false;
                    for (int j = liteShort.length - 1; j > i; j--) liteShort[j] = liteShort[j - 1];
                    liteShort[i] = r;
                    if (r == held) heldListed = true;
                    break;
                }
            }
        }
        if (held >= 0 && !heldListed) liteShort[liteShort.length - 1] = held;
        Rules.incomePerTurn(s, income);
        int beforeMe = income[me], beforeOpp = income[opp], best = -1;
        double bestScore = -Double.MAX_VALUE, heldScore = -Double.MAX_VALUE;
        for (int i = 0; i < liteShort.length; i++) {
            int r = liteShort[i];
            if (r < 0) break;
            if (timer.up()) break;
            int needed = GameState.INK_THRESHOLD - s.regionInstability[r], dOpp = 0, dOwn = 0;
            if (liteOppCells[r] + liteOwnCells[r] + liteNeutral[r] > 0 || needed == 1) {
                // INK_REFRESH_BEGIN
                if (p.USE_INCREMENTAL_INK_REFRESH) { inkBoard.set(s); inkBoard.inkAndRefresh(r); }
                else {
                // INK_REFRESH_END
                inkBoard.set(s); Rules.ink(inkBoard.state, r); inkBoard.refresh();
                // INK_REFRESH_BEGIN
                }
                // INK_REFRESH_END
                Rules.incomePerTurn(inkBoard.state, income);
                dOpp = beforeOpp - income[opp]; dOwn = beforeMe - income[me];
                if (needed == 1 && !Rules.anyPairPossible(inkBoard.state, pathing)) {
                    if (s.score[me] + income[me] > s.score[opp] + income[opp]) return r;
                    continue;
                }
            }
            double v = liteCheap[r] + LITE_D_OPP * dOpp + LITE_D_OWN * dOwn;
            if (r == held) heldScore = v;
            if (v > bestScore) { bestScore = v; best = r; }
        }
        if (best < 0) return -1;
        if (held >= 0 && heldScore > -Double.MAX_VALUE && bestScore < heldScore + p.BOSS_HOLD_MARGIN) return held;
        if (liteOppCells[best] == 0 && s.regionInstability[best] == 0
                // BOSS_ROUTES_BEGIN
                && !(p.USE_BOSS_LITE_ROUTES && liteRoutes[opp][best] > 0)
                // BOSS_ROUTES_END
                ) return -1;
        return best;
    }

    // BOSS_ROUTES_BEGIN
    /** The real turn's route portfolios, as the next turn's route-traffic term (one region count per route). */
    private void liteRecordRoutes(GameState s) {
        for (int q = 0; q < 2; q++) {
            Arrays.fill(liteRoutes[q], 0);
            for (int k = 0; k < counts[q]; k++) {
                int cost = 0;
                for (int i = 0; i < lengths[q][k]; i++) if (s.canBuild(routes[q][k][i])) cost += s.placeCost(routes[q][k][i]);
                if (cost <= 0) continue;
                for (int i = 0; i < lengths[q][k]; i++) {
                    int r = s.regionId[routes[q][k][i]];
                    if (s.regionHasTown(r)) continue;
                    boolean seen = false;
                    for (int j = 0; j < i; j++) if (s.regionId[routes[q][k][j]] == r) { seen = true; break; }
                    if (!seen) liteRoutes[q][r] += 1.0 / cost;
                }
            }
        }
    }
    // BOSS_ROUTES_END
    // BOSS_LITE_END
}
