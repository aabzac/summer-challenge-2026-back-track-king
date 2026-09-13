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
            for (String key : snapshot.stringPropertyNames()) params.set(key, snapshot.getProperty(key));
            return params;
        } catch (IOException e) {
            throw new IllegalStateException("cannot load frozen parameters: " + resource, e);
        }
    }
}
