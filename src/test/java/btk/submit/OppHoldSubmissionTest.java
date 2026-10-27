package btk.submit;

import btk.bot.BotFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** opp-hold-v1 (2026-10-26): the field ships only inside its {@code // OPP_HOLD} blocks; the default artifact is untouched (V12SubmissionTest). */
class OppHoldSubmissionTest {
    static final String O0 = "current?BOSS_OPP_HOLD_MARGIN=0";

    @Test void defaultArtifactOmitsTheBlocksAndTheFlagOnArtifactCompiles() throws Exception {
        String off = FileMerger.merge("current");
        for (String token : new String[]{"BOSS_OPP_HOLD_MARGIN", "oppHoldCalls", "OPP_HOLD_BEGIN"})
            assertFalse(off.contains(token), token + " must not ship at the default");
        String on = FileMerger.merge(O0);
        FileMerger.checkSourceSize(on);
        assertTrue(on.contains("BOSS_OPP_HOLD_MARGIN=0.0") && on.contains("oppHoldCalls"));
        assertTrue(on.length() > off.length());
        assertNotEquals(FileMerger.configSha256("current"), FileMerger.configSha256(O0));
        Path dir = Files.createTempDirectory("opp-hold-artifact");
        Path source = dir.resolve("Player.java");
        Files.writeString(source, on);
        assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-encoding", "UTF-8", "--release", "17", "-d", dir.toString(), source.toString()));
        assertEquals("v13 (modified)", BotFactory.displayName(O0));
    }
}
