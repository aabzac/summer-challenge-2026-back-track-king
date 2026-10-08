package btk.bot.neural;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Base64;
import java.util.function.LongSupplier;

/** Frozen v1 float32 inference. All forward-pass storage is owned and reused by this instance. */
public final class SpatialNet {
    public static final int CHANNELS = 8;
    public static final int WEIGHT_COUNT = 3439;
    static final int STEM_W = 0, STEM_B = 1728, TOWN_W = 1736, TOWN_B = 2128;
    static final int RES1_W = 2136, RES1_B = 2712, RES2_W = 2720, RES2_B = 3296;
    static final int GLOBAL_W = 3304, GLOBAL_B = 3368, BUILD_W = 3376, BUILD_B = 3384;
    static final int INK_W = 3385, INK_B = 3393, STOP_W = 3394, STOP_B = 3408;
    static final int NOINK_W = 3409, NOINK_B = 3423, VALUE_W = 3424, VALUE_B = 3438;
    private static final int W = SpatialObservation.W, H = SpatialObservation.H, S = SpatialObservation.S;

    private final float[] weights;
    private final float[] trunk = new float[CHANNELS * S];
    private final float[] middle = new float[CHANNELS * S];
    private final float[] residual = new float[CHANNELS * S];
    private final float[] mean = new float[CHANNELS], injected = new float[CHANNELS], z = new float[CHANNELS + 6];
    private final float[] nextBuild = new float[S + 1], nextDisrupt = new float[S + 1];
    private final int[] regionCounts = new int[S], neighbours = new int[S * 9];
    /** Unmasked logits; callers must apply the observation's legal-action masks. */
    public final float[] buildLogits = new float[S + 1], disruptLogits = new float[S + 1];
    public float value;

    public SpatialNet(String base64) { this(decode(base64)); }

