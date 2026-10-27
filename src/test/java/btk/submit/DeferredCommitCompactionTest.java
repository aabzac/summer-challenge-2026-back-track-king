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
    private static final String CANDIDATE = "current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",NOISE=0,USE_ROLLOUT_MISSED_DEADLINE=true";
    @TempDir Path temp;

    @Test void compactionIsCandidateOnlyAndHistoricalReferenceRemainsExact() throws Exception {
        // Current may receive later runtime optimizations; the completed packaging reference is immutable.
        // Extracted from docs/evidence/v09-deferred-commit-runtime-v1-artifacts.zip (no longer tracked; sha in its manifest).
        String reference = Files.readString(Path.of("src/test/resources/historical/v09-deferred-commit-runtime-v1/Player.java"), StandardCharsets.UTF_8);
        assertEquals(108524, reference.length());
        assertEquals("da4487e574fee5bc691724d81157183f7319bc74a39a2a699d5cea5a2b9e0c56", FileMerger.sha256(reference));
        for (String spec : new String[]{btk.bot.LiveTargetExperimentParams.V09_ON_CURRENT, "current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",NOISE=0", "current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",USE_ROLLOUT_MISSED_DEADLINE=false",
                "v05", "v06", "v07", "v08", "v09", "neural-v6"})
            assertEquals(FileMerger.mergeUncompressed(spec), FileMerger.merge(spec), spec);
    }

    @Test void readableCandidateFitsAndCompactsToIdenticalTokensAndExecutableClassBytes() throws Exception {
        // readable-export-v1 (2026-10-25): current exports are no longer compacted. The compactor still packages the
        // frozen v10 export, so it must keep every token and class byte of a readable candidate.
        String readable = FileMerger.merge(CANDIDATE);
        assertEquals(FileMerger.mergeUncompressed(CANDIDATE), readable, "current exports are readable, not compacted");
        assertTrue(readable.length() < FileMerger.MAX_SOURCE_CHARS);
        assertDoesNotThrow(() -> FileMerger.checkSourceSize(readable));
        assertDoesNotThrow(() -> FileMerger.manifest(CANDIDATE, readable));
        String normalizedReadable = FileMerger.normalizeMarker(readable), compact = JavaSourceCompactor.compact(normalizedReadable);
        assertTrue(normalizedReadable.length() - compact.length() >= 15000, "layout the compactor would remove");
        assertEquals(JavaSourceCompactor.tokens(normalizedReadable), JavaSourceCompactor.tokens(compact));
        Map<String, String> before = compile(normalizedReadable, "reference"), after = compile(compact, "compact");
        assertEquals(22, before.size(), "classes in the artifact; NetworkChords and TacticalPathing left with prune-v1");
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
