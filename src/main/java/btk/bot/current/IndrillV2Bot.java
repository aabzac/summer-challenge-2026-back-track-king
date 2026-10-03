package btk.bot.current;

import btk.bot.Bot;
import btk.bot.Params;
import btk.core.Action;
import btk.core.GameState;
import java.util.ArrayList;
import java.util.List;

/** V2 uses the same additive action advantages plus one learned board-value baseline. */
public final class IndrillV2Bot implements Bot {
    private final Params params;
    private final IndrillV2Net net;
    public IndrillFeatures features;
    /** These arrays are action advantages; complete Q includes stateValue once. */
    public float[] buildQ, inkQ;
    public float stateValue;
    public final int[] selectedCells = new int[3];
    public int selectedCount, selectedInk;

    public IndrillV2Bot(Params p) { this(p, new IndrillV2Net()); }
    public IndrillV2Bot(Params p, IndrillV2Net net) { params = p.copy(); this.net = net; }
    public String name() { return "indrill-q-v2"; }
    public void init(GameState s, int me) {
        features = new IndrillFeatures(s, params);
        buildQ = new float[s.size];
        inkQ = new float[s.regionCount];
        net.init(s);
    }
    public void decide(GameState s, int me) {
        features.prepare(s, me);
        net.score(s, features, buildQ, inkQ);
        stateValue = net.stateValue;
        selectedCount = IndrillV2Net.selectBuild(buildQ, features.buildCost, selectedCells);
        selectedInk = -1;
        float best = 0;
        if (params.USE_DISRUPT) for (int r = 0; r < s.regionCount; r++)
            if (features.legalInk[r] && inkQ[r] > best) { selectedInk = r; best = inkQ[r]; }
    }
    /** Full greedy action Q, including the learned baseline once even for WAIT. */
    public float selectedValue() {
        return net.actionValue(buildQ, inkQ, selectedCells, selectedCount, selectedInk);
    }
    public List<Action> play(GameState s, int me, long budgetMs) {
        if (budgetMs <= 0) return List.of(Action.waitAction());
        decide(s, me);
        return actions(s, selectedCells, selectedCount, selectedInk);
    }
    public static List<Action> actions(GameState s, int[] cells, int count, int ink) {
        List<Action> result = new ArrayList<>(4);
        for (int i = 0; i < count; i++) result.add(Action.place(s.x(cells[i]), s.y(cells[i])));
        if (ink >= 0) result.add(Action.disrupt(ink));
        if (result.isEmpty()) result.add(Action.waitAction());
        return result;
    }
}
