package btk.tools;

import btk.bot.BotFactory;
import btk.runner.TimingProfile;
import com.google.gson.GsonBuilder;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Read configuration and generated classes only; never initialize/play/warm up a game. */
public final class V09Ink40ArtifactCheck {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        Map<String, Object> result = new LinkedHashMap<>();
        for (String name : new String[]{"artifact", "artifact-off", "artifact36"}) {
            var manifest = com.google.gson.JsonParser.parseString(Files.readString(root.resolve(name + "/manifest.json"))).getAsJsonObject();
            String spec = manifest.get("strategy").getAsString();
            var nativeParams = BotFactory.configuration(spec);
            if (!new com.google.gson.Gson().toJsonTree(nativeParams).equals(manifest.get("params")))
                throw new IllegalStateException("Native/generated params mismatch: " + name);
            int expected = name.equals("artifact36") ? 36 : 40;
            if (TimingProfile.CG.firstSearchMs != 486 || TimingProfile.CG.turnBudget(spec) != expected)
                throw new IllegalStateException("Native budget mismatch");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("params", nativeParams);
            row.put("firstSearchMs", 486);
            row.put("turnSearchMs", expected);
            try (var loader = new URLClassLoader(new java.net.URL[]{root.resolve(name + "/classes").toUri().toURL()}, null)) {
                Class<?> main = loader.loadClass("Player$MainBot");
                var constructor = main.getDeclaredConstructor(); constructor.setAccessible(true);
                Object bot = constructor.newInstance();
                if (expected == 40) {
                    var budget = main.getDeclaredMethod("turnBudgetMs"); budget.setAccessible(true);
                    if (((Number) budget.invoke(bot)).longValue() != 40) throw new IllegalStateException("Generated budget mismatch");
                }
                var paramsField = main.getDeclaredField("params"); paramsField.setAccessible(true);
                Object params = paramsField.get(bot);
                for (String key : new String[]{"USE_BATCHED_REFRESH", "USE_INCREMENTAL_INK_REFRESH", "NOISE"}) {
                    java.lang.reflect.Field field;
                    try { field = params.getClass().getDeclaredField(key); }
                    catch (NoSuchFieldException missing) {
                        if (key.equals("USE_INCREMENTAL_INK_REFRESH") && nativeParams.getOrDefault(key, "false").equals("false")) continue;
                        throw missing;
                    }
                    field.setAccessible(true);
                    String actual = String.valueOf(field.get(params));
                    if (!actual.equals(nativeParams.getOrDefault(key, "false")))
                        throw new IllegalStateException("Generated field mismatch: " + key);
                }
                Class<?> runner = loader.loadClass("Player$CGRunner");
                for (var e : Map.of("TURN_MS", 50L, "FIRST_TURN_MS", 1000L, "MARGIN_MS", 14L, "WARMUP_MS", 500L).entrySet()) {
                    var field = runner.getDeclaredField(e.getKey()); field.setAccessible(true);
                    if (field.getLong(null) != e.getValue()) throw new IllegalStateException("Changed runner limit");
                }
            }
            result.put(name, row);
        }
        result.put("complete", true); result.put("passed", true); result.put("botCalls", 0);
        Files.writeString(root.resolve("artifact-check.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result),
                java.nio.file.StandardOpenOption.CREATE_NEW);
    }
}
