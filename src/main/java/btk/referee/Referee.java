package btk.referee;

import btk.bot.Bot;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;

import java.util.List;

/**
 * Local referee. Plays a full game between two {@link Bot}s on a given initial state, recording
 * an optional {@link Replay}. Bots receive a copy of the state each turn with connections
 * already computed (mirrors what {@code CGRunner} does from the protocol).
 */
public final class Referee {

    /** Treat an invalid command line as immediate loss (CG behaviour). */
    public boolean strictInvalid = true;
    /** Report (not punish) moves slower than this. */
    public long slowMoveMs = 50;
    public long firstTurnBudgetMs = 1000;
    public long turnBudgetMs = 50;

    public static final class Result {
        public final int[] score = new int[2];
        /** 0/1 or -1 for a draw. */
        public int winner;
        public int turns;
        public final String[] error = new String[2];
        public final long[] totalMs = new long[2];
        public final long[] maxMs = new long[2];
        public final int[] slowMoves = new int[2];
        /** Accepted actions from the shared resolver, never inferred from command text. */
        public final int[] placementTurns = new int[2], disruptTurns = new int[2];
        public final int[] placedCells = new int[2], paintSpent = new int[2];
        public boolean closedEarly;
        public Replay replay;

        public String summary() {
            return String.format("score %d-%d winner=%s turns=%d maxMs=[%d,%d]%s%s",
                    score[0], score[1], winner < 0 ? "draw" : "P" + winner, turns, maxMs[0], maxMs[1],
                    error[0] != null ? " P0 error: " + error[0] : "",
                    error[1] != null ? " P1 error: " + error[1] : "");
        }
    }

    public Result play(GameState initial, Bot b0, Bot b1, long seed, boolean record) {
        GameState s = initial.copy();
        Pathing pathing = new Pathing(s.size);
        Rules.updateConnections(s, pathing);
        Result res = new Result();
        Replay replay = record ? new Replay(s, seed, b0.name(), b1.name()) : null;
        Bot[] bots = {b0, b1};
        for (int p = 0; p < 2; p++) bots[p].init(s.copy(), p);

        while (!Rules.gameOver(s, pathing)) {
            @SuppressWarnings("unchecked")
            List<Action>[] acts = new List[2];
            long budget = s.turn == 0 ? firstTurnBudgetMs : turnBudgetMs;
            for (int p = 0; p < 2; p++) {
                long t0 = System.nanoTime();
                try {
                    acts[p] = bots[p].play(s.copy(), p, budget);
                } catch (RuntimeException e) {
                    acts[p] = null;
                    res.error[p] = "exception: " + e;
                }
                long ms = (System.nanoTime() - t0) / 1_000_000L;
                res.totalMs[p] += ms;
                res.maxMs[p] = Math.max(res.maxMs[p], ms);
                if (ms > slowMoveMs) res.slowMoves[p]++;
            }
            Rules.TurnResult tr = Rules.resolve(s, acts[0], acts[1], pathing);
            for (int p = 0; p < 2; p++) {
                if (tr.moves[p].error != null && res.error[p] == null) res.error[p] = tr.moves[p].error;
                if (tr.moves[p].count > 0) res.placementTurns[p]++;
                if (!tr.moves[p].disrupts.isEmpty()) res.disruptTurns[p]++;
                res.placedCells[p] += tr.moves[p].count;
                res.paintSpent[p] += tr.moves[p].cost;
            }
            if (replay != null) replay.addTurn(s, acts, tr);
            if (strictInvalid && (res.error[0] != null || res.error[1] != null)) break;
        }
        res.turns = s.turn;
        res.closedEarly = res.error[0] == null && res.error[1] == null && s.turn < s.maxTurns
                && !Rules.anyPairPossible(s, pathing);
        res.score[0] = s.score[0];
        res.score[1] = s.score[1];
        if (strictInvalid && (res.error[0] != null || res.error[1] != null)) {
            if (res.error[0] != null && res.error[1] != null) res.winner = -1;
            else res.winner = res.error[0] != null ? 1 : 0;
        } else {
            res.winner = res.score[0] > res.score[1] ? 0 : res.score[1] > res.score[0] ? 1 : -1;
        }
        if (replay != null) {
            replay.finish(res);
            res.replay = replay;
        }
        return res;
    }
}
