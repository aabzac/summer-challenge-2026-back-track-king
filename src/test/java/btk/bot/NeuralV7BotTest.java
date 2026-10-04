package btk.bot;

import btk.bot.neural.NeuralV6Bot;
import btk.bot.neural.NeuralV7Bot;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.referee.MapGenerator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Deadline safety and preserved neural decisions for the V7 timeout-only extension. */
class NeuralV7BotTest {
    private static final class PhaseClock implements LongSupplier {
        IntSupplier phase = () -> 0, placements = () -> 0;
        int trigger = -1, atPlacements, allowCalls = 1, calls;
        int hardPhase = -1, hardAllowCalls, hardCalls, hardAfterPlacements;
        long softTime = 34_000_000L, now;
        public long getAsLong() {
            if (phase.getAsInt() == trigger && placements.getAsInt() >= atPlacements
                    && ++calls > allowCalls) now = Math.max(now, softTime);
            if (phase.getAsInt() == hardPhase && placements.getAsInt() >= hardAfterPlacements
                    && ++hardCalls > hardAllowCalls) now = 36_000_000L;
            return now;
        }
        void bind(NeuralV7Bot bot) { phase = () -> bot.deadlinePhase; placements = () -> bot.lastPlacements; }
        void bind(NeuralV6Bot bot) { phase = () -> bot.deadlinePhase; placements = () -> bot.lastPlacements; }
    }

    private static Params params(boolean fill) {
        Params p = BotFactory.parameters("neural-v6?NOISE=0");
        p.USE_NEURAL_FALLBACK = fill;
        p.NEURAL_FALLBACK_MAX_INSTABILITY = 0;
        return p;
    }

    private static GameState board() {
        int[] regions = new int[21];
        for (int c = 0; c < regions.length; c++) regions[c] = c;
        GameState s = new GameState(7, 3, regions, new int[21], new int[]{0, 6},
                new int[]{0, 2}, new int[][]{{1}, {0}});
        s.owner[10] = 0;
        Rules.updateConnections(s, new Pathing(s.size));
        return s;
    }

    private static List<Integer> placed(GameState s, List<Action> actions) {
        return actions.stream().filter(a -> a.type == Action.Type.PLACE).map(a -> s.cell(a.x, a.y)).toList();
    }

    private static void assertLegal(GameState s, int me, List<Action> actions) {
        var result = Rules.resolve(s.copy(), me == 0 ? actions : List.of(Action.waitAction()),
                me == 1 ? actions : List.of(Action.waitAction()), new Pathing(s.size));
        assertNull(result.moves[me].error);
        assertTrue(result.moves[me].skipped.isEmpty(), result.moves[me].skipped.toString());
    }

