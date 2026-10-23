package btk.bot;

import btk.bot.neural.SpatialBot;
import btk.bot.neural.SpatialNet;
import btk.bot.neural.SpatialV4Bot;
import btk.bot.neural.SpatialV4Weights;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SpatialV4BotTest {
    @Test void factorySelectsExactV4WeightsAndUnchangedFrozenParameters() throws Exception {
        Bot bot = BotFactory.create("spatial-ppo-v4");
        assertInstanceOf(SpatialV4Bot.class, bot);
        assertEquals("spatial-ppo-v4", bot.name());
        assertEquals(BotFactory.configuration("spatial-ppo-v2"), BotFactory.configuration("spatial-ppo-v4"));
        Params dirty = new Params(LiveTargetExperimentParams.V10_OFF + ",SPATIAL_RESERVE_MS=999,NOISE=0.8");
        FrozenParams.applyTo("spatial-ppo-v4", dirty);
        assertEquals(2, dirty.SPATIAL_RESERVE_MS);
        assertEquals(.1, dirty.NOISE);
        assertEquals(5, BotFactory.parameters("spatial-ppo-v4?SPATIAL_RESERVE_MS=5").SPATIAL_RESERVE_MS);
        assertEquals("candidate", BotCatalog.describe("spatial-ppo-v4").category());
        assertEquals("local_only", BotCatalog.describe("spatial-ppo-v4").status());
        var delegateField = SpatialV4Bot.class.getDeclaredField("delegate"); delegateField.setAccessible(true);
        var netField = SpatialBot.class.getDeclaredField("net"); netField.setAccessible(true);
        var weightsField = SpatialNet.class.getDeclaredField("weights"); weightsField.setAccessible(true);
        assertArrayEquals((float[]) weightsField.get(new SpatialNet(SpatialV4Weights.DATA)),
                (float[]) weightsField.get(netField.get(delegateField.get(bot))), 0f);
    }

    @Test void wrapperMatchesFrozenRuntimeForBothSeatsAndExhaustedBudget() {
        GameState state = new GameState(5, 2, new int[]{0,1,2,3,4,5,6,7,8,9}, new int[10],
                new int[]{0,4}, new int[]{0,0}, new int[][]{{1},{0}});
        state.owner[1] = 0; state.owner[2] = 1; state.turn = 17;
        Rules.disrupt(state, 2); Rules.updateConnections(state, new Pathing(state.size));
        for (int me = 0; me < 2; me++) {
            Bot actual = BotFactory.create("spatial-ppo-v4");
            SpatialBot expected = new SpatialBot(BotFactory.parameters("spatial-ppo-v4"),
                    new SpatialNet(SpatialV4Weights.DATA), () -> 0);
            actual.init(state, me); expected.init(state, me);
            assertEquals(Action.format(expected.play(state.copy(), me, 60_000)),
                    Action.format(actual.play(state.copy(), me, 60_000)), "seat=" + me);
            assertEquals("WAIT", Action.format(actual.play(state.copy(), me, 2)));
        }
    }

    @Test void previousSpatialVersionSourcesRemainIdenticalAcrossLineEndings() throws Exception {
        Map<String, String> frozen = Map.of(
                "SpatialV2Weights.java", "63bd3bfb477bd585c8c2a4bbaa3a284666685b79e47b05da400f54f793f3ed50",
                "SpatialInitialWeights.java", "17cd3f6e4076c406f23c2e71fd8201e457914e594c6a5b3dff6d42431f3dba19",
                "SpatialVariantBot.java", "0d4253a1770138dcd32082d065f312711fe78c1164b031a6386e502c5c2a038a",
                "SpatialV3Weights.java", "fbaa98123972f1977da6b6a1e8df6c7df664b47e9eae5736612669d27ab8feaf",
                "SpatialV3Bot.java", "ecf4e282e9272a671ba99c2d9b6e06804b0f7dc62ccd6fe5cc6df3eb19b860fa");
        for (var entry : frozen.entrySet()) {
            byte[] source = Files.readString(Path.of("src/main/java/btk/bot/neural", entry.getKey()))
                    .replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8);
            assertEquals(entry.getValue(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source)), entry.getKey());
        }
    }
}
