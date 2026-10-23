package btk.bot;

import btk.bot.neural.NeuralV2Bot;
import btk.bot.neural.NeuralV3Bot;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.referee.MapGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.*;

class NeuralV3BotTest {
    private static final class PhaseClock implements LongSupplier {
        NeuralV3Bot bot;
        int phase, placements, calls, allowCalls = 1;
        long now;
        public long getAsLong() {
            if (bot != null && bot.deadlinePhase == phase && bot.lastPlacements >= placements
                    && ++calls > allowCalls) now = 34_000_000L;
            return now;
        }
    }

    @Test void unlimitedClockPreservesWeightsActionsNoiseAndMemoryOnBothSeats() {
        for (double noise : new double[]{0, .1}) for (int me = 0; me < 2; me++) {
            GameState state = MapGenerator.generate(2209000 + me);
            Pathing pathing = new Pathing(state.size);
            Rules.updateConnections(state, pathing);
            Params p = BotFactory.parameters("neural-v2?NOISE=" + noise);
            NeuralV2Bot original = new NeuralV2Bot(p);
            NeuralV3Bot candidate = new NeuralV3Bot(p, () -> 0);
            Bot opponent = BotFactory.create("neural-v1?SEED=19");
            original.init(state, me); candidate.init(state, me); opponent.init(state, 1 - me);
            for (int turn = 0; turn < 15 && !Rules.gameOver(state, pathing); turn++) {
                List<Action> expected = original.play(state.copy(), me, 60_000);
                List<Action> actual = candidate.play(state.copy(), me, 36);
                assertEquals(Action.format(expected), Action.format(actual), "noise=" + noise + " seat=" + me + " turn=" + turn);
                assertEquals(original.lastTarget, candidate.lastTarget);
                assertEquals(original.lastBuildCandidates, candidate.lastBuildCandidates);
                assertEquals(0, candidate.lastAbortedPhase);
                List<Action> theirs = opponent.play(state.copy(), 1 - me, 60_000);
                var result = Rules.resolve(state, me == 0 ? actual : theirs, me == 1 ? actual : theirs, pathing);
                assertNull(result.moves[me].error);
                assertTrue(result.moves[me].skipped.isEmpty());
            }
        }
    }

    @Test void unfinishedInitialOrSabotagePassReturnsWaitAndRecordsNoTarget() throws Exception {
        for (int phase : new int[]{NeuralV3Bot.INITIAL, NeuralV3Bot.SABOTAGE_FEATURES, NeuralV3Bot.SABOTAGE_SCORES}) {
            GameState state = MapGenerator.generate(2209000);
            PhaseClock clock = new PhaseClock(); clock.phase = phase;
            NeuralV3Bot bot = new NeuralV3Bot(BotFactory.parameters("neural-v3"), clock);
            clock.bot = bot; bot.init(state, 0);
            assertEquals("WAIT", Action.format(bot.play(state, 0, 36)), "phase=" + phase);
            assertEquals(-1, bot.lastTarget);
            assertEquals(0, bot.lastPlacements);
            assertEquals(phase, bot.lastAbortedPhase);
            var featuresField = NeuralV3Bot.class.getDeclaredField("features"); featuresField.setAccessible(true);
            Object features = featuresField.get(bot);
            var targetField = features.getClass().getDeclaredField("myLastTarget"); targetField.setAccessible(true);
            assertEquals(-1, targetField.getInt(features));
            var observedField = features.getClass().getDeclaredField("observedTurn"); observedField.setAccessible(true);
            assertEquals(state.turn, observedField.getInt(features));
        }
    }

    @Test void incompleteBuildFeaturesOrScorePassKeepsOnlyCompletedActions() {
        for (int phase : new int[]{NeuralV3Bot.BUILD_FEATURES, NeuralV3Bot.BUILD_SCORES}) {
            GameState state = MapGenerator.generate(2209000);
            Params params = BotFactory.parameters("neural-v3?NOISE=0");
            NeuralV3Bot full = new NeuralV3Bot(params, () -> 0); full.init(state, 0);
            List<Action> expected = full.play(state.copy(), 0, 36);
            for (int completed : new int[]{0, 1}) {
                PhaseClock clock = new PhaseClock(); clock.phase = phase; clock.placements = completed;
                NeuralV3Bot bot = new NeuralV3Bot(params, clock); clock.bot = bot; bot.init(state, 0);
                List<Action> actual = bot.play(state.copy(), 0, 36);
                assertEquals(completed, bot.lastPlacements, "phase=" + phase);
                assertEquals(full.lastTarget, bot.lastTarget);
                assertEquals(phase, bot.lastAbortedPhase);
                assertEquals(expected.stream().filter(a -> a.type == Action.Type.PLACE).limit(completed).map(Action::toString).toList(),
                        actual.stream().filter(a -> a.type == Action.Type.PLACE).map(Action::toString).toList());
                assertEquals(expected.stream().filter(a -> a.type == Action.Type.DISRUPT).map(Action::toString).toList(),
                        actual.stream().filter(a -> a.type == Action.Type.DISRUPT).map(Action::toString).toList());
                var resolved = Rules.resolve(state.copy(), actual, List.of(Action.waitAction()), new Pathing(state.size));
                assertNull(resolved.moves[0].error);
                assertTrue(resolved.moves[0].skipped.isEmpty());
            }
        }
    }

    @Test void deadlineBeforeFirstBuildAndZeroBudgetAdvanceMemoryWithoutStartingWork() throws Exception {
        for (long budget : new long[]{0, 2}) {
            GameState state = MapGenerator.generate(2209001); state.turn = 4;
            NeuralV3Bot bot = new NeuralV3Bot(BotFactory.parameters("neural-v3"), () -> 0); bot.init(state, 0);
            assertEquals("WAIT", Action.format(bot.play(state, 0, budget)));
            assertEquals(-1, bot.lastTarget);
            assertEquals(0, bot.lastSabotageCandidates);
            assertEquals(0, bot.lastBuildCandidates);
        }
        PhaseClock clock = new PhaseClock(); clock.phase = NeuralV3Bot.BUILD_PREPARE; clock.allowCalls = 0;
        GameState state = MapGenerator.generate(2209001);
        NeuralV3Bot bot = new NeuralV3Bot(BotFactory.parameters("neural-v3"), clock); clock.bot = bot; bot.init(state, 0);
        List<Action> actions = bot.play(state, 0, 36);
        assertEquals(0, bot.lastPlacements);
        assertEquals(0, bot.lastBuildCandidates);
        assertTrue(actions.stream().anyMatch(a -> a.type == Action.Type.DISRUPT));
        assertThrows(IllegalArgumentException.class, () -> new NeuralV3Bot(new Params(LiveTargetExperimentParams.V10_OFF + ",NEURAL_DEADLINE_RESERVE_MS=-1")));
    }
}
