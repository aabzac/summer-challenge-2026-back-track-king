package btk.bot;

import btk.bot.current.EvalBot;
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
        if (version.equals("pressure-v1")) FrozenParams.applyTo(version, params);
        return params.apply(query);
    }

    /** Full resolved configuration, independent of Params.diff() and future current defaults. */
    public static Map<String, String> configuration(String spec) {
        Params params = parameters(spec);
        Map<String, String> values = new TreeMap<>();
        if (version(spec).equals("v00")) return values; // This bot has no tunable fields.
        try {
            for (var field : Params.class.getFields()) {
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
            case "pressure-v1": return PressureBot::new;
            default:
                if (version.startsWith("exp-") && BotCatalog.entry(version).category().equals("experiment")) return EvalBot::new;
                return null;
        }
    }
}
