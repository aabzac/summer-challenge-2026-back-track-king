package btk.tools;

import btk.bot.Bot;
import btk.bot.BotFactory;
import btk.bot.Params;
import btk.bot.current.IndrillV2Bot;
import btk.bot.current.IndrillFeatures;
import btk.bot.current.IndrillV2Net;
import btk.core.Action;
import btk.core.GameState;
import btk.core.InkProjection;
import btk.core.Pathing;
import btk.core.Rules;
import btk.core.SearchState;
import btk.referee.MapGenerator;
import com.google.gson.GsonBuilder;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.zip.GZIPOutputStream;

/** Offline outcome-driven replay collection. Neither this tool nor exploration ships. */
public final class IndrillV2Collect {
    private IndrillV2Collect() {}

    public static void main(String[] args) throws Exception {
        int games = Integer.parseInt(args[0]);
        long seed = Long.parseLong(args[1]);
        Path weights = Path.of(args[2]), out = Path.of(args[3]);
        double epsilon = Double.parseDouble(args[4]);
        String opponent = args[5];
        if (games <= 0 || seed == 0 || epsilon < 0 || epsilon > 1 || !Double.isFinite(epsilon))
            throw new IllegalArgumentException("Invalid fixed collection plan");
        if (Files.exists(out) || Files.exists(Path.of(out + ".json"))) throw new IllegalStateException("Collection output exists");
        float[][] tensors = readWeights(weights);
        Files.createDirectories(out.toAbsolutePath().getParent());
        List<Object> results = new ArrayList<>();
        long began = System.nanoTime();
        try (DataOutputStream file = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(out)))) {
            file.write("BTKQ0001".getBytes(StandardCharsets.US_ASCII));
            file.writeInt(IndrillFeatures.INPUTS);
            file.writeInt(games);
            for (int game = 0; game < games; game++) {
                GameState s = MapGenerator.generate(seed + game);
                Pathing pathing = new Pathing(s.size);
                SearchState projection = new SearchState(s);
                Pathing projectionPaths = new Pathing(s.size);
                int[] before = new int[2], after = new int[2];
                IndrillV2Bot[] q = {new IndrillV2Bot(new Params(), new IndrillV2Net(tensors)),
                        new IndrillV2Bot(new Params(), new IndrillV2Net(tensors))};
                q[0].init(s, 0); q[1].init(s, 1);
                String control = opponent.equals("mixed") ? (game % 4 < 2 ? "self" : game % 4 == 2 ? "v09" : "neural-v1") : opponent;
                int controlled = (game / 4) % 2;
                Bot frozen = control.equals("self") ? null : BotFactory.create(control);
                if (frozen != null) frozen.init(s, controlled);
                Random random = new Random((seed + game) * 1000003L);
                ByteArrayOutputStream episode = new ByteArrayOutputStream();
                DataOutputStream record = new DataOutputStream(episode);
                int states = 0;
                boolean done;
                do {
                    q[0].decide(s, 0); q[1].decide(s, 1);
                    writeObservation(record, q, s);
                    List<Action> a0 = frozen != null && controlled == 0 ? frozen.play(s, 0, s.turn == 0 ? 486 : 36)
                            : explore(s, q[0], random, epsilon);
                    List<Action> a1 = frozen != null && controlled == 1 ? frozen.play(s, 1, s.turn == 0 ? 486 : 36)
                            : explore(s, q[1], random, epsilon);
                    InkProjection.scores(s, projection, projectionPaths, before);
                    Rules.TurnResult tr = Rules.resolve(s, a0, a1, pathing);
                    if (tr.hasError(0) || tr.hasError(1) || !tr.moves[0].skipped.isEmpty() || !tr.moves[1].skipped.isEmpty())
                        throw new IllegalStateException("Illegal training action at game " + game + " turn " + s.turn);
                    done = Rules.gameOver(s, pathing);
                    for (int p = 0; p < 2; p++) {
                        Rules.PlayerMoves moves = tr.moves[p];
                        record.writeInt(moves.disrupts.isEmpty() ? -1 : moves.disrupts.get(0));
                        record.writeInt(moves.count);
                        for (int k = 0; k < 3; k++) record.writeInt(k < moves.count ? moves.cells[k] : -1);
                    }
                    InkProjection.scores(s, projection, projectionPaths, after);
                    float shaped = ((after[0] - after[1]) - (before[0] - before[1])) / 100f;
                    float terminal = done ? Integer.compare(s.score[0], s.score[1]) : 0;
                    record.writeFloat(shaped + terminal);
                    record.writeFloat(-shaped - terminal);
                    record.writeBoolean(done);
                    states++;
                } while (!done);
                // An explicit final state allows exact n-step successor construction.
                q[0].features.prepare(s, 0); q[1].features.prepare(s, 1);
                writeObservation(record, q, s);
                for (int p = 0; p < 2; p++) { record.writeInt(-1); record.writeInt(0); for (int k = 0; k < 3; k++) record.writeInt(-1); }
                record.writeFloat(0); record.writeFloat(0); record.writeBoolean(true);
                states++;
                record.flush();
                file.writeInt(game); file.writeInt(s.width); file.writeInt(s.height); file.writeInt(s.regionCount);
                for (int r : s.regionId) file.writeInt(r);
                for (int c = 0; c < s.size; c++) file.writeByte(s.placeCost(c));
                file.writeInt(states);
                file.write(episode.toByteArray());
                results.add(java.util.Map.of("seed", seed + game, "opponent", control, "controlSeat", controlled,
                        "turns", s.turn, "score0", s.score[0], "score1", s.score[1]));
                if ((game + 1) % 8 == 0) System.out.printf("Collected %d/%d games%n", game + 1, games);
            }
        }
        var meta = new java.util.LinkedHashMap<String, Object>();
        meta.put("schema", "BTKQ0001"); meta.put("features", 64); meta.put("games", games);
        meta.put("seed", seed); meta.put("epsilon", epsilon); meta.put("opponent", opponent);
        meta.put("weightsSha256", sha(weights)); meta.put("datasetSha256", sha(out));
        meta.put("reward", "delta projectedInkAll score difference / 100 + terminal {-1,0,1}; opponent antisymmetric");
        meta.put("seconds", (System.nanoTime() - began) / 1e9); meta.put("results", results);
        Files.writeString(Path.of(out + ".json"), new GsonBuilder().setPrettyPrinting().create().toJson(meta));
        System.out.println("Saved " + out);
    }

    private static void writeObservation(DataOutputStream out, IndrillV2Bot[] q, GameState s) throws Exception {
        for (int p = 0; p < 2; p++) for (float[] x : q[p].features.x) for (float f : x) out.writeFloat(f);
        for (int cost : q[0].features.buildCost) out.writeByte(cost == 0 ? 0 : 1);
        for (boolean legal : q[0].features.legalInk) out.writeBoolean(legal);
    }

    private static List<Action> explore(GameState s, IndrillV2Bot bot, Random random, double epsilon) {
        if (random.nextDouble() >= epsilon) return IndrillV2Bot.actions(s, bot.selectedCells, bot.selectedCount, bot.selectedInk);
        int[] cells = new int[3];
        int count = 0, paint = 3;
        while (paint > 0 && count < 3) {
            int chosen = -1, seen = 0;
            for (int c = 0; c < s.size; c++) {
                boolean used = false;
                for (int i = 0; i < count; i++) if (cells[i] == c) used = true;
                if (!used && bot.features.buildCost[c] > 0 && bot.features.buildCost[c] <= paint && random.nextInt(++seen) == 0) chosen = c;
            }
            if (chosen < 0) break;
            cells[count++] = chosen; paint -= s.placeCost(chosen);
        }
        int target = -1, seen = 0;
        for (int r = 0; r < s.regionCount; r++) if (bot.features.legalInk[r] && random.nextInt(++seen) == 0) target = r;
        if (random.nextDouble() < .1) target = -1;
        return IndrillV2Bot.actions(s, cells, count, target);
    }

    public static float[][] readWeights(Path path) throws Exception {
        try (DataInputStream in = new DataInputStream(Files.newInputStream(path))) {
            if (!new String(in.readNBytes(8), StandardCharsets.US_ASCII).equals("BTKW0002") || in.readInt() != 64 || in.readInt() != 24)
                throw new IllegalArgumentException("Invalid Q weight format");
            int[] sizes = {64 * 24, 24, 24 * 24, 24 * 24, 24 * 24, 24, 24, 24, 1, 1, 24, 1};
            float[][] result = new float[sizes.length][];
            for (int i = 0; i < sizes.length; i++) {
                result[i] = new float[sizes[i]];
                for (int j = 0; j < sizes[i]; j++) result[i][j] = in.readFloat();
            }
            if (in.read() != -1) throw new IllegalArgumentException("Trailing weight data");
            return result;
        }
    }
    private static String sha(Path path) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (var in = Files.newInputStream(path)) { byte[] b = new byte[65536]; int n; while ((n = in.read(b)) >= 0) md.update(b, 0, n); }
        return HexFormat.of().formatHex(md.digest());
    }
}
