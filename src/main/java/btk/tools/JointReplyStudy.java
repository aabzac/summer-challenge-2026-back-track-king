package btk.tools;

import btk.bot.BotCatalog;
import btk.bot.Timer;
import btk.bot.current.EvalBot;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tools-only joint-action audit of the existing captured sample. No gameplay or timing reruns. */
public final class JointReplyStudy {
    record Prior(int schemaVersion, String corpusManifestSha256, BotCatalog.Identity candidate,
                 JsonObject probeParams, List<ReplyStudy.Sample> samples) {}
    record Effect(int prefixCellsInked, boolean routeInked, boolean terminal, int scoreLead) {}
    record Scenario(ReplyProbe.Value[] none, ReplyProbe.Value[] fixed, ReplyProbe.Value[] adapted,
                    int noneSelected, int fixedSelected, int adaptedSelected, Effect[] adaptedEffects) {}
    record Joint(int preBuildTarget, int[] prefixTargets, int priorSelected,
                 int priorFullPlayTarget, Map<String, Scenario> scenarios) {}
    record Sample(String game, long seed, int me, String policy, int turn, String stateSha256, Joint joint) {}

    static final class Counts {
        int positions, candidateStates, targetDependsOnPrefix, targetDiffersFromPreBuild, selectedTargetDiffersFromFullPlay;
        int predictedChoiceChanges, oracleBetter, oracleWorse, oracleEqual;
        int selectedPrefixInkedBefore, selectedPrefixInkedAfter, selectedRouteInkedBefore, selectedRouteInkedAfter;
        int selectedTerminalBefore, selectedTerminalAfter;
        Map<String, int[]> rankingChanges = new LinkedHashMap<>(); // fixed-v-none, adapted-v-none, adapted-v-fixed
        void add(Joint row) {
            positions++;
            if (row == null) return;
            candidateStates++;
            if (Arrays.stream(row.prefixTargets).distinct().count() > 1) targetDependsOnPrefix++;
            if (Arrays.stream(row.prefixTargets).anyMatch(r -> r != row.preBuildTarget)) targetDiffersFromPreBuild++;
            if (row.prefixTargets[row.priorSelected] != row.priorFullPlayTarget) selectedTargetDiffersFromFullPlay++;
            row.scenarios.forEach((name, s) -> {
                int[] counts = rankingChanges.computeIfAbsent(name, unused -> new int[3]);
                if (better(s.fixed, s.fixedSelected, s.noneSelected)) counts[0]++;
                if (better(s.adapted, s.adaptedSelected, s.noneSelected)) counts[1]++;
                if (better(s.adapted, s.adaptedSelected, s.fixedSelected)) counts[2]++;
            });
            int selected = row.scenarios.get("predicted").adaptedSelected;
            if (selected != row.priorSelected) predictedChoiceChanges++;
            Scenario oracle = row.scenarios.get("oracle");
            if (better(oracle.adapted, selected, row.priorSelected)) oracleBetter++;
            else if (better(oracle.adapted, row.priorSelected, selected)) oracleWorse++;
            else oracleEqual++;
            Effect before = oracle.adaptedEffects[row.priorSelected], after = oracle.adaptedEffects[selected];
            if (before.prefixCellsInked > 0) selectedPrefixInkedBefore++;
            if (after.prefixCellsInked > 0) selectedPrefixInkedAfter++;
            if (before.routeInked) selectedRouteInkedBefore++;
            if (after.routeInked) selectedRouteInkedAfter++;
            if (before.terminal) selectedTerminalBefore++;
            if (after.terminal) selectedTerminalAfter++;
        }
    }

    /** The real post-build chooser sees only our prefix and all initially buildable planned cells. */
    static int target(GameState before, int me, int[] prefix, int[] route) throws Exception {
        EvalBot bot = ReplyProbe.fresh(before, me);
        boolean[] planned = (boolean[]) CgDisruptStudy.field(bot, "myRoute");
        for (int c : route) if (before.canBuild(c)) planned[c] = true;
        GameState afterBuild = before.copy();
        var moves = new Rules.PlayerMoves();
        Rules.expand(before, me, ReplyProbe.actions(before, new ReplyProbe.Reply(prefix, -1)),
                new Pathing(before.size), moves, new Rules.TurnResult());
        if (moves.error != null || !moves.skipped.isEmpty()) throw new IllegalArgumentException("Invalid prefix");
        int[] empty = new int[0];
        Rules.applyPlacements(afterBuild, me == 0 ? prefix : empty, me == 0 ? prefix.length : 0,
                me == 1 ? prefix : empty, me == 1 ? prefix.length : 0);
        Rules.updateConnections(afterBuild, new Pathing(before.size));
        ((Timer) CgDisruptStudy.field(bot, "timer")).start(ReplyProbe.AMPLE_MS);
        int target = (int) CgDisruptStudy.call(bot, "chooseDisrupt", CgDisruptStudy.STATE_PLAYER, afterBuild, me);
        if (!(boolean) CgDisruptStudy.field(bot, "disruptSearchComplete")) throw new IllegalStateException("Incomplete target scan");
        return target;
    }

