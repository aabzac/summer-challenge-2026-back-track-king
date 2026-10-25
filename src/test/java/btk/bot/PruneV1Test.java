package btk.bot;

import btk.bot.current.RolloutBot;
import btk.bot.v10.RolloutBotV10;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.referee.MapGenerator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * prune-v1 (2026-10-25): the rejected rollout experiments left {@code current}; frozen {@code v10} is the
 * reference algorithm. With ample time every search is complete and deterministic (seeded noise, bounded
 * route enumeration), so the pruned bot must reproduce the frozen copy's decisions move for move.
 */
class PruneV1Test {
    private static final long AMPLE_MS = 60_000;
    private static final int MAX_TURNS = 60;

    /** One generated map per board size among the first seeds, largest sizes included. */
    static List<GameState> mapsOfEverySize() {
        Map<String, GameState> bySize = new LinkedHashMap<>();
        for (long seed = 1; seed <= 400 && bySize.size() < 7; seed++) {
            GameState s = MapGenerator.generate(seed);
            bySize.putIfAbsent(s.width + "x" + s.height, s);
        }
        assertTrue(bySize.containsKey("30x20"), "the tall 30x20 board must be covered: " + bySize.keySet());
        assertTrue(bySize.size() >= 5, "map sizes: " + bySize.keySet());
        return new ArrayList<>(bySize.values());
    }

    @Test void currentPlaysExactlyLikeFrozenV10OnEveryBoardSizeAndSeat() {
        for (GameState map : mapsOfEverySize()) for (int seat = 0; seat < 2; seat++) {
            GameState s = map.copy();
            Pathing pathing = new Pathing(s.size);
            Rules.updateConnections(s, pathing);
            RolloutBot current = (RolloutBot) BotFactory.create(LiveTargetExperimentParams.V10_ON_CURRENT); // the v10 configuration since the v11 promotion
            RolloutBotV10 frozen = (RolloutBotV10) BotFactory.create("v10");
            Bot opponent = BotFactory.create("v10");
            current.init(s.copy(), seat); frozen.init(s.copy(), seat); opponent.init(s.copy(), 1 - seat);
            int turns = 0;
            while (!Rules.gameOver(s, pathing) && turns < MAX_TURNS) {
                String where = map.width + "x" + map.height + " seat " + seat + " turn " + s.turn;
                List<Action> expected = frozen.play(s.copy(), seat, AMPLE_MS);
                List<Action> actual = current.play(s.copy(), seat, AMPLE_MS);
                assertEquals(Action.format(expected), Action.format(actual), where);
                assertEquals(frozen.routeQueries, current.routeQueries, where);
                assertEquals(frozen.completedComparisons, current.completedComparisons, where);
                assertEquals(frozen.simulatedTurns, current.simulatedTurns, where);
                assertEquals(frozen.selectedValue, current.selectedValue, where);
                assertEquals(frozen.selectedRoute, current.selectedRoute, where);
                assertEquals(frozen.selectedTarget, current.selectedTarget, where);
                assertEquals(frozen.networkRoots, current.networkRoots, where);
                assertEquals(frozen.fillCells, current.fillCells, where);
                assertEquals(frozen.selectedOpening, current.selectedOpening, where);
                assertTrue(current.ordinarySearchComplete || current.selectedOpening, where);
                List<Action> reply = opponent.play(s.copy(), 1 - seat, AMPLE_MS);
                Rules.resolve(s, seat == 0 ? actual : reply, seat == 0 ? reply : actual, pathing);
                turns++;
            }
            assertTrue(turns >= 10, "the comparison must cover a real game: " + turns + " turns");
        }
    }

    @Test void prunedExperimentsAreRefusedOnCurrentAndStillRunOnFrozenV10() {
        for (String flag : BotFactory.PRUNED_ROLLOUT_FLAGS) {
            assertThrows(IllegalArgumentException.class, () -> BotFactory.create("current?" + flag + "=1"), flag);
            assertThrows(IllegalArgumentException.class, () -> BotFactory.parameters("current?" + flag + "=1"), flag);
            assertThrows(IllegalArgumentException.class, () -> BotFactory.create("main?" + flag + "=1"), flag);
            assertDoesNotThrow(() -> BotFactory.parameters("current?" + flag + "=0"), "an explicit off value stays legal");
            assertFalse(BotFactory.parameters("v10").USE_ROLLOUT_SPREAD);
        }
        // Frozen v10 keeps every mechanism (the historical fixtures of btk.bot.v10 switch the later selections off
        // the way the original experiments did); RHEA code left the shipped source with v09 and v10 refuses it too.
        String fixture = "v10?" + BotFactory.V10_FLAGS_OFF + ",USE_PROTECTED_OPENING=0,USE_ROLLOUT_QUICK_ROUTE=0,USE_ROLLOUT_NETWORK_AFTER_BASE=0,USE_ROLLOUT_FILL=0,";
        for (String flag : BotFactory.PRUNED_ROLLOUT_FLAGS) {
            if (flag.equals("USE_ROLLOUT_RHEA")) continue;
            String spec = fixture + flag + "=1"
                    + (flag.equals("USE_ROLLOUT_NETWORK_CHORDS") ? ",USE_ROLLOUT_NETWORK_AFTER_BASE=1" : "")
                    + (flag.equals("USE_ROLLOUT_ROOT_FILL") ? ",USE_ROLLOUT_FILL=1" : "");
            assertInstanceOf(RolloutBotV10.class, BotFactory.create(spec), spec);
        }
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("v10?USE_ROLLOUT_RHEA=1"));
        // Since the v11 promotion the test helper also switches the root-target flag off (the lite chooser off implies it).
        assertEquals(BotFactory.V10_FLAGS_OFF + "," + BotFactory.V11_FLAGS_OFF, LiveTargetExperimentParams.V10_OFF);
        assertTrue(BotFactory.PRUNED_ROLLOUT_FLAGS.containsAll(List.of("USE_ROLLOUT_SPREAD", "USE_ROLLOUT_NETWORK_CHORDS",
                "USE_ROLLOUT_ALL_CELL_RISK", "USE_ROLLOUT_ROOT_FILL", "USE_ROLLOUT_LIVE_TARGET", "USE_ROLLOUT_TRACK_VALUE")));
    }
}
