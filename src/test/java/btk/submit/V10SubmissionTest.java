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

    /** Since the v11 promotion (2026-10-25) the v10 configuration on current needs the root-target flag off. */
    private static final String V10_ON_CURRENT = LiveTargetExperimentParams.V10_ON_CURRENT;

    @Test void defaultArtifactIsTheArenaConfigurationAndThePreviousBaselinesKeepTheirBytes() throws Exception {
        String current = FileMerger.merge(V10_ON_CURRENT), frozen = FileMerger.merge("v10");
        FileMerger.checkSourceSize(current);
        FileMerger.checkSourceSize(frozen);
        // prune-v1 (2026-10-25): the same configuration and decisions in 70,397 characters (was 99,676 at the promotion);
        // readable-export-v1 (2026-10-25): the same tokens laid out readably (tabs, one statement per line) in 88,671.
        assertEquals(88687, current.length()); // v11 promotion (2026-10-25): the marker's display name reads "v12 (modified)"
        assertEquals("ec331323b84a50605d292765f061a2cd4db19c484ac1c6297d27456bbdc23ecc", FileMerger.sha256(current));
        assertEquals(99598, frozen.length());
        assertEquals("9c3b40085db2d818b3a46212f4f5659f7ca6d6d2514eae026dd98daed21e4699", FileMerger.sha256(frozen));
        // Same resolved configuration as the uploaded D artifact: only the Params initializer literals differ in the text.
        assertEquals(D_CONFIG_SHA, FileMerger.configSha256(V10_ON_CURRENT));
        assertEquals(D_CONFIG_SHA, FileMerger.configSha256(LiveTargetExperimentParams.D_SPEC));
        assertEquals(current, FileMerger.merge(LiveTargetExperimentParams.D_SPEC), "the D spec is the v10 configuration on current");
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
        // readable-export-v1: the viewer shows the strategy name first; the machine marker and the frozen bytes are unchanged.
        assertTrue(current.contains("\n\t\tpublic static final String BUILD_MARKER = \"v12 (modified) | BTK_BUILD_V1 strategy=current build="));
        assertTrue(frozen.contains("public static final String BUILD_MARKER = \"BTK_BUILD_V1 strategy=v10 build="));
        assertTrue(current.contains("\n\tstatic final class Action {\n"), "one statement per line, tab-indented inside Player");
        assertTrue(current.lines().count() > 2000 && frozen.lines().count() < 100, "current readable, frozen v10 compact");
        assertEquals("v12 (modified)", FileMerger.manifest(V10_ON_CURRENT, current).get("label"), "the v10 configuration is a modification of the selected v11");
        assertNull(FileMerger.manifest("v10", frozen).get("label"));
        assertEquals("v12", BotFactory.displayName("current"));
        assertEquals("v12", BotFactory.displayName("current?NOISE=0.1"), "an equivalent spelling is not a modification");
        assertEquals("v12 (modified)", BotFactory.displayName(LiveTargetExperimentParams.D_SPEC));
        assertEquals("v12 (modified)", BotFactory.displayName(LiveTargetExperimentParams.V09_ON_CURRENT));
        assertEquals("v10", BotFactory.displayName("v10"));
        assertEquals("v10 (modified)", BotFactory.displayName("v10?ROLLOUT_DEPTH=6"));
        assertEquals("v09", BotFactory.displayName("v09"));
        assertEquals("neural-v6", BotFactory.displayName("neural-v6"));
        // The v09-era baseline on current is laid out readably like every current export; frozen v09 itself is untouched.
        assertEquals("9ab8ebac55e185d829f4603f2605dbf531181d1d5e8cae49a1f2e1ec3999c792",
                FileMerger.sha256(FileMerger.merge(LiveTargetExperimentParams.V09_ON_CURRENT)));
        assertEquals("aca0be459116e78b97400ba0a0abfaa151aca7cc97db7804b8466e8cda788fac", FileMerger.sha256(FileMerger.merge("v09")));
        var currentParams = new TreeMap<>(BotFactory.configuration(V10_ON_CURRENT));
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
