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
        removeCurrentOnlyAndCheck(values);
        for (var entry : DEFAULTS.entrySet())
            assertEquals(entry.getValue(), values.remove(entry.getKey()), entry.getKey());
    }

    /** v09 already contains the earlier fields, so remove only the independent later policy field. */
    /**
     * v09 expressed on current after the v10 promotion (2026-10-23): the seven selected v10 flags switched off; since the
     * v11 promotion (2026-10-25) also the root-target flag, so that a historical spec that re-enables the lite chooser on
     * this base (boss-sabotage-v2, opponent-model-v1, ...) keeps its measured configuration.
     */
    public static final String V10_OFF = "USE_BOSS_LITE=0,USE_BOSS_OPPONENT_MODEL=0,USE_BOSS_OWN_MODEL=0,USE_BOSS_LITE_ROUTES=0,USE_INCREMENTAL_INK_REFRESH=0,USE_FAST_CHEAPEST_PATH=0,USE_BATCHED_REFRESH=0,USE_BOSS_ROOT_TARGET=0,USE_EXPOSURE_INCOME=0";
    public static final String V09_ON_CURRENT = "current?" + V10_OFF;
    /** The selected v12 flag switched off (2026-10-25 night): the v11 configuration expressed on current. */
    public static final String V12_OFF = "USE_EXPOSURE_INCOME=0";
    public static final String V11_ON_CURRENT = "current?" + V12_OFF;
    /** The selected v11 and v12 flags switched off: the v10 configuration expressed on current. */
    public static final String V11_OFF = "USE_BOSS_ROOT_TARGET=0," + V12_OFF;
    public static final String V10_ON_CURRENT = "current?" + V11_OFF;
    /**
     * The spec that was screened, process-checked and uploaded as D (= v10 since the 2026-10-23 promotion); since the
     * v11 promotion (2026-10-25) the same configuration needs the root-target flag off explicitly.
     */
    public static final String D_SPEC = "current?NOISE=0.1,USE_BOSS_LITE=true,USE_BOSS_OPPONENT_MODEL=true,USE_BOSS_OWN_MODEL=true,USE_BOSS_LITE_ROUTES=true,USE_INCREMENTAL_INK_REFRESH=true,USE_FAST_CHEAPEST_PATH=true,USE_BATCHED_REFRESH=true," + V11_OFF;
    /** The spec that was screened, process-checked and live-tested as B (= v11 since the 2026-10-25 promotion); since v12 it needs the exposure flag off explicitly. */
    public static final String B_SPEC = "current?USE_BOSS_ROOT_TARGET=true," + V12_OFF;
    /** The spec that was screened, process-checked and live-tested as E1 (= v12 since the 2026-10-25 promotion). */
    public static final String E_SPEC = "current?USE_EXPOSURE_INCOME=true";

    /** Fresh parameters of the v09 configuration (the seven v10 flags off) for mechanism tests written before the promotion. */
    public static Params v09Params() {
        return BotFactory.parameters(V09_ON_CURRENT);
    }

    public static void removeIndrillAndCheck(Map<String, String> values) {
        removeCurrentOnlyAndCheck(values);
        assertEquals("2", values.remove("INDRILL_INK_PENALTY"), "INDRILL_INK_PENALTY");
    }

    public static void removeCurrentOnlyAndCheck(Map<String, String> values) {
        for (String key : new String[]{"USE_ROLLOUT_ALL_CELL_RISK", "USE_ROLLOUT_ROOT_FILL"})
            if (values.containsKey(key)) assertEquals("false", values.remove(key), key);
    }
}
