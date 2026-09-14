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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarlyDisruptFallbackTest {
    @Test
    void readyTargetSurvivesAnAlreadyExpiredNormalDisruptSearch() throws Exception {
        var input = getClass().getResourceAsStream("/btk/bot/sabotage-starvation.json");
        assertNotNull(input);
        try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            int checked = 0;
            for (var element : JsonParser.parseReader(reader).getAsJsonArray()) {
                var fixture = element.getAsJsonObject();
                var replay = new CgReplay();
                replay.seed = fixture.get("refereeInput").getAsString();
                var state = MapGenerator.generate(replay.mapSeed().orElseThrow());
                var pathing = new Pathing(state.size);
                var stdout = fixture.getAsJsonArray("stdout");
                int turn = fixture.get("turn").getAsInt();
                for (int t = 0; t < turn; t++) {
                    Rules.resolve(state, Action.parseLine(stdout.get(t * 2).getAsString()),
                            Action.parseLine(stdout.get(t * 2 + 1).getAsString()), pathing);
                }
                int me = fixture.get("myId").getAsInt();
                int region = fixture.get("expectedRegion").getAsInt();
                if (state.regionInstability[region] != 3) continue;
                checked++;

                Params baselineParams = new Params("NOISE=0");
                var baseline = new EvalBot(baselineParams);
                baseline.init(state.copy(), me);
                assertFalse(baseline.play(state.copy(), me, 0).stream()
                        .anyMatch(a -> a.type == Action.Type.DISRUPT));

                Params candidateParams = new Params("NOISE=0,EARLY_DISRUPT_FALLBACK=1");
                var candidate = new EvalBot(candidateParams);
                candidate.init(state.copy(), me);
                var actions = candidate.play(state.copy(), me, 0);
                assertTrue(actions.stream().anyMatch(a -> a.type == Action.Type.DISRUPT
                                && a.regionId == region),
                        fixture.get("gameId") + ": " + Action.format(actions));
            }
            assertTrue(checked > 0);
        }
    }

    @Test
    void experimentIsDefaultOff() {
        assertFalse(new Params().EARLY_DISRUPT_FALLBACK);
    }
}
