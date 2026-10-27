package btk.bot;

import btk.bot.current.RolloutBot;
import btk.bot.v07.RolloutBotV07;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.referee.MapGenerator;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Properties;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class V07PromotionTest {
    private static final String CURRENT_V07 = "current?" + LiveTargetExperimentParams.V10_OFF + ",USE_PROTECTED_OPENING=0,USE_ROLLOUT_FILL=0,USE_BOSS_LITE=0,USE_BOSS_OPPONENT_MODEL=0,USE_BOSS_OWN_MODEL=0,USE_BOSS_LITE_ROUTES=0,USE_INCREMENTAL_INK_REFRESH=0,USE_FAST_CHEAPEST_PATH=0,USE_BATCHED_REFRESH=0";
    private static TreeMap<String, String> confirmedParameters() throws Exception {
        Properties record = new Properties();
        // Independent copy of launch.json resolvedParameters; never read the production snapshot.
        try (var in = V07PromotionTest.class.getResourceAsStream("/v07-confirmed.properties")) {
            assertNotNull(in);
            record.load(in);
        }
        TreeMap<String, String> result = new TreeMap<>();
        for (String key : record.stringPropertyNames()) result.put(key, record.getProperty(key));
        return result;
    }

    @Test void historicalCurrentAndFrozenMatchAllMeasuredV07Parameters() throws Exception {
        var expected = confirmedParameters();
        assertEquals(98, expected.size());
        for (String spec : new String[]{CURRENT_V07, "v07"}) {
            var actual = new TreeMap<>(BotFactory.configuration(spec));
            assertEquals("false", actual.remove("USE_ROLLOUT_ROUTE_DEPENDENCIES"));
            assertEquals("false", actual.remove("USE_ROLLOUT_NETWORK_CHORDS"));
            SearchExperimentParams.removeAndCheck(actual);
            LiveTargetExperimentParams.removeAndCheck(actual);
            assertEquals(expected, actual, spec);
        }
        assertEquals("true", expected.get("USE_ROLLOUT_QUICK_ROUTE"));
        assertEquals("true", expected.get("USE_ROLLOUT_NETWORK_AFTER_BASE"));
        assertEquals("false", expected.get("USE_ROLLOUT_NETWORK"));
        assertInstanceOf(RolloutBot.class, BotFactory.create(CURRENT_V07));
        assertInstanceOf(RolloutBotV07.class, BotFactory.create("v07"));
        assertEquals("v07", BotFactory.create("v07").name());

        Params dirty = new Params();
        int fields = 0;
        for (var field : Params.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            fields++;
            if (field.getType() == boolean.class) field.setBoolean(dirty, !field.getBoolean(dirty));
            else if (field.getType() == int.class) field.setInt(dirty, -999);
            else if (field.getType() == double.class) field.setDouble(dirty, -999);
        }
        assertEquals(expected.size() + 51 + SearchExperimentParams.DEFAULTS.size()
                + LiveTargetExperimentParams.DEFAULTS.size(), fields,
                "retained later fields plus proposal/deadline/batched flags and turn-budget override");
        FrozenParams.applyTo("v07", dirty);
        assertEquals(36, dirty.TURN_BUDGET_MS);
        assertFalse(dirty.USE_ROLLOUT_MISSED_DEADLINE);
        for (var entry : expected.entrySet())
            assertEquals(entry.getValue(), String.valueOf(Params.class.getField(entry.getKey()).get(dirty)), entry.getKey());
        Params overridden = BotFactory.parameters("v07?ROLLOUT_NETWORK_ROOTS=1,NOISE=0");
        assertEquals(1, overridden.ROLLOUT_NETWORK_ROOTS);
        assertEquals(0, overridden.NOISE);
        overridden.USE_ROLLOUT_NETWORK_AFTER_BASE = false;
        assertTrue(BotFactory.parameters("v07").USE_ROLLOUT_NETWORK_AFTER_BASE);
        assertEquals(2, BotFactory.parameters("v07").ROLLOUT_NETWORK_ROOTS);
    }

    @Test void historicalMainCurrentAndFrozenAgreeAcrossEvolvingBoardsOnBothSides() {
        for (int seed : new int[]{310, 1000}) for (int me = 0; me < 2; me++) {
            GameState state = MapGenerator.generate(seed);
            Bot current = BotFactory.create(CURRENT_V07);
            Bot frozen = BotFactory.create("v07");
            Bot main = new MainBot(BotFactory.parameters("v07"));
            current.init(state.copy(), me);
            frozen.init(state.copy(), me);
            main.init(state.copy(), me);
            Pathing pathing = new Pathing(state.size);
            for (int turn = 0; turn < 8 && !Rules.gameOver(state, pathing); turn++) {
                var actions = current.play(state.copy(), me, 60_000);
                String expected = Action.format(actions);
                assertEquals(expected, Action.format(frozen.play(state.copy(), me, 60_000)),
                        "frozen seed=" + seed + " side=" + me + " turn=" + turn);
                assertEquals(expected, Action.format(main.play(state.copy(), me, 60_000)),
                        "main seed=" + seed + " side=" + me + " turn=" + turn);
                var wait = List.of(Action.waitAction());
                var result = Rules.resolve(state, me == 0 ? actions : wait, me == 1 ? actions : wait, pathing);
                assertNull(result.moves[me].error);
            }
        }
    }

    @Test void frozenAndMainActuallyUseTheNewLateNetworkMove() {
        for (int me = 0; me < 2; me++) {
            int[] regions = new int[63];
            for (int c = 0; c < regions.length; c++) regions[c] = c;
            GameState state = new GameState(9, 7, regions, new int[63],
                    new int[]{1, 7}, new int[]{3, 3}, new int[][]{{1}, {}});
            for (int cell : new int[]{29, 30, 21, 22, 23, 32, 33}) state.owner[cell] = 1 - me;
            state.turn = 10;
            Rules.updateConnections(state, new Pathing(state.size));
            String query = "?USE_PROTECTED_OPENING=0,USE_ROLLOUT_FILL=0,USE_DISRUPT=0,NOISE=0";
            Bot current = BotFactory.create("current?" + LiveTargetExperimentParams.V10_OFF + "," + query.substring(1));
            Bot frozen = BotFactory.create("v07" + query);
            Bot main = new MainBot(BotFactory.parameters("v07" + query));
            for (Bot bot : new Bot[]{current, frozen, main}) {
                bot.init(state.copy(), me);
                assertEquals("PLACE_TRACKS 4 3", Action.format(bot.play(state.copy(), me, 60_000)));
            }
            assertEquals(-1, state.owner[31], "independent decisions leave their observation untouched");
        }
    }
}
