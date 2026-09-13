package btk.tools;

import btk.bot.Bot;
import btk.bot.BotFactory;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.runner.TimingProfile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * What do strong players actually do? Replays every downloaded CG game through the engine and,
 * for the better-ranked player of each game, collects: disrupt frequency and first disrupt turn,
 * what kind of region they disrupt (empty / opponent tracks / own tracks / mixed), paint usage,
 * how the game ended (100 turns vs closed) and who closed it. Optionally runs one of our bots on
 * every state and measures agreement with the top player's moves.
 *
 * <p>Args: replayDir [botSpec]
 */
public final class CgPolicyStudy {

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args.length > 0 && !args[0].isEmpty() ? args[0] : "replays/cg");
        String botSpec = args.length > 1 && !args[1].isEmpty() ? args[1] : null;
        TimingProfile timing = TimingProfile.parse(args.length > 2 ? args[2] : "cg");
        if (botSpec != null) System.out.printf("search timing: %s, first %d ms / later %d ms%n",
                timing, timing.firstSearchMs, timing.turnSearchMs);

        int games = 0, ended100 = 0, closedByLeader = 0, closedByTrailer = 0, leaderWonEarly = 0;
        long topTurns = 0, topDisruptTurns = 0, topPaint = 0, topPaintMax = 0, topXY = 0;
        Map<String, Integer> targetKind = new TreeMap<>();
        Map<Integer, Integer> firstDisrupt = new TreeMap<>();
        Map<Integer, Integer> disruptsPerTurnAtTurn = new TreeMap<>();
        Map<Integer, Integer> turnsAtTurn = new TreeMap<>();
        Map<String, Integer> firstDisruptTargetKind = new TreeMap<>();
        long botTurns = 0, botDisruptAgree = 0, botDisruptBothNone = 0, botCellsTop = 0, botCellsMatch = 0, botDisruptTop = 0, botDisruptBot = 0;
        List<String> perGame = new ArrayList<>();

        for (Path f : CgReplay.listReplays(dir)) {
            CgReplay r = CgReplay.load(f);
            GameState s = r.toState();
            Pathing pathing = new Pathing(s.size);
            int top = r.ranks[0] > 0 && r.ranks[1] > 0 ? (r.ranks[0] <= r.ranks[1] ? 0 : 1) : (r.finalScores[0] >= r.finalScores[1] ? 0 : 1);
            Bot bot = botSpec == null ? null : BotFactory.create(botSpec);
            if (bot != null) bot.init(s.copy(), top);
            int firstDis = -1;
            int lastInkTurn = -1, lastInkBy = -1;
            for (int t = 0; t < r.turns(); t++) {
                String[] lines = r.turnStdout.get(t);
                @SuppressWarnings("unchecked")
                List<Action>[] acts = new List[2];
                for (int p = 0; p < 2; p++) {
                    try {
                        acts[p] = lines[p] == null ? List.of(Action.waitAction()) : Action.parseLine(lines[p]);
                    } catch (RuntimeException e) {
                        acts[p] = List.of(Action.waitAction());
                    }
                }
                // --- top player's move on the state before resolution ---
                Set<Integer> topCells = new HashSet<>();
                int topRegion = -1;
                int paint = 0;
                for (Action a : acts[top]) {
                    if (a.type == Action.Type.PLACE) {
                        int c = s.cell(a.x, a.y);
                        topCells.add(c);
                        if (s.canBuild(c)) paint += s.placeCost(c);
                    } else if (a.type == Action.Type.DISRUPT) {
                        topRegion = a.disruptRegion(s);
                        if (a.x >= 0) topXY++;
                    }
                }
                topStats.add(s, topCells, top, pathing);
                topTurns++;
                topPaint += Math.min(paint, GameState.PAINT_PER_TURN);
                if (paint >= GameState.PAINT_PER_TURN) topPaintMax++;
                turnsAtTurn.merge(t / 10 * 10, 1, Integer::sum);
                if (topRegion >= 0) {
                    topDisruptTurns++;
                    disruptsPerTurnAtTurn.merge(t / 10 * 10, 1, Integer::sum);
                    String kind = regionKind(s, topRegion, top);
                    targetKind.merge(kind, 1, Integer::sum);
                    if (firstDis < 0) {
                        firstDis = t;
                        firstDisruptTargetKind.merge(kind, 1, Integer::sum);
                    }
                }
                // --- our bot on the same state ---
                if (bot != null) {
                    List<Action> mine = bot.play(s.copy(), top, timing.budget(t));
                    Set<Integer> myCells = new HashSet<>();
                    int myRegion = -1;
                    for (Action a : mine) {
                        if (a.type == Action.Type.PLACE) myCells.add(s.cell(a.x, a.y));
                        else if (a.type == Action.Type.DISRUPT) myRegion = a.disruptRegion(s);
                    }
                    botStats.add(s, myCells, top, pathing);
                    botTurns++;
                    botCellsTop += topCells.size();
                    for (int c : topCells) if (myCells.contains(c)) botCellsMatch++;
                    if (topRegion >= 0) botDisruptTop++;
                    if (myRegion >= 0) botDisruptBot++;
                    if (topRegion >= 0 && topRegion == myRegion) botDisruptAgree++;
                    if (topRegion < 0 && myRegion < 0) botDisruptBothNone++;
                }
                int[] scoreBefore = s.score.clone();
                Rules.TurnResult tr = Rules.resolve(s, acts[0], acts[1], pathing);
                if (!tr.inkedRegions.isEmpty()) {
                    lastInkTurn = t;
                    // who pushed it over: the player(s) that disrupted the inked region this turn
                    lastInkBy = -1;
                    for (int p = 0; p < 2; p++) {
                        for (Action a : acts[p]) {
                            if (a.type == Action.Type.DISRUPT && tr.inkedRegions.contains(a.disruptRegion(s))) lastInkBy = lastInkBy < 0 ? p : 2;
                        }
                    }
                }
            }
            games++;
            firstDisrupt.merge(firstDis < 0 ? 999 : firstDis, 1, Integer::sum);
            int leader = s.score[0] >= s.score[1] ? 0 : 1;
            if (r.turns() >= 100) {
                ended100++;
            } else {
                if (lastInkBy == leader) closedByLeader++;
                else if (lastInkBy == 1 - leader) closedByTrailer++;
                if (leader == top) leaderWonEarly++;
            }
            perGame.add(String.format("game %d %s(r%d) vs %s(r%d): turns=%d score %d-%d top=P%d firstDisrupt=%d lastInk t%d by %s",
                    r.gameId, r.nicknames[0], r.ranks[0], r.nicknames[1], r.ranks[1], r.turns(), s.score[0], s.score[1], top, firstDis, lastInkTurn,
                    lastInkBy == 2 ? "both" : lastInkBy < 0 ? "-" : "P" + lastInkBy));
        }
        System.out.println("games: " + games + "  ended at 100 turns: " + ended100 + "  closed early: " + (games - ended100)
                + " (last ink by leader " + closedByLeader + ", by trailer " + closedByTrailer + ", top-ranked player leading at early end " + leaderWonEarly + ")");
        System.out.printf("top player: disrupt on %.1f%% of turns, DISRUPT x y syntax %d, paint used %.2f/3 per turn, full paint on %.1f%% of turns%n",
                100.0 * topDisruptTurns / topTurns, topXY, (double) topPaint / topTurns, 100.0 * topPaintMax / topTurns);
        System.out.println("first disrupt turn histogram: " + firstDisrupt);
        System.out.println("first disrupt target kind: " + firstDisruptTargetKind);
        System.out.println("disrupt target kind (all): " + targetKind);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, Integer> e : turnsAtTurn.entrySet()) {
            sb.append(String.format("t%d:%.0f%% ", e.getKey(), 100.0 * disruptsPerTurnAtTurn.getOrDefault(e.getKey(), 0) / e.getValue()));
        }
        System.out.println("disrupt rate by turn decade: " + sb);
        System.out.println("top player placements: " + topStats);
        if (botSpec != null) {
            System.out.printf("bot %s agreement over %d states: placement cells matched %d/%d (%.1f%%), disrupt region same %d (top disrupted %d, bot %d), both none %d%n",
                    botSpec, botTurns, botCellsMatch, botCellsTop, 100.0 * botCellsMatch / Math.max(1, botCellsTop), botDisruptAgree, botDisruptTop, botDisruptBot, botDisruptBothNone);
            System.out.println("bot placements:        " + botStats);
        }
        System.out.println();
        for (String g : perGame.subList(0, Math.min(30, perGame.size()))) System.out.println("  " + g);
    }

    private static final PlacementStats topStats = new PlacementStats(), botStats = new PlacementStats();

    /** Where do placed cells go: town regions, regions shared with the opponent, unstable regions, terrain cost, completing a connection. */
    static final class PlacementStats {
        long cells, townRegion, oppInRegion, ownOnlyRegion, emptyRegion, unstable, cost1, cost2, cost3, adjacentOwn, onActivePath, completes, turnsWithPlacement, turns;

        void add(GameState s, Set<Integer> placed, int me, Pathing pathing) {
            turns++;
            if (placed.isEmpty()) return;
            turnsWithPlacement++;
            int[] mine = new int[s.regionCount], opp = new int[s.regionCount];
            for (int c = 0; c < s.size; c++) {
                if (s.owner[c] == me) mine[s.regionId[c]]++;
                else if (s.owner[c] == 1 - me) opp[s.regionId[c]]++;
            }
            int[] inc0 = new int[2], inc1 = new int[2];
            Rules.incomePerTurn(s, inc0);
            GameState sim = s.copy();
            for (int c : placed) if (sim.canBuild(c)) sim.owner[c] = me;
            Rules.updateConnections(sim, pathing);
            Rules.incomePerTurn(sim, inc1);
            boolean gained = inc1[me] > inc0[me];
            for (int c : placed) {
                if (!s.canBuild(c)) continue;
                cells++;
                int r = s.regionId[c];
                if (s.regionHasTown(r)) townRegion++;
                else if (opp[r] > 0) oppInRegion++;
                else if (mine[r] > 0) ownOnlyRegion++;
                else emptyRegion++;
                if (s.regionInstability[r] > 0) unstable++;
                int cost = s.placeCost(c);
                if (cost == 1) cost1++; else if (cost == 2) cost2++; else cost3++;
                boolean adj = false;
                for (int d = 0; d < 4; d++) {
                    int nx = s.x(c) + GameState.DX[d], ny = s.y(c) + GameState.DY[d];
                    if (nx < 0 || ny < 0 || nx >= s.width || ny >= s.height) continue;
                    if (s.owner[s.cell(nx, ny)] == me) adj = true;
                }
                if (adj) adjacentOwn++;
                for (int p = 0; p < s.pairCount; p++) {
                    if (!s.pairActive(p)) continue;
                    for (int i = 0; i < s.pairPathLen[p]; i++) if (s.pairPath[p][i] == c) { onActivePath++; p = s.pairCount; break; }
                }
            }
            if (gained) completes++;
        }

        @Override
        public String toString() {
            double n = Math.max(1, cells);
            return String.format("cells=%d (%.2f/turn, placed on %.0f%% of turns) townRegion %.0f%% | oppInRegion %.0f%% | ownOnlyRegion %.0f%% | emptyRegion %.0f%% | unstable %.0f%% | cost1/2/3 %.0f/%.0f/%.0f%% | adjacentOwn %.0f%% | onActivePath %.0f%% | turns raising own income %.0f%%",
                    cells, (double) cells / Math.max(1, turns), 100.0 * turnsWithPlacement / Math.max(1, turns), 100 * townRegion / n, 100 * oppInRegion / n, 100 * ownOnlyRegion / n, 100 * emptyRegion / n,
                    100 * unstable / n, 100 * cost1 / n, 100 * cost2 / n, 100 * cost3 / n, 100 * adjacentOwn / n, 100 * onActivePath / n, 100.0 * completes / Math.max(1, turnsWithPlacement));
        }
    }

    private static String regionKind(GameState s, int region, int me) {
        int mine = 0, opp = 0, neutral = 0;
        for (int c = 0; c < s.size; c++) {
            if (s.regionId[c] != region) continue;
            if (s.owner[c] == me) mine++;
            else if (s.owner[c] == 1 - me) opp++;
            else if (s.owner[c] == 2) neutral++;
        }
        String pressure = "inst" + s.regionInstability[region];
        if (mine == 0 && opp == 0) return "empty/" + pressure + (neutral > 0 ? "+neutral" : "");
        if (mine == 0) return "oppOnly/" + pressure;
        if (opp == 0) return "ownOnly/" + pressure;
        return "mixed/" + pressure;
    }
}
