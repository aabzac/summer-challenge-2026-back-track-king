package btk.bot.current;

import btk.bot.Params;
import btk.bot.Timer;
import btk.core.*;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static btk.bot.current.RolloutDiverseRepliesTest.*;
import static org.junit.jupiter.api.Assertions.*;

class RolloutNetworkChordsTest {
    private static GameState tripleGap(int enemy) {
        int[] regions = new int[14];
        for (int c = 0; c < regions.length; c++) regions[c] = c;
        var s = new GameState(7, 2, regions, new int[14],
                new int[]{0, 6}, new int[]{1, 1}, new int[][]{{1}, {}});
        for (int c : new int[]{8, 1, 2, 3, 4, 5, 12}) s.owner[c] = enemy;
        Rules.ink(s, 0); Rules.ink(s, 6);
        Rules.updateConnections(s, new Pathing(s.size));
        return s;
    }

    private static RolloutBot bot(GameState s, int me, boolean on) {
        var p = new Params("USE_PROTECTED_OPENING=0,NOISE=0,USE_DISRUPT=0");
        p.USE_ROLLOUT_NETWORK_CHORDS = on;
        var b = new RolloutBot(p); b.init(s, me); return b;
    }

    @Test void wholeTripleWinsAfterUnchangedOrdinaryAndSingletonSearchOnBothSides() throws Exception {
        for (int me = 0; me < 2; me++) {
            var s = tripleGap(1 - me);
            var off = bot(s, me, false);
            String baseline = Action.format(off.play(s, me, 60_000));
            var on = bot(s, me, true);
            int[] owners = s.owner.clone();
            var actions = on.play(s, me, 60_000);
            assertEquals("WAIT", baseline);
            assertTrue(on.ordinarySearchComplete);
            assertTrue(on.networkPhaseComplete);
            assertTrue(on.chordSearchStarted);
            assertEquals(off.ordinaryComparisons, on.ordinaryComparisons);
            assertEquals(off.ordinarySelectedValue, on.ordinarySelectedValue);
            assertArrayEquals(off.ordinarySelectedCells, on.ordinarySelectedCells);
            assertEquals(off.networkEligible, on.networkEligible);
            assertEquals(off.networkEvaluated, on.networkEvaluated);
            assertEquals(off.networkComparisons, on.networkComparisons);
            assertEquals(3, on.chordEligible);
            assertEquals(3, on.chordEvaluated);
            assertEquals(1, on.chordRoots);
            assertEquals(1, on.chordComparisons);
            assertTrue(on.selectedChord);
            assertFalse(on.selectedNetwork);
            assertEquals(-1, on.selectedNetworkCell);
            assertEquals("PLACE_TRACKS 2 1;PLACE_TRACKS 3 1;PLACE_TRACKS 4 1", Action.format(actions));
            assertTrue(on.selectedValue > off.selectedValue);
            assertEquals(on.ordinaryComparisons + on.networkComparisons + on.chordComparisons, on.completedComparisons);
            assertArrayEquals(owners, s.owner);
            var resolved = new Rules.TurnResult();
            Rules.expand(s, me, actions, new Pathing(s.size), resolved.moves[me], resolved);
            assertNull(resolved.moves[me].error);
            assertTrue(resolved.moves[me].skipped.isEmpty());
            assertEquals(3, resolved.moves[me].count);
        }
    }

    @Test void disabledModeHasNoBuffersAndChordsRequireAfterBase() throws Exception {
        assertFalse(new Params().USE_ROLLOUT_NETWORK_CHORDS);
        var off = bot(tripleGap(1), 0, false);
        for (String name : new String[]{"chordA", "chordB", "chordC", "chordOrder", "chordPriority", "chordGains", "chordBest"})
            assertNull(field(off, name));
        assertThrows(IllegalArgumentException.class, () -> new RolloutBot(new Params(
                "USE_PROTECTED_OPENING=0,USE_ROLLOUT_NETWORK_CHORDS=1,USE_ROLLOUT_NETWORK_AFTER_BASE=0")));
    }

