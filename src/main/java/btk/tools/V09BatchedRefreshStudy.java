package btk.tools;

import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Array;
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
import java.util.Objects;
import java.util.TreeSet;

/** Fixed observed-state chronology: ample semantic parity first, then nominal coverage. No games. */
public final class V09BatchedRefreshStudy {
    private static final long[] GAMES={907134258L,907134581L,907134651L,907134720L};
    private static final String[] LABELS={"v09","off","on"};
    private static final long AMPLE_MS=5000, WARMUP_MS=500;
    private static final String[] REQUIRED={"routeQueries","completedComparisons","simulatedTurns",
            "ordinarySearchComplete","networkPhaseComplete","selectedOpening","selectedValue","selectedRoute","selectedTarget"};
    private V09BatchedRefreshStudy() {}

    private record Source(long gameId,List<GameState> states,Map<String,Object> pin) {}
    private record Artifact(String label,Path directory,Map<String,String> params,Map<String,Object> pins) {}
    public record Decision(String action,int[] cells,int target,double wallMs,Long selectedValueRawBits,
                           boolean completeSearch,Map<String,Object> diagnostics) {}

    private static void require(boolean ok,String message) { if(!ok)throw new IllegalStateException(message); }
    private static Map<String,Object> pin(Path file)throws Exception {
        return Map.of("path",file.toString(),"sha256",V09ContinuationStudy.hash(file),"bytes",Files.size(file));
    }
    private static Map<String,String> parameters(JsonObject manifest) {
        Map<String,String> result=new LinkedHashMap<>();
        for(var field:manifest.getAsJsonObject("params").entrySet())result.put(field.getKey(),field.getValue().getAsString());
        return result;
    }
    private static Artifact artifact(String label,Path directory)throws Exception {
        Path manifest=directory.resolve("manifest.json"),source=directory.resolve("Player.java"),classes=directory.resolve("classes");
        var document=JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
        verifyManifestSource(document,source);
        require(document.get("strategyId").getAsString().equals(label.equals("v09")?"v09":"current"),"Wrong artifact strategy: "+label);
        Map<String,String> params=parameters(document);
        require("0.1".equals(params.get("NOISE")),"Keep NOISE=0.1: "+label);
        require(!Boolean.parseBoolean(params.getOrDefault("USE_ROLLOUT_MISSED_DEADLINE","false")),"Deadline correction must remain OFF");
        require("true".equals(params.get("ISOLATED_ROUTE_WARMUP")),"Generated MainBot must isolate warmup RNG");
        require(Boolean.parseBoolean(params.getOrDefault("USE_BATCHED_REFRESH","false"))==label.equals("on"),"Wrong batching flag: "+label);
        MessageDigest digest=MessageDigest.getInstance("SHA-256");int count=0;
        try(var files=Files.walk(classes)) {
            for(Path file:files.filter(p->p.toString().endsWith(".class")).sorted().toList()) {
                digest.update(classes.relativize(file).toString().replace('\\','/').getBytes(StandardCharsets.UTF_8));
                digest.update((byte)0);digest.update(Files.readAllBytes(file));count++;
            }
        }
        require(count>0&&Files.isRegularFile(classes.resolve("Player.class")),"Missing generated classes");
        Map<String,Object> pins=new LinkedHashMap<>();pins.put("source",pin(source));pins.put("manifest",pin(manifest));
        pins.put("classesSha256",HexFormat.of().formatHex(digest.digest()));pins.put("classCount",count);
        pins.put("strategy",document.get("strategy").getAsString());pins.put("params",params);
        return new Artifact(label,directory,params,pins);
    }
    private static void sameParameters(Artifact baseline,Artifact other) {
        for(var entry:baseline.params.entrySet())if(!entry.getKey().equals("USE_BATCHED_REFRESH")&&other.params.containsKey(entry.getKey()))
            require(entry.getValue().equals(other.params.get(entry.getKey())),"Parameter drift: "+entry.getKey());
    }
    static void verifyManifestSource(JsonObject manifest,Path source)throws Exception {
        require(V09ContinuationStudy.hash(source).equals(manifest.get("sha256").getAsString()),"Artifact source differs from manifest");
    }
    static boolean sameOffOnParameters(Map<String,String> off,Map<String,String> on) {
        Map<String,String> a=new LinkedHashMap<>(off),b=new LinkedHashMap<>(on);
        a.remove("USE_BATCHED_REFRESH");b.remove("USE_BATCHED_REFRESH");return a.equals(b);
    }
    private static List<Source> sources(Path audit)throws Exception {
        var document=JsonParser.parseString(Files.readString(audit)).getAsJsonObject();
        require(document.get("complete").getAsBoolean(),"Incomplete source audit");List<Source> result=new ArrayList<>();
        for(long id:GAMES) {
            JsonObject selected=null;
            for(var element:document.getAsJsonArray("rows")) {
                var row=element.getAsJsonObject();if(row.get("gameId").getAsLong()!=id)continue;
                require(selected==null,"Duplicate source "+id);selected=row;
            }
            require(selected!=null,"Missing fixed source "+id);var canonical=selected.getAsJsonObject("canonical");
            Path file=Path.of(canonical.get("path").getAsString());
            require(V09ContinuationStudy.hash(file).equals(canonical.get("sha256").getAsString()),"Changed source "+id);
            CgReplay replay=CgReplay.load(file);require(replay.gameId==id,"Source ID mismatch");
            require(CgValidate.validate(replay,false).firstDivergenceTurn<0,"Core divergence "+id);
            GameState state=replay.toState();Pathing pathing=new Pathing(state.size);Rules.updateConnections(state,pathing);
            List<GameState> inputs=new ArrayList<>();
            for(int turn=0;turn<replay.turns();turn++) {
                require(replay.failure(turn,0)==null&&replay.failure(turn,1)==null,"Failed recorded turn");
                inputs.add(state.copy());Rules.resolve(state,CgPlayerStudy.effectiveActions(replay,turn,0),
                        CgPlayerStudy.effectiveActions(replay,turn,1),pathing);
            }
            require(Arrays.equals(state.score,replay.finalScores),"Final replay score mismatch");
            result.add(new Source(id,List.copyOf(inputs),pin(file)));
        }
        return List.copyOf(result);
    }

