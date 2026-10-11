package btk.tools;

import btk.bot.Timer;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import btk.tools.V09ContinuationProbe.ReplyTrace;
import btk.tools.V09ContinuationProbe.Turn;

/** Independent tools oracle: frozen v09 decisions, core transitions, only later hits on r removed. */
final class V09DeferredCommitOracle {
    private V09DeferredCommitOracle() {}

    static List<ReplyTrace> withoutTarget(V09FullActionProbe probe, GameState initial, int me, int target) throws Exception {
        var meta = probe.metadata();
        var paramsField=V09FullActionProbe.class.getDeclaredField("params");paramsField.setAccessible(true);
        require(((btk.bot.Params)paramsField.get(probe)).ROLLOUT_DEPTH==8,"Oracle requires declared depth8");
        List<ReplyTrace> result = new ArrayList<>();
        try (AutoCloseable ignored = (AutoCloseable) invoke(probe,"preserve",new Class<?>[0])) {
            Timer clock=(Timer)invoke(probe,"timer",new Class<?>[0]);clock.start(V09ContinuationProbe.AMPLE_MS);
            for (int reply=0;reply<meta.replies();reply++) {
                GameState s=initial.copy(); Pathing pathing=new Pathing(s.size);
                List<Turn> turns=new ArrayList<>(); boolean terminal=false;
                for (int offset=0;offset<Math.min(8,initial.turnsLeft());offset++) {
                    int[][] cells=new int[2][];int[] proposed={-1,-1},targets={-1,-1};
                    int[] beforeIncome=new int[2];Rules.incomePerTurn(s,beforeIncome);
                    for(int player=0;player<2;player++) {
                        if(player==me && offset==0) cells[player]=probe.selected().cells();
                        else {
                            int first=player==me ? (meta.root()<meta.ownCount()?meta.root():0) : reply;
                            cells[player]=(int[])invoke(probe,"futureCells",new Class<?>[]{GameState.class,int.class,int.class},s,player,first);
                            proposed[player]=(int)invoke(probe,"futureTarget",new Class<?>[]{GameState.class,int.class,int[].class},s,player,cells[player]);
                            targets[player]=player==me && proposed[player]==target ? -1 : proposed[player];
                        }
                    }
                    require(!clock.up(),"Independent ample oracle expired");
                    int turn=s.turn;
                    var resolved=Rules.resolve(s,actions(s,cells[0],targets[0]),actions(s,cells[1],targets[1]),pathing);
                    int[] paint=new int[2];
                    for(int player=0;player<2;player++) {
                        var move=resolved.moves[player];
                        require(move.error==null && move.skipped.isEmpty() && !resolved.unknownActions,"Oracle action rejected");
                        require(Arrays.equals(cells[player],Arrays.copyOf(move.cells,move.count))
                                && targets[player]==(move.disrupts.isEmpty()?-1:move.disrupts.get(0)),"Oracle action changed");
                        paint[player]=move.cost;
                    }
                    terminal=Rules.gameOver(s,pathing);int[] afterIncome=new int[2];Rules.incomePerTurn(s,afterIncome);
                    turns.add(new Turn(offset,turn,cells,proposed,targets,paint,beforeIncome,afterIncome,
                            resolved.gained.clone(),s.score.clone(),terminal,List.copyOf(resolved.inkedRegions),s.regionInstability.clone()));
                    if(terminal)break;
                }
                double value=(double)invoke(probe,"heuristic",new Class<?>[]{GameState.class,boolean.class},s,terminal);
                require(!clock.up(),"Independent ample oracle expired");
                result.add(new ReplyTrace(reply,value,List.copyOf(turns)));
            }
        }
        return List.copyOf(result);
    }

    private static Object invoke(Object receiver,String name,Class<?>[] types,Object...args)throws Exception {
        Method method=receiver.getClass().getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(receiver,args);
    }
    static List<Action> actions(GameState s,int[] cells,int target) {
        List<Action> result=new ArrayList<>();for(int cell:cells)result.add(Action.place(s.x(cell),s.y(cell)));
        if(target>=0)result.add(Action.disrupt(target));if(result.isEmpty())result.add(Action.waitAction());return result;
    }
    static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
