package btk.bot.current;

import btk.bot.BotFactory;
import btk.bot.Params;
import btk.bot.Timer;
import btk.core.*;
import btk.referee.MapGenerator;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OwnBuildPlanTest {
    private static Object field(EvalBot bot, String name) throws Exception {
        var f = EvalBot.class.getDeclaredField(name); f.setAccessible(true); return f.get(bot);
    }
    private static Object call(EvalBot bot, String name, Class<?>[] types, Object... args) throws Exception {
        var m = EvalBot.class.getDeclaredMethod(name, types); m.setAccessible(true); return m.invoke(bot, args);
    }
    private static EvalBot bot(GameState s, int me, String extra) {
        var b = new EvalBot(new Params(btk.bot.LiveTargetExperimentParams.V10_OFF + ",USE_OWN_BUILD_PLAN=1,NOISE=0,USE_DISRUPT=0" + extra));
        b.init(s, me); return b;
    }
    private static GameState twoProjects() {
        int[] regions = new int[21];
        for (int c = 0; c < regions.length; c++) regions[c] = c;
        var s = new GameState(7, 3, regions, new int[21], new int[]{0,4,0,6},
                new int[]{0,0,2,2}, new int[][]{{1},{0},{3},{2}});
        for (int c = 7; c < 14; c++) Rules.ink(s, c); // separate two corridors
        Rules.updateConnections(s, new Pathing(s.size));
        return s;
    }
    private static void pool(EvalBot b, int[]... routes) throws Exception {
        int[][] paths = (int[][]) field(b, "buildBeamPath");
        int[] lengths = (int[]) field(b, "buildBeamLen");
        for (int i = 0; i < routes.length; i++) {
            System.arraycopy(routes[i], 0, paths[i], 0, routes[i].length); lengths[i] = routes[i].length;
        }
    }
    private static void rank(EvalBot b, GameState s, int me, int count) throws Exception {
        call(b, "rankOwnBuildPlans", new Class<?>[]{GameState.class,int.class,int.class}, s,me,count);
    }
    private static int simulate(EvalBot b, GameState s, int me, int first, int count, int level) throws Exception {
        return (int) call(b, "simulateOwnBuild", new Class<?>[]{GameState.class,int.class,int.class,int.class,int.class,boolean[].class},
                s, me, first, count, level, null);
    }
    @Test void earnedIncomePrefersFinishingShortProjectBeforeLongOneForBothSides() throws Exception {
        for (int me = 0; me < 2; me++) {
            GameState s = twoProjects(); s.maxTurns = 3; s.score[0] = 100; s.score[1] = 500;
            var b = bot(s, me, "");
            pool(b, new int[]{15,16,17,18,19}, new int[]{1,2,3});
            ((Timer) field(b, "timer")).start(60_000);
            rank(b, s, me, 2);
            assertTrue(b.ownPlanCommitted); assertEquals(1, b.ownPlanSelected);
            assertEquals(28, b.ownPlanValue, 1e-9); // short 6*3 + long 10, versus long-first 26
            assertArrayEquals(new int[]{1,2,3}, Arrays.copyOf((int[]) field(b,"bestPath"),3));
            assertEquals(14, b.ownPlanNodes); assertEquals(8, b.ownPlanLeaves);
            assertEquals(0,s.turn); assertEquals(100,s.score[0]); assertEquals(500,s.score[1]);
            assertEquals(GameState.NO_TRACK,s.owner[1]);
        }
    }
    @Test void eachTurnUsesActualPackingCostsAndCoreScoring() throws Exception {
        GameState s = twoProjects();
        s.terrain[15] = s.terrain[16] = s.terrain[17] = GameState.RIVER;
        var b = bot(s, 1, ""); pool(b, new int[]{15,16,17}, new int[]{1,2,3});
        GameState current = s;
        for (int level = 0; level < 3; level++) {
            int n = simulate(b, current, 1, 0, 2, level);
            int[] cells = ((int[][]) field(b,"ownPlanCells"))[level];
            assertEquals(2,n); assertEquals(15+level,cells[0]); assertEquals(1+level,cells[1]);
            var expected = current.copy();
            var actions = List.of(Action.place(expected.x(cells[0]),expected.y(cells[0])),
                    Action.place(expected.x(cells[1]),expected.y(cells[1])));
            var result = Rules.resolve(expected,List.of(),actions,new Pathing(expected.size));
            assertTrue(result.moves[1].skipped.isEmpty());
            current = ((GameState[])field(b,"ownPlanStates"))[level];
            assertArrayEquals(expected.owner,current.owner); assertArrayEquals(expected.score,current.score);
            assertEquals(expected.turn,current.turn);
        }
        assertEquals(3,current.turn); // six river paint cannot be packed into two turns
    }
    @Test void deduplicatesWholeFirstActionsRegardlessOfPlacementOrder() throws Exception {
        var s = twoProjects(); var b = bot(s,0,"");
        pool(b,new int[]{1,2,3},new int[]{3,2,1});
        ((Timer)field(b,"timer")).start(60_000); rank(b,s,0,2);
        assertTrue(b.ownPlanCommitted); assertEquals(0,b.ownPlanSelected);
        assertEquals(8,b.ownPlanNodes); assertEquals(4,b.ownPlanLeaves);
    }
    @Test void threeByThreeBoundAndEndOfGameHorizonAreRespected() throws Exception {
        for (int turns = 1; turns <= 3; turns++) {
            var s = twoProjects(); s.maxTurns=turns; var b=bot(s,0,"");
            pool(b,new int[]{15,16,17,18,19},new int[]{1,2,3},new int[]{19,18,17,16,15});
            ((Timer)field(b,"timer")).start(60_000); rank(b,s,0,3);
            assertTrue(b.ownPlanCommitted);
            assertEquals((int)Math.pow(3,turns),b.ownPlanLeaves);
            assertEquals(turns==1?3:turns==2?12:39,b.ownPlanNodes);
        }
    }
    @Test void expiredSearchKeepsFallbackAndNeverCommitsPartialComparison() throws Exception {
        var s=twoProjects(); var b=bot(s,0,""); pool(b,new int[]{1,2,3});
        call(b,"copyBuildCandidateToBest",new Class<?>[]{int.class},0);
        ((Timer)field(b,"timer")).start(-1); rank(b,s,0,1);
        assertFalse(b.ownPlanCommitted); assertEquals(-1,b.ownPlanSelected); assertEquals(0,b.ownPlanNodes);
        assertArrayEquals(new int[]{1,2,3},Arrays.copyOf((int[])field(b,"bestPath"),3));
        double value=(double)call(b,"ownBuildContinuation",new Class<?>[]{GameState.class,int.class,int.class,int.class,int.class},s,0,1,1,3);
        assertTrue(Double.isNaN(value));
        b.play(s.copy(),0,0); assertFalse(b.ownPlanCommitted);
    }
    @Test void emittedFirstActionMatchesPlanAndDoesNotAddIdlePaint() throws Exception {
        GameState s=twoProjects(); s.terrain[1]=GameState.RIVER;
        // Only one two-paint gap; idle extension would spend the leftover point.
        s.owner[2]=s.owner[3]=0;
        for(int c=15;c<=19;c++) Rules.ink(s,c);
        Rules.updateConnections(s,new Pathing(s.size));
        var b=bot(s,0,""); var out=b.play(s.copy(),0,60_000);
        assertTrue(b.ownPlanCommitted);
        var places=out.stream().filter(a->a.type==Action.Type.PLACE).toList(); assertEquals(1,places.size());
        int[] prefix=((int[][])field(b,"ownPlanFirstCells"))[b.ownPlanSelected];
        assertEquals(Action.format(List.of(Action.place(s.x(prefix[0]),s.y(prefix[0])))),Action.format(places));
        var next=s.copy(); assertTrue(Rules.resolve(next,out,List.of(),new Pathing(s.size)).moves[0].skipped.isEmpty());
    }
    @Test void capturedPositionIsReproducibleWithAmpleTimeAndOffMatchesFrozenHistory() throws Exception {
        try(var reader=new InputStreamReader(getClass().getResourceAsStream("/btk/bot/defense-priority.json"),StandardCharsets.UTF_8)) {
            var f=JsonParser.parseReader(reader).getAsJsonObject();
            var s=MapGenerator.generate(Long.parseLong(f.get("refereeInput").getAsString().substring(5)));
            var stdout=f.getAsJsonArray("stdout"); int t=f.get("turn").getAsInt(), me=f.get("me").getAsInt();
            assertEquals(2*t,stdout.size());
            for(int i=0;i<t;i++) Rules.resolve(s,Action.parseLine(stdout.get(2*i).getAsString()),
                    Action.parseLine(stdout.get(2*i+1).getAsString()),new Pathing(s.size));
            var a=new EvalBot(new Params(btk.bot.LiveTargetExperimentParams.V10_OFF + ",USE_OWN_BUILD_PLAN=1")); a.init(s,me);
            var b=new EvalBot(new Params(btk.bot.LiveTargetExperimentParams.V10_OFF + ",USE_OWN_BUILD_PLAN=1")); b.init(s,me);
            var off=BotFactory.create("current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",USE_ROLLOUT_POLICY=0,USE_OWN_BUILD_PLAN=0"); off.init(s,me);
            var frozen=BotFactory.create("v05"); frozen.init(s,me);
            assertNull(field((EvalBot)off,"ownPlanStates"));
            for(int i=0;i<3;i++) {
                var action=a.play(s.copy(),me,60_000);
                assertEquals(Action.format(action),Action.format(b.play(s.copy(),me,60_000)));
                assertTrue(a.ownPlanCommitted); assertTrue(a.ownPlanNodes<=39); assertTrue(a.ownPlanLeaves<=27);
                var resolved=Rules.resolve(s.copy(),me==0?action:List.of(),me==1?action:List.of(),new Pathing(s.size));
                assertTrue(resolved.moves[me].skipped.isEmpty());
                assertEquals(Action.format(frozen.play(s.copy(),me,60_000)),Action.format(off.play(s.copy(),me,60_000)));
            }
        }
    }
    @Test void featureIsOffAndInvalidOrCombinedConfigurationsAreRejected() {
        assertFalse(new Params().USE_OWN_BUILD_PLAN);
        for(String bad:new String[]{"OWN_BUILD_WIDTH=0","OWN_BUILD_WIDTH=4","OWN_BUILD_DEPTH=1","OWN_BUILD_DEPTH=4",
                "USE_DEFENSE_PRIORITY=1","USE_REPLY_RERANK=1","USE_JOINT_ACTION=1","USE_OPP_MODEL=1",
                "CHOOSE_BUILD_DIRECTION=1","GUARD_BUILD_INCOME=1","EARLY_DISRUPT_FALLBACK=1","INTERRUPT_VULN_EVAL=1",
                "USE_HEURISTIC_POLICY=1"}) {
            assertThrows(IllegalArgumentException.class,()->BotFactory.create("current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",USE_ROLLOUT_POLICY=0,USE_OWN_BUILD_PLAN=1,"+bad),bad);
        }
    }
}
