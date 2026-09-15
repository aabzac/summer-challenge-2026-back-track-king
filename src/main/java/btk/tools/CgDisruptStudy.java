package btk.tools;

import btk.bot.BotFactory;
import btk.bot.Params;
import btk.bot.Timer;
import btk.bot.current.EvalBot;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import com.google.gson.GsonBuilder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Tools-only inspection of the actual private evaluator; no copied bot policy or shipped hooks. */
public final class CgDisruptStudy {
    record Target(int region, int needed, int earning, int ownTracks, int opponentTracks,
                  double ownPlan, double ownIncomeLoss, double opponentIncomeLoss,
                  Double value, boolean scannedAsTrackTarget) {}
    record Evaluation(int selected, List<Target> targets) {}
    record Sample(long gameId, int turn, int me, String recorded, int recordedTarget,
                  Evaluation recordedBuildEvaluation, String timedAction, double timedMs,
                  boolean timedScanComplete, Evaluation timedBuildEvaluation) {}

    // Reflection is deliberately confined to this offline tool. Fail fast if evaluator internals change.
    static Object field(EvalBot bot, String name) throws Exception {
        Field f = EvalBot.class.getDeclaredField(name); f.setAccessible(true); return f.get(bot);
    }

    static Object call(EvalBot bot, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = EvalBot.class.getDeclaredMethod(name, types); m.setAccessible(true);
        return m.invoke(bot, args);
    }

    static final Class<?>[] STATE_PLAYER = {GameState.class, int.class};

    /** Known-state assumption: our recorded accepted build succeeds, opponent does nothing yet. */
    static Evaluation recordedBuild(GameState before, int me, List<Action> actions, Params params) throws Exception {
        GameState after = before.copy();
        EvalBot bot = new EvalBot(params.copy()); bot.init(after, me);
        Pathing paths = new Pathing(after.size);
        Rules.PlayerMoves moves = new Rules.PlayerMoves();
        Rules.expand(before, me, actions, paths, moves, new Rules.TurnResult());
        if (moves.error != null) throw new IllegalArgumentException(moves.error);
        boolean[] route = (boolean[]) field(bot, "myRoute");
        for (int i = 0; i < moves.count; i++) {
            int c = moves.cells[i]; after.owner[c] = me; route[c] = true;
        }
        Rules.updateConnections(after, paths);
        return evaluate(bot, after, me);
    }

    /** Preserve the timed bot's own-route marks. No future/recorded opponent actions are consulted. */
    static Evaluation evaluate(EvalBot bot, GameState afterBuild, int me) throws Exception {
        Params params = (Params) field(bot, "P");
        double noise = params.NOISE;
        params.NOISE = 0;
        try {
            ((Timer) field(bot, "timer")).start(60_000);
            int selected = (int) call(bot, "chooseDisrupt", STATE_PLAYER, afterBuild, me);
            if (!(boolean) field(bot, "disruptSearchComplete")) {
                throw new IllegalStateException("Full diagnostic scan did not finish");
            }
            int[] own = (int[]) field(bot, "myTracks"), opponent = (int[]) field(bot, "oppTracks");
            double[] plan = (double[]) field(bot, "myPlanByRegion");
            int[] income = (int[]) field(bot, "inc0");
            List<Target> targets = new ArrayList<>();
            for (int r = 0; r < afterBuild.regionCount; r++) {
                if (afterBuild.regionInked[r] || afterBuild.regionHasTown(r)) continue;
                double value = (double) call(bot, "disruptValue",
                        new Class<?>[]{GameState.class, int.class, int.class, boolean.class},
                        afterBuild, me, r, false);
                // Compute components with the shared core even for a rejected/terminal target.
                GameState ink = afterBuild.copy(); Rules.ink(ink, r);
                Rules.updateConnections(ink, new Pathing(ink.size));
                int[] afterIncome = new int[2]; Rules.incomePerTurn(ink, afterIncome);
                int needed = Math.max(1, GameState.INK_THRESHOLD - afterBuild.regionInstability[r]);
                targets.add(new Target(r, needed, afterBuild.turnsLeft() - needed, own[r], opponent[r],
                        plan[r], income[me] - afterIncome[me], income[1 - me] - afterIncome[1 - me],
                        Double.isFinite(value) ? value : null, own[r] + opponent[r] > 0));
            }
            return new Evaluation(selected, targets);
        } finally {
            params.NOISE = noise;
        }
    }

