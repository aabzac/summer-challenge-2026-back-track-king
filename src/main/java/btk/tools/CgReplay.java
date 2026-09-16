package btk.tools;

import btk.core.GameState;
import btk.core.Protocol;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A CodinGame {@code gameResult/findByGameId} document as saved by {@link CgFetch}, with helpers
 * to rebuild the initial {@link GameState} from the viewer's global data and to extract both
 * players' stdout per turn and the viewer events (see docs/replay_format.md).
 */
public final class CgReplay {

    public long gameId;
    public String seed;
    public String[] nicknames = new String[2];
    public int[] agentIds = new int[2];
    public boolean[] arenaBosses = new boolean[2];
    public int[] finalScores = new int[2];
    public int[] ranks = new int[2];
    public JsonObject metadata;
    /** turnStdout.get(t)[p] = raw stdout line of player p at turn t (null if missing). */
    public final List<String[]> turnStdout = new ArrayList<>();
    /** Server-side failure for a player on a turn, such as a timeout; null when both succeeded. */
    public final List<String[]> turnFailures = new ArrayList<>();
    /** Viewer events per turn (keyframe graphics parsed). */
    public final List<List<Event>> turnEvents = new ArrayList<>();
    public String globalGraphics;

    private static final Pattern TIMEOUT = Pattern.compile(
            "\\$(\\d+) has not provided \\d+ lines? in time", Pattern.CASE_INSENSITIVE);

    /** One viewer event: {@code type|start|end|data}. */
    public static final class Event {
        public int type;
        public int start, end;
        public String data;
        public int[] ints;

        @Override
        public String toString() {
            return type + ":" + data;
        }
    }

    public static CgReplay load(Path file) throws IOException {
        return parse(JsonParser.parseString(Files.readString(file)).getAsJsonObject());
    }

    public static CgReplay parse(JsonObject root) {
        CgReplay r = new CgReplay();
        r.gameId = root.has("gameId") ? root.get("gameId").getAsLong() : 0;
        r.seed = root.has("refereeInput") && !root.get("refereeInput").isJsonNull()
                ? root.get("refereeInput").getAsString().trim() : "";
        r.metadata = root.has("metadata") && root.get("metadata").isJsonObject()
                ? root.getAsJsonObject("metadata") : new JsonObject();
        // Legacy fallback. Older CgFetch versions added this field, sometimes with P0/P1
        // reversed because they mistook battle finishing order for the in-game index.
        if (root.has("btkNicknames")) {
            JsonObject n = root.getAsJsonObject("btkNicknames");
            for (int p = 0; p < 2; p++) if (n.has(String.valueOf(p))) r.nicknames[p] = n.get(String.valueOf(p)).getAsString();
        }
        // gameResult.agents[].index is authoritative for the in-game player position.
        if (root.has("agents")) {
            for (JsonElement e : root.getAsJsonArray("agents")) {
                JsonObject a = e.getAsJsonObject();
                int idx = a.get("index").getAsInt();
                if (idx < 0 || idx > 1) continue;
                r.agentIds[idx] = a.has("agentId") ? a.get("agentId").getAsInt() : -1;
                if (r.agentIds[idx] == -2) r.nicknames[idx] = "Boss";
                if (a.has("codingamer") && a.get("codingamer").isJsonObject()) {
                    JsonObject c = a.getAsJsonObject("codingamer");
                    if (c.has("pseudo")) r.nicknames[idx] = c.get("pseudo").getAsString();
                }
                if (a.has("arenaboss") && a.get("arenaboss").isJsonObject()) {
                    r.arenaBosses[idx] = true;
                    JsonObject boss = a.getAsJsonObject("arenaboss");
                    r.nicknames[idx] = boss.has("nickname") && !boss.get("nickname").isJsonNull()
                            ? boss.get("nickname").getAsString() : "Boss";
                }
                if (a.has("rank")) r.ranks[idx] = a.get("rank").getAsInt();
            }
        }
        for (int p = 0; p < 2; p++) if (r.nicknames[p] == null) r.nicknames[p] = "P" + p;
        if (root.has("ranks")) {
            JsonArray ranks = root.getAsJsonArray("ranks");
            for (int i = 0; i < Math.min(2, ranks.size()); i++) r.ranks[i] = ranks.get(i).getAsInt();
        }
        JsonArray frames = root.getAsJsonArray("frames");
        // frame 0 = init
        JsonObject f0 = frames.get(0).getAsJsonObject();
        JsonObject view0 = viewJson(f0.get("view").getAsString());
        if (view0 != null && view0.has("global")) {
            r.globalGraphics = view0.getAsJsonObject("global").get("graphics").getAsString();
        }
        String[] current = null;
        for (int i = 1; i < frames.size(); i++) {
            JsonObject f = frames.get(i).getAsJsonObject();
            int agent = f.has("agentId") ? f.get("agentId").getAsInt() : -1;
            String stdout = f.has("stdout") && !f.get("stdout").isJsonNull() ? f.get("stdout").getAsString() : null;
            boolean keyframe = f.has("keyframe") && f.get("keyframe").getAsBoolean();
            if (current == null) current = new String[2];
            if (agent >= 0 && agent < 2 && stdout != null) current[agent] = stdout.trim();
            if (keyframe) {
                r.turnStdout.add(current);
                current = null;
                String[] failures = new String[2];
                if (f.has("summary") && !f.get("summary").isJsonNull()) {
                    Matcher timeout = TIMEOUT.matcher(f.get("summary").getAsString());
                    while (timeout.find()) {
                        int player = Integer.parseInt(timeout.group(1));
                        if (player >= 0 && player < failures.length) failures[player] = timeout.group();
                    }
                }
                r.turnFailures.add(failures);
                List<Event> events = new ArrayList<>();
                JsonObject v = viewJson(f.get("view").getAsString());
                if (v != null && v.has("graphics")) parseEvents(v.get("graphics").getAsString(), events);
                r.turnEvents.add(events);
                if (v != null && v.has("endScreen")) {
                    JsonArray es = v.getAsJsonArray("endScreen");
                    JsonArray sc = es.get(0).getAsJsonArray();
                    r.finalScores[0] = sc.get(0).getAsInt();
                    r.finalScores[1] = sc.get(1).getAsInt();
                }
            }
        }
        if (current != null) {
            r.turnStdout.add(current);
            r.turnFailures.add(new String[2]);
        }
        return r;
    }

