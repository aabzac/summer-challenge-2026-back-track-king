import btk.bot.Bot;
import btk.bot.BotFactory;
import btk.bot.Params;
import btk.bot.neural.NeuralV3Bot;
import btk.bot.neural.NeuralV3Features;
import btk.bot.neural.NeuralV5Bot;
import btk.bot.neural.NeuralV5Features;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Protocol;
import btk.core.Rules;
import btk.tools.CgReplay;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * One fixed V3/V5 comparison on the existing 800 real board prefixes; no new games.
 * Run ONLY after full tests + cgValidate, alone, with uninstrumented main classes/resources.
 * Calls each bot once per position at ample real-wall-clock budget, alternating arm order;
 * compares every full/incremental feature row on V3's chosen within-turn placement prefixes.
 * Timing/allocated bytes bracket bot.play only. The additional feature checks are untimed.
 * Output and intent are write-once. These reused positions provide development evidence,
 * not independent strength evidence or a reconstruction of CG execution conditions.
 */
class NeuralV5SavedStateCheck {
    static final Path SELF = Path.of("tools/diagnostics/NeuralV5SavedStateCheck.java");
    static final Path PAINT = Path.of("docs/evidence/neural-v3-arena-paint-20261004.json");
    static final Path PRIOR = Path.of("build/analysis/neural-v3-arena-20261004/budget-probe.json");
    static final Path POLICY = Path.of("docs/neural-policy-v5.md");
    static final long BUDGET_MS = 60_000, WARMUP_MS = 500;
    static final int TURNS = 20;
    static final Field V3_FEATURES = field(NeuralV3Bot.class, "features"), V5_FEATURES = field(NeuralV5Bot.class, "features");
    static final Field V3_RANDOM = field(NeuralV3Bot.class, "random"), V5_RANDOM = field(NeuralV5Bot.class, "random");
    static final com.sun.management.ThreadMXBean ALLOCATION = allocationBean();
    static long fullPasses, incrementalPasses, sabotageRows, buildRows, featureValues, featureMismatches;
    static final List<Map<String,Object>> mismatchExamples = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("new-output-directory");
        Path dir = Path.of(args[0]), output = dir.resolve("results.json"), intent = dir.resolve("intent.json");
        if (Files.exists(output) || Files.exists(intent)) throw new IllegalStateException("Preserve the first attempt; no implicit repeat");
        Files.createDirectories(dir);
        // Accidentally loading the preceding instrumented profile must fail before any bot calls.
        try { NeuralV3Features.class.getField("profileNanos"); throw new IllegalStateException("Instrumented V3 on classpath"); }
        catch (NoSuchFieldException expected) { }
        JsonObject evidence = JsonParser.parseString(Files.readString(PAINT)).getAsJsonObject();
        Path study = null;
        for (var item : evidence.getAsJsonArray("inputs")) {
            JsonObject pin = item.getAsJsonObject(); checkPin(pin);
            if (pin.get("path").getAsString().endsWith("/player-study.json")) study = Path.of(pin.get("path").getAsString());
        }
        for (String key : List.of("rawReplayPins", "sourcePins")) for (var item : evidence.getAsJsonArray(key)) checkPin(item.getAsJsonObject());
        if (study == null) throw new IllegalStateException("Missing study input");
        var games = JsonParser.parseString(Files.readString(study)).getAsJsonObject().getAsJsonArray("games");
        var priorRows = JsonParser.parseString(Files.readString(PRIOR)).getAsJsonObject().getAsJsonArray("rows");
        if (games.size() != 40 || priorRows.size() != 800) throw new IllegalStateException("Expected fixed 40 x 20 panel");
        Params params3 = BotFactory.parameters("neural-v3"), params5 = BotFactory.parameters("neural-v5");
        if (!BotFactory.configuration("neural-v3").equals(BotFactory.configuration("neural-v5"))) throw new IllegalStateException("Configurations differ");
        if (!Arrays.equals(NeuralV3Features.BUILD_NAMES, NeuralV5Features.BUILD_NAMES)
                || !Arrays.equals(NeuralV3Features.SABOTAGE_NAMES, NeuralV5Features.SABOTAGE_NAMES)) throw new IllegalStateException("Feature names/order changed");
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", 1); result.put("kind", "fixed uninstrumented V3/V5 saved-state equivalence and cost diagnostic; zero new games");
        result.put("selection", "all 40 previously pinned arena games, every turn 0..19, one paired pass");
        result.put("budgetMs", BUDGET_MS); result.put("armOrder", "V3 then V5 when (gameIndex+turn) even, V5 then V3 otherwise");
        result.put("memory", "both bots and separate feature instances observe actual recorded board prefixes; previous accepted actual disruption overrides diagnostic target memory");
        result.put("rng", "same deterministic per-state seed as prior probe; no claim of reconstructed CG RNG history");
        result.put("featureTrajectory", "full preparation, sabotage rows/closure flags, then all build rows and incremental preparation on V3 chosen placements; actual replay alone advances the next turn");
        result.put("configuration", BotFactory.configuration("neural-v3"));
        result.put("inputs", List.of(pin(SELF), pin(PAINT), pin(PRIOR), pin(POLICY), pin(study),
                pin(Path.of("build/analysis/neural-v3-arena-20261004/gson-2.11.0.jar")),
                pin(Path.of("build/submit-neural-v5/Player.java")), pin(Path.of("build/submit-neural-v5/manifest.json"))));
        List<Map<String,Object>> runtime = new ArrayList<>();
        for (String path : List.of("src/main/java", "src/main/resources", "build/classes/java/main", "build/resources/main")) {
            try (var files = Files.walk(Path.of(path))) { for (Path file : files.filter(Files::isRegularFile).sorted().toList()) runtime.add(pin(file)); }
        }
        result.put("runtimeAndSourcePins", runtime); result.put("javaVersion", System.getProperty("java.version"));
        result.put("javaClassPath", System.getProperty("java.class.path")); result.put("allocationSupported", ALLOCATION != null);
        result.put("limitations", List.of("One local pass on reused positions; timings are development diagnostics, not CG deadline or strength evidence.",
                "Both bots have real 60000-ms clocks; no interrupt was expected. Different interrupted decisions remain possible in actual timed games.",
                "Two disposable warm-ups, V3 then V5, 500 ms each on the same fixed largest map; this is not full CG lifecycle emulation.",
                "Timing and allocation bracket bot.play only; input copies, feature differential checks, replay progression and reporting are excluded.",
                "Same RNG per state isolates decision equivalence, but does not reconstruct historical arena random/JIT/scheduling state.",
                "Inclusive existing prepare/sabotage/build counters are diagnostic only. Allocation counts do not identify GC pauses."));
        write(intent, result);
        JsonObject warmGame = games.get(0).getAsJsonObject();
        for (var item : games) if (item.getAsJsonObject().get("height").getAsInt() > warmGame.get("height").getAsInt()) warmGame = item.getAsJsonObject();
        CgReplay warmReplay = CgReplay.load(Path.of(warmGame.get("file").getAsString()));
        GameState warmState = warmReplay.toState(); Rules.updateConnections(warmState, new Pathing(warmState.size));
        int warmSeat = warmGame.get("me").getAsInt();
        result.put("warmup", List.of(warmup(new NeuralV3Bot(params3), warmState, warmSeat), warmup(new NeuralV5Bot(params5), warmState, warmSeat)));
        List<Map<String,Object>> rows = new ArrayList<>(); long actionMismatches = 0, priorMismatches = 0;
        for (int gi = 0; gi < games.size(); gi++) {
            JsonObject game = games.get(gi).getAsJsonObject(); int me = game.get("me").getAsInt();
            CgReplay replay = CgReplay.load(Path.of(game.get("file").getAsString()));
            if (replay.gameId != game.get("gameId").getAsLong() || replay.turns() < TURNS) throw new IllegalStateException("Replay identity/length changed");
            GameState actual = replay.toState(); Pathing paths = new Pathing(actual.size); Rules.updateConnections(actual, paths);
            NeuralV3Bot bot3 = new NeuralV3Bot(params3); NeuralV5Bot bot5 = new NeuralV5Bot(params5);
            bot3.init(actual, me); bot5.init(actual, me);
            NeuralV3Features internal3 = (NeuralV3Features) V3_FEATURES.get(bot3);
            NeuralV5Features internal5 = (NeuralV5Features) V5_FEATURES.get(bot5);
            FeaturePair check = new FeaturePair(actual);
            for (int turn = 0; turn < TURNS; turn++) {
                if (actual.turn != turn || replay.failure(turn, 0) != null || replay.failure(turn, 1) != null) throw new IllegalStateException("Bad recorded turn");
                long noiseSeed = params3.SEED * 1_000_003L + me * 7919L + replay.gameId * 31L + turn * 104729L;
                V3_RANDOM.set(bot3, new Random(noiseSeed)); V5_RANDOM.set(bot5, new Random(noiseSeed));
                Measurement v3, v5;
                if ((gi + turn) % 2 == 0) { v3 = measure(bot3, internal3, null, actual, me); v5 = measure(bot5, null, internal5, actual, me); }
                else { v5 = measure(bot5, null, internal5, actual, me); v3 = measure(bot3, internal3, null, actual, me); }
                if (bot3.lastAbortedPhase != 0 || bot5.lastAbortedPhase != 0) throw new IllegalStateException("Ample bot call aborted");
                String line3 = Action.format(v3.actions), line5 = Action.format(v5.actions);
                boolean actionsMatch = line3.equals(line5); if (!actionsMatch) actionMismatches++;
                JsonObject prior = priorRows.get(rows.size()).getAsJsonObject();
                if (prior.get("gameId").getAsLong() != replay.gameId || prior.get("turn").getAsInt() != turn) throw new IllegalStateException("Prior state order drift");
                boolean priorMatch = line3.equals(prior.getAsJsonObject("ample").get("actions").getAsString()); if (!priorMatch) priorMismatches++;
                String inputBefore = Protocol.turnBlock(actual, me);
                long mismatchesBefore = featureMismatches;
                check.compare(actual, me, v3.actions, replay.gameId);
                if (!inputBefore.equals(Protocol.turnBlock(actual, me))) throw new IllegalStateException("Feature input mutated");
                Map<String,Object> row = new LinkedHashMap<>();
                row.put("gameId", replay.gameId); row.put("turn", turn); row.put("me", me); row.put("height", actual.height); row.put("width", actual.width); row.put("noiseSeed", noiseSeed);
                row.put("v3", v3.toMap()); row.put("v5", v5.toMap()); row.put("actionsMatch", actionsMatch);
                row.put("v3PriorAmpleActionMatch", priorMatch); row.put("featureMismatches", featureMismatches - mismatchesBefore);
                Rules.TurnResult recorded = Rules.resolve(actual, Action.parseLine(replay.turnStdout.get(turn)[0]), Action.parseLine(replay.turnStdout.get(turn)[1]), paths);
                JsonObject expected = game.getAsJsonArray("trace").get(turn).getAsJsonObject();
                if (recorded.moves[me].cost != expected.getAsJsonArray("paint").get(me).getAsInt()
                        || recorded.moves[me].count != expected.getAsJsonArray("cells").get(me).getAsInt()
                        || actual.score[0] != expected.getAsJsonArray("score").get(0).getAsInt()
                        || actual.score[1] != expected.getAsJsonArray("score").get(1).getAsInt()) throw new IllegalStateException("Recorded prefix drift");
                int target = recorded.moves[me].disrupts.isEmpty() ? -1 : recorded.moves[me].disrupts.get(0);
                internal3.recordTarget(target); internal5.recordTarget(target); check.a.recordTarget(target); check.b.recordTarget(target);
                row.put("recordedPaint", recorded.moves[me].cost); rows.add(row);
            }
            System.out.println("Saved-state equivalence " + (gi + 1) + "/40; states=" + rows.size() + "; feature mismatches=" + featureMismatches);
        }
        for (Map<String,Object> pin : runtime) if (!hash(Path.of((String) pin.get("path"))).equals(pin.get("sha256"))) throw new IllegalStateException("Source/runtime changed during diagnostic");
        result.put("states", rows.size()); result.put("botCalls", 2 * rows.size()); result.put("actionMismatches", actionMismatches);
        result.put("v3PriorAmpleActionMismatches", priorMismatches); result.put("featureMismatches", featureMismatches);
        result.put("fullPreparePasses", fullPasses); result.put("incrementalPreparePasses", incrementalPasses);
        result.put("sabotageCandidateRows", sabotageRows); result.put("buildCandidateRows", buildRows); result.put("featureValuesCompared", featureValues);
        result.put("mismatchExamples", mismatchExamples); result.put("rows", rows); result.put("complete", rows.size() == 800);
        result.put("equivalencePass", actionMismatches == 0 && priorMismatches == 0 && featureMismatches == 0);
        write(output, result);
        System.out.println("Complete. Action mismatches=" + actionMismatches + ", feature mismatches=" + featureMismatches + ", values=" + featureValues);
    }

    static final class FeaturePair {
        final NeuralV3Features a; final NeuralV5Features b;
        final int[] idsA, idsB; final float[][] sabA, sabB, buildA, buildB;
        final Pathing paths;
        FeaturePair(GameState s) {
            a = new NeuralV3Features(s); b = new NeuralV5Features(s); paths = new Pathing(s.size);
            idsA = new int[Math.max(s.size, s.regionCount)]; idsB = new int[idsA.length];
            sabA = new float[s.regionCount][NeuralV3Features.SABOTAGE_F]; sabB = new float[s.regionCount][NeuralV5Features.SABOTAGE_F];
            buildA = new float[s.size][NeuralV3Features.BUILD_F]; buildB = new float[s.size][NeuralV5Features.BUILD_F];
        }
        void compare(GameState actual, int me, List<Action> actions, long gameId) {
            GameState s = actual.copy(); a.observe(s, me); b.observe(s, me);
            a.prepare(s, me, true); b.prepare(s, me, true); fullPasses++;
            String location = gameId + ":" + actual.turn;
            same(a.inactivePairs(), b.inactivePairs(), location + ":inactivePairs/full");
            int nA = a.sabotageCandidates(s, me, idsA, sabA), nB = b.sabotageCandidates(s, me, idsB, sabB);
            rows(nA, nB, sabA, sabB, location + ":sabotage"); sabotageRows += nA;
            for (int r = 0; r < s.regionCount; r++) {
                same(a.closes[r] ? 1 : 0, b.closes[r] ? 1 : 0, location + ":closes:" + r);
                same(a.winsIfClosed[r] ? 1 : 0, b.winsIfClosed[r] ? 1 : 0, location + ":winsIfClosed:" + r);
            }
            int target = -1;
            List<Action> placements = new ArrayList<>();
            for (Action action : actions) {
                if (action.type == Action.Type.DISRUPT) target = action.disruptRegion(s);
                else if (action.type == Action.Type.PLACE) placements.add(action);
                else if (action.type != Action.Type.WAIT) throw new IllegalStateException("Unexpected neural action");
            }
            int paint = GameState.PAINT_PER_TURN;
            for (int k = 0; paint > 0; k++) {
                if (k > 0) { a.prepare(s, me, false); b.prepare(s, me, false); incrementalPasses++; }
                same(a.inactivePairs(), b.inactivePairs(), location + ":inactivePairs:" + k);
                nA = a.buildCandidates(s, me, paint, target, k, idsA, buildA);
                nB = b.buildCandidates(s, me, paint, target, k, idsB, buildB);
                rows(nA, nB, buildA, buildB, location + ":build:" + k); buildRows += nA;
                if (k >= placements.size()) break;
                Action chosen = placements.get(k); int cell = s.cell(chosen.x, chosen.y);
                paint -= s.placeCost(cell); s.owner[cell] = me; a.markPlaced(cell); b.markPlaced(cell);
                Rules.updateConnections(s, paths);
            }
        }
        void rows(int nA, int nB, float[][] x, float[][] y, String location) {
            same(nA, nB, location + ":count");
            for (int i = 0; i < Math.min(nA, nB); i++) {
                same(idsA[i], idsB[i], location + ":id:" + i);
                for (int j = 0; j < x[i].length; j++) {
                    featureValues++;
                    if (Float.floatToRawIntBits(x[i][j]) != Float.floatToRawIntBits(y[i][j]))
                        same(Float.floatToRawIntBits(x[i][j]), Float.floatToRawIntBits(y[i][j]), location + ":feature:" + i + ":" + j);
                }
            }
        }
    }
    static void same(int a, int b, String location) {
        if (a == b) return; featureMismatches++;
        if (mismatchExamples.size() < 12) mismatchExamples.add(Map.of("location", location, "v3", a, "v5", b));
    }
    record Measurement(List<Action> actions, long nanos, long allocatedBytes, long prepareNanos, long sabotageNanos, long buildNanos) {
        Map<String,Object> toMap() { return Map.of("actions", Action.format(actions), "playNanos", nanos, "allocatedBytes", allocatedBytes,
                "prepareNanos", prepareNanos, "sabotageNanos", sabotageNanos, "buildNanos", buildNanos); }
    }
    static Measurement measure(Bot bot, NeuralV3Features a, NeuralV5Features b, GameState actual, int me) {
        GameState input = actual.copy();
        long prep = a == null ? b.prepareNanos : a.prepareNanos, sab = a == null ? b.sabotageNanos : a.sabotageNanos, build = a == null ? b.buildNanos : a.buildNanos;
        long bytes = allocated(), start = System.nanoTime(); List<Action> actions = bot.play(input, me, BUDGET_MS);
        long elapsed = System.nanoTime() - start, after = allocated();
        return new Measurement(actions, elapsed, bytes < 0 ? -1 : after - bytes,
                (a == null ? b.prepareNanos : a.prepareNanos) - prep, (a == null ? b.sabotageNanos : a.sabotageNanos) - sab,
                (a == null ? b.buildNanos : a.buildNanos) - build);
    }
    static Map<String,Object> warmup(Bot bot, GameState s, int me) {
        bot.init(s, me); long start = System.nanoTime(); int calls = 0;
        do { bot.play(s.copy(), me, 36); calls++; } while (System.nanoTime() - start < WARMUP_MS * 1_000_000L);
        return Map.of("bot", bot.name(), "requestedMs", WARMUP_MS, "actualWallMs", (System.nanoTime() - start) / 1e6, "calls", calls,
                "width", s.width, "height", s.height, "seat", me, "method", "disposable bot on the fixed largest saved map; 36-ms slices, actual board unchanged");
    }
    static Field field(Class<?> type, String name) {
        try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
        catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); }
    }
    static com.sun.management.ThreadMXBean allocationBean() {
        var base = java.lang.management.ManagementFactory.getThreadMXBean();
        if (!(base instanceof com.sun.management.ThreadMXBean bean) || !bean.isThreadAllocatedMemorySupported()) return null;
        if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
        return bean;
    }
    static long allocated() { return ALLOCATION == null ? -1 : ALLOCATION.getThreadAllocatedBytes(Thread.currentThread().getId()); }
    static Map<String,Object> pin(Path path) throws Exception { return Map.of("path", path.toString().replace('\\', '/'), "sha256", hash(path)); }
    static void checkPin(JsonObject pin) throws Exception {
        if (!hash(Path.of(pin.get("path").getAsString())).equals(pin.get("sha256").getAsString())) throw new IllegalStateException("Changed pinned input: " + pin.get("path"));
    }
    static String hash(Path path) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
    static void write(Path path, Object value) throws Exception { Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(value) + "\n", StandardOpenOption.CREATE_NEW); }
}
