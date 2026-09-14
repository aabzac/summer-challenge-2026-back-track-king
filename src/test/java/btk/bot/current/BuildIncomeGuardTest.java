package btk.bot.current;

import btk.bot.BotFactory;
import btk.bot.Params;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.referee.MapGenerator;
import btk.tools.CgReplay;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BuildIncomeGuardTest {
    @Test
    void replacesKnownHarmfulBuildsUsingOnlyThePreTurnState() throws Exception {
        var input = getClass().getResourceAsStream("/btk/bot/build-income-guard.json");
        assertNotNull(input);
        try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            for (var value : JsonParser.parseReader(reader).getAsJsonArray()) {
                var fixture = value.getAsJsonObject();
                var replay = new CgReplay();
                replay.seed = fixture.get("refereeInput").getAsString();
                GameState state = MapGenerator.generate(replay.mapSeed().orElseThrow());
                var pathing = new Pathing(state.size);
                var stdout = fixture.getAsJsonArray("stdout");
                int turn = fixture.get("turn").getAsInt(), me = fixture.get("me").getAsInt();
                assertEquals(turn * 2, stdout.size());
                for (int t = 0; t < turn; t++) {
                    Rules.resolve(state, Action.parseLine(stdout.get(t * 2).getAsString()),
                            Action.parseLine(stdout.get(t * 2 + 1).getAsString()), pathing);
                }
                assertEquals(turn, state.turn);
                int before = incomeDiff(state, me);
                var recorded = Action.parseLine(fixture.get("recordedAction").getAsString()).stream()
                        .filter(a -> a.type == Action.Type.PLACE).toList();
                GameState bad = state.copy();
                Rules.resolve(bad, me == 0 ? recorded : List.of(), me == 1 ? recorded : List.of(), pathing);
                assertEquals(fixture.get("knownBuildDelta").getAsInt(), incomeDiff(bad, me) - before);

                // The bot receives neither the opponent's current actions nor future turns.
                var bot = BotFactory.create("current?NOISE=0,GUARD_BUILD_INCOME=1,USE_DISRUPT=0");
                bot.init(state.copy(), me);
                var actions = bot.play(state.copy(), me, 60_000);
                assertTrue(actions.stream().anyMatch(a -> a.type == Action.Type.PLACE),
                        "should find an alternative, not merely wait: " + fixture.get("gameId"));
                GameState actual = state.copy();
                var result = Rules.resolve(actual, me == 0 ? actions : List.of(),
                        me == 1 ? actions : List.of(), pathing);
                assertTrue(result.moves[me].skipped.isEmpty(), Action.format(actions));
                assertTrue(incomeDiff(actual, me) >= before,
                        fixture.get("gameId") + ": " + Action.format(actions));
                assertEquals(before, incomeDiff(state, me), "simulation must not alter the input fixture");
            }
        }
    }

    @Test
    void experimentRemainsOffUntilPromoted() {
        assertFalse(new Params().GUARD_BUILD_INCOME);
    }

    private static int incomeDiff(GameState s, int me) {
        int[] income = new int[2];
        Rules.incomePerTurn(s, income);
        return income[me] - income[1 - me];
    }
}
