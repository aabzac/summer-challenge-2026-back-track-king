package btk.tools;

import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingFile;

/** Offline JFR attribution only; does not execute a policy or infer historical causes. */
public final class V09JfrSummary {
    private static final String PLAY = "btk.GeneratedPolicyPlay";
    private static final int GLOBAL_STACK_LIMIT = 100, PLAY_STACK_LIMIT = 20;
    private static final long MILLION = 1_000_000L;

    private V09JfrSummary() {}

    record Interval(String type, long start, long end, Map<String, Object> detail) {}
    private record Sample(long time, String stack) {}
    private record Play(long start, long end, long threadId, Map<String, Object> fields) {}

    private static final class Totals {
        long count, positiveDurationCount, durationNs, maxNs;
        void add(long duration) {
            count++;
            if (duration > 0) positiveDurationCount++;
            durationNs += duration;
            maxNs = Math.max(maxNs, duration);
        }
        Map<String, Object> json() {
            return Map.of("count", count, "positiveDurationCount", positiveDurationCount,
                    "summedDurationMs", durationNs / 1e6, "maxDurationMs", maxNs / 1e6);
        }
    }

    /** Output must be new. A marker-free child recording still yields event timelines and samples. */
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("recording.jfr new-summary.json");
        Path input = Path.of(args[0]), output = Path.of(args[1]);
        if (Files.exists(output)) throw new IllegalArgumentException("Refuse to overwrite " + output);
        Map<String, Object> report = summarize(input);
        if (output.toAbsolutePath().getParent() != null) Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(report) + "\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    public static Map<String, Object> summarize(Path input) throws Exception {
        Map<String, Totals> totals = new TreeMap<>();
        List<Interval> pauses = new ArrayList<>(), safepoints = new ArrayList<>();
        List<Interval> operations = new ArrayList<>(), compilations = new ArrayList<>();
        List<Play> plays = new ArrayList<>();
        Map<Long, List<Sample>> samples = new HashMap<>();
        Map<String, String> stackStrings = new HashMap<>();
        Map<String, Long> stackCounts = new HashMap<>();
        Map<Long, Map<String, Object>> threads = new TreeMap<>();
        long first = Long.MAX_VALUE, last = Long.MIN_VALUE, sampleCount = 0, missingSampleThread = 0;
        try (RecordingFile recording = new RecordingFile(input)) {
            while (recording.hasMoreEvents()) {
                RecordedEvent event = recording.readEvent();
                String type = event.getEventType().getName();
                long start = nanos(event.getStartTime()), end = nanos(event.getEndTime());
                first = Math.min(first, start); last = Math.max(last, end);
                totals.computeIfAbsent(type, ignored -> new Totals()).add(end - start);
                if (type.equals(PLAY)) {
                    RecordedThread thread = event.getThread();
                    rememberThread(threads, thread);
                    plays.add(new Play(start, end, thread == null ? -1 : thread.getId(), playFields(event)));
                } else if (type.equals("jdk.ExecutionSample")) {
                    sampleCount++;
                    // eventThread may be the sampler: never use it instead of sampledThread.
                    RecordedThread thread = event.hasField("sampledThread") ? event.getThread("sampledThread") : null;
                    rememberThread(threads, thread);
                    if (thread == null) missingSampleThread++;
                    String stack = stack(event.getStackTrace());
                    String previous = stackStrings.putIfAbsent(stack, stack);
                    if (previous != null) stack = previous;
                    stackCounts.merge(stack, 1L, Long::sum);
                    samples.computeIfAbsent(thread == null ? -1 : thread.getId(), ignored -> new ArrayList<>())
                            .add(new Sample(start, stack));
                } else if (type.equals("jdk.GCPhasePause")) {
                    pauses.add(interval(event, start, end));
                } else if (type.startsWith("jdk.Safepoint")) {
                    safepoints.add(interval(event, start, end));
                } else if (type.equals("jdk.ExecuteVMOperation")) {
                    operations.add(interval(event, start, end));
                } else if (type.equals("jdk.Compilation")) {
                    compilations.add(interval(event, start, end));
                }
            }
        }
        Comparator<Interval> byStart = Comparator.comparingLong(Interval::start).thenComparingLong(Interval::end);
        for (List<Interval> events : List.of(pauses, safepoints, operations, compilations)) events.sort(byStart);
        for (List<Sample> values : samples.values()) values.sort(Comparator.comparingLong(Sample::time));
        plays.sort(Comparator.comparingLong(Play::start));
        List<Map<String, Object>> playRows = new ArrayList<>();
        int above36 = 0, above50 = 0, keyCount = 0;
        for (Play play : plays) {
            Map<String, Object> row = new LinkedHashMap<>(play.fields);
            row.put("jfrStart", instant(play.start)); row.put("jfrEnd", instant(play.end));
            row.put("jfrDurationMs", (play.end - play.start) / 1e6);
            row.put("eventThreadJfrId", play.threadId);
            row.put("eventThread", threads.get(play.threadId));
            Long startNs = number(play.fields.get("startNs")), endNs = number(play.fields.get("endNs"));
            Double wall = startNs != null && endNs != null && endNs >= startNs ? (endNs - startNs) / 1e6 : null;
            Double cpu = delta(play.fields, "threadCpuBeforeNs", "threadCpuAfterNs", MILLION);
            row.put("measuredInvokeWallMs", wall); row.put("threadCpuMs", cpu);
            row.put("wallMinusCoarseThreadCpuMs", wall == null || cpu == null ? null : wall - cpu);
            row.put("jvmWideGcCountDelta", delta(play.fields, "gcCountBefore", "gcCountAfter", 1));
            row.put("jvmWideGcTimeDeltaMs", delta(play.fields, "gcTimeBeforeMs", "gcTimeAfterMs", 1));
            String correlation = String.valueOf(play.fields.get("correlationId"));
            boolean key = correlation.startsWith("907134651/38/P1/") && Integer.valueOf(40).equals(play.fields.get("turn"));
            List<String> retention = new ArrayList<>();
            retention.add("all-markers-retained; controls join by correlationId");
            if (wall != null && wall > 36) { above36++; retention.add("invoke-above-36ms"); }
            if (wall != null && wall > 50) { above50++; retention.add("invoke-above-50ms"); }
            if (key) { keyCount++; retention.add("key-907134651-start38-P1-turn40"); }
            row.put("retentionReasons", retention);
            row.put("gcPhasePauseOverlap", overlap(pauses, play));
            row.put("safepointOverlapCorrelationOnly", overlap(safepoints, play));
            row.put("executeVmOperationOverlapCorrelationOnly", overlap(operations, play));
            row.put("overlappingCompilationCorrelationOnly", overlap(compilations, play));
            row.put("sameThreadExecutionSamples", sampleSummary(samples.get(play.threadId), play));
            playRows.add(row);
        }
        Map<String, Object> typeRows = new TreeMap<>();
        for (var entry : totals.entrySet()) typeRows.put(entry.getKey(), entry.getValue().json());
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("kind", "v09-jfr-attribution-summary-v1");
        report.put("recording", input.toAbsolutePath().normalize().toString());
        report.put("recordingSha256", V09ContinuationStudy.hash(input));
        report.put("eventTimeStart", first == Long.MAX_VALUE ? null : instant(first));
        report.put("eventTimeEnd", last == Long.MIN_VALUE ? null : instant(last));
        report.put("eventTypes", typeRows);
        report.put("observedEventTypesOnly", true);
        report.put("interpretation", List.of(
                "Diagnostic only: instrumentation overhead is present; this does not amend original timing failures or infer historical causes.",
                "Missing event types mean no recorded events, not necessarily zero activity; consult recording settings and event-availability.json.",
                "GC overlap is union length of top-level jdk.GCPhasePause events clipped to the native JFR play marker; nested pause-level events are not added.",
                "The JFR marker encloses the measured invocation slightly; measuredInvokeWallMs uses its startNs/endNs fields. CPU counters have platform-dependent resolution and wider snapshot bounds.",
                "Safepoint and ExecuteVMOperation durations are correlation only, not proof the play thread was stopped for their entire intervals; categories can overlap and must not be summed.",
                "Compilation overlap describes concurrent compiler activity only. It is not a pause duration or an attribution of the play's CPU time.",
                "Execution samples are filtered by sampledThread JFR identity matching eventThread, then by marker time. Sample counts are not elapsed-time measurements; absent samples prove no absence of work.",
                "Every play marker is retained, including controls and the key start38/P1 turn40; selection/control labels can be joined using correlationId."));
        report.put("threads", threads);
        report.put("executionSampleCount", sampleCount);
        report.put("executionSamplesWithoutThread", missingSampleThread);
        report.put("allThreadExecutionStacks", ranked(stackCounts, GLOBAL_STACK_LIMIT));
        report.put("allThreadExecutionFramesInclusive", ranked(frameCounts(stackCounts), GLOBAL_STACK_LIMIT));
        report.put("gcPhasePauses", intervalRows(pauses));
        report.put("safepoints", intervalRows(safepoints));
        report.put("executeVmOperations", intervalRows(operations));
        report.put("compilationsCorrelationOnly", intervalRows(compilations));
        report.put("playCount", plays.size()); report.put("invokeAbove36Ms", above36);
        report.put("invokeAbove50Ms", above50); report.put("keyTurn40Markers", keyCount);
        report.put("plays", playRows);
        return report;
    }

    private static Map<String, Object> playFields(RecordedEvent event) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String name : new String[]{"correlationId", "turn", "player", "budgetMs", "startNs", "endNs",
                "startInstant", "endInstant", "threadCpuBeforeNs", "threadCpuAfterNs", "gcCountBefore",
                "gcCountAfter", "gcTimeBeforeMs", "gcTimeAfterMs", "failure"}) {
            values.put(name, event.hasField(name) ? event.getValue(name) : null);
        }
        return values;
    }

    private static Interval interval(RecordedEvent event, long start, long end) {
        Map<String, Object> detail = new LinkedHashMap<>();
        for (String name : new String[]{"gcId", "name", "safepointId", "operation", "safepoint", "blocking",
                "compileId", "compiler", "compileLevel", "succeded", "succeeded", "isOsr"}) {
            if (event.hasField(name)) {
                Object value = event.getValue(name);
                if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean)
                    detail.put(name, value);
            }
        }
        if (event.hasField("method") && event.getValue("method") instanceof RecordedMethod method)
            detail.put("method", method(method));
        RecordedThread thread = event.getThread();
        if (thread != null) {
            detail.put("eventThreadJfrId", thread.getId()); detail.put("eventThreadJavaId", thread.getJavaThreadId());
            detail.put("eventThreadName", thread.getJavaName());
        }
        return new Interval(event.getEventType().getName(), start, end, detail);
    }

    private static Map<String, Object> overlap(List<Interval> events, Play play) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Interval event : events) {
            if (event.start >= play.end) break;
            if (event.end <= play.start) continue;
            long start = Math.max(event.start, play.start), end = Math.min(event.end, play.end);
            if (end <= start) continue;
            Map<String, Object> row = intervalRow(event);
            row.put("clippedOverlapMs", (end - start) / 1e6); rows.add(row);
        }
        return Map.of("eventCount", rows.size(), "unionOverlapMs", unionOverlapNanos(events, play.start, play.end) / 1e6,
                "events", rows);
    }

    /** Inputs are sorted once when the recording is loaded; half-open intervals avoid boundary double counting. */
    static long unionOverlapNanos(List<Interval> sorted, long windowStart, long windowEnd) {
        if (windowEnd < windowStart) throw new IllegalArgumentException("Negative window");
        long result = 0, coveredUntil = windowStart, previousStart = Long.MIN_VALUE;
        for (Interval event : sorted) {
            if (event.start < previousStart || event.end < event.start)
                throw new IllegalArgumentException("Expected sorted nonnegative intervals");
            previousStart = event.start;
            long start = Math.max(event.start, windowStart), end = Math.min(event.end, windowEnd);
            if (end <= start) continue;
            result += Math.max(0, end - Math.max(start, coveredUntil));
            coveredUntil = Math.max(coveredUntil, end);
        }
        return result;
    }

    private static Map<String, Object> sampleSummary(List<Sample> samples, Play play) {
        Map<String, Long> counts = new HashMap<>();
        long count = 0;
        if (samples != null && play.threadId >= 0) {
            int low = 0, high = samples.size();
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (samples.get(middle).time < play.start) low = middle + 1; else high = middle;
            }
            for (int i = low; i < samples.size() && samples.get(i).time < play.end; i++) {
                counts.merge(samples.get(i).stack, 1L, Long::sum); count++;
            }
        }
        return Map.of("count", count, "stacks", ranked(counts, PLAY_STACK_LIMIT),
                "framesInclusive", ranked(frameCounts(counts), PLAY_STACK_LIMIT));
    }

    private static Map<String, Long> frameCounts(Map<String, Long> stackCounts) {
        Map<String, Long> frames = new HashMap<>();
        for (var entry : stackCounts.entrySet()) {
            // A recursive frame counts once in an inclusive sample, not once per recursion level.
            for (String frame : new HashSet<>(List.of(entry.getKey().split("\n"))))
                frames.merge(frame, entry.getValue(), Long::sum);
        }
        return frames;
    }

    private static Map<String, Object> ranked(Map<String, Long> counts, int limit) {
        List<Map.Entry<String, Long>> entries = new ArrayList<>(counts.entrySet());
        entries.sort(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, entries.size()); i++)
            rows.add(Map.of("name", entries.get(i).getKey(), "samples", entries.get(i).getValue()));
        return Map.of("distinct", entries.size(), "limit", limit, "truncated", entries.size() > limit, "top", rows);
    }

    private static String stack(RecordedStackTrace stack) {
        if (stack == null || stack.getFrames().isEmpty()) return "<no recorded stack>";
        StringBuilder text = new StringBuilder();
        for (RecordedFrame frame : stack.getFrames()) {
            if (!text.isEmpty()) text.append('\n');
            text.append(method(frame.getMethod())).append(":").append(frame.getLineNumber())
                    .append(" [").append(frame.getType()).append(']');
        }
        if (stack.isTruncated()) text.append("\n<truncated>");
        return text.toString();
    }

    private static String method(RecordedMethod method) {
        return method.getType().getName() + "." + method.getName() + method.getDescriptor();
    }

    private static List<Map<String, Object>> intervalRows(List<Interval> events) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Interval event : events) rows.add(intervalRow(event));
        return rows;
    }

    private static Map<String, Object> intervalRow(Interval event) {
        Map<String, Object> row = new LinkedHashMap<>(event.detail);
        row.put("type", event.type); row.put("start", instant(event.start)); row.put("end", instant(event.end));
        row.put("durationMs", (event.end - event.start) / 1e6);
        return row;
    }

    private static void rememberThread(Map<Long, Map<String, Object>> threads, RecordedThread thread) {
        if (thread == null || threads.containsKey(thread.getId())) return;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("jfrId", thread.getId()); row.put("javaId", thread.getJavaThreadId()); row.put("osId", thread.getOSThreadId());
        row.put("javaName", thread.getJavaName()); row.put("osName", thread.getOSName()); threads.put(thread.getId(), row);
    }

    private static Long number(Object value) { return value instanceof Number n ? n.longValue() : null; }
    private static Double delta(Map<String, Object> fields, String before, String after, long divisor) {
        Long a = number(fields.get(before)), b = number(fields.get(after));
        return a == null || b == null || a < 0 || b < a ? null : (b - a) / (double) divisor;
    }
    private static long nanos(Instant instant) {
        return Math.addExact(Math.multiplyExact(instant.getEpochSecond(), 1_000_000_000L), instant.getNano());
    }
    private static String instant(long nanos) {
        return Instant.ofEpochSecond(Math.floorDiv(nanos, 1_000_000_000L), Math.floorMod(nanos, 1_000_000_000L)).toString();
    }
}
