package btk.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CgRenderSeedTest {
    @TempDir Path temp;

    @Test void rendererCarriesExactSignedSeedThroughBothCategoriesAndPreservesCanonicalBytes() throws Exception {
        long[] seeds = {Long.MIN_VALUE, Long.MAX_VALUE, -176203007668178900L, 9_007_199_254_740_993L, 0};
        for (int i = 0; i < seeds.length; i++) {
            long seed = seeds[i];
            boolean boss = i % 2 == 0;
            long gameId = 1000 + i;
            var raw = fixture(gameId, "# referee input\nother=value\nseed=" + seed + "\n", boss);
            Path input = temp.resolve(gameId + ".json");
            Files.writeString(input, raw.toString());
            byte[] original = Files.readAllBytes(input);
            Path output = temp.resolve("rendered");
            CgRender.main(new String[]{input.toString(), output.toString()});
            Path rendered = output.resolve(boss ? "boss" : "arena").resolve(gameId + ".json");
            var document = JsonParser.parseString(Files.readString(rendered)).getAsJsonObject();
            var meta = document.getAsJsonObject("meta");
            assertEquals(seed, meta.get("seed").getAsLong());
            assertTrue(meta.getAsJsonPrimitive("seedText").isString());
            assertEquals(Long.toString(seed), meta.get("seedText").getAsString());
            assertEquals(boss ? "cg-bot" : "cg-arena", meta.get("source").getAsString());
            assertEquals(1, document.getAsJsonArray("turns").size());
            assertArrayEquals(original, Files.readAllBytes(input), "Rendering must not rewrite canonical evidence");
        }
    }

    @Test void missingCgSeedIsOmittedAndInvalidCgSeedIsNeverReplacedWithZero() {
        for (String missing : new String[]{null, "", "other=123\n"}) {
            var outcome = CgValidate.validate(CgReplay.parse(fixture(2000, missing, true)), false);
            assertEquals(-1, outcome.firstDivergenceTurn);
            var meta = JsonParser.parseString(outcome.replay.toJson()).getAsJsonObject().getAsJsonObject("meta");
            assertFalse(meta.has("seed"));
            assertFalse(meta.has("seedText"));
        }
        for (String invalid : new String[]{"seed=invalid", "seed=9223372036854775808", "seed="}) {
            assertThrows(IllegalArgumentException.class,
                    () -> CgValidate.validate(CgReplay.parse(fixture(2000, invalid, false)), false));
        }
    }

    private static JsonObject fixture(long gameId, String refereeInput, boolean boss) {
        var raw = new JsonObject();
        raw.addProperty("gameId", gameId);
        if (refereeInput != null) raw.addProperty("refereeInput", refereeInput);
        var agents = new JsonArray();
        for (int i = 0; i < 2; i++) {
            var agent = new JsonObject();
            agent.addProperty("index", i);
            agent.addProperty("agentId", boss && i == 1 ? -2 : 100 + i);
            agents.add(agent);
        }
        raw.add("agents", agents);
        var frames = new JsonArray();
        var initial = new JsonObject();
        initial.addProperty("view", " 0\n{\"global\":{\"graphics\":\"2|1|3|0 0|1 0|0\"}}");
        frames.add(initial);
        for (int player = 0; player < 2; player++) {
            var frame = new JsonObject();
            frame.addProperty("agentId", player);
            frame.addProperty("keyframe", player == 1);
            frame.addProperty("stdout", "WAIT");
            frame.addProperty("view", player == 1
                    ? " 1\n{\"graphics\":\"0\",\"endScreen\":[[0,0],\"\",[]]}" : " 1\n");
            frames.add(frame);
        }
        raw.add("frames", frames);
        return raw;
    }
}
