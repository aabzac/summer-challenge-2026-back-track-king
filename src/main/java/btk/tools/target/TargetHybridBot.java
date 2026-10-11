package btk.tools.target;

import btk.bot.Bot;
import btk.bot.Params;
import btk.bot.Timer;
import btk.bot.current.RolloutBot;
import btk.core.Action;
import btk.core.GameState;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/** Local-only experiment. The unchanged v09 policy supplies the entire emitted construction;
 * at most one deterministic frozen neural target is compared against that same full action.
 * This intentionally depends on private RolloutBot internals and must not be submitted to CG.
 * The baseline keeps its original allowance; its tiny entry overhead is not a hard watchdog.
 * Optional work shares min(baseline deadline, wrapper entry + allowance), with no refund. */
public final class TargetHybridBot implements Bot {
    public static final double EPS = 1e-9;
    public static final int BASELINE = 1, FEATURES = 2, EVALUATE_E = 3, EVALUATE_D = 4, COMMIT = 5;
    private final Params p;
    private final LongSupplier clock;
    private final TargetProposalFeatures.Deadline deadlineCheck;
    public final RolloutBot baseline;
    public TargetProposer proposer;
    private MethodHandle rollout;
    private Field timerDeadline;
    private Timer timer;
    private int[][] roots;
    private int[] rootCounts, counts;
    private final int[] fullCells = new int[GameState.PAINT_PER_TURN], savedCells = new int[GameState.PAINT_PER_TURN];
    public final double[] lastEReplyValues, lastDReplyValues;
    public int lastPhase, lastBaseTarget = -1, lastProposedTarget = -1, lastEmittedTarget = -1;
    public int lastFullCount, lastReplyCount, lastEReplies, lastDReplies;
    public long lastDeadlineNanos, lastRemainingNanos;
    public double lastEScore = Double.NaN, lastDScore = Double.NaN;
    public boolean lastEligible, lastPairComplete, lastAccepted, lastComparisonCutoff, lastInvalidComparison;

    public long turns, eligibleTurns, proposalAttempts, sameTarget, noTargetProposals, noRemainingBudget;
    public long featureCutoffs, invalidProposals, pairsCompleted, comparisonCutoffs, nonfiniteComparisons, acceptedTargets;
    public long remainingSamples, remainingNanosTotal, remainingNanosMin = Long.MAX_VALUE, remainingNanosMax;

    public TargetHybridBot(Params params) { this(params, System::nanoTime); }

    /** Clock injection only for deterministic deadline diagnostics; native rollout also checks its own Timer. */
    public TargetHybridBot(Params params, LongSupplier clock) {
        p = params.copy(); validate(p);
        this.clock = java.util.Objects.requireNonNull(clock);
        deadlineCheck = this::expired;
        Params ordinary = p.copy(); ordinary.USE_NEURAL_TARGET_PROPOSAL = false;
        baseline = new RolloutBot(ordinary); // Preserve the original NOISE and random stream exactly.
        lastEReplyValues = new double[p.ROLLOUT_REPLIES]; lastDReplyValues = new double[p.ROLLOUT_REPLIES];
    }

    private static void validate(Params p) {
        if (!p.USE_ROLLOUT_POLICY || !p.USE_ROLLOUT_QUICK_ROUTE || !p.USE_ROLLOUT_NETWORK_AFTER_BASE
                || !p.USE_ROLLOUT_FILL || !p.USE_PROTECTED_OPENING || !p.USE_CLOSE || !p.USE_DISRUPT
                || p.USE_ROLLOUT_ALL_CELL_RISK || p.USE_ROLLOUT_ROOT_FILL || p.USE_ROLLOUT_NETWORK
                || p.USE_ROLLOUT_ROUTE_DEPENDENCIES || p.USE_ROLLOUT_NETWORK_CHORDS || p.USE_ROLLOUT_RHEA
                || p.USE_ROLLOUT_SECOND_TARGET || p.USE_ROLLOUT_REPLAN || p.USE_ROLLOUT_SPREAD
                || p.USE_ROLLOUT_DIVERSE_REPLIES || p.USE_ROLLOUT_QUICK_EARLY || p.USE_ROLLOUT_QUICK_VALUE
                || p.USE_ROLLOUT_QUICK_SAFE || p.USE_ROLLOUT_LIVE_TARGET || p.ROLLOUT_ALWAYS_DISRUPT
                || p.USE_ROLLOUT_TRACK_VALUE || p.ROLLOUT_TRACK_VALUE_SELF_ONLY || p.USE_NEURAL_FALLBACK
                || p.USE_SPATIAL_V5_SEARCH || p.USE_SPATIAL_V6_SEARCH || p.USE_SPATIAL_V7_SEARCH
                || p.USE_SPATIAL_V8_SEARCH || p.USE_SPATIAL_V9_TOWN_CONTEXT)
            throw new IllegalArgumentException("Target hybrid requires the selected v09 baseline without other experiments");
    }

