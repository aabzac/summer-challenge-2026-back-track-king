package btk.bot.current;

import btk.bot.BotFactory;
import btk.bot.Params;
import btk.bot.Timer;
import btk.core.*;
import btk.referee.MapGenerator;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RolloutBotTest {
    private static Object field(RolloutBot b,String name) throws Exception {
        var f=RolloutBot.class.getDeclaredField(name); f.setAccessible(true); return f.get(b);
    }
    private static Object call(RolloutBot b,String name,Class<?>[] types,Object...args) throws Exception {
        var m=RolloutBot.class.getDeclaredMethod(name,types); m.setAccessible(true); return m.invoke(b,args);
    }
    private static RolloutBot bot(GameState s,int me,String extra) {
        var b=new RolloutBot(RolloutTestParams.base("USE_ROLLOUT_POLICY=1,NOISE=0"+extra)); b.init(s,me); return b;
    }
    private static GameState corridor() {
        return new GameState(6,1,new int[]{0,1,1,1,1,2},new int[6],
                new int[]{0,5},new int[]{0,0},new int[][]{{1},{}});
    }
    private static void pool(RolloutBot b,int me,int[]...paths) throws Exception {
        int[][][] routes=(int[][][])field(b,"routes"); int[][] lengths=(int[][])field(b,"lengths");
        ((int[])field(b,"counts"))[me]=paths.length;
        for(int i=0;i<paths.length;i++) {
            System.arraycopy(paths[i],0,routes[me][i],0,paths[i].length); lengths[me][i]=paths[i].length;
        }
    }
    @Test void actualPackingSkipsDuplicateCellsAndAbandonsInkedProjects() throws Exception {
        var s=corridor(); s.terrain[1]=GameState.RIVER; s.terrain[2]=GameState.RIVER;
        var b=bot(s,0,",USE_DISRUPT=0"); pool(b,0,new int[]{1,2},new int[]{1,3,4});
        int[] out=new int[3];
        int n=(int)call(b,"fillBuild",new Class<?>[]{GameState.class,int.class,int.class,int[].class},s,0,0,out);
        assertEquals(2,n); assertArrayEquals(new int[]{1,3},Arrays.copyOf(out,n));
        assertEquals(-1,s.owner[1]);
        Rules.ink(s,1);
        assertEquals(0,call(b,"fillBuild",new Class<?>[]{GameState.class,int.class,int.class,int[].class},s,0,0,out));
    }
    @Test void rolloutEarnsOnlyAfterAffordableCompletionAndHonorsTurnLimitForBothSides() throws Exception {
        for(int me=0;me<2;me++) {
            var s=corridor(); s.maxTurns=3;
            var b=bot(s,me,",USE_DISRUPT=0"); pool(b,me,new int[]{1,2,3,4});
            ((int[][])field(b,"roots"))[0]=new int[]{1,2,3};
            ((int[])field(b,"rootCounts"))[0]=3;
            ((Timer)field(b,"timer")).start(60_000);
            double result=(double)call(b,"rollout",new Class<?>[]{GameState.class,int.class,int.class,int.class,int.class},s,me,0,-1,0);
            assertEquals(1_000_008,result); assertEquals(3,b.simulatedTurns);
            assertArrayEquals(new int[2],s.score); assertEquals(0,s.turn); assertEquals(-1,s.owner[1]);
        }
    }
    @Test void futureOpponentSabotageKillsRevenueInsideTheRollout() throws Exception {
        var s=corridor(); s.maxTurns=8;
        var b=bot(s,0,""); pool(b,0,new int[]{1,2,3,4});
        ((int[][])field(b,"roots"))[0]=new int[]{1,2,3};
        ((int[])field(b,"rootCounts"))[0]=3;
        ((Timer)field(b,"timer")).start(60_000);
        double result=(double)call(b,"rollout",new Class<?>[]{GameState.class,int.class,int.class,int.class,int.class},s,0,0,-1,0);
        // Opponent starts attacking the unfinished investment on turn 2, inks on turn 5.
        assertEquals(1_000_012,result); assertEquals(5,b.simulatedTurns);
        assertTrue(((SearchState)field(b,"work")).state.regionInked[1]);
    }
    @Test void rootComparisonIncludesWaitAndDeduplicatesIdenticalAffordableActions() {
        var s=corridor(); var b=bot(s,0,",USE_DISRUPT=0");
        var actions=b.play(s,0,60_000);
        assertTrue(b.completedComparisons>0); assertTrue(b.completedComparisons<=3);
        assertTrue(b.simulatedTurns<=3*2*8);
        assertEquals(3,actions.stream().filter(a->a.type==Action.Type.PLACE).count());
        assertTrue(Double.isFinite(b.selectedValue));
    }
    @Test void expiredRolloutIsDiscardedAndExpiredPlayIsLegal() throws Exception {
        var s=corridor(); var b=bot(s,0,"");
        ((Timer)field(b,"timer")).start(-1);
        assertTrue(Double.isNaN((double)call(b,"rollout",new Class<?>[]{GameState.class,int.class,int.class,int.class,int.class},s,0,0,-1,0)));
        assertEquals("WAIT",Action.format(b.play(s,0,0)));
        assertEquals(0,b.completedComparisons); assertEquals(0,b.simulatedTurns);
    }
    @Test void immediateWinningClosureIsSelectedAndLosingClosureRejected() throws Exception {
        for(int me=0;me<2;me++) {
            var s=corridor(); for(int c=1;c<5;c++)s.owner[c]=1-me;
            Rules.disrupt(s,1); Rules.disrupt(s,1); Rules.disrupt(s,1);
            Rules.updateConnections(s,new Pathing(s.size));
            var b=bot(s,me,""); ((Timer)field(b,"timer")).start(60_000);
            s.score[me]=20;
            assertEquals(1,call(b,"chooseTarget",new Class<?>[]{GameState.class,int.class},s,me));
            s.score[me]=0; s.score[1-me]=20;
            assertEquals(-1,call(b,"chooseTarget",new Class<?>[]{GameState.class,int.class},s,me));
        }
    }
    @Test void simulatedCollisionsUseSimultaneousOwnershipInsteadOfSequentialOverwrite() throws Exception {
        var s=corridor(); s.maxTurns=2;
        var b=bot(s,0,",USE_DISRUPT=0");
        pool(b,0,new int[]{1,2,3,4}); pool(b,1,new int[]{1,2,3,4});
        ((int[][])field(b,"roots"))[0]=new int[]{1,2,3}; ((int[])field(b,"rootCounts"))[0]=3;
        ((Timer)field(b,"timer")).start(60_000);
        assertEquals(0.0,call(b,"rollout",new Class<?>[]{GameState.class,int.class,int.class,int.class,int.class},s,0,0,-1,0));
        var end=((SearchState)field(b,"work")).state;
        for(int c=1;c<5;c++)assertEquals(GameState.NEUTRAL,end.owner[c]);
    }
    @Test void promotedSelectorRejectsMixedExperimentsAndOffIsStillV05() {
        assertTrue(btk.bot.LiveTargetExperimentParams.v09Params().USE_ROLLOUT_POLICY);
        assertInstanceOf(RolloutBot.class,BotFactory.create(btk.bot.LiveTargetExperimentParams.V09_ON_CURRENT));
        assertInstanceOf(EvalBot.class,BotFactory.create("current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",USE_ROLLOUT_POLICY=0"));
        assertInstanceOf(RolloutBot.class,BotFactory.create("current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",USE_ROLLOUT_POLICY=1"));
        for(String extra:new String[]{"USE_OWN_BUILD_PLAN=1","USE_HEURISTIC_POLICY=1","ROLLOUT_DEPTH=0","ROLLOUT_WIDTH=20","ROLLOUT_TAIL=NaN"})
            assertThrows(IllegalArgumentException.class,()->BotFactory.create("current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",USE_ROLLOUT_POLICY=1,"+extra));
    }
    @Test void generatedStateDecisionsAreLegalReproducibleAndDoNotMutateInput() {
        for(int me=0;me<2;me++) {
            var s=MapGenerator.generate(310); var a=bot(s,me,""); var b=bot(s,me,"");
            var out=a.play(s,me,60_000);
            assertEquals(Action.format(out),Action.format(b.play(s,me,60_000)));
            assertArrayEquals(new int[2],s.score); assertEquals(0,s.turn);
            for(int owner:s.owner)assertEquals(-1,owner);
            var tr=new Rules.TurnResult(); Rules.expand(s,me,out,new Pathing(s.size),tr.moves[me],tr);
            assertNull(tr.moves[me].error); assertTrue(tr.moves[me].skipped.isEmpty());
            assertTrue(a.routeQueries<=128); assertTrue(a.completedComparisons<=14);
            assertTrue(a.simulatedTurns<=14*2*8);
        }
    }
}
