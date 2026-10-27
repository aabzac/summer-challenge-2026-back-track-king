package btk.tools;

import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Protocol;
import btk.core.Rules;
import btk.referee.Replay;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;

/**
 * Shared-state helpers of the September reply study (positions, state hashes, JSON). The study itself
 * (capture/analyze of the retained exp-reply-rerank engine) left with the retained engine on 2026-10-27;
 * saved captures still load and replay against core.
 */
public final class ReplyStudy {
    static final Gson JSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    static final int[] TURNS = {10, 20, 30, 40, 50};
    /** Policy label of the captured candidate games (the retained engine itself is gone). */
    static final String CAPTURED_CANDIDATE = "exp-reply-rerank?BUILD_BEAM_WIDTH=2,OPP_REPLY_WIDTH=1";
    record Captured(long seed, int me, String policy, String init, Replay replay) {}
    record Entry(String file, String sha256) {}
    static final class Manifest {
        int schemaVersion;
        boolean complete;
        List<Entry> games = new ArrayList<>();
    }
    record Position(String game, long seed, int me, String policy, GameState state, String own, String opponent) {}

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
        List<Position> positions = new ArrayList<>();
        var cohort = new HashSet<String>();
        for (Entry entry : manifest.games) {
            byte[] bytes = Files.readAllBytes(directory.resolve(entry.file));
            if (!entry.sha256.equals(hash(bytes))) throw new IllegalStateException("Capture hash mismatch: " + entry.file);
            Captured game = JSON.fromJson(new String(bytes, StandardCharsets.UTF_8), Captured.class);
            if (game.seed < 20000 || game.seed > 20005 || game.me < 0 || game.me > 1
                    || !(game.policy.equals("v05") || game.policy.equals(CAPTURED_CANDIDATE))
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

    static String stateHash(GameState state) throws Exception {
        return hash((Protocol.initBlock(state, 0) + "\n" + state.turn + ":" + state.maxTurns + "\n"
                + Protocol.turnBlock(state, 0)).getBytes(StandardCharsets.UTF_8));
    }

    static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
}
