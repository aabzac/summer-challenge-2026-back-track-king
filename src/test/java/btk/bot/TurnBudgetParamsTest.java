package btk.bot;

import org.junit.jupiter.api.Test;
import java.util.TreeMap;
import static org.junit.jupiter.api.Assertions.*;

class TurnBudgetParamsTest {
    private static final String BASE = "current?NOISE=0.1,USE_BATCHED_REFRESH=true";

    @Test void onlyTheCurrentNativePolicyMayRequestTheExplicit38MsExperiment() {
        assertEquals(36, new Params().TURN_BUDGET_MS);
        assertEquals(36, new MainBot().turnBudgetMs());
        Params p = BotFactory.parameters(BASE + ",TURN_BUDGET_MS=38");
        MainBot main = new MainBot(p); p.TURN_BUDGET_MS = 36;
        assertEquals(38, main.turnBudgetMs(), "MainBot retains its own resolved configuration");
        var expected = new TreeMap<>(BotFactory.configuration(BASE));
        expected.put("TURN_BUDGET_MS", "38");
        assertEquals(expected, BotFactory.configuration(BASE + ",TURN_BUDGET_MS=38"));
        assertEquals(BotFactory.configuration(BASE), BotFactory.configuration(BASE + ",TURN_BUDGET_MS=36"));
        for (String spec : new String[]{"v09?TURN_BUDGET_MS=38", "neural-v1?TURN_BUDGET_MS=38",
                "current?TURN_BUDGET_MS=38,USE_ROLLOUT_POLICY=false",
                "current?TURN_BUDGET_MS=38,USE_HEURISTIC_POLICY=true",
                "current?TURN_BUDGET_MS=38,USE_NEURAL_TARGET_PROPOSAL=true",
                "current?TURN_BUDGET_MS=37", "current?TURN_BUDGET_MS=50", "current?TURN_BUDGET_MS=0"})
            assertThrows(IllegalArgumentException.class, () -> BotFactory.create(spec), spec);
        assertThrows(IllegalArgumentException.class, () -> new MainBot(new Params("TURN_BUDGET_MS=50")));
        assertThrows(IllegalArgumentException.class, () -> new MainBot(new Params("TURN_BUDGET_MS=38,USE_ROLLOUT_POLICY=false")));
        assertThrows(IllegalArgumentException.class, () -> new MainBot(new Params("TURN_BUDGET_MS=38,USE_HEURISTIC_POLICY=true")));
        assertThrows(IllegalArgumentException.class, () -> new MainBot(new Params("TURN_BUDGET_MS=38,USE_NEURAL_TARGET_PROPOSAL=true")));
    }

    @Test void frozenSnapshotsResetAnAbsentBudgetAndKeepTheirConfigurationsUnchanged() {
        Params params = new Params("TURN_BUDGET_MS=38");
        FrozenParams.applyTo("v09", params);
        assertEquals(36, params.TURN_BUDGET_MS);
        assertFalse(BotFactory.configuration("v09").containsKey("TURN_BUDGET_MS"));
        assertEquals(BotFactory.configuration("v09"), BotFactory.configuration("v09?TURN_BUDGET_MS=36"));
    }
}
