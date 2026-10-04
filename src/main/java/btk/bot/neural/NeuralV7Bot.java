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

/** Frozen V6 policy with a bounded extension only after an interrupted neural turn. */
public final class NeuralV7Bot implements Bot {
    public static final int CLOSE_MARGIN = 6;
    static final int PLACEMENT_RESERVE_MS = 6;
    public static final int INITIAL = 1, SABOTAGE_FEATURES = 2, SABOTAGE_SCORES = 3;
    public static final int BUILD_PREPARE = 4, BUILD_FEATURES = 5, BUILD_SCORES = 6, BUILD_COMMIT = 7;
    public static final int FALLBACK_PREPARE = 8, FALLBACK_SCAN = 9, FALLBACK_COMMIT = 10;
    private static final int ABORTED = -2;

    private final Params p;
    private final LongSupplier clock;
    private final NeuralV6Features.Deadline deadline;
    private final NeuralNet sabotageNet, buildNet;
    private NeuralV6Features features;
    private Pathing paths;
    private GameState work;
    private int[] candRegions, candCells;
    private byte[] fillMark;
    private boolean[] protectedRegion;
    private float[][] sabotageX, buildX;
    private Random random;
    private long startNs, budgetNs, featureBudgetNs;
    public int lastTarget = -1, lastSabotageCandidates, lastBuildCandidates, lastPlacements;
    /** Diagnostics only; lets tests place an injected deadline inside a real work phase. */
    public int deadlinePhase, lastAbortedPhase, lastFallbackPlacements, lastFallbackPaint;

    public NeuralV7Bot(Params params) { this(params, System::nanoTime); }

    public NeuralV7Bot(Params params, LongSupplier clock) {
        p = params.copy();
        if (!Double.isFinite(p.NOISE) || p.NOISE < 0) throw new IllegalArgumentException("Invalid NOISE");
        if (p.NEURAL_DEADLINE_RESERVE_MS < 0) throw new IllegalArgumentException("Negative neural deadline reserve");
        if (p.NEURAL_FALLBACK_MAX_INSTABILITY < 0) throw new IllegalArgumentException("Negative fallback instability");
        if (clock == null) throw new IllegalArgumentException("Clock is required");
        this.clock = clock;
        deadline = this::expired;
        sabotageNet = new NeuralNet(NeuralV2Weights.SABOTAGE_SIZES, NeuralV2Weights.SABOTAGE);
        buildNet = new NeuralNet(NeuralV2Weights.BUILD_SIZES, NeuralV2Weights.BUILD);
        if (sabotageNet.inputs() != NeuralV6Features.SABOTAGE_F || buildNet.inputs() != NeuralV6Features.BUILD_F)
            throw new IllegalStateException("Weights do not match the frozen feature definition");
    }

    public String name() { return "neural-v7"; }

    public void init(GameState s, int me) {
        features = new NeuralV6Features(s);
        paths = new Pathing(s.size);
        work = s.copy();
        candRegions = new int[s.regionCount]; candCells = new int[s.size];
        sabotageX = new float[s.regionCount][NeuralV6Features.SABOTAGE_F];
        buildX = new float[s.size][NeuralV6Features.BUILD_F];
        fillMark = new byte[s.size];
        protectedRegion = new boolean[s.regionCount];
        for (int r = 0; r < s.regionCount; r++) protectedRegion[r] = s.regionHasTown(r);
        random = new Random(p.SEED * 1_000_003L + me * 7919L);
    }

