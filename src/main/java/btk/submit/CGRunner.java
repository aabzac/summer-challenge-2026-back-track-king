package btk.submit;

import btk.bot.Bot;
import btk.bot.MainBot;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Protocol;
import btk.core.Rules;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;

/**
 * Entry point on CodinGame (and for {@code ProcessBot} locally). Reads the protocol, keeps the
 * state in sync, cross-checks our connection computation against the server's
 * {@code partOfActiveConnections}, and dispatches to {@link MainBot}.
 *
 * <p>Timing: the referee's clock starts when it sends the turn input, so the budget is
 * measured from the first token of the turn block. First turn: warm up the JIT with the
 * spare time before answering.
 */
public class CGRunner {

    public static final long FIRST_TURN_MS = 1000;
    public static final long TURN_MS = 50;
    /** Safety margin subtracted from the CG limits. */
    public static final long MARGIN_MS = 14;
    /** Portion of the first turn spent warming up the JIT. */
    public static final long WARMUP_MS = 500;
    /** Mirror stdin/stdout to stderr as [P:IN]/[P:OUT] (large; off by default). */
    public static final boolean LOG_IO_DEFAULT = false;
    /** Warn on stderr when our connection model disagrees with the server. */
    public static final boolean CHECK_CONNECTIONS = true;

    public static void main(String[] args) {
        try {
            run(System.in, System.out, System.err, new MainBot(), LOG_IO_DEFAULT);
        } catch (IOException e) {
            System.err.println("IO error: " + e);
        }
    }

    public static void run(InputStream in, PrintStream out, PrintStream err, Bot bot, boolean logIo)
            throws IOException {
        Protocol.TokenReader reader = new Protocol.TokenReader(in);
        Protocol.Init init = Protocol.readInit(reader);
        GameState s = init.state;
        int myId = init.myId;
        Pathing pathing = new Pathing(s.size);
        bot.init(s, myId);
        if (logIo) err.println("[P" + myId + ":INIT] " + s.width + "x" + s.height + " towns=" + s.townCount
                + " pairs=" + s.pairCount);

        int turn = 0;
        while (reader.hasNext()) {
            long budget = (turn == 0 ? FIRST_TURN_MS : TURN_MS) - MARGIN_MS;
            long t0 = System.nanoTime();
            Protocol.TurnInfo ti = Protocol.readTurn(reader, s, myId);
            s.turn = turn;
            s.syncRegionsFromCells();
            Rules.updateConnections(s, pathing);
            if (CHECK_CONNECTIONS) checkConnections(s, ti, err, turn);

            if (turn == 0) {
                bot.warmup(s.copy(), myId, WARMUP_MS);
            }
            long usedMs = (System.nanoTime() - t0) / 1_000_000L;
            List<Action> actions = bot.play(s.copy(), myId, Math.max(5, budget - usedMs));
            String line = Action.format(actions);
            out.println(line);
            out.flush();
            if (logIo) err.println("[P" + myId + ":OUT] " + line);
            long totalMs = (System.nanoTime() - t0) / 1_000_000L;
            if (totalMs > budget) err.println("WARN turn " + turn + " took " + totalMs + "ms");
            turn++;
        }
    }

    /** Compares server-reported active connection membership with our recomputation. */
    private static void checkConnections(GameState s, Protocol.TurnInfo ti, PrintStream err, int turn) {
        String[] ours = Protocol.activeStrings(s);
        int mismatches = 0;
        for (int i = 0; i < s.size; i++) {
            String a = ti.activeRaw[i], b = ours[i];
            if (a == null ? b != null : !sameSet(a, b)) {
                if (mismatches < 3) {
                    err.println("CONN MISMATCH t" + turn + " cell " + s.x(i) + "," + s.y(i)
                            + " server=" + a + " ours=" + b);
                }
                mismatches++;
            }
        }
        if (mismatches > 0) err.println("CONN MISMATCH t" + turn + " total=" + mismatches);
    }

    private static boolean sameSet(String a, String b) {
        if (b == null) return false;
        if (a.equals(b)) return true;
        String[] x = a.split(","), y = b.split(",");
        if (x.length != y.length) return false;
        java.util.Arrays.sort(x);
        java.util.Arrays.sort(y);
        return java.util.Arrays.equals(x, y);
    }
}
