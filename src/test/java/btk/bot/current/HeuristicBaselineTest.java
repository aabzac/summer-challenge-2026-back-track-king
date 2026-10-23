package btk.bot.current;

import btk.bot.Params;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.referee.MapGenerator;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class HeuristicBaselineTest {
    @Test void disabledDetourPreservesPreExperimentActionHistory() throws Exception {
        // Captured BEFORE detour code, HeuristicBot SHA-256 89873c737617856775ddf96982055e58893a7b12764763131a3036d81ec8fc51.
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (int seed = 24020; seed <= 24022; seed++) {
            GameState s = MapGenerator.generate(seed);
            Params p = new Params(btk.bot.LiveTargetExperimentParams.V10_OFF + ",USE_HEURISTIC_POLICY=1,HEURISTIC_EMERGENCY_STOP=0");
            HeuristicBot a = new HeuristicBot(p), b = new HeuristicBot(p);
            a.init(s, 0); b.init(s, 1);
            Pathing paths = new Pathing(s.size);
            for (int turn = 0; turn < 50 && !Rules.gameOver(s, paths); turn++) {
                var aa = a.play(s, 0, 0); var bb = b.play(s, 1, 0);
                String line = seed + ":" + turn + ":" + Action.format(aa) + "|" + Action.format(bb) + "\n";
                digest.update(line.getBytes(StandardCharsets.UTF_8));
                Rules.resolve(s, aa, bb, paths);
            }
        }
        assertEquals("c7c5273fb330afb0ed6dad99879eeaa2471c79511f1ca539016aa754e432109b",
                HexFormat.of().formatHex(digest.digest()));
    }
}
