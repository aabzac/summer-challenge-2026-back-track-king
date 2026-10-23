package btk.bot;

import btk.bot.neural.SpatialBot;
import btk.bot.neural.SpatialInitialWeights;
import btk.bot.neural.SpatialNet;
import btk.bot.neural.SpatialV2Weights;
import btk.bot.neural.SpatialVariantBot;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SpatialVariantsTest {
    @Test void variantsResolveExplicitWeightsAndFrozenParametersWithoutChangingV1() throws Exception {
        assertInstanceOf(SpatialBot.class, BotFactory.create("spatial-ppo-v1"));
        for (String version : new String[]{"spatial-ppo-v2", "spatial-ppo-v2-init"}) {
            String payload = version.endsWith("-init") ? SpatialInitialWeights.DATA : SpatialV2Weights.DATA;
            Bot bot = BotFactory.create(version);
            assertInstanceOf(SpatialVariantBot.class, bot);
            assertEquals(version, bot.name());
            assertEquals(BotFactory.configuration("spatial-ppo-v1"), BotFactory.configuration(version));
            assertEquals(2, BotFactory.parameters(version).SPATIAL_RESERVE_MS);
            Params dirty = new Params(LiveTargetExperimentParams.V10_OFF + ",SPATIAL_RESERVE_MS=999,NOISE=0.8");
            FrozenParams.applyTo(version, dirty);
            assertEquals(2, dirty.SPATIAL_RESERVE_MS);
            assertEquals(.1, dirty.NOISE);
            assertEquals(5, BotFactory.parameters(version + "?SPATIAL_RESERVE_MS=5").SPATIAL_RESERVE_MS);
            assertEquals("local_only", BotCatalog.describe(version).status());
            assertEquals(version.endsWith("-init") ? "reference" : "candidate", BotCatalog.describe(version).category());
            var delegateField = SpatialVariantBot.class.getDeclaredField("delegate");
            delegateField.setAccessible(true);
            SpatialBot delegate = (SpatialBot) delegateField.get(bot);
            var netField = SpatialBot.class.getDeclaredField("net"); netField.setAccessible(true);
            var weightsField = SpatialNet.class.getDeclaredField("weights"); weightsField.setAccessible(true);
            assertArrayEquals((float[]) weightsField.get(new SpatialNet(payload)),
                    (float[]) weightsField.get(netField.get(delegate)), 0f, "factory must select the exact intended tensor payload");
        }
        assertEquals("a60252899a6ebed8e599fc7ba9a22a53798bc38c1375329c4ecda81f25d96708",
                hash(Base64.getDecoder().decode(SpatialInitialWeights.DATA)), "initializer is the original pre-gradient model");
        assertFalse(BotFactory.configuration(LiveTargetExperimentParams.V09_ON_CURRENT).containsKey("SPATIAL_RESERVE_MS"));
        assertFalse(BotFactory.configuration("v09").containsKey("SPATIAL_RESERVE_MS"));
    }

    @Test void localWrapperPreservesCompleteAndExhaustedBudgetDecisionsForBothSeats() {
        GameState state = new GameState(5, 2, new int[]{0,1,2,3,4,5,6,7,8,9}, new int[10],
                new int[]{0,4}, new int[]{0,0}, new int[][]{{1},{0}});
        state.owner[1] = 0; state.owner[2] = 1; state.turn = 17;
        Rules.disrupt(state, 2); Rules.updateConnections(state, new Pathing(state.size));
        for (String version : new String[]{"spatial-ppo-v2", "spatial-ppo-v2-init"}) for (int me = 0; me < 2; me++) {
            Params params = BotFactory.parameters(version);
            String payload = version.endsWith("-init") ? SpatialInitialWeights.DATA : SpatialV2Weights.DATA;
            Bot actual = BotFactory.create(version);
            SpatialBot expected = new SpatialBot(params, new SpatialNet(payload), () -> 0);
            actual.init(state, me); expected.init(state, me);
            assertEquals(Action.format(expected.play(state.copy(), me, 60_000)),
                    Action.format(actual.play(state.copy(), me, 60_000)), version + " seat=" + me);
            assertEquals("WAIT", Action.format(actual.play(state.copy(), me, 2)));
        }
    }

    @Test void originalSpatialProductionSourcesRemainIdenticalAcrossCheckoutLineEndings() throws Exception {
        Map<String, String> frozen = Map.of(
                "SpatialBot.java", "72fa785972751c2e0b10d7cc3628e386692564d997c3363d46135c06eb087494",
                "SpatialNet.java", "c66935366d8aac3f7775c5d09546c71b349fdfd86aca380fe71168cdb541d8cb",
                "SpatialObservation.java", "10dc8e80edf7c97cd87b88f018f5588546618b7a8a86207687d9c26595e4b6e0",
                "SpatialWeights.java", "33ab28bfd7db568226fd95e387e51011fd305d8e96875fb86cbcecb647dbb980");
        for (var entry : frozen.entrySet())
            assertEquals(entry.getValue(), hash(Files.readString(Path.of("src/main/java/btk/bot/neural", entry.getKey()))
                    .replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8)), entry.getKey());
    }

    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
