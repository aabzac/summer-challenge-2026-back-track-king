package btk.bot.current;

import btk.bot.Bot;
import btk.bot.Params;
import btk.bot.Timer;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Fixed-work known-board policy; no simulated completed routes or nested state evaluator. */
public final class HeuristicBot implements Bot {
    private final Params p;
    private final Timer timer = new Timer();
    private Pathing paths;
    private GameState work, safety;
    private int[] route, bestRoute, own, enemy, ownActive, enemyActive, extra;
    private int[] detourRegions, detourPairs;
    private double[] detourBonus, pairImportance, pairShare;
    private final int[] income = new int[2];
    private boolean[] blocked, rejected;
    private int target = -1, routePair = -1, bestLength, bestPair;
    public int lastRouteQueries, lastDetourQueries, lastDetourRegions, emergencyStops;
    public boolean lastEmergency;
    public long lastElapsedMs;

    public HeuristicBot(Params params) {
        p = params.copy();
        if (p.HEURISTIC_MAX_ROUTES < 1 || p.HEURISTIC_MAX_ROUTES > 1024
                || p.HEURISTIC_AVOID_INSTABILITY < 0 || p.HEURISTIC_AVOID_INSTABILITY > GameState.INK_THRESHOLD
                || !positive(p.HEURISTIC_TARGET_STICKINESS) || !positive(p.HEURISTIC_ROUTE_STICKINESS)
                || !positive(p.HEURISTIC_OWN_PENALTY) || !positive(p.HEURISTIC_ACTIVE_WEIGHT)
                || !Double.isFinite(p.UNSTABLE_COST) || p.UNSTABLE_COST < 0
                || !positive(p.W_OWN) || !positive(p.W_OPP)
                || p.USE_REPLY_RERANK || p.USE_JOINT_ACTION || p.USE_OWN_BUILD_PLAN || p.USE_OPP_MODEL || p.GUARD_BUILD_INCOME
                || p.CHOOSE_BUILD_DIRECTION || p.EARLY_DISRUPT_FALLBACK || p.INTERRUPT_VULN_EVAL) {
            throw new IllegalArgumentException("Invalid or incompatible simple-policy parameters");
        }
        if (p.USE_HEURISTIC_DETOUR && (!positive(p.HEURISTIC_DETOUR_WEIGHT)
                || p.HEURISTIC_DETOUR_REGIONS < 1 || p.HEURISTIC_DETOUR_REGIONS > 16
                || p.HEURISTIC_DETOUR_PAIRS < 1 || p.HEURISTIC_DETOUR_PAIRS > 16
                || p.HEURISTIC_DETOUR_CAP < 1 || p.HEURISTIC_DETOUR_CAP > 100)) {
            throw new IllegalArgumentException("Invalid detour policy parameters");
        }
    }

    private static boolean positive(double x) { return Double.isFinite(x) && x > 0; }
    public String name() { return "current?USE_HEURISTIC_POLICY=1"; }

    public void init(GameState s, int me) {
        paths = new Pathing(s.size); work = s.copy(); safety = s.copy();
        route = new int[s.size]; bestRoute = new int[s.size]; extra = new int[s.size];
        blocked = new boolean[s.size]; rejected = new boolean[s.regionCount];
        own = new int[s.regionCount]; enemy = new int[s.regionCount];
        ownActive = new int[s.regionCount]; enemyActive = new int[s.regionCount];
        if (p.USE_HEURISTIC_DETOUR) {
            detourRegions = new int[p.HEURISTIC_DETOUR_REGIONS];
            detourPairs = new int[p.HEURISTIC_DETOUR_PAIRS];
            detourBonus = new double[s.regionCount];
            pairImportance = new double[s.pairCount]; pairShare = new double[s.pairCount];
        }
        target = routePair = -1; emergencyStops = 0;
    }

