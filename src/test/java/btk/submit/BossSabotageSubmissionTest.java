package btk.submit;

import btk.bot.BotFactory;
import btk.bot.Params;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/** boss-sabotage-v1 packaging: the default-off flag leaves every existing artifact byte-identical. */
class BossSabotageSubmissionTest {
    private static final String CANDIDATE = "current?NOISE=0.1,USE_BOSS_SABOTAGE=true";
    @TempDir Path temp;

    @Test void disabledFlagPreservesSelectedFrozenAndPreviousCandidateArtifacts() throws Exception {
        assertFalse(new Params().USE_BOSS_SABOTAGE);
        assertEquals("ab0954a4545f25222fce6bccf8fa11866d276266e96b4728b502c83996d847e5",
                FileMerger.sha256(FileMerger.merge("current")));
        assertEquals("aca0be459116e78b97400ba0a0abfaa151aca7cc97db7804b8466e8cda788fac",
                FileMerger.sha256(FileMerger.merge("v09")));
        assertEquals("3bd070b3802744778473e93cebed8148ba07a7b243bd744c8ac486076d304a2c",
                FileMerger.sha256(FileMerger.merge("current?NOISE=0,USE_ROLLOUT_MISSED_DEADLINE=true")));
        for (String spec : new String[]{"current", "v09", "neural-v1", "current?NOISE=0.1,USE_BATCHED_REFRESH=true"}) {
            assertFalse(FileMerger.merge(spec).contains("BossSabotage"), spec);
            assertFalse(FileMerger.merge(spec).contains("USE_BOSS_SABOTAGE"), spec);
            assertFalse(BotFactory.configuration(spec).containsKey("USE_BOSS_SABOTAGE"), spec);
        }
        for (String spec : new String[]{"v09?USE_BOSS_SABOTAGE=true", "current?USE_BOSS_SABOTAGE=true,USE_ROLLOUT_POLICY=false",
                "current?USE_BOSS_SABOTAGE=true,USE_HEURISTIC_POLICY=true"})
            assertThrows(IllegalArgumentException.class, () -> BotFactory.create(spec), spec);
        Map<String, String> expected = new TreeMap<>(BotFactory.configuration("current"));
        expected.put("USE_BOSS_SABOTAGE", "true");
        expected.put("BOSS_HOLD_MARGIN", "1.0");
        assertEquals(expected, BotFactory.configuration(CANDIDATE));
    }

    @Test void enabledCandidateFitsAndCompilesWithOnlySubmissionDependencies() throws Exception {
        String candidate = FileMerger.merge(CANDIDATE);
        FileMerger.checkSourceSize(candidate);
        assertTrue(candidate.contains("BossSabotage"));
        assertTrue(candidate.contains("USE_BOSS_SABOTAGE=true"));
        assertFalse(candidate.contains("btk.tools"));
        assertFalse(candidate.contains("btk.referee"));
        assertFalse(candidate.contains("deadlinePending"));
        Path source = temp.resolve("Player.java");
        Files.writeString(source, candidate);
        assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-encoding", "UTF-8", "--release", "17", source.toString()));
    }
}
