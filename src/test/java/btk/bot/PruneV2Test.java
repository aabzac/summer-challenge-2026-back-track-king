package btk.bot;

import btk.bot.current.RolloutBot;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.referee.MapGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * prune-v2 (2026-10-27, repo-cleanup): the rejected or parked experiments of v12's time (collision avoidance, corridor
 * routes, quick-always / delay power, the opponent hold margin, the no-close proxy switch, route commitment and the
 * threat model) left {@code current}. A request on current is refused, never silently ignored; the frozen v13 copy
 * still carries their code and runs them. The default artifact stays byte-identical (V13SubmissionTest pins it).
 */
class PruneV2Test {
    static final String PROXY_OFF = "USE_BOSS_SABOTAGE=1,USE_BOSS_LITE=0,USE_BOSS_OPPONENT_MODEL=0,USE_BOSS_OWN_MODEL=0,"
            + "USE_BOSS_LITE_ROUTES=0,USE_INCREMENTAL_INK_REFRESH=0,USE_FAST_CHEAPEST_PATH=0,USE_BATCHED_REFRESH=0,"
            + "USE_EXPOSURE_INCOME=0,USE_BYPASS_ROUTES=0";
    static final List<String> PARKED = List.of("USE_COLLISION_AVOIDANCE=1", "USE_CORRIDOR_ROUTES=1", "USE_QUICK_ALWAYS=1",
            "DELAY_POWER=0.5", "BOSS_OPP_HOLD_MARGIN=0", "USE_ROUTE_COMMIT=1", "USE_BOSS_THREAT_MODEL=1",
            PROXY_OFF + ",USE_BOSS_NO_CLOSE=1");

    @Test void parkedExperimentsAreRefusedOnCurrentAndRunOnFrozenV13() {
        for (String query : PARKED) {
            assertThrows(IllegalArgumentException.class, () -> BotFactory.create("current?" + query), query);
            assertDoesNotThrow(() -> BotFactory.create("v13?" + query), query);
        }
        Params direct = new Params(); direct.USE_ROUTE_COMMIT = true;
        assertThrows(IllegalArgumentException.class, () -> new RolloutBot(direct), "the constructor refuses too");
        for (String token : new String[]{"USE_ROUTE_COMMIT", "USE_CORRIDOR_ROUTES", "USE_COLLISION_AVOIDANCE", "USE_QUICK_ALWAYS",
                "DELAY_POWER", "BOSS_OPP_HOLD_MARGIN", "USE_BOSS_NO_CLOSE", "USE_BOSS_THREAT_MODEL"})
            assertFalse(BotFactory.configuration("current").containsKey(token), token);
    }

    @Test void frozenV13StillPlaysAParkedExperiment() {
        GameState state = MapGenerator.generate(841001L);
        Pathing paths = new Pathing(state.size); Rules.updateConnections(state, paths);
        Bot commit = BotFactory.create("v13?USE_ROUTE_COMMIT=1,NOISE=0"), opponent = BotFactory.create("v12?NOISE=0");
        commit.init(state.copy(), 0); opponent.init(state.copy(), 1);
        for (int turn = 0; turn < 6 && !Rules.gameOver(state, paths); turn++) {
            List<Action> a = commit.play(state.copy(), 0, 60_000), b = opponent.play(state.copy(), 1, 60_000);
            Rules.TurnResult result = Rules.resolve(state, a, b, paths);
            for (var moves : result.moves) assertNull(moves.error, "turn " + turn);
        }
    }
}
