package btk.bot;

import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Later unused fields as resolved for historical versions; their records stay unchanged. */
public final class LiveTargetExperimentParams {
    public static final Map<String, String> DEFAULTS = Map.ofEntries(
            Map.entry("USE_ROLLOUT_LIVE_TARGET", "false"), Map.entry("ROLLOUT_LIVE_WEIGHT", "12.0"),
            Map.entry("ROLLOUT_LIVE_TURNS", "2"), Map.entry("ROLLOUT_ALWAYS_DISRUPT", "false"),
            Map.entry("USE_ROLLOUT_TRACK_VALUE", "false"), Map.entry("ROLLOUT_OPP_TRACK_VALUE", "5.0"),
            Map.entry("ROLLOUT_OWN_TRACK_VALUE", "6.0"), Map.entry("ROLLOUT_TRACK_VALUE_SELF_ONLY", "false"),
            Map.entry("USE_ROLLOUT_FILL", "false"), Map.entry("ROLLOUT_FILL_MAX_INSTABILITY", "0"),
            Map.entry("INDRILL_INK_PENALTY", "2"));

    public static void removeAndCheck(Map<String, String> values) {
        removeRiskAndCheck(values);
        for (var entry : DEFAULTS.entrySet())
            assertEquals(entry.getValue(), values.remove(entry.getKey()), entry.getKey());
    }

    /** v09 already contains the earlier fields, so remove only the independent later policy field. */
    public static void removeIndrillAndCheck(Map<String, String> values) {
        removeRiskAndCheck(values);
        assertEquals("2", values.remove("INDRILL_INK_PENALTY"), "INDRILL_INK_PENALTY");
    }

    private static void removeRiskAndCheck(Map<String, String> values) {
        if (values.containsKey("USE_ROLLOUT_ALL_CELL_RISK"))
            assertEquals("false", values.remove("USE_ROLLOUT_ALL_CELL_RISK"));
    }
}
