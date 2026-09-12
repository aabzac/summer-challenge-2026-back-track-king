package btk.referee;

import btk.core.Action;
import btk.core.GameState;
import btk.core.Rules;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Replay document consumed by {@code visualizer/index.html}. Same shape is produced for local
 * games and for CG games run through the local engine ({@code btk.tools.CgValidate}).
 *
 * <pre>
 * { meta:{source,seed,bots[],gameId?,winner,score[],errors[]},
 *   map:{width,height,region[],terrain[],towns:[{id,x,y,desired[]}],pairs:[[from,to],...]},
 *   turns:[{actions:[s0,s1], placed:[[cell,owner],...], score:[a,b], gained:[a,b],
 *           paths:[[pairIdx,[cells...]],...], errors:[e0,e1], msg:[m0,m1]}] }
 * </pre>
 */
public final class Replay {

    public static final class Meta {
        public String source = "local";
        public long seed;
        public String[] bots;
        public String gameId;
        public int winner;
        public int[] score;
        public String[] errors;
        public int maxTurns;
    }

    public static final class MapDoc {
        public int width, height;
        public int[] region, terrain;
        public List<int[]> towns = new ArrayList<>();
        public List<List<Integer>> desired = new ArrayList<>();
        public List<int[]> pairs = new ArrayList<>();
    }

    public static final class Turn {
        public String[] actions = new String[2];
        public List<int[]> placed = new ArrayList<>();
        public int[] score = new int[2];
        public int[] gained = new int[2];
        public List<Object[]> paths = new ArrayList<>();
        public String[] errors = new String[2];
        public String[] msg = new String[2];
    }

    public Meta meta = new Meta();
    public MapDoc map = new MapDoc();
    public List<Turn> turns = new ArrayList<>();

    private transient int[] prevOwner;

    public Replay(GameState s, long seed, String bot0, String bot1) {
        meta.seed = seed;
        meta.bots = new String[]{bot0, bot1};
        meta.maxTurns = s.maxTurns;
        map.width = s.width;
        map.height = s.height;
        map.region = s.regionId;
        map.terrain = s.terrain;
        for (int t = 0; t < s.townCount; t++) {
            map.towns.add(new int[]{t, s.townX[t], s.townY[t]});
            List<Integer> d = new ArrayList<>();
            for (int x : s.desired[t]) d.add(x);
            map.desired.add(d);
        }
        for (int p = 0; p < s.pairCount; p++) map.pairs.add(new int[]{s.pairFrom[p], s.pairTo[p]});
        prevOwner = s.owner.clone();
    }

    /** Records the state right after {@code Rules.resolve}. */
    public void addTurn(GameState s, List<Action>[] acts, Rules.TurnResult tr) {
        Turn t = new Turn();
        for (int p = 0; p < 2; p++) {
            t.actions[p] = acts[p] == null ? null : Action.format(acts[p]);
            t.errors[p] = tr.moves[p].error;
            t.msg[p] = tr.moves[p].message;
        }
        for (int c = 0; c < s.size; c++) {
            if (s.owner[c] != prevOwner[c]) {
                t.placed.add(new int[]{c, s.owner[c]});
                prevOwner[c] = s.owner[c];
            }
        }
        t.score[0] = s.score[0];
        t.score[1] = s.score[1];
        t.gained[0] = tr.gained[0];
        t.gained[1] = tr.gained[1];
        for (int p = 0; p < s.pairCount; p++) {
            if (s.pairPath[p] == null) continue;
            int[] cells = new int[s.pairPathLen[p]];
            System.arraycopy(s.pairPath[p], 0, cells, 0, cells.length);
            t.paths.add(new Object[]{p, cells});
        }
        turns.add(t);
    }

    public void finish(Referee.Result r) {
        meta.winner = r.winner;
        meta.score = r.score.clone();
        meta.errors = r.error.clone();
    }

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public String toJson() {
        return GSON.toJson(this);
    }

    public Path write(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, toJson());
        return file;
    }
}
