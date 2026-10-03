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
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * PREPARED ONLY. Local saved-state diagnostic, not a match or strength benchmark.
 * Args: committed-paint-evidence.json new-output.json
 * Run alone, after timed training/strength work. No production source modifications.
 * One500ms disposable36ms-slice warmup on the largest saved map initializes this JVM.
 * Both probe bots observe the same recorded public board prefix. After every probe,
 * accepted recorded disruption replaces diagnostic target memory for the next turn.
 * A common deterministic per-state RNG seed is installed through reflection before
 * both calls, avoiding cross-turn RNG divergence after a truncated score pass. It
 * deliberately does not reconstruct the arena RNG or server/JIT/scheduling state.
 */
class NeuralSavedStateBudgetProbe {
    static final long FIRST_TIMED_MS = 486, TIMED_MS = 36, AMPLE_MS = 60_000, WARMUP_MS = 500;
    static final int TURNS = 20;
    static final Path SELF = Path.of("tools/diagnostics/NeuralSavedStateBudgetProbe.java");
    static final Field FEATURES, RANDOM;
    static {
        try {
            FEATURES = NeuralV3Bot.class.getDeclaredField("features"); FEATURES.setAccessible(true);
            RANDOM = NeuralV3Bot.class.getDeclaredField("random"); RANDOM.setAccessible(true);
        } catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("paint-evidence.json new-output.json");
        Path input = Path.of(args[0]), output = Path.of(args[1]);
        Path intent = output.resolveSibling(output.getFileName() + ".intent.json");
        if (Files.exists(output) || Files.exists(intent))
            throw new IllegalStateException("Preserve prior diagnostic attempt; no implicit repeat");
        JsonObject evidence = JsonParser.parseString(Files.readString(input)).getAsJsonObject();
        Path studyPath = null;
        for (var entry : evidence.getAsJsonArray("inputs")) {
            JsonObject pin = entry.getAsJsonObject(); checkPin(pin);
            if (pin.get("path").getAsString().endsWith("/player-study.json")) studyPath = Path.of(pin.get("path").getAsString());
        }
        for (var entry : evidence.getAsJsonArray("rawReplayPins")) checkPin(entry.getAsJsonObject());
        for (var entry : evidence.getAsJsonArray("sourcePins")) checkPin(entry.getAsJsonObject());
        if (studyPath == null) throw new IllegalArgumentException("Missing pinned study input");
        JsonObject study = JsonParser.parseString(Files.readString(studyPath)).getAsJsonObject();
        var games = study.getAsJsonArray("games");
        if (games.size() != 40) throw new IllegalArgumentException("Exactly the existing40 games required");
        Params params = BotFactory.parameters("neural-v3");
        List<Map<String,Object>> rows = new ArrayList<>();
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", 1);
        result.put("kind", "local saved-state budget diagnosis; zero new games");
        result.put("input", Map.of("path", input.toString(), "sha256", hash(input)));
        result.put("helper", Map.of("path", SELF.toString(), "sha256", hash(SELF)));
        result.put("configuration", BotFactory.configuration("neural-v3"));
        result.put("selection", "all40 pinned arena replays, every turn0..19; fixed before execution");
        result.put("timedFirstBudgetMs", FIRST_TIMED_MS); result.put("timedLaterBudgetMs", TIMED_MS);
        result.put("ampleBudgetMs", AMPLE_MS);
        result.put("javaVersion", System.getProperty("java.version"));
        result.put("javaClassPath", System.getProperty("java.class.path"));
        result.put("callOrder", "timed then ample on even(gameIndex+turn), reverse on odd; one pass only");
        result.put("memory", "public board prefixes and previous accepted recorded target identical; alternative actions never advance the replay");
        result.put("randomness", "both calls reseeded identically per state; not the unrecoverable arena RNG state");
        result.put("limits", List.of("Local wall-clock sensitivity only; does not reproduce CodinGame CPU, JIT or parsing overhead.",
                "Timed arm uses CG local profile486ms first turn and36ms later; parsing/runtime overhead is not reconstructed.",
                "lastAbortedPhase=0 does not prove completion: the6ms later-placement reserve can stop without setting this field.",
                "Accepted paint uses core Rules.resolve versus WAIT on a scratch copy; this is not a predicted game result.",
                "No alternate-action trajectories, new maps, outcome evaluation, repetitions or strategy edits."));
        Files.createDirectories(output.toAbsolutePath().getParent());
        write(intent, result);

        JsonObject warmGame = games.get(0).getAsJsonObject();
        for (var item : games) if (item.getAsJsonObject().get("height").getAsInt() > warmGame.get("height").getAsInt())
            warmGame = item.getAsJsonObject();
        CgReplay warmReplay = CgReplay.load(Path.of(warmGame.get("file").getAsString()));
        GameState warmState = warmReplay.toState();
        Rules.updateConnections(warmState, new Pathing(warmState.size));
        int warmSeat = warmGame.get("me").getAsInt();
        NeuralV3Bot disposable = new NeuralV3Bot(params); disposable.init(warmState, warmSeat);
        long warmStart = System.nanoTime(); int warmCalls = 0;
        do {
            disposable.play(warmState.copy(), warmSeat, TIMED_MS); warmCalls++;
        } while (System.nanoTime() - warmStart < WARMUP_MS * 1_000_000L);
        result.put("warmup", Map.of("gameId", warmReplay.gameId, "calls", warmCalls,
                "requestedMs", WARMUP_MS, "actualWallMs", (System.nanoTime() - warmStart) / 1e6,
                "method", "one disposable bot on the fixed largest saved map; actual board never advanced"));

        for (int gi = 0; gi < games.size(); gi++) {
            JsonObject game = games.get(gi).getAsJsonObject();
            int me = game.get("me").getAsInt();
            CgReplay replay = CgReplay.load(Path.of(game.get("file").getAsString()));
            if (replay.gameId != game.get("gameId").getAsLong() || replay.turns() < TURNS)
                throw new IllegalStateException("Replay identity/length mismatch");
            GameState actual = replay.toState(); Pathing paths = new Pathing(actual.size);
            Rules.updateConnections(actual, paths);
            NeuralV3Bot timed = new NeuralV3Bot(params), ample = new NeuralV3Bot(params);
            timed.init(actual, me); ample.init(actual, me);
            NeuralV3Features timedFeatures = (NeuralV3Features) FEATURES.get(timed);
            NeuralV3Features ampleFeatures = (NeuralV3Features) FEATURES.get(ample);
            for (int turn = 0; turn < TURNS; turn++) {
                if (actual.turn != turn || replay.failure(turn, 0) != null || replay.failure(turn, 1) != null)
                    throw new IllegalStateException("Bad recorded state or failure at " + replay.gameId + ":" + turn);
                long noiseSeed = params.SEED * 1_000_003L + me * 7919L + replay.gameId * 31L + turn * 104729L;
                RANDOM.set(timed, new Random(noiseSeed)); RANDOM.set(ample, new Random(noiseSeed));
                long timedBudget = turn == 0 ? FIRST_TIMED_MS : TIMED_MS;
                Map<String,Object> timedResult, ampleResult;
                if ((gi + turn) % 2 == 0) {
                    timedResult = probe(timed, timedFeatures, actual, me, timedBudget);
                    ampleResult = probe(ample, ampleFeatures, actual, me, AMPLE_MS);
                } else {
                    ampleResult = probe(ample, ampleFeatures, actual, me, AMPLE_MS);
                    timedResult = probe(timed, timedFeatures, actual, me, timedBudget);
                }
                List<Action> a0 = Action.parseLine(replay.turnStdout.get(turn)[0]);
                List<Action> a1 = Action.parseLine(replay.turnStdout.get(turn)[1]);
                Rules.TurnResult recorded = Rules.resolve(actual, a0, a1, paths);
                var expected = game.getAsJsonArray("trace").get(turn).getAsJsonObject();
                if (recorded.moves[me].cost != expected.getAsJsonArray("paint").get(me).getAsInt()
                        || recorded.moves[me].count != expected.getAsJsonArray("cells").get(me).getAsInt()
                        || actual.score[0] != expected.getAsJsonArray("score").get(0).getAsInt()
                        || actual.score[1] != expected.getAsJsonArray("score").get(1).getAsInt())
                    throw new IllegalStateException("Recorded prefix drift at " + replay.gameId + ":" + turn);
                int target = recorded.moves[me].disrupts.isEmpty() ? -1 : recorded.moves[me].disrupts.get(0);
                timedFeatures.recordTarget(target); ampleFeatures.recordTarget(target);
                Map<String,Object> row = new LinkedHashMap<>();
                row.put("gameId", replay.gameId); row.put("turn", turn); row.put("me", me);
                row.put("width", actual.width); row.put("height", actual.height); row.put("noiseSeed", noiseSeed);
                row.put("recordedPaint", recorded.moves[me].cost); row.put("recordedCells", recorded.moves[me].count);
                row.put("recordedActions", replay.turnStdout.get(turn)[me]);
                row.put("timed", timedResult); row.put("ample", ampleResult); rows.add(row);
            }
            System.out.println("Saved-state probes " + (gi + 1) + "/40; states=" + rows.size());
        }
        if (rows.size() != 800) throw new IllegalStateException("Incomplete fixed probe");
        result.put("states", rows.size()); result.put("botCalls", rows.size() * 2);
        result.put("rows", rows); result.put("complete", true); write(output, result);
    }

