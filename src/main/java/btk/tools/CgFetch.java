package btk.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Downloads CodinGame replays (no login needed) into {@code replays/cg/<gameId>.json}.
 *
 * <pre>
 * gradlew cgFetch -Ptop=20 -Plimit=10     # last 10 finished games of each top-20 player
 * gradlew cgFetch -Pplayer=SomePseudo     # by leaderboard pseudo
 * gradlew cgFetch -Pagent=6727937         # by agent id
 * gradlew cgFetch -Pgame=903337834        # one known arena or IDE game id
 * </pre>
 *
 * Also prints a leaderboard snapshot (league sizes, languages) for the DEVLOG.
 */
public final class CgFetch {

    static final String BASE = "https://www.codingame.com/services/";
    static final String CHALLENGE = "summer-challenge-2026-back-track-king";
    static final Path OUT_DIR = Path.of("replays/cg");

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    public static void main(String[] args) throws Exception {
        String top = arg(args, 0), agent = arg(args, 1), player = arg(args, 2), gameIds = arg(args, 4);
        int limit = Integer.parseInt(arg(args, 3).isEmpty() ? "10" : arg(args, 3));
        CgFetch f = new CgFetch();
        if (!gameIds.isEmpty()) {
            f.fetchGames(gameIds);
            return;
        }
        JsonObject lb = f.leaderboard();
        f.printLeaderboardSummary(lb);
        List<Integer> agents = new ArrayList<>();
        if (!top.isEmpty()) {
            int n = Integer.parseInt(top);
            JsonArray users = lb.getAsJsonArray("users");
            for (int i = 0; i < Math.min(n, users.size()); i++) {
                JsonObject u = users.get(i).getAsJsonObject();
                if (u.has("agentId")) agents.add(u.get("agentId").getAsInt());
            }
        }
        if (!agent.isEmpty()) agents.add(Integer.parseInt(agent));
        if (!player.isEmpty()) {
            for (JsonElement e : lb.getAsJsonArray("users")) {
                JsonObject u = e.getAsJsonObject();
                if (u.has("pseudo") && u.get("pseudo").getAsString().equalsIgnoreCase(player)) {
                    agents.add(u.get("agentId").getAsInt());
                    System.out.printf("player %s: rank %d score %.2f agent %d%n", player,
                            u.get("rank").getAsInt(), u.get("score").getAsDouble(), u.get("agentId").getAsInt());
                }
            }
        }
        if (agents.isEmpty()) {
            System.out.println("Nothing to fetch (use -Ptop=N, -Pagent=ID or -Pplayer=pseudo).");
            return;
        }
        Files.createDirectories(OUT_DIR);
        int saved = 0, skipped = 0;
        for (int a : agents) {
            JsonArray battles = f.lastBattles(a);
            int taken = 0;
            for (JsonElement e : battles) {
                if (taken >= limit) break;
                JsonObject b = e.getAsJsonObject();
                if (!b.has("done") || !b.get("done").getAsBoolean()) continue;
                long gameId = b.get("gameId").getAsLong();
                Path file = OUT_DIR.resolve(gameId + ".json");
                taken++;
                if (Files.exists(file)) {
                    skipped++;
                    continue;
                }
                JsonObject game = f.gameResult(gameId);
                // lastBattles.players[].position is finishing order, not the in-game player index.
                // Use gameResult.agents[].index so P0/P1 remain correct when P1 wins.
                JsonObject extra = nicknamesByGameIndex(game, b);
                if (extra.size() > 0) game.add("btkNicknames", extra);
                Files.writeString(file, game.toString());
                saved++;
                System.out.printf("saved %s (%s vs %s)%n", file.getFileName(),
                        extra.has("0") ? extra.get("0").getAsString() : "?",
                        extra.has("1") ? extra.get("1").getAsString() : "?");
            }
        }
        System.out.printf("done: %d saved, %d already present%n", saved, skipped);
    }

    private void fetchGames(String ids) throws IOException, InterruptedException {
        Files.createDirectories(OUT_DIR);
        int saved = 0, skipped = 0;
        for (String token : ids.split("[,\\s]+")) {
            if (token.isBlank()) continue;
            long gameId = Long.parseLong(token);
            Path file = OUT_DIR.resolve(gameId + ".json");
            if (Files.exists(file)) {
                skipped++;
                System.out.printf("already present %s%n", file.getFileName());
                continue;
            }
            JsonObject game = gameResult(gameId);
            Files.writeString(file, game.toString());
            saved++;
            System.out.printf("saved %s%n", file.getFileName());
        }
        System.out.printf("done: %d saved, %d already present%n", saved, skipped);
    }

