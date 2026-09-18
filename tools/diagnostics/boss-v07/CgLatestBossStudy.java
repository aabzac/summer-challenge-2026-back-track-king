package btk.tools;

import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Read-only explicit latest-boss sample. Compile outside main source tree; no policy dependency. */
public final class CgLatestBossStudy {
    record Input(Path file, long id, String sha256, CgReplay replay, CgBossDefenseStudy.Boss boss) {}
    public static final class BuildTurn {
        int turn;
        int[] incomeBefore;
        int[][] buildOnlyIncome = new int[2][];
        int[] buildOnlyActivatedPairs = new int[2], buildOnlyChangedActivePairs = new int[2];
        int[] threatenedPlacedCells = new int[2], acceptedPaint = new int[2];
        int[][][] placed = new int[2][][];
    }
    record Entry(int ordinal, String userReportedVersion, String file, String sha256,
                 CgBossDefenseStudy.Boss boss, CgPlayerStudy.Game game,
                 CgBossDefenseStudy.Game defense, List<BuildTurn> construction) {}

    public static void main(String[] args) throws Exception {
        if (args.length != 6) throw new IllegalArgumentException(
            "Expected directory expectedTotal latestCount firstId lastId output");
        Path directory = Path.of(args[0]), output = Path.of(args[5]);
        int total = Integer.parseInt(args[1]), count = Integer.parseInt(args[2]);
        long first = Long.parseLong(args[3]), last = Long.parseLong(args[4]);
        if (Files.exists(output)) throw new IllegalArgumentException("Preserve prior report: " + output);
        List<Input> all = new ArrayList<>();
        for (Path file : CgReplay.listReplays(directory)) {
            byte[] bytes = Files.readAllBytes(file);
            var raw = JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            CgReplay replay = CgReplay.parse(raw);
            all.add(new Input(file, replay.gameId, HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes)), replay, CgBossDefenseStudy.identifyBoss(raw)));
        }
        all.sort(Comparator.comparingLong(Input::id));
        if (all.size() != total || count <= 0 || count > total) throw new IllegalArgumentException("Unexpected corpus size " + all.size());
        List<Input> sample = all.subList(total - count, total);
        if (sample.get(0).id != first || sample.get(count - 1).id != last) throw new IllegalArgumentException("Unexpected latest sample bounds");
        List<Entry> games = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        for (Input in : sample) {
            if (!ids.add(in.id)) throw new IllegalArgumentException("Duplicate id " + in.id);
            var game = CgPlayerStudy.analyze(in.replay, 1 - in.boss.player());
            game.file = in.file.toString();
            var defense = CgBossDefenseStudy.analyze(in.file);
            var validation = CgValidate.validate(in.replay, false);
            validation.replay.meta.source = "cg-bot";
            validation.replay.write(CgRender.outputPath(Path.of("replays/rendered/cg"), in.replay));
            games.add(new Entry(games.size()+1, "v07 (user-reported; source hash unavailable)", in.file.toString(), in.sha256,
                in.boss, game, defense, buildStudy(in.replay)));
            System.out.printf("%d %-4s %d-%d turns=%d income=%d/%d paint=%d/%d unused=%d/%d messages=%s%n",
                in.id, game.outcome, game.scores[game.me], game.scores[1-game.me], game.turns,
                game.firstIncome[game.me],game.firstIncome[1-game.me],game.paint[game.me],game.paint[1-game.me],
                game.acceptedCellsNeverEarned[game.me],game.acceptedCellsNeverEarned[1-game.me],defense.messages);
        }
        Map<String,Object> report = new LinkedHashMap<>();
        report.put("schemaVersion",1); report.put("createdAt",Instant.now().toString());
        report.put("method", "Latest explicitly bounded 20 of 50 local canonical boss replays by numeric gameId. "
            + "All validated exactly through btk.core, with raw SHA-256; version v07 is user attribution, not source verification. "
            + "Arrays are P0/P1; me and boss.player identify roles. Turns zero-based. "
            + "PlayerStudy counterfactuals hold recorded SAME-turn opponent actions fixed and reset to actual state after each turn. "
            + "Build-only branches apply accepted placements of one player only, no opponent action or disruptions, and measure core income. "
            + "Defense branches reuse CgBossDefenseStudy: only already threatened ACTIVE pair bypass paint, no inference of intent. "
            + "All observations are descriptive; not independent trials, projected win rates, additive recoverable points or strength tests.");
        report.put("games",games);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output,new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(report));
        System.out.println("report: " + output);
    }

    static List<BuildTurn> buildStudy(CgReplay replay) {
        GameState s = replay.toState(); Pathing pathing = new Pathing(s.size);
        Rules.updateConnections(s,pathing);
        List<BuildTurn> result = new ArrayList<>();
        int[] empty = new int[0];
        for (int t=0;t<replay.turns();t++) {
            var a0 = CgPlayerStudy.effectiveActions(replay,t,0);
            var a1 = CgPlayerStudy.effectiveActions(replay,t,1);
            BuildTurn row = new BuildTurn(); row.turn=t; row.incomeBefore = income(s);
            for (int p=0;p<2;p++) {
                Rules.PlayerMoves moves=new Rules.PlayerMoves();
                Rules.expand(s,p,p==0?a0:a1,pathing,moves,new Rules.TurnResult());
                GameState built=s.copy();
                Rules.applyPlacements(built,p==0?moves.cells:empty,p==0?moves.count:0,
                    p==1?moves.cells:empty,p==1?moves.count:0);
                Rules.updateConnections(built,pathing); row.buildOnlyIncome[p]=income(built);
                row.acceptedPaint[p]=moves.cost; row.placed[p]=new int[moves.count][3];
                for(int i=0;i<moves.count;i++) {
                    int c=moves.cells[i]; row.placed[p][i]=new int[]{s.x(c),s.y(c),s.regionId[c]};
                    if(s.regionInstability[s.regionId[c]]>0) row.threatenedPlacedCells[p]++;
                }
                for(int pair=0;pair<s.pairCount;pair++) {
                    if(!s.pairActive(pair)&&built.pairActive(pair)) row.buildOnlyActivatedPairs[p]++;
                    if(s.pairActive(pair)&&built.pairActive(pair)&&!samePath(s,built,pair)) row.buildOnlyChangedActivePairs[p]++;
                }
            }
            result.add(row); Rules.resolve(s,a0,a1,pathing);
        }
        return result;
    }
    static int[] income(GameState s) { int[] a=new int[2]; Rules.incomePerTurn(s,a); return a; }
    static boolean samePath(GameState a,GameState b,int p) {
        if(a.pairPathLen[p]!=b.pairPathLen[p]) return false;
        for(int i=0;i<a.pairPathLen[p];i++) if(a.pairPath[p][i]!=b.pairPath[p][i]) return false;
        return true;
    }
}
