package btk.bot.current;

import btk.bot.Params;

/** Historical rollout fixtures opt into features independently of the selected defaults. */
public final class RolloutTestParams {
    private RolloutTestParams() {}

    public static Params base() {
        return base("");
    }

    public static Params base(String overrides) {
        return new Params(btk.bot.LiveTargetExperimentParams.V10_OFF + ",USE_PROTECTED_OPENING=0,USE_ROLLOUT_QUICK_ROUTE=0,USE_ROLLOUT_NETWORK=0,USE_ROLLOUT_NETWORK_AFTER_BASE=0,USE_ROLLOUT_FILL=0")
                .apply(overrides);
    }
}
