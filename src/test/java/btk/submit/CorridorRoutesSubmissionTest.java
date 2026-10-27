package btk.submit;

import btk.bot.BotFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** corridor-routes-v1 (2026-10-26): the flag ships only inside its {@code // CORRIDOR} block; the default artifact is untouched (V12SubmissionTest). */
class CorridorRoutesSubmissionTest {
    static final String C = "current?USE_CORRIDOR_ROUTES=true";

    @Test void defaultArtifactOmitsTheBlockAndTheFlagOnArtifactCompiles() throws Exception {
        String off = FileMerger.merge("current");
        for (String token : new String[]{"USE_CORRIDOR_ROUTES", "CORRIDOR_WEIGHT", "corridorCosts", "proposeCorridors", "corridorRecord", "CORRIDOR_BEGIN"})
            assertFalse(off.contains(token), token + " must not ship while the flag is off");
        String on = FileMerger.merge(C);
        FileMerger.checkSourceSize(on);
        assertTrue(on.contains("USE_CORRIDOR_ROUTES=true") && on.contains("CORRIDOR_WEIGHT=1.0"));
        assertTrue(on.contains("private boolean corridorCosts(") && on.contains("corridorProposals"));
        assertTrue(on.length() > off.length());
        assertNotEquals(FileMerger.configSha256("current"), FileMerger.configSha256(C));
        Path dir = Files.createTempDirectory("corridor-artifact");
        Path source = dir.resolve("Player.java");
        Files.writeString(source, on);
        assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-encoding", "UTF-8", "--release", "17", "-d", dir.toString(), source.toString()));
        assertEquals("v13 (modified)", BotFactory.displayName(C));
    }
}
