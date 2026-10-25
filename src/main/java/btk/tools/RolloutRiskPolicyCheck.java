package btk.tools;

import btk.bot.Bot;
import btk.bot.BotFactory;
import btk.bot.v10.RolloutBotV10;
import btk.bot.v09.RolloutBotV09;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Actual policy coverage on the existing development states, without strength/timing claims. */
public final class RolloutRiskPolicyCheck {
    private static final String CONTROL = "v09?NOISE=0", CANDIDATE = "v10?" + BotFactory.V10_FLAGS_OFF + ",USE_ROLLOUT_ALL_CELL_RISK=1,NOISE=0"; // prune-v1: the risk variant lives in frozen v10 (v09 configuration)
    record Play(String spec, long budgetMs, double wallMs, String actions, String unorderedAction, int queries, int comparisons,
                boolean ordinarySearchComplete) {}

    private static Play play(ReplyStudy.Position position, String spec, long budget) {
        GameState input = position.state().copy(), saved = input.copy(); int me = position.me();
        Bot bot = BotFactory.create(spec); bot.init(input.copy(), me);
        long start = System.nanoTime(); List<Action> actions = bot.play(input, me, budget);
        double elapsed = (System.nanoTime() - start) / 1e6;
        ReplyProbe.sameState(saved, input); ReplyProbe.sameState(saved, position.state());
        var check = new Rules.TurnResult(); Rules.expand(input, me, actions, new Pathing(input.size), check.moves[me], check);
        if (check.moves[me].error != null || !check.moves[me].skipped.isEmpty()) throw new IllegalStateException("Illegal " + spec + " output");
        String[] commands = Action.format(actions).split(";"); Arrays.sort(commands);
        String unordered = String.join(";", commands);
        if (bot instanceof RolloutBotV10 b) return new Play(spec, budget, elapsed, Action.format(actions), unordered, b.routeQueries,
                b.completedComparisons, b.ordinarySearchComplete);
        if (bot instanceof RolloutBotV09 b) return new Play(spec, budget, elapsed, Action.format(actions), unordered, b.routeQueries,
                b.completedComparisons, b.ordinarySearchComplete);
        throw new IllegalStateException("Unexpected policy implementation: " + bot.getClass());
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("captureDir reportPath");
        Path capture = Path.of(args[0]), out = Path.of(args[1]);
        if (Files.exists(out)) throw new IllegalArgumentException("Choose a new report path");
        var all = ReplyStudy.loadPositions(capture); List<ReplyStudy.Position> eligible = new ArrayList<>();
        for (var position : all) for (int p = 0; p < position.state().pairCount; p++)
            if (RolloutRiskStudy.eligiblePair(position.state(), position.me(), p)) { eligible.add(position); break; }
        List<Map<String, Object>> samples = new ArrayList<>(); int differs = 0, nominalDiffers = 0;
        for (int i = 0; i < Math.min(64, eligible.size()); i++) {
            var position = eligible.get(i); Play control, candidate;
            if ((i & 1) == 0) { control = play(position, CONTROL, 60_000); candidate = play(position, CANDIDATE, 60_000); }
            else { candidate = play(position, CANDIDATE, 60_000); control = play(position, CONTROL, 60_000); }
            if (!control.ordinarySearchComplete() || !candidate.ordinarySearchComplete()) throw new IllegalStateException("Ample search incomplete");
            Play nominal = play(position, CANDIDATE, 36); // fresh policy state; JVM has already seen both ample calls
            boolean changed = !control.unorderedAction().equals(candidate.unorderedAction()),
                    timedChanged = !candidate.unorderedAction().equals(nominal.unorderedAction());
            if (changed) differs++; if (timedChanged) nominalDiffers++;
            Map<String, Object> row = new LinkedHashMap<>(); row.put("game", position.game()); row.put("seed", position.seed());
            row.put("me", position.me()); row.put("sourcePolicy", position.policy()); row.put("turn", position.state().turn);
            row.put("stateSha256", ReplyStudy.stateHash(position.state())); row.put("controlFirst", (i & 1) == 0);
            row.put("control", control); row.put("candidate", candidate); row.put("candidateNominal", nominal);
            row.put("actionsDiffer", changed); row.put("nominalDiffersFromAmple", timedChanged); samples.add(row);
            System.out.printf("Risk policy coverage %d/%d%n", i + 1, Math.min(64, eligible.size()));
        }
        Map<String, String> hashes = new LinkedHashMap<>();
        for (Path directory : List.of(capture, Path.of("src/main/java/btk/core"))) try (var files = Files.list(directory)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) hashes.put(file.toString(), ReplyStudy.hash(Files.readAllBytes(file)));
        }
        for (String relative : List.of("tools/RolloutRiskPolicyCheck.java", "tools/RolloutRiskStudy.java", "tools/ReplyStudy.java", "tools/ReplyProbe.java",
                "bot/BotFactory.java", "bot/MainBot.java", "bot/Params.java", "bot/FrozenParams.java", "bot/Timer.java",
                "bot/v10/RolloutBotV10.java", "bot/v10/ProtectedOpeningV10.java", "bot/v09/RolloutBotV09.java", "bot/v09/ProtectedOpeningV09.java")) {
            Path file = Path.of("src/main/java/btk").resolve(relative); hashes.put(file.toString(), ReplyStudy.hash(Files.readAllBytes(file)));
        }
        for (String name : List.of("src/main/resources/btk/bot/versions/v09.properties", "docs/rollout-risk-v1.md"))
            hashes.put(name, ReplyStudy.hash(Files.readAllBytes(Path.of(name))));
        Map<String, Object> report = new LinkedHashMap<>(); report.put("schema", "rollout-risk-policy-check-v1"); report.put("complete", true);
        report.put("javaVersion", System.getProperty("java.version")); report.put("hashes", hashes);
        report.put("configurations", Map.of(CONTROL, BotFactory.configuration(CONTROL), CANDIDATE, BotFactory.configuration(CANDIDATE)));
        report.put("availablePositions", all.size()); report.put("eligiblePositions", eligible.size()); report.put("selectedPositions", samples.size());
        report.put("actionsDiffer", differs); report.put("nominalDiffersFromAmple", nominalDiffers);
        report.put("inputsUnchangedAndOutputsLegal", true); report.put("samples", samples);
        report.put("scope", "First64 eligible existing development states, no future-action selection. Unordered comparison of legal PLACE/DISRUPT/WAIT commands; original formatted outputs retained. Fresh bots, alternating ample order; nominal36ms follows both ample calls. No games, process check, strength or performance claim.");
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, ReplyStudy.JSON.toJson(report), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }
}