    @Test void incompleteEarlierPhaseAndExpiredComparisonKeepExistingWinner() throws Exception {
        var s = tripleGap(1);
        var b = bot(s, 0, true);
        b.play(s, 0, 60_000);
        int first = (int) field(b, "chordRootStart");
        double previousValue = b.selectedValue;
        int previousRoute = b.selectedRoute;
        int[] previousCells = ((int[]) field(b, "bestCells")).clone();
        b.chordSearchStarted = false;
        b.networkPhaseComplete = false;
        ((Timer) field(b, "timer")).start(60_000);
        call(b, "searchChordsAfterNetwork", new Class<?>[]{GameState.class, int.class, int.class, int.class, long.class},
                s, 0, first, 1, 60_000L);
        assertFalse(b.chordSearchStarted);
        assertEquals(previousValue, b.selectedValue);
        b.networkPhaseComplete = true;
        ((Timer) field(b, "timer")).start(5);
        call(b, "searchChordsAfterNetwork", new Class<?>[]{GameState.class, int.class, int.class, int.class, long.class},
                s, 0, first, 1, 36L);
        assertFalse(b.chordSearchStarted, "do not collect packages inside the reserved comparison time");
        ((Timer) field(b, "timer")).start(-1);
        call(b, "searchChordsAfterNetwork", new Class<?>[]{GameState.class, int.class, int.class, int.class, long.class},
                s, 0, first, 1, 36L);
        assertFalse(b.chordSearchStarted);
        int comparisons = b.completedComparisons;
        boolean complete = (boolean) call(b, "searchRoots",
                new Class<?>[]{GameState.class, int.class, int.class, int.class, int.class},
                s, 0, first, first + b.chordRoots, 2);
        assertFalse(complete);
        assertEquals(comparisons, b.completedComparisons);
        assertEquals(previousValue, b.selectedValue);
        assertEquals(previousRoute, b.selectedRoute);
        assertArrayEquals(previousCells, (int[]) field(b, "bestCells"));
    }

    @Test void expiredNewPlayClearsChordTelemetryAndKeepsLegalFallback() {
        var s = tripleGap(1);
        var b = bot(s, 0, true);
        b.play(s, 0, 60_000);
        assertTrue(b.selectedChord);
        assertEquals("WAIT", Action.format(b.play(s, 0, 0)));
        assertFalse(b.selectedChord); assertFalse(b.chordSearchStarted); assertFalse(b.networkPhaseComplete);
        assertEquals(0, b.chordEligible); assertEquals(0, b.chordEvaluated);
        assertEquals(0, b.chordRoots); assertEquals(0, b.chordComparisons);
    }

    @Test void realBossStateRunsBoundedLegalSearchWithoutInputMutation() throws Exception {
        try (var reader = new InputStreamReader(getClass().getResourceAsStream(
                "/btk/bot/network-chord-boss-position.json"), StandardCharsets.UTF_8)) {
            var fixture = JsonParser.parseReader(reader).getAsJsonObject();
            var s = new Gson().fromJson(fixture.get("state"), GameState.class);
            Rules.updateConnections(s, new Pathing(s.size));
            for (int me = 0; me < 2; me++) {
                int[] before = s.owner.clone(), score = s.score.clone();
                var b = bot(s, me, true);
                var actions = b.play(s, me, 60_000);
                assertTrue(b.ordinarySearchComplete);
                assertTrue(b.networkPhaseComplete);
                assertTrue(b.chordSearchStarted);
                assertTrue(b.chordEligible <= 8 * s.size);
                assertTrue(b.chordEvaluated <= 24);
                assertTrue(b.chordRoots <= 2);
                assertArrayEquals(before, s.owner); assertArrayEquals(score, s.score);
                assertEquals(26, s.turn);
                var result = new Rules.TurnResult();
                Rules.expand(s, me, actions, new Pathing(s.size), result.moves[me], result);
                assertNull(result.moves[me].error);
                assertTrue(result.moves[me].skipped.isEmpty());
            }
        }
    }
}
