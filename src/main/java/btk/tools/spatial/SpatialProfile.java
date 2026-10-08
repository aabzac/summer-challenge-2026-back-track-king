package btk.tools.spatial;

import btk.bot.BotFactory;
import btk.bot.Params;
import btk.bot.neural.SpatialBot;
import btk.bot.neural.SpatialNet;
import btk.bot.neural.SpatialObservation;
import btk.bot.neural.SpatialWeights;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Protocol;
import btk.core.Rules;
import btk.referee.MapGenerator;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fixed serial timing/legality diagnostic; it is neither a strength test nor a process check. */
public final class SpatialProfile {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String SPEC = "spatial-ppo-v1";
    private static final long FIRST_SEED = 9_700_100L;
    private static final int MAPS = 4, POSITIONS_PER_MAP = 16;
    private static final long WARMUP_MS = 100, AMPLE_MS = 60_000, NOMINAL_MS = 36;

    private SpatialProfile() {}

    public static void main(String[] args) throws Exception {
        if (args.length > 1) throw new IllegalArgumentException("Usage: SpatialProfile [fresh-output.json]");
        Path output = Path.of(args.length == 0 ? "build/analysis/spatial-ppo-v1/profile.json" : args[0]);
        if (Files.exists(output)) throw new IllegalArgumentException("Refusing to overwrite profile: " + output);
        Params params = BotFactory.parameters(SPEC);
        // Fail immediately if the generated model is missing, malformed or non-finite.
        SpatialNet profileNet = new SpatialNet(SpatialWeights.DATA);
        List<Map<String, Object>> maps = new ArrayList<>(), samples = new ArrayList<>();
        Timings ampleTime = new Timings(), nominalTime = new Timings(), prepareTime = new Timings(), forwardTime = new Timings();
        int positions = 0, comparisons = 0, matches = 0, uninterruptedMismatches = 0;
        int ampleInterrupted = 0, nominalInterrupted = 0, nominalOverBudget = 0;
        int errors = 0, skipped = 0, amplePaint = 0, nominalPaint = 0;
        int ampleZeroPaint = 0, nominalZeroPaint = 0;
        long allStarted = System.nanoTime();
        for (int map = 0; map < MAPS; map++) {
            long seed = FIRST_SEED + map;
            GameState state = MapGenerator.generate(seed);
            String mapHash = sha256(Protocol.initBlock(state, 0).getBytes(StandardCharsets.UTF_8));
            Pathing pathing = new Pathing(state.size);
            Rules.updateConnections(state, pathing);
            SpatialBot[] bots = {new SpatialBot(params), new SpatialBot(params)};
            for (int me = 0; me < 2; me++) {
                bots[me].init(state, me);
                bots[me].warmup(state, me, WARMUP_MS);
            }
            SpatialObservation observation = new SpatialObservation(state);
            observation.prepare(state, 0);
            profileNet.forward(observation); // Prime this separate instance outside the measurements.
            List<Integer> turns = new ArrayList<>();
            boolean invalidAmple = false;
            for (int position = 0; position < POSITIONS_PER_MAP && !Rules.gameOver(state, pathing); position++) {
                int turn = state.turn;
                turns.add(turn);
                positions++;
                String before = Protocol.turnBlock(state, 0);
                List<List<Action>> jointAmple = new ArrayList<>(2);
                for (int me = 0; me < 2; me++) {
                    SpatialBot bot = bots[me];
                    long started = System.nanoTime();
                    List<Action> ample = bot.play(state, me, AMPLE_MS);
                    double ampleMs = millis(started);
                    boolean ampleCut = bot.lastInterrupted;
                    int ampleDecisions = bot.lastCompletedDecisions;
                    started = System.nanoTime();
                    List<Action> nominal = bot.play(state, me, NOMINAL_MS);
                    double nominalMs = millis(started);
                    boolean nominalCut = bot.lastInterrupted;
                    int nominalDecisions = bot.lastCompletedDecisions;
                    if (!before.equals(Protocol.turnBlock(state, 0)))
                        throw new IllegalStateException("Policy mutated the pre-turn board: seed=" + seed + " turn=" + turn + " seat=" + me);
                    jointAmple.add(ample);
                    Rules.PlayerMoves ampleMoves = validate(state, me, ample, pathing);
                    Rules.PlayerMoves nominalMoves = validate(state, me, nominal, pathing);
                    errors += (ampleMoves.error == null ? 0 : 1) + (nominalMoves.error == null ? 0 : 1);
                    skipped += ampleMoves.skipped.size() + nominalMoves.skipped.size();
                    invalidAmple |= ampleMoves.error != null || !ampleMoves.skipped.isEmpty();
                    String ampleActions = Action.format(ample), nominalActions = Action.format(nominal);
                    boolean same = ampleActions.equals(nominalActions);
                    comparisons++;
                    if (same) matches++;
                    if (!same && !ampleCut && !nominalCut) uninterruptedMismatches++;
                    if (ampleCut) ampleInterrupted++;
                    if (nominalCut) nominalInterrupted++;
                    if (nominalMs > NOMINAL_MS) nominalOverBudget++;
                    amplePaint += ampleMoves.cost;
                    nominalPaint += nominalMoves.cost;
                    if (ampleMoves.cost == 0) ampleZeroPaint++;
                    if (nominalMoves.cost == 0) nominalZeroPaint++;
                    ampleTime.add(ampleMs);
                    nominalTime.add(nominalMs);

                    started = System.nanoTime();
                    observation.prepare(state, me);
                    double prepareMs = millis(started);
                    started = System.nanoTime();
                    profileNet.forward(observation);
                    double forwardMs = millis(started);
                    prepareTime.add(prepareMs);
                    forwardTime.add(forwardMs);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("seed", seed); row.put("mapHash", mapHash); row.put("turn", turn); row.put("me", me);
                    row.put("trackCells", trackCells(state)); row.put("activeConnections", activeConnections(state));
                    row.put("ampleMs", ampleMs); row.put("nominalMs", nominalMs);
                    row.put("prepareMs", prepareMs); row.put("forwardMs", forwardMs);
                    row.put("ampleActions", ampleActions); row.put("nominalActions", nominalActions); row.put("actionsMatch", same);
                    row.put("ampleInterrupted", ampleCut); row.put("nominalInterrupted", nominalCut);
                    row.put("ampleCompletedDecisions", ampleDecisions); row.put("nominalCompletedDecisions", nominalDecisions);
                    row.put("amplePaint", ampleMoves.cost); row.put("nominalPaint", nominalMoves.cost);
                    row.put("amplePlacements", ampleMoves.count); row.put("nominalPlacements", nominalMoves.count);
                    row.put("ampleError", ampleMoves.error); row.put("nominalError", nominalMoves.error);
                    row.put("ampleSkipped", ampleMoves.skipped); row.put("nominalSkipped", nominalMoves.skipped);
                    samples.add(row);
                }
                if (invalidAmple) break;
                // Both complete plans were chosen from the identical pre-turn state.
                Rules.resolve(state, jointAmple.get(0), jointAmple.get(1), pathing);
            }
            boolean terminal = Rules.gameOver(state, pathing);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("seed", seed); row.put("mapHash", mapHash);
            row.put("width", state.width); row.put("height", state.height); row.put("townCount", state.townCount);
            row.put("measuredTurns", turns); row.put("positions", turns.size()); row.put("endTurn", state.turn);
            row.put("terminal", terminal); row.put("stopReason", invalidAmple ? "invalid ample plan" : terminal ? "terminal" : "fixed position cap");
            maps.add(row);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("maps", maps.size()); summary.put("positions", positions); summary.put("seatSamples", comparisons);
        summary.put("actionMatches", matches); summary.put("actionMismatches", comparisons - matches);
        summary.put("uninterruptedActionMismatches", uninterruptedMismatches);
        summary.put("ampleInterrupted", ampleInterrupted); summary.put("nominalInterrupted", nominalInterrupted);
        summary.put("nominalCallsOver36Ms", nominalOverBudget);
        summary.put("errors", errors); summary.put("skippedActions", skipped);
        summary.put("amplePaintUsed", amplePaint); summary.put("nominalPaintUsed", nominalPaint);
        summary.put("ampleZeroPaintSamples", ampleZeroPaint); summary.put("nominalZeroPaintSamples", nominalZeroPaint);
        summary.put("amplePlayMs", ampleTime.summary()); summary.put("nominalPlayMs", nominalTime.summary());
        summary.put("prepareMs", prepareTime.summary()); summary.put("standaloneForwardMs", forwardTime.summary());
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schemaVersion", 1); report.put("spec", SPEC); report.put("params", BotFactory.configuration(SPEC));
        report.put("weightsSha256", sha256(Base64.getDecoder().decode(SpatialWeights.DATA)));
        report.put("firstSeed", FIRST_SEED); report.put("plannedMaps", MAPS); report.put("positionsPerMap", POSITIONS_PER_MAP);
        report.put("warmupMsPerSeatPerMap", WARMUP_MS); report.put("ampleBudgetMs", AMPLE_MS); report.put("nominalBudgetMs", NOMINAL_MS);
        report.put("method", "Fixed serial diagnostic. Each seat's bot warms once per map for 100 ms. "
                + "At each unchanged state, ample play precedes nominal play; both seats' ample plans then resolve simultaneously through core. "
                + "Preparation and one stage-0 network forward are timed separately after play, using a primed independent network. "
                + "The p95 is nearest-rank. Warmup, validation and serialization are outside play timing. "
                + "Sixteen positions may cover only an opening; actual turns and track density are recorded. "
                + "This is not a strategy strength verdict or an exact-artifact process deadline check.");
        report.put("javaVersion", System.getProperty("java.version")); report.put("os", System.getProperty("os.name"));
        report.put("durationMs", millis(allStarted)); report.put("summary", summary); report.put("maps", maps); report.put("samples", samples);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, JSON.toJson(report), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        System.out.println(JSON.toJson(summary));
        System.out.println("Profile saved: " + output.toAbsolutePath());
    }

    private static Rules.PlayerMoves validate(GameState state, int me, List<Action> plan, Pathing pathing) {
        Rules.TurnResult result = new Rules.TurnResult();
        Rules.expand(state, me, plan, pathing, result.moves[me], result);
        return result.moves[me];
    }

    private static int trackCells(GameState state) {
        int count = 0;
        for (int cell = 0; cell < state.size; cell++) if (state.hasTrack(cell)) count++;
        return count;
    }

    private static int activeConnections(GameState state) {
        int count = 0;
        for (int pair = 0; pair < state.pairCount; pair++) if (state.pairActive(pair)) count++;
        return count;
    }

    private static double millis(long started) { return (System.nanoTime() - started) / 1_000_000.0; }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static final class Timings {
        private final List<Double> values = new ArrayList<>();
        void add(double value) { values.add(value); }
        Map<String, Object> summary() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("samples", values.size());
            if (values.isEmpty()) return result;
            List<Double> sorted = new ArrayList<>(values);
            sorted.sort(Comparator.naturalOrder());
            double sum = 0;
            for (double value : sorted) sum += value;
            result.put("min", sorted.get(0)); result.put("mean", sum / sorted.size());
            result.put("p95", sorted.get((int) Math.ceil(.95 * sorted.size()) - 1));
            result.put("max", sorted.get(sorted.size() - 1));
            return result;
        }
    }
}
