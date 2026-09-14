package btk.submit;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * <p>Only the directories listed in {@link #SUBMIT_DIRS} plus {@link #SUBMIT_FILES} ship: the
 * referee, runners and tools must never leak into the submission.
 */
public class FileMerger {

    private static final Path SRC_ROOT = Path.of("src/main/java/btk");
    private static final Path OUTPUT = Path.of("build/submit/Player.java");

    private static final List<String> SUBMIT_DIRS = List.of("core", "bot/current");
    private static final List<String> SUBMIT_FILES = List.of(
            "bot/Bot.java", "bot/Params.java", "bot/Timer.java", "bot/MainBot.java",
            "submit/CGRunner.java");

    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+[\\w.]+\\s*;\\s*$");
    private static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+(.*);\\s*$");
    private static final Pattern PUBLIC_TYPE = Pattern.compile(
            "^(\\s*)public\\s+((?:(?:final|abstract|sealed|strictfp)\\s+)*)(class|interface|enum|record)\\s+");

    public static void main(String[] args) throws IOException {
        List<Path> files = new ArrayList<>();
        for (String dir : SUBMIT_DIRS) {
            try (Stream<Path> st = Files.list(SRC_ROOT.resolve(dir))) {
                st.filter(p -> p.toString().endsWith(".java")).sorted().forEach(files::add);
            }
        }
        for (String f : SUBMIT_FILES) files.add(SRC_ROOT.resolve(f));

        Set<String> imports = new TreeSet<>();
        StringBuilder body = new StringBuilder();
        int classes = 0;
        for (Path file : files) {
            if (!Files.exists(file)) throw new IOException("missing " + file);
            boolean inBlockComment = false;
            for (String line : Files.readAllLines(file)) {
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
            classes++;
        }

        StringBuilder sb = new StringBuilder();
        for (String imp : imports) sb.append("import ").append(imp).append(";\n");
        sb.append("\nclass Player {\n\n");
        sb.append(body);
        sb.append("    public static void main(String[] args) {\n");
        sb.append("        CGRunner.main(args);\n");
        sb.append("    }\n}\n");

        Files.createDirectories(OUTPUT.getParent());
        Files.writeString(OUTPUT, sb.toString());
        System.out.printf("Merged %d files into %s (%d bytes, %d lines)%n",
                classes, OUTPUT, sb.length(), sb.toString().split("\n").length);
        compileCheck();
    }

    private static void compileCheck() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            System.out.println("No system compiler available; skipping compile check");
            return;
        }
        Path outDir = OUTPUT.getParent().resolve("classes");
        Files.createDirectories(outDir);
        int rc = compiler.run(null, null, null, "-d", outDir.toString(), "-Xlint:none",
                OUTPUT.toString());
        if (rc != 0) {
            throw new IOException("Player.java does not compile (exit " + rc + ")");
        }
        System.out.println("Compile check OK -> " + outDir);
    }
}
