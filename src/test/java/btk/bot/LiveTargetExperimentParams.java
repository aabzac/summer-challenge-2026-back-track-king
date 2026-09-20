package btk.bot;

import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Post-v08 default-off sabotage fields as resolved for historical versions; their records stay unchanged. */
public final class LiveTargetExperimentParams {
    public static final Map<String, String> DEFAULTS = Map.of(
            "USE_ROLLOUT_LIVE_TARGET", "false", "ROLLOUT_LIVE_WEIGHT", "12.0",
            "ROLLOUT_LIVE_TURNS", "2", "ROLLOUT_ALWAYS_DISRUPT", "false",
            "USE_ROLLOUT_TRACK_VALUE", "false", "ROLLOUT_OPP_TRACK_VALUE", "5.0",
            "ROLLOUT_OWN_TRACK_VALUE", "6.0", "ROLLOUT_TRACK_VALUE_SELF_ONLY", "false",
            "USE_ROLLOUT_FILL", "false", "ROLLOUT_FILL_MAX_INSTABILITY", "0");

    public static void removeAndCheck(Map<String, String> values) {
        for (var entry : DEFAULTS.entrySet())
            assertEquals(entry.getValue(), values.remove(entry.getKey()), entry.getKey());
    }
}
