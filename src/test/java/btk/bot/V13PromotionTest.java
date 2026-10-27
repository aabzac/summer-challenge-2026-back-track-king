package btk.bot;

import btk.bot.current.RolloutBot;
import btk.bot.v12.RolloutBotV12;
import btk.bot.v13.RolloutBotV13;
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
 * v13 = the exact bypass-routes-v1 candidate B2 (current?USE_BYPASS_ROUTES=true), promoted 2026-10-27 on the user's
 * decision after the same-map proxy screen (+3.0 pp over v12 vs boss-v2, replicated: pooled +4.2 pp; 55.0 % head-to-head)
 * and a first live boss session above the +5 pp threshold (24/40 vs v12 21/40; docs/bypass-routes-v1.md). No retuning:
 * the one flag became a default and btk.bot.v13 is the frozen copy of the measured sources.
 */
class V13PromotionTest {
    static final String B2_SPEC = LiveTargetExperimentParams.B2_SPEC;

    private static TreeMap<String, String> record(String resource) throws Exception {
        Properties properties = new Properties();
        try (var in = V13PromotionTest.class.getResourceAsStream(resource)) {
            assertNotNull(in);
            properties.load(in);
        }
        var result = new TreeMap<String, String>();
        for (String key : properties.stringPropertyNames()) result.put(key, properties.getProperty(key));
        return result;
    }

