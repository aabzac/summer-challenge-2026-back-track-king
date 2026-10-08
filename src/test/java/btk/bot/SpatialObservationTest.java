package btk.bot;

import btk.bot.neural.SpatialObservation;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpatialObservationTest {
    @Test void paddingIsZeroAndPaddedIndicesDoNotAliasNativeCells() {
        GameState s = board();
        SpatialObservation obs = new SpatialObservation(s); obs.prepare(s, 0);
        assertEquals(1, obs.planes[30]); // native row1 col0, not native cell30.
        assertEquals(0, obs.planes[3]);
        assertEquals(-1, obs.regionIds[3]);
        assertFalse(obs.buildMask[3]);
        for (int plane = 0; plane < 24; plane++) assertEquals(0, obs.planes[plane * 600 + 3]);
        assertArrayEquals(new int[]{0, 32}, new int[]{obs.townCells[0], obs.townCells[1]});
        assertEquals(-1, obs.townCells[2]);
        for (int i = 2 * 49; i < obs.towns.length; i++) assertEquals(0, obs.towns[i]);
        assertEquals(3, obs.nativeCell(30));
        assertThrows(IllegalArgumentException.class, () -> obs.nativeCell(3));
        assertThrows(IllegalArgumentException.class, () -> obs.nativeCell(600));
    }

    @Test void playerSwapSwapsOwnershipIncomeAndTownCountsButNegatesScoreGap() {
        GameState s = board(); s.owner[1] = 0; s.owner[2] = 1; s.owner[4] = 0;
        s.score[0] = 20; s.score[1] = 3; Rules.updateConnections(s, new Pathing(s.size));
        SpatialObservation a = new SpatialObservation(s), b = new SpatialObservation(s);
        a.prepare(s, 0); b.prepare(s, 1);
        for (int p = 0; p < 600; p++) {
            assertEquals(a.planes[8 * 600 + p], b.planes[9 * 600 + p]);
            assertEquals(a.planes[15 * 600 + p], b.planes[16 * 600 + p]);
            assertEquals(a.planes[23 * 600 + p], -b.planes[23 * 600 + p], 1e-7);
        }
        assertEquals(a.scalars[3], b.scalars[4]);
        assertEquals(a.towns[1 + 4 + 2], b.towns[1 + 4 + 3]);
        assertEquals(1f, a.towns[1 + 4]); // directed desired+standing+joinable=7.
        assertArrayEquals(a.buildMask, b.buildMask); assertArrayEquals(a.disruptMask, b.disruptMask);
    }

    @Test void masksRespectCoreCostsAndPrefixesReplaceWithoutChangingBoard() {
        GameState s = board(); s.terrain[1] = GameState.RIVER; s.terrain[2] = GameState.MOUNTAIN;
        SpatialObservation obs = new SpatialObservation(s); obs.prepare(s, 0);
        assertTrue(obs.buildMask[1]); assertTrue(obs.buildMask[2]); assertFalse(obs.buildMask[0]);
        assertFalse(obs.disruptMask[0]); assertFalse(obs.disruptMask[5]); assertTrue(obs.disruptMask[1]);
        obs.prefix(new int[]{1}, 1, 1, 1, false);
        assertFalse(obs.buildMask[1]); assertFalse(obs.buildMask[2]); assertTrue(obs.buildMask[30]);
        assertEquals(1, obs.planes[13 * 600 + 1]); assertEquals(GameState.NO_TRACK, s.owner[1]);
        assertEquals(1f / 3, obs.scalars[2]);
        obs.prefix(new int[]{30}, 1, 2, 1, false);
        assertEquals(0, obs.planes[13 * 600 + 1]); assertEquals(1, obs.planes[13 * 600 + 30]);
        assertTrue(obs.buildMask[1]);
        assertThrows(IllegalArgumentException.class, () -> obs.prefix(new int[]{1}, 1, 2, 1, false));
        assertThrows(IllegalArgumentException.class, () -> obs.prefix(new int[]{30, 30}, 2, 1, 2, false));
        assertThrows(IllegalArgumentException.class, () -> obs.prefix(new int[]{0}, 1, 2, 1, false));
    }

    @Test void stopAndNoPaintForceOnlyStopAndInkDoesNotDependOnOwnPendingCells() {
        GameState s = board(); s.terrain[1] = GameState.MOUNTAIN;
        SpatialObservation obs = new SpatialObservation(s); obs.prepare(s, 0);
        boolean[] inks = obs.disruptMask.clone();
        obs.prefix(new int[]{1}, 1, 0, 1, false); onlyStop(obs);
        obs.prefix(null, 0, 3, 2, true); onlyStop(obs);
        obs.prefix(null, 0, 3, 3, false); onlyStop(obs);
        assertArrayEquals(inks, obs.disruptMask);
        assertEquals(0, s.turn);
    }

    private static void onlyStop(SpatialObservation obs) {
        for (int p = 0; p < 600; p++) assertFalse(obs.buildMask[p]);
        assertTrue(obs.buildMask[600]);
    }

    private static GameState board() {
        GameState s = new GameState(3, 2, new int[]{0,1,2,3,4,5}, new int[6],
                new int[]{0,2}, new int[]{0,1}, new int[][]{{1},{}});
        Rules.updateConnections(s, new Pathing(s.size)); return s;
    }
}
