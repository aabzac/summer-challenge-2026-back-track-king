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

/** V7 proposals with paired checkpoints through eight exact turns; no learned leaf value. */
public final class SpatialV8Bot implements Bot {
    private final Params params;
    private final SpatialNet net;
    private final LongSupplier clock;
    private final int[] prefix = new int[3];
    private SpatialObservation observation;
    private SearchState baseSimulation, alternativeSimulation;
    private long deadline;
    public int lastPlacements, lastTarget = -1, lastCompletedDecisions;
    public boolean lastInterrupted, lastSearchAttempted, lastSearchComplete, lastSearchChanged, lastSearchCutoff;
    public int lastBasePlacements, lastAlternativePlacements, lastBaseTarget = -1, lastAlternativeTarget = -1;
    public int lastBranchSlot = -1, lastOldCell = -1, lastNewCell = -1;
    public int lastOpponentPlacements, lastOpponentTarget = -1;
    public int lastBaseSteps, lastAlternativeSteps, lastCandidateEvaluations;
    public boolean lastBaseTerminal, lastAlternativeTerminal;
    public static final int NONE = 0, ALTERNATIVE = 1, OPPONENT = 2, BASE = 3, CANDIDATE = 4;
    /** Root choice uses padded indices (600=STOP); branch cells use native indices. */
    public int lastRootSelected = -1, lastAlternativeDecisions, lastAlternativePaint;
    public int lastSearchPhase, lastCutoffPhase;
    public boolean lastAlternativeComplete, lastAlternativeStopped, lastDuplicate, lastInvalidProposal;
    /** Published values stay separate from partially advanced work after a deadline. */
    public int lastCheckpointCount, lastCommittedHorizon, lastAttemptedHorizon;
    public int lastBaseWorkSteps, lastAlternativeWorkSteps;
    public boolean lastReachedEnd, lastBaseWorkTerminal, lastAlternativeWorkTerminal;
    public final int[] checkpointHorizon = new int[4], baseCheckpointSteps = new int[4], alternativeCheckpointSteps = new int[4];
    public final long[] baseCheckpointScoreDelta = new long[4], alternativeCheckpointScoreDelta = new long[4];
    public final boolean[] baseCheckpointTerminal = new boolean[4], alternativeCheckpointTerminal = new boolean[4];
    /** Long.MIN_VALUE means that the whole trajectory was not scored. Units are raw score points. */
    public long lastBaseScoreDelta = Long.MIN_VALUE, lastAlternativeScoreDelta = Long.MIN_VALUE;
    /** Native cell indices, valid up to each plan's recorded placement count. */
    public final int[] lastBaseCells = new int[3], lastAlternativeCells = new int[3], lastOpponentCells = new int[3];
    /** Padded-cell/region logits from the original preturn's stage-zero passes, never child passes. */
    public final float[] ownBuildPrior = new float[601], ownDisruptPrior = new float[601];
    public final float[] opponentBuildPrior = new float[601], opponentDisruptPrior = new float[601];
    public boolean lastOwnPriorComplete, lastOpponentComplete;
    private final Plan rootOpponent = new Plan(lastOpponentCells), nextOwn = new Plan(new int[3]), nextOpponent = new Plan(new int[3]);

    public SpatialV8Bot(Params params) { this(params, new SpatialNet(SpatialV4Weights.DATA), System::nanoTime); }

    public SpatialV8Bot(Params params, SpatialNet net, LongSupplier clock) {
        if (params == null || net == null || clock == null || params.SPATIAL_RESERVE_MS < 0
                || params.SPATIAL_V8_MIN_SEARCH_MS < 0 || params.SPATIAL_V8_DEPTH < 2 || params.SPATIAL_V8_DEPTH > 8
                || params.SPATIAL_V8_DEPTH % 2 != 0)
            throw new IllegalArgumentException("Invalid spatial v8 dependencies or search bounds");
        this.params = params.copy(); this.net = net; this.clock = clock;
    }

