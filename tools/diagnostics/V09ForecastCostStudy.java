package btk.tools;

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
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingFile;

/** Separately compiled tools-only fixed saved-state profiling and exact-policy comparison. */
public final class V09ForecastCostStudy {
    private static final long[] GAMES = {907134258L, 907134581L, 907134651L, 907134720L};
    private static final long AMPLE_MS = 5000, WARMUP_MS = 500;
    private static final int EXPECTED = 524;
    private static final String BASE_SOURCE = "2cb3a6a254fd8f37e0c9f07e2aa36565f4d61027ee44f508a5f64a93d547d61a";
    private static final String AUDIT_HASH = "bad756d65fbc1cb8d02f29e94b777bc2eae06cf0ea21a4977629dc48c1325981";
    private record Source(long gameId, List<GameState> states, Map<String,Object> pin) {}
    private record Artifact(Path directory, Map<String,String> params, Map<String,Object> pins) {}
    private record Decision(String action, int[] cells, int target, double wallMs, Long selectedValueRawBits,
                            boolean completeSearch, Map<String,Object> diagnostics) {}
    private record Window(long thread, Instant start, Instant end, String correlation) {}
    private record Sample(long thread, Instant time, List<String> methods, boolean generatedPlay) {}
    private V09ForecastCostStudy() {}
    private static void require(boolean ok, String why) { if (!ok) throw new IllegalStateException(why); }
    private static Map<String,Object> pin(Path file) throws Exception {
        return Map.of("path",file.toString(),"sha256",V09ContinuationStudy.hash(file),"bytes",Files.size(file));
    }
    private static void write(Path path, Object value) throws Exception {
        Files.writeString(path,V09ContinuationStudy.JSON.toJson(value)+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
    }
    private static void emit(BufferedWriter writer, Object value) throws Exception {
        writer.write(V09ContinuationStudy.JSON.toJson(value)); writer.newLine(); writer.flush();
    }
    private static Artifact artifact(Path directory, boolean baseline) throws Exception {
        Path source=directory.resolve("Player.java"), manifest=directory.resolve("manifest.json"), classes=directory.resolve("classes");
        JsonObject document=JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
        String sourceHash=V09ContinuationStudy.hash(source);
        require(sourceHash.equals(document.get("sha256").getAsString()),"Source/manifest mismatch");
        if (baseline) require(sourceHash.equals(BASE_SOURCE),"Retained baseline source changed");
        int chars=Files.readString(source,StandardCharsets.UTF_8).length();
        require(chars<=100000 && chars==document.get("charactersUtf16").getAsInt(),"Invalid source size");
        require(document.get("strategyId").getAsString().equals("current"),"Native current required");
        Map<String,String> params=new LinkedHashMap<>();
        for(var field:document.getAsJsonObject("params").entrySet())params.put(field.getKey(),field.getValue().getAsString());
        require("0.1".equals(params.get("NOISE")),"Keep NOISE0.1");
        require("true".equals(params.get("USE_BATCHED_REFRESH")),"Keep batching ON");
        require("true".equals(params.get("ISOLATED_ROUTE_WARMUP")),"Keep isolated warmup");
        require(!Boolean.parseBoolean(params.getOrDefault("USE_ROLLOUT_MISSED_DEADLINE","false")),"Native profile keeps correction OFF");
        require("36".equals(params.getOrDefault("TURN_BUDGET_MS","36")),"Keep36ms recipe");
        MessageDigest digest=MessageDigest.getInstance("SHA-256"); int count=0;
        try(var paths=Files.walk(classes)) {
            for(Path file:paths.filter(p->p.toString().endsWith(".class")).sorted().toList()) {
                digest.update(classes.relativize(file).toString().replace('\\','/').getBytes(StandardCharsets.UTF_8));
                digest.update((byte)0); digest.update(Files.readAllBytes(file)); count++;
            }
        }
        require(count>0 && Files.isRegularFile(classes.resolve("Player.class")),"Missing generated classes");
        return new Artifact(directory,params,Map.of("source",pin(source),"manifest",pin(manifest),"classesSha256",
                HexFormat.of().formatHex(digest.digest()),"classCount",count,"params",params));
    }
    private static List<Source> sources(Path audit) throws Exception {
        require(V09ContinuationStudy.hash(audit).equals(AUDIT_HASH),"Fixed canonical audit changed");
        var document=JsonParser.parseString(Files.readString(audit)).getAsJsonObject();
        require(document.get("complete").getAsBoolean(),"Incomplete audit");
        List<Source> result=new ArrayList<>(); int expected=0;
        for(long id:GAMES) {
            JsonObject selected=null;
            for(var entry:document.getAsJsonArray("rows")) {
                var row=entry.getAsJsonObject(); if(row.get("gameId").getAsLong()!=id)continue;
                require(selected==null,"Duplicate fixed game"); selected=row;
            }
            require(selected!=null,"Missing fixed game "+id); var canonical=selected.getAsJsonObject("canonical");
            Path file=Path.of(canonical.get("path").getAsString());
            require(V09ContinuationStudy.hash(file).equals(canonical.get("sha256").getAsString()),"Changed replay");
            CgReplay replay=CgReplay.load(file); require(replay.gameId==id,"Wrong game");
            require(CgValidate.validate(replay,false).firstDivergenceTurn<0,"Engine divergence");
            GameState state=replay.toState(); Pathing pathing=new Pathing(state.size); Rules.updateConnections(state,pathing);
            List<GameState> inputs=new ArrayList<>();
            for(int turn=0;turn<replay.turns();turn++) {
                require(replay.failure(turn,0)==null && replay.failure(turn,1)==null,"Recorded failure");
                require(!Rules.gameOver(state,pathing),"Unexpected terminal observed input");
                inputs.add(state.copy());
                Rules.resolve(state,CgPlayerStudy.effectiveActions(replay,turn,0),CgPlayerStudy.effectiveActions(replay,turn,1),pathing);
            }
            require(Arrays.equals(state.score,replay.finalScores),"Final score mismatch");
            expected+=2*inputs.size(); result.add(new Source(id,List.copyOf(inputs),pin(file)));
        }
        require(expected==EXPECTED,"Fixed state count changed: "+expected); return List.copyOf(result);
    }
    private static Decision decision(GameState state, int me, String text, double wall, Map<String,Object> diagnostics) {
        for(String name:List.of("routeQueries","completedComparisons","simulatedTurns","ordinarySearchComplete",
                "networkPhaseComplete","selectedOpening","selectedValue","selectedRoute","selectedTarget"))
            require(diagnostics.containsKey(name),"Missing diagnostic "+name);
        var accepted=V09ContinuationStudy.accepted(state,me,text);
        Long bits=diagnostics.get("selectedValue") instanceof Double value?Double.doubleToRawLongBits(value):null;
        boolean complete=Boolean.TRUE.equals(diagnostics.get("selectedOpening")) ||
                Boolean.TRUE.equals(diagnostics.get("ordinarySearchComplete")) && Boolean.TRUE.equals(diagnostics.get("networkPhaseComplete"));
        return new Decision(accepted.action(),accepted.cells(),accepted.target(),wall,bits,complete,diagnostics);
    }
    private static List<String> differences(Decision a, Decision b) {
        List<String> result=new ArrayList<>();
        if(!a.action.equals(b.action))result.add("orderedAction");
        if(!Arrays.equals(a.cells,b.cells)||a.target!=b.target)result.add("acceptedAction");
        if(!java.util.Objects.equals(a.selectedValueRawBits,b.selectedValueRawBits))result.add("selectedValueRawBits");
        if(!a.diagnostics.keySet().equals(b.diagnostics.keySet()))result.add("diagnosticKeyset");
        for(String key:new TreeSet<>(a.diagnostics.keySet()))
            if(!b.diagnostics.containsKey(key)||!V09BatchedRefreshStudy.semanticEquals(a.diagnostics.get(key),b.diagnostics.get(key)))result.add(key);
        return result;
    }
    private static void totals(Map<String,Map<String,Object>> all,String key,Decision d,long budget) {
        var row=all.computeIfAbsent(key,k->new LinkedHashMap<>());
        for(String field:List.of("calls","completeSearch","aboveBudget","above50Ms","routeQueries","completedComparisons",
                "simulatedTurns","ordinarySearchComplete","networkPhaseComplete")) {
            long n=switch(field) {
                case "calls" -> 1; case "completeSearch" -> d.completeSearch?1:0;
                case "aboveBudget" -> d.wallMs>budget?1:0; case "above50Ms" -> d.wallMs>50?1:0;
                case "ordinarySearchComplete","networkPhaseComplete" -> Boolean.TRUE.equals(d.diagnostics.get(field))?1:0;
                default -> ((Number)d.diagnostics.get(field)).longValue();
            };
            row.put(field,((Number)row.getOrDefault(field,0L)).longValue()+n);
        }
        row.put("totalWallMs",((Number)row.getOrDefault("totalWallMs",0d)).doubleValue()+d.wallMs);
        row.put("maxWallMs",Math.max(((Number)row.getOrDefault("maxWallMs",0d)).doubleValue(),d.wallMs));
    }
    public static void main(String[] args) throws Exception {
        boolean profile=args.length==4 && args[0].equals("profile");
        boolean compare=args.length==6 && args[0].equals("compare");
        if(!profile&&!compare)throw new IllegalArgumentException("profile audit baselineArtifact newOutputDirectory | compare audit baselineArtifact candidateArtifact newOutputDirectory performanceFlag");
        Path audit=Path.of(args[1]),out=Path.of(args[profile?3:4]);
        require(!Files.exists(out),"Refuse to overwrite experiment output");
        List<Source> sources=sources(audit); Artifact baseline=artifact(Path.of(args[2]),true);
        Artifact candidate=compare?artifact(Path.of(args[3]),false):null;
        if(compare) {
            String flag=args[5]; require(flag.matches("USE_[A-Z0-9_]+"),"Invalid performance flag");
            var left=new LinkedHashMap<>(baseline.params); var right=new LinkedHashMap<>(candidate.params);
            require(!Boolean.parseBoolean(left.getOrDefault(flag,"false")) && "true".equals(right.get(flag)),"Expected opt-in false to true");
            left.remove(flag); right.remove(flag); require(left.equals(right),"Unrelated parameter difference");
        }
        Files.createDirectories(out);
        Map<String,Object> report=new LinkedHashMap<>(); Map<String,Map<String,Object>> aggregate=new LinkedHashMap<>();
        report.put("schema","v09-forecast-cost-study-v1"); report.put("stage",args[0]); report.put("startedAt",Instant.now().toString());
        report.put("complete",false); report.put("gameIds",GAMES); report.put("expectedPositions",EXPECTED);
        report.put("audit",pin(audit)); report.put("sources",sources.stream().map(Source::pin).toList());
        report.put("baseline",baseline.pins); if(compare)report.put("candidate",candidate.pins);
        report.put("ampleBudgetMs",AMPLE_MS); report.put("nominalWarmupMs",WARMUP_MS); report.put("totals",aggregate);
        report.put("scope","Fixed saved inputs, persistent per-game/seat generated MainBot, original absolute turns; outputs do not advance source board. Profile is instrumented ample-only attribution. Compare first requires all ample semantics, ordered actions, raw selected value and diagnostic keysets exact; nominal timer/RNG differences are permitted observations. No whole games, strength or external process deadline evidence.");
        Throwable failure=null;
        try {
            if(profile)profile(sources,baseline,out,aggregate);
            else compare(sources,baseline,candidate,out,aggregate,report);
            report.put("complete",true);
        } catch(Exception|Error problem) { failure=problem; report.put("failure",problem.toString()); }
        finally { report.put("finishedAt",Instant.now().toString()); write(out.resolve("report.json"),report); }
        if(failure instanceof Exception problem)throw problem; if(failure instanceof Error problem)throw problem;
    }
    private static void profile(List<Source> sources,Artifact artifact,Path out,Map<String,Map<String,Object>> aggregate) throws Exception {
        try(Recording recording=new Recording(Configuration.getConfiguration("profile"))) {
            recording.setName("v09-forecast-cost-fixed-states"); recording.setToDisk(true);
            recording.enable(ProfileGeneratedPolicy.PlayEvent.class).withoutStackTrace().withThreshold(Duration.ZERO);
            recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(1));
            write(out.resolve("recording-settings.json"),recording.getSettings()); recording.start();
            try(var writer=Files.newBufferedWriter(out.resolve("decisions.jsonl"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
                int count=0;
                for(Source source:sources)for(int me=0;me<2;me++) {
                    try(var policy=new ProfileGeneratedPolicy(artifact.directory)) {
                        policy.init(source.states.get(0),me);
                        for(GameState state:source.states) {
                            String hash=ReplyStudy.stateHash(state),id=source.gameId+"/"+state.turn+"/P"+me;
                            var raw=policy.play(state,me,AMPLE_MS,id); require(hash.equals(ReplyStudy.stateHash(state)),"Source mutated");
                            var d=decision(state,me,raw.action(),raw.elapsedMs(),raw.diagnostics());
                            emit(writer,Map.of("gameId",source.gameId,"turn",state.turn,"me",me,"stateSha256",hash,"decision",d,"timing",raw.timing()));
                            totals(aggregate,"profile",d,AMPLE_MS); count++;
                            require(d.completeSearch,"Incomplete ample profile at "+id);
                        }
                    }
                    System.out.println("Profile "+source.gameId+"/P"+me+": "+count+"/"+EXPECTED);
                }
                require(count==EXPECTED,"Incomplete profile");
            } finally { recording.stop(); recording.dump(out.resolve("profile.jfr")); }
        }
        write(out.resolve("hotspots.json"),hotspots(out.resolve("profile.jfr")));
    }
    private static void compare(List<Source> sources,Artifact baseline,Artifact candidate,Path out,
                                Map<String,Map<String,Object>> aggregate,Map<String,Object> report) throws Exception {
        Artifact[] artifacts={baseline,candidate}; String[] labels={"baseline","candidate"};
        for(int phase=0;phase<2;phase++) {
            String name=phase==0?"ample":"nominal"; int count=0;
            try(var writer=Files.newBufferedWriter(out.resolve(name+".jsonl"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
                for(int game=0;game<sources.size();game++)for(int me=0;me<2;me++) {
                    Source source=sources.get(game); GeneratedPolicy[] policies=new GeneratedPolicy[2];
                    try {
                        for(int arm=0;arm<2;arm++) {
                            policies[arm]=new GeneratedPolicy(artifacts[arm].directory); policies[arm].init(source.states.get(0),me);
                            if(phase==1)policies[arm].warmup(source.states.get(0),me,WARMUP_MS);
                        }
                        for(GameState state:source.states) {
                            String hash=ReplyStudy.stateHash(state); long budget=phase==0?AMPLE_MS:state.turn==0?486:36;
                            Decision[] decisions=new Decision[2];
                            for(int order=0;order<2;order++) {
                                int arm=(order+game+me+state.turn)%2; var raw=policies[arm].play(state,me,budget);
                                require(hash.equals(ReplyStudy.stateHash(state)),"Source mutated");
                                decisions[arm]=decision(state,me,raw.action(),raw.elapsedMs(),raw.diagnostics());
                                totals(aggregate,name+"/"+labels[arm],decisions[arm],budget);
                            }
                            var differences=differences(decisions[0],decisions[1]);
                            emit(writer,Map.of("gameId",source.gameId,"turn",state.turn,"me",me,"stateSha256",hash,
                                    "budgetMs",budget,"baseline",decisions[0],"candidate",decisions[1],"differences",differences)); count++;
                            if(phase==0) {
                                require(decisions[0].completeSearch&&decisions[1].completeSearch,"Incomplete ample comparison");
                                require(differences.isEmpty(),"Ample mismatch at "+source.gameId+"/"+state.turn+"/P"+me+": "+differences);
                            }
                        }
                    } finally { for(var policy:policies)if(policy!=null)policy.close(); }
                    System.out.println(name+" "+source.gameId+"/P"+me+": "+count+"/"+EXPECTED);
                }
            }
            require(count==EXPECTED,"Incomplete "+name+" panel"); report.put(name+"PhaseComplete",true);
        }
    }
    private static Map<String,Object> hotspots(Path input) throws Exception {
        List<Window> windows=new ArrayList<>(); List<Sample> samples=new ArrayList<>();
        try(var recording=new RecordingFile(input)) {
            while(recording.hasMoreEvents()) {
                var event=recording.readEvent(); String type=event.getEventType().getName();
                if(type.equals("btk.GeneratedPolicyPlay")) {
                    RecordedThread thread=event.getThread(); require(thread!=null,"Missing play thread");
                    require(event.getString("failure").isEmpty(),"Failed generated play marker");
                    windows.add(new Window(thread.getId(),event.getStartTime(),event.getEndTime(),event.getString("correlationId")));
                } else if(type.equals("jdk.ExecutionSample")) {
                    RecordedThread thread=event.getThread("sampledThread"); if(thread==null||event.getStackTrace()==null)continue;
                    List<String> methods=new ArrayList<>(); boolean generated=false;
                    for(RecordedFrame frame:event.getStackTrace().getFrames()) {
                        var method=frame.getMethod(); String owner=method.getType().getName(),methodName=method.getName();
                        methods.add(owner+"."+methodName+method.getDescriptor());
                        if((owner.equals("Player$MainBot")||owner.equals("Player$RolloutBot"))&&methodName.equals("play"))generated=true;
                    }
                    samples.add(new Sample(thread.getId(),event.getStartTime(),List.copyOf(methods),generated));
                }
            }
        }
        require(windows.size()==EXPECTED,"Missing profile markers");
        windows.sort(Comparator.comparing(Window::start));
        Map<String,Long> leaf=new HashMap<>(),inclusive=new HashMap<>(),byPlay=new LinkedHashMap<>();
        long scoped=0,markerOnly=0;
        for(Sample sample:samples) {
            Window matching=null;
            for(Window window:windows) {
                if(window.start.isAfter(sample.time))break;
                if(window.thread==sample.thread&&!sample.time.isBefore(window.start)&&sample.time.isBefore(window.end)){matching=window;break;}
            }
            if(matching==null)continue; markerOnly++;
            if(!sample.generatedPlay||sample.methods.isEmpty())continue;
            scoped++; byPlay.merge(matching.correlation,1L,Long::sum); leaf.merge(sample.methods.get(0),1L,Long::sum);
            for(String method:new HashSet<>(sample.methods))inclusive.merge(method,1L,Long::sum);
        }
        Map<String,Object> result=new LinkedHashMap<>(); result.put("recording",pin(input)); result.put("playMarkers",windows.size());
        result.put("allExecutionSamples",samples.size()); result.put("sameThreadInsideMarkerSamples",markerOnly);
        result.put("generatedPlayScopedSamples",scoped); result.put("leafMethods",ranked(leaf,scoped));
        result.put("inclusiveMethods",ranked(inclusive,scoped)); result.put("samplesPerPlay",byPlay);
        result.put("interpretation","Only samples on the marker's sampledThread identity inside its half-open interval, with generated MainBot.play or RolloutBot.play on stack. Leaf uses first recorded frame; inclusive counts a method once per sample. Counts are approximate sampled CPU attribution, not elapsed timings; inlining/truncation/sampling bias applies. Both kinds retain all methods, including JDK helpers. Profile work is ample and instrumented, not strict timing evidence.");
        return result;
    }
    private static List<Map<String,Object>> ranked(Map<String,Long> counts,long total) {
        List<Map.Entry<String,Long>> entries=new ArrayList<>(counts.entrySet());
        entries.sort(Map.Entry.<String,Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()));
        List<Map<String,Object>> result=new ArrayList<>();
        for(var entry:entries)result.add(Map.of("method",entry.getKey(),"samples",entry.getValue(),"fraction",total==0?0d:entry.getValue()/(double)total));
        return result;
    }
}
