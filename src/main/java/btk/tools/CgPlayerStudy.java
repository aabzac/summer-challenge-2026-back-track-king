package btk.tools;

import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import com.google.gson.GsonBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Validated, player-specific arena diagnostics. Counterfactuals last ONE simultaneous turn only. */
public final class CgPlayerStudy {
    public static final class Report {
        public int schemaVersion = 1;
        public String createdAt = Instant.now().toString();
        public String input;
        public int agentId;
        public String method = "Exact CG validation first; accepted actions from Rules.resolve; zero-based turns. "
                + "Counterfactuals keep the opponent's recorded SAME-turn actions and reset to the actual state "
                + "next turn. Immediate score-difference deltas are not match wins or additive lost final points. "
                + "Agent id identifies the server entry, not an independently verified source-code hash.";
        public List<Game> games = new ArrayList<>();
    }

    public static final class Game {
        public long gameId;
        public String file, seed;
        public String[] names;
        public int[] agentIds, scores;
        public int me, turns, width, height, towns, pairs;
        public String outcome;
        public String[] failures = new String[2];
        // These arrays are P0/P1, not me/opponent.
        public int[] firstIncome = {-1, -1}, firstDisrupt = {-1, -1};
        public int[] cells = new int[2], paint = new int[2], placementTurns = new int[2], disruptTurns = new int[2];
        public int[] skippedActions = new int[2], acceptedCellsNeverEarned = new int[2];
        public List<Turn> trace = new ArrayList<>();
    }

    public static final class Turn {
        public int turn;
        public int[] beforeScore, incomeBefore, gained, score;
        public String[] actions, failures;
        public int[] cells = new int[2], paint = new int[2], disrupt = {-1, -1};
        public int[] inkedOwnTracks = new int[2], builtThenInked = new int[2];
        public List<Integer> inkedRegions;
        public boolean gameOver;
        public boolean counterfactualsAvailable;
        public int withoutMyPlacementDelta, withoutMyDisruptDelta, withoutOpponentDisruptDelta;
        public boolean withoutMyPlacementGameOver, withoutMyDisruptGameOver;
        public int bestImmediateDisrupt = -1, bestImmediateDisruptDelta;
        public boolean bestImmediateDisruptGameOver;
    }

    public static void main(String[] args) throws Exception {
        Path input = ReplayPaths.resolve(Path.of(args.length > 0 && !args[0].isBlank() ? args[0] : "replays/cg"));
        if (args.length < 2 || args[1].isBlank()) throw new IllegalArgumentException("cgAnalyze requires -Pagent=<agentId>");
        int agent = Integer.parseInt(args[1]);
        if (agent <= 0) throw new IllegalArgumentException("Use a positive arena agent id, not a boss");
        Report report = new Report();
        report.input = input.toString();
        report.agentId = agent;
        List<Path> files = Files.isDirectory(input) ? CgReplay.listReplays(input) : List.of(input);
        for (Path file : files) {
            CgReplay replay = CgReplay.load(file);
            int me = playerIndex(replay, agent);
            if (me < 0 || replay.isBossGame()) continue;
            Game game = analyze(replay, me);
            game.file = file.toString();
            report.games.add(game);
            System.out.printf("%d %-4s P%d vs %-16s %d-%d turns=%d first income=%d/%d first disrupt=%d/%d%n",
                    game.gameId, game.outcome, me, game.names[1 - me], game.scores[me], game.scores[1 - me],
                    game.turns, game.firstIncome[me], game.firstIncome[1 - me],
                    game.firstDisrupt[me], game.firstDisrupt[1 - me]);
        }
        if (report.games.isEmpty()) throw new IllegalStateException("No validated arena games for agent " + agent);
        Path output = Path.of(args.length > 2 && !args[2].isBlank() ? args[2]
                : "build/analysis/agent-" + agent + ".json");
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        for (String outcome : List.of("win", "loss", "draw")) {
            List<Game> subset = report.games.stream().filter(g -> g.outcome.equals(outcome)).toList();
            int turns = 0, placements = 0, disruption = 0, paint = 0, oppPaint = 0, cells = 0, unused = 0;
            for (Game g : subset) {
                turns += g.turns;
                placements += g.placementTurns[g.me];
                disruption += g.disruptTurns[g.me];
                paint += g.paint[g.me];
                oppPaint += g.paint[1 - g.me];
                cells += g.cells[g.me];
                unused += g.acceptedCellsNeverEarned[g.me];
            }
            System.out.printf("%s: %d games, avg turns %.1f; place %.1f%%, disrupt %.1f%%; paint %.2f vs %.2f; "
                            + "accepted cells never earning %d/%d%n", outcome, subset.size(),
                    (double) turns / Math.max(1, subset.size()), 100.0 * placements / Math.max(1, turns),
                    100.0 * disruption / Math.max(1, turns), (double) paint / Math.max(1, turns),
                    (double) oppPaint / Math.max(1, turns), unused, cells);
        }
        System.out.println("report: " + output);
    }