    static Joint inspect(GameState state, int me, ReplyProbe.Inspection prior) throws Exception {
        GameState unchanged = state.copy();
        ReplyProbe.Prepared prepared = ReplyProbe.prepare(state, me);
        EvalBot bot = prepared.bot(); int count = prepared.count();
        if (count != prior.choices().size()) throw new IllegalStateException("Candidate count drift");
        if (count == 0) { ReplyProbe.sameState(unchanged, state); return null; }
        // Compare regenerated routes/prefixes and the prediction BEFORE any oracle use or target scan.
        int[][] routes = (int[][]) CgDisruptStudy.field(bot, "buildBeamPath");
        int[] lengths = (int[]) CgDisruptStudy.field(bot, "buildBeamLen");
        int[][] prefixes = (int[][]) CgDisruptStudy.field(bot, "buildBeamPrefix");
        int[] prefixLengths = (int[]) CgDisruptStudy.field(bot, "buildBeamPrefixLen");
        double[] values = (double[]) CgDisruptStudy.field(bot, "buildBeamValue");
        for (int i = 0; i < count; i++) {
            var old = prior.choices().get(i);
            if (!Arrays.equals(Arrays.copyOf(routes[i], lengths[i]), old.route())
                    || !Arrays.equals(Arrays.copyOf(prefixes[i], prefixLengths[i]), old.prefix())
                    || !Double.isFinite(old.originalValue())
                    || Math.abs(values[i] - old.originalValue()) > 1e-8) throw new IllegalStateException("Candidate drift");
        }
        int[][] replies = (int[][]) CgDisruptStudy.field(bot, "replyBeamPrefix");
        int replyLen = ((int[]) CgDisruptStudy.field(bot, "replyBeamPrefixLen"))[0];
        int replyTarget = ((int[]) CgDisruptStudy.field(bot, "replyDisrupt"))[0];
        if (!Arrays.equals(Arrays.copyOf(replies[0], replyLen), prior.predicted().cells())
                || replyTarget != prior.predicted().disrupt()) throw new IllegalStateException("Prediction drift");

        int common = target(state, me, new int[0], new int[0]);
        int[] fixed = new int[count], adapted = new int[count]; Arrays.fill(fixed, common);
        for (int i = 0; i < count; i++) adapted[i] = target(state, me,
                prior.choices().get(i).prefix(), prior.choices().get(i).route());
        // Targets are now fixed independently of opponent scenario, including the recorded oracle.
        Map<String, Scenario> scenarios = new LinkedHashMap<>();
        for (String name : List.of("known", "predicted", "oracle")) {
            ReplyProbe.Reply reply = switch (name) {
                case "known" -> new ReplyProbe.Reply(new int[0], -1);
                case "predicted" -> prior.predicted();
                default -> prior.actual();
            };
            var noneValues = ReplyProbe.scenario(bot, state, me, count, reply);
            for (int i = 0; i < count; i++) {
                var old = prior.choices().get(i);
                sameValue(switch (name) { case "known" -> old.known(); case "predicted" -> old.predicted();
                    default -> old.oracle(); }, noneValues[i]);
            }
            var fixedValues = ReplyProbe.scenario(bot, state, me, count, reply, fixed);
            var adaptedValues = ReplyProbe.scenario(bot, state, me, count, reply, adapted);
            int noneBest = ReplyProbe.best(noneValues);
            if (name.equals("predicted") && noneBest != prior.selected()) throw new IllegalStateException("Selected route drift");
            Effect[] effects = new Effect[count];
            for (int i = 0; i < count; i++) {
                var choice = prior.choices().get(i);
                GameState after = ReplyProbe.resolve(state, me, choice.prefix(), adapted[i], reply);
                int inked = 0; boolean routeInked = false;
                for (int c : choice.prefix()) if (after.inked[c]) inked++;
                for (int c : choice.route()) if (after.inked[c]) routeInked = true;
                effects[i] = new Effect(inked, routeInked, Rules.gameOver(after, new Pathing(after.size)),
                        after.score[me] - after.score[1 - me]);
            }
            scenarios.put(name, new Scenario(noneValues, fixedValues, adaptedValues, noneBest,
                    bestKeepingTie(fixedValues, noneBest), bestKeepingTie(adaptedValues, noneBest), effects));
        }
        ReplyProbe.sameState(unchanged, state);
        return new Joint(common, adapted, prior.selected(), prior.ample().accepted().disrupt(), scenarios);
    }

    static void sameValue(ReplyProbe.Value expected, ReplyProbe.Value actual) {
        if ((expected.heuristic() != null && !Double.isFinite(expected.heuristic()))
                || (actual.heuristic() != null && !Double.isFinite(actual.heuristic()))) {
            throw new IllegalStateException("Non-finite evaluation must be encoded as null");
        }
        boolean bothNull = expected.heuristic() == null && actual.heuristic() == null;
        if (expected.immediateDelta() != actual.immediateDelta() || (!bothNull &&
                (expected.heuristic() == null || actual.heuristic() == null
                        || Math.abs(expected.heuristic() - actual.heuristic()) > 1e-8))) {
            throw new IllegalStateException("Original no-own-hit evaluation drift");
        }
    }

