package btk.tools;
import btk.core.*;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.*;
/** Scores all subsets of the boss's accepted placements using the core, without either sabotage. */
public class CgBossBuildSubsets {
 public static void main(String[] args)throws Exception {
  var result=new ArrayList<Object>();
  for(String spec:args[0].split(",")){
   String[] bits=spec.split(":");var file=Path.of("replays/cg/bot/"+bits[0]+".json");int turn=Integer.parseInt(bits[1]);
   var raw=JsonParser.parseString(Files.readString(file)).getAsJsonObject();var replay=CgReplay.parse(raw);
   int boss=CgBossDefenseStudy.identifyBoss(raw).player();var s=replay.toState();var paths=new Pathing(s.size);
   Rules.updateConnections(s,paths);
   for(int t=0;t<turn;t++)Rules.resolve(s,CgPlayerStudy.effectiveActions(replay,t,0),CgPlayerStudy.effectiveActions(replay,t,1),paths);
   var move=new Rules.PlayerMoves();Rules.expand(s,boss,CgPlayerStudy.effectiveActions(replay,turn,boss),paths,move,new Rules.TurnResult());
   var branches=new ArrayList<Object>();int[] before=income(s);
   for(int mask=1;mask<(1<<move.count);mask++){
    var built=s.copy();var cells=new ArrayList<Object>();int cost=0;
    for(int i=0;i<move.count;i++)if((mask&(1<<i))!=0){int c=move.cells[i];built.owner[c]=boss;cost+=s.placeCost(c);cells.add(new int[]{s.x(c),s.y(c)});}
    Rules.updateConnections(built,paths);int[] after=income(built);
    branches.add(Map.of("cells",cells,"paint",cost,"income",after,"bossNetGain",after[boss]-after[1-boss]-before[boss]+before[1-boss]));
   }
   result.add(Map.of("gameId",replay.gameId,"turn",turn,"boss",boss,"incomeBefore",before,"subsets",branches));
  }
  Path out=Path.of(args[1]);if(Files.exists(out))throw new IllegalStateException("Preserve "+out);
  Files.writeString(out,new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("method","All nonempty subsets of accepted boss placements at exact pre-turn state. No simultaneous opposing construction or either sabotage; immediate core income difference only, not future win value.","cases",result)));
  System.out.println(new GsonBuilder().create().toJson(result));
 }
 static int[] income(GameState s){int[] x=new int[2];Rules.incomePerTurn(s,x);return x;}
}
