package btk.core;

/** Allocation-free geometry for affordable connected two/three-cell network additions. */
public final class NetworkChords {
    private NetworkChords() {}

    /**
     * Enumerate each adjacent pair once and each three-cell chain once through its unique
     * middle. Both endpoints touch an existing connector; same-component shortcuts remain
     * eligible. Total paint is at most one turn. Caller buffers must each hold 8 * s.size:
     * at most 2 * size grid edges plus 6 * size pairs of neighbors around middle cells.
     * Pairs have a < b and c = -1; triples have endpoint a < c and middle b.
     */
    public static int collect(GameState s, int[] a, int[] b, int[] c) {
        int count = 0;
        for (int center = 0; center < s.size; center++) {
            if (!s.canBuild(center) || s.placeCost(center) >= GameState.PAINT_PER_TURN) continue;
            boolean centerTouches = touchesConnector(s, center);
            for (int direction = 0; direction < 4; direction++) {
                int first = neighbor(s, center, direction);
                if (first < 0 || !s.canBuild(first) || !touchesConnector(s, first)) continue;
                int pairCost = s.placeCost(center) + s.placeCost(first);
                if (pairCost > GameState.PAINT_PER_TURN) continue;
                if (center < first && centerTouches) {
                    a[count] = center; b[count] = first; c[count++] = -1;
                }
                if (pairCost >= GameState.PAINT_PER_TURN) continue;
                for (int otherDirection = direction + 1; otherDirection < 4; otherDirection++) {
                    int last = neighbor(s, center, otherDirection);
                    if (last < 0 || !s.canBuild(last) || !touchesConnector(s, last)
                            || pairCost + s.placeCost(last) > GameState.PAINT_PER_TURN) continue;
                    a[count] = Math.min(first, last); b[count] = center; c[count++] = Math.max(first, last);
                }
            }
        }
        return count;
    }

    private static boolean touchesConnector(GameState s, int cell) {
        for (int direction = 0; direction < 4; direction++) {
            int next = neighbor(s, cell, direction);
            if (next >= 0 && s.passable(next)) return true;
        }
        return false;
    }

    private static int neighbor(GameState s, int cell, int direction) {
        int x = cell % s.width;
        return switch (direction) {
            case 0 -> cell >= s.width ? cell - s.width : -1;
            case 1 -> x + 1 < s.width ? cell + 1 : -1;
            case 2 -> cell + s.width < s.size ? cell + s.width : -1;
            default -> x > 0 ? cell - 1 : -1;
        };
    }
}
