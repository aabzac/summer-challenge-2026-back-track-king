package btk.bot.current;

import btk.bot.Bot;
import btk.bot.Params;
import btk.bot.Timer;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.core.SearchState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** Independent portfolio rollouts: score affordable, simultaneous turns instead of a finished route. */
public final class RolloutBot implements Bot {
    private final Params p;
    private final Timer timer = new Timer();
    private Pathing pathing;
    private SearchState work, completion, targetBoard, inkBoard;
    private int[][][] routes;
    private int[][] lengths;
    private double[][] values;
    private final int[] counts = new int[2], income = new int[2];
    private final int[] baseDiff = new int[2], threatDiff = new int[2], threats = new int[2];
    private final int[][] moves = new int[2][GameState.PAINT_PER_TURN];
    private final int[] moveCounts = new int[2], targets = new int[2];
    private int[] path, extra, trackBalance, shortlist, bestCells, prefix;
    private boolean[] blocked, marked;
    private double[] priority;
    private int[][] roots;
    private int[] rootCounts, rootTargets;
    private Random random;
    private int[] quickPath;
    private int quickPlayer, quickLength, quickCost, quickGain, quickSlot;
    private double quickValue;
    public int routeQueries, completedComparisons, simulatedTurns, selectedRoute, selectedTarget;
    public double selectedValue;

    public RolloutBot(Params params) {
        p = params.copy();
        if (p.USE_HEURISTIC_POLICY || p.USE_OWN_BUILD_PLAN || p.USE_REPLY_RERANK || p.USE_JOINT_ACTION
                || p.USE_DEFENSE_PRIORITY || p.USE_OPP_MODEL || p.GUARD_BUILD_INCOME
                || p.CHOOSE_BUILD_DIRECTION || p.EARLY_DISRUPT_FALLBACK || p.INTERRUPT_VULN_EVAL
                || p.ROLLOUT_WIDTH < 1 || p.ROLLOUT_WIDTH > 8 || p.ROLLOUT_REPLIES < 1
                || p.ROLLOUT_REPLIES > p.ROLLOUT_WIDTH || p.ROLLOUT_DEPTH < 1 || p.ROLLOUT_DEPTH > 12
                || p.ROLLOUT_MAX_ROUTES < 1 || p.ROLLOUT_MAX_ROUTES > 128
                || p.ROLLOUT_QUICK_MAX_PAINT < 1 || p.ROLLOUT_QUICK_MAX_PAINT > 12
                || p.ROLLOUT_RESERVE_MS < 0 || p.ROLLOUT_TARGETS < 1 || p.ROLLOUT_TARGETS > 8
                || !finitePositive(p.ROLLOUT_TARGET_HORIZON) || !finitePositive(p.ROLLOUT_ROUTE_HORIZON)
                || !finitePositive(p.ROLLOUT_WIN_VALUE)
                || !unit(p.ROLLOUT_ROBUSTNESS) || !unit(p.ROLLOUT_THREAT_WEIGHT)
                || !Double.isFinite(p.ROLLOUT_TAIL) || p.ROLLOUT_TAIL < 0
                || !Double.isFinite(p.ROLLOUT_TRACK_WEIGHT) || p.ROLLOUT_TRACK_WEIGHT < 0) {
            throw new IllegalArgumentException("Rollout requires an independent policy and bounded valid parameters");
        }
    }

    private static boolean finitePositive(double v) { return Double.isFinite(v) && v > 0; }
    private static boolean unit(double v) { return Double.isFinite(v) && v >= 0 && v <= 1; }
    public String name() { return "current?" + p.diff(); }

    public void init(GameState s, int me) {
        pathing = new Pathing(s.size);
        work = new SearchState(s); completion = new SearchState(s);
        targetBoard = new SearchState(s); inkBoard = new SearchState(s);
        routes = new int[2][p.ROLLOUT_WIDTH][s.size];
        lengths = new int[2][p.ROLLOUT_WIDTH]; values = new double[2][p.ROLLOUT_WIDTH];
        path = new int[s.size]; extra = new int[s.size]; blocked = new boolean[s.size];
        trackBalance = new int[s.regionCount]; marked = new boolean[s.regionCount];
        priority = new double[s.regionCount]; shortlist = new int[p.ROLLOUT_TARGETS];
        roots = new int[p.ROLLOUT_WIDTH + 1][GameState.PAINT_PER_TURN];
        rootCounts = new int[roots.length]; rootTargets = new int[roots.length];
        prefix = new int[GameState.PAINT_PER_TURN]; bestCells = new int[GameState.PAINT_PER_TURN];
        random = new Random(p.SEED * 1_000_003L + me * 7919L);
        if (p.USE_ROLLOUT_QUICK_ROUTE) quickPath = new int[s.size];
    }

