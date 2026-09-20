package btk.tools;

import btk.bot.neural.NeuralFeatures;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Exports the decisions of named players as candidate-set datasets for {@code
 * tools/neural/train_neural.py}: every DISRUPT choice among all legal regions and every
 * PLACE_TRACKS cell (in stdout order) among the affordable candidates of the board that already
 * holds the earlier placements of that turn. Features come from {@link NeuralFeatures}, the same
 * code the shipped {@code NeuralBot} runs, so training rows equal inference rows.
 *
 * <p>Args: {@code paths players outDir holdoutGames negatives seed}. {@code paths}/{@code players}
 * are comma-separated and aligned (directory of canonical CG replays, nickname to imitate).
 * The last {@code holdoutGames} games (by ordinal, i.e. by game id) are exported with the full
 * candidate set; earlier games keep the chosen row plus {@code negatives} sampled rows
 * ({@code 0} = all rows).
 *
 * <p>Files: {@code sabotage.bin}/{@code build.bin} (little-endian float32 rows), {@code
 * sabotage.idx}/{@code build.idx} (int32 records {@code game, turn, k, rowStart, rowCount,
 * labelOffset, holdout}), {@code meta.json}.
 */
public final class CgNeuralExport {

    public static void main(String[] args) throws Exception {
        String[] dirs = (args.length > 0 && !args[0].isEmpty() ? args[0] : "replays/cg-indrill").split(",");
        String[] players = (args.length > 1 && !args[1].isEmpty() ? args[1] : "Indrill").split(",");
        Path out = Path.of(args.length > 2 && !args[2].isEmpty() ? args[2] : "build/analysis/neural-data");
        int holdout = args.length > 3 && !args[3].isEmpty() ? Integer.parseInt(args[3]) : 40;
        int negatives = args.length > 4 && !args[4].isEmpty() ? Integer.parseInt(args[4]) : 40;
        long seed = args.length > 5 && !args[5].isEmpty() ? Long.parseLong(args[5]) : 20260920L;
        if (dirs.length != players.length) throw new IllegalArgumentException("paths and players must align");
        Files.createDirectories(out);

        List<Object[]> games = new ArrayList<>();
        for (int i = 0; i < dirs.length; i++) {
            for (Path f : CgReplay.listReplays(Path.of(dirs[i].trim()))) games.add(new Object[]{f, players[i].trim()});
        }
        Random random = new Random(seed);
        Stats stats = new Stats();
        List<Map<String, Object>> gameMeta = new ArrayList<>();
        try (BinWriter sab = new BinWriter(out.resolve("sabotage.bin"));
             BinWriter sabIdx = new BinWriter(out.resolve("sabotage.idx"));
             BinWriter build = new BinWriter(out.resolve("build.bin"));
             BinWriter buildIdx = new BinWriter(out.resolve("build.idx"))) {
            int ordinal = 0;
            for (Object[] item : games) {
                CgReplay r = CgReplay.load((Path) item[0]);
                String player = (String) item[1];
                int me = r.nicknames[0].equals(player) ? 0 : r.nicknames[1].equals(player) ? 1 : -1;
                if (me < 0) continue;
                boolean isHoldout = ordinal >= games.size() - holdout;
                Exporter e = new Exporter(r, me, ordinal, isHoldout, isHoldout ? 0 : negatives, random, stats);
                e.run(sab, sabIdx, build, buildIdx);
                Map<String, Object> g = new LinkedHashMap<>();
                g.put("ordinal", ordinal);
                g.put("gameId", r.gameId);
                g.put("player", player);
                g.put("seat", me);
                g.put("opponent", r.nicknames[1 - me]);
                g.put("turns", r.turns());
                g.put("holdout", isHoldout);
                g.put("won", r.finalScores[me] > r.finalScores[1 - me]);
                gameMeta.add(g);
                ordinal++;
            }
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("schemaVersion", 1);
        meta.put("sabotageFeatures", NeuralFeatures.SABOTAGE_NAMES);
        meta.put("buildFeatures", NeuralFeatures.BUILD_NAMES);
        meta.put("idxFields", new String[]{"game", "turn", "k", "rowStart", "rowCount", "labelOffset", "holdout"});
        meta.put("holdoutGames", holdout);
        meta.put("negatives", negatives);
        meta.put("seed", seed);
        meta.put("games", gameMeta.size());
        meta.put("sabotageDecisions", stats.sabotageDecisions);
        meta.put("sabotageSkippedNoTarget", stats.sabotageNoTarget);
        meta.put("sabotageCandidatesMean", stats.sabotageDecisions == 0 ? 0 : (double) stats.sabotageCandidates / stats.sabotageDecisions);
        meta.put("buildDecisions", stats.buildDecisions);
        meta.put("buildLabelOutsideCandidates", stats.buildMissed);
        meta.put("buildCandidatesMean", stats.buildDecisions == 0 ? 0 : (double) stats.buildCandidates / stats.buildDecisions);
        meta.put("turns", stats.turns);
        meta.put("msPerTurnFeatures", stats.turns == 0 ? 0 : stats.featureNanos / 1e6 / stats.turns);
        meta.put("msPerTurnPrepare", stats.turns == 0 ? 0 : stats.prepareNanos / 1e6 / stats.turns);
        meta.put("msPerTurnSabotageCandidates", stats.turns == 0 ? 0 : stats.sabotageNanos / 1e6 / stats.turns);
        meta.put("msPerTurnBuildCandidates", stats.turns == 0 ? 0 : stats.buildNanos / 1e6 / stats.turns);
        meta.put("gameList", gameMeta);
        Files.writeString(out.resolve("meta.json"), new GsonBuilder().setPrettyPrinting().create().toJson(meta));
        System.out.printf("games %d, turns %d, sabotage decisions %d (no target %d, %.1f candidates), build decisions %d "
                        + "(label outside candidates %d, %.1f candidates), features %.2f ms/turn "
                        + "(prepare %.2f, sabotage %.2f, build %.2f) -> %s%n",
                gameMeta.size(), stats.turns, stats.sabotageDecisions, stats.sabotageNoTarget,
                (double) stats.sabotageCandidates / Math.max(1, stats.sabotageDecisions), stats.buildDecisions,
                stats.buildMissed, (double) stats.buildCandidates / Math.max(1, stats.buildDecisions),
                stats.featureNanos / 1e6 / Math.max(1, stats.turns), stats.prepareNanos / 1e6 / Math.max(1, stats.turns),
                stats.sabotageNanos / 1e6 / Math.max(1, stats.turns), stats.buildNanos / 1e6 / Math.max(1, stats.turns), out);
    }

    static final class Stats {
        long turns, sabotageDecisions, sabotageNoTarget, sabotageCandidates, buildDecisions, buildMissed, buildCandidates;
        long featureNanos, prepareNanos, sabotageNanos, buildNanos;
    }

    static final class Exporter {
        final CgReplay r;
        final int me, ordinal, negatives;
        final boolean holdout;
        final Random random;
        final Stats stats;
        final GameState s, work;
        final Pathing pathing;
        final NeuralFeatures f;
        final int[] regions, cells;
        final float[][] sabX, buildX;
        final int[] pick;

        Exporter(CgReplay r, int me, int ordinal, boolean holdout, int negatives, Random random, Stats stats) {
            this.r = r;
            this.me = me;
            this.ordinal = ordinal;
            this.holdout = holdout;
            this.negatives = negatives;
            this.random = random;
            this.stats = stats;
            s = r.toState();
            work = s.copy();
            pathing = new Pathing(s.size);
            f = new NeuralFeatures(s);
            regions = new int[s.regionCount];
            cells = new int[s.size];
            sabX = new float[s.regionCount][NeuralFeatures.SABOTAGE_F];
            buildX = new float[s.size][NeuralFeatures.BUILD_F];
            pick = new int[s.size];
        }

        void run(BinWriter sab, BinWriter sabIdx, BinWriter build, BinWriter buildIdx) throws IOException {
            Rules.updateConnections(s, pathing);
            for (int t = 0; t < r.turns(); t++) {
                String[] lines = r.turnStdout.get(t);
                @SuppressWarnings("unchecked")
                List<Action>[] acts = new List[2];
                for (int p = 0; p < 2; p++) {
                    try {
                        acts[p] = lines[p] == null || r.failure(t, p) != null
                                ? List.of(Action.waitAction()) : Action.parseLine(lines[p]);
                    } catch (RuntimeException e) {
                        acts[p] = List.of(Action.waitAction());
                    }
                }
                s.turn = t;
                long t0 = System.nanoTime();
                f.observe(s, me);
                f.prepare(s, me, true);
                // Sabotage decision on the pre-turn board.
                int target = -1;
                for (Action a : acts[me]) if (a.type == Action.Type.DISRUPT && target < 0) {
                    int region = a.disruptRegion(s);
                    if (region >= 0 && !s.regionInked[region] && !s.regionHasTown(region)) target = region;
                }
                int n = f.sabotageCandidates(s, me, regions, sabX);
                int label = -1;
                for (int i = 0; i < n; i++) if (regions[i] == target) label = i;
                if (label >= 0) {
                    writeGroup(sab, sabIdx, sabX, n, label, t, 0, NeuralFeatures.SABOTAGE_F);
                    stats.sabotageDecisions++;
                    stats.sabotageCandidates += n;
                } else {
                    stats.sabotageNoTarget++;
                }
                f.recordTarget(target);
                // Placements in stdout order, each on the board holding the earlier ones.
                Rules.TurnResult tr = new Rules.TurnResult();
                Rules.expand(s, me, acts[me], pathing, tr.moves[me], tr);
                Rules.PlayerMoves moves = tr.moves[me];
                work.copyFrom(s);
                int paint = GameState.PAINT_PER_TURN;
                for (int k = 0; k < moves.count; k++) {
                    int c = moves.cells[k];
                    if (k > 0) f.prepare(work, me, false);
                    int m = f.buildCandidates(work, me, paint, target, k, cells, buildX);
                    int lab = -1;
                    for (int i = 0; i < m; i++) if (cells[i] == c) lab = i;
                    if (lab >= 0) {
                        writeGroup(build, buildIdx, buildX, m, lab, t, k, NeuralFeatures.BUILD_F);
                        stats.buildDecisions++;
                        stats.buildCandidates += m;
                    } else {
                        stats.buildMissed++;
                    }
                    paint -= work.placeCost(c);
                    work.owner[c] = me;
                    f.markPlaced(c);
                    Rules.updateConnections(work, pathing);
                }
                stats.featureNanos += System.nanoTime() - t0;
                stats.turns++;
                Rules.resolve(s, acts[0], acts[1], pathing);
            }
            stats.prepareNanos += f.prepareNanos;
            stats.sabotageNanos += f.sabotageNanos;
            stats.buildNanos += f.buildNanos;
        }

        void writeGroup(BinWriter rows, BinWriter idx, float[][] x, int n, int label, int turn, int k, int width) throws IOException {
            int count;
            if (negatives <= 0 || n <= negatives + 1) {
                for (int i = 0; i < n; i++) pick[i] = i;
                count = n;
            } else {
                // Sampled softmax: keep the chosen row plus `negatives` distinct others.
                for (int i = 0; i < n; i++) pick[i] = i;
                pick[label] = pick[0];
                pick[0] = label;
                for (int i = 1; i <= negatives; i++) {
                    int j = i + random.nextInt(n - i);
                    int tmp = pick[i];
                    pick[i] = pick[j];
                    pick[j] = tmp;
                }
                count = negatives + 1;
            }
            long start = rows.rows;
            int labelOffset = -1;
            for (int i = 0; i < count; i++) {
                if (pick[i] == label) labelOffset = i;
                rows.writeRow(x[pick[i]], width);
            }
            idx.writeInts(ordinal, turn, k, (int) start, count, labelOffset, holdout ? 1 : 0);
        }
    }

    /** Buffered little-endian binary writer. */
    static final class BinWriter implements AutoCloseable {
        private final FileChannel channel;
        private final ByteBuffer buf = ByteBuffer.allocate(1 << 20).order(ByteOrder.LITTLE_ENDIAN);
        long rows;

        BinWriter(Path path) throws IOException {
            channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        }

        void writeRow(float[] x, int width) throws IOException {
            if (buf.remaining() < width * 4) flush();
            for (int i = 0; i < width; i++) buf.putFloat(x[i]);
            rows++;
        }

        void writeInts(int... values) throws IOException {
            if (buf.remaining() < values.length * 4) flush();
            for (int v : values) buf.putInt(v);
            rows++;
        }

        private void flush() throws IOException {
            buf.flip();
            while (buf.hasRemaining()) channel.write(buf);
            buf.clear();
        }

        @Override
        public void close() throws IOException {
            flush();
            channel.close();
        }
    }
}
