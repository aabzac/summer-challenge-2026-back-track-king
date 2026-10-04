import btk.bot.Params;
import btk.bot.current.IndrillFeatures;
import btk.bot.current.IndrillV2Net;
import btk.core.GameState;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Random;

/** Synthetic frozen-Java implementation witnesses; no replay reads or fitting. */
public final class IndrillCertificateSynthetic {
    static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    static String pin(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("NEW_RESULT_JSON required");
        Path output = Path.of(args[0]);
        require(!Files.exists(output), "Refusing existing synthetic result");
        int[] regions = new int[15];
        for (int c = 0; c < 15; c++) regions[c] = c;
        GameState state = new GameState(5, 3, regions, new int[15], new int[]{0, 4},
                new int[]{0, 2}, new int[][]{{1}, {0}});
        IndrillFeatures features = new IndrillFeatures(state, new Params());
        // Intentionally constant synthetic inputs; actual coordinate features
        // are not discarded or altered by production/export code.
        for (int c = 0; c < 15; c++) features.x[c][0] = .25f;
        int[][] groups = {{1, 2, 3}, {6, 7, 8}, {11, 12, 13}};
        int[] shapes = {64*24, 24, 24*24, 24*24, 24*24, 24, 24, 24, 1, 1, 24, 1};
        for (int seed = 0; seed < 12; seed++) {
            Random rng = new Random(8100 + seed);
            float[][] weights = new float[shapes.length][];
            for (int t = 0; t < shapes.length; t++) {
                weights[t] = new float[shapes[t]];
                for (int i = 0; i < shapes[t]; i++) weights[t][i] = (float) (rng.nextGaussian() * .05);
            }
            IndrillV2Net net = new IndrillV2Net(weights);
            net.init(state);
            float[] build = new float[15], ink = new float[15];
            net.score(state, features, build, ink);
            for (int[] group : groups) for (int c : group) {
                require(Float.isFinite(build[c]) && Float.isFinite(ink[c]), "Nonfinite synthetic output");
                require(Float.floatToRawIntBits(build[c]) == Float.floatToRawIntBits(build[group[0]]), "Java build equality failed");
                require(Float.floatToRawIntBits(ink[c]) == Float.floatToRawIntBits(ink[group[0]]), "Java region equality failed");
            }
        }
        float[] levels = {-1e30f, -1, 0, 1, 1e30f};
        int checks = 0;
        for (int cost = 1; cost <= 3; cost++) for (float equal : levels)
            for (float first : levels) for (float middle : levels) for (float last : levels) {
                int[] selected = new int[3];
                int count = IndrillV2Net.selectBuild(new float[]{first, equal, middle, equal, last},
                        new int[]{1, cost, 2, cost, 3}, selected);
                boolean lower = false, higher = false;
                for (int i = 0; i < count; i++) { lower |= selected[i] == 1; higher |= selected[i] == 3; }
                require(!higher || lower, "Same-cost shortlist bypassed lower tie");
                checks++;
            }
        Path draft = Path.of("tools/indrill/capacity_v1");
        String[] files = {
                "tools/indrill/capacity_v1/IndrillCertificateSynthetic.java",
                "build/analysis/indrill-capacity-v1/setup/export-classes/IndrillCertificateSynthetic.class",
                "src/main/java/btk/bot/current/IndrillV2Net.java",
                "build/classes/java/main/btk/bot/current/IndrillV2Net.class",
                "build/classes/java/main/btk/bot/current/IndrillFeatures.class",
                "build/classes/java/main/btk/core/GameState.class"};
        StringBuilder pins = new StringBuilder();
        for (String file : files) {
            if (!pins.isEmpty()) pins.append(',');
            pins.append('"').append(file).append("\":\"").append(pin(Path.of(file))).append('"');
        }
        String result = "{\"syntheticOnly\":true,\"passed\":true,\"sampledJavaModels\":12,"
                + "\"sameCostDecoderWitnesses\":" + checks + ",\"sourceAndClassPins\":{" + pins + "},"
                + "\"scope\":\"Frozen Java scalar implementation witnesses only; no universal NumPy equality or program-capacity claim.\"}\n";
        Files.writeString(output, result, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        System.out.print(result);
    }
}
