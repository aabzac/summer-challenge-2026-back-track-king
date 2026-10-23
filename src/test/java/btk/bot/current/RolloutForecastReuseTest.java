package btk.bot.current;

import btk.bot.Params;
import btk.bot.Timer;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.core.SearchState;
import com.google.gson.Gson;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Differential checks against the unchanged native copy-and-refresh path, on synthetic boards. */
class RolloutForecastReuseTest {
    private static final Gson JSON = new Gson();

    @Test void directEmptyBuildChooserMatchesCopiedAndRefreshedChooserWithoutMutatingItsBoard() throws Exception {
        for (int me = 0; me < 2; me++) for (int instability = 0; instability < 4; instability++) {
            GameState state = board(me);
            state.regionInstability[2] = state.instability[2] = instability;
            RolloutBot bot = initialized(state, me);
            SearchState copied = new SearchState(state);
            int[] empty = new int[0];
            call("placeOwn", null, new Class<?>[]{SearchState.class, int.class, int[].class, int.class},
                    copied, me, empty, 0);
            String sourceBefore = JSON.toJson(state), copiedBefore = JSON.toJson(copied.state);
            for (int chooser = 0; chooser < 2; chooser++) {
                ((Timer) field(bot, "timer")).start(60_000);
                int expected = (int) call("chooseTarget", bot, new Class<?>[]{GameState.class, int.class}, copied.state, chooser);
                ((Timer) field(bot, "timer")).start(60_000);
                int actual = (int) call("chooseTarget", bot, new Class<?>[]{GameState.class, int.class}, state, chooser);
                assertEquals(expected, actual, "The same canonical zero-build position must choose the same target");
                assertEquals(sourceBefore, JSON.toJson(state));
                assertEquals(copiedBefore, JSON.toJson(copied.state));
            }
        }
    }

    @Test void fullForecastValueAndFinalCoreStateMatchNativePathForBothSeatsAndRootTargets() throws Exception {
        for (int me = 0; me < 2; me++) for (int target : new int[]{-1, 2}) {
            GameState state = board(me); String before = JSON.toJson(state);
            RolloutBot bot = prepared(state, me);
            int root = 1; // Preserve an actual non-WAIT index, as a network-root continuation does.
            int[][] roots = (int[][]) field(bot, "roots"); int[] rootCounts = (int[]) field(bot, "rootCounts");
            int[] saved = roots[root].clone(); int count = rootCounts[root];
            System.arraycopy(field(bot, "bestCells"), 0, roots[root], 0, 2); rootCounts[root] = 2;
            ((Timer) field(bot, "timer")).start(60_000);
            double expected;
            try {
                // deadlineProbeActive=false takes precisely the old targetBoard.set/refresh path.
                expected = (double) call("rollout", bot,
                        new Class<?>[]{GameState.class, int.class, int.class, int.class, int.class}, state, me, root, target, 0);
            } finally {
                System.arraycopy(saved, 0, roots[root], 0, saved.length); rootCounts[root] = count;
            }
            GameState expectedState = ((SearchState) field(bot, "work")).state.copy();
            int simulated = bot.simulatedTurns;
            assertEquals(0, bot.deadlineReusedBoards, "Native comparisons must never use the shortcut");
            ((Timer) field(bot, "timer")).start(60_000);
            double actual = bot.deadlineRollout(state, me, root, target, 0, -1);
            assertTrue(Double.isFinite(expected));
            assertEquals(Double.doubleToLongBits(expected), Double.doubleToLongBits(actual));
            sameCoreState(expectedState, ((SearchState) field(bot, "work")).state);
            assertTrue(simulated > 1, "Fixture must exercise continuation, not only the root");
            // Enemy constructs three cells at offset0; every subsequent chooser has zero build.
            assertEquals(2 * (simulated - 1), bot.deadlineReusedBoards);
            assertEquals(simulated, bot.simulatedTurns, "Supplementary work preserves the native work counter");
            assertEquals(simulated, bot.deadlineExtraTurns);
            assertArrayEquals(saved, roots[root]); assertEquals(count, rootCounts[root]);
            assertArrayEquals(new int[]{5, 6}, Arrays.copyOf((int[]) field(bot, "bestCells"), 2));
            assertEquals(before, JSON.toJson(state));
            assertFalse((boolean) field(bot, "deadlineProbeActive"));
            bot.deadlineBegin(state.turn + 1, me);
            assertEquals(0, bot.deadlineReusedBoards, "Savings diagnostics reset on each real decision");
        }
    }

