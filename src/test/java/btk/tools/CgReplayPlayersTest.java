package btk.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CgReplayPlayersTest {

    @Test
    void gameAgentIndicesOverrideLegacyWinnerOrderedNicknames() {
        JsonObject root = new JsonObject();
        JsonArray agents = new JsonArray();
        agents.add(agent(0, 100, "MiniWall"));
        agents.add(agent(1, 200, "aabzac"));
        root.add("agents", agents);
        JsonObject legacy = new JsonObject();
        legacy.addProperty("0", "aabzac");
        legacy.addProperty("1", "MiniWall");
        root.add("btkNicknames", legacy);
        root.add("frames", initFrames());

        CgReplay replay = CgReplay.parse(root);

        assertEquals("MiniWall", replay.nicknames[0]);
        assertEquals("aabzac", replay.nicknames[1]);
    }

    @Test
    void fetchUsesGameIndicesInsteadOfBattleFinishingOrder() {
        JsonObject game = new JsonObject();
        JsonArray agents = new JsonArray();
        agents.add(agent(0, 100, "MiniWall"));
        agents.add(agent(1, 200, "aabzac"));
        game.add("agents", agents);

        JsonObject battle = new JsonObject();
        JsonArray players = new JsonArray();
        // Winner-first order: deliberately the reverse of the in-game indices.
        players.add(battlePlayer(0, 200, "aabzac"));
        players.add(battlePlayer(1, 100, "MiniWall"));
        battle.add("players", players);

        JsonObject names = CgFetch.nicknamesByGameIndex(game, battle);

        assertEquals("MiniWall", names.get("0").getAsString());
        assertEquals("aabzac", names.get("1").getAsString());
    }

    @Test
    void testSessionBossAgentGetsReadableName() {
        JsonObject root = new JsonObject();
        JsonArray agents = new JsonArray();
        agents.add(agent(0, 100, "aabzac"));
        JsonObject boss = new JsonObject();
        boss.addProperty("index", 1);
        boss.addProperty("agentId", -2);
        agents.add(boss);
        root.add("agents", agents);
        root.add("frames", initFrames());

        CgReplay replay = CgReplay.parse(root);

        assertEquals("aabzac", replay.nicknames[0]);
        assertEquals("Boss", replay.nicknames[1]);
    }

    private static JsonObject agent(int index, long userId, String pseudo) {
        JsonObject codingamer = new JsonObject();
        codingamer.addProperty("userId", userId);
        codingamer.addProperty("pseudo", pseudo);
        JsonObject agent = new JsonObject();
        agent.addProperty("index", index);
        agent.add("codingamer", codingamer);
        return agent;
    }

    private static JsonObject battlePlayer(int position, long userId, String nickname) {
        JsonObject player = new JsonObject();
        player.addProperty("position", position);
        player.addProperty("userId", userId);
        player.addProperty("nickname", nickname);
        return player;
    }

    private static JsonArray initFrames() {
        JsonObject frame = new JsonObject();
        frame.addProperty("view", " 0\n{}");
        JsonArray frames = new JsonArray();
        frames.add(frame);
        return frames;
    }
}
