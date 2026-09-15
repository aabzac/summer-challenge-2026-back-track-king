package btk.tools;

import btk.bot.BotFactory;
import btk.bot.Params;
import btk.bot.Timer;
import btk.bot.Bot;
import btk.bot.BotCatalog;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Offline access to the retained policy, never merged into Player. No copied scoring formula. */
final class ReplyProbe {
    static final String SPEC = "exp-reply-rerank?BUILD_BEAM_WIDTH=2,OPP_REPLY_WIDTH=1";
    static final long AMPLE_MS = 60_000;
    private static final List<Action> WAIT = List.of(Action.waitAction());
    record Reply(int[] cells, int disrupt) {}
    /** Null heuristic means a route rejected as impossible (-infinity), not a zero value. */
    record Value(Double heuristic, int immediateDelta) {}
    record Choice(int[] route, int[] prefix, double originalValue, Value known, Value predicted, Value oracle) {}
    record Play(String actions, Reply accepted, double elapsedMs, long budgetMs) {}
    record Prepared(Bot bot, int count, double elapsedMs) {}
    record Inspection(List<Choice> choices, Reply predicted, Reply actual, int selected,
                      int oracleSelected, int immediateSelected, Play nominal, Play ample,
                      boolean nominalFirst, boolean amplePrefixMatches, boolean nominalPrefixMatches,
                      double firstBuildProbeMs) {}

    static Params parameters() {
        // BotFactory.parameters verifies the retained engine before returning its pinned snapshot.
        Params params = BotFactory.parameters(SPEC);
        params.NOISE = 0; // diagnostic isolation, not the played candidate's 0.1
        return params;
    }

    static com.google.gson.JsonObject configuration() {
        var json = ReplyStudy.JSON.toJsonTree(parameters()).getAsJsonObject();
        json.keySet().retainAll(BotCatalog.parameterNames("exp-reply-rerank"));
        return json;
    }

    static Bot fresh(GameState state, int me) {
        Bot bot = BotCatalog.createExperiment(SPEC + ",NOISE=0"); bot.init(state.copy(), me); return bot;
    }

    static Inspection inspect(GameState state, int me, List<Action> recordedOpponent, boolean nominalFirst)
            throws Exception {
        // Full-play calls precede per-state oracle evaluation. The order alternates.
        Play nominal, ample;
        if (nominalFirst) { nominal = play(state, me, 36); ample = play(state, me, AMPLE_MS); }
        else { ample = play(state, me, AMPLE_MS); nominal = play(state, me, 36); }
        Prepared prepared = prepare(state, me);
        Bot bot = prepared.bot;
        int count = prepared.count;
        double probeMs = prepared.elapsedMs;
        int[] lengths = (int[]) CgDisruptStudy.field(bot, "buildBeamLen");
        Reply actual = accepted(state, 1 - me, recordedOpponent);
        if (count == 0) return new Inspection(List.of(), null, actual, -1, -1, -1, nominal, ample,
                nominalFirst, true, true, probeMs);

        int[][] routes = (int[][]) CgDisruptStudy.field(bot, "buildBeamPath");
        int[][] prefixes = (int[][]) CgDisruptStudy.field(bot, "buildBeamPrefix");
        int[] prefixLengths = (int[]) CgDisruptStudy.field(bot, "buildBeamPrefixLen");
        double[] original = (double[]) CgDisruptStudy.field(bot, "buildBeamValue");
        int[] best = Arrays.copyOf((int[]) CgDisruptStudy.field(bot, "bestPath"),
                (int) CgDisruptStudy.field(bot, "bestLen"));
        int selected = -1;
        for (int i = 0; i < count; i++) if (Arrays.equals(best, Arrays.copyOf(routes[i], lengths[i]))) selected = i;
        if (selected < 0) throw new IllegalStateException("Selected path missing from captured beam");
        int[][] replies = (int[][]) CgDisruptStudy.field(bot, "replyBeamPrefix");
        int[] replyLengths = (int[]) CgDisruptStudy.field(bot, "replyBeamPrefixLen");
        int[] disrupt = (int[]) CgDisruptStudy.field(bot, "replyDisrupt");
        Reply predicted = new Reply(Arrays.copyOf(replies[0], replyLengths[0]), disrupt[0]);

        Value[] known = scenario(bot, state, me, count, new Reply(new int[0], -1));
        Value[] predictedValues = scenario(bot, state, me, count, predicted);
        Value[] oracle = scenario(bot, state, me, count, actual);
        if (best(predictedValues) != selected) throw new IllegalStateException("Probe changed predicted ranking");
        List<Choice> choices = new ArrayList<>();
        int immediate = 0;
        for (int i = 0; i < count; i++) {
            choices.add(new Choice(Arrays.copyOf(routes[i], lengths[i]),
                    Arrays.copyOf(prefixes[i], prefixLengths[i]), original[i], known[i], predictedValues[i], oracle[i]));
            if (oracle[i].immediateDelta > oracle[immediate].immediateDelta) immediate = i;
        }
        boolean ampleMatches = startsWith(ample.accepted.cells, choices.get(selected).prefix);
        if (!ampleMatches) throw new IllegalStateException("First-build probe disagrees with ample full play");
        return new Inspection(choices, predicted, actual, selected, best(oracle), immediate, nominal, ample,
                nominalFirst, true, startsWith(nominal.accepted.cells, choices.get(selected).prefix), probeMs);
    }

