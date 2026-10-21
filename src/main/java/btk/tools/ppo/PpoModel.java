package btk.tools.ppo;

import java.util.Random;

/**
 * Three linear heads: placement, disruption, and state value. Small enough that a few
 * hundred self-play games can move the weights. Not the convolutional network from the
 * 3rd-place post-mortem.
 */
public final class PpoModel {
    public static final int CELL_F = 8;
    public static final int DISRUPT_F = 6;
    public static final int VALUE_F = 6;
    public static final double CLIP = 0.2;
    public static final double LR = 1e-2;
    public static final double ENTROPY = 0.01;
    public static final double VALUE_COEF = 0.5;

    public final double[] build = new double[CELL_F];
    public final double[] disrupt = new double[DISRUPT_F];
    public final double[] value = new double[VALUE_F];
    private final double[] buildM = new double[CELL_F], buildV = new double[CELL_F];
    private final double[] disruptM = new double[DISRUPT_F], disruptV = new double[DISRUPT_F];
    private final double[] valueM = new double[VALUE_F], valueV = new double[VALUE_F];
    private final double[] logits = new double[640];
    private final double[] probs = new double[640];
    private int adamStep;

    public PpoModel(long seed) {
        Random random = new Random(seed);
        for (int i = 0; i < build.length; i++) build[i] = random.nextGaussian() * 0.01;
        for (int i = 0; i < disrupt.length; i++) disrupt[i] = random.nextGaussian() * 0.01;
    }

    public PpoModel copy() {
        PpoModel o = new PpoModel(1);
        System.arraycopy(build, 0, o.build, 0, build.length);
        System.arraycopy(disrupt, 0, o.disrupt, 0, disrupt.length);
        System.arraycopy(value, 0, o.value, 0, value.length);
        return o;
    }

    public double value(float[] features) {
        return dot(value, features, 0, VALUE_F);
    }

    public int sample(double[] weights, float[] features, int n, int width, Random rng) {
        softmax(weights, features, n, width);
        if (rng == null) {
            int best = 0;
            for (int i = 1; i < n; i++) if (logits[i] > logits[best]) best = i;
            return best;
        }
        double u = rng.nextDouble(), cum = 0;
        for (int i = 0; i < n; i++) {
            cum += probs[i];
            if (u <= cum) return i;
        }
        return n - 1;
    }

    public double logProb(double[] weights, float[] features, int n, int width, int chosen) {
        softmax(weights, features, n, width);
        return logits[chosen] - logZ(n);
    }

    /** Gradient of {@code -coef * log π - entropyBeta * H}, added into {@code grad}. */
    public double policyGrad(double[] weights, float[] features, int n, int width, int chosen,
                              double coef, double entropyBeta, double[] grad) {
        softmax(weights, features, n, width);
        double logZ = logZ(n);
        double entropy = 0;
        for (int i = 0; i < n; i++) if (probs[i] > 0) entropy -= probs[i] * Math.log(probs[i]);
        for (int i = 0; i < n; i++) {
            double dH = -probs[i] * (Math.log(Math.max(probs[i], 1e-12)) + entropy);
            double dLogit = -coef * ((i == chosen ? 1 : 0) - probs[i]) - entropyBeta * dH;
            int row = i * width;
            for (int j = 0; j < width; j++) grad[j] += dLogit * features[row + j];
        }
        return logits[chosen] - logZ;
    }

    public void valueGrad(float[] features, double target, double[] grad) {
        double delta = VALUE_COEF * (dot(value, features, 0, VALUE_F) - target);
        for (int j = 0; j < VALUE_F; j++) grad[j] += delta * features[j];
    }

    public void apply(double[] buildGrad, double[] disruptGrad, double[] valueGrad) {
        adamStep++;
        adamInto(build, buildGrad, buildM, buildV, adamStep);
        adamInto(disrupt, disruptGrad, disruptM, disruptV, adamStep);
        adamInto(value, valueGrad, valueM, valueV, adamStep);
    }

    public double entropy(double[] weights, float[] features, int n, int width) {
        softmax(weights, features, n, width);
        double h = 0;
        for (int i = 0; i < n; i++) if (probs[i] > 0) h -= probs[i] * Math.log(probs[i]);
        return h;
    }

    private void adamInto(double[] weights, double[] grad, double[] moment, double[] second, int t) {
        double b1 = 0.9, b2 = 0.999, c1 = 1 - Math.pow(b1, t), c2 = 1 - Math.pow(b2, t);
        for (int i = 0; i < weights.length; i++) {
            moment[i] = b1 * moment[i] + (1 - b1) * grad[i];
            second[i] = b2 * second[i] + (1 - b2) * grad[i] * grad[i];
            weights[i] -= LR * (moment[i] / c1) / (Math.sqrt(second[i] / c2) + 1e-8);
        }
    }

    private void softmax(double[] weights, float[] features, int n, int width) {
        if (n > logits.length) throw new IllegalArgumentException("too many candidates: " + n);
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            logits[i] = dot(weights, features, i * width, width);
            if (logits[i] > max) max = logits[i];
        }
        double sum = 0;
        for (int i = 0; i < n; i++) {
            probs[i] = Math.exp(logits[i] - max);
            sum += probs[i];
        }
        for (int i = 0; i < n; i++) probs[i] /= sum;
    }

    private double logZ(int n) {
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) if (logits[i] > max) max = logits[i];
        double sum = 0;
        for (int i = 0; i < n; i++) sum += Math.exp(logits[i] - max);
        return max + Math.log(sum);
    }

    public static double dot(double[] weights, float[] features, int offset, int width) {
        double s = 0;
        for (int j = 0; j < width; j++) s += weights[j] * features[offset + j];
        return s;
    }

    public static double clipScale(double norm) {
        return norm > 1 ? 1 / norm : 1;
    }
}
