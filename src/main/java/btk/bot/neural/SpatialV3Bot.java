package btk.bot.neural;

import btk.bot.Bot;
import btk.bot.Params;
import btk.core.Action;
import btk.core.GameState;

import java.util.List;

/** Local v3 identity and generated weights; decisions and timing use the frozen v1 runtime. */
public final class SpatialV3Bot implements Bot {
    private final SpatialBot delegate;

    public SpatialV3Bot(Params params) {
        delegate = new SpatialBot(params, new SpatialNet(SpatialV3Weights.DATA), System::nanoTime);
    }

    @Override public String name() { return "spatial-ppo-v3"; }

    @Override public void init(GameState state, int me) { delegate.init(state, me); }

    @Override public List<Action> play(GameState state, int me, long budgetMs) {
        return delegate.play(state, me, budgetMs);
    }

    @Override public void warmup(GameState state, int me, long ms) { delegate.warmup(state, me, ms); }
}
