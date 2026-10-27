package btk.submit;

import btk.bot.BotFactory;
import btk.bot.LiveTargetExperimentParams;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Artifacts after the v12 promotion (2026-10-25 night): what ships by default is E1's configuration (docs/v12-promotion.md). */
class V12SubmissionTest {
    /** Config hash of candidate E1's artifact build/submit-stage4-e1 (docs/stage4-exposure-income-v1.md). */
    private static final String E1_CONFIG_SHA = "d7803b42356c60d009642357d2136e776048f6273edf7010917263592ff516b3";
    /** Config hash of the v11 / B artifact (the arena entry agent 6813813). */
    private static final String B_CONFIG_SHA = "179dcc662b80eb83f1680a86c97180be25e752b126883afb0844d3b664405c96";

    /** Since the v13 promotion (2026-10-27) the v12 configuration on current needs the bypass flag off. */
    private static final String V12_ON_CURRENT = LiveTargetExperimentParams.V12_ON_CURRENT;

    @Test void defaultArtifactIsCandidateE1sConfigurationAndTheV11ConfigurationStaysExpressible() throws Exception {
        String current = FileMerger.merge(V12_ON_CURRENT), frozen = FileMerger.merge("v12");
        FileMerger.checkSourceSize(current);
        FileMerger.checkSourceSize(frozen);
        assertEquals(91371, current.length()); // v13 promotion (2026-10-27): the label reads "v13 (modified)" (was 91,349, sha 109be1f3..., build ce1d3a3b...)
        assertEquals("bfd02a63cc0c6125ca46cc504bc8e1610b396f6b9f9411a11818dfc3a7b0b8dc", FileMerger.sha256(current));
        assertEquals("d1ae67594b81a6056dc8601c74c9a5cfedf3f42ee36f4e2b3c4bef8a38acfabe", FileMerger.manifest(V12_ON_CURRENT, current).get("buildId"));
        // Same resolved configuration as candidate E1's screened and live-tested artifact: only the initializer literal and the label differ.
        assertEquals(E1_CONFIG_SHA, FileMerger.configSha256(V12_ON_CURRENT));
        assertEquals(E1_CONFIG_SHA, FileMerger.configSha256(LiveTargetExperimentParams.E_SPEC));
        assertEquals(current, FileMerger.merge(LiveTargetExperimentParams.E_SPEC), "the E1 spec is the v12 configuration on current");
        assertEquals(91314, frozen.length());
        assertEquals("c89aa7add7599159fcabe0b2875d110a23f821847e7c53ccdcf19f86a65c27f9", FileMerger.sha256(frozen));
        for (String source : new String[]{current, frozen}) {
            assertTrue(source.contains("chooseBossLite") && source.contains("rootTargets[i] = liteHeld") && source.contains("private void exposureIncome("));
            assertTrue(source.contains("USE_EXPOSURE_INCOME=true") && source.contains("EXPOSURE_INCOME_WEIGHT=1.0") && source.contains("USE_BOSS_ROOT_TARGET=true"));
            assertFalse(source.contains("USE_BOSS_THREAT_MODEL") || source.contains("threatModelCalls"), "the parked sibling flag never ships");
            assertFalse(source.contains("BossSabotage"), "the proxy chooser never ships by default");
            assertFalse(source.contains("USE_BYPASS_ROUTES") || source.contains("proposeBypass"), "the v13 flag is off in the v12 configuration and absent from the frozen copy");
            assertFalse(source.contains("btk.bot.v12") || source.contains("RolloutBotV12"));
            assertTrue(source.lines().count() > 2000, "readable export");
        }
        assertTrue(current.contains("\n\t\tpublic static final String BUILD_MARKER = \"v13 (modified) | BTK_BUILD_V1 strategy=current build="));
        assertTrue(frozen.contains("BUILD_MARKER = \"BTK_BUILD_V1 strategy=v12 build="), "frozen exports keep the bare marker");
        assertTrue(current.contains("\n// BTK strategy: current, v13 (modified)\n"), "the header names the selected version");
        assertTrue(frozen.contains("\n// BTK strategy: v12\n"));
        assertEquals("v13 (modified)", FileMerger.manifest(V12_ON_CURRENT, current).get("label"));
        assertNull(FileMerger.manifest("v12", frozen).get("label"));
        // The v11 configuration is still expressible on current (the exposure flag off) and resolves to B's config hash.
        String v11OnCurrent = FileMerger.merge(LiveTargetExperimentParams.V11_ON_CURRENT);
        assertEquals(B_CONFIG_SHA, FileMerger.configSha256(LiveTargetExperimentParams.V11_ON_CURRENT));
        assertFalse(v11OnCurrent.contains("USE_EXPOSURE_INCOME"), "the flag's block is dropped while it is off");
        assertEquals("v13 (modified)", FileMerger.manifest(LiveTargetExperimentParams.V11_ON_CURRENT, v11OnCurrent).get("label"));
        var currentParams = new TreeMap<>(BotFactory.configuration(V12_ON_CURRENT));
        assertEquals("false", currentParams.remove("USE_ROLLOUT_ALL_CELL_RISK"));
        assertEquals("false", currentParams.remove("USE_ROLLOUT_ROOT_FILL"));
        assertEquals(currentParams, new TreeMap<>(BotFactory.configuration("v12")));
        assertEquals(133, BotFactory.configuration(V12_ON_CURRENT).size());
    }

    @Test void defaultAndFrozenArtifactsCompileWithOnlySubmissionDependencies() throws Exception {
        for (String version : new String[]{V12_ON_CURRENT, "v12"}) {
            Path dir = Files.createTempDirectory("v12-artifact");
            Path source = dir.resolve("Player.java");
            Files.writeString(source, FileMerger.merge(version));
            assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null,
                    "-encoding", "UTF-8", "--release", "17", "-d", dir.toString(), source.toString()), version);
        }
    }
}
