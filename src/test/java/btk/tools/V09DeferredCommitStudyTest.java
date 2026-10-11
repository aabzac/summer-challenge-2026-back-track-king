package btk.tools;

import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class V09DeferredCommitStudyTest {
    @Test void targetSpecificOracleMatchesFrozenPolicyWhenNoTargetIsSuppressed() throws Exception {
        for(int me=0;me<2;me++) {
            GameState s=line(me,20);String before=ReplyStudy.stateHash(s);
            var probe=V09FullActionProbe.prepare(s,me);
            var nativeTrace=probe.trace(-1,V09FullActionProbe.Variant.BASELINE);
            var noSuppression=V09DeferredCommitOracle.withoutTarget(probe,s,me,-2);
            assertEquals(V09ContinuationStudy.JSON.toJson(nativeTrace.replies()),V09ContinuationStudy.JSON.toJson(noSuppression));
            int target=probe.metadata().nominatedTarget();assertTrue(target>=0);
            var ablation=V09DeferredCommitOracle.withoutTarget(probe,s,me,target);
            boolean removed=false;
            for(var reply:ablation)for(var turn:reply.turns()) {
                if(turn.offset()>0) {
                    assertNotEquals(target,turn.targets()[me]);
                    if(turn.proposedTargets()[me]!=target)assertEquals(turn.proposedTargets()[me],turn.targets()[me]);
                    else removed=true;
                }
                assertEquals(turn.proposedTargets()[1-me],turn.targets()[1-me]);
            }
            assertTrue(removed,"Fixture must exercise actual future suppression");
            assertEquals(before,ReplyStudy.stateHash(s));
        }
    }
    @Test void branchesStartEmptyRetainNativeFirstActionAndStopAtTerminalOnBothSeats()throws Exception {
        for(int me=0;me<2;me++) {
            GameState s=line(me,99);String before=ReplyStudy.stateHash(s);
            var result=V09DeferredCommitStudy.episode(s,me,2);
            assertEquals(1,result.horizon());
            for(var pair:result.comparisons()) {
                assertTrue(pair.off().complete());assertTrue(pair.on().complete());
                assertEquals(pair.off().turns().get(0).ownAction().action(),pair.on().turns().get(0).ownAction().action());
                assertFalse(pair.on().turns().get(0).intervention());
                assertEquals("TERMINAL",pair.on().stopReason());
            }
            assertEquals(before,ReplyStudy.stateHash(s));
        }
    }
    private static GameState line(int me,int turn) {
        GameState s=new GameState(5,1,new int[]{0,1,2,3,4},new int[5],
                new int[]{0,4},new int[]{0,0},new int[][]{{1},{}});
        s.owner[1]=me;s.owner[2]=s.owner[3]=1-me;s.score[me]=100;s.turn=turn;
        Rules.updateConnections(s,new Pathing(s.size));return s;
    }
}
