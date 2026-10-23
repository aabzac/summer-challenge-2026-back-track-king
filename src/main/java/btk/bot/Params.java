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

    // TURN_BUDGET_BEGIN
    /** Current native rollout experiment: later-turn allowance including input preparation. */
    public int TURN_BUDGET_MS = 36;
    // TURN_BUDGET_END

    /** Local strength prototype only; FileMerger refuses this option until packaging is implemented. */
    public boolean USE_NEURAL_TARGET_PROPOSAL = false;

    /** Spatial PPO v1 only; historical/default artifacts omit this independent policy field. */
    public int SPATIAL_RESERVE_MS = 2;

    /** Spatial v5 only; omitted from all earlier configurations and submission sources. */
    public boolean USE_SPATIAL_V5_SEARCH = false;
    public int SPATIAL_V5_MIN_SEARCH_MS = 12;

    /** Spatial v6 only; omitted from earlier configurations and submission sources. */
    public boolean USE_SPATIAL_V6_SEARCH = false;
    public int SPATIAL_V6_MIN_SEARCH_MS = 12;
    public int SPATIAL_V6_DEPTH = 2;

    /** Spatial v7 only; omitted from earlier configurations and submission sources. */
    public boolean USE_SPATIAL_V7_SEARCH = false;
    public int SPATIAL_V7_MIN_SEARCH_MS = 12;
    public int SPATIAL_V7_DEPTH = 2;

    /** Spatial v8 only; omitted from earlier configurations and submission sources. */
    public boolean USE_SPATIAL_V8_SEARCH = false;
    public int SPATIAL_V8_MIN_SEARCH_MS = 12;
    public int SPATIAL_V8_DEPTH = 8;

    /** Spatial v9 town context; excluded from every earlier configuration and artifact. */
    public boolean USE_SPATIAL_V9_TOWN_CONTEXT = false;

    /** V3-only cooperative deadline; excluded from other bots' config and emitted Params to preserve their artifacts. */
    public int NEURAL_DEADLINE_RESERVE_MS = 2;
    /** V7-only bounded construction on deadline cutoff; historical artifacts omit these fields. */
    public boolean USE_NEURAL_FALLBACK = false;
    public int NEURAL_FALLBACK_MAX_INSTABILITY = 0;

    /** Independent Indrill-style action-value policy: extra route cost per instability level. */
    public int INDRILL_INK_PENALTY = 2;

    // Selected v09 policy; disable explicitly to use the retained EvalBot experiments.
    public boolean USE_ROLLOUT_POLICY = true;
    // BATCHED_REFRESH_BEGIN
    /** Selected v10 (stage2-compute-v1): share canonical connection BFS across pairs with one destination. Frozen pre-v10 snapshots keep it off. */
    public boolean USE_BATCHED_REFRESH = true;
    // BATCHED_REFRESH_END
    // INK_REFRESH_BEGIN
    /** Selected v10: preserve canonical paths unaffected by hypothetical ink (also inside the lite chooser). */
    public boolean USE_INCREMENTAL_INK_REFRESH = true;
    // INK_REFRESH_END
    // FAST_CHEAPEST_PATH_BEGIN
    /** Selected v10: reuse immutable grid geometry during canonical route construction. */
    public boolean USE_FAST_CHEAPEST_PATH = true;
    // FAST_CHEAPEST_PATH_END
    /** Current-only route proposal: charge instability on existing tracks as well as free cells. */
    public boolean USE_ROLLOUT_ALL_CELL_RISK = false;
    /** Current-only experiment: score and emit the same filled first action. */
    public boolean USE_ROLLOUT_ROOT_FILL = false;
    // MISSED_DEADLINE_BEGIN
    /** Current-only experiment: honor a missed, repeatedly forecast sabotage deadline. */
    public boolean USE_ROLLOUT_MISSED_DEADLINE = false;
    // MISSED_DEADLINE_END
    // BOSS_SABOTAGE_BEGIN
    /** Current-only experiment: boss-fitted sabotage chooser replaces the real-turn target (docs/boss-proxy-v1.md). */
    public boolean USE_BOSS_SABOTAGE = false;
    // BOSS_SABOTAGE_END
    // BOSS_HOLD_BEGIN
    /** Hold margin shared by both boss choosers: the held region is kept unless another beats it by this much. */
    public double BOSS_HOLD_MARGIN = 1.0;
    // BOSS_HOLD_END
    // BOSS_LITE_BEGIN
    /** Selected v10 (boss-sabotage-v2): the two-stage boss chooser (cheap logit shortlist, top-ROLLOUT_TARGETS ink rescoring) replaces the real-turn target; docs/stage1-opponent-model-v1.md. */
    public boolean USE_BOSS_LITE = true;
    // BOSS_LITE_END
    // BOSS_MODEL_BEGIN
    /** Selected v10 (opponent-model-v1): the simulated opponent inside the rollout disrupts with the two-stage boss chooser (held target carried through the simulated turns) instead of chooseTarget. Requires USE_BOSS_LITE. */
    public boolean USE_BOSS_OPPONENT_MODEL = true;
    /** How many reply scenarios use the boss opponent model (the rest keep chooseTarget); 2 = both. */
    public int BOSS_OPPONENT_REPLIES = 2;
    /** Our own simulated future disrupts inside the rollout also use the two-stage chooser (consistency with the real-turn choice). */
    public boolean USE_BOSS_OWN_MODEL = true;
    // BOSS_MODEL_END
    // BOSS_ROUTES_BEGIN
    /** Selected v10 (boss-lite-routes-v1): the two-stage chooser adds the fitted route-traffic term using the previous turn's route portfolio of the chooser's opponent (no path searches). Requires USE_BOSS_LITE. */
    public boolean USE_BOSS_LITE_ROUTES = true;
    public double BOSS_LITE_ROUTE_WEIGHT = 0.371747;
    // BOSS_ROUTES_END
    // Selected cheap-income portfolio slot; frozen v06 does not read these fields.
    public boolean USE_ROLLOUT_QUICK_ROUTE = true;
    public int ROLLOUT_QUICK_MAX_PAINT = 6;
    public boolean USE_ROLLOUT_QUICK_EARLY = false;
    public boolean USE_ROLLOUT_QUICK_VALUE = false;
    public boolean USE_ROLLOUT_QUICK_SAFE = false;
    public boolean USE_ROLLOUT_DIVERSE_REPLIES = false;
    public boolean USE_ROLLOUT_REPLAN = false;
    public int ROLLOUT_REPLAN_TURN = 4;
    public boolean USE_ROLLOUT_SECOND_TARGET = false;
    public boolean USE_ROLLOUT_SPREAD = false;
    public int ROLLOUT_SPREAD_ROOTS = 2;
    public boolean USE_ROLLOUT_NETWORK = false;
    public boolean USE_ROLLOUT_NETWORK_AFTER_BASE = true;
    /** Abandon simulated continuations when a canonical completed-path bridge is destroyed. */
    public boolean USE_ROLLOUT_ROUTE_DEPENDENCIES = false;
    /** Affordable two/three-cell network roots, after ordinary and single-cell comparisons. */
    public boolean USE_ROLLOUT_NETWORK_CHORDS = false;
    /** RHEA continuations: read by frozen v08 only; current rejects the flag (code removed 2026-09-20 for artifact size). */
    public boolean USE_ROLLOUT_RHEA = false;
    public int ROLLOUT_RHEA_MUTATIONS = 12;
    public int ROLLOUT_RHEA_ELITES = 2;
    /** Selected v09 policy: spend leftover paint on stable/protected cells next to own rails (boss/leader cell study). */
    public boolean USE_ROLLOUT_FILL = true;
    public int ROLLOUT_FILL_MAX_INSTABILITY = 0;
    /** Selected first-three-turn protected approach ownership policy. */
    public boolean USE_PROTECTED_OPENING = true;
    public int OPENING_TURNS = 3;
    public int OPENING_CITY_QUOTA = 1;
    public double OPENING_COLLISION_FACTOR = 0.25;
    public int ROLLOUT_NETWORK_MAX_CELLS = 24;
    public int ROLLOUT_NETWORK_ROOTS = 2;
    public int ROLLOUT_NETWORK_RESERVE_MS = 12;
    public int ROLLOUT_WIDTH = 6;
    public int ROLLOUT_REPLIES = 2;
    public int ROLLOUT_DEPTH = 8;
    public int ROLLOUT_MAX_ROUTES = 64;
    public int ROLLOUT_RESERVE_MS = 20;
    public int ROLLOUT_TARGETS = 4;
    public double ROLLOUT_TAIL = 3.0;
    public double ROLLOUT_ROBUSTNESS = 0.5;
    public double ROLLOUT_TRACK_WEIGHT = 0.4;
    public double ROLLOUT_TARGET_HORIZON = 8.0;
    public double ROLLOUT_ROUTE_HORIZON = 16.0;
    public double ROLLOUT_THREAT_WEIGHT = 0.5;
    public double ROLLOUT_WIN_VALUE = 1000000.0;
    /**
     * Sabotage the opponent's live project: regions where it placed tracks within the last
     * ROLLOUT_LIVE_TURNS observed turns gain ROLLOUT_LIVE_WEIGHT per such cell (income x turns).
     */
    public boolean USE_ROLLOUT_LIVE_TARGET = false;
    public double ROLLOUT_LIVE_WEIGHT = 12.0;
    public int ROLLOUT_LIVE_TURNS = 2;
    /** Spend the disruption point on the least harmful legal target when no region has positive value. */
    public boolean ROLLOUT_ALWAYS_DISRUPT = false;
    /**
     * Leader-calibrated track valuation (Indrill study, 2026-09-20): every opponent track in a region
     * is worth ROLLOUT_OPP_TRACK_VALUE and every own track costs ROLLOUT_OWN_TRACK_VALUE, in the same
     * income-times-turns units as the exact ink delta. Replaces the single trackBalance weight so
     * unfinished opponent projects are inked before they earn. Off: v08 valuation unchanged.
     */
    public boolean USE_ROLLOUT_TRACK_VALUE = false;
    public double ROLLOUT_OPP_TRACK_VALUE = 5.0;
    public double ROLLOUT_OWN_TRACK_VALUE = 6.0;
    /** Apply the track valuation only to our own chooser; the simulated opponent keeps the v08 term. */
    public boolean ROLLOUT_TRACK_VALUE_SELF_ONLY = false;

    // Independent simple policy; off in the selected v06 strategy.
    public boolean USE_HEURISTIC_POLICY = false;
    public boolean HEURISTIC_EMERGENCY_STOP = true;
    public int HEURISTIC_MAX_ROUTES = 64;
    public double HEURISTIC_TARGET_STICKINESS = 1.3;
    public double HEURISTIC_ROUTE_STICKINESS = 1.25;
    public double HEURISTIC_OWN_PENALTY = 2.0;
    public double HEURISTIC_ACTIVE_WEIGHT = 2.0;
    public int HEURISTIC_AVOID_INSTABILITY = 2;
    public boolean USE_HEURISTIC_DETOUR = false;
    public double HEURISTIC_DETOUR_WEIGHT = 1.0;
    public int HEURISTIC_DETOUR_REGIONS = 4;
    public int HEURISTIC_DETOUR_PAIRS = 4;
    public int HEURISTIC_DETOUR_CAP = 12;

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
    public int DISRUPT_RESERVE_MS = 6;
    /** Weight of our own income lost when inking a region (vs opponent's loss = 1). */
    public double W_SELF_LOSS = 1.2;
    /** Value per opponent track destroyed that is not (yet) earning. */
    public double W_FUTURE_TRACK = 0.3;
    /** Value per own track destroyed that is not (yet) earning. */
    public double W_FUTURE_OWN = 0.4;
    /** Minimum value to bother disrupting (below: no DISRUPT). */
    public double DISRUPT_MIN = 0.01;
    /** Experimental retention per extra hit before ink (0..1); 1 preserves v05 valuation. */
    public double DISRUPT_DELAY_FACTOR = 1.0;

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
    /** Experimental forced cheap bypass of already damaged active income; off until promoted. */
    public boolean USE_DEFENSE_PRIORITY = false;
    public int DEFENSE_MAX_REGIONS = 3;
    public int DEFENSE_MAX_PAIRS = 4;
    public int DEFENSE_MAX_PAINT = 6;
    public int DEFENSE_MIN_INSTABILITY = 1;
    /** Spend otherwise idle paint by extending our existing network through safe nearby cells. */
    public boolean USE_IDLE_EXTEND = true;
    /** Experimental: veto actual affordable builds that reduce current raw income difference. */
    public boolean GUARD_BUILD_INCOME = false;
    /** Experimental: build the selected route from the end with better affordable-prefix income. */
    public boolean CHOOSE_BUILD_DIRECTION = false;
    /** Experimental: rerank several build prefixes against likely simultaneous opponent moves. */
    public boolean USE_REPLY_RERANK = false;
    /** Experimental K2/R1 joint prefix/own-target search; off in v05 and unrelated experiments. */
    public boolean USE_JOINT_ACTION = false;
    /** Number of our distinct affordable build prefixes retained for reply-aware reranking. */
    public int BUILD_BEAM_WIDTH = 4;
    /** Number of distinct opponent build prefixes used as likely replies. */
    public int OPP_REPLY_WIDTH = 2;
    /** Experimental frozen-board construction sequences; no opponent reply prediction. */
    public boolean USE_OWN_BUILD_PLAN = false;
    public int OWN_BUILD_WIDTH = 3;
    public int OWN_BUILD_DEPTH = 3;

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
    /** Experimental: precompute a ready-to-ink target before route search as a timeout fallback. */
    public boolean EARLY_DISRUPT_FALLBACK = false;

    // ---- symmetry breaking / benches ----
    /** Relative multiplicative noise on candidate values (0 = deterministic). Breaks self-play mirrors. */
    public double NOISE = 0.1;
    /** Noise seed; the player id is mixed in so both sides differ. */
    public int SEED = 1;

    // ---- misc ----
    /** Verified submission-runtime warmup; frozen strategy implementations do not consume it. */
    public boolean ISOLATED_ROUTE_WARMUP = true;
    public int WARMUP_SLICE_MS = 36;
    /** Diagnostic counters only; leave off in strength benches and submissions. */
    public boolean PROFILE_SEARCH = false;
    /** Default-off experiment: discard incomplete vulnerability evaluations at build cutoff. */
    public boolean INTERRUPT_VULN_EVAL = false;
    /** Offline sabotage-forecast study only; not connected to current strategy. */
    public double FORECAST_DECAY = 0.95;
    public double FORECAST_FLOOR = 0.10;

    // Local-only pressure reference; current and frozen vNN strategies do not read these.
    public int PRESSURE_HORIZON = 8;
    public int PRESSURE_BUILD_RESERVE_MS = 12;
    public double PRESSURE_TRACK_WEIGHT = 0.8;
    public double PRESSURE_CORRIDOR_WEIGHT = 2.0;
    public double PRESSURE_STICKINESS = 0.25;

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
