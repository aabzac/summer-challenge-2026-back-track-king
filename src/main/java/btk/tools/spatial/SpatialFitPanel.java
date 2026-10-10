package btk.tools.spatial;

import btk.bot.BotFactory;
import btk.bot.neural.SpatialNet;
import btk.bot.neural.SpatialObservation;
import btk.bot.neural.SpatialV4Weights;
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
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
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
import java.util.HexFormat;
import java.util.List;
import java.util.function.LongSupplier;

/** Own-prefix memorization diagnostic on 48 fixed saved TRAIN roots; no new teacher games.
 * Both models use the unchanged context-off v4 runtime. The old label audit's internal model
 * slots are renamed initial/final in every emitted record; this tool never evaluates V9.
 */
public final class SpatialFitPanel {
    private static final Gson JSON = new GsonBuilder().serializeNulls().create();
    private static final String[] IDENTITY = {"id", "split", "mapIndex", "seed", "mapHash", "teacherSeat", "opponentSpec"};
    private static final SpatialLabelAudit.Model[] NO_MODELS = new SpatialLabelAudit.Model[0];
    private SpatialFitPanel() {}

    /** Arguments: corpus-directory selection.json final-weights.base64 fresh-output-directory. */
    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("SpatialFitPanel corpus-directory selection.json final-weights.base64 fresh-output-directory");
        Path corpus = Path.of(args[0]).toAbsolutePath().normalize(), out = Path.of(args[3]).toAbsolutePath().normalize();
        Files.createDirectories(out.getParent()); Files.createDirectory(out); Files.createDirectory(out.resolve("games"));
        JsonObject manifest = new JsonObject(); JsonArray games = new JsonArray(); manifest.add("games", games);
        manifest.addProperty("schemaVersion", 1); manifest.addProperty("experiment", "spatial-fit-probe-v1");
        manifest.addProperty("phase", "own-prefix-panel"); manifest.addProperty("status", "collecting");
        manifest.addProperty("createdAt", Instant.now().toString()); manifest.addProperty("budgetMs", SpatialLabelAudit.BUDGET_MS);
        manifest.addProperty("warmup", false); manifest.addProperty("townContext", false);
        manifest.addProperty("javaVersion", System.getProperty("java.version"));
        manifest.addProperty("reconstructedTurns", 0); manifest.addProperty("reconstructedRows", 0);
        manifest.addProperty("selectedRoots", 0); manifest.addProperty("selectedRows", 0);
        manifest.addProperty("interpretation", "Memorization/own-prefix diagnostic on selected training states; no generalization or strength claim");
        writeNew(out.resolve("intent.json"), manifest);
        try {
            byte[] corpusBytes = Files.readAllBytes(corpus.resolve("manifest.json"));
            byte[] selectionBytes = Files.readAllBytes(Path.of(args[1]));
            byte[] payloadFile = Files.readAllBytes(Path.of(args[2]));
            JsonObject source = object(corpusBytes), selection = object(selectionBytes);
            String corpusHash = sha(corpusBytes), payload = new String(payloadFile, StandardCharsets.US_ASCII).trim();
            byte[] finalBytes = Base64.getDecoder().decode(payload), initialBytes = Base64.getDecoder().decode(SpatialV4Weights.DATA);
            require(finalBytes.length == SpatialNet.WEIGHT_COUNT * Float.BYTES, "Final payload length mismatch");
            new SpatialNet(payload); // Validate every float before any inference.
            require(sha(initialBytes).equals(SpatialLabelAudit.V4_SHA), "Frozen v4 initializer changed");
            require(Arrays.equals(initialBytes, 3424 * Float.BYTES, initialBytes.length,
                    finalBytes, 3424 * Float.BYTES, finalBytes.length), "Frozen value-head coefficients changed");
            require(corpusHash.equals(selection.get("corpusManifestSha256").getAsString()), "Selection/corpus identity mismatch");
            manifest.addProperty("corpusManifestSha256", corpusHash); manifest.addProperty("selectionSha256", sha(selectionBytes));
            manifest.addProperty("finalPayloadFileSha256", sha(payloadFile));
            manifest.addProperty("valueHeadByteExact", true);
            JsonObject weights = new JsonObject(); weights.addProperty("initial", sha(initialBytes)); weights.addProperty("final", sha(finalBytes));
            manifest.add("weightsSha256", weights); manifest.add("params", JSON.toJsonTree(BotFactory.configuration("spatial-ppo-v4")));
            List<GameInput> inputs = preflight(corpus, source, selection);
            manifest.addProperty("sourcePreflightComplete", true); writeNew(out.resolve("preflight.json"), manifest);
            int roots = 0, turns = 0, rows = 0;
            for (GameInput input : inputs) {
                JsonObject index = identity(input.entry); games.add(index); index.addProperty("status", "collecting");
                index.addProperty("selectedTurn", input.selected.get("turn").getAsInt());
                String file = "games/" + input.entry.get("id").getAsString() + ".json"; index.addProperty("file", file);
                SpatialLabelAudit.Model[] models = {
                        new SpatialLabelAudit.Model(false, SpatialV4Weights.DATA, BotFactory.parameters("spatial-ppo-v4"), System::nanoTime),
                        new SpatialLabelAudit.Model(false, payload, BotFactory.parameters("spatial-ppo-v4"), System::nanoTime)};
                MessageDigest digest = digest(); JsonObject result; int regeneratedRows;
                try (DigestOutputStream sink = new DigestOutputStream(OutputStream.nullOutputStream(), digest)) {
                    SpatialTeacherExport.BinaryRows binary = new SpatialTeacherExport.BinaryRows(sink);
                    result = evaluateGame(input.state, input.entry, input.metadata, input.selected, models, binary, System::nanoTime);
                    regeneratedRows = binary.rows;
                }
                String raw = HexFormat.of().formatHex(digest.digest());
                require(raw.equals(input.entry.get("rawSha256").getAsString()) && regeneratedRows == input.entry.get("rowCount").getAsInt(), "Regenerated source rows mismatch");
                result.add("weightsSha256", weights.deepCopy()); result.addProperty("townContext", false); writeNew(out.resolve(file), result);
                index.addProperty("sha256", fileHash(out.resolve(file))); index.addProperty("regeneratedRawSha256", raw);
                index.addProperty("reconstructedTurns", input.state.turn); index.addProperty("reconstructedRows", regeneratedRows);
                index.addProperty("selectedRoots", 1); index.addProperty("selectedRows", 4); index.addProperty("status", "complete");
                turns += input.state.turn; rows += regeneratedRows; roots++;
                manifest.addProperty("reconstructedTurns", turns); manifest.addProperty("reconstructedRows", rows);
                manifest.addProperty("selectedRoots", roots); manifest.addProperty("selectedRows", roots * 4);
                System.out.println(input.entry.get("id").getAsString() + " selectedTurn=" + input.selected.get("turn") + " replay=verified");
            }
            require(roots == 48 && turns == 4548 && rows == 18192, "Fixed training-source coverage mismatch");
            manifest.addProperty("status", "complete"); manifest.addProperty("completedAt", Instant.now().toString());
            writeNew(out.resolve("manifest.json"), manifest);
        } catch (Exception failure) {
            manifest.addProperty("status", "failed"); manifest.addProperty("failure", failure.toString());
            manifest.addProperty("failedAt", Instant.now().toString()); writeNew(out.resolve("manifest.json"), manifest); throw failure;
        }
    }

    private record GameInput(GameState state, JsonObject entry, JsonObject metadata, JsonObject selected) {}

    /** This checks coverage/lineage, not the Python selection-ranking algorithm or conflict analysis. */
    static void validateSelection(JsonObject source, JsonObject selection) {
        require(selection.get("schemaVersion").getAsInt() == 1 && selection.get("experiment").getAsString().equals("spatial-fit-probe-v1")
                && selection.get("status").getAsString().equals("complete") && selection.get("rootCount").getAsInt() == 48
                && selection.get("rowCount").getAsInt() == 192 && selection.getAsJsonArray("roots").size() == 48
                && selection.get("initialWeightsSha256").getAsString().equals(SpatialLabelAudit.V4_SHA), "Unexpected fixed selection contract");
        JsonArray games = source.getAsJsonArray("games"); require(games.size() == 60, "Unexpected source game count");
        int gameRowStart = 0;
        for (int i = 0; i < 48; i++) {
            JsonObject game = games.get(i).getAsJsonObject(), root = selection.getAsJsonArray("roots").get(i).getAsJsonObject();
            for (String key : IDENTITY) equal(game.get(key), root.get(key), "Selected identity mismatch: " + key);
            require(game.get("split").getAsString().equals("train"), "Validation data cannot enter the fit panel");
            int turn = root.get("turn").getAsInt();
            require(turn >= 0 && turn < game.get("turns").getAsInt()
                    && root.get("cacheRowStart").getAsInt() == gameRowStart + 4 * turn, "Selected turn/cache lineage mismatch");
            JsonArray hashes = root.getAsJsonArray("inputSha256"); require(hashes.size() == 4, "Expected four selected input hashes");
            for (JsonElement hash : hashes) require(hash.getAsString().matches("[0-9a-f]{64}"), "Invalid selected input hash");
            gameRowStart += game.get("rowCount").getAsInt();
        }
    }

    private static List<GameInput> preflight(Path corpus, JsonObject source, JsonObject selection) throws Exception {
        require(source.get("status").getAsString().equals("complete") && source.get("experiment").getAsString().equals("spatial-ppo-v4")
                && source.get("mode").getAsString().equals("corpus") && source.get("format").getAsString().equals("BTKSD001")
                && source.get("headerBytes").getAsInt() == 16 && source.get("rowBytes").getAsInt() == 63638
                && source.get("rows").getAsInt() == 22640 && source.get("totalTurns").getAsInt() == 5660
                && source.getAsJsonArray("maps").size() == 30, "Unexpected frozen corpus");
        validateSelection(source, selection); List<GameInput> inputs = new ArrayList<>();
        List<SpatialTeacherExport.MapSpec> schedule = SpatialTeacherExport.schedule("corpus");
        for (int i = 0; i < 48; i++) {
            JsonObject entry = source.getAsJsonArray("games").get(i).getAsJsonObject();
            JsonObject map = source.getAsJsonArray("maps").get(i / 2).getAsJsonObject();
            SpatialTeacherExport.MapSpec spec = schedule.get(i / 2);
            require(entry.get("teacherSeat").getAsInt() == i % 2 && entry.get("mapIndex").getAsInt() == spec.index()
                    && entry.get("seed").getAsLong() == spec.seed() && entry.get("opponentSpec").getAsString().equals(spec.opponent()), "Source schedule mismatch");
            for (String key : new String[]{"split", "mapIndex", "seed", "mapHash", "opponentSpec"}) equal(entry.get(key), map.get(key), "Source map identity mismatch");
            String init = map.get("initBlockP0").getAsString();
            require(sha(init.getBytes(StandardCharsets.UTF_8)).equals(map.get("mapHash").getAsString()), "Saved map hash mismatch");
            GameState state = Protocol.readInit(new Protocol.TokenReader(new ByteArrayInputStream(init.getBytes(StandardCharsets.UTF_8)))).state;
            require(Protocol.initBlock(state, 0).equals(init), "Noncanonical saved initial map");
            String id = entry.get("id").getAsString();
            require(id.equals(String.format(java.util.Locale.ROOT, "train-%02d-p%d", i / 2, i % 2))
                    && entry.get("metadata").getAsString().equals(id + ".json") && entry.get("binary").getAsString().equals(id + ".bin.gz")
                    && entry.get("status").getAsString().equals("complete") && entry.get("terminal").getAsBoolean() && entry.get("failure").isJsonNull(), "Invalid source game");
            byte[] metadataBytes = Files.readAllBytes(corpus.resolve(id + ".json")); Path binary = corpus.resolve(id + ".bin.gz");
            require(sha(metadataBytes).equals(entry.get("metadataSha256").getAsString()) && fileHash(binary).equals(entry.get("sha256").getAsString())
                    && Files.size(binary) == entry.get("bytes").getAsLong(), "Source file identity mismatch");
            JsonObject metadata = object(metadataBytes); for (String key : IDENTITY) equal(entry.get(key), metadata.get(key), "Source metadata identity mismatch");
            int turns = entry.get("turns").getAsInt(), rows = entry.get("rowCount").getAsInt();
            require(turns > 0 && turns <= state.maxTurns && rows == turns * 4 && metadata.get("turnCount").getAsInt() == turns
                    && metadata.getAsJsonArray("turns").size() == turns && metadata.get("rowCount").getAsInt() == rows
                    && entry.get("uncompressedBytes").getAsLong() == 16L + 63638L * rows
                    && metadata.get("status").getAsString().equals("complete") && metadata.get("teacherSpec").getAsString().equals("v09")
                    && metadata.get("terminal").getAsBoolean() && metadata.get("failure").isJsonNull()
                    && metadata.get("stopReason").getAsString().equals("actual terminal"), "Source terminal/row contract mismatch");
            inputs.add(new GameInput(state, entry, metadata, selection.getAsJsonArray("roots").get(i).getAsJsonObject()));
        }
        return inputs;
    }

    static JsonObject evaluateGame(GameState state, JsonObject identity, JsonObject metadata, JsonObject selected,
                                   SpatialLabelAudit.Model[] models, SpatialTeacherExport.BinaryRows binary, LongSupplier clock) throws Exception {
        require(state.turn == 0 && binary.rows == 0 && models.length == 2, "Expected fresh saved game and initial/final models");
        int seat = identity.get("teacherSeat").getAsInt(), selectedTurn = selected.get("turn").getAsInt();
        require((seat == 0 || seat == 1) && selectedTurn >= 0 && selectedTurn < metadata.getAsJsonArray("turns").size(), "Invalid selected root");
        Pathing pathing = new Pathing(state.size); Rules.updateConnections(state, pathing);
        for (SpatialLabelAudit.Model model : models) {
            require(model.net != null && model.net9 == null, "Fit panel must use context-off v4 architecture"); model.bot.init(state.copy(), seat);
        }
        SpatialObservation observation = new SpatialObservation(state); JsonObject chosen = null;
        for (JsonElement turn : metadata.getAsJsonArray("turns")) {
            int currentTurn = state.turn; boolean score = currentTurn == selectedTurn;
            JsonObject audit = SpatialLabelAudit.auditRoot(state, seat, identity.get("opponentSpec").getAsString(), turn.getAsJsonObject(),
                    score ? models : NO_MODELS, observation, binary, clock);
            if (score) {
                require(chosen == null, "Repeated selected root"); chosen = audit;
                JsonArray hashes = selected.getAsJsonArray("inputSha256"); require(hashes.size() == 4, "Missing selected input identities");
                for (int stage = 0; stage < 4; stage++) equal(hashes.get(stage), audit.getAsJsonArray("rows").get(stage).getAsJsonObject().get("inputSha256"), "Selected input differs from prepared subset");
                renameModels(chosen.getAsJsonObject("ownPlans"));
                for (JsonElement row : chosen.getAsJsonArray("rows")) renameModels(row.getAsJsonObject().getAsJsonObject("models"));
                for (String key : IDENTITY) chosen.add(key, identity.get(key)); chosen.addProperty("turn", currentTurn);
            }
        }
        require(chosen != null && Rules.gameOver(state, pathing) && state.turn == metadata.get("turnCount").getAsInt(), "Selected/full-trajectory terminal coverage mismatch");
        equal(JSON.toJsonTree(state.score), metadata.get("score"), "Final source score mismatch"); return chosen;
    }

    private static void renameModels(JsonObject models) {
        require(models.size() == 2 && models.has("v4") && models.has("v9"), "Unexpected reused model slots");
        JsonElement initial = models.remove("v4"), last = models.remove("v9"); models.add("initial", initial); models.add("final", last);
    }
    private static JsonObject identity(JsonObject source) { JsonObject result = new JsonObject(); for (String key : IDENTITY) result.add(key, source.get(key)); return result; }
    private static JsonObject object(byte[] bytes) { return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject(); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static void equal(JsonElement expected, JsonElement actual, String message) { require(expected != null && expected.equals(actual), message); }
    private static MessageDigest digest() { try { return MessageDigest.getInstance("SHA-256"); } catch (Exception impossible) { throw new IllegalStateException(impossible); } }
    private static String sha(byte[] bytes) { return HexFormat.of().formatHex(digest().digest(bytes)); }
    private static String fileHash(Path path) throws Exception {
        MessageDigest digest = digest(); byte[] buffer = new byte[64 * 1024];
        try (InputStream in = Files.newInputStream(path)) { int count; while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count); }
        return HexFormat.of().formatHex(digest.digest());
    }
    private static void writeNew(Path path, JsonObject json) throws Exception { Files.writeString(path, JSON.toJson(json), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); }
}