    private static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }

    @Test void ampleBudgetPreservesV6ActionsNoiseAndMemoryWithFallbackEnabled() {
        for (double noise : new double[]{0, .1}) for (int me = 0; me < 2; me++) {
            GameState state = MapGenerator.generate(2209000 + me);
            Pathing paths = new Pathing(state.size);
            Rules.updateConnections(state, paths);
            Params p = params(true); p.NOISE = noise;
            NeuralV6Bot original = new NeuralV6Bot(p, () -> 0);
            NeuralV7Bot candidate = new NeuralV7Bot(p, () -> 0);
            Bot opponent = BotFactory.create("neural-v1?SEED=19");
            original.init(state, me); candidate.init(state, me); opponent.init(state, 1 - me);
            for (int turn = 0; turn < 15 && !Rules.gameOver(state, paths); turn++) {
                List<Action> expected = original.play(state.copy(), me, 36);
                List<Action> actual = candidate.play(state.copy(), me, 36);
                assertEquals(Action.format(expected), Action.format(actual));
                assertEquals(original.lastTarget, candidate.lastTarget);
                assertEquals(original.lastPlacements, candidate.lastPlacements);
                assertEquals(0, candidate.lastFallbackPlacements);
                List<Action> theirs = opponent.play(state.copy(), 1 - me, 60_000);
                Rules.resolve(state, me == 0 ? actual : theirs, me == 1 ? actual : theirs, paths);
            }
        }
    }

    @Test void disabledFallbackPreservesEveryV6DeadlineExit() {
        for (int phase : new int[]{1, 2, 3, 4, 5, 6}) for (int completed : new int[]{0, 1}) {
            if (phase < 4 && completed > 0) continue;
            GameState s = MapGenerator.generate(2209000);
            PhaseClock a = new PhaseClock(), b = new PhaseClock();
            a.trigger = b.trigger = phase; a.atPlacements = b.atPlacements = completed;
            NeuralV6Bot old = new NeuralV6Bot(params(false), a); a.bind(old); old.init(s, 0);
            NeuralV7Bot next = new NeuralV7Bot(params(false), b); b.bind(next); next.init(s, 0);
            assertEquals(Action.format(old.play(s.copy(), 0, 36)), Action.format(next.play(s.copy(), 0, 36)));
            assertEquals(old.lastAbortedPhase, next.lastAbortedPhase);
            assertEquals(0, next.lastFallbackPlacements);
        }
    }

    @Test void initialAndSabotageInterruptionsFillWithoutTargetOrInputMutation() throws Exception {
        for (int phase : new int[]{1, 2, 3}) {
            GameState s = board(); int[] owners = s.owner.clone();
            PhaseClock clock = new PhaseClock(); clock.trigger = phase;
            NeuralV7Bot bot = new NeuralV7Bot(params(true), clock); clock.bind(bot); bot.init(s, 0);
            List<Action> actions = bot.play(s, 0, 36);
            assertEquals(phase, bot.lastAbortedPhase);
            assertEquals(-1, bot.lastTarget);
            assertEquals(3, bot.lastFallbackPaint);
            assertEquals(3, bot.lastFallbackPlacements);
            assertFalse(actions.stream().anyMatch(a -> a.type == Action.Type.DISRUPT));
            assertArrayEquals(owners, s.owner);
            assertLegal(s, 0, actions);
            Object features = field(bot, "features");
            assertEquals(s.turn, field(features, "observedTurn"));
            assertEquals(-1, field(features, "myLastTarget"));
            boolean[] marked = (boolean[]) field(features, "placedThisTurn");
            for (int cell : placed(s, actions)) assertTrue(marked[cell]);
        }
    }

    @Test void incompleteBuildRetainsNeuralPrefixAndCompletedTarget() {
        for (int phase : new int[]{4, 5, 6}) for (int completed : new int[]{0, 1}) {
            GameState s = MapGenerator.generate(2209000);
            PhaseClock a = new PhaseClock(), b = new PhaseClock();
            a.trigger = b.trigger = phase; a.atPlacements = b.atPlacements = completed;
            NeuralV6Bot old = new NeuralV6Bot(params(false), a); a.bind(old); old.init(s, 0);
            NeuralV7Bot next = new NeuralV7Bot(params(true), b); b.bind(next); next.init(s, 0);
            List<Action> before = old.play(s.copy(), 0, 36), after = next.play(s.copy(), 0, 36);
            List<Integer> prefix = placed(s, before), all = placed(s, after);
            assertEquals(prefix, all.subList(0, prefix.size()));
            assertEquals(old.lastTarget, next.lastTarget);
            assertEquals(old.lastAbortedPhase, next.lastAbortedPhase);
            assertEquals(old.lastPlacements, next.lastPlacements - next.lastFallbackPlacements);
            for (int i = prefix.size(); i < all.size(); i++) assertNotEquals(next.lastTarget, s.regionId[all.get(i)]);
            assertEquals(before.stream().filter(a1 -> a1.type == Action.Type.DISRUPT).map(Action::toString).toList(),
                    after.stream().filter(a1 -> a1.type == Action.Type.DISRUPT).map(Action::toString).toList());
            assertLegal(s, 0, after);
        }
    }

    @Test void sixMillisecondReserveBreakCanFillWithoutClaimingAFeatureAbort() {
        GameState s = board();
        PhaseClock clock = new PhaseClock(); clock.trigger = NeuralV7Bot.BUILD_PREPARE;
        clock.atPlacements = 1; clock.allowCalls = 0; clock.softTime = 31_000_000L;
        NeuralV7Bot bot = new NeuralV7Bot(params(true), clock); clock.bind(bot); bot.init(s, 0);
        List<Action> actions = bot.play(s, 0, 36);
        assertEquals(0, bot.lastAbortedPhase);
        assertEquals(1, bot.lastPlacements - bot.lastFallbackPlacements);
        assertTrue(bot.lastFallbackPlacements > 0);
        assertLegal(s, 0, actions);
    }

    @Test void zeroBudgetAndHardExpiryDoNotStartFallback() throws Exception {
        GameState s = board();
        NeuralV7Bot zero = new NeuralV7Bot(params(true), () -> 0); zero.init(s, 0);
        assertEquals("WAIT", Action.format(zero.play(s, 0, 0)));
        assertEquals(0, zero.lastFallbackPlacements);
        assertEquals(s.turn, field(field(zero, "features"), "observedTurn"));
        PhaseClock clock = new PhaseClock(); clock.trigger = NeuralV7Bot.INITIAL; clock.softTime = 36_000_000L;
        NeuralV7Bot bot = new NeuralV7Bot(params(true), clock); clock.bind(bot); bot.init(s, 0);
        assertEquals("WAIT", Action.format(bot.play(s, 0, 36)));
        assertEquals(0, clock.hardCalls);
        assertEquals(0, bot.lastFallbackPlacements);
    }

    @Test void hardExpiryDuringPreparationPartialScanAndCommitDiscardsUncommittedFallback() {
        for (int phase : new int[]{8, 9, 10}) {
            GameState s = board();
            PhaseClock clock = new PhaseClock(); clock.trigger = NeuralV7Bot.INITIAL;
            clock.hardPhase = phase; clock.hardAllowCalls = phase == 10 ? 0 : 5;
            NeuralV7Bot bot = new NeuralV7Bot(params(true), clock); clock.bind(bot); bot.init(s, 0);
            assertEquals("WAIT", Action.format(bot.play(s, 0, 36)));
            assertEquals(0, bot.lastFallbackPlacements);
            assertEquals(NeuralV7Bot.INITIAL, bot.lastAbortedPhase);
        }
    }

    @Test void hardExpiryDuringSecondFallbackScanKeepsFirstLegalCell() {
        GameState s = board();
        PhaseClock clock = new PhaseClock(); clock.trigger = NeuralV7Bot.INITIAL;
        clock.hardPhase = NeuralV7Bot.FALLBACK_SCAN; clock.hardAfterPlacements = 1; clock.hardAllowCalls = 5;
        NeuralV7Bot bot = new NeuralV7Bot(params(true), clock); clock.bind(bot); bot.init(s, 0);
        List<Action> actions = bot.play(s, 0, 36);
        assertEquals(1, bot.lastFallbackPlacements);
        assertEquals(List.of(3), placed(s, actions));
        assertLegal(s, 0, actions);
    }

    @Test void noOwnRailsDoesNotInventTownSeedingAndCompletedNoCandidatesDoesNotFill() {
        GameState empty = board(); Arrays.fill(empty.owner, GameState.NO_TRACK);
        PhaseClock clock = new PhaseClock(); clock.trigger = NeuralV7Bot.INITIAL;
        NeuralV7Bot bot = new NeuralV7Bot(params(true), clock); clock.bind(bot); bot.init(empty, 0);
        assertEquals("WAIT", Action.format(bot.play(empty, 0, 36)));
        assertEquals(0, bot.lastFallbackPlacements);
        GameState full = board();
        for (int c = 0; c < full.size; c++) if (!full.isTown(c)) full.owner[c] = 0;
        Rules.updateConnections(full, new Pathing(full.size));
        Params p = params(true); p.USE_DISRUPT = false;
        NeuralV7Bot complete = new NeuralV7Bot(p, () -> 0); complete.init(full, 0);
        assertEquals("WAIT", Action.format(complete.play(full, 0, 36)));
        assertEquals(0, complete.lastAbortedPhase);
        assertEquals(0, complete.lastFallbackPlacements);
    }

    @Test void fallbackRejectsOccupiedInkedUnaffordableAndUnstableCells() {
        GameState s = board();
        s.owner[3] = 1;
        s.inked[9] = true;
        s.regionInstability[s.regionId[11]] = 1;
        s.terrain[17] = GameState.RIVER;
        s.terrain[16] = s.terrain[18] = GameState.MOUNTAIN;
        PhaseClock clock = new PhaseClock(); clock.trigger = NeuralV7Bot.INITIAL;
        NeuralV7Bot bot = new NeuralV7Bot(params(true), clock); clock.bind(bot); bot.init(s, 0);
        List<Action> actions = bot.play(s, 0, 36);
        assertEquals(List.of(17), placed(s, actions));
        assertEquals(2, bot.lastFallbackPaint);
        assertLegal(s, 0, actions);
    }

    @Test void earningCountThenProtectedRegionThenCostThenCellOrderChooseFallback() {
        for (int variant = 0; variant < 4; variant++) {
            int[] regions = new int[21];
            for (int c = 0; c < regions.length; c++) regions[c] = c;
            if (variant == 1) regions[9] = 0; // one earning neighbour loses to two, even protected
            if (variant == 2) regions[12] = 6; // protected region wins before construction cost
            GameState s = new GameState(7, 3, regions, new int[21], new int[]{0, 6},
                    new int[]{0, 0}, new int[][]{{1}, {0}});
            for (int c : new int[]{7, 14, 15, 16, 17, 18, 19, 20, 13}) s.owner[c] = 0;
            if (variant == 2) s.terrain[12] = GameState.RIVER;
            if (variant == 3) s.terrain[8] = GameState.RIVER;
            Rules.updateConnections(s, new Pathing(s.size));
            assertTrue(s.pairActive(0));
            PhaseClock clock = new PhaseClock(); clock.trigger = NeuralV7Bot.INITIAL;
            NeuralV7Bot bot = new NeuralV7Bot(params(true), clock); clock.bind(bot); bot.init(s, 0);
            List<Action> actions = bot.play(s, 0, 36);
            assertEquals(variant >= 2 ? 12 : 8, placed(s, actions).get(0));
            assertLegal(s, 0, actions);
        }
    }

    @Test void protectedRegionRemainsEligibleAboveInstabilityThresholdAndBothSeatsWork() {
        for (int me = 0; me < 2; me++) {
            GameState s = board();
            s.owner[10] = me;
            s.regionId[3] = s.regionId[s.townCell[0]];
            s.regionInstability[s.regionId[3]] = 3;
            s.regionInstability[s.regionId[9]] = 1;
            s.regionInstability[s.regionId[11]] = 1;
            s.regionInstability[s.regionId[17]] = 1;
            PhaseClock clock = new PhaseClock(); clock.trigger = NeuralV7Bot.INITIAL;
            NeuralV7Bot bot = new NeuralV7Bot(params(true), clock); clock.bind(bot); bot.init(s, me);
            List<Action> actions = bot.play(s, me, 36);
            assertEquals(3, placed(s, actions).get(0));
            assertLegal(s, me, actions);
        }
    }

    @Test void interruptedOverlayAndFeatureMemoryRecoverOnNextPublicState() throws Exception {
        for (int hardPhase : new int[]{NeuralV7Bot.FALLBACK_PREPARE, NeuralV7Bot.FALLBACK_SCAN, NeuralV7Bot.FALLBACK_COMMIT}) {
            GameState s = board();
            PhaseClock clock = new PhaseClock(); clock.trigger = NeuralV7Bot.INITIAL;
            clock.hardPhase = hardPhase; clock.hardAllowCalls = 2;
            NeuralV7Bot bot = new NeuralV7Bot(params(true), clock); clock.bind(bot); bot.init(s, 0);
            List<Action> first = bot.play(s.copy(), 0, 36);
            Pathing paths = new Pathing(s.size);
            Rules.resolve(s, first, List.of(Action.waitAction()), paths);
            clock.trigger = clock.hardPhase = -1; clock.now = 0;
            List<Action> next = bot.play(s.copy(), 0, 36);
            assertEquals(0, bot.lastAbortedPhase);
            assertEquals(0, bot.lastFallbackPlacements);
            assertEquals(s.turn, field(field(bot, "features"), "observedTurn"));
            assertTrue(bot.lastPlacements > 0);
            assertLegal(s, 0, next);
        }
        assertThrows(IllegalArgumentException.class, () -> {
            Params p = params(true); p.NEURAL_FALLBACK_MAX_INSTABILITY = -1; new NeuralV7Bot(p);
        });
    }
}
