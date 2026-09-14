package btk.bot.current;

import btk.bot.Params;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.referee.MapGenerator;
import btk.tools.CgReplay;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BuildDirectionTest {
    private static final int[] ROUTE = {0, 1, 2, 3, 4, 5, 6, 7, 8};

    @Test
    void delaysTheHarmfulShortcutInTwoCapturedArenaStates() throws Exception {
        var input = getClass().getResourceAsStream("/btk/bot/build-income-guard.json");
        assertNotNull(input);
        try (var reader = new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8)) {
            int checked = 0;
            for (var value : JsonParser.parseReader(reader).getAsJsonArray()) {
                var fixture = value.getAsJsonObject();
                String id = fixture.get("gameId").getAsString();
                if (!id.equals("903583608") && !id.equals("903584040")) continue;
                var replay = new CgReplay();
                replay.seed = fixture.get("refereeInput").getAsString();
                GameState state = MapGenerator.generate(replay.mapSeed().orElseThrow());
                var pathing = new Pathing(state.size);
                int me = fixture.get("me").getAsInt(), turn = fixture.get("turn").getAsInt();
                var stdout = fixture.getAsJsonArray("stdout");
                for (int t = 0; t < turn; t++) {
                    Rules.resolve(state, Action.parseLine(stdout.get(t * 2).getAsString()),
                            Action.parseLine(stdout.get(t * 2 + 1).getAsString()), pathing);
                }
                int[] before = new int[2], after = new int[2];
                Rules.incomePerTurn(state, before);
                var bot = new EvalBot(new Params("NOISE=0,CHOOSE_BUILD_DIRECTION=1,USE_DISRUPT=0"));
                bot.init(state, me);
                var actions = bot.play(state.copy(), me, 60_000);
                String expected = id.equals("903583608") ? "PLACE_TRACKS 10 11"
                        : "PLACE_TRACKS 17 6;PLACE_TRACKS 17 7";
                assertEquals(expected, Action.format(actions), id);
                var result = Rules.resolve(state, me == 0 ? actions : java.util.List.of(),
                        me == 1 ? actions : java.util.List.of(), pathing);
                assertTrue(result.moves[me].skipped.isEmpty(), id);
                Rules.incomePerTurn(state, after);
                assertTrue(after[me] - after[1 - me] >= before[me] - before[1 - me], id);
                checked++;
            }
            assertEquals(2, checked);
        }
    }

    @Test
    void playEmitsTheBetterPrefixOfTheSameSelectedRoute() {
        for (int me = 0; me < 2; me++) {
            GameState state = line();
            Params params = new Params("NOISE=0,W_VULN=0,USE_DISRUPT=0,USE_IDLE_EXTEND=0");
            EvalBot forward = new EvalBot(params.copy());
            forward.init(state, me);
            assertEquals("PLACE_TRACKS 1 0;PLACE_TRACKS 2 0;PLACE_TRACKS 3 0",
                    Action.format(forward.play(state.copy(), me, 60_000)));
            params.CHOOSE_BUILD_DIRECTION = true;
            EvalBot candidate = new EvalBot(params);
            candidate.init(state, me);
            var actions = candidate.play(state.copy(), me, 60_000);
            assertEquals("PLACE_TRACKS 7 0;PLACE_TRACKS 6 0;PLACE_TRACKS 5 0", Action.format(actions));
            var result = Rules.resolve(state, me == 0 ? actions : java.util.List.of(),
                    me == 1 ? actions : java.util.List.of(), new Pathing(state.size));
            assertTrue(result.moves[me].skipped.isEmpty());
            assertTrue(state.pairActive(1));
            assertFalse(state.pairActive(0), "the long-term route is still unfinished");
        }
    }

    @Test
    void choosesProfitableEndForEitherPlayerWithoutChangingInput() {
        for (int me = 0; me < 2; me++) {
            GameState state = line();
            EvalBot bot = new EvalBot();
            bot.init(state, me);
            int[] owners = state.owner.clone();
            assertEquals(0, bot.prefixIncome(state, me, 3, ROUTE, ROUTE.length, false));
            assertTrue(bot.prefixIncome(state, me, 3, ROUTE, ROUTE.length, true) > 0);
            assertTrue(bot.preferReverseBuild(state, me, 3, ROUTE, ROUTE.length));
            int[] backwards = {8, 7, 6, 5, 4, 3, 2, 1, 0};
            assertFalse(bot.preferReverseBuild(state, me, 3, backwards, backwards.length));
            assertArrayEquals(owners, state.owner);
            assertFalse(state.pairActive(0));
            assertFalse(state.pairActive(1));
        }
    }

    @Test
    void tiesAndCompleteRoutesKeepHistoricalDirection() {
        GameState state = line();
        EvalBot bot = new EvalBot();
        bot.init(state, 0);
        assertFalse(bot.preferReverseBuild(state, 0, 1, ROUTE, ROUTE.length));
        assertFalse(bot.preferReverseBuild(state, 0, 6, ROUTE, ROUTE.length));
        assertFalse(new Params().CHOOSE_BUILD_DIRECTION);
    }

    @Test
    void skipsUnaffordableCellsAndDefersContestedOnesInBothDirections() throws Exception {
        GameState state = line();
        state.terrain[1] = GameState.MOUNTAIN;
        state.terrain[6] = GameState.RIVER;
        EvalBot bot = new EvalBot();
        bot.init(state, 0);
        var contestedField = EvalBot.class.getDeclaredField("contested");
        contestedField.setAccessible(true);
        boolean[] contested = (boolean[]) contestedField.get(bot);
        contested[2] = true;
        // Forward with 2 points: mountain skipped, contested 2 deferred, build 3 and 5.
        bot.prefixIncome(state, 0, 2, ROUTE, ROUTE.length, false);
        assertScratchOwners(bot, 3, 5);
        // Reverse with 2 points: build 7, skip river 6, build 5.
        bot.prefixIncome(state, 0, 2, ROUTE, ROUTE.length, true);
        assertScratchOwners(bot, 5, 7);
        // A contested cell is still built in the second pass if paint remains.
        bot.prefixIncome(state, 0, 1, new int[]{0, 2, 4}, 3, false);
        assertScratchOwners(bot, 2);
    }

    private static void assertScratchOwners(EvalBot bot, int... placed) throws Exception {
        var field = EvalBot.class.getDeclaredField("sim");
        field.setAccessible(true);
        GameState scratch = (GameState) field.get(bot);
        for (int c = 0; c < scratch.size; c++) {
            boolean expected = false;
            for (int p : placed) expected |= p == c;
            assertEquals(expected ? 0 : GameState.NO_TRACK, scratch.owner[c], "cell " + c);
        }
    }

    private static GameState line() {
        GameState state = new GameState(9, 1, new int[9], new int[9],
                new int[]{0, 4, 8}, new int[]{0, 0, 0}, new int[][]{{2}, {2}, {}});
        Rules.updateConnections(state, new Pathing(state.size));
        return state;
    }
}
