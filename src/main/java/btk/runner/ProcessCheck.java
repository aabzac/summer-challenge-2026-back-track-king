package btk.runner;

import btk.bot.Bot;
import btk.bot.BotFactory;
import btk.core.GameState;
import btk.referee.Referee;
import btk.submit.FileMerger;
import btk.submit.CGRunner;
import com.google.gson.GsonBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Pre-submit check: merges Player.java (if needed), then plays it as an external process against
 * an in-process bot on real CG maps when available. Fails loudly on timeouts, invalid commands,
 * or process errors. Connection-model warnings are printed on stderr for inspection.
 *
 * <p>Args: opponentSpec games [map file or pool] [graceMs=0] [report path] [ioDiagnostics=false] [submission directory]
 */
public final class ProcessCheck {
    record GameCheck(int game, int player, String map, long seed, Referee.Result result,
                     int timeouts, int nominalOverruns, List<ProcessBot.TurnTiming> timings) {}

    public static void main(String[] args) throws Exception {
        String opp = args.length > 0 && !args[0].isEmpty() ? args[0] : "v00";
        int games = args.length > 1 && !args[1].isEmpty() ? Integer.parseInt(args[1]) : 3;
        long graceMs = args.length > 3 && !args[3].isBlank() ? Long.parseLong(args[3]) : 0;
        if (games <= 0 || graceMs < 0) throw new IllegalArgumentException("Positive games and nonnegative grace required");
        Path out = Path.of(args.length > 4 && !args[4].isBlank() ? args[4] : "build/analysis/process-check.json");
        boolean diagnostics = args.length > 5 && parseDiagnostics(args[5]);
        if (diagnostics && Files.exists(out)) throw new IllegalArgumentException("Choose a new diagnostic report: " + out);
        String configuredOutput = args.length > 6 ? args[6] : "";
        Path submission = submissionDirectory(configuredOutput);
        Path classes = submission.resolve("classes/Player.class");
        if (!configuredOutput.isBlank()) requireExplicitArtifact(submission);
        else if (!Files.exists(classes)) FileMerger.main(new String[0]);
        Path source = submission.resolve("Player.java");
        String sourceHash = Files.exists(source) ? HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source))) : "missing";
        // Hash actual executable files as well: source alone does not prove classes are fresh.
        String classesHash = classesHash(classes.getParent());
        Maps maps = new Maps(args.length > 2 && !args[2].isBlank() ? args[2]
                : Files.isDirectory(Path.of("replays/cg")) ? "replays/cg" : "");
        int wins = 0, problems = 0;
        List<GameCheck> checks = new ArrayList<>();
        System.out.printf("Process deadlines: first %d ms, later %d ms, explicit grace %d ms (%s)%n",
                CGRunner.FIRST_TURN_MS, CGRunner.TURN_MS, graceMs, graceMs == 0 ? "strict local check" : "diagnostic only");
        if (diagnostics) System.out.println("IO diagnostics enabled: host wall/CPU/GC counters add overhead; no child-search attribution.");
        for (int g = 0; g < games; g++) {
            long seed = 7000 + g;
            GameState initial = maps.get(g, seed);
            ProcessBot pb = ProcessBot.javaPlayer(classes.getParent().toString());
            if (diagnostics) pb.enableIoDiagnostics();
            pb.graceMs = graceMs;
            Bot other = TimingProfile.CG.cap(BotFactory.create(opp));
            boolean asP0 = g % 2 == 0;
            Referee ref = new Referee();
            // Process IO deadlines must not be reduced to the in-process search budget.
            ref.firstTurnBudgetMs = CGRunner.FIRST_TURN_MS;
            ref.turnBudgetMs = CGRunner.TURN_MS;
            Referee.Result r;
            try {
                r = ref.play(initial, asP0 ? pb : other, asP0 ? other : pb, seed, false);
            } finally {
                pb.close();
            }
            int me = asP0 ? 0 : 1;
            if (r.winner == me) wins++;
            boolean bad = pb.timeouts > 0 || r.error[0] != null || r.error[1] != null;
            if (bad) problems++;
            checks.add(new GameCheck(g, me, maps.name(g, seed), seed, r, pb.timeouts,
                    pb.nominalOverruns, List.copyOf(pb.timings)));
            System.out.printf("game %d as P%d: %s | %s, timeouts %d, nominal overruns %d%s%n", g, me,
                    r.summary(), timingSummary(pb.timings), pb.timeouts, pb.nominalOverruns, bad ? "  <-- PROBLEM" : "");
            if (diagnostics) {
                double prepare = 0, write = 0, flush = 0;
                for (var timing : pb.timings) if (timing.turn() > 0 && timing.io() != null) {
                    prepare = Math.max(prepare, timing.io().prepareMs());
                    write = Math.max(write, timing.io().writeMs());
                    flush = Math.max(flush, timing.io().flushMs());
                }
                System.out.printf("  later input phase maxima: prepare %.3f ms / write %.3f ms / flush %.3f ms%n", prepare, write, flush);
            }
        }
        Files.createDirectories(out.toAbsolutePath().getParent());
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schemaVersion", 1); report.put("sourceSha256", sourceHash); report.put("classesSha256", classesHash);
        report.put("submissionDirectory", submission.toAbsolutePath().normalize().toString());
        report.put("javaVersion", System.getProperty("java.version")); report.put("opponent", opp);
        report.put("opponentParams", BotFactory.configuration(opp)); report.put("graceMs", graceMs);
        report.put("strict", graceMs == 0); report.put("problems", problems); report.put("games", checks);
        report.put("ioDiagnostics", diagnostics);
        Files.writeString(out, new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(report), StandardCharsets.UTF_8);
        System.out.println("Per-turn timing report: " + out);
        System.out.printf("Player.java: %d/%d wins vs %s, %d problem games%n", wins, games, opp, problems);
        if (problems > 0) System.exit(1);
    }

    static Path submissionDirectory(String configuredOutput) {
        return Path.of(configuredOutput == null || configuredOutput.isBlank() ? "build/submit" : configuredOutput).normalize();
    }

    /** An explicitly requested artifact must already exist; never silently compile another policy. */
    static void requireExplicitArtifact(Path directory) {
        if (!Files.isDirectory(directory)) throw new IllegalArgumentException("submitOut must be an existing submission directory: " + directory);
        if (!Files.isRegularFile(directory.resolve("Player.java")))
            throw new IllegalArgumentException("Missing requested Player.java: " + directory);
        if (!Files.isRegularFile(directory.resolve("classes/Player.class")))
            throw new IllegalArgumentException("Missing requested compiled Player.class; run submit with the same submitOut: " + directory);
    }

    static boolean parseDiagnostics(String value) {
        return switch (value) {
            case "true", "1" -> true;
            case "false", "0", "" -> false;
            default -> throw new IllegalArgumentException("processDiagnostics must be true/false or 1/0");
        };
    }

    /** Never present consumer timeout/wakeup time as an observed child response. */
    static String timingSummary(List<ProcessBot.TurnTiming> timings) {
        ProcessBot.TurnTiming first = null;
        Double later = null;
        for (var t : timings) {
            if (t.turn() == 0) first = t;
            else if (t.responseMs() != null) later = later == null ? t.responseMs() : Math.max(later, t.responseMs());
        }
        return "first response " + millis(first == null ? null : first.responseMs())
                + ", first send " + millis(first == null ? null : first.sendMs())
                + ", first consumer " + millis(first == null ? null : first.consumerMs())
                + ", max later response " + millis(later);
    }

    private static String millis(Double value) {
        return value == null ? "none" : String.format(java.util.Locale.ROOT, "%.3f ms", value);
    }

    private static String classesHash(Path root) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".class")).sorted().toList()) {
                digest.update(root.relativize(file).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(Files.readAllBytes(file));
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
