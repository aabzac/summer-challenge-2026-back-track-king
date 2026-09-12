package btk.bot;

import btk.bot.current.EvalBot;
import btk.bot.v00.AutoplaceBot;
import btk.bot.v01.GreedyBotV01;
import btk.bot.v02.EvalBotV02;
import btk.bot.v03.EvalBotV03;

import java.util.function.Function;

/**
 * Creates bots by spec string: {@code "<version>"} or {@code "<version>?KEY=v&KEY2=v"}.
 * Versions: {@code current} (working copy), {@code main} (what ships), {@code vNN} (frozen).
 */
public final class BotFactory {

    private BotFactory() {}

    public static Bot create(String spec) {
        String version = spec, query = "";
        int q = spec.indexOf('?');
        if (q >= 0) {
            version = spec.substring(0, q);
            query = spec.substring(q + 1);
        }
        Params params = new Params(query);
        Function<Params, Bot> ctor = lookup(version);
        if (ctor == null) throw new IllegalArgumentException("unknown bot: " + version);
        return ctor.apply(params);
    }

    private static Function<Params, Bot> lookup(String version) {
        switch (version) {
            case "current": return EvalBot::new;
            case "main": return p -> new MainBot();
            case "v00": return p -> new AutoplaceBot();
            case "v01": return GreedyBotV01::new;
            case "v02": return EvalBotV02::new;
            case "v03": return EvalBotV03::new;
            default: return null;
        }
    }
}
