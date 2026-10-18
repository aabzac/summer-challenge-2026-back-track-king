package btk.bot;

import btk.bot.current.EvalBot;
import btk.bot.current.HeuristicBot;
import btk.bot.current.RolloutBot;
import btk.core.Action;
import btk.core.GameState;

import java.util.List;
import java.util.function.LongSupplier;

/** The bot that ships to CodinGame: a thin pointer to the current strategy with default params. */
public final class MainBot implements Bot {

    private final Params params;
    private final Bot delegate;
    public int warmupCalls;

    public MainBot() {
        this(new Params());
    }

    public MainBot(Params params) {
        this.params = params.copy();
        // FAST_CHEAPEST_PATH_BEGIN
        if (params.USE_FAST_CHEAPEST_PATH && (!params.USE_ROLLOUT_POLICY
                || params.USE_HEURISTIC_POLICY || params.USE_NEURAL_TARGET_PROPOSAL))
            throw new IllegalArgumentException("Fast cheapest path requires the current native rollout policy");
        // FAST_CHEAPEST_PATH_END
        // TURN_BUDGET_BEGIN
        if (params.TURN_BUDGET_MS != 36 && params.TURN_BUDGET_MS != 38 && params.TURN_BUDGET_MS != 40)
            throw new IllegalArgumentException("Turn budget must be 36, 38 or 40 ms");
        if (params.TURN_BUDGET_MS != 36 && (!params.USE_ROLLOUT_POLICY || params.USE_HEURISTIC_POLICY
                || params.USE_NEURAL_TARGET_PROPOSAL))
            throw new IllegalArgumentException("38/40 ms requires the current native rollout policy");
        // TURN_BUDGET_END
        if (params.ISOLATED_ROUTE_WARMUP && (params.WARMUP_SLICE_MS <= 0
                || params.USE_DISRUPT && params.WARMUP_SLICE_MS <= params.DISRUPT_RESERVE_MS)) {
            throw new IllegalArgumentException("Warmup slice must leave time beyond the sabotage reserve");
        }
        delegate = newStrategy(this.params);
    }

    // TURN_BUDGET_BEGIN
    public long turnBudgetMs() { return params.TURN_BUDGET_MS; }
    // TURN_BUDGET_END

    public static Bot newStrategy(Params params) {
        if (params.USE_ROLLOUT_POLICY) return new RolloutBot(params);
        return params.USE_HEURISTIC_POLICY ? new HeuristicBot(params) : new EvalBot(params);
    }

    @Override
    public String name() {
        return "main";
    }

    @Override
    public void init(GameState s, int myId) {
        delegate.init(s, myId);
    }

    @Override
    public List<Action> play(GameState s, int myId, long budgetMs) {
        return delegate.play(s, myId, budgetMs);
    }

    @Override
    public void warmup(GameState s, int myId, long ms) {
        warmupCalls = 0;
        if (!params.ISOLATED_ROUTE_WARMUP) {
            delegate.warmup(s, myId, ms);
            return;
        }
        if (ms <= 0) return;
        // Never train, mutate or consume RNG on the live delegate during speculative warmup.
        warmupCalls = warmIsolated(newStrategy(params.copy()), s, myId, ms,
                params.WARMUP_SLICE_MS, System::nanoTime);
    }

    /** Package-visible clock seam: setup and reset time consume the same warmup allowance. */
    static int warmIsolated(Bot disposable, GameState initial, int myId, long totalMs,
                            int sliceMs, LongSupplier clock) {
        if (totalMs <= 0) return 0;
        if (sliceMs <= 0) throw new IllegalArgumentException("Positive warmup slice required");
        long start = clock.getAsLong();
        GameState scratch = initial.copy();
        disposable.init(scratch, myId);
        int iterations = 0;
        while (true) {
            scratch.copyFrom(initial);
            scratch.turn = Math.min(initial.maxTurns - 1, initial.turn + iterations);
            long remainingNs = totalMs * 1_000_000L - (clock.getAsLong() - start);
            if (remainingNs < sliceMs * 1_000_000L) break; // no undersized final call
            disposable.play(scratch, myId, sliceMs);
            iterations++;
        }
        return iterations;
    }
}
