package btk.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CgReplaySeedTest {

    @Test
    void parsesPropertiesWithoutLosingSignedLongPrecision() {
        CgReplay replay = new CgReplay();
        for (long value : new long[]{-176203007668178900L, Long.MIN_VALUE, Long.MAX_VALUE, 0}) {
            replay.seed = "# CG referee input\r\nother=value\r\nseed = " + value + "\r\n";
            assertEquals(value, replay.mapSeed().orElseThrow());
        }
    }

    @Test
    void distinguishesMissingSeedFromInvalidOrOverflowedSeed() {
        CgReplay replay = new CgReplay();
        for (String input : new String[]{null, "", " \n", "other=123"}) {
            replay.seed = input;
            assertTrue(replay.mapSeed().isEmpty());
        }
        for (String input : new String[]{"seed=", "seed=invalid", "seed=9223372036854775808"}) {
            replay.seed = input;
            assertThrows(IllegalArgumentException.class, replay::mapSeed);
        }
    }
}
