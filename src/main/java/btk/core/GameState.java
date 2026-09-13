package btk.core;

import java.util.Arrays;

/**
 * Complete game state. Static map data (arrays marked "static") is shared between copies;
 * dynamic arrays are copied. Everything is primitive so that search code can copy/restore
 * states without allocation churn.
 *
 * <p>Cell index = y * width + x.
 */
public final class GameState {

    public static final int PLAINS = 0, RIVER = 1, MOUNTAIN = 2;
    public static final int NO_TRACK = -1, NEUTRAL = 2;
    public static final int[] COST = {1, 2, 3};
    public static final int PAINT_PER_TURN = 3;
    /** Inferred from 107 Silver replays: a region is inked when its instability reaches this. */
    public static final int INK_THRESHOLD = 4;
    /** Wood statement: 100 turns; games end earlier when no desired pair can be connected any more. */
    public static final int DEFAULT_MAX_TURNS = 100;

    /** Direction order matters: it is the shortest-path tie-break priority (N, E, S, W). */
    public static final int[] DX = {0, 1, 0, -1};
    public static final int[] DY = {-1, 0, 1, 0};

    // ---- static map data ----
    public final int width, height, size;
    public final int[] regionId;
    public final int[] terrain;
    public final int regionCount;
    public final int townCount;
    public final int[] townX, townY, townCell;
    public final int[][] desired;
    /** townAtCell[cell] = town id or -1. */
    public final int[] townAtCell;
    /** Desired connections flattened: pair p = (pairFrom[p] wants pairTo[p]). */
    public final int pairCount;
    public final int[] pairFrom, pairTo;

    // ---- dynamic ----
    public final int[] owner;
    /** Per cell as in the protocol; equals the region's instability. */
    public final int[] instability;
    /** Per cell; inked cells hold no tracks, cannot be built on and stay inked forever. */
    public final boolean[] inked;
    public final int[] regionInstability;
    public final boolean[] regionInked;
    public final int[] score = new int[2];
    public int turn;
    public int maxTurns = DEFAULT_MAX_TURNS;

    // ---- derived (see Rules.updateConnections) ----
    /** pairPath[p] = cells of the active connection incl. both towns, or null when inactive. */
    public final int[][] pairPath;
    public final int[] pairPathLen;

    public GameState(int width, int height, int[] regionId, int[] terrain,
                     int[] townX, int[] townY, int[][] desired) {
        this.width = width;
        this.height = height;
        this.size = width * height;
        this.regionId = regionId;
        this.terrain = terrain;
        int rc = 0;
        for (int r : regionId) rc = Math.max(rc, r + 1);
        this.regionCount = rc;
        this.townCount = townX.length;
        this.townX = townX;
        this.townY = townY;
        this.desired = desired;
        this.townCell = new int[townCount];
        this.townAtCell = new int[size];
        Arrays.fill(townAtCell, -1);
        for (int t = 0; t < townCount; t++) {
            townCell[t] = townY[t] * width + townX[t];
            townAtCell[townCell[t]] = t;
        }
        int pc = 0;
        for (int[] d : desired) pc += d.length;
        this.pairCount = pc;
        this.pairFrom = new int[pc];
        this.pairTo = new int[pc];
        int p = 0;
        for (int t = 0; t < townCount; t++) {
            for (int d : desired[t]) {
                pairFrom[p] = t;
                pairTo[p] = d;
                p++;
            }
        }
        this.owner = new int[size];
        Arrays.fill(owner, NO_TRACK);
        this.instability = new int[size];
        this.inked = new boolean[size];
        this.regionInstability = new int[regionCount];
        this.regionInked = new boolean[regionCount];
        this.pairPath = new int[pc][];
        this.pairPathLen = new int[pc];
    }

