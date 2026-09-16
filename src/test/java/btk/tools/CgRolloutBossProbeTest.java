package btk.tools;

import btk.core.GameState;
import btk.core.Action;
import btk.core.Pathing;
import btk.core.Rules;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CgRolloutBossProbeTest {
    private GameState line() {
        return new GameState(5,1,new int[]{0,1,2,3,4},new int[]{0,2,0,1,0},
                new int[]{0,4},new int[]{0,0},new int[][]{{1},{}});
    }

    @Test void optimisticCompletedRouteChargesTerrainAndPreservesInputForBothPlayers() throws Exception {
        for(int me=0;me<2;me++) {
            var state=line(); state.owner[2]=1-me;
            Rules.updateConnections(state,new Pathing(state.size));
            String before=ReplyStudy.stateHash(state);
            var route=CgRolloutBossProbe.cheapestUsefulRoute(state,me);
            assertNotNull(route);
            assertEquals(5,route.paint()); // Mountain + river, not affordable in one turn.
            assertEquals(2,route.ownIncomeGain());
            assertEquals(1,route.incomeDifferenceGain());
            assertEquals(before,ReplyStudy.stateHash(state));
        }
    }

    @Test void connectivityAloneDoesNotCountAsUsefulAndInkedRoutesStayImpossible() {
        var state=line(); state.owner[1]=state.owner[2]=1;
        Rules.updateConnections(state,new Pathing(state.size));
        assertNull(CgRolloutBossProbe.cheapestUsefulRoute(state,0)); // 1 own vs 2 enemy income.
        state=line(); Rules.ink(state,2); Rules.updateConnections(state,new Pathing(state.size));
        assertNull(CgRolloutBossProbe.cheapestUsefulRoute(state,0));
    }

    @Test void capturedBossPositionsRestoreExactlyBeforeAnySameTurnAction() throws Exception {
        try(var reader=new InputStreamReader(getClass().getResourceAsStream("/btk/bot/rollout-boss-positions.json"),StandardCharsets.UTF_8)) {
            var positions=JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("positions");
            assertEquals(2,positions.size());
            for(var item:positions) {
                var fixture=item.getAsJsonObject(); var replay=new CgReplay();
                replay.globalGraphics=fixture.get("globalGraphics").getAsString();
                var state=replay.toState(); var pathing=new Pathing(state.size);
                Rules.updateConnections(state,pathing);
                for(var turn:fixture.getAsJsonArray("history")) {
                    var actions=turn.getAsJsonArray();
                    var result=Rules.resolve(state,Action.parseLine(actions.get(0).getAsString()),
                            Action.parseLine(actions.get(1).getAsString()),pathing);
                    assertNull(result.moves[0].error); assertNull(result.moves[1].error);
                }
                assertEquals(fixture.get("turn").getAsInt(),state.turn);
                assertEquals(fixture.get("stateSha256").getAsString(),ReplyStudy.stateHash(state));
                if(state.turn==0) assertEquals(4,CgRolloutBossProbe.cheapestUsefulRoute(state,0).paint());
            }
        }
    }
}
