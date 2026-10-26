package btk.tools;

import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.core.TacticalPathing;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * boss-v4 diagnostic (2026-10-26): a tools-side copy of {@code btk.bot.current.BossSabotage.chooseInternal} that, at every
 * boss turn with a DISRUPT in a replay directory, writes one row per candidate region with the features exactly as the
 * proxy computes them, whether the region is a one-hit closure (and winning or losing for the boss), and the proxy's own
 * stateful choice at hold margins 1.0 and 0. Joined with CgLeaderStudy's sabotage.csv on (game, turn, region), it shows
 * which feature or rule makes the proxy diverge from the fitted logit. Diagnostic only; the shipped chooser is untouched.
 *
 * <p>Args: replayDir bossNickname outCsv
 */
public final class BossChooserProbe {
    static final double[] W = {
        +0.137316, -0.111814, +0.775046, -0.003927, -0.282833, +0.291303, +0.884541,
        +0.371747, -0.001751, +0.368378, -3.437223, +0.155573, +0.148325
    };

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]); String boss = args[1]; Path out = Path.of(args[2]);
        Files.createDirectories(out.toAbsolutePath().getParent());
        int games = 0, rows = 0;
        try (BufferedWriter w = Files.newBufferedWriter(out)) {
            w.write("game,turn,region,bossChosen,dOpp,dOwn,oppCells,oppActive,ownCells,oppPrev2,instab,oppTraffic,routesAny,adjTown,"
                    + "neutralCells,freeCells,closeWin,closeLose,score,heldM1,choiceM1,choiceM0\n");
            for (Path f : CgReplay.listReplays(dir)) {
                CgReplay r = CgReplay.load(f);
                int me = r.nicknames[0].equals(boss) ? 0 : r.nicknames[1].equals(boss) ? 1 : -1;
                if (me < 0) continue;
                games++;
                GameState s = r.toState();
                Pathing pathing = new Pathing(s.size);
                Rules.updateConnections(s, pathing);
                Probe m1 = new Probe(s, 1.0), m0 = new Probe(s, 0.0);
                for (int t = 0; t < r.turns(); t++) {
                    String[] lines = r.turnStdout.get(t);
                    @SuppressWarnings("unchecked")
                    List<Action>[] acts = new List[2];
                    for (int p = 0; p < 2; p++) {
                        try {
                            acts[p] = lines[p] == null || r.failure(t, p) != null ? List.of(Action.waitAction()) : Action.parseLine(lines[p]);
                        } catch (RuntimeException e) { acts[p] = List.of(Action.waitAction()); }
                    }
                    GameState pre = s.copy();
                    int bossT = -1;
                    for (Action a : acts[me]) if (a.type == Action.Type.DISRUPT && bossT < 0) bossT = a.disruptRegion(pre);
                    m1.observe(pre); m0.observe(pre);
                    int heldBefore = m1.target;
                    int c1 = m1.choose(pre, me, null), c0 = m0.choose(pre, me, null);
                    if (bossT >= 0) {
                        StringBuilder sb = new StringBuilder();
                        m1.chooseInternal(pre, me, heldBefore, sb, r.gameId, t, bossT, c1, c0);
                        w.write(sb.toString());
                        rows++;
                    }
                    Rules.resolve(s, acts[0], acts[1], pathing);
                }
            }
        }
        System.out.printf(Locale.ROOT, "%d boss games, %d disrupt turns -> %s%n", games, rows, out);
    }

    /** Copy of BossSabotage with a feature dump; the scoring and rules are unchanged. */
    static final class Probe {
        final double holdMargin;
        final Pathing paths; final TacticalPathing tactical; final GameState sim;
        final int[] route, entry, placedTurn, prevOwner;
        final int[] oppCells, ownCells, neutralCells, oppActive, oppPrev2, freeCells, routesAny;
        final double[] oppTraffic; final boolean[] adjTown, onActive, seen;
        final int[] before = new int[2], after = new int[2];
        int target = -1;

        Probe(GameState s, double holdMargin) {
            this.holdMargin = holdMargin;
            paths = new Pathing(s.size); tactical = new TacticalPathing(s.size); sim = s.copy();
            route = new int[s.size]; entry = new int[s.size]; placedTurn = new int[s.size]; prevOwner = new int[s.size];
            Arrays.fill(placedTurn, -99); Arrays.fill(prevOwner, GameState.NO_TRACK);
            oppCells = new int[s.regionCount]; ownCells = new int[s.regionCount]; neutralCells = new int[s.regionCount];
            oppActive = new int[s.regionCount]; oppPrev2 = new int[s.regionCount]; freeCells = new int[s.regionCount];
            routesAny = new int[s.regionCount]; oppTraffic = new double[s.regionCount];
            adjTown = new boolean[s.regionCount]; onActive = new boolean[s.size]; seen = new boolean[s.regionCount];
        }

        void observe(GameState s) {
            for (int c = 0; c < s.size; c++) {
                if (s.owner[c] != prevOwner[c] && s.owner[c] != GameState.NO_TRACK) placedTurn[c] = s.turn;
                prevOwner[c] = s.owner[c];
            }
        }

        int choose(GameState s, int me, StringBuilder sb) {
            target = chooseInternal(s, me, target, sb, 0, 0, -1, -1, -1);
            return target;
        }

        int chooseInternal(GameState s, int me, int held, StringBuilder sb, long game, int turn, int bossT, int c1, int c0) {
            int opp = 1 - me;
            if (held >= 0 && s.regionInked[held]) held = -1;
            Arrays.fill(oppCells, 0); Arrays.fill(ownCells, 0); Arrays.fill(neutralCells, 0);
            Arrays.fill(oppActive, 0); Arrays.fill(oppPrev2, 0); Arrays.fill(freeCells, 0);
            Arrays.fill(routesAny, 0); Arrays.fill(oppTraffic, 0); Arrays.fill(adjTown, false);
            Arrays.fill(onActive, false);
            for (int pair = 0; pair < s.pairCount; pair++) {
                if (!s.pairActive(pair)) continue;
                for (int i = 0; i < s.pairPathLen[pair]; i++) onActive[s.pairPath[pair][i]] = true;
            }
            for (int c = 0; c < s.size; c++) {
                int r = s.regionId[c];
                if (s.canBuild(c)) freeCells[r]++;
                int o = s.owner[c];
                if (o == opp) { oppCells[r]++; if (onActive[c]) oppActive[r]++; if (placedTurn[c] >= s.turn - 2) oppPrev2[r]++; }
                else if (o == me) ownCells[r]++;
                else if (o == GameState.NEUTRAL) neutralCells[r]++;
                int x = c % s.width, y = c / s.width;
                for (int d = 0; d < 4; d++) {
                    int nx = x + GameState.DX[d], ny = y + GameState.DY[d];
                    if (nx < 0 || ny < 0 || nx >= s.width || ny >= s.height) continue;
                    int nr = s.regionId[ny * s.width + nx];
                    if (nr != r && s.regionHasTown(nr)) adjTown[r] = true;
                }
            }
            for (int pair = 0; pair < s.pairCount; pair++) {
                if (s.pairActive(pair)) continue;
                int from = s.townCell[s.pairFrom[pair]], to = s.townCell[s.pairTo[pair]];
                int n = paths.cheapestPath(s, from, to, route);
                if (n > 0 && routeCost(s, route, n) > 0) mark(s, route, n, routesAny, null, 0);
                for (int c = 0; c < s.size; c++) {
                    int r = s.regionId[c];
                    if (s.inked[c]) entry[c] = TacticalPathing.INF;
                    else if (s.isTown(c) || s.owner[c] == opp || s.owner[c] == GameState.NEUTRAL) entry[c] = 0;
                    else if (s.owner[c] == me) entry[c] = TacticalPathing.INF;
                    else if (!s.regionHasTown(r) && s.regionInstability[r] >= 2) entry[c] = TacticalPathing.INF;
                    else entry[c] = s.placeCost(c);
                }
                n = tactical.weightedPath(s, from, to, entry, route);
                if (n > 0) { int cost = routeCost(s, route, n); if (cost > 0) mark(s, route, n, null, oppTraffic, 1.0 / cost); }
            }
            Rules.incomePerTurn(s, before);
            int best = -1; double bestScore = -Double.MAX_VALUE, heldScore = -Double.MAX_VALUE;
            int firstWinningClose = -1;
            for (int r = 0; r < s.regionCount; r++) {
                if (s.regionInked[r] || s.regionHasTown(r)) continue;
                boolean candidate = oppCells[r] > 0 || ownCells[r] > 0 || neutralCells[r] > 0 || s.regionInstability[r] > 0
                        || routesAny[r] > 0 || oppTraffic[r] > 0 || r == held || r == bossT;
                if (!candidate) continue;
                int needed = GameState.INK_THRESHOLD - s.regionInstability[r];
                int dOpp = 0, dOwn = 0; boolean closeWin = false, closeLose = false;
                if (oppCells[r] + ownCells[r] + neutralCells[r] > 0 || needed == 1) {
                    sim.copyFrom(s); Rules.ink(sim, r); Rules.updateConnections(sim, paths); Rules.incomePerTurn(sim, after);
                    dOpp = before[opp] - after[opp]; dOwn = before[me] - after[me];
                    if (needed == 1 && !Rules.anyPairPossible(sim, paths)) {
                        if (s.score[me] + after[me] > s.score[opp] + after[opp]) closeWin = true; else closeLose = true;
                    }
                }
                double v = W[0] * dOpp + W[1] * dOwn + W[2] * oppCells[r] + W[3] * oppActive[r] + W[4] * ownCells[r]
                        + W[5] * oppPrev2[r] + W[6] * s.regionInstability[r] + W[7] * oppTraffic[r] + W[8] * routesAny[r]
                        + W[9] * (adjTown[r] ? 1 : 0) + W[11] * neutralCells[r] + W[12] * Math.log1p(freeCells[r]);
                if (sb != null)
                    sb.append(game).append(',').append(turn).append(',').append(r).append(',').append(r == bossT ? 1 : 0).append(',')
                      .append(dOpp).append(',').append(dOwn).append(',').append(oppCells[r]).append(',').append(oppActive[r]).append(',')
                      .append(ownCells[r]).append(',').append(oppPrev2[r]).append(',').append(s.regionInstability[r]).append(',')
                      .append(String.format(Locale.ROOT, "%.4f", oppTraffic[r])).append(',').append(routesAny[r]).append(',')
                      .append(adjTown[r] ? 1 : 0).append(',').append(neutralCells[r]).append(',').append(freeCells[r]).append(',')
                      .append(closeWin ? 1 : 0).append(',').append(closeLose ? 1 : 0).append(',')
                      .append(String.format(Locale.ROOT, "%.4f", v)).append(',').append(r == held ? 1 : 0).append(',')
                      .append(r == c1 ? 1 : 0).append(',').append(r == c0 ? 1 : 0).append('\n');
                if (closeWin) { if (firstWinningClose < 0) firstWinningClose = r; continue; }
                if (closeLose) continue;
                if (r == bossT && !(oppCells[r] > 0 || ownCells[r] > 0 || neutralCells[r] > 0 || s.regionInstability[r] > 0
                        || routesAny[r] > 0 || oppTraffic[r] > 0 || r == held)) continue; // listed for the dump only
                if (r == held) heldScore = v;
                if (v > bestScore) { bestScore = v; best = r; }
            }
            if (firstWinningClose >= 0) return firstWinningClose;
            if (best < 0) return -1;
            if (held >= 0 && heldScore > -Double.MAX_VALUE && bestScore < heldScore + holdMargin) return held;
            if (oppCells[best] == 0 && s.regionInstability[best] == 0 && oppTraffic[best] == 0 && routesAny[best] == 0) return -1;
            return best;
        }

        static int routeCost(GameState s, int[] cells, int n) {
            int cost = 0;
            for (int i = 0; i < n; i++) if (s.canBuild(cells[i])) cost += s.placeCost(cells[i]);
            return cost;
        }

        void mark(GameState s, int[] cells, int n, int[] counts, double[] traffic, double weight) {
            Arrays.fill(seen, false);
            for (int i = 0; i < n; i++) {
                int r = s.regionId[cells[i]];
                if (seen[r] || s.regionHasTown(r)) continue;
                seen[r] = true;
                if (counts != null) counts[r]++;
                if (traffic != null) traffic[r] += weight;
            }
        }
    }
}
