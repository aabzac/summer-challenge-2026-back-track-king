package btk.submit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import static org.junit.jupiter.api.Assertions.*;

class DeferredCommitCompactionTest {
    private static final String CANDIDATE = "current?NOISE=0,USE_ROLLOUT_MISSED_DEADLINE=true";
    @TempDir Path temp;

    @Test void compactionIsCandidateOnlyAndHistoricalReferenceRemainsExact() throws Exception {
        // Current may receive later runtime optimizations; the completed packaging reference is immutable.
        try (var archive = new java.util.zip.ZipFile("docs/evidence/v09-deferred-commit-runtime-v1-artifacts.zip")) {
            var entry = archive.getEntry("build/analysis/v09-deferred-commit-runtime-v1/reference-uncompressed/Player.java");
            assertNotNull(entry);
            String reference = new String(archive.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(108524, reference.length());
            assertEquals("da4487e574fee5bc691724d81157183f7319bc74a39a2a699d5cea5a2b9e0c56", FileMerger.sha256(reference));
        }
        for (String spec : new String[]{"current", "current?NOISE=0", "current?USE_ROLLOUT_MISSED_DEADLINE=false",
                "v05", "v06", "v07", "v08", "v09", "neural-v1", "neural-v7", "spatial-ppo-v1", "spatial-ppo-v10"})
            assertEquals(FileMerger.mergeUncompressed(spec), FileMerger.merge(spec), spec);
    }

    @Test void compactCandidateFitsAndHasIdenticalTokensAndExecutableClassBytes() throws Exception {
        String reference = FileMerger.mergeUncompressed(CANDIDATE), compact = FileMerger.merge(CANDIDATE);
        assertTrue(compact.length() < FileMerger.MAX_SOURCE_CHARS);
        assertTrue(reference.length() - compact.length() >= 8525);
        assertDoesNotThrow(() -> FileMerger.checkSourceSize(compact));
        assertDoesNotThrow(() -> FileMerger.manifest(CANDIDATE, compact));
        String normalizedReference = FileMerger.normalizeMarker(reference), normalizedCompact = FileMerger.normalizeMarker(compact);
        assertEquals(JavaSourceCompactor.tokens(normalizedReference), JavaSourceCompactor.tokens(normalizedCompact));
        Map<String, String> before = compile(normalizedReference, "reference"), after = compile(normalizedCompact, "compact");
        assertEquals(24, before.size());
        assertEquals(before, after, "Every class must be byte-identical with marker and debug positions normalized");
    }

    @Test void compilerConfirmsAdversarialLiteralAndOperatorSample() throws Exception {
        String source = "public class Player { static int example(int a,int b) { int x=a + + b; "
                + "x += a - - b; x += a / /* keep separation */ b; return x; } "
                + "static double d=0x1.fp2 + .5e+2 + 1.e-3; static long n=0b10_01L+077+1_000L; "
                + "static String s=\"// /* literal */ \\\" quote \\\\ slash \\s\"; static char c='\\123'; }";
        assertEquals(compile(source, "sample-reference"), compile(JavaSourceCompactor.compact(source), "sample-compact"));
    }

    private Map<String, String> compile(String source, String name) throws Exception {
        Path directory = temp.resolve(name), classes = directory.resolve("classes");
        Files.createDirectories(classes);
        Path file = directory.resolve("Player.java"); Files.writeString(file, source, StandardCharsets.UTF_8);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-encoding", "UTF-8", "--release", "17", "-g:none", "-d", classes.toString(), file.toString()));
        Map<String, String> result = new TreeMap<>();
        try (var files = Files.walk(classes)) {
            for (Path generated : files.filter(Files::isRegularFile).toList())
                result.put(classes.relativize(generated).toString(), HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(generated))));
        }
        return result;
    }
}
