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
import btk.bot.neural.SpatialVariantBot;
import btk.bot.reference.LeaderBot;
import btk.bot.reference.LeaderBot2;
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
        if (version.equals("pressure-v1") || version.equals("leader-v1") || version.equals("leader-v2")
                || version.equals("neural-v1") || version.equals("neural-v2") || version.equals("neural-v3")
                || version.equals("neural-v4") || version.equals("neural-v5") || version.equals("neural-v6") || version.equals("neural-v7")
                || version.equals("indrill-q-v1")
                || version.equals("indrill-q-v2") || spatialVersion(version)) FrozenParams.applyTo(version, params);
        return params.apply(query);
    }

    /** Full resolved configuration, independent of Params.diff() and future current defaults. */
    public static Map<String, String> configuration(String spec) {
        Params params = parameters(spec);
        Map<String, String> values = new TreeMap<>();
        if (version(spec).equals("v00")) return values; // This bot has no tunable fields.
        try {
            for (var field : Params.class.getFields()) {
                if ((field.getName().equals("USE_SPATIAL_V5_SEARCH") || field.getName().equals("SPATIAL_V5_MIN_SEARCH_MS"))
                        && !version(spec).equals("spatial-ppo-v5")) continue;
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

    private static String version(String spec) {
        if (spec == null || spec.isBlank()) throw new IllegalArgumentException("empty bot spec");
        int q = spec.indexOf('?');
        return q < 0 ? spec : spec.substring(0, q);
    }

    private static boolean spatialVersion(String version) {
        return version.equals("spatial-ppo-v1") || version.equals("spatial-ppo-v2")
                || version.equals("spatial-ppo-v2-init") || version.equals("spatial-ppo-v3")
                || version.equals("spatial-ppo-v4") || version.equals("spatial-ppo-v5");
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
            case "pressure-v1": return PressureBot::new;
            case "leader-v1": return LeaderBot::new;
            case "leader-v2": return LeaderBot2::new;
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
            case "indrill-q-v1": return IndrillBot::new;
            case "indrill-q-v2": return IndrillV2Bot::new;
            case "indrill-forum-v1": return IndrillForumBot::new;
            default:
                if (version.startsWith("exp-") && BotCatalog.entry(version).category().equals("experiment")) return EvalBot::new;
                return null;
        }
    }
}
