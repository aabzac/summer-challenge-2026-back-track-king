package btk.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertFalse(replay.isBossGame());
        replay.gameId = 456;
        assertEquals(Path.of("rendered", "arena", "456.json"),
                CgRender.outputPath(Path.of("rendered"), replay));
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
        assertTrue(replay.isBossGame());
        replay.gameId = 123;
        assertEquals(Path.of("rendered", "bot", "123.json"),
                CgRender.outputPath(Path.of("rendered"), replay));
    }

    @Test
    void positiveArenaBossIdUsesAuthoritativeSlotAndRendersAsBossGame() {
        for (int slot = 0; slot < 2; slot++) {
            JsonObject root = new JsonObject();
            JsonArray agents = new JsonArray();
            JsonObject boss = new JsonObject();
            boss.addProperty("index", slot);
            boss.addProperty("agentId", 6740207);
            JsonObject identity = new JsonObject();
            identity.addProperty("nickname", "Boss 4");
            boss.add("arenaboss", identity);
            agents.add(boss); // array order deliberately differs from player order for P1
            agents.add(agent(1 - slot, 100, "aabzac"));
            root.add("agents", agents);
            root.add("frames", initFrames());
            CgReplay replay = CgReplay.parse(root);
            assertEquals("Boss 4", replay.nicknames[slot]);
            assertEquals("aabzac", replay.nicknames[1 - slot]);
            assertTrue(replay.isBossGame());
            replay.gameId = 904146009;
            assertEquals(Path.of("rendered", "bot", "904146009.json"),
                    CgRender.outputPath(Path.of("rendered"), replay));
        }
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
