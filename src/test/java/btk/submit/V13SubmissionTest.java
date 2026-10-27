package btk.submit;

import btk.bot.BotFactory;
import btk.bot.LiveTargetExperimentParams;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Artifacts after the v13 promotion (2026-10-27): what ships by default is B2's configuration (docs/v13-promotion.md). */
class V13SubmissionTest {
    /** Config hash of candidate B2's artifact build/submit-bypass-b2 (docs/bypass-routes-v1.md). */
    private static final String B2_CONFIG_SHA = "8c4b3d6d41713d5d95f724308381d4aac6ca58dd64fd3649dfa8e32601531268";
    /** Config hash of the v12 / E1 artifact (the arena entry agent 6814073). */
    private static final String E1_CONFIG_SHA = "d7803b42356c60d009642357d2136e776048f6273edf7010917263592ff516b3";

    @Test void defaultArtifactIsCandidateB2sConfigurationAndTheV12ConfigurationStaysExpressible() throws Exception {
        String current = FileMerger.merge("current"), frozen = FileMerger.merge("v13");
        FileMerger.checkSourceSize(current);
        FileMerger.checkSourceSize(frozen);
        assertEquals(94816, current.length());
        assertEquals("3d3e861d308731e09c2a7a9aead701b6a185484748c1ce82ccacd5a90fca2bf4", FileMerger.sha256(current));
        assertEquals("fb92323ec35a719f68ffd7b0a723f43a31e21a07a140d3cb7fe24a756b96b5cf", FileMerger.manifest("current", current).get("buildId"));
        // Same resolved configuration as candidate B2's screened and live-tested artifact: only the initializer literal and the label differ.
        assertEquals(B2_CONFIG_SHA, FileMerger.configSha256("current"));
        assertEquals(B2_CONFIG_SHA, FileMerger.configSha256(LiveTargetExperimentParams.B2_SPEC));
        assertEquals(current, FileMerger.merge(LiveTargetExperimentParams.B2_SPEC), "the B2 spec is now the default");
        assertEquals(94781, frozen.length());
        assertEquals("7b7dac9ab11c8a13ad0f8475c975229501c684f6c58de8fa0ecd120ec929c799", FileMerger.sha256(frozen));
        for (String source : new String[]{current, frozen}) {
            assertTrue(source.contains("chooseBossLite") && source.contains("private void exposureIncome(") && source.contains("private void proposeBypass("));
            assertTrue(source.contains("USE_BYPASS_ROUTES=true") && source.contains("BYPASS_MIN_INSTABILITY=2") && source.contains("BYPASS_WEIGHT=0.5")
                    && source.contains("USE_EXPOSURE_INCOME=true"));
            assertFalse(source.contains("USE_ROUTE_COMMIT") || source.contains("USE_CORRIDOR_ROUTES"), "the parked construction flags never ship");
            assertFalse(source.contains("BossSabotage"), "the proxy chooser never ships by default");
            assertFalse(source.contains("btk.bot.v13") || source.contains("RolloutBotV13"));
            assertTrue(source.lines().count() > 2000, "readable export");
        }
        assertTrue(current.contains("\n\t\tpublic static final String BUILD_MARKER = \"v13 | BTK_BUILD_V1 strategy=current build="));
        assertTrue(frozen.contains("BUILD_MARKER = \"BTK_BUILD_V1 strategy=v13 build="), "frozen exports keep the bare marker");
        assertTrue(current.contains("\n// BTK strategy: current, v13\n"), "the header names the selected version");
        assertTrue(frozen.contains("\n// BTK strategy: v13\n"));
        assertEquals("v13", FileMerger.manifest("current", current).get("label"));
        assertNull(FileMerger.manifest("v13", frozen).get("label"));
        // The v12 configuration is still expressible on current (the bypass flag off) and resolves to E1's config hash.
        String v12OnCurrent = FileMerger.merge(LiveTargetExperimentParams.V12_ON_CURRENT);
        assertEquals(E1_CONFIG_SHA, FileMerger.configSha256(LiveTargetExperimentParams.V12_ON_CURRENT));
        assertFalse(v12OnCurrent.contains("USE_BYPASS_ROUTES") || v12OnCurrent.contains("proposeBypass"), "the flag's blocks are dropped while it is off");
        assertEquals("v13 (modified)", FileMerger.manifest(LiveTargetExperimentParams.V12_ON_CURRENT, v12OnCurrent).get("label"));
        var currentParams = new TreeMap<>(BotFactory.configuration("current"));
        assertEquals("false", currentParams.remove("USE_ROLLOUT_ALL_CELL_RISK"));
        assertEquals("false", currentParams.remove("USE_ROLLOUT_ROOT_FILL"));
        assertEquals(currentParams, new TreeMap<>(BotFactory.configuration("v13")));
        assertEquals(136, BotFactory.configuration("current").size());
    }

    @Test void defaultAndFrozenArtifactsCompileWithOnlySubmissionDependencies() throws Exception {
        for (String version : new String[]{"current", "v13"}) {
            Path dir = Files.createTempDirectory("v13-artifact-" + version);
            Path source = dir.resolve("Player.java");
            Files.writeString(source, FileMerger.merge(version));
            assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null,
                    "-encoding", "UTF-8", "--release", "17", "-d", dir.toString(), source.toString()), version);
        }
    }
}
