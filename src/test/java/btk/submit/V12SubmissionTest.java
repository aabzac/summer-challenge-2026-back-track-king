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

    @Test void defaultArtifactIsCandidateE1sConfigurationAndTheV11ConfigurationStaysExpressible() throws Exception {
        String current = FileMerger.merge("current"), frozen = FileMerger.merge("v12");
        FileMerger.checkSourceSize(current);
        FileMerger.checkSourceSize(frozen);
        assertEquals(91349, current.length());
        assertEquals("109be1f36abf0b00f2a712a4b0b64936d381b742d554556a33f3d8b18584faee", FileMerger.sha256(current));
        assertEquals("ce1d3a3b047b74945ebcaa09a42011e2dccda70833ba7cad861bf4e4be79369b", FileMerger.manifest("current", current).get("buildId"));
        // Same resolved configuration as candidate E1's screened and live-tested artifact: only the initializer literal and the label differ.
        assertEquals(E1_CONFIG_SHA, FileMerger.configSha256("current"));
        assertEquals(E1_CONFIG_SHA, FileMerger.configSha256(LiveTargetExperimentParams.E_SPEC));
        assertEquals(current, FileMerger.merge(LiveTargetExperimentParams.E_SPEC), "the E1 spec is now the default");
        assertEquals(91314, frozen.length());
        assertEquals("c89aa7add7599159fcabe0b2875d110a23f821847e7c53ccdcf19f86a65c27f9", FileMerger.sha256(frozen));
        for (String source : new String[]{current, frozen}) {
            assertTrue(source.contains("chooseBossLite") && source.contains("rootTargets[i] = liteHeld") && source.contains("private void exposureIncome("));
            assertTrue(source.contains("USE_EXPOSURE_INCOME=true") && source.contains("EXPOSURE_INCOME_WEIGHT=1.0") && source.contains("USE_BOSS_ROOT_TARGET=true"));
            assertFalse(source.contains("USE_BOSS_THREAT_MODEL") || source.contains("threatModelCalls"), "the parked sibling flag never ships");
            assertFalse(source.contains("BossSabotage"), "the proxy chooser never ships by default");
            assertFalse(source.contains("btk.bot.v12") || source.contains("RolloutBotV12"));
            assertTrue(source.lines().count() > 2000, "readable export");
        }
        assertTrue(current.contains("\n\t\tpublic static final String BUILD_MARKER = \"v12 | BTK_BUILD_V1 strategy=current build="));
        assertTrue(frozen.contains("BUILD_MARKER = \"BTK_BUILD_V1 strategy=v12 build="), "frozen exports keep the bare marker");
        assertTrue(current.contains("\n// BTK strategy: current, v12\n"), "the header names the selected version");
        assertTrue(frozen.contains("\n// BTK strategy: v12\n"));
        assertEquals("v12", FileMerger.manifest("current", current).get("label"));
        assertNull(FileMerger.manifest("v12", frozen).get("label"));
        // The v11 configuration is still expressible on current (the exposure flag off) and resolves to B's config hash.
        String v11OnCurrent = FileMerger.merge(LiveTargetExperimentParams.V11_ON_CURRENT);
        assertEquals(B_CONFIG_SHA, FileMerger.configSha256(LiveTargetExperimentParams.V11_ON_CURRENT));
        assertFalse(v11OnCurrent.contains("USE_EXPOSURE_INCOME"), "the flag's block is dropped while it is off");
        assertEquals("v12 (modified)", FileMerger.manifest(LiveTargetExperimentParams.V11_ON_CURRENT, v11OnCurrent).get("label"));
        var currentParams = new TreeMap<>(BotFactory.configuration("current"));
        assertEquals("false", currentParams.remove("USE_ROLLOUT_ALL_CELL_RISK"));
        assertEquals("false", currentParams.remove("USE_ROLLOUT_ROOT_FILL"));
        assertEquals(currentParams, new TreeMap<>(BotFactory.configuration("v12")));
        assertEquals(133, BotFactory.configuration("current").size());
    }

    @Test void defaultAndFrozenArtifactsCompileWithOnlySubmissionDependencies() throws Exception {
        for (String version : new String[]{"current", "v12"}) {
            Path dir = Files.createTempDirectory("v12-artifact-" + version);
            Path source = dir.resolve("Player.java");
            Files.writeString(source, FileMerger.merge(version));
            assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null,
                    "-encoding", "UTF-8", "--release", "17", "-d", dir.toString(), source.toString()), version);
        }
    }
}
