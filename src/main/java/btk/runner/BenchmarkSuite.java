package btk.runner;

import btk.bot.BotFactory;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Sequential matched opponent panel. No combined opponent win rate or aggregate promotion gate. */
public final class BenchmarkSuite {
    public static void main(String[] args) throws Exception {
        String candidate = args[0]; BotFactory.parameters(candidate);
        BenchPool pool = BenchPool.select(args[1], "", "", "");
        byte[] panelBytes = Files.readAllBytes(Path.of(args[2]));
        var panel = JsonParser.parseString(new String(panelBytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        if (panel.get("schemaVersion").getAsInt() != 1) throw new IllegalArgumentException("Unsupported opponent panel");
        List<String> opponents = new ArrayList<>(); var unique = new HashSet<String>();
        for (var entry : panel.getAsJsonArray("opponents")) {
            String spec = entry.getAsString(); BotFactory.parameters(spec);
            if (!unique.add(spec)) throw new IllegalArgumentException("Duplicate opponent: " + spec);
            if (!spec.equals(candidate)) opponents.add(spec);
        }
        if (opponents.isEmpty()) throw new IllegalArgumentException("Empty panel after excluding exact self-spec");
        int threads = Integer.parseInt(args[3]);
        Path out = Path.of(args[4]);
        if (Files.exists(out)) throw new IllegalArgumentException("Choose a fresh suite output directory: " + out);
        var exclusions = BenchPool.exclusions(args[5], pool.purpose());
        List<Map<String, Object>> reports = new ArrayList<>(); Map<String, Integer> gamesPerMap = new LinkedHashMap<>();
        Files.createDirectories(out);
        for (int i = 0; i < opponents.size(); i++) {
            String opponent = opponents.get(i);
            System.out.println("Suite opponent " + (i + 1) + "/" + opponents.size() + ": " + opponent);
            var stats = BenchmarkRunner.run(candidate, opponent, pool.games(), new Maps(""), threads,
                    pool.baseSeed(), TimingProfile.CG, BotFactory::create, exclusions.hashes());
            pool.annotate(stats, exclusions);
            Path file = out.resolve("opponent-" + (i + 1) + ".json"); stats.write(file);
            System.out.println(stats.report());
            stats.gamesPerMap.forEach((hash, n) -> gamesPerMap.merge(hash, n, Integer::sum));
            reports.add(Map.of("opponent", opponent, "file", file.getFileName().toString(),
                    "sha256", BenchPool.digest(Files.readAllBytes(file)), "winRate", stats.rateA(),
                    "errors", stats.errorsA + stats.errorsB));
            // Save completed evidence even when a later match-up fails/interruption occurs.
            Files.writeString(out.resolve("index.json"), new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                    "schemaVersion", 1, "candidate", candidate, "pool", pool, "opponentPanelSha256", BenchPool.digest(panelBytes),
                    "reports", reports, "gamesPerMap", gamesPerMap, "complete", i + 1 == opponents.size(),
                    "method", "Separate matched opponent reports; no aggregate promotion gate. Exact self-spec omitted.")));
            if (stats.errorsA + stats.errorsB > 0) throw new IllegalStateException("Suite bot errors; see " + file);
        }
        System.out.println("Suite index: " + out.resolve("index.json"));
    }
}
