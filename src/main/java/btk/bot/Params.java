package btk.bot;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * All tunable weights and feature flags of the strategy in one place.
 *
 * <p>Defaults are what ships to CodinGame. Locally any public field can be overridden with a
 * query string, e.g. {@code "W_OPP=0.5&USE_STEAL=0"}; that is how ablations and parameter
 * sweeps are run from the arena without touching code. Frozen bot versions ignore fields they
 * do not know about.
 */
public class Params {

    // ---- greedy pair selection ----
    /** Weight of opponent-owned cells on a candidate path (they earn while we build). */
    public double W_OPP = 1.0;
    /** Weight of our already-owned cells on a candidate path. */
    public double W_OWN = 1.0;
    /** Penalty per paint point of build cost. */
    public double W_COST = 0.0;
    /** Skip candidates that cannot be finished before the game ends. */
    public boolean SKIP_UNFINISHABLE = true;

    // ---- v02 eval bot: building ----
    /** Consider "own route" candidates that avoid opponent tracks (ownership stealing). */
    public boolean USE_STEAL = true;
    /** Extra paint-cost per instability point for routing through unstable regions. */
    public double UNSTABLE_COST = 1.0;
    /** Value multiplier lost per instability point on the riskiest new cell (0..1 per 4 points). */
    public double W_RISK = 0.6;
    /** Weight of opponent income change in candidate evaluation. */
    public double W_OPP_INCOME = 1.0;

    // ---- v02 eval bot: disrupt ----
    public boolean USE_DISRUPT = true;
    /** Stop route/idle search this many ms before the turn deadline; 0 restores v04 scheduling. */
    public int DISRUPT_RESERVE_MS = 0;
    /** Weight of our own income lost when inking a region (vs opponent's loss = 1). */
    public double W_SELF_LOSS = 1.2;
    /** Value per opponent track destroyed that is not (yet) earning. */
    public double W_FUTURE_TRACK = 0.3;
    /** Value per own track destroyed that is not (yet) earning. */
    public double W_FUTURE_OWN = 0.4;
    /** Minimum value to bother disrupting (below: no DISRUPT). */
    public double DISRUPT_MIN = 0.01;

    // ---- v03 vulnerability eval ----
    /**
     * Weight of the worst-case ink: state value = income diff - W_VULN * max over regions of
     * (my income lost - W_THEIR_LOSS * their income lost) / disrupts needed. 0 disables.
     */
    public double W_VULN = 1.0;
    /** How much the opponent is assumed to care about hurting themselves when choosing an ink. */
    public double W_THEIR_LOSS = 1.0;
    /** Also generate backup routes for active pairs that avoid the most threatened region. */
    public boolean USE_BACKUP = true;
    /** Spend otherwise idle paint by extending our existing network through safe nearby cells. */
    public boolean USE_IDLE_EXTEND = true;

    // ---- v03 endgame / horizon ----
    /** Income is projected over at most this many turns (top Silver games close after ~50 turns). */
    public int HORIZON = 100;
    /** Consider whether an ink closes the map (no pair connectable) and end the game when ahead. */
    public boolean USE_CLOSE = true;

    // ---- v03 opponent model ----
    /** Predict the opponent's best route with our own algorithm and use it below. */
    public boolean USE_OPP_MODEL = false;
    /** Routing surcharge on cells the opponent is predicted to place this turn (collision -> neutral). */
    public double COLLISION_COST = 2.0;
    /** Weight of predicted opponent route cells (per cell) as future tracks in disrupt evaluation. */
    public double W_OPP_ROUTE = 0.3;
    /** When no region passes DISRUPT_MIN, still disrupt the region with the best track balance. */
    public boolean ALWAYS_DISRUPT = true;

    // ---- symmetry breaking / benches ----
    /** Relative multiplicative noise on candidate values (0 = deterministic). Breaks self-play mirrors. */
    public double NOISE = 0.1;
    /** Noise seed; the player id is mixed in so both sides differ. */
    public int SEED = 1;

    // ---- misc ----
    /** Emit a MESSAGE with debug info each turn. */
    public boolean MESSAGE = false;
    /** Log per-turn diagnostics to stderr. */
    public boolean DEBUG = false;

    public Params() {}

    public Params(String query) {
        apply(query);
    }

    /** Applies "KEY=value&KEY2=value2" (or comma-separated, shell-friendly) overrides; unknown keys throw. */
    public Params apply(String query) {
        if (query == null || query.isEmpty()) return this;
        for (String kv : query.split("[&,]")) {
            if (kv.isEmpty()) continue;
            int eq = kv.indexOf('=');
            if (eq < 0) throw new IllegalArgumentException("expected KEY=value: " + kv);
            set(kv.substring(0, eq).trim(), kv.substring(eq + 1).trim());
        }
        return this;
    }

    public void set(String key, String value) {
        try {
            Field f = getClass().getField(key);
            if (Modifier.isStatic(f.getModifiers())) throw new NoSuchFieldException(key);
            Class<?> t = f.getType();
            if (t == double.class) f.setDouble(this, Double.parseDouble(value));
            else if (t == int.class) f.setInt(this, Integer.parseInt(value));
            else if (t == boolean.class) f.setBoolean(this, parseBool(value));
            else if (t == String.class) f.set(this, value);
            else throw new IllegalArgumentException("unsupported param type " + t + " for " + key);
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException("unknown param: " + key);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean parseBool(String v) {
        return v.equals("1") || v.equalsIgnoreCase("true") || v.equalsIgnoreCase("on");
    }

    public Params copy() {
        Params p = new Params();
        for (Field f : getClass().getFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            try {
                f.set(p, f.get(this));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        return p;
    }

    /** Non-default values as a query string (for logging which variant played). */
    public String diff() {
        Params def = new Params();
        StringBuilder sb = new StringBuilder();
        for (Field f : getClass().getFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            try {
                Object a = f.get(this), b = f.get(def);
                if (!a.equals(b)) {
                    if (sb.length() > 0) sb.append('&');
                    sb.append(f.getName()).append('=').append(a);
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        String d = diff();
        return d.isEmpty() ? "default" : d;
    }
}
