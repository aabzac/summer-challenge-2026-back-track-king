package btk.referee;

import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReplayTest {

    @Test void localSeedsRetainZeroAndExactSignedLongText() {
        var state = new GameState(1, 1, new int[]{0}, new int[]{0},
                new int[0], new int[0], new int[0][]);
        for (long seed : new long[]{0, 9_007_199_254_740_993L, Long.MIN_VALUE, Long.MAX_VALUE}) {
            var replay = new Replay(state, seed, "p0", "p1");
            var meta = JsonParser.parseString(replay.toJson()).getAsJsonObject().getAsJsonObject("meta");
            assertTrue(meta.getAsJsonPrimitive("seed").isNumber());
            assertEquals(seed, meta.get("seed").getAsLong());
            assertTrue(meta.getAsJsonPrimitive("seedText").isString());
            assertEquals(Long.toString(seed), meta.get("seedText").getAsString());
        }
    }

    @Test void recordsExplicitLocalLabelsButNeverRelabelsCgPlayers() {
        var state = btk.referee.MapGenerator.generate(321);
        var local = new Replay(state, 321, "old bot name", "other");
        local.describeLocalBots("v05", "exp-income-guard?NOISE=0.2");
        assertArrayEquals(new String[]{"v05", "exp-income-guard?NOISE=0.2"}, local.meta.bots);
        assertEquals("v05 - Disrupt Reserve", local.meta.botDetails[0].displayName());
        assertEquals("not_promoted", local.meta.botDetails[1].status());
        assertTrue(local.toJson().contains("implementationSha256"));
        var cg = new Replay(state, 321, "v05", "exp-income-guard");
        cg.meta.source = "cg";
        assertNull(cg.meta.botDetails);
        assertThrows(IllegalStateException.class, () -> cg.describeLocalBots("v05", "exp-income-guard"));
        assertArrayEquals(new String[]{"v05", "exp-income-guard"}, cg.meta.bots);
    }

    @Test
    @SuppressWarnings("unchecked")
    void recordsRegionSabotageAndInkStatePerTurn() {
        GameState state = new GameState(2, 1, new int[]{0, 0}, new int[]{0, 0},
                new int[0], new int[0], new int[0][]);
        Replay replay = new Replay(state, 1L, "p0", "p1");
        Pathing pathing = new Pathing(state.size);

        for (int turn = 1; turn <= GameState.INK_THRESHOLD; turn++) {
            List<Action> disrupt = List.of(Action.disrupt(0));
            List<Action> wait = List.of(Action.waitAction());
            Rules.TurnResult result = Rules.resolve(state, disrupt, wait, pathing);
            replay.addTurn(state, new List[]{disrupt, wait}, result);

            assertArrayEquals(new int[]{turn}, replay.turns.get(turn - 1).regionInstability);
            assertArrayEquals(new boolean[]{turn == GameState.INK_THRESHOLD},
                    replay.turns.get(turn - 1).regionInked);
        }
    }
}