    public List<Action> play(GameState s, int me, long budgetMs) {
        timer.start(budgetMs);
        routeQueries = completedComparisons = simulatedTurns = 0;
        selectedRoute = selectedTarget = -1; selectedValue = Double.NaN;
        quickPlayer = -1; quickLength = quickCost = quickGain = 0;
        quickSlot = -1;
        quickValue = 0;
        if (budgetMs <= 0) return List.of(Action.waitAction());
        Arrays.fill(counts, 0);
        // A usable sabotage answer exists before route enumeration and all speculative work.
        int fallbackTarget = chooseTarget(s, me);
        threats[1 - me] = fallbackTarget;
        threats[me] = chooseTarget(s, 1 - me);
        Rules.incomePerTurn(s, income);
        if (p.USE_ROLLOUT_QUICK_ROUTE && income[me] == 0) quickPlayer = me;
        for (int player = 0; player < 2; player++) {
            baseDiff[player] = income[player] - income[1 - player];
            threatDiff[player] = baseDiff[player];
            if (threats[player] >= 0) {
                completion.set(s); Rules.ink(completion.state, threats[player]); completion.refresh();
                Rules.incomePerTurn(completion.state, income);
                threatDiff[player] = income[player] - income[1 - player];
                Rules.incomePerTurn(s, income);
            }
        }
        // Interleave both players so a deadline cannot spend the whole budget on one portfolio.
        int start = s.pairCount == 0 ? 0 : (s.turn * 7 + me * 13) % s.pairCount;
        int reserve = (int) Math.min(p.ROLLOUT_RESERVE_MS, budgetMs / 2);
        for (int k = 0; k < s.pairCount && routeQueries < 2 * p.ROLLOUT_MAX_ROUTES; k++) {
            int pair = (start + k) % s.pairCount;
            for (int variant = 0; variant < 3; variant++) {
                if (timer.up(reserve) || routeQueries >= 2 * p.ROLLOUT_MAX_ROUTES) break;
                propose(s, me, pair, variant);
                if (!timer.up(reserve)) propose(s, 1 - me, pair, variant);
            }
            if (timer.up(reserve)) break;
        }
        if (quickLength > 0) retainQuickRoute(s, me);
        int rootSize = counts[me] + 1; // last choice waits for construction this turn only
        for (int i = 0; i < rootSize; i++) {
            rootCounts[i] = i == counts[me] ? 0 : fillBuild(s, me, i, roots[i]);
            targetBoard.set(s);
            placeOwn(targetBoard, me, roots[i], rootCounts[i]);
            rootTargets[i] = timer.up() ? fallbackTarget : chooseTarget(targetBoard.state, me);
        }
        int bestCount = rootCounts[0];
        System.arraycopy(roots[0], 0, bestCells, 0, bestCount);
        selectedTarget = rootTargets[0];
        selectedRoute = counts[me] == 0 ? -1 : 0;
        double best = -Double.MAX_VALUE;
        int replies = Math.max(1, Math.min(p.ROLLOUT_REPLIES, counts[1 - me]));
        search:
        for (int order = 0; order < rootSize; order++) {
            int root = searchRoot(order);
            boolean duplicate = false;
            for (int j = 0; j < root; j++) {
                if (sameSet(roots[root], rootCounts[root], roots[j], rootCounts[j])) duplicate = true;
            }
            if (duplicate) continue;
            for (int attack = 0; attack < 2; attack++) {
                int target = attack == 0 ? rootTargets[root] : -1;
                if (attack == 1 && rootTargets[root] < 0) continue;
                double sum = 0, worst = Double.MAX_VALUE;
                for (int reply = 0; reply < replies; reply++) {
                    double value = rollout(s, me, root, target, reply);
                    if (!Double.isFinite(value)) break search; // discard ALL partial scenario coverage
                    sum += value; worst = Math.min(worst, value);
                }
                completedComparisons++;
                double value = (1 - p.ROLLOUT_ROBUSTNESS) * sum / replies + p.ROLLOUT_ROBUSTNESS * worst;
                if (value > best) {
                    best = value; selectedValue = value;
                    selectedRoute = root == counts[me] ? -1 : root; selectedTarget = target;
                    bestCount = rootCounts[root];
                    System.arraycopy(roots[root], 0, bestCells, 0, bestCount);
                }
            }
        }
        List<Action> actions = new ArrayList<>(4);
        for (int i = 0; i < bestCount; i++) actions.add(Action.place(s.x(bestCells[i]), s.y(bestCells[i])));
        if (selectedTarget >= 0) actions.add(Action.disrupt(selectedTarget));
        if (actions.isEmpty()) actions.add(Action.waitAction());
        return actions;
    }

