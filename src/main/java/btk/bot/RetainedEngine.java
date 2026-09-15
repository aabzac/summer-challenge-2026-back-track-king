package btk.bot;

import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;
import java.util.TreeSet;

/** Local-only: exactly two archived classes, sharing the verified live Bot/core/Timer ABI. */
final class RetainedEngine extends ClassLoader {
    static final RetainedEngine V1 = new RetainedEngine();
    private final Map<String, byte[]> bytes = new TreeMap<>();

    private RetainedEngine() {
        super(Bot.class.getClassLoader());
        try (var in = getParent().getResourceAsStream("btk/bot/experiment-engine-v1-classes.json")) {
            if (in == null) throw new IllegalStateException("Missing retained engine classes");
            JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject()
                    .entrySet().forEach(e -> bytes.put(e.getKey(), Base64.getDecoder().decode(e.getValue().getAsString())));
            if (!bytes.keySet().equals(Set.of("btk/bot/Params.class", "btk/bot/current/EvalBot.class"))) {
                throw new IllegalStateException("Unexpected retained class set");
            }
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }

    @Override protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        byte[] code = bytes.get(name.replace('.', '/') + ".class");
        if (code == null) return super.loadClass(name, resolve);
        Class<?> type = findLoadedClass(name);
        if (type == null) type = defineClass(name, code, 0, code.length);
        if (resolve) resolveClass(type);
        return type;
    }

    @Override public InputStream getResourceAsStream(String name) {
        byte[] code = bytes.get(name);
        return code == null ? super.getResourceAsStream(name) : new ByteArrayInputStream(code);
    }

    byte[] read(String name) {
        try (var in = getResourceAsStream(name.startsWith("/") ? name.substring(1) : name)) {
            if (in == null) throw new IllegalStateException("Missing retained dependency: " + name);
            return in.readAllBytes();
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }

    Set<String> parameterNames() {
        try {
            var names = new TreeSet<String>();
            for (var f : loadClass("btk.bot.Params").getFields()) {
                if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) names.add(f.getName());
            }
            return names;
        } catch (ClassNotFoundException e) { throw new IllegalStateException(e); }
    }

    Bot create(Params params) {
        try {
            Class<?> type = loadClass("btk.bot.Params");
            Object archived = type.getConstructor().newInstance();
            for (String key : parameterNames()) type.getField(key).set(archived, Params.class.getField(key).get(params));
            return (Bot) loadClass("btk.bot.current.EvalBot").getConstructor(type).newInstance(archived);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Cannot instantiate retained engine", e); }
    }
}
