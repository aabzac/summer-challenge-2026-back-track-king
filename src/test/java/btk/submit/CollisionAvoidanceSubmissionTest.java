package btk.submit;

import btk.bot.BotFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** collision-avoidance-v1 (2026-10-25): the flag ships only inside its {@code // COLLISION} block; the default artifact is untouched (V12SubmissionTest). */
class CollisionAvoidanceSubmissionTest {
    static final String C = "current?USE_COLLISION_AVOIDANCE=true";

    @Test void defaultArtifactOmitsTheBlockAndTheFlagOnArtifactCompiles() throws Exception {
        String off = FileMerger.merge("current");
        for (String token : new String[]{"USE_COLLISION_AVOIDANCE", "COLLISION_PENALTY", "predictCollisions", "collisionCells", "COLLISION_BEGIN"})
            assertFalse(off.contains(token), token + " must not ship while the flag is off");
        String on = FileMerger.merge(C);
        FileMerger.checkSourceSize(on);
        assertTrue(on.contains("USE_COLLISION_AVOIDANCE=true") && on.contains("COLLISION_PENALTY=5.0"));
        assertTrue(on.contains("private void predictCollisions(") && on.contains("collisionRootsPenalised"));
        assertTrue(on.length() > off.length());
        assertNotEquals(FileMerger.configSha256("current"), FileMerger.configSha256(C));
        Path dir = Files.createTempDirectory("collision-artifact");
        Path source = dir.resolve("Player.java");
        Files.writeString(source, on);
        assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-encoding", "UTF-8", "--release", "17", "-d", dir.toString(), source.toString()));
        assertEquals("v13 (modified)", BotFactory.displayName(C));
    }
}
