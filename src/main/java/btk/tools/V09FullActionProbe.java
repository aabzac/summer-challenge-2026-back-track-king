package btk.tools;

import btk.bot.Params;
import btk.bot.Timer;
import btk.bot.v09.RolloutBotV09;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.core.SearchState;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static btk.tools.V09ContinuationProbe.AMPLE_MS;
import btk.tools.V09ContinuationProbe.Plan;
import btk.tools.V09ContinuationProbe.ReplyTrace;
import btk.tools.V09ContinuationProbe.Root;
import btk.tools.V09ContinuationProbe.Score;
import btk.tools.V09ContinuationProbe.Turn;
import btk.tools.V09ContinuationProbe.Tuple;

/** Tools-only full-action diagnostic. The emitted construction is held fixed while one
 * supplied legal sabotage target changes. Its verified native root (including network roots)
 * retains the original own continuation; no fallback to an invented route-zero context. */
public final class V09FullActionProbe {
    public enum Variant { BASELINE, NO_FUTURE_OWN_DISRUPT }
    public record Metadata(boolean supported, String reason, int root, boolean networkRoot,
                           int ownCount, int replies, Double rawSelectedValue, Plan rawSelected,
                           Plan fullSelected, int nominatedTarget, int fullChooserTarget,
                           boolean rawSelectionMatched, boolean fillMatched) {}
    public record Trace(Variant variant, int root, int target, Score score, List<ReplyTrace> replies) {}
    public record Comparison(Trace noAttack, Trace attack, double delta) {}

    private final V09ContinuationProbe baseline;
    private final RolloutBotV09 bot;
    private final GameState original;
    private final int me;
    private final Params params;
    private final Plan selected;
    private final Metadata metadata;

    private V09FullActionProbe(GameState state, int player) throws Exception {
        original = state.copy(); me = player;
        baseline = V09ContinuationProbe.prepare(state, player);
        bot = (RolloutBotV09) read(baseline, "bot");
        params = baseline.parameters(); selected = baseline.selected();
        metadata = reconstruct();
        ReplyProbe.sameState(original, state);
    }
    public static V09FullActionProbe prepare(GameState state, int me) throws Exception {
        return new V09FullActionProbe(state, me);
    }
    public Metadata metadata() {
        return new Metadata(metadata.supported, metadata.reason, metadata.root, metadata.networkRoot,
                metadata.ownCount, metadata.replies, metadata.rawSelectedValue, copy(metadata.rawSelected),
                copy(metadata.fullSelected), metadata.nominatedTarget, metadata.fullChooserTarget,
                metadata.rawSelectionMatched, metadata.fillMatched);
    }
    public Plan selected() { return copy(selected); }
    public boolean eligible() { return metadata.supported && selected.target() == -1; }
    public int nominatedTarget() { return metadata.nominatedTarget; }

    private Metadata reconstruct() throws Exception {
        int own = baseline.ownCount(), replies = baseline.replies();
        int fullChooser = baseline.targetInfo(selected.cells()).chosen();
        if (baseline.openingSelected()) return unresolved("PROTECTED_OPENING", own, replies, fullChooser);
        if (!baseline.ordinaryComplete() || !baseline.networkComplete() || !Double.isFinite(baseline.selectedValue()))
            return unresolved("INCOMPLETE_NATIVE_SEARCH", own, replies, fullChooser);
        // Frozen v09's default searchRoot order is the root order, with attack before WAIT.
        // Strict improvement preserves the first equal-scoring tuple, exactly as searchRoots.
        Tuple winner = null; double best = -Double.MAX_VALUE;
        for (Tuple tuple : baseline.tuples()) {
            double value = baseline.invokeOriginal(tuple.root(), tuple.target()).value();
            if (value > best) { best = value; winner = tuple; }
        }
        int observedRoot = bot.selectedRoute < 0 ? own : bot.selectedRoute;
        if (winner == null || winner.root() != observedRoot || winner.target() != selected.target()
                || Double.doubleToLongBits(best) != Double.doubleToLongBits(baseline.selectedValue()))
            return unresolved("RAW_SELECTION_MISMATCH", own, replies, fullChooser);
        Root root = null;
        for (Root candidate : baseline.roots()) if (candidate.index() == observedRoot) root = candidate;
        int ordinary = (int) read(bot, "ordinaryRootSize");
        boolean network = observedRoot >= ordinary;
        if (root == null || root.suppressed() || network != bot.selectedNetwork
                || !Arrays.equals(winner.cells(), root.cells()))
            return unresolved("RAW_ROOT_PROVENANCE_MISMATCH", own, replies, fullChooser);
        Plan raw = plan(original, me, winner.cells(), winner.target());
        int[] reconstructed;
        try (AutoCloseable saved = preserve()) {
            timer().start(AMPLE_MS);
            int[] cells = (int[]) read(bot, "bestCells");
            Arrays.fill(cells, 0); System.arraycopy(raw.cells(), 0, cells, 0, raw.cells().length);
            write(bot, "bestCount", raw.cells().length); write(bot, "selectedTarget", raw.target());
            call("fill", new Class<?>[]{GameState.class, int.class}, original, me);
            require(!timer().up(), "Ample full-action fill reconstruction expired");
            reconstructed = Arrays.copyOf(cells, (int) read(bot, "bestCount"));
        }
        boolean fillMatched = Arrays.equals(reconstructed, selected.cells());
        return new Metadata(fillMatched, fillMatched ? "SUPPORTED" : "FINAL_FILL_MISMATCH", observedRoot,
                network, own, replies, best, raw, copy(selected), root.target(), fullChooser, true, fillMatched);
    }
    private Metadata unresolved(String reason, int own, int replies, int fullChooser) {
        Double value = Double.isFinite(baseline.selectedValue()) ? baseline.selectedValue() : null;
        return new Metadata(false, reason, -1, false, own, replies, value, null, copy(selected), -2,
                fullChooser, false, false);
    }

