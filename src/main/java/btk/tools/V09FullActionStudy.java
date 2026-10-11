package btk.tools;

import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fixed follow-up panel: complete construction, sabotage and genuine repeated decisions. */
public final class V09FullActionStudy {
    private static final String SELECTION_HASH = "c6cead6244d5d2fd873f645fde926dd3be1bf6848fa9f7aaab1c6695ba6c2129";
    private V09FullActionStudy() {}
    private static void require(boolean ok, String why) { V09ContinuationStudy.require(ok, why); }
    private static String key(JsonObject row) { return row.get("gameId").getAsLong() + "/" + row.get("turn").getAsInt(); }
    private static void increment(Map<String,Integer> counts, String key) { counts.merge(key,1,Integer::sum); }

    static JsonObject selection(Path path) throws Exception {
        require(V09ContinuationStudy.hash(path).equals(SELECTION_HASH), "Fixed selection changed");
        JsonObject selection = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        for (var entry : selection.getAsJsonObject("sources").entrySet()) {
            JsonObject pin = entry.getValue().getAsJsonObject();
            require(V09ContinuationStudy.hash(Path.of(pin.get("path").getAsString())).equals(pin.get("sha256").getAsString()),
                    "Changed selection source: " + entry.getKey());
        }
        require(selection.getAsJsonArray("panel").size()==967 && selection.getAsJsonArray("episodeStarts").size()==56,
                "Expected fixed967/56");
        Set<String> panel = new HashSet<>(), episodes = new HashSet<>();
        int index=0;
        for (var entry:selection.getAsJsonArray("panel")) {
            JsonObject row=entry.getAsJsonObject();
            require(panel.add(key(row)) && row.get("index").getAsInt()==++index,"Duplicate/out-of-order panel index");
            require(row.get("primaryTarget").getAsInt()>=0,"Missing pinned target");
        }
        for (var entry:selection.getAsJsonArray("episodeStarts")) {
            JsonObject row=entry.getAsJsonObject();
            require(panel.contains(key(row)) && episodes.add(key(row)),"Invalid episode start");
            require(row.getAsJsonObject("previousAmple").get("target").getAsInt()==-1,"Episode native action was not no-hit");
        }
        return selection;
    }

    static void requireSameAction(V09ContinuationProbe.Plan actual, JsonObject expected) {
        require(actual.target()==expected.get("target").getAsInt(),"Native target differs from prior frozen ample action");
        JsonArray cells=expected.getAsJsonArray("cells");
        require(actual.cells().length==cells.size(),"Native cell count differs from prior action");
        for(int i=0;i<cells.size();i++) require(actual.cells()[i]==cells.get(i).getAsInt(),"Native cell order changed");
        require(actual.action().equals(expected.get("action").getAsString()),"Native complete action string changed");
    }

    static Map<String,Object> inspect(GameState state, JsonObject selected) throws Exception {
        int me=selected.get("me").getAsInt(), target=selected.get("primaryTarget").getAsInt();
        String before=ReplyStudy.stateHash(state);
        require(before.equals(selected.get("stateSha256").getAsString()),"Source state differs from frozen selection");
        V09FullActionProbe probe=V09FullActionProbe.prepare(state,me);
        requireSameAction(probe.selected(),selected.getAsJsonObject("previousAmple"));
        Map<String,Object> row=new LinkedHashMap<>();
        row.put("selection",selected); row.put("metadata",probe.metadata());
        row.put("score",state.score.clone()); int[] income=new int[2]; Rules.incomePerTurn(state,income); row.put("income",income);
        if(probe.metadata().supported()) {
            require(probe.metadata().ownCount()==0,"Selected fixed panel no longer has empty own portfolio");
            var attack=probe.trace(target,V09FullActionProbe.Variant.BASELINE);
            var noHit=probe.trace(-1,V09FullActionProbe.Variant.BASELINE);
            var noPromise=probe.trace(-1,V09FullActionProbe.Variant.NO_FUTURE_OWN_DISRUPT);
            row.put("attack",attack); row.put("noHit",noHit); row.put("noFutureOwnSabotage",noPromise);
            double a=attack.score().value(),w=noHit.score().value(),w0=noPromise.score().value();
            row.put("attackMinusNoHit",a-w); row.put("attackMinusNoFuture",a-w0);
            row.put("fullNoHitPreferred",w>a+1e-9);
            row.put("fullPreferenceReversed",w>a+1e-9 && a>w0+1e-9);
            row.put("fullAttackPreferred",a>w+1e-9);
            row.put("status","COMPLETE");
        } else row.put("status","UNSUPPORTED_PROVENANCE");
        require(before.equals(ReplyStudy.stateHash(state)),"Full-action comparison mutated source state");
        return row;
    }

    private static void emit(BufferedWriter writer,Object row) throws Exception {
        writer.write(V09ContinuationStudy.JSON.toJson(row)); writer.newLine(); writer.flush();
    }
    private static Map<String,Object> pin(Path path) throws Exception {
        return Map.of("path",path.toString(),"sha256",V09ContinuationStudy.hash(path));
    }

