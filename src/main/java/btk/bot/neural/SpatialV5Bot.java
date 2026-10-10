package btk.bot.neural;

import btk.bot.Bot;
import btk.bot.Params;
import btk.core.Action;
import btk.core.GameState;
import btk.core.SearchState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.LongSupplier;

/** One-ply, two-disruption reranking over the unchanged spatial greedy construction. */
public final class SpatialV5Bot implements Bot {
    private final Params params;
    private final SpatialNet net;
    private final SpatialBot greedy;
    private final LongSupplier clock;
    private final int[] opponentPrefix = new int[3];
    private SpatialObservation observation;
    private SearchState simulation;
    private long deadline;
    public int lastPlacements, lastTarget = -1, lastCompletedDecisions;
    public boolean lastInterrupted, lastSearchAttempted, lastSearchComplete, lastSearchChanged, lastSearchCutoff;
    public int lastBaseTarget = -1, lastAlternativeTarget = -1, lastLeafEvaluations;
    public float lastBaseValue = Float.NaN, lastAlternativeValue = Float.NaN;
    public int lastOpponentPlacements, lastOpponentTarget = -1;
    /** Native cell indices, valid only up to the corresponding placement count. */
    public final int[] lastBaseCells = new int[3], lastOpponentCells = new int[3];

    public SpatialV5Bot(Params params) { this(params, new SpatialNet(SpatialV5Weights.DATA), System::nanoTime); }

    public SpatialV5Bot(Params params, SpatialNet net, LongSupplier clock) {
        if (params == null || net == null || clock == null || params.SPATIAL_V5_MIN_SEARCH_MS < 0)
            throw new IllegalArgumentException("Invalid spatial v5 dependencies or minimum search time");
        this.params = params.copy(); this.net = net; this.clock = clock;
        greedy = new SpatialBot(this.params, net, clock);
    }

    @Override public String name() { return "spatial-ppo-v5"; }

    @Override public void init(GameState state, int me) {
        greedy.init(state, me);
        observation = new SpatialObservation(state);
        simulation = new SearchState(state);
    }

    @Override public List<Action> play(GameState state, int me, long budgetMs) {
        long start = clock.getAsLong();
        long workMs = Math.max(0, budgetMs - params.SPATIAL_RESERVE_MS);
        deadline = start + (workMs > Long.MAX_VALUE / 1_000_000L ? Long.MAX_VALUE : workMs * 1_000_000L);
        resetDiagnostics();
        if (observation == null) init(state, me);
        List<Action> base = greedy.play(state, me, budgetMs);
        lastPlacements = greedy.lastPlacements; lastTarget = greedy.lastTarget;
        lastBaseTarget = lastTarget; lastCompletedDecisions = greedy.lastCompletedDecisions;
        lastInterrupted = greedy.lastInterrupted;
        int count = 0;
        for (Action action : base) if (action.type == Action.Type.PLACE)
            lastBaseCells[count++] = state.cell(action.x, action.y);
        if (!params.USE_SPATIAL_V5_SEARCH || lastInterrupted
                || deadline - clock.getAsLong() < params.SPATIAL_V5_MIN_SEARCH_MS * 1_000_000L) return base;

        // These are the delegate's final, own-prefix disruption logits. Save the runner-up
        // before a forward pass for the opponent overwrites the network's public outputs.
        int baseIndex = lastBaseTarget < 0 ? SpatialObservation.STOP : lastBaseTarget;
        int alternative = -1; float best = Float.NEGATIVE_INFINITY;
        for (int r = 0; r <= SpatialObservation.STOP; r++) {
            if (r != SpatialObservation.STOP && (r >= state.regionCount || state.regionInked[r] || state.regionHasTown(r))) continue;
            if (!Float.isFinite(net.disruptLogits[r])) { lastSearchAttempted = true; return abort(base); }
            if (r != baseIndex && (alternative < 0 || net.disruptLogits[r] > best)) {
                alternative = r; best = net.disruptLogits[r];
            }
        }
        if (alternative < 0) return base;
        lastAlternativeTarget = alternative == SpatialObservation.STOP ? -1 : alternative;
        lastSearchAttempted = true;
        if (expired() || !predictOpponent(state, 1 - me)) return abort(base);

        // Both complete plans are legal on the same original board. The predicted opponent
        // plan is fixed before either leaf and is never informed by our chosen disruption.
        if (!evaluate(state, me, lastBaseTarget, true) || !evaluate(state, me, lastAlternativeTarget, false)) return abort(base);
        lastSearchComplete = true;
        if (lastAlternativeValue <= lastBaseValue) return base;
        lastSearchChanged = true; lastTarget = lastAlternativeTarget;
        List<Action> changed = new ArrayList<>(4);
        for (Action action : base) if (action.type == Action.Type.PLACE) changed.add(action);
        if (lastTarget >= 0) changed.add(Action.disrupt(lastTarget));
        if (changed.isEmpty()) changed.add(Action.waitAction());
        return changed;
    }

