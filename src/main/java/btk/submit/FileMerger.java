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
        return merge(version, true);
    }

    /** Verification reference: preserves the enabled policy's source before lexical packaging. */
    static String mergeUncompressed(String version) throws IOException {
        return merge(version, false);
    }

    private static String merge(String version, boolean compactCandidate) throws IOException {
        String spec = version;
        if (BotFactory.parameters(spec).USE_NEURAL_TARGET_PROPOSAL)
            throw new IllegalArgumentException("USE_NEURAL_TARGET_PROPOSAL is local-only: candidate packaging and process checks are deferred");
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
        boolean spatialV4 = version.equals("spatial-ppo-v4");
        boolean spatialV5 = version.equals("spatial-ppo-v5");
        boolean spatialV6 = version.equals("spatial-ppo-v6");
        boolean spatialV7 = version.equals("spatial-ppo-v7");
        boolean spatialV8 = version.equals("spatial-ppo-v8");
        boolean spatialV9 = version.equals("spatial-ppo-v9");
        boolean spatialV10 = version.equals("spatial-ppo-v10");
        boolean spatialInitial = version.equals("spatial-ppo-v2-init");
        boolean spatial = version.equals("spatial-ppo-v1") || spatialV2 || spatialV3 || spatialV4 || spatialV5 || spatialV6 || spatialV7 || spatialV8 || spatialV9 || spatialV10 || spatialInitial;
        String spatialWeights = spatialV10 ? "SpatialV10Weights" : spatialV9 ? "SpatialV9Weights" : spatialV6 || spatialV7 || spatialV8 ? "SpatialV4Weights" : spatialV5 ? "SpatialV5Weights" : spatialV4 ? "SpatialV4Weights" : spatialV3 ? "SpatialV3Weights" : spatialV2 ? "SpatialV2Weights"
                : spatialInitial ? "SpatialInitialWeights" : "SpatialWeights";
        if (!version.equals("current") && !version.equals("v05") && !version.equals("v06")
                && !version.equals("v07") && !version.equals("v08") && !version.equals("v09") && !version.equals("v10") && !neural && !indrill && !spatial) {
            throw new IllegalArgumentException("submitVersion must be current, v05, v06, v07, v08, v09, v10, neural-v1, neural-v2, neural-v3, neural-v4, neural-v5, neural-v6, neural-v7, indrill-q-v1, indrill-q-v2, spatial-ppo-v1, spatial-ppo-v2, spatial-ppo-v2-init, spatial-ppo-v3, spatial-ppo-v4, spatial-ppo-v5, spatial-ppo-v6, spatial-ppo-v7, spatial-ppo-v8, spatial-ppo-v9 or spatial-ppo-v10");
        }
        List<Path> files = new ArrayList<>();
        boolean heuristic = version.equals("current") && BotFactory.parameters(spec).USE_HEURISTIC_POLICY;
        boolean rollout = version.equals("v06") || version.equals("v07") || version.equals("v08") || version.equals("v09")
                || version.equals("v10") || version.equals("current") && BotFactory.parameters(spec).USE_ROLLOUT_POLICY;
        if (rollout && heuristic) throw new IllegalArgumentException("Select only one independent policy");
        String policy = version.equals("v05") ? "bot/v05/EvalBotV05.java"
                : version.equals("v06") ? "bot/v06/RolloutBotV06.java"
                : version.equals("v07") ? "bot/v07/RolloutBotV07.java"
                : version.equals("v08") ? "bot/v08/RolloutBotV08.java"
                : version.equals("v09") ? "bot/v09/RolloutBotV09.java"
                : version.equals("v10") ? "bot/v10/RolloutBotV10.java"
                : spatialV9 ? "bot/neural/SpatialV9Bot.java"
                : spatialV8 ? "bot/neural/SpatialV8Bot.java"
                : spatialV7 ? "bot/neural/SpatialV7Bot.java"
                : spatialV6 ? "bot/neural/SpatialV6Bot.java"
                : spatialV5 ? "bot/neural/SpatialV5Bot.java"
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
        if (indrill || version.equals("current") && rollout || version.equals("v10")) files.add(SRC_ROOT.resolve("core/TacticalPathing.java"));
        if (neuralV5 || neuralV6 || neuralV7) files.add(SRC_ROOT.resolve("core/HypotheticalIncome.java"));
        if (neuralV6 || neuralV7) files.add(SRC_ROOT.resolve("core/FeaturePaths.java"));
        files.add(SRC_ROOT.resolve(policy));
        if (spatial) {
            if (spatialV5) files.add(SRC_ROOT.resolve("bot/neural/SpatialBot.java"));
            files.add(SRC_ROOT.resolve("bot/neural/SpatialObservation.java"));
            files.add(SRC_ROOT.resolve(spatialV9 ? "bot/neural/SpatialV9Net.java" : "bot/neural/SpatialNet.java"));
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
        if (version.equals("v10"))
            files.add(SRC_ROOT.resolve("bot/v10/ProtectedOpeningV10.java"));
        for (String f : SUBMIT_FILES) files.add(SRC_ROOT.resolve(f));

        Set<String> imports = new TreeSet<>();
        StringBuilder body = new StringBuilder();
        boolean missedDeadline = BotFactory.parameters(spec).USE_ROLLOUT_MISSED_DEADLINE;
        boolean bossSabotage = BotFactory.parameters(spec).USE_BOSS_SABOTAGE;
        if (bossSabotage) files.add(SRC_ROOT.resolve(version.equals("v10") ? "bot/v10/BossSabotageV10.java" : "bot/current/BossSabotage.java"));
        boolean bossLite = BotFactory.parameters(spec).USE_BOSS_LITE;
        boolean bossModel = BotFactory.parameters(spec).USE_BOSS_OPPONENT_MODEL || BotFactory.parameters(spec).USE_BOSS_OWN_MODEL;
        boolean bossRoutes = BotFactory.parameters(spec).USE_BOSS_LITE_ROUTES;
        boolean batchedRefresh = BotFactory.parameters(spec).USE_BATCHED_REFRESH;
        boolean incrementalInk = BotFactory.parameters(spec).USE_INCREMENTAL_INK_REFRESH;
        boolean fastCheapestPath = BotFactory.parameters(spec).USE_FAST_CHEAPEST_PATH;
        int turnBudgetMs = BotFactory.parameters(spec).TURN_BUDGET_MS;
        boolean turnBudgetOverride = turnBudgetMs != 36;
        for (Path file : files) {
            if (!Files.exists(file)) throw new IOException("missing " + file);
            boolean inBlockComment = false;
            boolean deadlineBlock = false;
            boolean bossBlock = false;
            boolean liteBlock = false;
            boolean holdBlock = false;
            boolean modelBlock = false;
            boolean routesBlock = false;
            boolean batchBlock = false;
            boolean turnBudgetBlock = false;
            boolean inkBlock = false;
            boolean fastPathBlock = false, oldPathBlock = false;
            for (String line : Files.readAllLines(file)) {
                if (line.trim().equals("// FAST_CHEAPEST_PATH_BEGIN")) {
                    if (fastPathBlock || oldPathBlock) throw new IllegalArgumentException("Nested fast-path block: " + file);
                    fastPathBlock = true;
                    continue;
                }
                if (line.trim().equals("// FAST_CHEAPEST_PATH_END")) {
                    if (!fastPathBlock) throw new IllegalArgumentException("Unbalanced fast-path block: " + file);
                    fastPathBlock = false;
                    continue;
                }
                if (line.trim().equals("// FAST_CHEAPEST_PATH_OLD_BEGIN")) {
                    if (fastPathBlock || oldPathBlock) throw new IllegalArgumentException("Nested old-path block: " + file);
                    oldPathBlock = true;
                    continue;
                }
                if (line.trim().equals("// FAST_CHEAPEST_PATH_OLD_END")) {
                    if (!oldPathBlock) throw new IllegalArgumentException("Unbalanced old-path block: " + file);
                    oldPathBlock = false;
                    continue;
                }
                if (fastPathBlock && !fastCheapestPath) continue;
                if (oldPathBlock && !fastCheapestPath && line.trim().equals("nc = ny * s.width + nx;"))
                    line = line.replace("nc =", "int nc =");
                if (!fastCheapestPath && file.endsWith("Pathing.java") && line.contains("pathNeighbors == null ?"))
                    line = line.replace("pathNeighbors == null ? ", "").replace(" : 0", "");
                if (fastPathBlock && file.endsWith("MainBot.java"))
                    line = line.replace("params.USE_NEURAL_TARGET_PROPOSAL", "false");
                if (line.trim().equals("// INK_REFRESH_BEGIN")) {
                    if (inkBlock) throw new IllegalArgumentException("Nested ink-refresh block: " + file);
                    inkBlock = true;
                    continue;
                }
                if (line.trim().equals("// INK_REFRESH_END")) {
                    if (!inkBlock) throw new IllegalArgumentException("Unbalanced ink-refresh block: " + file);
                    inkBlock = false;
                    continue;
                }
                if (inkBlock && !incrementalInk) continue;
                if (inkBlock && !batchedRefresh && file.endsWith("SearchState.java"))
                    line = line.replace("batchedRefresh", "false");
                if (line.trim().equals("// TURN_BUDGET_BEGIN")) {
                    if (turnBudgetBlock) throw new IllegalArgumentException("Nested turn-budget block: " + file);
                    turnBudgetBlock = true;
                    continue;
                }
                if (line.trim().equals("// TURN_BUDGET_END")) {
                    if (!turnBudgetBlock) throw new IllegalArgumentException("Unbalanced turn-budget block: " + file);
                    turnBudgetBlock = false;
                    continue;
                }
                if (turnBudgetBlock && !turnBudgetOverride) continue;
                // Export preflight rejects the local-only neural proposal flag; its Params field
                // is omitted from submissions, so fold only this guarded constructor check.
                if (turnBudgetBlock && file.endsWith("MainBot.java")) {
                    line = line.replace("params.USE_NEURAL_TARGET_PROPOSAL", "false");
                    // Keep the already measured 38/40-ms source and build marker byte-identical.
                    if (turnBudgetMs != 45) line = line.replace(" && params.TURN_BUDGET_MS != 45", "")
                            .replace("36, 38, 40 or 45 ms", "36, 38 or 40 ms")
                            .replace("38/40/45 ms requires", "38/40 ms requires");
                    if (turnBudgetMs == 38) line = line.replace(" && params.TURN_BUDGET_MS != 40", "")
                            .replace("36, 38 or 40 ms", "36 or 38 ms")
                            .replace("38/40 ms requires", "38 ms requires");
                }
                if (line.trim().equals("// BATCHED_REFRESH_BEGIN")) {
                    if (batchBlock) throw new IllegalArgumentException("Nested batch block: " + file);
                    batchBlock = true;
                    continue;
                }
                if (line.trim().equals("// BATCHED_REFRESH_END")) {
                    if (!batchBlock) throw new IllegalArgumentException("Unbalanced batch block: " + file);
                    batchBlock = false;
                    continue;
                }
                if (batchBlock && !batchedRefresh) continue;
                if (line.trim().equals("// MISSED_DEADLINE_BEGIN")) {
                    if (deadlineBlock) throw new IllegalArgumentException("Nested deadline block: " + file);
                    deadlineBlock = true;
                    continue;
                }
                if (line.trim().equals("// MISSED_DEADLINE_END")) {
                    if (!deadlineBlock) throw new IllegalArgumentException("Unbalanced deadline block: " + file);
                    deadlineBlock = false;
                    continue;
                }
                if (deadlineBlock && !missedDeadline) continue;
                if (line.trim().equals("// BOSS_SABOTAGE_BEGIN")) {
                    if (bossBlock) throw new IllegalArgumentException("Nested boss-sabotage block: " + file);
                    bossBlock = true;
                    continue;
                }
                if (line.trim().equals("// BOSS_SABOTAGE_END")) {
                    if (!bossBlock) throw new IllegalArgumentException("Unbalanced boss-sabotage block: " + file);
                    bossBlock = false;
                    continue;
                }
                if (bossBlock && !bossSabotage) continue;
                if (line.trim().equals("// BOSS_LITE_BEGIN")) {
                    if (liteBlock) throw new IllegalArgumentException("Nested boss-lite block: " + file);
                    liteBlock = true;
                    continue;
                }
                if (line.trim().equals("// BOSS_LITE_END")) {
                    if (!liteBlock) throw new IllegalArgumentException("Unbalanced boss-lite block: " + file);
                    liteBlock = false;
                    continue;
                }
                if (liteBlock && !bossLite) continue;
                if (line.trim().equals("// BOSS_HOLD_BEGIN")) {
                    if (holdBlock) throw new IllegalArgumentException("Nested boss-hold block: " + file);
                    holdBlock = true;
                    continue;
                }
                if (line.trim().equals("// BOSS_HOLD_END")) {
                    if (!holdBlock) throw new IllegalArgumentException("Unbalanced boss-hold block: " + file);
                    holdBlock = false;
                    continue;
                }
                if (holdBlock && !bossSabotage && !bossLite) continue;
                if (line.trim().equals("// BOSS_MODEL_BEGIN")) {
                    if (modelBlock) throw new IllegalArgumentException("Nested boss-model block: " + file);
                    modelBlock = true;
                    continue;
                }
                if (line.trim().equals("// BOSS_MODEL_END")) {
                    if (!modelBlock) throw new IllegalArgumentException("Unbalanced boss-model block: " + file);
                    modelBlock = false;
                    continue;
                }
                if (modelBlock && !bossModel) continue;
                if (line.trim().equals("// BOSS_ROUTES_BEGIN")) {
                    if (routesBlock) throw new IllegalArgumentException("Nested boss-routes block: " + file);
                    routesBlock = true;
                    continue;
                }
                if (line.trim().equals("// BOSS_ROUTES_END")) {
                    if (!routesBlock) throw new IllegalArgumentException("Unbalanced boss-routes block: " + file);
                    routesBlock = false;
                    continue;
                }
                if (routesBlock && !bossRoutes) continue;
                if (file.endsWith("Params.java") && line.contains("public boolean USE_NEURAL_TARGET_PROPOSAL =")) continue;
                if (!spatialV9 && file.endsWith("Params.java")
                        && line.contains("public boolean USE_SPATIAL_V9_TOWN_CONTEXT =")) continue;
                if (!spatialV8 && file.endsWith("Params.java")
                        && (line.contains("public boolean USE_SPATIAL_V8_SEARCH =")
                        || line.contains("public int SPATIAL_V8_MIN_SEARCH_MS =")
                        || line.contains("public int SPATIAL_V8_DEPTH ="))) continue;
                if (!spatialV7 && !spatialV8 && file.endsWith("Params.java")
                        && (line.contains("public boolean USE_SPATIAL_V7_SEARCH =")
                        || line.contains("public int SPATIAL_V7_MIN_SEARCH_MS =")
                        || line.contains("public int SPATIAL_V7_DEPTH ="))) continue;
                if (!spatialV6 && !spatialV7 && !spatialV8 && file.endsWith("Params.java")
                        && (line.contains("public boolean USE_SPATIAL_V6_SEARCH =")
                        || line.contains("public int SPATIAL_V6_MIN_SEARCH_MS =")
                        || line.contains("public int SPATIAL_V6_DEPTH ="))) continue;
                if (!spatialV5 && !spatialV6 && !spatialV7 && !spatialV8 && file.endsWith("Params.java")
                        && (line.contains("public boolean USE_SPATIAL_V5_SEARCH =")
                        || line.contains("public int SPATIAL_V5_MIN_SEARCH_MS ="))) continue;
                if (!spatial && file.endsWith("Params.java") && line.contains("public int SPATIAL_RESERVE_MS =")) continue;
                if (spatial && !spatialV5 && file.endsWith("SpatialBot.java")) line = line.replaceAll("\\bSpatialBot\\b", "EvalBot");
                if (spatialV5 && file.endsWith("SpatialV5Bot.java"))
                    line = line.replaceAll("\\bSpatialV5Bot\\b", "EvalBot")
                            .replaceAll("\\bSpatialV5Weights\\b", "SpatialWeights");
                if (spatialV6 && file.endsWith("SpatialV6Bot.java"))
                    line = line.replaceAll("\\bSpatialV6Bot\\b", "EvalBot")
                            .replaceAll("\\bSpatialV4Weights\\b", "SpatialWeights");
                if (spatialV7 && file.endsWith("SpatialV7Bot.java"))
                    line = line.replaceAll("\\bSpatialV7Bot\\b", "EvalBot")
                            .replaceAll("\\bSpatialV4Weights\\b", "SpatialWeights");
                if (spatialV8 && file.endsWith("SpatialV8Bot.java"))
                    line = line.replaceAll("\\bSpatialV8Bot\\b", "EvalBot")
                            .replaceAll("\\bSpatialV4Weights\\b", "SpatialWeights");
                if (spatialV9 && file.endsWith("SpatialV9Bot.java"))
                    line = line.replaceAll("\\bSpatialV9Bot\\b", "EvalBot")
                            .replaceAll("\\bSpatialV9Net\\b", "SpatialNet")
                            .replaceAll("\\bSpatialV9Weights\\b", "SpatialWeights");
                if (spatialV9 && file.endsWith("SpatialV9Net.java"))
                    line = line.replaceAll("\\bSpatialV9Net\\b", "SpatialNet");
                if ((spatialV2 || spatialV3 || spatialV4 || spatialV10 || spatialInitial) && file.endsWith("SpatialBot.java"))
                    line = line.replace("\"spatial-ppo-v1\"", "\"" + version + "\"");
                if ((spatialV2 || spatialV3 || spatialV4 || spatialV5 || spatialV6 || spatialV7 || spatialV8 || spatialV9 || spatialV10 || spatialInitial) && file.endsWith(spatialWeights + ".java"))
                    line = line.replaceAll("\\b" + spatialWeights + "\\b", "SpatialWeights");
                if (!((version.equals("current") || version.equals("v10")) && rollout) && file.endsWith("Params.java")
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
                if (version.equals("v10") && (file.endsWith("RolloutBotV10.java")
                        || file.endsWith("ProtectedOpeningV10.java") || file.endsWith("BossSabotageV10.java"))) {
                    line = line.replaceAll("\\bRolloutBotV10\\b", "EvalBot")
                            .replaceAll("\\bProtectedOpeningV10\\b", "ProtectedOpening")
                            .replaceAll("\\bBossSabotageV10\\b", "BossSabotage");
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
            if (deadlineBlock) throw new IllegalArgumentException("Unclosed deadline block: " + file);
            if (bossBlock) throw new IllegalArgumentException("Unclosed boss-sabotage block: " + file);
            if (liteBlock) throw new IllegalArgumentException("Unclosed boss-lite block: " + file);
            if (holdBlock) throw new IllegalArgumentException("Unclosed boss-hold block: " + file);
            if (modelBlock) throw new IllegalArgumentException("Unclosed boss-model block: " + file);
            if (routesBlock) throw new IllegalArgumentException("Unclosed boss-routes block: " + file);
            if (batchBlock) throw new IllegalArgumentException("Unclosed batch block: " + file);
            if (inkBlock) throw new IllegalArgumentException("Unclosed ink-refresh block: " + file);
            if (fastPathBlock || oldPathBlock) throw new IllegalArgumentException("Unclosed fast-path block: " + file);
            if (turnBudgetBlock) throw new IllegalArgumentException("Unclosed turn-budget block: " + file);
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
        if (compactCandidate && (version.equals("current") || version.equals("v10")) && (missedDeadline || batchedRefresh || incrementalInk || fastCheapestPath || bossSabotage || bossLite || bossModel || bossRoutes)) {
            normalized = JavaSourceCompactor.compact(normalized);
            String compactMarker = JavaSourceCompactor.compact(EMPTY_MARKER_DECLARATION).stripTrailing();
            if (normalized.indexOf(compactMarker) < 0
                    || normalized.indexOf(compactMarker) != normalized.lastIndexOf(compactMarker))
                throw new IllegalStateException("Expected exactly one compact empty marker declaration");
            // Preserve the established identity parser's spelling; this changes whitespace only.
            normalized = normalized.replace(compactMarker, EMPTY_MARKER_DECLARATION);
        }
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
