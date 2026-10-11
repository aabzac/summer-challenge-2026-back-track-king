package btk.tools;

import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import btk.tools.V09ContinuationProbe.Plan;
import btk.tools.V09ContinuationProbe.ReplyTrace;

/** Tools-only paired initial-action intervention, followed by unchanged full v09 replanning. */
public final class V09FullActionEpisode {
    private V09FullActionEpisode() {}
    public static final String NATIVE = "NATIVE_FIXED_PORTFOLIO";
    public static final String PASSIVE = "PURE_WAIT";

    public record Result(String initialStateSha256, int me, int horizon, int pinnedAttackTarget,
                         V09FullActionProbe.Metadata initialMetadata, List<Comparison> comparisons) {}
    public record Comparison(String opponentRegime, int nativeReply, Arm noHit, Arm attackNow) {}
    public record Arm(String intervention, boolean complete, String stopReason, int steps,
                      boolean terminal, String terminalOutcome, int finalScoreDifference,
                      int[] finalIncome, String finalStateSha256, int positiveAttackSteps,
                      Integer nominatedInkOffset, List<Step> turns,
                      List<ReplyCheck> originalPromiseChecks, List<ForecastCheck> rollingForecasts) {}
    public record Step(int offset, int turn, Plan ownAction, Plan opponentAction, boolean legal,
                       int[] scoreBefore, int[] incomeBefore, int[] regionInstabilityBefore,
                       boolean[] regionInkedBefore, int[] score, int[] income, int[] gained,
                       List<Integer> inkedRegions, boolean terminal, boolean topologyClosed,
                       boolean turnLimitReached, String stateBeforeSha256, String stateSha256) {}
    public record Promise(String kind, int sourceReply, int issuedTurn, Integer target,
                          Integer forecastOffset, Integer dueTurn) {}
    public record PromiseCheck(Promise promise, String status, Integer executionTurn,
                               Integer executionOffset, Integer inkTurn, String inkCause,
                               Integer terminalTurn, String terminalOutcome, boolean byDeadline) {}
    public record ForecastReply(int reply, double value, Promise firstFutureOwnHit,
                                Promise firstFutureNominatedHit, Integer terminalTurn,
                                Integer terminalScoreDifference) {}
    public record Forecast(int issuedOffset, int issuedTurn, String opponentContext,
                           int nominatedTarget, List<ForecastReply> replies) {}
    public record TerminalCheck(Integer predictedTurn, String predictedOutcome, String status,
                                Integer actualTurn, String actualOutcome) {}
    public record ReplyCheck(int sourceReply, PromiseCheck firstFutureOwnHit,
                             PromiseCheck firstFutureNominatedHit, TerminalCheck terminalForecast) {}
    public record ForecastCheck(Forecast forecast, List<ReplyCheck> replies) {}

    /**
     * Both arms spend exactly the native complete construction at step zero. Only that
     * step's disruption differs. Every later own action is freshly selected by unchanged
     * deterministic ample-time v09, including its normal filler and sabotage decision.
     * Native opponent reply identities are fixed at the entry state, but their actions are
     * recomputed against each divergent pre-turn board. No recorded future commands are used.
     */
    public static Result run(GameState initial, int me, int pinnedAttackTarget) throws Exception {
        require(me == 0 || me == 1, "Invalid player");
        require(!Rules.gameOver(initial, new Pathing(initial.size)), "Expected live episode start");
        String before = ReplyStudy.stateHash(initial);
        int horizon = Math.min(8, initial.turnsLeft());
        var probe = V09FullActionProbe.prepare(initial, me);
        var meta = probe.metadata();
        if (!meta.supported()) {
            require(before.equals(ReplyStudy.stateHash(initial)), "Unsupported preparation mutated input");
            return new Result(before, me, horizon, pinnedAttackTarget, meta, List.of());
        }
        // The prospectively pinned WAIT-root target can differ from a selected network root's target.
        require(pinnedAttackTarget >= 0, "Expected a pinned disruption target");
        Plan full = probe.selected();
        // Strict core expansion also checks the changed target without removing or refilling cells.
        canonical(initial, me, full.cells(), -1);
        canonical(initial, me, full.cells(), pinnedAttackTarget);
        Forecast original = forecast(probe.trace(-1, V09FullActionProbe.Variant.BASELINE),
                me, 0, initial.turn, pinnedAttackTarget, "ORIGINAL_NATIVE_REPLY_CONTEXT");
        List<Comparison> comparisons = new ArrayList<>();
        for (int reply = 0; reply < meta.replies(); reply++) {
            comparisons.add(pair(initial, me, pinnedAttackTarget, horizon, probe, original, NATIVE, reply));
        }
        comparisons.add(pair(initial, me, pinnedAttackTarget, horizon, probe, original, PASSIVE, -1));
        require(before.equals(ReplyStudy.stateHash(initial)), "Episode mutated the input state");
        return new Result(before, me, horizon, pinnedAttackTarget, meta, List.copyOf(comparisons));
    }

