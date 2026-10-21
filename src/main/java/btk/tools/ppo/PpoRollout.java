package btk.tools.ppo;

import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** One self-play game on {@code btk.core}. Both seats use the same linear policy. */
public final class PpoRollout {
    public static final double GAMMA = 0.99;
    public static final double TERMINAL = 30;

    public static final class Comp {
        public final float[] features;
        public final int n, width, chosen;
        public final boolean disrupt;

        Comp(float[] features, int n, int width, int chosen, boolean disrupt) {
            this.features = features;
            this.n = n;
            this.width = width;
            this.chosen = chosen;
            this.disrupt = disrupt;
        }
    }

    public static final class Step {
        public final int player;
        public final float[] state;
        public final List<Comp> comps = new ArrayList<>(4);
        public final int[] cells = new int[3];
        public int cellCount;
        public int disrupt = -1;
        public double oldLogp;
        public double oldValue;
        public double reward;
        public double advantage;
        public double returns;

        Step(int player, float[] state) {
            this.player = player;
            this.state = state;
        }
    }

    public static final class Game {
        public final List<Step> steps = new ArrayList<>(200);
        public final int[] score = new int[2];
        public int turns;
    }

    private final Pathing pathing;
    private final boolean[] townRegion;
    private final boolean[] used;
    private final double[] route;
    private final int[] ownTrack, oppTrack, ownPath, oppPath, disruptIds;
    private final float[] cellFeat, disruptFeat;
    private final int[] scratch;

    public PpoRollout(GameState map) {
        scratch = new int[map.size];
        pathing = new Pathing(map.size);
        townRegion = new boolean[map.regionCount];
        used = new boolean[map.size];
        route = new double[map.size];
        ownTrack = new int[map.regionCount];
        oppTrack = new int[map.regionCount];
        ownPath = new int[map.regionCount];
        oppPath = new int[map.regionCount];
        disruptIds = new int[map.regionCount + 1];
        cellFeat = new float[(map.size + 1) * PpoModel.CELL_F];
        disruptFeat = new float[(map.regionCount + 1) * PpoModel.DISRUPT_F];
        for (int t = 0; t < map.townCount; t++) townRegion[map.regionId[map.townCell[t]]] = true;
    }

    public Game play(GameState state, PpoModel model, Random rng) {
        Rules.updateConnections(state, pathing);
        Game game = new Game();
        while (!Rules.gameOver(state, pathing)) {
            Step a = decide(state, 0, model, rng);
            Step b = decide(state, 1, model, rng);
            Rules.TurnResult result = Rules.resolve(state, actions(state, a), actions(state, b), pathing);
            for (int p = 0; p < 2; p++) {
                if (result.moves[p].error != null || !result.moves[p].skipped.isEmpty()) {
                    throw new IllegalStateException("illegal action at turn " + (state.turn - 1) + " P" + p
                            + " " + result.moves[p].error + " " + result.moves[p].skipped);
                }
            }
            int gap = result.gained[0] - result.gained[1];
            a.reward = gap;
            b.reward = -gap;
            if (Rules.gameOver(state, pathing)) {
                int sign = Integer.compare(state.score[0], state.score[1]);
                a.reward += TERMINAL * sign;
                b.reward -= TERMINAL * sign;
            }
            game.steps.add(a);
            game.steps.add(b);
        }
        game.turns = state.turn;
        game.score[0] = state.score[0];
        game.score[1] = state.score[1];
        returns(game);
        return game;
    }

    public int[] versus(GameState state, PpoModel first, PpoModel second) {
        Rules.updateConnections(state, pathing);
        while (!Rules.gameOver(state, pathing)) {
            Step a = decide(state, 0, first, null);
            Step b = decide(state, 1, second, null);
            Rules.TurnResult result = Rules.resolve(state, actions(state, a), actions(state, b), pathing);
            for (int p = 0; p < 2; p++) {
                if (result.moves[p].error != null || !result.moves[p].skipped.isEmpty()) {
                    throw new IllegalStateException("illegal greedy action at turn " + (state.turn - 1));
                }
            }
        }
        return new int[]{state.score[0], state.score[1]};
    }