    @Override public String name() { return "spatial-ppo-v8"; }

    @Override public void init(GameState state, int me) {
        observation = new SpatialObservation(state);
        baseSimulation = new SearchState(state); alternativeSimulation = new SearchState(state);
    }

    @Override public List<Action> play(GameState state, int me, long budgetMs) {
        long start = clock.getAsLong(), workMs = Math.max(0, budgetMs - params.SPATIAL_RESERVE_MS);
        deadline = start + (workMs > Long.MAX_VALUE / 1_000_000L ? Long.MAX_VALUE : workMs * 1_000_000L);
        resetDiagnostics(); List<Action> actions = new ArrayList<>(4);
        if (budgetMs <= params.SPATIAL_RESERVE_MS || expired()) return finish(actions, true);
        if (observation == null) init(state, me);
        observation.prepare(state, me);
        if (expired()) return finish(actions, true);
        int paint = GameState.PAINT_PER_TURN; boolean stopped = false;
        // Frozen SpatialBot readout with passive root capture. No extra greedy forward.
        for (int stage = 0; stage < 3; stage++) {
            observation.prefix(prefix, lastPlacements, paint, stage, stopped);
            if (stopped || paint == 0) continue;
            if (!net.forward(observation, clock, deadline)) return finish(actions, true);
            int selected = argmax(net.buildLogits, observation.buildMask);
            if (expired()) return finish(actions, true);
            if (stage == 0) {
                System.arraycopy(net.buildLogits, 0, ownBuildPrior, 0, 601);
                System.arraycopy(net.disruptLogits, 0, ownDisruptPrior, 0, 601);
                lastOwnPriorComplete = true;
                lastRootSelected = selected;
                lastOldCell = selected == SpatialObservation.STOP ? -1 : observation.nativeCell(selected);
                lastNewCell = rootAlternative(selected);
                if (lastNewCell >= 0) lastBranchSlot = 0;
            }
            lastCompletedDecisions++;
            if (selected == SpatialObservation.STOP) { stopped = true; continue; }
            int cell = observation.nativeCell(selected);
            paint -= state.placeCost(cell); prefix[lastPlacements] = selected;
            lastBaseCells[lastPlacements++] = cell;
            actions.add(Action.place(state.x(cell), state.y(cell)));
        }
        observation.prefix(prefix, lastPlacements, paint, 3, stopped);
        if (!net.forward(observation, clock, deadline)) return finish(actions, true);
        int target = argmax(net.disruptLogits, observation.disruptMask);
        if (expired()) return finish(actions, true);
        lastCompletedDecisions++;
        if (target != SpatialObservation.STOP) { lastTarget = target; actions.add(Action.disrupt(target)); }
        List<Action> base = finish(actions, false);
        if (!params.USE_SPATIAL_V8_SEARCH || lastNewCell < 0 || !lastOwnPriorComplete
                || deadline - clock.getAsLong() < params.SPATIAL_V8_MIN_SEARCH_MS * 1_000_000L) return base;
        lastAlternativeTarget = lastTarget; lastSearchAttempted = true;
        lastSearchPhase = ALTERNATIVE;
        if (!completeAlternative(state)) return abort(base);
        lastDuplicate = sameSet();
        if (lastDuplicate) { lastSearchPhase = NONE; return base; }
        lastSearchPhase = OPPONENT;
        if (expired()) return abort(base);
        observation.prepare(state, 1 - me);
        if (expired() || !net.forward(observation, clock, deadline)) return abort(base);
        System.arraycopy(net.buildLogits, 0, opponentBuildPrior, 0, 601);
        System.arraycopy(net.disruptLogits, 0, opponentDisruptPrior, 0, 601);
        if (!pack(state, opponentBuildPrior, opponentDisruptPrior, rootOpponent)) return invalid(base);
        if (expired()) return abort(base);
        lastOpponentPlacements = rootOpponent.count; lastOpponentTarget = rootOpponent.target; lastOpponentComplete = true;
        baseSimulation.set(state); alternativeSimulation.set(state);
        for (int horizon = 1; horizon <= params.SPATIAL_V8_DEPTH; horizon++) {
            lastAttemptedHorizon = horizon; lastSearchPhase = BASE;
            if (!advance(baseSimulation, me, true)) return abort(base);
            lastSearchPhase = CANDIDATE;
            if (!advance(alternativeSimulation, me, false) || expired()) return abort(base);
            boolean terminal = lastBaseWorkTerminal && lastAlternativeWorkTerminal;
            if (horizon % 2 == 0 || terminal) publish(state, me, horizon);
            if (terminal || horizon == params.SPATIAL_V8_DEPTH) { lastReachedEnd = true; break; }
        }
        lastSearchPhase = NONE;
        return selectedPlan(state, base);
    }

