package btk.bot.neural;

import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import java.util.Arrays;

/**
 * Fixed v1 public observation. NCHW planes use padded index y*30+x, never native cell ids.
 * Planes: 0 board; 1..3 terrain; 4 town; 5 protected region; 6 north and 7 east region
 * boundary (including board edge); 8 own,9 enemy,10 neutral rail;11 instability/4;
 * 12 inked;13 pending own rails;14 active desired-path membership / pairCount;
 * 15 own and16 enemy rails in region /600;17 remaining instability/4 (zero protected/inked);
 * 18 turn/maxTurns;19 action stage/3;20 remaining paint/3;21 own and22 enemy income
 * log1p/10;23 signed log1p(score gap)/10. Scalars are the same six values as planes18..23.
 * Each town row is [paddedCell/599, 12 groups of (flags/7,length/600,own/600,enemy/600)].
 * Flags: desired directed connection=1, standing path=2, reachable avoiding ink=4.
 * Length and ownership count the core's current standing shortest path, including its
 * town endpoints in length; absent/self paths have zero length/counts. Missing rows are
 * zero with townCells=-1. These normalizations are the fixed v1 design, not source quotes.
 * Pending selections never mutate the engine state or the standing-path observation.
 */
public final class SpatialObservation {
    public static final int W = 30, H = 20, S = W * H, PLANES = 24;
    public static final int TOWNS = 12, TOWN_FEATURES = 49, STOP = S;
    public final float[] planes = new float[PLANES * S];
    public final float[] towns = new float[TOWNS * TOWN_FEATURES];
    public final float[] scalars = new float[6];
    public final int[] townCells = new int[TOWNS], regionIds = new int[S];
    public final boolean[] buildMask = new boolean[S + 1], disruptMask = new boolean[S + 1];
    private final Pathing pathing;
    private final int[] path, ownRegion = new int[S], enemyRegion = new int[S], income = new int[2];
    private final boolean[] selected = new boolean[S];
    private final int width, height;
    private GameState state;

    public SpatialObservation(GameState initial) {
        if (initial.width > W || initial.height > H || initial.townCount > TOWNS
                || initial.regionCount > S) throw new IllegalArgumentException("map exceeds spatial-v1 dimensions");
        width = initial.width;
        height = initial.height;
        pathing = new Pathing(initial.size);
        path = new int[initial.size];
        Arrays.fill(townCells, -1);
        Arrays.fill(regionIds, -1);
    }

