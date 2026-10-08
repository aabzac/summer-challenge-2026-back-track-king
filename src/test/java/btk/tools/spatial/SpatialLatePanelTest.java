package btk.tools.spatial;

import btk.bot.Bot;
import btk.bot.neural.SpatialNet;
import btk.core.Action;
import btk.core.GameState;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpatialLatePanelTest {
    @Test void terminalSlotsAreSkippedWithoutContinuingOrReplacingTheMap() throws Exception {
        GameState state = line(); state.maxTurns = 3;
        SpatialLatePanel.Capture result = SpatialLatePanel.capture(5, state,
                new Bot[]{waiting(), waiting()}, new int[]{0, 2, 3, 4}, 60_000);
        assertEquals(List.of(0, 2), result.positions.stream().map(p -> p.state.turn).toList());
        assertEquals(3, result.trajectory.size());
        assertEquals(3, state.turn);
        assertEquals(true, result.map.get("terminal"));
        assertEquals(2, ((List<?>) result.map.get("skippedSlots")).size());
        assertEquals("actual terminal", result.map.get("stopReason"));
    }

    @Test void bothReferenceSeatsSeeSamePreStateAndCapturedStatesRemainFrozen() throws Exception {
        GameState state = line();
        Bot sameCell = new Bot() {
            public String name() { return "synthetic"; }
            public List<Action> play(GameState s, int me, long budget) {
                assertEquals(GameState.NO_TRACK, s.owner[1]);
                return List.of(Action.place(1, 0));
            }
        };
        SpatialLatePanel.Capture result = SpatialLatePanel.capture(6, state,
                new Bot[]{sameCell, sameCell}, new int[]{0, 1}, 60_000);
        assertEquals(1, state.turn, "capture stops at the requested state without playing its move");
        assertEquals(1, result.trajectory.size());
        assertEquals(GameState.NO_TRACK, result.positions.get(0).state.owner[1]);
        assertEquals(GameState.NEUTRAL, result.positions.get(1).state.owner[1]);
        assertEquals(false, result.map.get("terminal"));
        assertTrue(((List<?>) result.map.get("skippedSlots")).isEmpty());
    }

    @Test void allFourConditionalRowsIncludeForcedStopAndIndependentObservationSnapshots() {
        GameState state = line(); state.terrain[1] = GameState.MOUNTAIN;
        SpatialLatePanel.Trace trace = SpatialLatePanel.trace(state, 0,
                new SpatialNet(new float[SpatialNet.WEIGHT_COUNT]));
        assertEquals(4, trace.rows.size());
        assertEquals(1, trace.rows.get(0).getAsJsonObject("java").get("selected").getAsInt());
        for (int stage = 1; stage < 3; stage++) {
            JsonObject row = trace.rows.get(stage);
            assertEquals(600, row.getAsJsonObject("java").get("selected").getAsInt());
            var mask = row.getAsJsonObject("observation").getAsJsonArray("buildMask");
            int legal = 0; for (var value : mask) if (value.getAsBoolean()) legal++;
            assertEquals(1, legal);
        }
        assertEquals(1, trace.rows.get(3).getAsJsonObject("java").get("selected").getAsInt());
        assertEquals(0, trace.rows.get(0).getAsJsonObject("observation").getAsJsonArray("planes").get(13 * 600 + 1).getAsFloat());
        assertEquals(1, trace.rows.get(1).getAsJsonObject("observation").getAsJsonArray("planes").get(13 * 600 + 1).getAsFloat());
        assertEquals("PLACE_TRACKS 1 0;DISRUPT 1", Action.format(trace.actions));
        assertEquals(GameState.NO_TRACK, state.owner[1]); assertEquals(0, state.turn);
    }

    @Test void repeatedOrUnsortedTurnScheduleRejectedBeforeCallingBots() {
        assertThrows(IllegalArgumentException.class, () -> SpatialLatePanel.capture(5, line(),
                new Bot[]{waiting(), waiting()}, new int[]{0, 0}, 60_000));
    }

    private static Bot waiting() {
        return new Bot() {
            public String name() { return "synthetic-wait"; }
            public List<Action> play(GameState s, int me, long budget) { return List.of(Action.waitAction()); }
        };
    }
    private static GameState line() {
        return new GameState(3, 1, new int[]{0, 1, 2}, new int[3],
                new int[]{0, 2}, new int[]{0, 0}, new int[][]{{1}, {}});
    }
}
