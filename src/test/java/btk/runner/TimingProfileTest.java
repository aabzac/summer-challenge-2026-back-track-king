package btk.runner;

import btk.bot.Bot;
import btk.core.Action;
import btk.core.GameState;
import btk.referee.Referee;
import btk.submit.CGRunner;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.*;

class TimingProfileTest {
    @Test
    void cgBudgetsFollowSubmissionConstantsAndLegacyRemainsExplicit() {
        assertEquals(CGRunner.FIRST_TURN_MS - CGRunner.MARGIN_MS - CGRunner.WARMUP_MS,
                TimingProfile.CG.budget(0));
        assertEquals(CGRunner.TURN_MS - CGRunner.MARGIN_MS, TimingProfile.CG.budget(1));
        assertEquals(1000, TimingProfile.LEGACY.budget(0));
        assertEquals(50, TimingProfile.LEGACY.budget(1));
        assertSame(TimingProfile.CG, TimingProfile.parse(""));
        assertSame(TimingProfile.LEGACY, TimingProfile.parse("legacy"));
        assertThrows(IllegalArgumentException.class, () -> TimingProfile.parse("typo"));
    }

    @Test
    void benchPassesProfileToBothSidesAndReportsIt() throws Exception {
        for (TimingProfile timing : TimingProfile.values()) {
            var seen = new ConcurrentLinkedQueue<Long>();
            var stats = BenchmarkRunner.run("v03", "v04", 2, new Maps(""), 1, 891,
                    timing, spec -> new Bot() {
                        public String name() { return spec; }
                        public List<Action> play(GameState s, int me, long ms) {
                            assertEquals(timing.budget(s.turn), ms);
                            seen.add(ms);
                            return List.of(Action.waitAction());
                        }
                    });
            assertEquals(4, seen.stream().filter(ms -> ms == timing.firstSearchMs).count());
            assertTrue(seen.contains(timing.turnSearchMs));
            assertSame(timing, stats.timingProfile);
            assertEquals(timing.firstSearchMs, stats.firstSearchBudgetMs);
            assertEquals(timing.turnSearchMs, stats.turnSearchBudgetMs);
        }
    }

    @Test
    void cappingLocalOpponentDoesNotChangeProcessDeadlineOrIncreaseCallerBudget() {
        Referee referee = new Referee();
        long[] seen = {-1};
        Bot delegate = new Bot() {
            public String name() { return "probe"; }
            public List<Action> play(GameState s, int me, long ms) {
                seen[0] = ms;
                return List.of(Action.waitAction());
            }
        };
        GameState s = new GameState(3, 1, new int[]{0, 1, 2}, new int[3],
                new int[]{0, 2}, new int[]{0, 0}, new int[][]{{1}, {}});
        Bot capped = TimingProfile.CG.cap(delegate);
        capped.play(s, 0, referee.firstTurnBudgetMs);
        assertEquals(TimingProfile.CG.firstSearchMs, seen[0]);
        s.turn = 1;
        capped.play(s, 0, referee.turnBudgetMs);
        assertEquals(TimingProfile.CG.turnSearchMs, seen[0]);
        capped.play(s, 0, 7);
        assertEquals(7, seen[0]);
        assertEquals(CGRunner.FIRST_TURN_MS, referee.firstTurnBudgetMs);
        assertEquals(CGRunner.TURN_MS, referee.turnBudgetMs);
    }
}
