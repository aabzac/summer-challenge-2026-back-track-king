package btk.runner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProcessCheckTest {
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
