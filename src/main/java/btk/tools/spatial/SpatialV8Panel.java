package btk.tools.spatial;

import btk.bot.Bot;
import btk.bot.BotFactory;
import btk.bot.neural.SpatialNet;
import btk.bot.neural.SpatialObservation;
import btk.bot.neural.SpatialV4Weights;
import btk.bot.neural.SpatialV8Bot;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Protocol;
import btk.core.Rules;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fixed saved-trajectory timing, followed by a separate Rules.resolve reconstruction. */
public final class SpatialV8Panel {
    private static final Gson JSON = new GsonBuilder().serializeNulls().setPrettyPrinting().create();
    private static final int[] TURNS = {0, 25, 50, 75, 99};
    private static final String V4_SHA = "89de11f4239844c55c6e61c987ee2443fd5d79a1c71af782d2686e836ec6f3ef";
    private record Position(long seed, String mapHash, GameState state) {}
    private record Plan(int[] cells, int target) {}
    private record Priors(float[] build, float[] disrupt) {}
    private record Greedy(Plan base, Plan alternative, int rootSelected, int newCell, Priors root,
                          int alternativeDecisions, boolean alternativeStopped, int alternativePaint,
                          List<Map<String, Object>> alternativeTrace) {}
    private SpatialV8Panel() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("SpatialV8Panel v4-corpus fresh-output-directory");
        Path corpus = Path.of(args[0]).toAbsolutePath().normalize(), out = Path.of(args[1]);
        Files.createDirectories(out.toAbsolutePath().getParent()); Files.createDirectory(out);
        Map<String, Object> panel = new LinkedHashMap<>();
        panel.put("experiment", "spatial-ppo-v8"); panel.put("status", "running");
        panel.put("weightsSha256", sha(Base64.getDecoder().decode(SpatialV4Weights.DATA)));
        panel.put("policyWeights", "unchanged spatial-ppo-v4; no value use or fitting");
        Map<String, String> params = BotFactory.configuration("spatial-ppo-v8");
        panel.put("params", params); panel.put("sourceManifestSha256", sha(Files.readAllBytes(corpus.resolve("manifest.json"))));
        panel.put("requestedTurns", TURNS); panel.put("requestedPositions", 20);
        panel.put("phases", new String[]{"baseline", "off", "ample", "nominal"});
        panel.put("ampleBudgetMs", 60000); panel.put("nominalBudgetMs", 36); panel.put("warmupMsPerMapSeatPhase", 100);
        panel.put("scheduledCheckpoints", new int[]{2, 4, 6, 8});
        panel.put("checkpointSemantics", "Same virtual horizon for both candidates; earlier publication when both terminal; cutoff retains deepest published pair, invalid search returns greedy");
        panel.put("role", "exposed v4 teacher trajectories; mechanism/timing only, not strength evidence");
        write(out.resolve("intent.json"), panel);
        List<Map<String, Object>> rows = new ArrayList<>(); panel.put("measurements", rows);
        try {
            require(V4_SHA.equals(panel.get("weightsSha256")), "Frozen v4 payload mismatch");
            require("true".equals(params.get("USE_SPATIAL_V8_SEARCH")) && "12".equals(params.get("SPATIAL_V8_MIN_SEARCH_MS"))
                    && "8".equals(params.get("SPATIAL_V8_DEPTH")) && "2".equals(params.get("SPATIAL_RESERVE_MS")), "Wrong fixed v8 parameters");
            List<Map<String, Object>> maps = new ArrayList<>(); panel.put("maps", maps);
            List<Position> positions = load(corpus, maps); panel.put("positions", positions.size());
            List<String> baseline = measure(positions, "spatial-ppo-v4", "baseline", 60000, rows, null);
            List<String> off = measure(positions, "spatial-ppo-v8?USE_SPATIAL_V8_SEARCH=0", "off", 60000, rows, baseline);
            require(baseline.equals(off), "Search-off differs from frozen v4");
            int ampleStart = rows.size();
            List<String> ample = measure(positions, "spatial-ppo-v8", "ample", 60000, rows, baseline);
            int nominalStart = rows.size();
            List<String> nominal = measure(positions, "spatial-ppo-v8", "nominal", 36, rows, baseline);
            for (int i = 0; i < nominal.size(); i++) {
                Map<String, Object> n = rows.get(nominalStart + i);
                n.put("matchesV8AmpleFinal", nominal.get(i).equals(ample.get(i)));
                compareNominalCheckpoint(n, rows.get(ampleStart + i), positions);
            }
            panel.put("summary", summaries(rows));
            // No reconstruction, additional policy inference or Rules.resolve audit occurs
            // between the four timing phases. The bot's simulator is not used below.
            panel.put("trajectoryAudit", audit(positions, rows));
            panel.put("status", "complete"); write(out.resolve("panel.json"), panel);
            System.out.println("v8 saved panel: " + positions.size() + "/20 positions; " + rows.size() + " calls");
            System.out.println(JSON.toJson(panel.get("summary")));
        } catch (Exception failure) {
            panel.put("status", "failed"); panel.put("failure", failure.toString());
            write(out.resolve("failure.json"), panel); throw failure;
        }
    }

    private static List<Position> load(Path corpus, List<Map<String, Object>> maps) throws Exception {
        JsonObject manifest = JsonParser.parseString(Files.readString(corpus.resolve("manifest.json"))).getAsJsonObject();
        require("complete".equals(manifest.get("status").getAsString()) && "corpus".equals(manifest.get("mode").getAsString()), "Incomplete/wrong corpus");
        List<Position> positions = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            JsonObject map = null, game = null; int matchingMaps = 0, matchingGames = 0;
            for (var item : manifest.getAsJsonArray("maps")) {
                JsonObject candidate = item.getAsJsonObject();
                if ("train".equals(candidate.get("split").getAsString()) && candidate.get("mapIndex").getAsInt() == index) { map = candidate; matchingMaps++; }
            }
            for (var item : manifest.getAsJsonArray("games")) {
                JsonObject candidate = item.getAsJsonObject();
                if ("train".equals(candidate.get("split").getAsString()) && candidate.get("mapIndex").getAsInt() == index
                        && candidate.get("teacherSeat").getAsInt() == 0) { game = candidate; matchingGames++; }
            }
            require(matchingMaps == 1 && matchingGames == 1, "Missing/duplicate fixed teacher-P0 game");
            String init = map.get("initBlockP0").getAsString(), hash = map.get("mapHash").getAsString();
            long seed = map.get("seed").getAsLong();
            require(seed == 12000000L + index && game.get("seed").getAsLong() == seed && hash.equals(game.get("mapHash").getAsString()), "Fixed map/game identity mismatch");
            require(sha(init.getBytes(StandardCharsets.UTF_8)).equals(hash), "Map hash mismatch");
            Path metadataPath = corpus.resolve(game.get("metadata").getAsString()).normalize();
            require(metadataPath.startsWith(corpus), "Metadata path escapes corpus");
            byte[] metadata = Files.readAllBytes(metadataPath);
            require(sha(metadata).equals(game.get("metadataSha256").getAsString()), "Metadata hash mismatch");
            JsonObject data = JsonParser.parseString(new String(metadata, StandardCharsets.UTF_8)).getAsJsonObject();
            require(data.get("seed").getAsLong() == seed && data.get("teacherSeat").getAsInt() == 0
                    && hash.equals(data.get("mapHash").getAsString()) && "train".equals(data.get("split").getAsString())
                    && "complete".equals(data.get("status").getAsString()) && data.get("terminal").getAsBoolean()
                    && game.get("id").getAsString().equals(data.get("id").getAsString()), "Recorded game lineage mismatch");
            GameState state = Protocol.readInit(new Protocol.TokenReader(new ByteArrayInputStream(init.getBytes(StandardCharsets.UTF_8)))).state;
            Pathing pathing = new Pathing(state.size); Rules.updateConnections(state, pathing);
            List<Integer> captured = new ArrayList<>();
            for (var item : data.getAsJsonArray("turns")) {
                JsonObject turn = item.getAsJsonObject();
                require(!Rules.gameOver(state, pathing) && state.turn == turn.get("turn").getAsInt()
                        && turn.get("rowStart").getAsInt() == 4 * state.turn && identity(state).equals(turn.get("preStateSha256").getAsString()), "Replay pre-state mismatch");
                for (int requested : TURNS) if (requested == state.turn) { positions.add(new Position(seed, hash, state.copy())); captured.add(requested); }
                Rules.TurnResult tr = Rules.resolve(state, Action.parseLine(turn.get("teacherActions").getAsString()),
                        Action.parseLine(turn.get("opponentActions").getAsString()), pathing);
                checkTransition(tr);
                for (int me = 0; me < 2; me++) {
                    require(state.score[me] == turn.getAsJsonArray("scoreAfter").get(me).getAsInt()
                            && tr.gained[me] == turn.getAsJsonArray("gained").get(me).getAsInt(), "Replay outcome mismatch");
                    checkRecordedPlan(state, tr.moves[me], turn.getAsJsonObject(me == 0 ? "acceptedTeacher" : "acceptedOpponent"));
                    require(turn.get(me == 0 ? "acceptedTeacher" : "acceptedOpponent").equals(turn.get(me == 0 ? "teacherExpanded" : "opponentExpanded")), "Recorded acceptance mismatch");
                }
                require(Rules.gameOver(state, pathing) == turn.get("terminalAfter").getAsBoolean(), "Replay terminal boundary mismatch");
            }
            require(Rules.gameOver(state, pathing) && state.turn == data.get("turnCount").getAsInt()
                    && state.turn == game.get("turns").getAsInt() && game.get("rowCount").getAsInt() == state.turn * 4, "Recorded game incomplete");
            for (int me = 0; me < 2; me++) require(state.score[me] == data.getAsJsonArray("score").get(me).getAsInt(), "Final replay score mismatch");
            List<Map<String, Object>> missing = new ArrayList<>();
            for (int requested : TURNS) if (!captured.contains(requested)) {
                require(requested >= state.turn, "Live requested slot was skipped");
                missing.add(Map.of("turn", requested, "reason", "at or after actual terminal", "terminalTurn", state.turn));
            }
            Map<String, Object> row = new LinkedHashMap<>(); row.put("seed", seed); row.put("mapHash", hash);
            row.put("game", game.get("id").getAsString()); row.put("metadataSha256", game.get("metadataSha256").getAsString());
            row.put("capturedTurns", captured); row.put("missingSlots", missing); row.put("terminalTurn", state.turn);
            row.put("score", state.score.clone()); row.put("terminalStateSha256", identity(state)); row.put("initBlockP0", init); maps.add(row);
        }
        return positions;
    }

    private static List<String> measure(List<Position> positions, String spec, String phase, long budget,
                                       List<Map<String, Object>> rows, List<String> baseline) throws Exception {
        List<String> result = new ArrayList<>(); long seed = -1; Bot[] bots = null;
        for (Position position : positions) {
            if (position.seed != seed) {
                seed = position.seed; bots = new Bot[]{BotFactory.create(spec), BotFactory.create(spec)};
                String warmState = identity(position.state);
                for (int me = 0; me < 2; me++) { bots[me].init(position.state, me); bots[me].warmup(position.state, me, 100); }
                require(warmState.equals(identity(position.state)), "Init/warmup mutated saved input");
            }
            for (int me = 0; me < 2; me++) {
                GameState state = position.state; String before = identity(state);
                Map<String, Object> row = new LinkedHashMap<>(); rows.add(row);
                row.put("phase", phase); row.put("spec", spec); row.put("seed", seed); row.put("mapHash", position.mapHash);
                row.put("turn", state.turn); row.put("me", me); row.put("budgetMs", budget); row.put("preStateSha256", before);
                long start = System.nanoTime(); List<Action> actions = bots[me].play(state, me, budget);
                double elapsed = (System.nanoTime() - start) / 1e6;
                String text = Action.format(actions); row.put("elapsedMs", elapsed); row.put("actions", text);
                row.put("inputUnchanged", before.equals(identity(state))); require(Boolean.TRUE.equals(row.get("inputUnchanged")), "Input mutated");
                Rules.PlayerMoves moves = expand(state, me, actions); row.put("paint", moves.cost); row.put("legal", true);
                row.put("placements", moves.count); row.put("cells", Arrays.copyOf(moves.cells, moves.count)); row.put("target", singleTarget(moves));
                if (bots[me] instanceof SpatialV8Bot bot) {
                    row.put("greedyInterrupted", bot.lastInterrupted); row.put("attempted", bot.lastSearchAttempted);
                    row.put("complete", bot.lastSearchComplete); row.put("changed", bot.lastSearchChanged); row.put("cutoff", bot.lastSearchCutoff);
                    row.put("alternativeComplete", bot.lastAlternativeComplete); row.put("duplicate", bot.lastDuplicate);
                    row.put("alternativeDecisions", bot.lastAlternativeDecisions); row.put("alternativeStopped", bot.lastAlternativeStopped);
                    row.put("alternativePaint", bot.lastAlternativePaint); row.put("rootSelected", bot.lastRootSelected);
                    row.put("cutoffPhase", bot.lastCutoffPhase); row.put("cutoffPhaseName", phaseName(bot.lastCutoffPhase));
                    row.put("searchPhase", bot.lastSearchPhase); row.put("invalidProposal", bot.lastInvalidProposal);
                    row.put("candidateEvaluations", bot.lastCandidateEvaluations); row.put("baseSteps", bot.lastBaseSteps); row.put("alternativeSteps", bot.lastAlternativeSteps);
                    row.put("checkpointCount", bot.lastCheckpointCount); row.put("committedHorizon", bot.lastCommittedHorizon);
                    row.put("reachedEnd", bot.lastReachedEnd); row.put("attemptedHorizon", bot.lastAttemptedHorizon);
                    row.put("baseWorkSteps", bot.lastBaseWorkSteps); row.put("alternativeWorkSteps", bot.lastAlternativeWorkSteps);
                    row.put("baseWorkTerminal", bot.lastBaseWorkTerminal); row.put("alternativeWorkTerminal", bot.lastAlternativeWorkTerminal);
                    List<Map<String, Object>> checkpoints = checkpoints(bot); row.put("checkpoints", checkpoints);
                    row.put("baseTerminal", bot.lastBaseTerminal); row.put("alternativeTerminal", bot.lastAlternativeTerminal);
                    row.put("baseScoreDelta", bot.lastBaseScoreDelta == Long.MIN_VALUE ? null : bot.lastBaseScoreDelta);
                    row.put("alternativeScoreDelta", bot.lastAlternativeScoreDelta == Long.MIN_VALUE ? null : bot.lastAlternativeScoreDelta);
                    row.put("baseTarget", bot.lastBaseTarget); row.put("alternativeTarget", bot.lastAlternativeTarget);
                    row.put("branchSlot", bot.lastBranchSlot); row.put("oldCell", bot.lastOldCell); row.put("newCell", bot.lastNewCell);
                    int[] baseCells = Arrays.copyOf(bot.lastBaseCells, bot.lastBasePlacements);
                    int[] alternativeCells = Arrays.copyOf(bot.lastAlternativeCells, bot.lastAlternativePlacements);
                    row.put("baseCells", baseCells); row.put("alternativeCells", alternativeCells);
                    row.put("opponentCells", Arrays.copyOf(bot.lastOpponentCells, bot.lastOpponentPlacements)); row.put("opponentTarget", bot.lastOpponentTarget);
                    row.put("ownPriorComplete", bot.lastOwnPriorComplete); row.put("opponentComplete", bot.lastOpponentComplete);
                    row.put("ownBuildPriorSha256", bot.lastOwnPriorComplete ? priorHash(bot.ownBuildPrior) : null);
                    row.put("ownDisruptPriorSha256", bot.lastOwnPriorComplete ? priorHash(bot.ownDisruptPrior) : null);
                    row.put("opponentBuildPriorSha256", bot.lastOpponentComplete ? priorHash(bot.opponentBuildPrior) : null);
                    row.put("opponentDisruptPriorSha256", bot.lastOpponentComplete ? priorHash(bot.opponentDisruptPrior) : null);
                    Plan base = new Plan(baseCells, bot.lastBaseTarget); String baseText = Action.format(actions(state, base)); row.put("greedyActions", baseText);
                    Rules.PlayerMoves baseMoves = expand(state, me, actions(state, base));
                    row.put("basePaint", baseMoves.cost); row.put("basePlacements", baseMoves.count);
                    require(singleTarget(moves) == bot.lastBaseTarget && moves.count == bot.lastPlacements, "Root disruption/output count changed");
                    if (alternativeCells.length > 0) {
                        require(bot.lastBranchSlot == 0 && bot.lastAlternativeTarget == bot.lastBaseTarget
                                && alternativeCells[0] == bot.lastNewCell && bot.lastNewCell >= 0, "Alternative root/disruption mismatch");
                        require(bot.lastOldCell == (baseCells.length == 0 ? -1 : baseCells[0])
                                && bot.lastOldCell != bot.lastNewCell, "Alternative did not choose another root BUILD");
                        Rules.PlayerMoves alt = expand(state, me, actions(state, new Plan(alternativeCells, bot.lastAlternativeTarget)));
                        row.put("alternativePaintCost", alt.cost); row.put("alternativePlacements", alt.count);
                        if (bot.lastAlternativeComplete) require(bot.lastAlternativePaint == GameState.PAINT_PER_TURN - alt.cost, "Alternative remaining paint mismatch");
                    }
                    require(!bot.lastAlternativeComplete || alternativeCells.length > 0 && bot.lastAlternativeDecisions >= 0
                            && bot.lastAlternativeDecisions <= 2 && bot.lastDuplicate == sameSet(baseCells, alternativeCells), "Alternative completion/duplicate mismatch");
                    require(!bot.lastDuplicate || bot.lastAlternativeComplete && !bot.lastSearchComplete && !bot.lastSearchChanged
                            && bot.lastCandidateEvaluations == 0, "Duplicate proposal was compared/selected");
                    require(!bot.lastSearchChanged || bot.lastSearchComplete && bot.lastAlternativeComplete && !bot.lastDuplicate
                            && !bot.lastInvalidProposal, "Partial/invalid comparison changed plan");
                    require(bot.lastSearchComplete == !checkpoints.isEmpty() && bot.lastCandidateEvaluations == 2 * checkpoints.size(), "Published-pair counters disagree");
                    require(!bot.lastSearchComplete || bot.lastBaseSteps >= 1 && bot.lastBaseSteps <= bot.lastCommittedHorizon
                            && bot.lastAlternativeSteps >= 1 && bot.lastAlternativeSteps <= bot.lastCommittedHorizon && bot.lastAlternativeComplete && !bot.lastDuplicate
                            && row.get("baseScoreDelta") != null && row.get("alternativeScoreDelta") != null, "Incomplete trajectory marked complete");
                    if (bot.lastSearchComplete) {
                        Map<String, Object> published = checkpoints.get(checkpoints.size() - 1);
                        require(bot.lastCommittedHorizon == (int) published.get("horizon"), "Committed horizon is not deepest checkpoint");
                        compareCheckpoint(row, published, "Last-published diagnostic");
                    } else require(bot.lastCommittedHorizon == 0, "No pair published but horizon set");
                    require(bot.lastSearchChanged == (bot.lastSearchComplete && !bot.lastInvalidProposal
                            && bot.lastAlternativeScoreDelta > bot.lastBaseScoreDelta), "Deepest published pair/tie/invalid choice mismatch");
                    require(!bot.lastReachedEnd || bot.lastSearchComplete && (bot.lastCommittedHorizon == 8
                            || bot.lastBaseTerminal && bot.lastAlternativeTerminal), "Full completion lacks final/terminal pair");
                    require(bot.lastBaseWorkSteps >= bot.lastBaseSteps && bot.lastAlternativeWorkSteps >= bot.lastAlternativeSteps
                            && bot.lastBaseWorkSteps <= 8 && bot.lastAlternativeWorkSteps <= 8, "Actual work/committed steps disagree");
                    require(text.equals(Action.format(actions(state, bot.lastSearchChanged ? new Plan(alternativeCells, bot.lastBaseTarget) : base))), "Returned plan differs from complete choice/fallback");
                    if (bot.lastSearchCutoff) require(bot.lastCutoffPhase >= 1 && bot.lastCutoffPhase <= 4
                            && !bot.lastReachedEnd, "Cutoff lost phase/claimed full completion");
                    if (bot.lastInvalidProposal) require(!bot.lastSearchChanged && text.equals(baseText), "Invalid proposal changed baseline");
                    if (budget == 60000) require(!bot.lastInterrupted, "Ample greedy interrupted");
                }
                if (baseline != null) row.put("matchesV4Ample", text.equals(baseline.get(result.size())));
                result.add(text);
            }
        }
        return result;
    }

    private static List<Map<String, Object>> checkpoints(SpatialV8Bot bot) {
        require(bot.lastCheckpointCount >= 0 && bot.lastCheckpointCount <= 4, "Invalid checkpoint count");
        List<Map<String, Object>> rows = new ArrayList<>(); int previous = 0;
        boolean baseTerminal = false, alternativeTerminal = false;
        for (int i = 0; i < bot.lastCheckpointCount; i++) {
            int horizon = bot.checkpointHorizon[i];
            boolean bothTerminal = bot.baseCheckpointTerminal[i] && bot.alternativeCheckpointTerminal[i];
            require(horizon > previous && horizon <= 8 && (horizon % 2 == 0 || bothTerminal), "Invalid publication horizon");
            require(horizon == previous + 2 || bothTerminal && horizon == previous + 1, "Publication skipped a completed checkpoint");
            require(!bothTerminal || i == bot.lastCheckpointCount - 1, "Published after both trajectories terminated");
            require(bot.baseCheckpointSteps[i] >= 1 && bot.baseCheckpointSteps[i] <= horizon
                    && bot.alternativeCheckpointSteps[i] >= 1 && bot.alternativeCheckpointSteps[i] <= horizon
                    && (bot.baseCheckpointTerminal[i] || bot.baseCheckpointSteps[i] == horizon)
                    && (bot.alternativeCheckpointTerminal[i] || bot.alternativeCheckpointSteps[i] == horizon), "Checkpoint actual steps/virtual horizon mismatch");
            require(!baseTerminal || bot.baseCheckpointTerminal[i], "Base terminal flag regressed");
            require(!alternativeTerminal || bot.alternativeCheckpointTerminal[i], "Alternative terminal flag regressed");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("horizon", horizon); row.put("baseScoreDelta", bot.baseCheckpointScoreDelta[i]);
            row.put("alternativeScoreDelta", bot.alternativeCheckpointScoreDelta[i]);
            row.put("baseSteps", bot.baseCheckpointSteps[i]); row.put("alternativeSteps", bot.alternativeCheckpointSteps[i]);
            row.put("baseTerminal", bot.baseCheckpointTerminal[i]); row.put("alternativeTerminal", bot.alternativeCheckpointTerminal[i]);
            row.put("prefersAlternative", bot.alternativeCheckpointScoreDelta[i] > bot.baseCheckpointScoreDelta[i]);
            rows.add(row); previous = horizon; baseTerminal = bot.baseCheckpointTerminal[i]; alternativeTerminal = bot.alternativeCheckpointTerminal[i];
        }
        return rows;
    }

    private static void compareCheckpoint(Map<String, Object> actual, Map<String, Object> expected, String context) {
        for (String field : new String[]{"baseScoreDelta", "alternativeScoreDelta", "baseSteps", "alternativeSteps"})
            require(actual.get(field) instanceof Number && ((Number) actual.get(field)).longValue() == ((Number) expected.get(field)).longValue(), context + " " + field + " mismatch");
        for (String field : new String[]{"baseTerminal", "alternativeTerminal"})
            require(expected.get(field).equals(actual.get(field)), context + " " + field + " mismatch");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> checkpointRows(Map<String, Object> measurement) {
        return (List<Map<String, Object>>) measurement.get("checkpoints");
    }

    private static void compareNominalCheckpoint(Map<String, Object> nominal, Map<String, Object> ample, List<Position> positions) {
        int horizon = (int) nominal.get("committedHorizon");
        nominal.put("matchesAmpleAtCommittedHorizon", null);
        if (horizon == 0) return;
        List<Map<String, Object>> nominalCheckpoints = checkpointRows(nominal), ampleCheckpoints = checkpointRows(ample);
        for (Map<String, Object> observed : nominalCheckpoints) {
            Map<String, Object> matching = null;
            for (Map<String, Object> expected : ampleCheckpoints)
                if (expected.get("horizon").equals(observed.get("horizon"))) matching = expected;
            require(matching != null, "Nominal published horizon absent from ample");
            compareCheckpoint(observed, matching, "Nominal/ample same-horizon");
        }
        nominal.put("publishedCheckpointsMatchAmple", true);
        require(Arrays.equals((int[]) nominal.get("baseCells"), (int[]) ample.get("baseCells"))
                && Arrays.equals((int[]) nominal.get("alternativeCells"), (int[]) ample.get("alternativeCells")), "Nominal candidate plans differ from ample");
        Position position = find(positions, ((Number) nominal.get("seed")).longValue(), (int) nominal.get("turn"));
        boolean chooseAlternative = !Boolean.TRUE.equals(nominal.get("invalidProposal"))
                && ((Number) nominal.get("alternativeScoreDelta")).longValue() > ((Number) nominal.get("baseScoreDelta")).longValue();
        Plan expected = new Plan((int[]) ample.get(chooseAlternative ? "alternativeCells" : "baseCells"), (int) ample.get("baseTarget"));
        boolean same = nominal.get("actions").equals(Action.format(actions(position.state, expected)));
        boolean invalid = Boolean.TRUE.equals(nominal.get("invalidProposal"));
        nominal.put("matchesAmpleAtCommittedHorizon", invalid ? null : same);
        nominal.put("committedChoiceRejectedAsInvalid", invalid);
        if (invalid) nominal.put("invalidFallbackMatchesAmpleGreedy", same);
        require(same, "Nominal choice differs from ample at its committed horizon/fallback");
    }

    private static Map<String, Object> audit(List<Position> positions, List<Map<String, Object>> measurements) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>(); List<Map<String, Object>> rows = new ArrayList<>();
        result.put("method", "Fresh original-state conditional alternative; independent public Rules.resolve; no SearchState or child network forwards");
        result.put("weightsSha256", V4_SHA); result.put("rows", rows);
        int compared = 0, transitions = 0, auditedCheckpoints = 0, finalDifferentFromFirst = 0;
        for (Map<String, Object> measurement : measurements) if ("ample".equals(measurement.get("phase"))) {
            Position position = find(positions, ((Number) measurement.get("seed")).longValue(), (int) measurement.get("turn"));
            GameState root = position.state; int me = (int) measurement.get("me"); String rootHash = identity(root);
            Greedy greedy = greedy(root, me);
            require(Arrays.equals(greedy.base.cells, (int[]) measurement.get("baseCells")) && greedy.base.target == (int) measurement.get("baseTarget"), "Independent greedy differs from bot baseline");
            require(greedy.rootSelected == (int) measurement.get("rootSelected") && greedy.newCell == (int) measurement.get("newCell")
                    && (greedy.newCell < 0 ? -1 : 0) == (int) measurement.get("branchSlot")
                    && (greedy.base.cells.length == 0 ? -1 : greedy.base.cells[0]) == (int) measurement.get("oldCell"), "Other legal root BUILD mismatch");
            require(priorHash(greedy.root.build).equals(measurement.get("ownBuildPriorSha256"))
                    && priorHash(greedy.root.disrupt).equals(measurement.get("ownDisruptPriorSha256")), "Own cached prior is not root-stage-zero policy");
            Map<String, Object> row = new LinkedHashMap<>(); rows.add(row);
            row.put("seed", position.seed); row.put("mapHash", position.mapHash); row.put("turn", root.turn); row.put("me", me);
            row.put("preStateSha256", rootHash); row.put("ownRootPriorSha256", priorHashes(greedy.root));
            row.put("baseActions", Action.format(actions(root, greedy.base))); row.put("rootSelected", greedy.rootSelected);
            row.put("alternativeRootCell", greedy.newCell); row.put("alternativeConditionalTrace", greedy.alternativeTrace);
            row.put("alternativeComplete", measurement.get("alternativeComplete")); row.put("duplicate", measurement.get("duplicate"));
            row.put("cutoffPhase", measurement.get("cutoffPhase"));
            boolean duplicate = greedy.alternative != null && sameSet(greedy.base.cells, greedy.alternative.cells);
            row.put("independentDuplicateSet", duplicate);
            if (greedy.alternative != null) row.put("independentAlternativeActions", Action.format(actions(root, greedy.alternative)));
            int[] generated = (int[]) measurement.get("alternativeCells");
            require(generated.length == 0 || greedy.alternative != null && generated.length <= greedy.alternative.cells.length
                    && Arrays.equals(generated, Arrays.copyOf(greedy.alternative.cells, generated.length)), "Generated alternative is not a fresh conditional prefix");
            row.put("generatedAlternativePrefixVerified", true);
            if (Boolean.TRUE.equals(measurement.get("alternativeComplete"))) {
                require(greedy.alternative != null && Arrays.equals(greedy.alternative.cells, (int[]) measurement.get("alternativeCells"))
                        && greedy.alternative.target == (int) measurement.get("alternativeTarget"), "Fresh conditional alternative differs from recorded proposal");
                require(greedy.alternativeDecisions == (int) measurement.get("alternativeDecisions")
                        && greedy.alternativeStopped == (boolean) measurement.get("alternativeStopped")
                        && greedy.alternativePaint == (int) measurement.get("alternativePaint")
                        && duplicate == (boolean) measurement.get("duplicate"), "Alternative suffix/paint/STOP/duplicate diagnostics mismatch");
            }
            row.put("completeComparison", measurement.get("complete"));
            row.put("committedHorizon", measurement.get("committedHorizon")); row.put("reachedEnd", measurement.get("reachedEnd"));
            if (!Boolean.TRUE.equals(measurement.get("complete"))) {
                row.put("reason", greedy.newCell < 0 ? "no other legal root BUILD" : duplicate ? "same unordered build set; proposal skipped"
                        : "comparison not completed; original greedy retained");
                require(!Boolean.TRUE.equals(measurement.get("changed")), "Unscored ample row changed plan");
                require(rootHash.equals(identity(root)), "Alternative audit mutated saved root"); continue;
            }
            require(greedy.alternative != null && !duplicate && Boolean.TRUE.equals(measurement.get("alternativeComplete"))
                    && Boolean.TRUE.equals(measurement.get("opponentComplete")), "Complete comparison lacks distinct complete alternative/opponent");
            Priors opponent = rootPriors(root, 1 - me);
            require(priorHash(opponent.build).equals(measurement.get("opponentBuildPriorSha256"))
                    && priorHash(opponent.disrupt).equals(measurement.get("opponentDisruptPriorSha256")), "Opponent prior not from same unchanged root");
            Plan rootOpponent = cachedPlan(root, opponent);
            require(Arrays.equals(rootOpponent.cells, (int[]) measurement.get("opponentCells")) && rootOpponent.target == (int) measurement.get("opponentTarget"), "Shared root-opponent plan mismatch");
            require(Arrays.equals(greedy.alternative.cells, (int[]) measurement.get("alternativeCells")), "Recorded alternative cells mismatch");
            row.put("opponentRootPriorSha256", priorHashes(opponent)); row.put("opponentDecisionStateSha256", rootHash);
            row.put("sharedRootOpponentActions", Action.format(actions(root, rootOpponent)));
            List<Map<String, Object>> branches = new ArrayList<>(); row.put("candidates", branches);
            List<List<Map<String, Object>>> histories = List.of(new ArrayList<>(), new ArrayList<>());
            GameState[] states = {root.copy(), root.copy()};
            Pathing[] pathings = {new Pathing(root.size), new Pathing(root.size)};
            boolean[] terminal = new boolean[2];
            List<Map<String, Object>> expectedCheckpoints = checkpointRows(measurement), independentCheckpoints = new ArrayList<>();
            row.put("checkpoints", independentCheckpoints); int checkpointIndex = 0;
            int committedHorizon = (int) measurement.get("committedHorizon");
            for (int horizon = 1; horizon <= committedHorizon; horizon++) {
                for (int candidate = 0; candidate < 2; candidate++) {
                    if (terminal[candidate]) continue;
                    GameState state = states[candidate]; Pathing pathing = pathings[candidate];
                    Plan own = candidate == 0 ? greedy.base : greedy.alternative, enemy = rootOpponent;
                    String before = identity(state); int[] beforeScore = state.score.clone();
                    if (horizon > 1) { own = cachedPlan(state, greedy.root); enemy = cachedPlan(state, opponent); }
                    require(before.equals(identity(state)), "Cached root-prior packing mutated child before simultaneous resolve");
                    List<Action> ownActions = actions(state, own), enemyActions = actions(state, enemy);
                    expand(state, me, ownActions); expand(state, 1 - me, enemyActions);
                    Map<String, Object> ownData = planData(state, own), enemyData = planData(state, enemy);
                    Rules.TurnResult tr = me == 0 ? Rules.resolve(state, ownActions, enemyActions, pathing) : Rules.resolve(state, enemyActions, ownActions, pathing);
                    checkTransition(tr); terminal[candidate] = Rules.gameOver(state, pathing); transitions++;
                    Map<String, Object> step = new LinkedHashMap<>(); histories.get(candidate).add(step);
                    step.put("virtualHorizon", horizon); step.put("actualStep", histories.get(candidate).size()); step.put("preStateSha256", before);
                    step.put("ownDecisionStateSha256", before); step.put("opponentDecisionStateSha256", before);
                    step.put("ownActions", Action.format(ownActions)); step.put("opponentActions", Action.format(enemyActions));
                    step.put("ownPlan", ownData); step.put("opponentPlan", enemyData);
                    step.put("scoreBefore", beforeScore); step.put("scoreAfter", state.score.clone()); step.put("gained", tr.gained.clone());
                    step.put("stepScoreGapDelta", ((long) tr.gained[me]) - tr.gained[1 - me]);
                    step.put("postStateSha256", identity(state)); step.put("terminal", terminal[candidate]);
                }
                if (horizon % 2 == 0 || terminal[0] && terminal[1]) {
                    require(checkpointIndex < expectedCheckpoints.size(), "Independent checkpoint missing from bot trace");
                    Map<String, Object> checkpoint = new LinkedHashMap<>(); checkpoint.put("horizon", horizon);
                    checkpoint.put("baseScoreDelta", scoreDelta(root, states[0], me)); checkpoint.put("alternativeScoreDelta", scoreDelta(root, states[1], me));
                    checkpoint.put("baseSteps", histories.get(0).size()); checkpoint.put("alternativeSteps", histories.get(1).size());
                    checkpoint.put("baseTerminal", terminal[0]); checkpoint.put("alternativeTerminal", terminal[1]);
                    Map<String, Object> expected = expectedCheckpoints.get(checkpointIndex++);
                    require((int) expected.get("horizon") == horizon, "Independent virtual horizon mismatch");
                    compareCheckpoint(checkpoint, expected, "Independent public-core checkpoint");
                    boolean prefersAlternative = scoreDelta(root, states[1], me) > scoreDelta(root, states[0], me);
                    checkpoint.put("prefersAlternative", prefersAlternative);
                    checkpoint.put("selectedActions", Action.format(actions(root, prefersAlternative ? greedy.alternative : greedy.base)));
                    checkpoint.put("baseStateSha256", identity(states[0])); checkpoint.put("alternativeStateSha256", identity(states[1]));
                    independentCheckpoints.add(checkpoint); auditedCheckpoints++;
                }
                if (terminal[0] && terminal[1]) { require(horizon == committedHorizon, "Bot continued after both terminal"); break; }
            }
            require(checkpointIndex == expectedCheckpoints.size(), "Extra unverified bot checkpoint");
            boolean expectedChoice = !Boolean.TRUE.equals(measurement.get("invalidProposal"))
                    && Boolean.TRUE.equals(independentCheckpoints.get(independentCheckpoints.size() - 1).get("prefersAlternative"));
            require(measurement.get("changed").equals(expectedChoice)
                    && measurement.get("actions").equals(Action.format(actions(root, expectedChoice ? greedy.alternative : greedy.base))), "Independent deepest-checkpoint/fallback selection mismatch");
            boolean differentFromFirst = !measurement.get("changed").equals(independentCheckpoints.get(0).get("prefersAlternative"));
            row.put("finalChoiceDifferentFromFirstCheckpoint", differentFromFirst);
            if (differentFromFirst) finalDifferentFromFirst++;
            for (int candidate = 0; candidate < 2; candidate++) {
                String choice = candidate == 0 ? "base" : "alternative"; GameState state = states[candidate];
                Map<String, Object> branch = new LinkedHashMap<>(); branches.add(branch); branch.put("choice", choice);
                branch.put("scoreGapDelta", scoreDelta(root, state, me)); branch.put("steps", histories.get(candidate));
                branch.put("actualSteps", histories.get(candidate).size()); branch.put("terminal", terminal[candidate]); branch.put("finalStateSha256", identity(state));
            }
            row.put("returnedBranch", measurement.get("changed").equals(true) ? "alternative" : "base");
            row.put("invalidatedAfterPublication", measurement.get("invalidProposal"));
            require(rootHash.equals(identity(root)), "Trajectory audit mutated saved root"); compared++;
        }
        result.put("passed", true); result.put("ampleRows", rows.size()); result.put("comparedRows", compared);
        int sameHorizonChecks = 0, sameHorizonCheckpointChecks = 0;
        for (Map<String, Object> measurement : measurements) if ("nominal".equals(measurement.get("phase"))
                && Boolean.TRUE.equals(measurement.get("publishedCheckpointsMatchAmple"))) {
            sameHorizonChecks++; sameHorizonCheckpointChecks += checkpointRows(measurement).size();
        }
        result.put("candidateTrajectories", compared * 2); result.put("publishedCheckpoints", auditedCheckpoints);
        result.put("sameHorizonChecks", sameHorizonChecks); result.put("sameHorizonCheckpointChecks", sameHorizonCheckpointChecks);
        result.put("ampleFinalChoiceDifferentFromFirstCheckpoint", finalDifferentFromFirst);
        result.put("coreTransitions", transitions); return result;
    }

    private static long scoreDelta(GameState root, GameState state, int me) {
        return ((long) state.score[me] - state.score[1 - me]) - ((long) root.score[me] - root.score[1 - me]);
    }

    private static Greedy greedy(GameState state, int me) throws Exception {
        SpatialNet net = new SpatialNet(SpatialV4Weights.DATA); SpatialObservation obs = new SpatialObservation(state); obs.prepare(state, me);
        int[] prefix = new int[3], cells = new int[3]; int count = 0, paint = GameState.PAINT_PER_TURN, rootSelected = -1, newCell = -1;
        boolean[] rootMask = null;
        boolean stopped = false; Priors root = null;
        for (int stage = 0; stage < 3; stage++) {
            obs.prefix(prefix, count, paint, stage, stopped); if (stopped || paint == 0) continue;
            net.forward(obs); finite(net.buildLogits); finite(net.disruptLogits);
            int selected = maskedBest(net.buildLogits, obs.buildMask);
            if (stage == 0) {
                root = new Priors(net.buildLogits.clone(), net.disruptLogits.clone()); rootMask = obs.buildMask.clone(); rootSelected = selected;
                int other = -1;
                for (int p = 0; p < SpatialObservation.STOP; p++) if (p != selected && rootMask[p]
                        && (other < 0 || root.build[p] > root.build[other])) other = p;
                newCell = other < 0 ? -1 : obs.nativeCell(other);
            }
            if (selected == SpatialObservation.STOP) { stopped = true; continue; }
            int cell = obs.nativeCell(selected); cells[count] = cell; prefix[count++] = selected; paint -= state.placeCost(cell);
        }
        obs.prefix(prefix, count, paint, 3, stopped); net.forward(obs); finite(net.disruptLogits);
        int disruption = maskedBest(net.disruptLogits, obs.disruptMask), target = disruption == SpatialObservation.STOP ? -1 : disruption;
        Plan base = new Plan(Arrays.copyOf(cells, count), target), alternative = null;
        List<Map<String, Object>> trace = new ArrayList<>(); int alternativeDecisions = 0, alternativePaint = GameState.PAINT_PER_TURN;
        boolean alternativeStopped = false;
        require(root != null && rootMask != null, "Ample root policy prior missing");
        if (newCell >= 0) {
            // This is a new actual proposal prefix on the original board, not a
            // child state and not cached off-prefix construction logits.
            SpatialObservation alternateObs = new SpatialObservation(state); alternateObs.prepare(state, me);
            int[] alternatePrefix = new int[3], alternateCells = new int[3]; int alternateCount = 1;
            int forced = SpatialObservation.paddedCell(state, newCell); alternatePrefix[0] = forced; alternateCells[0] = newCell;
            require(rootMask[forced] && forced != rootSelected && forced != SpatialObservation.STOP, "Forced root BUILD was not another legal action");
            trace.add(proposalStep(0, new int[0], GameState.PAINT_PER_TURN, forced, true, root.build, rootMask));
            alternativePaint -= state.placeCost(newCell);
            for (int stage = 1; stage < 3; stage++) {
                alternateObs.prefix(alternatePrefix, alternateCount, alternativePaint, stage, alternativeStopped);
                if (alternativeStopped || alternativePaint == 0) continue;
                net.forward(alternateObs); finite(net.buildLogits); alternativeDecisions++;
                int selected = maskedBest(net.buildLogits, alternateObs.buildMask);
                trace.add(proposalStep(stage, Arrays.copyOf(alternatePrefix, alternateCount), alternativePaint,
                        selected, false, net.buildLogits, alternateObs.buildMask));
                if (selected == SpatialObservation.STOP) { alternativeStopped = true; continue; }
                int cell = alternateObs.nativeCell(selected); alternateCells[alternateCount] = cell; alternatePrefix[alternateCount++] = selected;
                alternativePaint -= state.placeCost(cell);
            }
            alternative = new Plan(Arrays.copyOf(alternateCells, alternateCount), target);
            expand(state, me, actions(state, alternative));
        }
        return new Greedy(base, alternative, rootSelected, newCell, root, alternativeDecisions, alternativeStopped, alternativePaint, trace);
    }

    private static Map<String, Object> proposalStep(int stage, int[] prefix, int paint, int choice, boolean forced,
                                                     float[] logits, boolean[] mask) throws Exception {
        require(mask[choice] && Float.isFinite(logits[choice]), "Illegal/nonfinite independent proposal decision");
        Map<String, Object> row = new LinkedHashMap<>(); row.put("stage", stage); row.put("paddedPrefix", prefix);
        row.put("paintBefore", paint); row.put("selectedPadded", choice); row.put("forcedRoot", forced);
        row.put("selectedLogit", logits[choice]); row.put("buildLogitsSha256", priorHash(logits));
        byte[] bits = new byte[mask.length]; for (int i = 0; i < mask.length; i++) bits[i] = mask[i] ? (byte) 1 : 0;
        row.put("buildMaskSha256", sha(bits)); return row;
    }

    private static Priors rootPriors(GameState state, int me) {
        SpatialNet net = new SpatialNet(SpatialV4Weights.DATA); SpatialObservation obs = new SpatialObservation(state);
        obs.prepare(state, me); net.forward(obs); finite(net.buildLogits); finite(net.disruptLogits);
        return new Priors(net.buildLogits.clone(), net.disruptLogits.clone());
    }

    /** Reconstruct cached-policy readout independently, using the core's current legality. */
    private static Plan cachedPlan(GameState state, Priors priors) {
        int[] cells = new int[3]; int count = 0, paint = GameState.PAINT_PER_TURN;
        for (int stage = 0; stage < 3 && paint > 0; stage++) {
            int selected = -1;
            for (int p = 0; p <= SpatialObservation.STOP; p++) {
                boolean legal = p == SpatialObservation.STOP;
                if (!legal && p % SpatialObservation.W < state.width && p / SpatialObservation.W < state.height) {
                    int cell = state.cell(p % SpatialObservation.W, p / SpatialObservation.W); boolean chosen = false;
                    for (int i = 0; i < count; i++) if (cells[i] == cell) chosen = true;
                    legal = !chosen && state.canBuild(cell) && state.placeCost(cell) <= paint;
                }
                if (legal && (selected < 0 || priors.build[p] > priors.build[selected])) selected = p;
            }
            require(selected >= 0, "Cached build mask empty"); if (selected == SpatialObservation.STOP) break;
            int cell = state.cell(selected % SpatialObservation.W, selected / SpatialObservation.W); cells[count++] = cell; paint -= state.placeCost(cell);
        }
        int selected = -1;
        for (int r = 0; r <= SpatialObservation.STOP; r++) if (r == SpatialObservation.STOP
                || r < state.regionCount && !state.regionInked[r] && !state.regionHasTown(r))
            if (selected < 0 || priors.disrupt[r] > priors.disrupt[selected]) selected = r;
        return new Plan(Arrays.copyOf(cells, count), selected == SpatialObservation.STOP ? -1 : selected);
    }

    private static List<Action> actions(GameState state, Plan plan) {
        List<Action> result = new ArrayList<>(); for (int cell : plan.cells) result.add(Action.place(state.x(cell), state.y(cell)));
        if (plan.target >= 0) result.add(Action.disrupt(plan.target)); if (result.isEmpty()) result.add(Action.waitAction()); return result;
    }
    private static Rules.PlayerMoves expand(GameState state, int me, List<Action> actions) {
        Rules.TurnResult tr = new Rules.TurnResult(); Rules.expand(state, me, actions, new Pathing(state.size), tr.moves[me], tr);
        require(!tr.hasError(me) && tr.moves[me].skipped.isEmpty() && !tr.unknownActions, "Illegal/skipped candidate plan"); return tr.moves[me];
    }
    private static void checkTransition(Rules.TurnResult tr) {
        require(!tr.unknownActions, "Unknown transition action");
        for (int me = 0; me < 2; me++) require(!tr.hasError(me) && tr.moves[me].skipped.isEmpty(), "Core rejected/skipped transition plan");
    }
    private static int singleTarget(Rules.PlayerMoves moves) { require(moves.disrupts.size() <= 1, "Multiple disruptions"); return moves.disrupts.isEmpty() ? -1 : moves.disrupts.get(0); }
    private static void checkRecordedPlan(GameState state, Rules.PlayerMoves moves, JsonObject expected) {
        JsonArray cells = expected.getAsJsonArray("cells"); require(cells.size() == moves.count && expected.get("cost").getAsInt() == moves.cost
                && expected.get("disrupt").getAsInt() == singleTarget(moves), "Recorded core-expanded plan mismatch");
        for (int i = 0; i < moves.count; i++) require(cells.get(i).getAsInt() == SpatialObservation.paddedCell(state, moves.cells[i]), "Recorded ordered placement mismatch");
    }
    private static Map<String, Object> planData(GameState state, Plan plan) {
        int cost = 0; for (int cell : plan.cells) cost += state.placeCost(cell);
        return Map.of("nativeCells", plan.cells, "disrupt", plan.target, "cost", cost);
    }
    private static int maskedBest(float[] logits, boolean[] mask) {
        int best = -1; for (int i = 0; i < logits.length; i++) if (mask[i] && (best < 0 || logits[i] > logits[best])) best = i;
        require(best >= 0, "Empty conditional mask"); return best;
    }
    private static boolean sameSet(int[] left, int[] right) {
        if (left.length != right.length) return false;
        int[] a = left.clone(), b = right.clone(); Arrays.sort(a); Arrays.sort(b); return Arrays.equals(a, b);
    }
    private static String phaseName(int phase) {
        return switch (phase) {
            case SpatialV8Bot.NONE -> "none";
            case SpatialV8Bot.ALTERNATIVE -> "alternative";
            case SpatialV8Bot.OPPONENT -> "opponent";
            case SpatialV8Bot.BASE -> "base";
            case SpatialV8Bot.CANDIDATE -> "candidate";
            default -> throw new IllegalStateException("Unknown v8 search phase: " + phase);
        };
    }
    private static void finite(float[] values) { for (float value : values) require(Float.isFinite(value), "Non-finite cached policy prior"); }
    private static String priorHash(float[] values) throws Exception {
        finite(values); ByteBuffer buffer = ByteBuffer.allocate(values.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : values) buffer.putFloat(value); return sha(buffer.array());
    }
    private static Map<String, String> priorHashes(Priors priors) throws Exception { return Map.of("build", priorHash(priors.build), "disrupt", priorHash(priors.disrupt)); }
    private static Position find(List<Position> positions, long seed, int turn) {
        for (Position p : positions) if (p.seed == seed && p.state.turn == turn) return p; throw new IllegalStateException("Missing saved position");
    }
    private static List<Map<String, Object>> summaries(List<Map<String, Object>> rows) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (String phase : new String[]{"baseline", "off", "ample", "nominal"}) {
            List<Double> times = new ArrayList<>(); Map<String, Object> result = new LinkedHashMap<>(); result.put("phase", phase);
            int attempted = 0, complete = 0, changed = 0, cutoff = 0, interrupted = 0, over = 0, different = 0, steps = 0;
            int alternatives = 0, duplicates = 0, invalid = 0, suffixForwards = 0;
            int reachedEnd = 0, publishedPairs = 0, committedSteps = 0, cutoffWithPair = 0, cutoffWithoutPair = 0, sameHorizonCompared = 0;
            Map<String, Integer> horizons = new LinkedHashMap<>(); for (int h = 0; h <= 8; h++) horizons.put(Integer.toString(h), 0);
            Map<String, Integer> cutoffsByPhase = new LinkedHashMap<>();
            for (int code = SpatialV8Bot.ALTERNATIVE; code <= SpatialV8Bot.CANDIDATE; code++) cutoffsByPhase.put(phaseName(code), 0);
            for (var row : rows) if (phase.equals(row.get("phase"))) {
                double ms = (Double) row.get("elapsedMs"); times.add(ms); if (ms > ((Number) row.get("budgetMs")).doubleValue()) over++;
                attempted += Boolean.TRUE.equals(row.get("attempted")) ? 1 : 0; complete += Boolean.TRUE.equals(row.get("complete")) ? 1 : 0;
                changed += Boolean.TRUE.equals(row.get("changed")) ? 1 : 0; cutoff += Boolean.TRUE.equals(row.get("cutoff")) ? 1 : 0;
                interrupted += Boolean.TRUE.equals(row.get("greedyInterrupted")) ? 1 : 0;
                different += Boolean.FALSE.equals(row.get("matchesV8AmpleFinal")) ? 1 : 0;
                reachedEnd += Boolean.TRUE.equals(row.get("reachedEnd")) ? 1 : 0;
                sameHorizonCompared += Boolean.TRUE.equals(row.get("matchesAmpleAtCommittedHorizon")) ? 1 : 0;
                alternatives += Boolean.TRUE.equals(row.get("alternativeComplete")) ? 1 : 0;
                duplicates += Boolean.TRUE.equals(row.get("duplicate")) ? 1 : 0;
                invalid += Boolean.TRUE.equals(row.get("invalidProposal")) ? 1 : 0;
                if (row.containsKey("alternativeDecisions")) suffixForwards += ((Number) row.get("alternativeDecisions")).intValue();
                if (Boolean.TRUE.equals(row.get("cutoff"))) {
                    String cutoffPhase = phaseName(((Number) row.get("cutoffPhase")).intValue());
                    require(cutoffsByPhase.containsKey(cutoffPhase), "Cutoff lacks a search phase");
                    cutoffsByPhase.put(cutoffPhase, cutoffsByPhase.get(cutoffPhase) + 1);
                    if (Boolean.TRUE.equals(row.get("complete"))) cutoffWithPair++; else cutoffWithoutPair++;
                }
                if (row.containsKey("baseWorkSteps")) {
                    steps += ((Number) row.get("baseWorkSteps")).intValue() + ((Number) row.get("alternativeWorkSteps")).intValue();
                    committedSteps += ((Number) row.get("baseSteps")).intValue() + ((Number) row.get("alternativeSteps")).intValue();
                    publishedPairs += ((Number) row.get("checkpointCount")).intValue();
                    String horizon = row.get("committedHorizon").toString(); horizons.put(horizon, horizons.get(horizon) + 1);
                }
            }
            times.sort(Double::compare); require(!times.isEmpty(), "Empty panel phase");
            result.put("calls", times.size()); result.put("attempted", attempted); result.put("complete", complete); result.put("changed", changed);
            result.put("cutoff", cutoff); result.put("greedyInterrupted", interrupted); result.put("callsOverBudget", over);
            result.put("differentFromAmpleFinal", different); result.put("searchTransitions", steps);
            result.put("reachedEnd", reachedEnd); result.put("publishedPairs", publishedPairs); result.put("committedTransitions", committedSteps);
            result.put("committedHorizonCounts", horizons); result.put("cutoffsWithCommittedPair", cutoffWithPair);
            result.put("cutoffsWithoutCommittedPair", cutoffWithoutPair); result.put("sameHorizonCompared", sameHorizonCompared);
            result.put("alternativesComplete", alternatives); result.put("duplicates", duplicates); result.put("invalidProposals", invalid);
            result.put("alternativeSuffixDecisions", suffixForwards); result.put("cutoffsByPhase", cutoffsByPhase);
            result.put("meanMs", times.stream().mapToDouble(Double::doubleValue).average().orElseThrow());
            result.put("p95Ms", times.get((int) Math.ceil(times.size() * .95) - 1)); result.put("maxMs", times.get(times.size() - 1)); results.add(result);
        }
        return results;
    }
    private static String identity(GameState state) throws Exception { return sha((Protocol.initBlock(state, 0) + state.turn + "\n" + state.maxTurns + "\n" + Protocol.turnBlock(state, 0)).getBytes(StandardCharsets.UTF_8)); }
    private static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static void write(Path path, Object value) throws Exception { Files.writeString(path, JSON.toJson(value), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); }
}
