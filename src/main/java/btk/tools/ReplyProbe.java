package btk.tools;

import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Core-resolution helpers shared by the offline studies; never merged into Player. No copied scoring formula. */
final class ReplyProbe {
    private static final List<Action> WAIT = List.of(Action.waitAction());
    record Reply(int[] cells, int disrupt) {}

    static List<Action> actions(GameState state, Reply reply) {
        List<Action> actions = new ArrayList<>();
        for (int cell : reply.cells) actions.add(Action.place(state.x(cell), state.y(cell)));
        if (reply.disrupt >= 0) actions.add(Action.disrupt(reply.disrupt));
        return actions.isEmpty() ? WAIT : actions;
    }

    static GameState resolve(GameState before, int me, int[] own, int ownDisrupt, Reply reply) {
        GameState after = before.copy();
        List<Action> mine = actions(before, new Reply(own, ownDisrupt)), theirs = actions(before, reply);
        Rules.TurnResult result = Rules.resolve(after, me == 0 ? mine : theirs,
                me == 1 ? mine : theirs, new Pathing(before.size));
        for (var moves : result.moves) if (moves.error != null || !moves.skipped.isEmpty()) {
            throw new IllegalStateException("Noncanonical probe actions: " + moves.error + " " + moves.skipped);
        }
        return after;
    }

    static void sameState(GameState a, GameState b) {
        if (a.turn != b.turn || !Arrays.equals(a.owner, b.owner) || !Arrays.equals(a.score, b.score)
                || !Arrays.equals(a.regionInstability, b.regionInstability)
                || !Arrays.equals(a.regionInked, b.regionInked) || !Arrays.equals(a.instability, b.instability)
                || !Arrays.equals(a.inked, b.inked) || !Arrays.equals(a.pairPathLen, b.pairPathLen)) {
            throw new IllegalStateException("Probe simulation diverges from core");
        }
        for (int p = 0; p < a.pairCount; p++) for (int i = 0; i < a.pairPathLen[p]; i++) {
            if (a.pairPath[p][i] != b.pairPath[p][i]) throw new IllegalStateException("Path mismatch");
        }
    }

    static boolean sameCells(int[] a, int[] b) {
        int[] aa = a.clone(), bb = b.clone(); Arrays.sort(aa); Arrays.sort(bb); return Arrays.equals(aa, bb);
    }
}
