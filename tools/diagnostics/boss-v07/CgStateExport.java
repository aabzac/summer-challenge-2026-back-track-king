package btk.tools;
import btk.core.*;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;
/** Export exact core-derived public pre-turn state as a compact diagnostic/test fixture. */
public class CgStateExport {
 public static void main(String[] args)throws Exception {
  var r=CgReplay.load(Path.of(args[0]));int turn=Integer.parseInt(args[1]);var s=r.toState();var p=new Pathing(s.size);
  var validation=CgValidate.validate(r,false);if(validation.firstDivergenceTurn>=0)throw new IllegalStateException("Replay divergence");
  Rules.updateConnections(s,p);
  for(int t=0;t<turn;t++)Rules.resolve(s,CgPlayerStudy.effectiveActions(r,t,0),CgPlayerStudy.effectiveActions(r,t,1),p);
  Path out=Path.of(args[2]);if(Files.exists(out))throw new IllegalStateException("Preserve "+out);
  Files.createDirectories(out.toAbsolutePath().getParent());
  Files.writeString(out,new GsonBuilder().create().toJson(Map.of("gameId",r.gameId,"turn",turn,"globalGraphics",r.globalGraphics,"state",s)));
  System.out.println(out);
 }
}