    private void propose(GameState s, int me, int pair, int variant) {
        if (variant == 2 && threats[me] < 0) return;
        for (int c = 0; c < s.size; c++) {
            extra[c] = (int) Math.round(p.UNSTABLE_COST * s.instability[c]);
            blocked[c] = variant == 1 ? s.owner[c] == 1 - me
                    : variant == 2 && s.regionId[c] == threats[me];
        }
        int n = pathing.cheapestPath(s, s.townCell[s.pairFrom[pair]], s.townCell[s.pairTo[pair]],
                path, variant == 0 ? null : blocked, extra);
        routeQueries++;
        if (n <= 0) return;
        completion.set(s); int cost = 0;
        for (int i = 0; i < n; i++) if (s.canBuild(path[i])) {
            cost += s.placeCost(path[i]); completion.state.owner[path[i]] = me;
        }
        if (cost == 0) return;
        int turns = (cost + GameState.PAINT_PER_TURN - 1) / GameState.PAINT_PER_TURN;
        double earning = Math.min(p.ROLLOUT_ROUTE_HORIZON, s.turnsLeft()) - turns + 1;
        if (earning <= 0) return;
        completion.refresh(); Rules.incomePerTurn(completion.state, income);
        double gain = income[me] - income[1 - me] - baseDiff[me];
        // Reuse the bounded proposal pool: no extra path query, simulation or random draw.
        // Current own income is zero, so a positive completed own income is also a gain.
        if (me == quickPlayer && cost <= p.ROLLOUT_QUICK_MAX_PAINT && income[me] > 0 && gain > 0)
            considerQuick(s, me, n, cost, gain);
        if (threats[me] >= 0) {
            Rules.ink(completion.state, threats[me]); completion.refresh();
            Rules.incomePerTurn(completion.state, income);
            double protectedGain = income[me] - income[1 - me] - threatDiff[me];
            gain = (1 - p.ROLLOUT_THREAT_WEIGHT) * gain + p.ROLLOUT_THREAT_WEIGHT * protectedGain;
        }
        if (gain <= 0) return;
        double value = gain * earning / turns * (1 + p.NOISE * (random.nextDouble() - 0.5));
        insert(s, me, n, value);
        // Different ends of a route are genuinely different affordable first actions.
        for (int i = 0, j = n - 1; i < j; i++, j--) { int c = path[i]; path[i] = path[j]; path[j] = c; }
        insert(s, me, n, value);
    }

    private void insert(GameState s, int me, int length, double value) {
        int n = pack(s, path, length, prefix, 0, GameState.PAINT_PER_TURN);
        for (int i = 0; i < counts[me]; i++) {
            int other = pack(s, routes[me][i], lengths[me][i], moves[me], 0, GameState.PAINT_PER_TURN);
            if (!sameSet(prefix, n, moves[me], other)) continue;
            if (value <= values[me][i]) return;
            for (int j = i; j + 1 < counts[me]; j++) {
                System.arraycopy(routes[me][j + 1], 0, routes[me][j], 0, lengths[me][j + 1]);
                lengths[me][j] = lengths[me][j + 1]; values[me][j] = values[me][j + 1];
            }
            counts[me]--; break;
        }
        int slot = counts[me];
        for (int i = 0; i < counts[me]; i++) if (value > values[me][i]) { slot = i; break; }
        if (slot >= p.ROLLOUT_WIDTH) return;
        counts[me] = Math.min(p.ROLLOUT_WIDTH, counts[me] + 1);
        for (int i = counts[me] - 1; i > slot; i--) {
            System.arraycopy(routes[me][i - 1], 0, routes[me][i], 0, lengths[me][i - 1]);
            lengths[me][i] = lengths[me][i - 1]; values[me][i] = values[me][i - 1];
        }
        System.arraycopy(path, 0, routes[me][slot], 0, length);
        lengths[me][slot] = length; values[me][slot] = value;
    }

