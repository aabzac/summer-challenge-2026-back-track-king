package btk.bot.v10;

import btk.bot.current.RolloutTestParams;
import btk.bot.Params;
import btk.bot.Timer;
import btk.core.*;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static btk.bot.v10.RolloutDiverseRepliesTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** Moved from btk.bot.current by prune-v1 (2026-10-25): the mechanism under test now lives only in frozen v10. */
class RolloutSpreadTest {
    private static GameState board() {
        int[] regions = new int[30];
        for (int c = 0; c < regions.length; c++) regions[c] = c;
        return new GameState(10,3,regions,new int[30],new int[]{0,9,0,9},new int[]{0,0,2,2},
                new int[][]{{1},{},{3},{}});
    }
    private static RolloutBotV10 bot(GameState s, int me) throws Exception {
        var bot = new RolloutBotV10(RolloutTestParams.base("USE_ROLLOUT_QUICK_ROUTE=1,USE_ROLLOUT_SPREAD=1,USE_DISRUPT=0,NOISE=0"));
        bot.init(s,me);
        pool(bot,me,new int[]{0,1,2,3,4,5,6,7,8,9},new int[]{20,21,22,23,24,25,26,27,28,29});
        ((Timer)field(bot,"timer")).start(60_000);
        return bot;
    }
    private static int fill(RolloutBotV10 b, GameState s, int me, int quota, int[] out) throws Exception {
        return (int)call(b,"fillSpread",new Class<?>[]{GameState.class,int.class,int.class,int.class,int.class,int[].class},
                s,me,0,1,quota,out);
    }
    @Test void splitsRealPaintCostsAndNeverMutatesInput() throws Exception {
        for (int me = 0; me < 2; me++) {
            var s=board(); s.terrain[1]=GameState.RIVER;
            var b=bot(s,me); int[] out=new int[3], owners=s.owner.clone();
            int n=fill(b,s,me,2,out);
            assertArrayEquals(new int[]{1,21},Arrays.copyOf(out,n));
            assertEquals(3,s.placeCost(out[0])+s.placeCost(out[1]));
            n=fill(b,s,me,1,out);
            assertArrayEquals(new int[]{2,21,22},Arrays.copyOf(out,n));
            assertArrayEquals(owners,s.owner);
            for(int c=1;c<9;c++)s.terrain[c]=GameState.MOUNTAIN;
            n=fill(b,s,me,2,out);
            assertEquals(1,n); assertEquals(1,out[0]); assertEquals(false,field(b,"packedBoth"));
        }
    }
    @Test void finishesNearCompleteRouteAndAbandonsDestroyedOne() throws Exception {
        for(int me=0;me<2;me++) {
            var s=board(); var b=bot(s,me); int[] out=new int[3];
            for(int c=1;c<=6;c++)s.owner[c]=me;
            int n=fill(b,s,me,1,out);
            assertArrayEquals(new int[]{7,8,21},Arrays.copyOf(out,n));
            Rules.ink(s,7);
            n=fill(b,s,me,2,out);
            assertArrayEquals(new int[]{21,22,23},Arrays.copyOf(out,n));
            assertEquals(false,field(b,"packedBoth"));
        }
    }
    @Test void rejectsSharedVulnerableRegionButAllowsSharedTownRegion() throws Exception {
        var s=board(); var b=bot(s,0);
        var types=new Class<?>[]{GameState.class,int.class,int.class,int.class};
        assertEquals(true,call(b,"independentRegions",types,s,0,0,1));
        s.regionId[21]=s.regionId[1];
        assertEquals(false,call(b,"independentRegions",types,s,0,0,1));
        s.regionId[21]=s.regionId[0]; s.regionId[1]=s.regionId[0];
        assertEquals(true,call(b,"independentRegions",types,s,0,0,1));
    }
    @Test void multiTurnForecastContinuesSplittingAndAccountsForDelayedIncome() throws Exception {
        for(int me=0;me<2;me++) {
            var s=board(); s.maxTurns=4; var b=bot(s,me);
            var f=RolloutBotV10.class.getDeclaredField("ordinaryRootSize"); f.setAccessible(true); f.setInt(b,3);
            ((int[][])field(b,"roots"))[3]=new int[]{1,2,21};
            ((int[])field(b,"rootCounts"))[3]=3;
            ((int[])field(b,"spreadFirst"))[3]=0; ((int[])field(b,"spreadSecond"))[3]=1;
            ((int[])field(b,"spreadQuota"))[3]=2;
            double value=(double)call(b,"rollout",new Class<?>[]{GameState.class,int.class,int.class,int.class,int.class},s,me,3,-1,0);
            assertEquals(1_000_008,value); assertEquals(2,b.spreadBuildTurns);
            var end=((SearchState)field(b,"work")).state;
            assertEquals(8,end.score[me]); assertEquals(me,end.owner[24]); assertEquals(-1,end.owner[25]);
            assertArrayEquals(new int[2],s.score); assertEquals(-1,s.owner[1]);
            ((Timer)field(b,"timer")).start(-1);
            assertTrue(Double.isNaN((double)call(b,"rollout",new Class<?>[]{GameState.class,int.class,int.class,int.class,int.class},s,me,3,-1,0)));
        }
    }
    @Test void fullSearchEvaluatesBoundedSplitActionsWithoutChangingRouteGeneration() throws Exception {
        for(int me=0;me<2;me++) {
            var s=board(); Rules.updateConnections(s,new Pathing(s.size));
            var base=new RolloutBotV10(RolloutTestParams.base("USE_ROLLOUT_QUICK_ROUTE=1,USE_DISRUPT=0,NOISE=0")); base.init(s,me);
            base.play(s,me,60_000);
            var b=bot(s,me); var actions=b.play(s,me,60_000);
            assertEquals(base.routeQueries,b.routeQueries);
            assertEquals(2,b.spreadRoots); assertEquals(2,b.spreadComparisons);
            assertTrue(b.spreadBuildTurns>0); assertTrue(b.completedComparisons<=18); assertTrue(b.simulatedTurns<=288);
            var tr=new Rules.TurnResult(); Rules.expand(s,me,actions,new Pathing(s.size),tr.moves[me],tr);
            assertNull(tr.moves[me].error); assertTrue(tr.moves[me].skipped.isEmpty());
            int size=((int[])field(b,"counts"))[me]+1+b.spreadRoots;
            int[] order=new int[size];
            for(int i=0;i<size;i++)order[i]=(int)call(b,"searchRoot",new Class<?>[]{int.class},i);
            assertEquals(0,order[0]); Arrays.sort(order);
            for(int i=0;i<size;i++)assertEquals(i,order[i]);
        }
        assertFalse(btk.bot.LiveTargetExperimentParams.v09Params().USE_ROLLOUT_SPREAD);
        assertThrows(IllegalArgumentException.class,()->new RolloutBotV10(RolloutTestParams.base("USE_ROLLOUT_SPREAD=1,USE_ROLLOUT_REPLAN=1")));
    }
}
