import btk.bot.BotFactory;
import btk.bot.Params;
import btk.bot.neural.NeuralV3Bot;
import btk.bot.neural.NeuralV3Features;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.tools.CgReplay;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
 * Diagnostic-only source instrumentation of the frozen V3 feature implementation.
 * --prepare directory snapshots existing compiled classes/resources and generates an instrumented
 * source copy. Compile that copy into instrumented-classes, then launch this class with that
 * directory FIRST, the snapshot frozen-classes second, and gson last. The source and method body
 * order are unchanged except counters, timing/allocation brackets and wrapped core call sites.
 * Exactly one complete pass on the previously selected 40 x 20 real states, zero new games.
 * Nested timings are inclusive and must not be summed across levels. Instrumentation changes JIT
 * and adds calls, so timings identify costs on this host and are not CG timing-readiness evidence.
 */
public class NeuralFeatureCostProfile {
    static final Path SELF = Path.of("tools/diagnostics/NeuralFeatureCostProfile.java");
    static final Path ORIGINAL = Path.of("src/main/java/btk/bot/neural/NeuralV3Features.java");
    static final Path PAINT = Path.of("docs/evidence/neural-v3-arena-paint-20261004.json");
    static final Path PRIOR = Path.of("build/analysis/neural-v3-arena-20261004/budget-probe.json");
    static final String[] NAMES = {"prepare", "prepare.cheapestCostPath", "prepare.completionGain",
            "prepare.completionGain.copyFrom", "prepare.completionGain.updateConnections",
            "prepare.completionGain.income", "prepare.activePairRoutes", "prepare.activePairRoutes.cheapestCostPath",
            "prepare.labelComponents", "sabotage", "sabotage.copyFrom", "sabotage.updateConnections",
            "sabotage.income", "build", "build.copyFrom", "build.updateConnections", "build.income",
            "prepare.income", "other.cheapestCostPath"};
    static final long WARMUP_MS = 500, AMPLE_MS = 60_000;
    static final int TURNS = 20;

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("--prepare")) { prepare(Path.of(args[1])); return; }
        if (args.length != 1) throw new IllegalArgumentException("--prepare directory OR directory");
        run(Path.of(args[0]));
    }

    static void prepare(Path dir) throws Exception {
        if (Files.exists(dir)) throw new IllegalStateException("Preserve all existing attempts: " + dir);
        Files.createDirectories(dir);
        Map<String,Object> plan = new LinkedHashMap<>();
        plan.put("schemaVersion", 1);
        plan.put("kind", "fixed saved-state V3 feature cost profile; no games or strategy edits");
        plan.put("selection", "all 40 pinned arena games, turns 0..19, one pass, one V3 call per state");
        plan.put("budgetMs", AMPLE_MS); plan.put("warmupMs", WARMUP_MS);
        plan.put("memory", "actual public board prefix and previous accepted recorded disruption restored each turn");
        plan.put("rng", "same explicit per-state seed as NeuralSavedStateBudgetProbe; not arena RNG reconstruction");
        plan.put("profileNames", NAMES);
        plan.put("timing", "inclusive nested nanoTime and per-thread allocated-byte deltas; overhead included in parents");
        plan.put("limits", List.of("Diagnostic instrumentation changes code/JIT and adds method/native calls.",
                "One local sample per state, no repeated timing trials and no CG CPU/runtime reconstruction.",
                "Allocation counters show allocated bytes, not retained memory or pause causality.",
                "All states are reused development evidence; no strength or outcome claims.",
                "Actions compared with prior ample-budget probe under the identical seed/memory convention."));
        List<Map<String,Object>> inputs = new ArrayList<>();
        inputs.add(pin(SELF)); inputs.add(pin(ORIGINAL)); inputs.add(pin(PAINT)); inputs.add(pin(PRIOR));
        JsonObject evidence = JsonParser.parseString(Files.readString(PAINT)).getAsJsonObject();
        for (String key : List.of("inputs", "rawReplayPins", "sourcePins"))
            for (var item : evidence.getAsJsonArray(key)) checkPin(item.getAsJsonObject());
        plan.put("inputs", inputs);
        List<Map<String,Object>> frozen = new ArrayList<>();
        copyTree(Path.of("build/classes/java/main"), dir.resolve("frozen-classes"), frozen);
        copyTree(Path.of("build/resources/main"), dir.resolve("frozen-classes"), frozen);
        Path gsonSource = Path.of("build/analysis/neural-v3-arena-20261004/gson-2.11.0.jar");
        Path gson = dir.resolve("gson-2.11.0.jar"); Files.copy(gsonSource, gson); frozen.add(pin(gson));
        String s = Files.readString(ORIGINAL);
        // Wrap only the exact existing simulation sites, before adding helper definitions.
        s = s.replace("sim.copyFrom(s);", "profileCopy(s);")
                .replace("Rules.updateConnections(sim, paths);", "profileConnections();")
                .replace("Rules.incomePerTurn(sim, income2);", "profileIncome(sim, income2);")
                .replace("Rules.incomePerTurn(s, income);", "profileIncome(s, income);");
        s = wrap(s, "private boolean prepareUntil(", "0", 1);
        s = wrap(s, "private double completionGain(", "2", 2);
        s = wrap(s, "private boolean activePairRoutes(", "6", 3);
        s = wrap(s, "private void labelComponents(", "8", -1);
        s = wrap(s, "private int sabotageUntil(", "9", 4);
        s = wrap(s, "private int buildUntil(", "13", 5);
        s = wrap(s, "public int cheapestCostPath(", "profileContext == 3 ? 7 : profileContext == 1 ? 1 : 18", -1);
        String helpers = """

    // DIAGNOSTIC ONLY: generated counters; never ship this source.
    public final long[] profileNanos = new long[19], profileBytes = new long[19], profileCalls = new long[19];
    private int profileContext;
    private static final com.sun.management.ThreadMXBean PROFILE_BEAN = profileBean();
    private static com.sun.management.ThreadMXBean profileBean() {
        java.lang.management.ThreadMXBean base = java.lang.management.ManagementFactory.getThreadMXBean();
        if (!(base instanceof com.sun.management.ThreadMXBean bean) || !bean.isThreadAllocatedMemorySupported()) return null;
        if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
        return bean;
    }
    public static boolean profileAllocationSupported() { return PROFILE_BEAN != null; }
    private static long profileAllocated() { return PROFILE_BEAN == null ? -1 : PROFILE_BEAN.getThreadAllocatedBytes(Thread.currentThread().getId()); }
    private void profileEnd(int slot, long start, long bytes) {
        long end = System.nanoTime();
        long allocated = profileAllocated();
        profileNanos[slot] += end - start;
        profileCalls[slot]++;
        if (bytes >= 0 && allocated >= bytes) profileBytes[slot] += allocated - bytes;
    }
    private void profileCopy(GameState s) {
        int slot = profileContext == 2 ? 3 : profileContext == 4 ? 10 : 14;
        long allocated = profileAllocated(), start = System.nanoTime();
        try { sim.copyFrom(s); } finally { profileEnd(slot, start, allocated); }
    }
    private void profileConnections() {
        int slot = profileContext == 2 ? 4 : profileContext == 4 ? 11 : 15;
        long allocated = profileAllocated(), start = System.nanoTime();
        try { Rules.updateConnections(sim, paths); } finally { profileEnd(slot, start, allocated); }
    }
    private void profileIncome(GameState s, int[] out) {
        int slot = profileContext == 2 ? 5 : profileContext == 4 ? 12 : profileContext == 5 ? 16 : 17;
        long allocated = profileAllocated(), start = System.nanoTime();
        try { Rules.incomePerTurn(s, out); } finally { profileEnd(slot, start, allocated); }
    }
""";
        int classStart = s.indexOf("public final class NeuralV3Features {") + "public final class NeuralV3Features {".length();
        s = s.substring(0, classStart) + helpers + s.substring(classStart);
        Path generated = dir.resolve("src/btk/bot/neural/NeuralV3Features.java");
        Files.createDirectories(generated.getParent()); Files.writeString(generated, s, StandardOpenOption.CREATE_NEW);
        plan.put("generatedSource", pin(generated)); plan.put("frozenRuntimeFiles", frozen);
        plan.put("javaVersion", System.getProperty("java.version"));
        write(dir.resolve("plan.json"), plan);
        System.out.println("Prepared isolated source and " + frozen.size() + " runtime files. No bot calls.");
    }

    static String wrap(String s, String signature, String slot, int context) {
        int method = s.indexOf(signature);
        if (method < 0 || s.indexOf(signature, method + 1) >= 0) throw new IllegalArgumentException("Non-unique method: " + signature);
        int start = s.indexOf('{', method), end = start + 1, depth = 1;
        // Targeted frozen method bodies contain no braces inside string literals or comments.
        for (; end < s.length() && depth > 0; end++) {
            if (s.charAt(end) == '{') depth++;
            else if (s.charAt(end) == '}') depth--;
        }
        if (depth != 0) throw new IllegalStateException("Method brace mismatch");
        String before = "\n        int profileSlot = " + slot + ";\n        int oldProfileContext = profileContext;\n"
                + (context >= 0 ? "        profileContext = " + context + ";\n" : "")
                + "        long profileAllocationStart = profileAllocated(), profileStart = System.nanoTime();\n        try {\n";
        String after = "\n        } finally { profileEnd(profileSlot, profileStart, profileAllocationStart); profileContext = oldProfileContext; }\n    ";
        return s.substring(0, start + 1) + before + s.substring(start + 1, end - 1) + after + s.substring(end - 1);
    }

    static void run(Path dir) throws Exception {
        Path output = dir.resolve("results.json"), intent = dir.resolve("intent.json");
        if (Files.exists(output) || Files.exists(intent)) throw new IllegalStateException("Preserve initial profile attempt; no repeat");
        JsonObject plan = JsonParser.parseString(Files.readString(dir.resolve("plan.json"))).getAsJsonObject();
        for (var item : plan.getAsJsonArray("inputs")) checkPin(item.getAsJsonObject());
        checkPin(plan.getAsJsonObject("generatedSource"));
        for (var item : plan.getAsJsonArray("frozenRuntimeFiles")) checkPin(item.getAsJsonObject());
        JsonObject evidence = JsonParser.parseString(Files.readString(PAINT)).getAsJsonObject();
        Path studyPath = null;
        for (var item : evidence.getAsJsonArray("inputs")) {
            JsonObject pin = item.getAsJsonObject(); checkPin(pin);
            if (pin.get("path").getAsString().endsWith("/player-study.json")) studyPath = Path.of(pin.get("path").getAsString());
        }
        for (String key : List.of("rawReplayPins", "sourcePins"))
            for (var item : evidence.getAsJsonArray(key)) checkPin(item.getAsJsonObject());
        if (studyPath == null) throw new IllegalStateException("Missing study");
        var games = JsonParser.parseString(Files.readString(studyPath)).getAsJsonObject().getAsJsonArray("games");
        var priorRows = JsonParser.parseString(Files.readString(PRIOR)).getAsJsonObject().getAsJsonArray("rows");
        if (games.size() != 40 || priorRows.size() != 800) throw new IllegalStateException("Unexpected fixed panel size");
        Field featuresField = NeuralV3Bot.class.getDeclaredField("features"), randomField = NeuralV3Bot.class.getDeclaredField("random");
        featuresField.setAccessible(true); randomField.setAccessible(true);
        Field nanosField = NeuralV3Features.class.getField("profileNanos"), bytesField = NeuralV3Features.class.getField("profileBytes"), callsField = NeuralV3Features.class.getField("profileCalls");
        boolean allocationSupported = (Boolean) NeuralV3Features.class.getMethod("profileAllocationSupported").invoke(null);
        Params params = BotFactory.parameters("neural-v3");
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", 1); result.put("plan", pin(dir.resolve("plan.json")));
        result.put("configuration", BotFactory.configuration("neural-v3")); result.put("profileNames", NAMES);
        result.put("javaClassPath", System.getProperty("java.class.path")); result.put("javaVersion", System.getProperty("java.version"));
        result.put("allocationSupported", allocationSupported);
        List<Map<String,Object>> compiled = new ArrayList<>();
        try (var paths = Files.walk(dir.resolve("instrumented-classes"))) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) compiled.add(pin(path));
        }
        result.put("instrumentedClassPins", compiled); write(intent, result);

        JsonObject warmGame = games.get(0).getAsJsonObject();
        for (var item : games) if (item.getAsJsonObject().get("height").getAsInt() > warmGame.get("height").getAsInt()) warmGame = item.getAsJsonObject();
        CgReplay warmReplay = CgReplay.load(Path.of(warmGame.get("file").getAsString()));
        GameState warmState = warmReplay.toState(); Rules.updateConnections(warmState, new Pathing(warmState.size));
        int warmSeat = warmGame.get("me").getAsInt();
        NeuralV3Bot disposable = new NeuralV3Bot(params); disposable.init(warmState, warmSeat);
        long start = System.nanoTime(); int warmCalls = 0;
        do { disposable.play(warmState.copy(), warmSeat, 36); warmCalls++; }
        while (System.nanoTime() - start < WARMUP_MS * 1_000_000L);
        result.put("warmup", Map.of("gameId", warmReplay.gameId, "calls", warmCalls, "requestedMs", WARMUP_MS,
                "actualWallMs", (System.nanoTime() - start) / 1e6, "method", "one disposable V3 on the same largest saved map; 36-ms slices, unchanged board"));
        List<Map<String,Object>> rows = new ArrayList<>(); int actionMismatches = 0;
        com.sun.management.ThreadMXBean threadBean = allocationSupported ? (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean() : null;
        long threadId = Thread.currentThread().getId();
        for (int gi = 0; gi < games.size(); gi++) {
            JsonObject game = games.get(gi).getAsJsonObject(); int me = game.get("me").getAsInt();
            CgReplay replay = CgReplay.load(Path.of(game.get("file").getAsString()));
            if (replay.gameId != game.get("gameId").getAsLong() || replay.turns() < TURNS) throw new IllegalStateException("Replay identity/length mismatch");
            GameState actual = replay.toState(); Pathing paths = new Pathing(actual.size); Rules.updateConnections(actual, paths);
            NeuralV3Bot bot = new NeuralV3Bot(params); bot.init(actual, me);
            NeuralV3Features features = (NeuralV3Features) featuresField.get(bot);
            long[] nanos = (long[]) nanosField.get(features), bytes = (long[]) bytesField.get(features), calls = (long[]) callsField.get(features);
            for (int turn = 0; turn < TURNS; turn++) {
                if (actual.turn != turn || replay.failure(turn, 0) != null || replay.failure(turn, 1) != null) throw new IllegalStateException("Bad recorded turn");
                long noiseSeed = params.SEED * 1_000_003L + me * 7919L + replay.gameId * 31L + turn * 104729L;
                randomField.set(bot, new Random(noiseSeed)); Arrays.fill(nanos, 0); Arrays.fill(bytes, 0); Arrays.fill(calls, 0);
                GameState input = actual.copy();
                long beforeBytes = threadBean == null ? -1 : threadBean.getThreadAllocatedBytes(threadId);
                long wallStart = System.nanoTime(); List<Action> actions = bot.play(input, me, AMPLE_MS);
                long wallNs = System.nanoTime() - wallStart;
                long playBytes = threadBean == null ? -1 : threadBean.getThreadAllocatedBytes(threadId) - beforeBytes;
                if (bot.lastAbortedPhase != 0) throw new IllegalStateException("Ample profile unexpectedly aborted");
                JsonObject prior = priorRows.get(rows.size()).getAsJsonObject();
                if (prior.get("gameId").getAsLong() != replay.gameId || prior.get("turn").getAsInt() != turn) throw new IllegalStateException("Prior row ordering drift");
                String actionLine = Action.format(actions);
                boolean actionMatch = actionLine.equals(prior.getAsJsonObject("ample").get("actions").getAsString());
                if (!actionMatch) actionMismatches++;
                List<Map<String,Object>> metrics = new ArrayList<>();
                for (int i = 0; i < NAMES.length; i++) metrics.add(Map.of("name", NAMES[i], "calls", calls[i], "nanos", nanos[i], "allocatedBytes", allocationSupported ? bytes[i] : -1));
                GameState scratch = actual.copy(); List<Action> wait = List.of(Action.waitAction());
                var accepted = Rules.resolve(scratch, me == 0 ? actions : wait, me == 1 ? actions : wait, new Pathing(scratch.size)).moves[me];
                if (accepted.error != null || !accepted.skipped.isEmpty()) throw new IllegalStateException("Invalid profile action");
                Rules.TurnResult recorded = Rules.resolve(actual, Action.parseLine(replay.turnStdout.get(turn)[0]), Action.parseLine(replay.turnStdout.get(turn)[1]), paths);
                JsonObject expected = game.getAsJsonArray("trace").get(turn).getAsJsonObject();
                if (recorded.moves[me].cost != expected.getAsJsonArray("paint").get(me).getAsInt()
                        || recorded.moves[me].count != expected.getAsJsonArray("cells").get(me).getAsInt()
                        || actual.score[0] != expected.getAsJsonArray("score").get(0).getAsInt()
                        || actual.score[1] != expected.getAsJsonArray("score").get(1).getAsInt()) throw new IllegalStateException("Recorded prefix drift");
                int target = recorded.moves[me].disrupts.isEmpty() ? -1 : recorded.moves[me].disrupts.get(0); features.recordTarget(target);
                Map<String,Object> row = new LinkedHashMap<>();
                row.put("gameId", replay.gameId); row.put("turn", turn); row.put("me", me); row.put("height", actual.height); row.put("width", actual.width);
                row.put("noiseSeed", noiseSeed); row.put("actions", actionLine); row.put("priorAmpleActionMatch", actionMatch);
                row.put("acceptedPaint", accepted.cost); row.put("recordedPaint", recorded.moves[me].cost);
                row.put("playNanos", wallNs); row.put("playAllocatedBytes", playBytes); row.put("metrics", metrics); rows.add(row);
            }
            System.out.println("Feature profile " + (gi + 1) + "/40; states=" + rows.size());
        }
        if (rows.size() != 800) throw new IllegalStateException("Incomplete pass");
        result.put("states", rows.size()); result.put("botCalls", rows.size()); result.put("actionMismatchesAgainstPriorAmple", actionMismatches);
        result.put("rows", rows); result.put("complete", true); write(output, result);
        System.out.println("Completed 800 fixed-state calls; prior ample action mismatches=" + actionMismatches);
    }

    static void copyTree(Path source, Path destination, List<Map<String,Object>> pins) throws Exception {
        try (var paths = Files.walk(source)) {
            for (Path input : paths.filter(Files::isRegularFile).sorted().toList()) {
                Path output = destination.resolve(source.relativize(input)); Files.createDirectories(output.getParent());
                Files.copy(input, output, StandardCopyOption.COPY_ATTRIBUTES); pins.add(pin(output));
            }
        }
    }
    static Map<String,Object> pin(Path path) throws Exception { return Map.of("path", path.toString().replace('\\', '/'), "sha256", hash(path)); }
    static void checkPin(JsonObject pin) throws Exception {
        if (!hash(Path.of(pin.get("path").getAsString())).equals(pin.get("sha256").getAsString())) throw new IllegalStateException("Changed pinned input: " + pin.get("path"));
    }
    static String hash(Path path) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
    static void write(Path path, Object value) throws Exception { Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(value) + "\n", StandardOpenOption.CREATE_NEW); }
}
