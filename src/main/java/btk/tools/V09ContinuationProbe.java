package btk.tools;

import btk.bot.BotFactory;
import btk.bot.Params;
import btk.bot.Timer;
import btk.bot.v09.RolloutBotV09;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.core.SearchState;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Tools-only trace of the frozen v09 continuation. All transitions, legality, income and
 * termination are delegated to core; proposal/future-action decisions call the frozen bot.
 * This is an ample-time diagnostic, not a production policy or a strength measurement. */
public final class V09ContinuationProbe {
    public static final String SPEC = "v09?NOISE=0";
    public static final long AMPLE_MS = 60_000;
    public enum Variant { BASELINE, NO_FUTURE_OWN_DISRUPT, PASSIVE_OPPONENT }
    public record Plan(int[] cells, int target, String action) {}
    public record Root(int index, int[] cells, int target, boolean suppressed) {}
    public record Tuple(int root, int target, int[] cells) {}
    public record Score(double value, double[] replyValues) {}
    public record TargetInfo(int chosen, int[] shortlist) {}
    public record Turn(int offset, int turn, int[][] cells, int[] proposedTargets, int[] targets,
                       int[] paint, int[] beforeIncome, int[] afterIncome, int[] gained,
                       int[] score, boolean terminal, List<Integer> inkedRegions,
                       int[] regionInstability) {}
    public record ReplyTrace(int reply, double value, List<Turn> turns) {}
    public record Trace(Variant variant, int root, int target, Score score, List<ReplyTrace> replies) {}
    private record NativeReply(double value, GameState finalState) {}

    private static final Class<?>[] ROLLOUT_TYPES = {
            GameState.class, int.class, int.class, int.class, int.class};
    private final RolloutBotV09 bot;
    private final GameState original;
    private final int me;
    private final Params params;
    private final Plan selected;

    private V09ContinuationProbe(GameState state, int player) throws Exception {
        require(player == 0 || player == 1, "Invalid player");
        original = state.copy(); me = player; params = BotFactory.parameters(SPEC);
        bot = (RolloutBotV09) BotFactory.create(SPEC);
        bot.init(original.copy(), me);
        selected = accepted(original, me, bot.play(original, me, AMPLE_MS));
        require(!timer().up(), "Ample frozen-v09 preparation expired");
        require(bot.selectedOpening || bot.ordinarySearchComplete,
                "Incomplete ordinary search under ample budget");
        ReplyProbe.sameState(state, original);
    }

    public static V09ContinuationProbe prepare(GameState state, int me) throws Exception {
        return new V09ContinuationProbe(state, me);
    }
    public int ownCount() throws Exception { return counts()[me]; }
    public int opponentCount() throws Exception { return counts()[1 - me]; }
    public int replies() throws Exception { return Math.max(1, Math.min(params.ROLLOUT_REPLIES, opponentCount())); }
    public int waitRoot() throws Exception { return ownCount(); }
    public Plan selected() { return new Plan(selected.cells.clone(), selected.target, selected.action); }
    public Params parameters() { return params.copy(); }
    public boolean selectedOpening() { return bot.selectedOpening; }
    public boolean openingSelected() { return bot.selectedOpening; }
    public boolean ordinaryComplete() { return bot.ordinarySearchComplete; }
    public boolean networkComplete() { return bot.networkPhaseComplete; }
    public double selectedValue() { return bot.selectedValue; }

    /** All constructed roots; duplicate ordinary roots are retained but labelled. */
    public List<Root> roots() throws Exception {
        require(!bot.selectedOpening, "Protected opening bypassed native root construction");
        int ordinary = (int) field("ordinaryRootSize"), size = ordinary + bot.networkRoots;
        int[][] roots = (int[][]) field("roots"); int[] n = (int[]) field("rootCounts");
        int[] targets = (int[]) field("rootTargets");
        List<Root> result = new ArrayList<>();
        for (int root = 0; root < size; root++) {
            boolean duplicate = false;
            for (int j = 0; j < root && root < ordinary; j++)
                if (ReplyProbe.sameCells(Arrays.copyOf(roots[root], n[root]), Arrays.copyOf(roots[j], n[j])))
                    duplicate = true;
            result.add(new Root(root, Arrays.copyOf(roots[root], n[root]), targets[root], duplicate));
        }
        return List.copyOf(result);
    }

    /** The unsuppressed (root, selected target / no disruption) native search comparisons. */
    public List<Tuple> tuples() throws Exception {
        List<Tuple> result = new ArrayList<>();
        for (Root root : roots()) if (!root.suppressed) {
            result.add(new Tuple(root.index, root.target, root.cells.clone()));
            if (root.target >= 0) result.add(new Tuple(root.index, -1, root.cells.clone()));
        }
        return List.copyOf(result);
    }