    static int playerIndex(CgReplay replay, int agent) {
        boolean p0 = replay.agentIds[0] == agent, p1 = replay.agentIds[1] == agent;
        if (p0 && p1) throw new IllegalArgumentException("Ambiguous self-play agent " + agent);
        return p0 ? 0 : p1 ? 1 : -1;
    }

    static List<Action> effectiveActions(CgReplay replay, int turn, int player) {
        String line = replay.turnStdout.get(turn)[player];
        return replay.failure(turn, player) != null || line == null
                ? List.of(Action.waitAction()) : Action.parseLine(line);
    }

    static Game analyze(CgReplay replay, int me) {
        CgValidate.Outcome validation = CgValidate.validate(replay, false);
        if (validation.firstDivergenceTurn >= 0) throw new IllegalStateException("Cannot analyze " + replay.gameId
                + ": diverges at turn " + validation.firstDivergenceTurn + ": " + validation.reason);
        Game g = new Game();
        g.gameId = replay.gameId;
        g.seed = replay.seed;
        g.me = me;
        g.names = replay.nicknames.clone();
        g.agentIds = replay.agentIds.clone();
        g.scores = validation.finalLocal.clone();
        g.outcome = validation.replay.meta.winner < 0 ? "draw" : validation.replay.meta.winner == me ? "win" : "loss";
        GameState state = replay.toState();
        g.width = state.width; g.height = state.height; g.towns = state.townCount; g.pairs = state.pairCount;
        Pathing pathing = new Pathing(state.size);
        Rules.updateConnections(state, pathing);
        boolean[][] placed = new boolean[2][state.size], earned = new boolean[2][state.size];
        for (int t = 0; t < replay.turns(); t++) {
            List<Action> a0 = effectiveActions(replay, t, 0), a1 = effectiveActions(replay, t, 1);
            Turn row = inspectTurn(state, a0, a1, me,
                    replay.failure(t, 0) == null && replay.failure(t, 1) == null);
            row.failures = new String[]{replay.failure(t, 0), replay.failure(t, 1)};
            // Always advance from the recorded actions, never from a counterfactual branch.
            Rules.TurnResult result = Rules.resolve(state, a0, a1, pathing);
            for (int p = 0; p < 2; p++) {
                if (row.failures[p] != null) g.failures[p] = row.failures[p];
                if (g.firstIncome[p] < 0 && result.gained[p] > 0) g.firstIncome[p] = t;
                if (g.firstDisrupt[p] < 0 && row.disrupt[p] >= 0) g.firstDisrupt[p] = t;
                g.cells[p] += row.cells[p]; g.paint[p] += row.paint[p];
                if (row.cells[p] > 0) g.placementTurns[p]++;
                if (row.disrupt[p] >= 0) g.disruptTurns[p]++;
                g.skippedActions[p] += result.moves[p].skipped.size();
                for (int i = 0; i < result.moves[p].count; i++) placed[p][result.moves[p].cells[i]] = true;
            }
            for (int pair = 0; pair < state.pairCount; pair++) {
                if (!state.pairActive(pair)) continue;
                for (int i = 0; i < state.pairPathLen[pair]; i++) {
                    int c = state.pairPath[pair][i], owner = state.owner[c];
                    if (owner == 0 || owner == 1) earned[owner][c] = true;
                }
            }
            g.trace.add(row);
        }
        g.turns = state.turn;
        for (int p = 0; p < 2; p++) for (int c = 0; c < state.size; c++) {
            if (placed[p][c] && !earned[p][c]) g.acceptedCellsNeverEarned[p]++;
        }
        return g;
    }

    private record Branch(GameState state, Rules.TurnResult result, boolean over) {}

    private static Branch resolve(GameState before, List<Action> a0, List<Action> a1) {
        GameState state = before.copy();
        Pathing pathing = new Pathing(state.size);
        Rules.TurnResult result = Rules.resolve(state, a0, a1, pathing);
        return new Branch(state, result, Rules.gameOver(state, pathing));
    }

