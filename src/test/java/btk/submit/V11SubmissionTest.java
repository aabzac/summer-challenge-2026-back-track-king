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

    @Test void defaultArtifactIsCandidateBsConfigurationAndTheV10ConfigurationStaysExpressible() throws Exception {
        String current = FileMerger.merge("current"), frozen = FileMerger.merge("v11");
        FileMerger.checkSourceSize(current);
        FileMerger.checkSourceSize(frozen);
        assertEquals(88990, current.length());
        assertEquals("cf1972a17d7b67541cbcca68f0b12f1384bc050e2bbb42494288f0fcdd3db090", FileMerger.sha256(current));
        assertEquals("35176f244f6700bc3d71fccf625ca0d25ddb0e5eefe044d405ba510b7f375e03", FileMerger.manifest("current", current).get("buildId"));
        // Same resolved configuration as candidate B's screened and live-tested artifact: only the one initializer literal differs.
        assertEquals(B_CONFIG_SHA, FileMerger.configSha256("current"));
        assertEquals(B_CONFIG_SHA, FileMerger.configSha256(LiveTargetExperimentParams.B_SPEC));
        assertEquals(current, FileMerger.merge(LiveTargetExperimentParams.B_SPEC), "the B spec is now the default");
        assertEquals(88955, frozen.length());
        assertEquals("b12a1943a3c024b1ff7e09830d6c7f30d0c2a21b2da820970b5215e68efb258f", FileMerger.sha256(frozen));
        for (String source : new String[]{current, frozen}) {
            assertTrue(source.contains("chooseBossLite") && source.contains("rootTargets[i] = liteHeld"));
            assertTrue(source.contains("USE_BOSS_ROOT_TARGET=true") && source.contains("USE_BOSS_LITE=true"));
            assertFalse(source.contains("USE_BOSS_THREAT_MODEL") || source.contains("threatModelCalls"), "the parked sibling flag never ships");
            assertFalse(source.contains("BossSabotage"), "the proxy chooser never ships by default");
            assertFalse(source.contains("btk.bot.v11") || source.contains("RolloutBotV11"));
            assertTrue(source.lines().count() > 2000, "readable export");
        }
        assertTrue(current.contains("\n\t\tpublic static final String BUILD_MARKER = \"v11 | BTK_BUILD_V1 strategy=current build="));
        assertTrue(frozen.contains("BUILD_MARKER = \"BTK_BUILD_V1 strategy=v11 build="), "frozen exports keep the bare marker");
        assertTrue(current.contains("\n// BTK strategy: current, v11\n"), "the header names the selected version");
        assertTrue(frozen.contains("\n// BTK strategy: v11\n"));
        assertTrue(FileMerger.merge(LiveTargetExperimentParams.V10_ON_CURRENT).contains("\n// BTK strategy: current, v11 (modified)\n"));
        assertEquals("v11", FileMerger.manifest("current", current).get("label"));
        assertNull(FileMerger.manifest("v11", frozen).get("label"));
        // The v10 configuration is still expressible on current (the root-target flag off) and resolves to D's config hash.
        String v10OnCurrent = FileMerger.merge(LiveTargetExperimentParams.V10_ON_CURRENT);
        assertEquals(D_CONFIG_SHA, FileMerger.configSha256(LiveTargetExperimentParams.V10_ON_CURRENT));
        assertFalse(v10OnCurrent.contains("USE_BOSS_ROOT_TARGET"), "the flag's block is dropped while it is off");
        assertEquals("v11 (modified)", FileMerger.manifest(LiveTargetExperimentParams.V10_ON_CURRENT, v10OnCurrent).get("label"));
        var currentParams = new TreeMap<>(BotFactory.configuration("current"));
        assertEquals("false", currentParams.remove("USE_ROLLOUT_ALL_CELL_RISK"));
        assertEquals("false", currentParams.remove("USE_ROLLOUT_ROOT_FILL"));
        assertEquals(currentParams, new TreeMap<>(BotFactory.configuration("v11")));
        assertEquals(131, BotFactory.configuration("current").size());
    }

    @Test void defaultAndFrozenArtifactsCompileWithOnlySubmissionDependencies() throws Exception {
        for (String version : new String[]{"current", "v11"}) {
            Path dir = Files.createTempDirectory("v11-artifact-" + version);
            Path source = dir.resolve("Player.java");
            Files.writeString(source, FileMerger.merge(version));
            assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null,
                    "-encoding", "UTF-8", "--release", "17", "-d", dir.toString(), source.toString()), version);
        }
    }
}
