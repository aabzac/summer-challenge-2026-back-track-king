package btk.submit;

import btk.bot.Bot;
import btk.bot.MainBot;
import btk.bot.Params;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Protocol;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CGRunnerTurnBudgetTest {
    @ParameterizedTest
    @ValueSource(ints = {38, 40})
    void laterAllowanceStillChargesPreparationAndFirstTurnWarmupIsUnchanged(int budgetMs) throws Exception {
        GameState s = new GameState(3, 1, new int[]{0, 1, 2}, new int[3],
                new int[]{0, 2}, new int[]{0, 0}, new int[][]{{1}, {}});
        String init = Protocol.initBlock(s, 0), turn = Protocol.turnBlock(s, 0);
        byte[] input = (init + turn + turn).getBytes(StandardCharsets.US_ASCII);
        // hasNext reads the first score byte before t0. Stall the NEXT read, inside readTurn.
        int delayAt = init.length() + turn.length() + 1;
        var in = new ByteArrayInputStream(input) {
            boolean delayed;
            @Override public synchronized int read(byte[] b, int offset, int length) {
                if (pos == delayAt && !delayed) {
                    delayed = true;
                    long until = System.nanoTime() + 12_000_000L;
                    while (System.nanoTime() < until)
                        java.util.concurrent.locks.LockSupport.parkNanos(until - System.nanoTime());
                }
                return super.read(b, offset, Math.min(length, 1));
            }
        };
        List<Long> budgets = new ArrayList<>(), warmups = new ArrayList<>();
        Bot spy = new Bot() {
            public String name() { return "budget-spy"; }
            public List<Action> play(GameState state, int me, long ms) { budgets.add(ms); return List.of(Action.waitAction()); }
            public void warmup(GameState state, int me, long ms) { warmups.add(ms); }
        };
        MainBot main = new MainBot(new Params("TURN_BUDGET_MS=" + budgetMs + ",ISOLATED_ROUTE_WARMUP=false"));
        // Observe the actual runner/delegate boundary without any strategic search or JIT warmup.
        var delegate = MainBot.class.getDeclaredField("delegate"); delegate.setAccessible(true); delegate.set(main, spy);
        var output = new ByteArrayOutputStream();
        CGRunner.run(in, new PrintStream(output), new PrintStream(new ByteArrayOutputStream()), main, false);
        assertEquals(List.of(500L), warmups);
        assertEquals(2, budgets.size());
        assertTrue(budgets.get(0) > budgetMs && budgets.get(0) <= 986, "first allowance remains 1000 minus14");
        assertTrue(budgets.get(1) >= 5 && budgets.get(1) <= budgetMs - 12,
                "the 12ms input stall is deducted from the configured allowance");
        assertEquals(2, output.toString(StandardCharsets.US_ASCII).lines().count());
        assertEquals(14, CGRunner.MARGIN_MS);
    }
}
