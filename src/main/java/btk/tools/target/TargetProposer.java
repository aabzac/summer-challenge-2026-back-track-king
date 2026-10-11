package btk.tools.target;

import btk.bot.neural.NeuralNet;
import btk.bot.neural.NeuralV2Weights;
import btk.core.GameState;
import java.util.Arrays;

/** Isolated deterministic V3 sabotage proposal; no production integration or construction inference.
 * A suggestion is not an emitted action. The caller must record its actual target, even after abort.
 * Debug buffers are reusable and meaningful only under their completion/availability flags. */
public final class TargetProposer {
    public static final int ABORTED = -2, PREPARE = 1, CANDIDATES = 2, SCORES = 3;
    public static final int CLOSE_MARGIN = 6;
    private final NeuralNet net;
    private final boolean useClose;
    public final TargetProposalFeatures features;
    public final int[] regions;
    public final float[][] rows;
    public final float[] scores;
    public final boolean[] scoreAvailable;
    public int lastCount, lastTarget = -1, lastPhase, lastAbortedPhase;
    public boolean lastComplete;

    public TargetProposer(GameState state, boolean useClose) {
        this.useClose = useClose;
        features = new TargetProposalFeatures(state);
        net = new NeuralNet(NeuralV2Weights.SABOTAGE_SIZES, NeuralV2Weights.SABOTAGE);
        if (net.inputs() != TargetProposalFeatures.SABOTAGE_F)
            throw new IllegalStateException("Frozen sabotage feature/weight mismatch");
        regions = new int[state.regionCount]; rows = new float[state.regionCount][TargetProposalFeatures.SABOTAGE_F];
        scores = new float[state.regionCount]; scoreAvailable = new boolean[state.regionCount];
    }

    public void observe(GameState state, int me) { features.observe(state, me); }
    public void recordTarget(int actualTarget) { features.recordTarget(actualTarget); }
    private static boolean expired(TargetProposalFeatures.Deadline d) { return d != null && d.expired(); }

    /** Return region, -1 for no eligible target, or ABORTED. Never records the proposed target. */
    public int propose(GameState state, int me, TargetProposalFeatures.Deadline deadline) {
        lastCount = 0; lastTarget = -1; lastComplete = false; lastAbortedPhase = 0;
        Arrays.fill(scoreAvailable, false); Arrays.fill(scores, 0);
        observe(state, me); // Memory advances even when the proposal budget is already exhausted.
        lastPhase = PREPARE;
        if (!features.prepare(state, me, deadline)) return abort();
        lastPhase = CANDIDATES;
        int count = features.candidates(state, me, regions, rows, deadline);
        if (count < 0) return abort();
        lastCount = count;
        lastPhase = SCORES;
        int best = -1;
        double bestScore = 0;
        for (int i = 0; i < count; i++) {
            if (expired(deadline)) return abort();
            int region = regions[i];
            if (useClose && features.closes[region]) {
                if (features.winsIfClosed[region] && state.score[me] - state.score[1 - me] >= CLOSE_MARGIN) {
                    if (expired(deadline)) return abort();
                    return complete(region);
                }
                continue;
            }
            float score = net.score(rows[i]);
            scores[i] = score; scoreAvailable[i] = true;
            if (best < 0 || score > bestScore) { best = i; bestScore = score; }
        }
        if (expired(deadline)) return abort();
        return complete(best < 0 ? -1 : regions[best]);
    }

    private int complete(int target) { lastComplete = true; lastTarget = target; lastPhase = 0; return target; }
    private int abort() { lastAbortedPhase = lastPhase; lastTarget = -1; lastComplete = false; return ABORTED; }
}
