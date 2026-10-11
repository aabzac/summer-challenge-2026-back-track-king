package btk.tools;

import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import java.util.Arrays;
import java.util.List;
import btk.tools.V09ContinuationProbe.Plan;
import btk.tools.V09ContinuationProbe.ReplyTrace;
import btk.tools.V09ContinuationProbe.Score;
import btk.tools.V09ContinuationProbe.Turn;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static btk.tools.V09FullActionEpisode.*;

class V09FullActionEpisodeTest {
    @Test void pairedInitialInterventionPreservesCellsOpponentAndCallerOnBothSeats() throws Exception {
        for (int me = 0; me < 2; me++) {
            GameState initial = line(me, 10); GameState before = initial.copy();
            var prepared = V09FullActionProbe.prepare(initial, me);
            assertTrue(prepared.metadata().supported());
            int target = prepared.metadata().nominatedTarget(); assertTrue(target >= 0);
            Result result = run(initial, me, target);
            assertEquals(8, result.horizon());
            assertEquals(prepared.metadata().replies() + 1, result.comparisons().size());
            for (var pair : result.comparisons()) {
                assertTrue(pair.noHit().complete()); assertTrue(pair.attackNow().complete());
                assertTrue(pair.noHit().steps() <= 8); assertTrue(pair.attackNow().steps() <= 8);
                var noHit = pair.noHit().turns().get(0); var hit = pair.attackNow().turns().get(0);
                assertArrayEquals(prepared.selected().cells(), noHit.ownAction().cells());
                assertArrayEquals(noHit.ownAction().cells(), hit.ownAction().cells());
                assertEquals(-1, noHit.ownAction().target()); assertEquals(target, hit.ownAction().target());
                assertEquals(noHit.opponentAction().action(), hit.opponentAction().action());
                if (pair.opponentRegime().equals(PASSIVE)) {
                    assertEquals(-1, pair.nativeReply());
                    for (Arm arm : List.of(pair.noHit(), pair.attackNow()))
                        for (Step step : arm.turns()) assertEquals("WAIT", step.opponentAction().action());
                }
                replayAndCheck(initial, me, pair.noHit()); replayAndCheck(initial, me, pair.attackNow());
                for (Arm arm : List.of(pair.noHit(), pair.attackNow())) {
                    assertEquals(arm.turns().stream().filter(t -> t.ownAction().target() >= 0).count(),
                            arm.positiveAttackSteps());
                    for (var f : arm.rollingForecasts())
                        assertEquals("FRESH_V09_OPPONENT_CONTEXT", f.forecast().opponentContext());
                }
            }
            ReplyProbe.sameState(before, initial);
            assertTrue(Arrays.deepEquals(before.pairPath, initial.pairPath));

            GameState filler = filler(me), fillerBefore = filler.copy();
            var fillerProbe = V09FullActionProbe.prepare(filler, me);
            assertTrue(fillerProbe.metadata().supported());
            assertEquals(0, fillerProbe.metadata().rawSelected().cells().length);
            assertEquals(3, fillerProbe.selected().cells().length);
            // Target7 contains native filler cells: keep their collateral exposure identical in both arms.
            Result filled = run(filler, me, 7);
            for (var pair : filled.comparisons()) for (var arm : List.of(pair.noHit(), pair.attackNow())) {
                assertArrayEquals(fillerProbe.selected().cells(), arm.turns().get(0).ownAction().cells());
                assertEquals(3, arm.turns().get(0).ownAction().cells().length);
                replayAndCheck(filler, me, arm);
            }
            ReplyProbe.sameState(fillerBefore, filler);
        }
    }

    @Test void finalRealTurnUsesOneStepAndStopsBothArmsAtCoreTermination() throws Exception {
        GameState initial = line(0, 99);
        int target = V09FullActionProbe.prepare(initial, 0).metadata().nominatedTarget();
        // The final-turn nomination may have no positive future value. A ready target is meaningful now.
        if (target < 0) {
            for (int i = 0; i < 3; i++) Rules.disrupt(initial, 2);
            target = V09FullActionProbe.prepare(initial, 0).metadata().nominatedTarget();
        }
        assertTrue(target >= 0);
        Result result = run(initial, 0, target);
        assertEquals(1, result.horizon());
        for (var comparison : result.comparisons()) for (var arm : List.of(comparison.noHit(), comparison.attackNow())) {
            assertEquals(1, arm.steps()); assertTrue(arm.complete()); assertTrue(arm.terminal());
            assertEquals("TERMINAL", arm.stopReason()); assertTrue(arm.turns().get(0).turnLimitReached());
            assertTrue(arm.rollingForecasts().isEmpty());
        }
    }

