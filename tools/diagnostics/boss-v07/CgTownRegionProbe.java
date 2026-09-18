package btk.tools;
import btk.core.*;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;

/** Core-only probe: remove all disruptible regions, measure surviving income and routes. */
public class CgTownRegionProbe {
  public static void main(String[] args) throws Exception {
    var report=new ArrayList<Object>();
    for(String f:args[0].split(",")) {
      var r=CgReplay.load(Path.of(f)); var s=r.toState(); var paths=new Pathing(s.size);
      var turns=new ArrayList<Object>(); Rules.updateConnections(s,paths);
      var towns=new ArrayList<Object>();
      for(int i=0;i<s.townCount;i++)towns.add(Map.of("id",i,"x",s.townX[i],"y",s.townY[i],"region",s.regionId[s.townCell[i]]));
      var initialProtectedRoutes=new ArrayList<Object>();
      var allGone=townOnly(s,paths); int[] route=new int[s.size];
      for(int p=0;p<s.pairCount;p++) {
        int to=s.townCell[s.pairTo[p]];
        int n=paths.cheapestPath(allGone,s.townCell[s.pairFrom[p]],to,route);
        if(n<0)continue;
        var cells=new ArrayList<Object>();
        for(int i=0;i<n;i++)cells.add(new int[]{s.x(route[i]),s.y(route[i]),s.regionId[route[i]]});
        initialProtectedRoutes.add(Map.of("pair",p,"from",s.pairFrom[p],"to",s.pairTo[p],"paint",paths.lastCost(to),"cells",cells));
      }
      for(int t=0;t<r.turns();t++) {
        var a0=CgPlayerStudy.effectiveActions(r,t,0);var a1=CgPlayerStudy.effectiveActions(r,t,1);
        var before=income(s);var protectedBefore=income(townOnly(s,paths));
        var tr=Rules.resolve(s,a0,a1,paths);
        var protectedAfter=income(townOnly(s,paths));
        turns.add(Map.of("turn",t,"incomeBefore",before,"protectedBefore",protectedBefore,"protectedAfter",protectedAfter,"incomeAfter",income(s),"actions",r.turnStdout.get(t)));
      }
      System.out.println(r.gameId+" final "+Arrays.toString(s.score)+" protected "+Arrays.toString(income(townOnly(s,paths)))+" protected initial routes "+initialProtectedRoutes.size());
      report.add(Map.of("gameId",r.gameId,"towns",towns,"initialProtectedRoutes",initialProtectedRoutes,"turns",turns));
    }
    Path out=Path.of(args[1]); if(Files.exists(out))throw new IllegalStateException("Preserve report: "+out);
    Files.writeString(out,new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("method","Each turn removes ALL non-town regions through Rules.ink and recomputes connections/income through core. Not a prediction: later ownership and shorter routes can still change protected-network income. Raw replay actions retained only for recorded context. Turns zero-based.","games",report)));
  }
  static GameState townOnly(GameState s,Pathing paths) {
    var x=s.copy(); for(int r=0;r<x.regionCount;r++) if(!x.regionHasTown(r))Rules.ink(x,r);
    Rules.updateConnections(x,paths); return x;
  }
  static int[] income(GameState s){int[] x=new int[2];Rules.incomePerTurn(s,x);return x;}
}
