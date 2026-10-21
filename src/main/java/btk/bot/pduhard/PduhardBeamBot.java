package btk.bot.pduhard;

import btk.bot.Bot;
import btk.bot.Params;
import btk.bot.Timer;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import btk.core.SearchState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reconstruction of the pre-network beam from Pduhard's 3rd-place post-mortem
 * (https://github.com/Pduhard/back-track-king-postmortem). Not the unpublished PPO run:
 * no weights, no self-play training, no distilled value network.
 *
 * <p>Dijkstra cheapest paths shortlist placements. Regions are shortlisted by the income
 * they would swing once inked, plus a smaller bonus for unfinished opponent tracks.
 * Each disrupt is crossed with the best placement and then the next placements that still
 * fit in three paint. Plans are scored by the income gap over the turns still left, plus
 * the banked score. A finished game adds +/-30. The opponent answers with one greedy plan
 * on the same state; the top plans are rescored one turn further while time remains.
 */
public final class PduhardBeamBot implements Bot {
    static final int CELL_KEEP = 8;
    static final int DISRUPT_KEEP = 4;
    static final int BEAM = 4;
    static final int MAX_PLANS = 18;
    static final int CHAIN_STARTS = 2;
    static final int DEPTH2_PAIRS = 8;
    static final double PROGRESS = 0.35;
    static final double TERMINAL = 30;
    static final double TRACK_WEIGHT = 0.2;
    static final double OWN_TRACK_PENALTY = 1.5;
    /** Unfinished-track tie, kept below one point of real income. */
    static final double TRACK_PLAN_BONUS = 0.25;

    private final Params params;
    private final Timer timer = new Timer();
    private Pathing pathing;
    private SearchState after, deep;
    private boolean[] townRegion;
    private int[] path, cellOrder, disruptOrder, nearPair, nearMan;
    private int[] ownTrack, oppTrack, ownPath, oppPath;
    private double[] cellScore, disruptValue, trackSwing;
    private final int[][] planCells = new int[MAX_PLANS][GameState.PAINT_PER_TURN];
    private final int[] planN = new int[MAX_PLANS];
    private final int[] planDisrupt = new int[MAX_PLANS];
    private final double[] planValue = new double[MAX_PLANS];
    private final double[] planTie = new double[MAX_PLANS];
    private final double[] planTrack = new double[MAX_PLANS];
    private final int[] planDepth = new int[MAX_PLANS];
    private final int[] scoreOrder = new int[MAX_PLANS];
    private final int[] beam = new int[BEAM];
    private final int[] packBuf = new int[GameState.PAINT_PER_TURN];
    private final int[] greedyCells = new int[GameState.PAINT_PER_TURN];
    private final int[] rootOpp = new int[GameState.PAINT_PER_TURN];
    private final int[] me2 = new int[GameState.PAINT_PER_TURN];
    private final int[] income = new int[2];
    private int cellCount, disruptCount, greedyN, greedyDisrupt, rootOppN, rootOppDisrupt;
    private int planCount;

    public int plansGenerated, plansScored, depthReached;
    public int chosenDisrupt = -1, chosenCount;
    public final int[] chosenCells = new int[GameState.PAINT_PER_TURN];

    public PduhardBeamBot(Params params) {
        this.params = params == null ? new Params() : params.copy();
    }

    public String name() { return "pduhard-beam-v1"; }

    public void init(GameState s, int me) {
        pathing = new Pathing(s.size);
        after = new SearchState(s);
        deep = new SearchState(s);
        path = new int[s.size];
        cellScore = new double[s.size];
        cellOrder = new int[CELL_KEEP];
        disruptOrder = new int[DISRUPT_KEEP];
        nearPair = new int[DEPTH2_PAIRS];
        nearMan = new int[DEPTH2_PAIRS];
        townRegion = new boolean[s.regionCount];
        ownTrack = new int[s.regionCount];
        oppTrack = new int[s.regionCount];
        ownPath = new int[s.regionCount];
        oppPath = new int[s.regionCount];
        disruptValue = new double[s.regionCount];
        trackSwing = new double[s.regionCount];
        for (int t = 0; t < s.townCount; t++) townRegion[s.regionId[s.townCell[t]]] = true;
    }

    public List<Action> play(GameState s, int me, long budgetMs) {
        timer.start(Math.max(0, budgetMs));
        plansGenerated = plansScored = depthReached = chosenCount = 0;
        chosenDisrupt = -1;
        Arrays.fill(chosenCells, -1);
        List<Action> out = new ArrayList<>(4);
        if (budgetMs <= 0) {
            out.add(Action.waitAction());
            return out;
        }
        if (pathing == null) init(s, me);
        planCount = generatePlans(s, me);
        plansGenerated = planCount;
        greedy(s, 1 - me, -1);
        rootOppN = greedyN;
        rootOppDisrupt = greedyDisrupt;
        System.arraycopy(greedyCells, 0, rootOpp, 0, greedyN);
        orderByTie();
        int scored = 0;
        for (int k = 0; k < planCount; k++) {
            if (timer.up()) break;
            int i = scoreOrder[k];
            after.set(s);
            boolean over = resolve(after, me, planCells[i], planN[i], planDisrupt[i], rootOpp, rootOppN, rootOppDisrupt);
            planValue[i] = adjusted(i, evaluate(after.state, me, over));
            planDepth[i] = 1;
            scored++;
        }
        plansScored = scored;
        if (scored > 0 && timer.leftMs() >= 10) {
            int beamN = fillBeam(planCount);
            for (int b = 0; b < beamN && timer.leftMs() >= 3; b++) {
                int i = beam[b];
                if (planDepth[i] < 1) continue;
                after.set(s);
                boolean over = resolve(after, me, planCells[i], planN[i], planDisrupt[i], rootOpp, rootOppN, rootOppDisrupt);
                if (over) {
                    planDepth[i] = 2;
                    depthReached = 2;
                    continue;
                }
                greedy(after.state, me, DEPTH2_PAIRS);
                int nMe = greedyN, dMe = greedyDisrupt;
                System.arraycopy(greedyCells, 0, me2, 0, nMe);
                greedy(after.state, 1 - me, DEPTH2_PAIRS);
                deep.set(after.state);
                boolean over2 = resolve(deep, me, me2, nMe, dMe, greedyCells, greedyN, greedyDisrupt);
                planValue[i] = adjusted(i, evaluate(deep.state, me, over2));
                planDepth[i] = 2;
                depthReached = 2;
            }
        }
        int best = select(scored);
        emit(s, best, out);
        if (out.isEmpty()) out.add(Action.waitAction());
        return out;
    }

    private int generatePlans(GameState s, int player) {
        rankCells(s, player, -1);
        rankDisrupts(s, player);
        int count = addPlan(0, packBuf, 0, -1);
        count = addChains(s, count, -1);
        for (int i = 0; i < disruptCount && count < MAX_PLANS; i++) {
            count = addPlan(count, packBuf, 0, disruptOrder[i]);
            count = addChains(s, count, disruptOrder[i]);
        }
        return count;
    }

    private int addChains(GameState s, int count, int disrupt) {
        int starts = Math.min(CHAIN_STARTS, cellCount);
        for (int start = 0; start < starts && count < MAX_PLANS; start++) {
            int n = packFrom(s, disrupt, start, packBuf);
            if (n <= 0) continue;
            if (start == 0) {
                for (int len = 1; len <= n && count < MAX_PLANS; len++) count = addPlan(count, packBuf, len, disrupt);
            } else count = addPlan(count, packBuf, n, disrupt);
        }
        return count;
    }

    private int packFrom(GameState s, int disrupt, int start, int[] dest) {
        int paint = GameState.PAINT_PER_TURN, n = 0;
        boolean ink = disrupt >= 0 && s.regionInstability[disrupt] + 1 >= GameState.INK_THRESHOLD;
        for (int i = start; i < cellCount; i++) {
            int c = cellOrder[i];
            if (!s.canBuild(c)) continue;
            int cost = s.placeCost(c);
            if (cost > paint || (ink && s.regionId[c] == disrupt)) continue;
            dest[n++] = c;
            paint -= cost;
            if (paint == 0 || n == GameState.PAINT_PER_TURN) break;
        }
        return n;
    }

    private int addPlan(int count, int[] cells, int n, int disrupt) {
        for (int i = 0; i < count; i++) {
            if (planDisrupt[i] != disrupt || planN[i] != n) continue;
            boolean same = true;
            for (int j = 0; j < n; j++) if (planCells[i][j] != cells[j]) { same = false; break; }
            if (same) return count;
        }
        if (count >= MAX_PLANS) return count;
        for (int j = 0; j < n; j++) planCells[count][j] = cells[j];
        planN[count] = n;
        planDisrupt[count] = disrupt;
        double tie = 0;
        for (int j = 0; j < n; j++) tie += cellScore[cells[j]];
        planTie[count] = tie;
        planTrack[count] = disrupt >= 0 ? Math.max(0, trackSwing[disrupt]) * TRACK_PLAN_BONUS : 0;
        planValue[count] = Double.NEGATIVE_INFINITY;
        planDepth[count] = 0;
        return count + 1;
    }

    private void rankCells(GameState s, int player, int pairLimit) {
        Arrays.fill(cellScore, 0);
        int turns = Math.max(0, s.turnsLeft());
        int pairs = pairLimit < 0 ? s.pairCount : nearbyInactive(s, pairLimit);
        for (int k = 0; k < pairs && !timer.up(); k++) {
            int p = pairLimit < 0 ? k : nearPair[k];
            if (pairLimit < 0 && s.pairPath[p] != null) continue;
            int n = pathing.cheapestPath(s, s.townCell[s.pairFrom[p]], s.townCell[s.pairTo[p]], path);
            if (n <= 0) continue;
            int own = 0, opp = 0, empty = 0, emptyPaint = 0;
            for (int i = 0; i < n; i++) {
                int c = path[i];
                if (s.isTown(c)) continue;
                int o = s.owner[c];
                if (o == player) own++;
                else if (o == 1 - player) opp++;
                else if (s.canBuild(c)) { empty++; emptyPaint += s.placeCost(c); }
            }
            if (empty == 0) continue;
            int finish = (emptyPaint + GameState.PAINT_PER_TURN - 1) / GameState.PAINT_PER_TURN;
            int future = own + empty - opp;
            if (future <= 0 || finish >= turns) continue;
            double share = PROGRESS * future * (turns - finish) / empty;
            for (int i = 0; i < n; i++) {
                int c = path[i];
                if (!s.canBuild(c)) continue;
                int region = s.regionId[c];
                double frag = !townRegion[region] && s.regionInstability[region] >= 2 ? 0.25 : 1;
                cellScore[c] += share * frag / s.placeCost(c);
            }
        }
        double tieSign = player == 0 ? -1e-9 : 1e-9;
        int tieSeed = Math.floorMod(params.SEED, 5);
        cellCount = 0;
        for (int c = 0; c < s.size; c++) {
            if (cellScore[c] <= 0) continue;
            cellScore[c] += tieSign * (c + 1 + tieSeed);
            int pos = cellCount < CELL_KEEP ? cellCount : CELL_KEEP - 1;
            if (cellCount == CELL_KEEP && cellScore[c] <= cellScore[cellOrder[pos]]) continue;
            if (cellCount < CELL_KEEP) cellCount++;
            while (pos > 0 && cellScore[c] > cellScore[cellOrder[pos - 1]]) {
                cellOrder[pos] = cellOrder[pos - 1];
                pos--;
            }
            cellOrder[pos] = c;
        }
    }

    private int nearbyInactive(GameState s, int cap) {
        int n = 0;
        for (int p = 0; p < s.pairCount; p++) {
            if (s.pairPath[p] != null) continue;
            int man = Math.abs(s.townX[s.pairFrom[p]] - s.townX[s.pairTo[p]])
                    + Math.abs(s.townY[s.pairFrom[p]] - s.townY[s.pairTo[p]]);
            int pos = n < cap ? n : cap - 1;
            if (n == cap && man >= nearMan[pos]) continue;
            if (n < cap) n++;
            while (pos > 0 && man < nearMan[pos - 1]) {
                nearPair[pos] = nearPair[pos - 1];
                nearMan[pos] = nearMan[pos - 1];
                pos--;
            }
            nearPair[pos] = p;
            nearMan[pos] = man;
        }
        return n;
    }

    private void rankDisrupts(GameState s, int player) {
        exposure(s, player);
        disruptCount = 0;
        int turns = Math.max(0, s.turnsLeft());
        for (int r = 0; r < s.regionCount; r++) {
            trackSwing[r] = TRACK_WEIGHT * (oppTrack[r] - OWN_TRACK_PENALTY * ownTrack[r]);
            disruptValue[r] = 0;
            if (s.regionInked[r] || townRegion[r]) continue;
            int hits = GameState.INK_THRESHOLD - s.regionInstability[r];
            if (hits <= 0 || hits > turns) continue;
            int when = turns - hits + 1;
            if (when <= 0) continue;
            double score = (oppPath[r] - ownPath[r]) * when + Math.max(0, trackSwing[r]) * Math.min(when, 8);
            disruptValue[r] = score;
            if (score <= 0) continue;
            int pos = disruptCount < DISRUPT_KEEP ? disruptCount : DISRUPT_KEEP - 1;
            if (disruptCount == DISRUPT_KEEP && score <= disruptValue[disruptOrder[pos]]) continue;
            if (disruptCount < DISRUPT_KEEP) disruptCount++;
            while (pos > 0 && score > disruptValue[disruptOrder[pos - 1]]) {
                disruptOrder[pos] = disruptOrder[pos - 1];
                pos--;
            }
            disruptOrder[pos] = r;
        }
    }

    private void exposure(GameState s, int me) {
        Arrays.fill(ownTrack, 0);
        Arrays.fill(oppTrack, 0);
        Arrays.fill(ownPath, 0);
        Arrays.fill(oppPath, 0);
        int foe = 1 - me;
        for (int c = 0; c < s.size; c++) {
            int o = s.owner[c];
            if (o == me) ownTrack[s.regionId[c]]++;
            else if (o == foe) oppTrack[s.regionId[c]]++;
        }
        for (int p = 0; p < s.pairCount; p++) {
            int[] cells = s.pairPath[p];
            if (cells == null) continue;
            int n = s.pairPathLen[p];
            for (int i = 0; i < n; i++) {
                int o = s.owner[cells[i]];
                int region = s.regionId[cells[i]];
                if (o == me) ownPath[region]++;
                else if (o == foe) oppPath[region]++;
            }
        }
    }

    private void greedy(GameState s, int player, int pairLimit) {
        rankDisrupts(s, player);
        greedyDisrupt = -1;
        double best = 0;
        for (int i = 0; i < disruptCount; i++) {
            int r = disruptOrder[i];
            if (disruptValue[r] > best) { best = disruptValue[r]; greedyDisrupt = r; }
        }
        rankCells(s, player, pairLimit);
        greedyN = cellCount == 0 ? 0 : packFrom(s, greedyDisrupt, 0, greedyCells);
    }

    private void orderByTie() {
        for (int i = 0; i < planCount; i++) scoreOrder[i] = i;
        for (int i = 1; i < planCount; i++) {
            int id = scoreOrder[i];
            double key = planTrack[id] + planTie[id] * 1e-6;
            int j = i;
            while (j > 0 && planTrack[scoreOrder[j - 1]] + planTie[scoreOrder[j - 1]] * 1e-6 < key) {
                scoreOrder[j] = scoreOrder[j - 1];
                j--;
            }
            scoreOrder[j] = id;
        }
    }

    private int fillBeam(int n) {
        int m = 0;
        for (int i = 0; i < n; i++) {
            if (planDepth[i] < 1) continue;
            int pos = m < BEAM ? m : BEAM - 1;
            if (m == BEAM && planValue[i] <= planValue[beam[pos]]) continue;
            if (m < BEAM) m++;
            while (pos > 0 && planValue[i] > planValue[beam[pos - 1]]) {
                beam[pos] = beam[pos - 1];
                pos--;
            }
            beam[pos] = i;
        }
        return m;
    }

    private int select(int scored) {
        if (scored <= 0) {
            int best = 0;
            for (int i = 1; i < planCount; i++) {
                if (planTrack[i] + planTie[i] > planTrack[best] + planTie[best]) best = i;
            }
            return best;
        }
        int deepBest = bestAtDepth(2);
        return deepBest >= 0 ? deepBest : bestAtDepth(1);
    }

    private int bestAtDepth(int depth) {
        int best = -1;
        for (int i = 0; i < planCount; i++) {
            if (planDepth[i] != depth) continue;
            if (best < 0 || planValue[i] > planValue[best]) best = i;
        }
        return best;
    }

    private double adjusted(int plan, double raw) {
        return raw + planTrack[plan] + planTie[plan] * 1e-6 - clashes(plan) * 1e-3;
    }

    private int clashes(int plan) {
        int n = 0;
        for (int j = 0; j < planN[plan]; j++) {
            int c = planCells[plan][j];
            for (int k = 0; k < rootOppN; k++) if (rootOpp[k] == c) n++;
        }
        return n;
    }

    /** Path-income swing of the best region, as if that ink will be finished. */
    private double sabotage(GameState s, int me) {
        exposure(s, me);
        int turns = Math.max(0, s.turnsLeft());
        double best = 0;
        for (int r = 0; r < s.regionCount; r++) {
            if (s.regionInked[r] || townRegion[r]) continue;
            int hits = GameState.INK_THRESHOLD - s.regionInstability[r];
            if (hits <= 0 || hits > turns) continue;
            int when = turns - hits + 1;
            if (when <= 0) continue;
            double swing = oppPath[r] - ownPath[r];
            if (swing <= 0) continue;
            double v = swing * when;
            if (v > best) best = v;
        }
        return best;
    }

    private double evaluate(GameState s, int me, boolean over) {
        int banked = s.score[me] - s.score[1 - me];
        if (over) return banked + TERMINAL * Integer.compare(banked, 0);
        Rules.incomePerTurn(s, income);
        return banked + (income[me] - income[1 - me]) * (double) s.turnsLeft() + sabotage(s, me);
    }

    private boolean resolve(SearchState st, int me, int[] myC, int myN, int myD, int[] opC, int opN, int opD) {
        if (me == 0) return st.resolve(myC, myN, myD, opC, opN, opD);
        return st.resolve(opC, opN, opD, myC, myN, myD);
    }

    private void emit(GameState s, int plan, List<Action> out) {
        chosenDisrupt = planDisrupt[plan];
        int paint = GameState.PAINT_PER_TURN;
        for (int i = 0; i < planN[plan] && chosenCount < chosenCells.length; i++) {
            int c = planCells[plan][i];
            if (!s.canBuild(c)) continue;
            int cost = s.placeCost(c);
            if (cost > paint) continue;
            paint -= cost;
            chosenCells[chosenCount++] = c;
            out.add(Action.place(s.x(c), s.y(c)));
        }
        int d = planDisrupt[plan];
        if (d >= 0 && d < s.regionCount && !s.regionInked[d] && !townRegion[d]) out.add(Action.disrupt(d));
        else chosenDisrupt = -1;
    }
}
