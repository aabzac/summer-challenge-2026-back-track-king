package btk.bot.neural;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;

/**
 * Small fully connected scorer (ReLU hidden layers, one linear output) used as a conditional
 * logit over candidate rows: the candidate with the highest {@link #score} is the policy's choice.
 *
 * <p>Weights arrive as one base64 string produced by {@code tools/neural/train_neural.py}: float32
 * feature means and inverse standard deviations, then per layer a float32 scale followed by int16
 * weights (row-major {@code [out][in]}) and int16 biases. Raw feature rows from
 * {@link NeuralFeatures} are standardised here and scored directly. An empty string yields an
 * all-zero network (every candidate scores 0), which keeps the bot runnable before training.
 */
public final class NeuralNet {

    private final int[] sizes;
    private final float[] mean, invStd;
    private final float[][] weights, biases, activations;

    public NeuralNet(int[] sizes, String base64) {
        this.sizes = sizes.clone();
        int layers = sizes.length - 1;
        weights = new float[layers][];
        biases = new float[layers][];
        activations = new float[sizes.length][];
        for (int l = 0; l <= layers; l++) activations[l] = new float[sizes[l]];
        mean = new float[sizes[0]];
        invStd = new float[sizes[0]];
        ByteBuffer buf = base64 == null || base64.isEmpty() ? null
                : ByteBuffer.wrap(Base64.getDecoder().decode(base64)).order(ByteOrder.LITTLE_ENDIAN);
        if (buf != null) {
            for (int i = 0; i < sizes[0]; i++) mean[i] = buf.getFloat();
            for (int i = 0; i < sizes[0]; i++) invStd[i] = buf.getFloat();
        } else {
            java.util.Arrays.fill(invStd, 1f);
        }
        for (int l = 0; l < layers; l++) {
            int in = sizes[l], out = sizes[l + 1];
            weights[l] = new float[out * in];
            biases[l] = new float[out];
            if (buf == null) continue;
            float scale = buf.getFloat();
            for (int i = 0; i < out * in; i++) weights[l][i] = buf.getShort() * scale;
            for (int i = 0; i < out; i++) biases[l][i] = buf.getShort() * scale;
        }
        if (buf != null && buf.hasRemaining()) throw new IllegalArgumentException("Weight blob longer than the declared layers");
    }

    public int inputs() {
        return sizes[0];
    }

    public int parameters() {
        int n = 0;
        for (int l = 0; l + 1 < sizes.length; l++) n += sizes[l] * sizes[l + 1] + sizes[l + 1];
        return n;
    }

    /** Score of one raw candidate row; allocation-free. */
    public float score(float[] x) {
        float[] cur = activations[0];
        for (int i = 0; i < cur.length; i++) cur[i] = (x[i] - mean[i]) * invStd[i];
        int layers = sizes.length - 1;
        for (int l = 0; l < layers; l++) {
            int in = sizes[l], out = sizes[l + 1];
            float[] w = weights[l], b = biases[l], next = activations[l + 1];
            boolean last = l == layers - 1;
            for (int o = 0; o < out; o++) {
                float sum = b[o];
                int base = o * in;
                for (int i = 0; i < in; i++) sum += w[base + i] * cur[i];
                next[o] = last || sum > 0 ? sum : 0;
            }
            cur = next;
        }
        return cur[0];
    }
}
