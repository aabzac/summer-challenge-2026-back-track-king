package btk.bot.current;

import btk.bot.Params;
import btk.bot.Timer;
import btk.core.*;
import org.junit.jupiter.api.Test;

import static btk.bot.current.RolloutTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class RolloutOpeningIntegrationTest {
    private static GameState row() {
        return new GameState(9,1,new int[]{0,0,1,2,2,2,3,4,4},new int[9],
                new int[]{0,4,8},new int[3],new int[][]{{1,2},{2},{}});
    }

    private static RolloutBot bot(GameState s,int me,boolean opening) {
        var b=new RolloutBot(new Params(btk.bot.LiveTargetExperimentParams.V10_OFF + ",NOISE=0,USE_PROTECTED_OPENING="+opening));
        b.init(s,me); return b;
    }

    @Test void openingReplacesOnlyFirstThreeTurnsAndKeepsCoreLegalActionsOnBothSides() {
        for(int me=0;me<2;me++) {
            var s=row(); var opening=bot(s,me,true); var baseline=bot(s,me,false);
            for(int turn=0;turn<4;turn++) {
                s.turn=turn;
                var actions=opening.play(s,me,60_000);
                var base=baseline.play(s,me,60_000);
                assertEquals(baseline.routeQueries,opening.routeQueries);
                if(turn<3) {
                    assertTrue(opening.selectedOpening); assertEquals(3,opening.openingCount);
                    assertEquals(3,opening.openingPaint); assertEquals(3,opening.openingPairs);
                    assertEquals(4,opening.openingEligible); assertEquals(0,opening.completedComparisons);
                    for(var action:actions) if(action.type==Action.Type.PLACE)
                        assertTrue(s.regionHasTown(s.regionId[s.cell(action.x,action.y)]));
                } else {
                    assertFalse(opening.selectedOpening); assertEquals(0,opening.openingPairs);
                    assertEquals(0,opening.openingCount); assertEquals(0,opening.openingEligible);
                    assertEquals(0,opening.openingPaint);
                    assertEquals(Action.format(base),Action.format(actions));
                    assertEquals(baseline.selectedValue,opening.selectedValue);
                    assertEquals(baseline.completedComparisons,opening.completedComparisons);
                }
                var tr=new Rules.TurnResult(); Rules.expand(s,me,actions,new Pathing(s.size),tr.moves[me],tr);
                assertNull(tr.moves[me].error); assertTrue(tr.moves[me].skipped.isEmpty());
                for(int owner:s.owner) assertEquals(-1,owner);
                assertArrayEquals(new int[2],s.score); assertEquals(turn,s.turn);
            }
        }
    }

    @Test void openingUsesExistingSabotageChooserOnTheActualChosenConstruction() throws Exception {
        boolean changedByConstruction=false;
        for(int me=0;me<2;me++) {
            var s=row(); s.owner[2]=s.owner[6]=1-me;
            Rules.updateConnections(s,new Pathing(s.size));
            var opening=bot(s,me,true); var actions=opening.play(s,me,60_000);
            assertTrue(opening.selectedOpening);
            var expected=bot(s,me,false); ((Timer)field(expected,"timer")).start(60_000);
            int pre=(int)call(expected,"chooseTarget",new Class<?>[]{GameState.class,int.class},s,me);
            var board=s.copy();
            for(var action:actions) if(action.type==Action.Type.PLACE) board.owner[board.cell(action.x,action.y)]=me;
            Rules.updateConnections(board,new Pathing(board.size));
            int post=(int)call(expected,"chooseTarget",new Class<?>[]{GameState.class,int.class},board,me);
            assertEquals(post,opening.selectedTarget);
            changedByConstruction|=pre!=post;
            assertEquals(1-me,s.owner[2]); assertEquals(1-me,s.owner[6]);
            assertEquals(-1,s.owner[1]); assertArrayEquals(new int[2],s.score);
        }
        assertTrue(changedByConstruction,"fixture must distinguish pre-build from selected-build sabotage");
    }

    @Test void noProtectedCandidateFallsThroughAndExpiredPlayClearsAllTelemetry() {
        var s=new GameState(5,1,new int[]{0,1,2,3,4},new int[5],new int[]{0,4},new int[2],new int[][]{{1},{}});
        var opening=bot(s,0,true); var baseline=bot(s,0,false);
        assertEquals(Action.format(baseline.play(s,0,60_000)),Action.format(opening.play(s,0,60_000)));
        assertFalse(opening.selectedOpening); assertEquals(1,opening.openingPairs);
        assertEquals(0,opening.openingEligible); assertTrue(opening.completedComparisons>0);
        assertEquals(baseline.completedComparisons,opening.completedComparisons);
        assertEquals("WAIT",Action.format(opening.play(s,0,0)));
        assertFalse(opening.selectedOpening); assertEquals(0,opening.openingPairs);
        assertEquals(0,opening.openingEligible); assertEquals(0,opening.openingPaint); assertEquals(0,opening.openingCount);
    }
}