    private void considerQuick(GameState s, int me, int n, int cost, double gain) {
        double value = 0;
        if (p.USE_ROLLOUT_QUICK_VALUE) {
            int turns = quickBuildTurns(s, me, n);
            double earning = Math.min(p.ROLLOUT_ROUTE_HORIZON, s.turnsLeft()) - turns + 1;
            if (turns <= 0 || earning <= 0) return;
            value = gain * earning / turns;
        }
        boolean better = p.USE_ROLLOUT_QUICK_VALUE
                ? value > quickValue || value == quickValue && cost < quickCost
                : cost < quickCost || cost == quickCost && gain > quickGain;
        if (quickLength == 0 || better) {
            System.arraycopy(path, 0, quickPath, 0, n);
            quickLength = n; quickCost = cost; quickGain = (int) gain; quickValue = value;
        }
    }

    /** Exact affordable packing on a private board, without opponent actions or income rules. */
    private int quickBuildTurns(GameState s, int me, int n) {
        targetBoard.set(s);
        for (int turns = 0; turns < p.ROLLOUT_QUICK_MAX_PAINT; turns++) {
            int count = pack(targetBoard.state, path, n, prefix, 0, GameState.PAINT_PER_TURN);
            if (count == 0) return turns;
            Rules.applyPlacements(targetBoard.state, me == 0 ? prefix : moves[0], me == 0 ? count : 0,
                    me == 1 ? prefix : moves[1], me == 1 ? count : 0);
        }
        return p.ROLLOUT_QUICK_MAX_PAINT;
    }

    /** Preserve one cheap completion, replacing a duplicate prefix or the last existing slot. */
    private void retainQuickRoute(GameState s, int me) {
        int n = pack(s, quickPath, quickLength, prefix, 0, GameState.PAINT_PER_TURN);
        int slot = -1;
        for (int i = 0; i < counts[me]; i++) {
            int other = pack(s, routes[me][i], lengths[me][i], moves[me], 0, GameState.PAINT_PER_TURN);
            if (sameSet(prefix, n, moves[me], other)) { slot = i; break; }
        }
        if (slot < 0) {
            slot = Math.min(counts[me], p.ROLLOUT_WIDTH - 1);
            counts[me] = Math.min(counts[me] + 1, p.ROLLOUT_WIDTH);
        }
        System.arraycopy(quickPath, 0, routes[me][slot], 0, quickLength);
        lengths[me][slot] = quickLength;
        quickSlot = slot;
        // Values are only used during proposal insertion, which has finished.
        values[me][slot] = 0;
    }

    /** Keep the ordinary first comparison/fallback, then evaluate quick income before other roots. */
    private int searchRoot(int order) {
        if (!p.USE_ROLLOUT_QUICK_EARLY || quickSlot <= 0) return order;
        if (order == 1) return quickSlot;
        return order > 1 && order <= quickSlot ? order - 1 : order;
    }

    /** Pack real paint costs, including leftovers across projects; never mutate the pre-turn board. */
    private int fillBuild(GameState s, int me, int first, int[] out) {
        int count = 0, paint = GameState.PAINT_PER_TURN;
        for (int pass = -1; pass < counts[me] && paint > 0; pass++) {
            int route = pass < 0 ? first : pass;
            if (route < 0 || route >= counts[me] || pass == first) continue;
            boolean valid = true;
            for (int i = 0; i < lengths[me][route]; i++) if (s.inked[routes[me][route][i]]) { valid = false; break; }
            if (!valid) continue;
            int next = pack(s, routes[me][route], lengths[me][route], out, count, paint);
            for (int i = count; i < next; i++) paint -= s.placeCost(out[i]);
            count = next;
        }
        return count;
    }

    private static int pack(GameState s, int[] route, int n, int[] out, int count, int paint) {
        for (int i = 0; i < n && paint > 0; i++) {
            int c = route[i];
            if (!s.canBuild(c) || s.placeCost(c) > paint) continue;
            boolean duplicate = false;
            for (int j = 0; j < count; j++) if (out[j] == c) duplicate = true;
            if (duplicate) continue;
            out[count++] = c; paint -= s.placeCost(c);
        }
        return count;
    }

    private static boolean sameSet(int[] a, int n, int[] b, int m) {
        if (n != m) return false;
        for (int i = 0; i < n; i++) {
            boolean found = false;
            for (int j = 0; j < m; j++) if (a[i] == b[j]) found = true;
            if (!found) return false;
        }
        return true;
    }