    public List<Action> play(GameState s, int me, long budgetMs) {
        timer.start(budgetMs); lastRouteQueries = 0; lastEmergency = false;
        lastDetourQueries = lastDetourRegions = 0;
        if (paths == null) init(s, me);
        List<Action> out = new ArrayList<>(5);
        if (!expired()) {
            target = p.USE_DISRUPT ? chooseTarget(s, me) : -1;
            if (target >= 0) out.add(Action.disrupt(target));
            work.copyFrom(s);
            int paint = GameState.PAINT_PER_TURN;
            for (int pass = 0; pass < GameState.PAINT_PER_TURN && paint > 0 && !expired(); pass++) {
                if (!chooseRoute(work, me)) break;
                routePair = bestPair;
                int before = paint;
                // Prefer town regions, then route order. Never build in our disruption target.
                for (int priority = 0; priority < 2; priority++) for (int i = 0; i < bestLength; i++) {
                    int c = bestRoute[i];
                    if (!work.canBuild(c) || work.regionId[c] == target || work.placeCost(c) > paint) continue;
                    if ((priority == 0) != work.regionHasTown(work.regionId[c])) continue;
                    paint -= work.placeCost(c); work.owner[c] = me;
                    out.add(Action.place(work.x(c), work.y(c)));
                }
                if (paint == before) break;
                Rules.updateConnections(work, paths);
            }
            for (int i = 0; p.USE_IDLE_EXTEND && i < GameState.PAINT_PER_TURN && paint > 0 && !expired(); i++) {
                int c = idleCell(work, me, paint);
                if (c < 0) break;
                paint -= work.placeCost(c); work.owner[c] = me;
                out.add(Action.place(work.x(c), work.y(c)));
            }
        }
        lastElapsedMs = timer.elapsedMs();
        if (p.MESSAGE) out.add(Action.message("heuristic routes=" + lastRouteQueries + " ms=" + lastElapsedMs
                + (p.USE_HEURISTIC_DETOUR ? " detours=" + lastDetourQueries : "")
                + (lastEmergency ? " EMERGENCY" : "")));
        if (p.DEBUG) System.err.println("heuristic routes=" + lastRouteQueries + " ms=" + lastElapsedMs
                + " detours=" + lastDetourQueries + " emergency=" + lastEmergency);
        if (out.isEmpty()) out.add(Action.waitAction());
        return out;
    }

    private boolean expired() {
        if (!p.HEURISTIC_EMERGENCY_STOP || !timer.up()) return false;
        if (!lastEmergency) emergencyStops++;
        lastEmergency = true;
        return true;
    }

    private int chooseTarget(GameState s, int me) {
        Arrays.fill(own, 0); Arrays.fill(enemy, 0);
        Arrays.fill(ownActive, 0); Arrays.fill(enemyActive, 0); Arrays.fill(rejected, false);
        for (int c = 0; c < s.size; c++) {
            if (s.owner[c] == me) own[s.regionId[c]]++;
            if (s.owner[c] == 1 - me) enemy[s.regionId[c]]++;
        }
        for (int pair = 0; pair < s.pairCount; pair++) if (s.pairActive(pair)) {
            for (int i = 0; i < s.pairPathLen[pair]; i++) {
                int c = s.pairPath[pair][i];
                if (s.owner[c] == me) ownActive[s.regionId[c]]++;
                if (s.owner[c] == 1 - me) enemyActive[s.regionId[c]]++;
            }
        }
        if (p.USE_HEURISTIC_DETOUR) prepareDetours(s, me);
        for (int attempt = 0; attempt < s.regionCount && !expired(); attempt++) {
            int best = -1; double value = 0;
            for (int r = 0; r < s.regionCount; r++) {
                if (rejected[r] || s.regionInked[r] || s.regionHasTown(r)) continue;
                int needed = GameState.INK_THRESHOLD - s.regionInstability[r];
                if (needed > s.turnsLeft()) continue;
                double priority = (enemy[r] + p.HEURISTIC_ACTIVE_WEIGHT * enemyActive[r]
                        - p.HEURISTIC_OWN_PENALTY * (own[r] + p.HEURISTIC_ACTIVE_WEIGHT * ownActive[r])
                        + detourAdjustment(r)) / needed;
                if (r == target) priority *= p.HEURISTIC_TARGET_STICKINESS;
                if (priority > value || priority > 0 && priority == value && tie(r, me) < tie(best, me)) {
                    value = priority; best = r;
                }
            }
            if (best < 0) return -1;
            if (s.regionInstability[best] == GameState.INK_THRESHOLD - 1 && p.USE_CLOSE) {
                safety.copyFrom(s); Rules.ink(safety, best); Rules.updateConnections(safety, paths);
                if (!Rules.anyPairPossible(safety, paths)) {
                    Rules.incomePerTurn(safety, income);
                    if (s.score[me] + income[me] <= s.score[1 - me] + income[1 - me]) {
                        rejected[best] = true; continue;
                    }
                }
            }
            return expired() ? -1 : best;
        }
        return -1;
    }

