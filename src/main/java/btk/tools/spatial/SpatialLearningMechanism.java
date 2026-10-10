package btk.tools.spatial;

import btk.bot.neural.SpatialInitialWeights;
import btk.bot.neural.SpatialNet;
import btk.bot.neural.SpatialObservation;
import btk.bot.neural.SpatialV2Weights;
import btk.bot.neural.SpatialV3Weights;
import btk.bot.neural.SpatialV4Weights;
import btk.bot.neural.SpatialV5Weights;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Protocol;
import btk.core.Rules;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/** One exposed synthetic mechanism, with frozen inference and scripted shared-core transitions. */
public final class SpatialLearningMechanism {
    private static final Gson JSON = new GsonBuilder().serializeNulls().setPrettyPrinting().create();
    static final String[] MODELS = {"spatial-ppo-v2-init", "spatial-ppo-v2", "spatial-ppo-v3", "spatial-ppo-v4"};
    static final String[] WEIGHTS = {SpatialInitialWeights.DATA, SpatialV2Weights.DATA, SpatialV3Weights.DATA, SpatialV4Weights.DATA};
    static final double VALUE_SCORE_SCALE = 10000.;
    record Fixture(GameState root, int investment, int alternative, int[] completion) {}
    record Alias(float investmentLogit, float alternativeLogit, double investmentProbability,
                 double alternativeProbability, int greedyPaddedCell, int strictlyHigher, int tiedLegalCells) {}
    record Step(int turn, int[] ownCells, String ownAction, String opponentAction, int acceptedPaint,
                int[] score, int[] gained, boolean terminal, String stateSha256,
                String turnBlock, int[][] activePaths, float valueBefore, float valueAfter) {}
    record Trace(List<Step> steps, GameState finalState) {}
    private SpatialLearningMechanism() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("SpatialLearningMechanism fresh-output.json");
        Path out = Path.of(args[0]).toAbsolutePath().normalize();
        if (Files.exists(out)) throw new IllegalArgumentException("Refusing existing output: " + out);
        JsonObject report = report();
        Files.createDirectories(out.getParent());
        Files.writeString(out, JSON.toJson(report) + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        System.out.println("Spatial learning mechanism complete: " + out);
    }

    static JsonObject report() {
        Fixture f = fixture(); GameState root = f.root(); String initialIdentity = identity(root);
        JsonObject result = new JsonObject(); result.addProperty("schema", "spatial-learning-mechanism-v1");
        result.addProperty("kind", "exposed synthetic fixture; not policy strength or frequency evidence");
        result.addProperty("mechanism", "identical local BUILD representation despite different scripted delayed completion");
        result.addProperty("width", root.width); result.addProperty("height", root.height);
        result.addProperty("mapSha256", sha(Protocol.initBlock(root, 0).getBytes(StandardCharsets.UTF_8)));
        result.addProperty("initialStateSha256", initialIdentity); result.addProperty("initialTurnBlock", Protocol.turnBlock(root, 0));
        result.addProperty("initBlock", Protocol.initBlock(root, 0)); result.addProperty("maxTurns", root.maxTurns);
        result.addProperty("investmentCell", f.investment()); result.addProperty("alternativeCell", f.alternative());
        result.add("completionCells", JSON.toJsonTree(f.completion()));
        result.addProperty("scriptLength", 6); result.addProperty("opponent", "WAIT at every scripted transition");
        result.addProperty("scriptPolicy", "both branches spend one initial paint, then the same five legal continuation batches; no model chooses these actions");
        result.addProperty("valueWeightsSha256", sha(Base64.getDecoder().decode(SpatialV5Weights.DATA)));
        result.addProperty("valueMeaning", "V5 predicts remaining teacher-continuation score gap /10000; these script states are a transfer probe, not calibrated value targets");
        result.addProperty("valueScoreScale", VALUE_SCORE_SCALE);
        JsonArray perspectives = new JsonArray();
        for (int me = 0; me < 2; me++) {
            SpatialObservation obs = new SpatialObservation(root); obs.prepare(root, me);
            float[] a = patch(obs, f.investment()), b = patch(obs, f.alternative());
            boolean equalPatch = Arrays.equals(a, b);
            boolean noTown = noTownWithin(root, f.investment(), 2) && noTownWithin(root, f.alternative(), 2);
            require(equalPatch && noTown, "Fixture does not satisfy the exact local-alias contract");
            require(obs.buildMask[f.investment()] && obs.buildMask[f.alternative()], "Both investments must be legal");
            JsonObject perspective = new JsonObject(); perspective.addProperty("seat", me);
            perspective.addProperty("planePatchRadius", 3); perspective.addProperty("planePatchFloatCount", a.length);
            perspective.addProperty("planePatchesBitIdentical", equalPatch);
            perspective.addProperty("patchSha256", floatsSha(a)); perspective.addProperty("noTownEmbeddingWithinRadius2", noTown);
            JsonArray policies = new JsonArray();
            for (int model = 0; model < WEIGHTS.length; model++) {
                Alias alias = alias(obs, f.investment(), f.alternative(), WEIGHTS[model]);
                require(Float.floatToRawIntBits(alias.investmentLogit()) == Float.floatToRawIntBits(alias.alternativeLogit()),
                        "Frozen model distinguishes the supposedly identical local representation");
                JsonObject row = JSON.toJsonTree(alias).getAsJsonObject(); row.addProperty("model", MODELS[model]);
                row.addProperty("weightsSha256", sha(Base64.getDecoder().decode(WEIGHTS[model])));
                row.addProperty("logitDifference", (double) alias.investmentLogit() - alias.alternativeLogit());
                row.addProperty("logitsBitIdentical", true); policies.add(row);
            }
            perspective.add("policies", policies);
            Trace investment = script(f, me, f.investment()), alternative = script(f, me, f.alternative());
            for (int turn = 0; turn < 6; turn++) {
                require(investment.steps().get(turn).gained()[me] == (turn == 5 ? 15 : 0), "Unexpected investment gain");
                require(alternative.steps().get(turn).gained()[me] == 0, "Unexpected alternative gain");
            }
            perspective.add("investment", traceJson(root, investment, me));
            perspective.add("alternative", traceJson(root, alternative, me)); perspectives.add(perspective);
        }
        require(initialIdentity.equals(identity(root)), "Probe mutated the original state");
        result.add("perspectives", perspectives); result.addProperty("status", "complete"); return result;
    }