    static Play play(GameState state, int me, long budget) {
        Bot bot = fresh(state, me);
        GameState input = state.copy();
        long start = System.nanoTime();
        List<Action> actions = bot.play(input, me, budget);
        double ms = (System.nanoTime() - start) / 1e6;
        return new Play(Action.format(actions), accepted(state, me, actions), ms, budget);
    }

    /** Re-run deterministic candidate generation only; no full-play timing measurement. */
    static Prepared prepare(GameState state, int me) throws Exception {
        Bot bot = fresh(state, me);
        // Reuse the real play preparation (extraCost etc.) without copying it into this tool.
        bot.play(state.copy(), me, 0);
        Timer timer = (Timer) CgDisruptStudy.field(bot, "timer");
        timer.start(AMPLE_MS);
        long start = System.nanoTime();
        boolean found = (boolean) CgDisruptStudy.call(bot, "chooseCandidateWithReplies",
                new Class<?>[]{GameState.class, int.class, int.class}, state, me, GameState.PAINT_PER_TURN);
        double probeMs = (System.nanoTime() - start) / 1e6;
        if (timer.up(14)) throw new IllegalStateException("Ample first-build probe hit a phase cutoff");

        int[] lengths = (int[]) CgDisruptStudy.field(bot, "buildBeamLen");
        int count = 0;
        while (count < 2 && lengths[count] > 0) count++;
        if (found != (count > 0)) throw new IllegalStateException("Unexpected first-build buffers");
        return new Prepared(bot, count, probeMs);
    }

    /** Same candidate set/formula; only replace the hypothesized opponent action and its baseline. */
    static Value[] scenario(Bot bot, GameState state, int me, int count, Reply reply) throws Exception {
        int[] targets = new int[count]; Arrays.fill(targets, -1);
        return scenario(bot, state, me, count, reply, targets);
    }

    /** Joint diagnostic: fixed no-own-action baseline; include one known-state own target per prefix. */
    static Value[] scenario(Bot bot, GameState state, int me, int count, Reply reply, int[] targets) throws Exception {
        if (targets.length != count) throw new IllegalArgumentException("One own target per candidate required");
        ((Timer) CgDisruptStudy.field(bot, "timer")).start(AMPLE_MS);
        int[][] replies = (int[][]) CgDisruptStudy.field(bot, "replyBeamPrefix");
        System.arraycopy(reply.cells, 0, replies[0], 0, reply.cells.length);
        ((int[]) CgDisruptStudy.field(bot, "replyBeamPrefixLen"))[0] = reply.cells.length;
        GameState baseline = resolve(state, me, new int[0], reply);
        ((int[]) CgDisruptStudy.field(bot, "replyBaselineScoreDiff"))[0] = baseline.score[me] - baseline.score[1 - me];
        ((double[]) CgDisruptStudy.field(bot, "replyBaselineValue"))[0] = (double) CgDisruptStudy.call(
                bot, "evalState", CgDisruptStudy.STATE_PLAYER, baseline, me);
        int[][] prefixes = (int[][]) CgDisruptStudy.field(bot, "buildBeamPrefix");
        int[] lengths = (int[]) CgDisruptStudy.field(bot, "buildBeamPrefixLen");
        Value[] values = new Value[count];
        for (int i = 0; i < count; i++) {
            int[] own = Arrays.copyOf(prefixes[i], lengths[i]);
            GameState after = resolve(state, me, own, targets[i], reply);
            // Increment only, without inking/scoring: accepted placements are unchanged. The private
            // simulation then inks after BOTH hits. Each result is checked against joint Rules.resolve.
            GameState withOwnHit = state.copy();
            if (targets[i] >= 0) Rules.disrupt(withOwnHit, targets[i]);
            // Guard the private optimized simulation against the full resolver, including clashes/ink.
            GameState internal = state.copy();
            CgDisruptStudy.call(bot, "simulatePredictedTurn", new Class<?>[]{GameState.class, int.class,
                    int[].class, int.class, int[].class, int.class, int.class, GameState.class},
                    withOwnHit, me, own, own.length, reply.cells, reply.cells.length, reply.disrupt, internal);
            sameState(after, internal);
            double value = (double) CgDisruptStudy.call(bot, "replyAwareValue",
                    new Class<?>[]{GameState.class, int.class, int.class, int.class, int.class},
                    withOwnHit, me, i, 0, reply.disrupt);
            if (Double.isNaN(value) || value == Double.POSITIVE_INFINITY) throw new IllegalStateException("Invalid value");
            values[i] = new Value(Double.isFinite(value) ? value : null,
                    after.score[me] - after.score[1 - me] - baseline.score[me] + baseline.score[1 - me]);
        }
        if (((Timer) CgDisruptStudy.field(bot, "timer")).up(14)) throw new IllegalStateException("Scenario ceiling exceeded");
        return values;
    }

