package btk.tools.ppo;

import btk.referee.MapGenerator;
import com.google.gson.GsonBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Learning check only: does a short PPO loop beat its own initial weights?
 * Not a comparison with v09 and not a submission.
 */
public final class PpoProbe {
    public static final int ITERATIONS = 24;
    public static final int GAMES = 16;
    public static final int EVAL_MAPS = 30;
    public static final int EPOCHS = 4;
    public static final long TRAIN_SEED0 = 9_600_000L;
    public static final long EVAL_SEED0 = 9_610_000L;
    public static final long WEIGHT_SEED = 20261006L;
    public static final double PASS_RATE = 0.60;

    private PpoProbe() {}

    public static void main(String[] args) throws Exception {
        if (args.length > 1) throw new IllegalArgumentException("usage: PpoProbe [out.json]");
        Path out = Path.of(args.length == 0 ? "build/analysis/ppo-probe-v1/result.json" : args[0]);
        if (Files.exists(out)) throw new IllegalStateException("refusing to overwrite " + out);
        Map<String, Object> report = run();
        Files.createDirectories(out.getParent());
        Files.writeString(out, new GsonBuilder().setPrettyPrinting().create().toJson(report), StandardOpenOption.CREATE_NEW);
        System.out.println("verdict " + report.get("verdict") + " score " + report.get("score"));
        System.out.println("report: " + out);
    }

    /** Same training loop as {@link #run()}, without the mirror or the initial-weight games. */
    public static PpoModel[] initialAndTrained() {
        PpoModel model = new PpoModel(WEIGHT_SEED);
        PpoModel initial = model.copy();
        Random rng = new Random(WEIGHT_SEED + 17);
        for (int iter = 0; iter < ITERATIONS; iter++) {
            long t0 = System.nanoTime();
            List<PpoRollout.Step> batch = new ArrayList<>();
            for (int g = 0; g < GAMES; g++) {
                var state = MapGenerator.generate(TRAIN_SEED0 + iter * (long) GAMES + g);
                batch.addAll(new PpoRollout(state).play(state, model, rng).steps);
            }
            double[] stats = update(model, batch);
            System.out.printf("iter %d steps %d entropy %.3f %.1fs%n",
                    iter, batch.size(), stats[0], (System.nanoTime() - t0) / 1e9);
            System.out.flush();
        }
        return new PpoModel[]{initial, model};
    }

    public static Map<String, Object> run() {
        long started = System.nanoTime();
        PpoModel model = new PpoModel(WEIGHT_SEED);
        int[] mirror = tally(model, model, EVAL_SEED0, EVAL_MAPS);
        if (mirror[0] != mirror[1]) {
            throw new IllegalStateException("identical policies are seat-biased: " + Arrays.toString(mirror));
        }
        PpoModel initial = model.copy();
        double[] routeWeight0 = {model.build[1], model.disrupt[1]};
        Random rng = new Random(WEIGHT_SEED + 17);
        List<Map<String, Object>> curve = new ArrayList<>();
        for (int iter = 0; iter < ITERATIONS; iter++) {
            long t0 = System.nanoTime();
            List<PpoRollout.Step> batch = new ArrayList<>();
            for (int g = 0; g < GAMES; g++) {
                var state = MapGenerator.generate(TRAIN_SEED0 + iter * (long) GAMES + g);
                batch.addAll(new PpoRollout(state).play(state, model, rng).steps);
            }
            double[] stats = update(model, batch);
            double seconds = (System.nanoTime() - t0) / 1e9;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("iteration", iter);
            row.put("steps", batch.size());
            row.put("entropy", stats[0]);
            row.put("valueMse", stats[1]);
            row.put("kl", stats[2]);
            row.put("clipFraction", stats[3]);
            row.put("seconds", seconds);
            curve.add(row);
            System.out.printf("iter %d steps %d entropy %.3f mse %.3f kl %.4f clip %.2f %.1fs%n",
                    iter, batch.size(), stats[0], stats[1], stats[2], stats[3], seconds);
            System.out.flush();
        }
        int[] eval = tally(model, initial, EVAL_SEED0, EVAL_MAPS);
        double score = rate(eval);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("experiment", "ppo-probe-v1");
        report.put("question", "Does greedy play after 24 PPO iterations beat the initial weights?");
        report.put("iterations", ITERATIONS);
        report.put("gamesPerIteration", GAMES);
        report.put("evalMaps", EVAL_MAPS);
        report.put("epochs", EPOCHS);
        report.put("gamma", PpoRollout.GAMMA);
        report.put("clip", PpoModel.CLIP);
        report.put("learningRate", PpoModel.LR);
        report.put("entropyCoef", PpoModel.ENTROPY);
        report.put("terminalBonus", PpoRollout.TERMINAL);
        report.put("trainSeed0", TRAIN_SEED0);
        report.put("evalSeed0", EVAL_SEED0);
        report.put("weightSeed", WEIGHT_SEED);
        report.put("passRate", PASS_RATE);
        report.put("mirrorWinsLossesDraws", List.of(mirror[0], mirror[1], mirror[2]));
        report.put("wins", eval[0]);
        report.put("losses", eval[1]);
        report.put("draws", eval[2]);
        report.put("score", score);
        report.put("verdict", score >= PASS_RATE ? "PASS_LEARNING" : "NOT_MOVED");
        report.put("routeWeightBefore", routeWeight0[0]);
        report.put("routeWeightAfter", model.build[1]);
        report.put("disruptSwingWeightBefore", routeWeight0[1]);
        report.put("disruptSwingWeightAfter", model.disrupt[1]);
        report.put("curve", curve);
        report.put("elapsedSec", (System.nanoTime() - started) / 1e9);
        report.put("note", "Linear policy on public board features, income-gap reward, both seats. "
                + "Not the 3rd-place convolutional network and not a match against v09.");
        return report;
    }

