package btk.tools;

import btk.bot.BotFactory;
import btk.bot.Params;
import btk.bot.current.EvalBot;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.referee.MapGenerator;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class CgDisruptStudyTest {
    private JsonArray fixtures() throws Exception {
        var input = getClass().getResourceAsStream("/btk/bot/disrupt-valuation.json");
        assertNotNull(input);
        try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonArray();
        }
    }

    private GameState state(JsonObject fixture) {
        var replay = new CgReplay(); replay.seed = fixture.get("refereeInput").getAsString();
        GameState state = MapGenerator.generate(replay.mapSeed().orElseThrow());
        Pathing paths = new Pathing(state.size); Rules.updateConnections(state, paths);
        var stdout = fixture.getAsJsonArray("stdout");
        int turn = fixture.get("turn").getAsInt();
        assertEquals(turn * 2, stdout.size(), "fixture excludes opponent's selected-turn/future actions");
        for (int t = 0; t < turn; t++) Rules.resolve(state,
                Action.parseLine(stdout.get(t * 2).getAsString()),
                Action.parseLine(stdout.get(t * 2 + 1).getAsString()), paths);
        return state;
    }

    @Test
    void capturedKnownStateChoicesAndValueComponents() throws Exception {
        for (var item : fixtures()) {
            var fixture = item.getAsJsonObject(); var state = state(fixture);
            var before = state.copy();
            Params params = BotFactory.parameters("v05");
            var result = CgDisruptStudy.recordedBuild(state, fixture.get("me").getAsInt(),
                    Action.parseLine(fixture.get("recordedAction").getAsString()), params);
            assertEquals(fixture.get("expectedTarget").getAsInt(), result.selected());
            assertArrayEquals(before.owner, state.owner);
            assertArrayEquals(before.score, state.score);
            assertArrayEquals(before.regionInstability, state.regionInstability);
            assertEquals(before.turn, state.turn);
            assertEquals(0.1, params.NOISE);
            if (fixture.get("gameId").getAsLong() == 903970829L && state.turn == 43) {
                var ready = result.targets().stream().filter(t -> t.region() == 5).findFirst().orElseThrow();
                var chosen = result.targets().stream().filter(t -> t.region() == 7).findFirst().orElseThrow();
                assertEquals(1, ready.needed()); assertEquals(2, chosen.needed());
                assertEquals(295, ready.ownIncomeLoss()); assertEquals(472, ready.opponentIncomeLoss());
                assertEquals(6608.1, ready.value(), 1e-6);
                assertEquals(9256.9, chosen.value(), 1e-6);
            }
        }
    }

    @Test
    void fullInspectionDoesNotConsumeRngOrPermanentlyChangeNoise() throws Exception {
        var fixture = fixtures().get(0).getAsJsonObject(); var state = state(fixture);
        int me = fixture.get("me").getAsInt();
        Params params = BotFactory.parameters("v05");
        EvalBot inspected = new EvalBot(params), control = new EvalBot(params.copy());
        inspected.init(state, me); control.init(state, me);
        CgDisruptStudy.evaluate(inspected, state, me);
        assertEquals(0.1, params.NOISE);
        assertEquals(CgDisruptStudy.call(control, "noise", new Class<?>[0]),
                CgDisruptStudy.call(inspected, "noise", new Class<?>[0]));
    }

    @Test
    void distinguishesExpiredScanFromFullScanWithoutTimingSensitiveAssertions() throws Exception {
        var fixture = fixtures().get(0).getAsJsonObject(); var state = state(fixture);
        int me = fixture.get("me").getAsInt();
        EvalBot bot = new EvalBot(BotFactory.parameters("v05")); bot.init(state.copy(), me);
        bot.play(state.copy(), me, 0);
        assertEquals(false, CgDisruptStudy.field(bot, "disruptSearchComplete"));
        assertTrue(CgDisruptStudy.evaluate(bot, state, me).selected() >= 0);
        assertEquals(true, CgDisruptStudy.field(bot, "disruptSearchComplete"));
    }
}