    static Fixture fixture() {
        int[] regions = new int[600]; Arrays.setAll(regions, i -> i);
        GameState root = new GameState(30,20,regions,new int[600],new int[]{2,18},new int[]{9,9},new int[][]{{1},{}});
        Rules.updateConnections(root, new Pathing(root.size));
        int investment = root.cell(10,9), alternative = root.cell(10,15);
        int[] completion = new int[14]; int count = 0;
        for (int x = 3; x <= 17; x++) if (x != 10) completion[count++] = root.cell(x,9);
        return new Fixture(root, investment, alternative, completion);
    }

    static float[] patch(SpatialObservation obs, int paddedCell) {
        int x = paddedCell % SpatialObservation.W, y = paddedCell / SpatialObservation.W;
        require(x >= 3 && x + 3 < SpatialObservation.W && y >= 3 && y + 3 < SpatialObservation.H, "Patch meets padding");
        float[] result = new float[SpatialObservation.PLANES * 49]; int at = 0;
        for (int plane = 0; plane < SpatialObservation.PLANES; plane++)
            for (int dy = -3; dy <= 3; dy++) for (int dx = -3; dx <= 3; dx++)
                result[at++] = obs.planes[plane * SpatialObservation.S + (y+dy) * SpatialObservation.W + x+dx];
        return result;
    }

    static boolean noTownWithin(GameState state, int cell, int radius) {
        for (int town = 0; town < state.townCount; town++)
            if (Math.abs(state.x(cell)-state.townX[town]) <= radius && Math.abs(state.y(cell)-state.townY[town]) <= radius) return false;
        return true;
    }

    static Alias alias(SpatialObservation obs, int a, int b, String weights) {
        SpatialNet net = new SpatialNet(weights); net.forward(obs);
        int best = -1, higher = 0, tied = 0; double max = Double.NEGATIVE_INFINITY;
        for (int cell = 0; cell < obs.buildMask.length; cell++) if (obs.buildMask[cell]) {
            require(Float.isFinite(net.buildLogits[cell]), "Nonfinite legal BUILD logit");
            if (best < 0 || net.buildLogits[cell] > max) { best = cell; max = net.buildLogits[cell]; }
            if (net.buildLogits[cell] > net.buildLogits[a]) higher++;
            if (net.buildLogits[cell] == net.buildLogits[a]) tied++;
        }
        double total = 0;
        for (int cell = 0; cell < obs.buildMask.length; cell++) if (obs.buildMask[cell]) total += Math.exp(net.buildLogits[cell]-max);
        return new Alias(net.buildLogits[a], net.buildLogits[b], Math.exp(net.buildLogits[a]-max)/total,
                Math.exp(net.buildLogits[b]-max)/total, best, higher, tied);
    }

