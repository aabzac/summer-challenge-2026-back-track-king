package btk.bot.current;

import btk.bot.Params;

/** Historical rollout fixtures opt into features independently of the selected defaults. */
final class RolloutTestParams {
    private RolloutTestParams() {}

    static Params base() {
        return base("");
    }

    static Params base(String overrides) {
        return new Params("USE_PROTECTED_OPENING=0,USE_ROLLOUT_QUICK_ROUTE=0,USE_ROLLOUT_NETWORK=0,USE_ROLLOUT_NETWORK_AFTER_BASE=0")
                .apply(overrides);
    }
}