    /** Strict array/scalar equality, including the signed zero bit of floating-point values. */
    static boolean semanticEquals(Object a,Object b) {
        if(a==null||b==null)return a==b;
        if(a instanceof Double x&&b instanceof Double y)return Double.doubleToRawLongBits(x)==Double.doubleToRawLongBits(y);
        if(a instanceof Float x&&b instanceof Float y)return Float.floatToRawIntBits(x)==Float.floatToRawIntBits(y);
        if(a.getClass().isArray()&&b.getClass().isArray()) {
            if(Array.getLength(a)!=Array.getLength(b))return false;
            for(int i=0;i<Array.getLength(a);i++)if(!semanticEquals(Array.get(a,i),Array.get(b,i)))return false;
            return true;
        }
        return Objects.equals(a,b);
    }
    static List<String> differences(Decision a,Decision b) {
        List<String> result=new ArrayList<>();
        if(!a.action.equals(b.action))result.add("orderedAction");
        if(!semanticEquals(a.cells,b.cells)||a.target!=b.target)result.add("acceptedAction");
        if(!Objects.equals(a.selectedValueRawBits,b.selectedValueRawBits))result.add("selectedValueRawBits");
        for(String field:new TreeSet<>(a.diagnostics.keySet()))if(!field.startsWith("deadline")&&b.diagnostics.containsKey(field)
                &&!semanticEquals(a.diagnostics.get(field),b.diagnostics.get(field)))result.add(field);
        return result;
    }
    static boolean completeSearch(Map<String,Object> diagnostics) {
        return Boolean.TRUE.equals(diagnostics.get("selectedOpening"))
                ||Boolean.TRUE.equals(diagnostics.get("ordinarySearchComplete"))&&Boolean.TRUE.equals(diagnostics.get("networkPhaseComplete"));
    }
    private static Decision play(GeneratedPolicy policy,GameState state,int me,long budget)throws Exception {
        String before=ReplyStudy.stateHash(state);var output=policy.play(state,me,budget);var d=output.diagnostics();
        for(String field:REQUIRED)require(d.containsKey(field),"Missing policy diagnostic "+field);
        require(before.equals(ReplyStudy.stateHash(state)),"Generated policy mutated observed input");
        var accepted=V09ContinuationStudy.accepted(state,me,output.action());
        Long bits=d.get("selectedValue") instanceof Double value?Double.doubleToRawLongBits(value):null;
        boolean complete=completeSearch(d);
        return new Decision(accepted.action(),accepted.cells(),accepted.target(),output.elapsedMs(),bits,complete,d);
    }
    private static void addTotals(Map<String,Map<String,Object>> all,String key,Decision decision,long budget) {
        var totals=all.computeIfAbsent(key,k->new LinkedHashMap<>());
        for(String field:new String[]{"calls","completeSearch","aboveBudget","above50Ms","routeQueries","completedComparisons","simulatedTurns","ordinarySearchComplete","networkPhaseComplete"}) {
            long value=switch(field) {
                case "calls"->1;case "completeSearch"->decision.completeSearch?1:0;
                case "aboveBudget"->decision.wallMs>budget?1:0;case "above50Ms"->decision.wallMs>50?1:0;
                case "ordinarySearchComplete","networkPhaseComplete"->Boolean.TRUE.equals(decision.diagnostics.get(field))?1:0;
                default->((Number)decision.diagnostics.get(field)).longValue();
            };
            totals.put(field,((Number)totals.getOrDefault(field,0L)).longValue()+value);
        }
        totals.put("totalWallMs",((Number)totals.getOrDefault("totalWallMs",0d)).doubleValue()+decision.wallMs);
        totals.put("maxWallMs",Math.max(((Number)totals.getOrDefault("maxWallMs",0d)).doubleValue(),decision.wallMs));
    }

