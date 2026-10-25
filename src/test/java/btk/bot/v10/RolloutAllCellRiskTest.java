package btk.bot.v10;

import btk.bot.current.RolloutTestParams;
import btk.bot.BotFactory;
import btk.bot.Params;
import btk.bot.v09.RolloutBotV09;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.referee.MapGenerator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static btk.bot.v10.RolloutDiverseRepliesTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** Moved from btk.bot.current by prune-v1 (2026-10-25): the mechanism under test now lives only in frozen v10. */
class RolloutAllCellRiskTest {
    private static final long AMPLE_MS = 60_000;

    private static GameState connection(int owner, boolean protectedRegions, int pairs) {
        int[] desired = new int[pairs]; Arrays.fill(desired, 1);
        var s = new GameState(4, 2,
                protectedRegions ? new int[]{0, 0, 3, 3, 4, 4, 4, 4} : new int[]{0, 1, 2, 3, 4, 4, 4, 4},
                new int[8], new int[]{0, 3}, new int[]{0, 0}, new int[][]{desired, {}});
        s.owner[1] = s.owner[2] = owner;
        for (int c = 0; c < s.size; c++) if (c < 4 && (protectedRegions || c == 1 || c == 2)) {
            s.instability[c] = 3; s.regionInstability[s.regionId[c]] = 3;
        }
        s.turn = 10;
        Rules.updateConnections(s, new Pathing(s.size));
        return s;
    }

    private static Params parameters(boolean enabled) {
        Params p = RolloutTestParams.base("NOISE=0,USE_DISRUPT=0");
        p.USE_ROLLOUT_ALL_CELL_RISK = enabled;
        return p;
    }

    /** Feed the existing proposer exact core incomes at its normal preparation boundary. */
    private static RolloutBotV10 prepared(GameState s, int me, int threat) throws Exception {
        var b = new RolloutBotV10(parameters(true)); b.init(s, me);
        Arrays.fill((int[]) field(b, "threats"), -1);
        var quick = RolloutBotV10.class.getDeclaredField("quickPlayer"); quick.setAccessible(true); quick.setInt(b, -1);
        int[] before = new int[2], after = new int[2]; Rules.incomePerTurn(s, before);
        var inked = s.copy();
        if (threat >= 0) { Rules.ink(inked, threat); Rules.updateConnections(inked, new Pathing(s.size)); }
        Rules.incomePerTurn(inked, after);
        ((int[]) field(b, "baseDiff"))[me] = before[me] - before[1 - me];
        ((int[]) field(b, "threatDiff"))[me] = after[me] - after[1 - me];
        ((int[]) field(b, "threats"))[me] = threat;
        return b;
    }

    private static void propose(RolloutBotV10 b, GameState s, int me, int variant) throws Exception {
        call(b, "propose", new Class<?>[]{GameState.class, int.class, int.class, int.class}, s, me, 0, variant);
    }

    private static int[] build(RolloutBotV10 b, GameState s, int me, int route) throws Exception {
        int[] cells = new int[GameState.PAINT_PER_TURN];
        int n = (int) call(b, "fillBuild", new Class<?>[]{GameState.class, int.class, int.class, int[].class},
                s, me, route, cells);
        return Arrays.copyOf(cells, n);
    }

    private static List<Action> placements(GameState s, int[] cells) {
        List<Action> actions = new ArrayList<>();
        for (int c : cells) actions.add(Action.place(s.x(c), s.y(c)));
        return actions.isEmpty() ? List.of(Action.waitAction()) : actions;
    }

    private static void legal(GameState s, int me, List<Action> actions) {
        var result = new Rules.TurnResult();
        Rules.expand(s, me, actions, new Pathing(s.size), result.moves[me], result);
        assertNull(result.moves[me].error); assertTrue(result.moves[me].skipped.isEmpty());
    }

    private static void unchanged(GameState before, GameState after) {
        assertEquals(before.turn, after.turn); assertEquals(before.maxTurns, after.maxTurns);
        assertArrayEquals(before.owner, after.owner); assertArrayEquals(before.score, after.score);
        assertArrayEquals(before.instability, after.instability); assertArrayEquals(before.inked, after.inked);
        assertArrayEquals(before.regionInstability, after.regionInstability); assertArrayEquals(before.regionInked, after.regionInked);
        assertArrayEquals(before.pairPathLen, after.pairPathLen);
        for (int p = 0; p < before.pairCount; p++) assertArrayEquals(before.pairPath[p], after.pairPath[p]);
    }

    @Test void bypassIsRetainedByExistingThreatValueAndContinuesThroughCoreDestruction() throws Exception {
        for (int me = 0; me < 2; me++) {
            GameState s = connection(me, false, 1), saved = s.copy();
            RolloutBotV10 b = prepared(s, me, 1);
            propose(b, s, me, 0);
            assertEquals(0, ((int[]) field(b, "counts"))[me], "the existing free rail route offers no construction");
            int queries = b.routeQueries;
            propose(b, s, me, 3);
            assertEquals(queries + 1, b.routeQueries);
            assertEquals(2, ((int[]) field(b, "counts"))[me], "both affordable bypass ends survive the existing portfolio");
            assertTrue(((double[][]) field(b, "values"))[me][0] > 0);
            int[] first = build(b, s, me, 0);
            assertArrayEquals(new int[]{4, 5, 6}, first);
            assertArrayEquals(new int[]{7, 6, 5}, build(b, s, me, 1));
            legal(s, me, placements(s, first)); unchanged(saved, s);

            // Complete the actual two-turn build sequence, with the threatened old rail destroyed
            // between prefixes. Continuation is the existing fillBuild, never a copied decoder.
            GameState next = s.copy();
            Rules.applyPlacements(next, me == 0 ? first : new int[0], me == 0 ? first.length : 0,
                    me == 1 ? first : new int[0], me == 1 ? first.length : 0);
            Rules.ink(next, 1); Rules.updateConnections(next, new Pathing(next.size));
            int[] last = build(b, next, me, 0);
            assertArrayEquals(new int[]{7}, last); legal(next, me, placements(next, last));
            Rules.applyPlacements(next, me == 0 ? last : new int[0], me == 0 ? last.length : 0,
                    me == 1 ? last : new int[0], me == 1 ? last.length : 0);
            Rules.updateConnections(next, new Pathing(next.size));
            int[] income = new int[2]; Rules.incomePerTurn(next, income);
            assertEquals(4, income[me]); assertEquals(0, income[1 - me]); unchanged(saved, s);
        }
    }

