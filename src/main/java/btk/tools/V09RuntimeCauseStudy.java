package btk.tools;

import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.Duration;
import jdk.jfr.Configuration;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static btk.tools.V09ContinuationStudy.require;

/** Fixed diagnostic-only JFR attribution panel; instrumentation is not a timing or strength gate. */
public final class V09RuntimeCauseStudy {
    private V09RuntimeCauseStudy() {}
    private static final long searchMs=36, warmupMs=500;
    public record Step(int offset,int turn,V09ContinuationProbe.Plan ownAction,
                       V09ContinuationProbe.Plan opponentAction,double searchWallMs,
                       Map<String,Object> diagnostics,ProfileGeneratedPolicy.Timing timing,int[] score,List<Integer> inkedRegions,
                       boolean terminal,String stateBeforeSha256,String stateSha256) {}
    public record Arm(String artifact,boolean complete,int steps,String terminalOutcome,
                      int finalScoreDifference,String finalStateSha256,List<Step> turns) {}
    public record Pair(String opponentRegime,int nativeReply,Arm off,Arm on) {}
    public record Episode(String initialStateSha256,int me,int horizon,int pinnedTarget,List<Pair> comparisons) {}

    static Episode episode(GameState initial,int me,int target,Path off,Path on,String caseId)throws Exception {
        String before=ReplyStudy.stateHash(initial);
        var opponent=V09FullActionProbe.prepare(initial,me);
        require(opponent.metadata().supported(),"Unsupported fixed entry");
        int horizon=Math.min(8,initial.turnsLeft());List<Pair> pairs=new ArrayList<>();
        for(int reply=0;reply<opponent.metadata().replies();reply++)
            pairs.add(pair(initial,me,horizon,opponent,V09FullActionEpisode.NATIVE,reply,off,on,caseId));
        pairs.add(pair(initial,me,horizon,opponent,V09FullActionEpisode.PASSIVE,-1,off,on,caseId));
        require(before.equals(ReplyStudy.stateHash(initial)),"Runtime episode mutated source");
        return new Episode(before,me,horizon,target,List.copyOf(pairs));
    }
    private static Pair pair(GameState initial,int me,int horizon,V09FullActionProbe opponent,
                             String regime,int reply,Path off,Path on,String caseId)throws Exception {
        return new Pair(regime,reply,arm(initial,me,horizon,opponent,regime,reply,off,caseId,"OFF"),
                arm(initial,me,horizon,opponent,regime,reply,on,caseId,"ON"));
    }
    private static Arm arm(GameState initial,int me,int horizon,V09FullActionProbe opponent,
                            String regime,int reply,Path artifact,String caseId,String armName)throws Exception {
        GameState s=initial.copy();Pathing pathing=new Pathing(s.size);List<Step> steps=new ArrayList<>();
        boolean terminal=false;
        try(var policy=new ProfileGeneratedPolicy(artifact)) {
            policy.init(s,me);policy.warmup(s,me,warmupMs);
            for(int offset=0;offset<horizon;offset++) {
                String before=ReplyStudy.stateHash(s);int turn=s.turn;
                var decision=policy.play(s,me,searchMs,caseId+"/"+regime+"/"+reply+"/"+armName+"/"+offset);
                var legal=V09ContinuationStudy.accepted(s,me,decision.action());
                var own=new V09ContinuationProbe.Plan(legal.cells(),legal.target(),legal.action());
                var enemy=regime.equals(V09FullActionEpisode.NATIVE)?opponent.opponentAction(s,reply)
                        :V09ContinuationProbe.accepted(s,1-me,List.of(Action.waitAction()));
                var resolved=me==0?Rules.resolve(s,Action.parseLine(own.action()),Action.parseLine(enemy.action()),pathing)
                        :Rules.resolve(s,Action.parseLine(enemy.action()),Action.parseLine(own.action()),pathing);
                accepted(resolved,me,own);accepted(resolved,1-me,enemy);
                terminal=Rules.gameOver(s,pathing);
                steps.add(new Step(offset,turn,own,enemy,decision.elapsedMs(),decision.diagnostics(),decision.timing(),s.score.clone(),
                        List.copyOf(resolved.inkedRegions),terminal,before,ReplyStudy.stateHash(s)));
                if(terminal)break;
            }
        }
        int difference=s.score[me]-s.score[1-me];
        return new Arm(artifact.toString(),terminal||steps.size()==horizon,steps.size(),
                terminal?(difference>0?"WIN":difference<0?"LOSS":"DRAW"):"NONTERMINAL",
                difference,ReplyStudy.stateHash(s),List.copyOf(steps));
    }
    private static void accepted(Rules.TurnResult result,int me,V09ContinuationProbe.Plan plan) {
        var move=result.moves[me];require(move.error==null&&move.skipped.isEmpty()&&!result.unknownActions,"Core rejected runtime action");
        require(Arrays.equals(plan.cells(),Arrays.copyOf(move.cells,move.count))
                &&plan.target()==(move.disrupts.isEmpty()?-1:move.disrupts.get(0)),"Core changed runtime action");
    }
    private static String key(JsonObject row){return row.get("gameId").getAsLong()+"/"+row.get("turn").getAsInt();}
    private static void emit(BufferedWriter writer,Object row)throws Exception {
        writer.write(V09ContinuationStudy.JSON.toJson(row));writer.newLine();writer.flush();
    }
    private static Map<String,Object> pin(Path path)throws Exception {
        return Map.of("path",path.toString(),"sha256",V09ContinuationStudy.hash(path));
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=4)throw new IllegalArgumentException("causeSelection offArtifact onArtifact outputDirectory");
        Path selection=Path.of(args[0]),off=Path.of(args[1]),on=Path.of(args[2]),out=Path.of(args[3]);
        require(!Files.exists(out),"Refuse to overwrite diagnostic output");
        require(JsonParser.parseString(Files.readString(selection)).getAsJsonObject().get("finalized").getAsBoolean(),"Finalize protocol before profiling");
        require(V09ContinuationStudy.hash(off.resolve("Player.java")).equals("dfbe780475bb4f0af3267caad8001c5d5e12ef264e40fc6707bd8b3a31d9b736"),"Exact OFF source changed");
        require(V09ContinuationStudy.hash(on.resolve("Player.java")).equals("3bd070b3802744778473e93cebed8148ba07a7b243bd744c8ac486076d304a2c"),"Exact reuse-v1 ON source changed");
        Files.createDirectories(out);
        try(Recording recording=new Recording(Configuration.getConfiguration("profile"))) {
            recording.setName("v09-runtime-cause-v1-nominal");recording.setToDisk(true);
            recording.enable(ProfileGeneratedPolicy.PlayEvent.class).withoutStackTrace().withThreshold(Duration.ZERO);
            var supported=new java.util.HashSet<String>();
            for(var type:FlightRecorder.getFlightRecorder().getEventTypes())supported.add(type.getName());
            List<String> enabled=new ArrayList<>(),unsupported=new ArrayList<>();
            for(String name:new String[]{"jdk.ExecutionSample","jdk.GCPhasePause","jdk.GarbageCollection","jdk.SafepointBegin",
                    "jdk.SafepointEnd","jdk.SafepointStateSynchronization","jdk.SafepointCleanup",
                    "jdk.Compilation","jdk.ExecuteVMOperation"}) {
                if(!supported.contains(name)){unsupported.add(name);continue;}
                if(name.equals("jdk.ExecutionSample"))recording.enable(name).withPeriod(Duration.ofMillis(1));
                else recording.enable(name).withThreshold(Duration.ZERO);
                enabled.add(name);
            }
            V09ContinuationStudy.write(out.resolve("event-availability.json"),Map.of("enabled",enabled,
                    "unsupported",unsupported,"customEvent","btk.GeneratedPolicyPlay",
                    "interpretation","Unsupported event names are unavailable evidence, not zero observed events."));
            V09ContinuationStudy.write(out.resolve("recording-settings.json"),recording.getSettings());
            recording.start();
            try { run(args); }
            finally {
                recording.stop();
                Path recordingFile=out.resolve("profile.jfr");recording.dump(recordingFile);
                V09ContinuationStudy.write(out.resolve("recording.json"),Map.of("recording",pin(recordingFile),
                        "settings",pin(out.resolve("recording-settings.json")),
                        "eventAvailability",pin(out.resolve("event-availability.json")),"method",
                        "Custom btk.GeneratedPolicyPlay brackets only generated reflective play; preparation,formatting,diagnostics and counter reads remain outside. CPU deltas are coarse Windows main-thread accounting, GC totals JVM-wide; JFR pause overlap and samples are diagnostic, not a timing gate."));
            }
        }
    }

    private static void run(String[] args)throws Exception {
        Path selectionPath=Path.of(args[0]),off=Path.of(args[1]),on=Path.of(args[2]),out=Path.of(args[3]);
        require(V09ContinuationStudy.hash(Path.of("build/analysis/v09-deferred-forecast-reuse-v1/selection.json")).equals("85f081b6997d73cd3d1128ddc3a5258111478477b7eb389c3fe4905a3dd4d622"),"Predecessor selection changed");
        JsonObject runtime=JsonParser.parseString(Files.readString(selectionPath)).getAsJsonObject();
        require(runtime.get("finalized").getAsBoolean(),"Protocol must be finalized before timing");
        // The predecessor validates all selection/source pins and fixes the original56 starts.
        Path oldPath=Path.of("build/analysis/v09-deferred-commit-v1/selection.json");
        require(V09ContinuationStudy.hash(oldPath).equals("a04858f4023c1363f3170250d0c39f4e0044323c1d697c08c73c463a004a2c38"),"Old selection changed");
        JsonObject old=JsonParser.parseString(Files.readString(oldPath)).getAsJsonObject();
        JsonObject prior=V09FullActionStudy.selection(Path.of(old.getAsJsonObject("priorSelection").get("path").getAsString()));
        var sources=V09ContinuationStudy.load(Path.of(prior.getAsJsonObject("sources").getAsJsonObject("sourceAudit").get("path").getAsString()));
        Map<String,JsonObject> starts=new LinkedHashMap<>();
        for(var entry:prior.getAsJsonArray("episodeStarts")){var row=entry.getAsJsonObject();starts.put(key(row),row);}
        Files.createDirectories(out);int episodes=0,pairs=0;
        try(var writer=Files.newBufferedWriter(out.resolve("episodes.jsonl"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
            for(var source:sources) {
                GameState s=source.replay().toState();Pathing pathing=new Pathing(s.size);Rules.updateConnections(s,pathing);
                for(int turn=0;turn<source.replay().turns();turn++) {
                    JsonObject row=starts.get(source.replay().gameId+"/"+turn);
                    if(row!=null) {
                        require(ReplyStudy.stateHash(s).equals(row.get("stateSha256").getAsString()),"Pinned runtime state changed");
                        var result=episode(s,row.get("me").getAsInt(),row.get("primaryTarget").getAsInt(),off,on,key(row)+"/P"+row.get("me").getAsInt());
                        var output=Map.of("selection",row,"result",result);
                        emit(writer,output);episodes++;pairs+=result.comparisons().size();
                        System.out.println("Diagnostic attribution starts "+episodes+"/56; pairs "+pairs+"/144");
                    }
                    Rules.resolve(s,Action.parseLine(source.replay().turnStdout.get(turn)[0]),Action.parseLine(source.replay().turnStdout.get(turn)[1]),pathing);
                }
                require(Arrays.equals(s.score,source.replay().finalScores),"Replay score mismatch");
            }
        }
        require(episodes==56&&pairs==144,"Incomplete runtime cohort");
        JsonObject history=prior.getAsJsonObject("historical");
        CgReplay replay=CgReplay.load(Path.of(history.getAsJsonObject("replay").get("path").getAsString()));
        require(CgValidate.validate(replay,false).firstDivergenceTurn<0,"Historical engine divergence");
        GameState hs=replay.toState();Pathing hp=new Pathing(hs.size);Rules.updateConnections(hs,hp);
        for(int t=0;t<37;t++)Rules.resolve(hs,CgPlayerStudy.effectiveActions(replay,t,0),CgPlayerStudy.effectiveActions(replay,t,1),hp);
        require(ReplyStudy.stateHash(hs).equals(history.get("stateSha256").getAsString()),"Historical runtime state changed");
        var historical=episode(hs,0,history.get("primaryTarget").getAsInt(),off,on,replay.gameId+"/37/P0");
        require(historical.comparisons().size()==3,"Incomplete history");
        var historicalOutput=Map.of("selection",history,"result",historical);
        V09ContinuationStudy.write(out.resolve("historical.json"),historicalOutput);
        forcedInputProbe(sources,on,out);
        Map<String,Object> report=new LinkedHashMap<>();report.put("kind","v09-runtime-cause-v1");
        report.put("complete",true);report.put("createdAt",Instant.now().toString());report.put("selection",pin(selectionPath));
        report.put("offSource",pin(off.resolve("Player.java")));report.put("onSource",pin(on.resolve("Player.java")));
        report.put("stage","attribution");report.put("searchBudgetMs",searchMs);report.put("warmupMs",warmupMs);report.put("cohortStarts",episodes);
        report.put("cohortComparisons",pairs);report.put("historicalStarts",1);report.put("historicalComparisons",3);
        report.put("episodes",pin(out.resolve("episodes.jsonl")));report.put("historical",pin(out.resolve("historical.json")));
        report.put("forcedInputProbe",pin(out.resolve("forced-input.json")));
        report.put("scope","Diagnostic-only instrumented exact OFF/ON exported classes; original57/147 panel,serial OFF then ON,36ms/500ms,fresh loader per arm with persistent memory. JFR+CPU/GC observations have overhead and do not amend previous timing failures or establish readiness/strength. CPU counter resolution is platform-dependent; GC counters are JVM-wide coarse totals.");
        V09ContinuationStudy.write(out.resolve("report.json"),report);
    }

    /** Separate teacher-forced attribution: recorded actions, never current outputs, advance the inputs. */
    private static void forcedInputProbe(List<V09ContinuationStudy.Source> sources,Path on,Path out)throws Exception {
        Path retained=Path.of("build/analysis/v09-deferred-forecast-reuse-v1/run/episodes.jsonl");
        require(V09ContinuationStudy.hash(retained).equals("886ffc258fa3d69843692b852786678ffb5af6d8f35185c085aa6347d6e7184e"),
                "Retained forced-input trace changed");
        JsonObject oldArm=null;
        try(var reader=Files.newBufferedReader(retained)) {
            for(String line;(line=reader.readLine())!=null;) {
                var row=JsonParser.parseString(line).getAsJsonObject();var choice=row.getAsJsonObject("selection");
                if(choice.get("gameId").getAsLong()!=907135435L||choice.get("turn").getAsInt()!=59)continue;
                require(choice.get("me").getAsInt()==1,"Unexpected frozen-input seat");
                for(var entry:row.getAsJsonObject("result").getAsJsonArray("comparisons")) {
                    var pair=entry.getAsJsonObject();
                    if(pair.get("opponentRegime").getAsString().equals(V09FullActionEpisode.PASSIVE)) {
                        require(oldArm==null,"Duplicate frozen-input trace");oldArm=pair.getAsJsonObject("on");
                    }
                }
            }
        }
        require(oldArm!=null&&oldArm.get("complete").getAsBoolean()&&oldArm.get("steps").getAsInt()==8,
                "Missing complete frozen-input trace");
        V09ContinuationStudy.Source source=null;
        for(var candidate:sources)if(candidate.replay().gameId==907135435L)source=candidate;
        require(source!=null,"Missing frozen-input source replay");
        GameState state=source.replay().toState();Pathing pathing=new Pathing(state.size);Rules.updateConnections(state,pathing);
        for(int turn=0;turn<59;turn++)Rules.resolve(state,
                Action.parseLine(source.replay().turnStdout.get(turn)[0]),
                Action.parseLine(source.replay().turnStdout.get(turn)[1]),pathing);
        List<GameState> inputs=new ArrayList<>();
        var oldTurns=oldArm.getAsJsonArray("turns");
        require(oldTurns.size()==8,"Frozen-input horizon changed");
        // Validate every board and successor before any profiling decision on this trace.
        for(int offset=0;offset<8;offset++) {
            var prior=oldTurns.get(offset).getAsJsonObject();
            require(state.turn==59+offset&&prior.get("turn").getAsInt()==state.turn
                    &&prior.get("offset").getAsInt()==offset,"Frozen-input chronology changed");
            require(ReplyStudy.stateHash(state).equals(prior.get("stateBeforeSha256").getAsString()),
                    "Frozen input hash mismatch at "+state.turn);
            inputs.add(state.copy());
            var own=recordedPlan(state,1,prior.getAsJsonObject("ownAction"));
            var enemy=recordedPlan(state,0,prior.getAsJsonObject("opponentAction"));
            require(enemy.cells().length==0&&enemy.target()<0,"Frozen opponent is not passive");
            var resolved=Rules.resolve(state,Action.parseLine(enemy.action()),Action.parseLine(own.action()),pathing);
            accepted(resolved,1,own);accepted(resolved,0,enemy);
            require(ReplyStudy.stateHash(state).equals(prior.get("stateSha256").getAsString()),
                    "Frozen successor hash mismatch at "+prior.get("turn"));
            require(Arrays.equals(state.score,V09ContinuationStudy.JSON.fromJson(prior.get("score"),int[].class)),
                    "Frozen successor score mismatch");
            require(Rules.gameOver(state,pathing)==prior.get("terminal").getAsBoolean(),"Frozen terminal mismatch");
        }
        require(ReplyStudy.stateHash(state).equals(oldArm.get("finalStateSha256").getAsString()),"Frozen final state mismatch");
        List<Map<String,Object>> steps=new ArrayList<>();
        try(var policy=new ProfileGeneratedPolicy(on)) {
            policy.init(inputs.get(0),1);policy.warmup(inputs.get(0),1,warmupMs);
            for(int offset=0;offset<8;offset++) {
                var input=inputs.get(offset);var prior=oldTurns.get(offset).getAsJsonObject();
                String before=ReplyStudy.stateHash(input);
                var decision=policy.play(input,1,searchMs,"frozen-input/907135435/59/P1/ON/"+offset);
                var emitted=V09ContinuationStudy.accepted(input,1,decision.action());
                require(before.equals(ReplyStudy.stateHash(input)),"Profiling changed frozen input");
                Map<String,Object> step=new LinkedHashMap<>();
                step.put("offset",offset);step.put("turn",input.turn);step.put("emittedAction",emitted);
                step.put("appliedRecordedOwnAction",prior.get("ownAction"));
                step.put("appliedRecordedOpponentAction",prior.get("opponentAction"));
                step.put("stateBeforeSha256",before);step.put("recordedStateAfterSha256",prior.get("stateSha256"));
                step.put("recordedSearchWallMs",prior.get("searchWallMs"));
                step.put("searchWallMs",decision.elapsedMs());step.put("timing",decision.timing());
                step.put("diagnostics",decision.diagnostics());
                step.put("emittedMatchesRecorded",emitted.action().equals(prior.getAsJsonObject("ownAction").get("action").getAsString()));
                steps.add(step);
                // Retain partial diagnostic evidence if a subsequent call fails. Never retry it.
                writeForcedInput(out,retained,source.path(),on,steps);
            }
        }
    }

    private static V09ContinuationProbe.Plan recordedPlan(GameState state,int me,JsonObject row) {
        var accepted=V09ContinuationStudy.accepted(state,me,row.get("action").getAsString());
        require(accepted.target()==row.get("target").getAsInt()
                &&Arrays.equals(accepted.cells(),V09ContinuationStudy.JSON.fromJson(row.get("cells"),int[].class)),
                "Recorded action disagrees with canonical expansion");
        return new V09ContinuationProbe.Plan(accepted.cells(),accepted.target(),accepted.action());
    }

    private static void writeForcedInput(Path out,Path retained,Path replay,Path on,List<Map<String,Object>> steps)throws Exception {
        Map<String,Object> report=new LinkedHashMap<>();
        report.put("kind","teacher-forced-input-attribution");report.put("complete",steps.size()==8);
        report.put("sourceTrace",pin(retained));report.put("sourceReplay",pin(replay));report.put("artifact",pin(on.resolve("Player.java")));
        report.put("gameId",907135435L);report.put("startTurn",59);report.put("me",1);
        report.put("searchBudgetMs",searchMs);report.put("warmupMs",warmupMs);report.put("verifiedInputAndSuccessorHashes",8);
        report.put("scope","One fixed retained ON PURE_WAIT input trace. All successors follow recorded actions, not the new emitted choices; generated memory persists on these teacher-forced inputs. Exact historical boards, different JVM history. Excluded from mechanism, strength and timing-readiness totals.");
        report.put("turns",steps);V09ContinuationStudy.write(out.resolve("forced-input.json"),report);
    }
}
