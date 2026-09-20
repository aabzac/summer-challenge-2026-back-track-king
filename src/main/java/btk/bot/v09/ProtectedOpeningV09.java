package btk.bot.v09;

import btk.bot.Params;
import btk.core.GameState;
import java.util.Arrays;

/**
 * Opening ownership claims from the canonical route queries already made by RolloutBotV09.
 * This is a traffic heuristic, not an alternate path finder or income calculation.
 * The caller observes its variant-zero core paths before ordinary route filtering.
 */
public final class ProtectedOpeningV09 {
    private final boolean enabled;
    private final int turns, cityQuota;
    private final double collisionFactor;
    private final double[] traffic;
    private final boolean[] pairSeen, collision, selected;
    private final int[] adjacentTowns, adjacentCount, cityUsed;
    private int me;
    private boolean active;

    public int observedPairs, eligibleCells, selectedPaint;

    ProtectedOpeningV09(GameState s, Params p) {
        if (p.OPENING_TURNS < 1 || p.OPENING_TURNS > 3
                || p.OPENING_CITY_QUOTA < 1 || p.OPENING_CITY_QUOTA > GameState.PAINT_PER_TURN
                || !Double.isFinite(p.OPENING_COLLISION_FACTOR)
                || p.OPENING_COLLISION_FACTOR < 0 || p.OPENING_COLLISION_FACTOR > 1) {
            throw new IllegalArgumentException("Opening requires bounded turns, city quota and collision factor");
        }
        enabled = p.USE_PROTECTED_OPENING;
        turns = p.OPENING_TURNS;
        cityQuota = p.OPENING_CITY_QUOTA;
        collisionFactor = p.OPENING_COLLISION_FACTOR;
        traffic = new double[s.size];
        pairSeen = new boolean[s.townCount * s.townCount];
        collision = new boolean[s.size];
        selected = new boolean[s.size];
        adjacentTowns = new int[4 * s.size];
        adjacentCount = new int[s.size];
        cityUsed = new int[s.townCount];
        // Protection is map-static. At most four towns can touch one grid cell.
        for (int c = 0; c < s.size; c++) {
            if (s.isTown(c) || !s.regionHasTown(s.regionId[c])) continue;
            int x = s.x(c), y = s.y(c);
            for (int d = 0; d < 4; d++) {
                int nx = x + GameState.DX[d], ny = y + GameState.DY[d];
                if (!s.inBounds(nx, ny)) continue;
                int town = s.townAtCell[s.cell(nx, ny)];
                if (town >= 0) adjacentTowns[4 * c + adjacentCount[c]++] = town;
            }
        }
    }

    /** Clear prior observations even after the fixed opening window ends. */
    void reset(GameState s, int player) {
        me = player;
        active = enabled && s.turn >= 0 && s.turn < turns;
        Arrays.fill(traffic, 0);
        Arrays.fill(pairSeen, false);
        Arrays.fill(collision, false);
        Arrays.fill(selected, false);
        Arrays.fill(cityUsed, 0);
        observedPairs = eligibleCells = selectedPaint = 0;
    }

    /**
     * Observe one core-generated path for a desired directed pair, before shortlist truncation.
     * A second geometry or a reversed copy of this same pair cannot add demand twice.
     * A separately desired reverse pair is real additional scoring traffic and is counted.
     */
    void observe(GameState s, int pair, int[] path, int length) {
        if (!active || length <= 0) return;
        int key = s.pairFrom[pair] * s.townCount + s.pairTo[pair];
        if (pairSeen[key]) return;
        pairSeen[key] = true;
        observedPairs++;
        int remainingPaint = 0;
        for (int i = 0; i < length; i++) {
            int c = path[i];
            if (s.canBuild(c)) remainingPaint += s.placeCost(c);
        }
        if (remainingPaint == 0) return;
        double demand = 1.0 / (1.0 + remainingPaint);
        for (int i = 0; i < length; i++) {
            int c = path[i];
            if (adjacentCount[c] == 0 || !s.canBuild(c)) continue;
            if (traffic[c] == 0) eligibleCells++;
            traffic[c] += demand;
        }
    }

    /** Discount the known-board model's affordable opponent prefix, without future replay data. */
    void markOpponentPrefix(int[] cells, int count) {
        if (!active) return;
        for (int i = 0; i < count; i++) collision[cells[i]] = true;
    }

    /**
     * Produce an affordable fixed opening bundle; zero means use ordinary v07 construction.
     * Greedy score is summed pair demand / cell paint, with the configured collision discount.
     * Every adjacent city is charged against the quota; ties prefer cheaper cells, then
     * opposite cell ordering for the two seats to avoid deterministic symmetric collisions.
     */
    int build(GameState s, int[] out) {
        Arrays.fill(selected, false);
        Arrays.fill(cityUsed, 0);
        selectedPaint = 0;
        if (!active || s.turn < 0 || s.turn >= turns) return 0;
        int count = 0;
        while (count < GameState.PAINT_PER_TURN && selectedPaint < GameState.PAINT_PER_TURN) {
            int best = -1, bestCost = Integer.MAX_VALUE;
            double bestValue = 0;
            for (int c = 0; c < s.size; c++) {
                if (traffic[c] <= 0 || selected[c] || !s.canBuild(c)) continue;
                int cost = s.placeCost(c);
                if (cost > GameState.PAINT_PER_TURN - selectedPaint || !hasCityQuota(c)) continue;
                double value = traffic[c] / cost * (collision[c] ? collisionFactor : 1.0);
                if (value > bestValue || value > 0 && value == bestValue
                        && (cost < bestCost || cost == bestCost && (best < 0 || (me == 0 ? c < best : c > best)))) {
                    best = c;
                    bestCost = cost;
                    bestValue = value;
                }
            }
            if (best < 0) break;
            out[count++] = best;
            selected[best] = true;
            selectedPaint += bestCost;
            for (int i = 0; i < adjacentCount[best]; i++) cityUsed[adjacentTowns[4 * best + i]]++;
        }
        return count;
    }

    private boolean hasCityQuota(int cell) {
        for (int i = 0; i < adjacentCount[cell]; i++)
            if (cityUsed[adjacentTowns[4 * cell + i]] >= cityQuota) return false;
        return true;
    }
}
