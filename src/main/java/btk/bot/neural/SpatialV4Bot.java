package btk.bot.neural;

import btk.bot.Bot;
import btk.bot.Params;
import btk.core.Action;
import btk.core.GameState;

import java.util.List;

/** Local distillation identity and generated v4 weights; decisions use the frozen spatial runtime. */
public final class SpatialV4Bot implements Bot {
    private final SpatialBot delegate;

    public SpatialV4Bot(Params params) {
        delegate = new SpatialBot(params, new SpatialNet(SpatialV4Weights.DATA), System::nanoTime);
    }

    @Override public String name() { return "spatial-ppo-v4"; }

    @Override public void init(GameState state, int me) { delegate.init(state, me); }

    @Override public List<Action> play(GameState state, int me, long budgetMs) {
        return delegate.play(state, me, budgetMs);
    }

    @Override public void warmup(GameState state, int me, long ms) { delegate.warmup(state, me, ms); }
}
