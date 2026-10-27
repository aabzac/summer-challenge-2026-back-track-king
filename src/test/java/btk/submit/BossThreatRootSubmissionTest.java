package btk.submit;

import btk.bot.BotFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * stage4-threat-model-v1 (2026-10-25): the root-target flag ships only inside its own merger block ({@code // BOSS_ROOT});
 * the v10 configuration on current carries neither it nor the parked threat model (pruned from current by prune-v2,
 * 2026-10-27; its {@code // BOSS_THREAT} blocks remain in the frozen v11-v13 copies).
 */
class BossThreatRootSubmissionTest {
    // Since the v12 promotion the spec carries the exposure flag off, to stay the configuration the screen measured.
    static final String B = "current?USE_BOSS_ROOT_TARGET=true,USE_EXPOSURE_INCOME=0,USE_BYPASS_ROUTES=0";

    /** Since the v11 promotion (2026-10-25) the "both flags off" configuration is the v10 one on current. */
    static final String OFF = btk.bot.LiveTargetExperimentParams.V10_ON_CURRENT;

    @Test void configurationAndArtifactUnchangedWithTheFlagsOffAndTheFlagsOnArtifactCompiles() throws Exception {
        var current = BotFactory.configuration(OFF);
        assertEquals(130, current.size(), "the v10 arena configuration carries neither field while off");
        assertFalse(current.containsKey("USE_BOSS_THREAT_MODEL"));
        assertFalse(current.containsKey("USE_BOSS_ROOT_TARGET"));
        assertEquals("true", BotFactory.configuration(B).get("USE_BOSS_ROOT_TARGET"));
        assertEquals("true", BotFactory.configuration("current").get("USE_BOSS_ROOT_TARGET"), "selected since v11");
        assertEquals(131, BotFactory.configuration(B).size());
        assertNotEquals(FileMerger.configSha256(OFF), FileMerger.configSha256(B));
        assertEquals(FileMerger.configSha256(btk.bot.LiveTargetExperimentParams.V11_ON_CURRENT), FileMerger.configSha256(B), "B is the v11 configuration");
        String off = FileMerger.merge(OFF);
        for (String token : new String[]{"USE_BOSS_THREAT_MODEL", "USE_BOSS_ROOT_TARGET", "threatModelCalls", "BOSS_THREAT", "BOSS_ROOT"})
            assertFalse(off.contains(token), token + " must not ship while the flag is off");
        String on = FileMerger.merge(B);
        FileMerger.checkSourceSize(on);
        assertTrue(on.contains("USE_BOSS_ROOT_TARGET=true") && on.contains("rootTargets[i] = liteHeld"));
        assertFalse(on.contains("USE_BOSS_THREAT_MODEL") || on.contains("threatModelCalls"), "pruned from current (prune-v2)");
        assertTrue(on.length() > off.length());
        Path dir = Files.createTempDirectory("stage4-artifact");
        Path source = dir.resolve("Player.java");
        Files.writeString(source, on);
        assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-encoding", "UTF-8", "--release", "17", "-d", dir.toString(), source.toString()));
        assertEquals("v13 (modified)", BotFactory.displayName(B));
        assertEquals("v13 (modified)", BotFactory.displayName(OFF));
    }
}