    /** Exact chooser on the pre-turn state plus only these legal own proposed cells. */
    public TargetInfo targetInfo(int[] cells) throws Exception {
        accepted(original, me, actions(original, cells, -1));
        try (Context saved = new Context()) {
            timer().start(AMPLE_MS);
            SearchState board = new SearchState(original);
            placeOwn(board, me, cells);
            int target = (int) call("chooseTarget", new Class<?>[]{GameState.class, int.class}, board.state, me);
            require(!timer().up(), "Ample target inspection expired");
            return new TargetInfo(target, ((int[]) field("shortlist")).clone());
        }
    }

    public Score invokeOriginal(int root, int target) throws Exception {
        requireRoot(root, target);
        double[] values = new double[replies()];
        for (int reply = 0; reply < values.length; reply++) values[reply] = nativeReply(root, target, reply).value;
        return aggregate(values);
    }

    /** Each baseline trace is independently matched to the actual private rollout's scalar
     * AND final full core state. Counterfactuals change only the named continuation behaviour. */
    public Trace trace(int root, int target, Variant variant) throws Exception {
        requireRoot(root, target);
        int[][] roots = (int[][]) field("roots"); int[] rootCounts = (int[]) field("rootCounts");
        int[] rootCells = Arrays.copyOf(roots[root], rootCounts[root]);
        if (variant == Variant.NO_FUTURE_OWN_DISRUPT)
            require(ownCount() == 0 && root == waitRoot() && rootCells.length == 0 && target == -1,
                    "No-future-own-disrupt requires an empty portfolio and the no-attack WAIT root");
        require(variant != null, "Missing trace variant");
        List<ReplyTrace> traces = new ArrayList<>(); double[] values = new double[replies()];
        try (Context saved = new Context()) {
            timer().start(AMPLE_MS);
            for (int reply = 0; reply < values.length; reply++) {
                GameState state = original.copy(); Pathing pathing = new Pathing(state.size);
                List<Turn> turns = new ArrayList<>();
                int depth = Math.min(params.ROLLOUT_DEPTH, original.turnsLeft());
                int ownFirst = root < ownCount() ? root : 0;
                boolean terminal = false;
                for (int offset = 0; offset < depth; offset++) {
                    int[][] cells = new int[2][]; int[] targets = {-1, -1}, proposed = {-1, -1};
                    int[] beforeIncome = new int[2]; Rules.incomePerTurn(state, beforeIncome);
                    for (int player = 0; player < 2; player++) {
                        if (player == me && offset == 0) {
                            cells[player] = rootCells.clone(); targets[player] = proposed[player] = target;
                        } else if (player != me && variant == Variant.PASSIVE_OPPONENT) {
                            cells[player] = new int[0];
                        } else {
                            int[] out = new int[GameState.PAINT_PER_TURN];
                            int n = (int) call("fillBuild", new Class<?>[]{GameState.class, int.class, int.class, int[].class},
                                    state, player, player == me ? ownFirst : reply, out);
                            cells[player] = Arrays.copyOf(out, n);
                            SearchState board = new SearchState(state); placeOwn(board, player, cells[player]);
                            proposed[player] = (int) call("chooseTarget", new Class<?>[]{GameState.class, int.class}, board.state, player);
                            targets[player] = player == me && variant == Variant.NO_FUTURE_OWN_DISRUPT ? -1 : proposed[player];
                        }
                    }
                    require(!timer().up(), "Ample continuation trace expired");
                    int turn = state.turn;
                    Rules.TurnResult resolved = Rules.resolve(state, actions(state, cells[0], targets[0]),
                            actions(state, cells[1], targets[1]), pathing);
                    int[] paint = new int[2];
                    for (int player = 0; player < 2; player++) {
                        Rules.PlayerMoves accepted = resolved.moves[player];
                        require(accepted.error == null && accepted.skipped.isEmpty() && !resolved.unknownActions,
                                "Native continuation emitted an illegal action");
                        require(Arrays.equals(cells[player], Arrays.copyOf(accepted.cells, accepted.count)),
                                "Core changed the generated construction");
                        require(targets[player] == (accepted.disrupts.isEmpty() ? -1 : accepted.disrupts.get(0)),
                                "Core changed the generated disruption");
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
                if (variant == Variant.BASELINE) {
                    NativeReply nativeResult = nativeReply(root, target, reply);
                    require(Double.doubleToLongBits(values[reply]) == Double.doubleToLongBits(nativeResult.value),
                            "Trace scalar differs from frozen rollout");
                    ReplyProbe.sameState(nativeResult.finalState, state);
                }
                traces.add(new ReplyTrace(reply, values[reply], List.copyOf(turns)));
            }
        }
        return new Trace(variant, root, target, aggregate(values), List.copyOf(traces));
    }

    private NativeReply nativeReply(int root, int target, int reply) throws Exception {
        try (Context saved = new Context()) {
            timer().start(AMPLE_MS);
            double value = (double) call("rollout", ROLLOUT_TYPES, original, me, root, target, reply);
            require(Double.isFinite(value) && !timer().up(), "Incomplete native ample rollout");
            return new NativeReply(value, ((SearchState) field("work")).state.copy());
        }
    }

    /** The frozen bot's heuristic only: game income and termination are obtained from core. */
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
    private void requireRoot(int root, int target) throws Exception {
        require(target >= -1, "Invalid disruption target sentinel");
        Root found = null;
        for (Root candidate : roots()) if (candidate.index == root) found = candidate;
        require(found != null, "Unknown prepared root");
        accepted(original, me, actions(original, found.cells, target));
    }
    private static void placeOwn(SearchState board, int player, int[] cells) {
        int[] empty = new int[0];
        Rules.applyPlacements(board.state, player == 0 ? cells : empty, player == 0 ? cells.length : 0,
                player == 1 ? cells : empty, player == 1 ? cells.length : 0);
        board.refresh();
    }
    static Plan accepted(GameState state, int player, List<Action> actions) {
        Rules.TurnResult tr = new Rules.TurnResult();
        Rules.expand(state, player, actions, new Pathing(state.size), tr.moves[player], tr);
        Rules.PlayerMoves moves = tr.moves[player];
        require(moves.error == null && moves.skipped.isEmpty() && !tr.unknownActions && moves.disrupts.size() <= 1,
                "Noncanonical diagnostic plan");
        return new Plan(Arrays.copyOf(moves.cells, moves.count), moves.disrupts.isEmpty() ? -1 : moves.disrupts.get(0),
                Action.format(actions));
    }
    private static List<Action> actions(GameState state, int[] cells, int target) {
        List<Action> actions = new ArrayList<>();
        for (int cell : cells) actions.add(Action.place(state.x(cell), state.y(cell)));
        if (target >= 0) actions.add(Action.disrupt(target));
        if (actions.isEmpty()) actions.add(Action.waitAction());
        return actions;
    }
    private int[] counts() throws Exception { return (int[]) field("counts"); }
    private Timer timer() throws Exception { return (Timer) field("timer"); }
    private Object field(String name) throws Exception {
        Field field = RolloutBotV09.class.getDeclaredField(name); field.setAccessible(true); return field.get(bot);
    }
    private Object call(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = RolloutBotV09.class.getDeclaredMethod(name, types); method.setAccessible(true);
        return method.invoke(bot, args);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    /** Preserve all decision fields/arrays, dynamic work boards and clock around reflective calls.
     * Pathing scratch buffers are implementation workspace; every called public search resets it.
     * Frozen default-off history/replan flags ensure these calls never mutate RNG or live history. */
    private final class Context implements AutoCloseable {
        private final List<Field> fields = new ArrayList<>();
        private final List<Object> values = new ArrayList<>();
        private final List<SearchState> boards = new ArrayList<>();
        private final List<GameState> states = new ArrayList<>();
        private final long start, deadline;
        Context() throws Exception {
            for (Field field : RolloutBotV09.class.getDeclaredFields()) {
                field.setAccessible(true); Object value = field.get(bot);
                if (field.getType().isPrimitive() || field.getType().isArray()) {
                    fields.add(field); values.add(cloneArray(value));
                } else if (value instanceof SearchState board) { boards.add(board); states.add(board.state.copy()); }
            }
            start = clock("startNs", null); deadline = clock("deadlineNs", null);
        }
        public void close() throws Exception {
            for (int i = 0; i < fields.size(); i++) {
                Field field = fields.get(i); Object value = values.get(i);
                if (field.getType().isArray()) restoreArray(field.get(bot), value); else field.set(bot, value);
            }
            for (int i = 0; i < boards.size(); i++) boards.get(i).set(states.get(i));
            clock("startNs", start); clock("deadlineNs", deadline);
        }
    }
    private long clock(String name, Long replacement) throws Exception {
        Field field = Timer.class.getDeclaredField(name); field.setAccessible(true);
        long value = field.getLong(timer()); if (replacement != null) field.setLong(timer(), replacement); return value;
    }
    private static Object cloneArray(Object value) {
        if (value == null || !value.getClass().isArray()) return value;
        int n = Array.getLength(value); Object copy = Array.newInstance(value.getClass().getComponentType(), n);
        if (value.getClass().getComponentType().isPrimitive()) System.arraycopy(value, 0, copy, 0, n);
        else for (int i = 0; i < n; i++) Array.set(copy, i, cloneArray(Array.get(value, i)));
        return copy;
    }
    private static void restoreArray(Object original, Object saved) {
        if (original == null && saved == null) return;
        require(original != null && saved != null && Array.getLength(original) == Array.getLength(saved),
                "Reflective diagnostic changed array shape");
        int n = Array.getLength(saved);
        if (saved.getClass().getComponentType().isPrimitive()) System.arraycopy(saved, 0, original, 0, n);
        else for (int i = 0; i < n; i++) restoreArray(Array.get(original, i), Array.get(saved, i));
    }
}
