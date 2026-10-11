package btk.tools;

import btk.bot.neural.NeuralV2Weights;
import btk.submit.FileMerger;
import com.google.gson.GsonBuilder;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Counts a direct target-module port without creating or registering a candidate submission. */
public final class NeuralTargetSizeProbe {
    static final Path JAVA = Path.of("src/main/java/btk");
    static final Path MERGER = JAVA.resolve("submit/FileMerger.java");
    static final Path WEIGHTS = JAVA.resolve("bot/neural/NeuralV2Weights.java");
    static final List<Path> MODULE = List.of(
            JAVA.resolve("tools/target/TargetProposalFeatures.java"),
            JAVA.resolve("tools/target/TargetProposer.java"),
            JAVA.resolve("bot/neural/NeuralNet.java"),
            JAVA.resolve("core/FeaturePaths.java"));
    private static final Pattern PACKAGE = mergerPattern("PACKAGE");
    private static final Pattern IMPORT = mergerPattern("IMPORT");
    private static final Pattern PUBLIC_TYPE = mergerPattern("PUBLIC_TYPE");
    private static final Pattern TYPE = Pattern.compile("\\b(?:class|interface|enum|record)\\s+(\\w+)");
    private static final Pattern SIZES = Pattern.compile(
            "public\\s+static\\s+final\\s+int\\[\\]\\s+SABOTAGE_SIZES\\s*=\\s*\\{([^}]+)}\\s*;");
    private static final Pattern PAYLOAD = Pattern.compile(
            "public\\s+static\\s+final\\s+String\\s+SABOTAGE\\s*=\\s*\"([A-Za-z0-9+/=]+)\"\\s*;");

    record Body(String code, Set<String> externalImports, Set<String> internalImports) {}
    record Head(String source, int[] sizes, String payload, byte[] decoded, int parameters) {}

    private static Pattern mergerPattern(String name) {
        try {
            Field field = FileMerger.class.getDeclaredField(name);
            field.setAccessible(true);
            return (Pattern) field.get(null);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("FileMerger contract changed", e); }
    }
    static String baseline() throws Exception {
        Method merge = FileMerger.class.getDeclaredMethod("merge", String.class);
        merge.setAccessible(true);
        return (String) merge.invoke(null, "current");
    }
    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static String hash(String value) throws Exception { return hash(value.getBytes(StandardCharsets.UTF_8)); }
    private static String id(Path path) { return path.toString().replace('\\', '/'); }
    private static Map<String, Object> file(Path path) throws Exception {
        byte[] bytes = Files.readAllBytes(path);
        return Map.of("path", id(path), "sha256", hash(bytes), "bytes", bytes.length);
    }

    /** Same generic line transformations as merge(current) with rollout enabled.
     * This intentionally retains inline comments and the merger's newline rule, even in strings.
     * Policy aliases/Params filtering belong only to the exact baseline returned by FileMerger. */
    static Body body(String source) {
        Set<String> external = new TreeSet<>(), internal = new TreeSet<>();
        StringBuilder output = new StringBuilder(); boolean inBlock = false;
        for (String original : source.lines().toList()) {
            String line = original;
            require(!line.contains("\"\"\""), "FileMerger does not support text blocks");
            String trimmed = line.trim();
            if (inBlock) {
                if (trimmed.contains("*/")) inBlock = false;
                continue;
            }
            if (trimmed.startsWith("/*")) {
                if (!trimmed.contains("*/")) inBlock = true;
                continue;
            }
            if (trimmed.isEmpty() || trimmed.startsWith("//") || PACKAGE.matcher(line).matches()) continue;
            Matcher imported = IMPORT.matcher(line);
            if (imported.matches()) {
                String name = imported.group(1).trim();
                if (name.startsWith("btk.")) internal.add(name); else external.add(name);
                continue;
            }
            Matcher type = PUBLIC_TYPE.matcher(line);
            if (type.find()) line = type.group(1) + "static " + type.group(2) + type.group(3) + " " + line.substring(type.end());
            String code = line.strip(); output.append(code);
            if (code.contains("//") || !(code.endsWith(";") || code.endsWith("{") || code.endsWith("}"))) output.append('\n');
        }
        return new Body(output.toString(), external, internal);
    }

