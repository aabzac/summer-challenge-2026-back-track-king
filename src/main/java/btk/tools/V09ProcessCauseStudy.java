package btk.tools;

import btk.bot.Bot;
import btk.bot.BotFactory;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Protocol;
import btk.referee.Referee;
import btk.runner.Maps;
import btk.runner.ProcessBot;
import btk.runner.TimingProfile;
import btk.submit.CGRunner;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Tools-only, one-game diagnostic trajectory. Its 5000ms transport grace and JFR/IO
 * instrumentation make it unsuitable for process-readiness or strength decisions.
 * It does not reconstruct the unsaved original strict-check turn33 state.
 */
public final class V09ProcessCauseStudy {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private static final Gson LINES = new GsonBuilder().serializeNulls().create();
    private static final Path ARTIFACT = Path.of("build/analysis/v09-deferred-forecast-reuse-v1/artifact-on");
    private static final Path MAP = Path.of("replays/cg/boss/904148181.json");
    private static final String SOURCE_SHA = "3bd070b3802744778473e93cebed8148ba07a7b243bd744c8ac486076d304a2c";
    private static final String CLASSES_SHA = "9f331092f279670c9d1e1d3ee2a9311c2e52a4f455c3735a65afbbc2421fb478";
    private static final String MAP_SHA = "e2e2bc079a260c78f59af17c28766876246350dc82dbf39197dc9e0e500263fc";
    private static final String RECORDING = "v09ProcessCause";
    private static final long SEED = 7000;
    private static final long DIAGNOSTIC_GRACE_MS = 5000;

    private V09ProcessCauseStudy() {}

