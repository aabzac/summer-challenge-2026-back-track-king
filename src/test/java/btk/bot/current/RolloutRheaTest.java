package btk.bot.current;

import btk.bot.BotFactory;
import btk.bot.Params;
import btk.bot.Timer;
import btk.core.*;
import btk.referee.MapGenerator;
import org.junit.jupiter.api.Test;
import java.util.Arrays;

import static btk.bot.current.RolloutDiverseRepliesTest.*;
import static org.junit.jupiter.api.Assertions.*;

class RolloutRheaTest {
    private static final Class<?>[] COMPARE = {GameState.class,int.class,int.class,int.class,int.class};
    private static final Class<?>[] SEARCH = {GameState.class,int.class,int.class};

    private static void set(RolloutBot b, String name, Object value) throws Exception {
        var f = RolloutBot.class.getDeclaredField(name); f.setAccessible(true); f.set(b, value);
    }

    private static GameState fork() {
        // The right-hand connection scores twice; both plans can start with the same left cell.
        var s = new GameState(9,1,new int[]{0,1,2,3,4,5,6,7,8},new int[9],
                new int[]{0,4,8},new int[3],new int[][]{{1},{2},{1}});
        s.maxTurns = 2; Rules.updateConnections(s,new Pathing(s.size)); return s;
    }

    private static RolloutBot bot(GameState s, int me, String extra) {
        var b = new RolloutBot(new Params("USE_PROTECTED_OPENING=0,USE_ROLLOUT_RHEA=1,NOISE=0,USE_DISRUPT=0,ROLLOUT_DEPTH=2" + extra));
        b.init(s,me); return b;
    }

    private static RolloutBot prepared(GameState s, int me) throws Exception {
        var b = bot(s,me,""); pool(b,me,new int[]{1,2,3},new int[]{5,6,7});
        ((int[][])field(b,"roots"))[0][0] = 1;
        ((int[])field(b,"rootCounts"))[0] = 1;
        set(b,"ordinaryRootSize",3);
        ((Timer)field(b,"timer")).start(60_000);
        return b;
    }

    @Test void sameFirstPlacementHasDistinctFuturePlansAndExactBothSideValues() throws Exception {
        for (int me=0;me<2;me++) {
            var s=fork(); var b=prepared(s,me);
            int[] plan=(int[])field(b,"rheaCandidate");
            Arrays.fill(plan,0);
            double left=(double)call(b,"compareRhea",COMPARE,s,me,0,-1,1);
            assertEquals(1_000_003,left);
            plan[1]=1;
            double right=(double)call(b,"compareRhea",COMPARE,s,me,0,-1,1);
            assertEquals(1_000_006,right);
            var end=((SearchState)field(b,"work")).state;
            assertEquals(me,end.owner[1]);
            for(int c=5;c<8;c++) assertEquals(me,end.owner[c]);
            assertEquals(-1,end.owner[2]); assertNull(field(b,"activePlan"));
            assertArrayEquals(new int[2],s.score); assertEquals(0,s.turn);
            for(int c:s.owner) assertEquals(-1,c);
        }
    }

    @Test void seededMutationCanImproveSameRootWithoutChangingTheFirstAction() throws Exception {
        for(int me=0;me<2;me++) {
            var s=fork(); var b=prepared(s,me);
            b.ordinarySearchComplete=b.networkPhaseComplete=true;
            set(b,"best",1_000_003.0); set(b,"bestCount",1);
            ((int[])field(b,"bestCells"))[0]=1;
            b.selectedRoute=0; b.selectedTarget=-1; b.selectedValue=1_000_003;
            call(b,"seedRhea",new Class<?>[]{int.class,int.class,int.class,double.class},me,0,-1,1_000_003.0);
            call(b,"searchRhea",SEARCH,s,me,1);
            assertTrue(b.selectedRhea); assertEquals(1_000_006,b.selectedValue);
            assertEquals(0,b.selectedRoute); assertEquals(1,((int[])field(b,"bestCells"))[0]);
            assertEquals(1_000_003,b.rheaBaselineValue); assertEquals(1,b.rheaBaselineCells[0]);
            assertEquals(1,b.selectedRheaPlan[1]); assertEquals(12,b.rheaAttempts);
            assertEquals(12,b.rheaComparisons); assertEquals(1,b.rheaImprovements);
            assertEquals(1,b.rheaDistinctRoots);
        }
    }

    @Test void mutationPhaseExploresDifferentCompletedFirstActionsAndKeepsStrictTies() throws Exception {
        var s=fork(); var b=prepared(s,0);
        ((int[][])field(b,"roots"))[1][0]=5; ((int[])field(b,"rootCounts"))[1]=1;
        b.ordinarySearchComplete=b.networkPhaseComplete=true;
        b.selectedValue=1_000_006; b.selectedRoute=1; b.selectedTarget=-1;
        set(b,"best",1_000_006.0); set(b,"bestCount",1); ((int[])field(b,"bestCells"))[0]=5;
        call(b,"seedRhea",new Class<?>[]{int.class,int.class,int.class,double.class},0,0,-1,1_000_003.0);
        call(b,"seedRhea",new Class<?>[]{int.class,int.class,int.class,double.class},0,1,-1,1_000_006.0);
        call(b,"searchRhea",SEARCH,s,0,1);
        assertEquals(2,b.rheaDistinctRoots); assertFalse(b.selectedRhea);
        assertEquals(1,b.selectedRoute); assertEquals(5,((int[])field(b,"bestCells"))[0]);
        assertEquals(12,b.rheaComparisons);
    }

