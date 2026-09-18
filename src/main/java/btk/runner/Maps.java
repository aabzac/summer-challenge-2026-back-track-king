package btk.runner;

import btk.core.GameState;
import btk.referee.MapGenerator;
import btk.tools.CgReplay;
import btk.tools.ReplayPaths;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Map source for runners: authentic CG generation by seed, or maps from downloaded CG replays. */
public final class Maps {

    private final List<GameState> pool = new ArrayList<>();
    private final List<String> names = new ArrayList<>();

    /** @param dirOrFile empty = authentic generator; directory of CG replays or a single replay file */
    public Maps(String dirOrFile) throws IOException {
        if (dirOrFile == null || dirOrFile.isEmpty()) return;
        Path p = ReplayPaths.resolve(Path.of(dirOrFile));
        List<Path> files = p.toFile().isDirectory() ? CgReplay.listReplays(p) : List.of(p);
        for (Path f : files) {
            try {
                CgReplay r = CgReplay.load(f);
                pool.add(r.toState());
                names.add(f.getFileName().toString());
            } catch (RuntimeException e) {
                throw new IOException("cannot load requested map " + f, e);
            }
        }
        if (pool.isEmpty()) throw new IOException("no CG replay maps in " + p);
    }

    public boolean fromReplays() {
        return !pool.isEmpty();
    }

    public int poolSize() {
        return pool.size();
    }

    /** Fresh initial state for game index {@code i} with seed {@code seed}. */
    public GameState get(int i, long seed) {
        if (pool.isEmpty()) return MapGenerator.generate(seed);
        return pool.get(Math.floorMod(i, pool.size())).copy();
    }

    public String name(int i, long seed) {
        if (pool.isEmpty()) return "gen:" + seed;
        return names.get(Math.floorMod(i, pool.size()));
    }
}