    static boolean better(ReplyProbe.Value[] values, int a, int b) {
        return ReplyProbe.number(values[a]) > ReplyProbe.number(values[b]) + 1e-9;
    }

    static int bestKeepingTie(ReplyProbe.Value[] values, int baseline) {
        int best = baseline;
        for (int i = 0; i < values.length; i++) if (better(values, i, best)) best = i;
        return best;
    }

    static void analyze(Path capture, Path priorPath, Path out) throws Exception {
        if (Files.exists(out)) throw new IllegalArgumentException("Choose a new report path: " + out);
        byte[] priorBytes = Files.readAllBytes(priorPath);
        Prior prior = ReplyStudy.JSON.fromJson(new String(priorBytes, StandardCharsets.UTF_8), Prior.class);
        String manifestHash = ReplyStudy.hash(Files.readAllBytes(capture.resolve("manifest.json")));
        if (prior.schemaVersion != 1 || !manifestHash.equals(prior.corpusManifestSha256)
                || !BotCatalog.describe(ReplyProbe.SPEC).equals(prior.candidate)
                || !ReplyStudy.JSON.toJsonTree(ReplyProbe.parameters()).equals(prior.probeParams)) {
            throw new IllegalArgumentException("Prior report/capture/engine/configuration mismatch");
        }
        List<ReplyStudy.Position> positions = ReplyStudy.loadPositions(capture);
        if (positions.size() != prior.samples.size()) throw new IllegalArgumentException("Prior sample count mismatch");
        // Validate the entire sample mapping before any new diagnostic calls.
        for (int i = 0; i < positions.size(); i++) validateSample(positions.get(i), prior.samples.get(i));
        List<Sample> samples = new ArrayList<>();
        Counts all = new Counts(), unique = new Counts(); Map<String, Counts> byPolicy = new LinkedHashMap<>();
        var seen = new HashSet<String>();
        for (int i = 0; i < positions.size(); i++) {
            var p = positions.get(i); var old = prior.samples.get(i);
            Joint joint = inspect(p.state(), p.me(), old.inspection());
            samples.add(new Sample(p.game(), p.seed(), p.me(), p.policy(), p.state().turn, old.stateSha256(), joint));
            all.add(joint); byPolicy.computeIfAbsent(p.policy(), unused -> new Counts()).add(joint);
            if (seen.add(old.stateSha256() + ":" + p.me())) unique.add(joint);
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schemaVersion", 1); report.put("createdAt", Instant.now().toString());
        report.put("javaVersion", System.getProperty("java.version"));
        report.put("priorReportSha256", ReplyStudy.hash(priorBytes)); report.put("corpusManifestSha256", manifestHash);
        report.put("candidate", prior.candidate); report.put("probeParams", prior.probeParams);
        report.put("method", "Same candidates/private projection, fixed no-own-action baseline per opponent scenario. "
                + "Own targets selected using known-state only: pre-build or prefix-specific, full route marks retained. "
                + "No leftover build, future-target search, games or timing reruns. Oracle is evaluation only. "
                + "Projection can misvalue terminal states and discount sabotage by route duration. No strength claim. "
                + "rankingChanges arrays: fixed-v-none, adapted-v-none, adapted-v-fixed (strict value improvements). "
                + "Unique counts retain the first state/perspective occurrence; repeated turns/maps remain dependent.");
        report.put("summary", all); report.put("uniqueSummary", unique); report.put("bySourcePolicy", byPolicy);
        report.put("samples", samples);
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, ReplyStudy.JSON.toJson(report), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        System.out.println(ReplyStudy.JSON.toJson(all)); System.out.println("Report: " + out);
    }

    static void validateSample(ReplyStudy.Position p, ReplyStudy.Sample sample) throws Exception {
        if (!p.game().equals(sample.game()) || p.seed() != sample.seed() || p.me() != sample.me()
                || !p.policy().equals(sample.policy()) || p.state().turn != sample.turn()
                || !ReplyStudy.stateHash(p.state()).equals(sample.stateSha256())
                || !p.own().equals(sample.recordedOwn()) || !p.opponent().equals(sample.recordedOpponent())) {
            throw new IllegalArgumentException("Prior sample mapping mismatch");
        }
        var actual = ReplyProbe.accepted(p.state(), 1 - p.me(), Action.parseLine(p.opponent()));
        if (!Arrays.equals(actual.cells(), sample.inspection().actual().cells())
                || actual.disrupt() != sample.inspection().actual().disrupt()) throw new IllegalArgumentException("Oracle action mismatch");
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("captureDir priorReport newReport");
        analyze(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]));
    }
}
