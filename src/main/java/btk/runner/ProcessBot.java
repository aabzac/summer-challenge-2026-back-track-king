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
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

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
    record Response(String line, long receivedNs) {}
    public record TurnTiming(int turn, long budgetMs, long graceMs, double sendMs,
                             Double responseMs, double consumerMs, String status) {}
    private final BlockingQueue<Response> lines;
    private final LongSupplier clock;
    private boolean terminal;
    /** Explicit diagnostic allowance only. Default checks the nominal deadline without grace. */
    public long graceMs = 0;
    public int timeouts;
    public int nominalOverruns;
    public final List<TurnTiming> timings = new ArrayList<>();
    /** Slowest response after the first turn (which includes JIT warm-up). */
    public long maxResponseMs;
    public long firstTurnMs;

    public ProcessBot(String label, List<String> command) {
        this.label = label;
        this.command = List.copyOf(command);
        this.lines = new LinkedBlockingQueue<>();
        this.clock = System::nanoTime;
    }

    /** Deterministic transport/clock seam for deadline regression tests. */
    ProcessBot(Writer stdin, BlockingQueue<Response> lines, LongSupplier clock) {
        this.label = "test";
        this.command = List.of();
        this.stdin = stdin;
        this.lines = lines;
        this.clock = clock;
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
        if (proc != null || terminal) throw new IllegalStateException("ProcessBot is single-use");
        try {
            proc = new ProcessBuilder(command).start();
            stdin = new OutputStreamWriter(proc.getOutputStream(), StandardCharsets.US_ASCII);
            Process child = proc;
            Thread out = new Thread(() -> pump(child.getInputStream(), true), "proc-stdout");
            Thread err = new Thread(() -> pump(child.getErrorStream(), false), "proc-stderr");
            out.setDaemon(true);
            err.setDaemon(true);
            out.start();
            err.start();
            stdin.write(Protocol.initBlock(s, myId));
            stdin.flush();
        } catch (IOException e) {
            stop();
            throw new IllegalStateException("cannot start " + command, e);
        }
    }

    private void pump(java.io.InputStream in, boolean isStdout) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (isStdout) lines.add(new Response(line, clock.getAsLong()));
                else System.err.println("[" + label + " stderr] " + line);
            }
        } catch (IOException ignored) {
            // process ended
        } finally {
            // Wake immediately on EOF/read failure; do not mislabel it as a missing response.
            if (isStdout) lines.add(new Response(null, clock.getAsLong()));
        }
    }

    @Override
    public List<Action> play(GameState s, int myId, long budgetMs) {
        if (terminal) throw new IllegalStateException("Process session already ended");
        if (budgetMs <= 0 || graceMs < 0) throw new IllegalArgumentException("Invalid process deadline");
        long allowedNs = TimeUnit.MILLISECONDS.toNanos(Math.addExact(budgetMs, graceMs));
        try {
            long t0 = clock.getAsLong();
            stdin.write(Protocol.turnBlock(s, myId));
            stdin.flush();
            long sentNs = clock.getAsLong();
            long remainingNs = Math.max(0, allowedNs - (sentNs - t0));
            Response response = lines.poll(remainingNs, TimeUnit.NANOSECONDS);
            long consumedNs = clock.getAsLong();
            // The consumer may resume well after the reader queued the answer. Judge arrival,
            // not poll return time, but never accept a line stamped after the absolute deadline.
            Long elapsedNs = response == null ? null : response.receivedNs() - t0;
            boolean late = response == null || elapsedNs > allowedNs;
            boolean nominalOverrun = response == null || elapsedNs > TimeUnit.MILLISECONDS.toNanos(budgetMs);
            if (nominalOverrun) nominalOverruns++;
            String status = response != null && elapsedNs < 0 ? "UNSOLICITED"
                    : late ? "TIMEOUT" : response.line() == null ? "EOF"
                    : nominalOverrun ? "GRACE" : "OK";
            timings.add(new TurnTiming(s.turn, budgetMs, graceMs, (sentNs - t0) / 1e6,
                    elapsedNs == null ? null : elapsedNs / 1e6, (consumedNs - t0) / 1e6, status));
            long ms = (elapsedNs == null ? consumedNs - t0 : Math.max(0, elapsedNs)) / 1_000_000L;
            if (s.turn == 0) firstTurnMs = ms;
            else maxResponseMs = Math.max(maxResponseMs, ms);
            if (status.equals("TIMEOUT")) {
                timeouts++;
                stop();
                throw new IllegalStateException("TIMEOUT turn " + s.turn + " (deadline "
                        + (budgetMs + graceMs) + " ms)");
            }
            if (status.equals("EOF") || status.equals("UNSOLICITED")) {
                stop();
                throw new IllegalStateException(status + " on turn " + s.turn);
            }
            return Action.parseLine(response.line());
        } catch (InterruptedException e) {
            stop();
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (IOException e) {
            stop();
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void warmup(GameState s, int myId, long ms) {
        // the external process warms itself up on its first turn
    }

    private void stop() {
        terminal = true;
        if (proc != null) proc.destroy();
    }

    public void close() {
        stop();
        if (proc != null) {
            try {
                if (!proc.waitFor(2, TimeUnit.SECONDS)) proc.destroyForcibly();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
