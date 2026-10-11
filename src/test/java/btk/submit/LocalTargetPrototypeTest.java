package btk.submit;

import btk.bot.BotFactory;
import btk.bot.current.RolloutBot;
import btk.referee.MapGenerator;
import btk.referee.Referee;
import btk.tools.target.TargetHybridBot;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocalTargetPrototypeTest {
    @Test void defaultArtifactRemainsByteIdenticalAndCandidateCannotBeMislabeled() throws Exception {
        String source = FileMerger.merge("current");
        assertEquals(98709, source.length());
        assertEquals("ab0954a4545f25222fce6bccf8fa11866d276266e96b4728b502c83996d847e5", FileMerger.sha256(source));
        assertFalse(source.contains("USE_NEURAL_TARGET_PROPOSAL"));
        assertThrows(IllegalArgumentException.class,
                () -> FileMerger.merge("current?USE_NEURAL_TARGET_PROPOSAL=1"));
    }

    @Test void flagOnlySelectsLocalCurrentAndHistoricalConfigurationsStayUnchanged() {
        assertInstanceOf(RolloutBot.class, BotFactory.create("current"));
        assertInstanceOf(RolloutBot.class, BotFactory.create("current?USE_NEURAL_TARGET_PROPOSAL=0"));
        assertInstanceOf(TargetHybridBot.class, BotFactory.create("current?USE_NEURAL_TARGET_PROPOSAL=1"));
        assertFalse(BotFactory.configuration("current").containsKey("USE_NEURAL_TARGET_PROPOSAL"));
        assertFalse(BotFactory.configuration("v09").containsKey("USE_NEURAL_TARGET_PROPOSAL"));
        assertEquals("true", BotFactory.configuration("current?USE_NEURAL_TARGET_PROPOSAL=1").get("USE_NEURAL_TARGET_PROPOSAL"));
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("v09?USE_NEURAL_TARGET_PROPOSAL=1"));
    }

    @Test void refereeCapturesPrototypeCountersOnlyAfterGame() {
        var state = MapGenerator.generate(2209000); state.maxTurns = 1;
        Referee referee = new Referee(); referee.firstTurnBudgetMs = 5;
        var result = referee.play(state, BotFactory.create("current?USE_NEURAL_TARGET_PROPOSAL=1"),
                BotFactory.create("v09"), 2209000, false);
        assertEquals(1, result.turns); assertNull(result.error[0]); assertNull(result.error[1]);
        assertNotNull(result.targetProposalP0); assertFalse(result.targetProposalP0.isEmpty());
        assertNull(result.targetProposalP1);
    }
}
