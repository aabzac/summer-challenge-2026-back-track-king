package btk.tools;

import btk.bot.BotFactory;
import btk.bot.Timer;
import btk.bot.v09.RolloutBotV09;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class V09ClosureProbeTest {
    private static final int[] EMPTY = new int[0];

    @Test void readyBottleneckCanBeAbsentFromFrozenShortlistWithZeroOrNegativeExposure() throws Exception {
        for (int me = 0; me < 2; me++) {
          for (boolean activeOwnPath : new boolean[]{false, true}) {
            GameState s = line(9);
            instability(s, 5, 3);
            s.score[me] = 20; s.score[1 - me] = 10;
            if (activeOwnPath) for (int cell = 1; cell < 8; cell++) s.owner[cell] = me;
            Rules.updateConnections(s, new Pathing(s.size));
            var bot = (RolloutBotV09) BotFactory.create("v09?NOISE=0");
            bot.init(s, me);
            Field timer = RolloutBotV09.class.getDeclaredField("timer");
            timer.setAccessible(true); ((Timer) timer.get(bot)).start(60_000);
            Method target = RolloutBotV09.class.getDeclaredMethod("chooseTarget", GameState.class, int.class);
            target.setAccessible(true);
            assertEquals(-1, (int) target.invoke(bot, s, me));
            Field shortlist = RolloutBotV09.class.getDeclaredField("shortlist");
            shortlist.setAccessible(true);
            assertArrayEquals(new int[]{1, 2, 3, 4}, (int[]) shortlist.get(bot));

            var rows = V09ClosureProbe.scan(s, me, EMPTY);
            assertEquals(1, rows.size());
            var row = rows.get(0);
            assertEquals(5, row.region()); assertEquals(1, row.hitsNeeded());
            assertEquals(3, row.instability()); assertNull(row.withJointHit());
            assertTrue(row.versusPassive().terminal()); assertTrue(row.versusPassive().topologyClosed());
            assertFalse(row.versusPassive().turnLimitReached());
            assertEquals(10, row.versusPassive().scoreDifference());
            assertEquals(0, row.versusPassive().ownIncome());
            assertEquals(0, row.versusPassive().opponentIncome());
          }
        }
    }

    @Test void oneHitPotentialCutPreservesBankedResultDespiteSimultaneousConstructionOrOtherInk() {
        for (int me = 0; me < 2; me++) {
            GameState s = line(5); instability(s, 2, 3); instability(s, 3, 3);
            s.score[me] = 7; s.score[1 - me] = 6;
            for (int enemyTarget : new int[]{-1, 1, 2, 3}) {
                var result = V09ClosureProbe.evaluate(s, me, new int[]{1, 2}, 2,
                        new int[]{2, 3}, enemyTarget);
                assertTrue(result.topologyClosed()); assertTrue(result.terminal());
                assertEquals(1, result.scoreDifference());
                assertEquals(0, result.ownIncome()); assertEquals(0, result.opponentIncome());
                assertEquals(0, result.ownScoreGain()); assertEquals(0, result.opponentScoreGain());
            }
        }
    }

    @Test void twoHitRegionIsOnlyAClosureInSeparatelyLabelledJointScenario() {
        for (int me = 0; me < 2; me++) {
            GameState s = line(5); instability(s, 2, 2); s.score[me] = 4;
            var row = V09ClosureProbe.scan(s, me, EMPTY).get(0);
            assertEquals(2, row.hitsNeeded());
            assertFalse(row.versusPassive().terminal()); assertFalse(row.versusPassive().topologyClosed());
            assertNotNull(row.withJointHit()); assertTrue(row.withJointHit().terminal());
            assertTrue(row.withJointHit().topologyClosed());
            assertEquals(4, row.withJointHit().scoreDifference());
            assertFalse(V09ClosureProbe.evaluate(s, me, EMPTY, 2, EMPTY, 1).terminal());
        }
    }

    @Test void losingAndDrawingClosuresRemainInScanWithoutBeingCalledWins() {
        GameState s = line(5); instability(s, 2, 3);
        s.score[1] = 7;
        var losing = V09ClosureProbe.scan(s, 0, EMPTY).get(0).versusPassive();
        assertTrue(losing.topologyClosed()); assertEquals(-7, losing.scoreDifference());
        s.score[0] = 7;
        var drawing = V09ClosureProbe.scan(s, 0, EMPTY).get(0).versusPassive();
        assertTrue(drawing.topologyClosed()); assertEquals(0, drawing.scoreDifference());
    }

    @Test void protectedLowInstabilityAndNonBottleneckRegionsAreExcluded() {
        GameState s = line(5);
        instability(s, 0, 3); instability(s, 4, 3); instability(s, 1, 1);
        assertTrue(V09ClosureProbe.scan(s, 0, EMPTY).isEmpty());
        GameState grid = new GameState(3, 3, new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8}, new int[9],
                new int[]{0, 2}, new int[]{1, 1}, new int[][]{{1}, {}});
        instability(grid, 4, 3); instability(grid, 0, 3); Rules.ink(grid, 0);
        assertTrue(V09ClosureProbe.scan(grid, 0, EMPTY).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> V09ClosureProbe.evaluate(grid, 0, EMPTY, 0, EMPTY, -1));
    }

    @Test void fixedOpponentBuildsCollisionsIncomeAndPlayerPerspectiveUseCore() {
        for (int me = 0; me < 2; me++) {
            GameState s = line(5); s.owner[2] = me; s.score[me] = 10; s.score[1 - me] = 8;
            var built = V09ClosureProbe.evaluate(s, me, new int[]{1}, -1, new int[]{3}, -1);
            assertFalse(built.terminal()); assertEquals(2, built.ownIncome());
            assertEquals(1, built.opponentIncome()); assertEquals(3, built.scoreDifference());
            assertEquals(2, built.ownScoreGain()); assertEquals(1, built.opponentScoreGain());
            assertEquals(1, built.nextTurn());
            s.owner[3] = 1 - me;
            var collided = V09ClosureProbe.evaluate(s, me, new int[]{1}, -1, new int[]{1}, -1);
            assertEquals(1, collided.ownIncome()); assertEquals(1, collided.opponentIncome());
            assertEquals(2, collided.scoreDifference());
        }
    }

    @Test void turnLimitAndTopologyClosureAreIndependentFlags() {
        GameState s = line(5); s.turn = 9; s.maxTurns = 10;
        var limit = V09ClosureProbe.evaluate(s, 0, EMPTY, -1, EMPTY, -1);
        assertTrue(limit.terminal()); assertTrue(limit.turnLimitReached()); assertFalse(limit.topologyClosed());
        instability(s, 2, 3);
        var both = V09ClosureProbe.scan(s, 0, EMPTY).get(0).versusPassive();
        assertTrue(both.terminal()); assertTrue(both.turnLimitReached()); assertTrue(both.topologyClosed());
    }

    @Test void invalidConcreteActionsAndAlreadyTerminalInputsAreRejected() {
        GameState s = line(9);
        assertThrows(IllegalArgumentException.class, () -> V09ClosureProbe.evaluate(s, 0, EMPTY, 0, EMPTY, -1));
        assertThrows(IllegalArgumentException.class, () -> V09ClosureProbe.evaluate(s, 0, EMPTY, 99, EMPTY, -1));
        assertThrows(IllegalArgumentException.class, () -> V09ClosureProbe.evaluate(s, 0, EMPTY, -2, EMPTY, -1));
        for (int[] cells : new int[][]{{0}, {-1}, {9}, {1, 1}, {1, 2, 3, 4}})
            assertThrows(IllegalArgumentException.class, () -> V09ClosureProbe.scan(s, 0, cells));
        assertThrows(IllegalArgumentException.class, () -> V09ClosureProbe.evaluate(s, 2, EMPTY, -1, EMPTY, -1));
        s.owner[1] = 0;
        assertThrows(IllegalArgumentException.class, () -> V09ClosureProbe.scan(s, 0, new int[]{1}));
        s.turn = s.maxTurns;
        assertThrows(IllegalArgumentException.class, () -> V09ClosureProbe.scan(s, 0, EMPTY));
        s.turn = 0; Rules.ink(s, 5);
        assertThrows(IllegalArgumentException.class, () -> V09ClosureProbe.evaluate(s, 0, EMPTY, -1, EMPTY, -1));
    }

    @Test void scanAndBothReplyBranchesLeaveSourceAndSuppliedArraysUntouched() {
        GameState s = line(5); instability(s, 2, 2); s.owner[3] = 1;
        Rules.updateConnections(s, new Pathing(s.size));
        GameState before = s.copy(); int[] own = {1}; int[] enemy = {1};
        V09ClosureProbe.scan(s, 0, own);
        V09ClosureProbe.evaluate(s, 0, own, 2, enemy, 2);
        assertArrayEquals(new int[]{1}, own); assertArrayEquals(new int[]{1}, enemy);
        assertArrayEquals(before.owner, s.owner); assertArrayEquals(before.instability, s.instability);
        assertArrayEquals(before.inked, s.inked); assertArrayEquals(before.regionInstability, s.regionInstability);
        assertArrayEquals(before.regionInked, s.regionInked); assertArrayEquals(before.score, s.score);
        assertArrayEquals(before.pairPathLen, s.pairPathLen); assertEquals(before.turn, s.turn);
        assertEquals(before.maxTurns, s.maxTurns); assertTrue(Arrays.deepEquals(before.pairPath, s.pairPath));
    }

    private static GameState line(int size) {
        int[] regions = new int[size];
        for (int i = 0; i < size; i++) regions[i] = i;
        return new GameState(size, 1, regions, new int[size], new int[]{0, size - 1},
                new int[]{0, 0}, new int[][]{{1}, {}});
    }

    private static void instability(GameState s, int region, int level) {
        for (int i = 0; i < level; i++) Rules.disrupt(s, region);
    }
}
