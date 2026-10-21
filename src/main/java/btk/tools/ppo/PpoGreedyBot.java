package btk.tools.ppo;

import btk.bot.Bot;
import btk.core.Action;
import btk.core.GameState;

import java.util.List;

/** Greedy linear policy. One rollout buffer per game, sized at {@link #init}. */
public final class PpoGreedyBot implements Bot {
    private final PpoModel model;
    private final String name;
    private PpoRollout rollout;

    public PpoGreedyBot(PpoModel model, String name) {
        this.model = model;
        this.name = name;
    }

    public String name() { return name; }

    public void init(GameState state, int me) {
        rollout = new PpoRollout(state);
    }

    public List<Action> play(GameState state, int me, long budgetMs) {
        if (rollout == null) init(state, me);
        return rollout.greedy(state, me, model);
    }
}
