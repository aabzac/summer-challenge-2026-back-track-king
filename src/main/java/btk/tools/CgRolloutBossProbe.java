package btk.tools;

import btk.bot.Bot;
import btk.bot.BotFactory;
import btk.bot.Timer;
import btk.bot.v06.RolloutBotV06;
import btk.core.*;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.*;

/** Diagnostic only: known-state route coverage and first-turn opponent-model coverage. */
public final class CgRolloutBossProbe {
    record Route(int pair, int paint, int ownIncomeGain, int incomeDifferenceGain) {}
    record Prediction(int route, int[] cells, int target) {}

    public static void main(String[] args) throws Exception {
        Path source = Path.of(args[0]), out = Path.of(args[1]);
        if (Files.exists(out)) throw new IllegalArgumentException("Preserve existing report: " + out);
        var cohort = JsonParser.parseString(Files.readString(source)).getAsJsonObject();
        List<Map<String,Object>> samples = new ArrayList<>();
        String spec = "v06?NOISE=0";
        for (var entry : cohort.getAsJsonArray("games")) {
            var e = entry.getAsJsonObject();
            if (!e.get("userReportedVersion").getAsString().equals("v06")) continue;
            var game = e.getAsJsonObject("game");
            Path file = ReplayPaths.resolve(Path.of(game.get("file").getAsString()));
            if (!ReplyStudy.hash(Files.readAllBytes(file)).equals(e.get("sha256").getAsString()))
                throw new IllegalStateException("Input changed: " + file);
            var replay = CgReplay.load(file);
            if (CgValidate.validate(replay, false).firstDivergenceTurn >= 0) throw new IllegalStateException("Invalid replay");
            int me = game.get("me").getAsInt(), enemy = 1-me, priorTarget = -1;
            GameState state = replay.toState(); Pathing paths = new Pathing(state.size);
            Rules.updateConnections(state, paths);
            for (int t=0; t<replay.turns(); t++) {
                if (replay.failure(t,0)!=null || replay.failure(t,1)!=null) break;
                String hash = ReplyStudy.stateHash(state);
                var a0=CgPlayerStudy.effectiveActions(replay,t,0);
                var a1=CgPlayerStudy.effectiveActions(replay,t,1);
                var accepted = new Rules.TurnResult();
                Rules.expand(state,0,a0,paths,accepted.moves[0],accepted);
                Rules.expand(state,1,a1,paths,accepted.moves[1],accepted);
                var mine=accepted.moves[me]; var boss=accepted.moves[enemy];
                int actualTarget=boss.disrupts.isEmpty()?-1:boss.disrupts.get(0);
                // Fresh, noise-free, ample-time policy: mechanisms, not a reconstruction of CG timing/RNG.
                var bot=(RolloutBotV06)BotFactory.create(spec); bot.init(state.copy(),me);
                var chosen=bot.play(state.copy(),me,60_000);
                int[] counts=((int[])CgDisruptStudy.field(bot,"counts")).clone();
                int[][] roots=(int[][])CgDisruptStudy.field(bot,"roots");
                int[] rootCounts=(int[])CgDisruptStudy.field(bot,"rootCounts");
                int[][][] routes=(int[][][])CgDisruptStudy.field(bot,"routes");
                int[][] lengths=(int[][])CgDisruptStudy.field(bot,"lengths");
                int[] ours=sorted(mine.cells,mine.count);
                boolean covered=false;
                for(int i=0;i<=counts[me];i++) if(Arrays.equals(ours,sorted(roots[i],rootCounts[i]))) covered=true;
                List<Prediction> predictions=new ArrayList<>();
                int replies=Math.max(1,Math.min(2,counts[enemy]));
                for(int reply=0;reply<replies;reply++) {
                    int[] cells=new int[GameState.PAINT_PER_TURN];
                    int n=(int)CgDisruptStudy.call(bot,"fillBuild",new Class<?>[]{GameState.class,int.class,int.class,int[].class},state,enemy,reply,cells);
                    GameState built=state.copy();
                    Rules.applyPlacements(built,enemy==0?cells:new int[0],enemy==0?n:0,enemy==1?cells:new int[0],enemy==1?n:0);
                    Rules.updateConnections(built,new Pathing(state.size));
                    int target=target(bot,built,enemy);
                    predictions.add(new Prediction(reply,sorted(cells,n),target));
                }
                int observedPrediction=target(bot,state,enemy);
                int[] inc=new int[2]; Rules.incomePerTurn(state,inc);
                var cheapest=cheapestUsefulRoute(state,me);
                int selectedPaint=bot.selectedRoute<0?-1:paint(state,routes[me][bot.selectedRoute],lengths[me][bot.selectedRoute]);
                int minRetained=-1;
                for(int i=0;i<counts[me];i++) { int cost=paint(state,routes[me][i],lengths[me][i]); if(minRetained<0 || cost<minRetained) minRetained=cost; }
                List<CgBossDefenseStudy.PairCost> priorThreat=priorTarget>=0 && !state.regionInked[priorTarget]
                        ? CgBossDefenseStudy.probe(state,me,priorTarget,mine):List.of();
                var row=new LinkedHashMap<String,Object>();
                row.put("gameId",replay.gameId); row.put("outcome",game.get("outcome").getAsString());
                row.put("turn",t); row.put("me",me); row.put("stateSha256",hash); row.put("income",inc);
                row.put("actualOwnCells",ours); row.put("actualBossCells",sorted(boss.cells,boss.count));
                row.put("actualBossTarget",actualTarget); row.put("priorBossTarget",priorTarget);
                row.put("priorTargetStillLegal",priorTarget>=0 && !state.regionInked[priorTarget] && !state.regionHasTown(priorTarget));
                row.put("priorTargetInstability",priorTarget<0?-1:state.regionInstability[priorTarget]);
                row.put("priorTargetDefense",priorThreat);
                row.put("rootPortfolioCoversRecordedBuild",covered); row.put("probeAction",Action.format(chosen));
                row.put("routeCounts",counts); row.put("routeQueries",bot.routeQueries);
                row.put("completedComparisons",bot.completedComparisons); row.put("simulatedTurns",bot.simulatedTurns);
                row.put("selectedRoute",bot.selectedRoute); row.put("selectedRoutePaint",selectedPaint);
                row.put("minRetainedRoutePaint",minRetained); row.put("cheapestUsefulRoute",cheapest);
                row.put("knownBoardPredictedBossTarget",observedPrediction); row.put("predictions",predictions);
                samples.add(row);
                if(!hash.equals(ReplyStudy.stateHash(state))) throw new IllegalStateException("Probe mutated input");
                Rules.resolve(state,a0,a1,paths); priorTarget=actualTarget;
            }
            System.out.println(replay.gameId+" probed "+state.turn+" recorded states");
        }
        var report=new LinkedHashMap<String,Object>();
        report.put("spec",spec); report.put("params",BotFactory.configuration(spec));
        report.put("inputSha256",ReplyStudy.hash(Files.readAllBytes(source)));
        report.put("policyClassSha256",ReplyStudy.hash(Files.readAllBytes(Path.of("build/classes/java/main/btk/bot/v06/RolloutBotV06.class"))));
        report.put("method","All nonfailure recorded pre-turn states in the user-labelled v06 cohort. Fresh noise-free frozen v06, "
                +"60000-ms allowance per state; not original CG choices, RNG history, work coverage or timing. "
                +"Two first-turn opponent scenarios use actual frozen fillBuild/chooseTarget on known state plus only their own predicted placements. "
                +"Actual same-turn boss output is used ONLY to score coverage afterward, never fed into the policy. "
                +"Prior-target persistence uses only the previous accepted hit while it remains legal. "
                +"Cheapest useful route is an optimistic completed-route paint cost from core cheapestPath, with positive own and net income gain; "
                +"no future enemy action, disruption, or per-turn budget, and no guarantee of survival. "
                +"Defense probe uses only accepted own placements on the known prior-target state. "
                +"All states are reset to the recorded trajectory; differences are mechanism diagnostics, not strength evidence.");
        report.put("samples",samples);
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out,new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(report),StandardOpenOption.CREATE_NEW);
    }

    private static int target(Bot bot,GameState state,int player) throws Exception {
        ((Timer)CgDisruptStudy.field(bot,"timer")).start(60_000);
        return (int)CgDisruptStudy.call(bot,"chooseTarget",new Class<?>[]{GameState.class,int.class},state,player);
    }
    private static int[] sorted(int[] cells,int n) { int[] result=Arrays.copyOf(cells,n); Arrays.sort(result); return result; }
    private static int paint(GameState s,int[] route,int n) {
        int sum=0; for(int i=0;i<n;i++) if(s.canBuild(route[i])) sum+=s.placeCost(route[i]); return sum;
    }
    static Route cheapestUsefulRoute(GameState s,int me) {
        Pathing pathing=new Pathing(s.size); int[] route=new int[s.size],cells=new int[s.size],before=new int[2],after=new int[2];
        Rules.incomePerTurn(s,before); Route best=null;
        for(int pair=0;pair<s.pairCount;pair++) {
            int n=pathing.cheapestPath(s,s.townCell[s.pairFrom[pair]],s.townCell[s.pairTo[pair]],route,null);
            if(n<=0) continue;
            int cost=paint(s,route,n),count=0;
            if(cost==0 || best!=null && cost>=best.paint) continue;
            for(int i=0;i<n;i++) if(s.canBuild(route[i])) cells[count++]=route[i];
            GameState completed=s.copy();
            Rules.applyPlacements(completed,me==0?cells:new int[0],me==0?count:0,me==1?cells:new int[0],me==1?count:0);
            Rules.updateConnections(completed,new Pathing(s.size)); Rules.incomePerTurn(completed,after);
            int own=after[me]-before[me],net=own-(after[1-me]-before[1-me]);
            if(own>0 && net>0) best=new Route(pair,cost,own,net);
        }
        return best;
    }
}
