package btk.submit;

import btk.bot.BotFactory;
import com.google.gson.GsonBuilder;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.TreeMap;
import java.nio.file.StandardOpenOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Merges the submission sources into a single {@code build/submit/Player.java}: every class
 * becomes a static nested class of {@code Player}, packages are dropped, imports are unioned.
 * Then compiles the result to catch merge errors before pasting into CodinGame.
 *
 * <p>Uses {@link #SUBMIT_DIRS}, {@link #SUBMIT_FILES}, and only the selected policy.
 * Frozen v05/v06/v07/v08/v09 builds import their source as the generated
 * EvalBot and embed resolved parameters; current builds also embed their full configuration;
 * no frozen-package runtime dependency, referee, runner or tool ships.
 */
public class FileMerger {

    private static final Path SRC_ROOT = Path.of("src/main/java/btk");
    private static final Path DEFAULT_OUTPUT_DIR = Path.of("build/submit");
    private static final Path REGISTRY = Path.of("build/submission-registry");
    static final String EMPTY_MARKER_DECLARATION = "public static final String BUILD_MARKER = \"\";";
    static final Pattern MARKER_DECLARATION = Pattern.compile(
            "public static final String BUILD_MARKER = \"([^\"\\r\\n]*)\";");
    /** User-confirmed CG arena source limit; conservatively count Java UTF-16 code units. */
    public static final int MAX_SOURCE_CHARS = 100_000;

    private static final List<String> SUBMIT_DIRS = List.of("core");
    private static final List<String> SUBMIT_FILES = List.of(
            "bot/Bot.java", "bot/Params.java", "bot/Timer.java", "bot/MainBot.java",
            "submit/CGRunner.java");

    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+[\\w.]+\\s*;\\s*$");
    private static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+(.*);\\s*$");
    private static final Pattern PUBLIC_TYPE = Pattern.compile(
            "^(\\s*)public\\s+((?:(?:final|abstract|sealed|strictfp)\\s+)*)(class|interface|enum|record)\\s+");

    public static void main(String[] args) throws IOException {
        String version = args.length == 0 ? "current" : args[0];
        Path outputDir = args.length > 1 && !args[1].isBlank() ? Path.of(args[1]) : DEFAULT_OUTPUT_DIR;
        Path output = outputDir.resolve("Player.java");
        String source = merge(version);
        checkSourceSize(source);
        Files.createDirectories(outputDir);
        Files.writeString(output, source, StandardCharsets.UTF_8);
        compileCheck(output);
        Map<String, Object> manifest = manifest(version, source);
        String manifestJson = new GsonBuilder().setPrettyPrinting().create().toJson(manifest);
        Files.writeString(output.resolveSibling("manifest.json"), manifestJson, StandardCharsets.UTF_8);
        Map<String, Object> registered = new LinkedHashMap<>(manifest);
        // Equivalent override spellings can produce the same exact artifact. The registry uses
        // a canonical strategy id plus the full configuration, while the export retains the request.
        registered.put("strategy", manifest.get("strategyId"));
        register(REGISTRY, source, new GsonBuilder().setPrettyPrinting().create().toJson(registered),
                (String) manifest.get("buildId"));
        System.out.printf("Submission %s: %d/%d characters, %d UTF-8 bytes, SHA-256 %s%n",
                version, source.length(), MAX_SOURCE_CHARS,
                source.getBytes(StandardCharsets.UTF_8).length, manifest.get("sha256"));
        System.out.println("Build identity: " + manifest.get("buildId") + " -> " + output);
    }

    static Map<String, Object> manifest(String version, String source) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schemaVersion", 2);
        manifest.put("strategy", version);
        manifest.put("strategyId", version.split("\\?", 2)[0]);
        manifest.put("params", BotFactory.configuration(version));
        manifest.put("charactersUtf16", source.length());
        manifest.put("bytesUtf8", source.getBytes(StandardCharsets.UTF_8).length);
        manifest.put("sourceLimit", MAX_SOURCE_CHARS);
        manifest.put("sha256", sha256(source));
        manifest.put("buildId", sha256(normalizeMarker(source)));
        manifest.put("configSha256", configSha256(version));
        String marker = marker(version, (String) manifest.get("buildId"));
        Matcher declaration = MARKER_DECLARATION.matcher(source);
        if (!declaration.find() || !declaration.group(1).equals(marker))
            throw new IllegalArgumentException("Source marker does not match requested strategy/configuration");
        manifest.put("marker", marker);
        manifest.put("identityScheme", "sha256-source-with-empty-BUILD_MARKER-v1");
        manifest.put("attribution", "Replay MESSAGE is a self-reported identity. Matching the locally retained "
                + "artifact verifies the marker mapping, not independently which source the server executed.");
        return manifest;
    }

    static String merge(String version) throws IOException {
        String spec = version;
        int queryAt = version.indexOf('?');
        if (queryAt >= 0) version = version.substring(0, queryAt);
        boolean neuralV2 = version.equals("neural-v2");
        boolean neuralV3 = version.equals("neural-v3");
        boolean neuralV4 = version.equals("neural-v4");
        boolean neuralV5 = version.equals("neural-v5");
        boolean neuralV6 = version.equals("neural-v6");
        boolean neuralV7 = version.equals("neural-v7");
        boolean neuralDeadline = neuralV3 || neuralV4 || neuralV5 || neuralV6 || neuralV7;
        boolean neural = version.equals("neural-v1") || neuralV2 || neuralDeadline;
        boolean indrillV2 = version.equals("indrill-q-v2");
        boolean indrill = version.equals("indrill-q-v1") || indrillV2;
        boolean spatialV2 = version.equals("spatial-ppo-v2");
        boolean spatialV3 = version.equals("spatial-ppo-v3");
        boolean spatialInitial = version.equals("spatial-ppo-v2-init");
        boolean spatial = version.equals("spatial-ppo-v1") || spatialV2 || spatialV3 || spatialInitial;
        String spatialWeights = spatialV3 ? "SpatialV3Weights" : spatialV2 ? "SpatialV2Weights"
                : spatialInitial ? "SpatialInitialWeights" : "SpatialWeights";
        if (!version.equals("current") && !version.equals("v05") && !version.equals("v06")
                && !version.equals("v07") && !version.equals("v08") && !version.equals("v09") && !neural && !indrill && !spatial) {
            throw new IllegalArgumentException("submitVersion must be current, v05, v06, v07, v08, v09, neural-v1, neural-v2, neural-v3, neural-v4, neural-v5, neural-v6, neural-v7, indrill-q-v1, indrill-q-v2, spatial-ppo-v1, spatial-ppo-v2, spatial-ppo-v2-init or spatial-ppo-v3");
        }
        List<Path> files = new ArrayList<>();
        boolean heuristic = version.equals("current") && BotFactory.parameters(spec).USE_HEURISTIC_POLICY;
        boolean rollout = version.equals("v06") || version.equals("v07") || version.equals("v08") || version.equals("v09")
                || version.equals("current") && BotFactory.parameters(spec).USE_ROLLOUT_POLICY;
        if (rollout && heuristic) throw new IllegalArgumentException("Select only one independent policy");
        String policy = version.equals("v05") ? "bot/v05/EvalBotV05.java"
                : version.equals("v06") ? "bot/v06/RolloutBotV06.java"
                : version.equals("v07") ? "bot/v07/RolloutBotV07.java"
                : version.equals("v08") ? "bot/v08/RolloutBotV08.java"
                : version.equals("v09") ? "bot/v09/RolloutBotV09.java"
                : spatial ? "bot/neural/SpatialBot.java"
                : neuralV7 ? "bot/neural/NeuralV7Bot.java"
                : neuralV6 ? "bot/neural/NeuralV6Bot.java"
                : neuralV5 ? "bot/neural/NeuralV5Bot.java"
                : neuralV4 ? "bot/neural/NeuralV4Bot.java"
                : neuralV3 ? "bot/neural/NeuralV3Bot.java"
                : neuralV2 ? "bot/neural/NeuralV2Bot.java"
                : neural ? "bot/neural/NeuralBot.java"
                : indrillV2 ? "bot/current/IndrillV2Bot.java"
                : indrill ? "bot/current/IndrillBot.java"
                : rollout ? "bot/current/RolloutBot.java"
                : heuristic ? "bot/current/HeuristicBot.java" : "bot/current/EvalBot.java";
        for (String dir : SUBMIT_DIRS) {
            try (Stream<Path> st = Files.list(SRC_ROOT.resolve(dir))) {
                st.filter(p -> p.toString().endsWith(".java")
                        && !p.endsWith("TacticalPathing.java") && !p.endsWith("InkProjection.java")
                        && !p.endsWith("HypotheticalIncome.java") && !p.endsWith("FeaturePaths.java"))
                        .sorted().forEach(files::add);
            }
        }
        if (indrill || version.equals("current") && rollout) files.add(SRC_ROOT.resolve("core/TacticalPathing.java"));
        if (neuralV5 || neuralV6 || neuralV7) files.add(SRC_ROOT.resolve("core/HypotheticalIncome.java"));
        if (neuralV6 || neuralV7) files.add(SRC_ROOT.resolve("core/FeaturePaths.java"));
        files.add(SRC_ROOT.resolve(policy));
        if (spatial) {
            files.add(SRC_ROOT.resolve("bot/neural/SpatialObservation.java"));
            files.add(SRC_ROOT.resolve("bot/neural/SpatialNet.java"));
            files.add(SRC_ROOT.resolve("bot/neural/" + spatialWeights + ".java"));
        }
        if (neural) {
            files.add(SRC_ROOT.resolve(neuralV6 || neuralV7 ? "bot/neural/NeuralV6Features.java"
                    : neuralV5 ? "bot/neural/NeuralV5Features.java"
                    : neuralDeadline ? "bot/neural/NeuralV3Features.java" : "bot/neural/NeuralFeatures.java"));
            files.add(SRC_ROOT.resolve("bot/neural/NeuralNet.java"));
            files.add(SRC_ROOT.resolve(neuralV4 ? "bot/neural/NeuralV4Weights.java"
                    : neuralV2 || neuralV3 || neuralV5 || neuralV6 || neuralV7 ? "bot/neural/NeuralV2Weights.java" : "bot/neural/NeuralWeights.java"));
        }
        if (indrill) {
            files.add(SRC_ROOT.resolve("bot/current/IndrillFeatures.java"));
            files.add(SRC_ROOT.resolve(indrillV2 ? "bot/current/IndrillV2Net.java" : "bot/current/IndrillNet.java"));
            files.add(SRC_ROOT.resolve(indrillV2 ? "bot/current/IndrillV2Weights.java" : "bot/current/IndrillWeights.java"));
        }
        if (version.equals("current") && rollout)
            files.add(SRC_ROOT.resolve("bot/current/ProtectedOpening.java"));
        if (version.equals("v08"))
            files.add(SRC_ROOT.resolve("bot/v08/ProtectedOpeningV08.java"));
        if (version.equals("v09"))
            files.add(SRC_ROOT.resolve("bot/v09/ProtectedOpeningV09.java"));
        for (String f : SUBMIT_FILES) files.add(SRC_ROOT.resolve(f));

        Set<String> imports = new TreeSet<>();
        StringBuilder body = new StringBuilder();
        for (Path file : files) {
            if (!Files.exists(file)) throw new IOException("missing " + file);
            boolean inBlockComment = false;
            for (String line : Files.readAllLines(file)) {
                if (!spatial && file.endsWith("Params.java") && line.contains("public int SPATIAL_RESERVE_MS =")) continue;
                if (spatial && file.endsWith("SpatialBot.java")) line = line.replaceAll("\\bSpatialBot\\b", "EvalBot");
                if ((spatialV2 || spatialV3 || spatialInitial) && file.endsWith("SpatialBot.java"))
                    line = line.replace("\"spatial-ppo-v1\"", "\"" + version + "\"");
                if ((spatialV2 || spatialV3 || spatialInitial) && file.endsWith(spatialWeights + ".java"))
                    line = line.replaceAll("\\b" + spatialWeights + "\\b", "SpatialWeights");
                if (!(version.equals("current") && rollout) && file.endsWith("Params.java")
                        && (line.contains("public boolean USE_ROLLOUT_ALL_CELL_RISK =")
                        || line.contains("public boolean USE_ROLLOUT_ROOT_FILL ="))) continue;
                // Do not alter existing submissions that do not consume the neural deadline.
                if (!neuralDeadline && file.endsWith("Params.java") && line.contains("public int NEURAL_DEADLINE_RESERVE_MS =")) continue;
                if (!neuralV7 && file.endsWith("Params.java") && (line.contains("public boolean USE_NEURAL_FALLBACK =")
                        || line.contains("public int NEURAL_FALLBACK_MAX_INSTABILITY ="))) continue;
                if (neuralV3) line = line.replaceAll("\\bNeuralV3Bot\\b", "EvalBot")
                        .replaceAll("\\bNeuralV3Features\\b", "NeuralFeatures")
                        .replaceAll("\\bNeuralV2Weights\\b", "NeuralWeights");
                if (neuralV4) line = line.replaceAll("\\bNeuralV4Bot\\b", "EvalBot")
                        .replaceAll("\\bNeuralV3Features\\b", "NeuralFeatures")
                        .replaceAll("\\bNeuralV4Weights\\b", "NeuralWeights");
                if (neuralV5) line = line.replaceAll("\\bNeuralV5Bot\\b", "EvalBot")
                        .replaceAll("\\bNeuralV5Features\\b", "NeuralFeatures")
                        .replaceAll("\\bNeuralV2Weights\\b", "NeuralWeights");
                if (neuralV6) line = line.replaceAll("\\bNeuralV6Bot\\b", "EvalBot")
                        .replaceAll("\\bNeuralV6Features\\b", "NeuralFeatures")
                        .replaceAll("\\bNeuralV2Weights\\b", "NeuralWeights");
                if (neuralV7) line = line.replaceAll("\\bNeuralV7Bot\\b", "EvalBot")
                        .replaceAll("\\bNeuralV6Features\\b", "NeuralFeatures")
                        .replaceAll("\\bNeuralV2Weights\\b", "NeuralWeights");
                // This line-based merger cannot preserve text-block indentation/comment contents.
                if (line.contains("\"\"\"")) throw new IllegalArgumentException("Submission merger does not support text blocks: " + file);
                // Import frozen source as the generated current EvalBot, without editing v05.
                if (version.equals("v05") && file.endsWith("EvalBotV05.java")) {
                    line = line.replaceAll("\\bEvalBotV05\\b", "EvalBot");
                }
                if (version.equals("v06") && file.endsWith("RolloutBotV06.java")) {
                    line = line.replaceAll("\\bRolloutBotV06\\b", "EvalBot");
                }
                if (version.equals("v07") && file.endsWith("RolloutBotV07.java")) {
                    line = line.replaceAll("\\bRolloutBotV07\\b", "EvalBot");
                }
                if (version.equals("v08") && (file.endsWith("RolloutBotV08.java")
                        || file.endsWith("ProtectedOpeningV08.java"))) {
                    line = line.replaceAll("\\bRolloutBotV08\\b", "EvalBot")
                            .replaceAll("\\bProtectedOpeningV08\\b", "ProtectedOpening");
                }
                if (version.equals("v09") && (file.endsWith("RolloutBotV09.java")
                        || file.endsWith("ProtectedOpeningV09.java"))) {
                    line = line.replaceAll("\\bRolloutBotV09\\b", "EvalBot")
                            .replaceAll("\\bProtectedOpeningV09\\b", "ProtectedOpening");
                }
                if (neural && file.endsWith("NeuralBot.java")) {
                    line = line.replaceAll("\\bNeuralBot\\b", "EvalBot");
                }
                if (neuralV2 && file.endsWith("NeuralV2Bot.java")) {
                    line = line.replaceAll("\\bNeuralV2Bot\\b", "EvalBot");
                }
                if (indrill && file.endsWith("IndrillBot.java")) {
                    line = line.replaceAll("\\bIndrillBot\\b", "EvalBot");
                }
                if (indrillV2 && file.endsWith("IndrillV2Bot.java")) {
                    line = line.replaceAll("\\bIndrillV2Bot\\b", "EvalBot");
                }
                if (file.endsWith("MainBot.java")) {
                    line = line.replace("new Params()", paramsExpression(spec));
                }
                if (heuristic && file.endsWith("HeuristicBot.java")) {
                    line = line.replaceAll("\\bHeuristicBot\\b", "EvalBot");
                }
                if (rollout && file.endsWith("RolloutBot.java")) {
                    line = line.replaceAll("\\bRolloutBot\\b", "EvalBot");
                }
                if (file.endsWith("MainBot.java")) {
                    if (line.contains("if (params.USE_ROLLOUT_POLICY) return new RolloutBot(params);")) continue;
                    line = line.replace("return params.USE_HEURISTIC_POLICY ? new HeuristicBot(params) : new EvalBot(params);",
                            "return new EvalBot(params);");
                }
                String trimmed = line.trim();
                if (inBlockComment) {
                    if (trimmed.contains("*/")) inBlockComment = false;
                    continue;
                }
                if (trimmed.startsWith("/*")) {
                    if (!trimmed.contains("*/")) inBlockComment = true;
                    continue;
                }
                if (trimmed.isEmpty() || trimmed.startsWith("//")) continue;
                if (PACKAGE.matcher(line).matches()) continue;
                Matcher im = IMPORT.matcher(line);
                if (im.matches()) {
                    String imp = im.group(1).trim();
                    if (!imp.startsWith("btk.")) imports.add(imp);
                    continue;
                }
                Matcher pt = PUBLIC_TYPE.matcher(line);
                if (pt.find()) {
                    line = pt.group(1) + "static " + pt.group(2) + pt.group(3) + " " + line.substring(pt.end());
                }
                // Current rollout has optional experiments; punctuation already separates these tokens.
                // Keep newlines around line comments. Historical artifacts retain their original bytes.
                String code = line.strip();
                body.append(code);
                if (!(version.equals("current") && rollout && !code.contains("//")
                        && (code.endsWith(";") || code.endsWith("{") || code.endsWith("}")))) body.append('\n');
            }
        }

        StringBuilder sb = new StringBuilder();
        for (String imp : imports) sb.append("import ").append(imp).append(";\n");
        // Keep the strategy label in the normalized source identity too: identical
        // policy copies with different names must not collide in the registry.
        sb.append("\n// BTK strategy: ").append(version).append("\nclass Player {\n\n");
        sb.append(body);
        sb.append("    public static void main(String[] args) {\n");
        sb.append("        CGRunner.main(args);\n");
        sb.append("    }\n}\n");

        String normalized = sb.toString();
        if (normalized.indexOf(EMPTY_MARKER_DECLARATION) < 0
                || normalized.indexOf(EMPTY_MARKER_DECLARATION) != normalized.lastIndexOf(EMPTY_MARKER_DECLARATION)) {
            throw new IllegalStateException("Expected exactly one empty submission marker declaration");
        }
        return normalized.replace(EMPTY_MARKER_DECLARATION,
                "public static final String BUILD_MARKER = \"" + marker(spec, sha256(normalized)) + "\";");
    }

    static String normalizeMarker(String source) {
        Matcher match = MARKER_DECLARATION.matcher(source);
        if (!match.find()) throw new IllegalArgumentException("Missing build marker declaration");
        String normalized = match.replaceFirst(Matcher.quoteReplacement(EMPTY_MARKER_DECLARATION));
        if (MARKER_DECLARATION.matcher(normalized).results().count() != 1)
            throw new IllegalArgumentException("Ambiguous build marker declaration");
        return normalized;
    }

    static String configSha256(String spec) {
        StringBuilder canonical = new StringBuilder();
        for (var entry : new TreeMap<>(BotFactory.configuration(spec)).entrySet())
            canonical.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        return sha256(canonical.toString());
    }

    private static String marker(String spec, String buildId) {
        return "BTK_BUILD_V1 strategy=" + spec.split("\\?", 2)[0]
                + " build=" + buildId + " config=" + configSha256(spec);
    }

    static void register(Path registry, String source, String manifestJson, String buildId) throws IOException {
        Path directory = registry.resolve(buildId);
        Files.createDirectories(directory);
        immutableWrite(directory.resolve("Player.java"), source);
        immutableWrite(directory.resolve("manifest.json"), manifestJson);
    }

    private static void immutableWrite(Path path, String contents) throws IOException {
        if (Files.exists(path)) {
            if (!Files.readString(path, StandardCharsets.UTF_8).equals(contents))
                throw new IOException("Refusing to replace registered artifact: " + path);
        } else Files.writeString(path, contents, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    private static String paramsExpression(String spec) {
        StringBuilder query = new StringBuilder();
        for (var entry : BotFactory.configuration(spec).entrySet()) {
            if (query.length() > 0) query.append(',');
            query.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return "new Params(\"" + query + "\")";
    }

    static void checkSourceSize(String source) {
        if (source.length() > MAX_SOURCE_CHARS) throw new IllegalStateException(
                "Submission exceeds " + MAX_SOURCE_CHARS + " characters: " + source.length());
    }

    static String sha256(String source) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void compileCheck(Path output) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IOException("A JDK compiler is required to verify the submission");
        }
        Path outDir = output.getParent().resolve("classes");
        Files.createDirectories(outDir);
        int rc = compiler.run(null, null, null, "-encoding", "UTF-8", "-d", outDir.toString(), "-Xlint:none",
                output.toString());
        if (rc != 0) {
            throw new IOException("Player.java does not compile (exit " + rc + ")");
        }
        System.out.println("Compile check OK -> " + outDir);
    }
}