    public List<Action> greedy(GameState state, int me, PpoModel model) {
        return actions(state, decide(state, me, model, null));
    }

    private Step decide(GameState state, int me, PpoModel model, Random rng) {
        float[] valueFeat = valueFeatures(state, me);
        Step step = new Step(me, valueFeat);
        step.oldValue = model.value(valueFeat);
        expose(state, me);
        routes(state, me);
        int regions = disruptCandidates(state);
        int disrupt = model.sample(model.disrupt, disruptFeat, regions, PpoModel.DISRUPT_F, rng);
        step.disrupt = disruptIds[disrupt];
        step.oldLogp += model.logProb(model.disrupt, disruptFeat, regions, PpoModel.DISRUPT_F, disrupt);
        step.comps.add(new Comp(Arrays.copyOf(disruptFeat, regions * PpoModel.DISRUPT_F),
                regions, PpoModel.DISRUPT_F, disrupt, true));
        Arrays.fill(used, false);
        int paint = GameState.PAINT_PER_TURN;
        while (step.cellCount < 3) {
            int n = cellCandidates(state, me, paint);
            if (n == 0) break;
            int pick = model.sample(model.build, cellFeat, n, PpoModel.CELL_F, rng);
            step.oldLogp += model.logProb(model.build, cellFeat, n, PpoModel.CELL_F, pick);
            step.comps.add(new Comp(Arrays.copyOf(cellFeat, n * PpoModel.CELL_F),
                    n, PpoModel.CELL_F, pick, false));
            if (pick == n - 1) break;
            int cell = scratch[pick];
            step.cells[step.cellCount++] = cell;
            used[cell] = true;
            paint -= state.placeCost(cell);
        }
        return step;
    }

    private List<Action> actions(GameState state, Step step) {
        List<Action> actions = new ArrayList<>(4);
        for (int i = 0; i < step.cellCount; i++) {
            actions.add(Action.place(state.x(step.cells[i]), state.y(step.cells[i])));
        }
        if (step.disrupt >= 0) actions.add(Action.disrupt(step.disrupt));
        if (actions.isEmpty()) actions.add(Action.waitAction());
        return actions;
    }

    private float[] valueFeatures(GameState state, int me) {
        Rules.incomePerTurn(state, scratchIncome);
        int mine = 0, opp = 0;
        for (int c = 0; c < state.size; c++) {
            if (state.owner[c] == me) mine++;
            else if (state.owner[c] == 1 - me) opp++;
        }
        float[] f = new float[PpoModel.VALUE_F];
        f[0] = 1;
        f[1] = (scratchIncome[me] - scratchIncome[1 - me]) / 10f;
        f[2] = (state.score[me] - state.score[1 - me]) / 100f;
        f[3] = state.turn / (float) state.maxTurns;
        f[4] = mine / 50f;
        f[5] = opp / 50f;
        return f;
    }

    private final int[] scratchIncome = new int[2];

    private void expose(GameState state, int me) {
        Arrays.fill(ownTrack, 0);
        Arrays.fill(oppTrack, 0);
        Arrays.fill(ownPath, 0);
        Arrays.fill(oppPath, 0);
        int foe = 1 - me;
        for (int c = 0; c < state.size; c++) {
            int owner = state.owner[c];
            if (owner == me) ownTrack[state.regionId[c]]++;
            else if (owner == foe) oppTrack[state.regionId[c]]++;
        }
        for (int p = 0; p < state.pairCount; p++) {
            int[] path = state.pairPath[p];
            if (path == null) continue;
            int n = state.pairPathLen[p];
            for (int i = 0; i < n; i++) {
                int owner = state.owner[path[i]];
                int region = state.regionId[path[i]];
                if (owner == me) ownPath[region]++;
                else if (owner == foe) oppPath[region]++;
            }
        }
    }

