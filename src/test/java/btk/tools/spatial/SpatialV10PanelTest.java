package btk.tools.spatial;

import btk.bot.BotFactory;
import btk.bot.neural.SpatialNet;
import btk.bot.neural.SpatialV10Bot;
import btk.bot.neural.SpatialV10Weights;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Protocol;
import btk.core.Rules;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Saved synthetic WAIT trajectories only; never creates new reference-policy decisions. */
class SpatialV10PanelTest {
    private static final Gson JSON = new Gson();

    @Test void reusesExactSavedCoreStatesAndRefusesChangedTransitionsOrDuplicateSlots() throws Exception {
        JsonObject saved = fixture(); var states = new ArrayList<SpatialV9Panel.Position>();
        var trajectory = new ArrayList<java.util.Map<String,Object>>();
        SpatialV10Panel.load(saved, states, new ArrayList<>(), trajectory);
        assertEquals(16, states.size()); assertEquals(75, states.get(15).state.turn);
        assertEquals(0, trajectory.get(0).get("newReferenceCalls"));
        JsonObject badScore = saved.deepCopy();
        badScore.getAsJsonArray("trajectory").get(1).getAsJsonObject().add("scoreAfter", JSON.toJsonTree(new int[]{1,0}));
        assertThrows(IllegalStateException.class, () -> SpatialV10Panel.load(badScore,new ArrayList<>(),new ArrayList<>(),new ArrayList<>()));
        JsonObject duplicate = saved.deepCopy();
        duplicate.getAsJsonArray("positions").set(1,duplicate.getAsJsonArray("positions").get(0).deepCopy());
        assertThrows(IllegalStateException.class, () -> SpatialV10Panel.load(duplicate,new ArrayList<>(),new ArrayList<>(),new ArrayList<>()));
    }

    @Test void reusedConditionalTraceMatchesActualCandidateRuntimeAndPreservesTheState() throws Exception {
        GameState state = new GameState(5,2,new int[]{0,1,2,3,4,5,6,7,8,9},new int[10],new int[]{0,4},new int[]{1,1},new int[][]{{1},{0}});
        state.owner[2] = 1; state.turn = 37; Rules.updateConnections(state,new Pathing(state.size));
        String before = SpatialLabelAudit.semanticHash(state);
        for (int me = 0; me < 2; me++) {
            var trace = SpatialLatePanel.trace(state,me,new SpatialNet(SpatialV10Weights.DATA));
            SpatialV10Bot bot = new SpatialV10Bot(BotFactory.parameters("spatial-ppo-v10"),new SpatialNet(SpatialV10Weights.DATA),() -> 0L);
            bot.init(state,me);
            assertEquals(Action.format(trace.actions),Action.format(bot.play(state,me,60_000)));
            assertEquals(4,trace.rows.size());
            for (int stage = 0; stage < 4; stage++) {
                JsonObject row = trace.rows.get(stage); assertEquals(stage,row.get("stage").getAsInt());
                int selected = row.getAsJsonObject("java").get("selected").getAsInt();
                assertTrue(row.getAsJsonObject("observation").getAsJsonArray(stage == 3 ? "disruptMask" : "buildMask").get(selected).getAsBoolean());
            }
        }
        assertEquals(before,SpatialLabelAudit.semanticHash(state));
    }

    private static JsonObject fixture() throws Exception {
        JsonObject saved = new JsonObject(); saved.addProperty("firstSeed",10100000); saved.addProperty("plannedMaps",4); saved.addProperty("positionsCount",16);
        JsonArray maps = new JsonArray(), positions = new JsonArray(), trajectory = new JsonArray();
        saved.add("maps",maps); saved.add("positions",positions); saved.add("trajectory",trajectory);
        for (int index = 0; index < 4; index++) {
            long seed = 10100000 + index;
            GameState state = new GameState(3,1,new int[]{0,1,2},new int[3],new int[]{0,2},new int[]{0,0},new int[][]{{1},{}});
            Pathing pathing = new Pathing(state.size); Rules.updateConnections(state,pathing);
            String init = Protocol.initBlock(state,0), hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(init.getBytes(StandardCharsets.UTF_8)));
            JsonObject map = new JsonObject(); map.addProperty("seed",seed); map.addProperty("mapHash",hash); map.addProperty("initBlockP0",init);
            map.addProperty("endTurn",75); map.addProperty("terminal",false); map.add("scoreAtStop",JSON.toJsonTree(new int[2])); maps.add(map);
            for (int turn = 0; turn <= 75; turn++) {
                if (turn % 25 == 0) positions.add(JSON.toJsonTree(new SpatialV9Panel.Position(seed,hash,state.copy()).snapshot()));
                if (turn == 75) break;
                JsonObject row = new JsonObject(); row.addProperty("seed",seed); row.addProperty("mapHash",hash); row.addProperty("turn",turn);
                JsonArray seats = new JsonArray();
                for (int me = 0; me < 2; me++) { JsonObject seat = new JsonObject(); seat.addProperty("actions","WAIT"); seats.add(seat); }
                row.add("seats",seats); Rules.TurnResult next = Rules.resolve(state,List.of(Action.waitAction()),List.of(Action.waitAction()),pathing);
                row.add("gained",JSON.toJsonTree(next.gained)); row.add("scoreAfter",JSON.toJsonTree(state.score)); trajectory.add(row);
            }
        }
        return saved;
    }
}
