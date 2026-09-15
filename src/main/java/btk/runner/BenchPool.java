package btk.runner;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Explicit reproducible pool selection; a confirmation label alone does not prove independence. */
public record BenchPool(String id, String purpose, long baseSeed, int games, String sha256) {
    public record Exclusions(Set<String> hashes, Map<String, String> reportSha256) {}

    public static BenchPool select(String file, String games, String seed, String maps) throws Exception {
        if (file.isBlank()) return new BenchPool("custom", "development",
                seed.isBlank() ? 1000 : Long.parseLong(seed), games.isBlank() ? 200 : Integer.parseInt(games), "");
        if (!maps.isBlank() || !seed.isBlank()) throw new IllegalArgumentException("pool cannot be combined with maps/seed");
        byte[] bytes = Files.readAllBytes(Path.of(file));
        JsonObject json = JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        if (json.get("schemaVersion").getAsInt() != 1 || !json.get("generator").getAsString().equals("cg-sha1prng")) {
            throw new IllegalArgumentException("Unsupported pool manifest");
        }
        String id = json.get("id").getAsString(), purpose = json.get("purpose").getAsString();
        long start = json.get("seedStart").getAsBigDecimal().longValueExact();
        int pairs = json.get("pairs").getAsBigDecimal().intValueExact();
        int count = Math.multiplyExact(pairs, 2);
        long end = Math.addExact(start, (long) pairs - 1);
        if (id.isBlank() || !(purpose.equals("development") || purpose.equals("confirmation"))
                || pairs < 1 || start <= 0 && end >= 0) throw new IllegalArgumentException("Invalid pool manifest");
        if (!games.isBlank() && Integer.parseInt(games) != count) {
            throw new IllegalArgumentException("Use the complete pool: " + count + " games");
        }
        return new BenchPool(id, purpose, start, count, digest(bytes));
    }

    public static Exclusions exclusions(String files, String purpose) throws Exception {
        Set<String> hashes = new HashSet<>(); Map<String, String> sources = new LinkedHashMap<>();
        if (!files.isBlank()) for (String name : files.split(",", -1)) {
            Path path = Path.of(name.trim());
            byte[] bytes = Files.readAllBytes(path);
            JsonObject json = JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            if (!json.has("gamesPerMap") || !json.get("gamesPerMap").isJsonObject()
                    || json.getAsJsonObject("gamesPerMap").size() == 0) {
                throw new IllegalArgumentException("Expected nonempty benchmark report or suite index: " + path);
            }
            for (var entry : json.getAsJsonObject("gamesPerMap").entrySet()) {
                if (!entry.getKey().matches("[0-9a-f]{64}") || entry.getValue().getAsBigDecimal().intValueExact() <= 0) {
                    throw new IllegalArgumentException("Invalid map identity in " + path);
                }
                hashes.add(entry.getKey());
            }
            sources.put(path.toString(), digest(bytes));
        }
        if (purpose.equals("confirmation") && sources.isEmpty()) {
            throw new IllegalArgumentException("Confirmation requires -PexcludeReports=<development reports or suite index>");
        }
        return new Exclusions(Set.copyOf(hashes), sources);
    }

    public void annotate(BenchmarkRunner.Stats stats, Exclusions exclusions) {
        stats.poolId = id; stats.poolPurpose = purpose; stats.poolSha256 = sha256;
        stats.excludedReports = new LinkedHashMap<>(exclusions.reportSha256);
    }

    static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
