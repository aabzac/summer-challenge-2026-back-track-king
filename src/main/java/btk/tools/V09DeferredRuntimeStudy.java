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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static btk.tools.V09ContinuationStudy.require;

/** One fixed nominal-budget panel of exact exported classes; separate from process deadlines. */
public final class V09DeferredRuntimeStudy {
    private V09DeferredRuntimeStudy() {}
    static final long SEARCH_MS=36, WARMUP_MS=500;
    public record Step(int offset,int turn,V09ContinuationProbe.Plan ownAction,
                       V09ContinuationProbe.Plan opponentAction,double searchWallMs,
                       Map<String,Object> diagnostics,int[] score,List<Integer> inkedRegions,
                       boolean terminal,String stateBeforeSha256,String stateSha256) {}
    public record Arm(String artifact,boolean complete,int steps,String terminalOutcome,
                      int finalScoreDifference,String finalStateSha256,List<Step> turns) {}
    public record Pair(String opponentRegime,int nativeReply,Arm off,Arm on) {}
    public record Episode(String initialStateSha256,int me,int horizon,int pinnedTarget,List<Pair> comparisons) {}

    static Episode episode(GameState initial,int me,int target,Path off,Path on)throws Exception {
        String before=ReplyStudy.stateHash(initial);
        var opponent=V09FullActionProbe.prepare(initial,me);
        require(opponent.metadata().supported(),"Unsupported fixed entry");
        int horizon=Math.min(8,initial.turnsLeft());List<Pair> pairs=new ArrayList<>();
        for(int reply=0;reply<opponent.metadata().replies();reply++)
            pairs.add(pair(initial,me,horizon,opponent,V09FullActionEpisode.NATIVE,reply,off,on));
        pairs.add(pair(initial,me,horizon,opponent,V09FullActionEpisode.PASSIVE,-1,off,on));
        require(before.equals(ReplyStudy.stateHash(initial)),"Runtime episode mutated source");
        return new Episode(before,me,horizon,target,List.copyOf(pairs));
    }
    private static Pair pair(GameState initial,int me,int horizon,V09FullActionProbe opponent,
                             String regime,int reply,Path off,Path on)throws Exception {
        return new Pair(regime,reply,arm(initial,me,horizon,opponent,regime,reply,off),
                arm(initial,me,horizon,opponent,regime,reply,on));
    }
    private static Arm arm(GameState initial,int me,int horizon,V09FullActionProbe opponent,
                            String regime,int reply,Path artifact)throws Exception {
        GameState s=initial.copy();Pathing pathing=new Pathing(s.size);List<Step> steps=new ArrayList<>();
        boolean terminal=false;
        try(var policy=new GeneratedPolicy(artifact)) {
            policy.init(s,me);policy.warmup(s,me,WARMUP_MS);
            for(int offset=0;offset<horizon;offset++) {
                String before=ReplyStudy.stateHash(s);int turn=s.turn;
                var decision=policy.play(s,me,SEARCH_MS);
                var legal=V09ContinuationStudy.accepted(s,me,decision.action());
                var own=new V09ContinuationProbe.Plan(legal.cells(),legal.target(),legal.action());
                var enemy=regime.equals(V09FullActionEpisode.NATIVE)?opponent.opponentAction(s,reply)
                        :V09ContinuationProbe.accepted(s,1-me,List.of(Action.waitAction()));
                var resolved=me==0?Rules.resolve(s,Action.parseLine(own.action()),Action.parseLine(enemy.action()),pathing)
                        :Rules.resolve(s,Action.parseLine(enemy.action()),Action.parseLine(own.action()),pathing);
                accepted(resolved,me,own);accepted(resolved,1-me,enemy);
                terminal=Rules.gameOver(s,pathing);
                steps.add(new Step(offset,turn,own,enemy,decision.elapsedMs(),decision.diagnostics(),s.score.clone(),
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
        if(args.length!=4)throw new IllegalArgumentException("runtimeSelection offArtifact onArtifact outputDirectory");
        Path selectionPath=Path.of(args[0]),off=Path.of(args[1]),on=Path.of(args[2]),out=Path.of(args[3]);
        require(!Files.exists(out),"Refuse to overwrite measured runtime output");
        require(V09ContinuationStudy.hash(selectionPath).equals("4acf8a912571ad6c95edcaf9521040dedb87fb2cbb16e75678f09313e8ba521e"),"Runtime selection changed");
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
                        var result=episode(s,row.get("me").getAsInt(),row.get("primaryTarget").getAsInt(),off,on);
                        emit(writer,Map.of("selection",row,"result",result));episodes++;pairs+=result.comparisons().size();
                        System.out.println("Nominal runtime starts "+episodes+"/56; pairs "+pairs+"/144");
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
        var historical=episode(hs,0,history.get("primaryTarget").getAsInt(),off,on);
        require(historical.comparisons().size()==3,"Incomplete history");
        V09ContinuationStudy.write(out.resolve("historical.json"),Map.of("selection",history,"result",historical));
        Map<String,Object> report=new LinkedHashMap<>();report.put("kind","v09-deferred-commit-runtime-v1");
        report.put("complete",true);report.put("createdAt",Instant.now().toString());report.put("selection",pin(selectionPath));
        report.put("offSource",pin(off.resolve("Player.java")));report.put("onSource",pin(on.resolve("Player.java")));
        report.put("searchBudgetMs",SEARCH_MS);report.put("warmupMs",WARMUP_MS);report.put("cohortStarts",episodes);
        report.put("cohortComparisons",pairs);report.put("historicalStarts",1);report.put("historicalComparisons",3);
        report.put("episodes",pin(out.resolve("episodes.jsonl")));report.put("historical",pin(out.resolve("historical.json")));
        report.put("scope","Exact exported classes; fresh loader and bot per arm,500ms warmup then persistent memory; nominal36ms play only; no protocol/50ms guarantee or strength claim");
        V09ContinuationStudy.write(out.resolve("report.json"),report);
    }
}
