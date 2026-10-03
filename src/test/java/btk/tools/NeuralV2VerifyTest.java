package btk.tools;

import btk.bot.neural.NeuralNet;
import btk.bot.neural.NeuralV2Weights;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NeuralV2VerifyTest {
    @TempDir Path temp;

    @Test void parityReadsAllCandidatesOfFirst32GroupsAndRequiresCompiledWeights() throws Exception {
        Path weights = fixture();
        var result = JsonParser.parseString(new Gson().toJson(NeuralV2Verify.parity(temp, weights))).getAsJsonObject();
        assertTrue(result.get("compiledGeneratedWeightsMatch").getAsBoolean());
        var cases = result.getAsJsonArray("cases");
        assertEquals(64, cases.size());
        for (int i = 0; i < cases.size(); i++) {
            var sample = cases.get(i).getAsJsonObject();
            int group = i % 32;
            int width = i < 32 ? NeuralV2Weights.SABOTAGE_SIZES[0] : NeuralV2Weights.BUILD_SIZES[0];
            NeuralNet net = i < 32
                    ? new NeuralNet(NeuralV2Weights.SABOTAGE_SIZES, NeuralV2Weights.SABOTAGE)
                    : new NeuralNet(NeuralV2Weights.BUILD_SIZES, NeuralV2Weights.BUILD);
            assertEquals(group * 3, sample.get("rowStart").getAsInt());
            assertEquals(3, sample.getAsJsonArray("scores").size());
            int argmax = 0;
            float highest = Float.NEGATIVE_INFINITY;
            for (int c = 0; c < 3; c++) {
                float[] row = new float[width];
                row[0] = group * 3 + c;
                float score = net.score(row);
                assertEquals(score, sample.getAsJsonArray("scores").get(c).getAsFloat());
                if (score > highest) { highest = score; argmax = c; }
            }
            assertEquals(argmax, sample.get("argmax").getAsInt());
        }
        var invalid = JsonParser.parseString(Files.readString(weights)).getAsJsonObject();
        invalid.getAsJsonObject("build").addProperty("base64", "");
        Files.writeString(weights, invalid.toString());
        assertThrows(IllegalStateException.class, () -> NeuralV2Verify.parity(temp, weights));
    }

    @Test void evidenceIsWriteOnceAndMalformedInputProducesNoOutput() throws Exception {
        Path weights = fixture(), out = temp.resolve("parity.json");
        String[] args = {"parity", temp.toString(), weights.toString(), out.toString()};
        NeuralV2Verify.main(args);
        String original = Files.readString(out);
        assertThrows(IllegalStateException.class, () -> NeuralV2Verify.main(args));
        assertEquals(original, Files.readString(out));
        Files.write(temp.resolve("build.idx"), new byte[1]);
        Path invalidOut = temp.resolve("invalid.json");
        args[3] = invalidOut.toString();
        assertThrows(java.io.IOException.class, () -> NeuralV2Verify.main(args));
        assertFalse(Files.exists(invalidOut));
        assertThrows(java.io.IOException.class, () -> NeuralV2Verify.maps(temp.resolve("missing.json").toString()));
    }

    private Path fixture() throws Exception {
        var heads = new LinkedHashMap<String, Object>();
        for (String head : new String[]{"sabotage", "build"}) {
            int[] sizes = head.equals("sabotage") ? NeuralV2Weights.SABOTAGE_SIZES : NeuralV2Weights.BUILD_SIZES;
            String blob = head.equals("sabotage") ? NeuralV2Weights.SABOTAGE : NeuralV2Weights.BUILD;
            heads.put(head, Map.of("sizes", sizes, "base64", blob));
            ByteBuffer rows = ByteBuffer.allocate(33 * 3 * sizes[0] * 4).order(ByteOrder.LITTLE_ENDIAN);
            ByteBuffer idx = ByteBuffer.allocate(33 * 24).order(ByteOrder.LITTLE_ENDIAN);
            for (int g = 0; g < 33; g++) {
                idx.putInt(0).putInt(g).putInt(0).putInt(g * 3).putInt(3).putInt(1);
                for (int c = 0; c < 3; c++) {
                    rows.putFloat(g * 3 + c);
                    for (int f = 1; f < sizes[0]; f++) rows.putFloat(0);
                }
            }
            Files.write(temp.resolve(head + ".idx"), idx.array());
            Files.write(temp.resolve(head + ".bin"), rows.array());
        }
        Path weights = temp.resolve("weights.json");
        Files.writeString(weights, new Gson().toJson(heads));
        return weights;
    }
}
