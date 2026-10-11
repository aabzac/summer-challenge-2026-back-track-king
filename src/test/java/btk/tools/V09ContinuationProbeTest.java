package btk.tools;

import btk.bot.Timer;
import btk.bot.v09.RolloutBotV09;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.core.SearchState;
import java.lang.reflect.Field;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static btk.tools.V09ContinuationProbe.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual frozen-policy preparation on synthetic core maps; no retained panel or new games. */
class V09ContinuationProbeTest {
    private static GameState construction(int me) {
        GameState s = new GameState(5, 2, new int[]{0,1,2,3,4,5,6,7,8,9}, new int[10],
                new int[]{0,4}, new int[]{0,0}, new int[][]{{1},{}});
        s.owner[1] = me; s.owner[2] = 1 - me; s.turn = 10;
        Rules.updateConnections(s, new Pathing(s.size)); return s;
    }
    private static GameState noConstruction(int me, int turn) {
        GameState s = new GameState(5, 1, new int[]{0,1,2,3,4}, new int[5],
                new int[]{0,4}, new int[]{0,0}, new int[][]{{1},{}});
        s.owner[1] = me; s.owner[2] = s.owner[3] = 1 - me;
        s.score[me] = 100; s.turn = turn;
        Rules.updateConnections(s, new Pathing(s.size)); return s;
    }
    private static Object field(Object object, String name) throws Exception {
        Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object);
    }
    private static String context(V09ContinuationProbe probe) throws Exception {
        RolloutBotV09 bot = (RolloutBotV09) field(probe, "bot");
        StringBuilder result = new StringBuilder();
        for (Field field : RolloutBotV09.class.getDeclaredFields()) {
            field.setAccessible(true); Object value = field.get(bot);
            result.append(field.getName()).append('=');
            if (value != null && value.getClass().isArray()) result.append(Arrays.deepToString(new Object[]{value}));
            else if (value instanceof SearchState board) result.append(ReplyStudy.stateHash(board.state));
            else if (value instanceof Timer) result.append(field(value, "startNs")).append('/').append(field(value, "deadlineNs"));
            else if (field.getType().isPrimitive()) result.append(value);
            result.append(';');
        }
        return result.toString();
    }

    @Test void traceMatchesActualPrivateRolloutAndRestoresContextForAllSyntheticRootsBothSeats() throws Exception {
        for (int me = 0; me < 2; me++) {
            GameState state = construction(me), before = state.copy();
            V09ContinuationProbe probe = prepare(state, me);
            assertTrue(probe.ownCount() > 0); assertFalse(probe.tuples().isEmpty());
            String saved = context(probe);
            for (Tuple tuple : probe.tuples()) {
                Score original = probe.invokeOriginal(tuple.root(), tuple.target());
                Trace traced = probe.trace(tuple.root(), tuple.target(), Variant.BASELINE);
                assertEquals(original.value(), traced.score().value());
                assertArrayEquals(original.replyValues(), traced.score().replyValues());
                assertEquals(probe.replies(), traced.replies().size());
                for (ReplyTrace reply : traced.replies()) {
                    assertFalse(reply.turns().isEmpty());
                    assertArrayEquals(tuple.cells(), reply.turns().get(0).cells()[me]);
                    assertEquals(tuple.target(), reply.turns().get(0).targets()[me]);
                }
                assertEquals(saved, context(probe));
            }
            ReplyProbe.sameState(before, state);
        }
    }

    @Test void noFutureOwnDisruptionIsOnlyAnEmptyPortfolioNoAttackRootCounterfactual() throws Exception {
        for (int me = 0; me < 2; me++) {
            final int player = me;
            GameState state = noConstruction(me, 10), before = state.copy();
            V09ContinuationProbe probe = prepare(state, me);
            assertEquals(0, probe.ownCount()); assertEquals(0, probe.opponentCount());
            int root = probe.waitRoot(); String saved = context(probe);
            TargetInfo target = probe.targetInfo(new int[0]); assertTrue(target.chosen() >= 0);
            Trace baseline = probe.trace(root, -1, Variant.BASELINE);
            Trace noFuture = probe.trace(root, -1, Variant.NO_FUTURE_OWN_DISRUPT);
            Turn nativeFirst = baseline.replies().get(0).turns().get(0);
            Turn changedFirst = noFuture.replies().get(0).turns().get(0);
            assertArrayEquals(nativeFirst.targets(), changedFirst.targets());
            assertArrayEquals(nativeFirst.score(), changedFirst.score());
            assertTrue(baseline.replies().stream().flatMap(r -> r.turns().stream())
                    .anyMatch(t -> t.offset() > 0 && t.targets()[player] >= 0));
            for (ReplyTrace reply : noFuture.replies()) for (Turn turn : reply.turns()) {
                assertEquals(-1, turn.targets()[me]); assertEquals(0, turn.cells()[me].length);
            }
            assertTrue(noFuture.replies().get(0).turns().get(1).proposedTargets()[me] >= 0,
                    "Chooser still runs; only its future emitted target is overridden");
            assertThrows(IllegalStateException.class,
                    () -> probe.trace(root, target.chosen(), Variant.NO_FUTURE_OWN_DISRUPT));
            assertEquals(saved, context(probe)); ReplyProbe.sameState(before, state);
        }
        V09ContinuationProbe nonempty = prepare(construction(0), 0);
        assertThrows(IllegalStateException.class,
                () -> nonempty.trace(nonempty.waitRoot(), -1, Variant.NO_FUTURE_OWN_DISRUPT));
    }

    @Test void passiveOpponentKeepsRootAndOwnContinuationAndIsSeparateFromNoFutureAttack() throws Exception {
        for (int me = 0; me < 2; me++) {
            V09ContinuationProbe probe = prepare(noConstruction(me, 10), me);
            String saved = context(probe);
            Trace passive = probe.trace(probe.waitRoot(), -1, Variant.PASSIVE_OPPONENT);
            boolean futureOwnAttack = false;
            for (ReplyTrace reply : passive.replies()) for (Turn turn : reply.turns()) {
                assertEquals(0, turn.cells()[1 - me].length); assertEquals(-1, turn.targets()[1 - me]);
                if (turn.offset() == 0) assertEquals(-1, turn.targets()[me]);
                else if (turn.targets()[me] >= 0) futureOwnAttack = true;
            }
            assertTrue(futureOwnAttack); assertEquals(saved, context(probe));
        }
    }

    @Test void finalRealTurnRemainsEligibleButHasNoFutureToOverride() throws Exception {
        for (int me = 0; me < 2; me++) {
            V09ContinuationProbe probe = prepare(noConstruction(me, 99), me);
            assertEquals(0, probe.ownCount());
            Trace baseline = probe.trace(probe.waitRoot(), -1, Variant.BASELINE);
            Trace changed = probe.trace(probe.waitRoot(), -1, Variant.NO_FUTURE_OWN_DISRUPT);
            assertEquals(baseline.score().value(), changed.score().value());
            for (ReplyTrace reply : changed.replies()) {
                assertEquals(1, reply.turns().size()); assertTrue(reply.turns().get(0).terminal());
                assertEquals(99, reply.turns().get(0).turn());
            }
        }
    }

    @Test void returnedPlanAndRootsDoNotExposeMutableBotDecisionArrays() throws Exception {
        V09ContinuationProbe probe = prepare(construction(0), 0);
        String saved = context(probe);
        if (probe.selected().cells().length > 0) probe.selected().cells()[0] = -10;
        for (Root root : probe.roots()) if (root.cells().length > 0) root.cells()[0] = -10;
        probe.targetInfo(new int[0]);
        assertEquals(saved, context(probe));
    }
}
