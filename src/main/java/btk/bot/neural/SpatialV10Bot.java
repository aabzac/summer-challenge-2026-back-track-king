package btk.bot.neural;

import btk.bot.Bot;
import btk.bot.Params;
import btk.core.Action;
import btk.core.GameState;

import java.util.List;
import java.util.function.LongSupplier;

/** Full-corpus continuation identity; all decisions use the unchanged v4 spatial runtime. */
public final class SpatialV10Bot implements Bot {
    private final SpatialBot delegate;

    public SpatialV10Bot(Params params) {
        this(params, new SpatialNet(SpatialV10Weights.DATA), System::nanoTime);
    }

    /** Explicit dependencies support runtime parity without modifying historical policy classes. */
    public SpatialV10Bot(Params params, SpatialNet net, LongSupplier clock) {
        delegate = new SpatialBot(params, net, clock);
    }

    @Override public String name() { return "spatial-ppo-v10"; }

    @Override public void init(GameState state, int me) { delegate.init(state, me); }

    @Override public List<Action> play(GameState state, int me, long budgetMs) {
        return delegate.play(state, me, budgetMs);
    }

    @Override public void warmup(GameState state, int me, long ms) { delegate.warmup(state, me, ms); }
}
