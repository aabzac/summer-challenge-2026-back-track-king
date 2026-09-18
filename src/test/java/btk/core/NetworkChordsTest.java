package btk.core;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NetworkChordsTest {
    private static boolean adjacent(GameState s, int a, int b) {
        return Math.abs(s.x(a) - s.x(b)) + Math.abs(s.y(a) - s.y(b)) == 1;
    }

    private static boolean touches(GameState s, int cell) {
        for (int other = 0; other < s.size; other++) if (s.passable(other) && adjacent(s, cell, other)) return true;
        return false;
    }

    private static String key(int... cells) {
        Arrays.sort(cells);
        return Arrays.toString(cells);
    }

    @Test void collectorMatchesIndependentExhaustiveAffordableChainEnumeration() {
        var s = new GameState(5, 4, new int[20], new int[20],
                new int[]{0, 4}, new int[]{0, 3}, new int[][]{{1}, {}});
        for (int c : new int[]{1, 6, 8, 13, 17}) s.owner[c] = c % 3;
        s.terrain[3] = GameState.RIVER;
        s.terrain[11] = GameState.MOUNTAIN;
        s.inked[7] = true;
        int[] before = s.owner.clone();
        Set<String> expected = new HashSet<>();
        for (int a = 0; a < s.size; a++) for (int b = a + 1; b < s.size; b++) {
            if (!s.canBuild(a) || !s.canBuild(b)) continue;
            if (adjacent(s, a, b) && touches(s, a) && touches(s, b) && s.placeCost(a) + s.placeCost(b) <= 3)
                expected.add(key(a, b));
            for (int c = b + 1; c < s.size; c++) {
                if (!s.canBuild(c) || s.placeCost(a) + s.placeCost(b) + s.placeCost(c) > 3) continue;
                int[] cells = {a, b, c};
                int totalDegree = 0;
                boolean valid = true;
                for (int cell : cells) {
                    int degree = 0;
                    for (int other : cells) if (adjacent(s, cell, other)) degree++;
                    totalDegree += degree;
                    if (degree == 0 || degree == 1 && !touches(s, cell)) valid = false;
                }
                if (valid && totalDegree == 4) expected.add(key(a, b, c));
            }
        }
        int[] a = new int[8 * s.size], b = a.clone(), c = a.clone();
        int count = NetworkChords.collect(s, a, b, c);
        Set<String> actual = new HashSet<>();
        for (int i = 0; i < count; i++) {
            assertTrue(actual.add(c[i] < 0 ? key(a[i], b[i]) : key(a[i], b[i], c[i])), "no duplicate package");
            if (c[i] < 0) assertTrue(a[i] < b[i]);
            else assertTrue(a[i] < c[i]);
        }
        assertFalse(expected.isEmpty());
        assertEquals(expected, actual);
        assertArrayEquals(before, s.owner);
        int[] againA = a.clone(), againB = b.clone(), againC = c.clone();
        assertEquals(count, NetworkChords.collect(s, againA, againB, againC));
        assertArrayEquals(a, againA); assertArrayEquals(b, againB); assertArrayEquals(c, againC);
    }

    @Test void realBossTripleChangesThreePairNetworkOnlyWhenAllThreeCellsAreBuilt() throws Exception {
        try (var reader = new InputStreamReader(getClass().getResourceAsStream(
                "/btk/bot/network-chord-boss-position.json"), StandardCharsets.UTF_8)) {
            var fixture = JsonParser.parseReader(reader).getAsJsonObject();
            assertEquals(904554568, fixture.get("gameId").getAsLong());
            assertEquals(26, fixture.get("turn").getAsInt());
            var s = new Gson().fromJson(fixture.get("state"), GameState.class);
            Rules.updateConnections(s, new Pathing(s.size));
            int[] before = new int[2]; Rules.incomePerTurn(s, before);
            assertArrayEquals(new int[]{132, 126}, before);
            int[] cells = {s.cell(20, 6), s.cell(21, 6), s.cell(22, 6)};
            assertEquals(3, Arrays.stream(cells).map(s::placeCost).sum());
            for (int mask = 1; mask < 8; mask++) {
                var work = new SearchState(s);
                for (int i = 0; i < cells.length; i++) if ((mask & (1 << i)) != 0) work.state.owner[cells[i]] = 1;
                work.refresh();
                int[] after = new int[2]; Rules.incomePerTurn(work.state, after);
                assertArrayEquals(mask == 7 ? new int[]{64, 186} : before, after);
            }
            int[] a = new int[8 * s.size], b = a.clone(), c = a.clone();
            int count = NetworkChords.collect(s, a, b, c), found = 0;
            for (int i = 0; i < count; i++) if (a[i] == cells[0] && b[i] == cells[1] && c[i] == cells[2]) found++;
            assertEquals(1, found, "retain same-component three-cell rerouting despite zero-value subsets");
        }
    }
}
