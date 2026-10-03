package btk.bot.current;

import btk.bot.Params;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.TacticalPathing;
import btk.core.Rules;
import btk.core.SearchState;
import java.util.Arrays;

/** Board observation inspired by Indrill's public post; no move valuation or search. */
public final class IndrillFeatures {
    public static final int INPUTS = 64;
    public final float[][] x;
    public final int[][] neighbors;
    public final int[] regionSizes;
    public final boolean[] legalInk;
    public final int[] buildCost;
    private final Pathing pathing;
    private final TacticalPathing tactical;
    private final SearchState work;
    private final int[][][] forward, backward;
    private final int[][] entry;
    private final int[] route, canonical, income = new int[2];
    private final boolean[] onPath;
    private final int inkPenalty;

    public IndrillFeatures(GameState s, Params p) {
        if (p.INDRILL_INK_PENALTY < 0 || p.INDRILL_INK_PENALTY > 100)
            throw new IllegalArgumentException("Invalid INDRILL_INK_PENALTY");
        inkPenalty = p.INDRILL_INK_PENALTY;
        x = new float[s.size][INPUTS];
        neighbors = new int[s.size][4];
        regionSizes = new int[s.regionCount];
        legalInk = new boolean[s.regionCount];
        buildCost = new int[s.size];
        forward = new int[2][s.townCount][s.size];
        backward = new int[2][s.townCount][s.size];
        entry = new int[2][s.size];
        route = new int[s.size];
        canonical = new int[s.size];
        onPath = new boolean[s.size];
        pathing = new Pathing(s.size);
        tactical = new TacticalPathing(s.size);
        work = new SearchState(s);
        for (int c = 0; c < s.size; c++) {
            regionSizes[s.regionId[c]]++;
            for (int d = 0; d < 4; d++) {
                int nx = s.x(c) + GameState.DX[d], ny = s.y(c) + GameState.DY[d];
                neighbors[c][d] = s.inBounds(nx, ny) ? s.cell(nx, ny) : -1;
            }
        }
    }

    public void prepare(GameState s, int me) {
        Rules.incomePerTurn(s, income);
        work.set(s);
        for (int r = 0; r < s.regionCount; r++) legalInk[r] = !s.regionInked[r] && !s.regionHasTown(r);
        for (int c = 0; c < s.size; c++) {
            float[] f = x[c];
            Arrays.fill(f, 0);
            int o = s.owner[c];
            f[0] = o == me ? 1 : 0;
            f[1] = o == 1 - me ? 1 : 0;
            f[2] = o == GameState.NEUTRAL ? 1 : 0;
            f[3] = o == GameState.NO_TRACK ? 1 : 0;
            f[4] = s.isTown(c) ? 1 : 0;
            f[5] = s.inked[c] ? 1 : 0;
            f[6 + s.terrain[c]] = 1;
            f[9] = s.placeCost(c) / 3f;
            f[10] = s.instability[c] / 4f;
            f[11] = s.regionHasTown(s.regionId[c]) ? 1 : 0;
            f[12] = s.x(c) / (float)Math.max(1, s.width - 1);
            f[13] = s.y(c) / (float)Math.max(1, s.height - 1);
            f[14] = s.width / 40f;
            f[15] = s.height / 40f;
            f[16] = s.turn / 100f;
            f[17] = s.turnsLeft() / 100f;
            f[18] = (float)Math.tanh((s.score[me] - s.score[1 - me]) / 200.0);
            f[19] = s.score[me] / 1000f;
            f[20] = s.score[1 - me] / 1000f;
            f[21] = income[me] / 32f;
            f[22] = income[1 - me] / 32f;
            for (int d = 0; d < 4; d++) {
                int n = neighbors[c][d];
                if (n < 0) { f[29 + d] = 1; continue; }
                f[23] += s.owner[n] == me ? .25f : 0;
                f[24] += s.owner[n] == 1 - me ? .25f : 0;
                f[25] += s.hasTrack(n) ? .25f : 0;
                f[26] += s.canBuild(n) ? .25f : 0;
                f[27] += s.isTown(n) ? .25f : 0;
                f[28] += !s.inked[n] ? .25f : 0;
                f[29 + d] = s.regionId[n] != s.regionId[c] ? 1 : 0;
            }
            f[33] = 1;
            for (int k = 0; k < 6; k++) { f[34 + k * 5 + 2] = 1; f[34 + k * 5 + 4] = 1; }
            buildCost[c] = s.canBuild(c) ? s.placeCost(c) : 0;
            entry[0][c] = s.hasTrack(c) || s.isTown(c) ? 0 : s.placeCost(c);
            entry[1][c] = entry[0][c] + (s.regionHasTown(s.regionId[c]) ? 0 : inkPenalty * s.instability[c]);
        }
        for (int mode = 0; mode < 2; mode++) {
            for (int t = 0; t < s.townCount; t++) {
                tactical.weightedDistances(s, s.townCell[t], entry[mode], false, forward[mode][t]);
                tactical.weightedDistances(s, s.townCell[t], entry[mode], true, backward[mode][t]);
            }
            for (int pair = 0; pair < s.pairCount; pair++) addPair(s, me, mode, pair);
        }
    }

    private void addPair(GameState s, int me, int mode, int pair) {
        int from = s.pairFrom[pair], to = s.pairTo[pair];
        int total = forward[mode][from][s.townCell[to]];
        if (total >= TacticalPathing.INF) return;
        int n = tactical.weightedPath(s, s.townCell[from], s.townCell[to], entry[mode], route);
        if (n < 0) return;
        Arrays.fill(onPath, false);
        int paint = 0, stable = GameState.INK_THRESHOLD;
        for (int i = 0; i < n; i++) {
            int c = route[i];
            onPath[c] = true;
            if (s.canBuild(c)) { paint += s.placeCost(c); work.state.owner[c] = me; }
            if (!s.regionHasTown(s.regionId[c])) stable = Math.min(stable, GameState.INK_THRESHOLD - s.instability[c]);
        }
        // Ask the core for the scored path after hypothetical completion, not the tactical path.
        int actual = pathing.shortestPath(work.state, from, to, canonical);
        work.state.pairPath[pair] = canonical;
        work.state.pairPathLen[pair] = Math.max(0, actual);
        int yield = SearchState.connectionIncomeDifference(work.state, pair, me);
        for (int i = 0; i < n; i++) work.state.owner[route[i]] = s.owner[route[i]];
        for (int c = 0; c < s.size; c++) {
            if (s.inked[c] || forward[mode][from][c] >= TacticalPathing.INF || backward[mode][to][c] >= TacticalPathing.INF) continue;
            int slack = forward[mode][from][c] + backward[mode][to][c] - total;
            if (slack == 0) aggregate(x[c], 34 + mode * 15, yield, stable, paint);
            if (slack <= entry[0][c]) aggregate(x[c], 39 + mode * 15, yield, stable, paint);
            if (onPath[c]) aggregate(x[c], 44 + mode * 15, yield, stable, paint);
        }
    }

    private static void aggregate(float[] f, int offset, int yield, int stability, int paint) {
        f[offset] += 1 / 16f;
        f[offset + 1] += yield / 64f;
        f[offset + 2] = Math.min(f[offset + 2], stability / 4f);
        f[offset + 3] += stability * yield / 256f;
        f[offset + 4] = Math.min(f[offset + 4], ((paint + 2) / 3) / 12f);
    }
}
