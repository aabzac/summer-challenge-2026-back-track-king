package btk.tools;

import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.referee.Replay;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Replays both players' stdout from downloaded CG games through the local engine and compares,
 * turn by turn, what the CG viewer recorded (placements, per-connection scoring, active paths)
 * with what our {@link Rules} produce. This is the ground truth for engine fidelity.
 *
 * <p>Args: pathToReplayDirOrFile [gameIdToShowInDetail | "all"]
 *
 * <p>Viewer event types (see docs/replay_format.md): 1 = track placed {player x y ?},
 * 2 = disrupt {player region}, 3 = score {player points townA townB}, 7 = active path
 * {townA townB x y x y ...}, 8 = path cell highlight {x y}.
 */
public final class CgValidate {

    public static void main(String[] args) throws Exception {
        Path path = Path.of(args.length > 0 && !args[0].isEmpty() ? args[0] : "replays/cg");
        String show = args.length > 1 ? args[1] : "";
        List<Path> files = Files.isDirectory(path) ? CgReplay.listReplays(path) : List.of(path);
        int ok = 0, total = 0;
        List<String> failures = new ArrayList<>();
        for (Path f : files) {
            total++;
            CgReplay r;
            try {
                r = CgReplay.load(f);
            } catch (RuntimeException e) {
                System.out.println(f.getFileName() + ": cannot parse: " + e);
                failures.add(f.getFileName() + ": cannot parse: " + e);
                continue;
            }
            boolean verbose = show.equals("all") || show.equals(String.valueOf(r.gameId));
            Outcome o = validate(r, verbose);
            if (o.firstDivergenceTurn < 0) ok++;
            else failures.add(String.format("%s %s vs %s: turn %d: %s", f.getFileName(), r.nicknames[0], r.nicknames[1],
                    o.firstDivergenceTurn, o.reason));
            if (verbose || o.firstDivergenceTurn >= 0 && failures.size() <= 15) {
                System.out.printf("%s: %s%n", f.getFileName(), o.firstDivergenceTurn < 0 ? "OK (" + r.turns() + " turns, final "
                        + o.finalLocal[0] + "-" + o.finalLocal[1] + " == CG " + r.finalScores[0] + "-" + r.finalScores[1] + ")"
                        : "DIVERGES at turn " + o.firstDivergenceTurn + ": " + o.reason);
            }
            if (verbose && o.replay != null) {
                Path out = Path.of("replays/cg-local", r.gameId + ".json");
                o.replay.write(out);
                System.out.println("  local replay written: " + out);
            }
        }
        System.out.printf("%n%d/%d replays reproduced exactly%n", ok, total);
        if (!failures.isEmpty()) {
            System.out.println("first divergences:");
            for (String s : failures.subList(0, Math.min(failures.size(), 25))) System.out.println("  " + s);
        }
        if (total == 0 || ok != total) throw new IllegalStateException("CG engine validation failed");
    }

    public static final class Outcome {
        public int firstDivergenceTurn = -1;
        public String reason = "";
        public int[] finalLocal = new int[2];
        public Replay replay;
    }

    public static Outcome validate(CgReplay r, boolean verbose) {
        Outcome o = new Outcome();
        GameState s = r.toState();
        s.maxTurns = Math.max(s.maxTurns, r.turns());
        Pathing pathing = new Pathing(s.size);
        Rules.updateConnections(s, pathing);
        Replay replay = new Replay(s, 0, r.nicknames[0], r.nicknames[1]);
        replay.meta.source = "cg-local";
        replay.meta.gameId = String.valueOf(r.gameId);
        String[] serverFailures = new String[2];
        for (int t = 0; t < r.turns(); t++) {
            String[] lines = r.turnStdout.get(t);
            @SuppressWarnings("unchecked")
            List<Action>[] acts = new List[2];
            boolean failedThisTurn = false;
            for (int p = 0; p < 2; p++) {
                String failure = r.failure(t, p);
                if (failure != null) {
                    // CG may retain stdout that arrived after the deadline, but the referee does
                    // not apply it. Keep the text in CgReplay and resolve this player as WAIT.
                    acts[p] = List.of(Action.waitAction());
                    serverFailures[p] = failure;
                    failedThisTurn = true;
                    continue;
                }
                try {
                    acts[p] = lines[p] == null ? List.of(Action.waitAction()) : Action.parseLine(lines[p]);
                } catch (RuntimeException e) {
                    acts[p] = List.of(Action.waitAction());
                    note(o, t, "P" + p + " unparsable stdout '" + lines[p] + "'");
                }
            }
            int[] ownerBefore = s.owner.clone();
            Rules.TurnResult tr = Rules.resolve(s, acts[0], acts[1], pathing);
            replay.addTurn(s, acts, tr);
            if (failedThisTurn) {
                Replay.Turn replayTurn = replay.turns.get(replay.turns.size() - 1);
                for (int p = 0; p < 2; p++) {
                    if (r.failure(t, p) != null) {
                        replayTurn.errors[p] = r.failure(t, p);
                        replayTurn.score[p] = -1;
                    }
                }
            }
            List<CgReplay.Event> events = t < r.turnEvents.size() ? r.turnEvents.get(t) : List.of();
            if (verbose) {
                System.out.printf("-- turn %d  P0: %s | P1: %s%n", t, lines[0], lines[1]);
                for (CgReplay.Event e : events) System.out.println("     ev " + e);
            }
            compareTurn(s, ownerBefore, tr, events, t, o, verbose);
            if (o.firstDivergenceTurn >= 0 && !verbose) break;
            boolean over = Rules.gameOver(s, pathing);
            boolean cgOver = t == r.turns() - 1;
            if (failedThisTurn && !cgOver && o.firstDivergenceTurn < 0) {
                note(o, t, "server failure recorded before the final CG turn");
            } else if (!failedThisTurn && over != cgOver && o.firstDivergenceTurn < 0) {
                note(o, t, over ? "local says game over (no pair connectable), CG continued to turn " + r.turns()
                        : "CG ended here, local would continue (pairs still connectable)");
            }
        }
        o.finalLocal[0] = s.score[0];
        o.finalLocal[1] = s.score[1];
        for (int p = 0; p < 2; p++) if (serverFailures[p] != null) o.finalLocal[p] = -1;
        replay.meta.score = o.finalLocal.clone();
        replay.meta.errors = serverFailures.clone();
        if (serverFailures[0] != null && serverFailures[1] != null) replay.meta.winner = -1;
        else if (serverFailures[0] != null) replay.meta.winner = 1;
        else if (serverFailures[1] != null) replay.meta.winner = 0;
        else replay.meta.winner = o.finalLocal[0] > o.finalLocal[1] ? 0
                : o.finalLocal[1] > o.finalLocal[0] ? 1 : -1;
        if (o.firstDivergenceTurn < 0
                && (r.finalScores[0] != o.finalLocal[0] || r.finalScores[1] != o.finalLocal[1])) {
            note(o, r.turns(), "final score local " + o.finalLocal[0] + "-" + o.finalLocal[1] + " vs CG "
                    + r.finalScores[0] + "-" + r.finalScores[1]);
        }
        o.replay = replay;
        return o;
    }