    public List<Action> play(GameState s, int me, long budgetMs) {
        deadlinePhase = INITIAL;
        startNs = clock.getAsLong();
        budgetNs = nanos(Math.max(0, budgetMs));
        featureBudgetNs = nanos(Math.max(0, Math.max(0, budgetMs) - p.NEURAL_DEADLINE_RESERVE_MS));
        lastTarget = -1; lastPlacements = 0; lastSabotageCandidates = 0; lastBuildCandidates = 0; lastAbortedPhase = 0;
        lastFallbackPlacements = lastFallbackPaint = 0;
        List<Action> actions = new ArrayList<>(4);
        // Public-board memory must advance even when this turn cannot afford feature work.
        features.observe(s, me);
        if (budgetMs <= 0 || expired() || !features.prepare(s, me, true, deadline) || expired()) return abortTurn(s, me, actions);
        int target = p.USE_DISRUPT ? chooseTarget(s, me) : -1;
        if (target == ABORTED) return abortTurn(s, me, actions);
        features.recordTarget(target);
        lastTarget = target;
        work.copyFrom(s);
        int paint = GameState.PAINT_PER_TURN;
        boolean interrupted = false;
        for (int k = 0; paint > 0; k++) {
            deadlinePhase = BUILD_PREPARE;
            if (expired()) { lastAbortedPhase = deadlinePhase; interrupted = true; break; }
            if (k > 0 && leftMs() < PLACEMENT_RESERVE_MS) { interrupted = true; break; }
            if (k > 0 && !features.prepare(work, me, false, deadline)) { lastAbortedPhase = deadlinePhase; interrupted = true; break; }
            if (expired()) { lastAbortedPhase = deadlinePhase; interrupted = true; break; }
            deadlinePhase = BUILD_FEATURES;
            int n = features.buildCandidates(work, me, paint, target, k, candCells, buildX, deadline);
            if (n < 0 || expired()) { lastAbortedPhase = deadlinePhase; interrupted = true; break; }
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
            if (best == ABORTED || expired()) { lastAbortedPhase = deadlinePhase; interrupted = true; break; }
            deadlinePhase = BUILD_COMMIT;
            int cell = candCells[best];
            paint -= work.placeCost(cell);
            work.owner[cell] = me;
            features.markPlaced(cell);
            Rules.updateConnections(work, paths);
            actions.add(Action.place(work.x(cell), work.y(cell)));
            lastPlacements++;
        }
        if (interrupted) fill(work, me, paint, target, actions);
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

    private List<Action> abortTurn(GameState s, int me, List<Action> actions) {
        lastAbortedPhase = deadlinePhase;
        features.recordTarget(-1);
        lastTarget = -1;
        fill(s, me, GameState.PAINT_PER_TURN, -1, actions);
        if (actions.isEmpty()) actions.add(Action.waitAction());
        deadlinePhase = 0;
        return actions;
    }

    // Only board data is read: an interrupted feature pass can leave every feature buffer partial.
    private void fill(GameState s, int me, int paint, int target, List<Action> actions) {
        if (!p.USE_NEURAL_FALLBACK || paint <= 0 || hardExpired()) return;
        deadlinePhase = FALLBACK_PREPARE;
        for (int c = 0; c < s.size; c++) {
            if (hardExpired()) return;
            fillMark[c] = (byte) (s.owner[c] == me ? 1 : 0);
        }
        for (int q = 0; q < s.pairCount; q++) {
            if (hardExpired()) return;
            if (s.pairPath[q] == null) continue;
            for (int i = 0; i < s.pairPathLen[q]; i++) {
                if (hardExpired()) return;
                int c = s.pairPath[q][i];
                if (fillMark[c] == 1) fillMark[c] = 3;
            }
        }
        while (paint > 0) {
            deadlinePhase = FALLBACK_SCAN;
            int best = -1, bestAdj = 0, bestTown = 0, bestCost = 0;
            for (int c = 0; c < s.size; c++) {
                if (hardExpired()) return;
                int r = s.regionId[c], cost = s.placeCost(c);
                if ((fillMark[c] & 1) != 0 || !s.canBuild(c) || cost > paint || r == target
                        || !protectedRegion[r] && s.regionInstability[r] > p.NEURAL_FALLBACK_MAX_INSTABILITY) continue;
                int adj = 0, town = protectedRegion[r] ? 1 : 0;
                boolean nearOwn = false;
                for (int d = 0; d < 4; d++) {
                    int x = s.x(c) + GameState.DX[d], y = s.y(c) + GameState.DY[d];
                    if (s.inBounds(x, y)) {
                        byte mark = fillMark[s.cell(x, y)];
                        if (mark != 0) nearOwn = true;
                        if (mark == 3) adj++;
                    }
                }
                if (!nearOwn) continue;
                if (best < 0 || adj > bestAdj || adj == bestAdj && (town > bestTown
                        || town == bestTown && cost < bestCost)) {
                    best = c; bestAdj = adj; bestTown = town; bestCost = cost;
                }
            }
            deadlinePhase = FALLBACK_COMMIT;
            if (best < 0 || hardExpired()) return;
            paint -= bestCost;
            fillMark[best] = 1;
            features.markPlaced(best);
            actions.add(Action.place(s.x(best), s.y(best)));
            lastPlacements++; lastFallbackPlacements++; lastFallbackPaint += bestCost;
        }
    }

    private boolean hardExpired() { return clock.getAsLong() - startNs >= budgetNs; }
    private boolean expired() { return clock.getAsLong() - startNs >= featureBudgetNs; }
    private long leftMs() { return (budgetNs - (clock.getAsLong() - startNs)) / 1_000_000L; }
    private static long nanos(long ms) { return ms > Long.MAX_VALUE / 1_000_000L ? Long.MAX_VALUE : ms * 1_000_000L; }
    // An interrupted score pass discards its choice but retains any RNG draws already consumed.
    private double noise() { return p.NOISE == 0 ? 0 : p.NOISE * random.nextGaussian(); }

    @Override public void warmup(GameState s, int me, long ms) {
        NeuralV7Bot disposable = new NeuralV7Bot(p);
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
