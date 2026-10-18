package btk.bot.current;

import btk.bot.Bot;
import btk.bot.Params;
import btk.bot.Timer;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.NetworkCells;
import btk.core.NetworkChords;
import btk.core.Rules;
import btk.core.SearchState;
import btk.core.TacticalPathing;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** Independent portfolio rollouts: score affordable, simultaneous turns instead of a finished route. */
public final class RolloutBot implements Bot {
    private final Params p;
    private final Timer timer = new Timer();
    private Pathing pathing;
    private TacticalPathing tactical;
    private SearchState work, completion, targetBoard, inkBoard;
    private int[][][] routes;
    private int[][] lengths;
    private double[][] values;
    private final int[] counts = new int[2], income = new int[2];
    private final int[] baseDiff = new int[2], threatDiff = new int[2], threats = new int[2];
    private final int[][] moves = new int[2][GameState.PAINT_PER_TURN];
    private final int[] moveCounts = new int[2], targets = new int[2];
    private int[] path, extra, trackBalance, shortlist, bestCells, prefix;
    private int[] oppTracks, ownTracks;
    private int realPlayer;
    private boolean[] blocked, marked;
    private double[] priority;
    private int[] placedTurn, lastOwner;
    private int[][] live;
    public int liveTargets, forcedTargets;
    private int[][] roots;
    private int[] rootCounts, rootTargets;
    private int[] replyRoutes;
    private int[] rootAlternateTargets;
    private int secondTarget;
    private Random random;
    private int[] quickPath;
    private int quickPlayer, quickLength, quickCost, quickGain, quickSlot;
    private double quickValue;
    private int[][][] routeDependencies;
    private int[][] dependencyCounts;
    private int[] proposalDependencies, quickDependencies;
    private int proposalDependencyCount, quickDependencyCount;
    public int routeQueries, completedComparisons, simulatedTurns, selectedRoute, selectedTarget;
    public double selectedValue;
    public int replanCalls, replanChanges;
    public int extraTargetComparisons;
    public boolean selectedExtraTarget;
    private int[] spreadFirst, spreadSecond, spreadQuota;
    private int ordinaryRootSize;
    private boolean packedBoth;
    public int spreadRoots, spreadComparisons, spreadBuildTurns;
    public boolean selectedSpread;
    private int[] networkCells, networkWeights, networkPriority, networkGains, networkBest;
    private boolean[] networkCompared;
    public int networkEligible, networkEvaluated, networkRoots, networkComparisons;
    public boolean selectedNetwork;
    public int selectedNetworkCell = -1;
    private int[] chordA, chordB, chordC, chordOrder, chordPriority, chordGains, chordBest;
    private int chordRootStart = Integer.MAX_VALUE;
    public int chordEligible, chordEvaluated, chordRoots, chordComparisons;
    public boolean selectedChord, networkPhaseComplete, chordSearchStarted;
    private int bestCount;
    private int[] filledRoot;
    private byte[] fillMarks;
    private int filledRootCount;
    private boolean selectedRootFilled;
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
        // MISSED_DEADLINE_BEGIN
        if (p.USE_ROLLOUT_MISSED_DEADLINE && (p.USE_ROLLOUT_ROOT_FILL || p.USE_ROLLOUT_ALL_CELL_RISK
                || p.USE_ROLLOUT_SECOND_TARGET || p.USE_ROLLOUT_SPREAD
                || p.USE_ROLLOUT_NETWORK || p.USE_ROLLOUT_NETWORK_CHORDS || p.USE_ROLLOUT_ROUTE_DEPENDENCIES
                || p.USE_ROLLOUT_REPLAN || p.USE_ROLLOUT_DIVERSE_REPLIES || p.USE_ROLLOUT_QUICK_EARLY
                || p.USE_ROLLOUT_QUICK_VALUE || p.USE_ROLLOUT_QUICK_SAFE || p.USE_ROLLOUT_LIVE_TARGET
                || p.ROLLOUT_ALWAYS_DISRUPT || p.USE_ROLLOUT_TRACK_VALUE))
            throw new IllegalArgumentException("Missed-deadline correction requires the native rollout continuation");
        // MISSED_DEADLINE_END
        // RHEA (strategy-search-rhea-v1, rejected) was removed from current for artifact size; frozen v08 keeps it.
        if (p.USE_ROLLOUT_RHEA) throw new IllegalArgumentException("USE_ROLLOUT_RHEA is no longer available in current");
        if (p.USE_HEURISTIC_POLICY || p.USE_OWN_BUILD_PLAN || p.USE_REPLY_RERANK || p.USE_JOINT_ACTION
                || p.USE_DEFENSE_PRIORITY || p.USE_OPP_MODEL || p.GUARD_BUILD_INCOME
                || p.CHOOSE_BUILD_DIRECTION || p.EARLY_DISRUPT_FALLBACK || p.INTERRUPT_VULN_EVAL
                || p.ROLLOUT_WIDTH < 1 || p.ROLLOUT_WIDTH > 8 || p.ROLLOUT_REPLIES < 1
                || p.ROLLOUT_REPLIES > p.ROLLOUT_WIDTH || p.ROLLOUT_DEPTH < 1 || p.ROLLOUT_DEPTH > 12
                || p.ROLLOUT_MAX_ROUTES < 1 || p.ROLLOUT_MAX_ROUTES > 128
                || p.ROLLOUT_QUICK_MAX_PAINT < 1 || p.ROLLOUT_QUICK_MAX_PAINT > 12
                || p.ROLLOUT_REPLAN_TURN < 1 || p.ROLLOUT_REPLAN_TURN > 11
                || p.ROLLOUT_SPREAD_ROOTS < 1 || p.ROLLOUT_SPREAD_ROOTS > 2
                || p.ROLLOUT_NETWORK_MAX_CELLS < 1 || p.ROLLOUT_NETWORK_MAX_CELLS > 64
                || p.ROLLOUT_NETWORK_ROOTS < 1 || p.ROLLOUT_NETWORK_ROOTS > 2
                || p.ROLLOUT_NETWORK_RESERVE_MS < 0 || p.ROLLOUT_NETWORK_RESERVE_MS > 36
                || p.OPENING_TURNS < 1 || p.OPENING_TURNS > 3
                || p.OPENING_CITY_QUOTA < 1 || p.OPENING_CITY_QUOTA > 3
                || !unit(p.OPENING_COLLISION_FACTOR)
                || p.USE_PROTECTED_OPENING
                    && (!p.USE_ROLLOUT_NETWORK_AFTER_BASE || !p.USE_ROLLOUT_QUICK_ROUTE
                    || p.USE_ROLLOUT_NETWORK || p.USE_ROLLOUT_NETWORK_CHORDS
                    || p.USE_ROLLOUT_ROUTE_DEPENDENCIES || p.USE_ROLLOUT_REPLAN
                    || p.USE_ROLLOUT_SPREAD || p.USE_ROLLOUT_SECOND_TARGET
                    || p.USE_ROLLOUT_DIVERSE_REPLIES || p.USE_ROLLOUT_QUICK_EARLY
                    || p.USE_ROLLOUT_QUICK_VALUE || p.USE_ROLLOUT_QUICK_SAFE)
                || p.USE_ROLLOUT_NETWORK && p.USE_ROLLOUT_NETWORK_AFTER_BASE
                || p.USE_ROLLOUT_ROOT_FILL && (!p.USE_ROLLOUT_FILL || p.USE_ROLLOUT_ALL_CELL_RISK
                    || p.USE_ROLLOUT_SECOND_TARGET || p.USE_ROLLOUT_SPREAD || p.USE_ROLLOUT_NETWORK
                    || p.USE_ROLLOUT_NETWORK_CHORDS || p.USE_ROLLOUT_ROUTE_DEPENDENCIES
                    || p.USE_ROLLOUT_REPLAN || p.USE_ROLLOUT_DIVERSE_REPLIES
                    || p.USE_ROLLOUT_QUICK_EARLY || p.USE_ROLLOUT_QUICK_VALUE || p.USE_ROLLOUT_QUICK_SAFE
                    || p.USE_ROLLOUT_LIVE_TARGET || p.ROLLOUT_ALWAYS_DISRUPT || p.USE_ROLLOUT_TRACK_VALUE)
                || p.USE_ROLLOUT_NETWORK_CHORDS && !p.USE_ROLLOUT_NETWORK_AFTER_BASE
                || (p.USE_ROLLOUT_NETWORK || p.USE_ROLLOUT_NETWORK_AFTER_BASE)
                    && (p.USE_ROLLOUT_QUICK_EARLY || p.USE_ROLLOUT_QUICK_VALUE
                    || p.USE_ROLLOUT_QUICK_SAFE || p.USE_ROLLOUT_DIVERSE_REPLIES
                    || p.USE_ROLLOUT_REPLAN || p.USE_ROLLOUT_SECOND_TARGET || p.USE_ROLLOUT_SPREAD)
                || p.USE_ROLLOUT_SPREAD && (p.USE_ROLLOUT_QUICK_EARLY || p.USE_ROLLOUT_QUICK_VALUE
                    || p.USE_ROLLOUT_QUICK_SAFE || p.USE_ROLLOUT_DIVERSE_REPLIES
                    || p.USE_ROLLOUT_REPLAN || p.USE_ROLLOUT_SECOND_TARGET)
                || p.ROLLOUT_RESERVE_MS < 0 || p.ROLLOUT_TARGETS < 1 || p.ROLLOUT_TARGETS > 8
                || !finitePositive(p.ROLLOUT_TARGET_HORIZON) || !finitePositive(p.ROLLOUT_ROUTE_HORIZON)
                || !finitePositive(p.ROLLOUT_WIN_VALUE)
                || !unit(p.ROLLOUT_ROBUSTNESS) || !unit(p.ROLLOUT_THREAT_WEIGHT)
                || !Double.isFinite(p.ROLLOUT_TAIL) || p.ROLLOUT_TAIL < 0
                || !Double.isFinite(p.ROLLOUT_TRACK_WEIGHT) || p.ROLLOUT_TRACK_WEIGHT < 0
                || !Double.isFinite(p.ROLLOUT_LIVE_WEIGHT) || p.ROLLOUT_LIVE_WEIGHT < 0
                || p.ROLLOUT_LIVE_TURNS < 1 || p.ROLLOUT_LIVE_TURNS > 10
                || !Double.isFinite(p.ROLLOUT_OPP_TRACK_VALUE) || p.ROLLOUT_OPP_TRACK_VALUE < 0
                || !Double.isFinite(p.ROLLOUT_OWN_TRACK_VALUE) || p.ROLLOUT_OWN_TRACK_VALUE < 0) {
            throw new IllegalArgumentException("Rollout requires an independent policy and bounded valid parameters");
        }
    }

    private static boolean finitePositive(double v) { return Double.isFinite(v) && v > 0; }
    private static boolean unit(double v) { return Double.isFinite(v) && v >= 0 && v <= 1; }
    public String name() { return "current?" + p.diff(); }

    public void init(GameState s, int me) {
        pathing = new Pathing(s.size);
        if (p.USE_ROLLOUT_ALL_CELL_RISK) tactical = new TacticalPathing(s.size);
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
        oppTracks = new int[s.regionCount]; ownTracks = new int[s.regionCount];
        priority = new double[s.regionCount]; shortlist = new int[p.ROLLOUT_TARGETS];
        if (p.USE_ROLLOUT_LIVE_TARGET) {
            placedTurn = new int[s.size]; lastOwner = s.owner.clone(); live = new int[2][s.regionCount];
            // Tracks already present at the first observation have no known age.
            Arrays.fill(placedTurn, Integer.MIN_VALUE / 2);
        }
        roots = new int[p.ROLLOUT_WIDTH + 1 + (p.USE_ROLLOUT_SPREAD ? p.ROLLOUT_SPREAD_ROOTS : 0)
                + (p.USE_ROLLOUT_NETWORK || p.USE_ROLLOUT_NETWORK_AFTER_BASE ? p.ROLLOUT_NETWORK_ROOTS : 0)
                + (p.USE_ROLLOUT_NETWORK_CHORDS ? p.ROLLOUT_NETWORK_ROOTS : 0)][GameState.PAINT_PER_TURN];
        rootCounts = new int[roots.length]; rootTargets = new int[roots.length];
        prefix = new int[GameState.PAINT_PER_TURN]; bestCells = new int[GameState.PAINT_PER_TURN];
        if (p.USE_ROLLOUT_ROOT_FILL) { filledRoot = new int[GameState.PAINT_PER_TURN]; fillMarks = new byte[s.size]; }
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
        if (p.USE_ROLLOUT_QUICK_ROUTE) quickPath = new int[s.size];
        if (p.USE_ROLLOUT_ROUTE_DEPENDENCIES) {
            routeDependencies = new int[2][p.ROLLOUT_WIDTH][s.regionCount];
            dependencyCounts = new int[2][p.ROLLOUT_WIDTH];
            proposalDependencies = new int[s.regionCount];
            if (p.USE_ROLLOUT_QUICK_ROUTE) quickDependencies = new int[s.regionCount];
        }
        if (p.USE_ROLLOUT_DIVERSE_REPLIES) replyRoutes = new int[p.ROLLOUT_REPLIES];
        if (p.USE_ROLLOUT_SECOND_TARGET) rootAlternateTargets = new int[roots.length];
        if (p.USE_ROLLOUT_SPREAD) {
            spreadFirst = new int[roots.length]; spreadSecond = new int[roots.length];
            spreadQuota = new int[roots.length];
        }
        if (p.USE_ROLLOUT_NETWORK || p.USE_ROLLOUT_NETWORK_AFTER_BASE) {
            networkCells = new int[s.size]; networkWeights = new int[s.size];
            networkPriority = new int[s.size]; networkGains = new int[s.size];
            networkBest = new int[p.ROLLOUT_NETWORK_ROOTS]; networkCompared = new boolean[roots.length];
        }
        if (p.USE_ROLLOUT_NETWORK_CHORDS) {
            int capacity = 8 * s.size;
            chordA = new int[capacity]; chordB = new int[capacity]; chordC = new int[capacity];
            chordOrder = new int[capacity]; chordPriority = new int[capacity]; chordGains = new int[capacity];
            chordBest = new int[p.ROLLOUT_NETWORK_ROOTS];
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
        selectedRootFilled = false;
        if (p.USE_ROLLOUT_ROOT_FILL) fillCells = 0;
        routeQueries = completedComparisons = simulatedTurns = 0;
        replanCalls = replanChanges = 0;
        extraTargetComparisons = 0; selectedExtraTarget = false;
        spreadRoots = spreadComparisons = spreadBuildTurns = 0; selectedSpread = false;
        networkEligible = networkEvaluated = networkRoots = networkComparisons = 0;
        selectedNetwork = false; selectedNetworkCell = -1;
        chordEligible = chordEvaluated = chordRoots = chordComparisons = 0;
        selectedChord = networkPhaseComplete = chordSearchStarted = false;
        chordRootStart = Integer.MAX_VALUE;
        ordinarySearchComplete = networkSearchStarted = false;
        ordinaryComparisons = ordinarySelectedCount = 0;
        ordinarySelectedRoute = ordinarySelectedTarget = -1;
        ordinarySelectedValue = Double.NaN;
        Arrays.fill(ordinarySelectedCells, 0);
        selectedOpening = false; openingCount = openingPairs = openingEligible = openingPaint = 0;
        if (opening != null) opening.reset(s, me);
        if (p.USE_ROLLOUT_NETWORK) {
            Arrays.fill(networkCompared, false); Arrays.fill(networkGains, Integer.MIN_VALUE);
        }
        selectedRoute = selectedTarget = -1; selectedValue = Double.NaN;
        liveTargets = forcedTargets = 0;
        if (p.USE_ROLLOUT_LIVE_TARGET) observeLive(s);
        quickPlayer = -1; quickLength = quickCost = quickGain = 0;
        quickSlot = -1;
        quickValue = 0;
        if (budgetMs <= 0) return List.of(Action.waitAction());
        Arrays.fill(counts, 0);
        // A usable sabotage answer exists before route enumeration and all speculative work.
        int fallbackTarget = chooseTarget(s, me);
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
            for (int variant = 0; variant < (p.USE_ROLLOUT_ALL_CELL_RISK ? 4 : 3); variant++) {
                if (timer.up(reserve) || routeQueries >= 2 * p.ROLLOUT_MAX_ROUTES) break;
                propose(s, me, pair, variant);
                if (variant < 3 && !timer.up(reserve)
                        && (!p.USE_ROLLOUT_ALL_CELL_RISK || routeQueries < 2 * p.ROLLOUT_MAX_ROUTES))
                    propose(s, 1 - me, pair, variant);
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
            if (p.USE_ROLLOUT_SECOND_TARGET) secondTarget = -1;
            rootTargets[i] = timer.up() ? fallbackTarget : chooseTarget(targetBoard.state, me);
            if (p.USE_ROLLOUT_SECOND_TARGET) rootAlternateTargets[i] = secondTarget;
        }
        if (p.USE_ROLLOUT_SPREAD && !timer.up()) rootSize = addSpreadRoots(s, me, rootSize);
        if (p.USE_ROLLOUT_NETWORK && !timer.up()) rootSize = addNetworkRoots(s, me, rootSize,
                (int) Math.min(p.ROLLOUT_NETWORK_RESERVE_MS, budgetMs / 3));
        bestCount = rootCounts[0];
        System.arraycopy(roots[0], 0, bestCells, 0, bestCount);
        selectedTarget = rootTargets[0];
        selectedRoute = counts[me] == 0 ? -1 : 0;
        best = -Double.MAX_VALUE;
        int replies = Math.max(1, Math.min(p.ROLLOUT_REPLIES, counts[1 - me]));
        if (p.USE_ROLLOUT_DIVERSE_REPLIES) selectReplyRoutes(1 - me, replies);
        boolean complete = searchRoots(s, me, 0, rootSize, replies);
        saveOrdinaryChoice(complete);
        if (p.USE_ROLLOUT_NETWORK_AFTER_BASE) searchNetworkAfterBase(s, me, replies, budgetMs);
        // Ordinary roots retain their budget priority; only the winning construction gets an extra target.
        if (p.USE_ROLLOUT_SECOND_TARGET && completedComparisons > 0 && !timer.up()) {
            int root = selectedRoute < 0 ? counts[me] : selectedRoute;
            int target = rootAlternateTargets[root];
            if (target >= 0 && target != selectedTarget) {
                double sum = 0, worst = Double.MAX_VALUE;
                boolean covered = true;
                for (int reply = 0; reply < replies; reply++) {
                    double value = rollout(s, me, root, target,
                            p.USE_ROLLOUT_DIVERSE_REPLIES ? replyRoutes[reply] : reply);
                    if (!Double.isFinite(value)) { covered = false; break; }
                    sum += value; worst = Math.min(worst, value);
                }
                if (covered) {
                    completedComparisons++; extraTargetComparisons++;
                    double value = (1 - p.ROLLOUT_ROBUSTNESS) * sum / replies + p.ROLLOUT_ROBUSTNESS * worst;
                    if (value > best) {
                        selectedValue = value; selectedTarget = target; selectedExtraTarget = true;
                    }
                }
            }
        }
        return actions(s);
    }

    private List<Action> actions(GameState s) {
        if (p.USE_ROLLOUT_FILL && !selectedRootFilled) fill(s, realPlayer);
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
        byte[] m = p.USE_ROLLOUT_ROOT_FILL ? fillMarks : new byte[n]; // 1 own, 3 earning, 4 proposed route
        if (p.USE_ROLLOUT_ROOT_FILL) Arrays.fill(m, (byte) 0);
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
        for (int order = first; order < end; order++) {
            int root = searchRoot(order);
            boolean duplicate = false;
            for (int j = 0; j < root && root < ordinaryRootSize; j++) {
                if (sameSet(roots[root], rootCounts[root], roots[j], rootCounts[j])) duplicate = true;
            }
            if (duplicate) continue;
            for (int attack = 0; attack < 2; attack++) {
                int target = attack == 0 ? rootTargets[root] : -1;
                if (attack == 1 && rootTargets[root] < 0) continue;
                if (p.USE_ROLLOUT_ROOT_FILL) {
                    if (timer.up()) return false;
                    System.arraycopy(roots[root], 0, filledRoot, 0, rootCounts[root]);
                    filledRootCount = fill(s, me, filledRoot, rootCounts[root], target);
                    if (timer.up()) return false; // never score a deadline-truncated fill
                }
                double sum = 0, worst = Double.MAX_VALUE;
                for (int reply = 0; reply < replies; reply++) {
                    double value = rollout(s, me, root, target,
                            p.USE_ROLLOUT_DIVERSE_REPLIES ? replyRoutes[reply] : reply);
                    if (!Double.isFinite(value)) return false; // discard ALL partial scenario coverage
                    sum += value; worst = Math.min(worst, value);
                }
                completedComparisons++;
                if (p.USE_ROLLOUT_SPREAD && root >= ordinaryRootSize) spreadComparisons++;
                if ((p.USE_ROLLOUT_NETWORK || p.USE_ROLLOUT_NETWORK_AFTER_BASE) && root >= ordinaryRootSize) {
                    if (root >= chordRootStart) chordComparisons++;
                    else { networkComparisons++; networkCompared[root] = true; }
                }
                double value = (1 - p.ROLLOUT_ROBUSTNESS) * sum / replies + p.ROLLOUT_ROBUSTNESS * worst;
                if (value > best) {
                    best = value; selectedValue = value;
                    selectedRoute = root == counts[me] ? -1 : root; selectedTarget = target;
                    selectedSpread = p.USE_ROLLOUT_SPREAD && root >= ordinaryRootSize;
                    selectedNetwork = (p.USE_ROLLOUT_NETWORK || p.USE_ROLLOUT_NETWORK_AFTER_BASE)
                            && root >= ordinaryRootSize && root < chordRootStart;
                    selectedChord = root >= chordRootStart;
                    selectedNetworkCell = selectedNetwork ? roots[root][0] : -1;
                    bestCount = p.USE_ROLLOUT_ROOT_FILL ? filledRootCount : rootCounts[root];
                    System.arraycopy(p.USE_ROLLOUT_ROOT_FILL ? filledRoot : roots[root], 0, bestCells, 0, bestCount);
                    if (p.USE_ROLLOUT_ROOT_FILL) { selectedRootFilled = true; fillCells = bestCount - rootCounts[root]; }
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
        searchChordsAfterNetwork(s, me, end, replies, budgetMs);
    }

    /** Every completed singleton comparison keeps priority over these additional packages. */
    private void searchChordsAfterNetwork(GameState s, int me, int first, int replies, long budgetMs) {
        if (!p.USE_ROLLOUT_NETWORK_CHORDS || !ordinarySearchComplete || !networkPhaseComplete || timer.up()) return;
        int reserve = (int) Math.min(p.ROLLOUT_NETWORK_RESERVE_MS, budgetMs / 3);
        if (timer.up(reserve)) return;
        chordSearchStarted = true;
        chordRootStart = first;
        int end = addChordRoots(s, me, first, reserve);
        searchRoots(s, me, first, end, replies);
    }

    /** Keep scenario count fixed, preferring another project over its reverse construction. */
    private void selectReplyRoutes(int enemy, int replies) {
        replyRoutes[0] = 0;
        for (int slot = 1; slot < replies; slot++) {
            int chosen = -1;
            for (int mode = 0; mode < 3 && chosen < 0; mode++) {
                for (int route = 1; route < counts[enemy]; route++) {
                    boolean distinct = true;
                    for (int j = 0; j < slot; j++) {
                        int previous = replyRoutes[j];
                        if (route == previous || mode == 0 && sameProject(enemy, route, previous)
                                || mode == 1 && sameCorridor(enemy, route, previous)) distinct = false;
                    }
                    if (distinct) { chosen = route; break; }
                }
            }
            replyRoutes[slot] = chosen;
        }
    }

    private boolean sameProject(int player, int a, int b) {
        int[] x = routes[player][a], y = routes[player][b];
        int xn = lengths[player][a], yn = lengths[player][b];
        return x[0] == y[0] && x[xn - 1] == y[yn - 1]
                || x[0] == y[yn - 1] && x[xn - 1] == y[0];
    }

    private boolean sameCorridor(int player, int a, int b) {
        int n = lengths[player][a];
        if (n != lengths[player][b]) return false;
        boolean forward = true, reverse = true;
        for (int i = 0; i < n; i++) {
            if (routes[player][a][i] != routes[player][b][i]) forward = false;
            if (routes[player][a][i] != routes[player][b][n - 1 - i]) reverse = false;
        }
        return forward || reverse;
    }

    private void propose(GameState s, int me, int pair, int variant) {
        if (variant == 2 && threats[me] < 0) return;
        if (variant == 3) {
            boolean own = false, risk = false;
            for (int i = 0; i < s.pairPathLen[pair]; i++) {
                int c = s.pairPath[pair][i], r = s.regionId[c];
                own |= s.owner[c] == me;
                risk |= s.regionInstability[r] > 0 && !s.regionHasTown(r);
            }
            if (!own || !risk) return;
        }
        for (int c = 0; c < s.size; c++) {
            extra[c] = (int) Math.round(p.UNSTABLE_COST * s.instability[c]);
            if (variant == 3 && !s.passable(c)) extra[c] += s.placeCost(c);
            blocked[c] = variant == 1 ? s.owner[c] == 1 - me
                    : variant == 2 && s.regionId[c] == threats[me];
        }
        int n = variant == 3 ? tactical.weightedPath(s, s.townCell[s.pairFrom[pair]], s.townCell[s.pairTo[pair]], extra, path)
                : pathing.cheapestPath(s, s.townCell[s.pairFrom[pair]], s.townCell[s.pairTo[pair]],
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
        if (p.USE_ROLLOUT_ROUTE_DEPENDENCIES) captureDependencies(completion.state, pair);
        double gain = income[me] - income[1 - me] - baseDiff[me];
        // Reuse the bounded proposal pool: no extra path query or random draw.
        // Current own income is zero, so a positive completed own income is also a gain.
        boolean quickEligible = me == quickPlayer && cost <= p.ROLLOUT_QUICK_MAX_PAINT && income[me] > 0 && gain > 0;
        double rawGain = gain;
        if (quickEligible && (!p.USE_ROLLOUT_QUICK_SAFE || threats[me] < 0))
            considerQuick(s, me, n, cost, gain);
        if (threats[me] >= 0) {
            Rules.ink(completion.state, threats[me]); completion.refresh();
            Rules.incomePerTurn(completion.state, income);
            double protectedGain = income[me] - income[1 - me] - threatDiff[me];
            if (quickEligible && p.USE_ROLLOUT_QUICK_SAFE && protectedGain > 0)
                considerQuick(s, me, n, cost, rawGain);
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
                if (p.USE_ROLLOUT_ROUTE_DEPENDENCIES) copyDependencies(me, j + 1, j);
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
            if (p.USE_ROLLOUT_ROUTE_DEPENDENCIES) copyDependencies(me, i - 1, i);
        }
        System.arraycopy(path, 0, routes[me][slot], 0, length);
        lengths[me][slot] = length; values[me][slot] = value;
        if (p.USE_ROLLOUT_ROUTE_DEPENDENCIES) {
            System.arraycopy(proposalDependencies, 0, routeDependencies[me][slot], 0, proposalDependencyCount);
            dependencyCounts[me][slot] = proposalDependencyCount;
        }
    }

    /**
     * cheapestPath can omit existing connector cells inside a rail block. Retain a canonical
     * completed-path witness separately, leaving the proposed build sequence and cost intact.
     * This is conservative: an inked witness region abandons the project even if another
     * detour survives. The next real observation generates a fresh portfolio as usual.
     */
    private void captureDependencies(GameState completed, int pair) {
        proposalDependencyCount = 0;
        Arrays.fill(marked, false);
        for (int i = 0; i < completed.pairPathLen[pair]; i++) {
            int region = completed.regionId[completed.pairPath[pair][i]];
            if (marked[region] || completed.regionHasTown(region)) continue;
            marked[region] = true;
            proposalDependencies[proposalDependencyCount++] = region;
        }
    }

    private void copyDependencies(int player, int from, int to) {
        int n = dependencyCounts[player][from];
        System.arraycopy(routeDependencies[player][from], 0, routeDependencies[player][to], 0, n);
        dependencyCounts[player][to] = n;
    }

    private boolean missingDependency(GameState s, int player, int route) {
        for (int i = 0; i < dependencyCounts[player][route]; i++)
            if (s.regionInked[routeDependencies[player][route][i]]) return true;
        return false;
    }

    private void considerQuick(GameState s, int me, int n, int cost, double gain) {
        double value = 0;
        if (p.USE_ROLLOUT_QUICK_VALUE) {
            int turns = quickBuildTurns(s, me, n);
            double earning = Math.min(p.ROLLOUT_ROUTE_HORIZON, s.turnsLeft()) - turns + 1;
            if (turns <= 0 || earning <= 0) return;
            value = gain * earning / turns;
        }
        boolean better = p.USE_ROLLOUT_QUICK_VALUE
                ? value > quickValue || value == quickValue && cost < quickCost
                : cost < quickCost || cost == quickCost && gain > quickGain;
        if (quickLength == 0 || better) {
            System.arraycopy(path, 0, quickPath, 0, n);
            quickLength = n; quickCost = cost; quickGain = (int) gain; quickValue = value;
            if (p.USE_ROLLOUT_ROUTE_DEPENDENCIES) {
                System.arraycopy(proposalDependencies, 0, quickDependencies, 0, proposalDependencyCount);
                quickDependencyCount = proposalDependencyCount;
            }
        }
    }

    /** Exact affordable packing on a private board, without opponent actions or income rules. */
    private int quickBuildTurns(GameState s, int me, int n) {
        targetBoard.set(s);
        for (int turns = 0; turns < p.ROLLOUT_QUICK_MAX_PAINT; turns++) {
            int count = pack(targetBoard.state, path, n, prefix, 0, GameState.PAINT_PER_TURN);
            if (count == 0) return turns;
            Rules.applyPlacements(targetBoard.state, me == 0 ? prefix : moves[0], me == 0 ? count : 0,
                    me == 1 ? prefix : moves[1], me == 1 ? count : 0);
        }
        return p.ROLLOUT_QUICK_MAX_PAINT;
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
        if (p.USE_ROLLOUT_ROUTE_DEPENDENCIES) {
            System.arraycopy(quickDependencies, 0, routeDependencies[me][slot], 0, quickDependencyCount);
            dependencyCounts[me][slot] = quickDependencyCount;
        }
        quickSlot = slot;
        // Values are only used during proposal insertion, which has finished.
        values[me][slot] = 0;
    }

    /** Keep the ordinary first comparison/fallback, then evaluate quick income before other roots. */
    private int searchRoot(int order) {
        if (p.USE_ROLLOUT_NETWORK) {
            if (order == 0) return 0;
            return order <= networkRoots ? ordinaryRootSize + order - 1 : order - networkRoots;
        }
        if (p.USE_ROLLOUT_SPREAD) {
            if (order == 0) return 0;
            return order <= spreadRoots ? ordinaryRootSize + order - 1 : order - spreadRoots;
        }
        if (!p.USE_ROLLOUT_QUICK_EARLY || quickSlot <= 0) return order;
        if (order == 1) return quickSlot;
        return order > 1 && order <= quickSlot ? order - 1 : order;
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

    /** Reuse the single-cell phase limits for a separate bounded, later package comparison. */
    private int addChordRoots(GameState s, int me, int size, int reserve) {
        chordEligible = NetworkChords.collect(s, chordA, chordB, chordC);
        for (int i = 0; i < chordEligible; i++) {
            chordOrder[i] = i;
            chordPriority[i] = NetworkCells.adjacentConnectorWeight(s, chordA[i], networkWeights)
                    + NetworkCells.adjacentConnectorWeight(s, chordB[i], networkWeights)
                    + (chordC[i] < 0 ? 0 : NetworkCells.adjacentConnectorWeight(s, chordC[i], networkWeights));
        }
        Arrays.fill(chordBest, -1);
        Rules.incomePerTurn(s, income);
        int before = income[me] - income[1 - me];
        int limit = Math.min(chordEligible, p.ROLLOUT_NETWORK_MAX_CELLS);
        for (int i = 0; i < limit && !timer.up(reserve); i++) {
            int best = i;
            for (int j = i + 1; j < chordEligible; j++) {
                int a = chordOrder[j], b = chordOrder[best];
                if (chordPriority[a] > chordPriority[b]
                        || chordPriority[a] == chordPriority[b] && chordCheaper(s, a, b)) best = j;
            }
            int candidate = chordOrder[best]; chordOrder[best] = chordOrder[i]; chordOrder[i] = candidate;
            completion.set(s);
            completion.state.owner[chordA[candidate]] = me;
            completion.state.owner[chordB[candidate]] = me;
            if (chordC[candidate] >= 0) completion.state.owner[chordC[candidate]] = me;
            completion.refresh(); Rules.incomePerTurn(completion.state, income);
            int gain = income[me] - income[1 - me] - before;
            chordGains[candidate] = gain; chordEvaluated++;
            if (gain <= 0) continue;
            int n = chordCells(candidate, prefix);
            boolean duplicate = false;
            for (int root = 0; root < size; root++)
                if (sameSet(prefix, n, roots[root], rootCounts[root])) duplicate = true;
            if (duplicate) continue;
            for (int slot = 0; slot < chordBest.length; slot++) {
                int other = chordBest[slot];
                if (other >= 0 && (gain < chordGains[other]
                        || gain == chordGains[other] && !chordCheaper(s, candidate, other))) continue;
                for (int j = chordBest.length - 1; j > slot; j--) chordBest[j] = chordBest[j - 1];
                chordBest[slot] = candidate; break;
            }
        }
        for (int candidate : chordBest) {
            if (candidate < 0 || timer.up()) break;
            int count = chordCells(candidate, roots[size]);
            targetBoard.set(s); placeOwn(targetBoard, me, roots[size], count);
            int target = chooseTarget(targetBoard.state, me);
            if (timer.up()) break;
            rootCounts[size] = count; rootTargets[size] = target;
            size++; chordRoots++;
        }
        return size;
    }

    private int chordCells(int candidate, int[] out) {
        out[0] = chordA[candidate]; out[1] = chordB[candidate];
        if (chordC[candidate] < 0) return 2;
        out[2] = chordC[candidate]; return 3;
    }

    private boolean chordCheaper(GameState s, int a, int b) {
        int aCost = s.placeCost(chordA[a]) + s.placeCost(chordB[a]) + (chordC[a] < 0 ? 0 : s.placeCost(chordC[a]));
        int bCost = s.placeCost(chordA[b]) + s.placeCost(chordB[b]) + (chordC[b] < 0 ? 0 : s.placeCost(chordC[b]));
        return aCost < bCost || aCost == bCost && (chordA[a] < chordA[b]
                || chordA[a] == chordA[b] && (chordB[a] < chordB[b]
                || chordB[a] == chordB[b] && chordC[a] < chordC[b]));
    }

    /** First ranked independent pair with a genuinely split action; at most two extra roots. */
    private int addSpreadRoots(GameState s, int me, int size) {
        for (int a = 0; a < counts[me] && !timer.up(); a++) {
            for (int b = a + 1; b < counts[me] && !timer.up(); b++) {
                if (sameProject(me, a, b) || !independentRegions(s, me, a, b)) continue;
                for (int quota = 2; quota >= 1 && spreadRoots < p.ROLLOUT_SPREAD_ROOTS; quota--) {
                    int n = fillSpread(s, me, a, b, quota, roots[size]);
                    if (!packedBoth) continue;
                    boolean duplicate = false;
                    for (int j = 0; j < size; j++)
                        if (sameSet(roots[size], n, roots[j], rootCounts[j])) duplicate = true;
                    if (duplicate) continue;
                    targetBoard.set(s); placeOwn(targetBoard, me, roots[size], n);
                    int target = chooseTarget(targetBoard.state, me);
                    if (timer.up()) return size;
                    rootCounts[size] = n; rootTargets[size] = target;
                    spreadFirst[size] = a; spreadSecond[size] = b; spreadQuota[size] = quota;
                    size++; spreadRoots++;
                }
                if (spreadRoots > 0) return size;
            }
        }
        return size;
    }

    /** A single legal region destruction must not sever both retained projects. */
    private boolean independentRegions(GameState s, int me, int a, int b) {
        Arrays.fill(marked, false);
        for (int i = 0; i < lengths[me][a]; i++) {
            int region = s.regionId[routes[me][a][i]];
            if (!s.regionHasTown(region)) marked[region] = true;
        }
        for (int i = 0; i < lengths[me][b]; i++)
            if (marked[s.regionId[routes[me][b][i]]]) return false;
        return true;
    }

    private int remainingPaint(GameState s, int me, int route) {
        if (p.USE_ROLLOUT_ROUTE_DEPENDENCIES && missingDependency(s, me, route)) return -1;
        int paint = 0;
        for (int i = 0; i < lengths[me][route]; i++) {
            int c = routes[me][route][i];
            if (s.inked[c]) return -1;
            if (s.canBuild(c)) paint += s.placeCost(c);
        }
        return paint;
    }

    /** Keep the same split throughout a forecast, finish affordable projects, abandon inked ones. */
    private int fillSpread(GameState s, int me, int a, int b, int quota, int[] out) {
        packedBoth = false;
        int needA = remainingPaint(s, me, a), needB = remainingPaint(s, me, b);
        if (needA <= 0) return fillBuild(s, me, b, out);
        if (needB <= 0 || needA <= GameState.PAINT_PER_TURN) return fillBuild(s, me, a, out);
        if (needB <= GameState.PAINT_PER_TURN) return fillBuild(s, me, b, out);
        int count = pack(s, routes[me][a], lengths[me][a], out, 0, quota);
        int paint = GameState.PAINT_PER_TURN;
        for (int i = 0; i < count; i++) paint -= s.placeCost(out[i]);
        int next = pack(s, routes[me][b], lengths[me][b], out, count, paint);
        if (count == 0 || next == count) return fillBuild(s, me, a, out);
        packedBoth = true;
        for (int i = count; i < next; i++) paint -= s.placeCost(out[i]);
        count = next;
        for (int route = 0; route < counts[me] && paint > 0; route++) {
            if (remainingPaint(s, me, route) <= 0) continue;
            next = pack(s, routes[me][route], lengths[me][route], out, count, paint);
            for (int i = count; i < next; i++) paint -= s.placeCost(out[i]);
            count = next;
        }
        return count;
    }

    /** Pack real paint costs, including leftovers across projects; never mutate the pre-turn board. */
    private int fillBuild(GameState s, int me, int first, int[] out) {
        int count = 0, paint = GameState.PAINT_PER_TURN;
        for (int pass = -1; pass < counts[me] && paint > 0; pass++) {
            int route = pass < 0 ? first : pass;
            if (route < 0 || route >= counts[me] || pass == first) continue;
            if (p.USE_ROLLOUT_ROUTE_DEPENDENCIES && missingDependency(s, me, route)) continue;
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
        for (int turn = 0; turn < depth; turn++) {
            if (timer.up()) return Double.NaN;
            GameState s = work.state;
            if (p.USE_ROLLOUT_REPLAN && turn == p.ROLLOUT_REPLAN_TURN) {
                ownFirst = reconsider(s, me, ownFirst);
                if (ownFirst == -2) return Double.NaN;
            }
            for (int player = 0; player < 2; player++) {
                if (turn == 0 && player == me) {
                    moveCounts[player] = p.USE_ROLLOUT_ROOT_FILL ? filledRootCount : rootCounts[root];
                    System.arraycopy(p.USE_ROLLOUT_ROOT_FILL ? filledRoot : roots[root], 0,
                            moves[player], 0, moveCounts[player]);
                    targets[player] = ownTarget;
                } else {
                    int first = player == me ? ownFirst : reply;
                    if (p.USE_ROLLOUT_SPREAD && player == me && root >= ordinaryRootSize) {
                        moveCounts[player] = fillSpread(s, me, spreadFirst[root], spreadSecond[root],
                                spreadQuota[root], moves[player]);
                        if (packedBoth) spreadBuildTurns++;
                    } else moveCounts[player] = fillBuild(s, player, first, moves[player]);
                    // MISSED_DEADLINE_BEGIN
                    if (deadlineProbeActive && moveCounts[player] == 0) {
                        // The resolved pre-turn board already has the same canonical paths.
                        deadlineReusedBoards++;
                        targets[player] = chooseTarget(s, player);
                    } else {
                    // MISSED_DEADLINE_END
                    targetBoard.set(s);
                    placeOwn(targetBoard, player, moves[player], moveCounts[player]);
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

    /** Revalue only retained continuations on the evolved simulated board; -2 discards partial work. */
    private int reconsider(GameState s, int me, int previous) {
        replanCalls++;
        Rules.incomePerTurn(s, income);
        int before = income[me] - income[1 - me], chosen = previous;
        double best = 0;
        for (int pass = -1; pass < counts[me]; pass++) {
            if (timer.up()) return -2;
            int route = pass < 0 ? previous : pass;
            if (route < 0 || route >= counts[me] || pass == previous) continue;
            if (p.USE_ROLLOUT_ROUTE_DEPENDENCIES && missingDependency(s, me, route)) continue;
            int cost = 0;
            boolean valid = true;
            completion.set(s);
            for (int i = 0; i < lengths[me][route]; i++) {
                int cell = routes[me][route][i];
                if (s.inked[cell]) { valid = false; break; }
                if (s.canBuild(cell)) {
                    cost += s.placeCost(cell); completion.state.owner[cell] = me;
                }
            }
            if (!valid || cost == 0) continue;
            int turns = (cost + GameState.PAINT_PER_TURN - 1) / GameState.PAINT_PER_TURN;
            double earning = Math.min(p.ROLLOUT_ROUTE_HORIZON, s.turnsLeft()) - turns + 1;
            if (earning <= 0) continue;
            completion.refresh(); Rules.incomePerTurn(completion.state, income);
            double value = (income[me] - income[1 - me] - before) * earning / turns;
            if (value > best) { best = value; chosen = route; }
        }
        if (timer.up()) return -2;
        if (chosen != previous) replanChanges++;
        return chosen;
    }

    /** Cheap exposure shortlist, then exact core ink evaluation. No actual future enemy action is read. */
    private int chooseTarget(GameState s, int me) {
        if (p.USE_ROLLOUT_SECOND_TARGET) secondTarget = -1;
        if (!p.USE_DISRUPT) return -1;
        Arrays.fill(trackBalance, 0); Arrays.fill(priority, 0);
        Arrays.fill(oppTracks, 0); Arrays.fill(ownTracks, 0);
        for (int c = 0; c < s.size; c++) {
            if (s.owner[c] == 1 - me) { trackBalance[s.regionId[c]]++; oppTracks[s.regionId[c]]++; }
            else if (s.owner[c] == me) { trackBalance[s.regionId[c]]--; ownTracks[s.regionId[c]]++; }
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
        int[] recent = p.USE_ROLLOUT_LIVE_TARGET ? live[1 - me] : null;
        Arrays.fill(shortlist, -1);
        for (int r = 0; r < s.regionCount; r++) {
            if (s.regionInked[r] || s.regionHasTown(r)) continue;
            priority[r] = (priority[r] * p.ROLLOUT_TARGET_HORIZON + trackValue(r, me)
                    + (recent == null ? 0 : recent[r] * p.ROLLOUT_LIVE_WEIGHT))
                    / (GameState.INK_THRESHOLD - s.regionInstability[r]);
            for (int i = 0; i < shortlist.length; i++) {
                if (shortlist[i] < 0 || priority[r] > priority[shortlist[i]]) {
                    for (int j = shortlist.length - 1; j > i; j--) shortlist[j] = shortlist[j - 1];
                    shortlist[i] = r; break;
                }
            }
        }
        Rules.incomePerTurn(s, income);
        int before = income[me] - income[1 - me], bestTarget = -1, forcedTarget = -1;
        double best = 0, second = 0, forced = -Double.MAX_VALUE;
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
                if (s.score[me] - s.score[1 - me] + after > 0) {
                    if (p.USE_ROLLOUT_SECOND_TARGET) secondTarget = -1;
                    return r;
                }
                continue;
            }
            double value = ((after - before) * Math.max(0, Math.min(p.ROLLOUT_TARGET_HORIZON, s.turnsLeft()) - needed + 1)
                    + trackValue(r, me)
                    + (recent == null ? 0 : recent[r] * p.ROLLOUT_LIVE_WEIGHT)) / needed;
            // A wasted disruption point never returns; prefer a harmless hit over WAIT.
            if (p.ROLLOUT_ALWAYS_DISRUPT && after >= before && trackBalance[r] >= 0 && value > forced) {
                forced = value; forcedTarget = r;
            }
            if (value > best) {
                if (p.USE_ROLLOUT_SECOND_TARGET) { second = best; secondTarget = bestTarget; }
                best = value; bestTarget = r;
            } else if (p.USE_ROLLOUT_SECOND_TARGET && value > second) {
                second = value; secondTarget = r;
            }
        }
        if (p.USE_ROLLOUT_SECOND_TARGET && timer.up()) secondTarget = -1;
        if (bestTarget < 0 && forcedTarget >= 0 && !timer.up()) { forcedTargets++; return forcedTarget; }
        if (bestTarget >= 0 && recent != null && recent[bestTarget] > 0) liveTargets++;
        return bestTarget;
    }

    /** Per-region track term of the target valuation; the flag-off branch is the v08 expression. */
    private double trackValue(int region, int chooser) {
        return p.USE_ROLLOUT_TRACK_VALUE && (!p.ROLLOUT_TRACK_VALUE_SELF_ONLY || chooser == realPlayer)
                ? oppTracks[region] * p.ROLLOUT_OPP_TRACK_VALUE - ownTracks[region] * p.ROLLOUT_OWN_TRACK_VALUE
                : trackBalance[region] * p.ROLLOUT_TRACK_WEIGHT;
    }

    /**
     * Observed placements since the previous real turn reveal each player's live project. Only
     * changes between observations count; the rollout reuses these counts unchanged.
     */
    private void observeLive(GameState s) {
        for (int c = 0; c < s.size; c++) {
            if (s.owner[c] != lastOwner[c]) {
                if (s.owner[c] >= 0) placedTurn[c] = s.turn;
                lastOwner[c] = s.owner[c];
            }
        }
        Arrays.fill(live[0], 0); Arrays.fill(live[1], 0);
        for (int c = 0; c < s.size; c++) {
            int o = s.owner[c];
            if ((o == 0 || o == 1) && placedTurn[c] > s.turn - p.ROLLOUT_LIVE_TURNS) live[o][s.regionId[c]]++;
        }
    }
}
