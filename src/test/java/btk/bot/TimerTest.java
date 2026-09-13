package btk.bot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TimerTest {
    @Test
    void phaseDeadlineDoesNotConsumeOrRestartOverallBudget() {
        Timer timer = new Timer();
        timer.start(60_000);
        assertTrue(timer.up(60_000));
        assertFalse(timer.up());
        assertFalse(timer.up(0));
        assertFalse(timer.up(-1));
        assertTrue(timer.leftMs() > 0 && timer.leftMs() <= 60_000);
    }

    @Test
    void expiredOverallBudgetAlsoExpiresEveryPhase() {
        Timer timer = new Timer();
        timer.start(-1);
        assertTrue(timer.up());
        assertTrue(timer.up(0));
        assertTrue(timer.up(6));
    }
}
