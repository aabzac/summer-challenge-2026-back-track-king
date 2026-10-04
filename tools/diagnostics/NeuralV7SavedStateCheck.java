import btk.bot.Bot;
import btk.bot.BotFactory;
import btk.bot.Params;
import btk.bot.neural.NeuralV6Bot;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Protocol;
import btk.core.Rules;
import btk.tools.CgReplay;
import btk.tools.CgValidate;
import com.google.gson.*;

import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.function.LongSupplier;

/** Prepared diagnostic: run once only after V7 integration, tests, artifact freeze and permission. */
public final class NeuralV7SavedStateCheck {
    static final Path SELF = Path.of("tools/diagnostics/NeuralV7SavedStateCheck.java");
    static final Path PLAN = Path.of("build/cg-boss/neural-v6-v09-cached-40/plan.json");
    static final Path PREFIX = Path.of("docs/evidence/neural-v7-prefix.json");
    static final String PREFIX_SHA = "719c7183e29548d90947b4dfaf1213dacbba23289a96c38261fa589e55a87968";
    static final String PLAN_SHA = "d3a78d762fa2dd155e14b16acb1da8d1609edb4efc5da9ed1abe189ebe4a9635";
    static final Path POLICY = Path.of("docs/neural-policy-v7.md");
    static final Path ARTIFACT = Path.of("build/submit-neural-v7");
    static final Path OUT = Path.of("build/analysis/neural-v7/mechanism");
    static final Path FAILED = OUT.resolve("failed-overlay-assumption");
    static final long AMPLE_MS = 60_000, AMPLE_NS = 60_000_000_000L, WARMUP_NS = 500_000_000L;
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    static final int[] EMPTY = new int[0];
    static final LinkedHashMap<String,String> PINS = new LinkedHashMap<>();
    static final List<Map<String,Object>> PARITY_ROWS = new ArrayList<>(), FILL_ROWS = new ArrayList<>();
    static Class<?> v7Class;
    static Constructor<?> v7Constructor;
    static Method fill;
    static Field v6Random, v7Random, v6Features, v7Features, v7Start, v7Budget;
    record Input(int slot, int me, Path path, JsonObject result, JsonObject slotJson) {}
    record PrefixCase(long gameId, int turn, int me, GameState actual, GameState prefix,
                      int[] cells, int cost, int target, JsonObject prior) {}
    record Filled(Map<String,Object> report, int[] cells, long nanos) {}

