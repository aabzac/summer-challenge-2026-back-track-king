package btk.submit;

import btk.bot.BotFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * stage4-threat-model-v1 (2026-10-25): the two Stage 3–4 consistency flags ship only inside their own merger
 * blocks ({@code // BOSS_THREAT}, {@code // BOSS_ROOT}); the default artifact and the arena configuration hash
 * stay as V10SubmissionTest pins them.
 */
class BossThreatRootSubmissionTest {
    static final String A = "current?USE_BOSS_THREAT_MODEL=true";
    static final String B = "current?USE_BOSS_ROOT_TARGET=true";
    static final String AB = "current?USE_BOSS_THREAT_MODEL=true,USE_BOSS_ROOT_TARGET=true";

    @Test void configurationAndArtifactUnchangedWithTheFlagsOffAndTheFlagsOnArtifactCompiles() throws Exception {
        var current = BotFactory.configuration("current");
        assertEquals(130, current.size(), "the arena configuration carries neither new field while off");
        assertFalse(current.containsKey("USE_BOSS_THREAT_MODEL"));
        assertFalse(current.containsKey("USE_BOSS_ROOT_TARGET"));
        assertEquals("true", BotFactory.configuration(A).get("USE_BOSS_THREAT_MODEL"));
        assertEquals("true", BotFactory.configuration(B).get("USE_BOSS_ROOT_TARGET"));
        assertEquals(131, BotFactory.configuration(A).size());
        assertEquals(132, BotFactory.configuration(AB).size());
        assertNotEquals(FileMerger.configSha256("current"), FileMerger.configSha256(A));
        assertNotEquals(FileMerger.configSha256("current"), FileMerger.configSha256(B));
        String off = FileMerger.merge("current");
        for (String token : new String[]{"USE_BOSS_THREAT_MODEL", "USE_BOSS_ROOT_TARGET", "threatModelCalls", "BOSS_THREAT", "BOSS_ROOT"})
            assertFalse(off.contains(token), token + " must not ship while the flag is off");
        String on = FileMerger.merge(AB);
        FileMerger.checkSourceSize(on);
        assertTrue(on.contains("USE_BOSS_THREAT_MODEL=true") && on.contains("USE_BOSS_ROOT_TARGET=true"));
        assertTrue(on.contains("threatModelCalls++") && on.contains("rootTargets[i] = liteHeld"));
        assertTrue(on.length() > off.length());
        String onlyA = FileMerger.merge(A);
        assertTrue(onlyA.contains("threatModelCalls++") && !onlyA.contains("USE_BOSS_ROOT_TARGET"));
        Path dir = Files.createTempDirectory("stage4-artifact");
        Path source = dir.resolve("Player.java");
        Files.writeString(source, on);
        assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-encoding", "UTF-8", "--release", "17", "-d", dir.toString(), source.toString()));
        assertEquals("v10 Boss Model (modified)", BotFactory.displayName(AB));
    }
}
