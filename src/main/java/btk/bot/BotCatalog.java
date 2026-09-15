package btk.bot;

import com.google.gson.Gson;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/** Local-only labels and pinned experiment recipes. No strategy logic and no submission dependency. */
public final class BotCatalog {
    public record Entry(String id, String title, String category, String status, String description,
                        String engineSnapshot, String recipe, String historicalEvidence) {}
    public record Identity(String spec, String displayName, String category, String status,
                           String sourceRevision, String implementationSha256, String presetSha256,
                           boolean explicitOverrides) {}
    private record Engine(String sourceRevision, Map<String, String> classes,
                          Map<String, String> parameters, String snapshotSha256, String implementationSha256) {}

    private static final Map<String, Entry> ENTRIES = readEntries();
    private static final Map<String, Engine> ENGINES = new ConcurrentHashMap<>();
    private BotCatalog() {}

    public static List<Entry> entries() { return List.copyOf(ENTRIES.values()); }

    public static Entry entry(String id) {
        Entry entry = ENTRIES.get(id);
        if (entry == null) throw new IllegalArgumentException("Unknown catalog bot: " + id);
        return entry;
    }

    public static String displayName(String spec) {
        if (spec == null || spec.isBlank()) throw new IllegalArgumentException("Empty bot spec");
        int q = spec.indexOf('?');
        String id = q < 0 ? spec : spec.substring(0, q);
        Entry entry = ENTRIES.get(id);
        if (entry == null) return spec; // External/custom labels are not local bot identities.
        return id + " - " + entry.title + (q < 0 || q + 1 == spec.length() ? "" : " [" + spec.substring(q + 1) + "]");
    }

    public static Identity describe(String spec) {
        int q = spec.indexOf('?');
        Entry entry = entry(q < 0 ? spec : spec.substring(0, q));
        Engine engine = entry.engineSnapshot == null ? null : engine(entry.engineSnapshot);
        String presetHash = engine == null ? null : digest(engine.snapshotSha256 + "\n" + entry.recipe);
        return new Identity(spec, displayName(spec), entry.category, entry.status,
                engine == null ? null : engine.sourceRevision,
                engine == null ? null : engine.implementationSha256, presetHash,
                q >= 0 && q + 1 < spec.length());
    }

    static Params experimentParameters(String id) {
        Entry entry = entry(id);
        if (!entry.category.equals("experiment")) throw new IllegalArgumentException("Not an experiment: " + id);
        Engine engine = engine(entry.engineSnapshot);
        return applySnapshot(engine.parameters, new Params()).apply(entry.recipe);
    }

    public static Bot createExperiment(String spec) {
        if (spec == null || !spec.startsWith("exp-")) throw new IllegalArgumentException("Expected a retained experiment spec");
        Params params = BotFactory.parameters(spec); // verifies the whole original dependency manifest
        return RetainedEngine.V1.create(params);
    }

    public static java.util.Set<String> parameterNames(String id) {
        return engine(entry(id).engineSnapshot).parameters.keySet();
    }

    static Params applySnapshot(Map<String, String> snapshot, Params target) {
        var fields = RetainedEngine.V1.parameterNames();
        if (!fields.equals(snapshot.keySet())) throw new IllegalStateException("Experiment snapshot must contain every Params field");
        snapshot.forEach(target::set);
        return target;
    }

    private static Engine engine(String resource) {
        return ENGINES.computeIfAbsent(resource, name -> {
            byte[] bytes = read("/btk/bot/" + name);
            var json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            if (json.get("schemaVersion").getAsInt() != 1) throw new IllegalStateException("Unsupported experiment snapshot");
            Map<String, String> classes = new TreeMap<>(), parameters = new TreeMap<>();
            json.getAsJsonObject("classes").entrySet().forEach(e -> classes.put(e.getKey(), e.getValue().getAsString()));
            json.getAsJsonObject("parameters").entrySet().forEach(e -> parameters.put(e.getKey(), e.getValue().getAsString()));
            if (!name.equals("experiment-engine-v1.json")) throw new IllegalStateException("Unknown retained engine: " + name);
            verifyImplementation(classes, RetainedEngine.V1::read);
            StringBuilder fingerprint = new StringBuilder();
            classes.forEach((key, hash) -> fingerprint.append(key).append(':').append(hash).append('\n'));
            return new Engine(json.get("sourceRevision").getAsString(), Map.copyOf(classes), Map.copyOf(parameters),
                    digest(bytes), digest(fingerprint.toString()));
        });
    }

    /** Verify the actual classpath bytes, not potentially stale/missing source files in the workspace. */
    static void verifyImplementation(Map<String, String> classes, java.util.function.Function<String, byte[]> reader) {
        if (classes.isEmpty() || !classes.containsKey("btk/bot/current/EvalBot.class")
                || !classes.containsKey("btk/bot/Params.class") || !classes.containsKey("btk/core/Rules.class")) {
            throw new IllegalStateException("Incomplete experiment implementation manifest");
        }
        classes.forEach((name, expected) -> {
            if (!expected.equals(digest(reader.apply("/" + name)))) {
                throw new IllegalStateException("Experiment implementation changed: " + name
                        + ". Restore the recorded engine/compiler or register a NEW snapshot and experiment id;"
                        + " do not silently retarget an existing experiment. Raw current?KEY=value remains exploratory.");
            }
        });
    }

    private static Map<String, Entry> readEntries() {
        var json = JsonParser.parseString(new String(read("/btk/bot/catalog.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        if (json.get("schemaVersion").getAsInt() != 1) throw new IllegalStateException("Unsupported bot catalog");
        var entries = new LinkedHashMap<String, Entry>();
        var gson = new Gson();
        for (var item : json.getAsJsonArray("entries")) {
            Entry entry = gson.fromJson(item, Entry.class);
            if (entry.id == null || entry.id.isBlank() || entry.title == null || entry.title.isBlank()
                    || entry.category == null || entry.status == null || entries.putIfAbsent(entry.id, entry) != null) {
                throw new IllegalStateException("Invalid or duplicate catalog entry");
            }
            if (entry.category.equals("experiment") && (entry.engineSnapshot == null || entry.recipe == null)) {
                throw new IllegalStateException("Experiment must identify its engine and recipe");
            }
        }
        return Collections.unmodifiableMap(entries);
    }

    private static byte[] read(String resource) {
        try (var in = BotCatalog.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Missing catalog/implementation resource: " + resource);
            return in.readAllBytes();
        } catch (IOException e) { throw new IllegalStateException("Cannot read " + resource, e); }
    }
    private static String digest(String value) { return digest(value.getBytes(StandardCharsets.UTF_8)); }
    private static String digest(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
