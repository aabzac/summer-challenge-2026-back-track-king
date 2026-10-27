package btk.bot;

import btk.bot.current.EvalBot;
import btk.bot.current.IndrillBot;
import btk.bot.current.IndrillV2Bot;
import btk.bot.forum.IndrillForumBot;
import btk.bot.neural.NeuralBot;
import btk.bot.neural.NeuralV2Bot;
import btk.bot.neural.NeuralV3Bot;
import btk.bot.neural.NeuralV4Bot;
import btk.bot.neural.NeuralV5Bot;
import btk.bot.neural.NeuralV6Bot;
import btk.bot.neural.NeuralV7Bot;
import btk.bot.neural.SpatialBot;
import btk.bot.neural.SpatialInitialWeights;
import btk.bot.neural.SpatialV2Weights;
import btk.bot.neural.SpatialV3Bot;
import btk.bot.neural.SpatialV4Bot;
import btk.bot.neural.SpatialV5Bot;
import btk.bot.neural.SpatialV6Bot;
import btk.bot.neural.SpatialV7Bot;
import btk.bot.neural.SpatialV8Bot;
import btk.bot.neural.SpatialV9Bot;
import btk.bot.neural.SpatialV10Bot;
import btk.bot.neural.SpatialVariantBot;
import btk.bot.reference.LeaderBot;
import btk.bot.reference.LeaderBot2;
import btk.bot.reference.BossBot;
import btk.bot.reference.PressureBot;
import btk.bot.v00.AutoplaceBot;
import btk.bot.v01.GreedyBotV01;
import btk.bot.v02.EvalBotV02;
import btk.bot.v03.EvalBotV03;
import btk.bot.v04.EvalBotV04;
import btk.bot.v05.EvalBotV05;
import btk.bot.v06.RolloutBotV06;
import btk.bot.v07.RolloutBotV07;
import btk.bot.v08.RolloutBotV08;
import btk.bot.v09.RolloutBotV09;
import btk.bot.v10.RolloutBotV10;
import btk.bot.v11.RolloutBotV11;
import btk.bot.v12.RolloutBotV12;
import btk.core.Action;
import btk.core.GameState;

import java.util.function.Function;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.List;
import java.util.TreeMap;

/**
 * Creates bots by spec string: {@code "<version>"} or {@code "<version>?KEY=v,KEY2=v"}.
 * Versions: {@code current} (working copy), {@code main} (what ships), {@code vNN} (frozen).
 * Local-only {@code exp-*} recipes and readable labels are described by {@link BotCatalog}.
 */
public final class BotFactory {

    private BotFactory() {}

    public static Bot create(String spec) {
        String version = version(spec);
        Params params = parameters(spec);
        if (version.equals("current") && params.USE_NEURAL_TARGET_PROPOSAL)
            return new btk.tools.target.TargetHybridBot(params);
        Bot bot = version.startsWith("exp-") ? BotCatalog.createExperiment(spec) : lookup(version).apply(params);
        if (!version.startsWith("exp-")) return bot;
        // Preserve a named preset in generic referee replays without touching strategy classes.
        return new Bot() {
            public String name() { return spec; }
            public void init(GameState s, int me) { bot.init(s, me); }
            public List<Action> play(GameState s, int me, long ms) { return bot.play(s, me, ms); }
            public void warmup(GameState s, int me, long ms) { bot.warmup(s, me, ms); }
        };
    }

    /** The selected local version: {@code current} is its copy plus any later, not yet promoted work. */
    public static final String SELECTED_VERSION = "v12";

    /**
     * Short human-readable strategy name for the first-turn MESSAGE and the artifact header: the version id
     * ({@code "v11"}; {@code current}/{@code main} carry the selected version's), followed by {@code " (modified)"}
     * when the spec's resolved configuration differs from the version's defaults. The BOTS.md titles are not part
     * of it since 2026-10-25 (user request: the viewer line shows the version only).
     */
    public static String displayName(String spec) {
        String version = version(spec);
        String name = version.equals("current") || version.equals("main") ? SELECTED_VERSION : version;
        boolean modified = spec.indexOf('?') >= 0 && !configuration(spec).equals(configuration(version));
        return modified ? name + " (modified)" : name;
    }

