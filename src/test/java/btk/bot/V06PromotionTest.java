package btk.bot;

import btk.bot.current.RolloutBot;
import btk.bot.v06.RolloutBotV06;
import btk.core.Action;
import btk.core.Pathing;
import btk.core.Rules;
import btk.referee.MapGenerator;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Properties;
import java.util.TreeMap;
import static org.junit.jupiter.api.Assertions.*;

class V06PromotionTest {
    private static final String CURRENT_V06 = "current?USE_PROTECTED_OPENING=0,USE_ROLLOUT_QUICK_ROUTE=0,USE_ROLLOUT_NETWORK_AFTER_BASE=0";

    @Test void historicalCurrentAndFrozenParametersMatchTheConfirmedCandidateRecord() throws Exception {
        // Independent evidence captured from rollout-confirmation-2x1000/run-1.json paramsA.
        var record = new Properties();
        try (var in = getClass().getResourceAsStream("/v06-confirmed.properties")) { record.load(in); }
        var expected = new TreeMap<String,String>();
        for (var key : record.stringPropertyNames()) expected.put(key, record.getProperty(key));
        // Post-v06 fields are inactive and absent from the immutable confirmation record.
        expected.put("USE_ROLLOUT_QUICK_ROUTE", "false");
        expected.put("USE_ROLLOUT_QUICK_EARLY", "false");
        expected.put("USE_ROLLOUT_QUICK_VALUE", "false");
        expected.put("USE_ROLLOUT_QUICK_SAFE", "false");
        expected.put("USE_ROLLOUT_DIVERSE_REPLIES", "false");
        expected.put("USE_ROLLOUT_REPLAN", "false");
        expected.put("ROLLOUT_REPLAN_TURN", "4");
        expected.put("USE_ROLLOUT_SECOND_TARGET", "false");
        expected.put("USE_ROLLOUT_SPREAD", "false");
        expected.put("ROLLOUT_SPREAD_ROOTS", "2");
        expected.put("USE_ROLLOUT_NETWORK", "false");
        expected.put("USE_ROLLOUT_NETWORK_AFTER_BASE", "false");
        expected.put("USE_ROLLOUT_ROUTE_DEPENDENCIES", "false"); // Later opt-in fields ignored by frozen v06.
        expected.put("USE_ROLLOUT_NETWORK_CHORDS", "false");
        expected.putAll(SearchExperimentParams.DEFAULTS);
        expected.putAll(LiveTargetExperimentParams.DEFAULTS);
        expected.put("ROLLOUT_NETWORK_MAX_CELLS", "24");
        expected.put("ROLLOUT_NETWORK_ROOTS", "2");
        expected.put("ROLLOUT_NETWORK_RESERVE_MS", "12");
        expected.put("ROLLOUT_QUICK_MAX_PAINT", "6");
        for (var spec : new String[]{CURRENT_V06, "v06"})
            assertEquals(expected, BotFactory.configuration(spec), spec);
        Params dirty = new Params();
        for (var field : Params.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            if (field.getType() == boolean.class) field.setBoolean(dirty, !field.getBoolean(dirty));
            else if (field.getType() == int.class) field.setInt(dirty, -999);
            else if (field.getType() == double.class) field.setDouble(dirty, -999);
        }
        FrozenParams.applyTo("v06", dirty);
        for (var entry : expected.entrySet()) if (record.containsKey(entry.getKey()))
            assertEquals(entry.getValue(), String.valueOf(Params.class.getField(entry.getKey()).get(dirty)), entry.getKey());
        assertFalse(dirty.USE_ROLLOUT_QUICK_ROUTE);
        assertFalse(dirty.USE_ROLLOUT_NETWORK_AFTER_BASE);
        assertInstanceOf(RolloutBot.class, BotFactory.create(CURRENT_V06));
        assertInstanceOf(RolloutBotV06.class, BotFactory.create("v06"));
        assertEquals("v06", BotFactory.create("v06").name());
        assertEquals(4, BotFactory.parameters("v06?ROLLOUT_WIDTH=4").ROLLOUT_WIDTH);
        assertEquals(6, BotFactory.parameters("v06").ROLLOUT_WIDTH);
        for (var old : new String[]{"v01", "v02", "v03", "v04", "v05", "pressure-v1"})
            assertFalse(BotFactory.parameters(old).USE_ROLLOUT_POLICY, old);
    }

    @Test void frozenAndHistoricalCurrentAgreeAcrossEvolvingBoards() {
        for (int seed : new int[]{310, 1000}) for (int me = 0; me < 2; me++) {
            var state = MapGenerator.generate(seed);
            Bot current = BotFactory.create(CURRENT_V06), frozen = BotFactory.create("v06");
            current.init(state.copy(), me); frozen.init(state.copy(), me);
            var pathing = new Pathing(state.size);
            for (int turn = 0; turn < 8 && !Rules.gameOver(state, pathing); turn++) {
                var a = current.play(state.copy(), me, 60_000);
                assertEquals(Action.format(a), Action.format(frozen.play(state.copy(), me, 60_000)));
                var wait = List.of(Action.waitAction());
                var result = Rules.resolve(state, me == 0 ? a : wait, me == 1 ? a : wait, pathing);
                assertNull(result.moves[me].error);
            }
        }
    }
}
