package btk.tools;

import btk.bot.Bot;
import btk.bot.BotFactory;
import btk.bot.Params;
import btk.bot.neural.NeuralFeatures;
import btk.bot.neural.NeuralNet;
import btk.bot.neural.NeuralV2Bot;
import btk.bot.neural.NeuralWeights;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Protocol;
import btk.core.Rules;
import btk.referee.MapGenerator;
import btk.runner.TimingProfile;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * On-policy student-only collection. Args: games seed weights.json outDir [temperature=1].
 * One fixed opponent per map pair, round-robin v09/neural-v1/v07, with both student seats.
 * Student receives an offline 60-second budget so export overhead never truncates its policy;
 * frozen opponents retain the nominal CG search budget. Only terminal win/draw/loss is rewarded.
 */
public final class NeuralV2Collect {
    static final String[] OPPONENTS = {"v09", "neural-v1", "v07"};
    static final long STUDENT_BUDGET_MS = 60_000;
    private NeuralV2Collect() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 4 || args.length > 5)
            throw new IllegalArgumentException("Expected games seed weights.json outDir [temperature]");
        int games = Integer.parseInt(args[0]);
        long seed = Long.parseLong(args[1]);
        Path weights = Path.of(args[2]), out = Path.of(args[3]);
        double temperature = args.length > 4 ? Double.parseDouble(args[4]) : 1;
        if (games < 2 || games % 2 != 0 || seed <= 0 || !Double.isFinite(temperature) || temperature <= 0)
            throw new IllegalArgumentException("Require positive even games, positive seed and temperature");
        Math.addExact(seed, games / 2L - 1);
        if (Files.exists(out)) throw new IllegalStateException("Collection output exists: " + out);
        JsonObject weightJson = JsonParser.parseString(Files.readString(weights)).getAsJsonObject();
        NeuralNet sabotage = readHead(weightJson, "sabotage", NeuralFeatures.SABOTAGE_F);
        NeuralNet build = readHead(weightJson, "build", NeuralFeatures.BUILD_F);
        String weightsSha256 = sha(weights);
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.createDirectory(out);
        List<Map<String, Object>> results = new ArrayList<>();
        long started = System.nanoTime();
        long sabotageRows, sabotageDecisions, buildRows, buildDecisions;
        try (HeadWriter sabWriter = new HeadWriter(out, "sabotage", NeuralFeatures.SABOTAGE_F);
             HeadWriter buildWriter = new HeadWriter(out, "build", NeuralFeatures.BUILD_F)) {
            for (int game = 0; game < games; game++) {
                long mapSeed = seed + game / 2;
                int me = game % 2;
                String opponent = OPPONENTS[(game / 2) % OPPONENTS.length];
                GameState s = MapGenerator.generate(mapSeed);
                String mapHash = sha(Protocol.initBlock(s, 0).getBytes(StandardCharsets.UTF_8));
                Pathing pathing = new Pathing(s.size);
                Rules.updateConnections(s, pathing);
                long behaviorSeed = mapSeed * 1_000_003L + me * 7919L + 0x4e455552414cL;
                SoftmaxChooser chooser = new SoftmaxChooser(game, temperature, new Random(behaviorSeed),
                        sabWriter, buildWriter);
                Params params = BotFactory.parameters("neural-v1?NOISE=0");
                NeuralV2Bot student = new NeuralV2Bot(params, sabotage, build, chooser);
                Bot frozen = BotFactory.create(opponent);
                student.init(s.copy(), me);
                frozen.init(s.copy(), 1 - me);
                while (!Rules.gameOver(s, pathing)) {
                    List<Action> mine = student.play(s.copy(), me, STUDENT_BUDGET_MS);
                    List<Action> theirs = frozen.play(s.copy(), 1 - me, TimingProfile.CG.budget(s.turn));
                    Rules.TurnResult tr = Rules.resolve(s, me == 0 ? mine : theirs, me == 1 ? mine : theirs, pathing);
                    if (tr.hasError(0) || tr.hasError(1) || !tr.moves[0].skipped.isEmpty() || !tr.moves[1].skipped.isEmpty())
                        throw new IllegalStateException("Illegal training action at game " + game + " turn " + s.turn
                                + ": " + tr.moves[0].error + "/" + tr.moves[1].error
                                + "; skipped " + tr.moves[0].skipped + "/" + tr.moves[1].skipped);
                }
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("id", game); result.put("seed", mapSeed); result.put("mapHash", mapHash);
                result.put("studentSeat", me); result.put("opponent", opponent); result.put("behaviorSeed", behaviorSeed);
                result.put("outcome", Integer.compare(s.score[me], s.score[1 - me]));
                result.put("scoreStudent", s.score[me]); result.put("scoreOpponent", s.score[1 - me]);
                result.put("turns", s.turn); results.add(result);
                if ((game + 1) % 8 == 0 || game + 1 == games)
                    System.out.printf("Collected %d/%d games, %.1fs%n", game + 1, games, (System.nanoTime() - started) / 1e9);
            }
            sabotageRows = sabWriter.rows; sabotageDecisions = sabWriter.decisions;
            buildRows = buildWriter.rows; buildDecisions = buildWriter.decisions;
        }
        var gson = new GsonBuilder().setPrettyPrinting().create();
        Files.writeString(out.resolve("games.json"), gson.toJson(results), StandardOpenOption.CREATE_NEW);
        Map<String, String> checksums = new LinkedHashMap<>();
        for (String name : new String[]{"sabotage.bin", "sabotage.idx", "sabotage.logp", "sabotage.teacher",
                "build.bin", "build.idx", "build.logp", "build.teacher", "games.json"})
            checksums.put(name, sha(out.resolve(name)));
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("schema", "BTKNEURALRL1"); meta.put("schemaVersion", 1);
        meta.put("sabotageFeatures", NeuralFeatures.SABOTAGE_NAMES); meta.put("buildFeatures", NeuralFeatures.BUILD_NAMES);
        meta.put("idxFields", new String[]{"game", "turn", "k", "rowStart", "rowCount", "labelOffset"});
        meta.put("binaryFormat", "little-endian float32 rows/logp/teacher and int32 idx");
        meta.put("games", games); meta.put("seed", seed); meta.put("temperature", temperature);
        meta.put("studentWeightsSha256", weightsSha256); meta.put("weightsSha256", weightsSha256);
        meta.put("teacherSabotageBlobSha256", sha(NeuralWeights.SABOTAGE.getBytes(StandardCharsets.US_ASCII)));
        meta.put("teacherBuildBlobSha256", sha(NeuralWeights.BUILD.getBytes(StandardCharsets.US_ASCII)));
        meta.put("studentSabotageSizes", weightJson.getAsJsonObject("sabotage").getAsJsonArray("sizes"));
        meta.put("studentBuildSizes", weightJson.getAsJsonObject("build").getAsJsonArray("sizes"));
        meta.put("studentParameters", BotFactory.configuration("neural-v1?NOISE=0"));
        Map<String, Object> opponentParams = new LinkedHashMap<>();
        for (String opponent : OPPONENTS) opponentParams.put(opponent, BotFactory.configuration(opponent));
        meta.put("opponents", opponentParams);
        meta.put("opponentSchedule", "pair-index round-robin v09,neural-v1,v07; student seats 0 then 1");
        meta.put("reward", "terminal student outcome {-1,0,1}; gamma=1; no score shaping");
        meta.put("forcedDecisions", "closing guard, singleton candidate sets and WAIT omitted from policy-gradient data");
        meta.put("studentBudgetMs", STUDENT_BUDGET_MS); meta.put("opponentTiming", "CG");
        meta.put("opponentFirstBudgetMs", TimingProfile.CG.firstSearchMs);
        meta.put("opponentTurnBudgetMs", TimingProfile.CG.turnSearchMs);
        meta.put("sabotageRows", sabotageRows); meta.put("sabotageDecisions", sabotageDecisions);
        meta.put("buildRows", buildRows); meta.put("buildDecisions", buildDecisions);
        meta.put("checksums", checksums); meta.put("seconds", (System.nanoTime() - started) / 1e9);
        Files.writeString(out.resolve("meta.json"), gson.toJson(meta), StandardOpenOption.CREATE_NEW);
        System.out.println("Saved " + out);
    }

    static NeuralNet readHead(JsonObject json, String name, int inputs) {
        JsonObject head = json.getAsJsonObject(name);
        int[] sizes = new com.google.gson.Gson().fromJson(head.get("sizes"), int[].class);
        if (sizes == null || sizes.length < 2 || sizes[0] != inputs || sizes[sizes.length - 1] != 1)
            throw new IllegalArgumentException("Invalid " + name + " architecture");
        for (int size : sizes) if (size < 1 || size > 4096) throw new IllegalArgumentException("Invalid layer size");
        NeuralNet net = new NeuralNet(sizes, head.get("base64").getAsString());
        if (!Float.isFinite(net.score(new float[inputs]))) throw new IllegalArgumentException("Non-finite " + name + " weights");
        return net;
    }

    static final class SoftmaxChooser implements NeuralV2Bot.DecisionChooser {
        final int game;
        final double temperature;
        final Random random;
        final HeadWriter sabotage, build;
        final NeuralNet teacherSabotage = new NeuralNet(NeuralWeights.SABOTAGE_SIZES, NeuralWeights.SABOTAGE);
        final NeuralNet teacherBuild = new NeuralNet(NeuralWeights.BUILD_SIZES, NeuralWeights.BUILD);

        SoftmaxChooser(int game, double temperature, Random random, HeadWriter sabotage, HeadWriter build) {
            this.game = game; this.temperature = temperature; this.random = random;
            this.sabotage = sabotage; this.build = build;
        }

        @Override public int choose(boolean isSabotage, int turn, int k, float[][] rows, int[] indices,
                                    double[] logits, int count) {
            if (count < 2) throw new IllegalStateException("Forced decisions must not be exported");
            double max = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < count; i++) {
                if (!Double.isFinite(logits[i])) throw new IllegalStateException("Non-finite student logit");
                max = Math.max(max, logits[i] / temperature);
            }
            double total = 0;
            for (int i = 0; i < count; i++) total += Math.exp(logits[i] / temperature - max);
            double pick = random.nextDouble() * total;
            int chosen = count - 1;
            for (int i = 0; i < count; i++) {
                pick -= Math.exp(logits[i] / temperature - max);
                if (pick < 0) { chosen = i; break; }
            }
            double logp = logits[chosen] / temperature - max - Math.log(total);
            try {
                (isSabotage ? sabotage : build).write(game, turn, k, rows, indices, count, chosen, (float) logp,
                        isSabotage ? teacherSabotage : teacherBuild);
            } catch (IOException e) { throw new UncheckedIOException(e); }
            return chosen;
        }
    }

    static final class HeadWriter implements AutoCloseable {
        final BinaryWriter features, idx, logp, teacher;
        final int width;
        long rows, decisions;
        HeadWriter(Path out, String name, int width) throws IOException {
            this.width = width;
            features = new BinaryWriter(out.resolve(name + ".bin")); idx = new BinaryWriter(out.resolve(name + ".idx"));
            logp = new BinaryWriter(out.resolve(name + ".logp")); teacher = new BinaryWriter(out.resolve(name + ".teacher"));
        }
        void write(int game, int turn, int k, float[][] x, int[] indices, int count, int chosen,
                   float behaviorLogp, NeuralNet prior) throws IOException {
            if (count < 2 || chosen < 0 || chosen >= count || !Float.isFinite(behaviorLogp))
                throw new IllegalArgumentException("Invalid trainable decision");
            int start = Math.toIntExact(rows);
            Math.toIntExact(rows + count);
            idx.putInt(game); idx.putInt(turn); idx.putInt(k); idx.putInt(start); idx.putInt(count); idx.putInt(chosen);
            for (int i = 0; i < count; i++) {
                float[] row = x[indices[i]];
                for (int f = 0; f < width; f++) {
                    if (!Float.isFinite(row[f])) throw new IllegalArgumentException("Non-finite feature");
                    features.putFloat(row[f]);
                }
                float priorLogit = prior.score(row);
                if (!Float.isFinite(priorLogit)) throw new IllegalArgumentException("Non-finite teacher logit");
                teacher.putFloat(priorLogit);
            }
            logp.putFloat(behaviorLogp);
            rows += count; decisions++;
        }
        @Override public void close() throws IOException {
            try (features; idx; logp; teacher) { /* close all even if a flush fails */ }
        }
    }

    static final class BinaryWriter implements AutoCloseable {
        final FileChannel channel;
        final ByteBuffer buffer = ByteBuffer.allocate(1 << 20).order(ByteOrder.LITTLE_ENDIAN);
        BinaryWriter(Path path) throws IOException {
            channel = FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }
        void putInt(int value) throws IOException { if (buffer.remaining() < 4) flush(); buffer.putInt(value); }
        void putFloat(float value) throws IOException { if (buffer.remaining() < 4) flush(); buffer.putFloat(value); }
        void flush() throws IOException {
            buffer.flip(); while (buffer.hasRemaining()) channel.write(buffer); buffer.clear();
        }
        @Override public void close() throws IOException { try { flush(); } finally { channel.close(); } }
    }

    static String sha(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var in = Files.newInputStream(path)) {
            byte[] buf = new byte[65536]; int n;
            while ((n = in.read(buf)) >= 0) digest.update(buf, 0, n);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    private static String sha(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
}
