package btk.tools;

import btk.bot.BotCatalog;
import btk.bot.BotFactory;

/** Read-only catalog. Does not run games, export artifacts, or change reference selection. */
public final class BotCatalogTool {
    public static void main(String[] args) {
        if (args.length == 0 || args[0].isBlank()) {
            for (var entry : BotCatalog.entries()) {
                System.out.printf("%s [%s / %s]%n  %s%n", BotCatalog.displayName(entry.id()),
                        entry.category(), entry.status(), entry.description());
            }
            System.out.println("Details and resolved parameters: gradlew botCatalog -Pbot=<id>");
            System.out.println("Candidates and references are local-only; only the selected version ships.");
            return;
        }
        String spec = args[0];
        var params = BotFactory.configuration(spec); // Fail closed on invalid parameters.
        var identity = BotCatalog.describe(spec);
        var entry = BotCatalog.entry(spec.split("\\?", 2)[0]);
        System.out.println(identity.displayName() + " [" + identity.category() + " / " + identity.status() + "]");
        System.out.println(entry.description());
        System.out.println("Explicit query overrides: " + identity.explicitOverrides());
        params.forEach((key, value) -> System.out.println("  " + key + "=" + value));
    }
}
