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
    @Test void reportsPreserveMachineIdsAlongsideLabelsAndExperimentIdentity() throws Exception {
        var stats = new BenchmarkRunner.Stats("exp-reply-rerank", "v05");
        assertEquals("exp-reply-rerank", stats.a);
        assertEquals("v05", stats.b);
        assertEquals("not_promoted", stats.botA.status());
        assertEquals("v05 - Disrupt Reserve", stats.botB.displayName());
        assertNotNull(stats.botA.implementationSha256());
        assertEquals("true", stats.paramsA.get("USE_REPLY_RERANK"));
        assertTrue(stats.report().contains("Reply Rerank"));
        assertTrue(stats.report().contains("not_promoted"));
    }
    @TempDir Path temp;
    record Seen(String bot, int side, String map) {}

    @Test void practicalThresholdsCountDrawsAndRequireCompleteFreshPairs() {
        var stats = new BenchmarkRunner.Stats("v01", "v02");
        stats.games = 300; stats.winsA = 179; stats.draws = 2;
        for (int i = 0; i < 150; i++) stats.gamesPerMap.put("map" + i, 2);
        assertEquals("PASS_300", stats.practicalDirectStage());
        assertFalse(stats.promotionGatePassed(), "60% does not pass the historical CI gate");
        stats.draws = 1;
        assertEquals("NEEDS_FRESH_1000", stats.practicalDirectStage());
        stats.winsA = 155;
        assertEquals("STOP_SCREEN_300", stats.practicalDirectStage()); // 51.833...%
        stats.draws = 2;
        assertEquals("STOP_SCREEN_300", stats.practicalDirectStage()); // exactly 52%
        stats.draws = 3;
        assertEquals("NEEDS_FRESH_1000", stats.practicalDirectStage()); // 52.166...%
        stats.games = 1000; stats.winsA = 549; stats.draws = 2;
        for (int i = 150; i < 500; i++) stats.gamesPerMap.put("map" + i, 2);
        assertEquals("PASS_1000", stats.practicalDirectStage());
        stats.draws = 1;
        assertEquals("NOT_PASSED", stats.practicalDirectStage());
        stats.gamesPerMap.remove("map499"); stats.gamesPerMap.put("map0", 4);
        assertEquals("INVALID_MAP_COVERAGE", stats.practicalDirectStage());
        stats.errorsB = 1;
        assertEquals("INVALID_ERRORS", stats.practicalDirectStage());
        stats.errorsB = 0; stats.timingProfile = TimingProfile.LEGACY;
        assertEquals("INVALID_TIMING", stats.practicalDirectStage());
        stats.games = 1300;
        assertEquals("NOT_APPLICABLE", stats.practicalDirectStage());
    }

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
        assertEquals(3, json.get("schemaVersion").getAsInt());
        assertEquals("practical-300-1000-boss-cache-v3", json.get("promotionPolicy").getAsString());
        assertEquals(10, json.getAsJsonObject("byHeight").getAsJsonObject("1").get("games").getAsInt());
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

    @Test
    void heightSlicesPreserveSideMappingAndDuplicateMapUncertainty() {
        var stats = new BenchmarkRunner.Stats("v01", "v02");
        var r0 = new btk.referee.Referee.Result(); var r1 = new btk.referee.Referee.Result();
        r0.winner = 0; r1.winner = 0; r0.turns = 10; r1.turns = 20;
        r0.score[0] = 100; r1.score[0] = 60;
        r0.disruptTurns[0] = 8; r0.disruptTurns[1] = 2;
        r1.disruptTurns[0] = 4; r1.disruptTurns[1] = 12;
        r0.closedEarly = true;
        var p = new BenchmarkRunner.PairResult(0, 1, "a", "same", 20, 14, 4, 6, r0, r1);
        stats.add(p);
        double[] firstCi = stats.byHeight.get(14).confidence95.clone();
        stats.add(p);
        stats.add(new BenchmarkRunner.PairResult(1, 2, "b", "other", 30, 20, 8, 12, r0, r1));
        assertEquals(6, stats.games);
        assertEquals(4, stats.byHeight.get(14).games); assertEquals(2, stats.byHeight.get(20).games);
        assertArrayEquals(firstCi, stats.byHeight.get(14).confidence95);
        assertArrayEquals(new long[]{60, 18}, stats.behavior.disruptTurns);
        assertEquals(2.0 / 3, stats.behavior.disruptRate[0], 1e-12);
        assertEquals(3, stats.behavior.closedEarly);
        assertEquals(0.5, stats.byHeight.get(14).rateA);
        assertEquals(20, stats.byHeight.get(14).avgDiff);
        assertEquals(stats.games, stats.byHeight.values().stream().mapToInt(s -> s.games).sum());
        assertEquals(stats.sumDiff, stats.byHeight.values().stream().mapToLong(s -> s.sumDiff).sum());
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