    /** Signed, capped recovery-paint feature before hits/stickiness. Package-visible for diagnostics. */
    double detourAdjustment(int region) {
        return p.USE_HEURISTIC_DETOUR ? p.HEURISTIC_DETOUR_WEIGHT * detourBonus[region] : 0;
    }

    private void prepareDetours(GameState s, int me) {
        Arrays.fill(detourBonus, 0); Arrays.fill(detourRegions, -1); Arrays.fill(detourPairs, -1);
        Arrays.fill(pairImportance, 0); Arrays.fill(pairShare, 0);
        // Same base priorities as v1. Do not promote empty/own-only speculative sabotage targets.
        for (int k = 0; k < detourRegions.length; k++) {
            int best = -1; double value = 0;
            for (int r = 0; r < s.regionCount; r++) {
                if (s.regionInked[r] || s.regionHasTown(r)) continue;
                boolean used = false;
                for (int i = 0; i < k; i++) if (detourRegions[i] == r) used = true;
                if (used) continue;
                int hits = GameState.INK_THRESHOLD - s.regionInstability[r];
                if (hits > s.turnsLeft()) continue;
                double v = (enemy[r] + p.HEURISTIC_ACTIVE_WEIGHT * enemyActive[r]
                        - p.HEURISTIC_OWN_PENALTY * (own[r] + p.HEURISTIC_ACTIVE_WEIGHT * ownActive[r])) / hits;
                if (r == target) v *= p.HEURISTIC_TARGET_STICKINESS;
                if (v > value || v > 0 && v == value && tie(r, me) < tie(best, me)) { best = r; value = v; }
            }
            if (best < 0) break;
            detourRegions[k] = best;
        }
        for (int pair = 0; pair < s.pairCount; pair++) if (s.pairActive(pair)) {
            boolean duplicate = false;
            for (int q = 0; q < pair; q++) {
                if (s.pairFrom[q] == s.pairFrom[pair] && s.pairTo[q] == s.pairTo[pair]
                        || s.pairFrom[q] == s.pairTo[pair] && s.pairTo[q] == s.pairFrom[pair]) {
                    duplicate = true; break;
                }
            }
            if (duplicate) continue;
            int mine = 0, theirs = 0;
            for (int i = 0; i < s.pairPathLen[pair]; i++) {
                int c = s.pairPath[pair][i];
                if (s.owner[c] == me) mine++;
                if (s.owner[c] == 1 - me) theirs++;
            }
            double margin = theirs - p.HEURISTIC_OWN_PENALTY * mine;
            pairImportance[pair] = Math.abs(margin);
            pairShare[pair] = mine + theirs == 0 ? 0 : margin / (mine + theirs);
        }
        for (int k = 0; k < detourPairs.length; k++) {
            int best = -1; double value = 0;
            for (int pair = 0; pair < s.pairCount; pair++) {
                boolean used = false;
                for (int i = 0; i < k; i++) if (detourPairs[i] == pair) used = true;
                if (used) continue;
                double v = pairImportance[pair];
                if (v > value || v > 0 && v == value && tie(pair, me) < tie(best, me)) { best = pair; value = v; }
            }
            if (best < 0) break;
            detourPairs[k] = best;
        }
        for (int region : detourRegions) {
            if (region < 0) break;
            for (int c = 0; c < s.size; c++) blocked[c] = s.regionId[c] == region;
            double enemyBurden = 0, ownBurden = 0;
            for (int pair : detourPairs) {
                if (pair < 0) break;
                boolean crosses = false;
                for (int i = 0; i < s.pairPathLen[pair]; i++) if (blocked[s.pairPath[pair][i]]) { crosses = true; break; }
                if (!crosses) continue;
                if (expired()) { Arrays.fill(detourBonus, 0); lastDetourRegions = 0; return; }
                int to = s.townCell[s.pairTo[pair]];
                int n = paths.cheapestPath(s, s.townCell[s.pairFrom[pair]], to, route, blocked);
                lastDetourQueries++;
                if (expired()) { Arrays.fill(detourBonus, 0); lastDetourRegions = 0; return; }
                int cost = n < 0 ? p.HEURISTIC_DETOUR_CAP : Math.min(p.HEURISTIC_DETOUR_CAP, paths.lastCost(to));
                double burden = cost * pairShare[pair];
                enemyBurden = Math.max(enemyBurden, burden); ownBurden = Math.max(ownBurden, -burden);
            }
            detourBonus[region] = enemyBurden - ownBurden;
            lastDetourRegions++;
        }
    }

