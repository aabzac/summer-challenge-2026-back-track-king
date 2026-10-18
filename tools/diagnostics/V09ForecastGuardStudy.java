package btk.tools;

import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Seven historical guards use batching OFF: combined batching/deadline/ink export exceeds the source cap. */
public final class V09ForecastGuardStudy {
    private static final Path SELECTION=Path.of("build/analysis/v09-full-action-v1/selection.json");
    private static final String SELECTION_HASH="c6cead6244d5d2fd873f645fde926dd3be1bf6848fa9f7aaab1c6695ba6c2129";
    private static final Path AUDIT=Path.of("docs/evidence/neural-v7-control-boss.json");
    private static final String AUDIT_HASH="bad756d65fbc1cb8d02f29e94b777bc2eae06cf0ea21a4977629dc48c1325981";
    private static final String BASELINE_SOURCE="3bd070b3802744778473e93cebed8148ba07a7b243bd744c8ac486076d304a2c";
    private static final int[] CLOSING={46,47,49,50,51,52};
    private record Start(long gameId,int turn,int me,int target,boolean closing,GameState state,String stateSha256) {}
    private record Artifact(Path directory,Map<String,String> params,Map<String,Object> pins) {}
    private record Step(int offset,int turn,V09ContinuationProbe.Plan ownAction,V09ContinuationProbe.Plan opponentAction,
                        double searchWallMs,Long selectedValueRawBits,Map<String,Object> diagnostics,int[] score,
                        List<Integer> inkedRegions,boolean terminal,String stateBeforeSha256,String stateSha256) {}
    private record Arm(String artifact,boolean complete,int steps,String terminalOutcome,int finalScoreDifference,
                       String finalStateSha256,List<Step> turns) {}
    private V09ForecastGuardStudy() {}
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
    private static Map<String,Object> pin(Path file)throws Exception {
        return Map.of("path",file.toString(),"sha256",V09ContinuationStudy.hash(file),"bytes",Files.size(file));
    }
    private static void write(Path path,Object value)throws Exception {
        Files.writeString(path,V09ContinuationStudy.JSON.toJson(value)+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
    }
    private static Artifact artifact(Path path)throws Exception {
        Path source=path.resolve("Player.java"),manifest=path.resolve("manifest.json"),classes=path.resolve("classes");
        JsonObject doc=JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
        require(V09ContinuationStudy.hash(source).equals(doc.get("sha256").getAsString()),"Source mismatch");
        int size=Files.readString(source).length(); require(size<=100000&&size==doc.get("charactersUtf16").getAsInt(),"Source size mismatch");
        require("current".equals(doc.get("strategyId").getAsString()),"Current-native required");
        Map<String,String> params=new LinkedHashMap<>();
        for(var entry:doc.getAsJsonObject("params").entrySet())params.put(entry.getKey(),entry.getValue().getAsString());
        require(Double.parseDouble(params.get("NOISE"))==0,"Guard controls use NOISE0");
        require(!Boolean.parseBoolean(params.getOrDefault("USE_BATCHED_REFRESH","false")),"Historical guard recipe keeps batching OFF");
        for(String flag:List.of("USE_ROLLOUT_MISSED_DEADLINE","ISOLATED_ROUTE_WARMUP"))
            require("true".equals(params.get(flag)),"Required guard recipe flag "+flag);
        require("36".equals(params.getOrDefault("TURN_BUDGET_MS","36")),"Keep36ms");
        MessageDigest digest=MessageDigest.getInstance("SHA-256"); int count=0;
        try(var stream=Files.walk(classes)) {
            for(Path file:stream.filter(p->p.toString().endsWith(".class")).sorted().toList()) {
                digest.update(classes.relativize(file).toString().replace('\\','/').getBytes(StandardCharsets.UTF_8));
                digest.update((byte)0);digest.update(Files.readAllBytes(file));count++;
            }
        }
        require(count>0&&Files.isRegularFile(classes.resolve("Player.class")),"Missing executable");
        return new Artifact(path,params,Map.of("source",pin(source),"manifest",pin(manifest),"classesSha256",
                HexFormat.of().formatHex(digest.digest()),"classCount",count,"params",params));
    }
    private static List<Start> starts()throws Exception {
        require(V09ContinuationStudy.hash(SELECTION).equals(SELECTION_HASH),"Historical selection changed");
        require(V09ContinuationStudy.hash(AUDIT).equals(AUDIT_HASH),"Historical source audit changed");
        var selection=JsonParser.parseString(Files.readString(SELECTION)).getAsJsonObject();
        var audit=JsonParser.parseString(Files.readString(AUDIT)).getAsJsonObject();
        List<Start> result=new ArrayList<>();
        for(long game:new long[]{907134651L,907134720L}) {
            Path path=null;
            for(var e:audit.getAsJsonArray("rows")) {
                var row=e.getAsJsonObject();if(row.get("gameId").getAsLong()!=game)continue;
                require(path==null,"Duplicate source");var source=row.getAsJsonObject("canonical");path=Path.of(source.get("path").getAsString());
                require(V09ContinuationStudy.hash(path).equals(source.get("sha256").getAsString()),"Replay changed");
            }
            require(path!=null,"Missing replay"); CgReplay replay=CgReplay.load(path);
            require(CgValidate.validate(replay,false).firstDivergenceTurn<0,"Engine divergence");
            GameState state=replay.toState();Pathing pathing=new Pathing(state.size);Rules.updateConnections(state,pathing);
            for(int turn=0;turn<replay.turns();turn++) {
                final int currentTurn=turn;
                boolean selected=game==907134651L?turn==38:Arrays.stream(CLOSING).anyMatch(x->x==currentTurn);
                if(selected) {
                    JsonObject pinned=null;
                    for(var e:selection.getAsJsonArray("episodeStarts")) {
                        var row=e.getAsJsonObject();if(row.get("gameId").getAsLong()==game&&row.get("turn").getAsInt()==turn) {
                            require(pinned==null,"Duplicate start");pinned=row;
                        }
                    }
                    require(pinned!=null&&pinned.get("me").getAsInt()==1,"Missing fixed P1 start");
                    String hash=ReplyStudy.stateHash(state);require(hash.equals(pinned.get("stateSha256").getAsString()),"State changed");
                    result.add(new Start(game,turn,1,pinned.get("primaryTarget").getAsInt(),game==907134720L,state.copy(),hash));
                }
                Rules.resolve(state,CgPlayerStudy.effectiveActions(replay,turn,0),CgPlayerStudy.effectiveActions(replay,turn,1),pathing);
            }
            require(Arrays.equals(state.score,replay.finalScores),"Replay final mismatch");
        }
        require(result.size()==7,"Missing controls");return List.copyOf(result);
    }
    private static Arm arm(Start start,V09FullActionProbe opponent,String regime,int reply,Artifact artifact,long budget,long warmup)throws Exception {
        GameState state=start.state.copy();Pathing pathing=new Pathing(state.size);List<Step> steps=new ArrayList<>();boolean terminal=false;
        int horizon=Math.min(8,state.turnsLeft());
        try(var policy=new GeneratedPolicy(artifact.directory)) {
            policy.init(state,start.me);if(warmup>0)policy.warmup(state,start.me,warmup);
            for(int offset=0;offset<horizon;offset++) {
                String before=ReplyStudy.stateHash(state);int turn=state.turn;
                var raw=policy.play(state,start.me,budget); require(before.equals(ReplyStudy.stateHash(state)),"Policy mutated input");
                var accepted=V09ContinuationStudy.accepted(state,start.me,raw.action());
                var own=new V09ContinuationProbe.Plan(accepted.cells(),accepted.target(),accepted.action());
                var enemy=regime.equals(V09FullActionEpisode.NATIVE)?opponent.opponentAction(state,reply)
                        :V09ContinuationProbe.accepted(state,1-start.me,List.of(Action.waitAction()));
                var resolved=Rules.resolve(state,Action.parseLine(enemy.action()),Action.parseLine(own.action()),pathing);
                for(int p=0;p<2;p++) {
                    var action=p==start.me?own:enemy;var move=resolved.moves[p];
                    require(move.error==null&&move.skipped.isEmpty()&&!resolved.unknownActions,"Rejected guard action");
                    require(Arrays.equals(action.cells(),Arrays.copyOf(move.cells,move.count))&&action.target()==
                            (move.disrupts.isEmpty()?-1:move.disrupts.get(0)),"Accepted action drift");
                }
                terminal=Rules.gameOver(state,pathing);
                Long bits=raw.diagnostics().get("selectedValue") instanceof Double d?Double.doubleToRawLongBits(d):null;
                steps.add(new Step(offset,turn,own,enemy,raw.elapsedMs(),bits,raw.diagnostics(),state.score.clone(),
                        List.copyOf(resolved.inkedRegions),terminal,before,ReplyStudy.stateHash(state)));
                if(terminal)break;
            }
        }
        int diff=state.score[start.me]-state.score[1-start.me];
        return new Arm(artifact.directory.toString(),terminal||steps.size()==horizon,steps.size(),
                terminal?(diff>0?"WIN":diff<0?"LOSS":"DRAW"):"NONTERMINAL",diff,ReplyStudy.stateHash(state),List.copyOf(steps));
    }
    private static void exact(Arm a,Arm b)throws Exception {
        require(a.complete&&b.complete&&a.steps==b.steps&&a.terminalOutcome.equals(b.terminalOutcome)&&
                a.finalScoreDifference==b.finalScoreDifference&&a.finalStateSha256.equals(b.finalStateSha256),"Guard final mismatch");
        for(int i=0;i<a.turns.size();i++) {
            Step x=a.turns.get(i),y=b.turns.get(i);
            for(Step step:List.of(x,y)) {
                boolean complete=Boolean.TRUE.equals(step.diagnostics.get("selectedOpening")) ||
                        Boolean.TRUE.equals(step.diagnostics.get("ordinarySearchComplete")) &&
                        Boolean.TRUE.equals(step.diagnostics.get("networkPhaseComplete"));
                String status=String.valueOf(step.diagnostics.get("deadlineStatus"));
                require(complete&&!status.startsWith("incomplete-")&&!status.equals("timeout"),"Incomplete ample guard search");
            }
            require(x.turn==y.turn&&x.offset==y.offset&&x.ownAction.action().equals(y.ownAction.action())&&
                    Arrays.equals(x.ownAction.cells(),y.ownAction.cells())&&x.ownAction.target()==y.ownAction.target()&&
                    x.opponentAction.action().equals(y.opponentAction.action())&&Arrays.equals(x.score,y.score)&&
                    x.inkedRegions.equals(y.inkedRegions)&&x.terminal==y.terminal&&x.stateBeforeSha256.equals(y.stateBeforeSha256)&&
                    x.stateSha256.equals(y.stateSha256)&&java.util.Objects.equals(x.selectedValueRawBits,y.selectedValueRawBits),"Guard step mismatch "+i);
            require(x.diagnostics.keySet().equals(y.diagnostics.keySet()),"Diagnostic keyset drift");
            for(String key:x.diagnostics.keySet())require(V09BatchedRefreshStudy.semanticEquals(x.diagnostics.get(key),y.diagnostics.get(key)),"Diagnostic mismatch "+key);
        }
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=4)throw new IllegalArgumentException("baselineArtifact candidateArtifact newOutputDirectory performanceFlag");
        Path out=Path.of(args[2]);require(!Files.exists(out),"Refuse to overwrite guard output");
        Artifact a=artifact(Path.of(args[0])),b=artifact(Path.of(args[1]));String flag=args[3];
        require(V09ContinuationStudy.hash(a.directory.resolve("Player.java")).equals(BASELINE_SOURCE),"Historical correction baseline changed");
        require(flag.matches("USE_[A-Z0-9_]+"),"Invalid flag");var ap=new LinkedHashMap<>(a.params);var bp=new LinkedHashMap<>(b.params);
        require(!Boolean.parseBoolean(ap.getOrDefault(flag,"false"))&&"true".equals(bp.get(flag)),"Wrong optimization flags");
        ap.remove(flag);bp.remove(flag);require(ap.equals(bp),"Unrelated parameter drift");List<Start> starts=starts();
        Files.createDirectories(out);Map<String,Object> report=new LinkedHashMap<>();Throwable failure=null;
        report.put("schema","v09-forecast-guard-study-v1");report.put("complete",false);report.put("startedAt",Instant.now().toString());
        report.put("baseline",a.pins);report.put("candidate",b.pins);report.put("selection",pin(SELECTION));report.put("audit",pin(AUDIT));
        report.put("starts",starts.stream().map(s->Map.of("gameId",s.gameId,"turn",s.turn,"me",s.me,"target",s.target,"closing",s.closing,"stateSha256",s.stateSha256)).toList());
        report.put("scope","7 historical starts; native reply identities and passive WAIT; persistent policy memory and absolute turns for8-turn branches. Both arms use original NOISE0/deadlineON/batchingOFF recipe because combined batchingON+deadlineON+incremental export exceeded the100000-character cap before any measured guard call. Only incremental flag differs. All ample5000 semantics exact; nominal36 with isolated500 warmup is mechanism coverage, not external50ms timing or strength. No complete new games; this does not validate the oversized combined guard recipe.");
        try {
            for(int phase=0;phase<2;phase++) {
                String name=phase==0?"ample":"nominal";long budget=phase==0?5000:36,warmup=phase==0?0:500;
                int pairs=0,baselineWins=0,candidateWins=0,lostWins=0;List<Map<String,Object>> keyTurns=new ArrayList<>();
                try(var writer=Files.newBufferedWriter(out.resolve(name+".jsonl"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
                    for(Start start:starts) {
                        var opponent=V09FullActionProbe.prepare(start.state,start.me);require(opponent.metadata().supported(),"Unsupported start");
                        for(int condition=0;condition<=opponent.metadata().replies();condition++) {
                            String regime=condition<opponent.metadata().replies()?V09FullActionEpisode.NATIVE:V09FullActionEpisode.PASSIVE;
                            int reply=condition<opponent.metadata().replies()?condition:-1;Arm left,right;
                            if((pairs&1)==0){left=arm(start,opponent,regime,reply,a,budget,warmup);right=arm(start,opponent,regime,reply,b,budget,warmup);}
                            else{right=arm(start,opponent,regime,reply,b,budget,warmup);left=arm(start,opponent,regime,reply,a,budget,warmup);}
                            var row=Map.of("gameId",start.gameId,"startTurn",start.turn,"me",start.me,"target",start.target,
                                    "regime",regime,"reply",reply,"baseline",left,"candidate",right);
                            writer.write(V09ContinuationStudy.JSON.toJson(row));writer.newLine();writer.flush();pairs++;
                            if(left.terminalOutcome.equals("WIN"))baselineWins++;if(right.terminalOutcome.equals("WIN"))candidateWins++;
                            if(left.terminalOutcome.equals("WIN")&&!right.terminalOutcome.equals("WIN"))lostWins++;
                            if(start.gameId==907134651L)for(int arm=0;arm<2;arm++)for(Step step:(arm==0?left:right).turns)if(step.turn==40)
                                keyTurns.add(Map.of("regime",regime,"reply",reply,"arm",arm==0?"baseline":"candidate","step",step));
                            if(phase==0)exact(left,right);
                        }
                        System.out.println(name+" guard "+start.gameId+"/"+start.turn+"/P1 complete");
                    }
                }
                report.put(name,Map.of("pairs",pairs,"baselineWins",baselineWins,"candidateWins",candidateWins,"lostBaselineWins",lostWins,"keyTurn40",keyTurns));
                report.put(name+"PhaseComplete",true);
            }
            report.put("complete",true);
        }catch(Exception|Error problem){failure=problem;report.put("failure",problem.toString());}
        finally{report.put("finishedAt",Instant.now().toString());write(out.resolve("report.json"),report);}
        if(failure instanceof Exception problem)throw problem;if(failure instanceof Error problem)throw problem;
    }
}
