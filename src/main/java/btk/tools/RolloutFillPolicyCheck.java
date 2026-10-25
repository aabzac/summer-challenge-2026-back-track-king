package btk.tools;

import btk.bot.BotFactory;
import btk.bot.v10.RolloutBotV10;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Production-policy parity against the immutable saved-state mechanism report. No games. */
public final class RolloutFillPolicyCheck {
    // prune-v1 (2026-10-25): the root-fill mechanism lives in frozen v10 only; both arms use the v09 configuration on it.
    private static final String OFF = "v10?" + BotFactory.V10_FLAGS_OFF + ",USE_ROLLOUT_ROOT_FILL=0,NOISE=0";
    private static final String ON = "v10?" + BotFactory.V10_FLAGS_OFF + ",USE_ROLLOUT_ROOT_FILL=1,NOISE=0";
    record Play(String spec, long budgetMs, double wallMs, String actions, String unorderedAction, Double selectedValue,
                int selectedRoute, int selectedTarget, int queries, int comparisons,
                boolean ordinarySearchComplete, boolean networkPhaseComplete) {}

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    private static String actionKey(String action) { String[] parts = action.split(";"); Arrays.sort(parts); return String.join(";", parts); }
    private static Play play(ReplyStudy.Position position, String spec, long budget) {
        GameState input = position.state().copy(), saved = input.copy(); int me = position.me();
        RolloutBotV10 bot = (RolloutBotV10) BotFactory.create(spec); bot.init(input.copy(), me);
        long start = System.nanoTime(); List<Action> actions = bot.play(input, me, budget);
        double wall = (System.nanoTime() - start) / 1e6;
        ReplyProbe.sameState(saved, input); ReplyProbe.sameState(saved, position.state());
        var check = new Rules.TurnResult(); Rules.expand(input, me, actions, new Pathing(input.size), check.moves[me], check);
        require(check.moves[me].error == null && check.moves[me].skipped.isEmpty(), "Illegal " + spec + " output");
        String formatted = Action.format(actions);
        return new Play(spec, budget, wall, formatted, actionKey(formatted),
                Double.isFinite(bot.selectedValue) ? bot.selectedValue : null, bot.selectedRoute, bot.selectedTarget,
                bot.routeQueries, bot.completedComparisons, bot.ordinarySearchComplete, bot.networkPhaseComplete);
    }
    private static Map<String, String> hashes(Path capture) throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        for (Path directory : List.of(capture, Path.of("src/main/java/btk/core"))) try (var files = Files.list(directory)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) result.put(file.toString(), ReplyStudy.hash(Files.readAllBytes(file)));
        }
        for (String name : List.of("tools/RolloutFillPolicyCheck.java", "tools/RolloutFillStudy.java", "tools/ReplyStudy.java", "tools/ReplyProbe.java",
                "bot/v10/RolloutBotV10.java", "bot/v10/ProtectedOpeningV10.java", "bot/v09/RolloutBotV09.java", "bot/v09/ProtectedOpeningV09.java",
                "bot/Params.java", "bot/BotFactory.java", "bot/FrozenParams.java", "bot/Timer.java")) {
            Path file = Path.of("src/main/java/btk").resolve(name); result.put(file.toString(), ReplyStudy.hash(Files.readAllBytes(file)));
        }
        Path version = Path.of("src/main/resources/btk/bot/versions/v09.properties");
        result.put(version.toString(), ReplyStudy.hash(Files.readAllBytes(version))); return result;
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("captureDir immutableMechanismJson newPolicyReport");
        Path capture = Path.of(args[0]), mechanismPath = Path.of(args[1]), out = Path.of(args[2]);
        require(!Files.exists(out), "Choose a new policy report path");
        byte[] mechanismBytes = Files.readAllBytes(mechanismPath);
        String mechanismHash = ReplyStudy.hash(mechanismBytes);
        JsonObject mechanism = ReplyStudy.JSON.fromJson(new String(mechanismBytes, StandardCharsets.UTF_8), JsonObject.class);
        require(mechanism.get("schema").getAsString().equals("rollout-fill-root-study-v1") && mechanism.get("complete").getAsBoolean(),
                "Expected a completed mechanism report");
        var positions = ReplyStudy.loadPositions(capture); var expected = mechanism.getAsJsonArray("samples");
        require(positions.size() == 110 && expected.size() == positions.size(), "Expected all 110 saved states");
        require(!BotFactory.parameters(ON).USE_ROLLOUT_ALL_CELL_RISK && !BotFactory.parameters(OFF).USE_ROLLOUT_ALL_CELL_RISK,
                "Risk experiment must remain OFF");
        Map<String, String> sourceHashes = hashes(capture); List<Map<String, Object>> samples = new ArrayList<>();
        int ampleChanged = 0, nominalChanged = 0, nominalOnVsAmple = 0, nominalOffVsAmple = 0, onComplete = 0, offComplete = 0;
        double onMs = 0, offMs = 0, onMaxMs = 0, offMaxMs = 0;
        for (int i = 0; i < positions.size(); i++) {
            var position = positions.get(i); JsonObject old = expected.get(i).getAsJsonObject();
            String stateHash = ReplyStudy.stateHash(position.state());
            require(old.get("game").getAsString().equals(position.game()) && old.get("seed").getAsLong() == position.seed()
                    && old.get("me").getAsInt() == position.me() && old.get("turn").getAsInt() == position.state().turn
                    && old.get("stateSha256").getAsString().equals(stateHash), "Saved input/order mismatch at " + i);
            JsonObject selected = old.getAsJsonArray("tuples").get(old.get("filledSelectedTuple").getAsInt()).getAsJsonObject();
            String expectedAction = selected.get("filledAction").getAsString(), baselineAction = old.get("baselineAction").getAsString();
            Play ample = play(position, ON, 60_000);
            require(ample.ordinarySearchComplete && ample.networkPhaseComplete, "Ample production policy incomplete at " + i);
            require(ample.unorderedAction.equals(actionKey(expectedAction)), "Filled action differs from mechanism winner at " + i);
            require(ample.selectedValue != null && Math.abs(ample.selectedValue - selected.get("filledValue").getAsDouble()) < 1e-9,
                    "Filled value differs from mechanism winner at " + i);
            Play on, off;
            if ((i & 1) == 0) { on = play(position, ON, 36); off = play(position, OFF, 36); }
            else { off = play(position, OFF, 36); on = play(position, ON, 36); }
            boolean changed = !ample.unorderedAction.equals(actionKey(baselineAction));
            boolean nominalDiff = !on.unorderedAction.equals(off.unorderedAction);
            boolean onDiff = !on.unorderedAction.equals(ample.unorderedAction), offDiff = !off.unorderedAction.equals(actionKey(baselineAction));
            if (changed) ampleChanged++; if (nominalDiff) nominalChanged++; if (onDiff) nominalOnVsAmple++; if (offDiff) nominalOffVsAmple++;
            if (on.ordinarySearchComplete) onComplete++; if (off.ordinarySearchComplete) offComplete++;
            onMs += on.wallMs; offMs += off.wallMs; onMaxMs = Math.max(onMaxMs, on.wallMs); offMaxMs = Math.max(offMaxMs, off.wallMs);
            Map<String, Object> row = new LinkedHashMap<>(); row.put("game", position.game()); row.put("seed", position.seed());
            row.put("me", position.me()); row.put("turn", position.state().turn); row.put("stateSha256", stateHash);
            row.put("expectedFilledAction", expectedAction); row.put("savedBaselineAction", baselineAction); row.put("nominalOnFirst", (i & 1) == 0);
            row.put("ampleOn", ample); row.put("nominalOn", on); row.put("nominalOff", off); row.put("ampleActionChanged", changed);
            row.put("nominalActionsDiffer", nominalDiff); row.put("nominalOnDiffersFromAmple", onDiff); row.put("nominalOffDiffersFromAmple", offDiff);
            samples.add(row); System.out.printf("Fill policy check %d/%d%n", samples.size(), positions.size());
        }
        require(sourceHashes.equals(hashes(capture)) && mechanismHash.equals(ReplyStudy.hash(Files.readAllBytes(mechanismPath))),
                "Source, capture or mechanism report changed during diagnosis");
        Map<String, Object> summary = new LinkedHashMap<>(); summary.put("ampleActionChanged", ampleChanged);
        summary.put("nominalActionsDiffer", nominalChanged); summary.put("nominalOnDiffersFromAmple", nominalOnVsAmple);
        summary.put("nominalOffDiffersFromAmple", nominalOffVsAmple); summary.put("nominalOnOrdinaryComplete", onComplete);
        summary.put("nominalOffOrdinaryComplete", offComplete); summary.put("nominalOnMeanMs", onMs / positions.size());
        summary.put("nominalOffMeanMs", offMs / positions.size()); summary.put("nominalOnMaxMs", onMaxMs); summary.put("nominalOffMaxMs", offMaxMs);
        Map<String, Object> report = new LinkedHashMap<>(); report.put("schema", "rollout-fill-policy-check-v1"); report.put("complete", true);
        report.put("createdAt", Instant.now().toString()); report.put("javaVersion", System.getProperty("java.version")); report.put("hashes", sourceHashes);
        report.put("mechanismPath", mechanismPath.toString()); report.put("mechanismSha256", mechanismHash);
        report.put("configurations", Map.of(OFF, BotFactory.configuration(OFF), ON, BotFactory.configuration(ON)));
        report.put("positions", positions.size()); report.put("allAmpleActionsAndValuesMatchMechanism", true);
        report.put("inputsUnchangedAndOutputsLegal", true); report.put("summary", summary); report.put("samples", samples);
        report.put("scope", "All 110 saved development positions, identical order/input hashes to immutable mechanism report; not a current-arena sample. Fresh ample ON matches precomputed filled winner action/value. Fresh nominal36ms ON/OFF alternate order within each state after ample warmup; timing/completion/action counts describe this run only. No nominal parity, process-budget compliance, statistical performance or strength claim. No live games or outcome selection.");
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, ReplyStudy.JSON.toJson(report), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        System.out.println(ReplyStudy.JSON.toJson(summary));
    }
}