    static Trace script(Fixture f, int me, int firstCell) {
        require(firstCell == f.investment() || firstCell == f.alternative(), "Unknown scripted investment");
        GameState state = f.root().copy(); Pathing pathing = new Pathing(state.size);
        SpatialObservation obs = new SpatialObservation(state); SpatialNet value = new SpatialNet(SpatialV5Weights.DATA);
        List<Step> steps = new ArrayList<>(); List<Action> wait = List.of(Action.waitAction());
        for (int turn = 0; turn < 6; turn++) {
            require(!Rules.gameOver(state, pathing), "Unexpected terminal before script ends");
            int[] cells = turn == 0 ? new int[]{firstCell}
                    : Arrays.copyOfRange(f.completion(), (turn-1)*3, Math.min(turn*3, f.completion().length));
            List<Action> actions = new ArrayList<>();
            for (int cell : cells) actions.add(Action.place(state.x(cell), state.y(cell)));
            obs.prepare(state, me); value.forward(obs); float before = value.value;
            Rules.TurnResult result = me == 0 ? Rules.resolve(state, actions, wait, pathing) : Rules.resolve(state, wait, actions, pathing);
            for (int seat = 0; seat < 2; seat++)
                require(result.moves[seat].error == null && result.moves[seat].skipped.isEmpty(), "Illegal scripted plan");
            require(!result.unknownActions && result.moves[me].count == cells.length, "Script expansion changed placements");
            require(result.moves[me].cost <= GameState.PAINT_PER_TURN, "Script exceeds core paint budget");
            obs.prepare(state, me); value.forward(obs); float after = value.value;
            require(Float.isFinite(before) && Float.isFinite(after), "Nonfinite V5 prediction");
            boolean terminal = Rules.gameOver(state, pathing);
            int[][] paths = new int[state.pairCount][];
            for (int pair = 0; pair < paths.length; pair++)
                paths[pair] = state.pairPath[pair] == null ? null : Arrays.copyOf(state.pairPath[pair], state.pairPathLen[pair]);
            steps.add(new Step(state.turn, cells, Action.format(actions), Action.format(wait), result.moves[me].cost,
                    state.score.clone(), result.gained.clone(), terminal, identity(state), Protocol.turnBlock(state, 0), paths, before, after));
        }
        return new Trace(steps, state);
    }

    private static JsonObject traceJson(GameState root, Trace trace, int me) {
        JsonObject result = new JsonObject(); JsonArray rows = new JsonArray();
        for (Step step : trace.steps()) {
            JsonObject row = JSON.toJsonTree(step).getAsJsonObject();
            row.addProperty("valueBeforeScoreUnits", step.valueBefore()*VALUE_SCORE_SCALE);
            row.addProperty("valueAfterScoreUnits", step.valueAfter()*VALUE_SCORE_SCALE);
            row.addProperty("valueChangeScoreUnits", ((double) step.valueAfter()-step.valueBefore())*VALUE_SCORE_SCALE);
            rows.add(row);
        }
        result.add("steps", rows); result.add("finalScore", JSON.toJsonTree(trace.finalState().score));
        result.addProperty("observedScriptScoreGapChange", ((long) trace.finalState().score[me]-trace.finalState().score[1-me])
                - ((long) root.score[me]-root.score[1-me]));
        result.addProperty("actualGameTerminal", trace.steps().get(trace.steps().size()-1).terminal());
        result.addProperty("scriptOnly", true); return result;
    }

    static String identity(GameState state) {
        return sha((state.turn + "\n" + state.maxTurns + "\n" + Protocol.initBlock(state, 0) + Protocol.turnBlock(state, 0))
                .getBytes(StandardCharsets.UTF_8));
    }
    private static String floatsSha(float[] values) {
        ByteBuffer bytes = ByteBuffer.allocate(values.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : values) bytes.putFloat(value); return sha(bytes.array());
    }
    private static String sha(byte[] data) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
