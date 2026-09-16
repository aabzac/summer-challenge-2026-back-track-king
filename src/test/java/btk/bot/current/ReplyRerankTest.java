package btk.bot.current;

import btk.bot.BotFactory;
import btk.bot.Params;
import btk.core.Action;
import btk.core.Pathing;
import btk.core.Rules;
import btk.referee.MapGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplyRerankTest {
    @Test
    void reranksDistinctPrefixesAgainstLikelySimultaneousReplies() {
        for (int me = 0; me < 2; me++) {
            var state = MapGenerator.generate(1000);
            var baseline = BotFactory.create("current?USE_ROLLOUT_POLICY=0,NOISE=0,USE_REPLY_RERANK=0,USE_DISRUPT=0");
            var candidate = BotFactory.create("current?USE_ROLLOUT_POLICY=0,NOISE=0,USE_REPLY_RERANK=1,USE_DISRUPT=0");
            baseline.init(state.copy(), me);
            candidate.init(state.copy(), me);
            assertEquals("PLACE_TRACKS 1 2;PLACE_TRACKS 1 3;PLACE_TRACKS 2 3",
                    Action.format(baseline.play(state.copy(), me, 1000)));
            var actions = candidate.play(state.copy(), me, 1000);
            assertEquals("PLACE_TRACKS 6 11;PLACE_TRACKS 7 11;PLACE_TRACKS 8 11",
                    Action.format(actions));
            var result = Rules.resolve(state, me == 0 ? actions : List.of(),
                    me == 1 ? actions : List.of(), new Pathing(state.size));
            assertTrue(result.moves[me].skipped.isEmpty());
        }
    }

    @Test
    void experimentDefaultsAreFrozenAndOff() {
        Params params = new Params();
        assertFalse(params.USE_REPLY_RERANK);
        assertEquals(4, params.BUILD_BEAM_WIDTH);
        assertEquals(2, params.OPP_REPLY_WIDTH);
    }
}
