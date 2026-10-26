package btk.bot;

import btk.bot.current.RolloutBot;
import btk.bot.v09.RolloutBotV09;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.referee.MapGenerator;
import btk.tools.CgReplay;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** v09 Never Idle: the exact fill-v1 candidate (current?USE_ROLLOUT_FILL=1) frozen without retuning. */
class V09PromotionTest {
    private static final Gson JSON = new Gson();

    private static TreeMap<String, String> record(String resource) throws Exception {
        Properties properties = new Properties();
        try (var in = V09PromotionTest.class.getResourceAsStream(resource)) {
            assertNotNull(in);
            properties.load(in);
        }
        var result = new TreeMap<String, String>();
        for (String key : properties.stringPropertyNames()) result.put(key, properties.getProperty(key));
        return result;
    }

    private static Params measuredParameters() throws Exception {
        Params params = LiveTargetExperimentParams.v09Params(); // the record predates the seven v10 flags
        for (var entry : record("/v09-confirmed.properties").entrySet())
            params.apply(entry.getKey() + "=" + entry.getValue());
        return params;
    }

    @Test void currentMainAndFrozenMatchEveryMeasuredParameterWithoutRetuning() throws Exception {
        var expected = record("/v09-confirmed.properties");
        assertEquals(117, expected.size());
        assertEquals("true", expected.get("USE_ROLLOUT_FILL"));
        assertEquals("0", expected.get("ROLLOUT_FILL_MAX_INSTABILITY"));
        assertEquals("false", expected.get("USE_ROLLOUT_RHEA"));
        // Since the v10 promotion (2026-10-23) current carries the seven selected flags; v09 is current with them off.
        for (String spec : new String[]{LiveTargetExperimentParams.V09_ON_CURRENT, "v09", LiveTargetExperimentParams.V09_ON_CURRENT + ",USE_ROLLOUT_FILL=1"}) {
            var actual = new TreeMap<>(BotFactory.configuration(spec));
            LiveTargetExperimentParams.removeIndrillAndCheck(actual);
            assertEquals(expected, actual, spec);
        }
        assertTrue(new Params().USE_ROLLOUT_FILL);
        assertInstanceOf(RolloutBot.class, BotFactory.create(LiveTargetExperimentParams.V09_ON_CURRENT));
        assertInstanceOf(MainBot.class, BotFactory.create("main"));
        assertInstanceOf(RolloutBotV09.class, BotFactory.create("v09"));
        assertEquals("v09", BotFactory.create("v09").name());

        Params dirty = new Params();
        int fields = 0;
        for (var field : Params.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            fields++;
            if (field.getType() == boolean.class) field.setBoolean(dirty, !field.getBoolean(dirty));
            else if (field.getType() == int.class) field.setInt(dirty, -999);
            else if (field.getType() == double.class) field.setDouble(dirty, -999);
        }
        assertEquals(expected.size() + 43, fields, "retained later fields plus proposal/deadline/batched/incremental-ink/fast-path/boss-sabotage/boss-lite/stage4 flags and turn-budget override");
        FrozenParams.applyTo("v09", dirty);
        assertEquals(36, dirty.TURN_BUDGET_MS);
        assertFalse(dirty.USE_ROLLOUT_ALL_CELL_RISK);
        assertFalse(dirty.USE_ROLLOUT_ROOT_FILL);
        assertFalse(dirty.USE_ROLLOUT_MISSED_DEADLINE);
        assertFalse(dirty.USE_BOSS_SABOTAGE, "boss-sabotage experiment stays off for the frozen snapshot");
        assertFalse(dirty.USE_BOSS_LITE, "boss-lite experiment stays off for the frozen snapshot");
        assertFalse(dirty.USE_BOSS_OPPONENT_MODEL); assertFalse(dirty.USE_BOSS_OWN_MODEL); assertFalse(dirty.USE_BOSS_LITE_ROUTES);
        assertFalse(dirty.USE_BATCHED_REFRESH); assertFalse(dirty.USE_INCREMENTAL_INK_REFRESH); assertFalse(dirty.USE_FAST_CHEAPEST_PATH);
        assertEquals(2, dirty.INDRILL_INK_PENALTY, "unused later field resets to its explicit historical default");
        for (var entry : expected.entrySet())
            assertEquals(entry.getValue(), String.valueOf(Params.class.getField(entry.getKey()).get(dirty)), entry.getKey());
        Params override = BotFactory.parameters("v09?USE_ROLLOUT_FILL=0,ROLLOUT_FILL_MAX_INSTABILITY=1");
        assertFalse(override.USE_ROLLOUT_FILL);
        assertEquals(1, override.ROLLOUT_FILL_MAX_INSTABILITY);
        assertTrue(BotFactory.parameters("v09").USE_ROLLOUT_FILL);
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("v09?USE_ROLLOUT_RHEA=1"),
                "RHEA code was removed before this freeze");
    }

    private static String normalizedSource(Path source) throws Exception {
        return Files.readString(source).replace("\r\n", "\n");
    }

    private static String sha(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    /** The frozen copies, with only their identity changes undone, must hash to the measured sources. */
    @Test void bothFrozenClassesAreTheMeasuredAlgorithmWithOnlyIdentityChanges() throws Exception {
        for (String[] item : new String[][]{
                {"RolloutBot", "fd0c8023720dd71c03da75cab27f13d39d25b3919f722c365cca25cc10951464"},
                {"ProtectedOpening", "96c2de3a843439a57e2d12e63c5e83d3b5fc0b6278d875e36b4587d2424613d3"}}) {
            String frozen = normalizedSource(Path.of("src/main/java/btk/bot/v09/" + item[0] + "V09.java"));
            String measured = frozen.replace("package btk.bot.v09;", "package btk.bot.current;")
                    .replace("RolloutBotV09", "RolloutBot")
                    .replace("ProtectedOpeningV09", "ProtectedOpening")
                    .replace("return \"v09\";", "return \"current?\" + p.diff();");
            assertEquals(item[1], sha(measured), "measured source " + item[0]);
            // RolloutBot now contains a default-off risk proposal. The frozen measured source
            // stays pinned above; the existing evolving/saved-state tests retain OFF parity.
            if (item[0].equals("ProtectedOpening"))
                assertEquals(item[1], sha(normalizedSource(Path.of("src/main/java/btk/bot/current/" + item[0] + ".java"))),
                        "current source " + item[0]);
        }
    }

    @Test void historicalV00ThroughV08SourcesAndSnapshotsAreUnchanged() throws Exception {
        var pins = record("/v09-historical-pins.properties");
        assertEquals(18, pins.size());
        for (var entry : pins.entrySet())
            assertEquals(entry.getValue(), sha(normalizedSource(Path.of(entry.getKey()))), entry.getKey());
        assertTrue(BotFactory.configuration("v00").isEmpty());
        for (int version = 1; version <= 8; version++)
            assertFalse(BotFactory.parameters("v0" + version).USE_ROLLOUT_FILL, "v0" + version + " keeps unused paint");
    }

    private static Bot[] candidates(GameState state, int me) throws Exception {
        Bot[] bots = {new RolloutBot(measuredParameters()), BotFactory.create(LiveTargetExperimentParams.V09_ON_CURRENT),
                new MainBot(BotFactory.parameters(LiveTargetExperimentParams.V09_ON_CURRENT)), BotFactory.create("v09")};
        for (Bot bot : bots) bot.init(state.copy(), me);
        return bots;
    }

    private static List<Action> assertParity(Bot[] bots, GameState state, int me, String context) {
        String before = JSON.toJson(state);
        List<Action> expected = null;
        for (Bot bot : bots) {
            var actions = bot.play(state, me, 60_000);
            if (expected == null) expected = actions;
            else assertEquals(Action.format(expected), Action.format(actions), context + " " + bot.name());
            assertEquals(before, JSON.toJson(state), context + " input mutation by " + bot.name());
            var result = new Rules.TurnResult();
            Rules.expand(state, me, actions, new Pathing(state.size), result.moves[me], result);
            assertNull(result.moves[me].error, context);
            assertTrue(result.moves[me].skipped.isEmpty(), context);
        }
        var measured = (RolloutBot) bots[0];
        var current = (RolloutBot) bots[1];
        var frozen = (RolloutBotV09) bots[3];
        assertEquals(measured.fillCells, current.fillCells, context);
        assertEquals(measured.fillCells, frozen.fillCells, context);
        assertEquals(measured.selectedOpening, frozen.selectedOpening, context);
        return expected;
    }

    @Test void measuredCurrentMainAndFrozenAgreeOnBothSeatsAcrossEarlyAndEvolvingBoards() throws Exception {
        int decisions = 0, filled = 0;
        for (int seed : new int[]{310, 311, 1000, 1001}) for (int me = 0; me < 2; me++) {
            GameState state = MapGenerator.generate(seed);
            Bot[] bots = candidates(state, me);
            var opponent = BotFactory.create("v08");
            opponent.init(state.copy(), 1 - me);
            var pathing = new Pathing(state.size);
            for (int turn = 0; turn < 12; turn++) {
                assertFalse(Rules.gameOver(state, pathing), "fixture must reach all early and later decisions");
                var actions = assertParity(bots, state, me, "seed=" + seed + " seat=" + me + " turn=" + turn);
                decisions++;
                if (((RolloutBot) bots[0]).fillCells > 0) filled++;
                var reply = opponent.play(state.copy(), 1 - me, 60_000);
                var result = Rules.resolve(state, me == 0 ? actions : reply, me == 1 ? actions : reply, pathing);
                assertNull(result.moves[0].error);
                assertNull(result.moves[1].error);
            }
        }
        assertEquals(96, decisions);
        assertTrue(filled > 0, "the evolving boards must exercise the filler at least once");
    }

    private static JsonObject fixture(String resource) throws Exception {
        try (var reader = new InputStreamReader(V09PromotionTest.class.getResourceAsStream(resource), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    @Test void measuredCurrentMainAndFrozenAgreeOnSavedBossPositions() throws Exception {
        var positions = fixture("/btk/bot/rollout-boss-positions.json").getAsJsonArray("positions");
        assertEquals(2, positions.size());
        for (var item : positions) {
            var saved = item.getAsJsonObject();
            var replay = new CgReplay();
            replay.globalGraphics = saved.get("globalGraphics").getAsString();
            var state = replay.toState();
            var pathing = new Pathing(state.size);
            Rules.updateConnections(state, pathing);
            for (var turn : saved.getAsJsonArray("history")) {
                var actions = turn.getAsJsonArray();
                Rules.resolve(state, Action.parseLine(actions.get(0).getAsString()),
                        Action.parseLine(actions.get(1).getAsString()), pathing);
            }
            assertEquals(saved.get("turn").getAsInt(), state.turn);
            for (int me = 0; me < 2; me++) assertParity(candidates(state, me), state, me, "saved turn=" + state.turn + " seat=" + me);
        }
        GameState chord = JSON.fromJson(fixture("/btk/bot/network-chord-boss-position.json").get("state"), GameState.class);
        Rules.updateConnections(chord, new Pathing(chord.size));
        assertEquals(26, chord.turn);
        for (int me = 0; me < 2; me++) assertParity(candidates(chord, me), chord, me, "saved chord seat=" + me);
    }
}