    public static void main(String[] args) throws Exception {
        require(args.length == 0, "Fixed diagnostic; no adjustable parameters");
        Path intent = OUT.resolve("intent.json"), resultPath = OUT.resolve("result.json");
        require(!Files.exists(intent) && !Files.exists(resultPath), "Preserve prior diagnostic attempts; no implicit repeat");
        require(hash(PREFIX).equals(PREFIX_SHA) && hash(PLAN).equals(PLAN_SHA), "Fixed corpus evidence changed");
        JsonObject plan = read(PLAN), previous = read(PREFIX);
        require(previous.get("complete").getAsBoolean() && previous.get("states").getAsInt() == 800, "Incomplete actual-prefix evidence");
        pin(SELF); pin(PLAN); pin(PREFIX); pin(POLICY); pin(ARTIFACT.resolve("Player.java")); pin(ARTIFACT.resolve("manifest.json"));
        pinTree(FAILED);
        for (String path : List.of("src/main/java", "src/main/resources", "build/classes/java/main", "build/resources/main")) pinTree(Path.of(path));
        for (String item : System.getProperty("java.class.path").split(java.io.File.pathSeparator)) {
            Path path = Path.of(item); if (Files.isRegularFile(path)) pin(path); else if (Files.isDirectory(path)) pinTree(path);
        }
        List<Input> inputs = inputs(plan);
        v7Class = Class.forName("btk.bot.neural.NeuralV7Bot");
        v7Constructor = v7Class.getConstructor(Params.class, LongSupplier.class);
        fill = v7Class.getDeclaredMethod("fill", GameState.class, int.class, int.class, int.class, List.class); fill.setAccessible(true);
        v6Random = field(NeuralV6Bot.class, "random"); v7Random = field(v7Class, "random");
        v6Features = field(NeuralV6Bot.class, "features"); v7Features = field(v7Class, "features");
        v7Start = field(v7Class, "startNs"); v7Budget = field(v7Class, "budgetNs");
        Params params6 = BotFactory.parameters("neural-v6"), params7 = BotFactory.parameters("neural-v7");
        require(params6.SEED == params7.SEED && params6.NOISE == params7.NOISE, "Compared RNG configuration differs");
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", 1); result.put("kind", "fixed V6/V7 ample action parity, actual-prefix fallback mechanism and local fill latency");
        result.put("diagnosticRepair", "First helper failed after4 complete games/80 parity states because it incorrectly required fill to mutate work.owner. Production fill intentionally uses an overlay and leaves its input unchanged. Failed source/classes/intent/log and repair manifest are preserved and pinned under failed-overlay-assumption. This helper verifies all serialized input fields unchanged and independently applies emitted placements through core. No candidate, corpus, actions, evaluation games or selection changed; no first completed result existed.");
        result.put("createdAtUtc", Instant.now().toString()); result.put("newGames", 0);
        result.put("selection", "All40 existing V6 boss replays, all first20 recorded states; fixed70 underpaint prefixes from prior diagnostic");
        result.put("ampleActionBudgetMs", AMPLE_MS); result.put("armOrder", "V6 then V7 at even(slotIndex+turn), otherwise V7 then V6");
        result.put("history", "Both bots observe the recorded public boards. Their feature recordTarget is reset to the actual accepted target after each turn. Per-state shared RNG seed isolates parity; neither server RNG nor interrupted execution history is reconstructed");
        result.put("fillMechanism", "Invoke actual private V7 fill on accepted own prefix via reflection with constant-zero clock, startNs0, budgetNs60000000000; prefix/target are fixed. Validate complete command with Rules.expand; income via Rules.updateConnections/Rules.incomePerTurn outside fill");
        result.put("fillTiming", "After all parity/mechanism checks, one fixed500ms warmup on the first tallest retained underpaint prefix. Each of70 fixed prefixes then invokes real-clock V7 fill once with ample60000ms budget. Timed interval includes reflection invocation but excludes bot init, copying, clock setup, validation, income and report work");
        result.put("candidateRule", "The exact production V7 method is measured; earning membership is frozen at accepted prefix, not recomputed during fill. No hypothetical opponent actions influence rank");
        result.put("configV6", BotFactory.configuration("neural-v6")); result.put("configV7", BotFactory.configuration("neural-v7"));
        result.put("limitations", List.of("Reused boss states are development diagnostics, not independent strength evidence.",
                "Ample action parity does not prove timed V6/V7 decisions will match.",
                "Direct fill invocation tests legal prefix completion, not whether CG actually aborts at a particular phase.",
                "Immediate hypothetical own/net income omits simultaneous opponent actions and future survival; extra paint is not a win guarantee.",
                "Single-process warmed wall timings include reflection overhead and do not reconstruct CodinGame CPU/JIT/scheduling/parsing.",
                "The ample60000ms diagnostic does not enforce the production turn deadline; zero-clock and real-clock selected fills must agree."));
        result.put("javaVersion", System.getProperty("java.version")); result.put("javaClassPath", System.getProperty("java.class.path"));
        result.put("inputPins", pinList()); Files.createDirectories(OUT); write(intent, result);
        List<PrefixCase> prefixes = new ArrayList<>(); int mismatches = 0;
        for (int i = 0; i < inputs.size(); i++) {
            Input input = inputs.get(i); CgReplay replay = CgReplay.load(input.path);
            validateReplay(input, replay, plan);
            GameState actual = replay.toState(); Pathing paths = new Pathing(actual.size); Rules.updateConnections(actual, paths);
            NeuralV6Bot bot6 = new NeuralV6Bot(params6); Bot bot7 = newBot(params7, System::nanoTime);
            bot6.init(actual, input.me); bot7.init(actual, input.me);
            for (int turn = 0; turn < 20; turn++) {
                JsonObject prior = previous.getAsJsonArray("rows").get(PARITY_ROWS.size()).getAsJsonObject();
                require(prior.get("gameId").getAsLong() == replay.gameId && prior.get("turn").getAsInt() == turn && prior.get("seat").getAsInt() == input.me, "Previous prefix order changed");
                long rngSeed = params6.SEED*1_000_003L + input.me*7919L + replay.gameId*31L + turn*104729L;
                v6Random.set(bot6, new Random(rngSeed)); v7Random.set(bot7, new Random(rngSeed));
                String before = Protocol.turnBlock(actual, input.me); List<Action> a6, a7;
                if ((i+turn)%2 == 0) { a6 = bot6.play(actual.copy(), input.me, AMPLE_MS); a7 = bot7.play(actual.copy(), input.me, AMPLE_MS); }
                else { a7 = bot7.play(actual.copy(), input.me, AMPLE_MS); a6 = bot6.play(actual.copy(), input.me, AMPLE_MS); }
                require(before.equals(Protocol.turnBlock(actual,input.me)), "Actual board mutated");
                require(bot6.lastAbortedPhase == 0 && v7Class.getField("lastAbortedPhase").getInt(bot7) == 0, "Ample bot unexpectedly aborted");
                String line6 = Action.format(a6), line7 = Action.format(a7); boolean match = line6.equals(line7); if (!match) mismatches++;
                validateCommand(actual,input.me,a6); validateCommand(actual,input.me,a7);
                List<Action> ownActions = Action.parseLine(replay.turnStdout.get(turn)[input.me]);
                Rules.PlayerMoves own = validateCommand(actual,input.me,ownActions);
                int target = own.disrupts.isEmpty() ? -1 : own.disrupts.get(0);
                require(own.cost == prior.get("acceptedPaint").getAsInt() && own.count == prior.get("acceptedCells").getAsInt() && target == prior.get("target").getAsInt(), "Accepted prefix changed");
                require(Arrays.equals(Arrays.copyOf(own.cells,own.count), GSON.fromJson(prior.get("prefixCells"), int[].class)), "Recorded prefix cells changed");
                Map<String,Object> row = new LinkedHashMap<>();
                row.put("gameId",replay.gameId); row.put("turn",turn); row.put("seat",input.me); row.put("height",actual.height); row.put("noiseSeed",rngSeed);
                row.put("v6Actions",line6); row.put("v7Actions",line7); row.put("actionsMatch",match); row.put("recordedPaint",own.cost); PARITY_ROWS.add(row);
                if (own.cost < 3) {
                    GameState prefix = actual.copy(); applyOwn(prefix,input.me,own.cells,own.count);
                    PrefixCase pc = new PrefixCase(replay.gameId,turn,input.me,actual.copy(),prefix,Arrays.copyOf(own.cells,own.count),own.cost,target,prior);
                    prefixes.add(pc); Filled mechanism = runFill(pc,params7,false);
                    FILL_ROWS.add(mechanism.report);
                }
                Rules.TurnResult recorded = Rules.resolve(actual,Action.parseLine(replay.turnStdout.get(turn)[0]),Action.parseLine(replay.turnStdout.get(turn)[1]),paths);
                require(recorded.moves[input.me].cost == own.cost, "Replay expansion differs");
                recordTarget(v6Features.get(bot6),target); recordTarget(v7Features.get(bot7),target);
            }
            System.out.println("V7 mechanism/parity " + (i+1) + "/40; states=" + PARITY_ROWS.size()+"; mismatches="+mismatches);
        }
        require(PARITY_ROWS.size()==800 && prefixes.size()==70, "Fixed panel incomplete");
        PrefixCase warm = prefixes.get(0); for(PrefixCase pc : prefixes) if(pc.prefix.height>warm.prefix.height) warm=pc;
        long start = System.nanoTime(); int warmed=0;
        do { runFill(warm,params7,true); warmed++; } while(System.nanoTime()-start<WARMUP_NS);
        result.put("warmup",Map.of("gameId",warm.gameId,"turn",warm.turn,"height",warm.prefix.height,"requestedMs",500,"calls",warmed,"actualWallMs",(System.nanoTime()-start)/1e6));
        List<Double> latency = new ArrayList<>(); int fillMismatches=0;
        for(int i=0;i<prefixes.size();i++) {
            Filled timed=runFill(prefixes.get(i),params7,true); Map<String,Object> mechanism=FILL_ROWS.get(i);
            boolean match=Arrays.equals((int[])mechanism.get("addedCells"),timed.cells); if(!match) fillMismatches++;
            mechanism.put("realClockFillMatches",match); mechanism.put("fillWallMs",timed.nanos/1e6); latency.add(timed.nanos/1e6);
        }
        verifyPins(); result.put("engineReplaysExact",40); result.put("states",800); result.put("fullBotCalls",1600); result.put("mechanismFillCalls",70); result.put("measuredFillCalls",70);
        result.put("actionMismatches",mismatches); result.put("constantVsRealClockFillMismatches",fillMismatches); result.put("parityPass",mismatches==0 && fillMismatches==0);
        result.put("fillSummary",Map.of("all",summarize(FILL_ROWS),"heightBelow18",summarize(FILL_ROWS.stream().filter(r -> (int)r.get("height")<18).toList()),
                "height18AndAbove",summarize(FILL_ROWS.stream().filter(r -> (int)r.get("height")>=18).toList())));
        result.put("fillLatencyMs",latency(latency)); result.put("parityRows",PARITY_ROWS); result.put("fillRows",FILL_ROWS); result.put("complete",true); result.put("finishedAtUtc",Instant.now().toString());
        write(resultPath,result); System.out.println("Complete: action mismatches="+mismatches+"; fill clock mismatches="+fillMismatches+"; "+GSON.toJson(result.get("fillSummary")));
    }