    static Map<String,Object> probe(NeuralV3Bot bot, NeuralV3Features features, GameState actual, int me, long budget) {
        GameState input = actual.copy();
        long prep = features.prepareNanos, sab = features.sabotageNanos, build = features.buildNanos;
        long start = System.nanoTime();
        List<Action> actions = bot.play(input, me, budget);
        double wallMs = (System.nanoTime() - start) / 1e6;
        GameState scratch = actual.copy();
        List<Action> wait = List.of(Action.waitAction());
        Rules.TurnResult accepted = Rules.resolve(scratch, me == 0 ? actions : wait, me == 1 ? actions : wait,
                new Pathing(scratch.size));
        var moves = accepted.moves[me];
        if (moves.error != null || !moves.skipped.isEmpty()) throw new IllegalStateException("Diagnostic action was not legal");
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("actions", Action.format(actions)); result.put("wallMs", wallMs);
        result.put("acceptedPaint", moves.cost); result.put("paintLeft", GameState.PAINT_PER_TURN - moves.cost);
        result.put("acceptedCells", moves.count); result.put("completedPlacements", bot.lastPlacements);
        result.put("lastAbortedPhase", bot.lastAbortedPhase); result.put("target", bot.lastTarget);
        result.put("lastBuildCandidates", bot.lastBuildCandidates); result.put("lastSabotageCandidates", bot.lastSabotageCandidates);
        result.put("prepareMs", (features.prepareNanos - prep) / 1e6);
        result.put("sabotageFeaturesMs", (features.sabotageNanos - sab) / 1e6);
        result.put("buildFeaturesMs", (features.buildNanos - build) / 1e6);
        return result;
    }
    static void checkPin(JsonObject pin) throws Exception {
        if (!hash(Path.of(pin.get("path").getAsString())).equals(pin.get("sha256").getAsString()))
            throw new IllegalStateException("Changed diagnostic input: " + pin.get("path"));
    }
    static String hash(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
    static void write(Path path, Object value) throws Exception {
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(value) + "\n", StandardOpenOption.CREATE_NEW);
    }
}
