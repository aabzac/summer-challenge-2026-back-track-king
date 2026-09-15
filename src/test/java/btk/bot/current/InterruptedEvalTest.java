package btk.bot.current;

import btk.bot.BotFactory;
import btk.bot.Params;
import btk.bot.Timer;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.referee.MapGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InterruptedEvalTest {
    @Test void expiredEvalNeverReturnsAnOptimisticPartialScore() throws Exception {
        GameState s = connectedState();
        Params p = new Params("INTERRUPT_VULN_EVAL=1,PROFILE_SEARCH=1,DISRUPT_RESERVE_MS=120000");
        EvalBot bot = new EvalBot(p); bot.init(s, 0);
        var timerField = EvalBot.class.getDeclaredField("timer"); timerField.setAccessible(true);
        ((Timer) timerField.get(bot)).start(60_000); // phase already expired, no racing real time
        var eval = EvalBot.class.getDeclaredMethod("evalState", GameState.class, int.class);
        eval.setAccessible(true);
        assertTrue(Double.isNaN((double) eval.invoke(bot, s, 0)));
        assertEquals(1, bot.profileAbortedEvals);
        p.INTERRUPT_VULN_EVAL = false;
        assertTrue(Double.isFinite((double) eval.invoke(bot, s, 0)));
    }

    @Test void ampleTimeAndProfilingPreserveFrozenV05Actions() {
        for (int seed : new int[]{1000, 7000}) for (int me = 0; me < 2; me++) {
            GameState s = MapGenerator.generate(seed);
            var frozen = BotFactory.create("v05"); frozen.init(s.copy(), me);
            var current = new EvalBot(new Params("INTERRUPT_VULN_EVAL=1,PROFILE_SEARCH=1"));
            current.init(s.copy(), me);
            assertEquals(Action.format(frozen.play(s.copy(), me, 60_000)),
                    Action.format(current.play(s.copy(), me, 60_000)));
            assertTrue(current.profileLifetimeBuildCalls > 0);
            assertEquals(0, current.profileAbortedEvals);
        }
    }

    @Test void expiredBuildStillPermitsSabotageAndRejectsUnsupportedCombination() {
        GameState s = connectedState();
        EvalBot bot = new EvalBot(new Params("INTERRUPT_VULN_EVAL=1,PROFILE_SEARCH=1,DISRUPT_RESERVE_MS=120000"));
        bot.init(s.copy(), 1);
        var actions = bot.play(s.copy(), 1, 60_000);
        assertTrue(actions.stream().noneMatch(a -> a.type == Action.Type.PLACE));
        assertTrue(actions.stream().anyMatch(a -> a.type == Action.Type.DISRUPT));
        assertEquals(0, bot.profileLifetimeBuildCalls);
        assertThrows(IllegalArgumentException.class,
                () -> new EvalBot(new Params("INTERRUPT_VULN_EVAL=1,USE_REPLY_RERANK=1")));
    }

    private static GameState connectedState() {
        GameState s = new GameState(5, 1, new int[]{0, 1, 1, 1, 2}, new int[5],
                new int[]{0, 4}, new int[]{0, 0}, new int[][]{{1}, {}});
        s.owner[1] = s.owner[2] = s.owner[3] = 0;
        Rules.updateConnections(s, new Pathing(s.size));
        return s;
    }
}