    static Filled runFill(PrefixCase pc,Params params,boolean realClock) throws Exception {
        Bot bot=newBot(params,realClock ? System::nanoTime : () -> 0L); bot.init(pc.actual,pc.me);
        GameState work=pc.prefix.copy(); String inputSnapshot=GSON.toJson(work); List<Action> actions=new ArrayList<>();
        for(int c:pc.cells) actions.add(Action.place(work.x(c),work.y(c)));
        v7Class.getField("lastPlacements").setInt(bot,pc.cells.length);
        v7Class.getField("lastTarget").setInt(bot,pc.target);
        v7Budget.setLong(bot,AMPLE_NS); v7Start.setLong(bot,realClock ? System.nanoTime() : 0L);
        long start=System.nanoTime(); fill.invoke(bot,work,pc.me,3-pc.cost,pc.target,actions); long nanos=System.nanoTime()-start;
        require(inputSnapshot.equals(GSON.toJson(work)),"Fill mutated input GameState; expected overlay-only construction");
        if(pc.target>=0) actions.add(Action.disrupt(pc.target)); if(actions.isEmpty()) actions.add(Action.waitAction());
        Rules.PlayerMoves accepted=validateCommand(pc.actual,pc.me,actions);
        require(accepted.count>=pc.cells.length && accepted.cost>=pc.cost,"Fill removed actual prefix");
        for(int k=0;k<pc.cells.length;k++) require(accepted.cells[k]==pc.cells[k],"Fill changed actual prefix");
        require(accepted.disrupts.size()==(pc.target<0?0:1) && (pc.target<0 || accepted.disrupts.get(0)==pc.target),"Fill changed actual target");
        int[] added=Arrays.copyOfRange(accepted.cells,pc.cells.length,accepted.count);
        GameState reference=pc.actual.copy(); applyOwn(reference,pc.me,accepted.cells,accepted.count);
        int[] initial=income(pc.prefix), after=income(reference); List<Map<String,Object>> steps=new ArrayList<>();
        GameState stepState=pc.prefix.copy(); int minOwn=0,minNet=0;
        for(int c:added) {
            require(stepState.regionId[c]!=pc.target,"Fill entered selected disruption target");
            applyOwn(stepState,pc.me,new int[]{c},1); int[] stepIncome=income(stepState);
            int own=stepIncome[pc.me]-initial[pc.me],net=(stepIncome[pc.me]-stepIncome[1-pc.me])-(initial[pc.me]-initial[1-pc.me]);
            minOwn=Math.min(minOwn,own); minNet=Math.min(minNet,net);
            steps.add(Map.of("cell",c,"cost",stepState.placeCost(c),"income",stepIncome,"ownIncomeDelta",own,"netIncomeDelta",net));
        }
        Map<String,Object> row=new LinkedHashMap<>(); row.put("gameId",pc.gameId); row.put("turn",pc.turn); row.put("seat",pc.me); row.put("height",pc.actual.height);
        row.put("actualPrefixCells",pc.cells); row.put("actualPrefixPaint",pc.cost); row.put("target",pc.target); row.put("addedCells",added);
        row.put("recoveredPaint",accepted.cost-pc.cost); row.put("paintLeft",3-accepted.cost); row.put("filledActions",Action.format(actions));
        row.put("prefixIncome",initial); row.put("filledIncome",after); row.put("ownIncomeDelta",after[pc.me]-initial[pc.me]);
        row.put("netIncomeDelta",(after[pc.me]-after[1-pc.me])-(initial[pc.me]-initial[1-pc.me])); row.put("steps",steps);
        row.put("minStepOwnIncomeDelta",minOwn); row.put("minStepNetIncomeDelta",minNet);
        return new Filled(row,added,nanos);
    }
    static Bot newBot(Params p,LongSupplier clock) throws Exception { return (Bot)v7Constructor.newInstance(p,clock); }
    static void recordTarget(Object feature,int target) throws Exception { feature.getClass().getMethod("recordTarget",int.class).invoke(feature,target); }
    static Rules.PlayerMoves validateCommand(GameState s,int me,List<Action> actions) {
        Rules.TurnResult tr=new Rules.TurnResult(); Rules.expand(s,me,actions,new Pathing(s.size),tr.moves[me],tr);
        require(tr.moves[me].error==null && tr.moves[me].skipped.isEmpty() && tr.moves[me].disrupts.size()<=1,"Illegal diagnostic action"); return tr.moves[me];
    }
    static void applyOwn(GameState s,int me,int[] cells,int count) {
        Rules.applyPlacements(s,me==0?cells:EMPTY,me==0?count:0,me==1?cells:EMPTY,me==1?count:0); Rules.updateConnections(s,new Pathing(s.size));
    }
    static int[] income(GameState s) { int[] result=new int[2]; Rules.incomePerTurn(s,result); return result; }
    static List<Input> inputs(JsonObject plan) throws Exception {
        require(plan.getAsJsonArray("slots").size()==40,"Incomplete plan"); List<Input> inputs=new ArrayList<>(); Set<Integer> seen=new HashSet<>();
        JsonObject build=plan.getAsJsonObject("builds").getAsJsonObject("candidate");
        for(JsonElement item:plan.getAsJsonArray("slots")) {
            JsonObject slot=item.getAsJsonObject(); int number=slot.get("slot").getAsInt(); require(number>=1 && number<=40 && seen.add(number),"Duplicate/out-of-range slot");
            Path dir=PLAN.getParent().resolve(String.format("%04d",number)); Path resultPath=dir.resolve("result.json"),raw=dir.resolve("canonical.json");
            JsonObject result=read(resultPath),capture=result.getAsJsonObject("capture"),attribution=capture.getAsJsonObject("attribution");
            require(result.get("planId").equals(plan.get("planId")) && result.getAsJsonObject("slot").equals(slot),"Result identity differs");
            require(capture.get("ok").getAsBoolean() && capture.get("expectedBuildMatches").getAsBoolean(),"Unverified captured artifact");
            require(attribution.get("buildId").equals(build.get("buildId")) && attribution.get("artifactSha256").equals(build.get("sourceSha256"))
                    && attribution.get("configSha256").equals(build.get("configSha256")),"Wrong capture artifact");
            require(hash(raw).equals(capture.get("rawSha256").getAsString()),"Changed canonical replay");
            Path retained=Path.of(capture.get("path").getAsString()); require(hash(retained).equals(hash(raw)),"Changed retained replay");
            pin(resultPath);pin(raw);pin(retained); inputs.add(new Input(number,slot.get("seat").getAsInt(),raw,result,slot));
        }
        inputs.sort(Comparator.comparingInt(Input::slot)); return inputs;
    }
    static void validateReplay(Input input,CgReplay replay,JsonObject plan) throws Exception {
        require(replay.turns()>=20 && replay.gameId==input.result.getAsJsonObject("capture").get("gameId").getAsLong(),"Replay identity/length differs");
        JsonObject outcome=input.result.getAsJsonObject("outcome");
        require(input.me==outcome.get("seat").getAsInt() && outcome.get("seed").equals(input.slotJson.get("seed")),"Wrong seed/seat");
        require(replay.seed.trim().equals("seed="+input.slotJson.get("seed").getAsString()),"Wrong replay seed");
        require(replay.agentIds[1-input.me]==plan.get("bossAgentId").getAsInt() && replay.isBossGame(),"Wrong boss identity");
        require(hashBytes(replay.globalGraphics.getBytes(StandardCharsets.UTF_8)).equals(outcome.get("mapHash").getAsString()),"Wrong map");
        CgValidate.Outcome check=CgValidate.validate(replay,false); require(check.firstDivergenceTurn<0,"Engine divergence: "+check.reason);
    }
    static Map<String,Object> summarize(List<Map<String,Object>> rows) {
        int paint=0,left=0,filled=0,ownP=0,ownN=0,netP=0,netN=0,ownSum=0,netSum=0,minOwn=0,minNet=0,stepOwnN=0,stepNetN=0,minStepOwn=0,minStepNet=0;
        for(Map<String,Object> r:rows) {
            int p=(int)r.get("recoveredPaint"),o=(int)r.get("ownIncomeDelta"),n=(int)r.get("netIncomeDelta"); paint+=p;left+=(int)r.get("paintLeft");if(p>0)filled++;
            if(o>0)ownP++;if(o<0)ownN++;if(n>0)netP++;if(n<0)netN++;ownSum+=o;netSum+=n;minOwn=Math.min(minOwn,o);minNet=Math.min(minNet,n);
            int so=(int)r.get("minStepOwnIncomeDelta"),sn=(int)r.get("minStepNetIncomeDelta");if(so<0)stepOwnN++;if(sn<0)stepNetN++;minStepOwn=Math.min(minStepOwn,so);minStepNet=Math.min(minStepNet,sn);
        }
        Map<String,Object> r=new LinkedHashMap<>();r.put("prefixes",rows.size());r.put("filledPrefixes",filled);r.put("recoveredPaint",paint);r.put("paintLeft",left);
        r.put("ownIncomePositive",ownP);r.put("ownIncomeNegative",ownN);r.put("netIncomePositive",netP);r.put("netIncomeNegative",netN);r.put("ownIncomeDeltaSum",ownSum);r.put("netIncomeDeltaSum",netSum);
        r.put("ownIncomeMinimumIncludingNoChange",minOwn);r.put("netIncomeMinimumIncludingNoChange",minNet);r.put("anyOwnNegativeStepPrefixes",stepOwnN);r.put("anyNetNegativeStepPrefixes",stepNetN);
        r.put("ownStepMinimumIncludingNoChange",minStepOwn);r.put("netStepMinimumIncludingNoChange",minStepNet);return r;
    }
    static Map<String,Object> latency(List<Double> values) {
        List<Double> sorted=new ArrayList<>(values);Collections.sort(sorted);double total=0;for(double v:values)total+=v;
        return Map.of("calls",values.size(),"total",total,"mean",total/values.size(),"maximum",sorted.get(sorted.size()-1),"p95NearestRank",sorted.get((int)Math.ceil(0.95*sorted.size())-1));
    }
    static Field field(Class<?> type,String name) throws Exception { Field f=type.getDeclaredField(name);f.setAccessible(true);return f; }
    static JsonObject read(Path path)throws Exception{return JsonParser.parseString(Files.readString(path)).getAsJsonObject();}
    static String hash(Path path)throws Exception{return hashBytes(Files.readAllBytes(path));}
    static String hashBytes(byte[] data)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}
    static void pin(Path path)throws Exception{PINS.put(path.toString().replace('\\','/'),hash(path));}
    static void pinTree(Path path)throws Exception{try(var files=Files.walk(path)){for(Path f:files.filter(Files::isRegularFile).sorted().toList())pin(f);}}
    static List<Map<String,String>> pinList(){List<Map<String,String>> result=new ArrayList<>();PINS.forEach((p,s)->result.add(Map.of("path",p,"sha256",s)));return result;}
    static void verifyPins()throws Exception{for(var p:PINS.entrySet())require(hash(Path.of(p.getKey())).equals(p.getValue()),"Input changed during diagnostic: "+p.getKey());}
    static void require(boolean ok,String msg){if(!ok)throw new IllegalStateException(msg);}
    static void write(Path path,Object value)throws Exception{Files.writeString(path,GSON.toJson(value)+"\n",StandardOpenOption.CREATE_NEW);}
}
