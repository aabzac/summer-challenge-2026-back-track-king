package btk.bot;

import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Explicit later inactive fields; immutable historical parameter records stay unchanged. */
public final class SearchExperimentParams {
    public static final Map<String, String> DEFAULTS = Map.of(
            "USE_ROLLOUT_RHEA", "false", "ROLLOUT_RHEA_MUTATIONS", "12",
            "ROLLOUT_RHEA_ELITES", "2", "USE_PROTECTED_OPENING", "false",
            "OPENING_TURNS", "3", "OPENING_CITY_QUOTA", "1", "OPENING_COLLISION_FACTOR", "0.25");

    public static void removeAndCheck(Map<String, String> values) {
        for (var entry : DEFAULTS.entrySet())
            assertEquals(entry.getValue(), values.remove(entry.getKey()), entry.getKey());
    }
}