    private static Branch alternative(GameState before, List<Action> mine, List<Action> opponent, int me) {
        return me == 0 ? resolve(before, mine, opponent) : resolve(before, opponent, mine);
    }

    private static int diff(Branch b, int me) { return b.result.gained[me] - b.result.gained[1 - me]; }

    static Turn inspectTurn(GameState before, List<Action> a0, List<Action> a1, int me) {
        return inspectTurn(before, a0, a1, me, true);
    }

    static Turn inspectTurn(GameState before, List<Action> a0, List<Action> a1, int me, boolean counterfactuals) {
        Branch actual = resolve(before, a0, a1);
        Turn row = new Turn();
        row.turn = before.turn;
        row.beforeScore = before.score.clone();
        row.incomeBefore = new int[2];
        Rules.incomePerTurn(before, row.incomeBefore);
        row.score = actual.state.score.clone();
        row.gained = actual.result.gained.clone();
        row.actions = new String[]{Action.format(a0), Action.format(a1)};
        row.inkedRegions = actual.result.inkedRegions;
        row.gameOver = actual.over;
        row.counterfactualsAvailable = counterfactuals;
        for (int p = 0; p < 2; p++) {
            Rules.PlayerMoves move = actual.result.moves[p];
            row.cells[p] = move.count; row.paint[p] = move.cost;
            if (!move.disrupts.isEmpty()) row.disrupt[p] = move.disrupts.get(0);
            for (int i = 0; i < move.count; i++) {
                if (actual.state.inked[move.cells[i]]) row.builtThenInked[p]++;
            }
            for (int c = 0; c < before.size; c++) {
                if (before.owner[c] == p && actual.state.inked[c]) row.inkedOwnTracks[p]++;
            }
        }
        if (!counterfactuals) return row;
        List<Action> mine = me == 0 ? a0 : a1, opponent = me == 0 ? a1 : a0;
        List<Action> noBuild = without(mine, Action.Type.PLACE, Action.Type.AUTOPLACE);
        List<Action> noDisrupt = without(mine, Action.Type.DISRUPT);
        Branch noBuildBranch = alternative(before, noBuild, opponent, me);
        Branch noDisruptBranch = alternative(before, noDisrupt, opponent, me);
        row.withoutMyPlacementDelta = diff(noBuildBranch, me) - diff(actual, me);
        row.withoutMyPlacementGameOver = noBuildBranch.over;
        row.withoutMyDisruptDelta = diff(noDisruptBranch, me) - diff(actual, me);
        row.withoutMyDisruptGameOver = noDisruptBranch.over;
        row.withoutOpponentDisruptDelta = diff(alternative(before, mine,
                without(opponent, Action.Type.DISRUPT), me), me) - diff(actual, me);
        row.bestImmediateDisrupt = row.disrupt[me];
        row.bestImmediateDisruptGameOver = actual.over;
        if (row.withoutMyDisruptDelta > 0) {
            row.bestImmediateDisrupt = -1;
            row.bestImmediateDisruptDelta = row.withoutMyDisruptDelta;
            row.bestImmediateDisruptGameOver = noDisruptBranch.over;
        }
        for (int r = 0; r < before.regionCount; r++) {
            if (before.regionInked[r] || before.regionHasTown(r) || r == row.disrupt[me]) continue;
            // A lower pressure cannot change THIS turn's score (but may matter strategically).
            if (before.regionInstability[r] + 1 + (row.disrupt[1 - me] == r ? 1 : 0)
                    < GameState.INK_THRESHOLD) continue;
            List<Action> candidate = new ArrayList<>(noDisrupt);
            candidate.add(Action.disrupt(r));
            Branch branch = alternative(before, candidate, opponent, me);
            int delta = diff(branch, me) - diff(actual, me);
            if (delta > row.bestImmediateDisruptDelta) {
                row.bestImmediateDisrupt = r;
                row.bestImmediateDisruptDelta = delta;
                row.bestImmediateDisruptGameOver = branch.over;
            }
        }
        return row;
    }

    private static List<Action> without(List<Action> actions, Action.Type... types) {
        List<Action> result = new ArrayList<>();
        for (Action a : actions) if (!Arrays.asList(types).contains(a.type)) result.add(a);
        if (result.isEmpty()) result.add(Action.waitAction());
        return result;
    }
}
