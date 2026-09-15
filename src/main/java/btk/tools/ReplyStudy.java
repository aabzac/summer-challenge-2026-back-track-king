package btk.tools;

import btk.bot.BotCatalog;
import btk.bot.BotFactory;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Protocol;
import btk.core.Rules;
import btk.referee.MapGenerator;
import btk.referee.Referee;
import btk.referee.Replay;
import btk.runner.TimingProfile;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fixed development capture and separate shared-state diagnosis. Not a strength/promotion test. */
public final class ReplyStudy {
    static final Gson JSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    static final int[] TURNS = {10, 20, 30, 40, 50};
    record Captured(long seed, int me, String policy, String init, Replay replay) {}
    record Entry(String file, String sha256) {}
    static final class Manifest {
        int schemaVersion = 1;
        boolean complete;
        String createdAt = Instant.now().toString(), javaVersion = System.getProperty("java.version");
        long firstSearchMs = TimingProfile.CG.firstSearchMs, laterSearchMs = TimingProfile.CG.turnSearchMs;
        BotCatalog.Identity candidate = BotCatalog.describe(ReplyProbe.SPEC);
        Map<String, Map<String, String>> configurations = Map.of("v05", BotFactory.configuration("v05"),
                ReplyProbe.SPEC, BotFactory.configuration(ReplyProbe.SPEC), "pressure-v1", BotFactory.configuration("pressure-v1"));
        List<Entry> games = new ArrayList<>();
    }
    record Position(String game, long seed, int me, String policy, GameState state, String own, String opponent) {}
    record Sample(String game, long seed, int me, String policy, int turn, int height, String stateSha256,
                  String recordedOwn, String recordedOpponent, ReplyProbe.Inspection inspection) {}
    static final class Summary {
        int positions, noCandidate, candidateStates, twoChoiceStates, actualBuilds, predictedBuilds;
        int placementExact, placementExactOnBuild, predictedCells, actualCells, intersectingCells;
        int predictedBuildOnNoBuild, actualDisrupts, disruptExact, missedDisrupt;
        int knownRankingDiffers, oracleImprovesChoice, oracleRescuesInkedChoice, oracleVsImmediateConflict;
        int nominalActionDiffers, nominalPrefixDiffers;
        double nominalMs, nominalMaxMs, ampleMs, ampleMaxMs;

        void add(ReplyProbe.Inspection row) {
            positions++;
            nominalMs += row.nominal().elapsedMs(); nominalMaxMs = Math.max(nominalMaxMs, row.nominal().elapsedMs());
            ampleMs += row.ample().elapsedMs(); ampleMaxMs = Math.max(ampleMaxMs, row.ample().elapsedMs());
            if (!ReplyProbe.sameCells(row.nominal().accepted().cells(), row.ample().accepted().cells())
                    || row.nominal().accepted().disrupt() != row.ample().accepted().disrupt()) nominalActionDiffers++;
            if (row.choices().isEmpty()) { noCandidate++; return; }
            candidateStates++;
            if (row.choices().size() == 2) twoChoiceStates++;
            if (!row.nominalPrefixMatches()) nominalPrefixDiffers++;
            int[] predicted = row.predicted().cells(), actual = row.actual().cells();
            if (actual.length > 0) actualBuilds++;
            if (predicted.length > 0) predictedBuilds++;
            if (predicted.length > 0 && actual.length == 0) predictedBuildOnNoBuild++;
            if (ReplyProbe.sameCells(predicted, actual)) {
                placementExact++; if (actual.length > 0) placementExactOnBuild++;
            }
            predictedCells += predicted.length; actualCells += actual.length;
            for (int cell : predicted) for (int other : actual) if (cell == other) intersectingCells++;
            if (row.actual().disrupt() >= 0) {
                actualDisrupts++;
                if (row.predicted().disrupt() == row.actual().disrupt()) disruptExact++;
                if (row.predicted().disrupt() < 0) missedDisrupt++;
            }
            ReplyProbe.Value chosen = row.choices().get(row.selected()).oracle();
            ReplyProbe.Value best = row.choices().get(row.oracleSelected()).oracle();
            if (ReplyProbe.number(best) > ReplyProbe.number(chosen) + 1e-9) {
                oracleImprovesChoice++;
                if (chosen.heuristic() == null) oracleRescuesInkedChoice++;
            }
            if (row.choices().get(row.immediateSelected()).oracle().immediateDelta() > best.immediateDelta()) {
                oracleVsImmediateConflict++;
            }
            var known = row.choices().stream().map(ReplyProbe.Choice::known).toArray(ReplyProbe.Value[]::new);
            if (ReplyProbe.number(known[ReplyProbe.best(known)]) > ReplyProbe.number(known[row.selected()]) + 1e-9) {
                knownRankingDiffers++;
            }
        }
    }

