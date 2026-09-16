package btk.tools;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;

/** Explicit chronological boss cohorts, reusing the validated player-study core diagnostics. */
public final class CgBossLossStudy {
    record Input(Path file, String sha256, CgReplay replay, CgBossDefenseStudy.Boss boss) {}
    record Entry(int ordinal, String userReportedVersion, String sha256,
                 CgBossDefenseStudy.Boss boss, CgPlayerStudy.Game game) {}

    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("Expected: directory beforeCount totalCount output");
        Path input = Path.of(args[0]), output = Path.of(args[3]);
        int before = Integer.parseInt(args[1]), total = Integer.parseInt(args[2]);
        if (before <= 0 || before >= total) throw new IllegalArgumentException("Both cohorts must be nonempty");
        if (Files.exists(output)) throw new IllegalArgumentException("Preserve existing report: " + output);
        List<Input> sample = new ArrayList<>();
        for (Path file : CgReplay.listReplays(input)) {
            byte[] bytes = Files.readAllBytes(file);
            var raw = JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var replay = CgReplay.parse(raw);
            sample.add(new Input(file, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                    replay, CgBossDefenseStudy.identifyBoss(raw)));
        }
        if (sample.size() != total) throw new IllegalArgumentException("Expected " + total + " files, found " + sample.size());
        sample.sort(Comparator.comparingLong(s -> s.replay.gameId));
        List<Entry> games = new ArrayList<>();
        long last = -1;
        for (Input item : sample) {
            if (item.replay.gameId <= last) throw new IllegalArgumentException("Duplicate/invalid game id");
            last = item.replay.gameId;
            int ordinal = games.size() + 1;
            var game = CgPlayerStudy.analyze(item.replay, 1 - item.boss.player());
            game.file = item.file.toString();
            game.names[item.boss.player()] = item.boss.name();
            String version = ordinal <= before ? "v05" : "v06";
            games.add(new Entry(ordinal, version, item.sha256, item.boss, game));
            System.out.printf("%02d %s %d %s %d-%d %d turns failures=%s/%s%n", ordinal, version,
                    game.gameId, game.outcome, game.scores[game.me], game.scores[1-game.me], game.turns,
                    game.failures[game.me], game.failures[1-game.me]);
        }
        var report = new LinkedHashMap<String,Object>();
        report.put("schemaVersion", 1);
        report.put("createdAt", Instant.now().toString());
        report.put("input", input.toString());
        report.put("method", "All explicitly counted files sorted by gameId. Cohort labels v05/v06 are user-reported, "
                + "not independently verified source hashes. Different unpaired maps; descriptive, not a controlled strength test. "
                + "Boss slot comes from agents.index/arenaboss. Every replay must validate exactly. "
                + "Player-study counterfactuals hold the recorded SAME-turn opponent action fixed and reset next turn; "
                + "one-turn oracle diagnostics, not legal predictions or additive recoverable final points. Failure turns excluded from counterfactuals. "
                + "All turn indices zero-based. Raw input SHA-256 retained per game.");
        report.put("games", games);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(report));
        System.out.println("report: " + output);
    }
}
