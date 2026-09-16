package btk.tools;

import btk.bot.BotFactory;
import btk.bot.Params;
import btk.bot.current.EvalBot;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.referee.MapGenerator;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class CgDisruptStudyTest {
    private JsonArray fixtures() throws Exception {
        var input = getClass().getResourceAsStream("/btk/bot/disrupt-valuation.json");
        assertNotNull(input);
        try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonArray();
        }
    }

    private GameState state(JsonObject fixture) {
        var replay = new CgReplay(); replay.seed = fixture.get("refereeInput").getAsString();
        GameState state = MapGenerator.generate(replay.mapSeed().orElseThrow());
        Pathing paths = new Pathing(state.size); Rules.updateConnections(state, paths);
        var stdout = fixture.getAsJsonArray("stdout");
        int turn = fixture.get("turn").getAsInt();
        assertEquals(turn * 2, stdout.size(), "fixture excludes opponent's selected-turn/future actions");
        for (int t = 0; t < turn; t++) Rules.resolve(state,
                Action.parseLine(stdout.get(t * 2).getAsString()),
                Action.parseLine(stdout.get(t * 2 + 1).getAsString()), paths);
        return state;
    }

    @Test
    void capturedKnownStateChoicesAndValueComponents() throws Exception {
        for (var item : fixtures()) {
            var fixture = item.getAsJsonObject(); var state = state(fixture);
            var before = state.copy();
            Params params = BotFactory.parameters("v05");
            var result = CgDisruptStudy.recordedBuild(state, fixture.get("me").getAsInt(),
                    Action.parseLine(fixture.get("recordedAction").getAsString()), params);
            assertEquals(fixture.get("expectedTarget").getAsInt(), result.selected());
            assertArrayEquals(before.owner, state.owner);
            assertArrayEquals(before.score, state.score);
            assertArrayEquals(before.regionInstability, state.regionInstability);
            assertEquals(before.turn, state.turn);
            assertEquals(0.1, params.NOISE);
            if (fixture.get("gameId").getAsLong() == 903970829L && state.turn == 43) {
                var ready = result.targets().stream().filter(t -> t.region() == 5).findFirst().orElseThrow();
                var chosen = result.targets().stream().filter(t -> t.region() == 7).findFirst().orElseThrow();
                assertEquals(1, ready.needed()); assertEquals(2, chosen.needed());
                assertEquals(295, ready.ownIncomeLoss()); assertEquals(472, ready.opponentIncomeLoss());
                assertEquals(6608.1, ready.value(), 1e-6);
                assertEquals(9256.9, chosen.value(), 1e-6);
            }
        }
    }

    @Test
    void fullInspectionDoesNotConsumeRngOrPermanentlyChangeNoise() throws Exception {
        var fixture = fixtures().get(0).getAsJsonObject(); var state = state(fixture);
        int me = fixture.get("me").getAsInt();
        Params params = BotFactory.parameters("v05");
        EvalBot inspected = new EvalBot(params), control = new EvalBot(params.copy());
        inspected.init(state, me); control.init(state, me);
        CgDisruptStudy.evaluate(inspected, state, me);
        assertEquals(0.1, params.NOISE);
        assertEquals(CgDisruptStudy.call(control, "noise", new Class<?>[0]),
                CgDisruptStudy.call(inspected, "noise", new Class<?>[0]));
    }

    @Test
    void delayDiscountChangesMeruemPreferenceButNotReadyTargetValues() throws Exception {
        int[] expected = {5, 3, 27, 12};
        int index = 0;
        for (var item : fixtures()) {
            var fixture = item.getAsJsonObject(); var state = state(fixture);
            int me = fixture.get("me").getAsInt();
            var actions = Action.parseLine(fixture.get("recordedAction").getAsString());
            var baseline = CgDisruptStudy.recordedBuild(state, me, actions, BotFactory.parameters("current"));
            var candidate = CgDisruptStudy.recordedBuild(state, me, actions,
                    BotFactory.parameters("current?USE_ROLLOUT_POLICY=0,DISRUPT_DELAY_FACTOR=0.65"));
            assertEquals(expected[index++], candidate.selected());
            for (int i = 0; i < baseline.targets().size(); i++) {
                var b = baseline.targets().get(i); var c = candidate.targets().get(i);
                assertEquals(b.region(), c.region());
                if (b.value() == null) assertNull(c.value());
                else if (b.needed() == 1) assertEquals(b.value(), c.value());
                else assertEquals(b.value() * Math.pow(0.65, b.needed() - 1), c.value(), 1e-8);
            }
        }
    }

    @Test
    void neutralFactorPreservesFrozenV05FullActionsOnCapturedStates() throws Exception {
        assertEquals(1.0, new Params().DISRUPT_DELAY_FACTOR);
        for (var item : fixtures()) {
            var fixture = item.getAsJsonObject(); var state = state(fixture);
            for (int me = 0; me < 2; me++) {
                var current = BotFactory.create("current?USE_ROLLOUT_POLICY=0,NOISE=0");
                var frozen = BotFactory.create("v05?NOISE=0");
                current.init(state.copy(), me); frozen.init(state.copy(), me);
                assertEquals(Action.format(frozen.play(state.copy(), me, 60_000)),
                        Action.format(current.play(state.copy(), me, 60_000)));
            }
        }
    }

    @Test
    void delayDiscountPreservesTerminalWinAndLossSafeguard() throws Exception {
        GameState state = new GameState(3, 1, new int[]{0, 1, 2}, new int[3],
                new int[]{0, 2}, new int[]{0, 0}, new int[][]{{1}, {}});
        state.owner[1] = 1; state.instability[1] = 3; state.syncRegionsFromCells();
        Rules.updateConnections(state, new Pathing(state.size));
        for (double factor : new double[]{0, 0.65, 1}) {
            EvalBot bot = new EvalBot(new Params("NOISE=0,DISRUPT_DELAY_FACTOR=" + factor));
            bot.init(state.copy(), 0);
            state.score[0] = 0; state.score[1] = 100;
            assertEquals(-1, CgDisruptStudy.evaluate(bot, state, 0).selected());
            state.score[0] = 200;
            assertEquals(1, CgDisruptStudy.evaluate(bot, state, 0).selected());
            assertEquals(1_000_100.0, CgDisruptStudy.evaluate(bot, state, 0).targets().get(0).value());
        }
    }

    @Test
    void invalidDelayFactorsAreRejected() {
        for (String value : new String[]{"-0.01", "1.01", "NaN", "Infinity"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> BotFactory.create("current?USE_ROLLOUT_POLICY=0,DISRUPT_DELAY_FACTOR=" + value));
        }
    }

    @Test
    void distinguishesExpiredScanFromFullScanWithoutTimingSensitiveAssertions() throws Exception {
        var fixture = fixtures().get(0).getAsJsonObject(); var state = state(fixture);
        int me = fixture.get("me").getAsInt();
        EvalBot bot = new EvalBot(BotFactory.parameters("v05")); bot.init(state.copy(), me);
        bot.play(state.copy(), me, 0);
        assertEquals(false, CgDisruptStudy.field(bot, "disruptSearchComplete"));
        assertTrue(CgDisruptStudy.evaluate(bot, state, me).selected() >= 0);
        assertEquals(true, CgDisruptStudy.field(bot, "disruptSearchComplete"));
    }
}
