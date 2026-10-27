package btk.submit;

import btk.bot.BotFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** bypass-routes-v1 (2026-10-27): the flag ships only inside its {@code // BYPASS} blocks; since the v13 promotion they ship by default, and the v12 configuration on current omits them (V12SubmissionTest). */
class BypassRoutesSubmissionTest {
    static final String B = "current?USE_BYPASS_ROUTES=true";

    @Test void defaultArtifactOmitsTheBlocksAndTheFlagOnArtifactCompiles() throws Exception {
        String off = FileMerger.merge(btk.bot.LiveTargetExperimentParams.V12_ON_CURRENT);
        for (String token : new String[]{"USE_BYPASS_ROUTES", "BYPASS_MIN_INSTABILITY", "BYPASS_WEIGHT", "proposeBypass", "BYPASS_BEGIN"})
            assertFalse(off.contains(token), token + " must not ship while the flag is off");
        String on = FileMerger.merge(B);
        FileMerger.checkSourceSize(on);
        assertTrue(on.contains("USE_BYPASS_ROUTES=true") && on.contains("BYPASS_MIN_INSTABILITY=2") && on.contains("BYPASS_WEIGHT=0.5"));
        assertTrue(on.contains("private void proposeBypass(") && on.contains("bypassProposals"));
        assertTrue(on.length() > off.length());
        assertNotEquals(FileMerger.configSha256(btk.bot.LiveTargetExperimentParams.V12_ON_CURRENT), FileMerger.configSha256(B));
        assertEquals(FileMerger.merge("current"), on, "the B2 spec is the v13 default artifact");
        Path dir = Files.createTempDirectory("bypass-artifact");
        Path source = dir.resolve("Player.java");
        Files.writeString(source, on);
        assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-encoding", "UTF-8", "--release", "17", "-d", dir.toString(), source.toString()));
        assertEquals("v13", BotFactory.displayName(B));
    }
}
