package btk.bot.v00;

import btk.bot.Bot;
import btk.core.Action;
import btk.core.GameState;

import java.util.List;

/** v00: AUTOPLACE towards the first inactive desired connection. Wood 2 exit bot. */
public final class AutoplaceBot implements Bot {

    @Override
    public String name() {
        return "v00";
    }

    @Override
    public List<Action> play(GameState s, int myId, long budgetMs) {
        for (int p = 0; p < s.pairCount; p++) {
            if (s.pairActive(p)) continue;
            int a = s.pairFrom[p], b = s.pairTo[p];
            return List.of(Action.autoplace(s.townX[a], s.townY[a], s.townX[b], s.townY[b]));
        }
        return List.of(Action.waitAction());
    }
}
