package btk.runner;

import btk.bot.Bot;
import btk.core.Action;
import btk.core.GameState;
import btk.referee.Referee;
import btk.submit.CGRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.*;

class TimingProfileTest {
    private static final String BASE = "current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",NOISE=0.1,USE_BATCHED_REFRESH=true";
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

    record BudgetSeen(String spec, int side, int turn, long ms) {}

    @ParameterizedTest
    @ValueSource(ints = {38, 40, 45})
    void perArmBudgetsFollowBothSeatAssignmentsAndAreReportedExplicitly(int budgetMs) throws Exception {
        String candidate = BASE + ",TURN_BUDGET_MS=" + budgetMs;
        var seen = new ConcurrentLinkedQueue<BudgetSeen>();
        var stats = BenchmarkRunner.run(candidate, BASE, 2, new Maps(""), 1, 891,
                TimingProfile.CG, spec -> new Bot() {
                    public String name() { return spec; }
                    public List<Action> play(GameState s, int me, long ms) {
                        seen.add(new BudgetSeen(spec, me, s.turn, ms));
                        return List.of(Action.waitAction());
                    }
                });
        for (String spec : List.of(candidate, BASE)) for (int side = 0; side < 2; side++) {
            final int seat = side;
            var events = seen.stream().filter(e -> e.spec.equals(spec) && e.side == seat).toList();
            assertEquals(1, events.stream().filter(e -> e.turn == 0 && e.ms == 486).count());
            assertTrue(events.stream().anyMatch(e -> e.turn > 0));
            assertTrue(events.stream().filter(e -> e.turn > 0).allMatch(e -> e.ms == (spec.equals(candidate) ? budgetMs : 36)));
        }
        assertEquals(486, stats.firstSearchBudgetMsA);
        assertEquals(486, stats.firstSearchBudgetMsB);
        assertEquals(budgetMs, stats.turnSearchBudgetMsA);
        assertEquals(36, stats.turnSearchBudgetMsB);
        assertEquals(36, stats.turnSearchBudgetMs, "historical global field remains the profile baseline");
        assertTrue(stats.report().contains("later A/B " + budgetMs + "/36 ms"));
    }

    @ParameterizedTest
    @ValueSource(ints = {38, 40, 45})
    void overrideIsRejectedUnderLegacyBeforeBotsOrGamesAndUnsetSeatsStayCompatible(int budgetMs) throws Exception {
        String candidate = BASE + ",TURN_BUDGET_MS=" + budgetMs;
        assertThrows(IllegalArgumentException.class, () -> BenchmarkRunner.run(candidate, BASE, 2,
                new Maps(""), 1, 891, TimingProfile.LEGACY, spec -> { fail("No bot creation before validation"); return null; }));
        Referee ref = new Referee();
        TimingProfile.CG.apply(ref, candidate, BASE);
        assertArrayEquals(new long[]{budgetMs, 36}, ref.playerTurnBudgetMs);
        TimingProfile.CG.apply(ref, BASE, candidate);
        assertArrayEquals(new long[]{36, budgetMs}, ref.playerTurnBudgetMs);
        TimingProfile.LEGACY.apply(ref);
        assertArrayEquals(new long[]{-1, -1}, ref.playerTurnBudgetMs);
        assertEquals(1000, ref.firstTurnBudgetMs);
        assertEquals(50, ref.turnBudgetMs);
    }

    @ParameterizedTest
    @ValueSource(ints = {38, 40, 45})
    void processOpponentCapHonorsItsRecipeWithoutChangingTheExternal50MsLimit(int budgetMs) {
        long[] seen = {-1};
        Bot delegate = new Bot() {
            public String name() { return "probe"; }
            public List<Action> play(GameState s, int me, long ms) { seen[0] = ms; return List.of(Action.waitAction()); }
        };
        GameState s = new GameState(3, 1, new int[]{0, 1, 2}, new int[3],
                new int[]{0, 2}, new int[]{0, 0}, new int[][]{{1}, {}});
        Referee external = new Referee();
        Bot capped = TimingProfile.CG.cap(delegate, BASE + ",TURN_BUDGET_MS=" + budgetMs);
        capped.play(s, 0, external.firstTurnBudgetMs);
        assertEquals(486, seen[0]);
        s.turn = 1;
        capped.play(s, 0, external.turnBudgetMs);
        assertEquals(budgetMs, seen[0]);
        capped.play(s, 0, 7);
        assertEquals(7, seen[0], "capping cannot enlarge the caller's allowance");
        assertEquals(50, external.turnBudgetMs);
        assertArrayEquals(new long[]{-1, -1}, external.playerTurnBudgetMs);
    }
}
