package btk.bot;

import btk.core.Action;
import btk.core.GameState;

import java.util.List;

/** A strategy. One instance plays one game (may keep per-game memory). */
public interface Bot {

    String name();

    /** Called once with the initial state (no tracks yet). */
    default void init(GameState s, int myId) {}

    /**
     * Chooses the actions for this turn. {@code s} already has connections computed
     * ({@code Rules.updateConnections}) and may be mutated freely (it is a copy).
     *
     * @param budgetMs wall-clock budget for this call
     */
    List<Action> play(GameState s, int myId, long budgetMs);

    /**
     * JIT warm-up hook used on the first CG turn. Default: replay {@link #play} on copies of the
     * state until the time is spent.
     */
    default void warmup(GameState s, int myId, long ms) {
        long end = System.nanoTime() + ms * 1_000_000L;
        int iterations = 0;
        while (System.nanoTime() < end) {
            GameState copy = s.copy();
            copy.turn = Math.min(copy.maxTurns - 1, iterations % copy.maxTurns);
            play(copy, myId, 5);
            iterations++;
        }
    }
}
