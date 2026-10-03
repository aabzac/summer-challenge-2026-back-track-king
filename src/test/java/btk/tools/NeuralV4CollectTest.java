package btk.tools;

import btk.bot.neural.NeuralFeatures;
import btk.bot.neural.NeuralNet;
import btk.bot.neural.NeuralWeights;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class NeuralV4CollectTest {
    @TempDir Path temp;

    private ByteBuffer read(String file) throws Exception {
        return ByteBuffer.wrap(Files.readAllBytes(temp.resolve(file))).order(ByteOrder.LITTLE_ENDIAN);
    }

    @Test void headTemperatureChangesSamplingAndStoredLogpButKeepsRawTeacherAndMaskedRows() throws Exception {
        float[][] sx = rows(NeuralFeatures.SABOTAGE_F), bx = rows(NeuralFeatures.BUILD_F);
        int[] allowed = {2, 0};
        double[] logits = {1001, 1000};
        // CDF(0) is 0.731 at T=1 and 0.881 at T=0.5. The same draw must switch action.
        Random draw = new Random() { @Override public double nextDouble() { return .8; } };
        try (var sab = new NeuralV4Collect.HeadWriter(temp, "sabotage", NeuralFeatures.SABOTAGE_F);
             var build = new NeuralV4Collect.HeadWriter(temp, "build", NeuralFeatures.BUILD_F)) {
            var chooser = new NeuralV4Collect.SoftmaxChooser(7, 1, .5, draw, sab, build);
            assertEquals(1, chooser.choose(true, 12, 0, sx, allowed, logits, 2));
            assertEquals(0, chooser.choose(false, 12, 2, bx, allowed, logits, 2));
            assertEquals(2, sab.rows); assertEquals(1, sab.decisions);
            assertEquals(2, build.rows); assertEquals(1, build.decisions);
            assertThrows(IllegalStateException.class, () -> chooser.choose(false, 12, 2, bx, allowed, logits, 1));
            assertThrows(IllegalStateException.class, () -> chooser.choose(false, 12, 2, bx, allowed,
                    new double[]{0, Double.NaN}, 2));
        }
        verifyExport("sabotage", sx, allowed, 0, 1,
                new NeuralNet(NeuralWeights.SABOTAGE_SIZES, NeuralWeights.SABOTAGE));
        verifyExport("build", bx, allowed, 2, 0,
                new NeuralNet(NeuralWeights.BUILD_SIZES, NeuralWeights.BUILD));
        assertEquals(-1 - Math.log1p(Math.exp(-1)), read("sabotage.logp").getFloat(), 1e-6);
        assertEquals(-Math.log1p(Math.exp(-2)), read("build.logp").getFloat(), 1e-6);
    }

    @Test void invalidHeadTemperatureFailsBeforeCreatingOutput() {
        for (String invalid : new String[]{"0", "-1", "NaN", "Infinity"}) {
            for (int head = 0; head < 2; head++) {
                Path out = temp.resolve("invalid-" + invalid + "-" + head);
                String[] args = {"2", "3100000", "missing.json", out.toString(), "1", "0.5"};
                args[4 + head] = invalid;
                assertThrows(IllegalArgumentException.class, () -> NeuralV4Collect.main(args));
                assertFalse(Files.exists(out));
            }
        }
    }

    @Test void existingOutputIsNeverOverwritten() throws Exception {
        Path out = temp.resolve("existing"); Files.createDirectory(out);
        Path marker = out.resolve("keep.txt"); Files.writeString(marker, "unchanged");
        assertThrows(IllegalStateException.class, () -> NeuralV4Collect.main(
                new String[]{"2", "3100000", "missing.json", out.toString(), "1", "0.5"}));
        assertEquals("unchanged", Files.readString(marker));
    }

    private static float[][] rows(int width) {
        float[][] x = new float[3][width];
        for (int row = 0; row < x.length; row++) for (int j = 0; j < width; j++) x[row][j] = row + .001f * j;
        return x;
    }

    private void verifyExport(String head, float[][] rows, int[] allowed, int k, int chosen, NeuralNet teacherNet)
            throws Exception {
        var idx = read(head + ".idx");
        for (int expected : new int[]{7, 12, k, 0, 2, chosen}) assertEquals(expected, idx.getInt());
        assertFalse(idx.hasRemaining());
        var features = read(head + ".bin");
        for (int row : allowed) for (float expected : rows[row]) assertEquals(expected, features.getFloat());
        assertFalse(features.hasRemaining());
        var teacher = read(head + ".teacher");
        for (int row : allowed) assertEquals(teacherNet.score(rows[row]), teacher.getFloat());
        assertFalse(teacher.hasRemaining());
    }
}