    /** The seven selected v10 flags switched off: the v09 configuration expressed on current or v10. */
    public static final String V10_FLAGS_OFF = "USE_BOSS_LITE=0,USE_BOSS_OPPONENT_MODEL=0,USE_BOSS_OWN_MODEL=0,USE_BOSS_LITE_ROUTES=0,"
            + "USE_INCREMENTAL_INK_REFRESH=0,USE_FAST_CHEAPEST_PATH=0,USE_BATCHED_REFRESH=0";
    /** The selected v11 flag switched off: the v10 configuration expressed on current or v11 (the lite chooser off implies it). */
    public static final String V11_FLAGS_OFF = "USE_BOSS_ROOT_TARGET=0";
    /** The selected v12 flag switched off: the v11 configuration expressed on current or v12. */
    public static final String V12_FLAGS_OFF = "USE_EXPOSURE_INCOME=0";

    /**
     * Rollout experiments removed from {@code current} by prune-v1 (2026-10-25) together with their code and their
     * Params fields in the artifact. The frozen {@code v10} package still implements every one of them
     * ({@code "v10?FLAG=1"}); on {@code current}/{@code main} a true value is refused instead of silently ignored.
     */
    public static final List<String> PRUNED_ROLLOUT_FLAGS = List.of(
            "USE_ROLLOUT_ALL_CELL_RISK", "USE_ROLLOUT_ROOT_FILL", "USE_ROLLOUT_QUICK_EARLY", "USE_ROLLOUT_QUICK_VALUE",
            "USE_ROLLOUT_QUICK_SAFE", "USE_ROLLOUT_DIVERSE_REPLIES", "USE_ROLLOUT_REPLAN", "USE_ROLLOUT_SECOND_TARGET",
            "USE_ROLLOUT_SPREAD", "USE_ROLLOUT_NETWORK", "USE_ROLLOUT_ROUTE_DEPENDENCIES", "USE_ROLLOUT_NETWORK_CHORDS",
            "USE_ROLLOUT_RHEA", "USE_ROLLOUT_LIVE_TARGET", "ROLLOUT_ALWAYS_DISRUPT", "USE_ROLLOUT_TRACK_VALUE",
            "ROLLOUT_TRACK_VALUE_SELF_ONLY");

