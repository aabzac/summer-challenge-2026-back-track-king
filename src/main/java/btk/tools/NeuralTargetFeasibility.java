package btk.tools;

import btk.bot.BotFactory;
import btk.bot.current.RolloutBot;
import btk.bot.neural.NeuralNet;
import btk.bot.neural.NeuralV2Weights;
import btk.bot.neural.NeuralV3Bot;
import btk.bot.neural.NeuralV3Features;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.tools.target.TargetProposer;
import btk.tools.target.TargetProposalFeatures;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static btk.tools.NeuralProposalStudy.*;

/** Isolated component parity and local timing; deliberately not a playable hybrid bot. */
public final class NeuralTargetFeasibility {
    private static final int WARMUPS = 3;
    private static final long BUDGET_MS = 36;
    private static final class Sample {
        final Position position;
        final TargetProposer proposer;
        final TargetProposer timedProposer;
        final RolloutBot baseline;
        int referenceTarget, candidates;
        Sample(Position position, TargetProposer proposer, TargetProposer timedProposer) {
            this.position = position; this.proposer = proposer; this.timedProposer = timedProposer;
            baseline = new RolloutBot(BotFactory.parameters("v09?NOISE=0"));
            baseline.init(position.state(), position.me());
        }
    }
    private static final class Deadline implements TargetProposalFeatures.Deadline {
        long end;
        public boolean expired() { return System.nanoTime() >= end; }
    }
    private static int target(GameState state, String command, int me) {
        var tr = new Rules.TurnResult();
        Rules.expand(state, me, Action.parseLine(command), new Pathing(state.size), tr.moves[me], tr);
        require(!tr.unknownActions && tr.moves[me].error == null && tr.moves[me].disrupts.size() <= 1,
                "Invalid historical target history");
        return tr.moves[me].disrupts.isEmpty() ? -1 : tr.moves[me].disrupts.get(0);
    }
    private static Sample parity(Position position, CgReplay replay) throws Exception {
        int me = position.me();
        GameState state = replay.toState(); state.maxTurns = Math.max(state.maxTurns, replay.turns());
        Pathing paths = new Pathing(state.size); Rules.updateConnections(state, paths);
        NeuralV3Features reference = new NeuralV3Features(state);
        TargetProposer proposal = new TargetProposer(state, true);
        TargetProposer timedProposal = new TargetProposer(state, true);
        for (int t = 0; t < position.state().turn; t++) {
            reference.observe(state, me); proposal.features.observe(state, me);
            timedProposal.observe(state, me);
            int emitted = target(state, replay.turnStdout.get(t)[me], me);
            reference.recordTarget(emitted); proposal.features.recordTarget(emitted);
            timedProposal.recordTarget(emitted);
            Rules.resolve(state, Action.parseLine(replay.turnStdout.get(t)[0]),
                    Action.parseLine(replay.turnStdout.get(t)[1]), paths);
        }
        ReplyProbe.sameState(position.state(), state);
        GameState before = state.copy(); reference.observe(state, me);
        require(reference.prepare(state, me, true, null), "Reference preparation incomplete");
        int[] regions = new int[state.regionCount]; float[][] rows = new float[state.regionCount][29];
        int n = reference.sabotageCandidates(state, me, regions, rows, null);
        int actual = proposal.propose(state, me, null);
        require(proposal.lastComplete && proposal.lastCount == n, "Candidate count mismatch");
        NeuralNet net = new NeuralNet(NeuralV2Weights.SABOTAGE_SIZES, NeuralV2Weights.SABOTAGE);
        int best = -1; double bestScore = 0; boolean closing = false;
        for (int i = 0; i < n; i++) {
            int r = regions[i];
            require(r == proposal.regions[i], "Candidate ordering mismatch");
            for (int j = 0; j < 29; j++) require(Float.floatToIntBits(rows[i][j])
                    == Float.floatToIntBits(proposal.rows[i][j]), "Feature mismatch game="
                    + position.gameId() + " turn=" + state.turn + " region=" + r + " feature=" + j);
            require(reference.closes[r] == proposal.features.closes[r]
                    && reference.winsIfClosed[r] == proposal.features.winsIfClosed[r], "Closing guard mismatch");
            float value = net.score(rows[i]);
            // Score every row independently, including guarded rows not scored by chooseTarget.
            float candidate = net.score(proposal.rows[i]);
            require(Float.floatToIntBits(value) == Float.floatToIntBits(candidate), "Score mismatch");
            require(proposal.scoreAvailable[i] == (!closing && !reference.closes[r]), "Score availability mismatch");
            if (proposal.scoreAvailable[i]) require(Float.floatToIntBits(value)
                    == Float.floatToIntBits(proposal.scores[i]), "Proposer inference mismatch");
            if (closing) continue;
            if (reference.closes[r]) {
                if (reference.winsIfClosed[r] && state.score[me] - state.score[1 - me] >= NeuralV3Bot.CLOSE_MARGIN) {
                    best = i; closing = true;
                }
                continue;
            }
            if (best < 0 || value > bestScore) { best = i; bestScore = value; }
        }
        int expected = best < 0 ? -1 : regions[best];
        require(expected == actual, "Deterministic target mismatch");
        ReplyProbe.sameState(before, state);
        Sample sample = new Sample(position, proposal, timedProposal);
        sample.referenceTarget = expected; sample.candidates = n;
        return sample;
    }
    private static Map<String, Object> measure(Sample sample, Deadline deadline, boolean measured) throws Exception {
        Position p = sample.position; GameState state = p.state(); GameState before = state.copy();
        long start = System.nanoTime();
        var actions = sample.baseline.play(state, p.me(), BUDGET_MS);
        long baselineNs = System.nanoTime() - start;
        // Outside measured baseline time: legality/action serialization and state assertions.
        Plan emitted = accepted(state, p.me(), Action.format(actions));
        deadline.end = System.nanoTime() + AMPLE_MS * 1_000_000L;
        // Measured instance has observed only the prefix: include this turn's real history update.
        TargetProposer proposer = measured ? sample.timedProposer : sample.proposer;
        start = System.nanoTime();
        int proposed = proposer.propose(state, p.me(), deadline);
        long proposalNs = System.nanoTime() - start;
        require(proposer.lastComplete && !deadline.expired()
                && proposed == sample.referenceTarget, "Timed proposal incomplete or differs from parity pass");
        if (proposed >= 0) legal(state, p.me(), emitted.cells(), proposed);
        ReplyProbe.sameState(before, state);
        long remaining = Math.max(0, BUDGET_MS * 1_000_000L - baselineNs);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("gameId", p.gameId()); row.put("turn", state.turn); row.put("me", p.me());
        row.put("candidates", sample.candidates); row.put("featuresCompared", sample.candidates * 29);
        row.put("referenceTarget", sample.referenceTarget); row.put("proposedTarget", proposed);
        row.put("baselineTarget", emitted.target()); row.put("baselineAction", emitted.action());
        row.put("baselineNs", baselineNs); row.put("proposalNs", proposalNs); row.put("remainingNs", remaining);
        row.put("moduleFitsRemaining", proposalNs <= remaining);
        row.put("ordinarySearchComplete", sample.baseline.ordinarySearchComplete);
        row.put("networkPhaseComplete", sample.baseline.networkPhaseComplete);
        row.put("selectedOpening", sample.baseline.selectedOpening); return row;
    }
    private static Map<String, Object> distribution(List<Map<String, Object>> rows, String key) {
        long[] values = rows.stream().mapToLong(r -> ((Number) r.get(key)).longValue()).sorted().toArray();
        return Map.of("minNs", values[0], "medianNs", values[values.length / 2],
                "p95Ns", values[(int) Math.ceil(values.length * .95) - 1], "maxNs", values[values.length - 1]);
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("selection.json fresh-report.json");
        Path selection = Path.of(args[0]), out = Path.of(args[1]); require(!Files.exists(out), "Report exists");
        Map<String, Object> report = new LinkedHashMap<>(); List<Map<String, Object>> rows = new ArrayList<>();
        report.put("schema", "neural-target-feasibility-v1"); report.put("selectionSha256", hash(selection));
        report.put("javaVersion", System.getProperty("java.version")); report.put("warmupPasses", WARMUPS);
        report.put("measuredPasses", 1); report.put("workers", 1); report.put("baselineBudgetMs", BUDGET_MS);
        report.put("scope", "Historical public histories; exact component parity and local timing only. "
                + "No E/D rollouts, hybrid games, cold-process pass or strength claim.");
        try {
            Loaded loaded = load(selection); require(loaded.positions().size() == 51 && loaded.omissions().size() == 13,
                    "Changed fixed panel");
            report.put("games", loaded.games()); report.put("omissions", loaded.omissions());
            JsonObject input = JsonParser.parseString(Files.readString(selection)).getAsJsonObject();
            Map<Long, CgReplay> replays = new LinkedHashMap<>();
            for (var item : input.getAsJsonArray("games")) {
                JsonObject game = item.getAsJsonObject();
                replays.put(game.get("gameId").getAsLong(), CgReplay.load(Path.of(game.get("file").getAsString())));
            }
            List<Sample> samples = new ArrayList<>();
            for (Position p : loaded.positions()) samples.add(parity(p, replays.get(p.gameId())));
            report.put("parityStates", samples.size());
            report.put("parityCandidates", samples.stream().mapToInt(s -> s.candidates).sum());
            report.put("parityFeatureValues", samples.stream().mapToInt(s -> s.candidates * 29).sum());
            Deadline deadline = new Deadline();
            for (int pass = 0; pass < WARMUPS; pass++) for (Sample sample : samples) measure(sample, deadline, false);
            for (Sample sample : samples) rows.add(measure(sample, deadline, true));
            report.put("baselineTime", distribution(rows, "baselineNs"));
            report.put("proposalTime", distribution(rows, "proposalNs"));
            report.put("remainingTime", distribution(rows, "remainingNs"));
            report.put("moduleFitsRemaining", rows.stream().filter(r -> Boolean.TRUE.equals(r.get("moduleFitsRemaining"))).count());
            report.put("baselineSearchComplete", rows.stream().filter(r -> Boolean.TRUE.equals(r.get("ordinarySearchComplete"))
                    && Boolean.TRUE.equals(r.get("networkPhaseComplete"))).count());
            report.put("complete", true);
        } catch (Exception e) { report.put("complete", false); report.put("error", e.toString()); throw e; }
        finally {
            report.put("samples", rows); Files.createDirectories(out.toAbsolutePath().getParent());
            Files.writeString(out, ReplyStudy.JSON.toJson(report), StandardOpenOption.CREATE_NEW);
        }
    }
}
