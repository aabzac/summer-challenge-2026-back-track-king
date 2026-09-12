package btk.tools;

import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Rule-inference helper: for every ink event (viewer type 5) in downloaded replays, print how many
 * DISRUPTs each player issued on that region since its last ink, the region size, and how many
 * tracks (per owner) were inside. Also applies ink events to our state (removing tracks) and
 * reports whether placements keep matching afterwards (i.e. whether inked cells block building).
 *
 * <p>Args: replayDir
 */
public final class CgInkStudy {

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args.length > 0 && !args[0].isEmpty() ? args[0] : "replays/cg");
        Map<String, Integer> histogram = new TreeMap<>();
        int inkEvents = 0, placementMismatchAfterInk = 0, placementsOnInkedCell = 0, gamesChecked = 0;
        Map<Integer, Integer> turnsHist = new TreeMap<>();
        List<String> samples = new ArrayList<>();
        for (Path f : CgReplay.listReplays(dir)) {
            CgReplay r = CgReplay.load(f);
            gamesChecked++;
            turnsHist.merge(r.turns(), 1, Integer::sum);
            GameState s = r.toState();
            s.maxTurns = 1000;
            Pathing pathing = new Pathing(s.size);
            int[] regionSize = new int[s.regionCount];
            for (int c = 0; c < s.size; c++) regionSize[s.regionId[c]]++;
            int[][] disrupts = new int[2][s.regionCount];
            boolean[] cellInked = new boolean[s.size];
            int[] inkedAtTurn = new int[s.regionCount];
            java.util.Arrays.fill(inkedAtTurn, -1);
            for (int t = 0; t < r.turns(); t++) {
                String[] lines = r.turnStdout.get(t);
                @SuppressWarnings("unchecked")
                List<Action>[] acts = new List[2];
                for (int p = 0; p < 2; p++) {
                    try {
                        acts[p] = lines[p] == null ? List.of(Action.waitAction()) : Action.parseLine(lines[p]);
                    } catch (RuntimeException e) {
                        acts[p] = List.of(Action.waitAction());
                    }
                    for (Action a : acts[p]) {
                        if (a.type != Action.Type.DISRUPT) continue;
                        int region = a.disruptRegion(s);
                        if (a.x >= 0) disruptXY++; else disruptId++;
                        if (region >= 0) disrupts[p][region]++;
                    }
                }
                int[] ownerBefore = s.owner.clone();
                Rules.TurnResult tr = Rules.resolve(s, acts[0], acts[1], pathing);
                // compare placements with CG
                java.util.Set<Integer> cg = new java.util.HashSet<>();
                List<CgReplay.Event> events = t < r.turnEvents.size() ? r.turnEvents.get(t) : List.of();
                for (CgReplay.Event e : events) if (e.type == 1 && e.ints.length >= 3) cg.add(s.cell(e.ints[1], e.ints[2]));
                java.util.Set<Integer> ours = new java.util.HashSet<>();
                for (int c = 0; c < s.size; c++) if (s.owner[c] != ownerBefore[c]) ours.add(c);
                for (int c : ours) if (cellInked[c]) placementsOnPreviouslyInked++;
                if (!cg.equals(ours)) {
                    placementMismatchAfterInk++;
                    for (int c : ours) if (cellInked[c] && !cg.contains(c)) placementsOnInkedCell++;
                    if (samples.size() < 8) samples.add(r.gameId + " t" + t + " CG=" + cg + " ours=" + ours
                            + " (inked among ours: " + ours.stream().filter(c -> cellInked[c]).count() + ")");
                }
                // ink events
                for (CgReplay.Event e : events) {
                    if (e.type != 5 || e.ints.length < 1) continue;
                    int region = e.ints[0];
                    inkEvents++;
                    int tr0 = 0, tr1 = 0, trn = 0;
                    for (int c = 0; c < s.size; c++) {
                        if (s.regionId[c] != region) continue;
                        if (s.owner[c] == 0) tr0++;
                        else if (s.owner[c] == 1) tr1++;
                        else if (s.owner[c] == 2) trn++;
                    }
                    String key = String.format("total=%d", disrupts[0][region] + disrupts[1][region]);
                    histogram.merge(key, 1, Integer::sum);
                    if (inkedAtTurn[region] >= 0) reInk.merge("gap=" + (t - inkedAtTurn[region]), 1, Integer::sum);
                    int listed = (e.ints.length - 1) / 2;
                    if (listed != tr0 + tr1 + trn) listedNotEqualTracks++;
                    if (inkEvents <= 40) {
                        System.out.printf("game %d t%d region %d size %d cellsListed %d | disrupts P0=%d P1=%d | tracks P0=%d P1=%d N=%d | prevInk t%d%n",
                                r.gameId, t, region, regionSize[region], (e.ints.length - 1) / 2,
                                disrupts[0][region], disrupts[1][region], tr0, tr1, trn, inkedAtTurn[region]);
                    }
                    // apply ink: remove tracks on listed cells, mark the whole region inked, reset disrupt counters
                    for (int i = 1; i + 1 < e.ints.length; i += 2) {
                        int c = s.cell(e.ints[i], e.ints[i + 1]);
                        s.owner[c] = GameState.NO_TRACK;
                    }
                    for (int c = 0; c < s.size; c++) if (s.regionId[c] == region) cellInked[c] = true;
                    disrupts[0][region] = 0;
                    disrupts[1][region] = 0;
                    inkedAtTurn[region] = t;
                }
                Rules.updateConnections(s, pathing);
            }
            // end-of-game snapshot
            int active = 0;
            for (int p = 0; p < s.pairCount; p++) if (s.pairActive(p)) active++;
            int free = 0;
            for (int c = 0; c < s.size; c++) if (!s.passable(c) && !cellInked[c]) free++;
            int townsInked = 0;
            boolean[] regionInked = new boolean[s.regionCount];
            for (int c = 0; c < s.size; c++) if (cellInked[c]) regionInked[s.regionId[c]] = true;
            int regionsInked = 0;
            for (boolean b : regionInked) if (b) regionsInked++;
            for (int t = 0; t < s.townCount; t++) if (regionInked[s.regionId[s.townCell[t]]]) townsInked++;
            int pairsAlive = 0;
            for (int p = 0; p < s.pairCount; p++) {
                if (!regionInked[s.regionId[s.townCell[s.pairFrom[p]]]] && !regionInked[s.regionId[s.townCell[s.pairTo[p]]]]) pairsAlive++;
            }
            endStats.add(String.format("game %d: turns=%d active %d/%d pairsWithBothTownsUninked=%d townsInked=%d/%d regionsInked=%d/%d freeCells=%d CG=%d-%d",
                    r.gameId, r.turns(), active, s.pairCount, pairsAlive, townsInked, s.townCount, regionsInked, s.regionCount, free,
                    r.finalScores[0], r.finalScores[1]));
        }
        System.out.println();
        System.out.println("games: " + gamesChecked + "  turns histogram: " + turnsHist);
        System.out.println("ink events: " + inkEvents);
        System.out.println("disrupt counts at ink time: " + histogram);
        System.out.println("re-ink gaps (turns since previous ink of same region): " + reInk);
        System.out.println("ink events where listed cells != tracks in region: " + listedNotEqualTracks);
        System.out.println("DISRUPT syntax: id=" + disruptId + " xy=" + disruptXY);
        System.out.println("placements on previously inked cells: " + placementsOnPreviouslyInked);
        System.out.println("turns with placement mismatch (after applying ink removals): " + placementMismatchAfterInk
                + ", of which our placements on inked cells: " + placementsOnInkedCell);
        for (String sample : samples) System.out.println("  " + sample);
        System.out.println();
        System.out.println("end of game (first 25):");
        for (String e : endStats.subList(0, Math.min(25, endStats.size()))) System.out.println("  " + e);
    }

    private static int disruptId, disruptXY, placementsOnPreviouslyInked, listedNotEqualTracks;
    private static final Map<String, Integer> reInk = new TreeMap<>();
    private static final List<String> endStats = new ArrayList<>();

    private static String abbrev(String s) {
        if (s == null) return "null";
        return s.length() > 60 ? s.substring(0, 60) + "..." : s;
    }
}
