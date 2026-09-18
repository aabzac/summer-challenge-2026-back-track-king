package btk.submit;

import btk.core.Action;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BuildProvenanceTest {
    @TempDir Path temp;

    @Test void markerIsDeterministicAndManifestBindsTheExactFinalArtifact() throws Exception {
        String source = FileMerger.merge("v07");
        assertEquals(source, FileMerger.merge("v07"));
        var manifest = FileMerger.manifest("v07", source);
        assertEquals(FileMerger.sha256(source), manifest.get("sha256"));
        assertEquals(FileMerger.sha256(FileMerger.normalizeMarker(source)), manifest.get("buildId"));
        assertNotEquals(manifest.get("sha256"), manifest.get("buildId"));
        assertEquals(FileMerger.configSha256("v07"), manifest.get("configSha256"));
        assertTrue(source.contains("BUILD_MARKER = \"" + manifest.get("marker") + "\";"));
        assertTrue(((String) manifest.get("marker")).startsWith("BTK_BUILD_V1 strategy=v07 build="));
        assertThrows(IllegalArgumentException.class, () -> FileMerger.manifest("current", source));
        FileMerger.checkSourceSize(source);
    }

    @Test void configurationAndSourceIdentityChangeTogetherWithoutChangingFrozenCode() throws Exception {
        String source = FileMerger.merge("v07"), changed = FileMerger.merge("v07?NOISE=0");
        assertNotEquals(FileMerger.manifest("v07", source).get("buildId"),
                FileMerger.manifest("v07?NOISE=0", changed).get("buildId"));
        assertNotEquals(FileMerger.configSha256("v07"), FileMerger.configSha256("v07?NOISE=0"));
        assertEquals(source, FileMerger.merge("v07?NOISE=0.1"), "equivalent requested configurations have one identity");
    }

    @Test void firstTurnKeepsOneMarkerMessageAlongsideGameplayAndDebugText() {
        var actions = List.of(Action.place(2, 3), Action.disrupt(4), Action.message("timing 12ms"));
        String marker = "BTK_BUILD_V1 strategy=v07 build=" + "a".repeat(64) + " config=" + "b".repeat(64);
        String first = CGRunner.formatOutput(actions, 0, marker);
        assertEquals("PLACE_TRACKS 2 3;DISRUPT 4;MESSAGE " + marker + " note=timing 12ms", first);
        assertEquals(1, Action.parseLine(first).stream().filter(a -> a.type == Action.Type.MESSAGE).count());
        assertEquals(Action.format(actions), CGRunner.formatOutput(actions, 1, marker));
        assertEquals(Action.format(actions), CGRunner.formatOutput(actions, 0, ""));
        assertEquals("WAIT;MESSAGE " + marker, CGRunner.formatOutput(List.of(), 0, marker));
        assertEquals("", CGRunner.BUILD_MARKER, "normal repository runtime does not claim a merged artifact");
    }

    @Test void registeredArtifactsAreImmutableAndIdempotent() throws Exception {
        String source = FileMerger.merge("v07");
        var manifest = FileMerger.manifest("v07", source);
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(manifest);
        String id = (String) manifest.get("buildId");
        FileMerger.register(temp, source, json, id);
        FileMerger.register(temp, source, json, id);
        assertThrows(java.io.IOException.class, () -> FileMerger.register(temp, source + "\n", json, id));
        assertEquals(source, Files.readString(temp.resolve(id).resolve("Player.java")));
        assertEquals(json, Files.readString(temp.resolve(id).resolve("manifest.json")));
    }
}
