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

/**
 * {@code neural-v1}: an imitation policy learned from the arena leader's replays instead of a
 * search. Two small conditional-logit networks ({@link NeuralNet}) score engine-grounded
 * candidate rows from {@link NeuralFeatures}: one picks the DISRUPT region among all legal
 * targets, the other picks placement cells one at a time until the paint is spent, each time on
 * the board that already contains this turn's earlier placements.
 *
 * <p>Main logic differs from every EvalBot/RolloutBot version: no route portfolio, no rollout,
 * no hand-written valuation; the decision rules are the trained weights. Two hard rules remain
 * because the leader's own losses come from missing them: never take a closing ink while behind,
 * always take one that wins outright ({@code USE_CLOSE}). {@code NOISE}/{@code SEED} only break
 * self-play mirrors with a small additive logit perturbation.
 */
public final class NeuralBot implements Bot {

    /** Extra lead required before we accept an ink that ends the game (simultaneous placements can still score). */
    public static final int CLOSE_MARGIN = 6;
    /** Do not start another placement (route recomputation + candidate scoring) with less time left. */
    static final int PLACEMENT_RESERVE_MS = 6;

    private final Params p;
    private final Timer timer = new Timer();
    private NeuralFeatures features;
    private NeuralNet sabotageNet, buildNet;
    private Pathing paths;
    private GameState work;
    private int[] candRegions, candCells;
    private float[][] sabotageX, buildX;
    private Random random;
    /** Diagnostics for tests/tools: last turn's chosen target, candidate counts and placements. */
    public int lastTarget = -1, lastSabotageCandidates, lastBuildCandidates, lastPlacements;

    public NeuralBot(Params params) {
        p = params.copy();
        if (!Double.isFinite(p.NOISE) || p.NOISE < 0) throw new IllegalArgumentException("Invalid NOISE");
        sabotageNet = new NeuralNet(NeuralWeights.SABOTAGE_SIZES, NeuralWeights.SABOTAGE);
        buildNet = new NeuralNet(NeuralWeights.BUILD_SIZES, NeuralWeights.BUILD);
        if (sabotageNet.inputs() != NeuralFeatures.SABOTAGE_F || buildNet.inputs() != NeuralFeatures.BUILD_F)
            throw new IllegalStateException("Trained weights do not match the feature definition");
    }

    public String name() {
        return "neural-v1";
    }

    public void init(GameState s, int me) {
        features = new NeuralFeatures(s);
        paths = new Pathing(s.size);
        work = s.copy();
        candRegions = new int[s.regionCount];
        candCells = new int[s.size];
        sabotageX = new float[s.regionCount][NeuralFeatures.SABOTAGE_F];
        buildX = new float[s.size][NeuralFeatures.BUILD_F];
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
                if (best < 0 || v > bestScore) {
                    best = i;
                    bestScore = v;
                }
            }
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

    /** Highest-scoring legal region; closing inks are forced when they win and refused otherwise. */
    private int chooseTarget(GameState s, int me) {
        int n = features.sabotageCandidates(s, me, candRegions, sabotageX);
        lastSabotageCandidates = n;
        int best = -1;
        double bestScore = 0;
        int opp = 1 - me;
        for (int i = 0; i < n; i++) {
            int r = candRegions[i];
            if (p.USE_CLOSE && features.closes[r]) {
                if (features.winsIfClosed[r] && s.score[me] - s.score[opp] >= CLOSE_MARGIN) return r;
                continue;
            }
            double v = sabotageNet.score(sabotageX[i]) + noise();
            if (best < 0 || v > bestScore) {
                best = i;
                bestScore = v;
            }
        }
        return best < 0 ? -1 : candRegions[best];
    }

    private double noise() {
        return p.NOISE == 0 ? 0 : p.NOISE * random.nextGaussian();
    }

    /** Warm the JIT on a disposable instance so the live per-game memory stays untouched. */
    @Override
    public void warmup(GameState s, int me, long ms) {
        NeuralBot disposable = new NeuralBot(p);
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
