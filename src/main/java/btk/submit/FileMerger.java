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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Merges the submission sources into a single {@code build/submit/Player.java}: every class
 * becomes a static nested class of {@code Player}, packages are dropped, imports are unioned.
 * Then compiles the result to catch merge errors before pasting into CodinGame.
 *
 * <p>The default uses {@link #SUBMIT_DIRS} plus {@link #SUBMIT_FILES}. Explicit v05 restoration
 * imports its frozen source as the generated EvalBot and embeds its resolved parameters;
 * no frozen-package runtime dependency, referee, runner or tool ships.
 */
public class FileMerger {

    private static final Path SRC_ROOT = Path.of("src/main/java/btk");
    private static final Path OUTPUT = Path.of("build/submit/Player.java");
    /** User-confirmed CG arena source limit; conservatively count Java UTF-16 code units. */
    public static final int MAX_SOURCE_CHARS = 100_000;

    private static final List<String> SUBMIT_DIRS = List.of("core", "bot/current");
    private static final List<String> SUBMIT_FILES = List.of(
            "bot/Bot.java", "bot/Params.java", "bot/Timer.java", "bot/MainBot.java",
            "submit/CGRunner.java");

    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+[\\w.]+\\s*;\\s*$");
    private static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+(.*);\\s*$");
    private static final Pattern PUBLIC_TYPE = Pattern.compile(
            "^(\\s*)public\\s+((?:(?:final|abstract|sealed|strictfp)\\s+)*)(class|interface|enum|record)\\s+");

    public static void main(String[] args) throws IOException {
        String version = args.length == 0 ? "current" : args[0];
        String source = merge(version);
        checkSourceSize(source);
        Files.createDirectories(OUTPUT.getParent());
        Files.writeString(OUTPUT, source, StandardCharsets.UTF_8);
        compileCheck();
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("strategy", version);
        manifest.put("params", BotFactory.configuration(version));
        manifest.put("charactersUtf16", source.length());
        manifest.put("bytesUtf8", source.getBytes(StandardCharsets.UTF_8).length);
        manifest.put("sourceLimit", MAX_SOURCE_CHARS);
        manifest.put("sha256", sha256(source));
        Files.writeString(OUTPUT.resolveSibling("manifest.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(manifest));
        System.out.printf("Submission %s: %d/%d characters, %d UTF-8 bytes, SHA-256 %s%n",
                version, source.length(), MAX_SOURCE_CHARS,
                source.getBytes(StandardCharsets.UTF_8).length, manifest.get("sha256"));
    }

    static String merge(String version) throws IOException {
        if (!version.equals("current") && !version.equals("v05")) {
            throw new IllegalArgumentException("submitVersion must be current or v05");
        }
        List<Path> files = new ArrayList<>();
        for (String dir : SUBMIT_DIRS) {
            if (version.equals("v05") && dir.equals("bot/current")) {
                files.add(SRC_ROOT.resolve("bot/v05/EvalBotV05.java"));
                continue;
            }
            try (Stream<Path> st = Files.list(SRC_ROOT.resolve(dir))) {
                st.filter(p -> p.toString().endsWith(".java")).sorted().forEach(files::add);
            }
        }
        for (String f : SUBMIT_FILES) files.add(SRC_ROOT.resolve(f));

        Set<String> imports = new TreeSet<>();
        StringBuilder body = new StringBuilder();
        for (Path file : files) {
            if (!Files.exists(file)) throw new IOException("missing " + file);
            boolean inBlockComment = false;
            for (String line : Files.readAllLines(file)) {
                // Import frozen source as the generated current EvalBot, without editing v05.
                if (version.equals("v05") && file.endsWith("EvalBotV05.java")) {
                    line = line.replaceAll("\\bEvalBotV05\\b", "EvalBot");
                }
                if (version.equals("v05") && file.endsWith("MainBot.java")) {
                    line = line.replace("new Params()", frozenParamsExpression());
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
                // Whitespace is not semantic here; omitting the merge prefix saves about 10 KB.
                body.append(line.stripTrailing()).append('\n');
            }
        }

        StringBuilder sb = new StringBuilder();
        for (String imp : imports) sb.append("import ").append(imp).append(";\n");
        sb.append("\nclass Player {\n\n");
        sb.append(body);
        sb.append("    public static void main(String[] args) {\n");
        sb.append("        CGRunner.main(args);\n");
        sb.append("    }\n}\n");

        return sb.toString();
    }

    private static String frozenParamsExpression() {
        StringBuilder query = new StringBuilder();
        for (var entry : BotFactory.configuration("v05").entrySet()) {
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

    private static void compileCheck() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IOException("A JDK compiler is required to verify the submission");
        }
        Path outDir = OUTPUT.getParent().resolve("classes");
        Files.createDirectories(outDir);
        int rc = compiler.run(null, null, null, "-encoding", "UTF-8", "-d", outDir.toString(), "-Xlint:none",
                OUTPUT.toString());
        if (rc != 0) {
            throw new IOException("Player.java does not compile (exit " + rc + ")");
        }
        System.out.println("Compile check OK -> " + outDir);
    }
}