    private static void placeOwn(SearchState board, int me, int[] cells, int count) {
        // Every cell is already checked against this pre-turn state; no score or turn advance.
        for (int i = 0; i < count; i++) board.state.owner[cells[i]] = me;
        board.refresh();
    }

    private double rollout(GameState original, int me, int root, int ownTarget, int reply) {
        work.set(original);
        int depth = Math.min(p.ROLLOUT_DEPTH, original.turnsLeft());
        for (int turn = 0; turn < depth; turn++) {
            if (timer.up()) return Double.NaN;
            GameState s = work.state;
            for (int player = 0; player < 2; player++) {
                if (turn == 0 && player == me) {
                    moveCounts[player] = rootCounts[root];
                    System.arraycopy(roots[root], 0, moves[player], 0, rootCounts[root]);
                    targets[player] = ownTarget;
                } else {
                    int first = player == me ? (root < counts[me] ? root : 0) : reply;
                    moveCounts[player] = fillBuild(s, player, first, moves[player]);
                    targetBoard.set(s);
                    placeOwn(targetBoard, player, moves[player], moveCounts[player]);
                    targets[player] = chooseTarget(targetBoard.state, player);
                }
            }
            if (timer.up()) return Double.NaN;
            boolean terminal = work.resolve(moves[0], moveCounts[0], targets[0], moves[1], moveCounts[1], targets[1]);
            simulatedTurns++;
            if (terminal) {
                int diff = s.score[me] - s.score[1 - me];
                return Math.signum(diff) * p.ROLLOUT_WIN_VALUE + diff;
            }
        }
        Rules.incomePerTurn(work.state, income);
        return work.state.score[me] - work.state.score[1 - me]
                + Math.min(p.ROLLOUT_TAIL, work.state.turnsLeft()) * (income[me] - income[1 - me]);
    }

    /** Cheap exposure shortlist, then exact core ink evaluation. No actual future enemy action is read. */
    private int chooseTarget(GameState s, int me) {
        if (!p.USE_DISRUPT) return -1;
        Arrays.fill(trackBalance, 0); Arrays.fill(priority, 0);
        for (int c = 0; c < s.size; c++) {
            if (s.owner[c] == 1 - me) trackBalance[s.regionId[c]]++;
            else if (s.owner[c] == me) trackBalance[s.regionId[c]]--;
        }
        for (int pair = 0; pair < s.pairCount; pair++) {
            int exposure = -SearchState.connectionIncomeDifference(s, pair, me);
            if (exposure == 0) continue;
            Arrays.fill(marked, false);
            for (int i = 0; i < s.pairPathLen[pair]; i++) {
                int r = s.regionId[s.pairPath[pair][i]];
                if (!marked[r]) { priority[r] += exposure; marked[r] = true; }
            }
        }
        Arrays.fill(shortlist, -1);
        for (int r = 0; r < s.regionCount; r++) {
            if (s.regionInked[r] || s.regionHasTown(r)) continue;
            priority[r] = (priority[r] * p.ROLLOUT_TARGET_HORIZON
                    + trackBalance[r] * p.ROLLOUT_TRACK_WEIGHT) / (GameState.INK_THRESHOLD - s.regionInstability[r]);
            for (int i = 0; i < shortlist.length; i++) {
                if (shortlist[i] < 0 || priority[r] > priority[shortlist[i]]) {
                    for (int j = shortlist.length - 1; j > i; j--) shortlist[j] = shortlist[j - 1];
                    shortlist[i] = r; break;
                }
            }
        }
        Rules.incomePerTurn(s, income);
        int before = income[me] - income[1 - me], bestTarget = -1;
        double best = 0;
        for (int i = 0; i < shortlist.length && !timer.up(); i++) {
            int r = shortlist[i]; if (r < 0) break;
            int needed = GameState.INK_THRESHOLD - s.regionInstability[r];
            inkBoard.set(s); Rules.ink(inkBoard.state, r); inkBoard.refresh();
            Rules.incomePerTurn(inkBoard.state, income);
            int after = income[me] - income[1 - me];
            if (needed == 1 && !Rules.anyPairPossible(inkBoard.state, pathing)) {
                if (s.score[me] - s.score[1 - me] + after > 0) return r;
                continue;
            }
            double value = ((after - before) * Math.max(0, Math.min(p.ROLLOUT_TARGET_HORIZON, s.turnsLeft()) - needed + 1)
                    + trackBalance[r] * p.ROLLOUT_TRACK_WEIGHT) / needed;
            if (value > best) { best = value; bestTarget = r; }
        }
        return bestTarget;
    }
}
