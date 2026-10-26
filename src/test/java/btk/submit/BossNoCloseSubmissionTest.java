package btk.submit;

import btk.bot.BotFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** boss-v4 (2026-10-26): USE_BOSS_NO_CLOSE ships only inside its {@code // BOSS_NOCLOSE} blocks; the default and proxy artifacts are untouched. */
class BossNoCloseSubmissionTest {
    static final String PROXY = "current?USE_BOSS_SABOTAGE=1,USE_BOSS_LITE=0,USE_BOSS_OPPONENT_MODEL=0,USE_BOSS_OWN_MODEL=0,USE_BOSS_LITE_ROUTES=0,"
            + "USE_INCREMENTAL_INK_REFRESH=0,USE_FAST_CHEAPEST_PATH=0,USE_BATCHED_REFRESH=0,USE_EXPOSURE_INCOME=0";
    static final String NC = PROXY + ",USE_BOSS_NO_CLOSE=true";

    @Test void defaultAndProxyArtifactsOmitTheBlocksAndTheFlagOnArtifactCompiles() throws Exception {
        for (String spec : new String[]{"current", PROXY}) {
            String off = FileMerger.merge(spec);
            for (String token : new String[]{"USE_BOSS_NO_CLOSE", "noClose", "BOSS_NOCLOSE_BEGIN"})
                assertFalse(off.contains(token), token + " must not ship in " + spec);
        }
        String on = FileMerger.merge(NC);
        FileMerger.checkSourceSize(on);
        assertTrue(on.contains("USE_BOSS_NO_CLOSE=true") && on.contains("noClose(true)"));
        assertNotEquals(FileMerger.configSha256(PROXY), FileMerger.configSha256(NC));
        Path dir = Files.createTempDirectory("no-close-artifact");
        Path source = dir.resolve("Player.java");
        Files.writeString(source, on);
        assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-encoding", "UTF-8", "--release", "17", "-d", dir.toString(), source.toString()));
    }
}
