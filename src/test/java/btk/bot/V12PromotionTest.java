package btk.bot;

import btk.bot.current.RolloutBot;
import btk.bot.v11.RolloutBotV11;
import btk.bot.v12.RolloutBotV12;
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
 * v12 = the exact stage4-exposure-income-v1 candidate E1 (current?USE_EXPOSURE_INCOME=true), promoted 2026-10-25 on the
 * user's decision after the same-map proxy screen (+3.7 pp over v11 vs boss-v2, 60.3 % head-to-head) and a first live
 * boss session that met the +5 pp threshold exactly (20/40 vs v11 18/40; docs/stage4-exposure-income-v1.md). No
 * retuning: the one flag became a default and btk.bot.v12 is the frozen copy of the measured sources.
 */
class V12PromotionTest {
    static final String E_SPEC = LiveTargetExperimentParams.E_SPEC;

    private static TreeMap<String, String> record(String resource) throws Exception {
        Properties properties = new Properties();
        try (var in = V12PromotionTest.class.getResourceAsStream(resource)) {
            assertNotNull(in);
            properties.load(in);
        }
        var result = new TreeMap<String, String>();
        for (String key : properties.stringPropertyNames()) result.put(key, properties.getProperty(key));
        return result;
    }

    @Test void currentMainFrozenAndTheMeasuredSpecResolveToTheSameConfiguration() throws Exception {
        var expected = record("/v12-confirmed.properties");
        assertEquals(133, expected.size(), "v11's 131 values plus the promoted flag and its weight");
        var v11 = record("/v11-confirmed.properties");
        var withoutFlag = new TreeMap<>(expected);
        assertEquals("true", withoutFlag.remove("USE_EXPOSURE_INCOME"));
        assertEquals("1.0", withoutFlag.remove("EXPOSURE_INCOME_WEIGHT"));
        assertEquals(v11, withoutFlag, "v12 = v11 + USE_EXPOSURE_INCOME (weight 1.0), nothing retuned");
        var frozenExpected = new TreeMap<>(expected);
        LiveTargetExperimentParams.removeCurrentOnlyAndCheck(frozenExpected); // the frozen package omits the two current-only fields
        // Since the v13 promotion (2026-10-27) the v12 configuration is current with the bypass flag off.
        for (String spec : new String[]{LiveTargetExperimentParams.V12_ON_CURRENT, E_SPEC}) {
            assertEquals(expected, new TreeMap<>(BotFactory.configuration(spec)), spec);
        }
        assertEquals(frozenExpected, new TreeMap<>(BotFactory.configuration("v12")), "v12");
        Params defaults = new Params();
        assertTrue(defaults.USE_EXPOSURE_INCOME && defaults.USE_BOSS_ROOT_TARGET);
        assertEquals(1.0, defaults.EXPOSURE_INCOME_WEIGHT);
        assertFalse(defaults.USE_BOSS_THREAT_MODEL, "the parked sibling flag stays off");
        assertFalse(defaults.USE_BOSS_SABOTAGE, "the screening proxy's chooser is never a default");
        assertInstanceOf(RolloutBot.class, BotFactory.create("current"));
        assertInstanceOf(MainBot.class, BotFactory.create("main"));
        assertInstanceOf(RolloutBotV12.class, BotFactory.create("v12"));
        assertInstanceOf(RolloutBotV11.class, BotFactory.create("v11"));
        assertEquals("v12", BotFactory.create("v12").name());
        assertEquals("v13", BotFactory.displayName("current"));
        assertEquals("v12", BotFactory.displayName("v12"));
        assertEquals("v13 (modified)", BotFactory.displayName(LiveTargetExperimentParams.V11_ON_CURRENT));
        assertFalse(BotFactory.parameters("v12").USE_BYPASS_ROUTES, "frozen v12 predates the v13 flag");
        assertEquals("v11", BotFactory.displayName("v11"));

        Params dirty = new Params();
        int fields = 0;
        for (var field : Params.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            fields++;
            if (field.getType() == boolean.class) field.setBoolean(dirty, !field.getBoolean(dirty));
            else if (field.getType() == int.class) field.setInt(dirty, -999);
            else if (field.getType() == double.class) field.setDouble(dirty, -999);
        }
        assertEquals(expected.size() + 34, fields, "fields outside the v12 record: current-only experiments, neural/spatial/turn-budget overrides, the v13 flag and its two parameters");
        FrozenParams.applyTo("v12", dirty);
        assertEquals(36, dirty.TURN_BUDGET_MS);
        assertFalse(dirty.USE_BOSS_THREAT_MODEL); assertFalse(dirty.USE_BOSS_SABOTAGE);
        assertFalse(dirty.USE_ROLLOUT_ALL_CELL_RISK); assertFalse(dirty.USE_ROLLOUT_ROOT_FILL);
        assertFalse(dirty.USE_BYPASS_ROUTES); assertEquals(2, dirty.BYPASS_MIN_INSTABILITY); assertEquals(0.5, dirty.BYPASS_WEIGHT);
        for (var entry : frozenExpected.entrySet())
            assertEquals(entry.getValue(), String.valueOf(Params.class.getField(entry.getKey()).get(dirty)), entry.getKey());
        // v11 keeps its measured configuration: its snapshot predates the flag and FrozenParams keeps it off.
        assertFalse(BotFactory.parameters("v11").USE_EXPOSURE_INCOME);
        assertEquals(new TreeMap<>(BotFactory.configuration("v11")),
                new TreeMap<>(BotFactory.configuration("v12?USE_EXPOSURE_INCOME=0")), "v11 == v12 with the flag off");
        var v11OnCurrent = new TreeMap<>(BotFactory.configuration(LiveTargetExperimentParams.V11_ON_CURRENT));
        LiveTargetExperimentParams.removeCurrentOnlyAndCheck(v11OnCurrent);
        assertEquals(new TreeMap<>(BotFactory.configuration("v11")), v11OnCurrent, "v11 == current with the flag off");
        // A policy that never reads the flag drops the inherited default silently; explicit requests fail; the proxy spec is unchanged.
        assertFalse(BotFactory.parameters("current?USE_ROLLOUT_POLICY=0").USE_EXPOSURE_INCOME);
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("current?USE_ROLLOUT_POLICY=0,USE_EXPOSURE_INCOME=1"));
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("current?USE_BOSS_SABOTAGE=1," + BotFactory.V10_FLAGS_OFF),
                "since the v13 promotion a sabotage proxy spec must switch the bypass flag off explicitly");
        assertTrue(BotFactory.parameters("current?USE_BOSS_SABOTAGE=1," + BotFactory.V10_FLAGS_OFF + "," + BotFactory.V13_FLAGS_OFF).USE_EXPOSURE_INCOME,
                "the exposure discount does not depend on the lite chooser: the proxy spec written without the v12 flag carries it");
        assertFalse(BotFactory.parameters("current?USE_BOSS_SABOTAGE=1," + LiveTargetExperimentParams.V10_OFF).USE_EXPOSURE_INCOME,
                "the test helper switches it off explicitly");
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("v11?USE_EXPOSURE_INCOME=1"), "frozen v11 never had the flag");
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("v12?USE_BYPASS_ROUTES=1"), "frozen v12 never had the v13 flag");
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("v10?USE_EXPOSURE_INCOME=1"));
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("v12?USE_ROLLOUT_SPREAD=1"), "the pruned experiments are refused on v12 as on current");
        assertDoesNotThrow(() -> BotFactory.create("v12?USE_BOSS_THREAT_MODEL=1"), "the parked sibling flag exists on the frozen copy");
    }

    private static String normalizedSource(Path source) throws Exception {
        return Files.readString(source).replace("\r\n", "\n");
    }

    /** The source as a default-off export sees it: the named experiment blocks (and their marker lines) removed. */
    private static String withoutBlocks(String text, String... blocks) {
        StringBuilder out = new StringBuilder(); String inside = null;
        for (String line : text.split("\n", -1)) {
            String t = line.trim();
            if (inside == null) {
                for (String b : blocks) if (t.equals("// " + b + "_BEGIN")) inside = b;
                if (inside != null) continue;
                out.append(line).append('\n');
            } else if (t.equals("// " + inside + "_END")) inside = null;
        }
        return out.substring(0, out.length() - 1);
    }

    private static String sha(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    /** The frozen copies, with only their identity changes undone, must hash to the measured current sources. */
    @Test void frozenClassesAreTheMeasuredAlgorithmWithOnlyIdentityChanges() throws Exception {
        for (String[] item : new String[][]{
                {"RolloutBot", "9943ae275ba29f927406a735d57fd119b5fb0cf526c3fb9db525804523cdd67c"},
                {"BossSabotage", "9c8be47388d33166c011fd526acf808934096b55c20b3bd30f8159a3530165eb"},
                {"ProtectedOpening", "96c2de3a843439a57e2d12e63c5e83d3b5fc0b6278d875e36b4587d2424613d3"}}) {
            String frozen = normalizedSource(Path.of("src/main/java/btk/bot/v12/" + item[0] + "V12.java"));
            String measured = frozen.replace("package btk.bot.v12;", "package btk.bot.current;")
                    .replace("RolloutBotV12", "RolloutBot")
                    .replace("BossSabotageV12", "BossSabotage")
                    .replace("ProtectedOpeningV12", "ProtectedOpening")
                    .replace("return \"v12\";", "return \"current?\" + p.diff();");
            assertEquals(item[1], sha(measured), "measured source " + item[0]);
            // current's RolloutBot keeps evolving after the freeze; the two helpers stay pinned to current.
            if (!item[0].equals("RolloutBot"))
                assertEquals(item[1], sha(withoutBlocks(normalizedSource(Path.of("src/main/java/btk/bot/current/" + item[0] + ".java")), "BOSS_NOCLOSE")),
                        "current source " + item[0] + " at the freeze (default-off experiment blocks stripped)");
        }
    }
}
