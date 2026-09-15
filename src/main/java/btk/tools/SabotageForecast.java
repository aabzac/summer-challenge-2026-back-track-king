package btk.tools;

import btk.bot.Params;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;

import java.util.Arrays;

/** Offline observer prototype. No access to replay stdout, future state or bot search calls. */
public final class SabotageForecast {
    public static final int UNKNOWN = -2, NONE = -1;
    public static final String[] EXPERTS = {"income", "tracks", "finish", "persist", "none"};
    private final Params params;
    private final Pathing pathing;
    private final GameState scratch;
    private final int[] victimTracks, actorTracks, income = new int[2], afterIncome = new int[2];
    private final double[] logWeight = new double[EXPERTS.length];
    public final double[] weights = new double[EXPERTS.length];
    public final double[][] probabilities;
    public final double[] adaptive, fixed;
    public final int[] choices = new int[EXPERTS.length];
    private int previousTarget = NONE;
    private boolean pending;

    public SabotageForecast(GameState initial, Params params) {
        if (!(params.FORECAST_DECAY > 0 && params.FORECAST_DECAY <= 1)
                || !(params.FORECAST_FLOOR > 0 && params.FORECAST_FLOOR < 1)) {
            throw new IllegalArgumentException("Invalid forecast decay/floor");
        }
        this.params = params.copy();
        pathing = new Pathing(initial.size);
        scratch = initial.copy();
        victimTracks = new int[initial.regionCount];
        actorTracks = new int[initial.regionCount];
        probabilities = new double[EXPERTS.length][initial.regionCount + 1];
        adaptive = new double[initial.regionCount + 1];
        fixed = new double[initial.regionCount + 1];
        Arrays.fill(weights, 1.0 / EXPERTS.length);
    }

    /** actor is the opponent being predicted. Only pre-turn state and previous observations. */
    public void predict(GameState before, int actor) {
        if (pending) throw new IllegalStateException("Consume observation before the next prediction");
        Arrays.fill(victimTracks, 0);
        Arrays.fill(actorTracks, 0);
        for (int c = 0; c < before.size; c++) {
            if (before.owner[c] == actor) actorTracks[before.regionId[c]]++;
            else if (before.owner[c] == 1 - actor) victimTracks[before.regionId[c]]++;
        }
        Rules.incomePerTurn(before, income);
        Arrays.fill(choices, before.regionCount); // final category = no effective disruption
        double bestIncome = 0, bestTracks = 0, bestFinish = 0;
        int categories = 1;
        for (int r = 0; r < before.regionCount; r++) {
            if (before.regionInked[r]) continue;
            categories++;
            if (before.regionHasTown(r)) continue; // floor still covers unusual town targets
            int needed = Math.max(1, GameState.INK_THRESHOLD - before.regionInstability[r]);
            double trackValue = (victimTracks[r] - params.W_SELF_LOSS * actorTracks[r]) / (double) needed;
            double finishValue = Math.max(0, victimTracks[r] - params.W_SELF_LOSS * actorTracks[r])
                    / (needed * (double) needed);
            double incomeValue = 0;
            if (victimTracks[r] + actorTracks[r] > 0) {
                scratch.copyFrom(before);
                Rules.ink(scratch, r);
                Rules.updateConnections(scratch, pathing);
                Rules.incomePerTurn(scratch, afterIncome);
                incomeValue = ((income[1 - actor] - afterIncome[1 - actor])
                        - params.W_SELF_LOSS * (income[actor] - afterIncome[actor])) / needed;
            }
            if (incomeValue > bestIncome) { bestIncome = incomeValue; choices[0] = r; }
            if (trackValue > bestTracks) { bestTracks = trackValue; choices[1] = r; }
            if (finishValue > bestFinish) { bestFinish = finishValue; choices[2] = r; }
        }
        choices[3] = previousTarget >= 0 && !before.regionInked[previousTarget]
                ? previousTarget : choices[0];
        Arrays.fill(adaptive, 0);
        Arrays.fill(fixed, 0);
        for (int e = 0; e < EXPERTS.length; e++) {
            for (int r = 0; r <= before.regionCount; r++) {
                double p = r == before.regionCount || !before.regionInked[r]
                        ? params.FORECAST_FLOOR / categories : 0;
                if (r == choices[e]) p += 1 - params.FORECAST_FLOOR;
                probabilities[e][r] = p;
                adaptive[r] += weights[e] * p;
                fixed[r] += p / EXPERTS.length;
            }
        }
        pending = true;
    }

    /** Learn after scoring the prediction. UNKNOWN neither updates weights nor invents a target. */
    public void observe(int target) {
        if (!pending) throw new IllegalStateException("No prediction to update");
        pending = false;
        if (target == UNKNOWN) return;
        int category = target == NONE ? adaptive.length - 1 : target;
        if (category < 0 || category >= adaptive.length || adaptive[category] <= 0) {
            throw new IllegalArgumentException("Observation outside predicted support");
        }
        double max = Double.NEGATIVE_INFINITY;
        for (int e = 0; e < EXPERTS.length; e++) {
            logWeight[e] = params.FORECAST_DECAY * logWeight[e] + Math.log(probabilities[e][category]);
            max = Math.max(max, logWeight[e]);
        }
        double sum = 0;
        for (int e = 0; e < EXPERTS.length; e++) {
            logWeight[e] -= max;
            weights[e] = Math.exp(logWeight[e]);
            sum += weights[e];
        }
        for (int e = 0; e < EXPERTS.length; e++) weights[e] /= sum;
        if (target >= 0) previousTarget = target;
    }

    /**
     * Infer the EFFECT of the opponent's last sabotage from protocol-visible instability/ink
     * and our known, accepted disruption. Enumerate core transitions, not hand-coded rules.
     * Already inked targets are indistinguishable from no effective sabotage and map to NONE.
     */
    public int inferObservation(GameState before, GameState after, int ownDisrupt) {
        if (before.regionCount != after.regionCount || after.turn != before.turn + 1) return UNKNOWN;
        int matched = UNKNOWN;
        for (int candidate = NONE; candidate < before.regionCount; candidate++) {
            if (candidate >= 0 && before.regionInked[candidate]) continue;
            scratch.copyFrom(before);
            if (ownDisrupt >= 0) Rules.disrupt(scratch, ownDisrupt);
            if (candidate >= 0) Rules.disrupt(scratch, candidate);
            Rules.inkUnstable(scratch);
            if (Arrays.equals(scratch.regionInstability, after.regionInstability)
                    && Arrays.equals(scratch.regionInked, after.regionInked)) {
                if (matched != UNKNOWN) return UNKNOWN;
                matched = candidate;
            }
        }
        return matched;
    }

    public static int top(double[] probabilities) {
        int best = 0;
        for (int i = 1; i < probabilities.length; i++) if (probabilities[i] > probabilities[best]) best = i;
        return best == probabilities.length - 1 ? NONE : best;
    }
}