    /** Projection of the two unchanged generated declarations; no BUILD head or feature class. */
    static Head sabotageHead(String source) {
        Matcher sizesMatch = SIZES.matcher(source), payloadMatch = PAYLOAD.matcher(source);
        require(sizesMatch.find() && payloadMatch.find(), "Frozen sabotage declarations missing");
        String sizeDeclaration = sizesMatch.group(), payloadDeclaration = payloadMatch.group();
        String dimensions = sizesMatch.group(1).replace("NeuralFeatures.SABOTAGE_F", "29");
        String payload = payloadMatch.group(1);
        require(!sizesMatch.find() && !payloadMatch.find(), "Ambiguous sabotage declarations");
        require(dimensions.matches("[\\d\\s,]+"), "Unexpected sabotage dimension expression");
        int[] sizes = Arrays.stream(dimensions.split(",")).map(String::trim).mapToInt(Integer::parseInt).toArray();
        require(Arrays.equals(sizes, new int[]{29, 32, 16, 1}) && Arrays.equals(sizes, NeuralV2Weights.SABOTAGE_SIZES)
                && payload.equals(NeuralV2Weights.SABOTAGE), "Frozen source/compiled sabotage head mismatch");
        int parameters = 0;
        for (int i = 1; i < sizes.length; i++) parameters += (sizes[i - 1] + 1) * sizes[i];
        byte[] decoded = Base64.getDecoder().decode(payload);
        require(decoded.length == sizes[0] * 8 + (sizes.length - 1) * 4 + parameters * 2,
                "Unexpected quantized sabotage payload length");
        String projected = "public final class NeuralV2Weights {\n"
                + sizeDeclaration.replace("NeuralFeatures.SABOTAGE_F", "29") + "\n"
                + payloadDeclaration + "\n}\n";
        return new Head(projected, sizes, payload, decoded, parameters);
    }

