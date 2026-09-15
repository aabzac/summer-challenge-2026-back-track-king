package btk.bot.current;

import btk.bot.Params;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.referee.MapGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HeuristicDetourTest {
    private static Params params(boolean enabled) {
        return new Params("USE_HEURISTIC_POLICY=1,HEURISTIC_EMERGENCY_STOP=0,USE_HEURISTIC_DETOUR=" + enabled);
    }

    @Test void chargesRecoveryPaintOnlyOncePerUndirectedConnection() {
        for (int me = 0; me < 2; me++) {
            GameState s = grid(5, 3, 1);
            for (int x = 1; x < 4; x++) s.owner[s.cell(x, 1)] = 1 - me;
            Rules.updateConnections(s, new Pathing(s.size));
            HeuristicBot bot = new HeuristicBot(params(true)); bot.init(s, me);
            legal(s, me, bot.play(s, me, 0));
            assertEquals(3, bot.lastDetourQueries); // three regions, not doubled by A->B / B->A
            assertEquals(3, bot.detourAdjustment(s.cell(2, 1)), 1e-9);
            assertEquals(3, bot.lastDetourRegions);
        }
    }

    @Test void readyBypassIsFreeAndChangesTargetRatherThanCountingExistingTrackAgain() {
        for (int me = 0; me < 2; me++) {
            GameState s = grid(7, 3, 1);
            for (int x = 1; x < 6; x++) s.owner[s.cell(x, 1)] = 1 - me;
            Rules.updateConnections(s, new Pathing(s.size));
            int old = target(s, me, false);
            // A longer, already-built neutral route around the old target. Current shortest stays intact.
            for (int x = s.x(old) - 1; x <= s.x(old) + 1; x++) s.owner[s.cell(x, 0)] = GameState.NEUTRAL;
            Rules.updateConnections(s, new Pathing(s.size));
            assertEquals(old, target(s, me, false));
            HeuristicBot bot = new HeuristicBot(params(true)); bot.init(s, me);
            var moves = legal(s, me, bot.play(s, me, 0));
            assertEquals(0, bot.detourAdjustment(old), 1e-9);
            assertEquals(1, moves.disrupts.size());
            assertNotEquals(old, moves.disrupts.get(0));
            assertTrue(bot.detourAdjustment(moves.disrupts.get(0)) > 0);
        }
    }

    @Test void impossibleRecoveryIsCappedAndOwnCollateralCanVetoBaseTarget() {
        for (int me = 0; me < 2; me++) {
            GameState enemyOnly = grid(3, 1, 0); enemyOnly.owner[1] = 1 - me;
            Rules.updateConnections(enemyOnly, new Pathing(enemyOnly.size));
            HeuristicBot a = new HeuristicBot(params(true)); a.init(enemyOnly, me);
            assertEquals(List.of(1), legal(enemyOnly, me, a.play(enemyOnly, me, 0)).disrupts);
            assertEquals(12, a.detourAdjustment(1), 1e-9);

            GameState mixed = grid(5, 1, 0);
            mixed.owner[1] = me; mixed.owner[2] = 1 - me; mixed.owner[3] = me;
            Rules.updateConnections(mixed, new Pathing(mixed.size));
            assertEquals(2, target(mixed, me, false));
            HeuristicBot b = new HeuristicBot(params(true)); b.init(mixed, me);
            assertTrue(legal(mixed, me, b.play(mixed, me, 0)).disrupts.isEmpty());
            assertEquals(-12, b.detourAdjustment(2), 1e-9);
        }
    }

    @Test void capUsesTerrainPaintAndDoesNotInventUnfinishedOpponentRoutes() {
        GameState s = grid(5, 3, 1);
        for (int x = 1; x < 4; x++) s.owner[s.cell(x, 1)] = 1;
        for (int c = 0; c < s.size; c++) if (s.y(c) != 1) s.terrain[c] = GameState.MOUNTAIN;
        Rules.updateConnections(s, new Pathing(s.size));
        Params p = params(true); p.HEURISTIC_DETOUR_CAP = 5;
        HeuristicBot bot = new HeuristicBot(p); bot.init(s, 0); bot.play(s, 0, 0);
        assertEquals(5, bot.detourAdjustment(s.cell(2, 1)), 1e-9); // actual detour is nine paint
        s.owner[s.cell(2, 1)] = GameState.NO_TRACK;
        Rules.updateConnections(s, new Pathing(s.size));
        bot.play(s, 0, 0);
        assertEquals(0, bot.lastDetourQueries);
        assertEquals(0, bot.detourAdjustment(s.cell(1, 1)), 1e-9);
    }

    @Test void boundAndInvalidConfigurationAreExplicit() {
        GameState s = grid(5, 3, 1);
        for (int x = 1; x < 4; x++) s.owner[s.cell(x, 1)] = 1;
        Rules.updateConnections(s, new Pathing(s.size));
        Params p = params(true); p.HEURISTIC_DETOUR_PAIRS = p.HEURISTIC_DETOUR_REGIONS = 1;
        HeuristicBot bot = new HeuristicBot(p); bot.init(s, 0); legal(s, 0, bot.play(s, 0, 0));
        assertEquals(1, bot.lastDetourQueries); assertEquals(1, bot.lastDetourRegions);
        for (String bad : new String[]{"HEURISTIC_DETOUR_PAIRS=0", "HEURISTIC_DETOUR_REGIONS=17",
                "HEURISTIC_DETOUR_WEIGHT=NaN", "HEURISTIC_DETOUR_CAP=0"}) {
            assertThrows(IllegalArgumentException.class, () -> new HeuristicBot(new Params(
                    "USE_HEURISTIC_POLICY=1,USE_HEURISTIC_DETOUR=1," + bad)));
        }
        p.HEURISTIC_EMERGENCY_STOP = true;
        bot = new HeuristicBot(p); bot.init(s, 0);
        assertEquals("WAIT", Action.format(bot.play(s, 0, 0)));
        assertTrue(bot.lastEmergency); assertEquals(0, bot.lastDetourQueries);
    }

    @Test void completeOnSequencesAreReproducibleLegalAndDoNotMutateObservations() {
        int totalQueries = 0;
        for (int seed = 24030; seed < 24032; seed++) {
            GameState s = MapGenerator.generate(seed); Pathing paths = new Pathing(s.size);
            // Identical simple bots can mirror into neutral rails. Start from a legal observed
            // opponent-owned route so this regression must exercise the new cost calculation.
            int[] initialRoute = new int[s.size];
            int n = paths.cheapestPath(s, s.townCell[s.pairFrom[0]], s.townCell[s.pairTo[0]], initialRoute);
            assertTrue(n > 0);
            for (int i = 0; i < n; i++) if (s.canBuild(initialRoute[i])) s.owner[initialRoute[i]] = 1;
            Rules.updateConnections(s, paths);
            HeuristicBot[] zero = {new HeuristicBot(params(true)), new HeuristicBot(params(true))};
            HeuristicBot[] ample = {new HeuristicBot(params(true)), new HeuristicBot(params(true))};
            for (int me = 0; me < 2; me++) { zero[me].init(s, me); ample[me].init(s, me); }
            for (int turn = 0; turn < 50 && !Rules.gameOver(s, paths); turn++) {
                GameState before = s.copy();
                var a = zero[0].play(s, 0, 0); var b = zero[1].play(s, 1, 0);
                assertEquals(Action.format(a), Action.format(ample[0].play(s, 0, 60_000)));
                assertEquals(Action.format(b), Action.format(ample[1].play(s, 1, 60_000)));
                assertArrayEquals(before.owner, s.owner); assertArrayEquals(before.inked, s.inked);
                assertArrayEquals(before.regionInstability, s.regionInstability);
                assertArrayEquals(before.score, s.score); assertArrayEquals(before.pairPathLen, s.pairPathLen);
                for (int pair = 0; pair < s.pairCount; pair++) assertArrayEquals(before.pairPath[pair], s.pairPath[pair]);
                for (var bot : zero) {
                    totalQueries += bot.lastDetourQueries;
                    assertTrue(bot.lastDetourQueries <= 16); assertTrue(bot.lastRouteQueries <= 64);
                    assertEquals(0, bot.emergencyStops);
                }
                legal(s, 0, a); legal(s, 1, b); Rules.resolve(s, a, b, paths);
            }
        }
        assertTrue(totalQueries > 0);
    }

    private static int target(GameState s, int me, boolean on) {
        HeuristicBot bot = new HeuristicBot(params(on)); bot.init(s, me);
        return legal(s, me, bot.play(s, me, 0)).disrupts.get(0);
    }
    private static Rules.PlayerMoves legal(GameState s, int me, List<Action> actions) {
        var moves = new Rules.PlayerMoves();
        Rules.expand(s, me, actions, new Pathing(s.size), moves, new Rules.TurnResult());
        assertNull(moves.error); assertTrue(moves.skipped.isEmpty(), moves.skipped.toString());
        return moves;
    }
    private static GameState grid(int width, int height, int y) {
        int[] regions = new int[width * height];
        for (int c = 0; c < regions.length; c++) regions[c] = c;
        return new GameState(width, height, regions, new int[regions.length],
                new int[]{0, width - 1}, new int[]{y, y}, new int[][]{{1}, {0}});
    }
}