    private List<Action> selectedPlan(GameState state, List<Action> base) {
        if (lastInvalidProposal || !lastSearchComplete || lastAlternativeScoreDelta <= lastBaseScoreDelta) return base;
        lastSearchChanged = true; lastPlacements = lastAlternativePlacements;
        List<Action> changed = new ArrayList<>(4);
        for (int i = 0; i < lastAlternativePlacements; i++) {
            int cell = lastAlternativeCells[i]; changed.add(Action.place(state.x(cell), state.y(cell)));
        }
        if (lastTarget >= 0) changed.add(Action.disrupt(lastTarget));
        if (changed.isEmpty()) changed.add(Action.waitAction());
        return changed;
    }

    private int rootAlternative(int selected) {
        if (!Float.isFinite(net.buildLogits[selected])) { lastInvalidProposal = true; return -1; }
        int best = -1;
        for (int p = 0; p < SpatialObservation.STOP; p++) if (p != selected && observation.buildMask[p]) {
            if (!Float.isFinite(net.buildLogits[p])) { lastInvalidProposal = true; return -1; }
            if (best < 0 || net.buildLogits[p] > net.buildLogits[best]) best = p;
        }
        return best < 0 ? -1 : observation.nativeCell(best);
    }

    /** Own observation is still prepared for the original board; replace its prefix only. */
    private boolean completeAlternative(GameState state) {
        if (expired()) return false;
        lastAlternativeCells[0] = lastNewCell; lastAlternativePlacements = 1;
        prefix[0] = SpatialObservation.paddedCell(state, lastNewCell);
        int paint = GameState.PAINT_PER_TURN - state.placeCost(lastNewCell);
        lastAlternativePaint = paint;
        for (int stage = 1; stage < 3; stage++) {
            observation.prefix(prefix, lastAlternativePlacements, paint, stage, lastAlternativeStopped);
            if (lastAlternativeStopped || paint == 0) continue;
            if (!net.forward(observation, clock, deadline)) return false;
            for (int p = 0; p <= SpatialObservation.STOP; p++) if (observation.buildMask[p]
                    && !Float.isFinite(net.buildLogits[p])) { lastInvalidProposal = true; return false; }
            int selected = argmax(net.buildLogits, observation.buildMask);
            if (expired()) return false;
            lastAlternativeDecisions++;
            if (selected == SpatialObservation.STOP) { lastAlternativeStopped = true; continue; }
            int cell = observation.nativeCell(selected);
            prefix[lastAlternativePlacements] = selected;
            lastAlternativeCells[lastAlternativePlacements++] = cell;
            paint -= state.placeCost(cell); lastAlternativePaint = paint;
        }
        if (expired()) return false;
        lastAlternativeComplete = true;
        return true;
    }

    private boolean sameSet() {
        if (lastBasePlacements != lastAlternativePlacements) return false;
        for (int i = 0; i < lastBasePlacements; i++) {
            boolean found = false;
            for (int j = 0; j < lastAlternativePlacements; j++)
                if (lastBaseCells[i] == lastAlternativeCells[j]) { found = true; break; }
            if (!found) return false;
        }
        return true;
    }

