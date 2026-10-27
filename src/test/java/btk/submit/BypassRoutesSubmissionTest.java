package btk.submit;

import btk.bot.BotFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** bypass-routes-v1 (2026-10-27): the flag ships only inside its {@code // BYPASS} blocks; the default artifact is untouched (V12SubmissionTest). */
class BypassRoutesSubmissionTest {
    static final String B = "current?USE_BYPASS_ROUTES=true";

    @Test void defaultArtifactOmitsTheBlocksAndTheFlagOnArtifactCompiles() throws Exception {
        String off = FileMerger.merge("current");
        for (String token : new String[]{"USE_BYPASS_ROUTES", "BYPASS_MIN_INSTABILITY", "BYPASS_WEIGHT", "proposeBypass", "BYPASS_BEGIN"})
            assertFalse(off.contains(token), token + " must not ship while the flag is off");
        String on = FileMerger.merge(B);
        FileMerger.checkSourceSize(on);
        assertTrue(on.contains("USE_BYPASS_ROUTES=true") && on.contains("BYPASS_MIN_INSTABILITY=2") && on.contains("BYPASS_WEIGHT=0.5"));
        assertTrue(on.contains("private void proposeBypass(") && on.contains("bypassProposals"));
        assertTrue(on.length() > off.length());
        assertNotEquals(FileMerger.configSha256("current"), FileMerger.configSha256(B));
        Path dir = Files.createTempDirectory("bypass-artifact");
        Path source = dir.resolve("Player.java");
        Files.writeString(source, on);
        assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-encoding", "UTF-8", "--release", "17", "-d", dir.toString(), source.toString()));
        assertEquals("v12 (modified)", BotFactory.displayName(B));
    }
}
