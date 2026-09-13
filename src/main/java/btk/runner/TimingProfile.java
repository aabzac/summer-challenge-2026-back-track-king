package btk.runner;

import btk.bot.Bot;
import btk.core.Action;
import btk.core.GameState;
import btk.referee.Referee;
import btk.submit.CGRunner;

import java.util.List;
import java.util.Locale;

/** Nominal in-process search budgets, not external process deadlines. */
public enum TimingProfile {
    CG(CGRunner.FIRST_TURN_MS - CGRunner.MARGIN_MS - CGRunner.WARMUP_MS,
            CGRunner.TURN_MS - CGRunner.MARGIN_MS),
    LEGACY(1000, 50);

    public final long firstSearchMs, turnSearchMs;

    TimingProfile(long firstSearchMs, long turnSearchMs) {
        this.firstSearchMs = firstSearchMs;
        this.turnSearchMs = turnSearchMs;
    }

    public long budget(int turn) { return turn == 0 ? firstSearchMs : turnSearchMs; }

    public void apply(Referee referee) {
        referee.firstTurnBudgetMs = firstSearchMs;
        referee.turnBudgetMs = turnSearchMs;
    }

    public static TimingProfile parse(String text) {
        if (text == null || text.isBlank()) return CG;
        return valueOf(text.toUpperCase(Locale.ROOT));
    }

    /** Cap only a local opponent; the external Player must still receive its full IO deadline. */
    public Bot cap(Bot delegate) {
        return new Bot() {
            public String name() { return delegate.name(); }
            public void init(GameState s, int me) { delegate.init(s, me); }
            public List<Action> play(GameState s, int me, long budgetMs) {
                return delegate.play(s, me, Math.min(budgetMs, budget(s.turn)));
            }
            public void warmup(GameState s, int me, long ms) { delegate.warmup(s, me, ms); }
        };
    }
}
