package btk.tools;

import btk.bot.BotFactory;
import btk.bot.Params;
import btk.bot.current.EvalBot;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import com.google.gson.GsonBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reset to recorded states, never claim this reproduces the local timeout match trajectory. */
public final class CgTimingStudy {
    record Sample(long gameId, int turn, int player, double totalMs, double buildMs,
                  double evalMs, double maxEvalMs, int evalCalls, double disruptMs, int abortedEvals) {}
    record Warmup(long gameId, int player, double elapsedMs, int buildCalls) {}

    public static void main(String[] args) throws Exception {
        Params params = BotFactory.parameters("current?USE_ROLLOUT_POLICY=0").apply(args.length > 1 ? args[1] : "");
        params.PROFILE_SEARCH = true;
        if (params.USE_ROLLOUT_POLICY || params.USE_HEURISTIC_POLICY
                || params.USE_REPLY_RERANK || params.USE_OPP_MODEL || params.EARLY_DISRUPT_FALLBACK
                || params.GUARD_BUILD_INCOME || params.CHOOSE_BUILD_DIRECTION) {
            throw new IllegalArgumentException("Timing study isolates the baseline build/disrupt policy");
        }
        List<Sample> samples = new ArrayList<>();
        List<Warmup> warmups = new ArrayList<>();
        for (String file : args[0].split(",")) {
            CgReplay replay = CgReplay.load(Path.of(file));
            if (CgValidate.validate(replay, false).firstDivergenceTurn >= 0) {
                throw new IllegalStateException("Invalid replay " + replay.gameId);
            }
            GameState state = replay.toState(); Pathing pathing = new Pathing(state.size);
            Rules.updateConnections(state, pathing);
            EvalBot[] bots = {new EvalBot(params.copy()), new EvalBot(params.copy())};
            for (int player = 0; player < 2; player++) {
                bots[player].init(state.copy(), player);
                long start = System.nanoTime();
                bots[player].warmup(state.copy(), player, 500);
                warmups.add(new Warmup(replay.gameId, player, (System.nanoTime() - start) / 1e6,
                        bots[player].profileLifetimeBuildCalls));
            }
            for (int turn = 0; turn < replay.turns(); turn++) {
                if (replay.failure(turn, 0) != null || replay.failure(turn, 1) != null) break;
                for (int player = 0; player < 2; player++) {
                    EvalBot bot = bots[player]; GameState input = state.copy();
                    long start = System.nanoTime();
                    bot.play(input, player, turn == 0 ? 486 : 36);
                    samples.add(new Sample(replay.gameId, turn, player, (System.nanoTime() - start) / 1e6,
                            bot.profileBuildNs / 1e6, bot.profileEvalNs / 1e6, bot.profileEvalMaxNs / 1e6,
                            bot.profileEvalCalls, bot.profileDisruptNs / 1e6, bot.profileAbortedEvals));
                }
                Rules.resolve(state, CgPlayerStudy.effectiveActions(replay, turn, 0),
                        CgPlayerStudy.effectiveActions(replay, turn, 1), pathing);
            }
        }
        if (samples.isEmpty()) throw new IllegalStateException("Empty timing sample");
        Path out = Path.of(args[2]); Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                "method", "Instrumented current with v05 defaults on recorded states, not frozen executable or local timeout trajectory. "
                        + "Includes cold JIT; warmup 500ms, play 486/36ms; inclusive evaluation is part of build. No timing/strength gate.",
                "params", params, "warmups", warmups, "samples", samples)));
        double build = samples.stream().mapToDouble(Sample::buildMs).sum();
        double eval = samples.stream().mapToDouble(Sample::evalMs).sum();
        System.out.printf(Locale.ROOT, "%d calls; build %.2f ms, nested eval %.2f ms (%.1f%%), disrupt %.2f ms%n",
                samples.size(), build, eval, 100 * eval / Math.max(1e-9, build),
                samples.stream().mapToDouble(Sample::disruptMs).sum());
        System.out.println("Warmup: " + warmups);
        samples.stream().filter(s -> s.turn > 0).sorted((a, b) -> Double.compare(b.totalMs, a.totalMs))
                .limit(8).forEach(System.out::println);
        System.out.println("Report: " + out);
    }
}
