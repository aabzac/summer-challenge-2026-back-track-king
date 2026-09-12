package btk.bot;

import btk.bot.current.EvalBot;
import btk.core.Action;
import btk.core.GameState;

import java.util.List;

/** The bot that ships to CodinGame: a thin pointer to the current strategy with default params. */
public final class MainBot implements Bot {

    private final Bot delegate = new EvalBot(new Params());

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
        delegate.warmup(s, myId, ms);
    }
}
