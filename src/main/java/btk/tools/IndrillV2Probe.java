package btk.tools;

import btk.bot.Bot;
import btk.bot.BotFactory;
import btk.bot.Params;
import btk.bot.current.IndrillV2Bot;
import btk.bot.current.IndrillV2Net;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Protocol;
import btk.core.Rules;
import btk.referee.MapGenerator;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** Fixed training-map exclusions and float32 Java/Python inference observations; local only. */
public final class IndrillV2Probe {
    private static final Gson JSON = new Gson();
    private static final long[] TRAINING_BASES = {2000000, 2001000, 2002000, 2003000, 2100000, 2101000, 2102000, 2103000, 2104000, 2105000};
    private static final long[] PROBE_SEEDS = {2109003, 2109004, 2109005};
    private static final int[] PROBE_TURNS = {0, 12, 25};
    private static final Path EXCLUSIONS = Path.of("build/bench/indrill-q-v2-training-excluded.json");
    private static final Path PROBE_EXCLUSIONS = Path.of("build/bench/indrill-q-v2-probe-excluded.json");

    private IndrillV2Probe() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("weights-binary output-directory");
        Path weights = Path.of(args[0]), output = Path.of(args[1]);
        if (Files.exists(output) || Files.exists(EXCLUSIONS) || Files.exists(PROBE_EXCLUSIONS))
            throw new IllegalStateException("Preserve existing probe and training-exclusion evidence");
        float[][] tensors = IndrillV2Collect.readWeights(weights);
        new IndrillV2Net(tensors); // Validate finite weights and model shape before writing evidence.
        String weightHash = hash(Files.readAllBytes(weights));
        Map<String, Object> exclusions = trainingExclusions();
        Files.createDirectories(EXCLUSIONS.toAbsolutePath().getParent());
        Files.writeString(EXCLUSIONS, new GsonBuilder().setPrettyPrinting().create().toJson(exclusions) + "\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        Files.createDirectories(output);
        Path probe = output.resolve("probe.jsonl");
        var probeMaps = new TreeMap<String, Integer>();
        int rows = 0;
        try (BufferedWriter file = Files.newBufferedWriter(probe, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW)) {
            for (long seed : PROBE_SEEDS) {
                GameState state = MapGenerator.generate(seed);
                String mapHash = hash(Protocol.initBlock(state, 0).getBytes(StandardCharsets.UTF_8));
                if (probeMaps.putIfAbsent(mapHash, 1) != null)
                    throw new IllegalStateException("Duplicate registered probe map content");
                Pathing pathing = new Pathing(state.size);
                IndrillV2Bot[] bots = {new IndrillV2Bot(new Params(), new IndrillV2Net(tensors)),
                        new IndrillV2Bot(new Params(), new IndrillV2Net(tensors))};
                bots[0].init(state, 0);
                bots[1].init(state, 1);
                Bot opponent = BotFactory.create("neural-v1");
                opponent.init(state, 1);
                int sample = 0;
                while (sample < PROBE_TURNS.length) {
                    if (state.turn == PROBE_TURNS[sample]) {
                        for (int player = 0; player < 2; player++) {
                            IndrillV2Bot bot = bots[player];
                            bot.decide(state, player);
                            file.write(JSON.toJson(observation(seed, state, player, bot, weightHash)));
                            file.newLine();
                            rows++;
                        }
                        file.flush();
                        sample++;
                        if (sample == PROBE_TURNS.length) break;
                    }
                    if (Rules.gameOver(state, pathing))
                        throw new IllegalStateException("Fixed probe game closed before its registered snapshot: " + seed);
                    var ours = bots[0].play(state, 0, 60000);
                    var theirs = opponent.play(state, 1, 60000);
                    var result = Rules.resolve(state, ours, theirs, pathing);
                    if (result.hasError(0) || result.hasError(1) || !result.moves[0].skipped.isEmpty()
                            || !result.moves[1].skipped.isEmpty())
                        throw new IllegalStateException("Illegal probe trajectory at seed " + seed + " turn " + state.turn);
                }
            }
        }
        var manifest = new LinkedHashMap<String, Object>();
        Files.writeString(PROBE_EXCLUSIONS, new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                "schemaVersion", 1, "diagnosticOnly", true, "gamesPerMap", probeMaps,
                "seeds", PROBE_SEEDS, "hashScheme", "sha256-init-block-player-0",
                "source", "Fixed numerical inference parity trajectories; exclude from later strength pools")) + "\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        manifest.put("schemaVersion", 1);
        manifest.put("weights", weights.toString());
        manifest.put("weightsSha256", weightHash);
        manifest.put("probeSha256", hash(Files.readAllBytes(probe)));
        manifest.put("exclusionSha256", hash(Files.readAllBytes(EXCLUSIONS)));
        manifest.put("probeExclusionSha256", hash(Files.readAllBytes(PROBE_EXCLUSIONS)));
        manifest.put("probeSeeds", PROBE_SEEDS);
        manifest.put("probeTurns", PROBE_TURNS);
        manifest.put("rows", rows);
        manifest.put("complete", rows == PROBE_SEEDS.length * PROBE_TURNS.length * 2);
        manifest.put("trajectory", "Indrill binary-loaded policy P0 against frozen neural-v1 P1; both-player Q observations");
        Files.writeString(output.resolve("manifest.json"), new GsonBuilder().setPrettyPrinting().create().toJson(manifest) + "\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        System.out.println("Registered 960 training maps for exclusion; wrote " + rows + " float32 inference observations to " + probe);
    }

