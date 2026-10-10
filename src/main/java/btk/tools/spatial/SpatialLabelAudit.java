package btk.tools.spatial;

import btk.bot.Bot;
import btk.bot.BotFactory;
import btk.bot.Params;
import btk.bot.neural.SpatialBot;
import btk.bot.neural.SpatialNet;
import btk.bot.neural.SpatialObservation;
import btk.bot.neural.SpatialV4Weights;
import btk.bot.neural.SpatialV9Bot;
import btk.bot.neural.SpatialV9Net;
import btk.bot.neural.SpatialV9Weights;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Protocol;
import btk.core.Rules;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.LongSupplier;

/** Offline, write-once audit of the complete frozen v4 corpus; never calls its teacher again.
 * Counterfactuals end after one simultaneous core transition. Teacher-prefix predictions are
 * observations only; complete student plans always come from the actual own-prefix bot runtime.
 */
public final class SpatialLabelAudit {
    static final long BUDGET_MS = 60_000;
    static final String V4_SHA = "89de11f4239844c55c6e61c987ee2443fd5d79a1c71af782d2686e836ec6f3ef";
    static final String V9_SHA = "fd616a7f454792ec47d3cfc8e5a10f63becfb889d06dbd64e0dea8bf053b4cb6";
    private static final Gson JSON = new GsonBuilder().serializeNulls().create();
    private static final String[] MODELS = {"v4", "v9"};
    private static final String[] IDENTITY = {"id", "split", "mapIndex", "seed", "mapHash", "teacherSeat", "opponentSpec"};
    private SpatialLabelAudit() {}

