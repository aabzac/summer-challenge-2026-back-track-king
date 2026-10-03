package btk.bot;

import btk.bot.current.IndrillFeatures;
import btk.bot.current.IndrillV2Bot;
import btk.bot.current.IndrillV2Net;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class IndrillV2BotTest {
    @Test void boardValueUsesTheMeanOfFinalCellRepresentationsAndWaitCanBeNegative() {
        GameState s = contextMap();
        float[][] weights = zeroWeights();
        weights[0][0] = weights[2][0] = weights[3][0] = weights[4][0] = 1;
        weights[6][0] = 2;
        weights[7][0] = 3;
        weights[10][0] = 3;
        weights[11][0] = -30;
        IndrillFeatures features = contextFeatures(s);
        IndrillV2Net net = new IndrillV2Net(weights);
        net.init(s);
        float[] build = new float[s.size], ink = new float[s.regionCount];
        net.score(s, features, build, ink);
        // Final cell channel is (17/3, 23/3, 29/3), whose board mean is 23/3.
        assertArrayEquals(new float[]{34f / 3, 46f / 3, 58f / 3}, build, 1e-5f);
        assertArrayEquals(new float[]{20, 29}, ink, 1e-5f);
        assertEquals(-7, net.stateValue, 1e-5f);
        assertEquals(-7, net.actionValue(build, ink, new int[0], 0, -1), 1e-5f);
    }

    @Test void changingOnlyTheBaselineChangesEveryCompleteActionValueExactlyOnce() {
        GameState s = contextMap();
        float[][] weights = zeroWeights();
        weights[0][0] = weights[2][0] = 1;
        weights[6][0] = .5f;
        weights[7][0] = 2;
        weights[10][0] = 3;
        weights[11][0] = -40;
        IndrillV2Net net = new IndrillV2Net(weights);
        net.init(s);
        IndrillFeatures features = contextFeatures(s);
        float[] build = new float[s.size], ink = new float[s.regionCount];
        int[] cells = {0, 1, 2}, selected = new int[3];
        net.score(s, features, build, ink);
        float[] oldBuild = build.clone(), oldInk = ink.clone();
        float[][] oldValues = new float[4][3];
        for (int count = 0; count <= 3; count++) for (int target = -1; target <= 1; target++)
            oldValues[count][target + 1] = net.actionValue(build, ink, cells, count, target);
        int oldCount = IndrillV2Net.selectBuild(build, new int[]{3, 1, 2}, selected);
        int[] oldSelected = selected.clone();
        weights[11][0] += 60;
        net.score(s, features, build, ink);
        assertArrayEquals(oldBuild, build, "value-head changes cannot affect build advantages");
        assertArrayEquals(oldInk, ink, "value-head changes cannot affect ink advantages");
        assertEquals(oldCount, IndrillV2Net.selectBuild(build, new int[]{3, 1, 2}, selected));
        assertArrayEquals(oldSelected, selected);
        for (int count = 0; count <= 3; count++) for (int target = -1; target <= 1; target++)
            assertEquals(60, net.actionValue(build, ink, cells, count, target)
                    - oldValues[count][target + 1], 1e-5f, "count=" + count + " target=" + target);
    }

    @Test void negativeStateValueDoesNotPreventAUsefulBuildOrForceANonnegativeBootstrap() {
        GameState s = corridor();
        float[][] weights = zeroWeights();
        weights[8][0] = 2;
        weights[9][0] = -1;
        weights[11][0] = -10;
        IndrillV2Bot bot = new IndrillV2Bot(new Params(), new IndrillV2Net(weights));
        bot.init(s, 0);
        List<Action> actions = bot.play(s, 0, 36000);
        assertEquals(-10, bot.stateValue);
        assertEquals(3, bot.selectedCount);
        assertArrayEquals(new int[]{1, 2, 3}, bot.selectedCells);
        assertEquals(-1, bot.selectedInk);
        assertEquals(-4, bot.selectedValue(), "best available complete action may remain negative");
        Rules.TurnResult result = Rules.resolve(s, actions, List.of(Action.waitAction()), new Pathing(s.size));
        assertNull(result.moves[0].error);
        assertTrue(result.moves[0].skipped.isEmpty());
        assertEquals(3, result.moves[0].cost);
    }

    @Test void fullWaitUsesTheLearnedBoardValueInsteadOfTheFixedZeroOfV1() {
        GameState s = corridor();
        float[][] weights = zeroWeights();
        weights[8][0] = weights[9][0] = -1;
        weights[11][0] = -6;
        IndrillV2Bot bot = new IndrillV2Bot(new Params(), new IndrillV2Net(weights));
        bot.init(s, 1);
        assertEquals("WAIT", Action.format(bot.play(s, 1, 36000)));
        assertEquals(0, bot.selectedCount);
        assertEquals(-1, bot.selectedInk);
        assertEquals(-6, bot.selectedValue());
    }

    @Test void advantageKnapsackMatchesExhaustiveJointScoresAcrossTerrainAndBaselines() {
        IndrillV2Net net = new IndrillV2Net(zeroWeights());
        Random random = new Random(20261004);
        for (int trial = 0; trial < 250; trial++) {
            float[] build = new float[8], ink = new float[]{random.nextInt(21) - 10};
            int[] cost = new int[8], selected = new int[3];
            net.stateValue = random.nextInt(101) - 50;
            for (int c = 0; c < build.length; c++) {
                build[c] = random.nextInt(21) - 10;
                cost[c] = random.nextInt(4);
            }
            float best = net.stateValue;
            for (int mask = 0; mask < 1 << build.length; mask++) {
                int paint = 0;
                float sum = net.stateValue;
                boolean legal = true;
                for (int c = 0; c < build.length; c++) if ((mask & (1 << c)) != 0) {
                    if (cost[c] == 0) legal = false;
                    paint += cost[c]; sum += build[c];
                }
                if (legal && paint <= 3) best = Math.max(best, sum + Math.max(0, ink[0]));
            }
            int count = IndrillV2Net.selectBuild(build, cost, selected);
            assertEquals(best, net.actionValue(build, ink, selected, count, ink[0] > 0 ? 0 : -1));
            for (int i = 0; i < count; i++) {
                assertTrue(cost[selected[i]] > 0);
                if (i > 0) assertTrue(selected[i] > selected[i - 1]);
            }
        }
    }

    @Test void valueTensorShapesAndFiniteWeightsAreValidated() {
        float[][] weights = zeroWeights();
        assertDoesNotThrow(() -> new IndrillV2Net(weights));
        assertThrows(IllegalArgumentException.class, () -> new IndrillV2Net(Arrays.copyOf(weights, 10)));
        weights[10] = new float[23];
        assertThrows(IllegalArgumentException.class, () -> new IndrillV2Net(weights));
        weights[10] = new float[24];
        weights[11][0] = Float.NaN;
        assertThrows(IllegalArgumentException.class, () -> new IndrillV2Net(weights));
    }

    private static GameState corridor() {
        return new GameState(5, 1, new int[]{0, 1, 1, 1, 2}, new int[5],
                new int[]{0, 4}, new int[]{0, 0}, new int[][]{{1}, {}});
    }

    private static GameState contextMap() {
        return new GameState(3, 1, new int[]{0, 0, 1}, new int[3], new int[0], new int[0], new int[0][]);
    }

    private static IndrillFeatures contextFeatures(GameState s) {
        IndrillFeatures features = new IndrillFeatures(s, new Params());
        features.x[0][0] = 1; features.x[1][0] = 2; features.x[2][0] = 5;
        return features;
    }

    private static float[][] zeroWeights() {
        int[] sizes = {64 * 24, 24, 24 * 24, 24 * 24, 24 * 24, 24, 24, 24, 1, 1, 24, 1};
        float[][] weights = new float[sizes.length][];
        for (int i = 0; i < sizes.length; i++) weights[i] = new float[sizes[i]];
        return weights;
    }
}
