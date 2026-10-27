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
 *
 * <p>Since prune-v1 (2026-10-25) a current rollout artifact drops the {@code // LOCAL_ONLY_BEGIN/END}
 * blocks (Params fields the shipped policy never reads, local-only constructor guards), their values in
 * the embedded configuration, and the {@code NetworkChords}/{@code TacticalPathing} classes the pruned
 * policy no longer references. Frozen exports ({@code v10} included) keep every field and class.
 *
 * <p>Since readable-export-v1 (2026-10-25) a {@code current} export keeps its line structure and indentation
 * (tabs, one level deeper inside {@code Player}; comments and blank-line runs are dropped) instead of being
 * joined and compacted — it is compacted only when its readable form would exceed the source limit — and its
 * first-turn MESSAGE leads with the strategy's display name
 * ({@link BotFactory#displayName}) before the unchanged machine-readable {@code BTK_BUILD_V1} marker.
 * Frozen exports keep their historical bytes: compact {@code v10}, bare marker.
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

    private static final Pattern PARAM_FIELD = Pattern.compile("^public (?:boolean|int|double|String) ([A-Z_0-9]+) =");

    public static void main(String[] args) throws IOException {
        String version = args.length == 0 ? "current" : args[0];
        Path outputDir = args.length > 1 && !args[1].isBlank() ? Path.of(args[1]) : DEFAULT_OUTPUT_DIR;
        Path output = outputDir.resolve("Player.java");
        String source = merge(version);
        checkSourceSize(source);
        String readable = mergeUncompressed(version);
        if (!readable.equals(source)) System.out.printf("Readable form (%d characters) exceeds the %d limit: compacted%n",
                readable.length(), MAX_SOURCE_CHARS);
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
        String message = message(version, (String) manifest.get("buildId"));
        Matcher declaration = MARKER_DECLARATION.matcher(source);
        if (!declaration.find() || !declaration.group(1).equals(message))
            throw new IllegalArgumentException("Source marker does not match requested strategy/configuration");
        manifest.put("marker", marker);
        if (!message.equals(marker)) manifest.put("label", BotFactory.displayName(version));
        manifest.put("identityScheme", "sha256-source-with-empty-BUILD_MARKER-v1");
        manifest.put("attribution", "Replay MESSAGE is a self-reported identity. Matching the locally retained "
                + "artifact verifies the marker mapping, not independently which source the server executed.");
        return manifest;
    }

    static String merge(String version) throws IOException {
        return merge(version, true);
    }

    /** Verification reference: the enabled policy's source before lexical packaging (only {@code v10} is compacted). */
    static String mergeUncompressed(String version) throws IOException {
        return merge(version, false);
    }

    private static String merge(String version, boolean compactCandidate) throws IOException {
        String spec = version;
        if (BotFactory.parameters(spec).USE_NEURAL_TARGET_PROPOSAL)
            throw new IllegalArgumentException("USE_NEURAL_TARGET_PROPOSAL is local-only: candidate packaging and process checks are deferred");
        int queryAt = version.indexOf('?');
        if (queryAt >= 0) version = version.substring(0, queryAt);
        // Since repo-cleanup tier 3 (2026-10-27) neural-v6 (the screening opponent) is the only learned bot left.
        boolean neuralV6 = version.equals("neural-v6");
        if (!version.equals("current") && !version.equals("v05") && !version.equals("v06")
                && !version.equals("v07") && !version.equals("v08") && !version.equals("v09") && !version.equals("v10") && !version.equals("v11") && !version.equals("v12") && !version.equals("v13") && !neuralV6) {
            throw new IllegalArgumentException("submitVersion must be current, v05, v06, v07, v08, v09, v10, v11, v12, v13 or neural-v6");
        }
        List<Path> files = new ArrayList<>();
        boolean heuristic = version.equals("current") && BotFactory.parameters(spec).USE_HEURISTIC_POLICY;
        boolean rollout = version.equals("v06") || version.equals("v07") || version.equals("v08") || version.equals("v09")
                || version.equals("v10") || version.equals("v11") || version.equals("v12") || version.equals("v13") || version.equals("current") && BotFactory.parameters(spec).USE_ROLLOUT_POLICY;
        if (rollout && heuristic) throw new IllegalArgumentException("Select only one independent policy");
        // prune-v1: only the selected current rollout drops the local-only blocks and the classes it no longer uses;
        // the frozen v11/v12/v13 copies are that pruned algorithm, so their exports are pruned the same way.
        boolean pruneLocalOnly = (version.equals("current") || version.equals("v11") || version.equals("v12") || version.equals("v13")) && rollout;
        Set<String> prunedParams = pruneLocalOnly ? localOnlyParams() : Set.of();
        String policy = version.equals("v05") ? "bot/v05/EvalBotV05.java"
                : version.equals("v06") ? "bot/v06/RolloutBotV06.java"
                : version.equals("v07") ? "bot/v07/RolloutBotV07.java"
                : version.equals("v08") ? "bot/v08/RolloutBotV08.java"
                : version.equals("v09") ? "bot/v09/RolloutBotV09.java"
                : version.equals("v10") ? "bot/v10/RolloutBotV10.java"
                : version.equals("v11") ? "bot/v11/RolloutBotV11.java"
                : version.equals("v12") ? "bot/v12/RolloutBotV12.java"
                : version.equals("v13") ? "bot/v13/RolloutBotV13.java"
                : neuralV6 ? "bot/neural/NeuralV6Bot.java"
                : rollout ? "bot/current/RolloutBot.java"
                : heuristic ? "bot/current/HeuristicBot.java" : "bot/current/EvalBot.java";
        for (String dir : SUBMIT_DIRS) {
            try (Stream<Path> st = Files.list(SRC_ROOT.resolve(dir))) {
                st.filter(p -> p.toString().endsWith(".java")
                        && !p.endsWith("TacticalPathing.java") && !p.endsWith("InkProjection.java")
                        && !p.endsWith("HypotheticalIncome.java") && !p.endsWith("FeaturePaths.java")
                        && !(pruneLocalOnly && p.endsWith("NetworkChords.java")))
                        .sorted().forEach(files::add);
            }
        }
        // prune-v1: current's RolloutBot no longer uses TacticalPathing; the boss-sabotage proxy chooser still does.
        if (version.equals("v10") || (version.equals("current") || version.equals("v11") || version.equals("v12") || version.equals("v13")) && rollout && BotFactory.parameters(spec).USE_BOSS_SABOTAGE)
            files.add(SRC_ROOT.resolve("core/TacticalPathing.java"));
        if (neuralV6) files.add(SRC_ROOT.resolve("core/HypotheticalIncome.java"));
        if (neuralV6) files.add(SRC_ROOT.resolve("core/FeaturePaths.java"));
        files.add(SRC_ROOT.resolve(policy));
        if (neuralV6) {
            files.add(SRC_ROOT.resolve("bot/neural/NeuralV6Features.java"));
            files.add(SRC_ROOT.resolve("bot/neural/NeuralNet.java"));
            files.add(SRC_ROOT.resolve("bot/neural/NeuralV2Weights.java"));
        }
        if (version.equals("current") && rollout)
            files.add(SRC_ROOT.resolve("bot/current/ProtectedOpening.java"));
        if (version.equals("v08"))
            files.add(SRC_ROOT.resolve("bot/v08/ProtectedOpeningV08.java"));
        if (version.equals("v09"))
            files.add(SRC_ROOT.resolve("bot/v09/ProtectedOpeningV09.java"));
        if (version.equals("v10"))
            files.add(SRC_ROOT.resolve("bot/v10/ProtectedOpeningV10.java"));
        if (version.equals("v11"))
            files.add(SRC_ROOT.resolve("bot/v11/ProtectedOpeningV11.java"));
        if (version.equals("v12"))
            files.add(SRC_ROOT.resolve("bot/v12/ProtectedOpeningV12.java"));
        if (version.equals("v13"))
            files.add(SRC_ROOT.resolve("bot/v13/ProtectedOpeningV13.java"));
        for (String f : SUBMIT_FILES) files.add(SRC_ROOT.resolve(f));

        Set<String> imports = new TreeSet<>();
        StringBuilder body = new StringBuilder();
        boolean missedDeadline = BotFactory.parameters(spec).USE_ROLLOUT_MISSED_DEADLINE;
        boolean bossSabotage = BotFactory.parameters(spec).USE_BOSS_SABOTAGE;
        if (bossSabotage) files.add(SRC_ROOT.resolve(version.equals("v10") ? "bot/v10/BossSabotageV10.java"
                : version.equals("v11") ? "bot/v11/BossSabotageV11.java"
                : version.equals("v12") ? "bot/v12/BossSabotageV12.java"
                : version.equals("v13") ? "bot/v13/BossSabotageV13.java" : "bot/current/BossSabotage.java"));
        boolean bossLite = BotFactory.parameters(spec).USE_BOSS_LITE;
        boolean bossModel = BotFactory.parameters(spec).USE_BOSS_OPPONENT_MODEL || BotFactory.parameters(spec).USE_BOSS_OWN_MODEL;
        boolean bossRoutes = BotFactory.parameters(spec).USE_BOSS_LITE_ROUTES;
        boolean bossThreat = BotFactory.parameters(spec).USE_BOSS_THREAT_MODEL;
        boolean bossRoot = BotFactory.parameters(spec).USE_BOSS_ROOT_TARGET;
        boolean exposureIncome = BotFactory.parameters(spec).USE_EXPOSURE_INCOME;
        boolean collisionAvoidance = BotFactory.parameters(spec).USE_COLLISION_AVOIDANCE;
        boolean corridorRoutes = BotFactory.parameters(spec).USE_CORRIDOR_ROUTES;
        boolean delayValuation = BotFactory.parameters(spec).USE_QUICK_ALWAYS || BotFactory.parameters(spec).DELAY_POWER != 1.0;
        boolean oppHold = BotFactory.parameters(spec).BOSS_OPP_HOLD_MARGIN >= 0;
        boolean bossNoClose = BotFactory.parameters(spec).USE_BOSS_NO_CLOSE;
        boolean routeCommit = BotFactory.parameters(spec).USE_ROUTE_COMMIT;
        boolean bypassRoutes = BotFactory.parameters(spec).USE_BYPASS_ROUTES;
        boolean batchedRefresh = BotFactory.parameters(spec).USE_BATCHED_REFRESH;
        boolean incrementalInk = BotFactory.parameters(spec).USE_INCREMENTAL_INK_REFRESH;
        boolean fastCheapestPath = BotFactory.parameters(spec).USE_FAST_CHEAPEST_PATH;
        int turnBudgetMs = BotFactory.parameters(spec).TURN_BUDGET_MS;
        boolean turnBudgetOverride = turnBudgetMs != 36;
        // readable-export-v1: current exports keep line structure and indentation; frozen exports keep their bytes
        // (v11, frozen after readable-export-v1, is exported readably like current).
        boolean readable = version.equals("current") || version.equals("v11") || version.equals("v12") || version.equals("v13");
        boolean pendingBlank = false;
        for (Path file : files) {
            if (!Files.exists(file)) throw new IOException("missing " + file);
            boolean inBlockComment = false;
            boolean deadlineBlock = false;
            boolean bossBlock = false;
            boolean liteBlock = false;
            boolean holdBlock = false;
            boolean modelBlock = false;
            boolean routesBlock = false;
            boolean threatBlock = false;
            boolean rootBlock = false;
            boolean exposureBlock = false;
            boolean collisionBlock = false;
            boolean corridorBlock = false;
            boolean delayBlock = false;
            boolean oppHoldBlock = false;
            boolean noCloseBlock = false;
            boolean commitBlock = false;
            boolean bypassBlock = false;
            boolean batchBlock = false;
            boolean turnBudgetBlock = false;
            boolean inkBlock = false;
            boolean fastPathBlock = false, oldPathBlock = false;
            boolean localOnlyBlock = false;
            for (String line : Files.readAllLines(file)) {
                if (line.trim().equals("// LOCAL_ONLY_BEGIN")) {
                    if (localOnlyBlock) throw new IllegalArgumentException("Nested local-only block: " + file);
                    localOnlyBlock = true;
                    continue;
                }
                if (line.trim().equals("// LOCAL_ONLY_END")) {
                    if (!localOnlyBlock) throw new IllegalArgumentException("Unbalanced local-only block: " + file);
                    localOnlyBlock = false;
                    continue;
                }
                if (localOnlyBlock && pruneLocalOnly) continue;
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
                if (line.trim().equals("// BOSS_THREAT_BEGIN")) {
                    if (threatBlock) throw new IllegalArgumentException("Nested boss-threat block: " + file);
                    threatBlock = true;
                    continue;
                }
                if (line.trim().equals("// BOSS_THREAT_END")) {
                    if (!threatBlock) throw new IllegalArgumentException("Unbalanced boss-threat block: " + file);
                    threatBlock = false;
                    continue;
                }
                if (threatBlock && !bossThreat) continue;
                if (line.trim().equals("// BOSS_ROOT_BEGIN")) {
                    if (rootBlock) throw new IllegalArgumentException("Nested boss-root block: " + file);
                    rootBlock = true;
                    continue;
                }
                if (line.trim().equals("// BOSS_ROOT_END")) {
                    if (!rootBlock) throw new IllegalArgumentException("Unbalanced boss-root block: " + file);
                    rootBlock = false;
                    continue;
                }
                if (rootBlock && !bossRoot) continue;
                if (line.trim().equals("// EXPOSURE_INCOME_BEGIN")) {
                    if (exposureBlock) throw new IllegalArgumentException("Nested exposure-income block: " + file);
                    exposureBlock = true;
                    continue;
                }
                if (line.trim().equals("// EXPOSURE_INCOME_END")) {
                    if (!exposureBlock) throw new IllegalArgumentException("Unbalanced exposure-income block: " + file);
                    exposureBlock = false;
                    continue;
                }
                if (exposureBlock && !exposureIncome) continue;
                if (line.trim().equals("// COLLISION_BEGIN")) {
                    if (collisionBlock) throw new IllegalArgumentException("Nested collision block: " + file);
                    collisionBlock = true;
                    continue;
                }
                if (line.trim().equals("// COLLISION_END")) {
                    if (!collisionBlock) throw new IllegalArgumentException("Unbalanced collision block: " + file);
                    collisionBlock = false;
                    continue;
                }
                if (collisionBlock && !collisionAvoidance) continue;
                if (line.trim().equals("// CORRIDOR_BEGIN")) {
                    if (corridorBlock) throw new IllegalArgumentException("Nested corridor block: " + file);
                    corridorBlock = true;
                    continue;
                }
                if (line.trim().equals("// CORRIDOR_END")) {
                    if (!corridorBlock) throw new IllegalArgumentException("Unbalanced corridor block: " + file);
                    corridorBlock = false;
                    continue;
                }
                if (corridorBlock && !corridorRoutes) continue;
                if (line.trim().equals("// DELAY_BEGIN")) {
                    if (delayBlock) throw new IllegalArgumentException("Nested delay block: " + file);
                    delayBlock = true;
                    continue;
                }
                if (line.trim().equals("// DELAY_END")) {
                    if (!delayBlock) throw new IllegalArgumentException("Unbalanced delay block: " + file);
                    delayBlock = false;
                    continue;
                }
                if (delayBlock && !delayValuation) continue;
                if (line.trim().equals("// OPP_HOLD_BEGIN")) {
                    if (oppHoldBlock) throw new IllegalArgumentException("Nested opponent-hold block: " + file);
                    oppHoldBlock = true;
                    continue;
                }
                if (line.trim().equals("// OPP_HOLD_END")) {
                    if (!oppHoldBlock) throw new IllegalArgumentException("Unbalanced opponent-hold block: " + file);
                    oppHoldBlock = false;
                    continue;
                }
                if (oppHoldBlock && !oppHold) continue;
                if (line.trim().equals("// BOSS_NOCLOSE_BEGIN")) {
                    if (noCloseBlock) throw new IllegalArgumentException("Nested no-close block: " + file);
                    noCloseBlock = true;
                    continue;
                }
                if (line.trim().equals("// BOSS_NOCLOSE_END")) {
                    if (!noCloseBlock) throw new IllegalArgumentException("Unbalanced no-close block: " + file);
                    noCloseBlock = false;
                    continue;
                }
                if (noCloseBlock && !bossNoClose) continue;
                if (line.trim().equals("// COMMIT_BEGIN")) {
                    if (commitBlock) throw new IllegalArgumentException("Nested commit block: " + file);
                    commitBlock = true;
                    continue;
                }
                if (line.trim().equals("// COMMIT_END")) {
                    if (!commitBlock) throw new IllegalArgumentException("Unbalanced commit block: " + file);
                    commitBlock = false;
                    continue;
                }
                if (commitBlock && !routeCommit) continue;
                if (line.trim().equals("// BYPASS_BEGIN")) {
                    if (bypassBlock) throw new IllegalArgumentException("Nested bypass block: " + file);
                    bypassBlock = true;
                    continue;
                }
                if (line.trim().equals("// BYPASS_END")) {
                    if (!bypassBlock) throw new IllegalArgumentException("Unbalanced bypass block: " + file);
                    bypassBlock = false;
                    continue;
                }
                if (bypassBlock && !bypassRoutes) continue;
                if (file.endsWith("Params.java") && line.contains("public boolean USE_NEURAL_TARGET_PROPOSAL =")) continue;
                // Fields of the removed spatial-ppo line never ship (repo-cleanup tier 3).
                if (file.endsWith("Params.java") && (line.contains("public boolean USE_SPATIAL_V")
                        || line.contains("public int SPATIAL_V") || line.contains("public int SPATIAL_RESERVE_MS ="))) continue;
                if (!((version.equals("current") || version.equals("v10") || version.equals("v11") || version.equals("v12") || version.equals("v13")) && rollout) && file.endsWith("Params.java")
                        && (line.contains("public boolean USE_ROLLOUT_ALL_CELL_RISK =")
                        || line.contains("public boolean USE_ROLLOUT_ROOT_FILL ="))) continue;
                // Do not alter existing submissions that do not consume the neural deadline.
                if (!neuralV6 && file.endsWith("Params.java") && line.contains("public int NEURAL_DEADLINE_RESERVE_MS =")) continue;
                if (file.endsWith("Params.java") && (line.contains("public boolean USE_NEURAL_FALLBACK =")
                        || line.contains("public int NEURAL_FALLBACK_MAX_INSTABILITY ="))) continue;
                if (neuralV6) line = line.replaceAll("\\bNeuralV6Bot\\b", "EvalBot")
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
                if (version.equals("v11") && (file.endsWith("RolloutBotV11.java")
                        || file.endsWith("ProtectedOpeningV11.java") || file.endsWith("BossSabotageV11.java"))) {
                    line = line.replaceAll("\\bRolloutBotV11\\b", "EvalBot")
                            .replaceAll("\\bProtectedOpeningV11\\b", "ProtectedOpening")
                            .replaceAll("\\bBossSabotageV11\\b", "BossSabotage");
                }
                if (version.equals("v12") && (file.endsWith("RolloutBotV12.java")
                        || file.endsWith("ProtectedOpeningV12.java") || file.endsWith("BossSabotageV12.java"))) {
                    line = line.replaceAll("\\bRolloutBotV12\\b", "EvalBot")
                            .replaceAll("\\bProtectedOpeningV12\\b", "ProtectedOpening")
                            .replaceAll("\\bBossSabotageV12\\b", "BossSabotage");
                }
                if (version.equals("v13") && (file.endsWith("RolloutBotV13.java")
                        || file.endsWith("ProtectedOpeningV13.java") || file.endsWith("BossSabotageV13.java"))) {
                    line = line.replaceAll("\\bRolloutBotV13\\b", "EvalBot")
                            .replaceAll("\\bProtectedOpeningV13\\b", "ProtectedOpening")
                            .replaceAll("\\bBossSabotageV13\\b", "BossSabotage");
                }
                if (file.endsWith("MainBot.java")) {
                    line = line.replace("new Params()", paramsExpression(spec, prunedParams));
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
                if (trimmed.isEmpty()) {
                    pendingBlank = true;
                    continue;
                }
                if (trimmed.startsWith("//")) continue;
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
                if (readable) {
                    // One blank line survives between statement groups and members. Indentation is kept as one
                    // tab per four-space level plus one for the nesting in Player: the same layout with spaces
                    // is 113,144 characters, over the 100,000 limit.
                    if (pendingBlank && body.length() > 0 && !endsWithBlankLine(body)) body.append('\n');
                    int indent = 0;
                    while (indent < line.length() && line.charAt(indent) == ' ') indent++;
                    body.append("\t".repeat(1 + (indent + 2) / 4)).append(line.strip()).append('\n');
                } else {
                    // Historical artifacts retain their original bytes: stripped lines, no blank lines.
                    body.append(line.strip()).append('\n');
                }
                pendingBlank = false;
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
            if (localOnlyBlock) throw new IllegalArgumentException("Unclosed local-only block: " + file);
        }

        StringBuilder sb = new StringBuilder();
        for (String imp : imports) sb.append("import ").append(imp).append(";\n");
        // Keep the strategy label in the normalized source identity too: identical
        // policy copies with different names must not collide in the registry.
        // A current export names the selected version it carries ("// BTK strategy: current, v11"); frozen exports keep their bytes.
        sb.append("\n// BTK strategy: ").append(version);
        if (version.equals("current")) sb.append(", ").append(BotFactory.displayName(spec));
        sb.append("\nclass Player {\n\n");
        sb.append(body);
        sb.append("    public static void main(String[] args) {\n");
        sb.append("        CGRunner.main(args);\n");
        sb.append("    }\n}\n");

        String normalized = sb.toString();
        // The frozen v10 export is compacted (its readable form exceeds the limit). A current export stays readable
        // while it fits and is compacted only when its readable form would exceed the limit (same tokens).
        boolean compactFrozenV10 = version.equals("v10")
                && (missedDeadline || batchedRefresh || incrementalInk || fastCheapestPath || bossSabotage || bossLite || bossModel || bossRoutes);
        if (compactCandidate && (compactFrozenV10 || readable && normalized.length() > MAX_SOURCE_CHARS)) {
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
                "public static final String BUILD_MARKER = \"" + message(spec, sha256(normalized)) + "\";");
    }

    private static boolean endsWithBlankLine(StringBuilder body) {
        int n = body.length();
        return n >= 2 && body.charAt(n - 1) == '\n' && body.charAt(n - 2) == '\n';
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

    /** The machine-readable provenance marker; its grammar is shared with tools/replay_capture.py. */
    private static String marker(String spec, String buildId) {
        return "BTK_BUILD_V1 strategy=" + spec.split("\\?", 2)[0]
                + " build=" + buildId + " config=" + configSha256(spec);
    }

    /**
     * The first-turn MESSAGE text. A current export leads with the strategy's display name
     * ({@code "v11 | BTK_BUILD_V1 strategy=current build=... config=..."}); frozen
     * exports keep the bare marker so their historical bytes do not change.
     */
    static String message(String spec, String buildId) {
        String marker = marker(spec, buildId);
        return spec.split("\\?", 2)[0].equals("current") ? BotFactory.displayName(spec) + " | " + marker : marker;
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

    /** The embedded configuration omits fields the artifact's Params no longer declares (prune-v1). */
    private static String paramsExpression(String spec, Set<String> excluded) {
        StringBuilder query = new StringBuilder();
        for (var entry : BotFactory.configuration(spec).entrySet()) {
            if (excluded.contains(entry.getKey())) continue;
            if (query.length() > 0) query.append(',');
            query.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return "new Params(\"" + query + "\")";
    }

    /** True for the artifacts that drop the local-only blocks: the selected current rollout policy (prune-v1). */
    static boolean prunesLocalOnly(String spec) {
        String version = spec.split("\\?", 2)[0];
        return version.equals("current") && BotFactory.parameters(spec).USE_ROLLOUT_POLICY
                && !BotFactory.parameters(spec).USE_HEURISTIC_POLICY;
    }

    /** Params fields declared inside {@code // LOCAL_ONLY_BEGIN/END} blocks: never read by the shipped current rollout. */
    public static Set<String> localOnlyParams() throws IOException {
        Set<String> names = new TreeSet<>();
        boolean inside = false;
        for (String line : Files.readAllLines(SRC_ROOT.resolve("bot/Params.java"))) {
            String trimmed = line.trim();
            if (trimmed.equals("// LOCAL_ONLY_BEGIN")) {
                if (inside) throw new IllegalArgumentException("Nested local-only block in Params.java");
                inside = true;
                continue;
            }
            if (trimmed.equals("// LOCAL_ONLY_END")) {
                if (!inside) throw new IllegalArgumentException("Unbalanced local-only block in Params.java");
                inside = false;
                continue;
            }
            Matcher m = PARAM_FIELD.matcher(trimmed);
            if (inside && m.find()) names.add(m.group(1));
        }
        if (inside) throw new IllegalArgumentException("Unclosed local-only block in Params.java");
        return names;
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
