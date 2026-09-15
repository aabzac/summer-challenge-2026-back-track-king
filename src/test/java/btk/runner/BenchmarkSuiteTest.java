package btk.runner;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BenchmarkSuiteTest {
    @TempDir Path temp;

    @Test
    void suitePreservesSeparateReportsSkipsSelfAndIndexCanExcludeMaps() throws Exception {
        Path pool = temp.resolve("pool.json"), panel = temp.resolve("panel.json"), out = temp.resolve("run");
        Files.writeString(pool, "{\"schemaVersion\":1,\"id\":\"test-dev\",\"purpose\":\"development\","
                + "\"generator\":\"cg-sha1prng\",\"seedStart\":123,\"pairs\":1}");
        Files.writeString(panel, "{\"schemaVersion\":1,\"opponents\":[\"v00\",\"v01\"]}");
        String[] args = {"v00", pool.toString(), panel.toString(), "1", out.toString(), ""};
        BenchmarkSuite.main(args);
        var index = JsonParser.parseString(Files.readString(out.resolve("index.json"))).getAsJsonObject();
        assertTrue(index.get("complete").getAsBoolean());
        assertEquals(1, index.getAsJsonArray("reports").size());
        assertEquals("v01", index.getAsJsonArray("reports").get(0).getAsJsonObject().get("opponent").getAsString());
        var report = JsonParser.parseString(Files.readString(out.resolve("opponent-1.json"))).getAsJsonObject();
        assertEquals("test-dev", report.get("poolId").getAsString());
        assertEquals(2, report.get("games").getAsInt());
        assertEquals(1, BenchPool.exclusions(out.resolve("index.json").toString(), "confirmation").hashes().size());
        assertThrows(IllegalArgumentException.class, () -> BenchmarkSuite.main(args), "never overwrite prior suite evidence");
    }
}
