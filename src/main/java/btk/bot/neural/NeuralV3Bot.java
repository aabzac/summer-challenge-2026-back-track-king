package btk.bot.neural;

import btk.bot.Bot;
import btk.bot.Params;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.LongSupplier;

/** V2 weights and greedy decisions with cooperative interruption of incomplete feature/score passes. */
public final class NeuralV3Bot implements Bot {
    public static final int CLOSE_MARGIN = 6;
    static final int PLACEMENT_RESERVE_MS = 6;
    public static final int INITIAL = 1, SABOTAGE_FEATURES = 2, SABOTAGE_SCORES = 3;
    public static final int BUILD_PREPARE = 4, BUILD_FEATURES = 5, BUILD_SCORES = 6, BUILD_COMMIT = 7;
    private static final int ABORTED = -2;

    private final Params p;
    private final LongSupplier clock;
    private final NeuralV3Features.Deadline deadline;
    private final NeuralNet sabotageNet, buildNet;
    private NeuralV3Features features;
    private Pathing paths;
    private GameState work;
    private int[] candRegions, candCells;
    private float[][] sabotageX, buildX;
    private Random random;
    private long startNs, budgetNs, featureBudgetNs;
    public int lastTarget = -1, lastSabotageCandidates, lastBuildCandidates, lastPlacements;
    /** Diagnostics only; lets tests place an injected deadline inside a real work phase. */
    public int deadlinePhase, lastAbortedPhase;

    public NeuralV3Bot(Params params) { this(params, System::nanoTime); }

    public NeuralV3Bot(Params params, LongSupplier clock) {
        p = params.copy();
        if (!Double.isFinite(p.NOISE) || p.NOISE < 0) throw new IllegalArgumentException("Invalid NOISE");
        if (p.NEURAL_DEADLINE_RESERVE_MS < 0) throw new IllegalArgumentException("Negative neural deadline reserve");
        if (clock == null) throw new IllegalArgumentException("Clock is required");
        this.clock = clock;
        deadline = this::expired;
        sabotageNet = new NeuralNet(NeuralV2Weights.SABOTAGE_SIZES, NeuralV2Weights.SABOTAGE);
        buildNet = new NeuralNet(NeuralV2Weights.BUILD_SIZES, NeuralV2Weights.BUILD);
        if (sabotageNet.inputs() != NeuralV3Features.SABOTAGE_F || buildNet.inputs() != NeuralV3Features.BUILD_F)
            throw new IllegalStateException("Weights do not match the frozen feature definition");
    }

    public String name() { return "neural-v3"; }

    public void init(GameState s, int me) {
        features = new NeuralV3Features(s);
        paths = new Pathing(s.size);
        work = s.copy();
        candRegions = new int[s.regionCount]; candCells = new int[s.size];
        sabotageX = new float[s.regionCount][NeuralV3Features.SABOTAGE_F];
        buildX = new float[s.size][NeuralV3Features.BUILD_F];
        random = new Random(p.SEED * 1_000_003L + me * 7919L);
    }

