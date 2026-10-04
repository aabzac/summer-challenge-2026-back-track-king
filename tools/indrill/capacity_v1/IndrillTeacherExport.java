import btk.bot.BotFactory;
import btk.bot.current.IndrillFeatures;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Protocol;
import btk.core.Rules;
import btk.tools.CgReplay;
import btk.tools.CgValidate;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Standalone teacher exporter; run through the frozen external launcher. */
public final class IndrillTeacherExport {
    static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
    static final Gson PRETTY = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    static final Set<Long> LATER = Set.of(905455435L,905455393L,905455347L,905455346L,
            905455155L,905455111L,905455102L,905454402L,905454189L,905453964L,905452339L);
    static final String PREP = "tools/indrill/capacity_v1/";

    static final class Game {
        long gameId;
        String replayPath, replaySha256, mapHash, split;
        int teacherSeat, teacherAgentId, width, height, regionCount, turns;
        boolean oldHoldout, later;
        List<String> cohortTags = new ArrayList<>();
        List<Integer> validTeacherTurns = new ArrayList<>();
        List<Map<String,Object>> excludedTurns = new ArrayList<>();
        Map<String,Object> coreValidation;
    }
    static final class Selected {
        String observationId, mapHash, split;
        long gameId;
        int teacherSeat, turn;
        List<String> panelTags = new ArrayList<>(), cohortTags;
    }
    static final class Wire {
        String[] stdout = new String[2];
        int[] frameCount = new int[2], stdoutCount = new int[2], lastStdoutFrame = {-1,-1};
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("synthetic")) { synthetic(Path.of(args[1])); return; }
        if (args.length != 3) throw new IllegalArgumentException(
                "Usage: synthetic NEW_DIR | preflight REPO NEW_DIR | export REPO PREFLIGHT_DIR");
        Path root = Path.of(args[1]).toAbsolutePath().normalize(), out = Path.of(args[2]).toAbsolutePath().normalize();
        if (!out.startsWith(root.resolve("build/analysis/"))) throw new IllegalArgumentException("Output must stay under repo build/analysis");
        if (args[0].equals("preflight")) preflight(root,out);
        else if (args[0].equals("export")) export(root,out);
        else throw new IllegalArgumentException("Unknown exporter mode");
    }

    static Path within(Path root, String relative) {
        Path path = root.resolve(relative).normalize();
        if (!path.startsWith(root)) throw new IllegalArgumentException("Input escapes repository: " + relative);
        return path;
    }
    static void require(boolean ok, String message) { if (!ok) throw new IllegalStateException(message); }
    static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    static String fileSha(Path path) throws Exception { return sha(Files.readAllBytes(path)); }
    static void writeNew(Path path, Object value) throws Exception {
        Files.writeString(path,PRETTY.toJson(value)+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
    }
    static JsonObject read(Path path) throws Exception { return JsonParser.parseString(Files.readString(path)).getAsJsonObject(); }
    static String mapHash(GameState s) throws Exception { return sha(Protocol.initBlock(s,0).getBytes(StandardCharsets.UTF_8)); }

    /** Independent frame-presence audit; never manufactures WAIT or nonnull stdout. */
    static List<Wire> wires(JsonObject raw) {
        JsonArray frames = raw.getAsJsonArray("frames");
        require(frames != null && !frames.isEmpty(),"Missing canonical frames");
        List<Wire> out = new ArrayList<>(); Wire current = null;
        for (int i=1;i<frames.size();i++) {
            if (current == null) current = new Wire();
            JsonObject frame = frames.get(i).getAsJsonObject();
            int seat = frame.has("agentId") && !frame.get("agentId").isJsonNull() ? frame.get("agentId").getAsInt() : -1;
            if (seat >= 0 && seat < 2) {
                current.frameCount[seat]++;
                if (frame.has("stdout") && !frame.get("stdout").isJsonNull()) {
                    current.stdoutCount[seat]++;
                    current.stdout[seat] = frame.get("stdout").getAsString().trim();
                    current.lastStdoutFrame[seat] = i;
                }
            }
            if (frame.has("keyframe") && frame.get("keyframe").getAsBoolean()) { out.add(current); current=null; }
        }
        if (current != null) out.add(current);
        return out;
    }
    static String teacherIssue(CgReplay replay,List<Wire> wire,int turn,int me) {
        if (replay.failure(turn,me) != null) return "server_failure: " + replay.failure(turn,me);
        Wire w = wire.get(turn);
        if (w.frameCount[me] == 0) return "missing_teacher_frame";
        if (w.stdoutCount[me] == 0) return "missing_teacher_stdout";
        if (w.stdoutCount[me] != 1) return "ambiguous_repeated_teacher_stdout";
        if (w.stdout[me].isBlank()) return "empty_teacher_stdout";
        if (!Objects.equals(w.stdout[me],replay.turnStdout.get(turn)[me])) return "raw_vs_parser_stdout_mismatch";
        try { Action.parseLine(w.stdout[me]); } catch (RuntimeException e) { return "unparsable_teacher_stdout: " + e; }
        return null;
    }
    @SuppressWarnings("unchecked")
    static List<Action>[] actual(CgReplay r,int turn) {
        List<Action>[] actions = new List[2];
        for (int me=0;me<2;me++) {
            String line = r.turnStdout.get(turn)[me];
            if (r.failure(turn,me)!=null || line==null) actions[me]=List.of(Action.waitAction());
            else { try { actions[me]=Action.parseLine(line); }
                catch (RuntimeException e) { throw new IllegalStateException("Unparsable reconstruction stdout: "+r.gameId+":"+turn+":"+me,e); } }
        }
        return actions;
    }

    static Game inspect(Path root,long id,String relative,boolean oldHoldout,boolean later) throws Exception {
        Path path=within(root,relative); byte[] bytes=Files.readAllBytes(path);
        JsonObject raw=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
        CgReplay replay=CgReplay.parse(raw);
        require(replay.gameId==id,"Game ID differs from manifest: "+relative);
        int me = "Indrill".equals(replay.nicknames[0]) ? 0 : "Indrill".equals(replay.nicknames[1]) ? 1 : -1;
        require(me>=0 && !("Indrill".equals(replay.nicknames[0]) && "Indrill".equals(replay.nicknames[1])),"Ambiguous/missing teacher: "+id);
        CgValidate.Outcome validation=CgValidate.validate(replay,false);
        require(validation.firstDivergenceTurn<0,"Core replay divergence: "+id+": "+validation.reason);
        List<Wire> wire=wires(raw); require(wire.size()==replay.turns(),"Raw frame turn count differs: "+id);
        GameState state=replay.toState(); state.maxTurns=Math.max(state.maxTurns,replay.turns());
        Pathing paths=new Pathing(state.size); Rules.updateConnections(state,paths);
        Game game=new Game(); game.gameId=id;game.replayPath=relative;game.replaySha256=sha(bytes);
        game.mapHash=mapHash(state);game.teacherSeat=me;game.teacherAgentId=replay.agentIds[me];
        game.width=state.width;game.height=state.height;game.regionCount=state.regionCount;game.turns=replay.turns();
        game.oldHoldout=oldHoldout;game.later=later;
        game.coreValidation=Map.of("exact",true,"turns",replay.turns(),"finalLocal",validation.finalLocal);
        for (int turn=0;turn<replay.turns();turn++) {
            List<Action>[] actions=actual(replay,turn);
            String issue=teacherIssue(replay,wire,turn,me);
            Rules.TurnResult tr=new Rules.TurnResult(); Rules.expand(state,me,actions[me],paths,tr.moves[me],tr);
            if (issue==null && tr.moves[me].error!=null) issue="core_action_error: "+tr.moves[me].error;
            if (issue==null) game.validTeacherTurns.add(turn);
            else game.excludedTurns.add(Map.of("turn",turn,"reason",issue,
                    "rawFrameCount",wire.get(turn).frameCount[me],"rawStdoutCount",wire.get(turn).stdoutCount[me]));
            Rules.resolve(state,actions[0],actions[1],paths);
        }
        return game;
    }

    static SortedMap<String,String> pins(Path root) throws Exception {
        SortedMap<String,String> pins=new TreeMap<>();
        for (String relative:List.of("src/main/java/btk/core","src/main/java/btk/tools/CgReplay.java",
                "src/main/java/btk/tools/CgValidate.java","src/main/java/btk/bot/current/IndrillFeatures.java",
                "src/main/java/btk/bot/current/IndrillV2Net.java","tools/indrill/train_q.py","tools/indrill/train_q_v2.py",
                "build/classes/java/main","build/resources/main","docs/indrill-capacity-v1.md",PREP+"schema-v1.md",
                PREP+"IndrillTeacherExport.java",PREP+"teacher_dataset.py")) {
            Path path=within(root,relative);require(Files.exists(path),"Missing pinned input: "+relative);
            if (Files.isDirectory(path)) { try(var walk=Files.walk(path)) {
                for (Path file:walk.filter(Files::isRegularFile).sorted().toList())
                    pins.put(root.relativize(file).toString().replace('\\','/'),fileSha(file));
            }} else pins.put(relative,fileSha(path));
        }
        return pins;
    }
    static void select(Map<String,Selected> selected,Game game,int count,String panel) {
        int total=game.validTeacherTurns.size();count=Math.min(count,total);
        for(int j=0;j<count;j++) {
            int index=count==1?0:(int)((long)j*(total-1)/(count-1));
            int turn=game.validTeacherTurns.get(index);
            String identity=game.gameId+":"+game.teacherSeat+":"+turn;
            Selected row=selected.computeIfAbsent(identity,key->{
                Selected s=new Selected();s.observationId=key;s.gameId=game.gameId;s.teacherSeat=game.teacherSeat;s.turn=turn;
                s.mapHash=game.mapHash;s.split=game.split;s.cohortTags=List.copyOf(game.cohortTags);return s;
            });
            if(!row.panelTags.contains(panel)) {row.panelTags.add(panel);Collections.sort(row.panelTags);}
        }
    }

    static void preflight(Path root,Path out) throws Exception {
        require(!Files.exists(out),"Refusing existing output directory");Files.createDirectories(out);
        writeNew(out.resolve("preflight-intent.json"),Map.of("preparationOnly",true,"mode","preflight",
                "root",root.toString(),"scope","local canonical teacher reads only; no learning/games"));
        Map<String,Object> result=new LinkedHashMap<>();result.put("schema","indrill-capacity-preflight-v1");
        result.put("preparationOnly",true);result.put("complete",false);
        List<String> failures=new ArrayList<>();List<Game> games=new ArrayList<>();
        try {
            result.put("sourceRuntimePins",pins(root));
            String oldMeta="build/analysis/neural-data/meta.json",laterMeta="docs/evidence/indrill-q-v2-corpus.json";
            result.put("inputManifestPins",Map.of(oldMeta,fileSha(within(root,oldMeta)),laterMeta,fileSha(within(root,laterMeta))));
            JsonArray old=read(within(root,oldMeta)).getAsJsonArray("gameList");
            require(old.size()==282,"Expected retained 282-game teacher manifest");
            Set<Long> seen=new HashSet<>(),prior=new TreeSet<>();
            for(JsonElement entry:old) {
                JsonObject g=entry.getAsJsonObject();long id=g.get("gameId").getAsLong();
                require(seen.add(id),"Duplicate old manifest game ID");
                boolean holdout=g.get("holdout").getAsBoolean();if(holdout)prior.add(id);
                games.add(inspect(root,id,"replays/cg-indrill/"+id+".json",holdout,false));
            }
            require(prior.size()==40,"Expected exact original40 holdout IDs");
            Set<Long> found=new TreeSet<>();
            for(JsonElement entry:read(within(root,laterMeta)).getAsJsonArray("games")) {
                JsonObject g=entry.getAsJsonObject();long id=g.get("gameId").getAsLong();
                if(!LATER.contains(id))continue;
                require(found.add(id),"Duplicate later manifest game ID");
                String relative=g.get("path").getAsString();
                require(fileSha(within(root,relative)).equals(g.get("sha256").getAsString()),"Later canonical bytes differ from audit");
                if(seen.add(id))games.add(inspect(root,id,relative,false,true));
                else for(Game existing:games)if(existing.gameId==id) {
                    require(existing.replaySha256.equals(fileSha(within(root,relative))),"Same game ID has contradictory bytes");existing.later=true;
                }
            }
            require(found.equals(LATER),"Later11 manifest set differs");
            result.put("priorHoldoutIds",prior);result.put("laterIds",found);
            Map<String,List<Game>> grouped=new TreeMap<>();for(Game game:games)grouped.computeIfAbsent(game.mapHash,k->new ArrayList<>()).add(game);
            List<Map<String,Object>> groups=new ArrayList<>();List<Game> trainRepresentatives=new ArrayList<>();
            Map<String,Selected> selection=new LinkedHashMap<>();List<String> unavailableHoldout=new ArrayList<>();
            for(var entry:grouped.entrySet()) {
                List<Game> members=entry.getValue();members.sort(Comparator.comparingLong(g->g.gameId));
                boolean holdout=members.stream().anyMatch(g->g.oldHoldout||g.later);
                List<String> groupCohorts=new ArrayList<>();
                if(members.stream().anyMatch(g->g.oldHoldout))groupCohorts.add("old40");
                if(members.stream().anyMatch(g->g.later))groupCohorts.add("later11");
                for(Game game:members) {
                    game.split=holdout?"holdout":"train";
                    game.cohortTags=new ArrayList<>(groupCohorts);
                    if(holdout&&!game.oldHoldout&&!game.later)game.cohortTags.add("holdoutCompanion");
                }
                groups.add(Map.of("mapHash",entry.getKey(),"gameIds",members.stream().map(g->g.gameId).toList(),
                        "split",holdout?"holdout":"train","cohortTags",groupCohorts));
                if(holdout) {
                    Game representative=members.stream().filter(g->g.later).findFirst().orElseGet(()->members.stream().filter(g->g.oldHoldout).findFirst().orElse(members.get(0)));
                    if(representative.validTeacherTurns.isEmpty())unavailableHoldout.add(entry.getKey());
                    else select(selection,representative,20,"holdout");
                } else members.stream().filter(g->g.validTeacherTurns.size()>=16).findFirst().ifPresent(trainRepresentatives::add);
            }
            Map<String,String> rank=new HashMap<>();for(Game g:trainRepresentatives)rank.put(g.mapHash,sha(("indrill-capacity-v1/train/"+g.mapHash).getBytes(StandardCharsets.US_ASCII)));
            trainRepresentatives.sort(Comparator.comparing(g->rank.get(g.mapHash)));
            require(trainRepresentatives.size()>=128,"Fewer than128 eligible training map groups");
            for(int i=0;i<128;i++) {Game g=trainRepresentatives.get(i);select(selection,g,16,"B");if(i<16)select(selection,g,4,"A");}
            List<Selected> ordered=new ArrayList<>(selection.values());ordered.sort(Comparator.comparingLong((Selected s)->s.gameId).thenComparingInt(s->s.teacherSeat).thenComparingInt(s->s.turn));
            result.put("mapGroups",groups);result.put("selection",ordered);result.put("panelCounts",panelCounts(ordered));
            result.put("unavailableHoldoutMaps",unavailableHoldout);
            require(panelCounts(ordered).get("A")==64&&panelCounts(ordered).get("B")==2048,"Fixed panel cardinalities differ");
            result.put("complete",true);
        } catch(Exception e) {failures.add(e.toString());}
        result.put("games",games);result.put("contractFailures",failures);
        writeNew(out.resolve("teacher-preflight.json"),result);
        require(failures.isEmpty(),"Preflight failed; retained failure manifest: "+failures);
    }
    static Map<String,Integer> panelCounts(List<Selected> rows) {
        Map<String,Integer> counts=new TreeMap<>();for(Selected row:rows)for(String tag:row.panelTags)counts.merge(tag,1,Integer::sum);return counts;
    }

    static String inputHash(GameState s,IndrillFeatures f,int[] costs,boolean[] legalBuild) throws Exception {
        MessageDigest md=MessageDigest.getInstance("SHA-256");
        try(DataOutputStream out=new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(),md))) {
            out.write("BTKCI001".getBytes(StandardCharsets.US_ASCII));
            for(int value:new int[]{s.width,s.height,s.size,s.regionCount,64,GameState.PAINT_PER_TURN})out.writeInt(value);
            ints(out,s.regionId);ints(out,costs);bools(out,legalBuild);ints(out,f.buildCost);bools(out,f.legalInk);
            out.writeInt(s.size*4);for(int[] row:f.neighbors)for(int v:row)out.writeInt(v);
            ints(out,f.regionSizes);out.writeInt(s.size*64);
            for(float[] row:f.x)for(float v:row) {require(Float.isFinite(v),"Nonfinite feature");out.writeInt(Float.floatToRawIntBits(v));}
        }
        return HexFormat.of().formatHex(md.digest());
    }
    static void ints(DataOutputStream out,int[] values) throws Exception {out.writeInt(values.length);for(int v:values)out.writeInt(v);}
    static void bools(DataOutputStream out,boolean[] values) throws Exception {out.writeInt(values.length);for(boolean v:values)out.writeBoolean(v);}
    static Map<String,Object> observation(Game game,Selected selected,GameState state,IndrillFeatures features,Rules.PlayerMoves moves,int frameIndex) throws Exception {
        int[] costs=new int[state.size];boolean[] legalBuild=new boolean[state.size];
        for(int c=0;c<state.size;c++){costs[c]=state.placeCost(c);legalBuild[c]=features.buildCost[c]>0;}
        int[] cells=Arrays.copyOf(moves.cells,moves.count);Arrays.sort(cells);
        int paint=0,previous=-1;for(int c:cells){require(c>previous&&legalBuild[c],"Invalid accepted teacher cell");paint+=costs[c];previous=c;}
        require(paint==moves.cost&&paint<=GameState.PAINT_PER_TURN,"Accepted paint mismatch");
        int ink=moves.disrupts.isEmpty()?-1:moves.disrupts.get(0);
        require(moves.disrupts.size()<=1&&(ink<0||features.legalInk[ink]),"Invalid accepted teacher ink");
        Map<String,Object> row=new LinkedHashMap<>();row.put("schema","indrill-capacity-observation-v1");
        row.put("observationId",selected.observationId);row.put("mapHash",game.mapHash);row.put("gameId",game.gameId);
        row.put("replaySha256",game.replaySha256);row.put("teacherSeat",game.teacherSeat);row.put("teacherAgentId",game.teacherAgentId);
        row.put("turn",selected.turn);row.put("teacherFrameIndex",frameIndex);row.put("split",selected.split);
        row.put("panelTags",selected.panelTags);row.put("cohortTags",selected.cohortTags);
        row.put("width",state.width);row.put("height",state.height);row.put("size",state.size);row.put("regionCount",state.regionCount);
        row.put("featureCount",64);row.put("paintBudget",GameState.PAINT_PER_TURN);
        row.put("regions",state.regionId);row.put("costs",costs);row.put("legalBuild",legalBuild);row.put("buildCost",features.buildCost);
        row.put("legalInk",features.legalInk);row.put("neighbors",features.neighbors);row.put("regionSizes",features.regionSizes);row.put("features",features.x);
        row.put("targetCells",cells);row.put("targetInk",ink);row.put("acceptedPaint",paint);
        row.put("hadSkippedActions",!moves.skipped.isEmpty());row.put("skippedActions",List.copyOf(moves.skipped));
        row.put("inputSha256",inputHash(state,features,costs,legalBuild));return row;
    }

    static void export(Path root,Path out) throws Exception {
        Path planPath=out.resolve("teacher-preflight.json"),rowsPath=out.resolve("teacher.jsonl"),manifestPath=out.resolve("dataset-manifest.json");
        require(!Files.exists(rowsPath)&&!Files.exists(manifestPath),"Refusing existing dataset output");
        JsonObject plan=read(planPath);require(plan.get("complete").getAsBoolean(),"Preflight incomplete");
        require(plan.get("schema").getAsString().equals("indrill-capacity-preflight-v1"),"Wrong preflight schema");
        for(String section:List.of("sourceRuntimePins","inputManifestPins"))for(var entry:plan.getAsJsonObject(section).entrySet())
            require(fileSha(within(root,entry.getKey())).equals(entry.getValue().getAsString()),"Pinned input changed: "+entry.getKey());
        List<Game> games=Arrays.asList(JSON.fromJson(plan.get("games"),Game[].class));
        List<Selected> selection=Arrays.asList(JSON.fromJson(plan.get("selection"),Selected[].class));
        Map<Long,Map<Integer,Selected>> needed=new TreeMap<>();for(Selected s:selection)
            require(needed.computeIfAbsent(s.gameId,k->new TreeMap<>()).put(s.turn,s)==null,"Duplicate selection tuple");
        Map<String,Object> manifest=new LinkedHashMap<>();manifest.put("schema","indrill-capacity-dataset-v1");manifest.put("complete",false);
        manifest.put("preparationOnly",true);manifest.put("preflightSha256",fileSha(planPath));
        int count=0;List<String> failures=new ArrayList<>();Map<String,Set<String>> splitsByInput=new TreeMap<>();
        Map<String,Map<String,Integer>> labelsByInput=new TreeMap<>();
        try(BufferedWriter writer=Files.newBufferedWriter(rowsPath,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
            for(Game game:games) {
                Path replayPath=within(root,game.replayPath);require(fileSha(replayPath).equals(game.replaySha256),"Replay changed after plan: "+game.gameId);
                if(!needed.containsKey(game.gameId))continue;
                JsonObject raw=read(replayPath);CgReplay replay=CgReplay.parse(raw);List<Wire> wire=wires(raw);
                require(wire.size()==replay.turns(),"Export raw frame count differs");
                GameState state=replay.toState();state.maxTurns=Math.max(state.maxTurns,replay.turns());
                require(mapHash(state).equals(game.mapHash),"Export map hash mismatch");
                Pathing paths=new Pathing(state.size);Rules.updateConnections(state,paths);
                IndrillFeatures features=new IndrillFeatures(state,BotFactory.parameters("indrill-q-v2"));
                for(int turn=0;turn<replay.turns();turn++) {
                    List<Action>[] actions=actual(replay,turn);Selected selected=needed.get(game.gameId).get(turn);
                    if(selected!=null) {
                        require(teacherIssue(replay,wire,turn,game.teacherSeat)==null,"Selected teacher source unavailable");
                        require(state.turn==turn,"Pre-turn state index mismatch");
                        Rules.TurnResult tr=new Rules.TurnResult();Rules.expand(state,game.teacherSeat,actions[game.teacherSeat],paths,tr.moves[game.teacherSeat],tr);
                        require(tr.moves[game.teacherSeat].error==null,"Selected core action error");
                        features.prepare(state,game.teacherSeat);
                        Map<String,Object> row=observation(game,selected,state,features,tr.moves[game.teacherSeat],wire.get(turn).lastStdoutFrame[game.teacherSeat]);
                        writer.write(JSON.toJson(row));writer.newLine();count++;
                        String hash=(String)row.get("inputSha256"),label=JSON.toJson(List.of(row.get("targetCells"),row.get("targetInk")));
                        splitsByInput.computeIfAbsent(hash,k->new TreeSet<>()).add(selected.split);
                        labelsByInput.computeIfAbsent(hash,k->new TreeMap<>()).merge(label,1,Integer::sum);
                    }
                    Rules.resolve(state,actions[0],actions[1],paths);
                }
            }
            require(count==selection.size(),"Export does not cover exact preflight selection");
            require(splitsByInput.values().stream().noneMatch(set->set.size()>1),"Exact model input overlaps train/holdout");
        } catch(Exception e) {failures.add(e.toString());}
        manifest.put("rowCount",count);manifest.put("panelCounts",panelCounts(selection));manifest.put("teacherJsonlSha256",fileSha(rowsPath));
        manifest.put("contractFailures",failures);manifest.put("inputLabelHistograms",labelsByInput);
        manifest.put("complete",failures.isEmpty());writeNew(manifestPath,manifest);
        require(failures.isEmpty(),"Export failed; retained partial rows and failure manifest: "+failures);
    }

    static void synthetic(Path out) throws Exception {
        require(!Files.exists(out),"Refusing existing synthetic directory");Files.createDirectories(out);
        int[] regions=new int[10];for(int i=0;i<10;i++)regions[i]=i;
        int[] terrain=new int[10];terrain[6]=GameState.RIVER;
        GameState state=new GameState(5,2,regions,terrain,new int[]{0,4},new int[]{0,1},new int[][]{{1},{0}});
        state.owner[1]=0;Pathing paths=new Pathing(state.size);Rules.updateConnections(state,paths);
        IndrillFeatures features=new IndrillFeatures(state,BotFactory.parameters("indrill-q-v2"));features.prepare(state,0);
        features.x[0][0]=-0.0f; // Signed-zero cross-language hash check, synthetic only.
        Rules.TurnResult tr=new Rules.TurnResult();Rules.expand(state,0,List.of(Action.place(2,0),Action.place(1,1),Action.disrupt(3)),paths,tr.moves[0],tr);
        require(tr.moves[0].error==null&&tr.moves[0].skipped.isEmpty(),"Synthetic commands unexpectedly invalid");
        Game game=new Game();game.gameId=1;game.teacherSeat=0;game.teacherAgentId=1;game.mapHash=mapHash(state);game.replaySha256="synthetic";
        Selected s=new Selected();s.observationId="1:0:0";s.gameId=1;s.teacherSeat=0;s.turn=0;s.mapHash=game.mapHash;s.split="train";
        s.panelTags=List.of("A","B");s.cohortTags=List.of();
        Map<String,Object> row=observation(game,s,state,features,tr.moves[0],1);
        Files.writeString(out.resolve("synthetic-rows.jsonl"),JSON.toJson(row)+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        JsonObject raw=JsonParser.parseString("{\"frames\":[{}, {\"agentId\":0,\"stdout\":\"WAIT\"}, {\"agentId\":1,\"stdout\":null,\"keyframe\":true}, {\"agentId\":1,\"stdout\":\"WAIT\",\"keyframe\":true}]}").getAsJsonObject();
        List<Wire> wire=wires(raw);require(wire.size()==2&&wire.get(0).stdoutCount[0]==1&&wire.get(0).stdoutCount[1]==0&&wire.get(1).frameCount[0]==0,"Raw presence synthetic check failed");
        writeNew(out.resolve("synthetic-java-result.json"),Map.of("syntheticOnly",true,"passed",true,"inputSha256",row.get("inputSha256"),
                "acceptedPaint",row.get("acceptedPaint"),"rawMissingFramesRemainMissing",true));
    }
}