    private static Comparison pair(GameState initial, int me, int target, int horizon,
                                    V09FullActionProbe initialProbe, Forecast original,
                                    String opponentRegime, int reply) throws Exception {
        Arm noHit = arm(initial, me, target, horizon, initialProbe, original, opponentRegime, reply, false);
        Arm attack = arm(initial, me, target, horizon, initialProbe, original, opponentRegime, reply, true);
        require(Arrays.equals(noHit.turns.get(0).ownAction.cells(), attack.turns.get(0).ownAction.cells()),
                "Paired initial construction differs");
        require(Arrays.equals(noHit.turns.get(0).opponentAction.cells(), attack.turns.get(0).opponentAction.cells())
                        && noHit.turns.get(0).opponentAction.target() == attack.turns.get(0).opponentAction.target(),
                "Simultaneous opponent saw the changed initial own action");
        return new Comparison(opponentRegime, reply, noHit, attack);
    }

    private static Arm arm(GameState initial, int me, int pinnedTarget, int horizon,
                            V09FullActionProbe initialProbe, Forecast original,
                            String opponentRegime, int reply, boolean attack) throws Exception {
        GameState state = initial.copy(); Pathing pathing = new Pathing(state.size);
        List<Step> steps = new ArrayList<>(); List<Forecast> forecasts = new ArrayList<>();
        boolean terminal = false; String stop = "HORIZON"; int hits = 0;
        Integer nominatedInk = null;
        for (int offset = 0; offset < horizon; offset++) {
            require(!Rules.gameOver(state, pathing), "Attempted to continue a terminal branch");
            Plan own;
            if (offset == 0) {
                own = canonical(state, me, initialProbe.selected().cells(), attack ? pinnedTarget : -1);
            } else {
                var fresh = V09FullActionProbe.prepare(state, me);
                var meta = fresh.metadata();
                if (!meta.supported()) { stop = "UNSUPPORTED_REPLAN:" + meta.reason(); break; }
                own = fresh.selected();
                if (own.target() < 0) {
                    forecasts.add(forecast(fresh.trace(-1, V09FullActionProbe.Variant.BASELINE), me,
                            offset, state.turn, pinnedTarget, "FRESH_V09_OPPONENT_CONTEXT"));
                }
            }
            // Both choices read this same known board; neither sees the other's simultaneous plan.
            Plan enemy = opponentRegime.equals(NATIVE) ? initialProbe.opponentAction(state, reply)
                    : canonical(state, 1 - me, new int[0], -1);
            String beforeHash = ReplyStudy.stateHash(state); int turn = state.turn;
            int[] scoreBefore = state.score.clone(), incomeBefore = income(state);
            int[] instabilityBefore = state.regionInstability.clone();
            boolean[] inkedBefore = state.regionInked.clone();
            var resolved = me == 0
                    ? Rules.resolve(state, Action.parseLine(own.action()), Action.parseLine(enemy.action()), pathing)
                    : Rules.resolve(state, Action.parseLine(enemy.action()), Action.parseLine(own.action()), pathing);
            requireAccepted(resolved, me, own); requireAccepted(resolved, 1 - me, enemy);
            terminal = Rules.gameOver(state, pathing);
            steps.add(new Step(offset, turn, own, enemy, true, scoreBefore, incomeBefore, instabilityBefore,
                    inkedBefore, state.score.clone(), income(state), resolved.gained.clone(),
                    List.copyOf(resolved.inkedRegions), terminal, !Rules.anyPairPossible(state, pathing),
                    state.turn >= state.maxTurns, beforeHash, ReplyStudy.stateHash(state)));
            if (own.target() >= 0) hits++;
            if (nominatedInk == null && resolved.inkedRegions.contains(pinnedTarget)) nominatedInk = offset;
            if (terminal) { stop = "TERMINAL"; break; }
        }
        boolean complete = terminal || steps.size() == horizon;
        int horizonEnd = initial.turn + horizon - 1;
        List<ReplyCheck> originalChecks = checks(original, steps, complete, stop, horizonEnd, me,
                opponentRegime.equals(NATIVE) ? reply : -1);
        List<ForecastCheck> rolling = new ArrayList<>();
        for (Forecast f : forecasts)
            rolling.add(new ForecastCheck(f, checks(f, steps, complete, stop, horizonEnd, me, -1)));
        return new Arm(attack ? "ATTACK_AT_STEP_ZERO_ONLY" : "NO_HIT_AT_STEP_ZERO_ONLY", complete, stop,
                steps.size(), terminal, terminal ? outcome(state.score[me] - state.score[1 - me]) : "NONTERMINAL",
                state.score[me] - state.score[1 - me], income(state), ReplyStudy.stateHash(state), hits,
                nominatedInk, List.copyOf(steps), originalChecks, List.copyOf(rolling));
    }