    @Test void currentMainFrozenAndTheMeasuredSpecResolveToTheSameConfiguration() throws Exception {
        var expected = record("/v13-confirmed.properties");
        assertEquals(136, expected.size(), "v12's 133 values plus the promoted flag and its two parameters");
        var v12 = record("/v12-confirmed.properties");
        var withoutFlag = new TreeMap<>(expected);
        assertEquals("true", withoutFlag.remove("USE_BYPASS_ROUTES"));
        assertEquals("2", withoutFlag.remove("BYPASS_MIN_INSTABILITY"));
        assertEquals("0.5", withoutFlag.remove("BYPASS_WEIGHT"));
        assertEquals(v12, withoutFlag, "v13 = v12 + USE_BYPASS_ROUTES (instability >= 2, weight 0.5), nothing retuned");
        var frozenExpected = new TreeMap<>(expected);
        LiveTargetExperimentParams.removeCurrentOnlyAndCheck(frozenExpected); // the frozen package omits the two current-only fields
        for (String spec : new String[]{"current", "main", B2_SPEC}) {
            assertEquals(expected, new TreeMap<>(BotFactory.configuration(spec)), spec);
        }
        assertEquals(frozenExpected, new TreeMap<>(BotFactory.configuration("v13")), "v13");
        Params defaults = new Params();
        assertTrue(defaults.USE_BYPASS_ROUTES && defaults.USE_EXPOSURE_INCOME && defaults.USE_BOSS_ROOT_TARGET);
        assertEquals(2, defaults.BYPASS_MIN_INSTABILITY);
        assertEquals(0.5, defaults.BYPASS_WEIGHT);
        assertFalse(defaults.USE_ROUTE_COMMIT || defaults.USE_CORRIDOR_ROUTES || defaults.USE_QUICK_ALWAYS, "the parked construction flags stay off");
        assertFalse(defaults.USE_BOSS_SABOTAGE, "the screening proxy's chooser is never a default");
        assertInstanceOf(RolloutBot.class, BotFactory.create("current"));
        assertInstanceOf(MainBot.class, BotFactory.create("main"));
        assertInstanceOf(RolloutBotV13.class, BotFactory.create("v13"));
        assertInstanceOf(RolloutBotV12.class, BotFactory.create("v12"));
        assertEquals("v13", BotFactory.create("v13").name());
        assertEquals("v13", BotFactory.displayName("current"));
        assertEquals("v13", BotFactory.displayName("v13"));
        assertEquals("v13 (modified)", BotFactory.displayName(LiveTargetExperimentParams.V12_ON_CURRENT));
        assertEquals("v12", BotFactory.displayName("v12"));

        Params dirty = new Params();
        int fields = 0;
        for (var field : Params.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            fields++;
            if (field.getType() == boolean.class) field.setBoolean(dirty, !field.getBoolean(dirty));
            else if (field.getType() == int.class) field.setInt(dirty, -999);
            else if (field.getType() == double.class) field.setDouble(dirty, -999);
        }
        assertEquals(expected.size() + 31, fields, "fields outside the v13 record: current-only experiments, neural/spatial/turn-budget overrides");
        FrozenParams.applyTo("v13", dirty);
        assertEquals(36, dirty.TURN_BUDGET_MS);
        assertFalse(dirty.USE_BOSS_THREAT_MODEL); assertFalse(dirty.USE_BOSS_SABOTAGE);
        assertFalse(dirty.USE_ROLLOUT_ALL_CELL_RISK); assertFalse(dirty.USE_ROLLOUT_ROOT_FILL);
        for (var entry : frozenExpected.entrySet())
            assertEquals(entry.getValue(), String.valueOf(Params.class.getField(entry.getKey()).get(dirty)), entry.getKey());
        // v12 keeps its measured configuration: its snapshot predates the flag and FrozenParams keeps it off.
        assertFalse(BotFactory.parameters("v12").USE_BYPASS_ROUTES);
        assertEquals(new TreeMap<>(BotFactory.configuration("v12")),
                new TreeMap<>(BotFactory.configuration("v13?USE_BYPASS_ROUTES=0")), "v12 == v13 with the flag off");
        var v12OnCurrent = new TreeMap<>(BotFactory.configuration(LiveTargetExperimentParams.V12_ON_CURRENT));
        LiveTargetExperimentParams.removeCurrentOnlyAndCheck(v12OnCurrent);
        assertEquals(new TreeMap<>(BotFactory.configuration("v12")), v12OnCurrent, "v12 == current with the flag off");
        // A policy that never reads the flag drops the inherited default silently; explicit requests fail; the proxy needs it off.
        assertFalse(BotFactory.parameters("current?USE_ROLLOUT_POLICY=0").USE_BYPASS_ROUTES);
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("current?USE_ROLLOUT_POLICY=0,USE_BYPASS_ROUTES=1"));
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("current?USE_BOSS_SABOTAGE=1," + BotFactory.V10_FLAGS_OFF + "," + BotFactory.V12_FLAGS_OFF),
                "a sabotage proxy spec written without the v13 flag is refused, not silently changed");
        assertDoesNotThrow(() -> BotFactory.create("current?USE_BOSS_SABOTAGE=1," + BotFactory.V10_FLAGS_OFF + "," + BotFactory.V12_FLAGS_OFF + "," + BotFactory.V13_FLAGS_OFF),
                "the boss-v2 proxy spec since v13");
        assertDoesNotThrow(() -> BotFactory.create("current?USE_BOSS_SABOTAGE=1," + LiveTargetExperimentParams.V10_OFF), "the test helper switches it off");
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("v12?USE_BYPASS_ROUTES=1"), "frozen v12 never had the flag");
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("v11?USE_BYPASS_ROUTES=1"));
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("v13?USE_ROLLOUT_SPREAD=1"), "the pruned experiments are refused on v13 as on current");
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("v13?USE_ROUTE_COMMIT=1"), "current-only experiments stay current-only");
        assertDoesNotThrow(() -> BotFactory.create("v13?USE_BOSS_THREAT_MODEL=1"), "the parked sibling flag exists on the frozen copy");
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
                {"RolloutBot", "5555e0cc6732b5f446e0f6019a19b5f0a391fa5f820268acdab1e58787b5caee", null},
                {"BossSabotage", "70497796fa2067ce610c33488123c04b43a9f8421de6430be39aa1f559bd2a58", "9c8be47388d33166c011fd526acf808934096b55c20b3bd30f8159a3530165eb"},
                {"ProtectedOpening", "96c2de3a843439a57e2d12e63c5e83d3b5fc0b6278d875e36b4587d2424613d3", "96c2de3a843439a57e2d12e63c5e83d3b5fc0b6278d875e36b4587d2424613d3"}}) {
            String frozen = normalizedSource(Path.of("src/main/java/btk/bot/v13/" + item[0] + "V13.java"));
            String measured = frozen.replace("package btk.bot.v13;", "package btk.bot.current;")
                    .replace("RolloutBotV13", "RolloutBot")
                    .replace("BossSabotageV13", "BossSabotage")
                    .replace("ProtectedOpeningV13", "ProtectedOpening")
                    .replace("return \"v13\";", "return \"current?\" + p.diff();");
            assertEquals(item[1], sha(measured), "measured source " + item[0]);
            // current's RolloutBot keeps evolving after the freeze; the two helpers stay pinned to current (proxy-only block stripped).
            if (item[2] != null)
                assertEquals(item[2], sha(withoutBlocks(normalizedSource(Path.of("src/main/java/btk/bot/current/" + item[0] + ".java")), "BOSS_NOCLOSE")),
                        "current source " + item[0] + " at the freeze (default-off experiment blocks stripped)");
        }
    }
}
