package btk.bot;

import btk.bot.current.RolloutBot;
import btk.bot.v10.RolloutBotV10;
import btk.bot.v11.RolloutBotV11;
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
 * v11 = the exact stage4-threat-model-v1 candidate B (current?USE_BOSS_ROOT_TARGET=true), promoted 2026-10-25 on the
 * user's decision after the same-map proxy screen (+8.8 pp over v10 vs boss-v2, 57.0 % head-to-head) and the first
 * live boss session (21/40 vs v10 20/40, +2.5 pp, inconclusive; docs/stage4-threat-model-v1.md). No retuning: the
 * one flag became a default and btk.bot.v11 is the frozen copy of the measured sources.
 */
class V11PromotionTest {
    static final String B_SPEC = LiveTargetExperimentParams.B_SPEC;

    private static TreeMap<String, String> record(String resource) throws Exception {
        Properties properties = new Properties();
        try (var in = V11PromotionTest.class.getResourceAsStream(resource)) {
            assertNotNull(in);
            properties.load(in);
        }
        var result = new TreeMap<String, String>();
        for (String key : properties.stringPropertyNames()) result.put(key, properties.getProperty(key));
        return result;
    }

    @Test void currentMainFrozenAndTheMeasuredSpecResolveToTheSameConfiguration() throws Exception {
        var expected = record("/v11-confirmed.properties");
        assertEquals(131, expected.size(), "v10's 130 values plus the one promoted flag");
        var v10 = record("/v10-confirmed.properties");
        var withoutFlag = new TreeMap<>(expected);
        assertEquals("true", withoutFlag.remove("USE_BOSS_ROOT_TARGET"));
        assertEquals(v10, withoutFlag, "v11 = v10 + USE_BOSS_ROOT_TARGET, nothing retuned");
        var frozenExpected = new TreeMap<>(expected);
        LiveTargetExperimentParams.removeCurrentOnlyAndCheck(frozenExpected); // the frozen package omits the two current-only fields
        // Since the v12 promotion (2026-10-25 night) the v11 configuration is current with the exposure flag off.
        for (String spec : new String[]{LiveTargetExperimentParams.V11_ON_CURRENT, B_SPEC}) {
            assertEquals(expected, new TreeMap<>(BotFactory.configuration(spec)), spec);
        }
        assertEquals(frozenExpected, new TreeMap<>(BotFactory.configuration("v11")), "v11");
        Params defaults = new Params();
        assertTrue(defaults.USE_BOSS_ROOT_TARGET);
        assertFalse(defaults.USE_BOSS_THREAT_MODEL, "the parked sibling flag stays off");
        assertFalse(BotFactory.parameters("v11").USE_EXPOSURE_INCOME, "frozen v11 predates the v12 flag");
        assertFalse(defaults.USE_BOSS_SABOTAGE, "the screening proxy's chooser is never a default");
        assertInstanceOf(RolloutBot.class, BotFactory.create("current"));
        assertInstanceOf(MainBot.class, BotFactory.create("main"));
        assertInstanceOf(RolloutBotV11.class, BotFactory.create("v11"));
        assertInstanceOf(RolloutBotV10.class, BotFactory.create("v10"));
        assertEquals("v11", BotFactory.create("v11").name());
        assertEquals("v12", BotFactory.displayName("current"));
        assertEquals("v11", BotFactory.displayName("v11"));
        assertEquals("v12 (modified)", BotFactory.displayName(LiveTargetExperimentParams.V10_ON_CURRENT));
        assertEquals("v10", BotFactory.displayName("v10"));

        Params dirty = new Params();
        int fields = 0;
        for (var field : Params.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            fields++;
            if (field.getType() == boolean.class) field.setBoolean(dirty, !field.getBoolean(dirty));
            else if (field.getType() == int.class) field.setInt(dirty, -999);
            else if (field.getType() == double.class) field.setDouble(dirty, -999);
        }
        assertEquals(expected.size() + 30, fields, "fields outside the v11 record: current-only experiments, neural/spatial/turn-budget overrides, the v12 flag and its weight");
        FrozenParams.applyTo("v11", dirty);
        assertEquals(36, dirty.TURN_BUDGET_MS);
        assertFalse(dirty.USE_BOSS_THREAT_MODEL); assertFalse(dirty.USE_BOSS_SABOTAGE);
        assertFalse(dirty.USE_ROLLOUT_ALL_CELL_RISK); assertFalse(dirty.USE_ROLLOUT_ROOT_FILL);
        assertFalse(dirty.USE_EXPOSURE_INCOME); assertEquals(1.0, dirty.EXPOSURE_INCOME_WEIGHT);
        for (var entry : frozenExpected.entrySet())
            assertEquals(entry.getValue(), String.valueOf(Params.class.getField(entry.getKey()).get(dirty)), entry.getKey());
        // v10 keeps its measured configuration: its snapshot predates the flag and FrozenParams keeps it off.
        assertFalse(BotFactory.parameters("v10").USE_BOSS_ROOT_TARGET);
        assertEquals(new TreeMap<>(BotFactory.configuration("v10")),
                new TreeMap<>(BotFactory.configuration("v11?USE_BOSS_ROOT_TARGET=0")), "v10 == v11 with the flag off");
        var v10OnCurrent = new TreeMap<>(BotFactory.configuration(LiveTargetExperimentParams.V10_ON_CURRENT));
        LiveTargetExperimentParams.removeCurrentOnlyAndCheck(v10OnCurrent);
        assertEquals(new TreeMap<>(BotFactory.configuration("v10")), v10OnCurrent, "v10 == current with the flag off");
        // The flag lives inside the lite chooser: switching the chooser off drops the inherited default silently, so the
        // v09 configuration on current and the boss-v2 proxy spec are unchanged by the promotion; explicit requests fail.
        assertFalse(BotFactory.parameters(LiveTargetExperimentParams.V09_ON_CURRENT).USE_BOSS_ROOT_TARGET);
        assertDoesNotThrow(() -> BotFactory.create("current?USE_BOSS_SABOTAGE=1," + LiveTargetExperimentParams.V10_OFF), "the boss-v2 proxy spec");
        assertFalse(BotFactory.parameters("current?USE_BOSS_SABOTAGE=1," + LiveTargetExperimentParams.V10_OFF).USE_BOSS_ROOT_TARGET);
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("current?" + LiveTargetExperimentParams.V10_OFF + ",USE_BOSS_ROOT_TARGET=1"));
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("v10?USE_BOSS_ROOT_TARGET=1"), "frozen v10 never had the flag");
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("v09?USE_BOSS_ROOT_TARGET=1"));
        assertDoesNotThrow(() -> BotFactory.create("current?USE_ROLLOUT_POLICY=0"), "EvalBot ablations drop the rollout-only defaults");
        assertFalse(BotFactory.parameters("current?USE_ROLLOUT_POLICY=0").USE_BOSS_ROOT_TARGET);
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("current?USE_ROLLOUT_POLICY=0,USE_BOSS_ROOT_TARGET=1"));
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("v11?USE_ROLLOUT_SPREAD=1"), "the pruned experiments are refused on v11 as on current");
        assertDoesNotThrow(() -> BotFactory.create("v11?USE_BOSS_THREAT_MODEL=1"), "the parked sibling flag exists on the frozen copy");
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("v11?USE_EXPOSURE_INCOME=1"), "frozen v11 never had the v12 flag");
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
                {"RolloutBot", "1657471fc7d476c7bcbe9b2ac39e3697115717e6eb5993a2d36469c8d94951f1"},
                {"BossSabotage", "9c8be47388d33166c011fd526acf808934096b55c20b3bd30f8159a3530165eb"},
                {"ProtectedOpening", "96c2de3a843439a57e2d12e63c5e83d3b5fc0b6278d875e36b4587d2424613d3"}}) {
            String frozen = normalizedSource(Path.of("src/main/java/btk/bot/v11/" + item[0] + "V11.java"));
            String measured = frozen.replace("package btk.bot.v11;", "package btk.bot.current;")
                    .replace("RolloutBotV11", "RolloutBot")
                    .replace("BossSabotageV11", "BossSabotage")
                    .replace("ProtectedOpeningV11", "ProtectedOpening")
                    .replace("return \"v11\";", "return \"current?\" + p.diff();");
            assertEquals(item[1], sha(measured), "measured source " + item[0]);
            // current's RolloutBot keeps evolving after the freeze (exposure-income-v1 was the first change); the helpers are untouched.
            if (!item[0].equals("RolloutBot"))
                assertEquals(item[1], sha(normalizedSource(Path.of("src/main/java/btk/bot/current/" + item[0] + ".java"))),
                        "current source " + item[0] + " at the freeze");
        }
    }
}
