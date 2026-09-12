package btk.referee;

import btk.core.GameState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Procedural map generator approximating CG maps: Voronoi-like contiguous regions, a few rivers
 * (random walks) and mountain blobs, one town per region with no two neighbouring regions both
 * holding a town, unilateral desired connections.
 *
 * <p>This is NOT the CG generator. For faithful benches prefer real maps from downloaded replays
 * (see {@code btk.tools.CgReplay#toState}). Tunables are conservative guesses from a Gold replay
 * (23x15, 49 regions, 6 towns).
 */
public final class MapGenerator {

    private MapGenerator() {}

    public static GameState generate(long seed) {
        Random rnd = new Random(seed);
        int w = 21 + rnd.nextInt(10);
        int h = 14 + rnd.nextInt(7);
        return generate(w, h, rnd);
    }

    public static GameState generate(int w, int h, Random rnd) {
        int size = w * h;
        int[] region = regions(w, h, rnd);
        int[] terrain = new int[size];
        rivers(w, h, terrain, rnd);
        mountains(w, h, terrain, rnd);

        int regionCount = Arrays.stream(region).max().orElse(0) + 1;
        boolean[][] adj = regionAdjacency(w, h, region, regionCount);

        int townCount = 4 + rnd.nextInt(9);
        List<Integer> townCells = new ArrayList<>();
        boolean[] regionUsed = new boolean[regionCount];
        Integer[] order = new Integer[regionCount];
        for (int i = 0; i < regionCount; i++) order[i] = i;
        shuffle(order, rnd);
        for (int r : order) {
            if (townCells.size() >= townCount) break;
            boolean blocked = false;
            for (int o = 0; o < regionCount && !blocked; o++) if (regionUsed[o] && adj[r][o]) blocked = true;
            if (blocked) continue;
            int cell = plainCellNearCentroid(w, h, region, terrain, r, rnd);
            if (cell < 0) continue;
            regionUsed[r] = true;
            townCells.add(cell);
        }
        townCount = townCells.size();
        int[] tx = new int[townCount], ty = new int[townCount];
        for (int t = 0; t < townCount; t++) {
            tx[t] = townCells.get(t) % w;
            ty[t] = townCells.get(t) / w;
        }
        int[][] desired = desires(townCount, rnd);
        return new GameState(w, h, region, terrain, tx, ty, desired);
    }

    private static int[] regions(int w, int h, Random rnd) {
        int size = w * h;
        int seeds = Math.max(8, size / (6 + rnd.nextInt(3)));
        int[] region = new int[size];
        Arrays.fill(region, -1);
        int[] queue = new int[size * 4];
        int head = 0, tail = 0;
        for (int i = 0; i < seeds; i++) {
            int c;
            do c = rnd.nextInt(size); while (region[c] >= 0);
            region[c] = i;
            queue[tail++] = c;
        }
        // randomized multi-source BFS gives irregular but contiguous regions
        while (head < tail) {
            int idx = head + rnd.nextInt(Math.min(4, tail - head));
            int c = queue[idx];
            queue[idx] = queue[head];
            head++;
            int x = c % w, y = c / w;
            for (int d = 0; d < 4; d++) {
                int nx = x + GameState.DX[d], ny = y + GameState.DY[d];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                int nc = ny * w + nx;
                if (region[nc] >= 0) continue;
                region[nc] = region[c];
                if (tail < queue.length) queue[tail++] = nc;
            }
        }
        for (int i = 0; i < size; i++) if (region[i] < 0) region[i] = 0;
        return region;
    }

    private static void rivers(int w, int h, int[] terrain, Random rnd) {
        int count = 1 + rnd.nextInt(2);
        for (int r = 0; r < count; r++) {
            boolean vertical = rnd.nextBoolean();
            int len = vertical ? h : w;
            int pos = vertical ? rnd.nextInt(w) : rnd.nextInt(h);
            for (int i = 0; i < len; i++) {
                int x = vertical ? pos : i, y = vertical ? i : pos;
                terrain[y * w + x] = GameState.RIVER;
                if (rnd.nextInt(3) == 0) {
                    pos += rnd.nextBoolean() ? 1 : -1;
                    pos = Math.max(0, Math.min((vertical ? w : h) - 1, pos));
                    int x2 = vertical ? pos : i, y2 = vertical ? i : pos;
                    terrain[y2 * w + x2] = GameState.RIVER;
                }
            }
        }
    }

    private static void mountains(int w, int h, int[] terrain, Random rnd) {
        int blobs = 2 + rnd.nextInt(4);
        for (int b = 0; b < blobs; b++) {
            int cx = rnd.nextInt(w), cy = rnd.nextInt(h);
            int cells = 3 + rnd.nextInt(8);
            int x = cx, y = cy;
            for (int i = 0; i < cells; i++) {
                if (x >= 0 && y >= 0 && x < w && y < h && terrain[y * w + x] == GameState.PLAINS) {
                    terrain[y * w + x] = GameState.MOUNTAIN;
                }
                int d = rnd.nextInt(4);
                x += GameState.DX[d];
                y += GameState.DY[d];
            }
        }
    }

    private static boolean[][] regionAdjacency(int w, int h, int[] region, int regionCount) {
        boolean[][] adj = new boolean[regionCount][regionCount];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int r = region[y * w + x];
                if (x + 1 < w) mark(adj, r, region[y * w + x + 1]);
                if (y + 1 < h) mark(adj, r, region[(y + 1) * w + x]);
            }
        }
        return adj;
    }

    private static void mark(boolean[][] adj, int a, int b) {
        if (a != b) {
            adj[a][b] = true;
            adj[b][a] = true;
        }
    }

    private static int plainCellNearCentroid(int w, int h, int[] region, int[] terrain, int r, Random rnd) {
        long sx = 0, sy = 0;
        int n = 0;
        for (int c = 0; c < region.length; c++) {
            if (region[c] != r) continue;
            sx += c % w;
            sy += c / w;
            n++;
        }
        if (n == 0) return -1;
        double cx = (double) sx / n, cy = (double) sy / n;
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int c = 0; c < region.length; c++) {
            if (region[c] != r || terrain[c] != GameState.PLAINS) continue;
            double d = Math.hypot(c % w - cx, c / w - cy) + rnd.nextDouble() * 0.5;
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    private static int[][] desires(int townCount, Random rnd) {
        List<List<Integer>> want = new ArrayList<>();
        boolean[] wanted = new boolean[townCount];
        for (int t = 0; t < townCount; t++) want.add(new ArrayList<>());
        for (int t = 0; t < townCount; t++) {
            int k = rnd.nextInt(Math.min(4, townCount));
            for (int i = 0; i < k; i++) {
                int o = rnd.nextInt(townCount);
                if (o == t || want.get(t).contains(o)) continue;
                want.get(t).add(o);
                wanted[o] = true;
            }
        }
        for (int t = 0; t < townCount; t++) {
            if (wanted[t]) continue;
            int from;
            do from = rnd.nextInt(townCount); while (from == t);
            want.get(from).add(t);
            wanted[t] = true;
        }
        int[][] out = new int[townCount][];
        for (int t = 0; t < townCount; t++) {
            List<Integer> l = want.get(t);
            l.sort(null);
            out[t] = l.stream().mapToInt(Integer::intValue).toArray();
        }
        return out;
    }

    private static <T> void shuffle(T[] a, Random rnd) {
        for (int i = a.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            T t = a[i];
            a[i] = a[j];
            a[j] = t;
        }
    }
}
