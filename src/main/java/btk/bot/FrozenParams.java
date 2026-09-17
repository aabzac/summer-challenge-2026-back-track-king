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
            // Pre-v06 snapshots predate this selector and retain their original false value.
            if (!snapshot.containsKey("USE_ROLLOUT_POLICY")) params.USE_ROLLOUT_POLICY = false;
            // Pre-v07 snapshots must not inherit the newly selected construction policies.
            if (!snapshot.containsKey("USE_ROLLOUT_QUICK_ROUTE")) params.USE_ROLLOUT_QUICK_ROUTE = false;
            if (!snapshot.containsKey("USE_ROLLOUT_NETWORK_AFTER_BASE")) params.USE_ROLLOUT_NETWORK_AFTER_BASE = false;
            for (String key : snapshot.stringPropertyNames()) params.set(key, snapshot.getProperty(key));
            return params;
        } catch (IOException e) {
            throw new IllegalStateException("cannot load frozen parameters: " + resource, e);
        }
    }
}