    /** Prediction extraction never reads the later replanned actions or recorded opponent actions. */
    static Forecast forecast(V09FullActionProbe.Trace trace, int me, int issuedOffset, int issuedTurn,
                              int nominatedTarget, String context) {
        List<ForecastReply> predictions = new ArrayList<>();
        for (ReplyTrace reply : trace.replies()) {
            Promise first = new Promise("FIRST_FUTURE_OWN_HIT", reply.reply(), issuedTurn, null, null, null);
            Promise nominated = new Promise("FIRST_FUTURE_NOMINATED_HIT", reply.reply(), issuedTurn, null, null, null);
            Integer terminalTurn = null, terminalDiff = null;
            for (var t : reply.turns()) {
                if (t.offset() > 0 && t.targets()[me] >= 0) {
                    if (first.target == null) first = new Promise(first.kind, reply.reply(), issuedTurn,
                            t.targets()[me], t.offset(), t.turn());
                    if (nominated.target == null && t.targets()[me] == nominatedTarget)
                        nominated = new Promise(nominated.kind, reply.reply(), issuedTurn,
                                nominatedTarget, t.offset(), t.turn());
                }
                if (t.terminal()) { terminalTurn = t.turn(); terminalDiff = t.score()[me] - t.score()[1 - me]; }
            }
            predictions.add(new ForecastReply(reply.reply(), reply.value(), first, nominated, terminalTurn, terminalDiff));
        }
        return new Forecast(issuedOffset, issuedTurn, context, nominatedTarget, List.copyOf(predictions));
    }

    private static List<ReplyCheck> checks(Forecast forecast, List<Step> steps, boolean complete,
                                           String stop, int end, int me, int onlyReply) {
        List<ReplyCheck> result = new ArrayList<>();
        for (var reply : forecast.replies) {
            if (onlyReply >= 0 && reply.reply != onlyReply) continue;
            result.add(new ReplyCheck(reply.reply, classify(reply.firstFutureOwnHit, steps, complete, stop, end, me),
                    classify(reply.firstFutureNominatedHit, steps, complete, stop, end, me),
                    terminalCheck(reply, steps, complete, end, me)));
        }
        return List.copyOf(result);
    }

