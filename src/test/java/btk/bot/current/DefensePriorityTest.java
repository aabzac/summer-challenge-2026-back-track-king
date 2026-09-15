package btk.bot.current;

import btk.bot.BotFactory;
import btk.bot.Params;
import btk.core.*;
import btk.referee.MapGenerator;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DefensePriorityTest {
    static GameState threatened(int me, int hits) {
        int[] regions = {3,3,3,3,3, 0,1,1,1,2, 4,4,4,4,4};
        GameState s = new GameState(5,3,regions,new int[15],new int[]{0,4},new int[]{1,1},new int[][]{{1},{0}});
        for (int x=1;x<4;x++) s.owner[s.cell(x,1)] = me;
        for (int hit=0;hit<hits;hit++) Rules.disrupt(s,1);
        Rules.updateConnections(s,new Pathing(s.size));
        return s;
    }
    private static EvalBot bot(GameState s, int me, String extra) {
        var b = new EvalBot(new Params("USE_DEFENSE_PRIORITY=1,NOISE=0,USE_IDLE_EXTEND=0" + extra));
        b.init(s.copy(),me); return b;
    }
    private static GameState built(GameState s, int me, List<Action> actions) {
        GameState out = s.copy();
        var places = actions.stream().filter(a -> a.type == Action.Type.PLACE).toList();
        var result = Rules.resolve(out, me==0?places:List.of(), me==1?places:List.of(),new Pathing(s.size));
        assertTrue(result.moves[me].skipped.isEmpty()); return out;
    }
    private static int diff(GameState s,int me) {
        int[] income=new int[2]; Rules.updateConnections(s,new Pathing(s.size));
        Rules.incomePerTurn(s,income); return income[me]-income[1-me];
    }
    @Test void finishesFivePaintBypassInTwoTurnsForBothSides() {
        for (int me=0;me<2;me++) {
            GameState s=threatened(me,2); var b=bot(s,me,"");
            int original=diff(s,me); var actions=b.play(s.copy(),me,60_000);
            assertEquals(1,b.defenseSelectedRegion); assertEquals(2,b.defenseSelectedTurns);
            GameState next=built(s,me,actions); assertEquals(original,diff(next,me));
            Rules.disrupt(next,1);
            var second=b.play(next.copy(),me,60_000);
            assertEquals(1,b.defenseSelectedRegion); assertEquals(1,b.defenseSelectedTurns);
            GameState safe=built(next,me,second); Rules.ink(safe,1);
            assertTrue(diff(safe,me)>0); assertEquals(original,diff(s,me));
        }
    }
    @Test void refusesLateUndamagedImpossibleAndAlreadyFreeBypasses() {
        GameState[] states={threatened(0,3),threatened(0,0),threatened(0,2),threatened(0,2)};
        Rules.ink(states[2],3); Rules.ink(states[2],4);
        for(int x=0;x<5;x++) states[3].owner[x]=GameState.NEUTRAL;
        for(GameState s:states) {
            Rules.updateConnections(s,new Pathing(s.size)); var b=bot(s,0,"");
            b.play(s.copy(),0,60_000); assertEquals(-1,b.defenseSelectedRegion);
        }
    }
    @Test void respectsPaintCapDeadlineAndPackingWaste() {
        GameState s=threatened(0,2); var capped=bot(s,0,",DEFENSE_MAX_PAINT=4");
        capped.play(s.copy(),0,60_000); assertEquals(-1,capped.defenseSelectedRegion);
        s.turn=99; var late=bot(s,0,""); late.play(s.copy(),0,60_000);
        assertEquals(-1,late.defenseSelectedRegion);
        // Six paint in three river cells needs three turns, not ceil(6 / 3) = two.
        s=threatened(0,2); Rules.ink(s,4);
        s.owner[0]=s.owner[4]=GameState.NEUTRAL;
        s.terrain[1]=s.terrain[2]=s.terrain[3]=GameState.RIVER;
        var packed=bot(s,0,""); packed.play(s.copy(),0,60_000);
        assertEquals(-1,packed.defenseSelectedRegion);
    }
    @Test void rejectsConnectivityThatOnlyImprovesEnemyIncome() {
        GameState s=threatened(0,2);
        // Existing hostile bypass has one gap. Completing it restores connectivity but not our income.
        for(int x=0;x<5;x++) s.owner[x]=1;
        s.owner[2]=GameState.NO_TRACK;
        s.terrain[2]=GameState.MOUNTAIN;
        // We retain 1 cell after ink, but the new alternative also awards four enemy cells.
        var b=bot(s,0,""); b.play(s.copy(),0,60_000);
        assertEquals(-1,b.defenseSelectedRegion);
    }
    @Test void zeroBuildBudgetCannotCommitUncheckedDefense() {
        GameState s=threatened(0,2); var b=bot(s,0,",DISRUPT_RESERVE_MS=60000");
        var actions=b.play(s.copy(),0,60_000);
        assertEquals(-1,b.defenseSelectedRegion); assertEquals(0,b.defensePathQueries);
        assertFalse(actions.stream().anyMatch(a->a.type==Action.Type.PLACE));
    }
    @Test void capturedKnownStateUsesNoFutureActionsAndKeepsOffBaseline() throws Exception {
        try(var reader=new InputStreamReader(getClass().getResourceAsStream("/btk/bot/defense-priority.json"),StandardCharsets.UTF_8)) {
            var f=JsonParser.parseReader(reader).getAsJsonObject();
            long seed=Long.parseLong(f.get("refereeInput").getAsString().substring(5));
            GameState s=MapGenerator.generate(seed); var paths=new Pathing(s.size);
            var stdout=f.getAsJsonArray("stdout"); int t=f.get("turn").getAsInt();
            assertEquals(t*2,stdout.size());
            for(int i=0;i<t;i++) Rules.resolve(s,Action.parseLine(stdout.get(2*i).getAsString()),
                    Action.parseLine(stdout.get(2*i+1).getAsString()),paths);
            int me=f.get("me").getAsInt(); int[] owners=s.owner.clone(); int before=diff(s,me);
            var candidate=bot(s,me,""); var a=candidate.play(s.copy(),me,60_000);
            assertEquals(22,candidate.defenseSelectedRegion); assertEquals(1,candidate.defenseSelectedTurns);
            assertTrue(candidate.defensePathQueries<=12); assertTrue(candidate.defenseRegionsScanned<=3);
            GameState out=built(s,me,a); assertTrue(diff(out,me)>=before);
            GameState noBuild=s.copy(); Rules.ink(noBuild,22); Rules.ink(out,22);
            assertTrue(diff(out,me)>diff(noBuild,me)); assertArrayEquals(owners,s.owner);
            var c=BotFactory.create("current?USE_DEFENSE_PRIORITY=0"); var v=BotFactory.create("v05");
            c.init(s.copy(),me); v.init(s.copy(),me);
            for(int n=0;n<3;n++) assertEquals(Action.format(v.play(s.copy(),me,60_000)),Action.format(c.play(s.copy(),me,60_000)));
            var again=bot(s,me,""); assertEquals(Action.format(a),Action.format(again.play(s.copy(),me,60_000)));
        }
    }
    @Test void featureIsOffAndLimitsAreBounded() {
        assertFalse(new Params().USE_DEFENSE_PRIORITY);
        assertThrows(IllegalArgumentException.class,()->new EvalBot(new Params("USE_DEFENSE_PRIORITY=1,DEFENSE_MAX_REGIONS=4")));
        assertThrows(IllegalArgumentException.class,()->new EvalBot(new Params("USE_DEFENSE_PRIORITY=1,USE_REPLY_RERANK=1")));
    }
}
