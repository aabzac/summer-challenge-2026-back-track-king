package btk.bot;

import btk.bot.neural.NeuralBot;
import btk.bot.neural.NeuralFeatures;
import btk.bot.neural.NeuralNet;
import btk.bot.neural.NeuralWeights;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.referee.MapGenerator;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** neural-v1: legality, closing-ink guard, memory features and weight/feature consistency. */
class NeuralBotTest {

    private static int feature(String[] names, String name) {
        int i = Arrays.asList(names).indexOf(name);
        assertTrue(i >= 0, name);
        return i;
    }

    @Test void weightsMatchFeatureDefinitionAndDecode() {
        NeuralNet sabotage = new NeuralNet(NeuralWeights.SABOTAGE_SIZES, NeuralWeights.SABOTAGE);
        NeuralNet build = new NeuralNet(NeuralWeights.BUILD_SIZES, NeuralWeights.BUILD);
        assertEquals(NeuralFeatures.SABOTAGE_F, sabotage.inputs());
        assertEquals(NeuralFeatures.BUILD_F, build.inputs());
        assertEquals(NeuralFeatures.SABOTAGE_F, NeuralFeatures.SABOTAGE_NAMES.length);
        assertEquals(NeuralFeatures.BUILD_F, NeuralFeatures.BUILD_NAMES.length);
        float[] zeros = new float[NeuralFeatures.BUILD_F];
        assertTrue(Float.isFinite(build.score(zeros)));
        assertTrue(Float.isFinite(sabotage.score(new float[NeuralFeatures.SABOTAGE_F])));
        // A blob for the wrong architecture must be rejected instead of silently misread.
        assertThrows(RuntimeException.class, () -> new NeuralNet(new int[]{NeuralFeatures.SABOTAGE_F, 2, 1}, NeuralWeights.BUILD));
    }

    @Test void playsLegalActionsOnBothSeatsAndDisruptsWheneverPossible() {
        for (int seed : new int[]{4242, 777}) for (int me = 0; me < 2; me++) {
            GameState s = MapGenerator.generate(seed);
            Pathing pathing = new Pathing(s.size);
            Rules.updateConnections(s, pathing);
            NeuralBot bot = new NeuralBot(BotFactory.parameters("neural-v1"));
            bot.init(s.copy(), me);
            Bot opponent = BotFactory.create("v09");
            opponent.init(s.copy(), 1 - me);
            int placements = 0, disrupts = 0, turns = 0;
            for (int turn = 0; turn < 40 && !Rules.gameOver(s, pathing); turn++) {
                List<Action> mine = bot.play(s.copy(), me, 60_000);
                var result = new Rules.TurnResult();
                Rules.expand(s, me, mine, pathing, result.moves[me], result);
                assertNull(result.moves[me].error, "seed " + seed + " turn " + turn);
                assertTrue(result.moves[me].skipped.isEmpty(), "seed " + seed + " turn " + turn + ": " + result.moves[me].skipped);
                boolean legalRegion = false;
                for (int r = 0; r < s.regionCount; r++) if (!s.regionInked[r] && !s.regionHasTown(r)) legalRegion = true;
                boolean disrupted = mine.stream().anyMatch(a -> a.type == Action.Type.DISRUPT);
                if (legalRegion && bot.lastTarget >= 0) assertTrue(disrupted);
                if (disrupted) disrupts++;
                placements += result.moves[me].count;
                List<Action> theirs = opponent.play(s.copy(), 1 - me, 60_000);
                Rules.resolve(s, me == 0 ? mine : theirs, me == 1 ? mine : theirs, pathing);
                turns++;
            }
            assertTrue(turns >= 10, "fixture game too short");
            assertTrue(placements >= turns, "neural-v1 must keep building: " + placements + " cells in " + turns + " turns");
            assertTrue(disrupts >= turns * 0.9, "neural-v1 sabotages nearly every turn: " + disrupts + "/" + turns);
        }
    }

    /** 5x3 board: both towns sit in their own column regions, the middle region is the only corridor. */
    private static GameState corridor(int me, int instability, int leadForMe) {
        int[] regions = new int[15];
        for (int c = 0; c < 15; c++) regions[c] = c % 5 == 0 ? 0 : c % 5 == 4 ? 2 : 1;
        GameState s = new GameState(5, 3, regions, new int[15], new int[]{0, 4}, new int[]{1, 1}, new int[][]{{1}, {}});
        for (int x = 1; x < 4; x++) s.owner[s.cell(x, 1)] = 1 - me;   // the opponent earns 3 per turn here
        for (int i = 0; i < instability; i++) Rules.disrupt(s, 1);
        s.score[me] = 100 + leadForMe;
        s.score[1 - me] = 100;
        s.turn = 20;
        Rules.updateConnections(s, new Pathing(s.size));
        return s;
    }