    /** Recompute only after the actual game changes; callers own that turn-level cache. */
    public void prepare(GameState s, int me) {
        if (me < 0 || me > 1 || s.width != width || s.height != height
                || s.townCount > TOWNS || s.regionCount > S) throw new IllegalArgumentException("invalid observation state/player");
        state = s;
        Arrays.fill(planes, 0);
        Arrays.fill(towns, 0);
        Arrays.fill(townCells, -1);
        Arrays.fill(regionIds, -1);
        Arrays.fill(ownRegion, 0);
        Arrays.fill(enemyRegion, 0);
        Arrays.fill(disruptMask, false);
        for (int c = 0; c < s.size; c++) {
            if (s.owner[c] == me) ownRegion[s.regionId[c]]++;
            else if (s.owner[c] == 1 - me) enemyRegion[s.regionId[c]]++;
        }
        Rules.incomePerTurn(s, income);
        scalars[0] = s.turn / (float) Math.max(1, s.maxTurns);
        scalars[1] = 0;
        scalars[2] = 1;
        scalars[3] = signedLog(income[me]);
        scalars[4] = signedLog(income[1 - me]);
        scalars[5] = signedLog(s.score[me] - s.score[1 - me]);
        for (int c = 0; c < s.size; c++) {
            int x = s.x(c), y = s.y(c), at = y * W + x, r = s.regionId[c];
            boolean protectedRegion = s.regionHasTown(r);
            regionIds[at] = r;
            planes[at] = 1;
            planes[(1 + s.terrain[c]) * S + at] = 1;
            planes[4 * S + at] = s.isTown(c) ? 1 : 0;
            planes[5 * S + at] = protectedRegion ? 1 : 0;
            planes[6 * S + at] = y == 0 || s.regionId[c - s.width] != r ? 1 : 0;
            planes[7 * S + at] = x + 1 == s.width || s.regionId[c + 1] != r ? 1 : 0;
            planes[8 * S + at] = s.owner[c] == me ? 1 : 0;
            planes[9 * S + at] = s.owner[c] == 1 - me ? 1 : 0;
            planes[10 * S + at] = s.owner[c] == GameState.NEUTRAL ? 1 : 0;
            planes[11 * S + at] = s.instability[c] / (float) GameState.INK_THRESHOLD;
            planes[12 * S + at] = s.inked[c] ? 1 : 0;
            planes[15 * S + at] = ownRegion[r] / (float) S;
            planes[16 * S + at] = enemyRegion[r] / (float) S;
            planes[17 * S + at] = protectedRegion || s.inked[c] ? 0
                    : (GameState.INK_THRESHOLD - s.regionInstability[r]) / (float) GameState.INK_THRESHOLD;
            for (int j = 0; j < 6; j++) planes[(18 + j) * S + at] = scalars[j];
        }
        float membership = 1f / Math.max(1, s.pairCount);
        for (int pair = 0; pair < s.pairCount; pair++) {
            if (s.pairPath[pair] == null) continue;
            for (int i = 0; i < s.pairPathLen[pair]; i++) {
                int c = s.pairPath[pair][i];
                planes[14 * S + paddedCell(s, c)] += membership;
            }
        }
        for (int from = 0; from < s.townCount; from++) {
            int row = from * TOWN_FEATURES;
            townCells[from] = paddedCell(s, s.townCell[from]);
            towns[row] = townCells[from] / (float) (S - 1);
            for (int to = 0; to < s.townCount; to++) {
                if (from == to) continue;
                int flags = 0;
                for (int desired : s.desired[from]) if (desired == to) { flags |= 1; break; }
                int n = pathing.shortestPath(s, from, to, path);
                int own = 0, enemy = 0;
                if (n > 0) {
                    flags |= 2;
                    for (int j = 0; j < n; j++) {
                        if (s.owner[path[j]] == me) own++;
                        else if (s.owner[path[j]] == 1 - me) enemy++;
                    }
                }
                if (pathing.reachableAvoidingInk(s, s.townCell[from], s.townCell[to])) flags |= 4;
                int offset = row + 1 + to * 4;
                towns[offset] = flags / 7f;
                towns[offset + 1] = Math.max(0, n) / (float) S;
                towns[offset + 2] = own / (float) S;
                towns[offset + 3] = enemy / (float) S;
            }
        }
        for (int r = 0; r < s.regionCount; r++) disruptMask[r] = !s.regionInked[r] && !s.regionHasTown(r);
        disruptMask[STOP] = true;
        prefix(null, 0, GameState.PAINT_PER_TURN, 0, false);
    }

    /** Replaces the pending prefix; does not accumulate choices from earlier calls. */
    public void prefix(int[] paddedCells, int count, int paint, int stage, boolean stopped) {
        if (state == null) throw new IllegalStateException("prepare before prefix");
        if (stage < 0 || stage > 3 || count < 0 || count > stage || count > 3
                || count > 0 && (paddedCells == null || paddedCells.length < count))
            throw new IllegalArgumentException("invalid plan prefix");
        Arrays.fill(selected, false);
        int remaining = GameState.PAINT_PER_TURN;
        for (int i = 0; i < count; i++) {
            int p = paddedCells[i], c = nativeCell(p);
            if (selected[p] || !state.canBuild(c)) throw new IllegalArgumentException("illegal prefix cell " + p);
            remaining -= state.placeCost(c);
            selected[p] = true;
        }
        if (remaining < 0 || paint != remaining) throw new IllegalArgumentException("prefix paint mismatch");
        Arrays.fill(planes, 13 * S, 14 * S, 0);
        Arrays.fill(buildMask, false);
        scalars[1] = stage / 3f;
        scalars[2] = paint / (float) GameState.PAINT_PER_TURN;
        for (int c = 0; c < state.size; c++) {
            int p = paddedCell(state, c);
            planes[13 * S + p] = selected[p] ? 1 : 0;
            planes[19 * S + p] = scalars[1];
            planes[20 * S + p] = scalars[2];
            buildMask[p] = stage < 3 && !stopped && !selected[p] && state.canBuild(c)
                    && state.placeCost(c) <= paint;
        }
        buildMask[STOP] = true;
    }

    /** Converts only a real on-board padded cell; STOP/padding is rejected. */
    public int nativeCell(int padded) {
        if (padded < 0 || padded >= S || padded % W >= width || padded / W >= height)
            throw new IllegalArgumentException("not a real padded cell " + padded);
        return padded / W * width + padded % W;
    }

    public static int paddedCell(GameState s, int nativeCell) { return s.y(nativeCell) * W + s.x(nativeCell); }

    private static float signedLog(int value) {
        return (float) Math.copySign(Math.log1p(Math.abs((double) value)) / 10, value);
    }
}