    /** Approximation: one pre-turn stage-zero pass, then legal cached-logit packing. */
    private boolean predictOpponent(GameState state, int me) {
        observation.prepare(state, me);
        if (expired() || !net.forward(observation, clock, deadline)) return false;
        int count = 0, paint = GameState.PAINT_PER_TURN;
        for (int stage = 0; stage < 3 && paint > 0; stage++) {
            observation.prefix(opponentPrefix, count, paint, stage, false);
            int choice = bestFinite(net.buildLogits, observation.buildMask);
            if (choice < 0) return false;
            if (choice == SpatialObservation.STOP) break;
            opponentPrefix[count] = choice;
            int cell = observation.nativeCell(choice);
            lastOpponentCells[count++] = cell; paint -= state.placeCost(cell);
        }
        int target = bestFinite(net.disruptLogits, observation.disruptMask);
        if (target < 0 || expired()) return false;
        lastOpponentPlacements = count; lastOpponentTarget = target == SpatialObservation.STOP ? -1 : target;
        return true;
    }

    private boolean evaluate(GameState root, int me, int target, boolean base) {
        if (expired()) return false;
        simulation.set(root);
        boolean terminal = me == 0
                ? simulation.resolve(lastBaseCells, lastPlacements, target, lastOpponentCells, lastOpponentPlacements, lastOpponentTarget)
                : simulation.resolve(lastOpponentCells, lastOpponentPlacements, lastOpponentTarget, lastBaseCells, lastPlacements, target);
        if (expired()) return false;
        GameState child = simulation.state;
        float continuation = 0;
        if (!terminal) {
            // Value is a fresh stage-zero successor, never an own pending-action prefix.
            observation.prepare(child, me);
            if (expired() || !net.forward(observation, clock, deadline)) return false;
            continuation = net.value;
        }
        float immediate = (float) (((child.score[me] - (double) child.score[1 - me])
                - (root.score[me] - (double) root.score[1 - me])) / 10_000.0);
        float value = immediate + continuation;
        if (!Float.isFinite(value) || expired()) return false;
        if (base) lastBaseValue = value; else lastAlternativeValue = value;
        lastLeafEvaluations++;
        return true;
    }

    private static int bestFinite(float[] logits, boolean[] mask) {
        int selected = -1; float best = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < logits.length; i++) if (mask[i]) {
            if (!Float.isFinite(logits[i])) return -1;
            if (selected < 0 || logits[i] > best) { selected = i; best = logits[i]; }
        }
        return selected;
    }

    private boolean expired() { return clock.getAsLong() - deadline >= 0; }

    /** No partially compared or nonfinite branch may replace a complete greedy move. */
    private List<Action> abort(List<Action> base) { lastSearchCutoff = true; return base; }

    private void resetDiagnostics() {
        lastSearchAttempted = false; lastSearchComplete = false; lastSearchChanged = false; lastSearchCutoff = false;
        lastBaseTarget = -1; lastAlternativeTarget = -1; lastLeafEvaluations = 0;
        lastBaseValue = Float.NaN; lastAlternativeValue = Float.NaN;
        lastOpponentPlacements = 0; lastOpponentTarget = -1;
        Arrays.fill(lastBaseCells, -1); Arrays.fill(lastOpponentCells, -1);
    }

    @Override public void warmup(GameState state, int me, long ms) { greedy.warmup(state, me, ms); }
}
