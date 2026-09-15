package btk.runner;

import btk.core.Protocol;
import btk.referee.MapGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BenchPoolTest {
    @TempDir Path temp;

    private Path manifest(String purpose, long seed, int pairs) throws Exception {
        Path path = temp.resolve("pool.json");
        Files.writeString(path, "{\"schemaVersion\":1,\"id\":\"test\",\"purpose\":\"" + purpose
                + "\",\"generator\":\"cg-sha1prng\",\"seedStart\":" + seed + ",\"pairs\":" + pairs + "}");
        return path;
    }

    @Test
    void fixedPoolRequiresCompleteRangeAndUnambiguousSelection() throws Exception {
        Path path = manifest("development", 123, 2);
        var p = BenchPool.select(path.toString(), "", "", "");
        assertEquals(123, p.baseSeed()); assertEquals(4, p.games()); assertEquals(64, p.sha256().length());
        assertThrows(IllegalArgumentException.class, () -> BenchPool.select(path.toString(), "2", "", ""));
        assertThrows(IllegalArgumentException.class, () -> BenchPool.select(path.toString(), "", "123", ""));
        assertThrows(IllegalArgumentException.class, () -> BenchPool.select(path.toString(), "", "", "replays/cg"));
        assertEquals(200, BenchPool.select("", "", "", "").games());
        assertEquals(1000, BenchPool.select("", "", "", "").baseSeed());
    }

    @Test
    void confirmationRequiresRealNonemptyPriorReports() throws Exception {
        var p = BenchPool.select(manifest("confirmation", 125, 2).toString(), "", "", "");
        assertThrows(IllegalArgumentException.class, () -> BenchPool.exclusions("", p.purpose()));
        Path bad = temp.resolve("bad.json"); Files.writeString(bad, "{\"gamesPerMap\":{}}");
        assertThrows(IllegalArgumentException.class, () -> BenchPool.exclusions(bad.toString(), p.purpose()));
        String hash = "a".repeat(64); Files.writeString(bad, "{\"gamesPerMap\":{\"" + hash + "\":2}}");
        var excluded = BenchPool.exclusions(bad.toString(), p.purpose());
        assertEquals(Set.of(hash), excluded.hashes());
        assertEquals(64, excluded.reportSha256().get(bad.toString()).length());
    }

    @Test
    void overlappingMapIsRejectedBeforeAnyBotIsCreated() throws Exception {
        String hash = BenchPool.digest(Protocol.initBlock(MapGenerator.generate(124), 0).getBytes(StandardCharsets.UTF_8));
        var created = new AtomicInteger();
        assertThrows(IllegalArgumentException.class, () -> BenchmarkRunner.run("v01", "v02", 4,
                new Maps(""), 1, 123, TimingProfile.CG,
                spec -> { created.incrementAndGet(); return btk.bot.BotFactory.create(spec); }, Set.of(hash)));
        assertEquals(0, created.get());
    }

    @Test
    void invalidPoolRangesAreNotSilentlyReplaced() throws Exception {
        Path zero = manifest("development", 0, 2);
        assertThrows(IllegalArgumentException.class, () -> BenchPool.select(zero.toString(), "", "", ""));
        Path overflow = manifest("development", Long.MAX_VALUE, 2);
        assertThrows(ArithmeticException.class, () -> BenchPool.select(overflow.toString(), "", "", ""));
    }
}