    /** Direct private-rollout comparison with the emitted full construction borrowed into its
     * ORIGINAL root slot. Only root cells/count change temporarily; future route priority does not. */
    public Score invokeOriginal(int target) throws Exception {
        requireSupportedTarget(target);
        try (AutoCloseable saved = preserve()) {
            injectFullConstruction();
            return baseline.invokeOriginal(metadata.root, target);
        }
    }

    public Trace trace(int target, Variant variant) throws Exception {
        requireSupportedTarget(target); require(variant != null, "Missing trace variant");
        if (variant == Variant.BASELINE) {
            try (AutoCloseable saved = preserve()) {
                injectFullConstruction();
                // Existing tracer verifies per-reply scalar and complete final core state against
                // the actual private rollout, now using precisely the emitted full root cells.
                V09ContinuationProbe.Trace result = baseline.trace(metadata.root, target,
                        V09ContinuationProbe.Variant.BASELINE);
                return new Trace(variant, metadata.root, target, result.score(), result.replies());
            }
        }
        return withoutFutureOwnDisruption(target);
    }
    public Comparison compare(int attackTarget, Variant variant) throws Exception {
        require(attackTarget >= 0, "A comparison needs a nominated legal disruption target");
        Trace noAttack = trace(-1, variant), attack = trace(attackTarget, variant);
        return new Comparison(noAttack, attack, attack.score.value() - noAttack.score.value());
    }

    /** Recompute the initial retained opponent policy on a divergent current board. No recorded
     * future commands or prior trace cells are replayed. The reply identity stays fixed. */
    public Plan opponentAction(GameState current, int reply) throws Exception {
        require(metadata.supported, "Unresolved selected-root provenance: " + metadata.reason);
        require(reply >= 0 && reply < metadata.replies, "Unknown retained opponent reply");
        require(current.width == original.width && current.height == original.height
                        && Arrays.equals(current.regionId, original.regionId) && Arrays.equals(current.terrain, original.terrain)
                        && Arrays.equals(current.townCell, original.townCell) && Arrays.deepEquals(current.desired, original.desired),
                "Opponent continuation requires the original map");
        GameState before = current.copy();
        try (AutoCloseable saved = preserve()) {
            timer().start(AMPLE_MS);
            int[] cells = futureCells(current, 1 - me, reply);
            int target = futureTarget(current, 1 - me, cells);
            require(!timer().up(), "Ample retained-opponent continuation expired");
            Plan result = plan(current, 1 - me, cells, target);
            ReplyProbe.sameState(before, current); return result;
        }
    }