    private static void compareTurn(GameState s, int[] ownerBefore, Rules.TurnResult tr,
                                    List<CgReplay.Event> events, int t, Outcome o, boolean verbose) {
        // 1) placements: viewer type 1 = {player x y ?}
        Set<String> cgPlaced = new HashSet<>();
        int[] cgGained = new int[2];
        List<String> cgPaths = new ArrayList<>();
        Set<Integer> cgInked = new HashSet<>();
        for (CgReplay.Event e : events) {
            int[] v = e.ints;
            if (e.type == 1 && v.length >= 3) cgPlaced.add(v[1] + "," + v[2]);
            else if (e.type == 3 && v.length >= 4) cgGained[v[0]] += v[1];
            else if (e.type == 7 && v.length >= 2) cgPaths.add(e.data.trim());
            else if (e.type == 5 && v.length >= 1) cgInked.add(v[0]);
        }
        if (!cgInked.equals(new HashSet<>(tr.inkedRegions))) {
            note(o, t, "inked regions CG=" + cgInked + " local=" + tr.inkedRegions + errs(tr));
            return;
        }
        Set<String> ourPlaced = new HashSet<>();
        for (int p = 0; p < 2; p++) {
            Rules.PlayerMoves m = tr.moves[p];
            if (m.error != null) continue;
            for (int i = 0; i < m.count; i++) ourPlaced.add(s.x(m.cells[i]) + "," + s.y(m.cells[i]));
        }
        if (!cgPlaced.isEmpty() || !ourPlaced.isEmpty()) {
            if (!cgPlaced.equals(ourPlaced)) {
                note(o, t, "placements CG=" + sorted(cgPlaced) + " local=" + sorted(ourPlaced)
                        + errs(tr));
                return;
            }
        }
        // 2) scoring
        if (cgGained[0] != tr.gained[0] || cgGained[1] != tr.gained[1]) {
            note(o, t, "gained CG=" + Arrays.toString(cgGained) + " local=" + Arrays.toString(tr.gained)
                    + " (local paths: " + describePaths(s) + ")");
            return;
        }
        // 3) active paths (only when the viewer emitted them this turn)
        if (!cgPaths.isEmpty()) {
            Set<String> ours = new HashSet<>();
            for (int p = 0; p < s.pairCount; p++) {
                if (s.pairPath[p] == null) continue;
                StringBuilder sb = new StringBuilder();
                sb.append(s.pairFrom[p]).append(' ').append(s.pairTo[p]);
                for (int i = 0; i < s.pairPathLen[p]; i++) {
                    int c = s.pairPath[p][i];
                    sb.append(' ').append(s.x(c)).append(' ').append(s.y(c));
                }
                ours.add(sb.toString());
            }
            for (String cg : cgPaths) {
                if (!ours.contains(cg)) {
                    String[] tok = cg.split("\\s+");
                    String prefix = tok[0] + " " + tok[1];
                    String local = ours.stream().filter(x -> x.startsWith(prefix + " ")).findFirst().orElse("<inactive>");
                    note(o, t, "path " + prefix + " CG=[" + cg + "] local=[" + local + "]");
                    return;
                }
            }
        }
    }

    private static String errs(Rules.TurnResult tr) {
        String e = "";
        if (tr.moves[0].error != null) e += " P0err=" + tr.moves[0].error;
        if (tr.moves[1].error != null) e += " P1err=" + tr.moves[1].error;
        return e;
    }

    private static String describePaths(GameState s) {
        StringBuilder sb = new StringBuilder();
        for (int p = 0; p < s.pairCount; p++) {
            if (s.pairPath[p] == null) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(s.pairName(p)).append(" len ").append(s.pairPathLen[p]);
        }
        return sb.length() == 0 ? "none" : sb.toString();
    }

    private static List<String> sorted(Set<String> s) {
        List<String> l = new ArrayList<>(s);
        l.sort(null);
        return l;
    }

    private static void note(Outcome o, int turn, String reason) {
        if (o.firstDivergenceTurn < 0) {
            o.firstDivergenceTurn = turn;
            o.reason = reason;
        }
    }
}
