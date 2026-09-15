package btk.tools;

import btk.bot.Bot;
import btk.bot.Params;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Protocol;
import btk.core.Rules;
import btk.referee.MapGenerator;
import btk.referee.Referee;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReplyStudyTest {
    private static final List<Action> WAIT = List.of(Action.waitAction());

    @Test void capturedPredictionAndProjectionRegressions() throws Exception {
        var input = getClass().getResourceAsStream("/btk/bot/reply-study.json"); assertNotNull(input);
        try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            for (var item : JsonParser.parseReader(reader).getAsJsonArray()) {
                var fixture = item.getAsJsonObject();
                GameState state = Protocol.parseInit(fixture.get("init").getAsString()).state;
                Pathing paths = new Pathing(state.size); Rules.updateConnections(state, paths);
                var history = fixture.getAsJsonArray("history");
                assertEquals(fixture.get("turn").getAsInt(), history.size());
                for (var turn : history) {
                    var actions = turn.getAsJsonArray();
                    Rules.resolve(state, Action.parseLine(actions.get(0).getAsString()),
                            Action.parseLine(actions.get(1).getAsString()), paths);
                }
                assertEquals(fixture.get("stateSha256").getAsString(), ReplyStudy.stateHash(state));
                var probe = ReplyProbe.inspect(state, fixture.get("me").getAsInt(),
                        Action.parseLine(fixture.get("opponentOracle").getAsString()), false);
                assertEquals(fixture.get("expectedSelected").getAsInt(), probe.selected());
                assertEquals(fixture.get("expectedOracleSelected").getAsInt(), probe.oracleSelected());
                assertEquals(fixture.get("expectedImmediateSelected").getAsInt(), probe.immediateSelected());
                var expected = fixture.getAsJsonArray("expectedChoices");
                assertEquals(expected.size(), probe.choices().size());
                for (int i = 0; i < expected.size(); i++) {
                    var choice = expected.get(i).getAsJsonObject(); var observed = probe.choices().get(i);
                    assertArrayEquals(ReplyStudy.JSON.fromJson(choice.get("prefix"), int[].class), observed.prefix());
                    assertEquals(choice.getAsJsonObject("predicted").get("heuristic").getAsDouble(),
                            observed.predicted().heuristic(), 1e-8);
                    assertEquals(choice.getAsJsonObject("oracle").get("heuristic").getAsDouble(),
                            observed.oracle().heuristic(), 1e-8);
                    assertEquals(choice.getAsJsonObject("oracle").get("immediateDelta").getAsInt(),
                            observed.oracle().immediateDelta());
                }
            }
        }
    }

    @Test void oracleCannotChangePredictionAndProbeMatchesRealFirstPrefix() throws Exception {
        for (int me = 0; me < 2; me++) {
            GameState state = MapGenerator.generate(1000);
            Rules.updateConnections(state, new Pathing(state.size));
            String before = ReplyStudy.stateHash(state);
            var waiting = ReplyProbe.inspect(state, me, WAIT, false);
            assertEquals(2, waiting.choices().size());
            assertTrue(waiting.amplePrefixMatches());
            var realReply = ReplyProbe.actions(state, waiting.predicted());
            var differentOracle = ReplyProbe.inspect(state, me, realReply, true);
            assertEquals(waiting.selected(), differentOracle.selected());
            assertArrayEquals(waiting.predicted().cells(), differentOracle.predicted().cells());
            assertEquals(waiting.predicted().disrupt(), differentOracle.predicted().disrupt());
            for (int i = 0; i < waiting.choices().size(); i++) {
                var a = waiting.choices().get(i); var b = differentOracle.choices().get(i);
                assertArrayEquals(a.prefix(), b.prefix()); assertArrayEquals(a.route(), b.route());
                assertEquals(a.predicted(), b.predicted()); assertEquals(a.known(), b.known());
                assertEquals(b.predicted(), b.oracle(), "actual=prediction must preserve values");
            }
            assertEquals(before, ReplyStudy.stateHash(state));
            assertEquals(0.0, ReplyProbe.parameters().NOISE);
            assertEquals(0.1, new Params().NOISE);
            assertFalse(new Params().USE_REPLY_RERANK);
        }
    }

    @Test void optimizedProbeAndResolverAgreeForCollisionAndSameTurnInk() throws Exception {
        GameState state = new GameState(5, 1, new int[]{0, 1, 2, 3, 4}, new int[5],
                new int[]{0, 4}, new int[]{0, 0}, new int[][]{{1}, {}});
        state.instability[2] = 3; state.syncRegionsFromCells();
        Rules.updateConnections(state, new Pathing(state.size));
        for (int me = 0; me < 2; me++) {
            var bot = ReplyProbe.fresh(state, me);
            int[] own = {1, 2}; var reply = new ReplyProbe.Reply(new int[]{2, 3}, 2);
            GameState expected = ReplyProbe.resolve(state, me, own, reply), actual = state.copy();
            CgDisruptStudy.call(bot, "simulatePredictedTurn", new Class<?>[]{GameState.class, int.class,
                    int[].class, int.class, int[].class, int.class, int.class, GameState.class},
                    state, me, own, own.length, reply.cells(), reply.cells().length, reply.disrupt(), actual);
            ReplyProbe.sameState(expected, actual);
            assertEquals(me, actual.owner[1]); assertEquals(1 - me, actual.owner[3]);
            assertTrue(actual.inked[2]); assertEquals(GameState.NO_TRACK, actual.owner[2]);
            assertFalse(state.inked[2]); assertEquals(0, state.turn);
        }
    }

    @Test void captureRoundTripPreservesSidesAndBeforeTurnBoundary() {
        var captured = toyCapture();
        var restored = ReplyStudy.JSON.fromJson(ReplyStudy.JSON.toJson(captured), ReplyStudy.Captured.class);
        var positions = ReplyStudy.reconstruct("fixture", restored, new int[]{0, 1, 2, 3});
        assertEquals(2, positions.size()); // t2/t3 unavailable; do not synthesize terminal positions
        assertEquals(0, positions.get(0).state().turn); assertEquals(1, positions.get(1).state().turn);
        assertEquals(1, positions.get(0).me()); assertEquals("WAIT", positions.get(0).opponent());
        assertEquals(2, positions.get(0).state().maxTurns);
        positions.get(0).state().score[0] = 50;
        assertEquals(0, positions.get(1).state().score[0]);
    }

    @Test void corruptedOrIncompleteCaptureIsRejected() {
        var corrupted = toyCapture(); corrupted.replay().turns.get(0).score[0]++;
        assertThrows(IllegalStateException.class, () -> ReplyStudy.reconstruct("bad", corrupted, new int[]{0}));
        var partial = toyCapture(); partial.replay().turns.remove(1);
        assertThrows(IllegalStateException.class, () -> ReplyStudy.reconstruct("partial", partial, new int[]{0}));
    }

    @Test void existingEvidenceCannotBeOverwritten(@TempDir Path directory) throws Exception {
        assertThrows(IllegalArgumentException.class, () -> ReplyStudy.capture(directory));
        Path report = directory.resolve("report.json"); Files.writeString(report, "existing evidence");
        assertThrows(IllegalArgumentException.class, () -> ReplyStudy.analyze(directory, report));
        assertEquals("existing evidence", Files.readString(report));
    }

    @Test void duplicateFilesCannotPretendToCompleteTheCapture(@TempDir Path directory) throws Exception {
        var manifest = new ReplyStudy.Manifest(); manifest.complete = true;
        for (int i = 0; i < 24; i++) manifest.games.add(new ReplyStudy.Entry("game-00.json", "unused"));
        Files.writeString(directory.resolve("manifest.json"), ReplyStudy.JSON.toJson(manifest));
        var failure = assertThrows(IllegalArgumentException.class,
                () -> ReplyStudy.analyze(directory, directory.resolve("result.json")));
        assertTrue(failure.getMessage().contains("Duplicate"));
    }

    @Test void missingCandidatesDoNotInflatePredictionAgreement() throws Exception {
        GameState state = new GameState(2, 1, new int[]{0, 1}, new int[2],
                new int[]{0, 1}, new int[]{0, 0}, new int[][]{{1}, {}});
        Rules.updateConnections(state, new Pathing(state.size));
        var probe = ReplyProbe.inspect(state, 0, WAIT, true);
        assertTrue(probe.choices().isEmpty()); assertNull(probe.predicted());
        var summary = new ReplyStudy.Summary(); summary.add(probe);
        assertEquals(1, summary.positions); assertEquals(1, summary.noCandidate);
        assertEquals(0, summary.candidateStates); assertEquals(0, summary.placementExact);
    }

    @Test void impossibleHeuristicsStayMissingAndTiesKeepFirstChoice() {
        var rejected = new ReplyProbe.Value(null, 5);
        var finite = new ReplyProbe.Value(0.0, 0);
        assertEquals(1, ReplyProbe.best(new ReplyProbe.Value[]{rejected, finite}));
        assertEquals(0, ReplyProbe.best(new ReplyProbe.Value[]{finite, finite}));
        assertEquals(0, ReplyProbe.best(new ReplyProbe.Value[]{rejected, rejected}));
        assertTrue(ReplyStudy.JSON.toJson(rejected).contains("null"));
    }

    private static ReplyStudy.Captured toyCapture() {
        GameState state = new GameState(3, 1, new int[]{0, 1, 2}, new int[3],
                new int[]{0, 2}, new int[]{0, 0}, new int[][]{{1}, {}});
        state.maxTurns = 2;
        Bot waiter = new Bot() {
            public String name() { return "wait-fixture"; }
            public List<Action> play(GameState s, int me, long ms) { return WAIT; }
        };
        var result = new Referee().play(state, waiter, waiter, 1, true);
        return new ReplyStudy.Captured(1, 1, "fixture", Protocol.initBlock(state, 0), result.replay);
    }
}