    @Test void emptyOpponentAtRootIsReusableButFullOwnConstructionStillExecutes() throws Exception {
        for (int me = 0; me < 2; me++) {
            GameState state = board(me); RolloutBot bot = prepared(state, me);
            ((int[]) field(bot, "counts"))[1 - me] = 0;
            // Isolate full root execution from unrelated sabotage in subsequent turns.
            ((Params) field(bot, "p")).ROLLOUT_DEPTH = 1;
            ((Timer) field(bot, "timer")).start(60_000);
            assertTrue(Double.isFinite(bot.deadlineRollout(state, me, 1, -1, 0, -1)));
            int turns = bot.deadlineExtraTurns;
            assertTrue(turns > 0);
            assertEquals(2 * turns - 1, bot.deadlineReusedBoards);
            GameState result = ((SearchState) field(bot, "work")).state;
            assertEquals(me, result.owner[5]); assertEquals(me, result.owner[6]);
        }
    }

    private static RolloutBot initialized(GameState state, int me) {
        RolloutBot bot = new RolloutBot(new Params(btk.bot.LiveTargetExperimentParams.V10_OFF + ",NOISE=0,USE_ROLLOUT_MISSED_DEADLINE=true"));
        bot.init(state, me); return bot;
    }

    private static RolloutBot prepared(GameState state, int me) throws Exception {
        RolloutBot bot = initialized(state, me);
        ((int[]) field(bot, "counts"))[1 - me] = 1;
        int[][][] routes = (int[][][]) field(bot, "routes");
        System.arraycopy(new int[]{7, 8, 9}, 0, routes[1 - me][0], 0, 3);
        ((int[][]) field(bot, "lengths"))[1 - me][0] = 3;
        ((int[][]) field(bot, "roots"))[1][0] = 5;
        ((int[]) field(bot, "rootCounts"))[1] = 1;
        System.arraycopy(new int[]{5, 6}, 0, field(bot, "bestCells"), 0, 2);
        var count = RolloutBot.class.getDeclaredField("bestCount"); count.setAccessible(true); count.setInt(bot, 2);
        return bot;
    }

    private static GameState board(int me) {
        GameState state = new GameState(5, 2, new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9}, new int[10],
                new int[]{0, 4}, new int[]{0, 0}, new int[][]{{1}, {}});
        state.owner[1] = me; state.owner[2] = state.owner[3] = 1 - me;
        state.score[me] = 100; state.turn = 10;
        Rules.updateConnections(state, new Pathing(state.size)); return state;
    }

    private static Object field(RolloutBot bot, String name) throws Exception {
        var field = RolloutBot.class.getDeclaredField(name); field.setAccessible(true); return field.get(bot);
    }

    private static Object call(String name, Object receiver, Class<?>[] types, Object... arguments) throws Exception {
        Method method = RolloutBot.class.getDeclaredMethod(name, types); method.setAccessible(true);
        return method.invoke(receiver, arguments);
    }

    private static void sameCoreState(GameState expected, GameState actual) {
        assertEquals(expected.turn, actual.turn); assertEquals(expected.maxTurns, actual.maxTurns);
        assertArrayEquals(expected.owner, actual.owner); assertArrayEquals(expected.score, actual.score);
        assertArrayEquals(expected.instability, actual.instability); assertArrayEquals(expected.inked, actual.inked);
        assertArrayEquals(expected.regionInstability, actual.regionInstability); assertArrayEquals(expected.regionInked, actual.regionInked);
        assertArrayEquals(expected.pairPathLen, actual.pairPathLen);
        for (int i = 0; i < expected.pairCount; i++) {
            if (expected.pairPath[i] == null) assertNull(actual.pairPath[i]);
            else assertArrayEquals(Arrays.copyOf(expected.pairPath[i], expected.pairPathLen[i]),
                    Arrays.copyOf(actual.pairPath[i], actual.pairPathLen[i]));
        }
    }
}
