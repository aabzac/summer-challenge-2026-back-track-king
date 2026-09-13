package btk.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CgValidateTest {
    @TempDir Path temp;

    @Test
    void emptyOrUnparseableInputCannotReportSuccess() throws Exception {
        assertThrows(IllegalStateException.class, () -> CgValidate.main(new String[]{temp.toString()}));
        Files.writeString(temp.resolve("bad.json"), "{}");
        assertThrows(IllegalStateException.class, () -> CgValidate.main(new String[]{temp.toString()}));
    }

    @Test
    void validReplayPassesButScoreDivergenceFailsTheCommand() throws Exception {
        // Both players disrupt the corridor twice: instability 2, then 4 ends the game.
        JsonObject view = new JsonObject();
        JsonObject global = new JsonObject();
        global.addProperty("graphics", "3|1|3|0 0|1 0|2 0|2|0 0 0 1|1 2 0 x");
        view.add("global", global);
        JsonArray frames = new JsonArray();
        frames.add(frame(view, false, -1, null));
        frames.add(frame(new JsonObject(), false, 0, "DISRUPT 1"));
        JsonObject firstTurn = new JsonObject();
        firstTurn.addProperty("graphics", "0");
        frames.add(frame(firstTurn, true, 1, "DISRUPT 1"));
        frames.add(frame(new JsonObject(), false, 0, "DISRUPT 1"));
        JsonObject end = new JsonObject();
        end.addProperty("graphics", "1|5|0|0|1");
        JsonArray scores = new JsonArray();
        scores.add(0);
        scores.add(0);
        JsonArray endScreen = new JsonArray();
        endScreen.add(scores);
        end.add("endScreen", endScreen);
        frames.add(frame(end, true, 1, "DISRUPT 1"));
        JsonObject root = new JsonObject();
        root.add("frames", frames);
        Path replay = temp.resolve("two-turn.json");
        Files.writeString(replay, root.toString());
        assertDoesNotThrow(() -> CgValidate.main(new String[]{replay.toString()}));
        scores.set(0, new JsonPrimitive(1));
        frames.set(4, frame(end, true, 1, "DISRUPT 1"));
        Files.writeString(replay, root.toString());
        assertThrows(IllegalStateException.class, () -> CgValidate.main(new String[]{replay.toString()}));
    }

    @Test
    void lateStdoutFromTimedOutPlayerIsNotApplied() throws Exception {
        JsonObject init = new JsonObject();
        JsonObject global = new JsonObject();
        global.addProperty("graphics", "3|1|3|0 0|1 0|2 0|2|0 0 0 1|1 2 0 x");
        init.add("global", global);
        JsonArray frames = new JsonArray();
        frames.add(frame(init, false, -1, null));
        frames.add(frame(new JsonObject(), false, 0, "PLACE_TRACKS 1 0"));

        JsonObject end = new JsonObject();
        end.addProperty("graphics", "2|1|0|0|1 1 0 1|3|0|0|1 1 0 1");
        JsonArray scores = new JsonArray();
        scores.add(-1);
        scores.add(1);
        JsonArray endScreen = new JsonArray();
        endScreen.add(scores);
        end.add("endScreen", endScreen);
        JsonObject last = frame(end, true, 1, "PLACE_TRACKS 1 0");
        last.addProperty("summary", "$0 has not provided 1 lines in time\n");
        frames.add(last);

        JsonObject root = new JsonObject();
        root.add("frames", frames);
        Path replay = temp.resolve("timeout.json");
        Files.writeString(replay, root.toString());

        assertDoesNotThrow(() -> CgValidate.main(new String[]{replay.toString()}));
        CgReplay parsed = CgReplay.load(replay);
        assertEquals("$0 has not provided 1 lines in time", parsed.failure(0, 0));
        assertNull(parsed.failure(0, 1));
        CgValidate.Outcome outcome = CgValidate.validate(parsed, false);
        assertArrayEquals(new int[]{-1, 1}, outcome.finalLocal);
        assertEquals(-1, outcome.replay.turns.get(0).score[0]);
        assertEquals("WAIT", outcome.replay.turns.get(0).actions[0]);
        assertEquals("$0 has not provided 1 lines in time", outcome.replay.meta.errors[0]);
    }

    private static JsonObject frame(JsonObject view, boolean keyframe, int agent, String stdout) {
        JsonObject frame = new JsonObject();
        frame.addProperty("view", " 0\n" + view);
        frame.addProperty("keyframe", keyframe);
        frame.addProperty("agentId", agent);
        if (stdout != null) frame.addProperty("stdout", stdout);
        return frame;
    }
}
