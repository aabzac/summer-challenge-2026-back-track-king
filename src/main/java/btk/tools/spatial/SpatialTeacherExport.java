package btk.tools.spatial;

import btk.bot.Bot;
import btk.bot.BotFactory;
import btk.bot.neural.SpatialBot;
import btk.bot.neural.SpatialNet;
import btk.bot.neural.SpatialObservation;
import btk.bot.neural.SpatialV2Weights;
import btk.bot.v09.RolloutBotV09;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Protocol;
import btk.core.Rules;
import btk.referee.MapGenerator;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.zip.GZIPOutputStream;

/** Write-once v4 teacher corpus. Binary format is documented in tools/spatial/teacher_format.md. */
public final class SpatialTeacherExport {
    static final byte[] MAGIC = "BTKSD001".getBytes(StandardCharsets.US_ASCII);
    static final int HEADER_BYTES = 16, ROW_BYTES = 63_638;
    static final long BUDGET_MS = 60_000;
    private static final String[] OPPONENTS = {"v01", "spatial-ppo-v2", "v09"};
    private static final Gson JSON = new GsonBuilder().serializeNulls().setPrettyPrinting().create();

    private SpatialTeacherExport() {}

    /** Arguments: smoke|corpus fresh-output-directory prior-exclusions.json. */
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Usage: SpatialTeacherExport smoke|corpus output-directory exclusions.json");
        String mode = args[0]; List<MapSpec> schedule = schedule(mode);
        Path out = Path.of(args[1]).toAbsolutePath(), exclusions = Path.of(args[2]).toAbsolutePath();
        byte[] exclusionBytes = Files.readAllBytes(exclusions);
        JsonObject exclusionJson = JsonParser.parseString(new String(exclusionBytes, StandardCharsets.UTF_8)).getAsJsonObject();
        if (!exclusionJson.has("gamesPerMap") || exclusionJson.getAsJsonObject("gamesPerMap").isEmpty())
            throw new IllegalArgumentException("nonempty gamesPerMap exclusion index required");
        Set<String> excluded = new HashSet<>(exclusionJson.getAsJsonObject("gamesPerMap").keySet());
        Files.createDirectories(out.getParent()); Files.createDirectory(out);
        JsonObject manifest = new JsonObject(); JsonArray maps = new JsonArray(), games = new JsonArray();
        manifest.addProperty("schemaVersion", 1); manifest.addProperty("format", "BTKSD001");
        manifest.addProperty("experiment", "spatial-ppo-v4"); manifest.addProperty("mode", mode);
        manifest.addProperty("createdAt", Instant.now().toString()); manifest.addProperty("status", "collecting");
        manifest.addProperty("headerBytes", HEADER_BYTES); manifest.addProperty("rowBytes", ROW_BYTES);
        manifest.addProperty("teacherSpec", "v09"); manifest.add("teacherParams", JSON.toJsonTree(BotFactory.configuration("v09")));
        JsonObject opponentParams = new JsonObject();
        for (String spec : OPPONENTS) opponentParams.add(spec, JSON.toJsonTree(BotFactory.configuration(spec)));
        manifest.add("opponentParams", opponentParams); manifest.addProperty("opponentV2WeightsSha256", payloadHash());
        manifest.addProperty("budgetMs", BUDGET_MS); manifest.addProperty("warmup", false);
        manifest.addProperty("exclusionsSha256", sha(exclusionBytes)); manifest.addProperty("excludedMapCount", excluded.size());
        manifest.addProperty("javaVersion", System.getProperty("java.version"));
        manifest.add("maps", maps); manifest.add("games", games);
        manifest.addProperty("rows", 0); manifest.addProperty("totalTurns", 0);
        JsonObject statistics = new JsonObject(); manifest.add("callStats", statistics);
        writeNew(out.resolve("intent.json"), manifest);
        int rows = 0, turns = 0;
        try {
            List<GameState> initialStates = new ArrayList<>(); Set<String> used = new HashSet<>();
            // All map identities are checked before any native policy is instantiated or called.
            for (MapSpec spec : schedule) {
                GameState state = MapGenerator.generate(spec.seed); String init = Protocol.initBlock(state, 0), hash = sha(init);
                requireFresh(hash, excluded, used);
                JsonObject map = identity(spec, hash, -1); map.addProperty("initBlockP0", init);
                map.addProperty("width", state.width); map.addProperty("height", state.height); map.addProperty("townCount", state.townCount);
                maps.add(map); initialStates.add(state);
            }
            manifest.addProperty("mapPreflightComplete", true); progress(out, manifest);
            for (int index = 0; index < schedule.size(); index++) {
                MapSpec spec = schedule.get(index); String hash = maps.get(index).getAsJsonObject().get("mapHash").getAsString();
                int gamesOnMap = mode.equals("corpus") ? 2 : 1;
                for (int arm = 0; arm < gamesOnMap; arm++) {
                    int seat = mode.equals("corpus") ? arm : spec.index % 2;
                    String id = spec.split + "-" + String.format(java.util.Locale.ROOT, "%02d", spec.index) + "-p" + seat;
                    JsonObject game = identity(spec, hash, seat); game.addProperty("id", id);
                    game.addProperty("binary", id + ".bin.gz"); game.addProperty("metadata", id + ".json"); games.add(game);
                    try {
                        exportGame(out, game, initialStates.get(index).copy(), seat, opponent("v09"), opponent(spec.opponent),
                                mode.equals("smoke") ? 2 : Integer.MAX_VALUE, System::nanoTime, statistics);
                    } finally {
                        rows += game.has("rowCount") ? game.get("rowCount").getAsInt() : 0;
                        turns += game.has("turns") ? game.get("turns").getAsInt() : 0;
                        manifest.addProperty("rows", rows); manifest.addProperty("totalTurns", turns); progress(out, manifest);
                    }
                    System.out.println(id + " turns=" + game.get("turns") + " terminal=" + game.get("terminal"));
                }
            }
            manifest.addProperty("status", "complete"); manifest.addProperty("completedAt", Instant.now().toString());
            writeNew(out.resolve("manifest.json"), manifest);
        } catch (Exception failure) {
            manifest.addProperty("status", "failed"); manifest.addProperty("failure", failure.toString());
            manifest.addProperty("failedAt", Instant.now().toString());
            writeNew(out.resolve("manifest.json"), manifest); throw failure;
        }
    }

    private static void exportGame(Path out, JsonObject game, GameState state, int teacherSeat,
                                   Bot teacher, Bot opponent, int limit, LongSupplier clock, JsonObject statistics) throws Exception {
        Path binary = out.resolve(game.get("binary").getAsString()), metadataPath = out.resolve(game.get("metadata").getAsString());
        JsonObject metadata = game.deepCopy(); metadata.remove("metadata"); metadata.addProperty("teacherSpec", "v09");
        metadata.addProperty("status", "collecting"); JsonArray records = new JsonArray(); metadata.add("turns", records);
        MessageDigest compressedDigest = digest(), rawDigest = digest(); BinaryRows rows = null; Exception failure = null;
        try (OutputStream file = new BufferedOutputStream(Files.newOutputStream(binary, StandardOpenOption.CREATE_NEW));
             DigestOutputStream compressed = new DigestOutputStream(file, compressedDigest);
             GZIPOutputStream gzip = new GZIPOutputStream(compressed, 64 * 1024);
             DigestOutputStream raw = new DigestOutputStream(gzip, rawDigest)) {
            rows = new BinaryRows(raw);
            playGame(state, teacherSeat, teacher, opponent, limit, clock, rows, records, statistics,
                    game.get("opponentSpec").getAsString());
        } catch (Exception ex) { failure = ex; }
        int count = rows == null ? 0 : rows.rows;
        Pathing pathing = new Pathing(state.size); boolean terminal = Rules.gameOver(state, pathing);
        String status = failure == null ? "complete" : "failed";
        metadata.addProperty("status", status); metadata.addProperty("rowCount", count);
        metadata.addProperty("turnCount", state.turn); metadata.addProperty("terminal", terminal);
        metadata.add("score", JSON.toJsonTree(state.score)); metadata.addProperty("failure", failure == null ? null : failure.toString());
        metadata.addProperty("stopReason", terminal ? "actual terminal" : failure == null ? "declared smoke cap" : "failure");
        writeNew(metadataPath, metadata);
        game.addProperty("status", status); game.addProperty("rowCount", count); game.addProperty("turns", state.turn);
        game.addProperty("terminal", terminal); game.addProperty("sha256", HexFormat.of().formatHex(compressedDigest.digest()));
        game.addProperty("rawSha256", HexFormat.of().formatHex(rawDigest.digest()));
        game.addProperty("metadataSha256", sha(Files.readAllBytes(metadataPath)));
        game.addProperty("bytes", Files.size(binary)); game.addProperty("uncompressedBytes", HEADER_BYTES + (long) count * ROW_BYTES);
        game.addProperty("failure", failure == null ? null : failure.toString());
        if (failure != null) throw failure;
        if (limit == Integer.MAX_VALUE && !terminal) throw new IllegalStateException("corpus game did not terminate");
    }

    /** Synthetic fixtures can supply bots, state, clock and sink without generating any teacher corpus. */
    static void playGame(GameState state, int teacherSeat, Bot teacher, Bot opponent, int limit, LongSupplier clock,
                         BinaryRows rows, JsonArray records, JsonObject statistics, String opponentSpec) throws IOException {
        if (teacherSeat < 0 || teacherSeat > 1 || limit < 1 || state.turn != 0)
            throw new IllegalArgumentException("invalid fresh game setup");
        Pathing pathing = new Pathing(state.size); Rules.updateConnections(state, pathing);
        Bot[] bots = teacherSeat == 0 ? new Bot[]{teacher, opponent} : new Bot[]{opponent, teacher};
        String beforeInit = stateHash(state);
        for (int me = 0; me < 2; me++) bots[me].init(state.copy(), me);
        if (!beforeInit.equals(stateHash(state))) throw new IllegalStateException("bot init mutated real state");
        SpatialObservation observation = new SpatialObservation(state);
        while (state.turn < limit && !Rules.gameOver(state, pathing)) {
            String before = stateHash(state); JsonObject record = new JsonObject(); records.add(record);
            record.addProperty("turn", state.turn); record.addProperty("rowStart", rows.rows);
            record.addProperty("preStateSha256", before); JsonArray calls = new JsonArray(); record.add("calls", calls);
            List<List<Action>> actions = new ArrayList<>(); List<Rules.PlayerMoves> expanded = new ArrayList<>();
            for (int me = 0; me < 2; me++) {
                String role = me == teacherSeat ? "teacher" : "opponent", spec = me == teacherSeat ? "v09" : opponentSpec;
                JsonObject call = new JsonObject(); calls.add(call);
                actions.add(call(bots[me], state, me, spec, role, pathing, clock, call, statistics));
                expanded.add(validate(state, me, actions.get(me), pathing));
            }
            if (!before.equals(stateHash(state))) throw new IllegalStateException("bot play mutated real pre-turn state");
            record.addProperty("teacherActions", Action.format(actions.get(teacherSeat)));
            record.addProperty("opponentActions", Action.format(actions.get(1 - teacherSeat)));
            record.add("teacherExpanded", expandedJson(state, expanded.get(teacherSeat)));
            record.add("opponentExpanded", expandedJson(state, expanded.get(1 - teacherSeat)));
            writeTeacherRows(state, teacherSeat, expanded.get(teacherSeat), observation, rows, record);
            Rules.TurnResult result = Rules.resolve(state, actions.get(0), actions.get(1), pathing);
            for (int me = 0; me < 2; me++) requireSame(expanded.get(me), result.moves[me]);
            // Compare core-accepted plans, not post-turn ownership: collisions/inking are valid outcomes.
            record.add("acceptedTeacher", expandedJson(state, result.moves[teacherSeat]));
            record.add("acceptedOpponent", expandedJson(state, result.moves[1 - teacherSeat]));
            record.add("gained", JSON.toJsonTree(result.gained)); record.add("scoreAfter", JSON.toJsonTree(state.score));
            record.add("neutralised", JSON.toJsonTree(result.neutralised)); record.add("inkedRegions", JSON.toJsonTree(result.inkedRegions));
            record.addProperty("terminalAfter", Rules.gameOver(state, pathing));
        }
    }

    private static List<Action> call(Bot bot, GameState state, int me, String spec, String role, Pathing pathing,
                                    LongSupplier clock, JsonObject row, JsonObject statistics) {
        row.addProperty("me", me); row.addProperty("spec", spec); row.addProperty("role", role);
        row.addProperty("budgetMs", BUDGET_MS); row.addProperty("complete", false); row.addProperty("legal", false);
        long start = clock.getAsLong();
        try {
            List<Action> actions = List.copyOf(bot.play(state.copy(), me, BUDGET_MS));
            double ms = (clock.getAsLong() - start) / 1_000_000.0; row.addProperty("elapsedMs", ms);
            boolean complete = true; row.addProperty("selectedOpening", false);
            if (bot instanceof RolloutBotV09 v09) {
                complete = v09.ordinarySearchComplete || v09.selectedOpening;
                row.addProperty("ordinarySearchComplete", v09.ordinarySearchComplete);
                row.addProperty("selectedOpening", v09.selectedOpening);
            } else if (bot instanceof SpatialBot spatial) {
                complete = !spatial.lastInterrupted; row.addProperty("interrupted", spatial.lastInterrupted);
            } else row.addProperty("completionEvidence", "finite greedy algorithm without timer exits");
            row.addProperty("actions", Action.format(actions)); validate(state, me, actions, pathing); row.addProperty("legal", true);
            if (!complete || ms >= BUDGET_MS) throw new IllegalStateException("incomplete or over-budget native policy " + spec);
            row.addProperty("complete", true); return actions;
        } catch (RuntimeException ex) {
            row.addProperty("failure", ex.toString()); throw ex;
        } finally {
            if (!row.has("elapsedMs")) row.addProperty("elapsedMs", (clock.getAsLong() - start) / 1_000_000.0);
            String key = role + ":" + spec;
            if (!statistics.has(key)) {
                JsonObject totals = new JsonObject(); totals.addProperty("calls", 0); totals.addProperty("completed", 0);
                totals.addProperty("failed", 0); totals.addProperty("elapsedMsTotal", 0); totals.addProperty("maxElapsedMs", 0);
                statistics.add(key, totals);
            }
            JsonObject totals = statistics.getAsJsonObject(key); boolean complete = row.get("complete").getAsBoolean();
            totals.addProperty("calls", totals.get("calls").getAsInt() + 1);
            String outcome = complete ? "completed" : "failed"; totals.addProperty(outcome, totals.get(outcome).getAsInt() + 1);
            double elapsed = row.get("elapsedMs").getAsDouble();
            totals.addProperty("elapsedMsTotal", totals.get("elapsedMsTotal").getAsDouble() + elapsed);
            totals.addProperty("maxElapsedMs", Math.max(totals.get("maxElapsedMs").getAsDouble(), elapsed));
        }
    }

    static void writeTeacherRows(GameState state, int me, Rules.PlayerMoves moves, SpatialObservation observation,
                                 BinaryRows writer, JsonObject record) throws IOException {
        int[] labels = new int[4]; Arrays.fill(labels, SpatialObservation.STOP);
        for (int i = 0; i < moves.count; i++) labels[i] = SpatialObservation.paddedCell(state, moves.cells[i]);
        if (!moves.disrupts.isEmpty()) labels[3] = moves.disrupts.get(0);
        List<Action> reconstructed = new ArrayList<>();
        for (int stage = 0; stage < 3; stage++) if (labels[stage] != SpatialObservation.STOP)
            reconstructed.add(Action.place(labels[stage] % SpatialObservation.W, labels[stage] / SpatialObservation.W));
        if (labels[3] != SpatialObservation.STOP) reconstructed.add(Action.disrupt(labels[3]));
        if (reconstructed.isEmpty()) reconstructed.add(Action.waitAction());
        requireSame(moves, validate(state, me, reconstructed, new Pathing(state.size)));
        observation.prepare(state, me); int[] prefix = new int[3]; int count = 0, paint = GameState.PAINT_PER_TURN;
        boolean stopped = false; JsonArray prefixes = new JsonArray(), paints = new JsonArray(), stops = new JsonArray();
        record.add("labels", JSON.toJsonTree(labels)); record.add("prefixes", prefixes);
        record.add("paintBefore", paints); record.add("stoppedBefore", stops);
        for (int stage = 0; stage < 4; stage++) {
            observation.prefix(prefix, count, paint, stage, stopped);
            boolean[] mask = stage == 3 ? observation.disruptMask : observation.buildMask;
            if (!mask[labels[stage]]) throw new IllegalStateException("teacher label absent from Java mask");
            prefixes.add(JSON.toJsonTree(Arrays.copyOf(prefix, count))); paints.add(paint); stops.add(stopped);
            writer.write(observation, labels[stage], stage, state.turn);
            if (stage < 3) {
                if (labels[stage] == SpatialObservation.STOP) stopped = true;
                else { prefix[count++] = labels[stage]; paint -= state.placeCost(observation.nativeCell(labels[stage])); }
            }
        }
    }

    static Rules.PlayerMoves validate(GameState state, int me, List<Action> actions, Pathing pathing) {
        Rules.TurnResult tr = new Rules.TurnResult(); Rules.expand(state, me, actions, pathing, tr.moves[me], tr);
        Rules.PlayerMoves moves = tr.moves[me];
        if (moves.error != null || !moves.skipped.isEmpty()) throw new IllegalArgumentException("illegal native plan: " + moves.error + " " + moves.skipped);
        return moves;
    }

    private static void requireSame(Rules.PlayerMoves expected, Rules.PlayerMoves actual) {
        if (actual.error != null || !actual.skipped.isEmpty() || expected.count != actual.count || expected.cost != actual.cost
                || !expected.disrupts.equals(actual.disrupts)) throw new IllegalStateException("core-expanded plans differ");
        for (int i = 0; i < expected.count; i++) if (expected.cells[i] != actual.cells[i])
            throw new IllegalStateException("core-expanded placement order differs");
    }

    private static JsonObject expandedJson(GameState state, Rules.PlayerMoves moves) {
        JsonObject result = new JsonObject(); JsonArray cells = new JsonArray();
        for (int i = 0; i < moves.count; i++) cells.add(SpatialObservation.paddedCell(state, moves.cells[i]));
        result.add("cells", cells); result.addProperty("disrupt", moves.disrupts.isEmpty() ? -1 : moves.disrupts.get(0));
        result.addProperty("cost", moves.cost); return result;
    }

    static List<MapSpec> schedule(String mode) {
        List<MapSpec> result = new ArrayList<>();
        if (mode.equals("smoke")) for (int i = 0; i < 3; i++) result.add(new MapSpec("smoke", i, 11_900_000L + i, OPPONENTS[i]));
        else if (mode.equals("corpus")) {
            for (int i = 0; i < 24; i++) result.add(new MapSpec("train", i, 12_000_000L + i, OPPONENTS[i % 3]));
            for (int i = 0; i < 6; i++) result.add(new MapSpec("validation", i, 12_100_000L + i, OPPONENTS[i % 3]));
        } else throw new IllegalArgumentException("mode must be smoke or corpus");
        return result;
    }

    static void requireFresh(String hash, Set<String> excluded, Set<String> seen) {
        if (excluded.contains(hash) || !seen.add(hash)) throw new IllegalArgumentException("excluded or duplicate map hash " + hash);
    }

    private static JsonObject identity(MapSpec spec, String hash, int seat) {
        JsonObject result = new JsonObject(); result.addProperty("split", spec.split); result.addProperty("mapIndex", spec.index);
        result.addProperty("seed", spec.seed); result.addProperty("mapHash", hash); result.addProperty("opponentSpec", spec.opponent);
        if (seat >= 0) result.addProperty("teacherSeat", seat); return result;
    }

    private static Bot opponent(String spec) {
        return spec.equals("spatial-ppo-v2") ? new SpatialBot(BotFactory.parameters(spec), new SpatialNet(SpatialV2Weights.DATA), System::nanoTime)
                : BotFactory.create(spec);
    }

    private static String payloadHash() { return sha(java.util.Base64.getDecoder().decode(SpatialV2Weights.DATA)); }
    private static String stateHash(GameState s) { return sha(Protocol.initBlock(s, 0) + s.turn + "\n" + s.maxTurns + "\n" + Protocol.turnBlock(s, 0)); }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String sha(String text) { return sha(text.getBytes(StandardCharsets.UTF_8)); }
    private static String sha(byte[] bytes) { return HexFormat.of().formatHex(digest().digest(bytes)); }
    private static void writeNew(Path path, JsonObject value) throws IOException {
        Files.writeString(path, JSON.toJson(value), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }
    private static void progress(Path directory, JsonObject manifest) throws IOException {
        Path temp = directory.resolve("manifest.progress.tmp"), destination = directory.resolve("manifest.progress.json");
        Files.writeString(temp, JSON.toJson(manifest), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    record MapSpec(String split, int index, long seed, String opponent) {}

    /** Little-endian exact float32/int32 records; no boxed observations or accumulated turn tensors. */
    static final class BinaryRows {
        final OutputStream output; final ByteBuffer buffer = ByteBuffer.allocate(ROW_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        int rows;
        BinaryRows(OutputStream output) throws IOException {
            this.output = output;
            output.write(ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN).put(MAGIC).putInt(ROW_BYTES).putInt(0).array());
        }
        void write(SpatialObservation o, int label, int stage, int turn) throws IOException {
            if (stage < 0 || stage > 3 || turn < 0 || label < 0 || label > SpatialObservation.STOP
                    || !(stage == 3 ? o.disruptMask : o.buildMask)[label]) throw new IllegalArgumentException("invalid binary row label/stage/turn");
            buffer.clear(); floats(o.planes); floats(o.towns); floats(o.scalars);
            for (int n : o.townCells) buffer.putInt(n);
            for (int n : o.regionIds) buffer.putInt(n);
            for (boolean b : o.buildMask) buffer.put((byte) (b ? 1 : 0));
            for (boolean b : o.disruptMask) buffer.put((byte) (b ? 1 : 0));
            buffer.putInt(label).putInt(stage).putInt(turn);
            if (buffer.position() != ROW_BYTES) throw new IllegalStateException("binary row size drift");
            output.write(buffer.array()); rows++;
        }
        private void floats(float[] values) {
            for (float f : values) {
                if (!Float.isFinite(f)) throw new IllegalArgumentException("non-finite teacher observation");
                buffer.putFloat(f);
            }
        }
    }
}