    /**
     * The live arena is the multiplayer puzzle that replaced the contest; the challenge leaderboard
     * is frozen at the contest's end (it kept reporting agent 6755892 for aabzac while the puzzle
     * entry was already agent 6807052 on 2026-10-22). Fall back to the challenge leaderboard only
     * if the puzzle one is unavailable.
     */
    JsonObject leaderboard() throws IOException, InterruptedException {
        String body = "[\"" + CHALLENGE + "\", null, \"global\", {\"active\":false,\"column\":\"\",\"filter\":\"\"}]";
        try {
            JsonElement puzzle = post("Leaderboards/getFilteredPuzzleLeaderboard", body);
            if (puzzle.isJsonObject() && puzzle.getAsJsonObject().has("users")) return puzzle.getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            System.out.println("puzzle leaderboard unavailable (" + e.getMessage() + "); using the frozen challenge leaderboard");
        }
        return post("Leaderboards/getFilteredChallengeLeaderboard", body).getAsJsonObject();
    }

    JsonArray lastBattles(int agentId) throws IOException, InterruptedException {
        return post("gamesPlayersRanking/findLastBattlesByAgentId", "[" + agentId + ", null]").getAsJsonArray();
    }

    JsonObject gameResult(long gameId) throws IOException, InterruptedException {
        return post("gameResult/findByGameId", "[" + gameId + ", null]").getAsJsonObject();
    }

    static JsonObject nicknamesByGameIndex(JsonObject game, JsonObject battle) {
        JsonObject names = new JsonObject();
        if (!game.has("agents") || !game.get("agents").isJsonArray()) return names;
        JsonArray battlePlayers = battle.has("players") && battle.get("players").isJsonArray()
                ? battle.getAsJsonArray("players") : new JsonArray();
        for (JsonElement ae : game.getAsJsonArray("agents")) {
            JsonObject agent = ae.getAsJsonObject();
            if (!agent.has("index")) continue;
            int index = agent.get("index").getAsInt();
            JsonObject codingamer = agent.has("codingamer") && agent.get("codingamer").isJsonObject()
                    ? agent.getAsJsonObject("codingamer") : null;
            if (codingamer != null && codingamer.has("pseudo")) {
                names.addProperty(String.valueOf(index), codingamer.get("pseudo").getAsString());
                continue;
            }
            if (codingamer == null || !codingamer.has("userId")) continue;
            long userId = codingamer.get("userId").getAsLong();
            for (JsonElement pe : battlePlayers) {
                JsonObject player = pe.getAsJsonObject();
                if (player.has("userId") && player.get("userId").getAsLong() == userId
                        && player.has("nickname")) {
                    names.addProperty(String.valueOf(index), player.get("nickname").getAsString());
                    break;
                }
            }
        }
        return names;
    }

    private JsonElement post(String service, String body) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + service))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IOException(service + " -> HTTP " + resp.statusCode() + ": " + resp.body());
        }
        return JsonParser.parseString(resp.body());
    }

    private void printLeaderboardSummary(JsonObject lb) {
        JsonArray users = lb.getAsJsonArray("users");
        System.out.printf("Leaderboard: %d players%n", users.size());
        java.util.Map<String, Integer> langs = new java.util.TreeMap<>();
        java.util.Map<Integer, Integer> leagues = new java.util.TreeMap<>();
        for (JsonElement e : users) {
            JsonObject u = e.getAsJsonObject();
            String lang = u.has("programmingLanguage") ? u.get("programmingLanguage").getAsString() : "?";
            langs.merge(lang, 1, Integer::sum);
            if (u.has("league")) leagues.merge(u.getAsJsonObject("league").get("divisionIndex").getAsInt(), 1, Integer::sum);
        }
        System.out.println("  leagues (divisionIndex -> players): " + leagues);
        System.out.println("  languages: " + langs);
        for (int i = 0; i < Math.min(10, users.size()); i++) {
            JsonObject u = users.get(i).getAsJsonObject();
            System.out.printf("  #%d %-20s %.2f %s%n", u.get("rank").getAsInt(), u.get("pseudo").getAsString(),
                    u.get("score").getAsDouble(), u.get("programmingLanguage").getAsString());
        }
    }

    private static String arg(String[] a, int i) {
        return a.length > i && a[i] != null ? a[i].trim() : "";
    }
}