    @Test void comparisonUsesEverySameOpponentReplyAndDiscardsExpiredWork() throws Exception {
        var s=fork(); var b=prepared(s,0); pool(b,1,new int[]{2,3},new int[]{5,6,7});
        int[] plan=(int[])field(b,"rheaCandidate"); plan[1]=1;
        set(b,"activePlan",plan);
        double a=(double)call(b,"rollout",COMPARE,s,0,0,-1,0);
        double c=(double)call(b,"rollout",COMPARE,s,0,0,-1,1);
        set(b,"activePlan",null);
        assertNotEquals(a,c);
        assertEquals(.25*(a+c)+.5*Math.min(a,c),(double)call(b,"compareRhea",COMPARE,s,0,0,-1,2));
        b.selectedValue=123; b.selectedRoute=0;
        ((Timer)field(b,"timer")).start(-1);
        assertTrue(Double.isNaN((double)call(b,"compareRhea",COMPARE,s,0,0,-1,2)));
        assertNull(field(b,"activePlan")); assertEquals(123,b.selectedValue); assertEquals(0,b.selectedRoute);
        assertEquals(0,b.rheaComparisons);
    }

    @Test void incompleteEarlierPhaseOrExpiredClockPreventsMutationAndPlayClearsState() throws Exception {
        var s=fork(); var b=prepared(s,0);
        call(b,"seedRhea",new Class<?>[]{int.class,int.class,int.class,double.class},0,0,-1,3.0);
        for(int stage=0;stage<3;stage++) {
            b.ordinarySearchComplete=stage>0; b.networkPhaseComplete=stage>1;
            ((Timer)field(b,"timer")).start(stage==2 ? -1 : 60_000);
            call(b,"searchRhea",SEARCH,s,0,1);
            assertFalse(b.rheaSearchStarted); assertEquals(0,b.rheaAttempts);
        }
        b.selectedRhea=true; b.rheaComparisons=8;
        assertEquals("WAIT",Action.format(b.play(s,0,0)));
        assertFalse(b.selectedRhea); assertFalse(b.rheaSearchStarted); assertEquals(0,b.rheaComparisons);
        assertTrue(Double.isNaN(b.rheaBaselineValue)); assertArrayEquals(new int[3],b.rheaBaselineCells);
    }

    @Test void fullPlayPreservesBaseCoverageIsReproducibleLegalAndLeavesInputUntouched() {
        for(int me=0;me<2;me++) {
            var s=MapGenerator.generate(310); var before=s.copy();
            var base=new RolloutBot(new Params("USE_PROTECTED_OPENING=0,NOISE=0")); base.init(s,me); base.play(s,me,60_000);
            var on=new RolloutBot(new Params("USE_PROTECTED_OPENING=0,USE_ROLLOUT_RHEA=1,NOISE=0")); on.init(s,me);
            var actions=on.play(s,me,60_000);
            var repeat=new RolloutBot(new Params("USE_PROTECTED_OPENING=0,USE_ROLLOUT_RHEA=1,NOISE=0")); repeat.init(s,me);
            assertEquals(Action.format(actions),Action.format(repeat.play(s,me,60_000)));
            assertEquals(base.routeQueries,on.routeQueries);
            assertEquals(base.ordinaryComparisons,on.ordinaryComparisons);
            assertEquals(base.networkComparisons,on.networkComparisons);
            assertEquals(base.selectedValue,on.rheaBaselineValue);
            assertEquals(base.completedComparisons+on.rheaComparisons,on.completedComparisons);
            assertTrue(on.rheaAttempts<=12); assertTrue(on.rheaComparisons<=on.rheaAttempts);
            assertTrue(on.selectedValue>=on.rheaBaselineValue);
            assertArrayEquals(before.owner,s.owner); assertArrayEquals(before.inked,s.inked);
            assertArrayEquals(before.instability,s.instability); assertArrayEquals(before.score,s.score);
            assertEquals(before.turn,s.turn);
            var tr=new Rules.TurnResult(); Rules.expand(s,me,actions,new Pathing(s.size),tr.moves[me],tr);
            assertNull(tr.moves[me].error); assertTrue(tr.moves[me].skipped.isEmpty());
        }
    }

    @Test void disabledMatchesFrozenV07AndConfigurationsAreBoundedIndependent() throws Exception {
        assertFalse(new Params().USE_ROLLOUT_RHEA);
        for(int me=0;me<2;me++) {
            var s=MapGenerator.generate(311);
            var off=new RolloutBot(new Params("USE_PROTECTED_OPENING=0,USE_ROLLOUT_RHEA=0,ROLLOUT_RHEA_MUTATIONS=64,ROLLOUT_RHEA_ELITES=4"));
            off.init(s,me); var frozen=BotFactory.create("v07"); frozen.init(s,me);
            assertEquals(Action.format(frozen.play(s,me,60_000)),Action.format(off.play(s,me,60_000)));
            assertNull(field(off,"rheaElitePlans")); assertEquals(0,off.rheaAttempts);
        }
        for(String invalid:new String[]{"ROLLOUT_RHEA_MUTATIONS=0","ROLLOUT_RHEA_MUTATIONS=65",
                "ROLLOUT_RHEA_ELITES=0","ROLLOUT_RHEA_ELITES=5","USE_PROTECTED_OPENING=1",
                "USE_ROLLOUT_NETWORK_AFTER_BASE=0","USE_ROLLOUT_QUICK_ROUTE=0",
                "USE_ROLLOUT_REPLAN=1","USE_ROLLOUT_NETWORK_CHORDS=1","USE_ROLLOUT_ROUTE_DEPENDENCIES=1"})
            assertThrows(IllegalArgumentException.class,()->new RolloutBot(new Params("USE_PROTECTED_OPENING=0,USE_ROLLOUT_RHEA=1,"+invalid)));
    }
}
