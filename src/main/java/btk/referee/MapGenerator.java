package btk.referee;

import btk.core.GameState;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Authentic CodinGame map generation from the signed long in refereeInput's seed property.
 * Uses the original Java 17 GridMaker algorithm; see docs/map_generator.md for provenance.
 */
public final class MapGenerator {

    private MapGenerator() {}

    /**
     * Fresh initial state. Each call owns its RNG and generator, including parallel benchmarks.
     * Zero is rejected: SecureRandom.setSeed(0L) is a no-op, so CG cannot reproduce that seed.
     */
    public static GameState generate(long seed) {
        if (seed == 0) throw new IllegalArgumentException("CG seed 0 is not reproducible; use a nonzero seed");
        GridMaker maker = new GridMaker();
        try {
            // MultiplayerGameManager supplies SHA1PRNG through the java.util.Random interface.
            SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
            random.setSeed(seed);
            maker.init(random);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("CodinGame map generation requires SHA1PRNG", e);
        }
        return maker.make();
    }
}
