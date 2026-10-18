package btk.runner;

import btk.bot.Bot;
import btk.bot.BotFactory;
import btk.bot.current.RolloutBot;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Protocol;
import btk.referee.Referee;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/** Local allowance experiment only. Separately compiled; never part of a submission. */
public final class LocalBudgetBenchmark {
    public static final String SPEC = "current?NOISE=0.1,USE_BATCHED_REFRESH=true";
    public static final long FIRST_MS = 486, A_LATER_MS = 100, B_LATER_MS = 36;
    public static final int THREADS = 2;
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    /** Only primitive accumulation after native play; no per-node or per-turn trace allocation. */
    public static final class CallTotals {
        public long calls, totalNs, maxNs, above36Ms, above50Ms, above100Ms, aboveAllowance;
        public long rolloutObservedCalls, routeQueries, completedComparisons, simulatedTurns;
        public long ordinarySearchComplete, networkPhaseComplete, selectedOpening, selectedNetwork;
        public long networkSearchStarted, networkEligible, networkEvaluated, networkRoots, networkComparisons;

        void observe(long ns, long allowance, RolloutBot rollout) {
            calls++; totalNs += ns; maxNs = Math.max(maxNs, ns);
            if (ns > 36_000_000L) above36Ms++;
            if (ns > 50_000_000L) above50Ms++;
            if (ns > 100_000_000L) above100Ms++;
            if (ns > allowance * 1_000_000L) aboveAllowance++;
            if (rollout == null) return;
            rolloutObservedCalls++;
            routeQueries += rollout.routeQueries;
            completedComparisons += rollout.completedComparisons;
            simulatedTurns += rollout.simulatedTurns;
            if (rollout.ordinarySearchComplete) ordinarySearchComplete++;
            if (rollout.networkPhaseComplete) networkPhaseComplete++;
            if (rollout.selectedOpening) selectedOpening++;
            if (rollout.selectedNetwork) selectedNetwork++;
            if (rollout.networkSearchStarted) networkSearchStarted++;
            networkEligible += rollout.networkEligible;
            networkEvaluated += rollout.networkEvaluated;
            networkRoots += rollout.networkRoots;
            networkComparisons += rollout.networkComparisons;
        }

        void add(CallTotals other) {
            calls += other.calls; totalNs += other.totalNs; maxNs = Math.max(maxNs, other.maxNs);
            above36Ms += other.above36Ms; above50Ms += other.above50Ms;
            above100Ms += other.above100Ms; aboveAllowance += other.aboveAllowance;
            rolloutObservedCalls += other.rolloutObservedCalls;
            routeQueries += other.routeQueries; completedComparisons += other.completedComparisons;
            simulatedTurns += other.simulatedTurns;
            ordinarySearchComplete += other.ordinarySearchComplete; networkPhaseComplete += other.networkPhaseComplete;
            selectedOpening += other.selectedOpening; selectedNetwork += other.selectedNetwork;
            networkSearchStarted += other.networkSearchStarted; networkEligible += other.networkEligible;
            networkEvaluated += other.networkEvaluated; networkRoots += other.networkRoots;
            networkComparisons += other.networkComparisons;
        }

        JsonObject json() {
            JsonObject result = JSON.toJsonTree(this).getAsJsonObject();
            result.addProperty("meanNativePlayMs", calls == 0 ? 0d : totalNs / (1_000_000d * calls));
            result.addProperty("maxNativePlayMs", maxNs / 1_000_000d);
            result.addProperty("meanRouteQueries", calls == 0 ? 0d : (double) routeQueries / calls);
            result.addProperty("meanCompletedComparisons", calls == 0 ? 0d : (double) completedComparisons / calls);
            result.addProperty("meanSimulatedTurns", calls == 0 ? 0d : (double) simulatedTurns / calls);
            return result;
        }
    }

    public static final class Observation {
        public final CallTotals first = new CallTotals(), later = new CallTotals();
        void add(Observation other) { first.add(other.first); later.add(other.later); }
        JsonObject json() {
            JsonObject result = new JsonObject(); result.add("first", first.json()); result.add("later", later.json());
            return result;
        }
    }

    static final class ObservedBot implements Bot {
        final Bot delegate;
        final RolloutBot rollout;
        final Observation observation = new Observation();
        ObservedBot(Bot delegate, boolean requireNativeRollout) {
            this.delegate = delegate;
            this.rollout = delegate instanceof RolloutBot nativeRollout ? nativeRollout : null;
            if (requireNativeRollout && rollout == null) throw new IllegalArgumentException("Exact native RolloutBot required");
        }
        public String name() { return delegate.name(); }
        public void init(GameState state, int me) { delegate.init(state, me); }
        public void warmup(GameState state, int me, long ms) { delegate.warmup(state, me, ms); }
        public List<Action> play(GameState state, int me, long allowance) {
            boolean first = state.turn == 0;
            long start = System.nanoTime();
            try {
                return delegate.play(state, me, allowance);
            } finally {
                long elapsed = System.nanoTime() - start;
                (first ? observation.first : observation.later).observe(elapsed, allowance, rollout);
            }
        }
    }