    public static void main(String[] args) throws Exception {
        Params params = BotFactory.parameters("v05");
        List<Sample> samples = new ArrayList<>();
        // Each spec is path@player@turn+turn. One chronological timed prefix per spec.
        for (String spec : args[0].split(",")) {
            String[] parts = spec.split("@");
            if (parts.length != 3) throw new IllegalArgumentException("Expected path@player@turn+turn");
            int me = Integer.parseInt(parts[1]);
            int[] turns = Arrays.stream(parts[2].split("\\+")).mapToInt(Integer::parseInt).sorted().toArray();
            CgReplay replay = CgReplay.load(Path.of(parts[0]));
            if (me < 0 || me > 1 || turns[0] < 0 || turns[turns.length - 1] >= replay.turns()) {
                throw new IllegalArgumentException("Invalid player/turn selection");
            }
            if (CgValidate.validate(replay, false).firstDivergenceTurn >= 0) {
                throw new IllegalStateException("Invalid replay " + replay.gameId);
            }
            GameState state = replay.toState(); Pathing paths = new Pathing(state.size);
            Rules.updateConnections(state, paths);
            EvalBot timed = new EvalBot(params.copy()); timed.init(state.copy(), me);
            // Old generic warmup, not MainBot's new isolated warmup and not proof of deployed hash.
            timed.warmup(state.copy(), me, 500);
            for (int t = 0; t <= turns[turns.length - 1]; t++) {
                if (replay.failure(t, 0) != null || replay.failure(t, 1) != null) {
                    throw new IllegalStateException("Selected prefix includes a terminal failure");
                }
                GameState input = state.copy();
                long start = System.nanoTime();
                List<Action> output = timed.play(input, me, t == 0 ? 486 : 36);
                double elapsed = (System.nanoTime() - start) / 1e6;
                if (Arrays.binarySearch(turns, t) >= 0) {
                    boolean complete = (boolean) field(timed, "disruptSearchComplete");
                    List<Action> recorded = CgPlayerStudy.effectiveActions(replay, t, me);
                    Rules.PlayerMoves moves = new Rules.PlayerMoves();
                    Rules.expand(state, me, recorded, paths, moves, new Rules.TurnResult());
                    Sample sample = new Sample(replay.gameId, t, me, Action.format(recorded),
                            moves.disrupts.isEmpty() ? -1 : moves.disrupts.get(0),
                            recordedBuild(state, me, recorded, params), Action.format(output), elapsed,
                            complete, evaluate(timed, input, me));
                    samples.add(sample);
                    System.out.printf("%d t%d P%d recorded=%d full=%d timed=%s complete=%s %.2fms fullTimed=%d%n",
                            sample.gameId, t, me, sample.recordedTarget, sample.recordedBuildEvaluation.selected,
                            sample.timedAction, complete, elapsed, sample.timedBuildEvaluation.selected);
                }
                Rules.resolve(state, CgPlayerStudy.effectiveActions(replay, t, 0),
                        CgPlayerStudy.effectiveActions(replay, t, 1), paths);
            }
        }
        Path out = Path.of(args[1]); Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(Map.of(
                "schemaVersion", 1, "params", params, "samples", samples,
                "method", "Known-state full noise-free evaluation of actual EvalBot methods with v05 params. "
                        + "Recorded build assumes own accepted placements without simultaneous opponent actions; "
                        + "marks only those cells as own plan; unbuilt intended route is unknown, so this is "
                        + "not exact reconstruction of hidden plan terms. Assumes opponent model off. Timed current run uses "
                        + "old generic 500ms warmup and chronological recorded prefixes at 486/36ms. "
                        + "Full evaluation after selected turns affects later JIT, but consumes no RNG. "
                        + "No CG CPU/seed/history equivalence or original timeout-cause claim. No strength gate.")));
        System.out.println("Report: " + out);
    }
}
