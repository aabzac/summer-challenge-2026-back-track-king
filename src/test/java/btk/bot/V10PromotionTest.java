package btk.bot;

import btk.bot.current.RolloutBot;
import btk.bot.v10.RolloutBotV10;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v10 = the exact stage2-compute-v1 candidate (D), promoted 2026-10-23 on the user's decision after the
 * live boss series session 1 (18/40 vs v09 9/40, docs/stage2-d-live-boss-v1.md). No retuning: current's
 * defaults became D's configuration and btk.bot.v10 is the frozen copy of the measured sources.
 */
class V10PromotionTest {
    /** The spec that was screened, process-checked and uploaded as D. */
    static final String D_SPEC = LiveTargetExperimentParams.D_SPEC;

    private static TreeMap<String, String> record(String resource) throws Exception {
        Properties properties = new Properties();
        try (var in = V10PromotionTest.class.getResourceAsStream(resource)) {
            assertNotNull(in);
            properties.load(in);
        }
        var result = new TreeMap<String, String>();
        for (String key : properties.stringPropertyNames()) result.put(key, properties.getProperty(key));
        return result;
    }

    @Test void currentMainFrozenAndTheMeasuredSpecResolveToTheSameConfiguration() throws Exception {
        var expected = record("/v10-confirmed.properties");
        assertEquals(130, expected.size());
        for (String flag : new String[]{"USE_BOSS_LITE", "USE_BOSS_OPPONENT_MODEL", "USE_BOSS_OWN_MODEL", "USE_BOSS_LITE_ROUTES",
                "USE_INCREMENTAL_INK_REFRESH", "USE_FAST_CHEAPEST_PATH", "USE_BATCHED_REFRESH", "USE_ROLLOUT_FILL"})
            assertEquals("true", expected.get(flag), flag);
        assertEquals("0.1", expected.get("NOISE"));
        assertEquals("2", expected.get("BOSS_OPPONENT_REPLIES"));
        assertEquals("1.0", expected.get("BOSS_HOLD_MARGIN"));
        var frozenExpected = new TreeMap<>(expected);
        LiveTargetExperimentParams.removeCurrentOnlyAndCheck(frozenExpected); // the frozen package omits the two current-only fields
        // Since the v11 promotion (2026-10-25) the v10 configuration is current with the root-target flag off.
        for (String spec : new String[]{LiveTargetExperimentParams.V10_ON_CURRENT, D_SPEC}) {
            assertEquals(expected, new TreeMap<>(BotFactory.configuration(spec)), spec);
        }
        assertEquals(frozenExpected, new TreeMap<>(BotFactory.configuration("v10")), "v10");
        Params defaults = new Params();
        assertTrue(defaults.USE_BOSS_LITE && defaults.USE_BOSS_OPPONENT_MODEL && defaults.USE_BOSS_OWN_MODEL && defaults.USE_BOSS_LITE_ROUTES);
        assertTrue(defaults.USE_INCREMENTAL_INK_REFRESH && defaults.USE_FAST_CHEAPEST_PATH && defaults.USE_BATCHED_REFRESH);
        assertFalse(defaults.USE_BOSS_SABOTAGE, "the screening proxy's chooser is never a default");
        assertFalse(BotFactory.parameters("v10").USE_BOSS_ROOT_TARGET, "frozen v10 predates the v11 flag");
        assertInstanceOf(RolloutBot.class, BotFactory.create("current"));
        assertInstanceOf(MainBot.class, BotFactory.create("main"));
        assertInstanceOf(RolloutBotV10.class, BotFactory.create("v10"));
        assertEquals("v10", BotFactory.create("v10").name());
        assertEquals("current", BotFactory.create("current").name().split("\\?", 2)[0]);

        Params dirty = new Params();
        int fields = 0;
        for (var field : Params.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            fields++;
            if (field.getType() == boolean.class) field.setBoolean(dirty, !field.getBoolean(dirty));
            else if (field.getType() == int.class) field.setInt(dirty, -999);
            else if (field.getType() == double.class) field.setDouble(dirty, -999);
        }
        // +2 since stage4-threat-model-v1 (2026-10-25): USE_BOSS_THREAT_MODEL, USE_BOSS_ROOT_TARGET (current-only, off by default).
        assertEquals(expected.size() + 30, fields, "fields outside the v10 record: current-only experiments, neural/spatial/turn-budget overrides");
        FrozenParams.applyTo("v10", dirty);
        assertEquals(36, dirty.TURN_BUDGET_MS);
        assertFalse(dirty.USE_ROLLOUT_ALL_CELL_RISK); assertFalse(dirty.USE_ROLLOUT_ROOT_FILL);
        assertFalse(dirty.USE_ROLLOUT_MISSED_DEADLINE); assertFalse(dirty.USE_BOSS_SABOTAGE);
        for (var entry : frozenExpected.entrySet())
            assertEquals(entry.getValue(), String.valueOf(Params.class.getField(entry.getKey()).get(dirty)), entry.getKey());
        // v09 keeps its measured configuration: its snapshot predates the seven flags and FrozenParams keeps them off.
        Params v09 = BotFactory.parameters("v09");
        assertFalse(v09.USE_BOSS_LITE || v09.USE_BOSS_OPPONENT_MODEL || v09.USE_BOSS_OWN_MODEL || v09.USE_BOSS_LITE_ROUTES);
        assertFalse(v09.USE_INCREMENTAL_INK_REFRESH || v09.USE_FAST_CHEAPEST_PATH || v09.USE_BATCHED_REFRESH);
        var v09OnCurrent = new TreeMap<>(BotFactory.configuration(LiveTargetExperimentParams.V09_ON_CURRENT));
        LiveTargetExperimentParams.removeCurrentOnlyAndCheck(v09OnCurrent);
        assertEquals(new TreeMap<>(BotFactory.configuration("v09")), v09OnCurrent, "v09 == current with the seven flags off");
        // Explicit requests that contradict a policy still fail; inherited defaults are dropped silently.
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("v10?USE_BOSS_SABOTAGE=1"), "sabotage proxy needs the lite chooser off");
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("current?USE_BOSS_SABOTAGE=1"));
        assertDoesNotThrow(() -> BotFactory.create("current?USE_BOSS_SABOTAGE=1," + LiveTargetExperimentParams.V10_OFF), "the boss-v2 proxy spec since the promotion");
        assertDoesNotThrow(() -> BotFactory.create("current?USE_ROLLOUT_POLICY=0"), "EvalBot ablations drop the rollout-only defaults");
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("current?USE_ROLLOUT_POLICY=0,USE_BATCHED_REFRESH=1"));
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("v09?USE_BOSS_LITE=1"));
    }

    private static String normalizedSource(Path source) throws Exception {
        return Files.readString(source).replace("\r\n", "\n");
    }

    private static String sha(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    /** The frozen copies, with only their identity changes undone, must hash to the measured current sources. */
    @Test void frozenClassesAreTheMeasuredAlgorithmWithOnlyIdentityChanges() throws Exception {
        for (String[] item : new String[][]{
                {"RolloutBot", "57ca78bdba03092021beabc48f726db6cc7aa3a1b859548b13aa8b973d328be3"},
                {"BossSabotage", "9c8be47388d33166c011fd526acf808934096b55c20b3bd30f8159a3530165eb"},
                {"ProtectedOpening", "96c2de3a843439a57e2d12e63c5e83d3b5fc0b6278d875e36b4587d2424613d3"}}) {
            String frozen = normalizedSource(Path.of("src/main/java/btk/bot/v10/" + item[0] + "V10.java"));
            String measured = frozen.replace("package btk.bot.v10;", "package btk.bot.current;")
                    .replace("RolloutBotV10", "RolloutBot")
                    .replace("BossSabotageV10", "BossSabotage")
                    .replace("ProtectedOpeningV10", "ProtectedOpening")
                    .replace("return \"v10\";", "return \"current?\" + p.diff();");
            assertEquals(item[1], sha(measured), "measured source " + item[0]);
            // prune-v1 (2026-10-25) removed the rejected experiments from current's RolloutBot; the two helpers are untouched.
            if (!item[0].equals("RolloutBot"))
                assertEquals(item[1], sha(normalizedSource(Path.of("src/main/java/btk/bot/current/" + item[0] + ".java"))),
                        "current source " + item[0] + " at the freeze");
        }
    }
}
