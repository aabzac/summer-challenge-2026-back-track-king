package btk.tools;

import btk.core.GameState;
import btk.submit.FileMerger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ProfileGeneratedPolicyTest {
    @TempDir Path temp;

    @Test void instrumentationPreservesExactGeneratedDecisionAndSemanticDiagnostics() throws Exception {
        var merge = FileMerger.class.getDeclaredMethod("merge", String.class);
        merge.setAccessible(true);
        String source = (String) merge.invoke(null, "current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",NOISE=0,USE_ROLLOUT_MISSED_DEADLINE=true");
        Path file = temp.resolve("Player.java"), classes = temp.resolve("classes");
        Files.writeString(file, source);
        Files.createDirectory(classes);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-encoding", "UTF-8", "--release", "17", "-d", classes.toString(), file.toString()));
        for (int me = 0; me < 2; me++) {
            GameState state = fixture(me);
            String before = ReplyStudy.stateHash(state);
            try (GeneratedPolicy plain = new GeneratedPolicy(temp);
                 ProfileGeneratedPolicy profiled = new ProfileGeneratedPolicy(temp)) {
                final int seat = me;
                assertThrows(IllegalStateException.class, () -> profiled.play(state, seat, 60_000));
                plain.init(state, me); profiled.init(state, me);
                // Zero-duration warmup checks bridge delegation without creating timed preflight outcomes.
                plain.warmup(state, me, 0); profiled.warmup(state, me, 0);
                var expected = plain.play(state, me, 60_000);
                var actual = profiled.play(state, me, 60_000, "synthetic/P" + me);
                assertEquals(expected.action(), actual.action());
                assertEquals(V09ContinuationStudy.JSON.toJsonTree(expected.diagnostics()),
                        V09ContinuationStudy.JSON.toJsonTree(actual.diagnostics()), "All semantic diagnostics");
                var time = actual.timing();
                assertTrue(time.endNs() >= time.startNs());
                assertEquals((time.endNs() - time.startNs()) / 1e6, actual.elapsedMs());
                assertFalse(Instant.parse(time.endInstant()).isBefore(Instant.parse(time.startInstant())));
                assertEquals(Thread.currentThread().getId(), time.threadId());
                assertEquals("synthetic/P" + me, time.correlationId());
                if (time.threadCpuMs() != null) {
                    assertNotNull(time.threadCpuBeforeNs()); assertNotNull(time.threadCpuAfterNs());
                    assertTrue(time.threadCpuMs() >= 0);
                    assertTrue(time.threadCpuAfterNs() >= time.threadCpuBeforeNs());
                    // Windows accounting is coarse; CPU need not be <= the narrow wall interval.
                }
                if (time.gcCountDelta() != null) assertTrue(time.gcCountDelta() >= 0);
                if (time.gcTimeDeltaMs() != null) assertTrue(time.gcTimeDeltaMs() >= 0);
                assertDoesNotThrow(() -> V09ContinuationStudy.JSON.toJson(actual));
                assertEquals(before, ReplyStudy.stateHash(state));
            }
        }
    }

    private static GameState fixture(int me) {
        GameState state = new GameState(5, 2, new int[]{0,1,2,3,4,5,6,7,8,9}, new int[10],
                new int[]{0,4}, new int[]{0,0}, new int[][]{{1},{}});
        state.owner[1] = me; state.owner[2] = 1-me; state.owner[3] = GameState.NEUTRAL;
        state.instability[2] = state.regionInstability[2] = 3;
        state.score[0] = 123; state.score[1] = 456;
        state.turn = 38; state.maxTurns = 72;
        return state;
    }
}
