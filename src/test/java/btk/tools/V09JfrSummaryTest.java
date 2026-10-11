package btk.tools;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class V09JfrSummaryTest {
    @Test void overlappingNestedDuplicateAndAdjacentPausesCountTheirUnionOnly() {
        var intervals = List.of(pause(10, 40), pause(10, 40), pause(15, 20), pause(30, 70), pause(70, 80));
        assertEquals(70, V09JfrSummary.unionOverlapNanos(intervals, 0, 100));
        assertEquals(60, V09JfrSummary.unionOverlapNanos(intervals, 15, 75));
        assertEquals(0, V09JfrSummary.unionOverlapNanos(List.of(), 0, 100));
    }

    @Test void clipsBothWindowEdgesIgnoresBoundaryTouchesAndKeepsGaps() {
        var intervals = List.of(pause(0, 100), pause(90, 120), pause(150, 250), pause(250, 260));
        assertEquals(70, V09JfrSummary.unionOverlapNanos(intervals, 100, 200));
        assertEquals(0, V09JfrSummary.unionOverlapNanos(intervals, 120, 150));
        assertEquals(0, V09JfrSummary.unionOverlapNanos(intervals, 110, 110));
        assertThrows(IllegalArgumentException.class,
                () -> V09JfrSummary.unionOverlapNanos(List.of(pause(20, 30), pause(0, 10)), 0, 50));
        assertThrows(IllegalArgumentException.class,
                () -> V09JfrSummary.unionOverlapNanos(intervals, 200, 100));
    }

    private static V09JfrSummary.Interval pause(long start, long end) {
        return new V09JfrSummary.Interval("jdk.GCPhasePause", start, end, Map.of());
    }
}
