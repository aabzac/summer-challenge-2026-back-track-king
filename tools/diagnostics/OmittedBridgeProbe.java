package btk.tools;
import btk.core.*;
import btk.bot.*;
import btk.bot.current.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
/** Fixed 20-game known-state representation audit; no policy search or strength claims. */
public class OmittedBridgeProbe {
 static Object field(Object o,String n)throws Exception {var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 static int fill(RolloutBot b,GameState s,int me,int[] out)throws Exception {var m=b.getClass().getDeclaredMethod("fillBuild",GameState.class,int.class,int.class,int[].class);m.setAccessible(true);return (int)m.invoke(b,s,me,0,out);}
 static Map<String,Object> fixture()throws Exception {
  var s=new GameState(7,1,new int[]{0,1,1,2,3,3,4},new int[7],new int[]{0,6},new int[]{0,0},new int[][]{{1},{}});
  s.owner[1]=0;s.owner[2]=0;var p=new Pathing(s.size);int[] route=new int[s.size];int n=p.cheapestPath(s,0,6,route);
  var bot=new RolloutBot(new Params());bot.init(s,0);((int[])field(bot,"counts"))[0]=1;((int[][])field(bot,"lengths"))[0][0]=n;System.arraycopy(route,0,((int[][][])field(bot,"routes"))[0][0],0,n);
  Rules.ink(s,1);Rules.updateConnections(s,p);int[] moves=new int[3];int count=fill(bot,s,0,moves);Rules.applyPlacements(s,moves,count,new int[0],0);Rules.updateConnections(s,p);
  return Map.of("route",Arrays.copyOf(route,n),"omittedBridgeCells",new int[]{1,2},"inkedRegion",1,"continuationCells",Arrays.copyOf(moves,count),"targetPairConnectedAfterCompletion",s.pairPathLen[0]>0);
 }
 public static void main(String[] args)throws Exception {
  Map<String,Object> report=new LinkedHashMap<>();report.put("fixture",fixture());System.out.println(new Gson().toJson(report));if(args.length==0)return;
  long[] gameIds={904553513,904553687,904553769,904553911,904553970,904554072,904554369,904554429,904554475,904554568,904554907,904554986,904555027,904555096,904555166,904555229,904555291,904555363,904555424,904555504}; var files=new ArrayList<Path>(); for(long gameId:gameIds)files.add(Path.of("replays/cg/bot/"+gameId+".json"));
  var games=new ArrayList<Map<String,Object>>();int allStates=0,inkStates=0,proposed=0,eligible=0,compressed=0,hidden=0,affectedStates=0;var examples=new ArrayList<Map<String,Object>>();
  for(var file:files){var r=CgReplay.load(file);var validation=CgValidate.validate(r,false);if(validation.firstDivergenceTurn>=0)throw new IllegalStateException("invalid "+file);int me=r.arenaBosses[0]||r.agentIds[0]==-2?1:0;
   var s=r.toState();var p=new Pathing(s.size);Rules.updateConnections(s,p);int gameHidden=0,gameAffected=0;
   for(int turn=0;turn<r.turns();turn++){if(r.failure(turn,0)!=null||r.failure(turn,1)!=null)break;allStates++;var after=s.copy();Rules.resolve(after,CgPlayerStudy.effectiveActions(r,turn,0),CgPlayerStudy.effectiveActions(r,turn,1),p);var inkedRegions=new ArrayList<Integer>();for(int region=0;region<s.regionCount;region++)if(!s.regionInked[region]&&after.regionInked[region])inkedRegions.add(region);
    if(!inkedRegions.isEmpty()){inkStates++;boolean stateHit=false;int[] base=new int[2];Rules.incomePerTurn(s,base);var work=new SearchState(s);int[] route=new int[s.size],extra=new int[s.size];boolean[] blocked=new boolean[s.size];for(int c=0;c<s.size;c++)extra[c]=s.instability[c];
     for(int pair=0;pair<s.pairCount;pair++)for(int variant=0;variant<2;variant++){for(int c=0;c<s.size;c++)blocked[c]=s.owner[c]==1-me;int n=p.cheapestPath(s,s.townCell[s.pairFrom[pair]],s.townCell[s.pairTo[pair]],route,variant==0?null:blocked,extra);if(n<=0)continue;proposed++;boolean jump=false,storedInk=false;int cost=0;work.set(s);for(int i=0;i<n;i++){int c=route[i];if(i>0&&Math.abs(s.x(c)-s.x(route[i-1]))+Math.abs(s.y(c)-s.y(route[i-1]))>1)jump=true;if(inkedRegions.contains(s.regionId[c]))storedInk=true;if(s.canBuild(c)){cost+=s.placeCost(c);work.state.owner[c]=me;}}
      if(cost==0)continue;work.refresh();int[] inc=new int[2];Rules.incomePerTurn(work.state,inc);int gain=inc[me]-inc[1-me]-base[me]+base[1-me];if(gain<=0)continue;eligible++;if(jump)compressed++;if(!jump||storedInk)continue;for(int region:inkedRegions)Rules.ink(work.state,region);work.refresh();if(work.state.pairPathLen[pair]>0)continue;
      hidden++;gameHidden++;stateHit=true;if(examples.size()<50){var row=new LinkedHashMap<String,Object>();row.put("gameId",r.gameId);row.put("turn",turn);row.put("me",me);row.put("outcome",r.finalScores[me]>r.finalScores[1-me]?"win":"loss");row.put("pair",new int[]{s.pairFrom[pair],s.pairTo[pair]});row.put("variant",variant);row.put("cost",cost);row.put("fullNetworkCompletionGain",gain);row.put("actualInkedRegions",inkedRegions);row.put("storedPath",Arrays.copyOf(route,n));examples.add(row);}
     }if(stateHit){affectedStates++;gameAffected++;}
    }s=after;
   }games.add(Map.of("gameId",r.gameId,"file",file.toString(),"sha256",ReplyStudy.hash(Files.readAllBytes(file)),"me",me,"validated",true,"hiddenBridgeRoutes",gameHidden,"affectedStates",gameAffected));System.out.println("game="+r.gameId+" hiddenRoutes="+gameHidden+" affectedStates="+gameAffected);
  }
  report.put("method","Fixed last-20 boss cohort captured on 2026-09-18 (904553513..904555504), all successful recorded pre-turn states, canonical validation first. At turns with recorded region ink, propose current ordinary and no-enemy cheapest-path families for our player using current instability surcharge 1. Count positive completed whole-network net-income candidate-family observations before threat weighting, with paid cells, omitted nonadjacent connector jumps, no explicitly stored cell in the inked region, but the target pair becomes unreachable after candidate completion plus that recorded ink. Repeated pairs and route variants are not independent or distinct actions. No policy search, shortlist/root selection, future score forecast, strength test or claim these candidates were selected. Actual ink is diagnostic only.");report.put("states",allStates);report.put("turnsWithInk",inkStates);report.put("proposedRoutesAtInkTurns",proposed);report.put("positivePaidCompletionRoutes",eligible);report.put("compressedPositiveRoutes",compressed);report.put("hiddenBridgeRoutes",hidden);report.put("affectedStates",affectedStates);report.put("games",games);report.put("examples",examples);Path out=Path.of(args[0]);Files.writeString(out,new GsonBuilder().setPrettyPrinting().create().toJson(report),StandardOpenOption.CREATE_NEW);System.out.println("TOTAL "+new Gson().toJson(Map.of("states",allStates,"inkStates",inkStates,"proposed",proposed,"eligible",eligible,"compressed",compressed,"hidden",hidden,"affectedStates",affectedStates)));
 }
}
