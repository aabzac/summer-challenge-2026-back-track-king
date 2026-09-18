package btk.tools;
import btk.core.*;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;
/** Exhaustive one/two-region ink diagnostics on one exact saved public pre-turn state. */
public class CgStalledSabotageProbe {
 public static void main(String[] args)throws Exception {
  var r=CgReplay.load(Path.of(args[0]));int turn=Integer.parseInt(args[1]);var s=r.toState();var p=new Pathing(s.size);
  Rules.updateConnections(s,p);
  for(int t=0;t<turn;t++)Rules.resolve(s,CgPlayerStudy.effectiveActions(r,t,0),CgPlayerStudy.effectiveActions(r,t,1),p);
  var singles=new ArrayList<Object>();var pairs=new ArrayList<Object>();
  for(int a=0;a<s.regionCount;a++){
   if(s.regionInked[a]||s.regionHasTown(a))continue;
   var x=s.copy();Rules.ink(x,a);Rules.updateConnections(x,p);
   int[] owned=new int[2];for(int c=0;c<s.size;c++)if(s.regionId[c]==a&&s.owner[c]>=0&&s.owner[c]<2)owned[s.owner[c]]++;
   singles.add(Map.of("region",a,"needed",4-s.regionInstability[a],"income",income(x),"terminal",!Rules.anyPairPossible(x,p),"ownedTracks",owned));
   for(int b=a+1;b<s.regionCount;b++){
    if(s.regionInked[b]||s.regionHasTown(b))continue;
    var y=x.copy();Rules.ink(y,b);Rules.updateConnections(y,p);
    pairs.add(Map.of("regions",new int[]{a,b},"income",income(y),"terminal",!Rules.anyPairPossible(y,p)));
   }
  }
  String spec=args.length>3?args[3]:"v07";
  var bot=btk.bot.BotFactory.create(spec);bot.init(r.toState(),0);
  var action=bot.play(s.copy(),0,10000);
  var debug=new TreeMap<String,Object>();
  for(var f:bot.getClass().getFields())if(!java.lang.reflect.Modifier.isStatic(f.getModifiers()))debug.put(f.getName(),f.get(bot));
  var choose=bot.getClass().getDeclaredMethod("chooseTarget",GameState.class,int.class);choose.setAccessible(true);
  int greedy=(int)choose.invoke(bot,s,0);debug.put("knownStateGreedyTarget",greedy);
  var rollout=bot.getClass().getDeclaredMethod("rollout",GameState.class,int.class,int.class,int.class,int.class);rollout.setAccessible(true);
  debug.put("greedyFirstRollout",rollout.invoke(bot,s,0,0,greedy,0));
  debug.put("waitFirstRollout",rollout.invoke(bot,s,0,0,-1,0));
  var tails=new ArrayList<Object>();
  for(int first:new int[]{greedy,-1}) {
   var x=s.copy();var trace=new ArrayList<Object>();
   for(int t=0;t<8;t++){
    int own=t==0?first:(int)choose.invoke(bot,x,0),enemy=(int)choose.invoke(bot,x,1);
    var tr=Rules.resolve(x,own<0?List.of(Action.waitAction()):List.of(Action.disrupt(own)),enemy<0?List.of(Action.waitAction()):List.of(Action.disrupt(enemy)),p);
    boolean over=Rules.gameOver(x,p);
    trace.add(Map.of("turn",x.turn-1,"targets",new int[]{own,enemy},"gained",tr.gained,"score",x.score.clone(),"terminal",over));
    if(over)break;
   }
   tails.add(Map.of("first",first,"trace",trace));
  }
  debug.put("noBuildPureGreedyTrajectories",tails);
  var report=Map.of("gameId",r.gameId,"turn",turn,"scores",s.score,"income",income(s),"singleRegions",singles,"twoRegions",pairs,"v07AmpleAction",Action.format(action),"v07Debug",debug,"botSpec",spec);
  Path out=Path.of(args[2]);if(Files.exists(out))throw new IllegalStateException("Preserve "+out);
  Files.writeString(out,new GsonBuilder().setPrettyPrinting().create().toJson(report));
  System.out.println("game "+r.gameId+" turn "+turn+" "+spec+" ample "+Action.format(action)+" debug "+new GsonBuilder().create().toJson(debug));
 }
 static int[] income(GameState s){int[] x=new int[2];Rules.incomePerTurn(s,x);return x;}
}
