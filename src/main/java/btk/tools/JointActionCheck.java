package btk.tools;

import btk.bot.Bot;
import btk.bot.BotCatalog;
import btk.bot.BotFactory;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Serial nominal-budget cost check on fixed saved states; not a strength or process test. */
public final class JointActionCheck {
    public static final String SPEC = "current?USE_ROLLOUT_POLICY=0,USE_REPLY_RERANK=1,BUILD_BEAM_WIDTH=2,OPP_REPLY_WIDTH=1,USE_JOINT_ACTION=1";
    record Play(String actions, double elapsedMs, int pairs, boolean complete, int committedTarget) {}
    record Sample(String game, int turn, int me, String stateSha256, boolean candidateFirst, Play control, Play candidate) {}

    static Play play(GameState state, int me, boolean candidate) throws Exception {
        Bot bot = candidate ? BotFactory.create(SPEC + ",NOISE=0")
                : BotCatalog.createExperiment(ReplyProbe.SPEC + ",NOISE=0");
        bot.init(state.copy(), me); GameState input = state.copy();
        long start = System.nanoTime(); var actions = bot.play(input, me, 36);
        double ms = (System.nanoTime() - start) / 1e6;
        var moves = new Rules.PlayerMoves();
        Rules.expand(state, me, actions, new Pathing(state.size), moves, new Rules.TurnResult());
        if (moves.error != null || !moves.skipped.isEmpty()) throw new IllegalStateException("Illegal diagnostic output");
        return new Play(Action.format(actions), ms,
                candidate ? (int) CgDisruptStudy.field(bot, "jointPairsEvaluated") : 0,
                candidate && (boolean) CgDisruptStudy.field(bot, "jointSearchComplete"),
                candidate ? (int) CgDisruptStudy.field(bot, "jointSelectedTarget") : -2);
    }

    public static void main(String[] args) throws Exception {
        Path capture = Path.of(args[0]), out = Path.of(args[1]);
        if (Files.exists(out)) throw new IllegalArgumentException("Choose a new report path");
        List<ReplyStudy.Position> positions = ReplyStudy.loadPositions(capture);
        List<Sample> samples = new ArrayList<>();
        int complete = 0, committed = 0, changed = 0, overControl = 0, overCandidate = 0;
        double sumControl = 0, sumCandidate = 0, maxControl = 0, maxCandidate = 0;
        for (int i = 0; i < positions.size(); i++) {
            var p = positions.get(i); boolean candidateFirst = i % 2 == 0;
            Play a, b;
            if (candidateFirst) { b = play(p.state(), p.me(), true); a = play(p.state(), p.me(), false); }
            else { a = play(p.state(), p.me(), false); b = play(p.state(), p.me(), true); }
            if (b.complete) complete++;
            if (b.committedTarget >= -1) committed++;
            if (!a.actions.equals(b.actions)) changed++;
            if (a.elapsedMs > 36) overControl++;
            if (b.elapsedMs > 36) overCandidate++;
            sumControl += a.elapsedMs; sumCandidate += b.elapsedMs;
            maxControl = Math.max(maxControl, a.elapsedMs); maxCandidate = Math.max(maxCandidate, b.elapsedMs);
            samples.add(new Sample(p.game(), p.state().turn, p.me(), ReplyStudy.stateHash(p.state()), candidateFirst, a, b));
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("positions", positions.size()); summary.put("completeJointScans", complete);
        summary.put("committedJointPairs", committed); summary.put("differentFullActions", changed);
        summary.put("controlMeanMs", sumControl / positions.size()); summary.put("candidateMeanMs", sumCandidate / positions.size());
        summary.put("controlMaxMs", maxControl); summary.put("candidateMaxMs", maxCandidate);
        summary.put("controlOver36Ms", overControl); summary.put("candidateOver36Ms", overCandidate);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("javaVersion", System.getProperty("java.version")); report.put("candidate", SPEC);
        report.put("candidateParams", BotFactory.configuration(SPEC + ",NOISE=0"));
        report.put("control", BotCatalog.describe(ReplyProbe.SPEC + ",NOISE=0"));
        report.put("controlParams", BotFactory.configuration(ReplyProbe.SPEC + ",NOISE=0"));
        report.put("engineClassSha256", ReplyStudy.hash(Files.readAllBytes(Path.of("build/classes/java/main/btk/bot/current/EvalBot.class"))));
        report.put("paramsClassSha256", ReplyStudy.hash(Files.readAllBytes(Path.of("build/classes/java/main/btk/bot/Params.class"))));
        report.put("captureManifestSha256", ReplyStudy.hash(Files.readAllBytes(capture.resolve("manifest.json"))));
        report.put("method", "Fresh NOISE=0 instances at 36ms, init outside clock, alternating order, all saved pre-turn states. "
                + "No historical execution reconstruction, oracle decision input, population strength or process readiness claim.");
        report.put("summary", summary); report.put("samples", samples);
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, ReplyStudy.JSON.toJson(report), StandardOpenOption.CREATE_NEW);
        System.out.println(ReplyStudy.JSON.toJson(summary));
    }
}
