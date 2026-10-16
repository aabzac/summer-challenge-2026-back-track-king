package btk.runner;

import btk.bot.Bot;
import btk.bot.BotFactory;
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
        referee.playerTurnBudgetMs[0] = referee.playerTurnBudgetMs[1] = -1;
    }

    /** Resolve before creating workers; legacy timing must never silently ignore an override. */
    public long turnBudget(String spec) {
        int configured = BotFactory.parameters(spec).TURN_BUDGET_MS;
        if (this != CG && configured != 36)
            throw new IllegalArgumentException("A turn-budget override requires CG timing");
        return this == CG ? configured : turnSearchMs;
    }

    /** Specs are in current seat order, including after the paired-game seat swap. */
    public void apply(Referee referee, String spec0, String spec1) {
        long budget0 = turnBudget(spec0), budget1 = turnBudget(spec1);
        apply(referee);
        referee.playerTurnBudgetMs[0] = budget0;
        referee.playerTurnBudgetMs[1] = budget1;
    }

    public static TimingProfile parse(String text) {
        if (text == null || text.isBlank()) return CG;
        return valueOf(text.toUpperCase(Locale.ROOT));
    }

    /** Cap only a local opponent; the external Player must still receive its full IO deadline. */
    public Bot cap(Bot delegate) {
        return cap(delegate, turnSearchMs);
    }

    /** The local opponent's recipe changes its search cap, never the external process deadline. */
    public Bot cap(Bot delegate, String spec) {
        return cap(delegate, turnBudget(spec));
    }

    private Bot cap(Bot delegate, long laterMs) {
        return new Bot() {
            public String name() { return delegate.name(); }
            public void init(GameState s, int me) { delegate.init(s, me); }
            public List<Action> play(GameState s, int me, long budgetMs) {
                return delegate.play(s, me, Math.min(budgetMs, s.turn == 0 ? firstSearchMs : laterMs));
            }
            public void warmup(GameState s, int me, long ms) { delegate.warmup(s, me, ms); }
        };
    }
}
