package btk.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigInteger;

/** Exact, untimed JSON parity gate for previous-ON versus optimized-ON generated episodes. */
public final class V09ForecastParity {
    private V09ForecastParity() {}

    /**
     * Accepts the study row containing result.comparisons, or its result object directly.
     * Only artifact paths, measured elapsed time, and the new reuse counter are excluded.
     * Every pre-existing diagnostic, including work/turn counters, must remain equal.
     */
    public static void verify(JsonObject row) {
        JsonObject episode = row.has("result") ? row.getAsJsonObject("result") : row;
        JsonArray pairs = episode.getAsJsonArray("comparisons");
        require(pairs != null && !pairs.isEmpty(), "result.comparisons", "missing comparisons");
        for (int i = 0; i < pairs.size(); i++) {
            JsonObject pair = pairs.get(i).getAsJsonObject();
            String path = "result.comparisons[" + i + "]";
            JsonObject previous = pair.getAsJsonObject("off"), optimized = pair.getAsJsonObject("on");
            validateArm(episode, previous, path + ".off");
            validateArm(episode, optimized, path + ".on");
            equal(semanticArm(previous), semanticArm(optimized), path);
        }
    }

    private static JsonObject semanticArm(JsonObject source) {
        JsonObject result = source.deepCopy();
        result.remove("artifact");
        for (JsonElement element : result.getAsJsonArray("turns")) {
            JsonObject turn = element.getAsJsonObject();
            turn.remove("searchWallMs");
            turn.getAsJsonObject("diagnostics").remove("deadlineReusedBoards");
        }
        return result;
    }

    /** Check report continuity too, so equal but malformed arms cannot silently pass. */
    private static void validateArm(JsonObject episode, JsonObject arm, String path) {
        require(arm != null, path, "missing arm");
        require(arm.get("complete").getAsBoolean(), path, "incomplete arm");
        JsonArray turns = arm.getAsJsonArray("turns");
        int horizon = episode.get("horizon").getAsInt();
        require(turns.size() > 0 && turns.size() <= horizon, path + ".turns", "invalid length");
        require(arm.get("steps").getAsInt() == turns.size(), path + ".steps", "length mismatch");
        String previous = episode.get("initialStateSha256").getAsString();
        int firstTurn = turns.get(0).getAsJsonObject().get("turn").getAsInt();
        for (int i = 0; i < turns.size(); i++) {
            JsonObject turn = turns.get(i).getAsJsonObject();
            String tp = path + ".turns[" + i + "]";
            require(turn.get("offset").getAsInt() == i, tp + ".offset", "nonconsecutive offset");
            require(turn.get("turn").getAsInt() == firstTurn + i, tp + ".turn", "nonconsecutive turn");
            require(previous.equals(turn.get("stateBeforeSha256").getAsString()), tp, "broken state chain");
            previous = turn.get("stateSha256").getAsString();
            require(i == turns.size() - 1 || !turn.get("terminal").getAsBoolean(), tp, "continued after terminal");
        }
        require(previous.equals(arm.get("finalStateSha256").getAsString()), path, "wrong final state");
        JsonObject last = turns.get(turns.size() - 1).getAsJsonObject();
        JsonArray score = last.getAsJsonArray("score");
        int me = episode.get("me").getAsInt();
        require(me == 0 || me == 1, path, "invalid seat");
        require(score.size() == 2, path, "invalid score");
        long difference = score.get(me).getAsLong() - score.get(1 - me).getAsLong();
        require(difference == arm.get("finalScoreDifference").getAsLong(), path, "wrong final score difference");
        boolean terminal = last.get("terminal").getAsBoolean();
        String outcome = terminal ? difference > 0 ? "WIN" : difference < 0 ? "LOSS" : "DRAW" : "NONTERMINAL";
        require(outcome.equals(arm.get("terminalOutcome").getAsString()), path, "wrong terminal label");
        require(terminal || turns.size() == horizon, path, "truncated nonterminal arm");
    }

    private static void equal(JsonElement a, JsonElement b, String path) {
        if (a == null || b == null) {
            require(a == b, path, "missing field");
        } else if (a.isJsonNull() || b.isJsonNull()) {
            require(a.isJsonNull() && b.isJsonNull(), path, "null mismatch");
        } else if (a.isJsonObject() && b.isJsonObject()) {
            JsonObject aa = a.getAsJsonObject(), bb = b.getAsJsonObject();
            require(aa.keySet().equals(bb.keySet()), path, "field set mismatch");
            for (String key : aa.keySet()) equal(aa.get(key), bb.get(key), path + "." + key);
        } else if (a.isJsonArray() && b.isJsonArray()) {
            JsonArray aa = a.getAsJsonArray(), bb = b.getAsJsonArray();
            require(aa.size() == bb.size(), path, "array length mismatch");
            for (int i = 0; i < aa.size(); i++) equal(aa.get(i), bb.get(i), path + "[" + i + "]");
        } else if (a.isJsonPrimitive() && b.isJsonPrimitive()) {
            JsonPrimitive aa = a.getAsJsonPrimitive(), bb = b.getAsJsonPrimitive();
            if (aa.isNumber() && bb.isNumber()) {
                String x = aa.getAsString(), y = bb.getAsString();
                if (integral(x) && integral(y)) {
                    require(new BigInteger(x).equals(new BigInteger(y)), path, "integer mismatch");
                } else {
                    double da = aa.getAsDouble(), db = bb.getAsDouble();
                    require(Double.isFinite(da) && Double.isFinite(db), path, "nonfinite values must be normalized to null");
                    require(Double.doubleToLongBits(da) == Double.doubleToLongBits(db), path, "double bits mismatch");
                }
            } else {
                require(aa.isBoolean() == bb.isBoolean() && aa.isString() == bb.isString()
                        && aa.equals(bb), path, "primitive mismatch");
            }
        } else {
            require(false, path, "JSON type mismatch");
        }
    }

    private static boolean integral(String value) {
        return value.indexOf('.') < 0 && value.indexOf('e') < 0 && value.indexOf('E') < 0
                && !value.equals("NaN") && !value.contains("Infinity");
    }

    private static void require(boolean condition, String path, String message) {
        if (!condition) throw new IllegalStateException("Forecast parity failed at " + path + ": " + message);
    }
}
