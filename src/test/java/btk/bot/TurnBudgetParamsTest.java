package btk.bot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.TreeMap;
import static org.junit.jupiter.api.Assertions.*;

class TurnBudgetParamsTest {
    private static final String BASE = "current?" + LiveTargetExperimentParams.V10_OFF + ",NOISE=0.1,USE_BATCHED_REFRESH=true";

    @ParameterizedTest
    @ValueSource(ints = {38, 40, 45})
    void onlyTheCurrentNativePolicyMayRequestAnExplicitBudgetOverride(int budgetMs) {
        assertEquals(36, new Params().TURN_BUDGET_MS);
        assertEquals(36, new MainBot().turnBudgetMs());
        String query = "TURN_BUDGET_MS=" + budgetMs;
        Params p = BotFactory.parameters(BASE + "," + query);
        MainBot main = new MainBot(p); p.TURN_BUDGET_MS = 36;
        assertEquals(budgetMs, main.turnBudgetMs(), "MainBot retains its own resolved configuration");
        var expected = new TreeMap<>(BotFactory.configuration(BASE));
        expected.put("TURN_BUDGET_MS", String.valueOf(budgetMs));
        assertEquals(expected, BotFactory.configuration(BASE + "," + query));
        assertEquals(BotFactory.configuration(BASE), BotFactory.configuration(BASE + ",TURN_BUDGET_MS=36"));
        for (String spec : new String[]{"v09?" + query, "neural-v1?" + query,
                "current?" + LiveTargetExperimentParams.V10_OFF + "," + query + ",USE_ROLLOUT_POLICY=false",
                "current?" + LiveTargetExperimentParams.V10_OFF + "," + query + ",USE_HEURISTIC_POLICY=true",
                "current?" + LiveTargetExperimentParams.V10_OFF + "," + query + ",USE_NEURAL_TARGET_PROPOSAL=true"})
            assertThrows(IllegalArgumentException.class, () -> BotFactory.create(spec), spec);
        for (String flag : new String[]{"USE_ROLLOUT_POLICY=false", "USE_HEURISTIC_POLICY=true",
                "USE_NEURAL_TARGET_PROPOSAL=true"})
            assertThrows(IllegalArgumentException.class, () -> new MainBot(new Params(query + "," + flag)), flag);
    }

    @Test void undeclaredBudgetValuesAreRejectedByBothEntryPoints() {
        for (int budgetMs : new int[]{0, 37, 39, 41, 44, 46, 50, 100}) {
            String query = "TURN_BUDGET_MS=" + budgetMs;
            assertThrows(IllegalArgumentException.class, () -> BotFactory.create("current?" + LiveTargetExperimentParams.V10_OFF + "," + query), query);
            assertThrows(IllegalArgumentException.class, () -> new MainBot(new Params(query)), query);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {38, 40, 45})
    void frozenSnapshotsResetAnAbsentBudgetAndKeepTheirConfigurationsUnchanged(int budgetMs) {
        Params params = new Params(LiveTargetExperimentParams.V10_OFF + ",TURN_BUDGET_MS=" + budgetMs);
        FrozenParams.applyTo("v09", params);
        assertEquals(36, params.TURN_BUDGET_MS);
        assertFalse(BotFactory.configuration("v09").containsKey("TURN_BUDGET_MS"));
        assertEquals(BotFactory.configuration("v09"), BotFactory.configuration("v09?TURN_BUDGET_MS=36"));
    }
}
