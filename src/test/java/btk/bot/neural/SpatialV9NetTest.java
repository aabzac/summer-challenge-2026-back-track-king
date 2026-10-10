package btk.bot.neural;

import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import java.util.Arrays;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpatialV9NetTest {
    @Test void contextOffIsBitExactWithFrozenNetworkForBothSeatsAndConditionalStages() {
        GameState state = fixture(9, 6, new int[]{0, 8}, new int[]{0, 5});
        state.turn = 37; state.owner[1] = 0; state.owner[2] = 1;
        Rules.updateConnections(state, new Pathing(state.size));
        for (String payload : new String[]{SpatialInitialWeights.DATA, SpatialV4Weights.DATA}) {
            SpatialNet old = new SpatialNet(payload);
            SpatialV9Net next = new SpatialV9Net(payload, false);
            for (int seat = 0; seat < 2; seat++) {
                SpatialObservation observation = observation(state, seat);
                for (int stage = 0; stage < 4; stage++) {
                    observation.prefix(new int[]{3, 4, 5}, Math.min(stage, 3), 3 - Math.min(stage, 3), stage, false);
                    old.forward(observation); next.forward(observation);
                    exact(old.buildLogits, next.buildLogits);
                    exact(old.disruptLogits, next.disruptLogits);
                    assertEquals(Float.floatToRawIntBits(old.value), Float.floatToRawIntBits(next.value));
                }
            }
        }
    }

    @Test void fixedPositiveTownEmbeddingCanDistinguishTheExposedLocalAliasWithoutTraining() {
        // Capacity/formula fixture only: a unit feature, not fitted weights or a strength assertion.
        GameState state = fixture(30, 20, new int[]{2, 18}, new int[]{9, 9});
        int investment = 9 * 30 + 10, alternative = 15 * 30 + 10;
        SpatialV9Net off = new SpatialV9Net(townFeatureWeights(), false);
        SpatialV9Net on = new SpatialV9Net(townFeatureWeights(), true);
        for (int seat = 0; seat < 2; seat++) {
            SpatialObservation observation = observation(state, seat);
            assertTrue(observation.buildMask[investment]); assertTrue(observation.buildMask[alternative]);
            for (int plane = 0; plane < SpatialObservation.PLANES; plane++)
                for (int dy = -3; dy <= 3; dy++) for (int dx = -3; dx <= 3; dx++)
                    assertEquals(observation.planes[plane * 600 + investment + dy * 30 + dx],
                            observation.planes[plane * 600 + alternative + dy * 30 + dx]);
            off.forward(observation); on.forward(observation);
            assertEquals(off.buildLogits[investment], off.buildLogits[alternative]);
            assertEquals(2f / 9, on.buildLogits[investment], 1e-7f);
            assertEquals(2f / 15, on.buildLogits[alternative], 1e-7f);
            assertTrue(on.buildLogits[investment] > on.buildLogits[alternative]);
        }
    }

    @Test void contextUsesActualTownCountRetainsScatterAndDoesNotFillPadding() {
        SpatialObservation observation = observation(fixture(4, 3, new int[]{0, 3}, new int[]{0, 2}), 0);
        SpatialV9Net net = new SpatialV9Net(townFeatureWeights(), true);
        // Missing rows contain garbage features but must never become towns or change the divisor.
        Arrays.fill(observation.towns, 2 * SpatialObservation.TOWN_FEATURES, observation.towns.length, 999f);
        net.forward(observation);
        assertEquals(2f * (1f / 6 + 1f / 8), net.buildLogits[31], 1e-7f);
        assertEquals(2f + 2f * (1f / 2 + 1f / 12), net.buildLogits[0], 1e-7f);
        for (int y = 0; y < 20; y++) for (int x = 0; x < 30; x++)
            if (x >= 4 || y >= 3) assertEquals(0f, net.buildLogits[y * 30 + x]);
        Arrays.fill(observation.townCells, -1);
        net.forward(observation);
        for (float logit : net.buildLogits) assertEquals(0f, logit);
    }

    @Test void cachedGeometryTracksChangedTownPositionsBoardPaddingAndZeroTownMaps() {
        float[] weights = townFeatureWeights();
        SpatialV9Net reused = new SpatialV9Net(weights, true);
        GameState[] maps = {
                fixture(9, 6, new int[]{0, 8}, new int[]{0, 5}),
                fixture(9, 6, new int[]{2, 6}, new int[]{1, 4}),
                fixture(5, 3, new int[]{0, 4}, new int[]{0, 2}),
                fixture(5, 3, new int[0], new int[0]),
                fixture(9, 6, new int[]{0, 8}, new int[]{0, 5})
        };
        for (GameState state : maps) {
            SpatialObservation observation = observation(state, 0);
            SpatialV9Net fresh = new SpatialV9Net(weights, true);
            reused.forward(observation); fresh.forward(observation);
            exact(fresh.buildLogits, reused.buildLogits);
            exact(fresh.disruptLogits, reused.disruptLogits);
            float[] prior = reused.buildLogits.clone();
            reused.forward(observation); exact(prior, reused.buildLogits);
        }
    }

    @Test void geometryAndContextCutoffsNeverPublishPartialOutputsAndInterruptedCacheRecovers() {
        GameState first = fixture(9, 6, new int[]{0, 8}, new int[]{0, 5});
        GameState second = fixture(10, 7, new int[]{2, 9}, new int[]{1, 6});
        SpatialV9Net net = new SpatialV9Net(SpatialV4Weights.DATA, true);
        net.forward(observation(first, 0));
        float[] build = net.buildLogits.clone(), disrupt = net.disruptLogits.clone();
        float value = net.value;
        DeadlineAtMethod geometry = new DeadlineAtMethod("buildGeometry", 3);
        assertFalse(net.forward(observation(second, 1), geometry, 1));
        assertEquals(3, geometry.hits); exact(build, net.buildLogits); exact(disrupt, net.disruptLogits);
        assertEquals(Float.floatToRawIntBits(value), Float.floatToRawIntBits(net.value));
        SpatialV9Net fresh = new SpatialV9Net(SpatialV4Weights.DATA, true);
        SpatialObservation observation = observation(second, 1);
        net.forward(observation); fresh.forward(observation);
        exact(fresh.buildLogits, net.buildLogits); exact(fresh.disruptLogits, net.disruptLogits);
        assertEquals(Float.floatToRawIntBits(fresh.value), Float.floatToRawIntBits(net.value));
        build = net.buildLogits.clone(); disrupt = net.disruptLogits.clone(); value = net.value;
        observation.towns[0] += .25f;
        DeadlineAtMethod context = new DeadlineAtMethod("broadcast", 3);
        assertFalse(net.forward(observation, context, 1));
        assertEquals(3, context.hits); exact(build, net.buildLogits); exact(disrupt, net.disruptLogits);
        assertEquals(Float.floatToRawIntBits(value), Float.floatToRawIntBits(net.value));
        net.forward(observation); fresh.forward(observation);
        exact(fresh.buildLogits, net.buildLogits); exact(fresh.disruptLogits, net.disruptLogits);
    }

    @Test void presentTownEmbeddingsRefreshEvenWhenGeometryIsCached() {
        float[] weights = townFeatureWeights();
        weights[SpatialV9Net.TOWN_W] = 1;
        SpatialV9Net cached = new SpatialV9Net(weights, true);
        SpatialObservation observation = observation(fixture(4, 3, new int[]{0, 3}, new int[]{0, 2}), 0);
        cached.forward(observation); float before = cached.buildLogits[31];
        observation.towns[0] += 1;
        cached.forward(observation);
        assertEquals(1f / 6, cached.buildLogits[31] - before, 1e-7f);
    }

    static float[] townFeatureWeights() {
        float[] weights = new float[SpatialV9Net.WEIGHT_COUNT];
        weights[SpatialV9Net.TOWN_B] = 2;
        weights[SpatialV9Net.BUILD_W] = 1;
        return weights;
    }

    static GameState fixture(int width, int height, int[] xs, int[] ys) {
        int[] regions = new int[width * height];
        for (int cell = 0; cell < regions.length; cell++) regions[cell] = cell;
        int[][] desired = new int[xs.length][];
        for (int town = 0; town < xs.length; town++) desired[town] = town == 0 && xs.length > 1 ? new int[]{1} : new int[0];
        return new GameState(width, height, regions, new int[regions.length], xs, ys, desired);
    }

    static SpatialObservation observation(GameState state, int seat) {
        SpatialObservation result = new SpatialObservation(state);
        result.prepare(state, seat);
        return result;
    }

    static void exact(float[] expected, float[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++)
            assertEquals(Float.floatToRawIntBits(expected[i]), Float.floatToRawIntBits(actual[i]), "float at " + i);
    }

    /** Test-only phase interruption; no timing/performance claim and no production instrumentation. */
    static final class DeadlineAtMethod implements LongSupplier {
        private final String method;
        private final int expireAt;
        int hits;
        DeadlineAtMethod(String method, int expireAt) { this.method = method; this.expireAt = expireAt; }
        @Override public long getAsLong() {
            for (StackTraceElement frame : Thread.currentThread().getStackTrace())
                if (frame.getClassName().equals(SpatialV9Net.class.getName()) && frame.getMethodName().equals(method))
                    return ++hits >= expireAt ? 1 : 0;
            return 0;
        }
    }
}
