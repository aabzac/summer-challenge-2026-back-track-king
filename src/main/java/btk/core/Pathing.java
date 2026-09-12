package btk.core;

import java.util.Arrays;

/**
 * Path finding on the grid. One instance holds scratch buffers; not thread-safe
 * (create one per thread / per bot).
 */
public final class Pathing {

    private final int size;
    private final int[] dist;
    private final int[] queue;
    private final int[] costBuf;
    private final int[] parent;
    private final int[] heap;
    private final int[] heapPos;

    public Pathing(int size) {
        this.size = size;
        this.dist = new int[size];
        this.queue = new int[size];
        this.costBuf = new int[size];
        this.parent = new int[size];
        this.heap = new int[size];
        this.heapPos = new int[size];
    }

    /**
     * Shortest path over connector cells (tracks of any owner + towns) from town {@code fromTown}
     * to town {@code toTown}. Among equal-length paths picks the one whose direction sequence
     * (from the requesting town) is lexicographically first in the order N, E, S, W.
     *
     * @param out buffer of at least {@code size} ints; receives cells incl. both towns
     * @return path length in cells, or -1 when unreachable
     */
    public int shortestPath(GameState s, int fromTown, int toTown, int[] out) {
        int src = s.townCell[fromTown];
        int dst = s.townCell[toTown];
        if (src == dst) return -1;
        int reach = bfsFrom(s, dst);
        if (dist[src] < 0) return -1;
        int n = 0;
        int cur = src;
        out[n++] = cur;
        while (cur != dst) {
            int d = dist[cur];
            int cx = cur % s.width, cy = cur / s.width;
            int next = -1;
            for (int dir = 0; dir < 4; dir++) {
                int nx = cx + GameState.DX[dir], ny = cy + GameState.DY[dir];
                if (nx < 0 || ny < 0 || nx >= s.width || ny >= s.height) continue;
                int nc = ny * s.width + nx;
                if (dist[nc] == d - 1) {
                    next = nc;
                    break;
                }
            }
            if (next < 0) return -1;
            cur = next;
            out[n++] = cur;
        }
        return n;
    }

    /** BFS over passable cells from {@code start}; fills {@link #dist} (-1 = unreachable). Returns reached count. */
    private int bfsFrom(GameState s, int start) {
        return bfsInto(s, start, dist);
    }

    private int bfsInto(GameState s, int start, int[] d) {
        Arrays.fill(d, -1);
        int head = 0, tail = 0;
        d[start] = 0;
        queue[tail++] = start;
        while (head < tail) {
            int c = queue[head++];
            int nd = d[c] + 1;
            int cx = c % s.width, cy = c / s.width;
            for (int dir = 0; dir < 4; dir++) {
                int nx = cx + GameState.DX[dir], ny = cy + GameState.DY[dir];
                if (nx < 0 || ny < 0 || nx >= s.width || ny >= s.height) continue;
                int nc = ny * s.width + nx;
                if (d[nc] >= 0 || !s.passable(nc)) continue;
                d[nc] = nd;
                queue[tail++] = nc;
            }
        }
        return tail;
    }

    private int[][] townDist;

    /**
     * Recomputes every pair's active connection with one BFS per destination town instead of one
     * per pair. Same result as {@link #shortestPath} for each pair.
     */
    public void updateAllConnections(GameState s) {
        if (townDist == null || townDist.length < s.townCount) townDist = new int[s.townCount][size];
        boolean[] done = new boolean[s.townCount];
        for (int p = 0; p < s.pairCount; p++) {
            int to = s.pairTo[p];
            if (!done[to]) {
                bfsInto(s, s.townCell[to], townDist[to]);
                done[to] = true;
            }
            int n = walk(s, s.townCell[s.pairFrom[p]], s.townCell[to], townDist[to], parent);
            if (n < 0) {
                s.pairPath[p] = null;
                s.pairPathLen[p] = 0;
            } else {
                int[] path = s.pairPath[p];
                if (path == null || path.length < n) path = new int[Math.max(n, 16)];
                System.arraycopy(parent, 0, path, 0, n);
                s.pairPath[p] = path;
                s.pairPathLen[p] = n;
            }
        }
    }

    /** Greedy descent along {@code d} (distances to {@code dst}) choosing N, E, S, W first. */
    private int walk(GameState s, int src, int dst, int[] d, int[] out) {
        if (src == dst || d[src] < 0) return -1;
        int n = 0;
        int cur = src;
        out[n++] = cur;
        while (cur != dst) {
            int dd = d[cur];
            int cx = cur % s.width, cy = cur / s.width;
            int next = -1;
            for (int dir = 0; dir < 4; dir++) {
                int nx = cx + GameState.DX[dir], ny = cy + GameState.DY[dir];
                if (nx < 0 || ny < 0 || nx >= s.width || ny >= s.height) continue;
                int nc = ny * s.width + nx;
                if (d[nc] == dd - 1) {
                    next = nc;
                    break;
                }
            }
            if (next < 0) return -1;
            cur = next;
            out[n++] = cur;
        }
        return n;
    }

    /** Distances (in connector steps) from the last {@link #shortestPath} target; -1 unreachable. */
    public int[] lastDist() {
        return dist;
    }

