package btk.tools;

import btk.bot.neural.NeuralFeatures;
import btk.bot.neural.NeuralNet;
import btk.bot.neural.NeuralV4Weights;
import btk.core.Protocol;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/** Read-only corpus exclusions and generated-policy numeric evidence; never plays games. */
public final class NeuralV4Verify {
    private static final int DECISIONS_PER_HEAD = 32;
    private NeuralV4Verify() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("mode(maps|parity) paths-or-dataset weights-json output-json");
        Path output = Path.of(args[3]);
        if (Files.exists(output)) throw new IllegalStateException("Verification output exists; preserve prior evidence");
        Map<String, Object> result = switch (args[0]) {
            case "maps" -> maps(args[1]);
            case "parity" -> parity(Path.of(args[1]), Path.of(args[2]));
            default -> throw new IllegalArgumentException("Unknown verification mode: " + args[0]);
        };
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        System.out.println("Saved neural-v4 " + args[0] + " evidence: " + output);
    }

    static Map<String, Object> maps(String inputs) throws Exception {
        if (inputs.isBlank()) throw new IllegalArgumentException("Supply explicit replay files or directories");
        var files = new TreeSet<Path>();
        for (String input : inputs.split(",")) {
            Path path = ReplayPaths.resolve(Path.of(input.trim()));
            if (!Files.exists(path)) throw new IOException("Missing exclusion input: " + path);
            List<Path> selected = Files.isDirectory(path) ? CgReplay.listReplays(path) : List.of(path);
            if (selected.isEmpty()) throw new IOException("No canonical replays in exclusion input: " + path);
            for (Path file : selected) files.add(file.toAbsolutePath().normalize());
        }
        var gamesPerMap = new TreeMap<String, Integer>();
        var replays = new ArrayList<Map<String, Object>>();
        for (Path file : files) {
            CgReplay replay = CgReplay.load(file);
            String mapHash = hash(Protocol.initBlock(replay.toState(), 0).getBytes(StandardCharsets.UTF_8));
            gamesPerMap.merge(mapHash, 1, Integer::sum);
            var item = new LinkedHashMap<String, Object>();
            item.put("path", file.toString());
            item.put("sha256", fileHash(file));
            item.put("gameId", replay.gameId);
            item.put("mapHash", mapHash);
            replays.add(item);
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("schemaVersion", 1);
        result.put("purpose", "neural-v4 teacher/boss/process map exclusion; no generated games");
        result.put("hashScheme", "sha256-utf8-Protocol.initBlock(initialState,0)");
        result.put("gamesPerMap", gamesPerMap);
        result.put("replays", replays);
        return result;
    }

    static Map<String, Object> parity(Path dataset, Path weightsPath) throws Exception {
        JsonObject weights = JsonParser.parseString(Files.readString(weightsPath)).getAsJsonObject();
        var cases = new ArrayList<Map<String, Object>>();
        var dataHashes = new TreeMap<String, String>();
        for (String head : new String[]{"sabotage", "build"}) {
            boolean sabotage = head.equals("sabotage");
            int width = sabotage ? NeuralFeatures.SABOTAGE_F : NeuralFeatures.BUILD_F;
            int[] compiledSizes = sabotage ? NeuralV4Weights.SABOTAGE_SIZES : NeuralV4Weights.BUILD_SIZES;
            String compiledBlob = sabotage ? NeuralV4Weights.SABOTAGE : NeuralV4Weights.BUILD;
            JsonObject item = weights.getAsJsonObject(head);
            int[] declaredSizes = new int[item.getAsJsonArray("sizes").size()];
            for (int i = 0; i < declaredSizes.length; i++) declaredSizes[i] = item.getAsJsonArray("sizes").get(i).getAsInt();
            if (!Arrays.equals(declaredSizes, compiledSizes) || !item.get("base64").getAsString().equals(compiledBlob))
                throw new IllegalStateException("Compiled generated " + head + " weights differ from supplied artifact");
            NeuralNet net = NeuralV4Collect.readHead(weights, head, width);
            Path indexPath = dataset.resolve(head + ".idx"), rowsPath = dataset.resolve(head + ".bin");
            long indexBytes = Files.size(indexPath), rowBytes = Files.size(rowsPath);
            if (indexBytes == 0 || indexBytes % 24 != 0 || rowBytes % (4L * width) != 0)
                throw new IOException("Invalid parity dataset sizes for " + head);
            dataHashes.put(head + ".idx", fileHash(indexPath));
            dataHashes.put(head + ".bin", fileHash(rowsPath));
            int count = (int) Math.min(DECISIONS_PER_HEAD, indexBytes / 24);
            try (FileChannel index = FileChannel.open(indexPath); FileChannel rows = FileChannel.open(rowsPath)) {
                ByteBuffer groups = read(index, 0, count * 24);
                for (int d = 0; d < count; d++) {
                    int game = groups.getInt(), turn = groups.getInt(), k = groups.getInt();
                    int start = groups.getInt(), n = groups.getInt(), label = groups.getInt();
                    if (start < 0 || n < 2 || label < 0 || label >= n || (start + (long) n) * width * 4 > rowBytes)
                        throw new IOException("Invalid candidate group in " + head + " at " + d);
                    ByteBuffer features = read(rows, start * (long) width * 4, Math.multiplyExact(n, width * 4));
                    float[] row = new float[width], scores = new float[n];
                    int argmax = 0;
                    for (int c = 0; c < n; c++) {
                        for (int f = 0; f < width; f++) {
                            row[f] = features.getFloat();
                            if (!Float.isFinite(row[f])) throw new IOException("Non-finite parity feature");
                        }
                        scores[c] = net.score(row);
                        if (!Float.isFinite(scores[c])) throw new IOException("Non-finite parity score");
                        if (scores[c] > scores[argmax]) argmax = c;
                    }
                    var sample = new LinkedHashMap<String, Object>();
                    sample.put("head", head); sample.put("game", game); sample.put("turn", turn); sample.put("k", k);
                    sample.put("rowStart", start); sample.put("count", n); sample.put("scores", scores); sample.put("argmax", argmax);
                    cases.add(sample);
                }
            }
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("schemaVersion", 1);
        result.put("selection", "first32DecisionsPerHead/allCandidates");
        result.put("compiledGeneratedWeightsMatch", true);
        result.put("weightsSha256", fileHash(weightsPath));
        result.put("dataSha256", dataHashes);
        result.put("cases", cases);
        return result;
    }

    private static ByteBuffer read(FileChannel channel, long position, int bytes) throws IOException {
        ByteBuffer result = ByteBuffer.allocate(bytes).order(ByteOrder.LITTLE_ENDIAN);
        while (result.hasRemaining()) {
            int read = channel.read(result, position + result.position());
            if (read <= 0) throw new IOException("Truncated verification input");
        }
        return result.flip();
    }

    private static String fileHash(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536];
            for (int n; (n = input.read(buffer)) >= 0;) if (n > 0) digest.update(buffer, 0, n);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String hash(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}
