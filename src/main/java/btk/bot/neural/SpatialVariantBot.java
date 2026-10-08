package btk.bot.neural;

import btk.bot.Bot;
import btk.bot.Params;
import btk.core.Action;
import btk.core.GameState;

import java.util.List;

/** Local identity/weight selector; all decisions and timing remain in the frozen v1 runtime. */
public final class SpatialVariantBot implements Bot {
    private final String version;
    private final SpatialBot delegate;

    public SpatialVariantBot(Params params, String version, String encodedWeights) {
        if (!"spatial-ppo-v2".equals(version) && !"spatial-ppo-v2-init".equals(version))
            throw new IllegalArgumentException("Unknown spatial weight variant: " + version);
        this.version = version;
        delegate = new SpatialBot(params, new SpatialNet(encodedWeights), System::nanoTime);
    }

    @Override public String name() { return version; }

    @Override public void init(GameState state, int me) { delegate.init(state, me); }

    @Override public List<Action> play(GameState state, int me, long budgetMs) {
        return delegate.play(state, me, budgetMs);
    }

    @Override public void warmup(GameState state, int me, long ms) { delegate.warmup(state, me, ms); }
}
