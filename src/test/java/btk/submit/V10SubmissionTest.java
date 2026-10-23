package btk.submit;

import btk.bot.BotFactory;
import btk.bot.LiveTargetExperimentParams;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Artifacts after the v10 promotion (2026-10-23): what ships by default is D's configuration. */
class V10SubmissionTest {
    /** Config hash of the arena artifact build/submit-stage2-d (marker config=...), recorded in docs/stage2-d-live-boss-v1.md. */
    private static final String D_CONFIG_SHA = "f0e507ac336a12610c7f14c3b20c3fa57527ced3a34bceb88826a4bf01d127fb";

    @Test void defaultArtifactIsTheArenaConfigurationAndThePreviousBaselinesKeepTheirBytes() throws Exception {
        String current = FileMerger.merge("current"), frozen = FileMerger.merge("v10");
        FileMerger.checkSourceSize(current);
        FileMerger.checkSourceSize(frozen);
        assertEquals(99676, current.length());
        assertEquals("1c8912347fefc39f8b16e3d37d0087ba4f749f433c219424eaaf2adb4c0804fc", FileMerger.sha256(current));
        assertEquals(99598, frozen.length());
        assertEquals("9c3b40085db2d818b3a46212f4f5659f7ca6d6d2514eae026dd98daed21e4699", FileMerger.sha256(frozen));
        // Same resolved configuration as the uploaded D artifact: only the Params initializer literals differ in the text.
        assertEquals(D_CONFIG_SHA, FileMerger.configSha256("current"));
        assertEquals(D_CONFIG_SHA, FileMerger.configSha256(LiveTargetExperimentParams.D_SPEC));
        assertEquals(current, FileMerger.merge(LiveTargetExperimentParams.D_SPEC), "the D spec is now the default");
        for (String source : new String[]{current, frozen}) {
            assertTrue(source.contains("chooseBossLite"));
            assertTrue(source.contains("USE_BOSS_LITE=true") && source.contains("USE_BATCHED_REFRESH=true")
                    && source.contains("USE_INCREMENTAL_INK_REFRESH=true") && source.contains("USE_FAST_CHEAPEST_PATH=true"));
            assertFalse(source.contains("BossSabotage"), "the proxy chooser never ships by default");
            assertFalse(source.contains("RolloutBotV10"));
            assertFalse(source.contains("btk.bot.v10"));
        }
        assertTrue(current.contains("strategy=current build="));
        assertTrue(frozen.contains("strategy=v10 build="));
        // The v09-era baseline and v09 itself are untouched.
        assertEquals("ab0954a4545f25222fce6bccf8fa11866d276266e96b4728b502c83996d847e5",
                FileMerger.sha256(FileMerger.merge(LiveTargetExperimentParams.V09_ON_CURRENT)));
        assertEquals("aca0be459116e78b97400ba0a0abfaa151aca7cc97db7804b8466e8cda788fac", FileMerger.sha256(FileMerger.merge("v09")));
        var currentParams = new TreeMap<>(BotFactory.configuration("current"));
        assertEquals("false", currentParams.remove("USE_ROLLOUT_ALL_CELL_RISK"));
        assertEquals("false", currentParams.remove("USE_ROLLOUT_ROOT_FILL"));
        assertEquals(currentParams, new TreeMap<>(BotFactory.configuration("v10")));
    }

    @Test void defaultArtifactCompilesWithOnlySubmissionDependencies() throws Exception {
        Path dir = Files.createTempDirectory("v10-artifact");
        Path source = dir.resolve("Player.java");
        Files.writeString(source, FileMerger.merge("current"));
        assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-encoding", "UTF-8", "--release", "17", "-d", dir.toString(), source.toString()));
    }
}
