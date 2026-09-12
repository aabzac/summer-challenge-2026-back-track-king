package btk.core;

import java.util.ArrayList;
import java.util.List;

/** One command of the CG protocol. Immutable. */
public final class Action {

    public enum Type { PLACE, AUTOPLACE, DISRUPT, WAIT, MESSAGE }

    public final Type type;
    public final int x, y, x2, y2;
    /** DISRUPT target region id, or -1. */
    public final int regionId;
    public final String text;

    private Action(Type type, int x, int y, int x2, int y2, int regionId, String text) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.x2 = x2;
        this.y2 = y2;
        this.regionId = regionId;
        this.text = text;
    }

    public static Action place(int x, int y) {
        return new Action(Type.PLACE, x, y, -1, -1, -1, null);
    }

    public static Action autoplace(int fromX, int fromY, int toX, int toY) {
        return new Action(Type.AUTOPLACE, fromX, fromY, toX, toY, -1, null);
    }

    public static Action disrupt(int regionId) {
        return new Action(Type.DISRUPT, -1, -1, -1, -1, regionId, null);
    }

    /** DISRUPT addressed by a cell; the target is that cell's region (CG accepts both forms). */
    public static Action disruptAt(int x, int y) {
        return new Action(Type.DISRUPT, x, y, -1, -1, -1, null);
    }

    /** Region targeted by a DISRUPT, resolving the x/y form against the map; -1 if out of bounds. */
    public int disruptRegion(GameState s) {
        if (type != Type.DISRUPT) return -1;
        if (x >= 0) return s.inBounds(x, y) ? s.regionId[s.cell(x, y)] : -1;
        return regionId >= 0 && regionId < s.regionCount ? regionId : -1;
    }

    public static Action waitAction() {
        return WAIT;
    }

    public static Action message(String text) {
        return new Action(Type.MESSAGE, -1, -1, -1, -1, -1, text);
    }

    private static final Action WAIT = new Action(Type.WAIT, -1, -1, -1, -1, -1, null);

    public String toCommand() {
        switch (type) {
            case PLACE: return "PLACE_TRACKS " + x + " " + y;
            case AUTOPLACE: return "AUTOPLACE " + x + " " + y + " " + x2 + " " + y2;
            case DISRUPT: return x >= 0 ? "DISRUPT " + x + " " + y : "DISRUPT " + regionId;
            case MESSAGE: return "MESSAGE " + text;
            default: return "WAIT";
        }
    }

    @Override
    public String toString() {
        return toCommand();
    }

    /** Parses one command; throws IllegalArgumentException for malformed input. */
    public static Action parse(String cmd) {
        String s = cmd.trim();
        if (s.isEmpty()) throw new IllegalArgumentException("empty command");
        String[] t = s.split("\\s+");
        switch (t[0]) {
            case "PLACE_TRACKS":
                need(t, 3, s);
                return place(Integer.parseInt(t[1]), Integer.parseInt(t[2]));
            case "AUTOPLACE":
                need(t, 5, s);
                return autoplace(Integer.parseInt(t[1]), Integer.parseInt(t[2]),
                        Integer.parseInt(t[3]), Integer.parseInt(t[4]));
            case "DISRUPT":
                need(t, 2, s);
                if (t.length >= 3) return disruptAt(Integer.parseInt(t[1]), Integer.parseInt(t[2]));
                return disrupt(Integer.parseInt(t[1]));
            case "WAIT":
                return WAIT;
            case "MESSAGE":
                return message(s.length() > 8 ? s.substring(8) : "");
            default:
                throw new IllegalArgumentException("unknown command: " + s);
        }
    }

    /** Parses a full output line ("A;B;C"). */
    public static List<Action> parseLine(String line) {
        List<Action> out = new ArrayList<>();
        for (String part : line.split(";")) {
            if (!part.trim().isEmpty()) out.add(parse(part));
        }
        return out;
    }

    public static String format(List<Action> actions) {
        if (actions == null || actions.isEmpty()) return "WAIT";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < actions.size(); i++) {
            if (i > 0) sb.append(';');
            sb.append(actions.get(i).toCommand());
        }
        return sb.toString();
    }

    private static void need(String[] t, int n, String s) {
        if (t.length < n) throw new IllegalArgumentException("malformed command: " + s);
    }
}