    private GameState(GameState o) {
        this.width = o.width;
        this.height = o.height;
        this.size = o.size;
        this.regionId = o.regionId;
        this.terrain = o.terrain;
        this.regionCount = o.regionCount;
        this.townCount = o.townCount;
        this.townX = o.townX;
        this.townY = o.townY;
        this.townCell = o.townCell;
        this.desired = o.desired;
        this.townAtCell = o.townAtCell;
        this.pairCount = o.pairCount;
        this.pairFrom = o.pairFrom;
        this.pairTo = o.pairTo;
        this.owner = o.owner.clone();
        this.instability = o.instability.clone();
        this.inked = o.inked.clone();
        this.regionInstability = o.regionInstability.clone();
        this.regionInked = o.regionInked.clone();
        this.score[0] = o.score[0];
        this.score[1] = o.score[1];
        this.turn = o.turn;
        this.maxTurns = o.maxTurns;
        this.pairPath = new int[o.pairCount][];
        this.pairPathLen = o.pairPathLen.clone();
        for (int p = 0; p < o.pairCount; p++) {
            pairPath[p] = o.pairPath[p] == null ? null : o.pairPath[p].clone();
        }
    }

    public GameState copy() {
        return new GameState(this);
    }

    /** Overwrites dynamic data from {@code o} (same map). Allocation-free except paths. */
    public void copyFrom(GameState o) {
        System.arraycopy(o.owner, 0, owner, 0, size);
        System.arraycopy(o.instability, 0, instability, 0, size);
        System.arraycopy(o.inked, 0, inked, 0, size);
        System.arraycopy(o.regionInstability, 0, regionInstability, 0, regionCount);
        System.arraycopy(o.regionInked, 0, regionInked, 0, regionCount);
        score[0] = o.score[0];
        score[1] = o.score[1];
        turn = o.turn;
        maxTurns = o.maxTurns;
        for (int p = 0; p < pairCount; p++) {
            pairPathLen[p] = o.pairPathLen[p];
            if (o.pairPath[p] == null) {
                pairPath[p] = null;
            } else {
                if (pairPath[p] == null || pairPath[p].length < o.pairPathLen[p]) {
                    pairPath[p] = o.pairPath[p].clone();
                } else {
                    System.arraycopy(o.pairPath[p], 0, pairPath[p], 0, o.pairPathLen[p]);
                }
            }
        }
    }

    public int cell(int x, int y) {
        return y * width + x;
    }

    public int x(int cell) {
        return cell % width;
    }

    public int y(int cell) {
        return cell / width;
    }

    public boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    public boolean isTown(int cell) {
        return townAtCell[cell] >= 0;
    }

    public boolean hasTrack(int cell) {
        return owner[cell] != NO_TRACK;
    }

    /** A cell counts as connector when it holds a track (any owner) or a town. */
    public boolean passable(int cell) {
        return owner[cell] != NO_TRACK || townAtCell[cell] >= 0;
    }

    public int placeCost(int cell) {
        return COST[terrain[cell]];
    }

    /** Free, buildable cell: no town, no track, not inked. */
    public boolean canBuild(int cell) {
        return owner[cell] == NO_TRACK && townAtCell[cell] < 0 && !inked[cell];
    }

    public boolean regionHasTown(int region) {
        for (int t = 0; t < townCount; t++) if (regionId[townCell[t]] == region) return true;
        return false;
    }

    /** Derives region-level ink/instability from the per-cell protocol arrays (after readTurn). */
    public void syncRegionsFromCells() {
        java.util.Arrays.fill(regionInked, false);
        java.util.Arrays.fill(regionInstability, 0);
        for (int c = 0; c < size; c++) {
            int r = regionId[c];
            if (inked[c]) regionInked[r] = true;
            regionInstability[r] = Math.max(regionInstability[r], instability[c]);
        }
    }

    public boolean pairActive(int p) {
        return pairPath[p] != null;
    }

    public int turnsLeft() {
        return maxTurns - turn;
    }

    public String pairName(int p) {
        return pairFrom[p] + "-" + pairTo[p];
    }
}
