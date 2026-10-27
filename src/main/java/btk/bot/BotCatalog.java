package btk.bot;

import com.google.gson.Gson;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Local-only labels. No strategy logic and no submission dependency. */
public final class BotCatalog {
    public record Entry(String id, String title, String category, String status, String description) {}
    public record Identity(String spec, String displayName, String category, String status, boolean explicitOverrides) {}

    private static final Map<String, Entry> ENTRIES = readEntries();
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
        return new Identity(spec, displayName(spec), entry.category, entry.status, q >= 0 && q + 1 < spec.length());
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
        }
        return Collections.unmodifiableMap(entries);
    }

    private static byte[] read(String resource) {
        try (var in = BotCatalog.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Missing catalog resource: " + resource);
            return in.readAllBytes();
        } catch (IOException e) { throw new IllegalStateException("Cannot read " + resource, e); }
    }
}
