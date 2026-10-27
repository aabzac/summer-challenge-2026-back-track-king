package btk.submit;

import btk.bot.Bot;
import btk.bot.BotFactory;
import btk.core.Action;
import btk.core.GameState;
import btk.referee.MapGenerator;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class FileMergerTest {
    @TempDir Path temp;

    @Test void characterLimitIsCheckedBeforePublishing() {
        assertDoesNotThrow(() -> FileMerger.checkSourceSize("a".repeat(100_000)));
        assertThrows(IllegalStateException.class, () -> FileMerger.checkSourceSize("a".repeat(100_001)));
        assertThrows(IllegalArgumentException.class, () -> FileMerger.merge("v99"));
        assertThrows(IllegalArgumentException.class, () -> FileMerger.merge("exp-income-guard"));
    }

    @Test void spatialPpoShipsOnlyItsPolicyAndPreservesExistingConfigurations() throws Exception {
        assertEquals("candidate", btk.bot.BotCatalog.describe("spatial-ppo-v1").category());
        String source = FileMerger.merge("spatial-ppo-v1");
        FileMerger.checkSourceSize(source);
        assertTrue(source.contains("static final class SpatialNet"));
        assertTrue(source.contains("static final class SpatialWeights"));
        assertTrue(source.contains("SPATIAL_RESERVE_MS=2"));
        assertFalse(source.contains("SpatialBridge"));
        assertFalse(source.contains("com.google.gson"));
        checkFrozenDecisions("spatial-ppo-v1", true);
        for (String prior : new String[]{btk.bot.LiveTargetExperimentParams.V09_ON_CURRENT, "v09", "neural-v1", "neural-v6", "indrill-q-v2"}) {
            assertFalse(FileMerger.merge(prior).contains("SPATIAL_RESERVE_MS"), prior);
            assertFalse(FileMerger.merge(prior).contains("class SpatialNet"), prior);
            assertFalse(BotFactory.configuration(prior).containsKey("SPATIAL_RESERVE_MS"), prior);
        }
    }

    @Test void spatialV2WeightVariantsShipOnlySelectedPayloadAndMatchLocalRuntime() throws Exception {
        for (String version : new String[]{"spatial-ppo-v2", "spatial-ppo-v2-init"}) {
            String source = FileMerger.merge(version);
            String expected = version.endsWith("-init") ? btk.bot.neural.SpatialInitialWeights.DATA
                    : btk.bot.neural.SpatialV2Weights.DATA;
            var declaration = java.util.regex.Pattern.compile("String DATA\\s*=\\s*([^;]+);").matcher(source);
            assertTrue(declaration.find());
            var chunks = java.util.regex.Pattern.compile("\"([A-Za-z0-9+/=]+)\"").matcher(declaration.group(1));
            StringBuilder actual = new StringBuilder();
            while (chunks.find()) actual.append(chunks.group(1));
            assertEquals(expected, actual.toString(), version);
            assertFalse(declaration.find(), "only one generated payload may ship");
            assertTrue(source.contains("return \"" + version + "\";"));
            assertTrue(source.contains("static final class SpatialWeights"));
            assertFalse(source.contains("SpatialVariantBot"));
            assertFalse(source.contains("SpatialV2Weights"));
            assertFalse(source.contains("SpatialInitialWeights"));
            assertFalse(source.contains("SpatialBridge"));
            FileMerger.checkSourceSize(source);
            checkFrozenDecisions(version, true);
        }
    }

    @Test void spatialV2RegistrationPreservesPriorExactSubmissionBytes() throws Exception {
        assertEquals("d9507cb3e0d68de4f526cfa9da06a2b1c909182e9992f4b7a3bb6cab3d24d6d7",
                FileMerger.sha256(FileMerger.merge("spatial-ppo-v1")));
        // Measured from the pre-v2 HEAD merger; the saved default Player.java is an older export.
        assertEquals("92d508882c42c4ad799847c83eb00d356dd88ea5ee7f83320f471ad5c7c85739",
                FileMerger.sha256(FileMerger.merge(btk.bot.LiveTargetExperimentParams.V09_ON_CURRENT)));
        assertEquals("aca0be459116e78b97400ba0a0abfaa151aca7cc97db7804b8466e8cda788fac",
                FileMerger.sha256(FileMerger.merge("v09")));
        assertEquals("16c49e637b808e82686651368155ea77032848faf9b3c83007433d69d6eb02a9",
                FileMerger.sha256(FileMerger.merge("neural-v2")));
    }

    @Test void spatialV3ShipsOnlySelectedPayloadAndMatchesLocalRuntime() throws Exception {
        String source = FileMerger.merge("spatial-ppo-v3");
        var declaration = java.util.regex.Pattern.compile("String DATA\\s*=\\s*([^;]+);").matcher(source);
        assertTrue(declaration.find());
        var chunks = java.util.regex.Pattern.compile("\"([A-Za-z0-9+/=]+)\"").matcher(declaration.group(1));
        StringBuilder payload = new StringBuilder();
        while (chunks.find()) payload.append(chunks.group(1));
        assertEquals(btk.bot.neural.SpatialV3Weights.DATA, payload.toString());
        assertFalse(declaration.find(), "only one generated payload may ship");
        assertTrue(source.contains("return \"spatial-ppo-v3\";"));
        assertTrue(source.contains("static final class SpatialWeights"));
        for (String omitted : new String[]{"SpatialV3Bot", "SpatialVariantBot", "SpatialV3Weights",
                "SpatialV2Weights", "SpatialInitialWeights", "SpatialBridge"})
            assertFalse(source.contains(omitted), omitted);
        FileMerger.checkSourceSize(source);
        checkFrozenDecisions("spatial-ppo-v3", true);
    }

    @Test void spatialV3RegistrationPreservesFrozenSpatialAndSelectedArtifactBytes() throws Exception {
        assertEquals("d9507cb3e0d68de4f526cfa9da06a2b1c909182e9992f4b7a3bb6cab3d24d6d7",
                FileMerger.sha256(FileMerger.merge("spatial-ppo-v1")));
        assertEquals("a3d0fdb7e587478fc4b6f63cd64bb6f607c34b64384cd41cc091bcec375107a1",
                FileMerger.sha256(FileMerger.merge("spatial-ppo-v2")));
        assertEquals("92d508882c42c4ad799847c83eb00d356dd88ea5ee7f83320f471ad5c7c85739",
                FileMerger.sha256(FileMerger.merge(btk.bot.LiveTargetExperimentParams.V09_ON_CURRENT)));
        assertEquals("aca0be459116e78b97400ba0a0abfaa151aca7cc97db7804b8466e8cda788fac",
                FileMerger.sha256(FileMerger.merge("v09")));
    }

    @Test void spatialV4ShipsOnlySelectedPayloadAndMatchesLocalRuntime() throws Exception {
        String source = FileMerger.merge("spatial-ppo-v4");
        var declaration = java.util.regex.Pattern.compile("String DATA\\s*=\\s*([^;]+);").matcher(source);
        assertTrue(declaration.find());
        var chunks = java.util.regex.Pattern.compile("\"([A-Za-z0-9+/=]+)\"").matcher(declaration.group(1));
        StringBuilder payload = new StringBuilder();
        while (chunks.find()) payload.append(chunks.group(1));
        assertEquals(btk.bot.neural.SpatialV4Weights.DATA, payload.toString());
        assertFalse(declaration.find(), "only one generated payload may ship");
        assertTrue(source.contains("return \"spatial-ppo-v4\";"));
        assertTrue(source.contains("static final class SpatialWeights"));
        for (String omitted : new String[]{"SpatialV4Bot", "SpatialV3Bot", "SpatialVariantBot", "SpatialV4Weights",
                "SpatialV3Weights", "SpatialV2Weights", "SpatialInitialWeights", "SpatialBridge"})
            assertFalse(source.contains(omitted), omitted);
        FileMerger.checkSourceSize(source);
        checkFrozenDecisions("spatial-ppo-v4", true);
    }

    @Test void spatialV4RegistrationPreservesPriorExactArtifactBytes() throws Exception {
        assertEquals("d9507cb3e0d68de4f526cfa9da06a2b1c909182e9992f4b7a3bb6cab3d24d6d7",
                FileMerger.sha256(FileMerger.merge("spatial-ppo-v1")));
        assertEquals("a3d0fdb7e587478fc4b6f63cd64bb6f607c34b64384cd41cc091bcec375107a1",
                FileMerger.sha256(FileMerger.merge("spatial-ppo-v2")));
        assertEquals("2e873833da40b7b73c585016fc8fe0039b77d256fe60445f60eaf1b4c967dfc7",
                FileMerger.sha256(FileMerger.merge("spatial-ppo-v3")));
        assertEquals("92d508882c42c4ad799847c83eb00d356dd88ea5ee7f83320f471ad5c7c85739",
                FileMerger.sha256(FileMerger.merge(btk.bot.LiveTargetExperimentParams.V09_ON_CURRENT)));
        assertEquals("aca0be459116e78b97400ba0a0abfaa151aca7cc97db7804b8466e8cda788fac",
                FileMerger.sha256(FileMerger.merge("v09")));
    }

    @Test void spatialV5ShipsOnlySelectedPayloadAndMatchesLocalSearchAndAblation() throws Exception {
        String source = FileMerger.merge("spatial-ppo-v5");
        var declaration = java.util.regex.Pattern.compile("String DATA\\s*=\\s*([^;]+);").matcher(source);
        assertTrue(declaration.find());
        var chunks = java.util.regex.Pattern.compile("\"([A-Za-z0-9+/=]+)\"").matcher(declaration.group(1));
        StringBuilder payload = new StringBuilder();
        while (chunks.find()) payload.append(chunks.group(1));
        assertEquals(btk.bot.neural.SpatialV5Weights.DATA, payload.toString());
        assertFalse(declaration.find(), "only one generated payload may ship");
        assertTrue(source.contains("return \"spatial-ppo-v5\";"));
        assertTrue(source.contains("static final class EvalBot"));
        assertTrue(source.contains("static final class SpatialBot"), "v5 keeps the unchanged greedy delegate");
        assertTrue(source.contains("static final class SpatialWeights"));
        assertTrue(source.contains("class SearchState"));
        assertTrue(source.contains("USE_SPATIAL_V5_SEARCH=true"));
        assertTrue(source.contains("SPATIAL_V5_MIN_SEARCH_MS=12"));
        for (String omitted : new String[]{"SpatialV5Bot", "SpatialV5Weights", "SpatialV4Bot", "SpatialV4Weights",
                "SpatialV3Weights", "SpatialV2Weights", "SpatialInitialWeights", "SpatialBridge", "SpatialTeacherExport",
                "btk.tools", "btk.referee", "com.google.gson"})
            assertFalse(source.contains(omitted), omitted);
        FileMerger.checkSourceSize(source);
        checkFrozenDecisions("spatial-ppo-v5", true);
        String ablation = "spatial-ppo-v5?USE_SPATIAL_V5_SEARCH=0";
        assertTrue(FileMerger.merge(ablation).contains("USE_SPATIAL_V5_SEARCH=false"));
        checkFrozenDecisions(ablation, true);
    }

    @Test void spatialV5RegistrationAddsOnlyItsExplicitSearchConfiguration() {
        assertInstanceOf(btk.bot.neural.SpatialV5Bot.class, BotFactory.create("spatial-ppo-v5"));
        assertEquals("spatial-ppo-v5", BotFactory.create("spatial-ppo-v5").name());
        assertEquals("candidate", btk.bot.BotCatalog.describe("spatial-ppo-v5").category());
        assertFalse(new btk.bot.Params().USE_SPATIAL_V5_SEARCH);
        assertEquals(12, new btk.bot.Params().SPATIAL_V5_MIN_SEARCH_MS);
        Map<String, String> expected = new TreeMap<>(BotFactory.configuration("spatial-ppo-v4"));
        expected.put("USE_SPATIAL_V5_SEARCH", "true");
        expected.put("SPATIAL_V5_MIN_SEARCH_MS", "12");
        assertEquals(expected, BotFactory.configuration("spatial-ppo-v5"));
        var ablation = BotFactory.parameters("spatial-ppo-v5?USE_SPATIAL_V5_SEARCH=0,SPATIAL_V5_MIN_SEARCH_MS=18");
        assertFalse(ablation.USE_SPATIAL_V5_SEARCH);
        assertEquals(18, ablation.SPATIAL_V5_MIN_SEARCH_MS);
    }

    @Test void spatialV5RegistrationPreservesAllPriorSpatialAndSelectedArtifactBytes() throws Exception {
        Map<String, String> hashes = Map.of(
                "spatial-ppo-v1", "d9507cb3e0d68de4f526cfa9da06a2b1c909182e9992f4b7a3bb6cab3d24d6d7",
                "spatial-ppo-v2", "a3d0fdb7e587478fc4b6f63cd64bb6f607c34b64384cd41cc091bcec375107a1",
                "spatial-ppo-v3", "2e873833da40b7b73c585016fc8fe0039b77d256fe60445f60eaf1b4c967dfc7",
                "spatial-ppo-v4", "a961638dca644f880a12224f4e385fcfbb9efbe52d5a3744e2a0c2267276bdb2",
                btk.bot.LiveTargetExperimentParams.V09_ON_CURRENT, "92d508882c42c4ad799847c83eb00d356dd88ea5ee7f83320f471ad5c7c85739",
                "v09", "aca0be459116e78b97400ba0a0abfaa151aca7cc97db7804b8466e8cda788fac",
                "neural-v2", "16c49e637b808e82686651368155ea77032848faf9b3c83007433d69d6eb02a9");
        for (var prior : hashes.entrySet()) {
            String source = FileMerger.merge(prior.getKey());
            assertEquals(prior.getValue(), FileMerger.sha256(source), prior.getKey());
            for (String field : new String[]{"USE_SPATIAL_V5_SEARCH", "SPATIAL_V5_MIN_SEARCH_MS"}) {
                assertFalse(source.contains(field), prior.getKey() + ": " + field);
                assertFalse(BotFactory.configuration(prior.getKey()).containsKey(field), prior.getKey() + ": " + field);
            }
        }
        for (String field : new String[]{"USE_SPATIAL_V5_SEARCH", "SPATIAL_V5_MIN_SEARCH_MS"}) {
            assertFalse(FileMerger.merge("spatial-ppo-v2-init").contains(field));
            assertFalse(BotFactory.configuration("spatial-ppo-v2-init").containsKey(field));
        }
    }

    @Test void spatialV6ShipsOnlyFrozenV4PayloadAndMatchesLocalSearchAndAblation() throws Exception {
        String source = FileMerger.merge("spatial-ppo-v6");
        var declaration = java.util.regex.Pattern.compile("String DATA\\s*=\\s*([^;]+);").matcher(source);
        assertTrue(declaration.find());
        var chunks = java.util.regex.Pattern.compile("\"([A-Za-z0-9+/=]+)\"").matcher(declaration.group(1));
        StringBuilder payload = new StringBuilder();
        while (chunks.find()) payload.append(chunks.group(1));
        assertEquals(btk.bot.neural.SpatialV4Weights.DATA, payload.toString());
        assertFalse(declaration.find(), "only the frozen v4 payload may ship");
        assertTrue(source.contains("return \"spatial-ppo-v6\";"));
        assertTrue(source.contains("static final class EvalBot"));
        assertTrue(source.contains("static final class SpatialWeights"));
        assertTrue(source.contains("class SearchState"));
        assertTrue(source.contains("USE_SPATIAL_V6_SEARCH=true"));
        assertTrue(source.contains("SPATIAL_V6_MIN_SEARCH_MS=12"));
        assertTrue(source.contains("SPATIAL_V6_DEPTH=2"));
        assertTrue(source.contains("USE_SPATIAL_V5_SEARCH=false"));
        for (String omitted : new String[]{"class SpatialBot", "SpatialV6Bot", "SpatialV6Weights",
                "SpatialV5Bot", "SpatialV5Weights", "SpatialV4Bot", "SpatialV4Weights", "SpatialV3Weights",
                "SpatialV2Weights", "SpatialInitialWeights", "SpatialBridge", "SpatialTeacherExport",
                "btk.tools", "btk.referee", "com.google.gson"})
            assertFalse(source.contains(omitted), omitted);
        FileMerger.checkSourceSize(source);
        checkFrozenDecisions("spatial-ppo-v6", true);
        String ablation = "spatial-ppo-v6?USE_SPATIAL_V6_SEARCH=0";
        assertTrue(FileMerger.merge(ablation).contains("USE_SPATIAL_V6_SEARCH=false"));
        checkFrozenDecisions(ablation, true);
    }

    @Test void spatialV6RegistrationRecordsOnlyThePrescribedConfiguration() {
        assertInstanceOf(btk.bot.neural.SpatialV6Bot.class, BotFactory.create("spatial-ppo-v6"));
        assertEquals("spatial-ppo-v6", BotFactory.create("spatial-ppo-v6").name());
        assertEquals("candidate", btk.bot.BotCatalog.describe("spatial-ppo-v6").category());
        var defaults = new btk.bot.Params();
        assertFalse(defaults.USE_SPATIAL_V6_SEARCH);
        assertEquals(12, defaults.SPATIAL_V6_MIN_SEARCH_MS);
        assertEquals(2, defaults.SPATIAL_V6_DEPTH);
        Map<String, String> expected = new TreeMap<>(BotFactory.configuration("spatial-ppo-v4"));
        expected.put("USE_SPATIAL_V5_SEARCH", "false");
        expected.put("SPATIAL_V5_MIN_SEARCH_MS", "12");
        expected.put("USE_SPATIAL_V6_SEARCH", "true");
        expected.put("SPATIAL_V6_MIN_SEARCH_MS", "12");
        expected.put("SPATIAL_V6_DEPTH", "2");
        assertEquals(expected, BotFactory.configuration("spatial-ppo-v6"));
        var ablation = BotFactory.parameters("spatial-ppo-v6?USE_SPATIAL_V6_SEARCH=0,SPATIAL_V6_MIN_SEARCH_MS=18,SPATIAL_V6_DEPTH=1");
        assertFalse(ablation.USE_SPATIAL_V6_SEARCH);
        assertEquals(18, ablation.SPATIAL_V6_MIN_SEARCH_MS);
        assertEquals(1, ablation.SPATIAL_V6_DEPTH);
        assertFalse(ablation.USE_SPATIAL_V5_SEARCH);
    }

    @Test void spatialV6SearchOffKeepsFrozenV4GreedyDecisions() {
        for (int me = 0; me < 2; me++) for (int turn : new int[]{0, 37}) {
            GameState state = MapGenerator.generate(1000);
            state.turn = turn;
            if (turn > 0) {
                for (int cell = 0; cell < state.size; cell += 11)
                    if (state.canBuild(cell)) state.owner[cell] = (cell / 11) % 2;
                btk.core.Rules.updateConnections(state, new btk.core.Pathing(state.size));
            }
            Bot old = BotFactory.create("spatial-ppo-v4");
            Bot off = BotFactory.create("spatial-ppo-v6?USE_SPATIAL_V6_SEARCH=0");
            old.init(state, me); off.init(state, me);
            assertEquals(Action.format(old.play(state.copy(), me, 60_000)),
                    Action.format(off.play(state.copy(), me, 60_000)), "seat=" + me + ",turn=" + turn);
        }
    }

    @Test void spatialV6RegistrationPreservesAllPriorSpatialAndSelectedArtifactBytes() throws Exception {
        Map<String, String> hashes = Map.of(
                "spatial-ppo-v1", "d9507cb3e0d68de4f526cfa9da06a2b1c909182e9992f4b7a3bb6cab3d24d6d7",
                "spatial-ppo-v2", "a3d0fdb7e587478fc4b6f63cd64bb6f607c34b64384cd41cc091bcec375107a1",
                "spatial-ppo-v3", "2e873833da40b7b73c585016fc8fe0039b77d256fe60445f60eaf1b4c967dfc7",
                "spatial-ppo-v4", "a961638dca644f880a12224f4e385fcfbb9efbe52d5a3744e2a0c2267276bdb2",
                "spatial-ppo-v5", "f5793756bf20c48af7302418b70b8fe92499b3c3eaefd3291999f1b4160550c2",
                btk.bot.LiveTargetExperimentParams.V09_ON_CURRENT, "92d508882c42c4ad799847c83eb00d356dd88ea5ee7f83320f471ad5c7c85739",
                "v09", "aca0be459116e78b97400ba0a0abfaa151aca7cc97db7804b8466e8cda788fac",
                "neural-v2", "16c49e637b808e82686651368155ea77032848faf9b3c83007433d69d6eb02a9");
        for (var prior : hashes.entrySet()) {
            String source = FileMerger.merge(prior.getKey());
            assertEquals(prior.getValue(), FileMerger.sha256(source), prior.getKey());
            for (String field : new String[]{"USE_SPATIAL_V6_SEARCH", "SPATIAL_V6_MIN_SEARCH_MS", "SPATIAL_V6_DEPTH"}) {
                assertFalse(source.contains(field), prior.getKey() + ": " + field);
                assertFalse(BotFactory.configuration(prior.getKey()).containsKey(field), prior.getKey() + ": " + field);
            }
        }
        for (String field : new String[]{"USE_SPATIAL_V6_SEARCH", "SPATIAL_V6_MIN_SEARCH_MS", "SPATIAL_V6_DEPTH"}) {
            assertFalse(FileMerger.merge("spatial-ppo-v2-init").contains(field));
            assertFalse(BotFactory.configuration("spatial-ppo-v2-init").containsKey(field));
        }
    }

    @Test void spatialV7ShipsOnlyFrozenV4PayloadAndMatchesLocalSearchAndAblation() throws Exception {
        String source = FileMerger.merge("spatial-ppo-v7");
        var declaration = java.util.regex.Pattern.compile("String DATA\\s*=\\s*([^;]+);").matcher(source);
        assertTrue(declaration.find());
        var chunks = java.util.regex.Pattern.compile("\"([A-Za-z0-9+/=]+)\"").matcher(declaration.group(1));
        StringBuilder payload = new StringBuilder();
        while (chunks.find()) payload.append(chunks.group(1));
        assertEquals(btk.bot.neural.SpatialV4Weights.DATA, payload.toString());
        assertFalse(declaration.find(), "only the frozen v4 payload may ship");
        assertTrue(source.contains("return \"spatial-ppo-v7\";"));
        assertTrue(source.contains("static final class EvalBot"));
        assertTrue(source.contains("static final class SpatialWeights"));
        assertTrue(source.contains("class SearchState"));
        assertTrue(source.contains("USE_SPATIAL_V7_SEARCH=true"));
        assertTrue(source.contains("SPATIAL_V7_MIN_SEARCH_MS=12"));
        assertTrue(source.contains("SPATIAL_V7_DEPTH=2"));
        assertTrue(source.contains("USE_SPATIAL_V5_SEARCH=false"));
        assertTrue(source.contains("USE_SPATIAL_V6_SEARCH=false"));
        for (String omitted : new String[]{"class SpatialBot", "SpatialV7Bot", "SpatialV7Weights",
                "SpatialV6Bot", "SpatialV6Weights", "SpatialV5Bot", "SpatialV5Weights", "SpatialV4Bot", "SpatialV4Weights", "SpatialV3Weights",
                "SpatialV2Weights", "SpatialInitialWeights", "SpatialBridge", "SpatialTeacherExport",
                "btk.tools", "btk.referee", "com.google.gson"})
            assertFalse(source.contains(omitted), omitted);
        FileMerger.checkSourceSize(source);
        checkFrozenDecisions("spatial-ppo-v7", true);
        String ablation = "spatial-ppo-v7?USE_SPATIAL_V7_SEARCH=0";
        assertTrue(FileMerger.merge(ablation).contains("USE_SPATIAL_V7_SEARCH=false"));
        checkFrozenDecisions(ablation, true);
    }

    @Test void spatialV7RegistrationRecordsOnlyThePrescribedConfiguration() {
        assertInstanceOf(btk.bot.neural.SpatialV7Bot.class, BotFactory.create("spatial-ppo-v7"));
        assertEquals("spatial-ppo-v7", BotFactory.create("spatial-ppo-v7").name());
        assertEquals("candidate", btk.bot.BotCatalog.describe("spatial-ppo-v7").category());
        var defaults = new btk.bot.Params();
        assertFalse(defaults.USE_SPATIAL_V7_SEARCH);
        assertEquals(12, defaults.SPATIAL_V7_MIN_SEARCH_MS);
        assertEquals(2, defaults.SPATIAL_V7_DEPTH);
        Map<String, String> expected = new TreeMap<>(BotFactory.configuration("spatial-ppo-v4"));
        expected.put("USE_SPATIAL_V5_SEARCH", "false");
        expected.put("SPATIAL_V5_MIN_SEARCH_MS", "12");
        expected.put("USE_SPATIAL_V6_SEARCH", "false");
        expected.put("SPATIAL_V6_MIN_SEARCH_MS", "12");
        expected.put("SPATIAL_V6_DEPTH", "2");
        expected.put("USE_SPATIAL_V7_SEARCH", "true");
        expected.put("SPATIAL_V7_MIN_SEARCH_MS", "12");
        expected.put("SPATIAL_V7_DEPTH", "2");
        assertEquals(expected, BotFactory.configuration("spatial-ppo-v7"));
        var ablation = BotFactory.parameters("spatial-ppo-v7?USE_SPATIAL_V7_SEARCH=0,SPATIAL_V7_MIN_SEARCH_MS=18,SPATIAL_V7_DEPTH=1");
        assertFalse(ablation.USE_SPATIAL_V7_SEARCH);
        assertEquals(18, ablation.SPATIAL_V7_MIN_SEARCH_MS);
        assertEquals(1, ablation.SPATIAL_V7_DEPTH);
        assertFalse(ablation.USE_SPATIAL_V5_SEARCH);
        assertFalse(ablation.USE_SPATIAL_V6_SEARCH);
    }

    @Test void spatialV7SearchOffKeepsFrozenV4GreedyDecisions() {
        for (int me = 0; me < 2; me++) for (int turn : new int[]{0, 37}) {
            GameState state = MapGenerator.generate(1000);
            state.turn = turn;
            if (turn > 0) {
                for (int cell = 0; cell < state.size; cell += 11)
                    if (state.canBuild(cell)) state.owner[cell] = (cell / 11) % 2;
                btk.core.Rules.updateConnections(state, new btk.core.Pathing(state.size));
            }
            Bot old = BotFactory.create("spatial-ppo-v4");
            Bot off = BotFactory.create("spatial-ppo-v7?USE_SPATIAL_V7_SEARCH=0");
            old.init(state, me); off.init(state, me);
            assertEquals(Action.format(old.play(state.copy(), me, 60_000)),
                    Action.format(off.play(state.copy(), me, 60_000)), "seat=" + me + ",turn=" + turn);
        }
    }

    @Test void spatialV7RegistrationPreservesAllPriorSpatialAndSelectedArtifactBytes() throws Exception {
        Map<String, String> hashes = Map.of(
                "spatial-ppo-v1", "d9507cb3e0d68de4f526cfa9da06a2b1c909182e9992f4b7a3bb6cab3d24d6d7",
                "spatial-ppo-v2", "a3d0fdb7e587478fc4b6f63cd64bb6f607c34b64384cd41cc091bcec375107a1",
                "spatial-ppo-v3", "2e873833da40b7b73c585016fc8fe0039b77d256fe60445f60eaf1b4c967dfc7",
                "spatial-ppo-v4", "a961638dca644f880a12224f4e385fcfbb9efbe52d5a3744e2a0c2267276bdb2",
                "spatial-ppo-v5", "f5793756bf20c48af7302418b70b8fe92499b3c3eaefd3291999f1b4160550c2",
                "spatial-ppo-v6", "4019ea1ab9c0e95081d5a56bcd27eff3a6b2ba9c87dbec2134b66ca94dd6d9e6",
                btk.bot.LiveTargetExperimentParams.V09_ON_CURRENT, "92d508882c42c4ad799847c83eb00d356dd88ea5ee7f83320f471ad5c7c85739",
                "v09", "aca0be459116e78b97400ba0a0abfaa151aca7cc97db7804b8466e8cda788fac",
                "neural-v2", "16c49e637b808e82686651368155ea77032848faf9b3c83007433d69d6eb02a9");
        for (var prior : hashes.entrySet()) {
            String source = FileMerger.merge(prior.getKey());
            assertEquals(prior.getValue(), FileMerger.sha256(source), prior.getKey());
            for (String field : new String[]{"USE_SPATIAL_V7_SEARCH", "SPATIAL_V7_MIN_SEARCH_MS", "SPATIAL_V7_DEPTH"}) {
                assertFalse(source.contains(field), prior.getKey() + ": " + field);
                assertFalse(BotFactory.configuration(prior.getKey()).containsKey(field), prior.getKey() + ": " + field);
            }
        }
        for (String field : new String[]{"USE_SPATIAL_V7_SEARCH", "SPATIAL_V7_MIN_SEARCH_MS", "SPATIAL_V7_DEPTH"}) {
            assertFalse(FileMerger.merge("spatial-ppo-v2-init").contains(field));
            assertFalse(BotFactory.configuration("spatial-ppo-v2-init").containsKey(field));
        }
    }

    @Test void spatialV8ShipsOnlyFrozenV4PayloadAndMatchesLocalSearchAndAblation() throws Exception {
        String source = FileMerger.merge("spatial-ppo-v8");
        var declaration = java.util.regex.Pattern.compile("String DATA\\s*=\\s*([^;]+);").matcher(source);
        assertTrue(declaration.find());
        var chunks = java.util.regex.Pattern.compile("\"([A-Za-z0-9+/=]+)\"").matcher(declaration.group(1));
        StringBuilder payload = new StringBuilder();
        while (chunks.find()) payload.append(chunks.group(1));
        assertEquals(btk.bot.neural.SpatialV4Weights.DATA, payload.toString());
        assertFalse(declaration.find(), "only the frozen v4 payload may ship");
        assertTrue(source.contains("return \"spatial-ppo-v8\";"));
        assertTrue(source.contains("static final class EvalBot"));
        assertTrue(source.contains("static final class SpatialWeights"));
        assertTrue(source.contains("class SearchState"));
        assertTrue(source.contains("USE_SPATIAL_V8_SEARCH=true"));
        assertTrue(source.contains("SPATIAL_V8_MIN_SEARCH_MS=12"));
        assertTrue(source.contains("SPATIAL_V8_DEPTH=8"));
        assertTrue(source.contains("USE_SPATIAL_V5_SEARCH=false"));
        assertTrue(source.contains("USE_SPATIAL_V6_SEARCH=false"));
        assertTrue(source.contains("USE_SPATIAL_V7_SEARCH=false"));
        for (String omitted : new String[]{"class SpatialBot", "SpatialV8Bot", "SpatialV8Weights", "SpatialV7Bot", "SpatialV7Weights",
                "SpatialV6Bot", "SpatialV6Weights", "SpatialV5Bot", "SpatialV5Weights", "SpatialV4Bot", "SpatialV4Weights", "SpatialV3Weights",
                "SpatialV2Weights", "SpatialInitialWeights", "SpatialBridge", "SpatialTeacherExport",
                "btk.tools", "btk.referee", "com.google.gson"})
            assertFalse(source.contains(omitted), omitted);
        FileMerger.checkSourceSize(source);
        checkFrozenDecisions("spatial-ppo-v8", true);
        String ablation = "spatial-ppo-v8?USE_SPATIAL_V8_SEARCH=0";
        assertTrue(FileMerger.merge(ablation).contains("USE_SPATIAL_V8_SEARCH=false"));
        checkFrozenDecisions(ablation, true);
    }

    @Test void spatialV8RegistrationRecordsOnlyThePrescribedConfiguration() {
        assertInstanceOf(btk.bot.neural.SpatialV8Bot.class, BotFactory.create("spatial-ppo-v8"));
        assertEquals("spatial-ppo-v8", BotFactory.create("spatial-ppo-v8").name());
        assertEquals("candidate", btk.bot.BotCatalog.describe("spatial-ppo-v8").category());
        var defaults = new btk.bot.Params();
        assertFalse(defaults.USE_SPATIAL_V8_SEARCH);
        assertEquals(12, defaults.SPATIAL_V8_MIN_SEARCH_MS);
        assertEquals(8, defaults.SPATIAL_V8_DEPTH);
        Map<String, String> expected = new TreeMap<>(BotFactory.configuration("spatial-ppo-v4"));
        expected.put("USE_SPATIAL_V5_SEARCH", "false");
        expected.put("SPATIAL_V5_MIN_SEARCH_MS", "12");
        expected.put("USE_SPATIAL_V6_SEARCH", "false");
        expected.put("SPATIAL_V6_MIN_SEARCH_MS", "12");
        expected.put("SPATIAL_V6_DEPTH", "2");
        expected.put("USE_SPATIAL_V7_SEARCH", "false");
        expected.put("SPATIAL_V7_MIN_SEARCH_MS", "12");
        expected.put("SPATIAL_V7_DEPTH", "2");
        expected.put("USE_SPATIAL_V8_SEARCH", "true");
        expected.put("SPATIAL_V8_MIN_SEARCH_MS", "12");
        expected.put("SPATIAL_V8_DEPTH", "8");
        assertEquals(expected, BotFactory.configuration("spatial-ppo-v8"));
        var ablation = BotFactory.parameters("spatial-ppo-v8?USE_SPATIAL_V8_SEARCH=0,SPATIAL_V8_MIN_SEARCH_MS=18,SPATIAL_V8_DEPTH=4");
        assertFalse(ablation.USE_SPATIAL_V8_SEARCH);
        assertEquals(18, ablation.SPATIAL_V8_MIN_SEARCH_MS);
        assertEquals(4, ablation.SPATIAL_V8_DEPTH);
        assertFalse(ablation.USE_SPATIAL_V5_SEARCH);
        assertFalse(ablation.USE_SPATIAL_V6_SEARCH);
        assertFalse(ablation.USE_SPATIAL_V7_SEARCH);
        for (int depth : new int[]{0, 1, 3, 7, 9, 10})
            assertThrows(IllegalArgumentException.class, () -> BotFactory.create("spatial-ppo-v8?SPATIAL_V8_DEPTH=" + depth));
    }

    @Test void spatialV8SearchOffAndDepthTwoKeepFrozenV4AndV7Decisions() {
        for (int me = 0; me < 2; me++) for (int turn : new int[]{0, 37}) {
            GameState state = MapGenerator.generate(1000);
            state.turn = turn;
            if (turn > 0) {
                for (int cell = 0; cell < state.size; cell += 11)
                    if (state.canBuild(cell)) state.owner[cell] = (cell / 11) % 2;
                btk.core.Rules.updateConnections(state, new btk.core.Pathing(state.size));
            }
            Bot old = BotFactory.create("spatial-ppo-v4");
            Bot off = BotFactory.create("spatial-ppo-v8?USE_SPATIAL_V8_SEARCH=0");
            old.init(state, me); off.init(state, me);
            Bot v7 = BotFactory.create("spatial-ppo-v7");
            Bot depthTwo = BotFactory.create("spatial-ppo-v8?SPATIAL_V8_DEPTH=2");
            v7.init(state, me); depthTwo.init(state, me);
            assertEquals(Action.format(v7.play(state.copy(), me, 60_000)),
                    Action.format(depthTwo.play(state.copy(), me, 60_000)), "depth2 seat=" + me + ",turn=" + turn);
            assertEquals(Action.format(old.play(state.copy(), me, 60_000)),
                    Action.format(off.play(state.copy(), me, 60_000)), "seat=" + me + ",turn=" + turn);
        }
    }

    @Test void spatialV8RegistrationPreservesAllPriorSpatialAndSelectedArtifactBytes() throws Exception {
        Map<String, String> hashes = Map.of(
                "spatial-ppo-v1", "d9507cb3e0d68de4f526cfa9da06a2b1c909182e9992f4b7a3bb6cab3d24d6d7",
                "spatial-ppo-v2", "a3d0fdb7e587478fc4b6f63cd64bb6f607c34b64384cd41cc091bcec375107a1",
                "spatial-ppo-v3", "2e873833da40b7b73c585016fc8fe0039b77d256fe60445f60eaf1b4c967dfc7",
                "spatial-ppo-v4", "a961638dca644f880a12224f4e385fcfbb9efbe52d5a3744e2a0c2267276bdb2",
                "spatial-ppo-v5", "f5793756bf20c48af7302418b70b8fe92499b3c3eaefd3291999f1b4160550c2",
                "spatial-ppo-v6", "4019ea1ab9c0e95081d5a56bcd27eff3a6b2ba9c87dbec2134b66ca94dd6d9e6",
                "spatial-ppo-v7", "32235e07e7582a0ed9929c2c7e6bb53a1a9cdeed54870cf949dd884f159bae74",
                btk.bot.LiveTargetExperimentParams.V09_ON_CURRENT, "92d508882c42c4ad799847c83eb00d356dd88ea5ee7f83320f471ad5c7c85739",
                "v09", "aca0be459116e78b97400ba0a0abfaa151aca7cc97db7804b8466e8cda788fac",
                "neural-v2", "16c49e637b808e82686651368155ea77032848faf9b3c83007433d69d6eb02a9");
        for (var prior : hashes.entrySet()) {
            String source = FileMerger.merge(prior.getKey());
            assertEquals(prior.getValue(), FileMerger.sha256(source), prior.getKey());
            for (String field : new String[]{"USE_SPATIAL_V8_SEARCH", "SPATIAL_V8_MIN_SEARCH_MS", "SPATIAL_V8_DEPTH"}) {
                assertFalse(source.contains(field), prior.getKey() + ": " + field);
                assertFalse(BotFactory.configuration(prior.getKey()).containsKey(field), prior.getKey() + ": " + field);
            }
        }
        for (String field : new String[]{"USE_SPATIAL_V8_SEARCH", "SPATIAL_V8_MIN_SEARCH_MS", "SPATIAL_V8_DEPTH"}) {
            assertFalse(FileMerger.merge("spatial-ppo-v2-init").contains(field));
            assertFalse(BotFactory.configuration("spatial-ppo-v2-init").containsKey(field));
        }
    }

    @Test void spatialV9ShipsOnlyItsOwnNetworkAndPayloadAndMatchesLocalContextModes() throws Exception {
        String source = FileMerger.merge("spatial-ppo-v9");
        var declaration = java.util.regex.Pattern.compile("String DATA\\s*=\\s*([^;]+);").matcher(source);
        assertTrue(declaration.find());
        var chunks = java.util.regex.Pattern.compile("\"([A-Za-z0-9+/=]+)\"").matcher(declaration.group(1));
        StringBuilder payload = new StringBuilder();
        while (chunks.find()) payload.append(chunks.group(1));
        assertEquals(btk.bot.neural.SpatialV9Weights.DATA, payload.toString());
        assertFalse(declaration.find(), "exactly one v9 weight payload must ship");
        assertTrue(source.contains("return \"spatial-ppo-v9\";"));
        assertTrue(source.contains("static final class EvalBot"));
        assertTrue(source.contains("static final class SpatialNet"));
        assertTrue(source.contains("static final class SpatialWeights"));
        assertTrue(source.contains("USE_SPATIAL_V9_TOWN_CONTEXT=true"));
        assertTrue(source.contains("townKernel"), "the new spatial network must be selected");
        for (String omitted : new String[]{"class SpatialBot", "SpatialV9Bot", "SpatialV9Net", "SpatialV9Weights",
                "SpatialV8Bot", "SpatialV7Bot", "SpatialV6Bot", "SpatialV5Bot", "SpatialV4Weights", "SpatialV2Weights",
                "USE_SPATIAL_V8_SEARCH", "USE_SPATIAL_V7_SEARCH", "USE_SPATIAL_V6_SEARCH", "USE_SPATIAL_V5_SEARCH",
                "SpatialBridge", "SpatialTeacherExport", "btk.tools", "btk.referee", "com.google.gson"})
            assertFalse(source.contains(omitted), omitted);
        FileMerger.checkSourceSize(source);
        checkFrozenDecisions("spatial-ppo-v9", true);
        String off = "spatial-ppo-v9?USE_SPATIAL_V9_TOWN_CONTEXT=0";
        assertTrue(FileMerger.merge(off).contains("USE_SPATIAL_V9_TOWN_CONTEXT=false"));
        checkFrozenDecisions(off, true);
    }

    @Test void spatialV9RegistrationChangesOnlyTownContextAndOffKeepsOriginalGreedyExecution() {
        assertInstanceOf(btk.bot.neural.SpatialV9Bot.class, BotFactory.create("spatial-ppo-v9"));
        assertEquals("spatial-ppo-v9", BotFactory.create("spatial-ppo-v9").name());
        assertEquals("candidate", btk.bot.BotCatalog.describe("spatial-ppo-v9").category());
        assertFalse(new btk.bot.Params().USE_SPATIAL_V9_TOWN_CONTEXT);
        Map<String, String> expected = new TreeMap<>(BotFactory.configuration("spatial-ppo-v4"));
        expected.put("USE_SPATIAL_V9_TOWN_CONTEXT", "true");
        assertEquals(expected, BotFactory.configuration("spatial-ppo-v9"));
        var params = BotFactory.parameters("spatial-ppo-v9?USE_SPATIAL_V9_TOWN_CONTEXT=0");
        assertFalse(params.USE_SPATIAL_V9_TOWN_CONTEXT);
        assertFalse(params.USE_SPATIAL_V5_SEARCH);
        assertFalse(params.USE_SPATIAL_V6_SEARCH);
        assertFalse(params.USE_SPATIAL_V7_SEARCH);
        assertFalse(params.USE_SPATIAL_V8_SEARCH);
        for (int me = 0; me < 2; me++) for (int turn : new int[]{0, 37}) {
            GameState state = MapGenerator.generate(1000);
            state.turn = turn;
            if (turn > 0) {
                for (int cell = 0; cell < state.size; cell += 11)
                    if (state.canBuild(cell)) state.owner[cell] = (cell / 11) % 2;
                btk.core.Rules.updateConnections(state, new btk.core.Pathing(state.size));
            }
            Bot original = new btk.bot.neural.SpatialBot(params,
                    new btk.bot.neural.SpatialNet(btk.bot.neural.SpatialV9Weights.DATA), () -> 0L);
            Bot off = new btk.bot.neural.SpatialV9Bot(params,
                    new btk.bot.neural.SpatialV9Net(btk.bot.neural.SpatialV9Weights.DATA, false), () -> 0L);
            original.init(state, me); off.init(state, me);
            assertEquals(Action.format(original.play(state.copy(), me, 60_000)),
                    Action.format(off.play(state.copy(), me, 60_000)), "seat=" + me + ",turn=" + turn);
        }
    }

    @Test void spatialV9RegistrationPreservesAllPriorSpatialAndSelectedArtifactBytes() throws Exception {
        Map<String, String> hashes = Map.ofEntries(
                Map.entry("spatial-ppo-v1", "d9507cb3e0d68de4f526cfa9da06a2b1c909182e9992f4b7a3bb6cab3d24d6d7"),
                Map.entry("spatial-ppo-v2", "a3d0fdb7e587478fc4b6f63cd64bb6f607c34b64384cd41cc091bcec375107a1"),
                Map.entry("spatial-ppo-v3", "2e873833da40b7b73c585016fc8fe0039b77d256fe60445f60eaf1b4c967dfc7"),
                Map.entry("spatial-ppo-v4", "a961638dca644f880a12224f4e385fcfbb9efbe52d5a3744e2a0c2267276bdb2"),
                Map.entry("spatial-ppo-v5", "f5793756bf20c48af7302418b70b8fe92499b3c3eaefd3291999f1b4160550c2"),
                Map.entry("spatial-ppo-v6", "4019ea1ab9c0e95081d5a56bcd27eff3a6b2ba9c87dbec2134b66ca94dd6d9e6"),
                Map.entry("spatial-ppo-v7", "32235e07e7582a0ed9929c2c7e6bb53a1a9cdeed54870cf949dd884f159bae74"),
                Map.entry("spatial-ppo-v8", "3a477686dd9606029da960d2d38380da30ebffb22307e0a41af3abaa5572aaff"),
                Map.entry(btk.bot.LiveTargetExperimentParams.V09_ON_CURRENT, "92d508882c42c4ad799847c83eb00d356dd88ea5ee7f83320f471ad5c7c85739"),
                Map.entry("v09", "aca0be459116e78b97400ba0a0abfaa151aca7cc97db7804b8466e8cda788fac"),
                Map.entry("neural-v2", "16c49e637b808e82686651368155ea77032848faf9b3c83007433d69d6eb02a9"));
        for (var prior : hashes.entrySet()) {
            String source = FileMerger.merge(prior.getKey());
            assertEquals(prior.getValue(), FileMerger.sha256(source), prior.getKey());
            assertFalse(source.contains("USE_SPATIAL_V9_TOWN_CONTEXT"), prior.getKey());
            assertFalse(BotFactory.configuration(prior.getKey()).containsKey("USE_SPATIAL_V9_TOWN_CONTEXT"), prior.getKey());
        }
        assertFalse(FileMerger.merge("spatial-ppo-v2-init").contains("USE_SPATIAL_V9_TOWN_CONTEXT"));
        assertFalse(BotFactory.configuration("spatial-ppo-v2-init").containsKey("USE_SPATIAL_V9_TOWN_CONTEXT"));
    }

    @Test void explicitCandidateParametersAreEmbeddedWithoutChangingDefaults() throws Exception {
        String source = FileMerger.merge("current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",USE_ROLLOUT_POLICY=0,INTERRUPT_VULN_EVAL=1");
        FileMerger.checkSourceSize(source);
        assertTrue(source.contains("INTERRUPT_VULN_EVAL=true"));
        assertTrue(source.contains("PROFILE_SEARCH=false"));
        assertFalse(new btk.bot.Params().INTERRUPT_VULN_EVAL);
    }

    @Test void jointCandidateFitsAndShipsNoArchive() throws Exception {
        String source = FileMerger.merge("current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",USE_ROLLOUT_POLICY=0,USE_REPLY_RERANK=1,BUILD_BEAM_WIDTH=2,OPP_REPLY_WIDTH=1,USE_JOINT_ACTION=1");
        FileMerger.checkSourceSize(source);
        assertTrue(source.contains("USE_JOINT_ACTION=true"));
        assertTrue(source.contains("chooseJointAction"));
        assertFalse(source.contains("class RetainedEngine"));
        assertFalse(source.contains("experiment-engine-v1-classes"));
        assertFalse(new btk.bot.Params().USE_JOINT_ACTION);
    }

    @Test void restoredV05CompilesAndMatchesFrozenDecisions() throws Exception {
        checkFrozenDecisions("v05", false);
    }

    @Test void rolloutSubmissionCompilesMatchesAndWarmsOnlySelectedPolicy() throws Exception {
        String spec = "current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",USE_ROLLOUT_POLICY=1";
        String source = FileMerger.merge(spec);
        assertTrue(source.contains("private double rollout("));
        assertFalse(source.contains("chooseCandidateWithReplies"));
        assertFalse(source.contains("class HeuristicBot"));
        assertTrue(FileMerger.merge(btk.bot.LiveTargetExperimentParams.V09_ON_CURRENT).contains("private double rollout("));
        assertFalse(FileMerger.merge("v05").contains("private double rollout("));
        checkFrozenDecisions(spec, true);
    }

    @Test void quickRouteSubmissionPreservesCandidateParametersAndWarmupDecisions() throws Exception {
        String spec = "current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",USE_PROTECTED_OPENING=0,USE_ROLLOUT_QUICK_ROUTE=1,USE_ROLLOUT_NETWORK_AFTER_BASE=0";
        assertTrue(FileMerger.merge(spec).contains("USE_ROLLOUT_QUICK_ROUTE=true"));
        assertTrue(new btk.bot.Params().USE_ROLLOUT_QUICK_ROUTE);
        checkFrozenDecisions(spec, true);
    }

    @Test void mergedV05WarmupIsIsolatedAndUsesTheFrozenStrategy() throws Exception {
        checkFrozenDecisions("v05?ISOLATED_ROUTE_WARMUP=1", true);
    }

    @Test void historicalV06SubmissionPreservesParametersAndIsolatedWarmup() throws Exception {
        String source = FileMerger.merge("v06");
        assertTrue(source.contains("USE_ROLLOUT_POLICY=true"));
        assertTrue(source.contains("ROLLOUT_DEPTH=8"));
        assertTrue(source.contains("USE_ROLLOUT_QUICK_ROUTE=false"));
        assertTrue(source.contains("USE_ROLLOUT_NETWORK_AFTER_BASE=false"));
        assertTrue(source.contains("private double rollout("));
        checkFrozenDecisions("v06", true);
    }

    @Test void frozenV07AndExplicitOpeningDisabledKeepMeasuredParametersAndExactFrozenPolicy() throws Exception {
        var launch = JsonParser.parseString(Files.readString(Path.of("docs/evidence/network-after-base-v1.json")))
                .getAsJsonObject().getAsJsonObject("launch");
        Map<String, String> measured = new TreeMap<>();
        launch.getAsJsonObject("resolvedParameters").getAsJsonObject(launch.get("candidate").getAsString())
                .entrySet().forEach(entry -> measured.put(entry.getKey(), entry.getValue().getAsString()));
        for (String spec : new String[]{"v07", "current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",USE_PROTECTED_OPENING=0,USE_ROLLOUT_FILL=0"}) {
            var actual = new TreeMap<>(BotFactory.configuration(spec));
            assertEquals("false", actual.remove("USE_ROLLOUT_ROUTE_DEPENDENCIES"));
            assertEquals("false", actual.remove("USE_ROLLOUT_NETWORK_CHORDS"));
            btk.bot.SearchExperimentParams.removeAndCheck(actual);
            btk.bot.LiveTargetExperimentParams.removeAndCheck(actual);
            assertEquals(measured, actual);
        }
        String current = FileMerger.merge("current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",USE_PROTECTED_OPENING=0"), frozen = FileMerger.merge("v07");
        assertTrue(current.contains("USE_ROLLOUT_QUICK_ROUTE=true"));
        assertTrue(current.contains("USE_ROLLOUT_NETWORK_AFTER_BASE=true"));
        assertFalse(current.contains("USE_ROLLOUT_NETWORK="), "pruned from current artifacts by prune-v1");
        assertTrue(frozen.contains("private void searchNetworkAfterBase("));
        assertFalse(frozen.contains("RolloutBotV07"));
        // Current deliberately contains later experiments; preserve the historical
        // frozen policy bytes independently of those additions and of shared Params fields.
        int policyStart = frozen.indexOf("static final class EvalBot implements Bot {");
        int policyEnd = frozen.indexOf("static interface Bot {", policyStart);
        assertTrue(policyStart >= 0 && policyEnd > policyStart);
        assertEquals("b3765f55b30aa34f2aafdc5444c6fbb273f3f43415c45cf93b88c3ae6558759a",
                FileMerger.sha256(frozen.substring(policyStart, policyEnd)));
        assertFalse(current.contains("USE_ROLLOUT_ROUTE_DEPENDENCIES="));
        assertFalse(current.contains("USE_ROLLOUT_NETWORK_CHORDS="));
        checkFrozenDecisions("current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",USE_PROTECTED_OPENING=0", true);
        checkFrozenDecisions("v07", true);
    }

    @Test void selectedAndFrozenV08KeepExactApprovedOpeningConfigurationAndPolicy() throws Exception {
        var launch = JsonParser.parseString(Files.readString(Path.of("docs/evidence/strategy-search-opening-v1.json")))
                .getAsJsonObject().getAsJsonObject("launch");
        Map<String, String> measured = new TreeMap<>();
        launch.getAsJsonObject("resolvedParameters").getAsJsonObject("current?USE_PROTECTED_OPENING=1")
                .entrySet().forEach(entry -> measured.put(entry.getKey(), entry.getValue().getAsString()));
        for (String spec : new String[]{"current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",USE_ROLLOUT_FILL=0", "v08"}) {
            var actual = new TreeMap<>(BotFactory.configuration(spec));
            btk.bot.LiveTargetExperimentParams.removeAndCheck(actual);
            assertEquals(measured, actual, "historical v08 measured configuration");
        }
        String current = FileMerger.merge(btk.bot.LiveTargetExperimentParams.V09_ON_CURRENT);
        assertEquals(current, FileMerger.merge("current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",USE_PROTECTED_OPENING=1"));
        assertEquals(current, FileMerger.merge("current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",USE_ROLLOUT_FILL=1"));
        assertTrue(current.contains("USE_PROTECTED_OPENING=true"));
        assertFalse(current.contains("USE_ROLLOUT_RHEA="));
        assertFalse(current.contains("USE_ROLLOUT_LIVE_TARGET="));
        assertFalse(current.contains("ROLLOUT_ALWAYS_DISRUPT="));
        FileMerger.checkSourceSize(current);
        String frozen = FileMerger.merge("v08");
        FileMerger.checkSourceSize(frozen);
        assertTrue(frozen.contains("static final class ProtectedOpening"));
        assertFalse(frozen.contains("RolloutBotV08"));
        assertFalse(frozen.contains("ProtectedOpeningV08"));
        // Current deliberately contains later default-off experiments; preserve the selected
        // frozen policy bytes independently of those additions and of shared Params fields.
        assertEquals("7f2fe8385f91a264ba3592620841045964dce334638cfa2e4df40670596661ef",
                FileMerger.sha256(policySection(frozen)));
        checkFrozenDecisions("v08", true, false, true);
        checkFrozenDecisions("current", true, false, true);
    }

    /** Shipped strategy classes between the first policy class and the shared Bot interface. */
    private static String policySection(String merged) {
        int start = merged.indexOf("static final class EvalBot implements Bot {");
        int end = merged.indexOf("static interface Bot {", start);
        assertTrue(start >= 0 && end > start);
        return merged.substring(start, end);
    }

    @Test void selectedSourcePreservesApprovedArtifactExceptTheDefaultFlagAndBuildMarker() throws Exception {
        String approved;
        try (var archive = new ZipFile("docs/evidence/strategy-search-v1-artifacts.zip")) {
            var entry = archive.getEntry("build/cg-boss/artifacts/strategy-search-opening-v1/Player.java");
            assertNotNull(entry, "approved source must remain in durable evidence");
            try (var input = archive.getInputStream(entry)) {
                approved = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        String previousDefault = "public boolean USE_PROTECTED_OPENING = false;\n";
        assertEquals(approved.indexOf(previousDefault), approved.lastIndexOf(previousDefault));
        assertTrue(approved.contains(previousDefault));
        assertTrue(FileMerger.merge(btk.bot.LiveTargetExperimentParams.V09_ON_CURRENT).contains("public boolean USE_PROTECTED_OPENING = true;"));
        // Later default-off Params fields changed the shipped source outside the policy classes;
        // the frozen v08 policy itself must still be the approved bytes apart from its label.
        String frozen = FileMerger.normalizeMarker(FileMerger.merge("v08"))
                .replace("public String name() { return \"v08\"; }",
                        "public String name() { return \"current?\" + p.diff(); }");
        assertEquals(policySection(FileMerger.normalizeMarker(approved)), policySection(frozen),
                "frozen v08 must ship the approved policy bytes");
    }

    @Test void frozenV09KeepsMeasuredPolicyAndCurrentKeepsItsDisabledRiskBaseline() throws Exception {
        String current = FileMerger.merge(btk.bot.LiveTargetExperimentParams.V09_ON_CURRENT), frozen = FileMerger.merge("v09");
        FileMerger.checkSourceSize(current);
        FileMerger.checkSourceSize(frozen);
        assertTrue(current.contains("USE_ROLLOUT_FILL=true"));
        assertTrue(frozen.contains("USE_ROLLOUT_FILL=true"));
        assertTrue(frozen.contains("private void fill("));
        assertFalse(frozen.contains("searchRhea"));
        assertFalse(frozen.contains("RolloutBotV09"));
        assertFalse(frozen.contains("ProtectedOpeningV09"));
        String normalized = FileMerger.normalizeMarker(frozen)
                .replace("public String name() { return \"v09\"; }",
                        "public String name() { return \"current?\" + p.diff(); }");
        assertEquals("2a9c84029cde47c9e2378a28a6dd0eb7c0d882ec85d85af0138b4160a9d74fe3",
                FileMerger.sha256(policySection(normalized)), "measured frozen v09 policy bytes");
        var currentParams = new TreeMap<>(BotFactory.configuration(btk.bot.LiveTargetExperimentParams.V09_ON_CURRENT));
        assertEquals("false", currentParams.remove("USE_ROLLOUT_ALL_CELL_RISK"));
        assertEquals("false", currentParams.remove("USE_ROLLOUT_ROOT_FILL"));
        assertEquals(currentParams, BotFactory.configuration("v09"));
        checkFrozenDecisions("v09", true, false, true);
        checkFrozenDecisions("current", true, false, true);
    }

    @Test void frozenV07SubmissionMatchesAnActiveNetworkChoiceIncludingWarmup() throws Exception {
        checkFrozenDecisions("v07?NOISE=0,USE_DISRUPT=0", true, true);
    }

    @Test void independentSearchCandidatesCompileAndKeepTheirExactConfiguration() throws Exception {
        for (String spec : new String[]{"current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",USE_PROTECTED_OPENING=0", "current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",USE_PROTECTED_OPENING=1"}) {
            String source = FileMerger.merge(spec);
            assertTrue(source.contains("static final class ProtectedOpening"));
            checkFrozenDecisions(spec, true);
        }
        assertFalse(FileMerger.merge("v07").contains("class ProtectedOpening"));
    }

    @Test void artifactAuditRejectsSameConfigurationWithDifferentSource() throws Exception {
        String spec = "current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",USE_PROTECTED_OPENING=0", source = FileMerger.merge(spec);
        Files.writeString(temp.resolve("Player.java"), source);
        Files.writeString(temp.resolve("manifest.json"),
                new com.google.gson.Gson().toJson(FileMerger.manifest(spec, source)));
        assertDoesNotThrow(() -> ArtifactCheck.main(new String[]{spec, temp.toString()}));
        Files.writeString(temp.resolve("Player.java"), source + "\n// changed source\n");
        assertThrows(IllegalStateException.class, () -> ArtifactCheck.main(new String[]{spec, temp.toString()}));
    }

    @Test void heuristicSubmissionCompilesMatchesAndWarmsOnlySelectedPolicy() throws Exception {
        String spec = "current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",USE_ROLLOUT_POLICY=0,USE_HEURISTIC_POLICY=1,HEURISTIC_EMERGENCY_STOP=0";
        String source = FileMerger.merge(spec);
        assertTrue(source.contains("chooseTarget"));
        assertFalse(source.contains("chooseJointAction"));
        assertFalse(source.contains("evalState"));
        assertFalse(source.contains("new HeuristicBot"));
        assertFalse(source.contains("private double rollout("));
        checkFrozenDecisions(spec, true);
    }

    private void checkFrozenDecisions(String spec, boolean warmup) throws Exception {
        checkFrozenDecisions(spec, warmup, false);
    }

    private void checkFrozenDecisions(String spec, boolean warmup, boolean networkFixture) throws Exception {
        checkFrozenDecisions(spec, warmup, networkFixture, false);
    }

    private void checkFrozenDecisions(String spec, boolean warmup, boolean networkFixture,
                                      boolean openingFixture) throws Exception {
        String source = FileMerger.merge(spec);
        FileMerger.checkSourceSize(source);
        if (!spec.contains("USE_DEFENSE_PRIORITY=1") && !spec.contains("USE_OWN_BUILD_PLAN=1")) {
            assertFalse(source.contains("chooseCandidateWithReplies"));
        }
        assertFalse(source.contains("btk.bot.v05"));
        assertFalse(source.contains("btk.bot.v06"));
        assertFalse(source.contains("btk.bot.v07"));
        assertFalse(source.contains("btk.bot.v08"));
        assertFalse(source.contains("btk.bot.v09"));
        assertFalse(source.contains("class PressureBot"));
        assertFalse(source.contains("class BenchPool"));
        assertFalse(source.contains("class BenchmarkSuite"));
        assertFalse(source.contains("class BotCatalog"));
        assertFalse(source.contains("class ReplyProbe"));
        assertFalse(source.contains("class ReplyStudy"));
        assertFalse(source.contains("class JointReplyStudy"));
        assertFalse(source.contains("class JointActionCheck"));
        assertFalse(source.contains("class RetainedEngine"));
        assertFalse(source.contains("experiment-engine-v1"));
        Path file = temp.resolve("Player.java");
        Files.writeString(file, source);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-encoding", "UTF-8", "-d", temp.toString(), file.toString()));
        try (var loader = new URLClassLoader(new java.net.URL[]{temp.toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            Class<?> stateClass = loader.loadClass("Player$GameState");
            var ctor = stateClass.getDeclaredConstructor(int.class, int.class, int[].class, int[].class,
                    int[].class, int[].class, int[][].class);
            ctor.setAccessible(true);
            Class<?> mainClass = loader.loadClass("Player$MainBot");
            var mainCtor = mainClass.getDeclaredConstructor(); mainCtor.setAccessible(true);
            var init = mainClass.getMethod("init", stateClass, int.class); init.setAccessible(true);
            var play = mainClass.getMethod("play", stateClass, int.class, long.class); play.setAccessible(true);
            Class<?> actionClass = loader.loadClass("Player$Action");
            var format = actionClass.getMethod("format", java.util.List.class); format.setAccessible(true);
            for (int me = 0; me < 2; me++) {
                GameState s = MapGenerator.generate(1000);
                if (openingFixture) {
                    s = new GameState(9, 1, new int[]{0, 0, 1, 2, 2, 2, 3, 4, 4}, new int[9],
                            new int[]{0, 4, 8}, new int[3], new int[][]{{1, 2}, {2}, {}});
                }
                if (spec.contains("USE_ROLLOUT_ROOT_FILL=1")) {
                    // A completed protected connection leaves the ordinary wait root to fill.
                    // An unfinished connection can instead pack several proposal prefixes and
                    // consume all paint before the feature under test is reached.
                    s = new GameState(5, 2, new int[]{0, 0, 0, 0, 4, 5, 6, 7, 8, 9}, new int[10],
                            new int[]{0, 4}, new int[2], new int[][]{{1}, {}});
                    s.owner[1] = s.owner[2] = s.owner[3] = me; s.turn = 10;
                    btk.core.Rules.updateConnections(s, new btk.core.Pathing(s.size));
                }
                if (networkFixture) {
                    int[] regions = new int[63];
                    for (int cell = 0; cell < regions.length; cell++) regions[cell] = cell;
                    s = new GameState(9, 7, regions, new int[63],
                            new int[]{1, 7}, new int[]{3, 3}, new int[][]{{1}, {}});
                    for (int cell : new int[]{29, 30, 21, 22, 23, 32, 33}) s.owner[cell] = 1 - me;
                    s.turn = 10;
                    btk.core.Rules.updateConnections(s, new btk.core.Pathing(s.size));
                }
                if (spec.contains("USE_HEURISTIC_DETOUR=1")) {
                    int[] regions = new int[15];
                    for (int c = 0; c < regions.length; c++) regions[c] = c;
                    s = new GameState(5, 3, regions, new int[15], new int[]{0, 4}, new int[]{1, 1}, new int[][]{{1}, {0}});
                    for (int x = 1; x < 4; x++) s.owner[s.cell(x, 1)] = 1 - me;
                    btk.core.Rules.updateConnections(s, new btk.core.Pathing(s.size));
                }
                if (spec.contains("USE_DEFENSE_PRIORITY=1")) {
                    s = new GameState(5,3,new int[]{3,3,3,3,3,0,1,1,1,2,4,4,4,4,4},new int[15],
                            new int[]{0,4},new int[]{1,1},new int[][]{{1},{0}});
                    for(int x=1;x<4;x++) s.owner[s.cell(x,1)] = me;
                    btk.core.Rules.disrupt(s,1); btk.core.Rules.disrupt(s,1);
                    btk.core.Rules.updateConnections(s,new btk.core.Pathing(s.size));
                }
                Object generatedState = ctor.newInstance(s.width, s.height, s.regionId, s.terrain,
                        s.townX, s.townY, s.desired);
                for (String field : new String[]{"turn", "maxTurns"}) {
                    var generatedField = stateClass.getField(field); generatedField.setAccessible(true);
                    generatedField.setInt(generatedState, field.equals("turn") ? s.turn : s.maxTurns);
                }
                var owners = stateClass.getField("owner"); owners.setAccessible(true);
                System.arraycopy(s.owner, 0, owners.get(generatedState), 0, s.size);
                for(String field : new String[]{"instability", "regionInstability"}) {
                    var f = stateClass.getField(field); f.setAccessible(true);
                    int[] values = field.equals("instability") ? s.instability : s.regionInstability;
                    System.arraycopy(values,0,f.get(generatedState),0,values.length);
                }
                Class<?> pathClass = loader.loadClass("Player$Pathing");
                var pathCtor = pathClass.getDeclaredConstructor(int.class); pathCtor.setAccessible(true);
                var update = loader.loadClass("Player$Rules").getMethod("updateConnections", stateClass, pathClass);
                update.setAccessible(true); update.invoke(null, generatedState, pathCtor.newInstance(s.size));
                Object generatedBot = mainCtor.newInstance();
                var paramsField = mainClass.getDeclaredField("params"); paramsField.setAccessible(true);
                Object generatedParams = paramsField.get(generatedBot);
                Map<String, String> embedded = new TreeMap<>();
                for (var field : generatedParams.getClass().getFields()) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                    field.setAccessible(true); embedded.put(field.getName(), String.valueOf(field.get(generatedParams)));
                }
                Map<String, String> expectedEmbedded = new TreeMap<>(BotFactory.configuration(spec));
                // prune-v1: a current rollout artifact declares and embeds only the fields its policy reads.
                if (FileMerger.prunesLocalOnly(spec)) expectedEmbedded.keySet().removeAll(FileMerger.localOnlyParams());
                assertEquals(expectedEmbedded, embedded, "merged full effective parameters");
                init.invoke(generatedBot, generatedState, me);
                if (warmup) {
                    var warm = mainClass.getMethod("warmup", stateClass, int.class, long.class);
                    warm.setAccessible(true); warm.invoke(generatedBot, generatedState, me, 150L);
                    var calls = mainClass.getField("warmupCalls"); calls.setAccessible(true);
                    assertTrue(calls.getInt(generatedBot) > 0);
                }
                Bot frozen = BotFactory.create(spec); frozen.init(s, me);
                var expectedActions = frozen.play(s.copy(), me, 60_000);
                String expected = Action.format(expectedActions);
                if (spec.contains("USE_ROLLOUT_ROOT_FILL=1"))
                    assertTrue(frozen.getClass().getField("fillCells").getInt(frozen) > 0,
                            "compiled parity fixture must actually score a filled root");
                if (openingFixture) {
                    assertEquals(3, expectedActions.stream().filter(a -> a.type == Action.Type.PLACE).count());
                    for (var action : expectedActions) if (action.type == Action.Type.PLACE)
                        assertTrue(s.regionHasTown(s.regionId[s.cell(action.x, action.y)]));
                    assertTrue(frozen.getClass().getField("selectedOpening").getBoolean(frozen),
                            "fixture must exercise the protected opening in the packaged policy");
                }
                if (networkFixture) assertEquals("PLACE_TRACKS 4 3", expected);
                assertEquals(expected,
                        format.invoke(null, play.invoke(generatedBot, generatedState, me, 60_000L)));
            }
        }
    }

    @Test void neuralSubmissionShipsOnlyTheImitationPolicyAndMatchesDecisions() throws Exception {
        String source = FileMerger.merge("neural-v1");
        assertTrue(source.contains("static final class NeuralFeatures"));
        assertTrue(source.contains("static final class NeuralNet"));
        assertTrue(source.contains("static final class NeuralWeights"));
        assertFalse(source.contains("NeuralBot"));
        assertFalse(source.contains("private double rollout("));
        assertFalse(source.contains("class RolloutBot"));
        assertFalse(source.contains("class HeuristicBot"));
        assertFalse(source.contains("class ProtectedOpening"));
        assertTrue(source.contains("NOISE=0.1"));
        checkFrozenDecisions("neural-v1", true);
        assertFalse(FileMerger.merge(btk.bot.LiveTargetExperimentParams.V09_ON_CURRENT).contains("NeuralFeatures"));
        assertThrows(IllegalArgumentException.class, () -> FileMerger.merge("neural-v99"));
    }

    @Test void neuralV2SubmissionIsIsolatedAndMatchesCompiledPolicyDecisions() throws Exception {
        String source = FileMerger.merge("neural-v2");
        assertTrue(source.contains("static final class NeuralFeatures"));
        assertTrue(source.contains("static final class NeuralNet"));
        assertTrue(source.contains("static final class NeuralV2Weights"));
        assertFalse(source.contains("static final class NeuralWeights"));
        assertFalse(source.contains("NeuralV2Bot"));
        assertFalse(source.contains("class NeuralBot"));
        assertFalse(source.contains("class NeuralV2Collect"));
        assertFalse(source.contains("private double rollout("));
        assertFalse(source.contains("class HeuristicBot"));
        assertFalse(source.contains("class ProtectedOpening"));
        FileMerger.checkSourceSize(source);
        checkFrozenDecisions("neural-v2", true);
        assertFalse(FileMerger.merge("neural-v1").contains("NeuralV2"));
        assertFalse(FileMerger.merge(btk.bot.LiveTargetExperimentParams.V09_ON_CURRENT).contains("NeuralV2"));
    }

    @Test void neuralV3DeadlineShipsOneFeatureCopyAndPreservesExistingArtifactIdentities() throws Exception {
        String source = FileMerger.merge("neural-v3");
        assertTrue(source.contains("static final class NeuralFeatures"));
        assertTrue(source.contains("interface Deadline"));
        assertTrue(source.contains("static final class NeuralWeights"));
        assertTrue(source.contains("NEURAL_DEADLINE_RESERVE_MS=2"));
        assertFalse(source.contains("NeuralV3Features"));
        assertFalse(source.contains("NeuralV3Bot"));
        assertFalse(source.contains("NeuralV2Weights"));
        assertFalse(source.contains("DecisionChooser"));
        FileMerger.checkSourceSize(source);
        checkFrozenDecisions("neural-v3", true);
        // The V3-only Param must not drift the completed V2 or the current merge at 1796158.
        assertFalse(FileMerger.merge("neural-v2").contains("NEURAL_DEADLINE_RESERVE_MS"));
        assertFalse(FileMerger.merge(btk.bot.LiveTargetExperimentParams.V09_ON_CURRENT).contains("NEURAL_DEADLINE_RESERVE_MS"));
        assertEquals("16c49e637b808e82686651368155ea77032848faf9b3c83007433d69d6eb02a9", FileMerger.sha256(FileMerger.merge("neural-v2")));
        // Current can gain default-off experiments; its immutable v09 reference stays exact.
        assertEquals("aca0be459116e78b97400ba0a0abfaa151aca7cc97db7804b8466e8cda788fac", FileMerger.sha256(FileMerger.merge("v09")));
    }

    @Test void neuralV4ShipsNewWeightsAndUnchangedDeadlineFeaturesWithoutDriftingV3() throws Exception {
        String source = FileMerger.merge("neural-v4");
        assertTrue(source.contains("static final class NeuralFeatures"));
        assertTrue(source.contains("interface Deadline"));
        assertTrue(source.contains("static final class NeuralWeights"));
        assertTrue(source.contains("NEURAL_DEADLINE_RESERVE_MS=2"));
        assertFalse(source.contains("NeuralV3Features"));
        assertFalse(source.contains("NeuralV4Bot"));
        assertFalse(source.contains("NeuralV4Weights"));
        assertFalse(source.contains("NeuralV2Weights"));
        assertFalse(source.contains("DecisionChooser"));
        assertFalse(source.contains("NeuralV4Collect"));
        FileMerger.checkSourceSize(source);
        checkFrozenDecisions("neural-v4", true);
        assertEquals("2aee3ffec84a06dd314179cdf6d97475e87cf3dc8bd01aab81f27291a6bc563f",
                FileMerger.sha256(FileMerger.merge("neural-v3")));
    }

    @Test void neuralV5OptimizationShipsOnlyItsHelperAndPreservesFrozenArtifacts() throws Exception {
        String source = FileMerger.merge("neural-v5");
        assertTrue(source.contains("static final class NeuralFeatures"));
        assertTrue(source.contains("interface Deadline"));
        assertTrue(source.contains("static final class NeuralWeights"));
        assertTrue(source.contains("class HypotheticalIncome"));
        assertTrue(source.contains("NEURAL_DEADLINE_RESERVE_MS=2"));
        assertFalse(source.contains("NeuralV5Features"));
        assertFalse(source.contains("NeuralV5Bot"));
        assertFalse(source.contains("NeuralV2Weights"));
        assertFalse(source.contains("NeuralV4Weights"));
        assertFalse(source.contains("DecisionChooser"));
        assertFalse(source.contains("NeuralV4Collect"));
        FileMerger.checkSourceSize(source);
        checkFrozenDecisions("neural-v5", true);
        for (String prior : new String[]{btk.bot.LiveTargetExperimentParams.V09_ON_CURRENT, "v09", "neural-v1", "neural-v2", "neural-v3", "neural-v4", "indrill-q-v1", "indrill-q-v2"})
            assertFalse(FileMerger.merge(prior).contains("HypotheticalIncome"), prior);
        assertEquals("16c49e637b808e82686651368155ea77032848faf9b3c83007433d69d6eb02a9",
                FileMerger.sha256(FileMerger.merge("neural-v2")));
        assertEquals("2aee3ffec84a06dd314179cdf6d97475e87cf3dc8bd01aab81f27291a6bc563f",
                FileMerger.sha256(FileMerger.merge("neural-v3")));
        assertEquals("0b124a2d1e17d2b1b0448c26accca5bb63065672cb24318228733159105b5163",
                FileMerger.sha256(FileMerger.merge("neural-v4")));
        assertEquals("aca0be459116e78b97400ba0a0abfaa151aca7cc97db7804b8466e8cda788fac",
                FileMerger.sha256(FileMerger.merge("v09")));
    }

    @Test void neuralV6ResumablePathsShipInIsolationAndKeepEveryFrozenArtifactExact() throws Exception {
        String source = FileMerger.merge("neural-v6");
        assertTrue(source.contains("static final class NeuralFeatures"));
        assertTrue(source.contains("interface Deadline"));
        assertTrue(source.contains("static final class NeuralWeights"));
        assertTrue(source.contains("class HypotheticalIncome"));
        assertTrue(source.contains("class FeaturePaths"));
        assertTrue(source.contains("NEURAL_DEADLINE_RESERVE_MS=2"));
        assertFalse(source.contains("NeuralV6Features"));
        assertFalse(source.contains("NeuralV6Bot"));
        assertFalse(source.contains("NeuralV2Weights"));
        assertFalse(source.contains("NeuralV4Weights"));
        assertFalse(source.contains("DecisionChooser"));
        FileMerger.checkSourceSize(source);
        checkFrozenDecisions("neural-v6", true);
        for (String prior : new String[]{btk.bot.LiveTargetExperimentParams.V09_ON_CURRENT, "v09", "neural-v1", "neural-v2", "neural-v3", "neural-v4", "neural-v5", "indrill-q-v1", "indrill-q-v2"})
            assertFalse(FileMerger.merge(prior).contains("FeaturePaths"), prior);
        assertEquals("3425928153d5d1c07ec32f711cf3aa04a25c9c5ae3d248176f3ef5d226d34730",
                FileMerger.sha256(FileMerger.merge("neural-v5")));
    }

    @Test void neuralV7BoundedFillShipsOnlyForV7AndPreservesFrozenArtifacts() throws Exception {
        String source = FileMerger.merge("neural-v7");
        assertTrue(source.contains("USE_NEURAL_FALLBACK=true"));
        assertTrue(source.contains("NEURAL_FALLBACK_MAX_INSTABILITY=0"));
        assertFalse(source.contains("NeuralV7Bot"));
        assertFalse(source.contains("NeuralV6Features"));
        FileMerger.checkSourceSize(source);
        checkFrozenDecisions("neural-v7", true);
        assertEquals("0597d5b7fba6fed69313c313f422616784d18acc7219c6d4b7835a7abd90b155",
                FileMerger.sha256(FileMerger.merge("neural-v6")));
        for (String prior : new String[]{btk.bot.LiveTargetExperimentParams.V09_ON_CURRENT, "v09", "neural-v1", "neural-v2", "neural-v3", "neural-v4", "neural-v5", "neural-v6", "indrill-q-v1", "indrill-q-v2"}) {
            assertFalse(FileMerger.merge(prior).contains("USE_NEURAL_FALLBACK"), prior);
            assertFalse(FileMerger.merge(prior).contains("NEURAL_FALLBACK_MAX_INSTABILITY"), prior);
        }
    }

    @Test void indrillSubmissionShipsOnlyTheActionValuePolicyAndMatchesDecisions() throws Exception {
        String source = FileMerger.merge("indrill-q-v1");
        assertTrue(source.contains("static final class IndrillFeatures"));
        assertTrue(source.contains("static final class IndrillNet"));
        assertTrue(source.contains("static final class IndrillWeights"));
        assertFalse(source.contains("IndrillBot"));
        assertFalse(source.contains("NeuralFeatures"));
        assertFalse(source.contains("private double rollout("));
        assertFalse(source.contains("class HeuristicBot"));
        assertFalse(source.contains("class ProtectedOpening"));
        assertFalse(source.contains("class IndrillCollect"));
        assertTrue(source.contains("static final class TacticalPathing"));
        assertFalse(source.contains("class InkProjection"));
        assertTrue(source.contains("INDRILL_INK_PENALTY=2"));
        checkFrozenDecisions("indrill-q-v1", true);
        assertFalse(FileMerger.merge(btk.bot.LiveTargetExperimentParams.V09_ON_CURRENT).contains("IndrillFeatures"));
        assertFalse(FileMerger.merge(btk.bot.LiveTargetExperimentParams.V09_ON_CURRENT).contains("class TacticalPathing"),
                "prune-v1: the all-cell-risk routes left current, so its artifact no longer carries TacticalPathing");
        assertFalse(FileMerger.merge("v09").contains("class InkProjection"));
        assertThrows(IllegalArgumentException.class, () -> FileMerger.merge("indrill-q-v3"));
    }

    @Test void detourSubmissionMatchesOnActiveRoutesIncludingItsWarmup() throws Exception {
        checkFrozenDecisions("current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",USE_ROLLOUT_POLICY=0,USE_HEURISTIC_POLICY=1,USE_HEURISTIC_DETOUR=1,HEURISTIC_EMERGENCY_STOP=0", true);
    }

    /**
     * prune-v1 (2026-10-25): the all-cell-risk and root-fill experiments left current. Their artifacts can no
     * longer be built (the request is refused, never silently ignored); the default artifact ships neither the
     * flags nor TacticalPathing; the mechanisms themselves are covered on the frozen copy by btk.bot.v10.
     */
    @Test void prunedRiskAndRootFillExperimentsCannotBePackagedFromCurrentAndLeftTheDefaultArtifact() throws Exception {
        for (String flag : new String[]{"USE_ROLLOUT_ALL_CELL_RISK", "USE_ROLLOUT_ROOT_FILL"}) {
            String spec = "current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + "," + flag + "=1";
            assertThrows(IllegalArgumentException.class, () -> FileMerger.merge(spec), spec);
            for (String old : new String[]{"current", btk.bot.LiveTargetExperimentParams.V09_ON_CURRENT, "v09", "neural-v3",
                    "neural-v6", "indrill-q-v2", "current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",USE_ROLLOUT_POLICY=0"})
                assertFalse(FileMerger.merge(old).contains(flag), old + " / " + flag);
        }
        assertFalse(FileMerger.merge("current").contains("class TacticalPathing"));
        assertTrue(FileMerger.merge("v10").contains("class TacticalPathing"), "the frozen export keeps every class");
    }
    @Test void v2SubmissionKeepsItsBaselineAndPreservesFrozenV1Artifact() throws Exception {
        String source = FileMerger.merge("indrill-q-v2");
        assertTrue(source.contains("static final class IndrillV2Net"));
        assertTrue(source.contains("static final class IndrillV2Weights"));
        assertTrue(source.contains("public float stateValue"));
        assertFalse(source.contains("IndrillV2Bot"));
        assertFalse(source.contains("static final class IndrillNet"));
        assertFalse(source.contains("static final class IndrillWeights"));
        assertFalse(source.contains("class InkProjection"));
        FileMerger.checkSourceSize(source);
        checkFrozenDecisions("indrill-q-v2", true);
        assertEquals("c186d548aef00b8a6f777f7a9e50742b93a8ecf5aadb9e68c4932c9e4aebb8db",
                FileMerger.sha256(FileMerger.merge("indrill-q-v1")));
    }
    @Test void defenseSubmissionMatchesOnDamagedActiveRouteIncludingWarmup() throws Exception {
        checkFrozenDecisions("current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",USE_ROLLOUT_POLICY=0,USE_DEFENSE_PRIORITY=1",true);
    }
    @Test void ownPlanSubmissionMatchesIncludingWarmup() throws Exception {
        checkFrozenDecisions("current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",USE_ROLLOUT_POLICY=0,USE_OWN_BUILD_PLAN=1", true);
    }
}
