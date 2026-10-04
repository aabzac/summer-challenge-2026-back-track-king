package btk.core;

import java.util.Arrays;

/**
 * Resumable feature Dijkstra: paint cost, then cell index; strict relaxation in N/E/S/W order.
 * This is the policy's path convention, not the referee's AUTOPLACE tie-break.
 * One instance serves one unchanged board and blocking mask until {@link #reset()}.
 * Changing the source starts a fresh search automatically. No per-query allocations.
 */
public final class FeaturePaths {
    private static final int INF = Integer.MAX_VALUE / 4;
    private final int width, height;
    private final int[] costs, parent, heap, position;
    private int source = -1, count, pending = -1;

    public FeaturePaths(int width, int height) {
        this.width = width;
        this.height = height;
        int size = width * height;
        costs = new int[size];
        parent = new int[size];
        heap = new int[size];
        position = new int[size];
    }

    /** Required after every board or mask change, even if the source stays the same. */
    public void reset() {
        source = -1;
        count = 0;
        pending = -1;
    }

    /** Returns the route including both endpoints, or -1. The source may be blocked or inked. */
    public int path(GameState s, int from, int to, int[] out, boolean[] block) {
        if (source != from) {
            Arrays.fill(costs, INF);
            Arrays.fill(position, -1);
            source = from;
            count = 0;
            pending = -1;
            costs[from] = 0;
            parent[from] = -1;
            push(from);
        }
        if (position[to] != -2) {
            // The previous target was popped but not expanded. Its outgoing edges are still
            // needed, including when it was the last element in the heap.
            if (pending >= 0) {
                expand(s, pending, block);
                pending = -1;
            }
            while (count > 0) {
                int c = pop();
                if (c == to) {
                    pending = c;
                    break;
                }
                expand(s, c, block);
            }
        }
        if (costs[to] >= INF) return -1;
        int n = 0;
        for (int c = to; c != -1; c = parent[c]) out[n++] = c;
        for (int i = 0, j = n - 1; i < j; i++, j--) {
            int c = out[i]; out[i] = out[j]; out[j] = c;
        }
        return n;
    }

    /** Current tentative cost; reset followed by one query exactly matches a fresh search. */
    public int cost(int cell) { return costs[cell]; }

    private void expand(GameState s, int c, boolean[] block) {
        int x = c % width, y = c / width, base = costs[c];
        for (int d = 0; d < 4; d++) {
            int nx = x + GameState.DX[d], ny = y + GameState.DY[d];
            if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
            int nc = ny * width + nx;
            if (s.inked[nc] || (block != null && block[nc])) continue;
            int value = base + (s.passable(nc) ? 0 : s.placeCost(nc));
            if (value < costs[nc]) {
                costs[nc] = value;
                parent[nc] = c;
                if (position[nc] >= 0) up(position[nc]);
                else push(nc);
            }
        }
    }

    private boolean less(int a, int b) {
        return costs[a] < costs[b] || (costs[a] == costs[b] && a < b);
    }

    private void push(int c) {
        heap[count] = c;
        position[c] = count;
        up(count++);
    }

    private int pop() {
        int c = heap[0];
        position[c] = -2;
        if (--count > 0) {
            heap[0] = heap[count];
            position[heap[0]] = 0;
            down(0);
        }
        return c;
    }

    private void up(int i) {
        int c = heap[i];
        while (i > 0) {
            int p = (i - 1) >> 1;
            if (!less(c, heap[p])) break;
            heap[i] = heap[p];
            position[heap[i]] = i;
            i = p;
        }
        heap[i] = c;
        position[c] = i;
    }

    private void down(int i) {
        int c = heap[i];
        while (true) {
            int l = 2 * i + 1, r = l + 1, m = i, mc = c;
            if (l < count && less(heap[l], mc)) { m = l; mc = heap[l]; }
            if (r < count && less(heap[r], mc)) { m = r; mc = heap[r]; }
            if (m == i) break;
            heap[i] = mc;
            position[mc] = i;
            i = m;
        }
        heap[i] = c;
        position[c] = i;
    }
}