    @Test void closingInkIsRefusedWhenBehindAndForcedWhenClearlyAhead() {
        for (int me = 0; me < 2; me++) {
            NeuralBot behind = new NeuralBot(BotFactory.parameters("neural-v1?NOISE=0"));
            GameState s = corridor(me, 3, -1);
            behind.init(s.copy(), me);
            List<Action> actions = behind.play(s.copy(), me, 60_000);
            assertEquals(-1, behind.lastTarget, "the only legal target closes the game while we are behind");
            assertTrue(actions.stream().noneMatch(a -> a.type == Action.Type.DISRUPT));

            NeuralBot ahead = new NeuralBot(BotFactory.parameters("neural-v1?NOISE=0"));
            GameState w = corridor(me, 3, NeuralBot.CLOSE_MARGIN + 10);
            ahead.init(w.copy(), me);
            actions = ahead.play(w.copy(), me, 60_000);
            assertEquals(1, ahead.lastTarget, "a closing ink that wins outright is taken");
            assertTrue(actions.stream().anyMatch(a -> a.type == Action.Type.DISRUPT && a.regionId == 1));

            // Two hits short of ink the same region is an ordinary candidate again.
            NeuralBot open = new NeuralBot(BotFactory.parameters("neural-v1?NOISE=0"));
            GameState o = corridor(me, 2, -1);
            open.init(o.copy(), me);
            open.play(o.copy(), me, 60_000);
            assertEquals(1, open.lastTarget);
        }
    }

    @Test void memoryDatesOpponentTracksAndAttributesInstabilityRises() {
        int me = 0;
        GameState s = MapGenerator.generate(4242);
        Pathing pathing = new Pathing(s.size);
        Rules.updateConnections(s, pathing);
        NeuralFeatures f = new NeuralFeatures(s);
        int[] regions = new int[s.regionCount];
        float[][] x = new float[s.regionCount][NeuralFeatures.SABOTAGE_F];
        int oppPrev = feature(NeuralFeatures.SABOTAGE_NAMES, "oppPlacedPrev");
        int hitMe = feature(NeuralFeatures.SABOTAGE_NAMES, "hitPrevByMe");
        int hitOpp = feature(NeuralFeatures.SABOTAGE_NAMES, "hitPrevByOpp");
        // Turn 0: nothing observed yet.
        f.observe(s, me);
        f.prepare(s, me, true);
        int n = f.sabotageCandidates(s, me, regions, x);
        assertTrue(n > 0);
        for (int i = 0; i < n; i++) assertEquals(0f, x[i][oppPrev] + x[i][hitMe] + x[i][hitOpp]);
        int mine = regions[0], theirs = regions[1], both = regions[2];
        f.recordTarget(mine);
        // Opponent places two tracks in `theirs`; instability rises by 1 (mine: our hit), 1 (theirs) and 2 (both: not ours).
        int placed = 0;
        for (int c = 0; c < s.size && placed < 2; c++) if (s.regionId[c] == theirs && s.canBuild(c)) { s.owner[c] = 1; placed++; }
        assertEquals(2, placed);
        Rules.disrupt(s, mine);
        Rules.disrupt(s, theirs);
        Rules.disrupt(s, both);
        Rules.disrupt(s, both);
        Rules.updateConnections(s, pathing);
        s.turn = 1;
        f.observe(s, me);
        f.prepare(s, me, true);
        n = f.sabotageCandidates(s, me, regions, x);
        for (int i = 0; i < n; i++) {
            int r = regions[i];
            assertEquals(r == theirs ? 2f : 0f, x[i][oppPrev], "oppPlacedPrev region " + r);
            assertEquals(r == mine ? 1f : 0f, x[i][hitMe], "hitPrevByMe region " + r);
            assertEquals(r == theirs || r == both ? 1f : 0f, x[i][hitOpp], "hitPrevByOpp region " + r);
            for (float v : x[i]) assertTrue(Float.isFinite(v));
        }
    }