    private Trace withoutFutureOwnDisruption(int rootTarget) throws Exception {
        List<ReplyTrace> replies = new ArrayList<>(); double[] values = new double[metadata.replies];
        try (AutoCloseable saved = preserve()) {
            timer().start(AMPLE_MS);
            for (int reply = 0; reply < values.length; reply++) {
                GameState state = original.copy(); Pathing pathing = new Pathing(state.size);
                List<Turn> turns = new ArrayList<>(); boolean terminal = false;
                int ownFirst = metadata.root < metadata.ownCount ? metadata.root : 0;
                int depth = Math.min(params.ROLLOUT_DEPTH, original.turnsLeft());
                for (int offset = 0; offset < depth; offset++) {
                    int[][] cells = new int[2][]; int[] targets = {-1,-1}, proposed = {-1,-1};
                    int[] beforeIncome = new int[2]; Rules.incomePerTurn(state, beforeIncome);
                    for (int player = 0; player < 2; player++) {
                        if (player == me && offset == 0) {
                            cells[player] = selected.cells().clone(); targets[player] = proposed[player] = rootTarget;
                        } else {
                            cells[player] = futureCells(state, player, player == me ? ownFirst : reply);
                            proposed[player] = futureTarget(state, player, cells[player]);
                            targets[player] = player == me ? -1 : proposed[player];
                        }
                    }
                    require(!timer().up(), "Ample no-future-disruption trace expired");
                    int turn = state.turn;
                    Rules.TurnResult resolved = Rules.resolve(state, actions(state, cells[0], targets[0]),
                            actions(state, cells[1], targets[1]), pathing);
                    int[] paint = new int[2];
                    for (int player = 0; player < 2; player++) {
                        Rules.PlayerMoves accepted = resolved.moves[player];
                        require(accepted.error == null && accepted.skipped.isEmpty() && !resolved.unknownActions,
                                "Counterfactual continuation emitted an illegal action");
                        require(Arrays.equals(cells[player], Arrays.copyOf(accepted.cells, accepted.count))
                                && targets[player] == (accepted.disrupts.isEmpty() ? -1 : accepted.disrupts.get(0)),
                                "Core changed generated continuation");
                        paint[player] = accepted.cost;
                    }
                    terminal = Rules.gameOver(state, pathing);
                    int[] afterIncome = new int[2]; Rules.incomePerTurn(state, afterIncome);
                    turns.add(new Turn(offset, turn, cells, proposed, targets, paint, beforeIncome, afterIncome,
                            resolved.gained.clone(), state.score.clone(), terminal, List.copyOf(resolved.inkedRegions),
                            state.regionInstability.clone()));
                    if (terminal) break;
                }
                values[reply] = heuristic(state, terminal);
                replies.add(new ReplyTrace(reply, values[reply], List.copyOf(turns)));
            }
        }
        return new Trace(Variant.NO_FUTURE_OWN_DISRUPT, metadata.root, rootTarget, aggregate(values), List.copyOf(replies));
    }
    private int[] futureCells(GameState state, int player, int first) throws Exception {
        int[] cells = new int[GameState.PAINT_PER_TURN];
        int n = (int) call("fillBuild", new Class<?>[]{GameState.class, int.class, int.class, int[].class},
                state, player, first, cells);
        return Arrays.copyOf(cells, n);
    }
    private int futureTarget(GameState state, int player, int[] cells) throws Exception {
        SearchState board = new SearchState(state); int[] empty = new int[0];
        Rules.applyPlacements(board.state, player == 0 ? cells : empty, player == 0 ? cells.length : 0,
                player == 1 ? cells : empty, player == 1 ? cells.length : 0);
        board.refresh();
        return (int) call("chooseTarget", new Class<?>[]{GameState.class, int.class}, board.state, player);
    }
    /** Frozen policy utility, not game scoring: income and terminal state come from core. */
    private double heuristic(GameState state, boolean terminal) {
        int diff = state.score[me] - state.score[1 - me];
        if (terminal) return Math.signum(diff) * params.ROLLOUT_WIN_VALUE + diff;
        int[] income = new int[2]; Rules.incomePerTurn(state, income);
        return diff + Math.min(params.ROLLOUT_TAIL, state.turnsLeft()) * (income[me] - income[1 - me]);
    }
    private Score aggregate(double[] values) {
        double sum = 0, worst = Double.MAX_VALUE;
        for (double value : values) { sum += value; worst = Math.min(worst, value); }
        return new Score((1 - params.ROLLOUT_ROBUSTNESS) * sum / values.length
                + params.ROLLOUT_ROBUSTNESS * worst, values.clone());
    }
    private void requireSupportedTarget(int target) {
        require(metadata.supported, "Unresolved selected-root provenance: " + metadata.reason);
        require(target >= -1, "Invalid target sentinel");
        plan(original, me, selected.cells(), target);
    }
    private void injectFullConstruction() throws Exception {
        int[][] roots = (int[][]) read(bot, "roots"); int[] counts = (int[]) read(bot, "rootCounts");
        System.arraycopy(selected.cells(), 0, roots[metadata.root], 0, selected.cells().length);
        counts[metadata.root] = selected.cells().length;
    }
    private static Plan copy(Plan plan) {
        return plan == null ? null : new Plan(plan.cells().clone(), plan.target(), plan.action());
    }
    private static Plan plan(GameState state, int player, int[] cells, int target) {
        return V09ContinuationProbe.accepted(state, player, actions(state, cells, target));
    }
    private static List<Action> actions(GameState state, int[] cells, int target) {
        List<Action> result = new ArrayList<>();
        for (int cell : cells) result.add(Action.place(state.x(cell), state.y(cell)));
        if (target >= 0) result.add(Action.disrupt(target));
        if (result.isEmpty()) result.add(Action.waitAction()); return result;
    }
    private Timer timer() throws Exception { return (Timer) read(bot, "timer"); }
    private static Object read(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static void write(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }
    private Object call(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = RolloutBotV09.class.getDeclaredMethod(name, types); method.setAccessible(true);
        return method.invoke(bot, args);
    }
    /** Reuse the prior probe's complete bot-array/work-board/clock guard instead of maintaining
     * a second subtly different restoration implementation. This class never ships. */
    private AutoCloseable preserve() throws Exception {
        Class<?> context = Class.forName(V09ContinuationProbe.class.getName() + "$Context");
        Constructor<?> constructor = context.getDeclaredConstructor(V09ContinuationProbe.class);
        constructor.setAccessible(true); return (AutoCloseable) constructor.newInstance(baseline);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
