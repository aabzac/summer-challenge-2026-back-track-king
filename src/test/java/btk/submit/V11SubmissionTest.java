package btk.submit;

import btk.bot.BotFactory;
import btk.bot.LiveTargetExperimentParams;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Artifacts after the v11 promotion (2026-10-25): what ships by default is B's configuration (docs/v11-promotion.md). */
class V11SubmissionTest {
    /** Config hash of candidate B's artifact build/submit-stage4-b (docs/stage4-threat-model-v1.md). */
    private static final String B_CONFIG_SHA = "179dcc662b80eb83f1680a86c97180be25e752b126883afb0844d3b664405c96";
    /** Config hash of the v10 / D arena artifact. */
    private static final String D_CONFIG_SHA = "f0e507ac336a12610c7f14c3b20c3fa57527ced3a34bceb88826a4bf01d127fb";

    /** Since the v12 promotion (2026-10-25 night) the v11 configuration on current needs the exposure flag off. */
    private static final String V11_ON_CURRENT = LiveTargetExperimentParams.V11_ON_CURRENT;

    @Test void defaultArtifactIsCandidateBsConfigurationAndTheV10ConfigurationStaysExpressible() throws Exception {
        String current = FileMerger.merge(V11_ON_CURRENT), frozen = FileMerger.merge("v11");
        FileMerger.checkSourceSize(current);
        FileMerger.checkSourceSize(frozen);
        assertEquals(89012, current.length()); // v12 promotion (2026-10-25 night): the label reads "v12 (modified)"
        assertEquals("48c92aab421fdab5e5260103a123a31952e555201fc79e7444c1749ce52294f8", FileMerger.sha256(current));
        assertEquals("0b73d984c7de46ff05e4f64bf9a765dac9695a39475d958d08e0e16ece0d4254", FileMerger.manifest(V11_ON_CURRENT, current).get("buildId"));
        // Same resolved configuration as candidate B's screened and live-tested artifact: only the one initializer literal differs.
        assertEquals(B_CONFIG_SHA, FileMerger.configSha256(V11_ON_CURRENT));
        assertEquals(B_CONFIG_SHA, FileMerger.configSha256(LiveTargetExperimentParams.B_SPEC));
        assertEquals(current, FileMerger.merge(LiveTargetExperimentParams.B_SPEC), "the B spec is the v11 configuration on current");
        assertEquals(88955, frozen.length());
        assertEquals("b12a1943a3c024b1ff7e09830d6c7f30d0c2a21b2da820970b5215e68efb258f", FileMerger.sha256(frozen));
        for (String source : new String[]{current, frozen}) {
            assertTrue(source.contains("chooseBossLite") && source.contains("rootTargets[i] = liteHeld"));
            assertTrue(source.contains("USE_BOSS_ROOT_TARGET=true") && source.contains("USE_BOSS_LITE=true"));
            assertFalse(source.contains("USE_BOSS_THREAT_MODEL") || source.contains("threatModelCalls"), "the parked sibling flag never ships");
            assertFalse(source.contains("USE_EXPOSURE_INCOME"), "the v12 flag is off in the v11 configuration and absent from the frozen copy");
            assertFalse(source.contains("BossSabotage"), "the proxy chooser never ships by default");
            assertFalse(source.contains("btk.bot.v11") || source.contains("RolloutBotV11"));
            assertTrue(source.lines().count() > 2000, "readable export");
        }
        assertTrue(current.contains("\n\t\tpublic static final String BUILD_MARKER = \"v12 (modified) | BTK_BUILD_V1 strategy=current build="));
        assertTrue(frozen.contains("BUILD_MARKER = \"BTK_BUILD_V1 strategy=v11 build="), "frozen exports keep the bare marker");
        assertTrue(current.contains("\n// BTK strategy: current, v12 (modified)\n"), "the header names the selected version");
        assertTrue(frozen.contains("\n// BTK strategy: v11\n"));
        assertEquals("v12 (modified)", FileMerger.manifest(V11_ON_CURRENT, current).get("label"));
        assertNull(FileMerger.manifest("v11", frozen).get("label"));
        // The v10 configuration is still expressible on current (the root-target flag off) and resolves to D's config hash.
        String v10OnCurrent = FileMerger.merge(LiveTargetExperimentParams.V10_ON_CURRENT);
        assertEquals(D_CONFIG_SHA, FileMerger.configSha256(LiveTargetExperimentParams.V10_ON_CURRENT));
        assertFalse(v10OnCurrent.contains("USE_BOSS_ROOT_TARGET"), "the flag's block is dropped while it is off");
        assertEquals("v12 (modified)", FileMerger.manifest(LiveTargetExperimentParams.V10_ON_CURRENT, v10OnCurrent).get("label"));
        var currentParams = new TreeMap<>(BotFactory.configuration(V11_ON_CURRENT));
        assertEquals("false", currentParams.remove("USE_ROLLOUT_ALL_CELL_RISK"));
        assertEquals("false", currentParams.remove("USE_ROLLOUT_ROOT_FILL"));
        assertEquals(currentParams, new TreeMap<>(BotFactory.configuration("v11")));
        assertEquals(131, BotFactory.configuration(V11_ON_CURRENT).size());
    }

    @Test void defaultAndFrozenArtifactsCompileWithOnlySubmissionDependencies() throws Exception {
        for (String version : new String[]{V11_ON_CURRENT, "v11"}) {
            Path dir = Files.createTempDirectory("v11-artifact");
            Path source = dir.resolve("Player.java");
            Files.writeString(source, FileMerger.merge(version));
            assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null,
                    "-encoding", "UTF-8", "--release", "17", "-d", dir.toString(), source.toString()), version);
        }
    }
}