    /** Same-region promises are classified against their original absolute due turn. */
    static PromiseCheck classify(Promise promise, List<Step> steps, boolean complete,
                                   String stop, int horizonEndTurn, int me) {
        if (promise.target == null) return new PromiseCheck(promise, "NO_PROMISE", null, null,
                null, null, null, null, false);
        Step executed = null, inked = null, invalid = null, terminal = null;
        for (Step step : steps) {
            if (step.turn < promise.issuedTurn) continue;
            if (executed == null && step.ownAction.target() == promise.target) executed = step;
            if (inked == null && step.inkedRegions.contains(promise.target)) inked = step;
            if (invalid == null && (promise.target < 0 || promise.target >= step.regionInkedBefore.length
                    || step.regionInkedBefore[promise.target])) invalid = step;
            if (terminal == null && step.terminal) terminal = step;
        }
        String inkCause = inked == null ? null : inked.ownAction.target() == promise.target
                ? inked.opponentAction.target() == promise.target ? "BOTH_HIT_SAME_REGION" : "OWN_HIT"
                : inked.opponentAction.target() == promise.target ? "OPPONENT_HIT" : "OTHER_CORE_INK";
        String status;
        if (executed != null) status = executed.turn < promise.dueTurn ? "EXECUTED_EARLY"
                : executed.turn == promise.dueTurn ? "EXECUTED_ON_TIME" : "EXECUTED_LATE";
        else if (inked != null && "OPPONENT_HIT".equals(inkCause)) status = inked.turn <= promise.dueTurn
                ? "OPPONENT_INKED_BEFORE_EXECUTION" : "OPPONENT_INKED_AFTER_DEADLINE";
        else if (terminal != null && terminal.turn <= promise.dueTurn) status = "TERMINAL_BY_DEADLINE_BEFORE_EXECUTION";
        else if (invalid != null) status = "TARGET_INVALID_BEFORE_EXECUTION";
        else if (promise.dueTurn > horizonEndTurn) status = "CENSORED_HORIZON";
        else if (!complete && (steps.isEmpty() || steps.get(steps.size() - 1).turn < promise.dueTurn))
            status = "CENSORED_UNSUPPORTED_REPLAN";
        else status = "UNFULFILLED";
        return new PromiseCheck(promise, status, executed == null ? null : executed.turn,
                executed == null ? null : executed.offset, inked == null ? null : inked.turn, inkCause,
                terminal == null ? null : terminal.turn,
                terminal == null ? null : outcome(terminal.score[me] - terminal.score[1 - me]),
                executed != null && executed.turn <= promise.dueTurn);
    }

    private static TerminalCheck terminalCheck(ForecastReply forecast, List<Step> steps,
                                                boolean complete, int horizonEnd, int me) {
        Step terminal = null;
        for (Step step : steps) if (step.terminal) terminal = step;
        String actual = terminal == null ? "NONTERMINAL" : outcome(terminal.score[me] - terminal.score[1 - me]);
        String predicted = forecast.terminalScoreDifference == null ? null : outcome(forecast.terminalScoreDifference);
        String status;
        if (forecast.terminalTurn == null) status = "NO_TERMINAL_FORECAST";
        else if (terminal != null && terminal.turn <= forecast.terminalTurn)
            status = actual.equals(predicted) ? "SAME_OUTCOME_BY_DEADLINE" : "DIFFERENT_OUTCOME_BY_DEADLINE";
        else if (forecast.terminalTurn > horizonEnd) status = "CENSORED_HORIZON";
        else if (!complete && (steps.isEmpty() || steps.get(steps.size() - 1).turn < forecast.terminalTurn))
            status = "CENSORED_UNSUPPORTED_REPLAN";
        else if (terminal != null) status = actual.equals(predicted) ? "SAME_OUTCOME_LATE" : "DIFFERENT_OUTCOME_LATE";
        else status = "NOT_TERMINAL_BY_DEADLINE";
        return new TerminalCheck(forecast.terminalTurn, predicted, status, terminal == null ? null : terminal.turn, actual);
    }

    private static Plan canonical(GameState state, int me, int[] cells, int target) {
        List<Action> actions = new ArrayList<>();
        for (int cell : cells) {
            require(cell >= 0 && cell < state.size, "Invalid episode cell");
            actions.add(Action.place(state.x(cell), state.y(cell)));
        }
        require(target >= -1, "Invalid episode target");
        if (target >= 0) actions.add(Action.disrupt(target));
        if (actions.isEmpty()) actions.add(Action.waitAction());
        return V09ContinuationProbe.accepted(state, me, actions);
    }
    private static void requireAccepted(Rules.TurnResult resolved, int player, Plan intended) {
        var accepted = resolved.moves[player];
        require(accepted.error == null && accepted.skipped.isEmpty() && !resolved.unknownActions
                        && Arrays.equals(intended.cells(), Arrays.copyOf(accepted.cells, accepted.count))
                        && intended.target() == (accepted.disrupts.isEmpty() ? -1 : accepted.disrupts.get(0)),
                "Core rejected or changed an episode action");
    }
    private static int[] income(GameState state) {
        int[] income = new int[2]; Rules.incomePerTurn(state, income); return income;
    }
    private static String outcome(int difference) { return difference > 0 ? "WIN" : difference < 0 ? "LOSS" : "DRAW"; }
    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
