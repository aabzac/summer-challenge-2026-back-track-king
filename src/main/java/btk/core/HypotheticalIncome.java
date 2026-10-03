package btk.core;

import java.util.Arrays;

/**
 * Exact income after hypothetical construction, without copying a board or materializing paths.
 * The canonical connection remains the shortest path with the requesting town's N/E/S/W
 * tie-break. One instance serves one fixed map and is not thread-safe; allocate it once.
 */
public final class HypotheticalIncome {
    private final int[] townCell, townAtCell, pairFrom, pairTo;
    private final int[] neighbors, destinations, firstPair, nextPair;
    private final int[] distance, queue, builtAt;
    private final int destinationCount;
    private int epoch;

    public HypotheticalIncome(GameState initial) {
        townCell = initial.townCell;
        townAtCell = initial.townAtCell;
        pairFrom = initial.pairFrom;
        pairTo = initial.pairTo;
        neighbors = new int[initial.size * 4];
        Arrays.fill(neighbors, -1);
        for (int c = 0; c < initial.size; c++) {
            int x = c % initial.width, y = c / initial.width;
            for (int direction = 0; direction < 4; direction++) {
                int nx = x + GameState.DX[direction], ny = y + GameState.DY[direction];
                if (initial.inBounds(nx, ny)) neighbors[c * 4 + direction] = initial.cell(nx, ny);
            }
        }
        destinations = new int[initial.townCount];
        firstPair = new int[initial.townCount];
        nextPair = new int[initial.pairCount];
        Arrays.fill(firstPair, -1);
        int count = 0;
        for (int p = 0; p < initial.pairCount; p++) {
            int destination = pairTo[p];
            if (firstPair[destination] < 0) destinations[count++] = destination;
            nextPair[p] = firstPair[destination];
            firstPair[destination] = p;
        }
        destinationCount = count;
        distance = new int[initial.size];
        queue = new int[initial.size];
        builtAt = new int[initial.size];
    }

    /**
     * Writes both players' exact total connection income after giving every currently buildable
     * cell in {@code route[0..n)} to {@code me}. Existing tracks, towns and inked empty cells are
     * unchanged, including when the route repeats them. All desired pairs contribute, not only
     * a pair whose completion motivated this route. The source and route are never modified.
     *
     * <p>The source must be the constructor's map (or a copy sharing its static arrays), with
     * player 0 or 1, valid route indices and an output array of length at least two. The output
     * must be caller-owned scratch, distinct from source arrays and the route. No allocation is
     * performed after construction. Like {@link GameState#passable}, existing tracks and towns
     * are connectors even in synthetic states with their ink flag set.
     */
    public void afterBuild(GameState source, int me, int[] route, int n, int[] outIncome) {
        if (source.townCell != townCell || source.townAtCell != townAtCell
                || source.pairFrom != pairFrom || source.pairTo != pairTo)
            throw new IllegalArgumentException("HypotheticalIncome requires the constructor's map");
        if (me < 0 || me > 1 || n < 0 || n > route.length || outIncome.length < 2)
            throw new IllegalArgumentException("Invalid construction income request");
        if (epoch == Integer.MAX_VALUE) {
            Arrays.fill(builtAt, 0);
            epoch = 1;
        } else {
            epoch++;
        }
        for (int i = 0; i < n; i++) {
            int c = route[i];
            if (source.canBuild(c)) builtAt[c] = epoch;
        }
        int income0 = 0, income1 = 0;
        for (int destinationIndex = 0; destinationIndex < destinationCount; destinationIndex++) {
            int destination = destinations[destinationIndex], end = townCell[destination];
            Arrays.fill(distance, -1);
            int head = 0, tail = 0;
            distance[end] = 0;
            queue[tail++] = end;
            while (head < tail) {
                int c = queue[head++], nextDistance = distance[c] + 1;
                int offset = c * 4;
                for (int direction = 0; direction < 4; direction++) {
                    int next = neighbors[offset + direction];
                    if (next < 0 || distance[next] >= 0) continue;
                    if (builtAt[next] != epoch && source.owner[next] == GameState.NO_TRACK
                            && townAtCell[next] < 0) continue;
                    distance[next] = nextDistance;
                    queue[tail++] = next;
                }
            }
            for (int p = firstPair[destination]; p >= 0; p = nextPair[p]) {
                int c = townCell[pairFrom[p]];
                // Pathing.walk treats a self-pair as inactive, even when the town has an owner.
                if (c == end || distance[c] < 0) continue;
                while (true) {
                    int owner = builtAt[c] == epoch ? me : source.owner[c];
                    if (owner == 0) income0++;
                    else if (owner == 1) income1++;
                    if (c == end) break;
                    int wanted = distance[c] - 1, offset = c * 4;
                    for (int direction = 0; direction < 4; direction++) {
                        int next = neighbors[offset + direction];
                        if (next >= 0 && distance[next] == wanted) {
                            c = next;
                            break;
                        }
                    }
                }
            }
        }
        outIncome[0] = income0;
        outIncome[1] = income1;
    }
}
