package btk.bot.neural;

import btk.bot.Bot;
import btk.bot.Params;
import btk.core.Action;
import btk.core.GameState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/** Greedy frozen spatial PPO v1 policy: three conditional placements, then one disruption. */
public final class SpatialBot implements Bot {
    private final Params params;
    private final SpatialNet net;
    private final LongSupplier clock;
    private final int[] prefix = new int[3];
    private SpatialObservation observation;
    public int lastPlacements, lastTarget = -1, lastCompletedDecisions;
    public boolean lastInterrupted;

    public SpatialBot(Params params) { this(params, new SpatialNet(SpatialWeights.DATA), System::nanoTime); }

    /** Explicit network and clock keep parity and cooperative-cutoff tests independent of trained weights. */
    public SpatialBot(Params params, SpatialNet net, LongSupplier clock) {
        if (params == null || net == null || clock == null) throw new IllegalArgumentException("Spatial bot dependencies are required");
        this.params = params.copy();
        if (this.params.SPATIAL_RESERVE_MS < 0) throw new IllegalArgumentException("Negative spatial deadline reserve");
        this.net = net;
        this.clock = clock;
    }

    @Override public String name() { return "spatial-ppo-v1"; }

    @Override public void init(GameState state, int me) { observation = new SpatialObservation(state); }

    @Override public List<Action> play(GameState state, int me, long budgetMs) {
        long start = clock.getAsLong();
        long workMs = Math.max(0, budgetMs - params.SPATIAL_RESERVE_MS);
        long deadline = start + (workMs > Long.MAX_VALUE / 1_000_000L ? Long.MAX_VALUE : workMs * 1_000_000L);
        lastPlacements = 0;
        lastTarget = -1;
        lastCompletedDecisions = 0;
        lastInterrupted = false;
        List<Action> actions = new ArrayList<>(4);
        if (budgetMs <= params.SPATIAL_RESERVE_MS || expired(deadline)) return finish(actions, true);
        if (observation == null) init(state, me);
        observation.prepare(state, me);
        if (expired(deadline)) return finish(actions, true);
        int paint = GameState.PAINT_PER_TURN;
        boolean stopped = false;
        for (int stage = 0; stage < 3; stage++) {
            observation.prefix(prefix, lastPlacements, paint, stage, stopped);
            if (stopped || paint == 0) continue;
            if (!net.forward(observation, clock, deadline)) return finish(actions, true);
            int selected = argmax(net.buildLogits, observation.buildMask);
            if (expired(deadline)) return finish(actions, true);
            lastCompletedDecisions++;
            if (selected == SpatialObservation.S) { stopped = true; continue; }
            int x = selected % SpatialObservation.W, y = selected / SpatialObservation.W;
            paint -= state.placeCost(state.cell(x, y));
            prefix[lastPlacements++] = selected;
            actions.add(Action.place(x, y));
        }
        observation.prefix(prefix, lastPlacements, paint, 3, stopped);
        if (!net.forward(observation, clock, deadline)) return finish(actions, true);
        int selected = argmax(net.disruptLogits, observation.disruptMask);
        if (expired(deadline)) return finish(actions, true);
        lastCompletedDecisions++;
        if (selected != SpatialObservation.S) {
            lastTarget = selected;
            actions.add(Action.disrupt(selected));
        }
        return finish(actions, false);
    }

    private static int argmax(float[] logits, boolean[] mask) {
        int best = -1;
        float score = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < logits.length; i++) if (mask[i] && (best < 0 || logits[i] > score)) {
            best = i;
            score = logits[i];
        }
        if (best < 0) throw new IllegalStateException("Spatial decision has no legal action");
        return best;
    }

    private boolean expired(long deadline) { return clock.getAsLong() - deadline >= 0; }

    private List<Action> finish(List<Action> actions, boolean interrupted) {
        lastInterrupted = interrupted;
        if (actions.isEmpty()) actions.add(Action.waitAction());
        return actions;
    }

    @Override public void warmup(GameState state, int me, long ms) {
        if (ms <= 0) return;
        SpatialObservation warm = new SpatialObservation(state);
        warm.prepare(state, me);
        long start = clock.getAsLong();
        long duration = ms > Long.MAX_VALUE / 1_000_000L ? Long.MAX_VALUE : ms * 1_000_000L;
        long end = start + duration;
        while (!expired(end)) if (!net.forward(warm, clock, end)) break;
    }
}
