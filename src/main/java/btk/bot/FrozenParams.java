package btk.bot;

import java.io.IOException;
import java.util.Properties;

/** Local-only parameter snapshots. Frozen strategy source packages must never be edited. */
final class FrozenParams {
    private FrozenParams() {}

    static Params applyTo(String version, Params params) {
        String resource = "/btk/bot/versions/" + version + ".properties";
        try (var input = FrozenParams.class.getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("missing frozen parameters: " + resource);
            Properties snapshot = new Properties();
            snapshot.load(input);
            if (snapshot.isEmpty()) throw new IllegalStateException("empty frozen parameters: " + resource);
            if (!snapshot.containsKey("TURN_BUDGET_MS")) params.TURN_BUDGET_MS = 36;
            // Pre-v06 snapshots predate this selector and retain their original false value.
            if (!snapshot.containsKey("USE_ROLLOUT_POLICY")) params.USE_ROLLOUT_POLICY = false;
            // Pre-v07 snapshots must not inherit the newly selected construction policies.
            if (!snapshot.containsKey("USE_ROLLOUT_QUICK_ROUTE")) params.USE_ROLLOUT_QUICK_ROUTE = false;
            if (!snapshot.containsKey("USE_ROLLOUT_NETWORK_AFTER_BASE")) params.USE_ROLLOUT_NETWORK_AFTER_BASE = false;
            // Pre-v08 snapshots retain the original opening instead of inheriting current's selection.
            if (!snapshot.containsKey("USE_PROTECTED_OPENING")) params.USE_PROTECTED_OPENING = false;
            // Pre-v09 snapshots keep leaving leftover paint unused instead of inheriting the fill default.
            if (!snapshot.containsKey("USE_ROLLOUT_FILL")) params.USE_ROLLOUT_FILL = false;
            if (!snapshot.containsKey("USE_ROLLOUT_ALL_CELL_RISK")) params.USE_ROLLOUT_ALL_CELL_RISK = false;
            if (!snapshot.containsKey("USE_ROLLOUT_ROOT_FILL")) params.USE_ROLLOUT_ROOT_FILL = false;
            if (!snapshot.containsKey("USE_ROLLOUT_MISSED_DEADLINE")) params.USE_ROLLOUT_MISSED_DEADLINE = false;
            // Pre-v10 snapshots predate the selected runtime flags and the boss-lite chooser; they stay off.
            if (!snapshot.containsKey("USE_BATCHED_REFRESH")) params.USE_BATCHED_REFRESH = false;
            if (!snapshot.containsKey("USE_INCREMENTAL_INK_REFRESH")) params.USE_INCREMENTAL_INK_REFRESH = false;
            if (!snapshot.containsKey("USE_FAST_CHEAPEST_PATH")) params.USE_FAST_CHEAPEST_PATH = false;
            if (!snapshot.containsKey("USE_BOSS_SABOTAGE")) params.USE_BOSS_SABOTAGE = false;
            if (!snapshot.containsKey("USE_BOSS_LITE")) params.USE_BOSS_LITE = false;
            if (!snapshot.containsKey("USE_BOSS_OPPONENT_MODEL")) params.USE_BOSS_OPPONENT_MODEL = false;
            if (!snapshot.containsKey("USE_BOSS_OWN_MODEL")) params.USE_BOSS_OWN_MODEL = false;
            if (!snapshot.containsKey("USE_BOSS_LITE_ROUTES")) params.USE_BOSS_LITE_ROUTES = false;
            // Pre-v11 snapshots (v10 included) predate the root-target flag of the rollout; it stays off.
            if (!snapshot.containsKey("USE_BOSS_ROOT_TARGET")) params.USE_BOSS_ROOT_TARGET = false;
            // The parked sibling flag is excluded from every record while off; a reused Params object must not keep it.
            if (!snapshot.containsKey("USE_BOSS_THREAT_MODEL")) params.USE_BOSS_THREAT_MODEL = false;
            if (!snapshot.containsKey("USE_EXPOSURE_INCOME")) { params.USE_EXPOSURE_INCOME = false; params.EXPOSURE_INCOME_WEIGHT = 1.0; }
            if (!snapshot.containsKey("USE_COLLISION_AVOIDANCE")) { params.USE_COLLISION_AVOIDANCE = false; params.COLLISION_PENALTY = 5.0; }
            if (!snapshot.containsKey("USE_CORRIDOR_ROUTES")) { params.USE_CORRIDOR_ROUTES = false; params.CORRIDOR_WEIGHT = 1.0; }
            // Historical strategies never read this independent policy's routing feature weight.
            if (!snapshot.containsKey("INDRILL_INK_PENALTY")) params.INDRILL_INK_PENALTY = 2;
            // Town context is opt-in even when a legacy snapshot is applied to a reused Params object.
            if (!snapshot.containsKey("USE_SPATIAL_V9_TOWN_CONTEXT")) params.USE_SPATIAL_V9_TOWN_CONTEXT = false;
            for (String key : snapshot.stringPropertyNames()) params.set(key, snapshot.getProperty(key));
            return params;
        } catch (IOException e) {
            throw new IllegalStateException("cannot load frozen parameters: " + resource, e);
        }
    }
}