    public static void main(String[] args)throws Exception {
        if(args.length!=5)throw new IllegalArgumentException("canonicalAudit artifactV09 artifactOff artifactOn newReport.json");
        Path out=Path.of(args[4]);require(!Files.exists(out),"Refuse to overwrite study");
        Files.createDirectories(out.toAbsolutePath().getParent());Map<String,Object> report=new LinkedHashMap<>();
        List<Map<String,Object>> rows=new ArrayList<>();Map<String,Map<String,Object>> totals=new LinkedHashMap<>();
        report.put("schema","v09-batched-refresh-study-v1");report.put("startedAt",Instant.now().toString());report.put("complete",false);
        report.put("amplePhaseComplete",false);report.put("ampleBudgetMs",AMPLE_MS);report.put("nominalWarmupMs",WARMUP_MS);
        report.put("nominalBudgetsMs",List.of(486,36));report.put("gameIds",GAMES);report.put("rows",rows);report.put("totals",totals);
        report.put("scope","Fixed4 saved CG games/all observed turns/both seats. Persistent generated instances advance their own RNG on identical recorded input chronology; emitted actions do not advance the board. Entire ample5000ms phase must pass before nominal486/36ms phase; rotating artifact order. NOISE0.1, deadlineOFF; nominal MainBot500ms isolated warmup, ample no warmup. Common semantic fields and ordered actions are compared at ample; nominal differences reflect work completed and divergent RNG history, not a fixed-work parity failure. Coverage/runtime diagnosis only, not strength, arena or strict process readiness.");
        Throwable failure=null;
        try {
            report.put("audit",pin(Path.of(args[0])));Artifact[] artifacts=new Artifact[3];
            for(int i=0;i<3;i++)artifacts[i]=artifact(LABELS[i],Path.of(args[i+1]));
            sameParameters(artifacts[0],artifacts[1]);sameParameters(artifacts[1],artifacts[2]);
            require(sameOffOnParameters(artifacts[1].params,artifacts[2].params),"OFF/ON parameters differ beyond batching");
            Map<String,Object> pins=new LinkedHashMap<>();for(var artifact:artifacts)pins.put(artifact.label,artifact.pins);report.put("artifacts",pins);
            List<Source> sources=sources(Path.of(args[0]));report.put("sources",sources.stream().map(Source::pin).toList());
            int expected=sources.stream().mapToInt(s->2*s.states.size()).sum();report.put("expectedPositions",expected);
            for(int phase=0;phase<2;phase++) {
                int rowIndex=0;
                for(int game=0;game<sources.size();game++)for(int me=0;me<2;me++) {
                    Source source=sources.get(game);GeneratedPolicy[] policies=new GeneratedPolicy[3];
                    try {
                        for(int i=0;i<3;i++) {
                            policies[i]=new GeneratedPolicy(artifacts[i].directory);policies[i].init(source.states.get(0),me);
                            if(phase==1)policies[i].warmup(source.states.get(0),me,WARMUP_MS);
                        }
                        for(GameState state:source.states) {
                            Map<String,Object> row;
                            if(phase==0) {
                                row=new LinkedHashMap<>();row.put("gameId",source.gameId);row.put("turn",state.turn);row.put("me",me);
                                row.put("stateSha256",ReplyStudy.stateHash(state));rows.add(row);
                            } else row=rows.get(rowIndex);
                            rowIndex++;long budget=phase==0?AMPLE_MS:state.turn==0?486:36;
                            Decision[] decisions=new Decision[3];Map<String,Object> values=new LinkedHashMap<>();
                            row.put(phase==0?"ample":"nominal",values);
                            for(int order=0;order<3;order++) {
                                int i=(order+game+me+state.turn)%3;decisions[i]=play(policies[i],state,me,budget);
                                values.put(LABELS[i],decisions[i]);addTotals(totals,(phase==0?"ample/":"nominal/")+LABELS[i],decisions[i],budget);
                            }
                            if(phase==0) {
                                var frozenOff=differences(decisions[0],decisions[1]);var offOn=differences(decisions[1],decisions[2]);
                                row.put("ampleFrozenOffDifferences",frozenOff);row.put("ampleOffOnDifferences",offOn);
                                require(Arrays.stream(decisions).allMatch(Decision::completeSearch),"Incomplete ample search at "+source.gameId+"/"+state.turn+"/P"+me);
                                require(frozenOff.isEmpty()&&offOn.isEmpty(),"Ample semantic mismatch at "+source.gameId+"/"+state.turn+"/P"+me);
                            } else {
                                row.put("nominalFrozenOffActionEqual",decisions[0].action.equals(decisions[1].action));
                                row.put("nominalOffOnActionEqual",decisions[1].action.equals(decisions[2].action));
                            }
                        }
                    } finally { for(var policy:policies)if(policy!=null)policy.close(); }
                    System.out.println((phase==0?"Ample":"Nominal")+" "+source.gameId+"/P"+me+" complete");
                }
                require(rowIndex==expected,"Incomplete fixed positions");if(phase==0)report.put("amplePhaseComplete",true);
            }
            report.put("complete",true);
        } catch(Exception|Error problem) { failure=problem;report.put("failure",problem.toString()); }
        finally {
            report.put("finishedAt",Instant.now().toString());report.put("retainedRows",rows.size());
            Files.writeString(out,V09ContinuationStudy.JSON.toJson(report),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        }
        if(failure instanceof Exception exception)throw exception;if(failure instanceof Error error)throw error;
    }
}
