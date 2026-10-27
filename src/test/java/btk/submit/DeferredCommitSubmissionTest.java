package btk.submit;

import btk.bot.BotFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class DeferredCommitSubmissionTest {
    @TempDir Path temp;
    @Test void disabledExperimentPreservesExactSelectedAndFrozenArtifacts()throws Exception {
        String current=FileMerger.merge(btk.bot.LiveTargetExperimentParams.V09_ON_CURRENT),v09=FileMerger.merge("v09");
        assertEquals(75925 /* v11 promotion + the 2026-10-25 marker change: version id in the label, selected version in the header comment */,current.length()); // readable-export-v1 (prune-v1: 66,737; before it 98,709)
        assertEquals("92d508882c42c4ad799847c83eb00d356dd88ea5ee7f83320f471ad5c7c85739",FileMerger.sha256(current));
        assertEquals("aca0be459116e78b97400ba0a0abfaa151aca7cc97db7804b8466e8cda788fac",FileMerger.sha256(v09));
        assertFalse(current.contains("deadlinePending"));assertFalse(v09.contains("USE_ROLLOUT_MISSED_DEADLINE"));
        assertFalse(BotFactory.configuration(btk.bot.LiveTargetExperimentParams.V09_ON_CURRENT).containsKey("USE_ROLLOUT_MISSED_DEADLINE"));
        assertThrows(IllegalArgumentException.class,()->BotFactory.create("v09?USE_ROLLOUT_MISSED_DEADLINE=true"));
    }
    @Test void enabledPolicyCompilesWithOnlySubmissionDependencies()throws Exception {
        String candidate=FileMerger.merge("current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",NOISE=0,USE_ROLLOUT_MISSED_DEADLINE=true");
        assertTrue(candidate.contains("deadlinePending"));assertFalse(candidate.contains("btk.tools"));
        assertTrue(candidate.contains("USE_ROLLOUT_MISSED_DEADLINE=true"));
        Path source=temp.resolve("Player.java");Files.writeString(source,candidate);
        assertEquals(0,javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,null,"-encoding","UTF-8","--release","17",source.toString()));
    }
}
