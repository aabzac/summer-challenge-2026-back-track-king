package btk.bot;

import btk.bot.neural.NeuralBot;
import btk.bot.neural.NeuralFeatures;
import btk.bot.neural.NeuralNet;
import btk.bot.neural.NeuralV2Bot;
import btk.bot.neural.NeuralWeights;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.referee.MapGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NeuralV2BotTest {
    private static NeuralV2Bot initial(Params p, NeuralV2Bot.DecisionChooser chooser) {
        return new NeuralV2Bot(p, new NeuralNet(NeuralWeights.SABOTAGE_SIZES, NeuralWeights.SABOTAGE),
                new NeuralNet(NeuralWeights.BUILD_SIZES, NeuralWeights.BUILD), chooser);
    }

    @Test void injectedOriginalWeightsPreserveGreedyActionsAndNoiseSequenceOnBothSeats() {
        for (double noise : new double[]{0, .1}) for (int me = 0; me < 2; me++) {
            GameState s = MapGenerator.generate(2209000 + me);
            Pathing pathing = new Pathing(s.size);
            Rules.updateConnections(s, pathing);
            Params params = BotFactory.parameters("neural-v1?NOISE=" + noise);
            NeuralBot original = new NeuralBot(params);
            NeuralV2Bot candidate = initial(params, null);
            NeuralBot opponent = new NeuralBot(BotFactory.parameters("neural-v1?SEED=19"));
            original.init(s, me); candidate.init(s, me); opponent.init(s, 1 - me);
            int turns = 0;
            while (turns < 20 && !Rules.gameOver(s, pathing)) {
                List<Action> expected = original.play(s.copy(), me, 60_000);
                List<Action> actual = candidate.play(s.copy(), me, 60_000);
                assertEquals(Action.format(expected), Action.format(actual), "seat=" + me + " turn=" + turns);
                assertEquals(original.lastTarget, candidate.lastTarget);
                assertEquals(original.lastBuildCandidates, candidate.lastBuildCandidates);
                List<Action> theirs = opponent.play(s.copy(), 1 - me, 60_000);
                Rules.TurnResult result = Rules.resolve(s, me == 0 ? actual : theirs, me == 1 ? actual : theirs, pathing);
                assertNull(result.moves[me].error);
                assertTrue(result.moves[me].skipped.isEmpty());
                turns++;
            }
            assertTrue(turns >= 10);
        }
    }

    private static GameState corridor(int lead, boolean extras) {
        int width = extras ? 7 : 5;
        int[] regions = new int[width * 3];
        for (int c = 0; c < regions.length; c++) {
            int x = c % width;
            regions[c] = x == 0 ? 0 : x == 4 ? 2 : x < 4 ? 1 : x - 2;
        }
        GameState s = new GameState(width, 3, regions, new int[regions.length],
                new int[]{0, 4}, new int[]{1, 1}, new int[][]{{1}, {}});
        for (int x = 1; x < 4; x++) s.owner[s.cell(x, 1)] = 1;
        for (int i = 0; i < 3; i++) Rules.disrupt(s, 1);
        s.score[0] = 100 + lead; s.score[1] = 100; s.turn = 20;
        Rules.updateConnections(s, new Pathing(s.size));
        return s;
    }

    @Test void closingGuardsFilterSamplingMaskAndForcedClosingProducesNoTrainingDecision() {
        for (int lead : new int[]{-1, 20}) {
            GameState s = corridor(lead, true);
            NeuralFeatures oracle = new NeuralFeatures(s);
            oracle.observe(s, 0); oracle.prepare(s, 0, true);
            int[] regions = new int[s.regionCount];
            float[][] expected = new float[s.regionCount][NeuralFeatures.SABOTAGE_F];
            int n = oracle.sabotageCandidates(s, 0, regions, expected);
            assertEquals(3, n);
            int[] sabotageCalls = {0};
            NeuralV2Bot candidate = initial(BotFactory.parameters("neural-v1?NOISE=0"),
                    (sabotage, turn, k, rows, indices, logits, count) -> {
                        assertTrue(count > 1);
                        if (sabotage) {
                            sabotageCalls[0]++;
                            assertEquals(2, count);
                            for (int i = 0; i < count; i++) {
                                assertFalse(oracle.closes[regions[indices[i]]]);
                                assertArrayEquals(expected[indices[i]], rows[indices[i]]);
                            }
                        }
                        return 0;
                    });
            candidate.init(s, 0); candidate.play(s, 0, 60_000);
            if (lead > 0) {
                assertEquals(1, candidate.lastTarget);
                assertEquals(0, sabotageCalls[0], "forced closing has no policy gradient");
            } else {
                assertNotEquals(1, candidate.lastTarget);
                assertEquals(1, sabotageCalls[0]);
            }
        }
    }

    @Test void noPermittedTargetAndSingletonTargetNeverInvokeSampler() {
        GameState s = corridor(-1, false);
        int[] sabotageCalls = {0};
        NeuralV2Bot.DecisionChooser chooser = (sabotage, turn, k, rows, indices, logits, count) -> {
            if (sabotage) sabotageCalls[0]++;
            assertTrue(count > 1);
            return 0;
        };
        NeuralV2Bot guarded = initial(BotFactory.parameters("neural-v1?NOISE=0"), chooser);
        guarded.init(s, 0); guarded.play(s, 0, 60_000);
        assertEquals(-1, guarded.lastTarget);
        NeuralV2Bot unguarded = initial(BotFactory.parameters("neural-v1?NOISE=0,USE_CLOSE=false"), chooser);
        unguarded.init(s, 0); unguarded.play(s, 0, 60_000);
        assertEquals(1, unguarded.lastTarget);
        assertEquals(0, sabotageCalls[0]);
    }

    @Test void samplingUsesUpdatedFeaturesAfterEveryChosenPlacement() {
        GameState s = MapGenerator.generate(2209002);
        Pathing pathing = new Pathing(s.size);
        Rules.updateConnections(s, pathing);
        GameState oracleState = s.copy();
        NeuralFeatures oracle = new NeuralFeatures(s);
        oracle.observe(s, 0); oracle.prepare(s, 0, true);
        int[] candidates = new int[Math.max(s.size, s.regionCount)];
        float[][] sabotageX = new float[s.regionCount][NeuralFeatures.SABOTAGE_F];
        float[][] buildX = new float[s.size][NeuralFeatures.BUILD_F];
        int[] target = {-1}, paint = {3}, buildCalls = {0};
        NeuralV2Bot candidate = initial(BotFactory.parameters("neural-v1?NOISE=0"),
                (sabotage, turn, k, rows, indices, logits, count) -> {
                    assertEquals(s.turn, turn);
                    if (sabotage) {
                        int expectedCount = oracle.sabotageCandidates(s, 0, candidates, sabotageX);
                        assertEquals(expectedCount, count); // Initial map has no closing guard exclusions.
                        for (int i = 0; i < count; i++) assertArrayEquals(sabotageX[i], rows[indices[i]]);
                        target[0] = candidates[0]; oracle.recordTarget(target[0]);
                        return 0;
                    }
                    if (k > 0) oracle.prepare(oracleState, 0, false);
                    int expectedCount = oracle.buildCandidates(oracleState, 0, paint[0], target[0], k, candidates, buildX);
                    assertEquals(expectedCount, count);
                    for (int i = 0; i < count; i++) {
                        assertEquals(i, indices[i]);
                        assertArrayEquals(buildX[i], rows[i], "placement=" + k + " candidate=" + i);
                    }
                    int selected = count / 2, cell = candidates[selected];
                    paint[0] -= oracleState.placeCost(cell);
                    oracleState.owner[cell] = 0; oracle.markPlaced(cell);
                    Rules.updateConnections(oracleState, pathing); buildCalls[0]++;
                    return selected;
                });
        candidate.init(s, 0);
        List<Action> actions = candidate.play(s, 0, 60_000);
        assertEquals(candidate.lastPlacements, buildCalls[0]);
        assertTrue(buildCalls[0] >= 2);
        Rules.TurnResult result = new Rules.TurnResult();
        Rules.expand(s, 0, actions, new Pathing(s.size), result.moves[0], result);
        assertNull(result.moves[0].error);
        assertTrue(result.moves[0].skipped.isEmpty());
    }
}
