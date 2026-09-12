package btk.tools;

import btk.core.GameState;
import btk.referee.MapGenerator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Checks seed-generated maps against independent viewer data from downloaded CG replays. */
public final class CgMapValidate {

    public static void main(String[] args) throws Exception {
        Path path = Path.of(args.length > 0 && !args[0].isEmpty() ? args[0] : "replays/cg");
        List<Path> files = Files.isDirectory(path) ? CgReplay.listReplays(path) : List.of(path);
        int ok = 0;
        for (Path file : files) {
            try {
                CgReplay replay = CgReplay.load(file);
                long seed = replay.mapSeed().orElseThrow(() -> new IllegalArgumentException("no refereeInput seed"));
                String difference = difference(replay.toState(), MapGenerator.generate(seed));
                if (difference == null) ok++;
                else System.out.println(file.getFileName() + " seed=" + seed + ": " + difference);
            } catch (RuntimeException e) {
                System.out.println(file.getFileName() + ": " + e);
            }
        }
        System.out.printf("%d/%d maps reproduced exactly from refereeInput seed%n", ok, files.size());
        if (files.isEmpty() || ok != files.size()) throw new IllegalStateException("CG map validation failed");
    }

    /** First difference, or null when all static map data match, including IDs and list order. */
    static String difference(GameState expected, GameState actual) {
        if (expected.width != actual.width || expected.height != actual.height) {
            return "dimensions CG=" + expected.width + "x" + expected.height
                    + " generated=" + actual.width + "x" + actual.height;
        }
        for (int c = 0; c < expected.size; c++) {
            if (expected.regionId[c] != actual.regionId[c] || expected.terrain[c] != actual.terrain[c]) {
                return "cell " + expected.x(c) + "," + expected.y(c)
                        + " region/terrain CG=" + expected.regionId[c] + "/" + expected.terrain[c]
                        + " generated=" + actual.regionId[c] + "/" + actual.terrain[c];
            }
        }
        if (expected.townCount != actual.townCount) {
            return "town count CG=" + expected.townCount + " generated=" + actual.townCount;
        }
        for (int t = 0; t < expected.townCount; t++) {
            if (expected.townX[t] != actual.townX[t] || expected.townY[t] != actual.townY[t]) {
                return "town " + t + " position CG=" + expected.townX[t] + "," + expected.townY[t]
                        + " generated=" + actual.townX[t] + "," + actual.townY[t];
            }
            if (!Arrays.equals(expected.desired[t], actual.desired[t])) {
                return "town " + t + " desired CG=" + Arrays.toString(expected.desired[t])
                        + " generated=" + Arrays.toString(actual.desired[t]);
            }
        }
        return null;
    }
}
