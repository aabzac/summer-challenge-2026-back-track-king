package btk.bot;

import btk.bot.neural.SpatialBot;
import btk.bot.neural.SpatialNet;
import btk.bot.neural.SpatialV10Bot;
import btk.bot.neural.SpatialV10Weights;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Protocol;
import btk.core.Rules;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpatialV10BotTest {
    @Test void isolatedVersionSelectsGeneratedPayloadAndExactV4EffectiveParameters() throws Exception {
        Bot bot = BotFactory.create("spatial-ppo-v10");
        assertInstanceOf(SpatialV10Bot.class, bot); assertEquals("spatial-ppo-v10", bot.name());
        assertEquals(BotFactory.configuration("spatial-ppo-v4"), BotFactory.configuration("spatial-ppo-v10"));
        assertEquals("candidate", BotCatalog.describe("spatial-ppo-v10").category());
        assertEquals("local_only", BotCatalog.describe("spatial-ppo-v10").status());
        Params dirty = new Params("SPATIAL_RESERVE_MS=99,NOISE=0.8,USE_SPATIAL_V9_TOWN_CONTEXT=1");
        FrozenParams.applyTo("spatial-ppo-v10", dirty);
        assertEquals(2, dirty.SPATIAL_RESERVE_MS); assertEquals(.1, dirty.NOISE); assertFalse(dirty.USE_SPATIAL_V9_TOWN_CONTEXT);
        assertEquals(5, BotFactory.parameters("spatial-ppo-v10?SPATIAL_RESERVE_MS=5").SPATIAL_RESERVE_MS);
        var delegate = SpatialV10Bot.class.getDeclaredField("delegate"); delegate.setAccessible(true);
        var net = SpatialBot.class.getDeclaredField("net"); net.setAccessible(true);
        var weights = SpatialNet.class.getDeclaredField("weights"); weights.setAccessible(true);
        assertArrayEquals((float[]) weights.get(new SpatialNet(SpatialV10Weights.DATA)),
                (float[]) weights.get(net.get(delegate.get(bot))), 0f);
    }

    @Test void wrapperMatchesOriginalGreedyRuntimeForBothSeatsAndBudgetFallbackWithoutMutation() {
        for (int seat = 0; seat < 2; seat++) for (int turn : new int[]{0,37}) {
            GameState state = fixture(turn); String before = Protocol.turnBlock(state, 0);
            Params params = BotFactory.parameters("spatial-ppo-v10");
            Bot original = new SpatialBot(params, new SpatialNet(SpatialV10Weights.DATA), () -> 0L);
            Bot candidate = new SpatialV10Bot(params, new SpatialNet(SpatialV10Weights.DATA), () -> 0L);
            original.init(state, seat); candidate.init(state, seat);
            assertEquals(Action.format(original.play(state, seat, 60_000)), Action.format(candidate.play(state, seat, 60_000)));
            assertEquals("WAIT", Action.format(candidate.play(state, seat, 2)));
            assertEquals(before, Protocol.turnBlock(state, 0)); assertEquals(turn, state.turn);
        }
    }

    @Test void runtimeWarmupUsesTheSelectedNetworkAndPreservesInput() {
        GameState state = fixture(37); String before = Protocol.turnBlock(state, 0);
        Bot bot = BotFactory.create("spatial-ppo-v10"); bot.init(state, 1);
        String expected = Action.format(bot.play(state, 1, 60_000));
        bot.warmup(state, 1, 2);
        assertEquals(expected, Action.format(bot.play(state, 1, 60_000)));
        assertEquals(before, Protocol.turnBlock(state, 0)); assertEquals(37, state.turn);
    }

    private static GameState fixture(int turn) {
        int[] region = new int[18]; for (int i = 0; i < region.length; i++) region[i] = i;
        GameState state = new GameState(6,3,region,new int[18],new int[]{0,5},new int[]{1,1},new int[][]{{1},{0}});
        state.terrain[2] = GameState.RIVER; state.terrain[3] = GameState.MOUNTAIN; state.turn = turn;
        if (turn > 0) { state.owner[7] = 0; state.owner[8] = 1; state.owner[9] = GameState.NEUTRAL; Rules.disrupt(state, 4); }
        Rules.updateConnections(state, new Pathing(state.size)); return state;
    }
}
