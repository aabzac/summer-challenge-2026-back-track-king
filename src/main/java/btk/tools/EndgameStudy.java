package btk.tools;

import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Game-end forecast study (end-forecast-v1, 2026-10-26): one row per turn of every validated replay with the
 * public-board features a bot can compute at that turn and the actual number of turns that remained. Rules and
 * reachability come from {@code btk.core}; nothing here feeds a bot. Replays are replayed through
 * {@code Rules.resolve} from the recorded actions, as in {@code CgPlayerStudy}.
 *
 * <p>Args: outCsv replayDirOrFile... (directories are walked recursively; {@code _metadata} folders skipped).
 */
public final class EndgameStudy {

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 && !args[0].isEmpty() ? args[0] : "build/analysis/endgame/turns.csv");
        Files.createDirectories(out.getParent());
        List<Path> files = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            Path p = Path.of(args[i]);
            if (Files.isDirectory(p)) {
                try (Stream<Path> st = Files.walk(p)) {
                    st.filter(f -> f.toString().endsWith(".json") && !f.toString().contains("_metadata")).sorted().forEach(files::add);
                }
            } else if (Files.exists(p)) files.add(p);
        }
        int games = 0, skipped = 0;
        try (BufferedWriter w = Files.newBufferedWriter(out)) {
            w.write("game,width,height,regions,pairs,gameTurns,turn,remaining,activePairs,possibleInactive,impossibleInactive,"
                    + "inkedRegions,unstableRegions,instabilitySum,cells0,cells1,scoreDiff\n");
            for (Path f : files) {
                CgReplay replay;
                try { replay = CgReplay.load(f); } catch (Exception e) { skipped++; continue; }
                CgValidate.Outcome validation;
                try { validation = CgValidate.validate(replay, false); } catch (Exception e) { skipped++; continue; }
                if (validation.firstDivergenceTurn >= 0) { skipped++; continue; }
                GameState state = replay.toState();
                Pathing pathing = new Pathing(state.size);
                Rules.updateConnections(state, pathing);
                int total = replay.turns();
                StringBuilder sb = new StringBuilder();
                for (int t = 0; t < total; t++) {
                    int active = 0, possible = 0, impossible = 0;
                    for (int pair = 0; pair < state.pairCount; pair++) {
                        if (state.pairActive(pair)) { active++; continue; }
                        if (pathing.reachableAvoidingInk(state, state.townCell[state.pairFrom[pair]], state.townCell[state.pairTo[pair]])) possible++;
                        else impossible++;
                    }
                    int inked = 0, unstable = 0, instab = 0;
                    for (int r = 0; r < state.regionCount; r++) {
                        if (state.regionInked[r]) inked++;
                        else if (state.regionInstability[r] > 0) { unstable++; instab += state.regionInstability[r]; }
                    }
                    int c0 = 0, c1 = 0;
                    for (int c = 0; c < state.size; c++) { if (state.owner[c] == 0) c0++; else if (state.owner[c] == 1) c1++; }
                    sb.append(replay.gameId).append(',').append(state.width).append(',').append(state.height).append(',')
                            .append(state.regionCount).append(',').append(state.pairCount).append(',').append(total).append(',')
                            .append(t).append(',').append(total - t).append(',').append(active).append(',').append(possible).append(',')
                            .append(impossible).append(',').append(inked).append(',').append(unstable).append(',').append(instab).append(',')
                            .append(c0).append(',').append(c1).append(',').append(state.score[0] - state.score[1]).append('\n');
                    List<Action> a0 = CgPlayerStudy.effectiveActions(replay, t, 0), a1 = CgPlayerStudy.effectiveActions(replay, t, 1);
                    Rules.resolve(state, a0, a1, pathing);
                }
                w.write(sb.toString());
                games++;
            }
        }
        System.out.println("games: " + games + " (skipped " + skipped + ") -> " + out);
    }
}
