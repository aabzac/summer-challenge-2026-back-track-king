package btk.bot;

import btk.bot.neural.NeuralNet;
import btk.bot.neural.NeuralV2Bot;
import btk.bot.neural.NeuralV4Bot;
import btk.bot.neural.NeuralV4Weights;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.referee.MapGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.*;

class NeuralV4BotTest {
    @Test void completePassMatchesOfflineCollectorPolicyWithSameGeneratedWeightsNoiseAndMemory() {
        for (double noise : new double[]{0, .1}) for (int me = 0; me < 2; me++) {
            GameState state = MapGenerator.generate(2209000 + me);
            Pathing pathing = new Pathing(state.size);
            Rules.updateConnections(state, pathing);
            Params p = BotFactory.parameters("neural-v3?NOISE=" + noise);
            NeuralV2Bot offline = new NeuralV2Bot(p,
                    new NeuralNet(NeuralV4Weights.SABOTAGE_SIZES, NeuralV4Weights.SABOTAGE),
                    new NeuralNet(NeuralV4Weights.BUILD_SIZES, NeuralV4Weights.BUILD), null);
            NeuralV4Bot runtime = new NeuralV4Bot(p, () -> 0);
            Bot opponent = BotFactory.create("neural-v1?SEED=19");
            offline.init(state, me); runtime.init(state, me); opponent.init(state, 1 - me);
            assertEquals("neural-v4", runtime.name());
            for (int turn = 0; turn < 15 && !Rules.gameOver(state, pathing); turn++) {
                List<Action> expected = offline.play(state.copy(), me, 60_000);
                List<Action> actual = runtime.play(state.copy(), me, 36);
                assertEquals(Action.format(expected), Action.format(actual), "noise=" + noise + " seat=" + me + " turn=" + turn);
                assertEquals(offline.lastTarget, runtime.lastTarget);
                assertEquals(offline.lastBuildCandidates, runtime.lastBuildCandidates);
                assertEquals(0, runtime.lastAbortedPhase);
                List<Action> theirs = opponent.play(state.copy(), 1 - me, 60_000);
                var result = Rules.resolve(state, me == 0 ? actual : theirs, me == 1 ? actual : theirs, pathing);
                assertNull(result.moves[me].error);
                assertTrue(result.moves[me].skipped.isEmpty());
            }
        }
    }

    @Test void interruptedScoringDiscardsUnfinishedActionAndCanRecoverNextTurn() {
        class BuildClock implements LongSupplier {
            NeuralV4Bot bot;
            boolean expire = true;
            public long getAsLong() {
                return expire && bot.deadlinePhase == NeuralV4Bot.BUILD_SCORES ? 34_000_000L : 0;
            }
        }
        GameState state = MapGenerator.generate(2209000);
        BuildClock clock = new BuildClock();
        NeuralV4Bot bot = new NeuralV4Bot(BotFactory.parameters("neural-v3?NOISE=0"), clock);
        clock.bot = bot; bot.init(state, 0);
        List<Action> interrupted = bot.play(state, 0, 36);
        assertEquals(0, bot.lastPlacements);
        assertEquals(NeuralV4Bot.BUILD_SCORES, bot.lastAbortedPhase);
        assertTrue(interrupted.stream().noneMatch(action -> action.type == Action.Type.PLACE));
        var resolved = Rules.resolve(state, interrupted, List.of(Action.waitAction()), new Pathing(state.size));
        assertNull(resolved.moves[0].error);
        assertTrue(resolved.moves[0].skipped.isEmpty());
        clock.expire = false;
        List<Action> resumed = bot.play(state, 0, 36);
        assertEquals(0, bot.lastAbortedPhase);
        assertTrue(bot.lastPlacements > 0);
        resolved = Rules.resolve(state, resumed, List.of(Action.waitAction()), new Pathing(state.size));
        assertNull(resolved.moves[0].error);
        assertTrue(resolved.moves[0].skipped.isEmpty());
    }
}
