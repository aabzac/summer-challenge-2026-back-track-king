package btk.tools;

import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.core.SearchState;
import java.util.ArrayList;
import java.util.List;

/** Offline one-turn closure counterfactuals. No policy or copied game rules live here. */
public final class V09ClosureProbe {
    private static final int[] EMPTY = new int[0];

    private V09ClosureProbe() {}

    /** All numeric outcomes are from {@code me}'s perspective, after scoring the resolved turn. */
    public record Outcome(boolean terminal, boolean topologyClosed, boolean turnLimitReached,
                          int scoreDifference, int ownIncome, int opponentIncome,
                          int ownScoreGain, int opponentScoreGain, int nextTurn) {}

    /**
     * {@code versusPassive} has no opposing construction or disruption. For a two-hit region,
     * {@code withJointHit} is the separate cooperative SAME-target scenario, also without
     * opposing construction. For a one-hit row, topology closure and the banked score result
     * also hold against every legal simultaneous reply: construction cannot restore a path
     * through ink and opposing disruption can only remove more potential paths. There are
     * then no active connections to score. The two-hit row remains conditional on the other hit.
     */
    public record ClosureCandidate(int region, int instability, int hitsNeeded,
                                   Outcome versusPassive, Outcome withJointHit) {}

    /**
     * Enumerates every legal region at instability two or higher whose hypothetical ink
     * removes every potential desired connection. The supplied construction is held fixed.
     * Only live pre-turn positions are accepted; pre-existing terminal states are not new
     * closing opportunities. Region order is stable and does not depend on the outcome.
     */
    public static List<ClosureCandidate> scan(GameState s, int me, int[] ownCells) {
        requireLive(s, me);
        legalMoves(s, me, ownCells, -1);
        var rows = new ArrayList<ClosureCandidate>();
        var pathing = new Pathing(s.size);
        for (int region = 0; region < s.regionCount; region++) {
            if (s.regionInstability[region] < 2) continue;
            if (!legalTarget(s, me, region)) continue;
            GameState inked = s.copy();
            Rules.ink(inked, region);
            if (Rules.anyPairPossible(inked, pathing)) continue;
            int needed = GameState.INK_THRESHOLD - s.regionInstability[region];
            if (needed < 1) throw new IllegalArgumentException("Uninked region at ink threshold: " + region);
            Outcome passive = evaluate(s, me, ownCells, region, EMPTY, -1);
            Outcome joint = needed == 2 ? evaluate(s, me, ownCells, region, EMPTY, region) : null;
            rows.add(new ClosureCandidate(region, s.regionInstability[region], needed, passive, joint));
        }
        return List.copyOf(rows);
    }

    /**
     * Resolves exactly one simultaneous turn through core against the fixed response given.
     * Inputs are concrete accepted actions: skipped, duplicate, over-budget or illegal actions
     * are rejected instead of silently changing the counterfactual. Use -1 for no disruption.
     * Topology closure and the turn limit are reported independently (both can be true).
     */
    public static Outcome evaluate(GameState s, int me, int[] ownCells, int ownTarget,
                                   int[] opponentCells, int opponentTarget) {
        requireLive(s, me);
        Rules.PlayerMoves mine = legalMoves(s, me, ownCells, ownTarget);
        Rules.PlayerMoves theirs = legalMoves(s, 1 - me, opponentCells, opponentTarget);
        SearchState work = new SearchState(s);
        boolean terminal = me == 0
                ? work.resolve(mine.cells, mine.count, ownTarget, theirs.cells, theirs.count, opponentTarget)
                : work.resolve(theirs.cells, theirs.count, opponentTarget, mine.cells, mine.count, ownTarget);
        GameState after = work.state;
        int[] income = new int[2];
        Rules.incomePerTurn(after, income);
        return new Outcome(terminal, !Rules.anyPairPossible(after, new Pathing(s.size)),
                after.turn >= after.maxTurns, after.score[me] - after.score[1 - me],
                income[me], income[1 - me], after.score[me] - s.score[me],
                after.score[1 - me] - s.score[1 - me], after.turn);
    }

    private static void requireLive(GameState s, int me) {
        if (s == null || me < 0 || me > 1) throw new IllegalArgumentException("Expected state and player 0 or 1");
        if (Rules.gameOver(s, new Pathing(s.size))) throw new IllegalArgumentException("Expected a live pre-turn state");
    }

    private static boolean legalTarget(GameState s, int me, int target) {
        Rules.TurnResult result = new Rules.TurnResult();
        Rules.PlayerMoves moves = result.moves[me];
        Rules.expand(s, me, List.of(Action.disrupt(target)), new Pathing(s.size), moves, result);
        return moves.error == null && moves.skipped.isEmpty() && moves.disrupts.size() == 1;
    }

    private static Rules.PlayerMoves legalMoves(GameState s, int me, int[] cells, int target) {
        if (cells == null || target < -1) throw new IllegalArgumentException("Expected concrete cells and target >= -1");
        var actions = new ArrayList<Action>(cells.length + 1);
        for (int cell : cells) {
            if (cell < 0 || cell >= s.size) throw new IllegalArgumentException("Invalid cell: " + cell);
            actions.add(Action.place(s.x(cell), s.y(cell)));
        }
        if (target >= 0) actions.add(Action.disrupt(target));
        if (actions.isEmpty()) actions.add(Action.waitAction());
        Rules.TurnResult result = new Rules.TurnResult();
        Rules.PlayerMoves moves = result.moves[me];
        Rules.expand(s, me, actions, new Pathing(s.size), moves, result);
        if (moves.error != null || !moves.skipped.isEmpty() || result.unknownActions
                || moves.count != cells.length || moves.disrupts.size() != (target >= 0 ? 1 : 0)) {
            throw new IllegalArgumentException("Invalid counterfactual action for player " + me
                    + ": " + moves.error + " " + moves.skipped);
        }
        return moves;
    }
}
