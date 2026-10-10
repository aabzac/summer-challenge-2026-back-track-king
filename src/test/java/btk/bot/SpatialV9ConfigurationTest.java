package btk.bot;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpatialV9ConfigurationTest {
    @Test void legacySnapshotsDisableTownContextEvenOnReusedParameters() {
        for (String version : new String[]{"spatial-ppo-v1", "spatial-ppo-v4", "spatial-ppo-v8", "v09"}) {
            Params params = new Params(); params.USE_SPATIAL_V9_TOWN_CONTEXT = true;
            FrozenParams.applyTo(version, params);
            assertFalse(params.USE_SPATIAL_V9_TOWN_CONTEXT, version);
        }
        assertTrue(FrozenParams.applyTo("spatial-ppo-v9", new Params()).USE_SPATIAL_V9_TOWN_CONTEXT);
    }
}