    public record GameObservations(Observation a, Observation b) {}
    public record PairObservations(int index, long seed, GameObservations aAsP0, GameObservations aAsP1) {}
    private record FinishedPair(BenchmarkRunner.PairResult result, PairObservations observations) {}

    static final class RunData {
        final BenchmarkRunner.Stats stats = new BenchmarkRunner.Stats(SPEC, SPEC);
        final int plannedGames;
        final Observation a = new Observation(), b = new Observation();
        final List<PairObservations> pairObservations = new ArrayList<>();
        boolean complete;
        String failure;
        RunData(int games, long seed, int threads, Map<String, String> exclusions) {
            validate(games, seed, threads);
            plannedGames = games;
            if (!stats.paramsA.equals(stats.paramsB)) throw new IllegalStateException("Identical policy parameters required");
            if (BotFactory.parameters(SPEC).TURN_BUDGET_MS != 36) throw new IllegalStateException("Production budget must remain36");
            stats.baseSeed = seed; stats.threads = threads;
            stats.firstSearchBudgetMs = stats.firstSearchBudgetMsA = stats.firstSearchBudgetMsB = FIRST_MS;
            stats.turnSearchBudgetMs = B_LATER_MS;
            stats.turnSearchBudgetMsA = A_LATER_MS; stats.turnSearchBudgetMsB = B_LATER_MS;
            stats.poolId = "v09-budget100-local-v1"; stats.poolPurpose = "development";
            stats.excludedReports = new LinkedHashMap<>(exclusions);
        }
        void add(FinishedPair pair) {
            stats.add(pair.result); pairObservations.add(pair.observations);
            a.add(pair.observations.aAsP0.a); a.add(pair.observations.aAsP1.a);
            b.add(pair.observations.aAsP0.b); b.add(pair.observations.aAsP1.b);
        }
    }

    private static void validate(int games, long seed, int threads) {
        if (games < 2 || games % 2 != 0) throw new IllegalArgumentException("Positive even game count required");
        if (threads != THREADS) throw new IllegalArgumentException("Exactly two workers required");
        long last = Math.addExact(seed, games / 2L - 1);
        if (seed <= 0 && last >= 0) throw new IllegalArgumentException("Generated seed interval cannot contain zero");
    }

    static void apply(Referee referee, boolean aAsP0) {
        TimingProfile.CG.apply(referee);
        if (referee.firstTurnBudgetMs != FIRST_MS || referee.turnBudgetMs != B_LATER_MS)
            throw new IllegalStateException("Original nominal486/36 profile changed");
        referee.playerTurnBudgetMs[0] = aAsP0 ? A_LATER_MS : B_LATER_MS;
        referee.playerTurnBudgetMs[1] = aAsP0 ? B_LATER_MS : A_LATER_MS;
    }

