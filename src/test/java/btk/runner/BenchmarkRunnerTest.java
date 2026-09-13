package btk.runner;

import btk.bot.Bot;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Protocol;
import btk.referee.MapGenerator;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BenchmarkRunnerTest {
    @TempDir Path temp;
    record Seen(String bot, int side, String map) {}

    @Test
    void bothSidesActuallyReceiveTheSameGeneratedMapsWithFreshBots() throws Exception {
        var seen = new ConcurrentLinkedQueue<Seen>();
        AtomicInteger created = new AtomicInteger();
        var stats = BenchmarkRunner.run("v01", "v02", 4, new Maps(""), 2, 123,
                spec -> observer(spec, seen, created));
        assertEquals(8, created.get());
        assertEquals(8, seen.size());
        assertEquals(4, stats.games);
        assertEquals(2, stats.gamesAasP0);
        assertEquals(2, stats.gamesAasP1);
        assertEquals(4, stats.draws);
        assertEquals(0.5, stats.rateA());
        assertEquals(List.of(123L, 124L), stats.pairs.stream().map(BenchmarkRunner.PairResult::seed).toList());
        for (long seed : new long[]{123, 124}) {
            String expected = Protocol.initBlock(MapGenerator.generate(seed), 0);
            for (String spec : List.of("v01", "v02")) {
                for (int side = 0; side < 2; side++) assertEquals(1, count(seen, spec, side, expected));
            }
        }
        assertEquals(2, stats.gamesPerMap.size());
    }

    @Test
    void replayPoolWrapsByPairAndIdenticalMapsRemainOneConfidenceGroup() throws Exception {
        writeReplay(temp.resolve("a.json"), 0);
        writeReplay(temp.resolve("b.json"), 1);
        writeReplay(temp.resolve("c.json"), 0); // Same map under a different replay filename.
        Maps maps = new Maps(temp.toString());
        var seen = new ConcurrentLinkedQueue<Seen>();
        AtomicInteger created = new AtomicInteger();
        var stats = BenchmarkRunner.run("v01", "v02", 10, maps, 2, 500,
                spec -> observer(spec, seen, created));
        assertEquals(List.of("a.json", "b.json", "c.json", "a.json", "b.json"),
                stats.pairs.stream().map(BenchmarkRunner.PairResult::map).toList());
        assertEquals(2, stats.gamesPerMap.size());
        assertEquals(List.of(4, 6), stats.gamesPerMap.values().stream().sorted().toList());
        for (String spec : List.of("v01", "v02")) {
            for (int side = 0; side < 2; side++) {
                assertEquals(3, count(seen, spec, side, Protocol.initBlock(maps.get(0, 1), 0)));
                assertEquals(2, count(seen, spec, side, Protocol.initBlock(maps.get(1, 1), 0)));
            }
        }
        Path report = temp.resolve("report/result.json");
        stats.write(report);
        JsonObject json = JsonParser.parseString(Files.readString(report)).getAsJsonObject();
        assertEquals(5, json.getAsJsonArray("pairs").size());
        assertEquals(500L, json.get("baseSeed").getAsLong());
        assertEquals("CG", json.get("timingProfile").getAsString());
        assertEquals(TimingProfile.CG.turnSearchMs, json.get("turnSearchBudgetMs").getAsLong());
        assertEquals("0.1", json.getAsJsonObject("paramsA").get("NOISE").getAsString());
        assertFalse(json.get("promotionGatePassed").getAsBoolean());
    }

    @Test
    void duplicatedGamesDoNotArtificiallyNarrowConfidenceOrPassTheGate() {
        var stats = new BenchmarkRunner.Stats("v01", "v02");
        stats.games = 200;
        stats.winsA = 160;
        stats.winsB = 40;
        for (int i = 0; i < 100; i++) stats.gamesPerMap.put("map" + i, 2);
        double[] original = stats.confidence95();
        assertEquals(0.8 - Math.sqrt(Math.log(40) / 200), original[0], 1e-12);
        assertTrue(stats.promotionGatePassed());
        stats.games *= 2;
        stats.winsA *= 2;
        stats.winsB *= 2;
        stats.gamesPerMap.replaceAll((map, count) -> count * 2);
        assertArrayEquals(original, stats.confidence95(), 1e-12);
        stats.gamesPerMap.clear();
        stats.gamesPerMap.put("same map", stats.games);
        assertArrayEquals(new double[]{0, 1}, stats.confidence95());
        assertFalse(stats.promotionGatePassed());
    }

    @Test
    void emptyOrMalformedMapPoolsAndInvalidRunSettingsFailFast() throws Exception {
        assertThrows(java.io.IOException.class, () -> new Maps(temp.toString()));
        Files.writeString(temp.resolve("bad.json"), "{}");
        assertThrows(java.io.IOException.class, () -> new Maps(temp.toString()));
        Maps generated = new Maps("");
        for (int games : new int[]{-2, 0, 1, 3}) {
            assertThrows(IllegalArgumentException.class, () -> BenchmarkRunner.run("v01", "v02", games, generated, 1, 1));
        }
        assertThrows(IllegalArgumentException.class, () -> BenchmarkRunner.run("v01", "v02", 2, generated, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> BenchmarkRunner.run("v01", "v02", 4, generated, 1, -1));
        assertThrows(ArithmeticException.class, () -> BenchmarkRunner.run("v01", "v02", 4, generated, 1, Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> BenchmarkRunner.run("v99", "v02", 2, generated, 1, 1));
    }

    private static Bot observer(String spec, ConcurrentLinkedQueue<Seen> seen, AtomicInteger created) {
        created.incrementAndGet();
        return new Bot() {
            boolean initialized;
            public String name() { return spec; }
            public void init(GameState state, int side) {
                assertFalse(initialized, "bot instance reused across games");
                initialized = true;
                for (int owner : state.owner) assertEquals(GameState.NO_TRACK, owner);
                seen.add(new Seen(spec, side, Protocol.initBlock(state, 0)));
            }
            public List<Action> play(GameState state, int side, long budgetMs) {
                assertTrue(initialized);
                return List.of(Action.waitAction());
            }
        };
    }

    private static long count(ConcurrentLinkedQueue<Seen> seen, String spec, int side, String map) {
        return seen.stream().filter(s -> s.bot().equals(spec) && s.side() == side && s.map().equals(map)).count();
    }

    private static void writeReplay(Path file, int terrain) throws Exception {
        JsonObject global = new JsonObject();
        global.addProperty("graphics", "4|1|3|0 0|1 " + terrain + "|2 0|3 0|2|0 0 0 1|1 3 0 x");
        JsonObject view = new JsonObject();
        view.add("global", global);
        JsonObject frame = new JsonObject();
        frame.addProperty("view", " 0\n" + view);
        JsonArray frames = new JsonArray();
        frames.add(frame);
        JsonObject replay = new JsonObject();
        replay.add("frames", frames);
        Files.writeString(file, replay.toString());
    }
}