    private void routes(GameState state, int me) {
        Arrays.fill(route, 0);
        int turns = Math.max(1, state.turnsLeft());
        int foe = 1 - me;
        for (int p = 0; p < state.pairCount; p++) {
            if (state.pairPath[p] != null) continue;
            int from = state.townCell[state.pairFrom[p]];
            int to = state.townCell[state.pairTo[p]];
            int n = pathing.cheapestPath(state, from, to, scratch);
            if (n <= 0) continue;
            int own = 0, opp = 0, empty = 0, emptyPaint = 0;
            for (int i = 0; i < n; i++) {
                int c = scratch[i];
                if (state.isTown(c)) continue;
                int owner = state.owner[c];
                if (owner == me) own++;
                else if (owner == foe) opp++;
                else if (state.canBuild(c)) {
                    empty++;
                    emptyPaint += state.placeCost(c);
                }
            }
            if (empty == 0) continue;
            int future = own + empty - opp;
            int finish = (emptyPaint + 2) / 3;
            if (future <= 0 || finish >= turns) continue;
            double share = future * (double) (turns - finish) / empty;
            for (int i = 0; i < n; i++) {
                int c = scratch[i];
                if (state.canBuild(c)) route[c] += share;
            }
        }
    }

    private int disruptCandidates(GameState state) {
        int n = 0;
        for (int r = 0; r < state.regionCount; r++) {
            if (state.regionInked[r] || townRegion[r]) continue;
            disruptIds[n] = r;
            putDisrupt(n, state, r, false);
            n++;
        }
        disruptIds[n] = -1;
        putDisrupt(n, state, -1, true);
        return n + 1;
    }

    private void putDisrupt(int index, GameState state, int region, boolean none) {
        int o = index * PpoModel.DISRUPT_F;
        disruptFeat[o] = 1;
        disruptFeat[o + 1] = none ? 0 : clamp((oppPath[region] - ownPath[region]) / 8.0);
        disruptFeat[o + 2] = none ? 0 : oppTrack[region] / 8f;
        disruptFeat[o + 3] = none ? 0 : ownTrack[region] / 8f;
        disruptFeat[o + 4] = none ? 0 : state.regionInstability[region] / 4f;
        disruptFeat[o + 5] = none ? 1 : 0;
    }

    private int cellCandidates(GameState state, int me, int paint) {
        int n = 0;
        for (int c = 0; c < state.size; c++) {
            if (used[c] || !state.canBuild(c) || state.placeCost(c) > paint) continue;
            scratch[n] = c;
            int o = n * PpoModel.CELL_F;
            int region = state.regionId[c];
            cellFeat[o] = 1;
            cellFeat[o + 1] = route[c] > 0 ? 1 : 0;
            cellFeat[o + 2] = (float) clamp(route[c] / 32.0);
            cellFeat[o + 3] = 1f / state.placeCost(c);
            cellFeat[o + 4] = touches(state, me, c) ? 1 : 0;
            cellFeat[o + 5] = state.regionInstability[region] / 4f;
            cellFeat[o + 6] = oppTrack[region] / 8f;
            cellFeat[o + 7] = ownTrack[region] / 8f;
            n++;
        }
        int o = n * PpoModel.CELL_F;
        cellFeat[o] = 1;
        cellFeat[o + 1] = cellFeat[o + 2] = cellFeat[o + 3] = 0;
        cellFeat[o + 4] = cellFeat[o + 5] = cellFeat[o + 6] = 0;
        cellFeat[o + 7] = -1;
        return n + 1;
    }

    private boolean touches(GameState state, int me, int cell) {
        int x = state.x(cell), y = state.y(cell);
        for (int d = 0; d < 4; d++) {
            int nx = x + GameState.DX[d], ny = y + GameState.DY[d];
            if (!state.inBounds(nx, ny)) continue;
            int n = state.cell(nx, ny);
            if (state.townAtCell[n] >= 0 || state.owner[n] == me) return true;
        }
        return false;
    }

    private static float clamp(double v) {
        return (float) Math.max(-2, Math.min(2, v));
    }

    public static void returns(Game game) {
        double[] next = new double[2];
        for (int i = game.steps.size() - 1; i >= 0; i--) {
            Step step = game.steps.get(i);
            next[step.player] = step.reward + GAMMA * next[step.player];
            step.returns = next[step.player];
            step.advantage = step.returns - step.oldValue;
        }
    }
}
