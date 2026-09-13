package btk.bot;

import btk.bot.current.EvalBot;
import btk.bot.v00.AutoplaceBot;
import btk.bot.v01.GreedyBotV01;
import btk.bot.v02.EvalBotV02;
import btk.bot.v03.EvalBotV03;
import btk.bot.v04.EvalBotV04;

import java.util.function.Function;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.TreeMap;

/**
 * Creates bots by spec string: {@code "<version>"} or {@code "<version>?KEY=v&KEY2=v"}.
 * Versions: {@code current} (working copy), {@code main} (what ships), {@code vNN} (frozen).
 */
public final class BotFactory {

    private BotFactory() {}

    public static Bot create(String spec) {
        String version = version(spec);
        Params params = parameters(spec);
        return lookup(version).apply(params);
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
        Params params = new Params();
        if (version.startsWith("v") && !version.equals("v00")) FrozenParams.applyTo(version, params);
        return params.apply(query);
    }

    /** Full resolved configuration, independent of Params.diff() and future current defaults. */
    public static Map<String, String> configuration(String spec) {
        Params params = parameters(spec);
        Map<String, String> values = new TreeMap<>();
        if (version(spec).equals("v00")) return values; // This bot has no tunable fields.
        try {
            for (var field : Params.class.getFields()) {
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
            case "current": return EvalBot::new;
            case "main": return p -> new MainBot();
            case "v00": return p -> new AutoplaceBot();
            case "v01": return GreedyBotV01::new;
            case "v02": return EvalBotV02::new;
            case "v03": return EvalBotV03::new;
            case "v04": return EvalBotV04::new;
            default: return null;
        }
    }
}