    static Set<String> declaredTypes(String code) {
        Set<String> names = new TreeSet<>(); Matcher matcher = TYPE.matcher(code);
        while (matcher.find()) names.add(matcher.group(1));
        return names;
    }
    static void requireDependencies(List<Body> bodies, Set<String> available) {
        for (Body source : bodies) {
            require(!source.code.contains("btk."), "Unstripped qualified project reference in proposed port");
            for (String dependency : source.internalImports) {
                require(!dependency.endsWith(".*") && available.contains(dependency.substring(dependency.lastIndexOf('.') + 1)),
                        "Missing direct-port dependency: " + dependency);
            }
        }
    }
    static Map<String, Object> baselineArtifact(String source, Path artifact) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>(); result.put("path", id(artifact));
        result.put("present", Files.exists(artifact));
        if (Files.exists(artifact)) {
            require(Files.readString(artifact, StandardCharsets.UTF_8).equals(source),
                    "Existing standard baseline differs from FileMerger.merge(current): " + artifact);
            result.put("byteExact", true); result.put("sha256", hash(Files.readAllBytes(artifact)));
        } else result.put("byteExact", null);
        return result;
    }

    public static Map<String, Object> inspect(Path existingBaseline) throws Exception {
        String baseline = baseline();
        require(baseline.contains("private double rollout("), "Expected current rollout baseline");
        // Detect drift in the copied generic merger loop against actual unchanged shipped bodies.
        for (String relative : List.of("bot/Timer.java", "core/GameState.java", "core/Pathing.java"))
            require(baseline.contains(body(Files.readString(JAVA.resolve(relative))).code),
                    "Generic transform no longer matches exact FileMerger baseline: " + relative);
        Map<String, Object> artifact = baselineArtifact(baseline, existingBaseline);
        Head head = sabotageHead(Files.readString(WEIGHTS));
        List<Body> bodies = new ArrayList<>(); List<Map<String, Object>> components = new ArrayList<>();
        Set<String> available = declaredTypes(baseline), newImports = new TreeSet<>();
        Set<String> baselineImports = new TreeSet<>();
        for (String line : baseline.lines().toList()) {
            Matcher matcher = IMPORT.matcher(line);
            if (matcher.matches()) baselineImports.add(matcher.group(1).trim());
        }
        int addonChars = 0, addonBytes = 0;
        List<Path> paths = new ArrayList<>(MODULE); paths.add(WEIGHTS);
        for (Path path : paths) {
            String source = path.equals(WEIGHTS) ? head.source : Files.readString(path);
            Body transformed = body(source); bodies.add(transformed);
            Set<String> names = declaredTypes(transformed.code);
            String primary = path.getFileName().toString().replace(".java", "");
            require(names.contains(primary), "Missing expected module type: " + primary);
            boolean alreadyPresent = available.contains(primary);
            if (alreadyPresent) require(baseline.contains(transformed.code), "Existing type differs from proposed module: " + primary);
            available.addAll(names);
            require(!transformed.code.contains("NeuralV2Weights.BUILD") && !transformed.code.contains("NeuralV2Weights.TRAINING"),
                    "Target module unexpectedly consumes non-sabotage weight fields");
            if (!alreadyPresent) {
                addonChars += transformed.code.length(); addonBytes += transformed.code.getBytes(StandardCharsets.UTF_8).length;
                newImports.addAll(transformed.externalImports);
            }
            Map<String, Object> item = new LinkedHashMap<>(file(path));
            item.put("type", primary); item.put("sourceCharactersUtf16", Files.readString(path).length());
            item.put("selection", path.equals(WEIGHTS) ? "SABOTAGE_SIZES and SABOTAGE only; input dimension mechanically resolved to 29" : "entire unchanged module body");
            item.put("alreadyInBaseline", alreadyPresent); item.put("countedBodyCharactersUtf16", alreadyPresent ? 0 : transformed.code.length());
            item.put("transformedBodySha256", hash(transformed.code));
            item.put("internalImports", transformed.internalImports); item.put("externalImports", transformed.externalImports);
            components.add(item);
        }
        requireDependencies(bodies, available);
        newImports.removeAll(baselineImports);
        int importChars = 0;
        for (String name : newImports) importChars += ("import " + name + ";\n").length();
        int knownPort = baseline.length() + addonChars + importChars;
        Method manifest = FileMerger.class.getDeclaredMethod("manifest", String.class, String.class);
        manifest.setAccessible(true);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schema", "neural-target-size-probe-v1"); report.put("createdAt", Instant.now().toString());
        report.put("status", knownPort > FileMerger.MAX_SOURCE_CHARS ? "STOP_DIRECT_PORT_SIZE" : "MODULE_SIZE_ONLY_NOT_READY");
        report.put("candidateArtifactCreated", false); report.put("sourceLimitCharactersUtf16", FileMerger.MAX_SOURCE_CHARS);
        report.put("baseline", manifest.invoke(null, "current", baseline)); report.put("existingBaselineArtifact", artifact);
        report.put("baselineHeadroomCharactersUtf16", FileMerger.MAX_SOURCE_CHARS - baseline.length());
        report.put("components", components); report.put("addonBodyCharactersUtf16", addonChars); report.put("addonBodyBytesUtf8", addonBytes);
        report.put("baselinePlusBodiesCharactersUtf16", baseline.length() + addonChars);
        report.put("incrementalExternalImports", newImports); report.put("incrementalImportCharactersUtf16", importChars);
        report.put("knownDirectModulePortCharactersUtf16", knownPort);
        report.put("remainingBeforeIntegrationCharactersUtf16", FileMerger.MAX_SOURCE_CHARS - knownPort);
        report.put("sabotageHead", Map.of("sizes", head.sizes, "parameters", head.parameters,
                "normalizationFloats", head.sizes[0] * 2, "layerScaleFloats", head.sizes.length - 1,
                "encodedCharacters", head.payload.length(), "decodedBytes", head.decoded.length,
                "decodedSha256", hash(head.decoded), "buildHeadIncluded", false));
        report.put("mergerSource", file(MERGER)); report.put("probeSource", file(JAVA.resolve("tools/NeuralTargetSizeProbe.java")));
        report.put("method", "Exact current FileMerger baseline plus nested module bodies using its line/comment/import/type/newline transforms. No assembled Player, compile, registry write or neural scoring.");
        report.put("limits", List.of("Direct port with retained class names; this is a size diagnostic, not a candidate artifact.",
                "E/D integration, new configuration/identity text and their dependencies are not counted; known port size is a lower bound for this unchanged-body design.",
                "Size overflow stops this direct port, not every possible future compression or redesign.",
                "A size fit alone does not establish feature parity, timing, compilation or strength."));
        require(baseline.equals(baseline()), "Baseline changed during size inspection");
        baselineArtifact(baseline, existingBaseline);
        return report;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) throw new IllegalArgumentException("fresh-size-report.json [existing-current-Player.java]");
        Path out = Path.of(args[0]); require(!Files.exists(out), "Choose a fresh report path");
        Path artifact = args.length == 2 ? Path.of(args[1]) : Path.of("build/submit/Player.java");
        Map<String, Object> report = inspect(artifact);
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(report),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        System.out.printf("%s: direct module %s/%d characters, integration not included%n", report.get("status"),
                report.get("knownDirectModulePortCharactersUtf16"), FileMerger.MAX_SOURCE_CHARS);
    }
}
