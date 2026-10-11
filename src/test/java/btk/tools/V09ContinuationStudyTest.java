package btk.tools;

import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class V09ContinuationStudyTest {
    @Test void oracleSweepFindsTwoDifferentCutsButDoesNotCallEitherASoloCut() {
        GameState s = new GameState(3, 3, new int[]{0,1,2,3,4,5,6,7,8}, new int[9],
                new int[]{0,2}, new int[]{1,1}, new int[][]{{1},{}});
        Rules.ink(s, 4);
        for (int i = 0; i < 3; i++) { Rules.disrupt(s, 1); Rules.disrupt(s, 7); }
        s.score[0] = 9; s.score[1] = 4;
        Rules.updateConnections(s, new Pathing(s.size));
        assertTrue(V09ClosureProbe.scan(s, 0, new int[0]).isEmpty());
        var rows = V09ContinuationStudy.oracleClosures(s, 0, new int[0],
                V09ContinuationStudy.accepted(s, 1, "DISRUPT 7"));
        assertEquals(1, rows.size()); assertEquals(1, rows.get(0).get("region"));
        assertEquals(false, rows.get(0).get("opponentAlreadyClosesWithoutOurHit"));
        assertTrue(V09ContinuationStudy.wins((V09ClosureProbe.Outcome) rows.get(0).get("outcome")));
    }

    @Test void fullActionWaitingDoesNotConfusePaintingOrDisruptionWithWait() {
        assertTrue(V09ContinuationStudy.waiting(new V09ContinuationStudy.Plan(new int[0], -1, "WAIT")));
        assertFalse(V09ContinuationStudy.waiting(new V09ContinuationStudy.Plan(new int[]{1}, -1, "PLACE_TRACKS 1 0")));
        assertFalse(V09ContinuationStudy.waiting(new V09ContinuationStudy.Plan(new int[0], 1, "DISRUPT 1")));
    }

    @Test void integrationSeparatesRecordedClosingWinFromAmpleShortlistMissOnBothSeats() throws Exception {
        for (int me = 0; me < 2; me++) {
            GameState s = new GameState(9, 1, new int[]{0,1,2,3,4,5,6,7,8}, new int[9],
                    new int[]{0,8}, new int[]{0,0}, new int[][]{{1},{}});
            for (int c = 1; c < 8; c++) s.owner[c] = me;
            s.turn = 20; s.score[me] = 20; s.score[1-me] = 10;
            for (int i = 0; i < 3; i++) Rules.disrupt(s, 5);
            Rules.updateConnections(s, new Pathing(s.size)); String before = ReplyStudy.stateHash(s);
            var counts = new LinkedHashMap<String, Integer>();
            var row = V09ContinuationStudy.inspect(s, me,
                    V09ContinuationStudy.accepted(s, me, "DISRUPT 5"),
                    V09ContinuationStudy.accepted(s, 1-me, "WAIT"), counts).row();
            assertEquals(1, row.get("guaranteedWinningRegions"));
            assertEquals(1, row.get("omittedWinningRegions"));
            assertEquals(false, row.get("missedGuaranteedByActual"));
            assertEquals(true, row.get("missedGuaranteedByAmple"));
            assertEquals(0, counts.get("statesMissedGuaranteedByActual"));
            assertEquals(1, counts.get("statesMissedGuaranteedByAmple"));
            assertEquals(before, ReplyStudy.stateHash(s));
        }
    }
}
