package btk.tools;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class V09ForecastParityTest {
    @Test void permitsOnlyDeclaredNonsemanticDifferencesAndDoesNotMutateInput() {
        JsonObject row = fixture();
        JsonObject on = arm(row, "on");
        on.addProperty("artifact", "new-generated-file");
        step(row, "on", 0).addProperty("searchWallMs", 0.01);
        step(row, "on", 0).getAsJsonObject("diagnostics").addProperty("deadlineReusedBoards", 8);
        String before = row.toString();
        assertDoesNotThrow(() -> V09ForecastParity.verify(row));
        assertDoesNotThrow(() -> V09ForecastParity.verify(row.getAsJsonObject("result")));
        assertEquals(before, row.toString());
    }

    @Test void detectsConstructionTargetGuardMemoryOpponentAndExistingCounterDifferences() {
        JsonObject row = fixture();
        step(row, "on", 0).getAsJsonObject("ownAction").getAsJsonArray("cells").set(0, new com.google.gson.JsonPrimitive(9));
        assertMismatch(row, "ownAction.cells[0]");
        row = fixture();
        step(row, "on", 0).getAsJsonObject("opponentAction").addProperty("target", 3);
        assertMismatch(row, "opponentAction.target");
        for (String field : new String[]{"deadlineWCompleted", "deadlinePendingDue", "deadlineExtraTurns", "simulatedTurns"}) {
            row = fixture();
            step(row, "on", 0).getAsJsonObject("diagnostics").addProperty(field, 99);
            assertMismatch(row, field);
        }
        row = fixture();
        step(row, "on", 0).getAsJsonObject("diagnostics").addProperty("deadlineStatus", "intervened");
        assertMismatch(row, "deadlineStatus");
    }

    @Test void comparesForecastDoublesBitExactlyIncludingSignedZeroAndNormalizedNull() {
        JsonObject row = fixture();
        step(row, "on", 0).getAsJsonObject("diagnostics").getAsJsonArray("deadlineWValues")
                .set(0, new com.google.gson.JsonPrimitive(Math.nextUp(1.25)));
        assertMismatch(row, "double bits mismatch");
        row = fixture();
        step(row, "on", 0).getAsJsonObject("diagnostics").getAsJsonArray("deadlineWValues")
                .set(1, new com.google.gson.JsonPrimitive(0.0));
        assertMismatch(row, "null mismatch");
        row = fixture();
        step(row, "on", 0).getAsJsonObject("diagnostics").addProperty("selectedValue", -0.0);
        assertMismatch(row, "double bits mismatch");
    }

    @Test void rejectsEvenMutuallyIdenticalBrokenStateChainsAndTerminalMetadata() {
        JsonObject row = fixture();
        for (String side : new String[]{"off", "on"}) step(row, side, 1).addProperty("stateBeforeSha256", "wrong");
        assertMismatch(row, "broken state chain");
        row = fixture();
        for (String side : new String[]{"off", "on"}) arm(row, side).addProperty("terminalOutcome", "WIN");
        assertMismatch(row, "wrong terminal label");
        row = fixture();
        for (String side : new String[]{"off", "on"}) arm(row, side).addProperty("finalScoreDifference", 7);
        assertMismatch(row, "wrong final score difference");
    }

    private static void assertMismatch(JsonObject row, String message) {
        var failure = assertThrows(IllegalStateException.class, () -> V09ForecastParity.verify(row));
        assertTrue(failure.getMessage().contains(message), failure.getMessage());
    }

    private static JsonObject arm(JsonObject row, String side) {
        return row.getAsJsonObject("result").getAsJsonArray("comparisons").get(0).getAsJsonObject().getAsJsonObject(side);
    }

    private static JsonObject step(JsonObject row, String side, int i) {
        return arm(row, side).getAsJsonArray("turns").get(i).getAsJsonObject();
    }

    private static JsonObject fixture() {
        JsonObject arm = JsonParser.parseString("""
                {"artifact":"old-generated-file","complete":true,"steps":2,"terminalOutcome":"NONTERMINAL",
                 "finalScoreDifference":3,"finalStateSha256":"c","turns":[
                  {"offset":0,"turn":38,"ownAction":{"cells":[4],"target":-1,"action":"PLACE_TRACKS 4 0"},
                   "opponentAction":{"cells":[],"target":-1,"action":"WAIT"},"searchWallMs":3.5,
                   "diagnostics":{"deadlineStatus":"armed","deadlinePendingDue":39,"deadlineWCompleted":1,
                     "deadlineWValues":[1.25,null],"deadlineFirstTarget":[2,-1],"deadlineFirstDue":[39,-1],
                     "deadlineWTerminal":[0,0],"deadlineExtraTurns":8,"simulatedTurns":16,"selectedValue":0.0},
                   "score":[5,3],"inkedRegions":[],"terminal":false,"stateBeforeSha256":"a","stateSha256":"b"},
                  {"offset":1,"turn":39,"ownAction":{"cells":[],"target":-1,"action":"WAIT"},
                   "opponentAction":{"cells":[],"target":-1,"action":"WAIT"},"searchWallMs":3.0,
                   "diagnostics":{"deadlineStatus":"waiting","deadlinePendingDue":39},
                   "score":[7,4],"inkedRegions":[],"terminal":false,"stateBeforeSha256":"b","stateSha256":"c"}]}
                """).getAsJsonObject();
        JsonObject row = JsonParser.parseString("""
                {"result":{"initialStateSha256":"a","me":0,"horizon":2,"pinnedTarget":2,
                  "comparisons":[{"opponentRegime":"PURE_WAIT","nativeReply":-1}]}}
                """).getAsJsonObject();
        JsonObject pair = row.getAsJsonObject("result").getAsJsonArray("comparisons").get(0).getAsJsonObject();
        pair.add("off", arm);
        pair.add("on", arm.deepCopy());
        return row;
    }
}
