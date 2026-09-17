package btk.core;

/** Allocation-free helpers for single-cell changes to the connector network. */
public final class NetworkCells {
    private NetworkCells() {}

    /**
     * Collects every affordable single-cell placement with at least two neighboring connectors.
     * This is a necessary, not sufficient, condition for one placement to change a connection;
     * neighbors in the same rail component are retained because a shortcut can change its path.
     * Results are in ascending cell order. Does not change the board or allocate scratch storage.
     *
     * @param out caller-owned buffer of at least {@code s.size} cells
     * @return number of candidates written to {@code out}
     */
    public static int collectSingleCellConnections(GameState s, int[] out) {
        int count = 0;
        for (int c = 0; c < s.size; c++) {
            if (!s.canBuild(c) || s.placeCost(c) > GameState.PAINT_PER_TURN) continue;
            int x = c % s.width;
            int neighbors = 0;
            if (c >= s.width && s.passable(c - s.width)) neighbors++;
            if (x + 1 < s.width && s.passable(c + 1)) neighbors++;
            if (c + s.width < s.size && s.passable(c + s.width)) neighbors++;
            if (x > 0 && s.passable(c - 1)) neighbors++;
            if (neighbors >= 2) out[count++] = c;
        }
        return count;
    }

    /**
     * Sums caller-supplied weights over the orthogonally adjacent towns or tracks.
     * The cell must be on the board and {@code weights} must contain at least {@code s.size}
     * entries. No ownership, scoring or candidate-ranking meaning is assigned to the weights.
     */
    public static int adjacentConnectorWeight(GameState s, int cell, int[] weights) {
        int x = cell % s.width;
        int result = 0;
        if (cell >= s.width && s.passable(cell - s.width)) result += weights[cell - s.width];
        if (x + 1 < s.width && s.passable(cell + 1)) result += weights[cell + 1];
        if (cell + s.width < s.size && s.passable(cell + s.width)) result += weights[cell + s.width];
        if (x > 0 && s.passable(cell - 1)) result += weights[cell - 1];
        return result;
    }
}