    static int best(Value[] values) {
        int selected = 0;
        for (int i = 1; i < values.length; i++) if (number(values[i]) > number(values[selected])) selected = i;
        return selected;
    }

    static double number(Value value) { return value.heuristic == null ? Double.NEGATIVE_INFINITY : value.heuristic; }

    static Reply accepted(GameState state, int me, List<Action> actions) {
        Rules.PlayerMoves moves = new Rules.PlayerMoves();
        Rules.expand(state, me, actions, new Pathing(state.size), moves, new Rules.TurnResult());
        if (moves.error != null) throw new IllegalArgumentException(moves.error);
        return new Reply(Arrays.copyOf(moves.cells, moves.count), moves.disrupts.isEmpty() ? -1 : moves.disrupts.get(0));
    }

    static List<Action> actions(GameState state, Reply reply) {
        List<Action> actions = new ArrayList<>();
        for (int cell : reply.cells) actions.add(Action.place(state.x(cell), state.y(cell)));
        if (reply.disrupt >= 0) actions.add(Action.disrupt(reply.disrupt));
        return actions.isEmpty() ? WAIT : actions;
    }

    static GameState resolve(GameState before, int me, int[] own, Reply reply) {
        return resolve(before, me, own, -1, reply);
    }

    static GameState resolve(GameState before, int me, int[] own, int ownDisrupt, Reply reply) {
        GameState after = before.copy();
        List<Action> mine = actions(before, new Reply(own, ownDisrupt)), theirs = actions(before, reply);
        Rules.TurnResult result = Rules.resolve(after, me == 0 ? mine : theirs,
                me == 1 ? mine : theirs, new Pathing(before.size));
        for (var moves : result.moves) if (moves.error != null || !moves.skipped.isEmpty()) {
            throw new IllegalStateException("Noncanonical probe actions: " + moves.error + " " + moves.skipped);
        }
        return after;
    }

    static void sameState(GameState a, GameState b) {
        if (a.turn != b.turn || !Arrays.equals(a.owner, b.owner) || !Arrays.equals(a.score, b.score)
                || !Arrays.equals(a.regionInstability, b.regionInstability)
                || !Arrays.equals(a.regionInked, b.regionInked) || !Arrays.equals(a.instability, b.instability)
                || !Arrays.equals(a.inked, b.inked) || !Arrays.equals(a.pairPathLen, b.pairPathLen)) {
            throw new IllegalStateException("Probe simulation diverges from core");
        }
        for (int p = 0; p < a.pairCount; p++) for (int i = 0; i < a.pairPathLen[p]; i++) {
            if (a.pairPath[p][i] != b.pairPath[p][i]) throw new IllegalStateException("Path mismatch");
        }
    }

    static boolean startsWith(int[] all, int[] prefix) {
        if (all.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (all[i] != prefix[i]) return false;
        return true;
    }

    static boolean sameCells(int[] a, int[] b) {
        int[] aa = a.clone(), bb = b.clone(); Arrays.sort(aa); Arrays.sort(bb); return Arrays.equals(aa, bb);
    }
}