    static void capture(Path directory) throws Exception {
        if (Files.exists(directory)) throw new IllegalArgumentException("Capture requires a new directory: " + directory);
        Manifest manifest = new Manifest(); // verifies engine/configurations before writing
        Files.createDirectories(directory);
        saveManifest(directory, manifest);
        for (long seed = 20000; seed <= 20005; seed++) for (int me = 0; me < 2; me++) {
            for (String policy : List.of("v05", ReplyProbe.SPEC)) {
                GameState initial = MapGenerator.generate(seed);
                String init = Protocol.initBlock(initial, 0);
                Referee referee = new Referee(); TimingProfile.CG.apply(referee);
                String p0 = me == 0 ? policy : "pressure-v1", p1 = me == 1 ? policy : "pressure-v1";
                Referee.Result result = referee.play(initial, BotFactory.create(p0), BotFactory.create(p1), seed, true);
                result.replay.describeLocalBots(p0, p1);
                Captured game = new Captured(seed, me, policy, init, result.replay);
                String file = "game-%02d.json".formatted(manifest.games.size());
                byte[] bytes = JSON.toJson(game).getBytes(StandardCharsets.UTF_8);
                Files.write(directory.resolve(file), bytes, StandardOpenOption.CREATE_NEW);
                manifest.games.add(new Entry(file, hash(bytes)));
                saveManifest(directory, manifest);
                System.out.printf("Captured %d/24 seed=%d P%d %s turns=%d%n", manifest.games.size(), seed, me, policy, result.turns);
                if (result.error[0] != null || result.error[1] != null) throw new IllegalStateException("Capture contains a bot error; evidence saved");
            }
        }
        manifest.complete = true; saveManifest(directory, manifest);
    }

    private static void saveManifest(Path directory, Manifest manifest) throws Exception {
        // This new capture owns the manifest; each update preserves previously captured game files.
        Files.writeString(directory.resolve("manifest.json"), JSON.toJson(manifest), StandardCharsets.UTF_8);
    }

    /** Rebuild all states and validate saved scores/ownership/ink before any probe sees a position. */
    static List<Position> reconstruct(String id, Captured game, int[] turns) {
        if (game.me < 0 || game.me > 1 || game.replay == null || game.replay.turns.isEmpty()
                || game.replay.meta.errors == null || Arrays.stream(game.replay.meta.errors).anyMatch(e -> e != null)) {
            throw new IllegalArgumentException("Invalid/failed capture " + id);
        }
        GameState state = Protocol.parseInit(game.init).state;
        state.maxTurns = game.replay.meta.maxTurns;
        Pathing paths = new Pathing(state.size); Rules.updateConnections(state, paths);
        int[] expectedOwner = state.owner.clone();
        List<Position> selected = new ArrayList<>();
        for (int t = 0; t < game.replay.turns.size(); t++) {
            Replay.Turn turn = game.replay.turns.get(t);
            if (Rules.gameOver(state, paths)) throw new IllegalStateException("Actions after game end in " + id);
            if (Arrays.binarySearch(turns, t) >= 0) selected.add(new Position(id, game.seed, game.me, game.policy,
                    state.copy(), turn.actions[game.me], turn.actions[1 - game.me]));
            Rules.TurnResult result = Rules.resolve(state, Action.parseLine(turn.actions[0]),
                    Action.parseLine(turn.actions[1]), paths);
            for (var moves : result.moves) if (moves.error != null) throw new IllegalStateException("Invalid replay action");
            for (int[] change : turn.placed) expectedOwner[change[0]] = change[1];
            if (!Arrays.equals(expectedOwner, state.owner) || !Arrays.equals(turn.score, state.score)
                    || !Arrays.equals(turn.gained, result.gained)
                    || !Arrays.equals(turn.regionInstability, state.regionInstability)
                    || !Arrays.equals(turn.regionInked, state.regionInked)) {
                throw new IllegalStateException("Capture/core mismatch " + id + " t" + t);
            }
        }
        if (!Rules.gameOver(state, paths) || !Arrays.equals(game.replay.meta.score, state.score)) {
            throw new IllegalStateException("Incomplete capture " + id);
        }
        return selected;
    }

