package btk.tools;

import btk.core.GameState;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/** Instrumented tools bridge. Exact generated policy; profiling overhead makes this diagnostic only. */
public final class ProfileGeneratedPolicy implements AutoCloseable {
    public record Timing(long startNs, long endNs, String startInstant, String endInstant,
                         Long threadCpuBeforeNs, Long threadCpuAfterNs, Double threadCpuMs,
                         Long gcCountBefore, Long gcCountAfter, Long gcCountDelta,
                         Long gcTimeBeforeMs, Long gcTimeAfterMs, Long gcTimeDeltaMs,
                         long threadId, String correlationId) {}
    public record Decision(String action, double elapsedMs, Map<String, Object> diagnostics, Timing timing) {}
    private record Gc(long count, long millis) {}

    @Name("btk.GeneratedPolicyPlay")
    @Label("Generated policy play")
    @Category({"BackTrackKing", "Runtime attribution"})
    @StackTrace(false)
    public static final class PlayEvent extends Event {
        public String correlationId;
        public int turn, player;
        public long budgetMs, startNs, endNs, threadCpuBeforeNs, threadCpuAfterNs;
        public long gcCountBefore, gcCountAfter, gcTimeBeforeMs, gcTimeAfterMs;
        public String startInstant, endInstant, failure;
    }

    private final GeneratedPolicy delegate;
    private final Object generatedBot;
    private final Method prepareState, generatedPlay, generatedFormat, diagnostics;
    private final ThreadMXBean threads;
    private final List<GarbageCollectorMXBean> collectors;
    private boolean initialized, closed;

    public ProfileGeneratedPolicy(Path submissionDirectory) throws Exception {
        delegate = new GeneratedPolicy(submissionDirectory);
        try {
            generatedBot = member(delegate, "bot");
            generatedPlay = (Method) member(delegate, "playMethod");
            generatedFormat = (Method) member(delegate, "formatMethod");
            prepareState = GeneratedPolicy.class.getDeclaredMethod("prepareState", GameState.class);
            prepareState.setAccessible(true);
            diagnostics = GeneratedPolicy.class.getDeclaredMethod("diagnostics");
            diagnostics.setAccessible(true);
            threads = ManagementFactory.getThreadMXBean();
            if (threads.isThreadCpuTimeSupported() && !threads.isThreadCpuTimeEnabled()) {
                try { threads.setThreadCpuTimeEnabled(true); } catch (UnsupportedOperationException | SecurityException ignored) {}
            }
            collectors = ManagementFactory.getGarbageCollectorMXBeans();
        } catch (Exception | Error failure) {
            try { delegate.close(); } catch (IOException suppressed) { failure.addSuppressed(suppressed); }
            throw failure;
        }
    }

    public void init(GameState state, int me) throws Exception {
        initialized = false;
        delegate.init(state, me);
        initialized = true;
    }

    public void warmup(GameState state, int me, long millis) throws Exception {
        delegate.warmup(state, me, millis);
    }

    public Decision play(GameState state, int me, long budgetMs) throws Exception {
        return play(state, me, budgetMs, "turn-" + state.turn + "-player-" + me);
    }

    /** Callable independently for later fixed-input attribution probes; retains generated bot memory. */
    @SuppressWarnings("unchecked")
    public Decision play(GameState state, int me, long budgetMs, String correlationId) throws Exception {
        if (closed || !initialized) throw new IllegalStateException("Initialize an open profiling policy first");
        if (me != 0 && me != 1) throw new IllegalArgumentException("Invalid player: " + me);
        Object[] arguments = {invoke(prepareState, delegate, state), me, budgetMs};
        PlayEvent event = new PlayEvent();
        event.correlationId = correlationId; event.turn = state.turn; event.player = me; event.budgetMs = budgetMs;
        // Counter snapshots and wall-clock instants bracket the measured invoke; no management calls inside it.
        Gc beforeGc = gc();
        long beforeCpu = cpu();
        Instant beforeInstant = Instant.now();
        Object actions = null;
        Throwable failure = null;
        event.begin();
        long startNs = System.nanoTime();
        long endNs;
        try {
            actions = generatedPlay.invoke(generatedBot, arguments);
        } catch (InvocationTargetException e) {
            failure = e.getCause();
        } catch (Exception | Error e) {
            failure = e;
        } finally {
            endNs = System.nanoTime();
            event.end();
        }
        Instant afterInstant = Instant.now();
        long afterCpu = cpu();
        Gc afterGc = gc();
        event.startNs = startNs; event.endNs = endNs;
        event.startInstant = beforeInstant.toString(); event.endInstant = afterInstant.toString();
        event.threadCpuBeforeNs = beforeCpu; event.threadCpuAfterNs = afterCpu;
        event.gcCountBefore = beforeGc.count; event.gcCountAfter = afterGc.count;
        event.gcTimeBeforeMs = beforeGc.millis; event.gcTimeAfterMs = afterGc.millis;
        event.failure = failure == null ? "" : failure.toString();
        event.commit();
        Timing timing = new Timing(startNs, endNs, event.startInstant, event.endInstant,
                available(beforeCpu), available(afterCpu), cpuDelta(beforeCpu, afterCpu),
                available(beforeGc.count), available(afterGc.count), delta(beforeGc.count, afterGc.count),
                available(beforeGc.millis), available(afterGc.millis), delta(beforeGc.millis, afterGc.millis),
                Thread.currentThread().getId(), correlationId);
        if (failure instanceof Exception exception) throw exception;
        if (failure instanceof Error error) throw error;
        if (failure != null) throw new IllegalStateException(failure);
        String action = (String) invoke(generatedFormat, null, actions);
        Map<String, Object> snapshot = (Map<String, Object>) invoke(diagnostics, delegate);
        return new Decision(action, (endNs - startNs) / 1e6, snapshot, timing);
    }

    private long cpu() {
        return threads.isCurrentThreadCpuTimeSupported() && threads.isThreadCpuTimeEnabled()
                ? threads.getCurrentThreadCpuTime() : -1;
    }

    private Gc gc() {
        if (collectors.isEmpty()) return new Gc(-1, -1);
        long count = 0, millis = 0;
        for (var collector : collectors) {
            long c = collector.getCollectionCount(), ms = collector.getCollectionTime();
            count = count < 0 || c < 0 ? -1 : count + c;
            millis = millis < 0 || ms < 0 ? -1 : millis + ms;
        }
        return new Gc(count, millis);
    }

    private static Long available(long value) { return value < 0 ? null : value; }
    private static Long delta(long before, long after) { return before < 0 || after < before ? null : after - before; }
    private static Double cpuDelta(long before, long after) {
        Long delta = delta(before, after); return delta == null ? null : delta / 1e6;
    }

    private static Object member(Object receiver, String name) throws Exception {
        Field field = GeneratedPolicy.class.getDeclaredField(name); field.setAccessible(true); return field.get(receiver);
    }

    private static Object invoke(Method method, Object receiver, Object... arguments) throws Exception {
        try { return method.invoke(receiver, arguments); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception exception) throw exception;
            if (failure.getCause() instanceof Error error) throw error;
            throw failure;
        }
    }

    @Override public void close() throws IOException { closed = true; delegate.close(); }
}
