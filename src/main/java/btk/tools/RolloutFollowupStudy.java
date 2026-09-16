package btk.tools;

import btk.bot.BotFactory;
import btk.bot.current.RolloutBot;
import btk.core.*;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.*;

/** Fixed shared-state coverage diagnostics, separate from strength and process timing. */
public final class RolloutFollowupStudy {
    public static void main(String[] args) throws Exception {
        Path input = Path.of(args[0]), output = Path.of(args[2]);
        if (Files.exists(output)) throw new IllegalArgumentException("Preserve existing report");
        String candidate = args[1] + ",NOISE=0";
        var cohort = JsonParser.parseString(Files.readString(input)).getAsJsonObject();
        List<Map<String,Object>> rows = new ArrayList<>();
        for (var item : cohort.getAsJsonArray("games")) {
            var entry = item.getAsJsonObject();
            if (!entry.get("userReportedVersion").getAsString().equals("v06")) continue;
            var game = entry.getAsJsonObject("game");
            Path file = Path.of(game.get("file").getAsString());
            if (!ReplyStudy.hash(Files.readAllBytes(file)).equals(entry.get("sha256").getAsString()))
                throw new IllegalStateException("Replay changed");
            var replay = CgReplay.load(file);
            if (CgValidate.validate(replay,false).firstDivergenceTurn >= 0)
                throw new IllegalStateException("Replay does not validate");
            GameState s = replay.toState(); Pathing pathing = new Pathing(s.size);
            Rules.updateConnections(s,pathing); int me = game.get("me").getAsInt();
            for (int t = 0; t < Math.min(91,replay.turns()); t++) {
                if (replay.failure(t,0) != null || replay.failure(t,1) != null) break;
                if (t <= 20 && t % 5 == 0 || t > 20 && t % 10 == 0) {
                    String before = ReplyStudy.stateHash(s);
                    var row = new LinkedHashMap<String,Object>();
                    row.put("gameId",replay.gameId); row.put("outcome",game.get("outcome").getAsString());
                    row.put("turn",t); row.put("me",me); row.put("stateSha256",before);
                    // Alternate policy order on successive states; no future actions given to either bot.
                    for (String spec : rows.size()%2 == 0 ? new String[]{"current?NOISE=0",candidate}
                            : new String[]{candidate,"current?NOISE=0"}) {
                        var result = new LinkedHashMap<String,Object>();
                        result.put("nominal",inspect(s,me,spec,t==0?486:36));
                        result.put("ample",inspect(s,me,spec,60_000));
                        row.put(spec.equals(candidate)?"candidate":"baseline",result);
                    }
                    if (!before.equals(ReplyStudy.stateHash(s))) throw new IllegalStateException("Input mutated");
                    rows.add(row);
                }
                Rules.resolve(s,CgPlayerStudy.effectiveActions(replay,t,0),
                        CgPlayerStudy.effectiveActions(replay,t,1),pathing);
            }
        }
        var report = new LinkedHashMap<String,Object>();
        report.put("spec",candidate); report.put("params",BotFactory.configuration(candidate));
        report.put("inputSha256",ReplyStudy.hash(Files.readAllBytes(input)));
        report.put("classSha256",ReplyStudy.hash(Files.readAllBytes(Path.of("build/classes/java/main/btk/bot/current/RolloutBot.class"))));
        report.put("method","Fixed pre-turn sample 0,5,10,15,20,30,40,50,60,70,80,90 from all 14 v06 boss games. "
                + "Fresh NOISE=0 instances; init outside clock; alternating baseline/candidate order. "
                + "Nominal 486/36 ms and ample 60000 ms. No future opponent actions. "
                + "Mechanism diagnostics only; not strength, original arena RNG/timing, or process readiness.");
        report.put("samples",rows);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output,ReplyStudy.JSON.toJson(report),StandardOpenOption.CREATE_NEW);
        System.out.println("Saved " + rows.size() + " shared states to " + output);
    }
    private static Map<String,Object> inspect(GameState s,int me,String spec,long budget) throws Exception {
        var bot = (RolloutBot)BotFactory.create(spec); bot.init(s,me);
        long start = System.nanoTime(); var actions = bot.play(s,me,budget);
        double elapsed = (System.nanoTime()-start)/1e6;
        var tr = new Rules.TurnResult(); Rules.expand(s,me,actions,new Pathing(s.size),tr.moves[me],tr);
        if (tr.moves[me].error != null || !tr.moves[me].skipped.isEmpty()) throw new IllegalStateException("Illegal action");
        int[] counts = (int[])CgDisruptStudy.field(bot,"counts");
        int[] replies = (int[])CgDisruptStudy.field(bot,"replyRoutes");
        int[][][] routes = (int[][][])CgDisruptStudy.field(bot,"routes");
        int[][] lengths = (int[][])CgDisruptStudy.field(bot,"lengths");
        int enemy = 1-me;
        var result = new LinkedHashMap<String,Object>();
        result.put("budgetMs",budget); result.put("elapsedMs",elapsed);
        result.put("completedComparisons",bot.completedComparisons); result.put("routeQueries",bot.routeQueries);
        result.put("simulatedTurns",bot.simulatedTurns); result.put("selectedRoute",bot.selectedRoute);
        result.put("action",Action.format(actions)); result.put("enemyRouteCount",counts[enemy]);
        if (counts[enemy]>=2) {
            int second = replies==null?1:replies[1];
            result.put("secondReplyRoute",second);
            result.put("sameProject",CgDisruptStudy.call(bot,"sameProject",new Class<?>[]{int.class,int.class,int.class},enemy,0,second));
            result.put("sameCorridor",CgDisruptStudy.call(bot,"sameCorridor",new Class<?>[]{int.class,int.class,int.class},enemy,0,second));
            result.put("firstReply",Arrays.copyOf(routes[enemy][0],lengths[enemy][0]));
            result.put("secondReply",Arrays.copyOf(routes[enemy][second],lengths[enemy][second]));
        }
        for (String name : new String[]{"replanCalls","replanChanges","extraTargetComparisons","selectedExtraTarget"}) {
            try { result.put(name,RolloutBot.class.getField(name).get(bot)); }
            catch (NoSuchFieldException ignored) { /* Added when its independent variant is implemented. */ }
        }
        return result;
    }
}
