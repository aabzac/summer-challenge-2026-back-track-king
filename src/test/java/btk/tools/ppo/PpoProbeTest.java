package btk.tools.ppo;

import btk.bot.Bot;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.referee.MapGenerator;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class PpoProbeTest {
    @Test void positiveAdvantageRaisesTheChosenLogProb() {
        PpoModel model = new PpoModel(1);
        double[] weights = new double[2];
        double[] grad = new double[2];
        float[] features = {1f, 0f, 0f, 1f};
        double before = model.logProb(weights, features, 2, 2, 0);
        model.policyGrad(weights, features, 2, 2, 0, 1, 0, grad);
        for (int j = 0; j < 2; j++) weights[j] -= grad[j];
        assertTrue(model.logProb(weights, features, 2, 2, 0) > before);
    }

    @Test void entropyStepSpreadsAPeakedPolicy() {
        PpoModel model = new PpoModel(1);
        double[] weights = {2, 0};
        float[] features = {1f, 0f, 0f, 1f};
        double before = model.entropy(weights, features, 2, 2);
        double[] grad = new double[2];
        model.policyGrad(weights, features, 2, 2, 0, 0, 1, grad);
        for (int j = 0; j < 2; j++) weights[j] -= 0.5 * grad[j];
        assertTrue(model.entropy(weights, features, 2, 2) > before);
    }

    @Test void oneGameIsLegalZeroSumAndFiniteAfterAnUpdate() {
        var state = MapGenerator.generate(11);
        var model = new PpoModel(3);
        var game = new PpoRollout(state).play(state, model, new Random(4));
        double reward = 0;
        for (var step : game.steps) reward += step.reward;
        assertEquals(0, reward, 1e-9);
        assertTrue(game.turns > 0);
        assertDoesNotThrow(() -> PpoProbe.update(model, game.steps));
        for (double w : model.build) assertTrue(Double.isFinite(w));
    }

    @Test void identicalGreedyPoliciesSwapToADrawOfWins() {
        PpoModel model = new PpoModel(5);
        int[] tally = PpoProbe.tally(model, model, 9_700_001L, 1);
        assertEquals(tally[0], tally[1], Arrays.toString(tally));
        assertEquals(0.5, PpoProbe.rate(tally), 1e-9);
    }

    @Test void terminalBonusCancelsAcrossSeats() {
        GameState state = MapGenerator.generate(12);
        state.score[0] = 10;
        state.turn = state.maxTurns - 1;
        var game = new PpoRollout(state).play(state, new PpoModel(6), new Random(7));
        double p0 = 0, p1 = 0;
        for (var step : game.steps) {
            if (step.player == 0) p0 += step.reward;
            else p1 += step.reward;
        }
        assertEquals(0, p0 + p1, 1e-9);
        assertTrue(p0 > 0);
    }

    @Test void greedyBotStaysLegalForOneTurn() {
        var state = MapGenerator.generate(11);
        Rules.updateConnections(state, new Pathing(state.size));
        Bot bot = new PpoGreedyBot(new PpoModel(1), "ppo-test");
        bot.init(state, 0);
        var actions = bot.play(state.copy(), 0, 36);
        var moves = new Rules.PlayerMoves();
        Rules.expand(state, 0, actions, new Pathing(state.size), moves, new Rules.TurnResult());
        assertNull(moves.error);
        assertTrue(moves.skipped.isEmpty());
        assertTrue(actions.stream().allMatch(a -> a.type != Action.Type.AUTOPLACE));
    }
}
