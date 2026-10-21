package btk.tools.ppo;

import btk.bot.Bot;
import btk.bot.BotFactory;
import btk.referee.MapGenerator;
import btk.referee.Referee;
import btk.runner.TimingProfile;
import com.google.gson.GsonBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Diagnostic: the same 30 held-out maps, both seats, initial weights and trained
 * weights each against frozen v09. CG search budgets 486/36 ms. Not a promotion.
 */
public final class PpoVsV09 {
    private PpoVsV09() {}

    public static void main(String[] args) throws Exception {
        if (args.length > 1) throw new IllegalArgumentException("usage: PpoVsV09 [out.json]");
        Path out = Path.of(args.length == 0 ? "build/analysis/ppo-probe-v1/vs-v09.json" : args[0]);
        if (Files.exists(out)) throw new IllegalStateException("refusing to overwrite " + out);
        PpoModel[] models = PpoProbe.initialAndTrained();
        List<Map<String, Object>> games = new ArrayList<>();
        int[] initial = new int[3], trained = new int[3];
        long initialDiff = 0, trainedDiff = 0;
        for (int i = 0; i < PpoProbe.EVAL_MAPS; i++) {
            long seed = PpoProbe.EVAL_SEED0 + i;
            for (int seat = 0; seat < 2; seat++) {
                Referee.Result before = play(models[0], "ppo-initial", seed, seat);
                Referee.Result after = play(models[1], "ppo-trained", seed, seat);
                if (before.error[0] != null || before.error[1] != null || after.error[0] != null || after.error[1] != null) {
                    throw new IllegalStateException("game error seed " + seed + " seat " + seat
                            + " initial " + before.error[0] + "/" + before.error[1]
                            + " trained " + after.error[0] + "/" + after.error[1]);
                }
                int d0 = diff(before, seat);
                int d1 = diff(after, seat);
                add(initial, outcome(before, seat));
                add(trained, outcome(after, seat));
                initialDiff += d0;
                trainedDiff += d1;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("seed", seed);
                row.put("seat", seat);
                row.put("initialDiff", d0);
                row.put("trainedDiff", d1);
                row.put("initialScore", List.of(before.score[0], before.score[1]));
                row.put("trainedScore", List.of(after.score[0], after.score[1]));
                games.add(row);
                System.out.printf("map %d seat %d initial %+d trained %+d%n", i, seat, d0, d1);
                System.out.flush();
            }
        }
        int n = games.size();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("experiment", "ppo-probe-v1-vs-v09");
        report.put("control", "v09");
        report.put("timing", "CG 486/36");
        report.put("maps", PpoProbe.EVAL_MAPS);
        report.put("gamesPerPolicy", n);
        report.put("evalSeed0", PpoProbe.EVAL_SEED0);
        report.put("initialWinsLossesDraws", List.of(initial[0], initial[1], initial[2]));
        report.put("trainedWinsLossesDraws", List.of(trained[0], trained[1], trained[2]));
        report.put("initialRate", PpoProbe.rate(initial));
        report.put("trainedRate", PpoProbe.rate(trained));
        report.put("rateShift", PpoProbe.rate(trained) - PpoProbe.rate(initial));
        report.put("initialMeanDiff", initialDiff / (double) n);
        report.put("trainedMeanDiff", trainedDiff / (double) n);
        report.put("meanDiffShift", (trainedDiff - initialDiff) / (double) n);
        report.put("games", games);
        report.put("note", "Matched maps and seats. Positive diff means the linear policy outscored v09. Not a promotion.");
        Files.createDirectories(out.getParent());
        Files.writeString(out, new GsonBuilder().setPrettyPrinting().create().toJson(report), StandardOpenOption.CREATE_NEW);
        System.out.printf("initial %d-%d-%d rate %.3f meanDiff %.1f%n", initial[0], initial[1], initial[2],
                PpoProbe.rate(initial), initialDiff / (double) n);
        System.out.printf("trained %d-%d-%d rate %.3f meanDiff %.1f%n", trained[0], trained[1], trained[2],
                PpoProbe.rate(trained), trainedDiff / (double) n);
        System.out.printf("shift rate %+.3f meanDiff %+.1f%n",
                PpoProbe.rate(trained) - PpoProbe.rate(initial), (trainedDiff - initialDiff) / (double) n);
        System.out.println("report: " + out);
    }

    private static Referee.Result play(PpoModel model, String name, long seed, int seat) {
        var state = MapGenerator.generate(seed);
        Bot policy = new PpoGreedyBot(model, name);
        Bot v09 = BotFactory.create("v09");
        Referee referee = new Referee();
        TimingProfile.CG.apply(referee);
        return seat == 0
                ? referee.play(state, policy, v09, seed, false)
                : referee.play(state, v09, policy, seed, false);
    }

    private static int diff(Referee.Result result, int seat) {
        return result.score[seat] - result.score[1 - seat];
    }

    /** 1 win, 0 loss, -1 draw, from the policy seat. */
    private static int outcome(Referee.Result result, int seat) {
        return Integer.compare(result.score[seat], result.score[1 - seat]);
    }

    private static void add(int[] wld, int outcome) {
        if (outcome > 0) wld[0]++;
        else if (outcome < 0) wld[1]++;
        else wld[2]++;
    }
}