    private boolean advance(SearchState simulation, int me, boolean base) {
        if (expired()) return false;
        if (base ? lastBaseWorkTerminal : lastAlternativeWorkTerminal) return true;
        int[] ownCells = base ? lastBaseCells : lastAlternativeCells, enemyCells = lastOpponentCells;
        int ownCount = base ? lastBasePlacements : lastAlternativePlacements, enemyCount = lastOpponentPlacements;
        int ownTarget = lastBaseTarget, enemyTarget = lastOpponentTarget;
        if (lastAttemptedHorizon > 1) {
            // Both players choose from the same unchanged branch state before core resolves.
            if (!pack(simulation.state, ownBuildPrior, ownDisruptPrior, nextOwn)
                    || !pack(simulation.state, opponentBuildPrior, opponentDisruptPrior, nextOpponent)) {
                lastInvalidProposal = true; return false;
            }
            ownCells = nextOwn.cells; ownCount = nextOwn.count; ownTarget = nextOwn.target;
            enemyCells = nextOpponent.cells; enemyCount = nextOpponent.count; enemyTarget = nextOpponent.target;
            if (expired()) return false;
        }
        boolean terminal = me == 0
                ? simulation.resolve(ownCells, ownCount, ownTarget, enemyCells, enemyCount, enemyTarget)
                : simulation.resolve(enemyCells, enemyCount, enemyTarget, ownCells, ownCount, ownTarget);
        if (base) { lastBaseWorkSteps++; lastBaseWorkTerminal = terminal; }
        else { lastAlternativeWorkSteps++; lastAlternativeWorkTerminal = terminal; }
        return !expired();
    }

    private void publish(GameState root, int me, int horizon) {
        long gap = (long) root.score[me] - root.score[1-me];
        lastBaseScoreDelta = (long) baseSimulation.state.score[me] - baseSimulation.state.score[1-me] - gap;
        lastAlternativeScoreDelta = (long) alternativeSimulation.state.score[me] - alternativeSimulation.state.score[1-me] - gap;
        lastBaseSteps = lastBaseWorkSteps; lastAlternativeSteps = lastAlternativeWorkSteps;
        lastBaseTerminal = lastBaseWorkTerminal; lastAlternativeTerminal = lastAlternativeWorkTerminal;
        int at = lastCheckpointCount++;
        checkpointHorizon[at] = horizon; baseCheckpointSteps[at] = lastBaseSteps; alternativeCheckpointSteps[at] = lastAlternativeSteps;
        baseCheckpointScoreDelta[at] = lastBaseScoreDelta; alternativeCheckpointScoreDelta[at] = lastAlternativeScoreDelta;
        baseCheckpointTerminal[at] = lastBaseTerminal; alternativeCheckpointTerminal[at] = lastAlternativeTerminal;
        lastCommittedHorizon = horizon; lastCandidateEvaluations += 2; lastSearchComplete = true;
    }

    /** Cached neural priors, current core legality and costs. No state mutation or inference. */
    private static boolean pack(GameState state, float[] build, float[] disrupt, Plan plan) {
        plan.count = 0; plan.target = -1; int paint = GameState.PAINT_PER_TURN;
        if (!Float.isFinite(build[SpatialObservation.STOP]) || !Float.isFinite(disrupt[SpatialObservation.STOP])) return false;
        for (int stage = 0; stage < 3 && paint > 0; stage++) {
            int selected = SpatialObservation.STOP; float best = build[selected];
            for (int c = 0; c < state.size; c++) {
                if (!state.canBuild(c) || state.placeCost(c) > paint) continue;
                boolean chosen = false;
                for (int i = 0; i < plan.count; i++) if (plan.cells[i] == c) { chosen = true; break; }
                if (chosen) continue;
                int p = SpatialObservation.paddedCell(state, c); float logit = build[p];
                if (!Float.isFinite(logit)) return false;
                if (logit > best || logit == best && p < selected) { selected = p; best = logit; }
            }
            if (selected == SpatialObservation.STOP) break;
            int c = state.cell(selected % SpatialObservation.W, selected / SpatialObservation.W);
            plan.cells[plan.count++] = c; paint -= state.placeCost(c);
        }
        int selected = SpatialObservation.STOP; float best = disrupt[selected];
        for (int r = 0; r < state.regionCount; r++) if (!state.regionInked[r] && !state.regionHasTown(r)) {
            if (!Float.isFinite(disrupt[r])) return false;
            if (disrupt[r] > best || disrupt[r] == best && r < selected) { selected = r; best = disrupt[r]; }
        }
        plan.target = selected == SpatialObservation.STOP ? -1 : selected;
        return true;
    }

