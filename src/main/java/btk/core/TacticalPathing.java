package btk.core;

import java.util.Arrays;

/** Reusable tactical entry-cost maps; distinct from canonical referee Pathing. */
public final class TacticalPathing {
    public static final int INF = Integer.MAX_VALUE / 4;
    private final int size;
    private final int[] costBuf, dist, queue, parent, heap, heapPos, heapTie;

    public TacticalPathing(int size) {
        this.size = size;
        costBuf = new int[size]; dist = new int[size]; queue = new int[size];
        parent = new int[size]; heap = new int[size]; heapPos = new int[size]; heapTie = new int[size];
    }

    /**
     * All grid distances with caller-supplied nonnegative costs of entering each cell.
     * Inked cells are impassable; tracks and towns otherwise use the supplied cost just like
     * empty cells. The source starts at zero, without paying its own entry cost. Values at or
     * above {@link #INF} prohibit entry and sums saturate at INF. No storage is allocated.
     *
     * <p>With {@code reverse}, traverse the reversed directed graph: a step from c to its
     * neighbour pays entryCost[c], so the output at b is the original distance from b to
     * source. This distinction matters whenever adjacent cell entry costs differ.
     *
     * <p>This feature-distance API does not choose a referee path or change the canonical
     * shortest-path/Autoplace tie-break. The canonical Pathing buffers remain separate.
     */
    public void weightedDistances(GameState s, int source, int[] entryCost,
                                  boolean reverse, int[] out) {
        if (s.size != size || source < 0 || source >= size
                || entryCost.length < size || out.length < size || entryCost == out)
            throw new IllegalArgumentException("Invalid weighted-distance buffers/source");
        for (int c = 0; c < size; c++)
            if (entryCost[c] < 0) throw new IllegalArgumentException("Negative entry cost");
        Arrays.fill(costBuf, INF);
        Arrays.fill(heapPos, -1);
        Arrays.fill(heapTie, 0);
        if (!s.inked[source]) {
            costBuf[source] = 0;
            int heapSize = heapPush(0, source);
            while (heapSize > 0) {
                int c = heap[0];
                heapSize = heapPop(heapSize);
                int cx = c % s.width, cy = c / s.width;
                for (int dir = 0; dir < 4; dir++) {
                    int nx = cx + GameState.DX[dir], ny = cy + GameState.DY[dir];
                    if (nx < 0 || ny < 0 || nx >= s.width || ny >= s.height) continue;
                    int nc = ny * s.width + nx;
                    if (s.inked[nc]) continue;
                    int step = entryCost[reverse ? c : nc];
                    if (step >= INF || costBuf[c] >= INF - step) continue;
                    int candidate = costBuf[c] + step;
                    if (candidate >= costBuf[nc]) continue;
                    costBuf[nc] = candidate;
                    if (heapPos[nc] >= 0) heapSize = heapFix(heapSize, heapPos[nc]);
                    else heapSize = heapPush(heapSize, nc);
                }
            }
        }
        System.arraycopy(costBuf, 0, out, 0, size);
    }

    /**
     * One paint/risk-optimal simple route under {@link #weightedDistances} entry costs.
     * Searches optimal edges in N/E/S/W order; zero-cost connected cycles are visited once.
     * Returns the number of cells including both endpoints, or -1 if unreachable. This
     * tactical route does not replace the referee's canonical scored connection path.
     */
    public int weightedPath(GameState s, int from, int to, int[] entryCost, int[] out) {
        if (from < 0 || from >= size || out.length < size || out == entryCost)
            throw new IllegalArgumentException("Invalid weighted-path buffers/source");
        weightedDistances(s, to, entryCost, true, dist);
        if (dist[from] >= INF) return -1;
        Arrays.fill(parent, -2);
        int depth = 0;
        queue[0] = from;
        heapTie[0] = 0;
        parent[from] = -1;
        while (depth >= 0) {
            int c = queue[depth];
            if (c == to) {
                System.arraycopy(queue, 0, out, 0, depth + 1);
                return depth + 1;
            }
            if (heapTie[depth] >= 4) {
                depth--;
                continue;
            }
            int dir = heapTie[depth]++;
            int nx = c % s.width + GameState.DX[dir], ny = c / s.width + GameState.DY[dir];
            if (nx < 0 || ny < 0 || nx >= s.width || ny >= s.height) continue;
            int nc = ny * s.width + nx;
            if (s.inked[nc] || parent[nc] != -2 || dist[nc] >= INF) continue;
            int step = entryCost[nc];
            if (step >= INF || step > INF - dist[nc] || step + dist[nc] != dist[c]) continue;
            parent[nc] = c;
            queue[++depth] = nc;
            heapTie[depth] = 0;
        }
        return -1;
    }

    // ---- binary heap keyed like referee AbstractAStar: (cost, incoming direction) ----

    private boolean less(int a, int b) {
        return costBuf[a] < costBuf[b] || (costBuf[a] == costBuf[b] && heapTie[a] < heapTie[b]);
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
