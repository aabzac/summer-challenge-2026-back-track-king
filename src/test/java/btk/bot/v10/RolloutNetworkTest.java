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
class RolloutNetworkTest {
    private static GameState chord(int owner) {
        int[] regions = new int[63];
        for (int c = 0; c < regions.length; c++) regions[c] = c;
        var s = new GameState(9,7,regions,new int[63],new int[]{1,7},new int[]{3,3},new int[][]{{1},{}});
        for (int c : new int[]{29,30,21,22,23,32,33}) s.owner[c] = owner;
        s.turn = 10; Rules.updateConnections(s,new Pathing(s.size)); return s;
    }
    private static RolloutBotV10 bot(GameState s,int me,String overrides) {
        var b = new RolloutBotV10(RolloutTestParams.base("USE_ROLLOUT_QUICK_ROUTE=1,USE_ROLLOUT_NETWORK=1,NOISE=0,USE_DISRUPT=0"+overrides));
        b.init(s,me); return b;
    }
    @Test void findsMissingMixedNetworkChordOnBothSidesAndEmitsOnlyItsActualCell() throws Exception {
        for (int me = 0; me < 2; me++) {
            var s=chord(1-me); var b=bot(s,me,""); int[] owners=s.owner.clone();
            var off=new RolloutBotV10(RolloutTestParams.base("USE_ROLLOUT_QUICK_ROUTE=1,NOISE=0,USE_DISRUPT=0")); off.init(s,me);
            assertEquals("WAIT",Action.format(off.play(s,me,60_000)));
            var actions=b.play(s,me,60_000);
            assertEquals("PLACE_TRACKS 4 3",Action.format(actions));
            assertTrue(b.selectedNetwork); assertEquals(31,b.selectedNetworkCell);
            assertEquals(4,((int[])field(b,"networkGains"))[31]);
            assertTrue(b.networkRoots>0); assertTrue(b.networkComparisons>0);
            assertEquals(off.routeQueries,b.routeQueries);
            assertTrue(b.networkEvaluated<=24); assertTrue(b.networkRoots<=2);
            assertTrue(b.completedComparisons<=18); assertTrue(b.simulatedTurns<=288);
            assertArrayEquals(owners,s.owner); assertArrayEquals(new int[2],s.score); assertEquals(10,s.turn);
            var tr=new Rules.TurnResult(); Rules.expand(s,me,actions,new Pathing(s.size),tr.moves[me],tr);
            assertNull(tr.moves[me].error); assertTrue(tr.moves[me].skipped.isEmpty());
            assertEquals(1,tr.moves[me].cost); assertEquals(1,tr.moves[me].count);
        }
    }
    @Test void coreIncomeRejectsHarmfulOwnRailShortcutAndUsesActualTerrainCost() throws Exception {
        for (int me=0;me<2;me++) {
            var own=chord(me);var bad=bot(own,me,"");bad.play(own,me,60_000);
            assertEquals(-2,((int[])field(bad,"networkGains"))[31]);
            int[][] roots=(int[][])field(bad,"roots");int start=(int)field(bad,"ordinaryRootSize");
            for(int i=0;i<bad.networkRoots;i++)assertNotEquals(31,roots[start+i][0]);
            var costly=chord(1-me);costly.terrain[31]=GameState.MOUNTAIN;
            var b=bot(costly,me,"");var actions=b.play(costly,me,60_000);
            assertTrue(b.selectedNetwork);assertEquals(31,b.selectedNetworkCell);
            var tr=new Rules.TurnResult();Rules.expand(costly,me,actions,new Pathing(costly.size),tr.moves[me],tr);
            assertEquals(3,tr.moves[me].cost);assertTrue(tr.moves[me].skipped.isEmpty());
        }
    }
    @Test void networkRootUsesOrdinaryContinuationAndSimultaneousCollisionRules() throws Exception {
        for(int me=0;me<2;me++) {
            var s=chord(1-me);s.maxTurns=12;var b=bot(s,me,"");
            pool(b,me,new int[]{0,1,2});pool(b,1-me,new int[]{31});
            var ordinary=RolloutBotV10.class.getDeclaredField("ordinaryRootSize");ordinary.setAccessible(true);ordinary.setInt(b,2);
            ((int[][])field(b,"roots"))[2][0]=31;((int[])field(b,"rootCounts"))[2]=1;
            ((Timer)field(b,"timer")).start(60_000);
            double value=(double)call(b,"rollout",new Class<?>[]{GameState.class,int.class,int.class,int.class,int.class},s,me,2,-1,0);
            assertTrue(Double.isFinite(value));var end=((SearchState)field(b,"work")).state;
            assertEquals(GameState.NEUTRAL,end.owner[31]);
            for(int c:new int[]{0,1,2})assertEquals(me,end.owner[c]);
            assertEquals(-1,s.owner[31]);assertEquals(-1,s.owner[0]);
        }
    }
    @Test void boundedScanDeduplicatesOrdinarySingletonAndExpiredPlayClearsDiagnostics() throws Exception {
        var s=chord(1);var b=bot(s,0,"");
        var ordinary=RolloutBotV10.class.getDeclaredField("ordinaryRootSize");ordinary.setAccessible(true);ordinary.setInt(b,2);
        ((int[][])field(b,"roots"))[0][0]=31;((int[])field(b,"rootCounts"))[0]=1;
        ((Timer)field(b,"timer")).start(60_000);
        int size=(int)call(b,"addNetworkRoots",new Class<?>[]{GameState.class,int.class,int.class,int.class},s,0,2,12);
        int[][] roots=(int[][])field(b,"roots");for(int i=2;i<size;i++)assertNotEquals(31,roots[i][0]);
        b.play(s,0,60_000);assertTrue(b.selectedNetwork);
        assertEquals("WAIT",Action.format(b.play(s,0,0)));
        assertFalse(b.selectedNetwork);assertEquals(-1,b.selectedNetworkCell);
        assertEquals(0,b.networkRoots);assertEquals(0,b.networkEvaluated);assertEquals(0,b.networkComparisons);
        var capped=bot(s,0,",ROLLOUT_NETWORK_MAX_CELLS=1");capped.play(s,0,60_000);
        assertEquals(1,capped.networkEvaluated);
        int[] cells=(int[])field(capped,"networkCells"),gains=(int[])field(capped,"networkGains");
        assertNotEquals(Integer.MIN_VALUE,gains[cells[0]]);
        for(int i=1;i<capped.networkEligible;i++)assertEquals(Integer.MIN_VALUE,gains[cells[i]]);
    }
    @Test void flagOffRetainsPriorDecisionsAndRejectsMixedExperiments() throws Exception {
        assertFalse(btk.bot.LiveTargetExperimentParams.v09Params().USE_ROLLOUT_NETWORK);
        var s=chord(1);var b=new RolloutBotV10(RolloutTestParams.base("USE_ROLLOUT_QUICK_ROUTE=1,NOISE=0,USE_DISRUPT=0"));b.init(s,0);
        assertEquals("WAIT",Action.format(b.play(s,0,60_000)));assertNull(field(b,"networkCells"));
        for(String variant:new String[]{"USE_ROLLOUT_SPREAD=1","USE_ROLLOUT_REPLAN=1","USE_ROLLOUT_SECOND_TARGET=1",
                "USE_ROLLOUT_DIVERSE_REPLIES=1","USE_ROLLOUT_QUICK_EARLY=1","ROLLOUT_NETWORK_MAX_CELLS=0",
                "ROLLOUT_NETWORK_ROOTS=3","ROLLOUT_NETWORK_RESERVE_MS=-1"})
            assertThrows(IllegalArgumentException.class,()->new RolloutBotV10(RolloutTestParams.base("USE_ROLLOUT_NETWORK=1,"+variant)));
    }
    @Test void rootOrderingIsAPermutationAndImmediateInkRemovesTheNewIncome() throws Exception {
        for(int me=0;me<2;me++) {
            var s=chord(1-me); s.score[me]=100; s.maxTurns=11;
            Rules.disrupt(s,31);Rules.disrupt(s,31);Rules.disrupt(s,31);
            var b=bot(s,me,"");b.play(s,me,60_000);
            int ordinary=(int)field(b,"ordinaryRootSize"),size=ordinary+b.networkRoots;
            int[] order=new int[size];
            for(int i=0;i<size;i++)order[i]=(int)call(b,"searchRoot",new Class<?>[]{int.class},i);
            assertEquals(0,order[0]);Arrays.sort(order);
            for(int i=0;i<size;i++)assertEquals(i,order[i]);
            int[][] roots=(int[][])field(b,"roots");int root=-1;
            for(int i=ordinary;i<size;i++)if(roots[i][0]==31)root=i;
            assertTrue(root>=0);
            var types=new Class<?>[]{GameState.class,int.class,int.class,int.class,int.class};
            ((Timer)field(b,"timer")).start(60_000);
            double safe=(double)call(b,"rollout",types,s,me,root,-1,0);
            ((Timer)field(b,"timer")).start(60_000);
            double inked=(double)call(b,"rollout",types,s,me,root,31,0);
            assertEquals(4,safe-inked);assertTrue(((SearchState)field(b,"work")).state.inked[31]);
            assertEquals(-1,((SearchState)field(b,"work")).state.owner[31]);
            assertFalse(s.inked[31]);assertEquals(-1,s.owner[31]);
        }
    }
}
