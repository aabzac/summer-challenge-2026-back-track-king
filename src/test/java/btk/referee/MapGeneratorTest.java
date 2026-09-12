package btk.referee;

import btk.core.GameState;
import btk.runner.Maps;
import btk.tools.CgReplay;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class MapGeneratorTest {

    @TestFactory
    Stream<DynamicTest> reproducesIndependentCgMapsForEveryHeight() throws Exception {
        List<CgReplay> fixtures = fixtures();
        Set<Integer> heights = new HashSet<>();
        for (CgReplay fixture : fixtures) heights.add(fixture.toState().height);
        assertEquals(Set.of(14, 15, 16, 17, 18, 19, 20), heights);
        return fixtures.stream().map(replay -> DynamicTest.dynamicTest("CG game " + replay.gameId, () -> {
            GameState actual = MapGenerator.generate(replay.mapSeed().orElseThrow());
            assertMapEquals(replay.toState(), actual);
            assertEquals(0, actual.turn);
            assertArrayEquals(new int[2], actual.score);
            for (int owner : actual.owner) assertEquals(GameState.NO_TRACK, owner);
        }));
    }

    @Test
    void parallelRunnerRequestsHaveIndependentRandomStreamsAndStates() throws Exception {
        Maps maps = new Maps("");
        var pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Void>> jobs = new ArrayList<>();
            for (CgReplay replay : fixtures()) {
                for (int repeat = 0; repeat < 2; repeat++) {
                    jobs.add(() -> {
                        long seed = replay.mapSeed().orElseThrow();
                        GameState first = maps.get(0, seed);
                        assertMapEquals(replay.toState(), first);
                        first.terrain[0] = -99;
                        first.owner[0] = 0;
                        first.desired[0] = new int[0];
                        GameState second = maps.get(0, seed);
                        assertMapEquals(replay.toState(), second);
                        assertEquals(GameState.NO_TRACK, second.owner[0]);
                        return null;
                    });
                }
            }
            for (var result : pool.invokeAll(jobs)) result.get();
        } finally {
            pool.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {1, -1, Long.MIN_VALUE, Long.MAX_VALUE})
    void fullSignedSeedRangeIsRepeatable(long seed) {
        assertMapEquals(MapGenerator.generate(seed), MapGenerator.generate(seed));
    }

    @Test
    void rejectsZeroRatherThanUsingUnseededSystemRandomness() {
        assertThrows(IllegalArgumentException.class, () -> MapGenerator.generate(0));
    }

    private static List<CgReplay> fixtures() throws Exception {
        var input = MapGeneratorTest.class.getResourceAsStream("/btk/referee/cg-maps.json");
        assertNotNull(input, "checked-in CG map fixtures");
        try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            List<CgReplay> fixtures = new ArrayList<>();
            for (JsonElement element : JsonParser.parseReader(reader).getAsJsonArray()) {
                JsonObject fixture = element.getAsJsonObject();
                CgReplay replay = new CgReplay();
                replay.gameId = fixture.get("gameId").getAsLong();
                replay.seed = fixture.get("refereeInput").getAsString();
                replay.globalGraphics = fixture.get("globalGraphics").getAsString();
                fixtures.add(replay);
            }
            return fixtures;
        }
    }

    private static void assertMapEquals(GameState expected, GameState actual) {
        assertEquals(expected.width, actual.width, "width");
        assertEquals(expected.height, actual.height, "height");
        assertArrayEquals(expected.regionId, actual.regionId, "region IDs");
        assertArrayEquals(expected.terrain, actual.terrain, "terrain");
        assertArrayEquals(expected.townX, actual.townX, "town x");
        assertArrayEquals(expected.townY, actual.townY, "town y");
        assertEquals(expected.desired.length, actual.desired.length, "town count");
        for (int t = 0; t < expected.townCount; t++) {
            assertArrayEquals(expected.desired[t], actual.desired[t], "town " + t + " desired connections");
        }
    }
}
