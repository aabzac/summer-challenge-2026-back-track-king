package btk.bot;

import btk.bot.current.RolloutBot;
import btk.bot.v08.RolloutBotV08;
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

class V08PromotionTest {
    private static final Gson JSON = new Gson();
    /** Historical v08 as reproduced by current: only the later selected fill default is disabled. */
    private static final String CURRENT_V08 = "current?" + LiveTargetExperimentParams.V10_OFF + ",USE_ROLLOUT_FILL=0,USE_BOSS_LITE=0,USE_BOSS_OPPONENT_MODEL=0,USE_BOSS_OWN_MODEL=0,USE_BOSS_LITE_ROUTES=0,USE_INCREMENTAL_INK_REFRESH=0,USE_FAST_CHEAPEST_PATH=0,USE_BATCHED_REFRESH=0";

    private static TreeMap<String, String> record(String resource) throws Exception {
        Properties properties = new Properties();
        try (var in = V08PromotionTest.class.getResourceAsStream(resource)) {
            assertNotNull(in);
            properties.load(in);
        }
        var result = new TreeMap<String, String>();
        for (String key : properties.stringPropertyNames()) result.put(key, properties.getProperty(key));
        return result;
    }

    private static Params measuredParameters() throws Exception {
        Params params = LiveTargetExperimentParams.v09Params(); // the record predates the seven v10 flags
        for (var entry : record("/v08-confirmed.properties").entrySet())
            params.apply(entry.getKey() + "=" + entry.getValue());
        params.USE_ROLLOUT_FILL = false; // later selected default, absent from the v08 record
        return params;
    }

