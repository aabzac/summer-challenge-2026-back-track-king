package btk.tools;

import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

/** Read-only replay diagnosis: does accepted building make an observed threatened route safer? */
public final class CgBossDefenseStudy {
    public record Boss(int player, int agentId, String name) {}
    public record PairCost(int pair, int from, int to, int bossTracks, int before, int after) {
        boolean improves() { return before > 0 && after >= 0 && after < before; }
    }
    public static final class Threat {
        public int region, instability, bossTracks, placementsInside;
        public boolean previousOpponentTarget, firstObservableHit;
        public List<PairCost> pairs = new ArrayList<>();
    }
    public static final class Turn {
        public int turn, priorOpponentTarget = -1, bossTarget = -1;
        public String bossCommand, opponentCommand, message;
        public int[][] placed;
        public List<Threat> threats = new ArrayList<>();
    }
    public static final class Game {
        public String file, sha256, seed;
        public long gameId;
        public Boss boss;
        public int turns, winner;
        public int[] scores, cells = new int[2], paint = new int[2], disruptTurns = new int[2], placementTurns = new int[2];
        public int repeatOpportunities, repeatedTargets;
        public Map<String, Integer> messages = new TreeMap<>();
        public Map<String, Integer> targetKinds = new TreeMap<>();
        public List<Turn> trace = new ArrayList<>();
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args[0].isBlank()) throw new IllegalArgumentException("cgBossStudy needs explicit comma-separated -Ppaths and -Pout");
        Path output = Path.of(args[1]);
        if (Files.exists(output)) throw new IllegalArgumentException("Preserve existing report; choose a new output: " + output);
        List<Game> games = new ArrayList<>();
        for (String file : args[0].split(",")) games.add(analyze(Path.of(file)));
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schemaVersion", 2);
        report.put("method", "Exact validation; zero-based pre-turn observations. Boss identified by agents.index and arenaboss. "
                + "Only already unstable, uninked regions with boss tracks are probed. Active town pairs are deduplicated undirected "
                + "after checking each direction's canonical route for exposure. "
                + "Before/after costs avoid the whole region and apply ONLY boss accepted placements, no opponent action or ink. "
                + "-1 means no route. Cost reduction is compatible with defense, not proof of intent or match-win benefit. "
                + "Prior opponent hit is observable; same-turn opponent output is retained only as recorded context. "
                + "Unfinished routes are outside the probe. Failure turns are rejected, not interpreted strategically.");
        report.put("games", games);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(report));
        for (Game g : games) System.out.printf("%d %s P%d scores=%s turns=%d messages=%s%n",
                g.gameId, g.boss.name, g.boss.player, Arrays.toString(g.scores), g.turns, g.messages);
        System.out.println("report: " + output);
    }

    static Boss identifyBoss(JsonObject raw) {
        Boss boss = null;
        for (var entry : raw.getAsJsonArray("agents")) {
            JsonObject a = entry.getAsJsonObject();
            boolean marked = a.has("arenaboss") && a.get("arenaboss").isJsonObject();
            int id = a.get("agentId").getAsInt();
            if (!marked && id != -2) continue;
            int player = a.get("index").getAsInt();
            if (boss != null || player < 0 || player > 1) throw new IllegalArgumentException("Ambiguous/invalid boss identity");
            String name = marked && a.getAsJsonObject("arenaboss").has("nickname")
                    ? a.getAsJsonObject("arenaboss").get("nickname").getAsString() : "Boss";
            boss = new Boss(player, id, name);
        }
        if (boss == null) throw new IllegalArgumentException("No explicitly identified boss");
        return boss;
    }

    static Game analyze(Path file) throws Exception {
        file = ReplayPaths.resolve(file);
        String json = Files.readString(file); JsonObject raw = JsonParser.parseString(json).getAsJsonObject();
        CgReplay replay = CgReplay.parse(raw);
        CgValidate.Outcome validation = CgValidate.validate(replay, false);
        if (validation.firstDivergenceTurn >= 0) throw new IllegalStateException("Replay diverges: " + file + " " + validation.reason);
        Game g = new Game(); g.file = file.toString(); g.gameId = replay.gameId; g.seed = replay.seed;
        g.sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        g.boss = identifyBoss(raw); g.scores = validation.finalLocal; g.winner = validation.replay.meta.winner;
        int boss = g.boss.player, enemy = 1 - boss, previousEnemy = -1, previousEnemyInstability = -1, previousBoss = -1;
        GameState s = replay.toState(); Pathing paths = new Pathing(s.size); Rules.updateConnections(s, paths);
        for (int t = 0; t < replay.turns(); t++) {
            if (replay.failure(t, 0) != null || replay.failure(t, 1) != null) throw new IllegalArgumentException("Failure turn outside study scope: " + file + ":" + t);
            var a0 = CgPlayerStudy.effectiveActions(replay, t, 0); var a1 = CgPlayerStudy.effectiveActions(replay, t, 1);
            Rules.PlayerMoves moves = new Rules.PlayerMoves();
            Rules.expand(s, boss, boss == 0 ? a0 : a1, paths, moves, new Rules.TurnResult());
            Turn row = new Turn(); row.turn = t; row.priorOpponentTarget = previousEnemy;
            row.bossCommand = replay.turnStdout.get(t)[boss]; row.opponentCommand = replay.turnStdout.get(t)[enemy];
            row.message = moves.message; row.placed = new int[moves.count][2];
            for (int i = 0; i < moves.count; i++) row.placed[i] = new int[]{s.x(moves.cells[i]), s.y(moves.cells[i])};
            row.bossTarget = moves.disrupts.isEmpty() ? -1 : moves.disrupts.get(0);
            g.messages.merge(moves.message == null ? "none" : moves.message.split("\\s+")[0], 1, Integer::sum);
            if (row.bossTarget >= 0) {
                int mine = tracks(s, row.bossTarget, boss), theirs = tracks(s, row.bossTarget, enemy);
                String kind = mine == 0 ? theirs == 0 ? "empty" : "opponentOnly" : theirs == 0 ? "ownOnly" : "mixed";
                g.targetKinds.merge(kind, 1, Integer::sum);
                if (previousBoss >= 0 && !s.regionInked[previousBoss] && !s.regionHasTown(previousBoss)) {
                    g.repeatOpportunities++; if (previousBoss == row.bossTarget) g.repeatedTargets++;
                }
            }
            for (int r = 0; r < s.regionCount; r++) {
                int own = tracks(s, r, boss);
                if (own == 0 || s.regionInstability[r] == 0 || s.regionInked[r] || s.regionHasTown(r)) continue;
                Threat threat = new Threat(); threat.region = r; threat.instability = s.regionInstability[r]; threat.bossTracks = own;
                threat.previousOpponentTarget = r == previousEnemy;
                threat.firstObservableHit = threat.previousOpponentTarget && previousEnemyInstability == 0;
                for (int i = 0; i < moves.count; i++) if (s.regionId[moves.cells[i]] == r) threat.placementsInside++;
                threat.pairs = probe(s, boss, r, moves);
                row.threats.add(threat);
            }
            g.trace.add(row);
            Rules.PlayerMoves enemyMoves = new Rules.PlayerMoves();
            Rules.expand(s, enemy, enemy == 0 ? a0 : a1, paths, enemyMoves, new Rules.TurnResult());
            previousEnemy = enemyMoves.disrupts.isEmpty() ? -1 : enemyMoves.disrupts.get(0);
            previousEnemyInstability = previousEnemy < 0 ? -1 : s.regionInstability[previousEnemy];
            previousBoss = row.bossTarget;
            var result = Rules.resolve(s, a0, a1, paths);
            for (int p = 0; p < 2; p++) {
                if (result.hasError(p)) throw new IllegalArgumentException("Invalid player output");
                g.cells[p] += result.moves[p].count; g.paint[p] += result.moves[p].cost;
                if (result.moves[p].count > 0) g.placementTurns[p]++;
                if (!result.moves[p].disrupts.isEmpty()) g.disruptTurns[p]++;
            }
            g.turns++;
        }
        if (!Arrays.equals(s.score, g.scores)) throw new IllegalStateException("Replay score drift");
        return g;
    }

    /** Shared core does all pathing/placement; isolated known-board build probe, no score forecast. */
    static List<PairCost> probe(GameState s, int boss, int region, Rules.PlayerMoves moves) {
        List<PairCost> result = new ArrayList<>();
        GameState built = s.copy(); int[] empty = new int[0];
        Rules.applyPlacements(built, boss == 0 ? moves.cells : empty, boss == 0 ? moves.count : 0,
                boss == 1 ? moves.cells : empty, boss == 1 ? moves.count : 0);
        boolean[] blocked = new boolean[s.size]; int[] route = new int[s.size]; Pathing paths = new Pathing(s.size);
        for (int c = 0; c < s.size; c++) blocked[c] = s.regionId[c] == region;
        Set<Long> seen = new HashSet<>();
        for (int p = 0; p < s.pairCount; p++) if (s.pairActive(p)) {
            int a = s.pairFrom[p], b = s.pairTo[p];
            long key = ((long) Math.min(a, b) << 32) | Math.max(a, b);
            if (seen.contains(key)) continue;
            int own = 0; boolean crosses = false;
            for (int i = 0; i < s.pairPathLen[p]; i++) {
                int c = s.pairPath[p][i]; own += s.owner[c] == boss ? 1 : 0;
                if (s.regionId[c] == region) crosses = true;
            }
            if (!crosses || own == 0) continue;
            // Equal-length canonical routes may differ by direction: check exposure before deduplication.
            seen.add(key);
            int from = s.townCell[a], to = s.townCell[b];
            int before = paths.cheapestPath(s, from, to, route, blocked) < 0 ? -1 : paths.lastCost(to);
            int after = paths.cheapestPath(built, from, to, route, blocked) < 0 ? -1 : paths.lastCost(to);
            result.add(new PairCost(p, a, b, own, before, after));
        }
        return result;
    }

    private static int tracks(GameState s, int region, int player) {
        int count = 0;
        for (int c = 0; c < s.size; c++) if (s.regionId[c] == region && s.owner[c] == player) count++;
        return count;
    }
}