    private static int argmax(float[] logits, boolean[] mask) {
        int best = -1; float score = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < logits.length; i++) if (mask[i] && (best < 0 || logits[i] > score)) { best = i; score = logits[i]; }
        if (best < 0) throw new IllegalStateException("Spatial decision has no legal action");
        return best;
    }
    private boolean expired() { return expired(deadline); }
    private boolean expired(long until) { return clock.getAsLong() - until >= 0; }
    private List<Action> abort(List<Action> base) {
        if (!lastInvalidProposal) { lastSearchCutoff = true; lastCutoffPhase = lastSearchPhase; }
        return selectedPlan(baseSimulation.state, base);
    }
    private List<Action> invalid(List<Action> base) { lastInvalidProposal = true; return base; }
    private List<Action> finish(List<Action> actions, boolean interrupted) {
        lastInterrupted = interrupted; lastBasePlacements = lastPlacements; lastBaseTarget = lastTarget;
        if (actions.isEmpty()) actions.add(Action.waitAction()); return actions;
    }
    private void resetDiagnostics() {
        lastPlacements = 0; lastTarget = -1; lastCompletedDecisions = 0; lastInterrupted = false;
        lastSearchAttempted = false; lastSearchComplete = false; lastSearchChanged = false; lastSearchCutoff = false;
        lastBasePlacements = 0; lastAlternativePlacements = 0; lastBaseTarget = -1; lastAlternativeTarget = -1;
        lastBranchSlot = -1; lastOldCell = -1; lastNewCell = -1; lastOpponentPlacements = 0; lastOpponentTarget = -1;
        lastBaseSteps = 0; lastAlternativeSteps = 0; lastCandidateEvaluations = 0;
        lastBaseTerminal = false; lastAlternativeTerminal = false;
        lastRootSelected = -1; lastAlternativeDecisions = 0; lastAlternativePaint = 0;
        lastSearchPhase = NONE; lastCutoffPhase = NONE;
        lastAlternativeComplete = false; lastAlternativeStopped = false; lastDuplicate = false; lastInvalidProposal = false;
        lastCheckpointCount = 0; lastCommittedHorizon = 0; lastAttemptedHorizon = 0; lastReachedEnd = false;
        lastBaseWorkSteps = 0; lastAlternativeWorkSteps = 0; lastBaseWorkTerminal = false; lastAlternativeWorkTerminal = false;
        lastBaseScoreDelta = Long.MIN_VALUE; lastAlternativeScoreDelta = Long.MIN_VALUE;
        lastOwnPriorComplete = false; lastOpponentComplete = false;
        Arrays.fill(lastBaseCells, -1); Arrays.fill(lastAlternativeCells, -1); Arrays.fill(lastOpponentCells, -1);
    }
    @Override public void warmup(GameState state, int me, long ms) {
        if (ms <= 0) return;
        SpatialObservation warm = new SpatialObservation(state); warm.prepare(state, me);
        long start = clock.getAsLong(), duration = ms > Long.MAX_VALUE / 1_000_000L ? Long.MAX_VALUE : ms * 1_000_000L;
        long end = start + duration;
        while (!expired(end)) if (!net.forward(warm, clock, end)) break;
    }
    private static final class Plan {
        final int[] cells; int count, target;
        Plan(int[] cells) { this.cells = cells; }
    }
}
