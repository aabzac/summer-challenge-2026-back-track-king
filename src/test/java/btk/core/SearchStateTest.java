package btk.core;

import btk.referee.MapGenerator;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class SearchStateTest {
    @Test void simultaneousProjectionMatchesResolveOnGeneratedTrajectories() {
        Random random = new Random(8463);
        for (int seed = 301; seed < 307; seed++) {
            GameState s = MapGenerator.generate(seed);
            Pathing pathing = new Pathing(s.size);
            SearchState search = new SearchState(s);
            for (int turn = 0; turn < 45; turn++) {
                @SuppressWarnings("unchecked") List<Action>[] actions = new List[]{new ArrayList<>(), new ArrayList<>()};
                int[][] cells = new int[2][3]; int[] counts = new int[2], targets = {-1,-1};
                for (int player = 0; player < 2; player++) {
                    int paint = 3, start = random.nextInt(s.size);
                    for (int i = 0; i < s.size && paint > 0; i++) {
                        int c = (start + i) % s.size;
                        if (!s.canBuild(c) || s.placeCost(c) > paint) continue;
                        cells[player][counts[player]++] = c; paint -= s.placeCost(c);
                        actions[player].add(Action.place(s.x(c), s.y(c)));
                    }
                    start = random.nextInt(s.regionCount);
                    for (int i = 0; i < s.regionCount; i++) {
                        int r = (start + i) % s.regionCount;
                        if (s.regionInked[r] || s.regionHasTown(r)) continue;
                        targets[player] = r; actions[player].add(Action.disrupt(r)); break;
                    }
                    if (actions[player].isEmpty()) actions[player].add(Action.waitAction());
                }
                search.set(s);
                boolean ended = search.resolve(cells[0],counts[0],targets[0],cells[1],counts[1],targets[1]);
                Rules.resolve(s,actions[0],actions[1],pathing);
                assertBoard(s,search.state);
                assertEquals(Rules.gameOver(s,pathing),ended);
                if (ended) break;
            }
        }
    }

    @Test void collisionsDoubleDisruptionAndInkBeforeIncomeUseCoreOrder() {
        GameState s = TestMaps.of(new String[]{"0...1"},new int[][]{{1},{}});
        TestMaps.track(s,0,1,0,3,0);
        Rules.disrupt(s,2); Rules.disrupt(s,2);
        SearchState search = new SearchState(s);
        int[] cells = {2};
        search.resolve(cells,1,2,cells,1,2);
        Rules.resolve(s,List.of(Action.place(2,0),Action.disrupt(2)),
                List.of(Action.place(2,0),Action.disrupt(2)),new Pathing(s.size));
        assertBoard(s,search.state);
        assertTrue(search.state.inked[2]); assertEquals(-1,search.state.owner[2]);
        assertArrayEquals(new int[]{0,0},search.state.score);
    }

    @Test void storageSurvivesInactiveActiveInactiveCyclesAndCopyIsolation() {
        GameState empty = TestMaps.of(new String[]{"0.1","..."},new int[][]{{1},{}});
        SearchState search = new SearchState(empty);
        search.state.owner[1]=0; search.refresh(); int[] reused = search.state.pairPath[0];
        assertEquals(1,SearchState.connectionIncomeDifference(search.state,0,0));
        assertEquals(-1,SearchState.connectionIncomeDifference(search.state,0,1));
        GameState active = search.state.copy();
        for(int i=0;i<4;i++) {
            search.set(empty); search.refresh(); assertFalse(search.state.pairActive(0));
            assertEquals(0,SearchState.connectionIncomeDifference(search.state,0,0));
            search.set(active); search.refresh(); assertSame(reused,search.state.pairPath[0]);
            assertBoard(active,search.state);
        }
        assertEquals(-1,empty.owner[1]);
        search.set(search.state); assertTrue(search.state.pairActive(0));
    }

    @Test void refreshedReusablePathsPreserveDirectionalTiesAndNeutralIncome() {
        GameState s=TestMaps.of(new String[]{"..1","...","0.."},new int[][]{{1},{0}});
        for(int c=0;c<s.size;c++) if(!s.isTown(c)) s.owner[c]=c%3;
        Rules.updateConnections(s,new Pathing(s.size));
        var search=new SearchState(s); assertBoard(s,search.state);
        assertEquals(s.cell(0,1),search.state.pairPath[0][1]);
        int[] income=new int[2]; Rules.incomePerTurn(s,income);
        int diff=0; for(int pair=0;pair<s.pairCount;pair++) diff+=SearchState.connectionIncomeDifference(s,pair,0);
        assertEquals(income[0]-income[1],diff);
    }

    private static void assertBoard(GameState expected,GameState actual) {
        assertArrayEquals(expected.owner,actual.owner); assertArrayEquals(expected.inked,actual.inked);
        assertArrayEquals(expected.instability,actual.instability);
        assertArrayEquals(expected.regionInstability,actual.regionInstability);
        assertArrayEquals(expected.regionInked,actual.regionInked);
        assertArrayEquals(expected.score,actual.score); assertEquals(expected.turn,actual.turn);
        assertArrayEquals(expected.pairPathLen,actual.pairPathLen);
        for(int pair=0;pair<expected.pairCount;pair++) {
            assertEquals(expected.pairActive(pair),actual.pairActive(pair));
            if(expected.pairActive(pair)) assertArrayEquals(Arrays.copyOf(expected.pairPath[pair],expected.pairPathLen[pair]),
                    Arrays.copyOf(actual.pairPath[pair],actual.pairPathLen[pair]));
        }
    }
}
