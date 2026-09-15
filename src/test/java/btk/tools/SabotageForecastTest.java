package btk.tools;

import btk.bot.Params;
import btk.core.*;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SabotageForecastTest {
    @Test void observationsUseProtocolStateAndOwnActionIncludingSimultaneousInk() throws Exception {
        for (int own : new int[]{-1, 1, 3}) for (int opponent : new int[]{-1, 1, 3}) {
            GameState before = state(); before.regionInstability[1] = 3;
            for (int c = 0; c < before.size; c++) if (before.regionId[c] == 1) before.instability[c] = 3;
            GameState after = before.copy();
            Rules.resolve(after, action(own), action(opponent), new Pathing(after.size));
            GameState visibleAfter = before.copy();
            Protocol.readTurn(new Protocol.TokenReader(new ByteArrayInputStream(
                    Protocol.turnBlock(after, 0).getBytes(StandardCharsets.UTF_8))), visibleAfter, 0);
            visibleAfter.syncRegionsFromCells(); visibleAfter.turn = 1;
            var model = new SabotageForecast(before, new Params());
            assertEquals(opponent, model.inferObservation(before, visibleAfter, own), own + "/" + opponent);
        }
    }

    @Test void unobservableNoEffectAndImpossibleTransitionAreNotInventedTargets() {
        GameState before = state(); Rules.ink(before, 3);
        GameState after = before.copy();
        Rules.resolve(after, action(-1), action(3), new Pathing(after.size));
        var model = new SabotageForecast(before, new Params());
        assertEquals(SabotageForecast.NONE, model.inferObservation(before, after, -1));
        after.regionInstability[1] = 2;
        assertEquals(SabotageForecast.UNKNOWN, model.inferObservation(before, after, -1));
        after.turn = 0;
        assertEquals(SabotageForecast.UNKNOWN, model.inferObservation(before, after, -1));
    }

    @Test void posteriorOnlyChangesAfterObservationAndResetsBetweenGames() {
        GameState state = state(); Params params = new Params();
        var model = new SabotageForecast(state, params);
        int[] owners = state.owner.clone();
        double[] initialWeights = model.weights.clone();
        model.predict(state, 1);
        assertArrayEquals(owners, state.owner);
        assertArrayEquals(initialWeights, model.weights);
        assertArrayEquals(model.fixed, model.adaptive, 1e-12);
        assertThrows(IllegalStateException.class, () -> model.predict(state, 1));
        model.observe(SabotageForecast.NONE);
        assertTrue(model.weights[4] > initialWeights[4]);
        model.predict(state, 1);
        assertTrue(model.adaptive[state.regionCount] > model.fixed[state.regionCount]);
        double[] learned = model.weights.clone();
        model.observe(SabotageForecast.UNKNOWN);
        assertArrayEquals(learned, model.weights);
        assertArrayEquals(initialWeights, new SabotageForecast(state, params).weights);
    }

    @Test void predictionIsNormalizedAndNeverZeroForObservableTargets() {
        GameState state = state(); Rules.ink(state, 3);
        var model = new SabotageForecast(state, new Params()); model.predict(state, 1);
        for (double[] distribution : model.probabilities) {
            assertEquals(1, Arrays.stream(distribution).sum(), 1e-12);
            assertEquals(0, distribution[3]);
            for (int i = 0; i < distribution.length; i++) if (i != 3) assertTrue(distribution[i] > 0);
        }
        assertEquals(1, Arrays.stream(model.adaptive).sum(), 1e-12);
        assertThrows(IllegalArgumentException.class, () -> new SabotageForecast(state, new Params("FORECAST_FLOOR=0")));
    }

    @Test void splitIsStableForTheSameOpponent() throws Exception {
        assertEquals(CgForecastStudy.split(12345), CgForecastStudy.split(12345));
        assertTrue(List.of("development", "holdout").contains(CgForecastStudy.split(12345)));
    }

    private static List<Action> action(int region) { return List.of(region < 0 ? Action.waitAction() : Action.disrupt(region)); }
    private static GameState state() {
        GameState s = new GameState(5, 2, new int[]{0, 1, 1, 1, 2, 3, 3, 4, 4, 4},
                new int[10], new int[]{0, 4}, new int[]{0, 0}, new int[][]{{1}, {}});
        s.owner[1] = s.owner[2] = s.owner[3] = 0;
        Rules.updateConnections(s, new Pathing(s.size));
        return s;
    }
}
