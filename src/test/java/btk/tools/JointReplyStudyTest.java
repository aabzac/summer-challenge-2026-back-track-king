package btk.tools;

import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.referee.MapGenerator;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JointReplyStudyTest {
    private static final List<Action> WAIT = List.of(Action.waitAction());

    @Test void capturedJointChoicesRemainReproducible() throws Exception {
        var input = getClass().getResourceAsStream("/btk/bot/joint-reply-study.json"); assertNotNull(input);
        try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            for (var item : JsonParser.parseReader(reader).getAsJsonArray()) {
                var fixture = item.getAsJsonObject();
                GameState state = MapGenerator.generate(fixture.get("seed").getAsLong());
                Pathing paths = new Pathing(state.size); Rules.updateConnections(state, paths);
                for (var turn : fixture.getAsJsonArray("history")) {
                    var actions = turn.getAsJsonArray();
                    Rules.resolve(state, Action.parseLine(actions.get(0).getAsString()),
                            Action.parseLine(actions.get(1).getAsString()), paths);
                }
                assertEquals(fixture.get("turn").getAsInt(), state.turn);
                assertEquals(fixture.get("stateSha256").getAsString(), ReplyStudy.stateHash(state));
                int me = fixture.get("me").getAsInt();
                var prior = ReplyProbe.inspect(state, me, Action.parseLine(fixture.get("opponentOracle").getAsString()), false);
                var joint = JointReplyStudy.inspect(state, me, prior);
                assertEquals(fixture.get("expectedPreBuildTarget").getAsInt(), joint.preBuildTarget());
                assertArrayEquals(ReplyStudy.JSON.fromJson(fixture.get("expectedPrefixTargets"), int[].class), joint.prefixTargets());
                assertEquals(fixture.get("expectedPriorSelected").getAsInt(), joint.priorSelected());
                assertEquals(fixture.get("expectedPredictedSelected").getAsInt(), joint.scenarios().get("predicted").adaptedSelected());
                for (String key : List.of("expectedPredictedFixed", "expectedPredictedAdapted", "expectedOracleAdapted")) {
                    var expected = ReplyStudy.JSON.fromJson(fixture.get(key), ReplyProbe.Value[].class);
                    var actual = switch (key) {
                        case "expectedPredictedFixed" -> joint.scenarios().get("predicted").fixed();
                        case "expectedPredictedAdapted" -> joint.scenarios().get("predicted").adapted();
                        default -> joint.scenarios().get("oracle").adapted();
                    };
                    assertEquals(expected.length, actual.length);
                    for (int i = 0; i < expected.length; i++) JointReplyStudy.sameValue(expected[i], actual[i]);
                }
            }
        }
    }

    @Test void ownTargetsAndPredictedJointSelectionCannotSeeOracle() throws Exception {
        for (int me = 0; me < 2; me++) {
            GameState state = MapGenerator.generate(1000);
            Rules.updateConnections(state, new Pathing(state.size));
            String before = ReplyStudy.stateHash(state);
            var waiting = ReplyProbe.inspect(state, me, WAIT, false);
            var other = ReplyProbe.inspect(state, me, ReplyProbe.actions(state, waiting.predicted()), true);
            var a = JointReplyStudy.inspect(state, me, waiting);
            var b = JointReplyStudy.inspect(state, me, other);
            assertNotNull(a); assertNotNull(b);
            assertEquals(a.preBuildTarget(), b.preBuildTarget());
            assertArrayEquals(a.prefixTargets(), b.prefixTargets());
            for (String scenario : List.of("known", "predicted")) {
                assertEquals(ReplyStudy.JSON.toJson(a.scenarios().get(scenario)),
                        ReplyStudy.JSON.toJson(b.scenarios().get(scenario)));
            }
            assertEquals(before, ReplyStudy.stateHash(state));
            // This fixture spends all three paint on its first prefix: no leftover-build ambiguity.
            assertEquals(3, Arrays.stream(waiting.choices().get(waiting.selected()).prefix())
                    .map(state::placeCost).sum());
            assertEquals(waiting.ample().accepted().disrupt(), a.prefixTargets()[waiting.selected()]);
        }
    }

    @Test void bothDisruptsInkAfterPlacementsAndBothSidesMatchCore() throws Exception {
        GameState state = new GameState(5, 1, new int[]{0, 1, 2, 3, 4}, new int[5],
                new int[]{0, 4}, new int[]{0, 0}, new int[][]{{1}, {}});
        state.instability[2] = 2; state.syncRegionsFromCells();
        Rules.updateConnections(state, new Pathing(state.size));
        String before = ReplyStudy.stateHash(state);
        int[] prefix = {1, 2}, route = {0, 1, 2, 3, 4};
        var reply = new ReplyProbe.Reply(new int[]{2, 3}, 2);
        for (int me = 0; me < 2; me++) {
            var bot = ReplyProbe.fresh(state, me);
            ((int[]) CgDisruptStudy.field(bot, "buildBeamLen"))[0] = route.length;
            ((int[]) CgDisruptStudy.field(bot, "buildBeamPrefixLen"))[0] = prefix.length;
            ((int[]) CgDisruptStudy.field(bot, "buildBeamMaxInst"))[0] = 2;
            System.arraycopy(route, 0, ((int[][]) CgDisruptStudy.field(bot, "buildBeamPath"))[0], 0, route.length);
            System.arraycopy(prefix, 0, ((int[][]) CgDisruptStudy.field(bot, "buildBeamPrefix"))[0], 0, prefix.length);
            var noHit = ReplyProbe.scenario(bot, state, me, 1, reply);
            assertNotNull(noHit[0].heuristic());
            var joint = ReplyProbe.scenario(bot, state, me, 1, reply, new int[]{2});
            assertNull(joint[0].heuristic(), "The route is destroyed by the joint fourth hit");
            var after = ReplyProbe.resolve(state, me, prefix, 2, reply);
            assertTrue(after.regionInked[2]); assertEquals(4, after.regionInstability[2]);
            assertEquals(GameState.NO_TRACK, after.owner[2]);
            assertEquals(me, after.owner[1]); assertEquals(1 - me, after.owner[3]);
            assertTrue(Rules.gameOver(after, new Pathing(after.size)));
            JointReplyStudy.sameValue(noHit[0], ReplyProbe.scenario(bot, state, me, 1, reply, new int[]{-1})[0]);
        }
        assertEquals(before, ReplyStudy.stateHash(state));
    }

    @Test void changedCandidateCannotBeSilentlyReanalyzed() throws Exception {
        GameState state = MapGenerator.generate(1000);
        Rules.updateConnections(state, new Pathing(state.size));
        var prior = ReplyProbe.inspect(state, 0, WAIT, false);
        prior.choices().get(0).prefix()[0] = -1;
        assertThrows(IllegalStateException.class, () -> JointReplyStudy.inspect(state, 0, prior));
    }

    @Test void mismatchedPositionCannotConsumeAnotherStatesOracle() throws Exception {
        GameState state = MapGenerator.generate(1000);
        Rules.updateConnections(state, new Pathing(state.size));
        var prior = ReplyProbe.inspect(state, 0, WAIT, false);
        var position = new ReplyStudy.Position("game-00.json", 1000, 0, "v05", state, "WAIT", "WAIT");
        var sample = new ReplyStudy.Sample("game-00.json", 1000, 0, "v05", state.turn, state.height,
                ReplyStudy.stateHash(state), "WAIT", "WAIT", prior);
        assertDoesNotThrow(() -> JointReplyStudy.validateSample(position, sample));
        var wrongSide = new ReplyStudy.Position(position.game(), position.seed(), 1, position.policy(), state, "WAIT", "WAIT");
        assertThrows(IllegalArgumentException.class, () -> JointReplyStudy.validateSample(wrongSide, sample));
    }

    @Test void tiesMissingValuesAndNoCandidatesDoNotCreateImprovements() {
        var values = new ReplyProbe.Value[]{new ReplyProbe.Value(5.0, 0), new ReplyProbe.Value(5.0, 10)};
        assertEquals(1, JointReplyStudy.bestKeepingTie(values, 1));
        assertFalse(JointReplyStudy.better(values, 0, 1));
        assertThrows(IllegalStateException.class,
                () -> JointReplyStudy.sameValue(new ReplyProbe.Value(Double.NaN, 0), values[0]));
        values[0] = new ReplyProbe.Value(null, 0);
        assertEquals(1, JointReplyStudy.bestKeepingTie(values, 0));
        var counts = new JointReplyStudy.Counts(); counts.add(null);
        assertEquals(1, counts.positions); assertEquals(0, counts.candidateStates);
        assertEquals(0, counts.oracleEqual); assertTrue(counts.rankingChanges.isEmpty());
    }

    @Test void previousEvidenceCannotBeOverwritten(@TempDir Path directory) throws Exception {
        Path out = directory.resolve("joint-report.json"); Files.writeString(out, "prior evidence");
        assertThrows(IllegalArgumentException.class, () -> JointReplyStudy.analyze(directory, directory, out));
        assertEquals("prior evidence", Files.readString(out));
    }
}
