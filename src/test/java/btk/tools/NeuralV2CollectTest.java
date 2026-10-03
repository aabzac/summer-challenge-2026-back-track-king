package btk.tools;

import btk.bot.neural.NeuralFeatures;
import btk.bot.neural.NeuralNet;
import btk.bot.neural.NeuralWeights;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class NeuralV2CollectTest {
    @TempDir Path temp;

    private ByteBuffer read(String file) throws Exception {
        return ByteBuffer.wrap(Files.readAllBytes(temp.resolve(file))).order(ByteOrder.LITTLE_ENDIAN);
    }

    @Test void exportsExactMaskedRowsTeacherLogitsAndStableBehaviorLogProbability() throws Exception {
        float[][] x = new float[3][NeuralFeatures.BUILD_F];
        for (int row = 0; row < x.length; row++) for (int j = 0; j < x[row].length; j++) x[row][j] = row + .001f * j;
        int[] allowed = {2, 0};
        double[] logits = {1001, 1000};
        int chosen;
        try (var sab = new NeuralV2Collect.HeadWriter(temp, "sabotage", NeuralFeatures.SABOTAGE_F);
             var build = new NeuralV2Collect.HeadWriter(temp, "build", NeuralFeatures.BUILD_F)) {
            var chooser = new NeuralV2Collect.SoftmaxChooser(7, 1, new Random(91), sab, build);
            chosen = chooser.choose(false, 12, 2, x, allowed, logits, 2);
            assertThrows(IllegalStateException.class, () -> chooser.choose(false, 12, 2, x, allowed, logits, 1));
            assertEquals(2, build.rows); assertEquals(1, build.decisions);
        }
        assertTrue(chosen == 0 || chosen == 1);
        var idx = read("build.idx");
        for (int expected : new int[]{7, 12, 2, 0, 2, chosen}) assertEquals(expected, idx.getInt());
        assertFalse(idx.hasRemaining());
        var features = read("build.bin");
        for (int selectedRow : allowed) for (float expected : x[selectedRow]) assertEquals(expected, features.getFloat());
        assertFalse(features.hasRemaining());
        NeuralNet prior = new NeuralNet(NeuralWeights.BUILD_SIZES, NeuralWeights.BUILD);
        var teacher = read("build.teacher");
        for (int selectedRow : allowed) assertEquals(prior.score(x[selectedRow]), teacher.getFloat());
        assertFalse(teacher.hasRemaining());
        assertEquals(logits[chosen] - 1001 - Math.log(1 + Math.exp(-1)), read("build.logp").getFloat(), 1e-6);
        assertEquals(0, Files.size(temp.resolve("sabotage.idx")));
    }

    @Test void invalidPlanAndExistingOutputFailWithoutClobbering() throws Exception {
        Path output = temp.resolve("existing"); Files.createDirectory(output);
        Path sentinel = output.resolve("keep.txt"); Files.writeString(sentinel, "unchanged");
        assertThrows(IllegalStateException.class, () -> NeuralV2Collect.main(
                new String[]{"2", "2200000", "missing.json", output.toString(), "1"}));
        assertEquals("unchanged", Files.readString(sentinel));
        assertThrows(IllegalArgumentException.class, () -> NeuralV2Collect.main(
                new String[]{"3", "2200000", "missing.json", temp.resolve("odd").toString(), "1"}));
        assertFalse(Files.exists(temp.resolve("odd")));
    }

    @Test void wrongWeightFeatureWidthIsRejected() {
        var json = JsonParser.parseString("{\"build\":{\"sizes\":[2,1],\"base64\":\"\"}}").getAsJsonObject();
        assertThrows(IllegalArgumentException.class, () -> NeuralV2Collect.readHead(json, "build", NeuralFeatures.BUILD_F));
    }
}
