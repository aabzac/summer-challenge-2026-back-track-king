package btk.bot;

import btk.bot.current.EvalBot;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.referee.MapGenerator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class RouteWarmupTest {
    @Test void schedulerCountsSetupAndNeverRunsTinyTailOrTouchesInitialState() {
        GameState state = MapGenerator.generate(7000);
        int[] owners = state.owner.clone();
        AtomicLong clock = new AtomicLong();
        List<Long> budgets = new ArrayList<>();
        Bot disposable = new Bot() {
            public String name() { return "spy"; }
            public void init(GameState s, int me) { s.owner[0] = 1; clock.addAndGet(10_000_000); }
            public List<Action> play(GameState s, int me, long budget) {
                assertArrayEquals(owners, s.owner); // reset mutations from init and previous iteration
                budgets.add(budget);
                s.owner[0] = 1;
                clock.addAndGet(budget * 1_000_000);
                return List.of(Action.waitAction());
            }
        };
        assertEquals(2, MainBot.warmIsolated(disposable, state, 0, 100, 36, clock::get));
        assertEquals(List.of(36L, 36L), budgets);
        assertArrayEquals(owners, state.owner);
        assertEquals(0, state.turn);
    }

    @Test void routeSearchIsActuallyReached() {
        GameState state = MapGenerator.generate(7000);
        EvalBot disposable = new EvalBot(new Params("PROFILE_SEARCH=1"));
        assertTrue(MainBot.warmIsolated(disposable, state, 0, 500, 36, System::nanoTime) > 0);
        assertTrue(disposable.profileLifetimeBuildCalls > 0);
    }

    @Test void isolatedWarmupPreservesLiveRandomSequenceAndActions() {
        for (int me = 0; me < 2; me++) {
            GameState initial = MapGenerator.generate(1000);
            MainBot warm = new MainBot(new Params("ISOLATED_ROUTE_WARMUP=1"));
            MainBot cold = new MainBot(new Params("ISOLATED_ROUTE_WARMUP=1"));
            warm.init(initial.copy(), me); cold.init(initial.copy(), me);
            warm.warmup(initial, me, 150);
            assertTrue(warm.warmupCalls > 0);
            GameState input = initial.copy();
            Pathing pathing = new Pathing(input.size);
            for (int turn = 0; turn < 4; turn++) {
                var expected = cold.play(input.copy(), me, 60_000);
                assertEquals(Action.format(expected), Action.format(warm.play(input.copy(), me, 60_000)));
                var wait = List.of(Action.waitAction());
                Rules.resolve(input, me == 0 ? expected : wait, me == 1 ? expected : wait, pathing);
            }
        }
    }

    @Test void noAllowanceDoesNoWorkAndInvalidSliceIsRejected() {
        MainBot bot = new MainBot(new Params("ISOLATED_ROUTE_WARMUP=1"));
        GameState state = MapGenerator.generate(1000); bot.init(state, 0);
        bot.warmup(state, 0, 0);
        assertEquals(0, bot.warmupCalls);
        assertThrows(IllegalArgumentException.class,
                () -> new MainBot(new Params("ISOLATED_ROUTE_WARMUP=1,WARMUP_SLICE_MS=5")));
    }

    @Test void fractionalRemainderCannotRoundUpToAnotherSlice() {
        AtomicLong clock = new AtomicLong();
        Bot spy = new Bot() {
            public String name() { return "spy"; }
            public void init(GameState s, int me) { clock.set(1); }
            public List<Action> play(GameState s, int me, long budget) {
                fail("36 ms minus 1 ns cannot fit a 36-ms slice");
                return List.of();
            }
        };
        assertEquals(0, MainBot.warmIsolated(spy, MapGenerator.generate(7000), 0, 36, 36, clock::get));
    }
}