    @Test void geometryAloneCannotBypassTheExistingNonpositiveGainFilter() throws Exception {
        for (int me = 0; me < 2; me++) {
            GameState s = connection(me, false, 1), saved = s.copy();
            RolloutBotV10 b = prepared(s, me, -1);
            propose(b, s, me, 3);
            assertEquals(1, b.routeQueries, "a route was found but its completion does not change the shorter scored path");
            assertEquals(0, ((int[]) field(b, "counts"))[me]); unchanged(saved, s);
        }
    }

    @Test void inactiveStableProtectedAndEnemyIncomePairsSpendNoAdditionalPathQuery() throws Exception {
        for (int me = 0; me < 2; me++) for (int kind = 0; kind < 4; kind++) {
            GameState s = connection(kind == 3 ? 1 - me : me, kind == 2, 1);
            if (kind == 0) s.owner[2] = GameState.NO_TRACK;
            if (kind == 1) { Arrays.fill(s.instability, 0); Arrays.fill(s.regionInstability, 0); }
            Rules.updateConnections(s, new Pathing(s.size)); GameState saved = s.copy();
            RolloutBotV10 b = prepared(s, me, -1); propose(b, s, me, 3);
            assertEquals(0, b.routeQueries, "ineligible case " + kind);
            assertEquals(0, ((int[]) field(b, "counts"))[me]); unchanged(saved, s);
        }
    }

    @Test void liveEnumerationAddsOnlyOurQueryAndHonorsOddRemainingGlobalCapacity() {
        for (int me = 0; me < 2; me++) {
            GameState enemy = connection(1 - me, false, 1), saved = enemy.copy();
            RolloutBotV10 off = new RolloutBotV10(parameters(false)), on = new RolloutBotV10(parameters(true));
            off.init(enemy, me); on.init(enemy, me);
            var oldAction = off.play(enemy, me, AMPLE_MS); var newAction = on.play(enemy, me, AMPLE_MS);
            assertEquals(off.routeQueries, on.routeQueries, "opponent portfolio receives no fourth route variant");
            assertEquals(Action.format(oldAction), Action.format(newAction)); legal(enemy, me, newAction); unchanged(saved, enemy);

            GameState own = connection(me, false, 2); saved = own.copy();
            Params limited = parameters(true); limited.ROLLOUT_MAX_ROUTES = 3;
            RolloutBotV10 capped = new RolloutBotV10(limited); capped.init(own, me);
            var action = capped.play(own, me, AMPLE_MS);
            assertEquals(2 * limited.ROLLOUT_MAX_ROUTES, capped.routeQueries);
            legal(own, me, action); unchanged(saved, own);
            assertEquals("WAIT", Action.format(capped.play(own, me, 0)));
            assertEquals(0, capped.routeQueries); unchanged(saved, own);
        }
    }

    @Test void disabledFlagMatchesFrozenV09AcrossEvolvingPositionsWithAmpleTime() {
        assertFalse(new Params().USE_ROLLOUT_ALL_CELL_RISK);
        for (GameState s : List.of(connection(0, false, 1), MapGenerator.generate(23))) {
            s.turn = 10;
            Rules.updateConnections(s, new Pathing(s.size));
            Params p = BotFactory.parameters("v09"); p.USE_ROLLOUT_ALL_CELL_RISK = false;
            RolloutBotV10[] current = {new RolloutBotV10(p), new RolloutBotV10(p)};
            RolloutBotV09[] frozen = {new RolloutBotV09(p), new RolloutBotV09(p)};
            for (int me = 0; me < 2; me++) { current[me].init(s, me); frozen[me].init(s, me); }
            for (int turn = 0; turn < 3; turn++) {
                List<List<Action>> chosen = new ArrayList<>(); GameState saved = s.copy();
                for (int me = 0; me < 2; me++) {
                    var expected = frozen[me].play(s, me, AMPLE_MS);
                    var actual = current[me].play(s, me, AMPLE_MS);
                    assertEquals(Action.format(expected), Action.format(actual));
                    assertEquals(frozen[me].routeQueries, current[me].routeQueries);
                    assertEquals(frozen[me].completedComparisons, current[me].completedComparisons);
                    assertEquals(frozen[me].selectedValue, current[me].selectedValue);
                    assertTrue(current[me].ordinarySearchComplete);
                    legal(s, me, actual); unchanged(saved, s); chosen.add(actual);
                }
                Rules.resolve(s, chosen.get(0), chosen.get(1), new Pathing(s.size));
            }
        }
    }
}
