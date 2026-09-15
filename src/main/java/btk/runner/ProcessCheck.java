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

/**
 * Pre-submit check: merges Player.java (if needed), then plays it as an external process against
 * an in-process bot on real CG maps when available. Fails loudly on timeouts, invalid commands,
 * or process errors. Connection-model warnings are printed on stderr for inspection.
 *
 * <p>Args: opponentSpec games [map file or pool] [graceMs=0] [report path]
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
        Path classes = Path.of("build/submit/classes/Player.class");
        if (!Files.exists(classes)) FileMerger.main(new String[0]);
        Path source = Path.of("build/submit/Player.java");
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
        for (int g = 0; g < games; g++) {
            long seed = 7000 + g;
            GameState initial = maps.get(g, seed);
            ProcessBot pb = ProcessBot.javaPlayer("build/submit/classes");
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
            System.out.printf("game %d as P%d: %s | reader first %d ms, max later %d ms, timeouts %d, nominal overruns %d%s%n", g, me,
                    r.summary(), pb.firstTurnMs, pb.maxResponseMs, pb.timeouts, pb.nominalOverruns, bad ? "  <-- PROBLEM" : "");
        }
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(Map.of(
                "schemaVersion", 1, "sourceSha256", sourceHash, "classesSha256", classesHash,
                "javaVersion", System.getProperty("java.version"), "opponent", opp,
                "opponentParams", BotFactory.configuration(opp), "graceMs", graceMs,
                "strict", graceMs == 0, "problems", problems, "games", checks)), StandardCharsets.UTF_8);
        System.out.println("Per-turn timing report: " + out);
        System.out.printf("Player.java: %d/%d wins vs %s, %d problem games%n", wins, games, opp, problems);
        if (problems > 0) System.exit(1);
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