    @Test void promiseRequiresSameTargetWithAnAbsoluteDeadlineAndAllowsEarlierExecution() {
        Promise p = new Promise("FIRST_FUTURE_OWN_HIT", 0, 10, 2, 2, 12);
        assertEquals("EXECUTED_EARLY", check(p, List.of(step(11, 2, -1, false, false)), true, 17).status());
        assertEquals("EXECUTED_ON_TIME", check(p, List.of(step(12, 2, -1, false, false)), true, 17).status());
        var late = check(p, List.of(step(12, 3, -1, false, false), step(13, 2, -1, false, false)), true, 17);
        assertEquals("EXECUTED_LATE", late.status()); assertFalse(late.byDeadline());
        assertEquals("UNFULFILLED", check(p, List.of(step(12, 3, -1, false, false)), true, 12).status());
        Promise newer = new Promise("FIRST_FUTURE_OWN_HIT", 0, 12, 2, 1, 13);
        assertEquals("UNFULFILLED", check(newer,
                List.of(step(11, 2, -1, false, false), step(13, 3, -1, false, false)), true, 17).status(),
                "A hit before the new promise was issued cannot satisfy it");
    }

    @Test void opponentResolutionTerminalSuccessInvalidityAndCensoringAreNotConflated() {
        Promise p = new Promise("FIRST_FUTURE_NOMINATED_HIT", 0, 10, 2, 2, 12);
        var opponent = check(p, List.of(step(11, -1, 2, true, false)), true, 17);
        assertEquals("OPPONENT_INKED_BEFORE_EXECUTION", opponent.status());
        assertEquals("OPPONENT_HIT", opponent.inkCause()); assertEquals(11, opponent.inkTurn());
        var lateOpponent = check(p, List.of(step(13, -1, 2, true, false)), true, 17);
        assertEquals("OPPONENT_INKED_AFTER_DEADLINE", lateOpponent.status());
        assertFalse(lateOpponent.byDeadline()); assertEquals(13, lateOpponent.inkTurn());
        var terminal = check(p, List.of(step(11, 3, -1, false, true)), true, 17);
        assertEquals("TERMINAL_BY_DEADLINE_BEFORE_EXECUTION", terminal.status());
        assertEquals("WIN", terminal.terminalOutcome());
        assertEquals("TERMINAL_BY_DEADLINE_BEFORE_EXECUTION",
                check(p, List.of(step(12, 3, -1, false, true)), true, 17).status());
        Step regular = step(11, -1, -1, false, false);
        regular.regionInkedBefore()[2] = true;
        assertEquals("TARGET_INVALID_BEFORE_EXECUTION", check(p, List.of(regular), true, 17).status());
        assertEquals("CENSORED_HORIZON", check(p, List.of(step(11, -1, -1, false, false)), true, 11).status());
        assertEquals("CENSORED_UNSUPPORTED_REPLAN", check(p, List.of(step(11, -1, -1, false, false)), false, 17).status());
        Promise absent = new Promise("FIRST_FUTURE_OWN_HIT", 0, 10, null, null, null);
        assertEquals("NO_PROMISE", check(absent, List.of(), true, 17).status());
    }

    @Test void forecastKeepsFirstFutureHitDistinctFromNominatedHitAndTerminalOutcome() {
        var turns = List.of(traceTurn(0, 40, -1, false), traceTurn(1, 41, 3, false),
                traceTurn(4, 44, 2, true));
        var trace = new V09FullActionProbe.Trace(V09FullActionProbe.Variant.BASELINE, 0, -1,
                new Score(1_000_001, new double[]{1_000_001}), List.of(new ReplyTrace(0, 1_000_001, turns)));
        Forecast forecast = V09FullActionEpisode.forecast(trace, 0, 2, 40, 2, "FRESH_V09_OPPONENT_CONTEXT");
        var reply = forecast.replies().get(0);
        assertEquals(3, reply.firstFutureOwnHit().target()); assertEquals(41, reply.firstFutureOwnHit().dueTurn());
        assertEquals(2, reply.firstFutureNominatedHit().target()); assertEquals(44, reply.firstFutureNominatedHit().dueTurn());
        assertEquals(44, reply.terminalTurn()); assertEquals(1, reply.terminalScoreDifference());
    }

