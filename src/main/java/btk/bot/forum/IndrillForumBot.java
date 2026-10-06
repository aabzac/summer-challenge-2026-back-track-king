package btk.bot.forum;

import btk.bot.Bot;
import btk.bot.Params;
import btk.bot.current.IndrillFeatures;
import btk.core.Action;
import btk.core.GameState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Hand readout of Indrill's published observation (forum post, rank 1, 2026-09-21).
 * Not his unpublished network, weights or training run.
 *
 * <p>The observation is the existing product of two entry-cost maps (plain, and plain plus
 * {@code 2 * instability}), three cell roles (best, good, path) and the five aggregates
 * (count, yield, stability, prospect, urgency). There is no search and no randomness.
 * Construction is an exact paint-3 knapsack over cell scores. Ink is the region whose
 * mean cell score is highest. Empty actions stay at zero and are skipped.
 */
public final class IndrillForumBot implements Bot {
    /** Frozen before any strength game. Good outranks best; the priority path outranks both. */
    static final float PATH_PROSPECT = 4f, GOOD_PROSPECT = 1.5f, BEST_PROSPECT = 0.25f;
    static final float RING0 = 3f, RING1 = 1.7f, RING_FAR = 0.35f;
    private final Params params;
    private IndrillFeatures features;
    private float[] buildQ;
    private int[] costs;
    private byte[] ring;
    public int selectedInk, selectedCount;
    public final int[] selectedCells = new int[3];

    public IndrillForumBot(Params params) { this.params = params.copy(); }

    public String name() { return "indrill-forum-v1"; }

    public void init(GameState s, int me) {
        features = new IndrillFeatures(s, params);
        buildQ = new float[s.size];
        costs = new int[s.size];
        ring = new byte[s.size];
    }

    public List<Action> play(GameState s, int me, long budgetMs) {
        if (budgetMs <= 0) return List.of(Action.waitAction());
        if (features == null) init(s, me);
        features.prepare(s, me);
        scoreBuild(s);
        selectedInk = params.USE_DISRUPT ? selectInk(s, me) : -1;
        if (selectedInk >= 0 && s.regionInstability[selectedInk] >= GameState.INK_THRESHOLD - 1) {
            for (int c = 0; c < s.size; c++) if (s.regionId[c] == selectedInk) costs[c] = 0;
        }
        selectedCount = selectBuild(buildQ, costs, selectedCells);
        List<Action> result = new ArrayList<>(4);
        for (int i = 0; i < selectedCount; i++) result.add(Action.place(s.x(selectedCells[i]), s.y(selectedCells[i])));
        if (selectedInk >= 0) result.add(Action.disrupt(selectedInk));
        if (result.isEmpty()) result.add(Action.waitAction());
        return result;
    }

    private void scoreBuild(GameState s) {
        Arrays.fill(ring, (byte) 2);
        for (int c = 0; c < s.size; c++) {
            costs[c] = features.buildCost[c];
            float base = cellBase(features.x[c]);
            buildQ[c] = base;
            if (base > 0 && costs[c] > 0 && touchesConnector(s, c)) ring[c] = 0;
        }
        for (int c = 0; c < s.size; c++) {
            if (ring[c] == 0 || buildQ[c] <= 0 || costs[c] == 0) continue;
            for (int d = 0; d < 4; d++) {
                int n = features.neighbors[c][d];
                if (n >= 0 && ring[n] == 0) { ring[c] = 1; break; }
            }
        }
        for (int c = 0; c < s.size; c++) {
            if (buildQ[c] <= 0 || costs[c] == 0) { buildQ[c] = 0; continue; }
            float weight = ring[c] == 0 ? RING0 : ring[c] == 1 ? RING1 : RING_FAR;
            buildQ[c] *= weight;
        }
    }

