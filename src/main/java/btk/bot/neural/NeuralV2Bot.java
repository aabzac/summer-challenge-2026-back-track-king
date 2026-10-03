package btk.bot.neural;

import btk.bot.Bot;
import btk.bot.Params;
import btk.bot.Timer;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Outcome fine-tuning candidate. The v1 feature sequence, masks and hard guards are preserved. */
public final class NeuralV2Bot implements Bot {
    public static final int CLOSE_MARGIN = 6;
    static final int PLACEMENT_RESERVE_MS = 6;

    /**
     * Optional offline sampling hook, absent from normal inference. Invoked only when at least
     * two actions remain after hard guards. Return an offset in [0,count), not a row index.
     * Buffers are borrowed for this call only; logits are aligned to offsets, rows use indices.
     * Forced closing actions, singleton sets and WAIT never reach this hook.
     */
    public interface DecisionChooser {
        int choose(boolean sabotage, int turn, int k, float[][] rows, int[] indices,
                   double[] logits, int count);
    }

    private final Params p;
    private final Timer timer = new Timer();
    private final NeuralNet sabotageNet, buildNet;
    private final DecisionChooser chooser;
    private NeuralFeatures features;
    private Pathing paths;
    private GameState work;
    private int[] candRegions, candCells, allowed;
    private float[][] sabotageX, buildX;
    private double[] logits;
    private Random random;
    public int lastTarget = -1, lastSabotageCandidates, lastBuildCandidates, lastPlacements;

    public NeuralV2Bot(Params params) {
        this(params, new NeuralNet(NeuralV2Weights.SABOTAGE_SIZES, NeuralV2Weights.SABOTAGE),
                new NeuralNet(NeuralV2Weights.BUILD_SIZES, NeuralV2Weights.BUILD), null);
    }

    public NeuralV2Bot(Params params, NeuralNet sabotage, NeuralNet build, DecisionChooser chooser) {
        p = params.copy();
        if (!Double.isFinite(p.NOISE) || p.NOISE < 0) throw new IllegalArgumentException("Invalid NOISE");
        sabotageNet = sabotage;
        buildNet = build;
        this.chooser = chooser;
        if (sabotageNet.inputs() != NeuralFeatures.SABOTAGE_F || buildNet.inputs() != NeuralFeatures.BUILD_F)
            throw new IllegalStateException("Trained weights do not match the feature definition");
    }

    public String name() { return "neural-v2"; }

    public void init(GameState s, int me) {
        features = new NeuralFeatures(s);
        paths = new Pathing(s.size);
        work = s.copy();
        candRegions = new int[s.regionCount];
        candCells = new int[s.size];
        sabotageX = new float[s.regionCount][NeuralFeatures.SABOTAGE_F];
        buildX = new float[s.size][NeuralFeatures.BUILD_F];
        allowed = new int[Math.max(s.size, s.regionCount)];
        logits = new double[allowed.length];
        random = new Random(p.SEED * 1_000_003L + me * 7919L);
    }

    public List<Action> play(GameState s, int me, long budgetMs) {
        timer.start(budgetMs);
        List<Action> actions = new ArrayList<>(4);
        if (budgetMs <= 0) {
            actions.add(Action.waitAction());
            return actions;
        }
        features.observe(s, me);
        features.prepare(s, me, true);
        int target = p.USE_DISRUPT ? chooseTarget(s, me) : -1;
        features.recordTarget(target);
        lastTarget = target;

        work.copyFrom(s);
        int paint = GameState.PAINT_PER_TURN;
        lastPlacements = 0;
        lastBuildCandidates = 0;
        for (int k = 0; paint > 0 && (k == 0 || timer.leftMs() >= PLACEMENT_RESERVE_MS); k++) {
            if (k > 0) features.prepare(work, me, false);
            int n = features.buildCandidates(work, me, paint, target, k, candCells, buildX);
            if (k == 0) lastBuildCandidates = n;
            if (n == 0) break;
            int best = -1;
            double bestScore = 0;
            for (int i = 0; i < n; i++) {
                double v = buildNet.score(buildX[i]) + noise();
                if (chooser != null) { allowed[i] = i; logits[i] = v; }
                if (best < 0 || v > bestScore) {
                    best = i;
                    bestScore = v;
                }
            }
            if (chooser != null && n > 1) best = chosen(false, s.turn, k, buildX, n);
            int c = candCells[best];
            paint -= work.placeCost(c);
            work.owner[c] = me;
            features.markPlaced(c);
            Rules.updateConnections(work, paths);
            actions.add(Action.place(work.x(c), work.y(c)));
            lastPlacements++;
        }
        if (target >= 0) actions.add(Action.disrupt(target));
        if (actions.isEmpty()) actions.add(Action.waitAction());
        if (p.DEBUG) System.err.println("t" + s.turn + " target=" + target + " placements=" + lastPlacements
                + " candidates=" + lastBuildCandidates + " ms=" + timer.elapsedMs());
        return actions;
    }

    private int chooseTarget(GameState s, int me) {
        int n = features.sabotageCandidates(s, me, candRegions, sabotageX);
        lastSabotageCandidates = n;
        int best = -1, count = 0;
        double bestScore = 0;
        int opp = 1 - me;
        for (int i = 0; i < n; i++) {
            int r = candRegions[i];
            if (p.USE_CLOSE && features.closes[r]) {
                if (features.winsIfClosed[r] && s.score[me] - s.score[opp] >= CLOSE_MARGIN) return r;
                continue;
            }
            double v = sabotageNet.score(sabotageX[i]) + noise();
            if (chooser != null) { allowed[count] = i; logits[count] = v; count++; }
            if (best < 0 || v > bestScore) {
                best = i;
                bestScore = v;
            }
        }
        if (chooser != null && count > 1) best = chosen(true, s.turn, 0, sabotageX, count);
        return best < 0 ? -1 : candRegions[best];
    }

    private int chosen(boolean sabotage, int turn, int k, float[][] rows, int count) {
        int offset = chooser.choose(sabotage, turn, k, rows, allowed, logits, count);
        if (offset < 0 || offset >= count) throw new IllegalStateException("Chooser selected an unavailable candidate");
        return allowed[offset];
    }

    private double noise() { return p.NOISE == 0 ? 0 : p.NOISE * random.nextGaussian(); }

    /** The disposable greedy instance never records or samples training decisions. */
    @Override public void warmup(GameState s, int me, long ms) {
        NeuralV2Bot disposable = new NeuralV2Bot(p, sabotageNet, buildNet, null);
        GameState scratch = s.copy();
        disposable.init(scratch, me);
        long end = System.nanoTime() + ms * 1_000_000L;
        int iterations = 0;
        while (System.nanoTime() < end) {
            scratch.copyFrom(s);
            scratch.turn = Math.min(s.maxTurns - 1, s.turn + iterations);
            disposable.play(scratch, me, 5);
            iterations++;
        }
    }
}
