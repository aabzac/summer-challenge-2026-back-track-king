package btk.tools;

import btk.bot.Timer;
import btk.bot.v09.RolloutBotV09;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.core.SearchState;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static btk.tools.V09FullActionProbe.*;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic fixtures only; no prior panel or strength games are consumed by these tests. */
class V09FullActionProbeTest {
    private static GameState construction(int me) {
        GameState s = new GameState(5, 2, new int[]{0,1,2,3,4,5,6,7,8,9}, new int[10],
                new int[]{0,4}, new int[]{0,0}, new int[][]{{1},{}});
        s.owner[1] = me; s.owner[2] = 1 - me; s.turn = 10;
        Rules.updateConnections(s, new Pathing(s.size)); return s;
    }
    private static GameState filler(int me) {
        int[] regions = {0,1,2,3,4,5,6,7,7,7,7,7,7,7,8,9,9,9,9,9,10};
        GameState s = new GameState(7, 3, regions, new int[21], new int[]{0,6,0,6}, new int[]{0,0,2,2},
                new int[][]{{1},{},{},{}});
        for (int c = 1; c <= 5; c++) s.owner[c] = me;
        s.turn = 10; Rules.updateConnections(s, new Pathing(s.size)); return s;
    }
    private static GameState network(int me) {
        // One town-containing region removes disruption from this established mixed-network
        // fixture without changing frozen parameters. The missing shortcut is cell31.
        GameState s = new GameState(9, 7, new int[63], new int[63],
                new int[]{1,7}, new int[]{3,3}, new int[][]{{1},{}});
        for (int c : new int[]{29,30,21,22,23,32,33}) s.owner[c] = 1 - me;
        s.turn = 10; Rules.updateConnections(s, new Pathing(s.size)); return s;
    }
    private static GameState attacks(int me) {
        GameState s = new GameState(5, 1, new int[]{0,1,2,3,4}, new int[5],
                new int[]{0,4}, new int[]{0,0}, new int[][]{{1},{}});
        s.owner[1] = me; s.owner[2] = s.owner[3] = 1 - me;
        s.score[me] = 100; s.turn = 10;
        Rules.updateConnections(s, new Pathing(s.size)); return s;
    }
    private static Object field(Object object, String name) throws Exception {
        Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object);
    }
    private static String context(V09FullActionProbe probe) throws Exception {
        RolloutBotV09 bot = (RolloutBotV09) field(probe, "bot"); StringBuilder out = new StringBuilder();
        for (Field f : RolloutBotV09.class.getDeclaredFields()) {
            f.setAccessible(true); Object value = f.get(bot); out.append(f.getName()).append('=');
            if (value != null && value.getClass().isArray()) out.append(Arrays.deepToString(new Object[]{value}));
            else if (value instanceof SearchState board) out.append(ReplyStudy.stateHash(board.state));
            else if (value instanceof Timer) out.append(field(value, "startNs")).append('/').append(field(value, "deadlineNs"));
            else if (f.getType().isPrimitive()) out.append(value);
            out.append(';');
        }
        return out.toString();
    }

    @Test void finalFillerIsRecoveredAndBothAttacksUseIdenticalCompleteConstructionBothSeats() throws Exception {
        for (int me = 0; me < 2; me++) {
            GameState state = filler(me), before = state.copy(); V09FullActionProbe probe = prepare(state, me);
            Metadata meta = probe.metadata(); assertTrue(meta.supported(), meta.reason());
            assertTrue(meta.rawSelectionMatched()); assertTrue(meta.fillMatched());
            assertEquals(0, meta.rawSelected().cells().length); assertEquals(3, meta.fullSelected().cells().length);
            String saved = context(probe);
            // Region7 can contain these new own filler cells. It stays the same legal construction,
            // including its collateral exposure, rather than rerunning fill for the changed target.
            Comparison pair = probe.compare(7, Variant.BASELINE);
            for (Trace trace : List.of(pair.noAttack(), pair.attack())) {
                assertEquals(meta.root(), trace.root());
                assertEquals(probe.invokeOriginal(trace.target()).value(), trace.score().value());
                for (var reply : trace.replies()) {
                    assertArrayEquals(meta.fullSelected().cells(), reply.turns().get(0).cells()[me]);
                    assertEquals(trace.target(), reply.turns().get(0).targets()[me]);
                }
            }
            assertEquals(pair.attack().score().value() - pair.noAttack().score().value(), pair.delta());
            assertEquals(saved, context(probe)); ReplyProbe.sameState(before, state);
        }
    }

    @Test void realNetworkWinnerRetainsItsVerifiedNativeRootAndFullActionParity() throws Exception {
        for (int me = 0; me < 2; me++) {
            V09FullActionProbe probe = prepare(network(me), me); Metadata meta = probe.metadata();
            assertTrue(meta.supported(), meta.reason()); assertTrue(meta.networkRoot());
            assertTrue(meta.root() > meta.ownCount()); assertArrayEquals(new int[]{31}, meta.rawSelected().cells());
            assertTrue(meta.fullSelected().cells().length > meta.rawSelected().cells().length);
            String saved = context(probe);
            Trace actual = probe.trace(-1, Variant.BASELINE), changed = probe.trace(-1, Variant.NO_FUTURE_OWN_DISRUPT);
            assertEquals(meta.root(), actual.root());
            assertEquals(probe.invokeOriginal(-1).value(), actual.score().value());
            assertEquals(actual.score().value(), changed.score().value(), "No legal disruption exists on this protected board");
            assertEquals(saved, context(probe));
        }
    }

    @Test void disablingFutureOwnAttacksPreservesRootEvenWhenItAlreadyAttacks() throws Exception {
        for (int me = 0; me < 2; me++) {
            V09FullActionProbe probe = prepare(attacks(me), me); assertTrue(probe.metadata().supported());
            int target = probe.metadata().fullChooserTarget(); assertTrue(target >= 0);
            String saved = context(probe);
            for (int rootTarget : new int[]{-1, target}) {
                Trace nativeTrace = probe.trace(rootTarget, Variant.BASELINE);
                Trace changed = probe.trace(rootTarget, Variant.NO_FUTURE_OWN_DISRUPT);
                for (int reply = 0; reply < changed.replies().size(); reply++) {
                    var firstNative = nativeTrace.replies().get(reply).turns().get(0);
                    var firstChanged = changed.replies().get(reply).turns().get(0);
                    assertArrayEquals(firstNative.cells()[me], firstChanged.cells()[me]);
                    assertArrayEquals(firstNative.targets(), firstChanged.targets());
                    assertArrayEquals(firstNative.score(), firstChanged.score());
                    for (var turn : changed.replies().get(reply).turns())
                        if (turn.offset() > 0) assertEquals(-1, turn.targets()[me]);
                }
            }
            boolean futureAttack = false;
            for (var reply : probe.trace(-1, Variant.BASELINE).replies())
                for (var turn : reply.turns()) if (turn.offset() > 0 && turn.targets()[me] >= 0) futureAttack = true;
            assertTrue(futureAttack); assertEquals(saved, context(probe));
        }
    }

    @Test void nonemptyPortfolioIsSupportedByTheFullActionCounterfactual() throws Exception {
        for (int me = 0; me < 2; me++) {
            V09FullActionProbe probe = prepare(construction(me), me);
            assertTrue(probe.metadata().supported()); assertTrue(probe.metadata().ownCount() > 0);
            Trace changed = probe.trace(-1, Variant.NO_FUTURE_OWN_DISRUPT);
            for (var reply : changed.replies()) {
                assertArrayEquals(probe.selected().cells(), reply.turns().get(0).cells()[me]);
                for (var turn : reply.turns()) if (turn.offset() > 0) assertEquals(-1, turn.targets()[me]);
            }
        }
    }

    @Test void retainedOpponentIsRecomputedOnCurrentBoardAndMatchesForecastAtSharedState() throws Exception {
        for (int me = 0; me < 2; me++) {
            GameState state = construction(me); V09FullActionProbe probe = prepare(state, me);
            assertTrue(probe.metadata().supported()); String saved = context(probe);
            Trace forecast = probe.trace(-1, Variant.BASELINE);
            for (var reply : forecast.replies()) {
                var predicted = probe.opponentAction(state, reply.reply()); var first = reply.turns().get(0);
                assertArrayEquals(first.cells()[1 - me], predicted.cells()); assertEquals(first.targets()[1 - me], predicted.target());
                GameState divergent = state.copy();
                for (int cell : predicted.cells()) divergent.owner[cell] = me;
                Rules.updateConnections(divergent, new Pathing(divergent.size));
                String before = ReplyStudy.stateHash(divergent);
                var updated = probe.opponentAction(divergent, reply.reply());
                for (int cell : updated.cells()) assertTrue(divergent.canBuild(cell));
                for (int occupied : predicted.cells()) for (int cell : updated.cells()) assertNotEquals(occupied, cell);
                assertEquals(before, ReplyStudy.stateHash(divergent));
            }
            assertEquals(saved, context(probe));
        }
    }

    @Test void openingIsExplicitlyUnsupportedAndRetainsActualFallbackInsteadOfGuessingARoot() throws Exception {
        GameState state = new GameState(5, 1, new int[5], new int[5], new int[]{0,4}, new int[]{0,0}, new int[][]{{1},{}});
        Rules.updateConnections(state, new Pathing(state.size)); V09FullActionProbe probe = prepare(state, 0);
        assertFalse(probe.metadata().supported()); assertEquals("PROTECTED_OPENING", probe.metadata().reason());
        assertEquals(-1, probe.metadata().root()); assertNull(probe.metadata().rawSelected());
        assertFalse(probe.eligible()); assertTrue(probe.selected().cells().length > 0);
        assertThrows(IllegalStateException.class, () -> probe.trace(-1, Variant.BASELINE));
        assertThrows(IllegalStateException.class, () -> probe.opponentAction(state, 0));
    }

    @Test void lastTurnHasNoFutureOverrideAndTargetValidationDoesNotMutateContext() throws Exception {
        for (int me = 0; me < 2; me++) {
            GameState state = attacks(me); state.turn = 99;
            V09FullActionProbe probe = prepare(state, me); String saved = context(probe);
            Trace baseline = probe.trace(-1, Variant.BASELINE), noFuture = probe.trace(-1, Variant.NO_FUTURE_OWN_DISRUPT);
            assertEquals(baseline.score().value(), noFuture.score().value());
            assertEquals(1, baseline.replies().get(0).turns().size());
            assertThrows(IllegalStateException.class, () -> probe.trace(0, Variant.BASELINE), "Town-region target must be rejected");
            assertThrows(IllegalStateException.class, () -> probe.trace(-2, Variant.BASELINE));
            assertEquals(saved, context(probe));
        }
    }
}
