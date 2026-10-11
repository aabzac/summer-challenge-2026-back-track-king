package btk.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static btk.tools.NeuralTargetSizeProbe.*;

class NeuralTargetSizeProbeTest {
    @TempDir Path temporary;

    @Test void genericTransformationMatchesExactShippedCoreAndTimerBodies() throws Exception {
        String merged = baseline();
        for (String path : List.of("bot/Timer.java", "core/GameState.java", "core/Pathing.java"))
            assertTrue(merged.contains(body(Files.readString(JAVA.resolve(path))).code()), path);
        assertFalse(declaredTypes(merged).contains("FeaturePaths"), "Required feature-path dependency is not free baseline code");
    }

    @Test void sabotageProjectionRetainsExactFrozenBlobAndExcludesBuildFeatureDependency() throws Exception {
        Head head = sabotageHead(Files.readString(WEIGHTS));
        assertArrayEquals(new int[]{29, 32, 16, 1}, head.sizes());
        assertEquals(1505, head.parameters()); assertEquals(4340, head.payload().length());
        assertEquals(3254, head.decoded().length);
        Body port = body(head.source());
        assertTrue(port.code().contains("static final class NeuralV2Weights"));
        assertFalse(port.code().contains("BUILD")); assertFalse(port.code().contains("TRAINING"));
        assertFalse(port.code().contains("NeuralFeatures"));
        assertThrows(IllegalStateException.class, () -> sabotageHead(head.source().replace("29", "28")));
    }

    @Test void dependencyAndBaselineIdentityChecksRejectOmissionsWithoutWritingAnArtifact() throws Exception {
        Body component = body("package btk.tools.target;\nimport btk.core.FeaturePaths;\npublic final class Target {\n}\n");
        assertThrows(IllegalStateException.class, () -> requireDependencies(List.of(component), Set.of("Target")));
        assertDoesNotThrow(() -> requireDependencies(List.of(component), Set.of("Target", "FeaturePaths")));
        String source = baseline(); Path absent = temporary.resolve("absent.java");
        assertEquals(false, baselineArtifact(source, absent).get("present")); assertFalse(Files.exists(absent));
        Path retained = temporary.resolve("retained.txt"); Files.writeString(retained, source);
        assertEquals(true, baselineArtifact(source, retained).get("byteExact"));
        Files.writeString(retained, source + "\n");
        assertThrows(IllegalStateException.class, () -> baselineArtifact(source, retained));
        assertEquals(source + "\n", Files.readString(retained));
    }
}
