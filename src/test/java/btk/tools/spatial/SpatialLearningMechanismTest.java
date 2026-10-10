package btk.tools.spatial;

import btk.bot.neural.SpatialObservation;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SpatialLearningMechanismTest {
    @Test void exactLocalAliasPersistsInAllFourFrozenPoliciesForBothSeats() {
        var f = SpatialLearningMechanism.fixture();
        for (int me = 0; me < 2; me++) {
            SpatialObservation obs = new SpatialObservation(f.root()); obs.prepare(f.root(), me);
            assertTrue(obs.buildMask[f.investment()]); assertTrue(obs.buildMask[f.alternative()]);
            assertTrue(SpatialLearningMechanism.noTownWithin(f.root(), f.investment(), 2));
            assertTrue(SpatialLearningMechanism.noTownWithin(f.root(), f.alternative(), 2));
            float[] a = SpatialLearningMechanism.patch(obs, f.investment()), b = SpatialLearningMechanism.patch(obs, f.alternative());
            assertEquals(24*7*7, a.length);
            for (int i = 0; i < a.length; i++) assertEquals(Float.floatToRawIntBits(a[i]), Float.floatToRawIntBits(b[i]));
            for (String weights : SpatialLearningMechanism.WEIGHTS) {
                var alias = SpatialLearningMechanism.alias(obs, f.investment(), f.alternative(), weights);
                assertEquals(Float.floatToRawIntBits(alias.investmentLogit()), Float.floatToRawIntBits(alias.alternativeLogit()));
                assertEquals(alias.investmentProbability(), alias.alternativeProbability());
                assertTrue(alias.investmentProbability() > 0); assertTrue(alias.tiedLegalCells() >= 2);
            }
        }
    }

    @Test void onlyOneMatchedInvestmentCompletesTheDelayedRouteThroughCore() {
        var f = SpatialLearningMechanism.fixture(); String before = SpatialLearningMechanism.identity(f.root());
        assertFalse(Rules.gameOver(f.root(), new Pathing(f.root().size)));
        for (int me = 0; me < 2; me++) {
            var investment = SpatialLearningMechanism.script(f, me, f.investment());
            var alternative = SpatialLearningMechanism.script(f, me, f.alternative());
            assertEquals(6, investment.steps().size()); assertEquals(6, alternative.steps().size());
            for (int turn = 0; turn < 6; turn++) {
                var a = investment.steps().get(turn); var b = alternative.steps().get(turn);
                assertEquals(turn+1, a.turn()); assertEquals(turn+1, b.turn());
                assertEquals(turn == 5 ? 15 : 0, a.gained()[me]); assertEquals(0, b.gained()[me]);
                assertEquals(0, a.gained()[1-me]); assertEquals(0, b.gained()[1-me]);
                assertEquals(a.acceptedPaint(), b.acceptedPaint()); assertTrue(a.acceptedPaint() <= GameState.PAINT_PER_TURN);
                if (turn > 0) assertArrayEquals(a.ownCells(), b.ownCells());
                assertFalse(a.terminal()); assertFalse(b.terminal());
                assertTrue(Float.isFinite(a.valueBefore())); assertTrue(Float.isFinite(a.valueAfter()));
                assertTrue(Float.isFinite(b.valueBefore())); assertTrue(Float.isFinite(b.valueAfter()));
                if (turn > 0) {
                    assertEquals(investment.steps().get(turn-1).valueAfter(), a.valueBefore());
                    assertEquals(alternative.steps().get(turn-1).valueAfter(), b.valueBefore());
                }
            }
            assertEquals(15, investment.finalState().score[me]); assertEquals(0, alternative.finalState().score[me]);
            assertEquals(17, investment.finalState().pairPathLen[0]); assertEquals(0, alternative.finalState().pairPathLen[0]);
            assertEquals(GameState.NO_TRACK, alternative.finalState().owner[f.investment()]);
            assertTrue(Arrays.stream(investment.finalState().pairPath[0]).anyMatch(cell -> cell == f.investment()));
        }
        assertEquals(before, SpatialLearningMechanism.identity(f.root()));
    }

    @Test void reportIsExplicitlyScriptedAndOutputCannotOverwriteExistingEvidence(@TempDir Path dir) throws Exception {
        JsonObject report = SpatialLearningMechanism.report();
        assertEquals("complete", report.get("status").getAsString());
        assertEquals(6, report.get("scriptLength").getAsInt()); assertEquals(2, report.getAsJsonArray("perspectives").size());
        for (var value : report.getAsJsonArray("perspectives")) {
            JsonObject row = value.getAsJsonObject(); assertEquals(4, row.getAsJsonArray("policies").size());
            assertTrue(row.getAsJsonObject("investment").get("scriptOnly").getAsBoolean());
            assertFalse(row.getAsJsonObject("investment").get("actualGameTerminal").getAsBoolean());
            assertEquals(15, row.getAsJsonObject("investment").get("observedScriptScoreGapChange").getAsInt());
            assertEquals(0, row.getAsJsonObject("alternative").get("observedScriptScoreGapChange").getAsInt());
        }
        Path existing = dir.resolve("preserve.json"); Files.writeString(existing, "original");
        assertThrows(IllegalArgumentException.class, () -> SpatialLearningMechanism.main(new String[]{existing.toString()}));
        assertEquals("original", Files.readString(existing));
    }
}