    /** Arguments: original-v4-corpus-directory fresh-output-directory. */
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("SpatialLabelAudit corpus-directory fresh-output-directory");
        Path corpus = Path.of(args[0]).toAbsolutePath().normalize(), out = Path.of(args[1]).toAbsolutePath().normalize();
        Files.createDirectories(out.getParent()); Files.createDirectory(out); Files.createDirectory(out.resolve("games"));
        JsonObject report = new JsonObject(); JsonArray games = new JsonArray(); report.add("games", games);
        report.addProperty("schemaVersion", 1); report.addProperty("experiment", "spatial-label-audit-v1");
        report.addProperty("status", "collecting"); report.addProperty("createdAt", Instant.now().toString());
        report.addProperty("budgetMs", BUDGET_MS); report.addProperty("warmup", false);
        report.addProperty("totalTurns", 0); report.addProperty("totalRows", 0);
        report.addProperty("javaVersion", System.getProperty("java.version"));
        report.addProperty("inputHashDefinition", "BTKSD001 row bytes[0:63626] followed by little-endian int32 stage; excludes label/raw turn");
        report.addProperty("semanticHashDefinition", "SHA256 of versioned static map identity plus every dynamic array, scores, turn/maxTurns, active-path flags/lengths/prefixes; excludes unused path-buffer tails");
        writeNew(out.resolve("intent.json"), report);
        try {
            byte[] manifestBytes = Files.readAllBytes(corpus.resolve("manifest.json"));
            report.addProperty("corpusManifestSha256", sha(manifestBytes));
            JsonObject source = JsonParser.parseString(new String(manifestBytes, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject weights = new JsonObject();
            weights.addProperty("v4", sha(Base64.getDecoder().decode(SpatialV4Weights.DATA)));
            weights.addProperty("v9", sha(Base64.getDecoder().decode(SpatialV9Weights.DATA)));
            require(V4_SHA.equals(weights.get("v4").getAsString()) && V9_SHA.equals(weights.get("v9").getAsString()), "Frozen model payload mismatch");
            report.add("weightsSha256", weights);
            JsonObject params = new JsonObject();
            params.add("v4", JSON.toJsonTree(BotFactory.configuration("spatial-ppo-v4")));
            params.add("v9", JSON.toJsonTree(BotFactory.configuration("spatial-ppo-v9"))); report.add("params", params);
            List<GameInput> inputs = preflight(corpus, source);
            report.addProperty("sourcePreflightComplete", true); writeNew(out.resolve("preflight.json"), report);
            int turns = 0, rows = 0, trainTurns = 0, validationTurns = 0;
            for (GameInput input : inputs) {
                JsonObject index = identity(input.entry); String file = "games/" + index.get("id").getAsString() + ".jsonl";
                index.addProperty("file", file); index.addProperty("status", "collecting"); games.add(index);
                Model[] models = {new Model(false, SpatialV4Weights.DATA, BotFactory.parameters("spatial-ppo-v4"), System::nanoTime),
                        new Model(true, SpatialV9Weights.DATA, BotFactory.parameters("spatial-ppo-v9"), System::nanoTime)};
                MessageDigest raw = digest(); int count;
                try (DigestOutputStream sink = new DigestOutputStream(OutputStream.nullOutputStream(), raw);
                     BufferedWriter writer = Files.newBufferedWriter(out.resolve(file), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
                    SpatialTeacherExport.BinaryRows binary = new SpatialTeacherExport.BinaryRows(sink);
                    count = collectGame(input.state, input.entry, input.metadata, models, binary, writer, System::nanoTime);
                    require(binary.rows == input.entry.get("rowCount").getAsInt(), "Regenerated row count mismatch");
                }
                String regenerated = HexFormat.of().formatHex(raw.digest());
                require(regenerated.equals(input.entry.get("rawSha256").getAsString()), "Regenerated observation stream mismatch: " + input.entry.get("id"));
                index.addProperty("regeneratedRawSha256", regenerated); index.addProperty("sha256", fileHash(out.resolve(file)));
                index.addProperty("turnCount", count); index.addProperty("rowCount", count * 4); index.addProperty("status", "complete");
                turns += count; rows += count * 4;
                if (index.get("split").getAsString().equals("train")) trainTurns += count; else validationTurns += count;
                report.addProperty("totalTurns", turns); report.addProperty("totalRows", rows);
                System.out.println(index.get("id").getAsString() + " roots=" + count + " rawHash=verified");
            }
            require(turns == 5660 && rows == 22640 && trainTurns == 4548 && validationTurns == 1112, "Fixed corpus coverage mismatch");
            report.addProperty("status", "complete"); report.addProperty("completedAt", Instant.now().toString());
            writeNew(out.resolve("manifest.json"), report);
        } catch (Exception failure) {
            report.addProperty("status", "failed"); report.addProperty("failure", failure.toString());
            report.addProperty("failedAt", Instant.now().toString()); writeNew(out.resolve("manifest.json"), report); throw failure;
        }
    }

    private record GameInput(GameState state, JsonObject entry, JsonObject metadata) {}

    /** Validate the entire fixed source schedule and its file identities before a student call. */
    private static List<GameInput> preflight(Path corpus, JsonObject source) throws Exception {
        require(source.get("status").getAsString().equals("complete") && source.get("mode").getAsString().equals("corpus")
                && source.get("experiment").getAsString().equals("spatial-ppo-v4") && source.get("format").getAsString().equals("BTKSD001")
                && source.get("headerBytes").getAsInt() == 16 && source.get("rowBytes").getAsInt() == 63638
                && source.get("rows").getAsInt() == 22640 && source.get("totalTurns").getAsInt() == 5660, "Unexpected source corpus");
        JsonArray maps = source.getAsJsonArray("maps"), games = source.getAsJsonArray("games");
        require(maps.size() == 30 && games.size() == 60, "Fixed map/game schedule mismatch");
        List<GameInput> result = new ArrayList<>(); Set<String> mapHashes = new HashSet<>();
        List<SpatialTeacherExport.MapSpec> schedule = SpatialTeacherExport.schedule("corpus");
        for (int i = 0; i < schedule.size(); i++) {
            SpatialTeacherExport.MapSpec spec = schedule.get(i); JsonObject map = maps.get(i).getAsJsonObject();
            require(map.get("split").getAsString().equals(spec.split()) && map.get("mapIndex").getAsInt() == spec.index()
                    && map.get("seed").getAsLong() == spec.seed() && map.get("opponentSpec").getAsString().equals(spec.opponent()), "Source map schedule drift");
            String init = map.get("initBlockP0").getAsString(), hash = sha(init.getBytes(StandardCharsets.UTF_8));
            require(hash.equals(map.get("mapHash").getAsString()) && mapHashes.add(hash), "Invalid/duplicate map identity");
            GameState initial = Protocol.readInit(new Protocol.TokenReader(new ByteArrayInputStream(init.getBytes(StandardCharsets.UTF_8)))).state;
            require(Protocol.initBlock(initial, 0).equals(init) && initial.width == map.get("width").getAsInt()
                    && initial.height == map.get("height").getAsInt() && initial.townCount == map.get("townCount").getAsInt(), "Invalid saved init block");
            for (int seat = 0; seat < 2; seat++) {
                JsonObject game = games.get(2 * i + seat).getAsJsonObject();
                String id = spec.split() + "-" + String.format(Locale.ROOT, "%02d", spec.index()) + "-p" + seat;
                for (String key : new String[]{"split", "mapIndex", "seed", "mapHash", "opponentSpec"}) equal(map.get(key), game.get(key), "Game map identity " + key);
                require(game.get("id").getAsString().equals(id) && game.get("teacherSeat").getAsInt() == seat
                        && game.get("metadata").getAsString().equals(id + ".json") && game.get("binary").getAsString().equals(id + ".bin.gz")
                        && game.get("status").getAsString().equals("complete") && game.get("terminal").getAsBoolean()
                        && game.get("failure").isJsonNull(), "Invalid source game identity/status");
                Path metadataPath = corpus.resolve(id + ".json"), binaryPath = corpus.resolve(id + ".bin.gz");
                byte[] bytes = Files.readAllBytes(metadataPath);
                require(sha(bytes).equals(game.get("metadataSha256").getAsString())
                        && fileHash(binaryPath).equals(game.get("sha256").getAsString())
                        && Files.size(binaryPath) == game.get("bytes").getAsLong(), "Source file hash/size mismatch: " + id);
                JsonObject metadata = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
                for (String key : IDENTITY) equal(game.get(key), metadata.get(key), "Metadata identity " + key);
                require(metadata.get("teacherSpec").getAsString().equals("v09") && metadata.get("status").getAsString().equals("complete")
                        && metadata.get("terminal").getAsBoolean() && metadata.get("failure").isJsonNull()
                        && metadata.get("stopReason").getAsString().equals("actual terminal"), "Invalid source metadata status");
                int turns = game.get("turns").getAsInt(), rows = game.get("rowCount").getAsInt();
                require(turns > 0 && turns <= initial.maxTurns && rows == 4 * turns && metadata.get("turnCount").getAsInt() == turns
                        && metadata.getAsJsonArray("turns").size() == turns && metadata.get("rowCount").getAsInt() == rows
                        && game.get("uncompressedBytes").getAsLong() == 16L + 63638L * rows, "Source game coverage mismatch");
                result.add(new GameInput(initial.copy(), game, metadata));
            }
        }
        return result;
    }

    static int collectGame(GameState state, JsonObject identity, JsonObject metadata, Model[] models,
                           SpatialTeacherExport.BinaryRows binary, BufferedWriter output, LongSupplier clock) throws Exception {
        require(models.length == 2 && state.turn == 0, "Expected two frozen policies and fresh saved state");
        int seat = identity.get("teacherSeat").getAsInt(); require(seat == 0 || seat == 1, "Invalid teacher seat");
        Pathing pathing = new Pathing(state.size); Rules.updateConnections(state, pathing);
        for (Model model : models) model.bot.init(state.copy(), seat);
        SpatialObservation observation = new SpatialObservation(state);
        for (JsonElement element : metadata.getAsJsonArray("turns")) {
            JsonObject record = element.getAsJsonObject(); int turn = state.turn;
            require(!Rules.gameOver(state, pathing), "Recorded root is terminal");
            JsonObject result = auditRoot(state, seat, identity.get("opponentSpec").getAsString(), record, models, observation, binary, clock);
            for (String key : IDENTITY) result.add(key, identity.get(key));
            result.addProperty("turn", turn); output.write(JSON.toJson(result)); output.newLine();
        }
        require(Rules.gameOver(state, pathing) && state.turn == metadata.get("turnCount").getAsInt(), "Final terminal boundary mismatch");
        equal(JSON.toJsonTree(state.score), metadata.get("score"), "Final score mismatch");
        return state.turn;
    }

    /** Mutates only the original recorded trajectory, after all alternative one-step branches end. */
    static JsonObject auditRoot(GameState state, int seat, String opponentSpec, JsonObject saved, Model[] models,
                                SpatialObservation observation, SpatialTeacherExport.BinaryRows binary, LongSupplier clock) throws Exception {
        Pathing pathing = new Pathing(state.size); String before = semanticHash(state);
        require(!Rules.gameOver(state, pathing) && saved.get("turn").getAsInt() == state.turn
                && saved.get("rowStart").getAsInt() == binary.rows && saved.get("preStateSha256").getAsString().equals(recordedHash(state)), "Recorded prestate/row identity mismatch");
        List<Action> teacherActions = Action.parseLine(saved.get("teacherActions").getAsString());
        List<Action> opponentActions = Action.parseLine(saved.get("opponentActions").getAsString());
        validateCalls(saved, seat, opponentSpec);
        Rules.PlayerMoves teacher = SpatialTeacherExport.validate(state, seat, teacherActions, pathing);
        Rules.PlayerMoves opponent = SpatialTeacherExport.validate(state, 1 - seat, opponentActions, pathing);
        equal(plan(state, teacher, false), saved.get("teacherExpanded"), "Expanded teacher plan mismatch");
        equal(plan(state, opponent, false), saved.get("opponentExpanded"), "Expanded opponent plan mismatch");
        GameState next = state.copy(); Rules.TurnResult resolved = resolve(next, seat, teacherActions, opponentActions, pathing);
        equal(plan(next, resolved.moves[seat], false), saved.get("acceptedTeacher"), "Accepted teacher plan mismatch");
        equal(plan(next, resolved.moves[1 - seat], false), saved.get("acceptedOpponent"), "Accepted opponent plan mismatch");
        sameMoves(teacher, resolved.moves[seat]); sameMoves(opponent, resolved.moves[1 - seat]);
        equal(JSON.toJsonTree(resolved.gained), saved.get("gained"), "Recorded gained mismatch");
        equal(JSON.toJsonTree(next.score), saved.get("scoreAfter"), "Recorded scores mismatch");
        equal(JSON.toJsonTree(resolved.neutralised), saved.get("neutralised"), "Recorded collision events mismatch");
        equal(JSON.toJsonTree(resolved.inkedRegions), saved.get("inkedRegions"), "Recorded ink events mismatch");
        require(!resolved.unknownActions && Rules.gameOver(next, pathing) == saved.get("terminalAfter").getAsBoolean(), "Recorded terminal/unknown-action mismatch");
        JsonObject result = new JsonObject(); result.addProperty("preStateSha256", recordedHash(state)); result.addProperty("semanticRootSha256", before);
        JsonObject teacherJson = plan(state, teacher, true); addSuccessor(teacherJson, next, resolved); result.add("teacher", teacherJson);
        result.add("permutations", permutations(state, seat, teacher, opponentActions, next, resolved.gained));
        JsonObject own = new JsonObject();
        for (int m = 0; m < models.length; m++) own.add(MODELS[m], ownPlan(state, seat, models[m], teacher, opponentActions, next, resolved.gained, clock));
        result.add("ownPlans", own);
        result.add("rows", teacherRows(state, seat, teacher, saved, models, observation, binary));
        require(before.equals(semanticHash(state)), "Audit mutated the recorded root");
        state.copyFrom(next); return result;
    }

    private static void validateCalls(JsonObject saved, int seat, String opponentSpec) {
        JsonArray calls = saved.getAsJsonArray("calls"); require(calls.size() == 2, "Missing native call records");
        for (int me = 0; me < 2; me++) {
            JsonObject c = calls.get(me).getAsJsonObject(); boolean teacher = me == seat;
            double elapsed = c.get("elapsedMs").getAsDouble();
            require(c.get("me").getAsInt() == me && c.get("role").getAsString().equals(teacher ? "teacher" : "opponent")
                    && c.get("spec").getAsString().equals(teacher ? "v09" : opponentSpec) && c.get("budgetMs").getAsLong() == BUDGET_MS
                    && c.get("complete").getAsBoolean() && c.get("legal").getAsBoolean() && Double.isFinite(elapsed) && elapsed >= 0 && elapsed < BUDGET_MS,
                    "Incomplete/invalid original native call");
            equal(c.get("actions"), saved.get(teacher ? "teacherActions" : "opponentActions"), "Original call action identity mismatch");
        }
    }

    private static JsonArray teacherRows(GameState state, int seat, Rules.PlayerMoves teacher, JsonObject saved, Model[] models,
                                          SpatialObservation observation, SpatialTeacherExport.BinaryRows binary) throws IOException {
        // Reuse the frozen exporter for labels/prefix bookkeeping and raw stream generation.
        JsonObject generated = new JsonObject();
        SpatialTeacherExport.writeTeacherRows(state, seat, teacher, observation, binary, generated);
        for (String key : new String[]{"labels", "prefixes", "paintBefore", "stoppedBefore"}) equal(generated.get(key), saved.get(key), "Teacher row metadata mismatch: " + key);
        JsonArray rows = new JsonArray(); int[] labels = ints(generated.getAsJsonArray("labels"));
        // A separate null sink encodes each regenerated observation for its exact input hash.
        SpatialTeacherExport.BinaryRows hashRows = new SpatialTeacherExport.BinaryRows(OutputStream.nullOutputStream());
        for (int stage = 0; stage < 4; stage++) {
            int[] prefix = ints(generated.getAsJsonArray("prefixes").get(stage).getAsJsonArray());
            int paint = generated.getAsJsonArray("paintBefore").get(stage).getAsInt();
            boolean stopped = generated.getAsJsonArray("stoppedBefore").get(stage).getAsBoolean();
            observation.prefix(prefix, prefix.length, paint, stage, stopped);
            boolean[] mask = stage == 3 ? observation.disruptMask : observation.buildMask;
            int[] remaining = stage == 3 || prefix.length == teacher.count ? new int[]{labels[stage]}
                    : Arrays.copyOfRange(labels, prefix.length, teacher.count);
            if (stage < 3 && prefix.length == teacher.count) require(remaining[0] == SpatialObservation.STOP, "Non-STOP empty teacher remainder");
            for (int target : remaining) require(mask[target], "Remaining teacher target is not legal");
            hashRows.write(observation, labels[stage], stage, state.turn);
            JsonObject row = new JsonObject(); row.addProperty("stage", stage); row.addProperty("label", labels[stage]);
            row.add("prefix", JSON.toJsonTree(prefix)); row.addProperty("paint", paint); row.addProperty("stopped", stopped);
            row.addProperty("inputSha256", inputHash(hashRows.buffer.array(), stage)); row.add("remainingLabels", JSON.toJsonTree(remaining));
            JsonObject predictions = new JsonObject();
            for (int m = 0; m < models.length; m++) {
                models[m].forward(observation);
                predictions.add(MODELS[m], prediction(stage == 3 ? models[m].disrupt() : models[m].build(), mask, labels[stage], remaining));
            }
            row.add("models", predictions); rows.add(row);
        }
        return rows;
    }

    static JsonObject prediction(float[] logits, boolean[] mask, int label, int[] remaining) {
        require(logits.length == mask.length && label >= 0 && label < mask.length && mask[label] && remaining.length > 0, "Invalid masked prediction");
        int best = -1, count = 0; double maximum = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < logits.length; i++) {
            require(Float.isFinite(logits[i]), "Non-finite frozen policy output");
            if (mask[i]) { count++; if (best < 0 || logits[i] > maximum) { best = i; maximum = logits[i]; } }
        }
        require(count > 0, "Empty decision mask"); double denominator = 0, mass = 0; boolean inRemaining = false;
        Set<Integer> seen = new HashSet<>();
        for (int target : remaining) {
            require(target >= 0 && target < mask.length && mask[target] && seen.add(target), "Illegal/duplicate remainder label");
            mass += Math.exp(logits[target] - maximum); if (target == best) inRemaining = true;
        }
        for (int i = 0; i < logits.length; i++) if (mask[i]) denominator += Math.exp(logits[i] - maximum);
        JsonObject result = new JsonObject(); result.addProperty("greedy", best); result.addProperty("legalCount", count);
        result.addProperty("labelProbability", Math.exp(logits[label] - maximum) / denominator);
        result.addProperty("remainingProbability", mass / denominator); result.addProperty("greedyInRemaining", inRemaining); return result;
    }

    static JsonObject ownPlan(GameState state, int seat, Model model, Rules.PlayerMoves teacher, List<Action> opponent,
                               GameState teacherNext, int[] teacherGained, LongSupplier clock) {
        String before = semanticHash(state); GameState input = state.copy(); long start = clock.getAsLong();
        List<Action> actions = List.copyOf(model.bot.play(input, seat, BUDGET_MS));
        double elapsed = (clock.getAsLong() - start) / 1_000_000.0;
        require(model.complete() && Double.isFinite(elapsed) && elapsed >= 0 && elapsed < BUDGET_MS, "Incomplete/late own-prefix policy call");
        require(before.equals(semanticHash(input)) && before.equals(semanticHash(state)), "Student mutated root/input");
        Pathing pathing = new Pathing(state.size); Rules.PlayerMoves moves = SpatialTeacherExport.validate(state, seat, actions, pathing);
        GameState next = state.copy(); Rules.TurnResult result = resolve(next, seat, actions, opponent, pathing); sameMoves(moves, result.moves[seat]);
        sameMoves(SpatialTeacherExport.validate(state, 1 - seat, opponent, pathing), result.moves[1 - seat]);
        JsonObject row = plan(state, moves, true); row.addProperty("actions", Action.format(actions)); addSuccessor(row, next, result);
        int[] actual = Arrays.copyOf(moves.cells, moves.count), expected = Arrays.copyOf(teacher.cells, teacher.count);
        row.addProperty("orderedBuildEqual", Arrays.equals(actual, expected)); Arrays.sort(actual); Arrays.sort(expected);
        row.addProperty("buildSetEqual", Arrays.equals(actual, expected)); row.addProperty("disruptEqual", moves.disrupts.equals(teacher.disrupts));
        row.addProperty("semanticEqual", semanticHash(next).equals(semanticHash(teacherNext)));
        row.addProperty("gainedEqual", Arrays.equals(result.gained, teacherGained)); row.addProperty("elapsedMs", elapsed); row.addProperty("complete", true);
        return row;
    }

    static JsonArray permutations(GameState root, int seat, Rules.PlayerMoves teacher, List<Action> opponent,
                                  GameState teacherNext, int[] teacherGained) {
        JsonArray result = new JsonArray(); int[] cells = Arrays.copyOf(teacher.cells, teacher.count);
        permute(root, seat, teacher, opponent, teacherNext, teacherGained, cells, 0, result); return result;
    }

    private static void permute(GameState root, int seat, Rules.PlayerMoves teacher, List<Action> opponent,
                                GameState teacherNext, int[] teacherGained, int[] cells, int position, JsonArray output) {
        if (position < cells.length) {
            for (int i = position; i < cells.length; i++) {
                int swap = cells[position]; cells[position] = cells[i]; cells[i] = swap;
                permute(root, seat, teacher, opponent, teacherNext, teacherGained, cells, position + 1, output);
                swap = cells[position]; cells[position] = cells[i]; cells[i] = swap;
            }
            return;
        }
        List<Action> actions = new ArrayList<>(); for (int cell : cells) actions.add(Action.place(root.x(cell), root.y(cell)));
        for (int region : teacher.disrupts) actions.add(Action.disrupt(region)); if (actions.isEmpty()) actions.add(Action.waitAction());
        Pathing pathing = new Pathing(root.size); Rules.PlayerMoves expanded = SpatialTeacherExport.validate(root, seat, actions, pathing);
        GameState next = root.copy(); Rules.TurnResult resolved = resolve(next, seat, actions, opponent, pathing);
        sameMoves(expanded, resolved.moves[seat]); sameMoves(SpatialTeacherExport.validate(root, 1 - seat, opponent, pathing), resolved.moves[1 - seat]);
        JsonObject row = plan(root, expanded, true); addSuccessor(row, next, resolved);
        row.addProperty("semanticEqual", semanticHash(next).equals(semanticHash(teacherNext)));
        row.addProperty("gainedEqual", Arrays.equals(resolved.gained, teacherGained)); output.add(row);
    }

    static final class Model {
        final SpatialNet net; final SpatialV9Net net9; final Bot bot;
        Model(boolean context, String payload, Params params, LongSupplier clock) {
            net = context ? null : new SpatialNet(payload); net9 = context ? new SpatialV9Net(payload, true) : null;
            bot = context ? new SpatialV9Bot(params, net9, clock) : new SpatialBot(params, net, clock);
        }
        void forward(SpatialObservation observation) { if (net == null) net9.forward(observation); else net.forward(observation); }
        float[] build() { return net == null ? net9.buildLogits : net.buildLogits; }
        float[] disrupt() { return net == null ? net9.disruptLogits : net.disruptLogits; }
        boolean complete() { return bot instanceof SpatialBot plain ? !plain.lastInterrupted : !((SpatialV9Bot) bot).lastInterrupted; }
    }

    private static Rules.TurnResult resolve(GameState state, int seat, List<Action> teacher, List<Action> opponent, Pathing pathing) {
        return Rules.resolve(state, seat == 0 ? teacher : opponent, seat == 0 ? opponent : teacher, pathing);
    }
    private static void addSuccessor(JsonObject row, GameState next, Rules.TurnResult result) {
        row.addProperty("nextStateSha256", semanticHash(next)); row.add("gained", JSON.toJsonTree(result.gained));
        row.add("scoreAfter", JSON.toJsonTree(next.score)); row.addProperty("terminal", Rules.gameOver(next, new Pathing(next.size)));
    }
    private static JsonObject plan(GameState state, Rules.PlayerMoves moves, boolean normalizedStop) {
        JsonObject result = new JsonObject(); JsonArray cells = new JsonArray();
        for (int i = 0; i < moves.count; i++) cells.add(SpatialObservation.paddedCell(state, moves.cells[i]));
        result.add("cells", cells); result.addProperty("cost", moves.cost);
        require(moves.disrupts.size() <= 1, "Multiple disruption commands outside corpus contract");
        result.addProperty("disrupt", moves.disrupts.isEmpty() ? normalizedStop ? 600 : -1 : moves.disrupts.get(0)); return result;
    }
    private static void sameMoves(Rules.PlayerMoves expected, Rules.PlayerMoves actual) {
        require(actual.error == null && actual.skipped.isEmpty() && actual.count == expected.count && actual.cost == expected.cost
                && actual.disrupts.equals(expected.disrupts), "Core acceptance differs from expanded plan");
        for (int i = 0; i < expected.count; i++) require(actual.cells[i] == expected.cells[i], "Core accepted order differs");
    }
    static String recordedHash(GameState state) {
        return sha((Protocol.initBlock(state, 0) + state.turn + "\n" + state.maxTurns + "\n" + Protocol.turnBlock(state, 0)).getBytes(StandardCharsets.UTF_8));
    }
    /** Every semantic dynamic field and active path is included; allocation capacity is not state. */
    static String semanticHash(GameState state) {
        MessageDigest digest = digest();
        try (DataOutputStream out = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
            out.writeUTF("BTK-SEMANTIC-STATE-1"); byte[] map = Protocol.initBlock(state, 0).getBytes(StandardCharsets.UTF_8);
            out.writeInt(map.length); out.write(map); out.writeInt(state.turn); out.writeInt(state.maxTurns);
            writeInts(out, state.owner); writeInts(out, state.instability); writeBooleans(out, state.inked);
            writeInts(out, state.regionInstability); writeBooleans(out, state.regionInked); writeInts(out, state.score);
            out.writeInt(state.pairCount);
            for (int p = 0; p < state.pairCount; p++) {
                int length = state.pairPathLen[p]; boolean active = state.pairPath[p] != null;
                require(length >= 0 && (active ? state.pairPath[p].length >= length : length == 0), "Invalid active derived path");
                out.writeBoolean(active); out.writeInt(length);
                if (active) for (int i = 0; i < length; i++) out.writeInt(state.pairPath[p][i]);
            }
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
        return HexFormat.of().formatHex(digest.digest());
    }
    static String inputHash(byte[] row, int stage) {
        require(row.length == SpatialTeacherExport.ROW_BYTES && stage >= 0 && stage <= 3, "Invalid input hash row");
        MessageDigest digest = digest(); digest.update(row, 0, row.length - 12);
        digest.update(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(stage).array()); return HexFormat.of().formatHex(digest.digest());
    }
    private static void writeInts(DataOutputStream out, int[] values) throws IOException { out.writeInt(values.length); for (int value : values) out.writeInt(value); }
    private static void writeBooleans(DataOutputStream out, boolean[] values) throws IOException { out.writeInt(values.length); for (boolean value : values) out.writeBoolean(value); }
    private static JsonObject identity(JsonObject source) { JsonObject result = new JsonObject(); for (String key : IDENTITY) result.add(key, source.get(key)); return result; }
    private static int[] ints(JsonArray array) { int[] result = new int[array.size()]; for (int i = 0; i < result.length; i++) result[i] = array.get(i).getAsInt(); return result; }
    private static void equal(JsonElement expected, JsonElement actual, String message) { require(expected != null && expected.equals(actual), message); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static MessageDigest digest() { try { return MessageDigest.getInstance("SHA-256"); } catch (Exception impossible) { throw new IllegalStateException(impossible); } }
    private static String sha(byte[] bytes) { return HexFormat.of().formatHex(digest().digest(bytes)); }
    private static String fileHash(Path path) throws IOException {
        MessageDigest digest = digest(); byte[] buffer = new byte[64 * 1024];
        try (InputStream input = Files.newInputStream(path)) { int count; while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count); }
        return HexFormat.of().formatHex(digest.digest());
    }
    private static void writeNew(Path path, JsonObject value) throws IOException { Files.writeString(path, JSON.toJson(value), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); }
}