    private static Map<String, Object> trainingExclusions() throws Exception {
        var hashes = new TreeMap<String, Integer>();
        var maps = new ArrayList<Object>();
        var blocks = new ArrayList<Object>();
        for (long base : TRAINING_BASES) {
            int blockGames = base < 2100000 ? 48 : 128;
            blocks.add(Map.of("seedStart", base, "games", blockGames));
            for (int game = 0; game < blockGames; game++) {
                long seed = base + game;
                GameState state = MapGenerator.generate(seed);
                String identity = hash(Protocol.initBlock(state, 0).getBytes(StandardCharsets.UTF_8));
                if (hashes.putIfAbsent(identity, 1) != null)
                    throw new IllegalStateException("Duplicate registered training-map content");
                maps.add(Map.of("seed", seed, "mapHash", identity));
            }
        }
        return Map.of("schemaVersion", 1, "trainingOnly", true, "gamesPerMap", hashes, "maps", maps,
                "seedBlocks", blocks, "generator", "cg-sha1prng", "hashScheme", "sha256-init-block-player-0",
                "source", "Fixed v2 training schedule: retained 192 plus six new 128-game blocks");
    }

    private static Map<String, Object> observation(long seed, GameState state, int player,
                                                  IndrillV2Bot bot, String weightHash) {
        var row = new LinkedHashMap<String, Object>();
        row.put("schemaVersion", 1);
        row.put("weightsSha256", weightHash);
        row.put("seed", seed);
        row.put("turn", state.turn);
        row.put("player", player);
        row.put("width", state.width);
        row.put("height", state.height);
        row.put("size", state.size);
        row.put("regionCount", state.regionCount);
        row.put("regions", state.regionId);
        int[] costs = new int[state.size];
        boolean[] legalBuild = new boolean[state.size];
        for (int cell = 0; cell < state.size; cell++) {
            costs[cell] = state.placeCost(cell);
            legalBuild[cell] = bot.features.buildCost[cell] > 0;
        }
        row.put("costs", costs);
        row.put("legalBuild", legalBuild);
        row.put("buildCost", bot.features.buildCost);
        row.put("legalInk", bot.features.legalInk);
        row.put("neighbors", bot.features.neighbors);
        row.put("regionSizes", bot.features.regionSizes);
        row.put("features", bot.features.x);
        row.put("stateValue", bot.stateValue);
        row.put("buildQ", bot.buildQ);
        row.put("inkQ", bot.inkQ);
        row.put("selectedCells", Arrays.copyOf(bot.selectedCells, bot.selectedCount));
        row.put("selectedInk", bot.selectedInk);
        return row;
    }

    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
