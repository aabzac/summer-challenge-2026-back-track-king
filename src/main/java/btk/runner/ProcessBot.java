package btk.runner;

import btk.bot.Bot;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Runs an external program (e.g. the merged {@code Player.java}) as a bot over the exact CG
 * stdin/stdout protocol, with a wall-clock timeout per turn. Used to verify the submission
 * end-to-end (parsing, JIT warm-up, timing) before pasting into CodinGame.
 */
public final class ProcessBot implements Bot {

    private final List<String> command;
    private final String label;
    private Process proc;
    private Writer stdin;
    private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();
    /** Extra wall time granted on top of the referee budget (process IO overhead). */
    public long graceMs = 30;
    public int timeouts;
    /** Slowest response after the first turn (which includes JIT warm-up). */
    public long maxResponseMs;
    public long firstTurnMs;

    public ProcessBot(String label, List<String> command) {
        this.label = label;
        this.command = command;
    }

    /** {@code java -cp <classesDir> Player} */
    public static ProcessBot javaPlayer(String classesDir) {
        String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        return new ProcessBot("Player.java", List.of(javaBin, "-cp", classesDir, "Player"));
    }

    @Override
    public String name() {
        return "proc:" + label;
    }

    @Override
    public void init(GameState s, int myId) {
        try {
            proc = new ProcessBuilder(command).start();
            stdin = new OutputStreamWriter(proc.getOutputStream(), StandardCharsets.US_ASCII);
            Thread out = new Thread(() -> pump(proc.getInputStream(), true), "proc-stdout");
            Thread err = new Thread(() -> pump(proc.getErrorStream(), false), "proc-stderr");
            out.setDaemon(true);
            err.setDaemon(true);
            out.start();
            err.start();
            stdin.write(Protocol.initBlock(s, myId));
            stdin.flush();
        } catch (IOException e) {
            throw new IllegalStateException("cannot start " + command, e);
        }
    }

    private void pump(java.io.InputStream in, boolean isStdout) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (isStdout) lines.add(line);
                else System.err.println("[" + label + " stderr] " + line);
            }
        } catch (IOException ignored) {
            // process ended
        }
    }

    @Override
    public List<Action> play(GameState s, int myId, long budgetMs) {
        try {
            long t0 = System.nanoTime();
            stdin.write(Protocol.turnBlock(s, myId));
            stdin.flush();
            String line = lines.poll(budgetMs + graceMs, TimeUnit.MILLISECONDS);
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            if (s.turn == 0) firstTurnMs = ms;
            else maxResponseMs = Math.max(maxResponseMs, ms);
            if (line == null) {
                timeouts++;
                System.err.printf("[%s] TIMEOUT turn %d (> %d ms)%n", label, s.turn, budgetMs + graceMs);
                return List.of(Action.waitAction());
            }
            return Action.parseLine(line);
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void warmup(GameState s, int myId, long ms) {
        // the external process warms itself up on its first turn
    }

    public void close() {
        if (proc != null) {
            proc.destroy();
            try {
                if (!proc.waitFor(2, TimeUnit.SECONDS)) proc.destroyForcibly();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