    @Test void historicalCurrentAndFrozenMatchEveryMeasuredParameterWithoutRetuning() throws Exception {
        var expected = record("/v08-confirmed.properties");
        assertEquals(107, expected.size());
        for (String spec : new String[]{CURRENT_V08, "v08", "current?" + LiveTargetExperimentParams.V10_OFF + ",USE_PROTECTED_OPENING=1,USE_ROLLOUT_FILL=0"}) {
            var actual = new TreeMap<>(BotFactory.configuration(spec));
            LiveTargetExperimentParams.removeAndCheck(actual);
            assertEquals(expected, actual, spec);
        }
        assertTrue(new Params().USE_PROTECTED_OPENING);
        assertFalse(new Params().USE_ROLLOUT_RHEA);
        assertInstanceOf(RolloutBot.class, BotFactory.create(CURRENT_V08));
        assertInstanceOf(RolloutBotV08.class, BotFactory.create("v08"));
        assertEquals("v08", BotFactory.create("v08").name());

        Params dirty = new Params();
        int fields = 0;
        for (var field : Params.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            fields++;
            if (field.getType() == boolean.class) field.setBoolean(dirty, !field.getBoolean(dirty));
            else if (field.getType() == int.class) field.setInt(dirty, -999);
            else if (field.getType() == double.class) field.setDouble(dirty, -999);
        }
        assertEquals(expected.size() + LiveTargetExperimentParams.DEFAULTS.size() + 34, fields,
                "retained later fields plus proposal/deadline/batched flags and turn-budget override");
        FrozenParams.applyTo("v08", dirty);
        assertEquals(36, dirty.TURN_BUDGET_MS);
        assertFalse(dirty.USE_ROLLOUT_MISSED_DEADLINE);
        for (var entry : expected.entrySet())
            assertEquals(entry.getValue(), String.valueOf(Params.class.getField(entry.getKey()).get(dirty)), entry.getKey());
        Params override = BotFactory.parameters("v08?USE_PROTECTED_OPENING=0,OPENING_CITY_QUOTA=2");
        assertFalse(override.USE_PROTECTED_OPENING);
        assertEquals(2, override.OPENING_CITY_QUOTA);
        assertTrue(BotFactory.parameters("v08").USE_PROTECTED_OPENING);
        assertEquals(1, BotFactory.parameters("v08").OPENING_CITY_QUOTA);
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
                {"RolloutBot", "70678ada9c55e5897dd9832fa6246b3af7b05568aadb7c215f9ea5b4be527ebc"},
                {"ProtectedOpening", "96c2de3a843439a57e2d12e63c5e83d3b5fc0b6278d875e36b4587d2424613d3"}}) {
            String frozen = normalizedSource(Path.of("src/main/java/btk/bot/v08/" + item[0] + "V08.java"));
            String measured = frozen.replace("package btk.bot.v08;", "package btk.bot.current;")
                    .replace("RolloutBotV08", "RolloutBot")
                    .replace("ProtectedOpeningV08", "ProtectedOpening")
                    .replace("return \"v08\";", "return \"current?\" + p.diff();");
            assertEquals(item[1], sha(measured), "measured source " + item[0]);
        }
    }

    @Test void historicalV00ThroughV07SourcesAndSnapshotsAreUnchanged() throws Exception {
        var pins = record("/v08-historical-pins.properties");
        assertEquals(15, pins.size());
        for (var entry : pins.entrySet())
            assertEquals(entry.getValue(), sha(normalizedSource(Path.of(entry.getKey()))), entry.getKey());
        assertTrue(BotFactory.configuration("v00").isEmpty());
        for (int version = 1; version <= 7; version++)
            assertFalse(BotFactory.parameters("v0" + version).USE_PROTECTED_OPENING);
    }

    private static Bot[] candidates(GameState state, int me) throws Exception {
        Bot[] bots = {new RolloutBot(measuredParameters()), BotFactory.create(CURRENT_V08),
                new MainBot(BotFactory.parameters("v08")), BotFactory.create("v08")};
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
        var frozen = (RolloutBotV08) bots[3];
        assertEquals(measured.selectedOpening, current.selectedOpening, context);
        assertEquals(measured.selectedOpening, frozen.selectedOpening, context);
        assertEquals(measured.openingPaint, frozen.openingPaint, context);
        if (!measured.selectedOpening) {
            assertEquals(0, measured.openingCount, context);
            assertEquals(0, current.openingCount, context);
            assertEquals(0, frozen.openingCount, context);
        }
        if (state.turn >= 3) assertFalse(frozen.selectedOpening, context);
        return expected;
    }

    @Test void measuredHistoricalCurrentMainAndFrozenAgreeOnBothSeatsAcrossEarlyAndEvolvingBoards() throws Exception {
        int early = 0, selected = 0, late = 0;
        boolean[][] openingCovered = new boolean[2][3];
        for (int seed : new int[]{310, 311, 1000, 1001}) for (int me = 0; me < 2; me++) {
            GameState state = MapGenerator.generate(seed);
            Bot[] bots = candidates(state, me);
            var opponent = BotFactory.create("v07");
            opponent.init(state.copy(), 1 - me);
            var pathing = new Pathing(state.size);
            for (int turn = 0; turn < 8; turn++) {
                assertFalse(Rules.gameOver(state, pathing), "fixture must reach all early and later decisions");
                var actions = assertParity(bots, state, me, "seed=" + seed + " seat=" + me + " turn=" + turn);
                if (turn < 3) {
                    early++;
                    if (((RolloutBot) bots[0]).selectedOpening) {
                        selected++;
                        openingCovered[me][turn] = true;
                    }
                } else late++;
                var reply = opponent.play(state.copy(), 1 - me, 60_000);
                var result = Rules.resolve(state, me == 0 ? actions : reply, me == 1 ? actions : reply, pathing);
                assertNull(result.moves[0].error);
                assertNull(result.moves[1].error);
            }
        }
        assertEquals(24, early);
        assertEquals(40, late);
        for (int me = 0; me < 2; me++) for (int turn = 0; turn < 3; turn++)
            assertTrue(openingCovered[me][turn], "opening coverage seat=" + me + " turn=" + turn);
        assertTrue(selected < early, "evolving early states also exercise the legal fallback");
    }

    private static JsonObject fixture(String resource) throws Exception {
        try (var reader = new InputStreamReader(V08PromotionTest.class.getResourceAsStream(resource), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    @Test void measuredHistoricalCurrentMainAndFrozenAgreeOnSavedBossPositions() throws Exception {
        var positions = fixture("/btk/bot/rollout-boss-positions.json").getAsJsonArray("positions");
        assertEquals(2, positions.size());
        int later = 0;
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
            if (state.turn >= 3) later++;
            for (int me = 0; me < 2; me++) assertParity(candidates(state, me), state, me, "saved turn=" + state.turn + " seat=" + me);
        }
        assertTrue(later > 0, "saved fixtures must include post-opening positions");
        GameState chord = JSON.fromJson(fixture("/btk/bot/network-chord-boss-position.json").get("state"), GameState.class);
        Rules.updateConnections(chord, new Pathing(chord.size));
        assertEquals(26, chord.turn);
        for (int me = 0; me < 2; me++) assertParity(candidates(chord, me), chord, me, "saved chord seat=" + me);
    }
}
