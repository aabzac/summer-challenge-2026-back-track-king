package btk.runner;

import btk.bot.Bot;
import btk.bot.BotFactory;
import btk.bot.Params;
import btk.bot.MainBot;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Protocol;
import btk.referee.MapGenerator;
import btk.referee.Referee;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Deterministic wiring check with WAIT fakes only. Does not execute the real strategy. */
public final class LocalBudgetBenchmarkSelfCheck {
    private record Seen(String role, int seat, int turn, long allowance) {}
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static Supplier<Bot> fake(String role, ConcurrentLinkedQueue<Seen> seen, AtomicInteger created) {
        return () -> {
            created.incrementAndGet();
            return new Bot() {
                public String name() { return "fake-" + role; }
                public List<Action> play(GameState state, int me, long allowance) {
                    seen.add(new Seen(role, me, state.turn, allowance));
                    return List.of(Action.waitAction());
                }
            };
        };
    }
    private static void rejected(Runnable action, String label) {
        try { action.run(); throw new AssertionError("Expected rejection: " + label); }
        catch (IllegalArgumentException expected) { /* expected */ }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected NEW self-check output directory");
        Path out = Path.of(args[0]);
        require(!Files.exists(out), "Preserve existing self-check output");
        Files.createDirectories(out);
        var seen = new ConcurrentLinkedQueue<Seen>();
        var created = new AtomicInteger();
        var data = new LocalBudgetBenchmark.RunData(4, 81371, 2, Map.of("synthetic-source", "hash-only-fixture"));
        LocalBudgetBenchmark.execute(data, new Maps(""), Set.of(), fake("A", seen, created), fake("B", seen, created), false);
        require(data.complete && data.stats.games == 4 && created.get() == 8, "Fresh fake bots per paired game");
        for (String role : List.of("A", "B")) for (int seat = 0; seat < 2; seat++) {
            final int side = seat;
            var calls = seen.stream().filter(x -> x.role.equals(role) && x.seat == side).toList();
            require(calls.stream().filter(x -> x.turn == 0).count() == 2, "Both maps/first turns represented");
            require(calls.stream().filter(x -> x.turn == 0).allMatch(x -> x.allowance == 486), "First budget unchanged");
            require(calls.stream().anyMatch(x -> x.turn > 0), "Later calls exist");
            require(calls.stream().filter(x -> x.turn > 0).allMatch(x -> x.allowance == (role.equals("A") ? 100 : 36)),
                    "Per-arm budget followed the swapped player seat");
        }
        require(data.stats.paramsA.equals(data.stats.paramsB), "Policy parameters identical");
        require(data.a.first.calls == 4 && data.b.first.calls == 4, "Separate first-call totals");
        require(data.a.later.calls == data.stats.turns - 4 && data.b.later.calls == data.stats.turns - 4,
                "Later-call counts reconcile with raw turns");
        require(data.a.later.rolloutObservedCalls == 0 && data.a.later.routeQueries == 0, "Fakes have no invented rollout work");
        require(data.a.later.totalNs >= data.a.later.maxNs && data.b.later.totalNs >= 0, "Native timing totals valid");
        require(data.stats.excludedReports.equals(Map.of("synthetic-source", "hash-only-fixture")), "Exclusion provenance preserved");
        JsonObject json = LocalBudgetBenchmark.document(data);
        require(json.get("timingProfile").getAsString().equals("LOCAL_DIAGNOSTIC"), "Diagnostic timing labelled");
        require(json.get("firstSearchBudgetMsA").getAsInt() == 486 && json.get("firstSearchBudgetMsB").getAsInt() == 486
                && json.get("turnSearchBudgetMsA").getAsInt() == 100 && json.get("turnSearchBudgetMsB").getAsInt() == 36,
                "Effective allowances reported");
        require(!json.get("promotionEligible").getAsBoolean() && !json.get("promotionGatePassed").getAsBoolean()
                && !json.get("promotion").getAsBoolean() && !json.get("processReadinessAssessed").getAsBoolean(),
                "No promotion/process implication");
        // Even an artificially perfect diagnostic score cannot enable a historical gate field.
        data.stats.winsA = data.stats.games; data.stats.draws = 0;
        require(!LocalBudgetBenchmark.document(data).get("promotionGatePassed").getAsBoolean(), "Gate remains disabled");
        data.stats.winsA = 0; data.stats.draws = data.stats.games;
        Path report = out.resolve("synthetic-report.json");
        LocalBudgetBenchmark.write(report, data);
        try { LocalBudgetBenchmark.write(report, data); throw new AssertionError("Report overwritten"); }
        catch (java.nio.file.FileAlreadyExistsException expected) { /* original retained */ }
        rejected(() -> new LocalBudgetBenchmark.RunData(3, 5, 2, Map.of()), "odd games");
        rejected(() -> new LocalBudgetBenchmark.RunData(2, 5, 1, Map.of()), "one worker");
        rejected(() -> new LocalBudgetBenchmark.RunData(4, -1, 2, Map.of()), "zero-containing seeds");
        rejected(() -> BotFactory.create(LocalBudgetBenchmark.SPEC + ",TURN_BUDGET_MS=100"), "Production factory100");
        rejected(() -> new MainBot(new Params("TURN_BUDGET_MS=100")), "Production MainBot100");
        require(new Params().TURN_BUDGET_MS == 36 && !new Params().USE_BATCHED_REFRESH, "Defaults unchanged");
        Referee standard = new Referee();
        TimingProfile.CG.apply(standard, LocalBudgetBenchmark.SPEC, LocalBudgetBenchmark.SPEC);
        require(standard.firstTurnBudgetMs == 486 && standard.playerTurnBudgetMs[0] == 36
                && standard.playerTurnBudgetMs[1] == 36 && standard.slowMoveMs == 50, "Ordinary harness unchanged");
        GameState initial = MapGenerator.generate(81371);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Protocol.initBlock(initial, 0).getBytes(StandardCharsets.UTF_8)));
        AtomicInteger blocked = new AtomicInteger();
        var overlap = new LocalBudgetBenchmark.RunData(2, 81371, 2, Map.of());
        try {
            LocalBudgetBenchmark.execute(overlap, new Maps(""), Set.of(hash), fake("A", seen, blocked), fake("B", seen, blocked), false);
            throw new AssertionError("Excluded map accepted");
        } catch (IllegalArgumentException expected) {
            require(blocked.get() == 0 && overlap.stats.games == 0 && overlap.failure != null,
                    "Overlap rejected before any bot or game");
        }
        // Pure arithmetic check for strict comparison boundaries; no sleeping/timing assertion.
        var totals = new LocalBudgetBenchmark.CallTotals();
        totals.observe(36_000_000, 36, null); totals.observe(50_000_001, 100, null); totals.observe(100_000_001, 100, null);
        require(totals.calls == 3 && totals.above36Ms == 2 && totals.above50Ms == 2
                && totals.above100Ms == 1 && totals.aboveAllowance == 1, "Nanosecond threshold counts");
        System.out.println("PASS local budget wiring: fake WAIT bots only, first486 both/later100-36 swapped; report and guards verified");
    }
}
