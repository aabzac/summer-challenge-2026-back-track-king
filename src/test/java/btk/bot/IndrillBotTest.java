package btk.bot;

import btk.bot.current.IndrillBot;
import btk.bot.current.IndrillFeatures;
import btk.bot.current.IndrillNet;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.referee.MapGenerator;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class IndrillBotTest {
    @Test void jointKnapsackMatchesExhaustiveEnumerationIncludingNegativeValuesAndTerrainCosts() {
        Random random = new Random(20261003);
        for (int trial = 0; trial < 1000; trial++) {
            float[] q = new float[10]; int[] costs = new int[10], selected = new int[3];
            for (int i = 0; i < q.length; i++) { q[i] = random.nextInt(21) - 10; costs[i] = random.nextInt(4); }
            float best = 0;
            for (int mask = 0; mask < 1 << q.length; mask++) {
                int paint = 0; float value = 0; boolean valid = true;
                for (int i = 0; i < q.length; i++) if ((mask & (1 << i)) != 0) {
                    if (costs[i] == 0) valid = false;
                    paint += costs[i]; value += q[i];
                }
                if (valid && paint <= 3) best = Math.max(best, value);
            }
            int n = IndrillNet.selectBuild(q, costs, selected), paint = 0;
            float value = 0;
            for (int i = 0; i < n; i++) {
                value += q[selected[i]]; paint += costs[selected[i]];
                if (i > 0) assertTrue(selected[i] > selected[i - 1]);
            }
            assertTrue(paint <= 3); assertEquals(best, value, "trial " + trial);
        }
        int[] cells = new int[3];
        assertEquals(3, IndrillNet.selectBuild(new float[]{3, 1, 1, 1}, new int[]{3, 1, 1, 1}, cells));
        assertArrayEquals(new int[]{1, 2, 3}, cells); // stable pattern priority on ties
    }

    @Test void localAndGlobalContextAndRegionMeansHaveKnownAnalyticalValues() {
        GameState s = new GameState(3, 1, new int[]{0, 0, 1}, new int[3], new int[0], new int[0], new int[0][]);
        float[][] weights = zeroWeights();
        weights[0][0] = 1; weights[2][0] = 1; weights[3][0] = 1; weights[4][0] = 1;
        weights[6][0] = 2; weights[7][0] = 3;
        IndrillFeatures f = new IndrillFeatures(s, new Params());
        f.x[0][0] = 1; f.x[1][0] = 2; f.x[2][0] = 5;
        IndrillNet net = new IndrillNet(weights); net.init(s);
        float[] build = new float[3], ink = new float[2]; net.score(s, f, build, ink);
        assertArrayEquals(new float[]{34f / 3, 46f / 3, 58f / 3}, build, 1e-5f);
        assertArrayEquals(new float[]{20, 29}, ink, 1e-5f);
    }

    @Test void goodMaskContainsOnePaintDetoursAndAltPathAvoidsUnstableShortcut() {
        // A straight route costs four; the upper detour costs five. Building one
        // detour cell makes it optimal, although it is not on a currently best route.
        int[] terrain = new int[15]; terrain[7] = GameState.RIVER;
        int[] regions = new int[15]; Arrays.fill(regions, 1); regions[5] = 0; regions[9] = 2;
        GameState s = new GameState(5, 3, regions, terrain, new int[]{0, 4}, new int[]{1, 1}, new int[][]{{1}, {}});
        IndrillFeatures f = new IndrillFeatures(s, new Params()); f.prepare(s, 0);
        assertTrue(f.x[7][34] > 0);
        assertEquals(0, f.x[1][34]);
        assertTrue(f.x[1][39] > 0);
        s.owner[6] = 1; s.owner[7] = 1; s.owner[8] = 1;
        // Put the risky shortcut in one region; the alternate path has a safe separate region.
        for (int c = 0; c < 5; c++) regions[c] = 3;
        // Region metadata is static per instance, so reconstruct after changing the fixture.
        GameState risky = new GameState(5, 3, regions, terrain, new int[]{0, 4}, new int[]{1, 1}, new int[][]{{1}, {}});
        risky.owner[6] = 1; risky.owner[7] = 1; risky.owner[8] = 1;
        Rules.disrupt(risky, 1); Rules.disrupt(risky, 1); Rules.disrupt(risky, 1);
        f = new IndrillFeatures(risky, new Params()); f.prepare(risky, 0);
        assertTrue(f.x[7][44] > 0);
        assertEquals(0, f.x[7][59]);
        assertTrue(f.x[2][59] > 0);
    }

    @Test void observationAndInferenceAreDeterministicAndDoNotMutateTheLiveBoard() {
        GameState s = MapGenerator.generate(2009001);
        IndrillBot bot = new IndrillBot(BotFactory.parameters("indrill-q-v1")); bot.init(s, 0);
        int[] owner = s.owner.clone(), instability = s.instability.clone();
        boolean[] inked = s.inked.clone();
        List<Action> first = bot.play(s, 0, 36000), second = bot.play(s, 0, 36000);
        assertEquals(Action.format(first), Action.format(second));
        assertArrayEquals(owner, s.owner); assertArrayEquals(instability, s.instability); assertArrayEquals(inked, s.inked);
        for (float[] row : bot.features.x) for (float v : row) assertTrue(Float.isFinite(v));
        assertFalse(Action.format(first).isEmpty());
    }

    @Test void policyRemainsLegalOnBothSeatsAfterSimultaneousConstructionAndInking() {
        for (int me = 0; me < 2; me++) {
            GameState s = MapGenerator.generate(2009002);
            Pathing paths = new Pathing(s.size);
            IndrillBot bot = new IndrillBot(BotFactory.parameters("indrill-q-v1")); bot.init(s, me);
            Bot opponent = BotFactory.create("neural-v1"); opponent.init(s, 1 - me);
            for (int turn = 0; turn < 30 && !Rules.gameOver(s, paths); turn++) {
                List<Action> mine = bot.play(s, me, 36000), theirs = opponent.play(s, 1 - me, 36000);
                var tr = Rules.resolve(s, me == 0 ? mine : theirs, me == 1 ? mine : theirs, paths);
                assertNull(tr.moves[me].error); assertTrue(tr.moves[me].skipped.isEmpty(), tr.moves[me].skipped.toString());
                assertTrue(tr.moves[me].cost <= 3);
            }
        }
    }

    private static float[][] zeroWeights() {
        int[] sizes = {64 * 24, 24, 24 * 24, 24 * 24, 24 * 24, 24, 24, 24, 1, 1};
        float[][] result = new float[sizes.length][];
        for (int i = 0; i < sizes.length; i++) result[i] = new float[sizes[i]];
        return result;
    }
}
