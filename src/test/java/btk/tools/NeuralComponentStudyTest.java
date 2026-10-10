package btk.tools;

import btk.bot.BotFactory;
import btk.bot.Params;
import btk.bot.Timer;
import btk.bot.current.RolloutBot;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static btk.tools.NeuralProposalStudy.*;
import static btk.tools.NeuralComponentStudy.*;
import static org.junit.jupiter.api.Assertions.*;

/** Fixed synthetic fixtures; no historical panel is evaluated by this test class. */
class NeuralComponentStudyTest {
    @TempDir Path temporary;
    private static GameState board(int me) {
        GameState s = new GameState(5, 2, new int[]{0,1,2,3,4,5,6,7,8,9}, new int[10],
                new int[]{0,4}, new int[2], new int[][]{{1},{}});
        s.owner[1] = s.owner[2] = me; s.turn = 10; Rules.updateConnections(s, new Pathing(s.size)); return s;
    }
    private static Params params() {
        return new Params("USE_PROTECTED_OPENING=0,USE_ROLLOUT_QUICK_ROUTE=0,USE_ROLLOUT_NETWORK=0,"
                + "USE_ROLLOUT_NETWORK_AFTER_BASE=0,USE_ROLLOUT_FILL=1,USE_DISRUPT=0,NOISE=0,ROLLOUT_DEPTH=2");
    }
    private static RolloutBot prepared(GameState s, int me, Params params) throws Exception {
        RolloutBot bot = new RolloutBot(params); bot.init(s, me);
        ((int[]) field(bot, "counts"))[me] = 1;
        ((int[][]) field(bot, "lengths"))[me][0] = 5;
        System.arraycopy(new int[]{0,1,2,3,4}, 0, ((int[][][]) field(bot, "routes"))[me][0], 0, 5);
        ((int[][]) field(bot, "roots"))[1][0] = 9; ((int[]) field(bot, "rootCounts"))[1] = 1;
        bot.selectedRoute = 0; bot.selectedTarget = -1; bot.selectedValue = 17; bot.simulatedTurns = 29;
        ((Timer) field(bot, "timer")).start(1234); return bot;
    }
    @Test void compositionPreservesExactOrderedConstructionAndDoesNotRefillAfterTargetSwap() throws Exception {
        for (int me = 0; me < 2; me++) {
            GameState s = board(me); GameState before = s.copy(); Params p = params(); RolloutBot bot = prepared(s, me, p);
            Plan e = legal(s, me, new int[]{3,6,5}, -1), n = legal(s, me, new int[]{7}, 6);
            var results = evaluateFour(bot, s, me, e, n, 1, p);
            assertArrayEquals(new int[]{7}, results.get("C").plan().cells()); assertEquals(-1, results.get("C").plan().target());
            assertArrayEquals(new int[]{3,6,5}, results.get("D").plan().cells()); assertEquals(6, results.get("D").plan().target());
            assertTrue(results.get("D").valid(), "core permits building in the same region as own disruption");
            // Calling the old target-specific filler WOULD avoid region 6, but the crossover must not call it.
            assertArrayEquals(new int[]{3,7,8}, filled(bot, s, me, legal(s, me, new int[]{3}, 6)).cells());
            assertArrayEquals(new int[]{3,6,5}, results.get("D").plan().cells());
            assertArrayEquals(new int[]{3,6,5}, e.cells()); assertArrayEquals(new int[]{7}, n.cells());
            ReplyProbe.sameState(before, s);
        }
    }
    @Test void sharedRolloutScoresMatchDirectProbeAndRestoreInputRootContextAndDiagnostics() throws Exception {
        for (int me = 0; me < 2; me++) {
            GameState s = board(me), before = s.copy(); Params p = params(); RolloutBot bot = prepared(s, me, p);
            Plan e = legal(s, me, new int[]{3}, -1), n = legal(s, me, new int[]{6}, 7);
            String roots = Arrays.deepToString((int[][]) field(bot, "roots"));
            String timer = ReplyStudy.JSON.toJson(field(bot, "timer")), config = ReplyStudy.JSON.toJson(p);
            Score expectedE = evaluate(bot, s, me, e, 1, 1, p), expectedN = evaluate(bot, s, me, n, 1, 1, p);
            var results = evaluateFour(bot, s, me, e, n, 1, p);
            sameScore(expectedE, results.get("E").score(), "E"); sameScore(expectedN, results.get("N").score(), "N");
            for (String key : List.of("C", "D")) {
                Result result = results.get(key);
                sameScore(evaluate(bot, s, me, result.plan(), 1, 1, p), result.score(), key);
            }
            assertEquals(roots, Arrays.deepToString((int[][]) field(bot, "roots")));
            assertEquals(timer, ReplyStudy.JSON.toJson(field(bot, "timer"))); assertEquals(config, ReplyStudy.JSON.toJson(p));
            assertEquals(17, bot.selectedValue); assertEquals(29, bot.simulatedTurns); assertEquals(0, bot.selectedRoute);
            ReplyProbe.sameState(before, s);
        }
    }
    @Test void sameAndEmptyComponentsRemainIdenticalActionsWithZeroInteraction() throws Exception {
        GameState s = board(0); Params p = params(); RolloutBot bot = prepared(s, 0, p);
        Plan wait = legal(s, 0, new int[0], -1);
        var empty = evaluateFour(bot, s, 0, wait, wait, 1, p);
        for (Result result : empty.values()) {
            assertTrue(result.valid()); assertEquals("WAIT", result.plan().action());
            sameScore(empty.get("E").score(), result.score(), "Empty components");
        }
        Plan a = legal(s, 0, new int[]{3,5}, 7), b = legal(s, 0, new int[]{5,3}, 7);
        var same = evaluateFour(bot, s, 0, a, b, 1, p);
        for (Result result : same.values()) sameScore(same.get("E").score(), result.score(), "Unordered-equivalent full actions");
        double v = same.get("E").score().value(); assertEquals(0.0, effects(v, v, v, v).get("interaction"));
    }
    @Test void coreRejectionsAreMissingCrossesAndAreNeverRepaired() {
        GameState s = board(0);
        Cross duplicate = compose(s, 0, new int[]{3,3}, -1);
        assertFalse(duplicate.valid()); assertNotNull(duplicate.invalidReason()); assertArrayEquals(new int[]{3,3}, duplicate.plan().cells());
        Cross protectedTarget = compose(s, 0, new int[]{3}, 0);
        assertFalse(protectedTarget.valid()); assertEquals(0, protectedTarget.plan().target());
        assertEquals("unavailable", thresholdCategory(null, 7.0, 3));
        var missing = effects(1.0, null, 2.0, 4.0);
        assertNull(missing.get("interaction")); assertNull(missing.get("buildAtBaselineTarget"));
        assertEquals(2.0, missing.get("buildAtNeuralTarget")); assertEquals(1.0, missing.get("targetAtBaselineBuild"));
    }
    @Test void interactionAndThresholdRequirementAreDistinctIncludingToleranceAndMissingDenominators() {
        var additive = effects(0.0, 6.0, 6.0, 12.0);
        assertEquals(0.0, additive.get("interaction"));
        assertEquals("neitherSeparatelyExceedsH", thresholdCategory(6.0, 6.0, 10));
        assertEquals(6.0, additive.get("buildAtNeuralTarget")); assertEquals(6.0, additive.get("targetAtNeuralBuild"));
        assertEquals(3.0, effects(2.0, 5.0, 8.0, 14.0).get("interaction"));
        assertEquals("onlyCExceedsH", thresholdCategory(2.0, 0.0, 1));
        assertEquals("onlyDExceedsH", thresholdCategory(0.0, 2.0, 1));
        assertEquals("bothSeparatelyExceedH", thresholdCategory(2.0, 2.0, 1));
        assertEquals("neitherSeparatelyExceedsH", thresholdCategory(EPS / 2, -EPS / 2, 0));
        JsonObject missing = new JsonObject(), actions = new JsonObject();
        for (String key : List.of("C", "D")) {
            JsonObject action = new JsonObject(); action.add("gapFromEmittedE", com.google.gson.JsonNull.INSTANCE);
            action.add("gapFromBestExistingH", com.google.gson.JsonNull.INSTANCE); actions.add(key, action);
        }
        missing.add("actions", actions); missing.addProperty("thresholdCategory", "unavailable");
        @SuppressWarnings("unchecked") var counts = (java.util.Map<String,Integer>) summarize(List.of(missing)).get("CVsEmittedE");
        assertEquals(1, counts.get("missing")); assertEquals(0, counts.get("worse"));
    }
    @Test void fullSyntheticRowReusesOldReconstructionAndPinsHWithoutNewTargetSearch() throws Exception {
        for (int me = 0; me < 2; me++) {
            GameState s = board(me); Params p = BotFactory.parameters("v09?NOISE=0");
            Position position = new Position(1, 0, "synthetic", "synthetic", me, s, legal(s, me, new int[]{3}, 6).action());
            var original = inspect(position, p); JsonObject prior = ReplyStudy.JSON.toJsonTree(original).getAsJsonObject();
            identicalPriorRow(prior, original);
            JsonObject row = study(position, prior, p);
            assertEquals(prior.get("bestCommonFilledScore"), row.get("bestExistingHScore"));
            assertEquals(prior.get("baselineCommonScore"), row.getAsJsonObject("actions").getAsJsonObject("E").get("score"));
            assertEquals(prior.get("neuralCommonScore"), row.getAsJsonObject("actions").getAsJsonObject("N").get("score"));
            assertTrue(row.get("inputAndContextUnchanged").getAsBoolean());
            prior.addProperty("stateSha256", "changed");
            assertThrows(IllegalStateException.class, () -> identicalPriorRow(prior, original));
        }
    }
    @Test void checksumPinsRejectChangedPriorOrSelectionBeforeAnyBotCalls() throws Exception {
        Path selection = temporary.resolve("selection.json"), prior = temporary.resolve("prior.json"), summary = temporary.resolve("summary.json");
        Files.writeString(selection, "{}"); Files.writeString(prior, "{}");
        JsonObject evidence = new JsonObject(), inputs = new JsonObject(), report = new JsonObject();
        inputs.addProperty("selectionSha256", hash(selection)); report.addProperty("sha256", hash(prior));
        evidence.add("inputs", inputs); evidence.add("fullReport", report); Files.writeString(summary, evidence.toString());
        Files.writeString(prior, "{\"changed\":true}");
        assertTrue(assertThrows(IllegalStateException.class, () -> pinnedPrior(selection, prior, summary)).getMessage().contains("SHA256"));
        Files.writeString(prior, "{}"); Files.writeString(selection, "{\"changed\":true}");
        assertTrue(assertThrows(IllegalStateException.class, () -> pinnedPrior(selection, prior, summary)).getMessage().contains("SHA256"));
    }
}
