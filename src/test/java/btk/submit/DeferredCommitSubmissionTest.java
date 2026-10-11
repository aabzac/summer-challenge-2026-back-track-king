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
        String current=FileMerger.merge("current"),v09=FileMerger.merge("v09");
        assertEquals(98709,current.length());
        assertEquals("ab0954a4545f25222fce6bccf8fa11866d276266e96b4728b502c83996d847e5",FileMerger.sha256(current));
        assertEquals("aca0be459116e78b97400ba0a0abfaa151aca7cc97db7804b8466e8cda788fac",FileMerger.sha256(v09));
        assertFalse(current.contains("deadlinePending"));assertFalse(v09.contains("USE_ROLLOUT_MISSED_DEADLINE"));
        assertFalse(BotFactory.configuration("current").containsKey("USE_ROLLOUT_MISSED_DEADLINE"));
        assertThrows(IllegalArgumentException.class,()->BotFactory.create("v09?USE_ROLLOUT_MISSED_DEADLINE=true"));
    }
    @Test void enabledPolicyCompilesWithOnlySubmissionDependencies()throws Exception {
        String candidate=FileMerger.merge("current?NOISE=0,USE_ROLLOUT_MISSED_DEADLINE=true");
        assertTrue(candidate.contains("deadlinePending"));assertFalse(candidate.contains("btk.tools"));
        assertTrue(candidate.contains("USE_ROLLOUT_MISSED_DEADLINE=true"));
        Path source=temp.resolve("Player.java");Files.writeString(source,candidate);
        assertEquals(0,javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,null,"-encoding","UTF-8","--release","17",source.toString()));
    }
}
