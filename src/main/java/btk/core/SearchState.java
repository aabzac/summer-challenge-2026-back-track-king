package btk.core;

/** Reusable exact search state. Allocate once, then copy/resolve without per-node objects. */
public final class SearchState {
    public final GameState state;
    private final Pathing pathing;
    private final int[][] paths;

    public SearchState(GameState initial) {
        state = initial.copy();
        pathing = new Pathing(initial.size);
        paths = new int[initial.pairCount][initial.size];
        set(initial);
        refresh();
    }

    public void set(GameState source) {
        if (source == state) return;
        // Restore reusable storage before copyFrom, including paths that were inactive last time.
        for (int p = 0; p < state.pairCount; p++) state.pairPath[p] = paths[p];
        state.copyFrom(source);
    }

    public void refresh() {
        // Existing Pathing supplies the canonical shortest path and directional tie-break.
        // Per-pair calls retain our buffers even when a connection disappears and reappears.
        for (int p = 0; p < state.pairCount; p++) {
            int n = pathing.shortestPath(state, state.pairFrom[p], state.pairTo[p], paths[p]);
            state.pairPath[p] = n < 0 ? null : paths[p];
            state.pairPathLen[p] = Math.max(0, n);
        }
    }

    /** Exact signed income of one active connection, kept in the core with all scoring rules. */
    public static int connectionIncomeDifference(GameState s, int pair, int me) {
        int result = 0;
        for (int i = 0; i < s.pairPathLen[pair]; i++) {
            int owner = s.owner[s.pairPath[pair][i]];
            if (owner == me) result++;
            else if (owner == 1 - me) result--;
        }
        return result;
    }

    /** Inputs must be legal against the same pre-turn board; -1 means no disruption. */
    public boolean resolve(int[] cells0, int n0, int target0, int[] cells1, int n1, int target1) {
        Rules.applyPlacements(state, cells0, n0, cells1, n1);
        if (target0 >= 0) Rules.disrupt(state, target0);
        if (target1 >= 0) Rules.disrupt(state, target1);
        Rules.inkUnstable(state);
        refresh();
        Rules.scoreTurn(state, null);
        state.turn++;
        return Rules.gameOver(state, pathing);
    }
}