    /** Wins, losses and draws of {@code subject} over both seats on each map. */
    public static int[] tally(PpoModel subject, PpoModel other, long seed0, int maps) {
        int wins = 0, losses = 0, draws = 0;
        for (int i = 0; i < maps; i++) {
            for (int seat = 0; seat < 2; seat++) {
                var state = MapGenerator.generate(seed0 + i);
                PpoModel p0 = seat == 0 ? subject : other;
                PpoModel p1 = seat == 0 ? other : subject;
                int[] score = new PpoRollout(state).versus(state, p0, p1);
                int cmp = Integer.compare(score[seat], score[1 - seat]);
                if (cmp > 0) wins++;
                else if (cmp < 0) losses++;
                else draws++;
            }
        }
        return new int[]{wins, losses, draws};
    }

    public static double rate(int[] wld) {
        int games = wld[0] + wld[1] + wld[2];
        return games == 0 ? 0 : (wld[0] + 0.5 * wld[2]) / games;
    }

    public static double[] update(PpoModel model, List<PpoRollout.Step> steps) {
        int n = steps.size();
        if (n == 0) throw new IllegalStateException("empty batch");
        double mean = 0;
        for (var step : steps) mean += step.advantage;
        mean /= n;
        double var = 0;
        for (var step : steps) {
            double d = step.advantage - mean;
            var += d * d;
        }
        double denom = Math.sqrt(var / n) + 1e-8;
        for (var step : steps) step.advantage = (step.advantage - mean) / denom;
        double[] buildGrad = new double[PpoModel.CELL_F];
        double[] disruptGrad = new double[PpoModel.DISRUPT_F];
        double[] valueGrad = new double[PpoModel.VALUE_F];
        double entropy = 0, mse = 0, kl = 0, clip = 0;
        int components = 0;
        for (int epoch = 0; epoch < EPOCHS; epoch++) {
            Arrays.fill(buildGrad, 0);
            Arrays.fill(disruptGrad, 0);
            Arrays.fill(valueGrad, 0);
            double epochMse = 0, epochKl = 0, epochEnt = 0;
            int epochComps = 0, clips = 0;
            for (var step : steps) {
                double newLogp = 0;
                for (var comp : step.comps) {
                    double[] weights = comp.disrupt ? model.disrupt : model.build;
                    newLogp += model.logProb(weights, comp.features, comp.n, comp.width, comp.chosen);
                }
                double delta = Math.max(-20, Math.min(20, newLogp - step.oldLogp));
                double ratio = Math.exp(delta);
                boolean clipped = step.advantage >= 0 ? ratio > 1 + PpoModel.CLIP : ratio < 1 - PpoModel.CLIP;
                if (clipped) clips++;
                double coef = clipped ? 0 : ratio * step.advantage;
                for (var comp : step.comps) {
                    double[] weights = comp.disrupt ? model.disrupt : model.build;
                    double[] grad = comp.disrupt ? disruptGrad : buildGrad;
                    model.policyGrad(weights, comp.features, comp.n, comp.width, comp.chosen, coef, PpoModel.ENTROPY, grad);
                    epochEnt += model.entropy(weights, comp.features, comp.n, comp.width);
                    epochComps++;
                }
                model.valueGrad(step.state, step.returns, valueGrad);
                double err = model.value(step.state) - step.returns;
                epochMse += err * err;
                epochKl += step.oldLogp - newLogp;
            }
            double norm = 0;
            for (double g : buildGrad) norm += g * g;
            for (double g : disruptGrad) norm += g * g;
            for (double g : valueGrad) norm += g * g;
            double scale = PpoModel.clipScale(Math.sqrt(norm));
            if (scale != 1) {
                for (int i = 0; i < buildGrad.length; i++) buildGrad[i] *= scale;
                for (int i = 0; i < disruptGrad.length; i++) disruptGrad[i] *= scale;
                for (int i = 0; i < valueGrad.length; i++) valueGrad[i] *= scale;
            }
            model.apply(buildGrad, disruptGrad, valueGrad);
            finite(model.build);
            finite(model.disrupt);
            finite(model.value);
            entropy = epochEnt / epochComps;
            mse = epochMse / n;
            kl = epochKl / n;
            clip = clips / (double) n;
            components = epochComps;
        }
        if (components == 0) throw new IllegalStateException("no policy components");
        return new double[]{entropy, mse, kl, clip};
    }

    private static void finite(double[] weights) {
        for (double w : weights) if (!Double.isFinite(w)) throw new IllegalStateException("non-finite weight");
    }
}
