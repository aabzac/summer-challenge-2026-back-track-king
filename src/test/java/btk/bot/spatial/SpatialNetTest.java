package btk.bot.neural;

import btk.core.GameState;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SpatialNetTest {
    private static final int S = SpatialObservation.S;

    static SpatialObservation blank() {
        return new SpatialObservation(new GameState(30, 20, new int[S], new int[S],
                new int[0], new int[0], new int[0][]));
    }

    @Test void weightsRequireExactFiniteLittleEndianPayloadAndAreCopied() {
        assertThrows(IllegalArgumentException.class, () -> new SpatialNet((String) null));
        assertThrows(IllegalArgumentException.class, () -> new SpatialNet("not:base64"));
        assertThrows(IllegalArgumentException.class, () -> new SpatialNet(""));
        assertThrows(IllegalArgumentException.class, () -> new SpatialNet(new float[SpatialNet.WEIGHT_COUNT - 1]));
        assertThrows(IllegalArgumentException.class, () -> new SpatialNet(new float[SpatialNet.WEIGHT_COUNT + 1]));
        float[] weights = new float[SpatialNet.WEIGHT_COUNT];
        for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            weights[17] = invalid;
            assertThrows(IllegalArgumentException.class, () -> new SpatialNet(weights));
        }
        weights[17] = 0;
        weights[SpatialNet.STOP_B] = .5f;
        weights[SpatialNet.NOINK_B] = -2f;
        weights[SpatialNet.VALUE_B] = 3.125f;
        ByteBuffer bytes = ByteBuffer.allocate(weights.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float weight : weights) bytes.putFloat(weight);
        SpatialNet fromBytes = new SpatialNet(Base64.getEncoder().encodeToString(bytes.array()));
        SpatialNet fromArray = new SpatialNet(weights);
        Arrays.fill(weights, 99);
        SpatialObservation observation = blank();
        fromBytes.forward(observation);
        fromArray.forward(observation);
        assertEquals(.5f, fromBytes.buildLogits[S]);
        assertEquals(-2f, fromBytes.disruptLogits[S]);
        assertEquals(3.125f, fromBytes.value);
        assertArrayEquals(fromBytes.buildLogits, fromArray.buildLogits);
        assertArrayEquals(fromBytes.disruptLogits, fromArray.disruptLogits);
        assertEquals(fromBytes.value, fromArray.value);
    }

    @Test void convolutionIsCrossCorrelationWithZeroPaddingAndNoRowWrap() {
        float[] weights = new float[SpatialNet.WEIGHT_COUNT];
        for (int k = 0; k < 9; k++) weights[SpatialNet.STEM_W + 9 + k] = k + 1;
        weights[SpatialNet.BUILD_W] = 1;
        SpatialObservation observation = blank();
        observation.planes[S + 31] = 2;
        observation.planes[S + 29] = 4;
        SpatialNet net = new SpatialNet(weights);
        net.forward(observation);
        assertEquals(18, net.buildLogits[0]);
        assertEquals(10, net.buildLogits[31]);
        assertEquals(2, net.buildLogits[62]);
        assertEquals(12, net.buildLogits[30], "the right edge of the previous row must not wrap");
        assertEquals(20, net.buildLogits[29]);
        assertEquals(24, net.buildLogits[28]);
        assertEquals(8, net.buildLogits[59]);
        assertEquals(0, net.buildLogits[90]);
    }

    @Test void townEncodingIsAddedAfterStemReluWithoutAnExtraActivation() {
        float[] weights = new float[SpatialNet.WEIGHT_COUNT];
        weights[SpatialNet.STEM_B] = -2;
        weights[SpatialNet.TOWN_W] = -3;
        weights[SpatialNet.TOWN_B] = 1;
        weights[SpatialNet.RES1_W + 4] = -1;
        weights[SpatialNet.RES2_W + 4] = 2;
        weights[SpatialNet.BUILD_W] = 1;
        SpatialObservation observation = blank();
        observation.townCells[0] = 0;
        observation.towns[0] = 2;
        SpatialNet net = new SpatialNet(weights);
        net.forward(observation);
        assertEquals(5, net.buildLogits[0], "ReLU(-5 + 2 * ReLU(-1 * -5))");
        assertEquals(0, net.buildLogits[1]);
    }

    @Test void globalAndRegionMeansExcludePaddingAndScalarHeadsUsePostInjectionFeatures() {
        float[] weights = new float[SpatialNet.WEIGHT_COUNT];
        weights[SpatialNet.STEM_W + 9 + 4] = 1;
        weights[SpatialNet.GLOBAL_W] = 3;
        weights[SpatialNet.GLOBAL_B] = 1;
        weights[SpatialNet.BUILD_W] = 1;
        weights[SpatialNet.INK_W] = 2;
        weights[SpatialNet.INK_B] = 1;
        weights[SpatialNet.STOP_W] = 2;
        weights[SpatialNet.STOP_W + 13] = 4;
        weights[SpatialNet.NOINK_W] = 3;
        weights[SpatialNet.VALUE_W] = 1;
        SpatialObservation observation = blank();
        observation.planes[0] = 1;
        Arrays.fill(observation.planes, S, 2 * S, 100);
        observation.planes[S] = 2;
        observation.regionIds[0] = 7;
        observation.regionIds[1] = 7; // A malformed padding id still cannot enter the region mean.
        observation.scalars[5] = .5f;
        SpatialNet net = new SpatialNet(weights);
        net.forward(observation);
        assertEquals(9, net.buildLogits[0]);
        assertEquals(107, net.buildLogits[1]);
        assertEquals(19, net.disruptLogits[7]);
        assertEquals(0, net.disruptLogits[8]);
        assertEquals(20, net.buildLogits[S]);
        assertEquals(27, net.disruptLogits[S]);
        assertEquals(9, net.value);
        observation.planes[S] = 4;
        net.forward(observation);
        assertEquals(17, net.value, "a second forward must not retain prior pooling sums");
        assertEquals(35, net.disruptLogits[7]);
    }

    @Test void interruptionDoesNotPublishPartialOutputs() {
        float[] weights = new float[SpatialNet.WEIGHT_COUNT];
        weights[SpatialNet.STEM_W + 9 + 4] = 1;
        weights[SpatialNet.BUILD_W] = 1;
        weights[SpatialNet.VALUE_W] = 1;
        SpatialNet net = new SpatialNet(weights);
        SpatialObservation observation = blank();
        observation.planes[0] = 1;
        observation.planes[S] = 2;
        net.forward(observation);
        float[] priorBuild = net.buildLogits.clone(), priorDisrupt = net.disruptLogits.clone();
        float priorValue = net.value;
        observation.planes[S] = 99;
        AtomicInteger reads = new AtomicInteger();
        assertFalse(net.forward(observation, () -> reads.incrementAndGet() >= 400 ? 10 : 0, 10));
        assertArrayEquals(priorBuild, net.buildLogits);
        assertArrayEquals(priorDisrupt, net.disruptLogits);
        assertEquals(priorValue, net.value);
        net.forward(observation);
        assertEquals(99, net.value);
    }
}