    /** Same generated-map preflight, paired games and worker count as BenchmarkRunner. */
    static void execute(RunData data, Maps maps, Set<String> exclusions,
                        Supplier<Bot> aFactory, Supplier<Bot> bFactory, boolean requireNative) throws Exception {
        if (maps.fromReplays()) throw new IllegalArgumentException("This fixed experiment uses generated maps only");
        long started = System.nanoTime();
        List<GameState> initialMaps = new ArrayList<>();
        List<String> hashes = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        try {
            for (int i = 0; i < data.plannedGames / 2; i++) {
                GameState initial = maps.get(i, data.stats.baseSeed + i);
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(Protocol.initBlock(initial, 0).getBytes(StandardCharsets.UTF_8)));
                if (exclusions.contains(hash) || !seen.add(hash))
                    throw new IllegalArgumentException("Known/repeated map before bot creation: " + hash);
                initialMaps.add(initial); hashes.add(hash);
            }
            var pool = Executors.newFixedThreadPool(data.stats.threads);
            List<Future<FinishedPair>> futures = new ArrayList<>();
            try {
                for (int i = 0; i < initialMaps.size(); i++) {
                    final int index = i;
                    futures.add(pool.submit(() -> {
                        long seed = data.stats.baseSeed + index;
                        GameState initial = initialMaps.get(index);
                        Referee referee = new Referee();
                        ObservedBot firstA = new ObservedBot(aFactory.get(), requireNative);
                        ObservedBot firstB = new ObservedBot(bFactory.get(), requireNative);
                        apply(referee, true);
                        Referee.Result first = referee.play(initial, firstA, firstB, seed, false);
                        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("benchmark cancelled");
                        ObservedBot secondA = new ObservedBot(aFactory.get(), requireNative);
                        ObservedBot secondB = new ObservedBot(bFactory.get(), requireNative);
                        apply(referee, false);
                        Referee.Result second = referee.play(initial, secondB, secondA, seed, false);
                        var result = new BenchmarkRunner.PairResult(index, seed, maps.name(index, seed), hashes.get(index),
                                initial.width, initial.height, initial.townCount, initial.pairCount, first, second);
                        return new FinishedPair(result, new PairObservations(index, seed,
                                new GameObservations(firstA.observation, firstB.observation),
                                new GameObservations(secondA.observation, secondB.observation)));
                    }));
                }
                for (Future<FinishedPair> future : futures) {
                    data.add(future.get());
                    if (data.stats.games % 50 == 0 || data.stats.games == data.plannedGames)
                        System.out.printf("Local diagnostic %d/%d games; A %.3f%%%n", data.stats.games,
                                data.plannedGames, 100 * data.stats.rateA());
                }
            } finally {
                for (Future<FinishedPair> future : futures) if (!future.isDone()) future.cancel(true);
                pool.shutdownNow();
            }
            data.complete = data.stats.games == data.plannedGames;
            if (data.stats.errorsA + data.stats.errorsB > 0) throw new IllegalStateException("Bot errors retained in report");
        } catch (Exception error) {
            data.failure = error.getClass().getSimpleName() + ": " + error.getMessage();
            throw error;
        } finally {
            data.stats.elapsedMs = (System.nanoTime() - started) / 1_000_000L;
        }
    }

    static JsonObject document(RunData data) {
        // Never call Stats.write/report: their historical gate helpers do not apply to100ms.
        JsonObject value = JSON.toJsonTree(data.stats).getAsJsonObject();
        value.addProperty("schemaVersion", 1);
        value.addProperty("schema", "local-budget-benchmark-v1");
        value.addProperty("experiment", "v09-budget100-local-v1");
        value.addProperty("timingProfile", "LOCAL_DIAGNOSTIC");
        value.addProperty("referenceTimingProfile", "CG");
        value.addProperty("experimentalTimingOnly", true);
        value.addProperty("localOnly", true);
        value.addProperty("promotionEligible", false);
        value.addProperty("promotion", false);
        value.addProperty("promotionGatePassed", false);
        value.addProperty("promotionPolicy", "NOT_APPLICABLE_LOCAL_TIMING_DIAGNOSTIC");
        value.addProperty("practicalDirectStage", "NOT_APPLICABLE");
        value.addProperty("processReadinessAssessed", false);
        value.addProperty("plannedGames", data.plannedGames);
        value.addProperty("complete", data.complete);
        value.addProperty("status", data.failure == null && data.complete ? "COMPLETE_LOCAL_DIAGNOSTIC" : "INCOMPLETE_OR_ERRORS");
        if (data.failure != null) value.addProperty("failure", data.failure);
        value.addProperty("searchBudgetSemantics", "Identical policy specs/params retain production36. Referee-only later allowances100/36 follow A/B across seats; first486 is unchanged. Unsuffixed486/36 fields are reference-profile baselines, not the effective A allowance.");
        value.addProperty("nativeTimingSemantics", "Referee totalMs/maxMs/slowMoves floor wall milliseconds including state.copy and observer overhead, including first turns; slow means strictly>50ms and is not an external timeout. Observation nanoseconds enclose native Bot.play only, excluding state.copy and counter accumulation; no protocol input/output or JIT warmup. Two nanoTime calls and primitive after-play sampling add overhead equally to both arms.");
        value.addProperty("workSemantics", "Existing primitive rollout counters accumulated afterplay, first/later separately. Means use each arm's own decision count and trajectories; complete flags cover retained comparisons, not proof that every route proposal was enumerated. Opening returns and fixed depth8/width6/replies2/route/network caps stay unchanged.");
        value.addProperty("externalCgDeadlineMsUnchanged", 50);
        value.addProperty("runtimeWarmupPerformed", false);
        value.addProperty("confidenceRole", "Descriptive paired-map uncertainty only; no promotion or CG deadline claim.");
        value.add("confidence95", JSON.toJsonTree(data.stats.confidence95()));
        JsonObject observations = new JsonObject(); observations.add("a", data.a.json()); observations.add("b", data.b.json());
        value.add("observations", observations);
        value.add("pairObservations", JSON.toJsonTree(data.pairObservations));
        return value;
    }

    static void write(Path out, RunData data) throws Exception {
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, JSON.toJson(document(data)) + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--help")) {
            System.out.println("LocalBudgetBenchmark <games> <baseSeed> <excludeReportsCSV> <newReport.json>\n"
                    + "Fixed two workers/generated paired maps; identical batched current specs; first486/486, later100/36; local diagnosis only.");
            return;
        }
        if (args.length != 4) throw new IllegalArgumentException("Expected games baseSeed exclusionsCSV newReport.json");
        int games = Integer.parseInt(args[0]); long seed = Long.parseLong(args[1]);
        Path out = Path.of(args[3]);
        if (Files.exists(out)) throw new IllegalArgumentException("Preserve existing report; no overwrite/retry");
        validate(games, seed, THREADS);
        BenchPool.Exclusions exclusions = BenchPool.exclusions(args[2], "confirmation");
        RunData data = new RunData(games, seed, THREADS, exclusions.reportSha256());
        try {
            execute(data, new Maps(""), exclusions.hashes(), () -> BotFactory.create(SPEC),
                    () -> BotFactory.create(SPEC), true);
        } finally {
            write(out, data);
        }
        System.out.printf("Saved local diagnostic: %dW %dL %dD; no promotion/process gate%n",
                data.stats.winsA, data.stats.winsB, data.stats.draws);
    }
}