    static List<Position> loadPositions(Path directory) throws Exception {
        byte[] manifestBytes = Files.readAllBytes(directory.resolve("manifest.json"));
        Manifest manifest = JSON.fromJson(new String(manifestBytes, StandardCharsets.UTF_8), Manifest.class);
        if (manifest.schemaVersion != 1 || !manifest.complete || manifest.games.size() != 24) {
            throw new IllegalArgumentException("Expected the completed 24-game capture");
        }
        var files = new HashSet<String>();
        for (Entry entry : manifest.games) {
            if (!entry.file.matches("game-[0-9]{2}\\.json")) throw new IllegalArgumentException("Invalid capture filename");
            if (!files.add(entry.file)) throw new IllegalArgumentException("Duplicate capture file");
        }
        if (!manifest.candidate.implementationSha256().equals(BotCatalog.describe(ReplyProbe.SPEC).implementationSha256())) {
            throw new IllegalStateException("Captured engine differs from probe engine");
        }
        List<Position> positions = new ArrayList<>();
        var cohort = new HashSet<String>();
        for (Entry entry : manifest.games) {
            byte[] bytes = Files.readAllBytes(directory.resolve(entry.file));
            if (!entry.sha256.equals(hash(bytes))) throw new IllegalStateException("Capture hash mismatch: " + entry.file);
            Captured game = JSON.fromJson(new String(bytes, StandardCharsets.UTF_8), Captured.class);
            if (game.seed < 20000 || game.seed > 20005 || game.me < 0 || game.me > 1
                    || !(game.policy.equals("v05") || game.policy.equals(ReplyProbe.SPEC))
                    || !cohort.add(game.seed + ":" + game.me + ":" + game.policy)) {
                throw new IllegalArgumentException("Wrong or duplicate capture cohort entry");
            }
            if (!game.replay.meta.bots[game.me].equals(game.policy)
                    || !game.replay.meta.bots[1 - game.me].equals("pressure-v1")) {
                throw new IllegalArgumentException("Wrong captured player identity");
            }
            positions.addAll(reconstruct(entry.file, game, TURNS));
        }
        return positions;
    }

    static void analyze(Path directory, Path out) throws Exception {
        if (Files.exists(out)) throw new IllegalArgumentException("Choose a new report path: " + out);
        byte[] manifestBytes = Files.readAllBytes(directory.resolve("manifest.json"));
        List<Position> positions = loadPositions(directory);
        List<Sample> samples = new ArrayList<>();
        Summary total = new Summary(); Map<String, Summary> byPolicy = new LinkedHashMap<>();
        for (int i = 0; i < positions.size(); i++) {
            Position p = positions.get(i); GameState unchanged = p.state.copy();
            ReplyProbe.Inspection probe = ReplyProbe.inspect(p.state, p.me, Action.parseLine(p.opponent), i % 2 == 0);
            ReplyProbe.sameState(unchanged, p.state);
            samples.add(new Sample(p.game, p.seed, p.me, p.policy, p.state.turn, p.state.height,
                    stateHash(p.state), p.own, p.opponent, probe));
            total.add(probe); byPolicy.computeIfAbsent(p.policy, unused -> new Summary()).add(probe);
            if ((i + 1) % 20 == 0 || i + 1 == positions.size()) System.out.printf("Analyzed %d/%d shared states%n", i + 1, positions.size());
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schemaVersion", 1); report.put("createdAt", Instant.now().toString());
        report.put("javaVersion", System.getProperty("java.version")); report.put("corpusManifestSha256", hash(manifestBytes));
        report.put("candidate", BotCatalog.describe(ReplyProbe.SPEC)); report.put("probeParams", ReplyProbe.parameters());
        report.put("requestedTurns", TURNS); report.put("unavailableEndedPositions", 24 * TURNS.length - positions.size());
        report.put("method", "Shared captured states; NOISE=0 fresh instances; first-build candidate set only. "
                + "Known/predicted/oracle use the SAME private heuristic; own later sabotage and leftover builds excluded "
                + "from ranking. Immediate score delta is not final match utility. Full-play nominal/ample order alternates; "
                + "not historical RNG/JIT reconstruction or process timing. Prediction denominators exclude no-candidate states. "
                + "No strength/promotion inference. See docs/reply-study.md.");
        report.put("summary", total); report.put("bySourcePolicy", byPolicy); report.put("samples", samples);
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, JSON.toJson(report), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        System.out.println(JSON.toJson(total)); System.out.println("Report: " + out);
    }

    static String stateHash(GameState state) throws Exception {
        return hash((Protocol.initBlock(state, 0) + "\n" + state.turn + ":" + state.maxTurns + "\n"
                + Protocol.turnBlock(state, 0)).getBytes(StandardCharsets.UTF_8));
    }

    static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("mode captureDir reportPath");
        switch (args[0]) {
            case "capture" -> capture(Path.of(args[1]));
            case "analyze" -> analyze(Path.of(args[1]), Path.of(args[2]));
            default -> throw new IllegalArgumentException("Mode must be capture or analyze");
        }
    }
}
