package btk.core;

/** Exact-rule-based hypothetical potential for training, not a final-scoring rule. */
public final class InkProjection {
    private InkProjection() {}

    /**
     * Training potential: instantly ink every legally disruptable region, freeze the remaining
     * board, then project exact current income through the remaining turns. Already earned
     * scores are preserved. If no desired pair can still be connected (or the turn cap was
     * reached), the game's actual termination rule permits no future income.
     *
     * <p>This hypothetical projection is not a new final-scoring rule. Town regions remain
     * protected, and canonical connection/scoring rules determine the surviving income.
     * Source is unchanged; scratch must be a distinct reusable SearchState for the same map.
     */
    public static void scores(GameState source, SearchState scratch, Pathing pathing, int[] out) {
        if (source == scratch.state || out.length < 2 || out == source.score)
            throw new IllegalArgumentException("Projection requires distinct source, scratch and output");
        scratch.set(source);
        GameState s = scratch.state;
        for (int r = 0; r < s.regionCount; r++)
            if (!s.regionInked[r] && !s.regionHasTown(r)) Rules.ink(s, r);
        scratch.refresh();
        if (Rules.gameOver(s, pathing)) {
            out[0] = source.score[0];
            out[1] = source.score[1];
            return;
        }
        Rules.incomePerTurn(s, out);
        int remaining = Math.max(0, s.turnsLeft());
        out[0] = source.score[0] + remaining * out[0];
        out[1] = source.score[1] + remaining * out[1];
    }
}
