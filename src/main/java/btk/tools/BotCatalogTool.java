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
            System.out.println("Historical evidence is not a new benchmark of the catalog snapshot. Experiments are not promoted.");
            return;
        }
        String spec = args[0];
        var params = BotFactory.configuration(spec); // Fail closed on stale engine / invalid parameters.
        var identity = BotCatalog.describe(spec);
        var entry = BotCatalog.entry(spec.split("\\?", 2)[0]);
        System.out.println(identity.displayName() + " [" + identity.category() + " / " + identity.status() + "]");
        System.out.println(entry.description());
        if (entry.recipe() != null) {
            System.out.println("Recipe: " + entry.recipe());
            System.out.println("Historical evidence (not remeasured here): " + entry.historicalEvidence());
            System.out.println("Engine source revision: " + identity.sourceRevision());
            System.out.println("Verified classpath SHA-256: " + identity.implementationSha256());
            System.out.println("Pinned preset SHA-256: " + identity.presetSha256());
        }
        System.out.println("Explicit query overrides: " + identity.explicitOverrides());
        params.forEach((key, value) -> System.out.println("  " + key + "=" + value));
    }
}
