package btk.runner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProcessCheckTest {
    @Test void submissionDirectoryKeepsTheDefaultAndAcceptsAnExplicitArtifact() {
        assertEquals(Path.of("build/submit"), ProcessCheck.submissionDirectory(""));
        assertEquals(Path.of("build/submit"), ProcessCheck.submissionDirectory(null));
        assertEquals(Path.of("build/tagged-v07"), ProcessCheck.submissionDirectory("build/tagged-v07"));
    }

    @Test void explicitArtifactRequiresSourceAndCompiledPlayer(@TempDir Path directory) throws Exception {
        Path artifact = directory.resolve("tagged");
        assertThrows(IllegalArgumentException.class, () -> ProcessCheck.requireExplicitArtifact(artifact));
        Files.createDirectories(artifact.resolve("classes"));
        assertThrows(IllegalArgumentException.class, () -> ProcessCheck.requireExplicitArtifact(artifact));
        Files.writeString(artifact.resolve("Player.java"), "class Player {}");
        assertThrows(IllegalArgumentException.class, () -> ProcessCheck.requireExplicitArtifact(artifact));
        Files.write(artifact.resolve("classes/Player.class"), new byte[]{0});
        assertDoesNotThrow(() -> ProcessCheck.requireExplicitArtifact(artifact));
    }

    @Test void missingExplicitArtifactFailsBeforeStartingGamesOrCreatingReport(@TempDir Path directory) {
        Path report = directory.resolve("report.json"), missing = directory.resolve("missing");
        var error = assertThrows(IllegalArgumentException.class, () -> ProcessCheck.main(
                new String[]{"v07", "1", "", "0", report.toString(), "false", missing.toString()}));
        assertTrue(error.getMessage().contains("submitOut"));
        assertFalse(Files.exists(report));
        assertFalse(Files.exists(missing));
    }

    @Test void timeoutSummaryDoesNotInventAResponse() {
        var t = new ProcessBot.TurnTiming(0,1000,0,1112.2,null,1112.3,"TIMEOUT",null);
        String summary = ProcessCheck.timingSummary(List.of(t));
        assertTrue(summary.contains("first response none"));
        assertTrue(summary.contains("first send 1112.200 ms"));
        assertTrue(summary.contains("first consumer 1112.300 ms"));
        assertTrue(summary.contains("max later response none"));
    }
    @Test void summaryKeepsReaderAndConsumerTimesSeparate() {
        var first = new ProcessBot.TurnTiming(0,1000,0,2,500.0,550,"OK",null);
        var next = new ProcessBot.TurnTiming(1,50,0,3,35.0,180,"OK",null);
        String summary = ProcessCheck.timingSummary(List.of(first,next));
        assertTrue(summary.contains("first response 500.000 ms"));
        assertTrue(summary.contains("first consumer 550.000 ms"));
        assertTrue(summary.contains("max later response 35.000 ms"));
    }
    @Test void diagnosticsFlagRequiresExplicitKnownValue() {
        for (String flag : new String[]{"true", "1"}) assertTrue(ProcessCheck.parseDiagnostics(flag));
        for (String flag : new String[]{"false", "0", ""}) assertFalse(ProcessCheck.parseDiagnostics(flag));
        for (String flag : new String[]{"yes", "2", "tru"}) {
            assertThrows(IllegalArgumentException.class, () -> ProcessCheck.parseDiagnostics(flag));
        }
    }

    @Test void diagnosticRunRefusesToOverwriteEvidence(@TempDir Path directory) throws Exception {
        Path report = directory.resolve("diagnostic.json");
        Files.writeString(report, "previous evidence");
        var error = assertThrows(IllegalArgumentException.class,
                () -> ProcessCheck.main(new String[]{"v04", "8", "", "0", report.toString(), "true"}));
        assertTrue(error.getMessage().contains("Choose a new diagnostic report"));
        assertEquals("previous evidence", Files.readString(report));
    }
}
