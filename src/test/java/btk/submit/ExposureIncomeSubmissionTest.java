package btk.submit;

import btk.bot.BotFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** exposure-income-v1 (2026-10-25): the flag ships only inside its {@code // EXPOSURE_INCOME} block; the default artifact is untouched (V11SubmissionTest). */
class ExposureIncomeSubmissionTest {
    static final String E = "current?USE_EXPOSURE_INCOME=true";

    @Test void defaultArtifactOmitsTheBlockAndTheFlagOnArtifactCompiles() throws Exception {
        String off = FileMerger.merge("current");
        for (String token : new String[]{"USE_EXPOSURE_INCOME", "EXPOSURE_INCOME_WEIGHT", "exposureIncome", "EXPOSURE_INCOME"})
            assertFalse(off.contains(token), token + " must not ship while the flag is off");
        String on = FileMerger.merge(E);
        FileMerger.checkSourceSize(on);
        assertTrue(on.contains("USE_EXPOSURE_INCOME=true") && on.contains("EXPOSURE_INCOME_WEIGHT=1.0"));
        assertTrue(on.contains("private void exposureIncome(") && on.contains("exposureThreatDiff"));
        assertTrue(on.length() > off.length());
        assertNotEquals(FileMerger.configSha256("current"), FileMerger.configSha256(E));
        assertEquals(FileMerger.configSha256(E), FileMerger.configSha256(E + ",EXPOSURE_INCOME_WEIGHT=1.0"));
        assertNotEquals(FileMerger.configSha256(E), FileMerger.configSha256(E + ",EXPOSURE_INCOME_WEIGHT=0.5"));
        Path dir = Files.createTempDirectory("exposure-artifact");
        Path source = dir.resolve("Player.java");
        Files.writeString(source, on);
        assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-encoding", "UTF-8", "--release", "17", "-d", dir.toString(), source.toString()));
        assertEquals("v11 (modified)", BotFactory.displayName(E));
    }
}