    public static void main(String[] args) throws Exception {
        if(args.length!=2) throw new IllegalArgumentException("fixedSelection outputDirectory");
        Path out=Path.of(args[1]); require(!Files.exists(out),"Refuse to overwrite full-action diagnostic output");
        JsonObject selection=selection(Path.of(args[0]));
        var sources=V09ContinuationStudy.load(Path.of(selection.getAsJsonObject("sources").getAsJsonObject("sourceAudit").get("path").getAsString()));
        JsonObject historicalSelection=selection.getAsJsonObject("historical");
        CgReplay historical=CgReplay.load(Path.of(historicalSelection.getAsJsonObject("replay").get("path").getAsString()));
        require(historical.gameId==904554072 && CgValidate.validate(historical,false).firstDivergenceTurn<0,"Historical identity/engine check");
        Map<String,JsonObject> panel=new LinkedHashMap<>(); Set<String> starts=new HashSet<>();
        for(var entry:selection.getAsJsonArray("panel")) {var row=entry.getAsJsonObject();panel.put(key(row),row);}
        for(var entry:selection.getAsJsonArray("episodeStarts")) starts.add(key(entry.getAsJsonObject()));
        Files.createDirectories(out);
        Map<String,Integer> counts=new LinkedHashMap<>(); List<Map<String,Object>> games=new ArrayList<>();
        Path positionFile=out.resolve("positions.jsonl"),episodeFile=out.resolve("episodes.jsonl");
        int positions=0,episodes=0;
        try(var pw=Files.newBufferedWriter(positionFile,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            var ew=Files.newBufferedWriter(episodeFile,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
            for(var source:sources) {
                GameState state=source.replay().toState(); Pathing pathing=new Pathing(state.size);Rules.updateConnections(state,pathing);
                int gamePositions=0,gameEpisodes=0;
                for(int turn=0;turn<source.replay().turns();turn++) {
                    require(state.turn==turn && !Rules.gameOver(state,pathing),"Unexpected replay termination");
                    String key=source.replay().gameId+"/"+turn;JsonObject chosen=panel.get(key);
                    if(chosen!=null) {
                        var row=inspect(state,chosen);emit(pw,row);positions++;gamePositions++;
                        increment(counts,(String)row.get("status"));
                        increment(counts,"group_"+chosen.get("classification").getAsString());
                        if(Boolean.TRUE.equals(row.get("fullPreferenceReversed"))) increment(counts,"fullPreferenceReversed");
                        if(Boolean.TRUE.equals(row.get("fullAttackPreferred"))) increment(counts,"fullAttackPreferred");
                        if(starts.contains(key)) {
                            var result=V09FullActionEpisode.run(state,chosen.get("me").getAsInt(),chosen.get("primaryTarget").getAsInt());
                            emit(ew,Map.of("selection",chosen,"result",result));episodes++;gameEpisodes++;
                        }
                    }
                    Rules.resolve(state,Action.parseLine(source.replay().turnStdout.get(turn)[0]),
                            Action.parseLine(source.replay().turnStdout.get(turn)[1]),pathing);
                }
                require(state.score[0]==source.replay().finalScores[0] && state.score[1]==source.replay().finalScores[1],"Final replay mismatch");
                games.add(Map.of("gameId",source.replay().gameId,"positions",gamePositions,"episodes",gameEpisodes));
                System.out.println("Completed source "+games.size()+"/40; positions="+positions+"/967; starts="+episodes+"/56");
            }
        }
        require(positions==967 && episodes==56,"Incomplete fixed panel");
        GameState hs=historical.toState();Pathing hp=new Pathing(hs.size);Rules.updateConnections(hs,hp);
        for(int turn=0;turn<37;turn++) Rules.resolve(hs,CgPlayerStudy.effectiveActions(historical,turn,0),CgPlayerStudy.effectiveActions(historical,turn,1),hp);
        var h=inspect(hs,historicalSelection);
        h.put("episode",V09FullActionEpisode.run(hs,0,historicalSelection.get("primaryTarget").getAsInt()));
        V09ContinuationStudy.write(out.resolve("historical.json"),h);
        Map<String,Object> report=new LinkedHashMap<>();report.put("kind","v09-full-action-v1");report.put("complete",true);
        report.put("createdAt",Instant.now().toString());report.put("selection",pin(Path.of(args[0])));
        report.put("positionsCount",positions);report.put("cohortStarts",episodes);report.put("historicalStarts",1);
        report.put("counts",counts);report.put("sourceGames",games);report.put("engineCohortExact",40);report.put("engineHistoricalExact",1);
        report.put("positions",pin(positionFile));report.put("episodes",pin(episodeFile));report.put("historical",pin(out.resolve("historical.json")));
        report.put("scope","Fixed exposed development full-action/8-step mechanism experiment; no match-strength claim or policy change");
        V09ContinuationStudy.write(out.resolve("report.json"),report);
        System.out.println(V09ContinuationStudy.JSON.toJson(counts));
    }
}
