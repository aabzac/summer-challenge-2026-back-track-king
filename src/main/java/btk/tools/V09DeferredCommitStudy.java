package btk.tools;

import btk.bot.BotFactory;
import btk.bot.current.RolloutBot;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedWriter;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import btk.tools.V09ContinuationProbe.Plan;
import btk.tools.V09ContinuationProbe.ReplyTrace;
import static btk.tools.V09DeferredCommitOracle.require;

/** Prospectively fixed local mechanism screen; empty per-branch memory, real repeated decisions. */
public final class V09DeferredCommitStudy {
    private V09DeferredCommitStudy() {}
    static final String OFF="current?NOISE=0";
    static final String ON="current?NOISE=0,USE_ROLLOUT_MISSED_DEADLINE=true";
    private static final String SELECTION_HASH="a04858f4023c1363f3170250d0c39f4e0044323c1d697c08c73c463a004a2c38";
    public record Step(int offset,int turn,Plan nativeAction,Plan ownAction,Plan opponentAction,
                       V09FullActionProbe.Metadata nativeMetadata,
                       Map<String,Object> diagnostics,Map<String,Object> oracle,boolean intervention,
                       int[] scoreBefore,int[] score,int[] income,List<Integer> inkedRegions,
                       boolean terminal,String stateBeforeSha256,String stateSha256) {}
    public record Arm(String spec,boolean complete,int steps,String stopReason,String terminalOutcome,
                      int finalScoreDifference,String finalStateSha256,List<Step> turns) {}
    public record Pair(String opponentRegime,int nativeReply,Arm off,Arm on) {}
    public record Episode(String initialStateSha256,int me,int horizon,int pinnedTarget,
                          V09FullActionProbe.Metadata initialMetadata,List<Pair> comparisons) {}

    static RolloutBot bot(boolean enabled,GameState s,int me) {
        var result=(RolloutBot)BotFactory.create(enabled?ON:OFF);result.init(s.copy(),me);return result;
    }
    static Episode episode(GameState initial,int me,int target)throws Exception {
        String before=ReplyStudy.stateHash(initial);
        var probe=V09FullActionProbe.prepare(initial,me);require(probe.metadata().supported(),"Unsupported entry");
        int horizon=Math.min(8,initial.turnsLeft());List<Pair> pairs=new ArrayList<>();
        for(int reply=0;reply<probe.metadata().replies();reply++)
            pairs.add(pair(initial,me,horizon,probe,V09FullActionEpisode.NATIVE,reply));
        pairs.add(pair(initial,me,horizon,probe,V09FullActionEpisode.PASSIVE,-1));
        require(before.equals(ReplyStudy.stateHash(initial)),"Episode mutated source");
        return new Episode(before,me,horizon,target,probe.metadata(),List.copyOf(pairs));
    }
    private static Pair pair(GameState initial,int me,int horizon,V09FullActionProbe initialProbe,String regime,int reply)throws Exception {
        Arm off=arm(initial,me,horizon,initialProbe,regime,reply,false);
        Arm on=arm(initial,me,horizon,initialProbe,regime,reply,true);
        require(off.turns.get(0).ownAction.action().equals(on.turns.get(0).ownAction.action()),"Fresh memory changed first action");
        return new Pair(regime,reply,off,on);
    }
    private static Arm arm(GameState initial,int me,int horizon,V09FullActionProbe opponent,String regime,int reply,boolean enabled)throws Exception {
        GameState s=initial.copy();Pathing pathing=new Pathing(s.size);RolloutBot policy=bot(enabled,s,me);
        List<Step> steps=new ArrayList<>();boolean terminal=false;
        for(int offset=0;offset<horizon;offset++) {
            String before=ReplyStudy.stateHash(s);int turn=s.turn;
            var nativeProbe=V09FullActionProbe.prepare(s,me);require(nativeProbe.metadata().supported(),"Unsupported native replan");
            Plan nativeAction=nativeProbe.selected();
            Plan own=V09ContinuationProbe.accepted(s,me,policy.play(s.copy(),me,V09ContinuationProbe.AMPLE_MS));
            require(policy.ordinarySearchComplete && policy.networkPhaseComplete,"Incomplete ample candidate search");
            require(Arrays.equals(nativeAction.cells(),own.cells()),"Correction changed complete construction");
            boolean changed=own.target()!=nativeAction.target();
            require(!changed || enabled && nativeAction.target()==-1 && own.target()==nativeProbe.metadata().nominatedTarget(),"Unexpected policy divergence");
            Map<String,Object> diagnostics=enabled?diagnostics(policy):Map.of();
            Map<String,Object> oracle=enabled?verify(policy,nativeProbe,s,me,diagnostics):Map.of();
            Plan enemy=regime.equals(V09FullActionEpisode.NATIVE)?opponent.opponentAction(s,reply)
                    :V09ContinuationProbe.accepted(s,1-me,List.of(Action.waitAction()));
            int[] scoreBefore=s.score.clone();
            var resolved=me==0?Rules.resolve(s,Action.parseLine(own.action()),Action.parseLine(enemy.action()),pathing)
                    :Rules.resolve(s,Action.parseLine(enemy.action()),Action.parseLine(own.action()),pathing);
            accepted(resolved,me,own);accepted(resolved,1-me,enemy);
            terminal=Rules.gameOver(s,pathing);int[] income=new int[2];Rules.incomePerTurn(s,income);
            steps.add(new Step(offset,turn,nativeAction,own,enemy,nativeProbe.metadata(),diagnostics,oracle,changed,scoreBefore,s.score.clone(),income,
                    List.copyOf(resolved.inkedRegions),terminal,before,ReplyStudy.stateHash(s)));
            if(terminal)break;
        }
        int difference=s.score[me]-s.score[1-me];
        return new Arm(enabled?ON:OFF,terminal||steps.size()==horizon,steps.size(),terminal?"TERMINAL":"HORIZON",
                terminal?(difference>0?"WIN":difference<0?"LOSS":"DRAW"):"NONTERMINAL",difference,
                ReplyStudy.stateHash(s),List.copyOf(steps));
    }

