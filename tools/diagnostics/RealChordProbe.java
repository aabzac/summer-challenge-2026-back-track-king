package btk.tools;

import btk.bot.BotFactory;
import btk.bot.current.RolloutBot;
import btk.core.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

/** Read-only fixed-sample known-board action-space probe; no strategy or future-action input. */
public final class RealChordProbe {
    static final String SPEC = "current?USE_ROLLOUT_QUICK_ROUTE=1,NOISE=0";
    static Object field(Object o,String n) throws Exception {
        var f=o.getClass().getDeclaredField(n); f.setAccessible(true); return f.get(o);
    }
    static int diff(GameState s,int me) { int[] a=new int[2]; Rules.incomePerTurn(s,a); return a[me]-a[1-me]; }
    static int[] income(GameState s) { int[] a=new int[2]; Rules.incomePerTurn(s,a); return a; }
    static int gain(GameState s,SearchState work,int me,int[] cells,int n,int before) {
        work.set(s); int[] empty=new int[0];
        Rules.applyPlacements(work.state,me==0?cells:empty,me==0?n:0,me==1?cells:empty,me==1?n:0);
        work.refresh(); return diff(work.state,me)-before;
    }
    static List<Map<String,Object>> changes(GameState before,GameState after,int me) {
        List<Map<String,Object>> result=new ArrayList<>();
        for(int p=0;p<before.pairCount;p++) {
            int old=SearchState.connectionIncomeDifference(before,p,me);
            int now=SearchState.connectionIncomeDifference(after,p,me);
            if(old==now) continue;
            var r=new LinkedHashMap<String,Object>(); r.put("pair",before.pairFrom[p]+"-"+before.pairTo[p]);
            r.put("beforeNetIncome",old);r.put("afterNetIncome",now);
            r.put("beforeLength",before.pairPathLen[p]);r.put("afterLength",after.pairPathLen[p]);
            r.put("beforePath",before.pairPath[p]==null?new int[0]:Arrays.copyOf(before.pairPath[p],before.pairPathLen[p]));
            r.put("afterPath",after.pairPath[p]==null?new int[0]:Arrays.copyOf(after.pairPath[p],after.pairPathLen[p]));
            result.add(r);
        }
        return result;
    }
    static Map<String,Object> inspect(GameState s,int me) throws Exception {
        var bot=(RolloutBot)BotFactory.create(SPEC);bot.init(s,me);var action=bot.play(s,me,60000);
        int[] counts=(int[])field(bot,"counts"),sizes=(int[])field(bot,"rootCounts");
        int[][] roots=(int[][])field(bot,"roots"),lengths=(int[][])field(bot,"lengths");
        int[][][] routes=(int[][][])field(bot,"routes");
        boolean[] rootMember=new boolean[s.size],routeMember=new boolean[s.size];
        var work=new SearchState(s);int before=diff(s,me),bestRoot=0,bestRootIndex=-1,legal=0;
        List<Map<String,Object>> rootData=new ArrayList<>();
        for(int r=0;r<=counts[me];r++) {
            for(int j=0;j<sizes[r];j++) rootMember[roots[r][j]]=true;
            int g=gain(s,work,me,roots[r],sizes[r],before);
            if(g>bestRoot){bestRoot=g;bestRootIndex=r;}
            var row=new LinkedHashMap<String,Object>();row.put("index",r);row.put("gain",g);
            row.put("cells",Arrays.copyOf(roots[r],sizes[r]));rootData.add(row);
        }
        for(int r=0;r<counts[me];r++) for(int j=0;j<lengths[me][r];j++) routeMember[routes[me][r][j]]=true;
        int best=0,bestCell=-1,outsideRoot=0,outsideRoute=0,bestMissing=-1;List<Map<String,Object>> positive=new ArrayList<>();
        for(int c=0;c<s.size;c++) if(s.canBuild(c)&&s.placeCost(c)<=3) {
            legal++;int g=gain(s,work,me,new int[]{c},1,before);if(g<=0) continue;
            var row=new LinkedHashMap<String,Object>();row.put("cell",c);row.put("x",s.x(c));row.put("y",s.y(c));
            row.put("paint",s.placeCost(c));row.put("gain",g);row.put("inAnyRoot",rootMember[c]);row.put("inAnyRoute",routeMember[c]);positive.add(row);
            if(g>best){best=g;bestCell=c;}
            if(!rootMember[c])outsideRoot=Math.max(outsideRoot,g);
            if(!routeMember[c]&&g>outsideRoute){outsideRoute=g;bestMissing=c;}
        }
        var row=new LinkedHashMap<String,Object>();row.put("width",s.width);row.put("height",s.height);
        row.put("incomeBefore",income(s));row.put("scoreBefore",s.score.clone());row.put("legalSingleCells",legal);
        row.put("rootCount",counts[me]+1);row.put("bestRootGain",bestRoot);row.put("bestRootIndex",bestRootIndex);
        row.put("bestSingleGain",best);row.put("bestSingleCell",bestCell);
        row.put("bestSingleInRoot",bestCell>=0&&rootMember[bestCell]);row.put("bestSingleInRoute",bestCell>=0&&routeMember[bestCell]);
        row.put("bestOutsideRootGain",outsideRoot);row.put("bestOutsideRouteGain",outsideRoute);
        row.put("bestOutsideRouteCell",bestMissing);row.put("positiveCells",positive);row.put("roots",rootData);
        row.put("selectedAction",Action.format(action));row.put("completedComparisons",bot.completedComparisons);
        row.put("routeQueries",bot.routeQueries);
        if(bestCell>=0){gain(s,work,me,new int[]{bestCell},1,before);row.put("bestSingleIncomeAfter",income(work.state));row.put("bestSingleChanges",changes(s,work.state,me));}
        if(bestMissing>=0){gain(s,work,me,new int[]{bestMissing},1,before);row.put("bestMissingIncomeAfter",income(work.state));row.put("bestMissingChanges",changes(s,work.state,me));}
        return row;
    }
    public static void main(String[] args) throws Exception {
        Path input=Path.of("docs/evidence/spread-construction-v1-diagnostic.json"),output=Path.of("tmp/network-strategy-analysis/RealChordProbe-output.json");
        if(Files.exists(output))throw new IllegalArgumentException("Preserve existing report");
        var prior=JsonParser.parseString(Files.readString(input)).getAsJsonObject();var samples=prior.getAsJsonArray("samples");
        Map<Long,List<JsonObject>> grouped=new LinkedHashMap<>();
        for(var item:samples){var row=item.getAsJsonObject();grouped.computeIfAbsent(row.get("gameId").getAsLong(),k->new ArrayList<>()).add(row);}
        List<Map<String,Object>> rows=new ArrayList<>(),games=new ArrayList<>();int totalLegal=0,positiveStates=0,improved=0,absent=0,missedBetter=0;
        for(var entry:grouped.entrySet()) {
            Path file=Path.of("replays/cg/bot/"+entry.getKey()+".json");var replay=CgReplay.load(file);
            var validation=CgValidate.validate(replay,false);if(validation.firstDivergenceTurn>=0)throw new IllegalStateException("Invalid replay "+file);
            var info=new LinkedHashMap<String,Object>();info.put("file",file.toString());info.put("sha256",ReplyStudy.hash(Files.readAllBytes(file)));info.put("validated",true);games.add(info);
            GameState s=replay.toState();Pathing pathing=new Pathing(s.size);Rules.updateConnections(s,pathing);
            int next=0,last=entry.getValue().get(entry.getValue().size()-1).get("turn").getAsInt();
            for(int t=0;t<=last;t++) {
                if(replay.failure(t,0)!=null||replay.failure(t,1)!=null)throw new IllegalStateException("Failed replay turn");
                var sample=entry.getValue().get(next);
                if(t==sample.get("turn").getAsInt()) {
                    String before=ReplyStudy.stateHash(s);if(!before.equals(sample.get("stateSha256").getAsString()))throw new IllegalStateException("State changed");
                    int me=sample.get("me").getAsInt();var row=new LinkedHashMap<String,Object>();
                    row.put("gameId",entry.getKey());row.put("turn",t);row.put("me",me);row.put("outcome",sample.get("outcome").getAsString());row.put("stateSha256",before);
                    row.putAll(inspect(s,me));if(!before.equals(ReplyStudy.stateHash(s)))throw new IllegalStateException("Input mutated");
                    rows.add(row);int b=(int)row.get("bestRootGain"),one=(int)row.get("bestSingleGain"),missing=(int)row.get("bestOutsideRouteGain");
                    totalLegal+=(int)row.get("legalSingleCells");if(one>0)positiveStates++;if(one>b)improved++;if(missing>0)absent++;if(missing>b)missedBetter++;
                    if(++next==entry.getValue().size())break;
                }
                Rules.resolve(s,CgPlayerStudy.effectiveActions(replay,t,0),CgPlayerStudy.effectiveActions(replay,t,1),pathing);
            }
            System.out.println("Validated/probed "+entry.getKey()+", cumulative "+rows.size()+" states");
        }
        if(rows.size()!=samples.size())throw new IllegalStateException("Incomplete sample");
        var report=new LinkedHashMap<String,Object>();report.put("spec",SPEC);report.put("params",BotFactory.configuration(SPEC));
        report.put("inputSha256",ReplyStudy.hash(Files.readAllBytes(input)));report.put("rolloutClassSha256",ReplyStudy.hash(Files.readAllBytes(Path.of("build/classes/java/main/btk/bot/current/RolloutBot.class"))));
        report.put("method","All 133 previously fixed boss pre-turn states, matching hashes, 14 replays validated first. Exhaustive legal own single-cell placements, known board with no disruptions or enemy placements; core recomputes exact income. Compare with every ample-time NOISE=0 QuickIncome root placement set, including WAIT. Recorded actions used only to reconstruct pre-turn states. No live win rate, future income or process/timing claim.");
        report.put("sampleCount",rows.size());report.put("legalSingleCells",totalLegal);report.put("statesWithPositiveSingle",positiveStates);
        report.put("statesSingleBeatsBestRoot",improved);report.put("statesPositiveOutsideRoutes",absent);report.put("statesOutsideRouteBeatsBestRoot",missedBetter);
        report.put("games",games);report.put("samples",rows);Files.writeString(output,ReplyStudy.JSON.toJson(report),StandardOpenOption.CREATE_NEW);
        System.out.println("states="+rows.size()+" legalCells="+totalLegal+" positive="+positiveStates+" singleBeatsRoot="+improved+" missingPositive="+absent+" missingBeatsRoot="+missedBetter);
    }
}
