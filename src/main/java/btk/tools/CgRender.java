package btk.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Replays canonical CodinGame games through {@code btk.core}, verifies exact fidelity, and writes
 * visualizer-format files under {@code replays/rendered/cg/arena} or {@code .../bot}.
 *
 * <p>Args: pathToReplayDirOrFile [outputRoot]
 */
public final class CgRender {

    private CgRender() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || args[0].isBlank()) {
            throw new IllegalArgumentException("cgRender requires -Ppath=<CG replay file or directory>");
        }
        Path path = Path.of(args[0]);
        Path outputRoot = Path.of(args.length > 1 && !args[1].isBlank()
                ? args[1] : "replays/rendered/cg");
        List<Path> files = Files.isDirectory(path) ? CgReplay.listReplays(path) : List.of(path);
        int rendered = 0;
        List<String> failures = new ArrayList<>();
        for (Path file : files) {
            CgReplay replay;
            try {
                replay = CgReplay.load(file);
            } catch (Exception e) {
                failures.add(file.getFileName() + ": cannot parse: " + e);
                continue;
            }
            CgValidate.Outcome outcome = CgValidate.validate(replay, false);
            if (outcome.firstDivergenceTurn >= 0) {
                failures.add(file.getFileName() + ": turn " + outcome.firstDivergenceTurn
                        + ": " + outcome.reason);
                continue;
            }
            Path output = outputPath(outputRoot, replay);
            outcome.replay.meta.source = replay.isBossGame() ? "cg-bot" : "cg-arena";
            outcome.replay.write(output);
            rendered++;
            System.out.println(file.getFileName() + " -> " + output);
        }
        System.out.printf("%n%d/%d CG replays rendered%n", rendered, files.size());
        if (!failures.isEmpty()) {
            System.out.println("not rendered:");
            for (String failure : failures) System.out.println("  " + failure);
        }
        if (files.isEmpty() || rendered != files.size()) {
            throw new IllegalStateException("CG replay rendering failed");
        }
    }

    static Path outputPath(Path outputRoot, CgReplay replay) {
        String category = replay.isBossGame() ? "bot" : "arena";
        return outputRoot.resolve(category).resolve(replay.gameId + ".json");
    }
}