    /** Copy public diagnostic arrays before the persistent policy chooses its next move. */
    static Map<String,Object> diagnostics(RolloutBot bot)throws Exception {
        Map<String,Object> result=new TreeMap<>();
        for(var field:RolloutBot.class.getFields())if(!Modifier.isStatic(field.getModifiers())&&field.getName().startsWith("deadline")) {
            Object value=field.get(bot);
            if(value instanceof int[] a)value=a.clone();
            if(value instanceof double[] a){Double[] finite=new Double[a.length];for(int i=0;i<a.length;i++)finite[i]=Double.isFinite(a[i])?a[i]:null;value=finite;}
            result.put(field.getName(),value);
        }
        for(var method:RolloutBot.class.getMethods())if(method.getParameterCount()==0&&method.getName().startsWith("deadline"))
            result.put(method.getName(),method.invoke(bot));
        return result;
    }
    private static Map<String,Object> verify(RolloutBot policy,V09FullActionProbe probe,GameState s,int me,Map<String,Object> diagnostics)throws Exception {
        Map<String,Object> result=new LinkedHashMap<>();
        require(policy.deadlineNativeTarget==probe.selected().target()
                && Arrays.equals(Arrays.copyOf(policy.deadlineNativeCells,policy.deadlineNativeCount),probe.selected().cells()),"Wrong native diagnostic action");
        int w=((Number)diagnostics.get("deadlineWCompleted")).intValue();
        if(w>0) {
            require(policy.deadlineRoot==probe.metadata().root() && policy.deadlineNominee==probe.metadata().nominatedTarget()
                    && policy.deadlineReplies==probe.metadata().replies() && probe.metadata().ownCount()==0,"Wrong diagnostic context");
            var trace=probe.trace(-1,V09FullActionProbe.Variant.BASELINE);
            checkReplies(policy,"deadlineW",trace.replies(),w,me,true);result.put("W",trace);
        }
        int a=((Number)diagnostics.get("deadlineACompleted")).intValue();
        int wr=((Number)diagnostics.get("deadlineWr0Completed")).intValue();
        if(a>0||wr>0) {
            int target=probe.metadata().nominatedTarget();
            if(a>0){var trace=probe.trace(target,V09FullActionProbe.Variant.BASELINE);checkReplies(policy,"deadlineA",trace.replies(),a,me,false);result.put("A",trace);}
            if(wr>0){var trace=V09DeferredCommitOracle.withoutTarget(probe,s,me,target);checkReplies(policy,"deadlineWr0",trace,wr,me,false);result.put("Wr0",trace);}
        }
        return result;
    }
    private static void checkReplies(RolloutBot policy,String prefix,List<ReplyTrace> traces,int completed,int me,boolean first)throws Exception {
        double[] values=(double[])RolloutBot.class.getField(prefix+"Values").get(policy);
        int[] outcomes=(int[])RolloutBot.class.getField(prefix+"Terminal").get(policy);
        require(completed<=traces.size(),"Too many completed replies");
        for(int i=0;i<completed;i++) {
            var trace=traces.get(i);require(Double.doubleToLongBits(values[i])==Double.doubleToLongBits(trace.value()),"Forecast differs from independent frozen oracle: "+prefix+"/"+i);
            int terminal=0,firstTarget=-1,firstDue=-1;
            for(var t:trace.turns()) {
                if(firstTarget<0 && t.offset()>0 && t.targets()[me]>=0){firstTarget=t.targets()[me];firstDue=t.turn();}
                if(t.terminal()){int diff=t.score()[me]-t.score()[1-me];terminal=diff>0?1:diff<0?-1:2;}
            }
            require(outcomes[i]==terminal,"Wrong terminal diagnostic");
            if(first){require(((int[])RolloutBot.class.getField("deadlineFirstTarget").get(policy))[i]==firstTarget,"Wrong promised target");
                require(((int[])RolloutBot.class.getField("deadlineFirstDue").get(policy))[i]==firstDue,"Wrong promised turn");}
        }
    }
    private static void accepted(Rules.TurnResult result,int me,Plan plan) {
        var move=result.moves[me];require(move.error==null&&move.skipped.isEmpty()&&!result.unknownActions,"Core rejected action");
        require(Arrays.equals(plan.cells(),Arrays.copyOf(move.cells,move.count))&&plan.target()==(move.disrupts.isEmpty()?-1:move.disrupts.get(0)),"Core changed action");
    }
    private static String key(JsonObject row){return row.get("gameId").getAsLong()+"/"+row.get("turn").getAsInt();}
    private static void emit(BufferedWriter writer,Object row)throws Exception {writer.write(V09ContinuationStudy.JSON.toJson(row));writer.newLine();writer.flush();}
    private static Map<String,Object> pin(Path path)throws Exception{return Map.of("path",path.toString(),"sha256",V09ContinuationStudy.hash(path));}

    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("fixedSelection outputDirectory");
        Path selectionPath=Path.of(args[0]),out=Path.of(args[1]);require(!Files.exists(out),"Refuse to overwrite diagnostic output");
        require(V09ContinuationStudy.hash(selectionPath).equals(SELECTION_HASH),"Fixed selection changed");
        JsonObject selection=JsonParser.parseString(Files.readString(selectionPath)).getAsJsonObject();
        JsonObject priorPin=selection.getAsJsonObject("priorSelection");Path priorPath=Path.of(priorPin.get("path").getAsString());
        require(V09ContinuationStudy.hash(priorPath).equals(priorPin.get("sha256").getAsString()),"Prior selection changed");
        JsonObject prior=V09FullActionStudy.selection(priorPath);
        var sources=V09ContinuationStudy.load(Path.of(prior.getAsJsonObject("sources").getAsJsonObject("sourceAudit").get("path").getAsString()));
        Map<String,JsonObject> panel=new LinkedHashMap<>(),starts=new LinkedHashMap<>();
        for(var entry:prior.getAsJsonArray("panel")){var row=entry.getAsJsonObject();panel.put(key(row),row);}
        for(var entry:prior.getAsJsonArray("episodeStarts")){var row=entry.getAsJsonObject();starts.put(key(row),row);}
        Files.createDirectories(out);int positions=0,episodes=0,game=0;
        try(var pw=Files.newBufferedWriter(out.resolve("positions.jsonl"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            var ew=Files.newBufferedWriter(out.resolve("episodes.jsonl"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
            for(var source:sources) {
                GameState s=source.replay().toState();Pathing pathing=new Pathing(s.size);Rules.updateConnections(s,pathing);
                for(int turn=0;turn<source.replay().turns();turn++) {
                    JsonObject row=panel.get(source.replay().gameId+"/"+turn);
                    if(row!=null) {
                        require(ReplyStudy.stateHash(s).equals(row.get("stateSha256").getAsString()),"Pinned state changed");
                        int me=row.get("me").getAsInt();var off=bot(false,s,me);
                        Plan action=V09ContinuationProbe.accepted(s,me,off.play(s.copy(),me,V09ContinuationProbe.AMPLE_MS));
                        require(off.ordinarySearchComplete&&off.networkPhaseComplete,"Incomplete OFF search");
                        V09FullActionStudy.requireSameAction(action,row.getAsJsonObject("previousAmple"));
                        emit(pw,Map.of("selection",row,"action",action,"status","EXACT_OFF"));positions++;
                        if(starts.containsKey(key(row))){emit(ew,Map.of("selection",row,"result",episode(s,me,row.get("primaryTarget").getAsInt())));episodes++;}
                    }
                    Rules.resolve(s,Action.parseLine(source.replay().turnStdout.get(turn)[0]),Action.parseLine(source.replay().turnStdout.get(turn)[1]),pathing);
                }
                require(Arrays.equals(s.score,source.replay().finalScores),"Replay final score mismatch");
                System.out.println("Completed source "+(++game)+"/40; OFF="+positions+"/967; episodes="+episodes+"/56");
            }
        }
        require(positions==967&&episodes==56,"Incomplete fixed panel");
        JsonObject history=prior.getAsJsonObject("historical");CgReplay replay=CgReplay.load(Path.of(history.getAsJsonObject("replay").get("path").getAsString()));
        require(CgValidate.validate(replay,false).firstDivergenceTurn<0,"Historical engine divergence");
        GameState hs=replay.toState();Pathing hp=new Pathing(hs.size);Rules.updateConnections(hs,hp);
        for(int t=0;t<37;t++)Rules.resolve(hs,CgPlayerStudy.effectiveActions(replay,t,0),CgPlayerStudy.effectiveActions(replay,t,1),hp);
        require(ReplyStudy.stateHash(hs).equals(history.get("stateSha256").getAsString()),"Historical state changed");
        V09ContinuationStudy.write(out.resolve("historical.json"),Map.of("selection",history,"result",episode(hs,0,history.get("primaryTarget").getAsInt())));
        Map<String,Object> report=new LinkedHashMap<>();report.put("kind","v09-deferred-commit-v1");report.put("complete",true);report.put("createdAt",Instant.now().toString());
        report.put("selection",pin(selectionPath));report.put("offExactStates",positions);report.put("cohortStarts",episodes);report.put("historicalStarts",1);
        report.put("engineCohortExact",40);report.put("engineHistoricalExact",1);report.put("positions",pin(out.resolve("positions.jsonl")));
        report.put("episodes",pin(out.resolve("episodes.jsonl")));report.put("historical",pin(out.resolve("historical.json")));
        report.put("scope","Exposed development panel; empty memory at each start, persistent per branch; ample time; no strength gate/promotion");
        V09ContinuationStudy.write(out.resolve("report.json"),report);
    }
}