    @Test void episodeAcceptsOtherPinnedLegalTargetButRejectsIllegalTargetsAndTerminalStarts() throws Exception {
        GameState initial = line(0, 10);
        int target = V09FullActionProbe.prepare(initial, 0).metadata().nominatedTarget();
        int differentTarget = target == 2 ? 3 : 2;
        var result = run(initial, 0, differentTarget);
        assertEquals(differentTarget, result.pinnedAttackTarget());
        for (var pair : result.comparisons())
            assertEquals(differentTarget, pair.attackNow().turns().get(0).ownAction().target());
        assertThrows(IllegalStateException.class, () -> run(initial, 0, 0));
        assertThrows(IllegalStateException.class, () -> run(initial, 0, initial.regionCount));
        initial.turn = initial.maxTurns;
        assertThrows(IllegalStateException.class, () -> run(initial, 0, target));
    }

    @Test void unsupportedOpeningStartReturnsExplicitMetadataWithoutInventingAContext() throws Exception {
        GameState opening = new GameState(7, 1, new int[]{0, 0, 1, 2, 3, 4, 4}, new int[7],
                new int[]{0, 6}, new int[]{0, 0}, new int[][]{{1}, {}});
        Rules.updateConnections(opening, new Pathing(opening.size));
        var unsupported = run(opening, 0, 2);
        assertFalse(unsupported.initialMetadata().supported());
        assertEquals("PROTECTED_OPENING", unsupported.initialMetadata().reason());
        assertTrue(unsupported.comparisons().isEmpty());
    }

    private static PromiseCheck check(Promise p, List<Step> steps, boolean complete, int end) {
        return V09FullActionEpisode.classify(p, steps, complete,
                complete ? "HORIZON" : "UNSUPPORTED_REPLAN:SYNTHETIC", end, 0);
    }
    private static Step step(int turn, int own, int enemy, boolean ink, boolean terminal) {
        return new Step(turn - 10, turn, plan(own), plan(enemy), true, new int[]{5, 4}, new int[2],
                new int[5], new boolean[5], new int[]{5, 4}, new int[2], new int[2],
                ink ? List.of(2) : List.of(), terminal, terminal, false, "before", "after");
    }
    private static Plan plan(int target) {
        return new Plan(new int[0], target, target < 0 ? "WAIT" : "DISRUPT " + target);
    }
    private static Turn traceTurn(int offset, int turn, int target, boolean terminal) {
        return new Turn(offset, turn, new int[][]{new int[0], new int[0]}, new int[]{target, -1},
                new int[]{target, -1}, new int[2], new int[2], new int[2], new int[2], new int[]{5, 4},
                terminal, List.of(), new int[5]);
    }
    private static GameState line(int me, int turn) {
        GameState s = new GameState(5, 1, new int[]{0, 1, 2, 3, 4}, new int[5],
                new int[]{0, 4}, new int[]{0, 0}, new int[][]{{1}, {}});
        s.owner[1] = me; s.owner[2] = s.owner[3] = 1 - me; s.score[me] = 100; s.turn = turn;
        Rules.updateConnections(s, new Pathing(s.size)); return s;
    }
    private static GameState filler(int me) {
        int[] regions = {0,1,2,3,4,5,6,7,7,7,7,7,7,7,8,9,9,9,9,9,10};
        GameState s = new GameState(7, 3, regions, new int[21], new int[]{0,6,0,6},
                new int[]{0,0,2,2}, new int[][]{{1},{},{},{}});
        for (int cell = 1; cell <= 5; cell++) s.owner[cell] = me;
        s.turn = 10; Rules.updateConnections(s, new Pathing(s.size)); return s;
    }
    private static void replayAndCheck(GameState initial, int me, Arm arm) throws Exception {
        GameState replay = initial.copy(); Pathing pathing = new Pathing(replay.size);
        for (Step step : arm.turns()) {
            assertEquals(ReplyStudy.stateHash(replay), step.stateBeforeSha256());
            assertEquals(replay.turn, step.turn());
            var own = Action.parseLine(step.ownAction().action()); var enemy = Action.parseLine(step.opponentAction().action());
            var turn = me == 0 ? Rules.resolve(replay, own, enemy, pathing) : Rules.resolve(replay, enemy, own, pathing);
            assertNull(turn.moves[0].error); assertNull(turn.moves[1].error);
            assertTrue(turn.moves[0].skipped.isEmpty()); assertTrue(turn.moves[1].skipped.isEmpty());
            assertArrayEquals(replay.score, step.score()); assertArrayEquals(turn.gained, step.gained());
            assertEquals(ReplyStudy.stateHash(replay), step.stateSha256());
            assertEquals(Rules.gameOver(replay, pathing), step.terminal());
        }
        assertEquals(arm.finalStateSha256(), ReplyStudy.stateHash(replay));
        assertEquals(arm.finalScoreDifference(), replay.score[me] - replay.score[1 - me]);
    }
}
