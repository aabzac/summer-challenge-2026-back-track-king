package btk.submit;

import btk.bot.BotFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** earning-delay-v1 (2026-10-26): the two fields ship only inside their {@code // DELAY} block; the default artifact is untouched (V12SubmissionTest). */
class EarningDelaySubmissionTest {
    static final String Q = "current?USE_QUICK_ALWAYS=true";
    static final String D2 = "current?DELAY_POWER=2";

    @Test void defaultArtifactOmitsTheBlockAndTheFlagOnArtifactsCompile() throws Exception {
        String off = FileMerger.merge("current");
        for (String token : new String[]{"USE_QUICK_ALWAYS", "DELAY_POWER", "DELAY_BEGIN"})
            assertFalse(off.contains(token), token + " must not ship while the flags are off");
        for (String spec : new String[]{Q, D2}) {
            String on = FileMerger.merge(spec);
            FileMerger.checkSourceSize(on);
            assertTrue(on.contains("USE_QUICK_ALWAYS") && on.contains("DELAY_POWER"), spec);
            assertTrue(on.length() > off.length(), spec);
            assertNotEquals(FileMerger.configSha256("current"), FileMerger.configSha256(spec), spec);
            Path dir = Files.createTempDirectory("delay-artifact");
            Path source = dir.resolve("Player.java");
            Files.writeString(source, on);
            assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null,
                    "-encoding", "UTF-8", "--release", "17", "-d", dir.toString(), source.toString()), spec + " compiles");
        }
        assertTrue(FileMerger.merge(Q).contains("USE_QUICK_ALWAYS=true"));
        assertTrue(FileMerger.merge(D2).contains("DELAY_POWER=2.0"));
        assertEquals("v12 (modified)", BotFactory.displayName(Q));
    }
}
