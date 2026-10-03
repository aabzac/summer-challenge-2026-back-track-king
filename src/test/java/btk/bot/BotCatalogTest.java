package btk.bot;

import btk.core.Action;
import btk.referee.MapGenerator;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BotCatalogTest {
    @Test void archivedEngineKeepsItsOriginalIdentityAndSharesCoreWithoutAMoveAdapter() throws Exception {
        var bot = BotCatalog.createExperiment("exp-reply-rerank");
        assertEquals("btk.bot.current.EvalBot", bot.getClass().getName());
        assertNotSame(btk.bot.current.EvalBot.class, bot.getClass());
        assertTrue(Bot.class.isAssignableFrom(bot.getClass()));
        assertNotNull(bot.getClass().getMethod("play", btk.core.GameState.class, int.class, long.class));
        assertEquals("05b2595d987156fef7e49baec8ad84900e0673d6cf634fcfa426bb817803343a",
                BotCatalog.describe("exp-reply-rerank").implementationSha256());
        assertEquals(46, BotFactory.configuration("exp-reply-rerank").size());
        assertFalse(BotFactory.configuration("exp-reply-rerank").containsKey("USE_JOINT_ACTION"));
        assertThrows(IllegalArgumentException.class, () -> BotCatalog.createExperiment("current"));
    }

    @Test void labelsDoNotRenameExistingIdsOrUnknownPlayers() {
        assertEquals("v05 - Disrupt Reserve", BotCatalog.displayName("v05"));
        assertEquals("v06 - Simultaneous Rollout", BotCatalog.displayName("v06"));
        assertEquals("v07 - Network After Base", BotCatalog.displayName("v07"));
        assertEquals("v08 - Protected Opening", BotCatalog.displayName("v08"));
        assertEquals("v09 - Never Idle", BotCatalog.displayName("v09"));
        assertEquals("selected", BotCatalog.describe("v09").status());
        assertEquals("historical", BotCatalog.describe("v08").status());
        assertEquals("historical", BotCatalog.describe("v07").status());
        assertEquals("historical", BotCatalog.describe("v06").status());
        assertEquals("historical", BotCatalog.describe("v05").status());
        assertEquals(java.util.List.of("v09"), BotCatalog.entries().stream()
                .filter(entry -> entry.status().equals("selected")).map(BotCatalog.Entry::id).toList());
        assertEquals("v04 - Network Extension [NOISE=0.2]", BotCatalog.displayName("v04?NOISE=0.2"));
        assertEquals("external player", BotCatalog.displayName("external player"));
        assertEquals("v03", BotFactory.create("v03").name().split("\\?", 2)[0]);
        assertEquals("working", BotCatalog.describe("current").category());
        assertEquals("local_only", BotCatalog.describe("pressure-v1").status());
        assertEquals("indrill-q-v1 - Indrill Action Values", BotCatalog.displayName("indrill-q-v1"));
        assertEquals("candidate", BotCatalog.describe("indrill-q-v1").category());
        assertEquals("local_only", BotCatalog.describe("indrill-q-v1").status());
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("exp-does-not-exist"));
    }

    @Test void allSixPresetsHaveCompletePinnedConfigAndIndependentObjects() {
        int count = 0;
        for (var entry : BotCatalog.entries()) {
            if (!entry.category().equals("experiment")) continue;
            count++;
            var actual = BotFactory.configuration(entry.id());
            var expected = BotFactory.configuration("current?" + entry.recipe());
            expected.keySet().retainAll(BotCatalog.parameterNames(entry.id())); // original fields only
            assertEquals(expected, actual, entry.id());
            var identity = BotCatalog.describe(entry.id());
            assertEquals("not_promoted", identity.status());
            assertEquals("218457a9b6ce72a9e06c5b6a9102112a6c7e25e2", identity.sourceRevision());
            assertEquals(64, identity.implementationSha256().length());
            assertEquals(64, identity.presetSha256().length());
            assertFalse(identity.explicitOverrides());
            assertNotSame(BotFactory.parameters(entry.id()), BotFactory.parameters(entry.id()));
            assertEquals(entry.id(), BotFactory.create(entry.id()).name());
        }
        assertEquals(6, count);
        var changed = BotFactory.parameters("exp-income-guard?NOISE=0.2,GUARD_BUILD_INCOME=false");
        assertFalse(changed.GUARD_BUILD_INCOME); assertEquals(0.2, changed.NOISE);
        assertTrue(BotFactory.parameters("exp-income-guard").GUARD_BUILD_INCOME);
        assertFalse(new Params().GUARD_BUILD_INCOME);
        assertTrue(BotCatalog.describe("exp-income-guard?NOISE=0.2").explicitOverrides());
        assertThrows(IllegalArgumentException.class, () -> BotFactory.parameters("exp-income-guard?UNKNOWN=1"));
        assertThrows(IllegalArgumentException.class, () -> BotFactory.parameters("exp-income-guard?USE_JOINT_ACTION=0"));
    }

    @Test void fullSnapshotOverridesLiveDefaultsAndRejectsMissingOrExtraFields() throws Exception {
        Map<String, String> snapshot = new HashMap<>();
        try (var in = getClass().getResourceAsStream("/btk/bot/experiment-engine-v1.json")) {
            var json = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            json.getAsJsonObject("parameters").entrySet().forEach(e -> snapshot.put(e.getKey(), e.getValue().getAsString()));
        }
        Params dirtyDefaults = new Params("HORIZON=2,GUARD_BUILD_INCOME=true,USE_REPLY_RERANK=true,NOISE=0.8");
        BotCatalog.applySnapshot(snapshot, dirtyDefaults);
        assertEquals(100, dirtyDefaults.HORIZON); assertFalse(dirtyDefaults.GUARD_BUILD_INCOME);
        assertFalse(dirtyDefaults.USE_REPLY_RERANK); assertEquals(0.1, dirtyDefaults.NOISE);
        snapshot.remove("HORIZON");
        assertThrows(IllegalStateException.class, () -> BotCatalog.applySnapshot(snapshot, new Params()));
        snapshot.put("HORIZON", "100"); snapshot.put("NOT_A_PARAM", "2");
        assertThrows(IllegalStateException.class, () -> BotCatalog.applySnapshot(snapshot, new Params()));
    }

    @Test void implementationDriftFailsClosedBeforeStartingBot() throws Exception {
        byte[] original = "original bytecode".getBytes(StandardCharsets.UTF_8);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original));
        var classes = Map.of("btk/bot/current/EvalBot.class", hash, "btk/bot/Params.class", hash, "btk/core/Rules.class", hash);
        assertDoesNotThrow(() -> BotCatalog.verifyImplementation(classes, name -> original));
        var error = assertThrows(IllegalStateException.class,
                () -> BotCatalog.verifyImplementation(classes, name -> new byte[]{1}));
        assertTrue(error.getMessage().contains("NEW snapshot"));
        assertThrows(IllegalStateException.class, () -> BotCatalog.verifyImplementation(Map.of(), name -> original));
    }

    @Test void presetWrapperMatchesExplicitRecipeForBothPlayers() {
        for (var entry : BotCatalog.entries()) {
            if (!entry.category().equals("experiment")) continue;
            for (int player = 0; player < 2; player++) {
                var state = MapGenerator.generate(321);
                var named = BotFactory.create(entry.id() + "?NOISE=0");
                var explicit = BotFactory.create("current?USE_ROLLOUT_POLICY=0," + entry.recipe() + ",NOISE=0");
                named.init(state.copy(), player); explicit.init(state.copy(), player);
                assertEquals(Action.format(explicit.play(state.copy(), player, 60_000)),
                        Action.format(named.play(state.copy(), player, 60_000)), entry.id());
            }
        }
    }
}
