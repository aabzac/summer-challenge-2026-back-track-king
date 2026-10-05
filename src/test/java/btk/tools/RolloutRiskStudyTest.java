package btk.tools;

import btk.bot.Params;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.core.TacticalPathing;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RolloutRiskStudyTest {
    private GameState threatenedConnection() {
        GameState s = new GameState(4, 2, new int[]{0, 1, 2, 3, 4, 4, 4, 4},
                new int[8], new int[]{0, 3}, new int[]{0, 0}, new int[][]{{1}, {}});
        s.owner[1] = s.owner[2] = 0;
        s.instability[1] = s.instability[2] = 3;
        s.regionInstability[1] = s.regionInstability[2] = 3;
        Rules.updateConnections(s, new Pathing(s.size));
        return s;
    }

    @Test void matchedControlUsesExistingRailsWhileAllCellRiskFindsAnAffordableStartOfBypass() {
        GameState s = threatenedConnection();
        TacticalPathing paths = new TacticalPathing(s.size);
        int[] path = new int[s.size];
        int[] control = RolloutRiskStudy.entryCosts(s, new Params(), false);
        int[] risk = RolloutRiskStudy.entryCosts(s, new Params(), true);
        int n = paths.weightedPath(s, 0, 3, control, path);
        assertArrayEquals(new int[]{0, 1, 2, 3}, Arrays.copyOf(path, n));
        n = paths.weightedPath(s, 0, 3, risk, path);
        assertArrayEquals(new int[]{0, 4, 5, 6, 7, 3}, Arrays.copyOf(path, n));
        // A real core transition can afford the first three new cells; completing the bypass
        // takes another turn. This is a construction opportunity, not a won-game assertion.
        Rules.applyPlacements(s, new int[]{4, 5, 6}, 3, new int[0], 0);
        assertEquals(0, s.owner[4]);
        assertEquals(0, s.owner[5]);
        assertEquals(0, s.owner[6]);
        assertEquals(GameState.NO_TRACK, s.owner[7]);
    }

    @Test void selectionRequiresCurrentOwnIncomeAndAnUnprotectedUnstableConnection() {
        GameState s = threatenedConnection();
        assertTrue(RolloutRiskStudy.eligiblePair(s, 0, 0));
        assertFalse(RolloutRiskStudy.eligiblePair(s, 1, 0));
        s.regionInstability[1] = s.regionInstability[2] = 0;
        assertFalse(RolloutRiskStudy.eligiblePair(s, 0, 0));
        s.regionInstability[1] = 3;
        s.owner[2] = GameState.NO_TRACK;
        Rules.updateConnections(s, new Pathing(s.size));
        assertFalse(RolloutRiskStudy.eligiblePair(s, 0, 0));
    }
}