    public String failure(int turn, int player) {
        return turn >= 0 && turn < turnFailures.size() && player >= 0 && player < 2
                ? turnFailures.get(turn)[player] : null;
    }

    /** View strings look like {@code " 12\n{json}"}; the JSON part may be missing. */
    static JsonObject viewJson(String view) {
        if (view == null) return null;
        int nl = view.indexOf('\n');
        String rest = nl < 0 ? "" : view.substring(nl + 1).trim();
        if (rest.isEmpty() || rest.charAt(0) != '{') return null;
        return JsonParser.parseString(rest).getAsJsonObject();
    }

    /** {@code count|type|start|end|data|type|start|end|data...} (data may be empty). */
    static void parseEvents(String graphics, List<Event> out) {
        String[] t = graphics.split("\\|", -1);
        if (t.length < 1) return;
        int count;
        try {
            count = Integer.parseInt(t[0]);
        } catch (NumberFormatException e) {
            return;
        }
        int i = 1;
        for (int k = 0; k < count && i + 3 < t.length; k++, i += 4) {
            Event ev = new Event();
            ev.type = Integer.parseInt(t[i]);
            ev.start = Integer.parseInt(t[i + 1]);
            ev.end = Integer.parseInt(t[i + 2]);
            ev.data = t[i + 3];
            ev.ints = toInts(ev.data);
            out.add(ev);
        }
    }

    private static int[] toInts(String data) {
        if (data.isEmpty()) return new int[0];
        String[] p = data.trim().split("\\s+");
        int[] out = new int[p.length];
        for (int i = 0; i < p.length; i++) {
            try {
                out[i] = Integer.parseInt(p[i]);
            } catch (NumberFormatException e) {
                return new int[0];
            }
        }
        return out;
    }

    /** Rebuilds the initial state from the viewer's global graphics string. */
    public GameState toState() {
        if (globalGraphics == null) throw new IllegalStateException("no global graphics in replay " + gameId);
        String[] t = globalGraphics.split("\\|", -1);
        int w = Integer.parseInt(t[0]);
        int h = Integer.parseInt(t[1]);
        // t[2] is a constant (3) of unknown meaning; cells follow
        int size = w * h;
        int[] region = new int[size];
        int[] terrain = new int[size];
        int i = 3;
        for (int c = 0; c < size; c++, i++) {
            String[] rt = t[i].trim().split("\\s+");
            region[c] = Integer.parseInt(rt[0]);
            terrain[c] = Integer.parseInt(rt[1]);
        }
        int townCount = Integer.parseInt(t[i++]);
        int[] tx = new int[townCount], ty = new int[townCount];
        int[][] desired = new int[townCount][];
        for (int k = 0; k < townCount; k++, i++) {
            String[] tt = t[i].trim().split("\\s+");
            int id = Integer.parseInt(tt[0]);
            tx[id] = Integer.parseInt(tt[1]);
            ty[id] = Integer.parseInt(tt[2]);
            desired[id] = Protocol.parseIdList(tt.length > 3 ? tt[3] : "x");
        }
        return new GameState(w, h, region, terrain, tx, ty, desired);
    }

    public int turns() {
        return turnStdout.size();
    }

    /** True for an IDE TestSession game against the built-in boss agent. */
    public boolean isBossGame() {
        return arenaBosses[0] || arenaBosses[1] || agentIds[0] == -2 || agentIds[1] == -2;
    }

    /** Signed 64-bit map seed from the referee's Java properties; absent in some replay exports. */
    public OptionalLong mapSeed() {
        if (seed == null || seed.isBlank()) return OptionalLong.empty();
        Properties input = new Properties();
        try {
            input.load(new StringReader(seed));
        } catch (IOException e) {
            throw new IllegalStateException("cannot read refereeInput for replay " + gameId, e);
        }
        String value = input.getProperty("seed");
        if (value == null) return OptionalLong.empty();
        try {
            return OptionalLong.of(Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid refereeInput seed for replay " + gameId + ": " + value, e);
        }
    }

    /** Loads every *.json replay in a directory (sorted by name). */
    public static List<Path> listReplays(Path dir) throws IOException {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        try (var st = Files.list(dir)) {
            st.filter(p -> p.toString().endsWith(".json")).sorted().forEach(out::add);
        }
        return out;
    }

    static final Gson GSON = new Gson();
}
