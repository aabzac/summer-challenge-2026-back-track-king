package btk.tools;

import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.submit.FileMerger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.ToolProvider;
import static org.junit.jupiter.api.Assertions.*;

class GeneratedPolicyTest {
    @TempDir Path temp;

    @Test void requiresAnExistingExactArtifact() {
        assertThrows(java.io.IOException.class, () -> new GeneratedPolicy(temp));
    }

    @Test void generatedStateCopiesEveryDynamicFieldAndDiagnosticsAreDetached() throws Exception {
        var merge = FileMerger.class.getDeclaredMethod("merge", String.class);
        merge.setAccessible(true);
        String source = (String) merge.invoke(null, "current?" + btk.bot.LiveTargetExperimentParams.V10_OFF + ",NOISE=0,USE_ROLLOUT_MISSED_DEADLINE=true");
        Path file = temp.resolve("Player.java"), classes = temp.resolve("classes");
        Files.writeString(file, source);
        Files.createDirectory(classes);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-encoding", "UTF-8", "--release", "17", "-d", classes.toString(), file.toString()));
        for (int me = 0; me < 2; me++) {
            final int seat = me;
            GameState s = fixture(me);
            String before = ReplyStudy.stateHash(s);
            try (GeneratedPolicy policy = new GeneratedPolicy(temp)) {
                assertThrows(IllegalStateException.class, () -> policy.play(s, seat, 0));
                var prepare = GeneratedPolicy.class.getDeclaredMethod("prepareState", GameState.class);
                prepare.setAccessible(true);
                Object generated = prepare.invoke(policy, s);
                assertEquals(ClassLoader.getPlatformClassLoader(), generated.getClass().getClassLoader().getParent());
                assertEquals(s.turn, field(generated, "turn"));
                assertEquals(s.maxTurns, field(generated, "maxTurns"));
                for (String name : new String[]{"owner", "instability", "regionInstability", "score",
                        "regionId", "terrain", "townX", "townY"}) {
                    int[] expected = (int[]) GameState.class.getField(name).get(s);
                    int[] actual = (int[]) field(generated, name);
                    assertArrayEquals(expected, actual, name);
                    assertNotSame(expected, actual, name);
                }
                assertArrayEquals(s.inked, (boolean[]) field(generated, "inked"));
                assertArrayEquals(s.regionInked, (boolean[]) field(generated, "regionInked"));
                assertNotSame(s.inked, field(generated, "inked"));
                assertNotSame(s.regionInked, field(generated, "regionInked"));
                int[][] desired = (int[][]) field(generated, "desired");
                for (int i = 0; i < desired.length; i++) {
                    assertArrayEquals(s.desired[i], desired[i]);
                    assertNotSame(s.desired[i], desired[i]);
                }
                // Derived input is deliberately absent: generated btk.core must reconstruct it.
                assertNull(s.pairPath[0]);
                GameState expected = s.copy();
                Rules.updateConnections(expected, new Pathing(expected.size));
                assertArrayEquals(expected.pairPathLen, (int[]) field(generated, "pairPathLen"));
                int[][] generatedPaths = (int[][]) field(generated, "pairPath");
                for (int i = 0; i < expected.pairCount; i++)
                    assertArrayEquals(expected.pairPath[i], generatedPaths[i]);

                policy.init(s, me);
                policy.warmup(s, me, 0);
                var decision = policy.play(s, me, 0);
                assertFalse(Action.parseLine(decision.action()).isEmpty());
                assertTrue(Double.isFinite(decision.elapsedMs()) && decision.elapsedMs() >= 0);
                assertTrue(decision.diagnostics().containsKey("ordinarySearchComplete"));
                assertTrue(decision.diagnostics().containsKey("networkPhaseComplete"));
                assertTrue(decision.diagnostics().containsKey("deadlinePendingTarget"));
                assertEquals(-1, decision.diagnostics().get("deadlinePendingTarget"));
                assertThrows(UnsupportedOperationException.class, () -> decision.diagnostics().put("x", 1));
                int[] snapshot = (int[]) decision.diagnostics().get("deadlineNativeCells");
                Field delegate = GeneratedPolicy.class.getDeclaredField("delegate");
                delegate.setAccessible(true);
                int[] live = (int[]) field(delegate.get(policy), "deadlineNativeCells");
                assertNotSame(live, snapshot);
                int saved = snapshot[0];
                live[0] = saved + 100;
                assertEquals(saved, snapshot[0]);
                Double[] values = (Double[]) decision.diagnostics().get("deadlineWValues");
                for (Double value : values) assertNull(value, "Uncompleted NaN probes must serialize as null");
                Field selectedValue = delegate.get(policy).getClass().getField("selectedValue");
                selectedValue.setAccessible(true);
                selectedValue.setDouble(delegate.get(policy), Double.NaN);
                var diagnostics = GeneratedPolicy.class.getDeclaredMethod("diagnostics");
                diagnostics.setAccessible(true);
                var sanitized = (java.util.Map<?, ?>) diagnostics.invoke(policy);
                assertNull(sanitized.get("selectedValue"));
                assertDoesNotThrow(() -> V09ContinuationStudy.JSON.toJson(sanitized));
                assertEquals(before, ReplyStudy.stateHash(s));
            }
        }
    }

    private static Object field(Object value, String name) throws Exception {
        Field f = value.getClass().getField(name);
        f.setAccessible(true);
        return f.get(value);
    }

    private static GameState fixture(int me) {
        GameState s = new GameState(5, 2, new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9}, new int[10],
                new int[]{0, 4}, new int[]{0, 0}, new int[][]{{1}, {}});
        s.owner[1] = me;
        s.owner[2] = 1 - me;
        s.owner[3] = GameState.NEUTRAL;
        s.instability[2] = s.regionInstability[2] = 3;
        s.inked[7] = s.regionInked[7] = true;
        s.instability[7] = s.regionInstability[7] = GameState.INK_THRESHOLD;
        s.score[0] = 123;
        s.score[1] = 456;
        s.turn = 38;
        s.maxTurns = 72;
        return s;
    }
}
