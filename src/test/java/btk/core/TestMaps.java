package btk.core;

/** Small hand-made maps for rule tests. */
final class TestMaps {

    private TestMaps() {}

    /**
     * Builds a state from an ASCII picture. Characters: '.' plains, '~' river, '^' mountain,
     * digits = town id on plains. Every cell is its own region unless {@code regions} given.
     * {@code desired[t]} lists towns t wants to reach.
     */
    static GameState of(String[] rows, int[][] desired) {
        int h = rows.length, w = rows[0].length();
        int[] region = new int[w * h];
        int[] terrain = new int[w * h];
        int townCount = desired.length;
        int[] tx = new int[townCount], ty = new int[townCount];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                char ch = rows[y].charAt(x);
                int c = y * w + x;
                region[c] = c;
                if (ch == '~') terrain[c] = GameState.RIVER;
                else if (ch == '^') terrain[c] = GameState.MOUNTAIN;
                else terrain[c] = GameState.PLAINS;
                if (Character.isDigit(ch)) {
                    int id = ch - '0';
                    tx[id] = x;
                    ty[id] = y;
                }
            }
        }
        return new GameState(w, h, region, terrain, tx, ty, desired);
    }

    static void track(GameState s, int owner, int... xy) {
        for (int i = 0; i < xy.length; i += 2) s.owner[s.cell(xy[i], xy[i + 1])] = owner;
    }
}
