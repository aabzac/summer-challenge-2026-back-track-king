import btk.bot.Params;
import btk.bot.current.RolloutBot;
import btk.core.*;
import java.lang.reflect.Field;
import java.util.*;

/** Mechanism-only toy fixture. Run: java --class-path build/classes/java/main tmp/network-strategy-analysis/SyntheticChordProbe.java */
public class SyntheticChordProbe {
    static Object field(Object o, String name) throws Exception {
        Field f=o.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(o);
    }
    static GameState fixture(int trackOwner) {
        int w=9,h=7; int[] regions=new int[w*h],terrain=new int[w*h];
        // Each cell is its own connected region; towns are well separated.
        for(int i=0;i<regions.length;i++) regions[i]=i;
        GameState s=new GameState(w,h,regions,terrain,new int[]{1,7},new int[]{3,3},new int[][]{{1},{}});
        int[][] rails={{2,3},{3,3},{3,2},{4,2},{5,2},{5,3},{6,3}};
        for(int[] c:rails) s.owner[s.cell(c[0],c[1])]=trackOwner;
        s.turn=10;
        Rules.updateConnections(s,new Pathing(s.size)); return s;
    }
    static String cells(GameState s,int[] path,int n) {
        StringJoiner j=new StringJoiner(" ");
        for(int i=0;i<n;i++) j.add("("+s.x(path[i])+","+s.y(path[i])+")"); return j.toString();
    }
    static int[] income(GameState s) { int[] v=new int[2]; Rules.incomePerTurn(s,v);return v; }
    static void showBoard(GameState s) {
        for(int y=0;y<s.height;y++) {StringBuilder line=new StringBuilder();
            for(int x=0;x<s.width;x++) {int c=s.cell(x,y);line.append(s.isTown(c)?'T':x==4&&y==3?'?':s.owner[c]==1?'E':s.owner[c]==0?'O':'.');}
            System.out.println(line);
        }
    }
    static void manualVariants(GameState s,int threat) {
        Pathing p=new Pathing(s.size);int[] path=new int[s.size],extra=new int[s.size];boolean[] blocked=new boolean[s.size];
        for(int variant=0;variant<3;variant++) {
            if(variant==2&&threat<0){System.out.println("variant=avoid-predicted-threat SKIPPED (no threat)");continue;}
            for(int c=0;c<s.size;c++) blocked[c]=variant==1?s.owner[c]==1:variant==2&&s.regionId[c]==threat;
            int n=p.cheapestPath(s,s.townCell[0],s.townCell[1],path,variant==0?null:blocked,extra),paint=0;
            GameState completion=s.copy();boolean chord=false;
            for(int i=0;i<Math.max(n,0);i++) if(s.canBuild(path[i])) {paint+=s.placeCost(path[i]);completion.owner[path[i]]=0;chord|=path[i]==s.cell(4,3);}
            Rules.updateConnections(completion,new Pathing(s.size));
            System.out.println("variant="+variant+" length="+n+" paint="+paint+" contains-new-chord="+chord+" completed-income="+Arrays.toString(income(completion))+" path="+cells(s,path,Math.max(n,0)));
        }
    }
    static void botProbe(GameState s,String label,Params params) throws Exception {
        RolloutBot b=new RolloutBot(params);b.init(s,0);List<Action> out=b.play(s,0,60_000);
        int[] count=(int[])field(b,"counts"),threat=(int[])field(b,"threats");
        int[][][] routes=(int[][][])field(b,"routes");int[][] lengths=(int[][])field(b,"lengths");
        int[][] roots=(int[][])field(b,"roots");int[] rootCounts=(int[])field(b,"rootCounts");
        boolean foundRoute=false,foundRoot=false;
        System.out.println("POLICY "+label+" params="+params.diff());
        System.out.println("ample-budget-ms=60000 queries="+b.routeQueries+" completed-comparisons="+b.completedComparisons+" threats="+Arrays.toString(threat)+" action="+Action.format(out));
        for(int i=0;i<count[0];i++) {for(int j=0;j<lengths[0][i];j++) foundRoute|=routes[0][i][j]==s.cell(4,3);
            System.out.println("route["+i+"]="+cells(s,routes[0][i],lengths[0][i]));}
        for(int i=0;i<count[0]+1;i++) {for(int j=0;j<rootCounts[i];j++) foundRoot|=roots[i][j]==s.cell(4,3);
            System.out.println("root["+i+"]="+cells(s,roots[i],rootCounts[i]));}
        System.out.println("chord-in-retained-route="+foundRoute+" chord-in-affordable-root="+foundRoot);
        if(foundRoute||foundRoot) throw new AssertionError("Counterexample failed: chord present");
        if(label.equals("QuickIncome-default-noise"))manualVariants(s,threat[0]);
    }
    public static void main(String[] args) throws Exception {
        System.out.println("SYNTHETIC DIAGNOSTIC ONLY: not a replay, benchmark, win forecast or authentic generator sample.");
        System.out.println("Toy constraints: 9x7 (official minimum 21x14), two towns (official minimum four), one desired pair, singleton regions; core-valid fixture only.");
        System.out.println("T town, E enemy rail, O own rail, ? legal one-paint chord (4,3).");
        GameState s=fixture(1);showBoard(s);int[] before=income(s);
        SearchState after=new SearchState(s);after.resolve(new int[]{s.cell(4,3)},1,-1,new int[0],0,-1);
        int[] gained=income(after.state);int delta=(gained[0]-gained[1])-(before[0]-before[1]);
        System.out.println("positive-case before-income="+Arrays.toString(before)+" after-income="+Arrays.toString(gained)+" signed-income-delta="+delta+" before-path="+cells(s,s.pairPath[0],s.pairPathLen[0])+" after-path="+cells(s,after.state.pairPath[0],after.state.pairPathLen[0]));
        if(delta!=4)throw new AssertionError("Expected +4 income difference");
        botProbe(s,"v06-policy-current-code",new Params());
        botProbe(s,"QuickIncome-default-noise",new Params("USE_ROLLOUT_QUICK_ROUTE=1"));
        botProbe(s,"QuickIncome-noise0",new Params("USE_ROLLOUT_QUICK_ROUTE=1,NOISE=0"));
        GameState negative=fixture(0);before=income(negative);SearchState ownAfter=new SearchState(negative);
        ownAfter.resolve(new int[]{negative.cell(4,3)},1,-1,new int[0],0,-1);gained=income(ownAfter.state);
        delta=(gained[0]-gained[1])-(before[0]-before[1]);
        System.out.println("negative-control same-geometry-own-rails before-income="+Arrays.toString(before)+" after-income="+Arrays.toString(gained)+" signed-income-delta="+delta);
        if(delta!=-2)throw new AssertionError("Expected harmful own shortcut -2");
        System.out.println("PASS: beneficial mixed-network chord absent from ample-time roots; exact core rejects naive always-shorten objective.");
    }
}
