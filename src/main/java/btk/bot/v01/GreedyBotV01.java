package btk.bot.v01;

import btk.bot.Bot;
import btk.bot.Params;
import btk.bot.Timer;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;

import java.util.ArrayList;
import java.util.List;

/**
 * FROZEN v01 ??? reference opponent for the arena, do not edit.
 *
 * <p>Greedy: for every inactive desired pair compute the cheapest (paint) path, rank pairs by
 * projected income over the remaining turns, and spend this turn's paint along the best path.
 * Leftover paint flows to the next best pair.
 */
public final class GreedyBotV01 implements Bot {

    private final Params P;
    private final Timer timer = new Timer();
    private Pathing pathing;
    private int[] pathBuf;
    private int[] bestPath;

    public GreedyBotV01() {
        this(new Params());
    }

    public GreedyBotV01(Params params) {
        this.P = params;
    }

    @Override
    public String name() {
        String d = P.diff();
        return d.isEmpty() ? "v01" : "v01?" + d;
    }

    @Override
    public void init(GameState s, int myId) {
        ensureBuffers(s);
    }

    private void ensureBuffers(GameState s) {
        if (pathing == null || pathBuf == null || pathBuf.length < s.size) {
            pathing = new Pathing(s.size);
            pathBuf = new int[s.size];
            bestPath = new int[s.size];
        }
    }

    @Override
    public List<Action> play(GameState s, int myId, long budgetMs) {
        timer.start(budgetMs);
        ensureBuffers(s);
        List<Action> out = new ArrayList<>(3);
        int budget = GameState.PAINT_PER_TURN;
        boolean[] planned = new boolean[s.size];
        int guard = 0;
        while (budget > 0 && guard++ < 8) {
            int bestLen = choosePair(s, myId, budget, planned);
            if (bestLen < 0) break;
            int spentBefore = budget;
            for (int i = 0; i < bestLen && budget > 0; i++) {
                int c = bestPath[i];
                if (s.passable(c) || planned[c]) continue;
                int cost = s.placeCost(c);
                if (cost > budget) continue;
                planned[c] = true;
                budget -= cost;
                out.add(Action.place(s.x(c), s.y(c)));
                // Pretend the track is down so following pairs see it as free connector.
                s.owner[c] = myId;
            }
            if (budget == spentBefore) break;
        }
        if (P.MESSAGE) out.add(Action.message("t" + s.turn + " " + timer.elapsedMs() + "ms"));
        if (out.isEmpty()) out.add(Action.waitAction());
        return out;
    }

    /**
     * Picks the best inactive pair; its cheapest path is left in {@link #bestPath}.
     *
     * @return path length or -1 when nothing worth building
     */
    private int choosePair(GameState s, int myId, int budgetNow, boolean[] planned) {
        double bestValue = 0;
        int bestLen = -1;
        int turnsLeft = s.turnsLeft();
        for (int p = 0; p < s.pairCount; p++) {
            if (s.pairActive(p)) continue;
            int from = s.townCell[s.pairFrom[p]], to = s.townCell[s.pairTo[p]];
            int n = pathing.cheapestPath(s, from, to, pathBuf);
            if (n < 0) continue;
            int cost = pathing.lastCost(to);
            if (cost <= 0) continue;
            int mine = 0, opp = 0, free = 0;
            for (int i = 0; i < n; i++) {
                int c = pathBuf[i];
                int o = s.owner[c];
                if (o == myId) mine++;
                else if (o == 1 - myId) opp++;
                else if (o == GameState.NO_TRACK && !s.isTown(c)) free++;
            }
            // turns to finish: this turn's remaining paint, then 3 per turn
            int remaining = Math.max(0, cost - budgetNow);
            int turnsToFinish = (remaining + GameState.PAINT_PER_TURN - 1) / GameState.PAINT_PER_TURN;
            int earning = turnsLeft - turnsToFinish;
            if (earning <= 0) {
                if (P.SKIP_UNFINISHABLE) continue;
                earning = 0;
            }
            double value = (free + P.W_OWN * mine - P.W_OPP * opp) * earning - P.W_COST * cost;
            value /= Math.max(1, turnsToFinish + 1);
            if (value > bestValue) {
                bestValue = value;
                bestLen = n;
                System.arraycopy(pathBuf, 0, bestPath, 0, n);
            }
        }
        return bestLen;
    }
}
