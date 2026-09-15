package btk.runner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProcessCheckTest {
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
