package btk.tools.spatial;

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

class SpatialV9PanelTest {
    private static final Gson JSON = new Gson();
    @Test void savedStatesComeFromExactCoreReplayAndRejectChangedSnapshots() throws Exception {
        JsonObject saved = synthetic();
        var states = new ArrayList<SpatialV9Panel.Position>();
        SpatialV9Panel.loadSaved(saved, states, new ArrayList<>(), new ArrayList<>());
        assertEquals(16, states.size()); assertEquals(75, states.get(15).state.turn);
        saved.getAsJsonArray("positions").get(0).getAsJsonObject().addProperty("turnBlockP0", "corrupted");
        assertThrows(IllegalStateException.class, () -> SpatialV9Panel.loadSaved(saved, new ArrayList<>(), new ArrayList<>(), new ArrayList<>()));
    }
    @Test void corruptedSavedTransitionFailsBeforeProfiling() throws Exception {
        JsonObject saved = synthetic();
        saved.getAsJsonArray("trajectory").get(1).getAsJsonObject().add("scoreAfter", JSON.toJsonTree(new int[]{1,0}));
        assertThrows(IllegalStateException.class, () -> SpatialV9Panel.loadSaved(saved, new ArrayList<>(), new ArrayList<>(), new ArrayList<>()));
    }
    private static JsonObject synthetic() throws Exception {
        JsonObject saved = new JsonObject(); saved.addProperty("firstSeed", 10100000); saved.addProperty("plannedMaps", 4);
        saved.addProperty("positionsCount", 16); JsonArray maps = new JsonArray(), positions = new JsonArray(), trajectory = new JsonArray();
        saved.add("maps", maps); saved.add("positions", positions); saved.add("trajectory", trajectory);
        for (int m = 0; m < 4; m++) {
            long seed = 10100000 + m;
            GameState s = new GameState(3,1,new int[]{0,1,2},new int[3],new int[]{0,2},new int[]{0,0},new int[][]{{1},{}});
            Pathing pathing = new Pathing(s.size); Rules.updateConnections(s, pathing);
            String init = Protocol.initBlock(s,0), hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(init.getBytes(StandardCharsets.UTF_8)));
            JsonObject map = new JsonObject(); map.addProperty("seed", seed); map.addProperty("mapHash", hash); map.addProperty("initBlockP0", init);
            map.addProperty("endTurn", 75); map.addProperty("terminal", false); map.add("scoreAtStop", JSON.toJsonTree(new int[2])); maps.add(map);
            for (int turn = 0; turn <= 75; turn++) {
                if (turn % 25 == 0) positions.add(JSON.toJsonTree(new SpatialV9Panel.Position(seed,hash,s.copy()).snapshot()));
                if (turn == 75) break;
                JsonObject row = new JsonObject(); row.addProperty("seed", seed); row.addProperty("mapHash", hash); row.addProperty("turn", turn);
                JsonArray seats = new JsonArray();
                for (int me = 0; me < 2; me++) { JsonObject seat = new JsonObject(); seat.addProperty("actions", "WAIT"); seats.add(seat); }
                row.add("seats", seats);
                Rules.TurnResult result = Rules.resolve(s, List.of(Action.waitAction()), List.of(Action.waitAction()), pathing);
                row.add("gained", JSON.toJsonTree(result.gained)); row.add("scoreAfter", JSON.toJsonTree(s.score)); trajectory.add(row);
            }
        }
        return saved;
    }
}