    /** Positive only for cells that sit on at least one suggested route. */
    static float cellBase(float[] f) {
        boolean path = f[44] > 0 || f[59] > 0;
        boolean good = f[39] > 0 || f[54] > 0;
        boolean best = f[34] > 0 || f[49] > 0;
        if (!path && !good && !best) return 0;
        float prospect = PATH_PROSPECT * (f[47] + f[62])
                + GOOD_PROSPECT * (f[42] + f[57])
                + BEST_PROSPECT * (f[37] + f[52]);
        if (prospect <= 0) return 0;
        float urgency = 1f;
        if (path) {
            if (f[44] > 0) urgency = f[48];
            if (f[59] > 0) urgency = f[44] > 0 ? Math.min(urgency, f[63]) : f[63];
        } else if (f[39] > 0 && f[54] > 0) urgency = Math.min(f[43], f[58]);
        else if (f[39] > 0) urgency = f[43];
        else if (f[54] > 0) urgency = f[58];
        return prospect * (1.2f - 0.45f * urgency);
    }

    private boolean touchesConnector(GameState s, int c) {
        for (int d = 0; d < 4; d++) {
            int n = features.neighbors[c][d];
            if (n >= 0 && s.passable(n) && !s.inked[n]) return true;
        }
        return false;
    }

    /**
     * Mean over the whole region. Opponent tracks and routes that stay theirs pull the
     * mean up; our own tracks pull it down. Started disruptions are amplified so a
     * four-hit commitment finishes when it is still favorable.
     */
    private int selectInk(GameState s, int me) {
        int best = -1;
        float bestQ = 0;
        for (int r = 0; r < s.regionCount; r++) {
            if (!features.legalInk[r] || features.regionSizes[r] == 0) continue;
            float sum = 0;
            for (int c = 0; c < s.size; c++) {
                if (s.regionId[c] != r || s.inked[c]) continue;
                float[] f = features.x[c];
                float signal = 0.35f * Math.max(0f, f[42] + f[47] + f[57] + f[62]);
                signal += Math.max(0f, -(f[40] + f[45] + f[55] + f[60]));
                int owner = s.owner[c];
                if (owner == 1 - me) signal += 1f;
                else if (owner == me) signal -= 0.5f;
                sum += signal;
            }
            float q = sum / features.regionSizes[r] * (1f + s.regionInstability[r]);
            if (q > bestQ) { bestQ = q; best = r; }
        }
        return best;
    }

    /** Exact budget-three maximum for an additive cell head. Same cases as the earlier Q prototype. */
    public static int selectBuild(float[] q, int[] costs, int[] selected) {
        int a = -1, b = -1, c = -1, river = -1, mountain = -1;
        for (int i = 0; i < q.length; i++) {
            if (costs[i] == 1) {
                if (a < 0 || q[i] > q[a]) { c = b; b = a; a = i; }
                else if (b < 0 || q[i] > q[b]) { c = b; b = i; }
                else if (c < 0 || q[i] > q[c]) c = i;
            } else if (costs[i] == 2 && (river < 0 || q[i] > q[river])) river = i;
            else if (costs[i] == 3 && (mountain < 0 || q[i] > q[mountain])) mountain = i;
        }
        Arrays.fill(selected, -1);
        float best = 0;
        int n = 0;
        if (a >= 0 && q[a] > 0) { selected[n++] = a; best += q[a]; }
        if (b >= 0 && q[b] > 0) { selected[n++] = b; best += q[b]; }
        if (c >= 0 && q[c] > 0) { selected[n++] = c; best += q[c]; }
        float mixed = river < 0 ? Float.NEGATIVE_INFINITY : q[river] + (a >= 0 ? Math.max(0, q[a]) : 0);
        if (mixed > best) {
            Arrays.fill(selected, -1); selected[0] = river; n = 1; best = mixed;
            if (a >= 0 && q[a] > 0) selected[n++] = a;
        }
        if (mountain >= 0 && q[mountain] > best) {
            Arrays.fill(selected, -1); selected[0] = mountain; n = 1;
        }
        if (n > 1 && selected[0] > selected[1]) { int t = selected[0]; selected[0] = selected[1]; selected[1] = t; }
        if (n > 2) {
            if (selected[1] > selected[2]) { int t = selected[1]; selected[1] = selected[2]; selected[2] = t; }
            if (selected[0] > selected[1]) { int t = selected[0]; selected[0] = selected[1]; selected[1] = t; }
        }
        return n;
    }
}
