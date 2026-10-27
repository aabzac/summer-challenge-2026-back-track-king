package btk.submit;

import btk.bot.BotFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** route-commit-v1 (2026-10-27): the flag ships only inside its {@code // COMMIT} blocks; the default artifact is untouched (V12SubmissionTest). */
class RouteCommitSubmissionTest {
    static final String C = "current?USE_ROUTE_COMMIT=true";

    @Test void defaultArtifactOmitsTheBlocksAndTheFlagOnArtifactCompiles() throws Exception {
        String off = FileMerger.merge("current");
        for (String token : new String[]{"USE_ROUTE_COMMIT", "COMMIT_BONUS", "recordCommitment", "onCommitted", "COMMIT_BEGIN"})
            assertFalse(off.contains(token), token + " must not ship while the flag is off");
        String on = FileMerger.merge(C);
        FileMerger.checkSourceSize(on);
        assertTrue(on.contains("USE_ROUTE_COMMIT=true") && on.contains("COMMIT_BONUS=10.0"));
        assertTrue(on.contains("private void recordCommitment(") && on.contains("commitRootsBonused"));
        assertTrue(on.length() > off.length());
        assertNotEquals(FileMerger.configSha256("current"), FileMerger.configSha256(C));
        Path dir = Files.createTempDirectory("commit-artifact");
        Path source = dir.resolve("Player.java");
        Files.writeString(source, on);
        assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-encoding", "UTF-8", "--release", "17", "-d", dir.toString(), source.toString()));
        assertEquals("v13 (modified)", BotFactory.displayName(C));
    }
}