    /** Flood fill over non-inked cells (tracks, towns and free cells alike). */
    public boolean reachableAvoidingInk(GameState s, int from, int to) {
        if (s.inked[from] || s.inked[to]) return false;
        Arrays.fill(dist, -1);
        int head = 0, tail = 0;
        dist[from] = 0;
        queue[tail++] = from;
        while (head < tail) {
            int c = queue[head++];
            if (c == to) return true;
            int cx = c % s.width, cy = c / s.width;
            for (int dir = 0; dir < 4; dir++) {
                int nx = cx + GameState.DX[dir], ny = cy + GameState.DY[dir];
                if (nx < 0 || ny < 0 || nx >= s.width || ny >= s.height) continue;
                int nc = ny * s.width + nx;
                if (dist[nc] >= 0 || s.inked[nc]) continue;
                dist[nc] = dist[c] + 1;
                queue[tail++] = nc;
            }
        }
        return false;
    }

    /**
     * Cheapest path in paint points from cell {@code from} to cell {@code to}. Existing tracks and
     * towns cost 0; free cells cost their terrain cost. Ties broken by fewer cells, then by
     * direction order N, E, S, W (deterministic).
     *
     * @param out receives cells incl. endpoints
     * @return path length in cells, or -1 if impossible
     */
    public int cheapestPath(GameState s, int from, int to, int[] out) {
        return cheapestPath(s, from, to, out, null);
    }

    /**
     * Same as {@link #cheapestPath(GameState, int, int, int[])} but cells with {@code blocked[c]}
     * set are treated as impassable (unless they already hold a track or a town).
     */
    public int cheapestPath(GameState s, int from, int to, int[] out, boolean[] blocked) {
        return cheapestPath(s, from, to, out, blocked, null);
    }

    /**
     * Cheapest path with an optional per-cell surcharge {@code extraCost} on free cells (used by
     * bots to route around unstable regions; the referee never passes it).
     */
    public int cheapestPath(GameState s, int from, int to, int[] out, boolean[] blocked, int[] extraCost) {
        final int INF = Integer.MAX_VALUE / 4;
        Arrays.fill(costBuf, INF);
        Arrays.fill(dist, INF);
        Arrays.fill(parent, -1);
        Arrays.fill(heapPos, -1);
        int heapSize = 0;
        costBuf[from] = 0;
        dist[from] = 0;
        heapSize = heapPush(heapSize, from);
        while (heapSize > 0) {
            int c = heap[0];
            heapSize = heapPop(heapSize);
            if (c == to) break;
            int cx = c % s.width, cy = c / s.width;
            for (int dir = 0; dir < 4; dir++) {
                int nx = cx + GameState.DX[dir], ny = cy + GameState.DY[dir];
                if (nx < 0 || ny < 0 || nx >= s.width || ny >= s.height) continue;
                int nc = ny * s.width + nx;
                int step;
                if (s.passable(nc)) {
                    if (blocked != null && blocked[nc]) continue;
                    step = 0;
                } else {
                    if (s.inked[nc] || (blocked != null && blocked[nc])) continue;
                    step = s.placeCost(nc) + (extraCost == null ? 0 : extraCost[nc]);
                }
                int nCost = costBuf[c] + step;
                int nLen = dist[c] + 1;
                if (nCost < costBuf[nc] || (nCost == costBuf[nc] && nLen < dist[nc])) {
                    costBuf[nc] = nCost;
                    dist[nc] = nLen;
                    parent[nc] = c;
                    if (heapPos[nc] >= 0) heapSize = heapFix(heapSize, heapPos[nc]);
                    else heapSize = heapPush(heapSize, nc);
                }
            }
        }
        if (costBuf[to] >= INF) return -1;
        int n = 0;
        for (int c = to; c != -1; c = parent[c]) out[n++] = c;
        for (int i = 0, j = n - 1; i < j; i++, j--) {
            int t = out[i];
            out[i] = out[j];
            out[j] = t;
        }
        return n;
    }

    /** Paint cost of the last {@link #cheapestPath} result to any cell. */
    public int lastCost(int cell) {
        return costBuf[cell];
    }

    // ---- binary heap keyed by (costBuf, dist) ----

    private boolean less(int a, int b) {
        return costBuf[a] < costBuf[b] || (costBuf[a] == costBuf[b] && dist[a] < dist[b]);
    }

    private int heapPush(int n, int c) {
        heap[n] = c;
        heapPos[c] = n;
        siftUp(n);
        return n + 1;
    }

    private int heapPop(int n) {
        heapPos[heap[0]] = -1;
        n--;
        if (n > 0) {
            heap[0] = heap[n];
            heapPos[heap[0]] = 0;
            siftDown(0, n);
        }
        return n;
    }

    private int heapFix(int n, int pos) {
        siftUp(pos);
        return n;
    }

    private void siftUp(int i) {
        int c = heap[i];
        while (i > 0) {
            int p = (i - 1) >> 1;
            if (!less(c, heap[p])) break;
            heap[i] = heap[p];
            heapPos[heap[i]] = i;
            i = p;
        }
        heap[i] = c;
        heapPos[c] = i;
    }

    private void siftDown(int i, int n) {
        int c = heap[i];
        while (true) {
            int l = 2 * i + 1, r = l + 1, m = i;
            int mc = c;
            if (l < n && less(heap[l], mc)) {
                m = l;
                mc = heap[l];
            }
            if (r < n && less(heap[r], mc)) {
                m = r;
                mc = heap[r];
            }
            if (m == i) break;
            heap[i] = mc;
            heapPos[mc] = i;
            i = m;
        }
        heap[i] = c;
        heapPos[c] = i;
    }
}