    /** Output must be a new directory; the fixed artifact/map/seats/seed cannot be overridden. */
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("newOutputDirectory");
        Path out = Path.of(args[0]).toAbsolutePath().normalize();
        if (Files.exists(out)) throw new IllegalArgumentException("Refuse to overwrite diagnostic output: " + out);
        require(hash(ARTIFACT.resolve("Player.java")).equals(SOURCE_SHA), "Exact enabled source changed");
        require(classesHash(ARTIFACT.resolve("classes")).equals(CLASSES_SHA), "Exact enabled classes changed");
        require(hash(MAP).equals(MAP_SHA), "Fixed map changed");
        Path java = Path.of(System.getProperty("java.home"), "bin", "java.exe");
        Path jcmd = Path.of(System.getProperty("java.home"), "bin", "jcmd.exe");
        require(Files.isRegularFile(java) && Files.isRegularFile(jcmd), "Use the explicit Windows JDK with java/jcmd");
        Files.createDirectories(out.getParent());
        Files.createDirectory(out);
        Path recording = out.resolve("child.jfr");
        List<String> command = List.of(java.toString(),
                "-XX:FlightRecorderOptions=stackdepth=128",
                // JFR startup info normally uses stdout; keep the gameplay channel clean.
                "-Xlog:jfr*=warning:stderr",
                "-XX:StartFlightRecording=name=" + RECORDING + ",settings=profile,disk=true,dumponexit=true,filename="
                        + recording.toString().replace('\\', '/'),
                "-cp", ARTIFACT.resolve("classes").toAbsolutePath().toString(), "Player");
        ProcessBot process = new ProcessBot("v09-cause-jfr", command);
        process.graceMs = DIAGNOSTIC_GRACE_MS;
        process.enableIoDiagnostics();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("kind", "v09-runtime-cause-v1-process-diagnostic");
        report.put("startedAt", Instant.now().toString());
        report.put("diagnosticOnly", true);
        report.put("strictReadinessReevaluation", false);
        report.put("originalStrictReadiness", "FAIL_UNCHANGED");
        report.put("sameTrajectoryAsOriginalFailure", false);
        report.put("scope", "One new full local game; exact prior enabled artifact; instrumented child JFR and host IO counters; no timing retry or strategy change.");
        report.put("command", command);
        report.put("source", pin(ARTIFACT.resolve("Player.java")));
        report.put("classesSha256", CLASSES_SHA);
        report.put("map", pin(MAP));
        report.put("seed", SEED);
        report.put("candidateSeat", 0);
        report.put("opponent", "v09");
        report.put("opponentParams", BotFactory.configuration("v09"));
        report.put("graceMs", DIAGNOSTIC_GRACE_MS);
        report.put("nominalFirstResponseMs", CGRunner.FIRST_TURN_MS);
        report.put("nominalLaterResponseMs", CGRunner.TURN_MS);
        report.put("unchangedChildMarginMs", CGRunner.MARGIN_MS);
        report.put("ioDiagnostics", true);
        report.put("instrumentationCaveat", "JFR, optional host counters, input capture and trace writes add overhead. CPU/GC counters from ProcessBot.io describe the host, not child search. Actions are normalized parsed commands, not raw stdout text.");
        report.put("java", pin(java));
        report.put("jcmd", pin(jcmd));
        report.put("hostJavaVersion", System.getProperty("java.version"));
        writeNew(out.resolve("declaration.json"), JSON.toJson(report));
        Referee.Result result = null;
        Throwable failure = null;
        Map<String, Object> dump;
        TracedBot traced;
        try (BufferedWriter writer = Files.newBufferedWriter(out.resolve("turns.jsonl"), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW)) {
            traced = new TracedBot(process, out, writer);
            try {
                GameState initial = new Maps(MAP.toString()).get(0, SEED);
                Referee referee = new Referee();
                referee.firstTurnBudgetMs = CGRunner.FIRST_TURN_MS;
                referee.turnBudgetMs = CGRunner.TURN_MS;
                Bot opponent = TimingProfile.CG.cap(BotFactory.create("v09"));
                result = referee.play(initial, traced, opponent, SEED, true);
            } catch (Throwable problem) {
                failure = problem;
            } finally {
                // Capture the live child recording before close destroys the process.
                dump = dumpRecording(process, jcmd, recording, out);
                process.close();
            }
        }
        report.put("finishedAt", Instant.now().toString());
        boolean executionCompleted = failure == null && result != null;
        boolean gameCompletedWithoutErrors = executionCompleted && result.error[0] == null && result.error[1] == null;
        report.put("executionCompleted", executionCompleted);
        report.put("complete", gameCompletedWithoutErrors);
        report.put("gameCompletedWithoutErrors", gameCompletedWithoutErrors);
        report.put("failure", failure == null ? null : failure.toString());
        report.put("childPid", traced.childPid);
        report.put("init", traced.initTrace);
        report.put("turns", traced.turns);
        report.put("processTimings", process.timings);
        report.put("timeoutsIncludingGrace", process.timeouts);
        report.put("nominalOverruns", process.nominalOverruns);
        report.put("jfrDump", dump);
        report.put("jfrDumpSucceeded", "DUMPED".equals(dump.get("status")) && Files.isRegularFile(recording));
        report.put("jfr", Files.isRegularFile(recording) ? pin(recording) : null);
        if (result != null && result.replay != null) {
            writeNew(out.resolve("replay.json"), result.replay.toJson());
            report.put("replay", pin(out.resolve("replay.json")));
        }
        report.put("result", result);
        report.put("turnTrace", pin(out.resolve("turns.jsonl")));
        writeNew(out.resolve("report.json"), JSON.toJson(report));
        System.out.println("Diagnostic trajectory retained: " + out.resolve("report.json"));
        System.out.println("Diagnostic grace is not a strict-readiness pass. Original strict FAIL is unchanged.");
        if (failure != null) throw new IllegalStateException("Diagnostic execution failed; evidence retained", failure);
    }

    private static final class TracedBot implements Bot {
        final ProcessBot process;
        final Path out;
        final BufferedWriter writer;
        final List<Map<String, Object>> turns = new ArrayList<>();
        Map<String, Object> initTrace;
        Long childPid;
        TracedBot(ProcessBot process, Path out, BufferedWriter writer) {
            this.process = process; this.out = out; this.writer = writer;
        }
        @Override public String name() { return "v09-cause-jfr-diagnostic"; }
        @Override public void init(GameState state, int me) {
            try {
                writeNew(out.resolve("init.txt"), Protocol.initBlock(state, me));
                initTrace = new LinkedHashMap<>();
                initTrace.put("input", pin(out.resolve("init.txt")));
                initTrace.put("stateSha256", ReplyStudy.stateHash(state));
                initTrace.put("beforeInstant", Instant.now().toString());
                initTrace.put("beforeNs", System.nanoTime());
                process.init(state, me);
                initTrace.put("afterNs", System.nanoTime());
                initTrace.put("afterInstant", Instant.now().toString());
                Process child = child(process);
                childPid = child == null ? null : child.pid();
            } catch (Exception e) { throw new IllegalStateException("Process init capture failed", e); }
        }
        @Override public List<Action> play(GameState state, int me, long budgetMs) {
            Map<String, Object> trace = new LinkedHashMap<>();
            try {
                trace.put("turn", state.turn); trace.put("seat", me); trace.put("budgetMs", budgetMs);
                trace.put("stateBeforeSha256", ReplyStudy.stateHash(state));
                String input = Protocol.turnBlock(state, me);
                Path inputFile = out.resolve(String.format(java.util.Locale.ROOT, "input-%03d.txt", state.turn));
                writeNew(inputFile, input);
                trace.put("input", pin(inputFile)); trace.put("inputBlock", input);
            } catch (Exception e) { throw new IllegalStateException("Process input capture failed", e); }
            trace.put("beforeInstant", Instant.now().toString());
            long before = System.nanoTime();
            trace.put("beforeNs", before);
            List<Action> actions = null;
            try {
                actions = process.play(state, me, budgetMs);
                return actions;
            } catch (RuntimeException | Error e) {
                trace.put("failure", e.toString());
                throw e;
            } finally {
                long after = System.nanoTime();
                trace.put("afterNs", after); trace.put("afterInstant", Instant.now().toString());
                trace.put("wrapperPlayWallMs", (after - before) / 1e6);
                trace.put("normalizedParsedAction", actions == null ? null : Action.format(actions));
                ProcessBot.TurnTiming timing = process.timings.isEmpty() ? null : process.timings.get(process.timings.size() - 1);
                trace.put("processTiming", timing != null && timing.turn() == state.turn ? timing : null);
                turns.add(trace);
                try { writer.write(LINES.toJson(trace)); writer.newLine(); writer.flush(); }
                catch (IOException e) { throw new IllegalStateException("Process trace write failed", e); }
            }
        }
        @Override public void warmup(GameState state, int me, long ms) { process.warmup(state, me, ms); }
    }

    private static Process child(ProcessBot bot) throws ReflectiveOperationException {
        Field field = ProcessBot.class.getDeclaredField("proc");
        field.setAccessible(true);
        return (Process) field.get(bot);
    }
    private static Map<String, Object> dumpRecording(ProcessBot bot, Path jcmd, Path recording, Path out) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("attemptedAt", Instant.now().toString());
        try {
            Process child = child(bot);
            result.put("pid", child == null ? null : child.pid());
            result.put("childAliveBeforeClose", child != null && child.isAlive());
            if (child == null || !child.isAlive()) {
                result.put("status", "CHILD_NOT_ALIVE_FOR_DUMP");
                return result;
            }
            List<String> command = List.of(jcmd.toString(), Long.toString(child.pid()), "JFR.dump",
                    "name=" + RECORDING, "filename=" + recording);
            result.put("command", command);
            Process dump = new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(out.resolve("jcmd-dump.log").toFile()).start();
            boolean finished = dump.waitFor(30, TimeUnit.SECONDS);
            if (!finished) { dump.destroyForcibly(); result.put("status", "JCMD_TIMEOUT"); }
            else { result.put("exitCode", dump.exitValue()); result.put("status", dump.exitValue() == 0 ? "DUMPED" : "JCMD_FAILED"); }
            result.put("log", pin(out.resolve("jcmd-dump.log")));
        } catch (Exception e) { result.put("status", "DUMP_EXCEPTION"); result.put("failure", e.toString()); }
        return result;
    }
    private static Map<String, Object> pin(Path path) throws Exception {
        return Map.of("path", path.toString(), "sha256", hash(path), "bytes", Files.size(path));
    }
    private static String hash(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
    private static String classesHash(Path root) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".class")).sorted().toList()) {
                digest.update(root.relativize(file).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0); digest.update(Files.readAllBytes(file));
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    private static void writeNew(Path path, String text) throws IOException {
        Files.writeString(path, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
