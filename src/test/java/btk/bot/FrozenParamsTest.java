package btk.bot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class FrozenParamsTest {
    // Independent record of the effective defaults at 8364ad5, not read from the snapshots.
    private static final String BASELINE = "ALWAYS_DISRUPT=true,COLLISION_COST=2.0,DEBUG=false,DISRUPT_MIN=0.01,"
            + "HORIZON=100,MESSAGE=false,NOISE=0.1,SEED=1,SKIP_UNFINISHABLE=true,UNSTABLE_COST=1.0,"
            + "USE_BACKUP=true,USE_CLOSE=true,USE_DISRUPT=true,USE_IDLE_EXTEND=false,USE_OPP_MODEL=false,USE_STEAL=true,W_COST=0.0,"
            + "W_FUTURE_OWN=0.4,W_FUTURE_TRACK=0.3,W_OPP_INCOME=1.0,W_OPP_ROUTE=0.3,W_OPP=1.0,W_OWN=1.0,"
            + "W_RISK=0.6,W_SELF_LOSS=1.2,W_THEIR_LOSS=1.0,W_VULN=1.0";

    @ParameterizedTest
    @ValueSource(strings = {"v01", "v02", "v03"})
    void snapshotOverridesFutureDefaultsAndPreservesRecordedValues(String version) throws Exception {
        Params changedDefaults = new Params();
        for (var field : Params.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            if (field.getType() == double.class) field.setDouble(changedDefaults, -999);
            else if (field.getType() == int.class) field.setInt(changedDefaults, -999);
            else if (field.getType() == boolean.class) field.setBoolean(changedDefaults, !field.getBoolean(changedDefaults));
        }
        FrozenParams.applyTo(version, changedDefaults);
        Map<String, String> expected = baseline();
        for (var entry : expected.entrySet()) {
            assertEquals(entry.getValue(), String.valueOf(Params.class.getField(entry.getKey()).get(changedDefaults)), entry.getKey());
            assertEquals(entry.getValue(), BotFactory.configuration(version).get(entry.getKey()), entry.getKey());
        }
        // Ensure the factory really passes the resolved parameters into the frozen implementation.
        Bot bot = BotFactory.create(version);
        var field = bot.getClass().getDeclaredField("P");
        field.setAccessible(true);
        Params supplied = (Params) field.get(bot);
        assertEquals(0.1, supplied.NOISE);
        assertEquals(0.6, supplied.W_RISK);
    }

    @Test
    void ablationsAreAppliedAfterSnapshotAndInstancesRemainIndependent() {
        Params changed = BotFactory.parameters("v03?NOISE=0.4,USE_BACKUP=0");
        assertEquals(0.4, changed.NOISE);
        assertFalse(changed.USE_BACKUP);
        changed.W_RISK = 99;
        Params fresh = BotFactory.parameters("v03");
        assertEquals(0.1, fresh.NOISE);
        assertTrue(fresh.USE_BACKUP);
        assertEquals(0.6, fresh.W_RISK);
        assertEquals("0.4", BotFactory.configuration("v03?NOISE=0.4").get("NOISE"));
    }

    @Test
    void v04SnapshotPreservesPromotedIdleExtension() {
        Params params = BotFactory.parameters("v04");
        assertTrue(params.USE_IDLE_EXTEND);
        assertEquals("true", BotFactory.configuration("v04").get("USE_IDLE_EXTEND"));
        assertEquals("v04", BotFactory.create("v04").name().split("\\?", 2)[0]);
    }

    @Test
    void currentUsesLiveDefaultsAndUnsupportedSpecsDoNotSilentlyIgnoreOverrides() {
        assertEquals(new Params().NOISE, BotFactory.parameters("current").NOISE);
        assertEquals(0.7, BotFactory.parameters("current?NOISE=0.7").NOISE);
        assertTrue(BotFactory.configuration("v00").isEmpty());
        for (String spec : new String[]{"v99", "v03?TYPO=1", "main?NOISE=0.7", "v00?NOISE=0.7", ""}) {
            assertThrows(IllegalArgumentException.class, () -> BotFactory.create(spec), spec);
        }
        assertThrows(IllegalStateException.class, () -> FrozenParams.applyTo("v99", new Params()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"v01", "v02", "v03", "v04", "v05", "v06", "pressure-v1"})
    void olderSnapshotsDoNotInheritV07ConstructionDefaults(String version) {
        Params changedDefaults = new Params("USE_ROLLOUT_QUICK_ROUTE=1,USE_ROLLOUT_NETWORK_AFTER_BASE=1");
        FrozenParams.applyTo(version, changedDefaults);
        assertFalse(changedDefaults.USE_ROLLOUT_QUICK_ROUTE);
        assertFalse(changedDefaults.USE_ROLLOUT_NETWORK_AFTER_BASE);
        assertFalse(BotFactory.parameters(version).USE_ROLLOUT_QUICK_ROUTE);
        assertFalse(BotFactory.parameters(version).USE_ROLLOUT_NETWORK_AFTER_BASE);
        assertTrue(BotFactory.parameters(version + "?USE_ROLLOUT_QUICK_ROUTE=1").USE_ROLLOUT_QUICK_ROUTE,
                "explicit query overrides remain later than snapshot fallback");
    }

    @ParameterizedTest
    @ValueSource(strings = {"v01", "v02", "v03", "v04", "v05", "v06", "v07", "pressure-v1"})
    void olderSnapshotsDoNotInheritProtectedOpeningDefault(String version) {
        Params defaults = new Params();
        assertTrue(defaults.USE_PROTECTED_OPENING);
        FrozenParams.applyTo(version, defaults);
        assertFalse(defaults.USE_PROTECTED_OPENING);
        assertFalse(BotFactory.parameters(version).USE_PROTECTED_OPENING);
        assertTrue(BotFactory.parameters(version + "?USE_PROTECTED_OPENING=1").USE_PROTECTED_OPENING,
                "explicit overrides remain later than the historical missing-field fallback");
    }

    @Test
    void v05SnapshotPreservesEveryConfirmedParameterAndFactoryWiring() throws Exception {
        Map<String, String> expected = baseline();
        expected.put("USE_IDLE_EXTEND", "true");
        expected.put("DISRUPT_RESERVE_MS", "6");
        expected.put("GUARD_BUILD_INCOME", "false"); // New field is not read by frozen v05.
        expected.put("CHOOSE_BUILD_DIRECTION", "false"); // Likewise ignored by frozen v05.
        expected.put("EARLY_DISRUPT_FALLBACK", "false"); // Default-off experiment; frozen v05 ignores it.
        expected.put("USE_REPLY_RERANK", "false"); // Default-off experiment; frozen v05 ignores it.
        expected.put("USE_JOINT_ACTION", "false"); // Frozen v05 does not consume joint search.
        expected.put("USE_OWN_BUILD_PLAN", "false"); // Frozen v05 does not consume own-plan search.
        expected.put("OWN_BUILD_WIDTH", "3");
        expected.put("OWN_BUILD_DEPTH", "3");
        expected.put("USE_DEFENSE_PRIORITY", "false"); // Frozen v05 ignores these new fields.
        expected.put("DEFENSE_MAX_REGIONS", "3");
        expected.put("DEFENSE_MAX_PAIRS", "4");
        expected.put("DEFENSE_MAX_PAINT", "6");
        expected.put("DEFENSE_MIN_INSTABILITY", "1");
        expected.put("USE_HEURISTIC_POLICY", "false"); // Independent policy; v05 ignores these fields.
        expected.put("USE_ROLLOUT_POLICY", "false"); // Independent policy; v05 ignores these fields.
        expected.put("ROLLOUT_WIDTH", "6");
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
        expected.put("USE_ROLLOUT_NETWORK", "false"); // Frozen v05 never reads the new generator fields.
        expected.put("USE_ROLLOUT_NETWORK_AFTER_BASE", "false");
        expected.put("USE_ROLLOUT_ROUTE_DEPENDENCIES", "false"); // Later opt-in fields ignored by frozen policies.
        expected.put("USE_ROLLOUT_NETWORK_CHORDS", "false");
        expected.putAll(SearchExperimentParams.DEFAULTS);
        expected.putAll(LiveTargetExperimentParams.DEFAULTS);
        expected.put("ROLLOUT_NETWORK_MAX_CELLS", "24");
        expected.put("ROLLOUT_NETWORK_ROOTS", "2");
        expected.put("ROLLOUT_NETWORK_RESERVE_MS", "12");
        expected.put("ROLLOUT_QUICK_MAX_PAINT", "6");
        expected.put("ROLLOUT_REPLIES", "2");
        expected.put("ROLLOUT_DEPTH", "8");
        expected.put("ROLLOUT_MAX_ROUTES", "64");
        expected.put("ROLLOUT_RESERVE_MS", "20");
        expected.put("ROLLOUT_TARGETS", "4");
        expected.put("ROLLOUT_TAIL", "3.0");
        expected.put("ROLLOUT_ROBUSTNESS", "0.5");
        expected.put("ROLLOUT_TRACK_WEIGHT", "0.4");
        expected.put("ROLLOUT_TARGET_HORIZON", "8.0");
        expected.put("ROLLOUT_ROUTE_HORIZON", "16.0");
        expected.put("ROLLOUT_THREAT_WEIGHT", "0.5");
        expected.put("ROLLOUT_WIN_VALUE", "1000000.0");
        expected.put("HEURISTIC_EMERGENCY_STOP", "true");
        expected.put("HEURISTIC_MAX_ROUTES", "64");
        expected.put("HEURISTIC_TARGET_STICKINESS", "1.3");
        expected.put("HEURISTIC_ROUTE_STICKINESS", "1.25");
        expected.put("HEURISTIC_OWN_PENALTY", "2.0");
        expected.put("HEURISTIC_ACTIVE_WEIGHT", "2.0");
        expected.put("HEURISTIC_AVOID_INSTABILITY", "2");
        expected.put("USE_HEURISTIC_DETOUR", "false");
        expected.put("HEURISTIC_DETOUR_WEIGHT", "1.0");
        expected.put("HEURISTIC_DETOUR_REGIONS", "4");
        expected.put("HEURISTIC_DETOUR_PAIRS", "4");
        expected.put("HEURISTIC_DETOUR_CAP", "12");
        expected.put("BUILD_BEAM_WIDTH", "4");
        expected.put("OPP_REPLY_WIDTH", "2");
        expected.put("FORECAST_DECAY", "0.95");
        expected.put("PROFILE_SEARCH", "false");
        expected.put("ISOLATED_ROUTE_WARMUP", "true"); // Shared MainBot runtime, not frozen policy.
        expected.put("WARMUP_SLICE_MS", "36");
        expected.put("INTERRUPT_VULN_EVAL", "false");
        expected.put("DISRUPT_DELAY_FACTOR", "1.0"); // Frozen v05 does not read this field.
        expected.put("PRESSURE_HORIZON", "8");
        expected.put("PRESSURE_BUILD_RESERVE_MS", "12");
        expected.put("PRESSURE_TRACK_WEIGHT", "0.8");
        expected.put("PRESSURE_CORRIDOR_WEIGHT", "2.0");
        expected.put("PRESSURE_STICKINESS", "0.25");
        expected.put("FORECAST_FLOOR", "0.1");
        assertEquals(expected, BotFactory.configuration("v05"));
        assertEquals(expected, BotFactory.configuration("current?USE_PROTECTED_OPENING=0,USE_ROLLOUT_POLICY=0,USE_ROLLOUT_QUICK_ROUTE=0,USE_ROLLOUT_NETWORK_AFTER_BASE=0,USE_ROLLOUT_FILL=0"));
        Params changed = new Params("DISRUPT_RESERVE_MS=999,NOISE=0.8,USE_IDLE_EXTEND=0");
        FrozenParams.applyTo("v05", changed);
        for (var entry : expected.entrySet()) {
            assertEquals(entry.getValue(), String.valueOf(Params.class.getField(entry.getKey()).get(changed)));
        }
        Bot bot = BotFactory.create("v05");
        assertEquals("v05", bot.name().split("\\?", 2)[0]);
        var field = bot.getClass().getDeclaredField("P");
        field.setAccessible(true);
        assertEquals(6, ((Params) field.get(bot)).DISRUPT_RESERVE_MS);
        assertEquals(0, BotFactory.parameters("v05?DISRUPT_RESERVE_MS=0").DISRUPT_RESERVE_MS);
        assertEquals(6, BotFactory.parameters("v05").DISRUPT_RESERVE_MS);
    }

    private static Map<String, String> baseline() {
        Map<String, String> result = new TreeMap<>();
        for (String pair : BASELINE.split(",")) {
            String[] kv = pair.split("=");
            result.put(kv[0], kv[1]);
        }
        return result;
    }
}