    public List<Action> play(GameState s, int me, long budgetMs) {
        deadlinePhase = INITIAL;
        startNs = clock.getAsLong();
        budgetNs = nanos(Math.max(0, budgetMs));
        featureBudgetNs = nanos(Math.max(0, Math.max(0, budgetMs) - p.NEURAL_DEADLINE_RESERVE_MS));
        lastTarget = -1; lastPlacements = 0; lastSabotageCandidates = 0; lastBuildCandidates = 0; lastAbortedPhase = 0;
        List<Action> actions = new ArrayList<>(4);
        // Public-board memory must advance even when this turn cannot afford feature work.
        features.observe(s, me);
        if (budgetMs <= 0 || expired() || !features.prepare(s, me, true, deadline) || expired()) return abortTurn(actions);
        int target = p.USE_DISRUPT ? chooseTarget(s, me) : -1;
        if (target == ABORTED) return abortTurn(actions);
        features.recordTarget(target);
        lastTarget = target;
        work.copyFrom(s);
        int paint = GameState.PAINT_PER_TURN;
        for (int k = 0; paint > 0; k++) {
            deadlinePhase = BUILD_PREPARE;
            if (expired()) { lastAbortedPhase = deadlinePhase; break; }
            if (k > 0 && leftMs() < PLACEMENT_RESERVE_MS) break;
            if (k > 0 && !features.prepare(work, me, false, deadline)) { lastAbortedPhase = deadlinePhase; break; }
            if (expired()) { lastAbortedPhase = deadlinePhase; break; }
            deadlinePhase = BUILD_FEATURES;
            int n = features.buildCandidates(work, me, paint, target, k, candCells, buildX, deadline);
            if (n < 0 || expired()) { lastAbortedPhase = deadlinePhase; break; }
            if (k == 0) lastBuildCandidates = n;
            if (n == 0) break;
            deadlinePhase = BUILD_SCORES;
            int best = -1;
            double bestScore = 0;
            for (int i = 0; i < n; i++) {
                if (expired()) { best = ABORTED; break; }
                double value = buildNet.score(buildX[i]) + noise();
                if (best < 0 || value > bestScore) { best = i; bestScore = value; }
            }
            if (best == ABORTED || expired()) { lastAbortedPhase = deadlinePhase; break; }
            deadlinePhase = BUILD_COMMIT;
            int cell = candCells[best];
            paint -= work.placeCost(cell);
            work.owner[cell] = me;
            features.markPlaced(cell);
            Rules.updateConnections(work, paths);
            actions.add(Action.place(work.x(cell), work.y(cell)));
            lastPlacements++;
        }
        if (target >= 0) actions.add(Action.disrupt(target));
        if (actions.isEmpty()) actions.add(Action.waitAction());
        if (p.DEBUG) System.err.println("t" + s.turn + " target=" + target + " placements=" + lastPlacements
                + " candidates=" + lastBuildCandidates + " abort=" + lastAbortedPhase);
        deadlinePhase = 0;
        return actions;
    }

    private int chooseTarget(GameState s, int me) {
        deadlinePhase = SABOTAGE_FEATURES;
        int n = features.sabotageCandidates(s, me, candRegions, sabotageX, deadline);
        if (n < 0 || expired()) return ABORTED;
        lastSabotageCandidates = n;
        deadlinePhase = SABOTAGE_SCORES;
        int best = -1;
        double bestScore = 0;
        for (int i = 0; i < n; i++) {
            if (expired()) return ABORTED;
            int region = candRegions[i];
            if (p.USE_CLOSE && features.closes[region]) {
                if (features.winsIfClosed[region] && s.score[me] - s.score[1 - me] >= CLOSE_MARGIN)
                    return expired() ? ABORTED : region;
                continue;
            }
            double value = sabotageNet.score(sabotageX[i]) + noise();
            if (best < 0 || value > bestScore) { best = i; bestScore = value; }
        }
        if (expired()) return ABORTED;
        return best < 0 ? -1 : candRegions[best];
    }

    private List<Action> abortTurn(List<Action> actions) {
        lastAbortedPhase = deadlinePhase;
        features.recordTarget(-1);
        lastTarget = -1;
        actions.add(Action.waitAction());
        deadlinePhase = 0;
        return actions;
    }

    private boolean expired() { return clock.getAsLong() - startNs >= featureBudgetNs; }
    private long leftMs() { return (budgetNs - (clock.getAsLong() - startNs)) / 1_000_000L; }
    private static long nanos(long ms) { return ms > Long.MAX_VALUE / 1_000_000L ? Long.MAX_VALUE : ms * 1_000_000L; }
    // An interrupted score pass discards its choice but retains any RNG draws already consumed.
    private double noise() { return p.NOISE == 0 ? 0 : p.NOISE * random.nextGaussian(); }

    @Override public void warmup(GameState s, int me, long ms) {
        NeuralV3Bot disposable = new NeuralV3Bot(p);
        GameState scratch = s.copy();
        disposable.init(scratch, me);
        long end = System.nanoTime() + ms * 1_000_000L;
        int iterations = 0;
        while (System.nanoTime() < end) {
            scratch.copyFrom(s);
            scratch.turn = Math.min(s.maxTurns - 1, s.turn + iterations++);
            disposable.play(scratch, me, 5);
        }
    }
}
