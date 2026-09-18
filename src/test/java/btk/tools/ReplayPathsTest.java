package btk.tools;

import btk.runner.Maps;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReplayPathsTest {
    @TempDir Path directory;

    @Test void missingHistoricalCanonicalAndRenderedPathsResolveWithoutMovingFiles() throws Exception {
        for (String root : List.of("replays/cg", "replays/rendered/cg")) {
            Path old = directory.resolve(root).resolve("bot/123.json");
            Path current = write(root + "/boss/123.json", "original bytes");
            assertEquals(current, ReplayPaths.resolve(old));
            assertEquals(current.getParent(), ReplayPaths.resolve(old.getParent()));
            assertFalse(Files.exists(old));
            assertEquals("original bytes", Files.readString(current));
            assertEquals(current, ReplayPaths.resolve(current));
        }
    }

    @Test void existingExplicitPathsAndUnrelatedBotDirectoriesAreNeverRemapped() throws Exception {
        Path old = write("replays/cg/bot/123.json", "explicit");
        write("replays/cg/boss/123.json", "renamed");
        assertEquals(old, ReplayPaths.resolve(old));
        Path unrelated = directory.resolve("elsewhere/bot/123.json");
        write("elsewhere/boss/123.json", "unrelated");
        assertEquals(unrelated, ReplayPaths.resolve(unrelated));
        Path absent = directory.resolve("replays/cg/bot/404.json");
        assertEquals(absent, ReplayPaths.resolve(absent));
    }

    @Test void bossDiscoveryFindsNestedCanonicalGamesAndExcludesRawAndMetadata() throws Exception {
        Path flat = write("replays/cg/boss/100.json", "{}");
        Path unknown = write("replays/cg/boss/unknown/101.json", "{}");
        Path version = write("replays/cg/boss/v07/build-id/102.json", "{}");
        write("replays/cg/boss/raw/unknown/103.json", "{}");
        write("replays/cg/boss/_metadata/104.json", "{}");
        write("replays/cg/boss/v07/build-id/102.meta.json", "{}");
        write("replays/cg/boss/v07/build-id/manifest.json", "{}");
        write("replays/cg/boss/v07/build-id/105.txt", "{}");
        Files.createDirectories(directory.resolve("replays/cg/boss/106.json"));
        List<Path> expected = List.of(flat, unknown, version).stream().sorted().toList();
        assertEquals(expected, CgReplay.listReplays(directory.resolve("replays/cg/boss")));
        assertEquals(expected, CgReplay.listReplays(directory.resolve("replays/cg/bot")));
        assertEquals(List.of(version), CgReplay.listReplays(version.getParent()));
        assertTrue(CgReplay.listReplays(directory.resolve("replays/cg/boss/raw")).isEmpty());
        assertTrue(CgReplay.listReplays(directory.resolve("replays/cg/boss/_metadata")).isEmpty());
    }

    @Test void defaultArenaDiscoveryRemainsFlat() throws Exception {
        Path arena = write("replays/cg/123.json", "{}");
        write("replays/cg/boss/456.json", "{}");
        write("replays/cg/other/789.json", "{}");
        assertEquals(List.of(arena), CgReplay.listReplays(directory.resolve("replays/cg")));
    }

    @Test void duplicateBossCapturesCannotSilentlyWeightTheMapPoolTwice() throws Exception {
        write("replays/cg/boss/123.json", "{}");
        write("replays/cg/boss/v07/build-id/123.json", "{}");
        IOException error = assertThrows(IOException.class,
                () -> CgReplay.listReplays(directory.resolve("replays/cg/boss")));
        assertTrue(error.getMessage().contains("Duplicate boss replay filename"));
    }

    @Test void historicalFileAndMapPoolLoadAfterRename() throws Exception {
        JsonObject global = new JsonObject();
        global.addProperty("graphics", "3|1|3|0 0|1 0|2 0|2|0 0 0 1|1 2 0 x");
        JsonObject view = new JsonObject(); view.add("global", global);
        JsonObject frame = new JsonObject(); frame.addProperty("view", " 0\n" + view);
        JsonArray frames = new JsonArray(); frames.add(frame);
        JsonObject replay = new JsonObject(); replay.addProperty("gameId", 123); replay.add("frames", frames);
        write("replays/cg/boss/123.json", replay.toString());
        Path historical = directory.resolve("replays/cg/bot");
        assertEquals(123, CgReplay.load(historical.resolve("123.json")).gameId);
        Maps maps = new Maps(historical.toString());
        assertTrue(maps.fromReplays());
        assertEquals(1, maps.poolSize());
        assertEquals(3, maps.get(0, 0).width);
    }

    private Path write(String relative, String content) throws Exception {
        Path file = directory.resolve(relative);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }
}
