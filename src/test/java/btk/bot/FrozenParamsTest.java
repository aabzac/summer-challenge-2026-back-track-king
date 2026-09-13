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
            + "USE_BACKUP=true,USE_CLOSE=true,USE_DISRUPT=true,USE_OPP_MODEL=false,USE_STEAL=true,W_COST=0.0,"
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
    void currentUsesLiveDefaultsAndUnsupportedSpecsDoNotSilentlyIgnoreOverrides() {
        assertEquals(new Params().NOISE, BotFactory.parameters("current").NOISE);
        assertEquals(0.7, BotFactory.parameters("current?NOISE=0.7").NOISE);
        assertTrue(BotFactory.configuration("v00").isEmpty());
        for (String spec : new String[]{"v99", "v03?TYPO=1", "main?NOISE=0.7", "v00?NOISE=0.7", ""}) {
            assertThrows(IllegalArgumentException.class, () -> BotFactory.create(spec), spec);
        }
        assertThrows(IllegalStateException.class, () -> FrozenParams.applyTo("v99", new Params()));
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