    @Override public String name() { return "current?" + p.diff(); }

    @Override public void init(GameState state, int me) {
        baseline.init(state, me); proposer = new TargetProposer(state, p.USE_CLOSE);
        try {
            roots = (int[][]) field("roots"); rootCounts = (int[]) field("rootCounts");
            counts = (int[]) field("counts"); timer = (Timer) field("timer");
            timerDeadline = Timer.class.getDeclaredField("deadlineNs"); timerDeadline.setAccessible(true);
            rollout = MethodHandles.privateLookupIn(RolloutBot.class, MethodHandles.lookup()).findVirtual(
                    RolloutBot.class, "rollout", MethodType.methodType(double.class, GameState.class,
                            int.class, int.class, int.class, int.class)).bindTo(baseline);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Local rollout adapter contract changed", e); }
        turns = eligibleTurns = proposalAttempts = sameTarget = noTargetProposals = noRemainingBudget = 0;
        featureCutoffs = invalidProposals = pairsCompleted = comparisonCutoffs = nonfiniteComparisons = acceptedTargets = 0;
        remainingSamples = remainingNanosTotal = remainingNanosMax = 0; remainingNanosMin = Long.MAX_VALUE;
        resetLast();
    }

    private Object field(String name) throws ReflectiveOperationException {
        Field f = RolloutBot.class.getDeclaredField(name); f.setAccessible(true); return f.get(baseline);
    }
    private long nativeDeadline() {
        try { return timerDeadline.getLong(timer); }
        catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }
    private void nativeDeadline(long value) {
        try { timerDeadline.setLong(timer, value); }
        catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }
    private boolean expired() { return clock.getAsLong() >= lastDeadlineNanos || timer.up(); }
    private static long deadline(long now, long ms) {
        long duration = Math.max(0, ms) > Long.MAX_VALUE / 1_000_000L ? Long.MAX_VALUE : Math.max(0, ms) * 1_000_000L;
        return duration > 0 && now > Long.MAX_VALUE - duration ? Long.MAX_VALUE : now + duration;
    }
    private void resetLast() {
        lastPhase = BASELINE; lastBaseTarget = lastProposedTarget = lastEmittedTarget = -1;
        lastFullCount = lastReplyCount = lastEReplies = lastDReplies = 0; lastRemainingNanos = 0;
        lastEScore = lastDScore = Double.NaN;
        lastEligible = lastPairComplete = lastAccepted = lastComparisonCutoff = lastInvalidComparison = false;
        Arrays.fill(lastEReplyValues, Double.NaN); Arrays.fill(lastDReplyValues, Double.NaN);
    }

    @Override public List<Action> play(GameState state, int me, long budgetMs) {
        if (proposer == null) throw new IllegalStateException("init must precede play");
        long entry = clock.getAsLong(); resetLast(); lastDeadlineNanos = deadline(entry, budgetMs); turns++;
        long oldDeadline = 0; boolean clamped = false;
        int actualTarget = -1;
        try {
            List<Action> emitted = baseline.play(state, me, budgetMs);
            lastBaseTarget = actualTarget = readEmitted(state, emitted);
            oldDeadline = nativeDeadline(); lastDeadlineNanos = Math.min(lastDeadlineNanos, oldDeadline);
            nativeDeadline(lastDeadlineNanos); clamped = true;
            proposer.observe(state, me); // Public memory advances even on openings, disabled runs and cutoffs.
            if (!p.USE_NEURAL_TARGET_PROPOSAL || baseline.selectedOpening || !baseline.ordinarySearchComplete
                    || !baseline.networkSearchStarted || !baseline.networkPhaseComplete) return emitted;
            lastEligible = true; eligibleTurns++;
            lastRemainingNanos = Math.max(0, lastDeadlineNanos - clock.getAsLong());
            remainingSamples++; remainingNanosTotal += lastRemainingNanos;
            remainingNanosMin = Math.min(remainingNanosMin, lastRemainingNanos);
            remainingNanosMax = Math.max(remainingNanosMax, lastRemainingNanos);
            if (expired()) { noRemainingBudget++; return emitted; }
            proposalAttempts++; lastPhase = FEATURES;
            int target = proposer.propose(state, me, deadlineCheck); lastProposedTarget = target;
            if (target == TargetProposer.ABORTED || !proposer.lastComplete) { featureCutoffs++; return emitted; }
            if (!validTarget(state, target) || !finiteProposal()) { invalidProposals++; return emitted; }
            if (target < 0) noTargetProposals++;
            if (target == actualTarget) { sameTarget++; return emitted; }
            if (!compareTargets(state, me, fullCells, lastFullCount, actualTarget, target)) return emitted;
            List<Action> result = chooseComparedTarget(emitted, target);
            if (lastAccepted) actualTarget = target;
            return result;
        } finally {
            if (clamped) nativeDeadline(oldDeadline);
            lastEmittedTarget = actualTarget;
            proposer.recordTarget(actualTarget); // Record the action actually emitted, not the neural suggestion.
        }
    }

    private boolean finiteProposal() {
        for (int i = 0; i < proposer.lastCount; i++)
            if (proposer.scoreAvailable[i] && !Float.isFinite(proposer.scores[i])) return false;
        return true;
    }
    static boolean validTarget(GameState state, int target) {
        return target == -1 || target >= 0 && target < state.regionCount
                && !state.regionInked[target] && !state.regionHasTown(target);
    }
    private int readEmitted(GameState state, List<Action> actions) {
        int target = -1; lastFullCount = 0;
        for (int i = 0; i < actions.size(); i++) {
            Action action = actions.get(i);
            if (action.type == Action.Type.PLACE) {
                if (lastFullCount == fullCells.length) throw new IllegalStateException("Baseline emitted too many placements");
                fullCells[lastFullCount++] = state.cell(action.x, action.y);
            } else if (action.type == Action.Type.DISRUPT) target = action.disruptRegion(state);
            else if (action.type != Action.Type.WAIT && action.type != Action.Type.MESSAGE)
                throw new IllegalStateException("Unexpected baseline command: " + action.type);
        }
        return target;
    }
    static List<Action> retarget(List<Action> original, int target) {
        List<Action> changed = new ArrayList<>(original.size() + 1);
        for (int i = 0; i < original.size(); i++) {
            Action action = original.get(i);
            if (action.type == Action.Type.PLACE || action.type == Action.Type.MESSAGE) changed.add(action);
        }
        if (target >= 0) changed.add(Action.disrupt(target));
        if (changed.isEmpty()) changed.add(Action.waitAction());
        return changed;
    }

    List<Action> chooseComparedTarget(List<Action> emitted, int target) {
        lastPhase = COMMIT;
        if (!lastPairComplete) return emitted;
        if (expired()) { markCutoff(); return emitted; }
        if (!(lastDScore > lastEScore + EPS)) return emitted;
        List<Action> changed = retarget(emitted, target);
        // List conversion is the only additional per-turn allocation. Never commit a late result.
        if (expired()) { markCutoff(); return emitted; }
        lastAccepted = true; acceptedTargets++;
        return changed;
    }

    /** Same retained opponent replies and robustness for BOTH fully emitted actions. Package scope
     * permits synthetic storage/continuation tests, without a second policy or alternate evaluator. */
    boolean compareTargets(GameState state, int me, int[] cells, int count, int eTarget, int dTarget) {
        int root = counts[me]; // WAIT root forces ownFirst=0, also when there are no own routes.
        int replies = Math.max(1, Math.min(p.ROLLOUT_REPLIES, counts[1 - me]));
        int savedCount = rootCounts[root], savedTurns = baseline.simulatedTurns;
        long savedDeadline = nativeDeadline();
        System.arraycopy(roots[root], 0, savedCells, 0, GameState.PAINT_PER_TURN);
        lastReplyCount = replies; lastEReplies = lastDReplies = 0;
        lastPairComplete = lastComparisonCutoff = lastInvalidComparison = false;
        lastEScore = lastDScore = Double.NaN;
        try {
            nativeDeadline(Math.min(savedDeadline, lastDeadlineNanos));
            System.arraycopy(cells, 0, roots[root], 0, count); rootCounts[root] = count;
            for (int side = 0; side < 2; side++) {
                lastPhase = side == 0 ? EVALUATE_E : EVALUATE_D;
                double sum = 0, worst = Double.POSITIVE_INFINITY;
                for (int reply = 0; reply < replies; reply++) {
                    if (expired()) { markCutoff(); return false; }
                    double value = invokeRollout(state, me, root, side == 0 ? eTarget : dTarget, reply);
                    if (expired()) { markCutoff(); return false; }
                    if (!Double.isFinite(value)) { markNonfinite(); return false; }
                    if (side == 0) { lastEReplyValues[reply] = value; lastEReplies++; }
                    else { lastDReplyValues[reply] = value; lastDReplies++; }
                    sum += value; worst = Math.min(worst, value);
                }
                double value = (1 - p.ROLLOUT_ROBUSTNESS) * sum / replies + p.ROLLOUT_ROBUSTNESS * worst;
                if (!Double.isFinite(value)) { markNonfinite(); return false; }
                if (side == 0) lastEScore = value; else lastDScore = value;
            }
            if (expired()) { markCutoff(); return false; }
            lastPairComplete = true; pairsCompleted++; return true;
        } finally {
            System.arraycopy(savedCells, 0, roots[root], 0, GameState.PAINT_PER_TURN); rootCounts[root] = savedCount;
            baseline.simulatedTurns = savedTurns; nativeDeadline(savedDeadline);
        }
    }
    private double invokeRollout(GameState state, int me, int root, int target, int reply) {
        try { return (double) rollout.invokeExact(state, me, root, target, reply); }
        catch (RuntimeException | Error e) { throw e; }
        catch (Throwable e) { throw new IllegalStateException("Local rollout invocation failed", e); }
    }
    private void markCutoff() { if (!lastComparisonCutoff) comparisonCutoffs++; lastComparisonCutoff = true; }
    private void markNonfinite() { if (!lastInvalidComparison) nonfiniteComparisons++; lastInvalidComparison = true; }

    /** Allocated by the referee once after the game, never in play/search. */
    public Map<String, Long> diagnostics() {
        Map<String, Long> d = new LinkedHashMap<>();
        d.put("turns", turns); d.put("eligibleTurns", eligibleTurns); d.put("proposalAttempts", proposalAttempts);
        d.put("sameTarget", sameTarget); d.put("noTargetProposals", noTargetProposals);
        d.put("noRemainingBudget", noRemainingBudget); d.put("featureCutoffs", featureCutoffs);
        d.put("invalidProposals", invalidProposals); d.put("pairsCompleted", pairsCompleted);
        d.put("comparisonCutoffs", comparisonCutoffs); d.put("nonfiniteComparisons", nonfiniteComparisons);
        d.put("acceptedTargets", acceptedTargets); d.put("remainingSamples", remainingSamples);
        d.put("remainingNanosTotal", remainingNanosTotal); d.put("remainingNanosMin", remainingSamples == 0 ? 0 : remainingNanosMin);
        d.put("remainingNanosMax", remainingNanosMax); return d;
    }
    @Override public void warmup(GameState state, int me, long ms) {
        TargetHybridBot isolated = new TargetHybridBot(p); isolated.init(state, me); isolated.warmupIsolated(state, me, ms);
    }
    private void warmupIsolated(GameState state, int me, long ms) { Bot.super.warmup(state, me, ms); }
}
