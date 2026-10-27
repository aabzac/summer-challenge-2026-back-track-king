package btk.submit;

import btk.bot.BotFactory;
import btk.bot.Params;
import btk.bot.current.RolloutBot;
import btk.referee.MapGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import static org.junit.jupiter.api.Assertions.*;

class BatchedRefreshSubmissionTest {
    private static final String CANDIDATE = "current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",NOISE=0.1,USE_BATCHED_REFRESH=true";
    @TempDir Path temp;

    @Test void disabledFlagPreservesSelectedFrozenAndPreviousCandidateArtifacts() throws Exception {
        assertTrue(new Params().USE_BATCHED_REFRESH, "selected default since the v10 promotion");
        assertEquals("92d508882c42c4ad799847c83eb00d356dd88ea5ee7f83320f471ad5c7c85739",
                FileMerger.sha256(FileMerger.merge(btk.bot.LiveTargetExperimentParams.V09_ON_CURRENT)));
        assertEquals("aca0be459116e78b97400ba0a0abfaa151aca7cc97db7804b8466e8cda788fac",
                FileMerger.sha256(FileMerger.merge("v09")));
        assertEquals("1fa974e7615d6d40b3d5e51995071df5adca82f8e494053edeee547da3e9a034",
                FileMerger.sha256(FileMerger.merge("current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",NOISE=0,USE_ROLLOUT_MISSED_DEADLINE=true")));
        for (String spec : new String[]{btk.bot.LiveTargetExperimentParams.V09_ON_CURRENT, "v09", "neural-v1", "spatial-ppo-v10"}) {
            assertFalse(FileMerger.merge(spec).contains("batchedRefresh"));
            assertFalse(BotFactory.configuration(spec).containsKey("USE_BATCHED_REFRESH"));
        }
        for (String spec : new String[]{"v09?USE_BATCHED_REFRESH=true",
                "current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",USE_BATCHED_REFRESH=true,USE_ROLLOUT_POLICY=false"})
            assertThrows(IllegalArgumentException.class, () -> BotFactory.create(spec));
        Map<String, String> expected = new TreeMap<>(BotFactory.configuration(btk.bot.LiveTargetExperimentParams.V09_ON_CURRENT));
        expected.put("USE_BATCHED_REFRESH", "true");
        assertEquals(expected, BotFactory.configuration(CANDIDATE));
        assertFalse(BotFactory.parameters(CANDIDATE).USE_ROLLOUT_MISSED_DEADLINE);
        assertEquals(BotFactory.parameters("v09").NOISE, BotFactory.parameters(CANDIDATE).NOISE);
    }

    @Test void candidateFitsAndCompactionPreservesTokensAndAllExecutableClasses() throws Exception {
        String plain = FileMerger.mergeUncompressed(CANDIDATE), compact = FileMerger.merge(CANDIDATE);
        FileMerger.checkSourceSize(compact);
        assertTrue(compact.contains("USE_BATCHED_REFRESH=true"));
        assertTrue(compact.contains("updateReusableConnections"));
        assertFalse(compact.contains("deadlinePending"));
        assertFalse(compact.contains("btk.tools"));
        assertFalse(compact.contains("btk.referee"));
        String a = FileMerger.normalizeMarker(plain), b = FileMerger.normalizeMarker(compact);
        assertEquals(JavaSourceCompactor.tokens(a), JavaSourceCompactor.tokens(b));
        assertEquals(compile(a, "plain"), compile(b, "compact"));
    }

    @Test void allFourRolloutBoardsOptInOnlyForCandidate() throws Exception {
        for (boolean enabled : new boolean[]{false, true}) {
            Params params = new Params(); params.USE_BATCHED_REFRESH = enabled;
            RolloutBot bot = new RolloutBot(params);
            bot.init(MapGenerator.generate(241015L), 0);
            for (String name : new String[]{"work", "completion", "targetBoard", "inkBoard"}) {
                var boardField = RolloutBot.class.getDeclaredField(name); boardField.setAccessible(true);
                Object board = boardField.get(bot);
                var flag = board.getClass().getDeclaredField("batchedRefresh"); flag.setAccessible(true);
                assertEquals(enabled, flag.getBoolean(board), name);
            }
        }
    }

    private Map<String, String> compile(String source, String name) throws Exception {
        Path directory = temp.resolve(name), classes = directory.resolve("classes");
        Files.createDirectories(classes);
        Path file = directory.resolve("Player.java"); Files.writeString(file, source);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-encoding", "UTF-8", "--release", "17", "-g:none", "-d", classes.toString(), file.toString()));
        Map<String, String> result = new TreeMap<>();
        try (var files = Files.walk(classes)) {
            for (Path generated : files.filter(Files::isRegularFile).toList())
                result.put(classes.relativize(generated).toString(), java.util.HexFormat.of().formatHex(
                        java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(generated))));
        }
        assertFalse(result.isEmpty());
        return result;
    }
}
