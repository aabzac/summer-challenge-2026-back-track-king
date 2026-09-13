package btk.bot.current;

import btk.bot.Params;
import btk.core.Action;
import btk.core.Pathing;
import btk.core.Rules;
import btk.referee.MapGenerator;
import btk.tools.CgReplay;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class SabotageRegressionTest {
    @Test
    void reservedPhaseFindsTargetsOnCapturedLargeMapFailureStates() throws Exception {
        // Exact CG map seeds and the recorded first 40 simultaneous turns, not hypothetical
        // continuations. Full source games passed cgValidate before extracting this fixture.
        var input = getClass().getResourceAsStream("/btk/bot/sabotage-starvation.json");
        assertNotNull(input);
        try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            for (var element : JsonParser.parseReader(reader).getAsJsonArray()) {
                var fixture = element.getAsJsonObject();
                var replay = new CgReplay();
                replay.seed = fixture.get("refereeInput").getAsString();
                var state = MapGenerator.generate(replay.mapSeed().orElseThrow());
                var pathing = new Pathing(state.size);
                var stdout = fixture.getAsJsonArray("stdout");
                int turn = fixture.get("turn").getAsInt();
                assertEquals(turn * 2, stdout.size());
                for (int t = 0; t < turn; t++) {
                    Rules.resolve(state, Action.parseLine(stdout.get(t * 2).getAsString()),
                            Action.parseLine(stdout.get(t * 2 + 1).getAsString()), pathing);
                }
                assertEquals(turn, state.turn);
                assertTrue(state.height >= 19);
                int region = fixture.get("expectedRegion").getAsInt();
                int me = fixture.get("myId").getAsInt();
                assertFalse(state.regionInked[region]);
                assertFalse(state.regionHasTown(region));
                Params params = new Params();
                params.NOISE = 0;
                // Make the build deadline already expired without racing JIT/CI timing.
                // Real 36-ms scheduling is checked separately by probes and procCheck.
                params.DISRUPT_RESERVE_MS = 60_000;
                var bot = new EvalBot(params);
                bot.init(state.copy(), me);
                var actions = bot.play(state.copy(), me, 60_000);
                assertTrue(actions.stream().anyMatch(a -> a.type == Action.Type.DISRUPT
                        && a.regionId == region), fixture.get("gameId") + ": " + Action.format(actions));
                assertFalse(actions.stream().anyMatch(a -> a.type == Action.Type.PLACE));
            }
        }
    }
}