    private boolean chooseRoute(GameState s, int me) {
        double bestValue = 0; bestLength = 0; bestPair = -1;
        for (int c = 0; c < s.size; c++) extra[c] = (int) Math.round(p.UNSTABLE_COST * s.regionInstability[s.regionId[c]]);
        for (int pair = 0; pair < s.pairCount; pair++) {
            for (int variant = 0; variant < 2; variant++) {
                if (lastRouteQueries >= p.HEURISTIC_MAX_ROUTES || expired()) return bestLength > 0;
                if (variant == 1 && !p.USE_STEAL) continue;
                if (variant == 0 && s.pairActive(pair)) continue;
                for (int c = 0; c < s.size; c++) {
                    int r = s.regionId[c];
                    blocked[c] = r == target || variant == 1 && s.owner[c] == 1 - me
                            || s.canBuild(c) && !s.regionHasTown(r) && s.regionInstability[r] >= p.HEURISTIC_AVOID_INSTABILITY;
                }
                int n = paths.cheapestPath(s, s.townCell[s.pairFrom[pair]], s.townCell[s.pairTo[pair]], route, blocked, extra);
                lastRouteQueries++;
                if (expired()) return bestLength > 0;
                if (n < 0 || s.pairActive(pair) && n >= s.pairPathLen[pair]) continue;
                int cost = 0, ours = 0, theirs = 0, empty = 0;
                for (int i = 0; i < n; i++) {
                    int c = route[i];
                    if (s.canBuild(c)) { cost += s.placeCost(c); empty++; }
                    else if (s.owner[c] == me) ours++;
                    else if (s.owner[c] == 1 - me) theirs++;
                }
                if (cost == 0 || cost > s.turnsLeft() * GameState.PAINT_PER_TURN) continue;
                // Local route feature, NOT a simulated income or completed-position evaluation.
                double value = (p.W_OWN * (empty + ours) - p.W_OPP * theirs) / (cost + 1);
                if (pair == routePair) value *= p.HEURISTIC_ROUTE_STICKINESS;
                if (value > bestValue || value > 0 && value == bestValue && tie(pair, me) < tie(bestPair, me)) {
                    bestValue = value; bestPair = pair; bestLength = n;
                    System.arraycopy(route, 0, bestRoute, 0, n);
                }
            }
        }
        return bestLength > 0;
    }

    private int idleCell(GameState s, int me, int paint) {
        int best = -1, bestValue = 0;
        for (int c = 0; c < s.size; c++) {
            int r = s.regionId[c];
            if (!s.canBuild(c) || s.placeCost(c) > paint || r == target
                    || !s.regionHasTown(r) && s.regionInstability[r] >= p.HEURISTIC_AVOID_INSTABILITY) continue;
            int value = 0;
            for (int d = 0; d < 4; d++) {
                int x = s.x(c) + GameState.DX[d], y = s.y(c) + GameState.DY[d];
                if (!s.inBounds(x, y)) continue;
                int near = s.cell(x, y);
                if (s.owner[near] == me || s.townAtCell[near] >= 0) value++;
            }
            if (value == 0) continue;
            if (s.regionHasTown(r)) value++;
            if (value > bestValue || value == bestValue && tie(c, me) < tie(best, me)) {
                best = c; bestValue = value;
            }
        }
        return best;
    }

    private long tie(int id, int me) {
        if (id < 0) return Long.MAX_VALUE;
        long x = id * 0x9E3779B9L + p.SEED * 1_000_003L + me * 7919L;
        x ^= x >>> 16; x *= 0x45d9f3bL; x ^= x >>> 16;
        return x & 0x7fffffffffffffffL;
    }
}
