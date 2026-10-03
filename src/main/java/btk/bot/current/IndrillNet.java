package btk.bot.current;

import btk.core.GameState;
import java.util.Arrays;

/** Two Q heads with one local mean block and global pooling; fixed model schema. */
public final class IndrillNet {
    public static final int HIDDEN = 24;
    private final float[][] weights;
    private float[][] h0, h1, region;
    private final float[] global = new float[HIDDEN], local = new float[HIDDEN];

    public IndrillNet() {
        this(new float[][] {IndrillWeights.W0, IndrillWeights.B0, IndrillWeights.WS,
                IndrillWeights.WN, IndrillWeights.WG, IndrillWeights.B1, IndrillWeights.WB,
                IndrillWeights.WI, IndrillWeights.BB, IndrillWeights.BI});
    }

    public IndrillNet(float[][] weights) {
        int[] sizes = {64 * 24, 24, 24 * 24, 24 * 24, 24 * 24, 24, 24, 24, 1, 1};
        if (weights.length != sizes.length) throw new IllegalArgumentException("Invalid model tensor count");
        for (int i = 0; i < sizes.length; i++) {
            if (weights[i].length != sizes[i]) throw new IllegalArgumentException("Invalid model shape");
            for (float w : weights[i]) if (!Float.isFinite(w)) throw new IllegalArgumentException("Non-finite model weight");
        }
        this.weights = weights;
    }

    public void init(GameState s) {
        h0 = new float[s.size][HIDDEN];
        h1 = new float[s.size][HIDDEN];
        region = new float[s.regionCount][HIDDEN];
    }

    public void score(GameState s, IndrillFeatures features, float[] build, float[] ink) {
        Arrays.fill(global, 0);
        for (int c = 0; c < s.size; c++) {
            float[] h = h0[c], x = features.x[c];
            System.arraycopy(weights[1], 0, h, 0, HIDDEN);
            for (int f = 0; f < IndrillFeatures.INPUTS; f++) {
                float v = x[f];
                if (v == 0) continue;
                for (int j = 0; j < HIDDEN; j++) h[j] += v * weights[0][f * HIDDEN + j];
            }
            for (int j = 0; j < HIDDEN; j++) { h[j] = Math.max(0, h[j]); global[j] += h[j] / s.size; }
        }
        for (int r = 0; r < s.regionCount; r++) Arrays.fill(region[r], 0);
        for (int c = 0; c < s.size; c++) {
            Arrays.fill(local, 0);
            int count = 0;
            for (int d = 0; d < 4; d++) {
                int n = features.neighbors[c][d];
                if (n < 0) continue;
                count++;
                for (int j = 0; j < HIDDEN; j++) local[j] += h0[n][j];
            }
            if (count > 0) for (int j = 0; j < HIDDEN; j++) local[j] /= count;
            float[] h = h1[c];
            System.arraycopy(weights[5], 0, h, 0, HIDDEN);
            for (int i = 0; i < HIDDEN; i++) for (int j = 0; j < HIDDEN; j++)
                h[j] += h0[c][i] * weights[2][i * HIDDEN + j]
                        + local[i] * weights[3][i * HIDDEN + j] + global[i] * weights[4][i * HIDDEN + j];
            float q = weights[8][0];
            for (int j = 0; j < HIDDEN; j++) {
                h[j] = Math.max(0, h[j]);
                q += h[j] * weights[6][j];
                region[s.regionId[c]][j] += h[j] / features.regionSizes[s.regionId[c]];
            }
            build[c] = q;
        }
        for (int r = 0; r < s.regionCount; r++) {
            float q = weights[9][0];
            for (int j = 0; j < HIDDEN; j++) q += region[r][j] * weights[7][j];
            ink[r] = q;
        }
    }

    /** Exact argmax for this additive build head, with budget three and stable cell-index ties. */
    public static int selectBuild(float[] q, int[] costs, int[] selected) {
        int a = -1, b = -1, c = -1, river = -1, mountain = -1;
        for (int i = 0; i < q.length; i++) {
            if (costs[i] == 1) {
                if (a < 0 || q[i] > q[a]) { c = b; b = a; a = i; }
                else if (b < 0 || q[i] > q[b]) { c = b; b = i; }
                else if (c < 0 || q[i] > q[c]) c = i;
            } else if (costs[i] == 2 && (river < 0 || q[i] > q[river])) river = i;
            else if (costs[i] == 3 && (mountain < 0 || q[i] > q[mountain])) mountain = i;
        }
        Arrays.fill(selected, -1);
        float best = 0;
        int n = 0;
        if (a >= 0 && q[a] > 0) { selected[n++] = a; best += q[a]; }
        if (b >= 0 && q[b] > 0) { selected[n++] = b; best += q[b]; }
        if (c >= 0 && q[c] > 0) { selected[n++] = c; best += q[c]; }
        float mixed = river < 0 ? Float.NEGATIVE_INFINITY : q[river] + (a >= 0 ? Math.max(0, q[a]) : 0);
        if (mixed > best) {
            Arrays.fill(selected, -1); selected[0] = river; n = 1; best = mixed;
            if (a >= 0 && q[a] > 0) selected[n++] = a;
        }
        if (mountain >= 0 && q[mountain] > best) {
            Arrays.fill(selected, -1); selected[0] = mountain; n = 1;
        }
        // Output order is irrelevant to explicit PLACE legality, but deterministic for artifacts.
        if (n > 1 && selected[0] > selected[1]) { int t = selected[0]; selected[0] = selected[1]; selected[1] = t; }
        if (n > 2) {
            if (selected[1] > selected[2]) { int t = selected[1]; selected[1] = selected[2]; selected[2] = t; }
            if (selected[0] > selected[1]) { int t = selected[0]; selected[0] = selected[1]; selected[1] = t; }
        }
        return n;
    }
}