    private static boolean flagValue(Params params, String flag) {
        try {
            return Params.class.getField(flag).getBoolean(params);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Resolve historical defaults first, then explicit ablation overrides. Always a fresh object. */
    public static Params parameters(String spec) {
        String version = version(spec);
        int q = spec.indexOf('?');
        String query = q < 0 ? "" : spec.substring(q + 1);
        if (lookup(version) == null) throw new IllegalArgumentException("unknown bot: " + version);
        if ((version.equals("main") || version.equals("v00")) && !query.isBlank()) {
            throw new IllegalArgumentException(version + " does not accept parameter overrides");
        }
        if (version.startsWith("exp-")) {
            for (String item : query.split("[&,]")) if (!item.isBlank()) {
                int eq = item.indexOf('=');
                if (eq < 0 || !BotCatalog.parameterNames(version).contains(item.substring(0, eq).trim())) {
                    throw new IllegalArgumentException("Parameter absent from retained engine: " + item);
                }
            }
            return BotCatalog.experimentParameters(version).apply(query);
        }
        Params params = new Params();
        if (version.startsWith("v") && !version.equals("v00")) FrozenParams.applyTo(version, params);
        if (version.equals("pressure-v1") || version.equals("leader-v1") || version.equals("leader-v2") || version.equals("boss-v1")
                || version.equals("neural-v1") || version.equals("neural-v2") || version.equals("neural-v3")
                || version.equals("neural-v4") || version.equals("neural-v5") || version.equals("neural-v6") || version.equals("neural-v7")
                || version.equals("indrill-q-v1")
                || version.equals("indrill-q-v2") || spatialVersion(version)) FrozenParams.applyTo(version, params);
        params.apply(query);
        if (params.TURN_BUDGET_MS != 36 && params.TURN_BUDGET_MS != 38 && params.TURN_BUDGET_MS != 40 && params.TURN_BUDGET_MS != 45)
            throw new IllegalArgumentException("Turn budget must be 36, 38, 40 or 45 ms");
        if (params.TURN_BUDGET_MS != 36 && (!nativeRollout(version) || !params.USE_ROLLOUT_POLICY
                || params.USE_NEURAL_TARGET_PROPOSAL || params.USE_HEURISTIC_POLICY))
            throw new IllegalArgumentException("38/40/45 ms requires the current native rollout policy");
        // The seven selected v10 flags are rollout-only. A policy that never reads them (EvalBot, heuristic,
        // neural proposal) or a frozen package without them drops the *default* values silently, but an
        // explicit request to ship them with such a policy is still an error, as before the promotion.
        boolean rolloutPolicy = params.USE_ROLLOUT_POLICY && !params.USE_NEURAL_TARGET_PROPOSAL && !params.USE_HEURISTIC_POLICY;
        if (!rolloutPolicy || !nativeRollout(version)) {
            if (params.USE_BATCHED_REFRESH) {
                if (explicit(query, "USE_BATCHED_REFRESH")) throw new IllegalArgumentException("Batched refresh requires the current rollout policy");
                params.USE_BATCHED_REFRESH = false;
            }
            if (params.USE_INCREMENTAL_INK_REFRESH) {
                if (explicit(query, "USE_INCREMENTAL_INK_REFRESH")) throw new IllegalArgumentException("Incremental ink refresh requires the current native rollout policy");
                params.USE_INCREMENTAL_INK_REFRESH = false;
            }
            if (params.USE_FAST_CHEAPEST_PATH) {
                if (explicit(query, "USE_FAST_CHEAPEST_PATH")) throw new IllegalArgumentException("Fast cheapest path requires the current native rollout policy");
                params.USE_FAST_CHEAPEST_PATH = false;
            }
            if (params.USE_BOSS_LITE || params.USE_BOSS_OPPONENT_MODEL || params.USE_BOSS_OWN_MODEL || params.USE_BOSS_LITE_ROUTES) {
                if (explicit(query, "USE_BOSS_LITE") || explicit(query, "USE_BOSS_OPPONENT_MODEL")
                        || explicit(query, "USE_BOSS_OWN_MODEL") || explicit(query, "USE_BOSS_LITE_ROUTES"))
                    throw new IllegalArgumentException("Boss lite chooser requires the current native rollout policy without USE_BOSS_SABOTAGE");
                params.USE_BOSS_LITE = params.USE_BOSS_OPPONENT_MODEL = params.USE_BOSS_OWN_MODEL = params.USE_BOSS_LITE_ROUTES = false;
            }
        }
        // The selected v11 flag (root target) lives inside the lite chooser: switching the lite chooser off, or a policy
        // that never reads it, drops the inherited default silently; an explicit request is still refused (same rule as v10).
        if (params.USE_BOSS_ROOT_TARGET && (!params.USE_BOSS_LITE || !rolloutPolicy || !nativeRollout(version))) {
            if (explicit(query, "USE_BOSS_ROOT_TARGET"))
                throw new IllegalArgumentException("Boss root target requires USE_BOSS_LITE (current native rollout)");
            params.USE_BOSS_ROOT_TARGET = false;
        }
        if (params.USE_BOSS_SABOTAGE && (!nativeRollout(version) || !params.USE_ROLLOUT_POLICY
                || params.USE_NEURAL_TARGET_PROPOSAL || params.USE_HEURISTIC_POLICY))
            throw new IllegalArgumentException("Boss sabotage requires the current native rollout policy");
        if (params.USE_BOSS_LITE && params.USE_BOSS_SABOTAGE)
            throw new IllegalArgumentException("Boss lite chooser requires the current native rollout policy without USE_BOSS_SABOTAGE"
                    + " (since the v10 promotion the lite chooser is on by default: a sabotage proxy spec must add "
                    + "USE_BOSS_LITE=0,USE_BOSS_OPPONENT_MODEL=0,USE_BOSS_OWN_MODEL=0,USE_BOSS_LITE_ROUTES=0 explicitly)");
        if ((params.USE_BOSS_OPPONENT_MODEL || params.USE_BOSS_OWN_MODEL) && !params.USE_BOSS_LITE)
            throw new IllegalArgumentException("Boss opponent/own models require USE_BOSS_LITE");
        if (params.USE_BOSS_LITE_ROUTES && !params.USE_BOSS_LITE)
            throw new IllegalArgumentException("Boss lite route term requires USE_BOSS_LITE");
        if ((params.USE_BOSS_THREAT_MODEL || params.USE_BOSS_ROOT_TARGET)
                && !version.equals("current") && !version.equals("main") && !version.equals("v11") && !version.equals("v12"))
            throw new IllegalArgumentException("Boss threat model / root target exist on current, v11 and v12 only (stage4-threat-model-v1); frozen v10 has neither");
        // The selected v12 flag is rollout-only: a policy that never reads it drops the inherited default silently, an
        // explicit request is refused (the v10/v11 rule); frozen versions before v12 never had it.
        if (params.USE_EXPOSURE_INCOME && (!rolloutPolicy || !nativeRollout(version))) {
            if (explicit(query, "USE_EXPOSURE_INCOME"))
                throw new IllegalArgumentException("Exposure income requires the current native rollout policy");
            params.USE_EXPOSURE_INCOME = false;
        }
        if (params.USE_EXPOSURE_INCOME && !version.equals("current") && !version.equals("main") && !version.equals("v12"))
            throw new IllegalArgumentException("Exposure income exists on current and v12 only (exposure-income-v1); frozen v11 and v10 never had it");
        if (!Double.isFinite(params.EXPOSURE_INCOME_WEIGHT) || params.EXPOSURE_INCOME_WEIGHT < 0 || params.EXPOSURE_INCOME_WEIGHT > 1)
            throw new IllegalArgumentException("EXPOSURE_INCOME_WEIGHT must be within [0, 1]");
        if (params.USE_COLLISION_AVOIDANCE && (!version.equals("current") || !rolloutPolicy))
            throw new IllegalArgumentException("Collision avoidance is a current-only rollout experiment (collision-avoidance-v1)");
        if (!Double.isFinite(params.COLLISION_PENALTY) || params.COLLISION_PENALTY < 0)
            throw new IllegalArgumentException("COLLISION_PENALTY must be a finite non-negative number");
        if (params.USE_CORRIDOR_ROUTES && (!version.equals("current") || !rolloutPolicy))
            throw new IllegalArgumentException("Corridor routes are a current-only rollout experiment (corridor-routes-v1)");
        if (!Double.isFinite(params.CORRIDOR_WEIGHT) || params.CORRIDOR_WEIGHT < 0)
            throw new IllegalArgumentException("CORRIDOR_WEIGHT must be a finite non-negative number");
        if ((params.USE_QUICK_ALWAYS || params.DELAY_POWER != 1.0) && (!version.equals("current") || !rolloutPolicy))
            throw new IllegalArgumentException("Quick-always / delay power are current-only rollout experiments (earning-delay-v1)");
        if (params.USE_QUICK_ALWAYS && !params.USE_ROLLOUT_QUICK_ROUTE)
            throw new IllegalArgumentException("USE_QUICK_ALWAYS requires USE_ROLLOUT_QUICK_ROUTE");
        if (!Double.isFinite(params.DELAY_POWER) || params.DELAY_POWER <= 0)
            throw new IllegalArgumentException("DELAY_POWER must be a finite positive number");
        if (!Double.isFinite(params.BOSS_OPP_HOLD_MARGIN))
            throw new IllegalArgumentException("BOSS_OPP_HOLD_MARGIN must be finite (negative = BOSS_HOLD_MARGIN)");
        if (params.BOSS_OPP_HOLD_MARGIN >= 0 && (!version.equals("current") || !rolloutPolicy || !params.USE_BOSS_LITE || !params.USE_BOSS_OPPONENT_MODEL))
            throw new IllegalArgumentException("BOSS_OPP_HOLD_MARGIN is a current-only experiment of the lite opponent model (opp-hold-v1)");
        if (params.USE_BOSS_NO_CLOSE && (!version.equals("current") || !params.USE_BOSS_SABOTAGE))
            throw new IllegalArgumentException("USE_BOSS_NO_CLOSE is a current-only switch of the BossSabotage proxy (boss-v4)");
        if (params.USE_ROUTE_COMMIT && (!version.equals("current") || !rolloutPolicy))
            throw new IllegalArgumentException("Route commitment is a current-only rollout experiment (route-commit-v1)");
        if (!Double.isFinite(params.COMMIT_BONUS) || params.COMMIT_BONUS < 0)
            throw new IllegalArgumentException("COMMIT_BONUS must be a finite non-negative number");
        if (params.USE_BOSS_THREAT_MODEL && !params.USE_BOSS_OPPONENT_MODEL)
            throw new IllegalArgumentException("Boss threat model requires USE_BOSS_OPPONENT_MODEL (current native rollout)");
        if (params.USE_BOSS_ROOT_TARGET && !params.USE_BOSS_LITE)
            throw new IllegalArgumentException("Boss root target requires USE_BOSS_LITE (current native rollout)");
        if (params.BOSS_OPPONENT_REPLIES < 0 || params.BOSS_OPPONENT_REPLIES > params.ROLLOUT_REPLIES)
            throw new IllegalArgumentException("BOSS_OPPONENT_REPLIES must be between 0 and ROLLOUT_REPLIES");
        if ((version.equals("current") || version.equals("main") || version.equals("v11") || version.equals("v12")) && rolloutPolicy)
            for (String flag : PRUNED_ROLLOUT_FLAGS) if (flagValue(params, flag))
                throw new IllegalArgumentException(flag + " was pruned from current (prune-v1, 2026-10-25); run it on v10");
        if (params.USE_ROLLOUT_MISSED_DEADLINE && !version.equals("current"))
            throw new IllegalArgumentException("Missed-deadline correction is a current-only experiment");
        if (params.USE_ROLLOUT_MISSED_DEADLINE && (!params.USE_ROLLOUT_POLICY || params.USE_NEURAL_TARGET_PROPOSAL))
            throw new IllegalArgumentException("Missed-deadline correction requires the current native rollout");
        if (params.USE_NEURAL_TARGET_PROPOSAL && !version.equals("current"))
            throw new IllegalArgumentException("Neural target proposal is a local current-only experiment");
        return params;
    }

    /** Full resolved configuration, independent of Params.diff() and future current defaults. */
    public static Map<String, String> configuration(String spec) {
        Params params = parameters(spec);
        Map<String, String> values = new TreeMap<>();
        if (version(spec).equals("v00")) return values; // This bot has no tunable fields.
        try {
            for (var field : Params.class.getFields()) {
                if (field.getName().equals("TURN_BUDGET_MS") && params.TURN_BUDGET_MS == 36) continue;
                if (field.getName().equals("USE_BATCHED_REFRESH") && !params.USE_BATCHED_REFRESH) continue;
                if (field.getName().equals("USE_INCREMENTAL_INK_REFRESH") && !params.USE_INCREMENTAL_INK_REFRESH) continue;
                if (field.getName().equals("USE_FAST_CHEAPEST_PATH") && !params.USE_FAST_CHEAPEST_PATH) continue;
                if (field.getName().equals("USE_ROLLOUT_MISSED_DEADLINE") && !params.USE_ROLLOUT_MISSED_DEADLINE) continue;
                if (field.getName().equals("USE_BOSS_SABOTAGE") && !params.USE_BOSS_SABOTAGE) continue;
                if (field.getName().equals("BOSS_HOLD_MARGIN") && !params.USE_BOSS_SABOTAGE && !params.USE_BOSS_LITE) continue;
                if (field.getName().equals("USE_BOSS_LITE") && !params.USE_BOSS_LITE) continue;
                if ((field.getName().equals("USE_BOSS_OPPONENT_MODEL") || field.getName().equals("BOSS_OPPONENT_REPLIES")
                        || field.getName().equals("USE_BOSS_OWN_MODEL")) && !params.USE_BOSS_LITE) continue;
                if ((field.getName().equals("USE_BOSS_LITE_ROUTES") || field.getName().equals("BOSS_LITE_ROUTE_WEIGHT"))
                        && !params.USE_BOSS_LITE_ROUTES) continue;
                if (field.getName().equals("USE_BOSS_THREAT_MODEL") && !params.USE_BOSS_THREAT_MODEL) continue;
                if ((field.getName().equals("USE_EXPOSURE_INCOME") || field.getName().equals("EXPOSURE_INCOME_WEIGHT"))
                        && !params.USE_EXPOSURE_INCOME) continue;
                if ((field.getName().equals("USE_COLLISION_AVOIDANCE") || field.getName().equals("COLLISION_PENALTY"))
                        && !params.USE_COLLISION_AVOIDANCE) continue;
                if ((field.getName().equals("USE_CORRIDOR_ROUTES") || field.getName().equals("CORRIDOR_WEIGHT"))
                        && !params.USE_CORRIDOR_ROUTES) continue;
                if (field.getName().equals("USE_QUICK_ALWAYS") && !params.USE_QUICK_ALWAYS) continue;
                if (field.getName().equals("DELAY_POWER") && params.DELAY_POWER == 1.0) continue;
                if (field.getName().equals("BOSS_OPP_HOLD_MARGIN") && params.BOSS_OPP_HOLD_MARGIN < 0) continue;
                if (field.getName().equals("USE_BOSS_NO_CLOSE") && !params.USE_BOSS_NO_CLOSE) continue;
                if ((field.getName().equals("USE_ROUTE_COMMIT") || field.getName().equals("COMMIT_BONUS")) && !params.USE_ROUTE_COMMIT) continue;
                if (field.getName().equals("USE_BOSS_ROOT_TARGET") && !params.USE_BOSS_ROOT_TARGET) continue;
                if (field.getName().equals("USE_NEURAL_TARGET_PROPOSAL") && !params.USE_NEURAL_TARGET_PROPOSAL) continue;
                if (field.getName().equals("USE_SPATIAL_V9_TOWN_CONTEXT") && !version(spec).equals("spatial-ppo-v9")) continue;
                if ((field.getName().equals("USE_SPATIAL_V8_SEARCH") || field.getName().equals("SPATIAL_V8_MIN_SEARCH_MS")
                        || field.getName().equals("SPATIAL_V8_DEPTH")) && !version(spec).equals("spatial-ppo-v8")) continue;
                if ((field.getName().equals("USE_SPATIAL_V7_SEARCH") || field.getName().equals("SPATIAL_V7_MIN_SEARCH_MS")
                        || field.getName().equals("SPATIAL_V7_DEPTH")) && !version(spec).equals("spatial-ppo-v7")
                        && !version(spec).equals("spatial-ppo-v8")) continue;
                if ((field.getName().equals("USE_SPATIAL_V6_SEARCH") || field.getName().equals("SPATIAL_V6_MIN_SEARCH_MS")
                        || field.getName().equals("SPATIAL_V6_DEPTH")) && !version(spec).equals("spatial-ppo-v6")
                        && !version(spec).equals("spatial-ppo-v7") && !version(spec).equals("spatial-ppo-v8")) continue;
                if ((field.getName().equals("USE_SPATIAL_V5_SEARCH") || field.getName().equals("SPATIAL_V5_MIN_SEARCH_MS"))
                        && !version(spec).equals("spatial-ppo-v5") && !version(spec).equals("spatial-ppo-v6")
                        && !version(spec).equals("spatial-ppo-v7") && !version(spec).equals("spatial-ppo-v8")) continue;
                if (field.getName().equals("SPATIAL_RESERVE_MS") && !spatialVersion(version(spec))) continue;
                if ((field.getName().equals("USE_ROLLOUT_ALL_CELL_RISK") || field.getName().equals("USE_ROLLOUT_ROOT_FILL"))
                        && (!(version(spec).equals("current") || version(spec).equals("main"))
                        || !params.USE_ROLLOUT_POLICY)) continue;
                // Only deadline-aware neural bots consume this field; historical configurations stay exact.
                if (field.getName().equals("NEURAL_DEADLINE_RESERVE_MS")
                        && !version(spec).equals("neural-v3") && !version(spec).equals("neural-v4")
                        && !version(spec).equals("neural-v5") && !version(spec).equals("neural-v6")
                        && !version(spec).equals("neural-v7")) continue;
                if ((field.getName().equals("USE_NEURAL_FALLBACK") || field.getName().equals("NEURAL_FALLBACK_MAX_INSTABILITY"))
                        && !version(spec).equals("neural-v7")) continue;
                if (version(spec).startsWith("exp-") && !BotCatalog.parameterNames(version(spec)).contains(field.getName())) continue;
                if (!Modifier.isStatic(field.getModifiers())) values.put(field.getName(), String.valueOf(field.get(params)));
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        return values;
    }

    /** True when the query names the key with a true value (explicit request, not an inherited default). */
    private static boolean explicit(String query, String key) {
        boolean result = false; // the last mention wins, exactly as Params.apply resolves repeated keys
        for (String item : query.split("[&,]")) {
            int eq = item.indexOf('=');
            if (eq < 0 || !item.substring(0, eq).trim().equals(key)) continue;
            String value = item.substring(eq + 1).trim();
            result = value.equals("true") || value.equals("1");
        }
        return result;
    }

    /** Versions whose shipped policy is the native rollout with the selected v10 runtime/boss-lite blocks. */
    private static boolean nativeRollout(String version) {
        return version.equals("current") || version.equals("main") || version.equals("v10") || version.equals("v11") || version.equals("v12");
    }

    private static String version(String spec) {
        if (spec == null || spec.isBlank()) throw new IllegalArgumentException("empty bot spec");
        int q = spec.indexOf('?');
        return q < 0 ? spec : spec.substring(0, q);
    }

    private static boolean spatialVersion(String version) {
        return version.equals("spatial-ppo-v1") || version.equals("spatial-ppo-v2")
                || version.equals("spatial-ppo-v2-init") || version.equals("spatial-ppo-v3")
                || version.equals("spatial-ppo-v4") || version.equals("spatial-ppo-v5") || version.equals("spatial-ppo-v6")
                || version.equals("spatial-ppo-v7") || version.equals("spatial-ppo-v8") || version.equals("spatial-ppo-v9")
                || version.equals("spatial-ppo-v10");
    }

    private static Function<Params, Bot> lookup(String version) {
        switch (version) {
            case "current": return MainBot::newStrategy;
            case "main": return p -> new MainBot();
            case "v00": return p -> new AutoplaceBot();
            case "v01": return GreedyBotV01::new;
            case "v02": return EvalBotV02::new;
            case "v03": return EvalBotV03::new;
            case "v04": return EvalBotV04::new;
            case "v05": return EvalBotV05::new;
            case "v06": return RolloutBotV06::new;
            case "v07": return RolloutBotV07::new;
            case "v08": return RolloutBotV08::new;
            case "v09": return RolloutBotV09::new;
            case "v10": return RolloutBotV10::new;
            case "v11": return RolloutBotV11::new;
            case "v12": return RolloutBotV12::new;
            case "pressure-v1": return PressureBot::new;
            case "leader-v1": return LeaderBot::new;
            case "leader-v2": return LeaderBot2::new;
            case "boss-v1": return BossBot::new;
            case "neural-v1": return NeuralBot::new;
            case "neural-v2": return NeuralV2Bot::new;
            case "neural-v3": return NeuralV3Bot::new;
            case "neural-v4": return NeuralV4Bot::new;
            case "neural-v5": return NeuralV5Bot::new;
            case "neural-v6": return NeuralV6Bot::new;
            case "neural-v7": return NeuralV7Bot::new;
            case "spatial-ppo-v1": return SpatialBot::new;
            case "spatial-ppo-v2": return p -> new SpatialVariantBot(p, "spatial-ppo-v2", SpatialV2Weights.DATA);
            case "spatial-ppo-v2-init": return p -> new SpatialVariantBot(p, "spatial-ppo-v2-init", SpatialInitialWeights.DATA);
            case "spatial-ppo-v3": return SpatialV3Bot::new;
            case "spatial-ppo-v4": return SpatialV4Bot::new;
            case "spatial-ppo-v5": return SpatialV5Bot::new;
            case "spatial-ppo-v6": return SpatialV6Bot::new;
            case "spatial-ppo-v7": return SpatialV7Bot::new;
            case "spatial-ppo-v8": return SpatialV8Bot::new;
            case "spatial-ppo-v9": return SpatialV9Bot::new;
            case "spatial-ppo-v10": return SpatialV10Bot::new;
            case "indrill-q-v1": return IndrillBot::new;
            case "indrill-q-v2": return IndrillV2Bot::new;
            case "indrill-forum-v1": return IndrillForumBot::new;
            default:
                if (version.startsWith("exp-") && BotCatalog.entry(version).category().equals("experiment")) return EvalBot::new;
                return null;
        }
    }
}