    @Test void featurePathsCostExactlyWhatTheRefereePathingCosts() {
        int checked = 0, reachable = 0;
        for (int seed : new int[]{4242, 777, 1000}) {
            GameState s = MapGenerator.generate(seed);
            Pathing pathing = new Pathing(s.size);
            Rules.updateConnections(s, pathing);
            Bot a = BotFactory.create("v09"), b = BotFactory.create("leader-v1");
            a.init(s.copy(), 0);
            b.init(s.copy(), 1);
            NeuralFeatures f = new NeuralFeatures(s);
            int[] fast = new int[s.size], exact = new int[s.size];
            boolean[] blocked = new boolean[s.size];
            for (int turn = 0; turn < 30 && !Rules.gameOver(s, pathing); turn++) {
                for (int p = 0; p < s.pairCount; p++) {
                    int from = s.townCell[s.pairFrom[p]], to = s.townCell[s.pairTo[p]];
                    for (int variant = 0; variant < 2; variant++) {
                        boolean[] block = null;
                        if (variant == 1) {
                            for (int c = 0; c < s.size; c++) blocked[c] = s.owner[c] == 1;
                            block = blocked;
                        }
                        int n1 = f.cheapestCostPath(s, from, to, fast, block);
                        int n2 = pathing.cheapestPath(s, from, to, exact, block);
                        assertEquals(n2 > 0, n1 > 0, "reachability seed " + seed + " turn " + turn + " pair " + p);
                        checked++;
                        if (n1 <= 0) continue;
                        reachable++;
                        int fastCost = 0;
                        for (int i = 0; i < n1; i++) if (s.canBuild(fast[i])) fastCost += s.placeCost(fast[i]);
                        assertEquals(pathing.lastCost(to), fastCost, "cost seed " + seed + " turn " + turn + " pair " + p);
                        assertEquals(fastCost, f.lastCost(to));
                        assertEquals(from, fast[0]);
                        assertEquals(to, fast[n1 - 1]);
                    }
                }
                Rules.resolve(s, a.play(s.copy(), 0, 60_000), b.play(s.copy(), 1, 60_000), pathing);
            }
        }
        assertTrue(checked > 200 && reachable > 100, checked + "/" + reachable);
    }

    @Test void featureRowsAreDeterministicAndSequentialPlacementUpdatesTheBoard() {
        int me = 1;
        GameState s = MapGenerator.generate(777);
        Pathing pathing = new Pathing(s.size);
        // A few opponent tracks and one active connection of ours make the route features non-trivial.
        Bot v09 = BotFactory.create("v09");
        v09.init(s.copy(), 0);
        Rules.updateConnections(s, pathing);
        for (int t = 0; t < 6; t++) Rules.resolve(s, v09.play(s.copy(), 0, 60_000), List.of(Action.waitAction()), pathing);
        NeuralFeatures a = new NeuralFeatures(s), b = new NeuralFeatures(s);
        int[] cellsA = new int[s.size], cellsB = new int[s.size];
        float[][] xa = new float[s.size][NeuralFeatures.BUILD_F], xb = new float[s.size][NeuralFeatures.BUILD_F];
        a.observe(s, me); a.prepare(s, me, true);
        b.observe(s, me); b.prepare(s, me, true);
        int na = a.buildCandidates(s, me, 3, -1, 0, cellsA, xa), nb = b.buildCandidates(s, me, 3, -1, 0, cellsB, xb);
        assertEquals(na, nb);
        assertTrue(na > 10);
        for (int i = 0; i < na; i++) {
            assertEquals(cellsA[i], cellsB[i]);
            assertArrayEquals(xa[i], xb[i]);
            assertTrue(s.canBuild(cellsA[i]));
            assertTrue(s.placeCost(cellsA[i]) <= 3);
        }
        // Placing the first candidate: the remaining paint shrinks and the cell disappears from the next set.
        int c = cellsA[0];
        GameState work = s.copy();
        work.owner[c] = me;
        a.markPlaced(c);
        Rules.updateConnections(work, pathing);
        a.prepare(work, me, false);
        int paint = 3 - s.placeCost(c);
        int m = a.buildCandidates(work, me, paint, -1, 1, cellsA, xa);
        int adjPlaced = feature(NeuralFeatures.BUILD_NAMES, "adjPlacedThisTurn");
        int paintLeft = feature(NeuralFeatures.BUILD_NAMES, "paintLeft");
        int index = feature(NeuralFeatures.BUILD_NAMES, "placementIndex");
        boolean neighbourSeen = false;
        for (int i = 0; i < m; i++) {
            assertNotEquals(c, cellsA[i]);
            assertTrue(s.placeCost(cellsA[i]) <= paint);
            assertEquals(paint, (int) xa[i][paintLeft]);
            assertEquals(1, (int) xa[i][index]);
            if (xa[i][adjPlaced] == 1f) neighbourSeen = true;
        }
        assertTrue(paint == 0 || neighbourSeen, "cells next to this turn's placement are flagged");
    }
}