    /** Explicit weights for export/parity checks. The supplied array is defensively copied. */
    public SpatialNet(float[] source) {
        if (source == null || source.length != WEIGHT_COUNT)
            throw new IllegalArgumentException("Spatial v1 requires exactly " + WEIGHT_COUNT + " float32 weights");
        weights = source.clone();
        for (int i = 0; i < weights.length; i++)
            if (!Float.isFinite(weights[i])) throw new IllegalArgumentException("Non-finite spatial weight at " + i);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int offset = (y * W + x) * 9;
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                int xx = x + dx, yy = y + dy;
                neighbours[offset++] = xx >= 0 && xx < W && yy >= 0 && yy < H ? yy * W + xx : -1;
            }
        }
    }

    private static float[] decode(String base64) {
        if (base64 == null) throw new IllegalArgumentException("Spatial weights are missing");
        byte[] bytes = Base64.getDecoder().decode(base64);
        if (bytes.length != WEIGHT_COUNT * Float.BYTES)
            throw new IllegalArgumentException("Wrong spatial weight payload length: " + bytes.length);
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] result = new float[WEIGHT_COUNT];
        for (int i = 0; i < result.length; i++) result[i] = buffer.getFloat();
        return result;
    }

    public void forward(SpatialObservation observation) { forward(observation, null, 0); }

    /**
     * Returns false at the cooperative deadline. Public outputs change only after a full pass,
     * so an interrupted decision cannot publish a partly scored action or value.
     */
    public boolean forward(SpatialObservation observation, LongSupplier clock, long deadlineNs) {
        if (expired(clock, deadlineNs)) return false;
        if (!conv(observation.planes, SpatialObservation.PLANES, trunk, STEM_W, STEM_B, true, clock, deadlineNs)) return false;
        for (int town = 0; town < SpatialObservation.TOWNS; town++) {
            int cell = observation.townCells[town];
            if (cell < 0) continue;
            int features = town * SpatialObservation.TOWN_FEATURES;
            for (int channel = 0; channel < CHANNELS; channel++) {
                float sum = weights[TOWN_B + channel];
                int row = TOWN_W + channel * SpatialObservation.TOWN_FEATURES;
                for (int f = 0; f < SpatialObservation.TOWN_FEATURES; f++) sum += weights[row + f] * observation.towns[features + f];
                trunk[channel * S + cell] += sum;
            }
        }
        if (!conv(trunk, CHANNELS, middle, RES1_W, RES1_B, true, clock, deadlineNs)
                || !conv(middle, CHANNELS, residual, RES2_W, RES2_B, false, clock, deadlineNs)) return false;
        for (int i = 0; i < trunk.length; i++) trunk[i] = Math.max(0, trunk[i] + residual[i]);
        maskedMean(trunk, observation.planes, mean);
        for (int out = 0; out < CHANNELS; out++) {
            float sum = weights[GLOBAL_B + out];
            for (int in = 0; in < CHANNELS; in++) sum += weights[GLOBAL_W + out * CHANNELS + in] * mean[in];
            injected[out] = sum;
        }
        for (int channel = 0; channel < CHANNELS; channel++)
            for (int cell = 0; cell < S; cell++) {
                int index = channel * S + cell;
                trunk[index] = Math.max(0, trunk[index] + injected[channel]);
            }
        maskedMean(trunk, observation.planes, z);
        System.arraycopy(observation.scalars, 0, z, CHANNELS, 6);
        Arrays.fill(nextDisrupt, 0);
        Arrays.fill(regionCounts, 0);
        for (int cell = 0; cell < S; cell++) {
            float build = weights[BUILD_B], ink = weights[INK_B];
            for (int channel = 0; channel < CHANNELS; channel++) {
                float feature = trunk[channel * S + cell];
                build += weights[BUILD_W + channel] * feature;
                ink += weights[INK_W + channel] * feature;
            }
            nextBuild[cell] = build;
            int region = observation.regionIds[cell];
            if (region >= 0 && observation.planes[cell] > 0) {
                nextDisrupt[region] += ink;
                regionCounts[region]++;
            }
        }
        for (int region = 0; region < S; region++)
            if (regionCounts[region] > 0) nextDisrupt[region] /= regionCounts[region];
        nextBuild[S] = linear(STOP_W, STOP_B);
        nextDisrupt[S] = linear(NOINK_W, NOINK_B);
        float nextValue = linear(VALUE_W, VALUE_B);
        if (expired(clock, deadlineNs)) return false;
        System.arraycopy(nextBuild, 0, buildLogits, 0, S + 1);
        System.arraycopy(nextDisrupt, 0, disruptLogits, 0, S + 1);
        value = nextValue;
        return true;
    }

    /** Conv2d cross-correlation in PyTorch [out,in,dy,dx] order, padding=1 on the full 30x20 tensor. */
    private boolean conv(float[] input, int channels, float[] output, int weightOffset, int biasOffset,
                         boolean relu, LongSupplier clock, long deadlineNs) {
        for (int out = 0; out < CHANNELS; out++) {
            int row = weightOffset + out * channels * 9;
            for (int cell = 0; cell < S; cell++) {
                if (cell % W == 0 && expired(clock, deadlineNs)) return false;
                float sum = weights[biasOffset + out];
                int near = cell * 9;
                for (int in = 0; in < channels; in++) {
                    int base = in * S, kernel = row + in * 9;
                    for (int k = 0; k < 9; k++) {
                        int source = neighbours[near + k];
                        if (source >= 0) sum += input[base + source] * weights[kernel + k];
                    }
                }
                output[out * S + cell] = relu ? Math.max(0, sum) : sum;
            }
        }
        return true;
    }

    private static void maskedMean(float[] input, float[] planes, float[] output) {
        int count = 0;
        for (int cell = 0; cell < S; cell++) if (planes[cell] > 0) count++;
        for (int channel = 0; channel < CHANNELS; channel++) {
            float sum = 0;
            for (int cell = 0; cell < S; cell++) if (planes[cell] > 0) sum += input[channel * S + cell];
            output[channel] = count == 0 ? 0 : sum / count;
        }
    }

    private float linear(int offset, int bias) {
        float sum = weights[bias];
        for (int i = 0; i < z.length; i++) sum += weights[offset + i] * z[i];
        return sum;
    }

    private static boolean expired(LongSupplier clock, long deadlineNs) {
        return clock != null && clock.getAsLong() - deadlineNs >= 0;
    }
}
