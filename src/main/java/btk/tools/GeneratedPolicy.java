package btk.tools;

import btk.core.GameState;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tools-only bridge to an already compiled submission, isolated from repository bot classes.
 * One instance owns one persistent generated MainBot. The measured interval is reflective
 * MainBot.play only: state conversion, generated connection refresh, formatting and diagnostic
 * copying occur outside it. This is an in-process search measurement, not a protocol deadline.
 */
public final class GeneratedPolicy implements AutoCloseable {
    public record Decision(String action, double elapsedMs, Map<String, Object> diagnostics) {}

    private static final String[] ARRAY_FIELDS = {
            "owner", "instability", "inked", "regionInstability", "regionInked", "score"
    };
    private final URLClassLoader loader;
    private final Constructor<?> stateConstructor, pathConstructor;
    private final Method initMethod, warmupMethod, playMethod, formatMethod, updateMethod;
    private final Field turnField, maxTurnsField;
    private final Field[] stateArrays;
    private final Object bot, delegate;
    private final List<Field> diagnosticFields;
    private final Map<String, Method> diagnosticMethods;
    private boolean initialized, closed;

    /** Expects the exact artifact at submissionDir/Player.java and submissionDir/classes. */
    public GeneratedPolicy(Path submissionDir) throws Exception {
        Path classes = submissionDir.resolve("classes");
        if (!Files.isRegularFile(submissionDir.resolve("Player.java"))
                || !Files.isRegularFile(classes.resolve("Player.class"))) {
            throw new IOException("Missing precompiled submission: " + submissionDir);
        }
        loader = new URLClassLoader(new URL[]{classes.toUri().toURL()},
                ClassLoader.getPlatformClassLoader());
        try {
            Class<?> stateClass = loader.loadClass("Player$GameState");
            stateConstructor = stateClass.getDeclaredConstructor(int.class, int.class,
                    int[].class, int[].class, int[].class, int[].class, int[][].class);
            stateConstructor.setAccessible(true);
            Class<?> pathClass = loader.loadClass("Player$Pathing");
            pathConstructor = pathClass.getDeclaredConstructor(int.class);
            pathConstructor.setAccessible(true);
            updateMethod = method(loader.loadClass("Player$Rules"), "updateConnections", stateClass, pathClass);
            turnField = field(stateClass, "turn");
            maxTurnsField = field(stateClass, "maxTurns");
            stateArrays = new Field[ARRAY_FIELDS.length];
            for (int i = 0; i < stateArrays.length; i++) stateArrays[i] = field(stateClass, ARRAY_FIELDS[i]);

            Class<?> mainClass = loader.loadClass("Player$MainBot");
            Constructor<?> mainConstructor = mainClass.getDeclaredConstructor();
            mainConstructor.setAccessible(true);
            bot = mainConstructor.newInstance();
            initMethod = method(mainClass, "init", stateClass, int.class);
            warmupMethod = method(mainClass, "warmup", stateClass, int.class, long.class);
            playMethod = method(mainClass, "play", stateClass, int.class, long.class);
            formatMethod = method(loader.loadClass("Player$Action"), "format", List.class);
            Field delegateField = mainClass.getDeclaredField("delegate");
            delegateField.setAccessible(true);
            delegate = delegateField.get(bot);
            diagnosticFields = new ArrayList<>();
            Field[] fields = delegate.getClass().getFields();
            Arrays.sort(fields, java.util.Comparator.comparing(Field::getName));
            for (Field f : fields) {
                Class<?> type = f.getType();
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (type.isPrimitive() || type == String.class
                        || type.isArray() && type.getComponentType().isPrimitive()) {
                    f.setAccessible(true);
                    diagnosticFields.add(f);
                }
            }
            diagnosticMethods = new LinkedHashMap<>();
            for (String name : new String[]{"deadlinePendingTarget", "deadlinePendingDue"}) {
                try {
                    diagnosticMethods.put(name, method(delegate.getClass(), name));
                } catch (NoSuchMethodException ignored) {
                    // The disabled export deliberately excludes the experimental fields.
                }
            }
        } catch (Exception | Error failure) {
            try { loader.close(); } catch (IOException closeFailure) { failure.addSuppressed(closeFailure); }
            throw failure;
        }
    }

    public void init(GameState state, int me) throws Exception {
        ensureOpen();
        checkSeat(me);
        initialized = false;
        invoke(initMethod, bot, new Object[]{prepareState(state), me});
        initialized = true;
    }

    public void warmup(GameState state, int me, long budgetMs) throws Exception {
        ensureInitialized();
        checkSeat(me);
        invoke(warmupMethod, bot, new Object[]{prepareState(state), me, budgetMs});
    }

    public Decision play(GameState state, int me, long budgetMs) throws Exception {
        ensureInitialized();
        checkSeat(me);
        Object[] arguments = {prepareState(state), me, budgetMs};
        long start = System.nanoTime();
        Object actions = invoke(playMethod, bot, arguments);
        double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;
        String action = (String) invoke(formatMethod, null, new Object[]{actions});
        return new Decision(action, elapsedMs, diagnostics());
    }

    /** All arrays, including static map arrays, are isolated from the caller. */
    private Object prepareState(GameState state) throws Exception {
        int[][] desired = new int[state.desired.length][];
        for (int i = 0; i < desired.length; i++) desired[i] = state.desired[i].clone();
        Object generated = stateConstructor.newInstance(new Object[]{state.width, state.height,
                state.regionId.clone(), state.terrain.clone(), state.townX.clone(), state.townY.clone(), desired});
        Object[] arrays = {state.owner, state.instability, state.inked,
                state.regionInstability, state.regionInked, state.score};
        for (int i = 0; i < stateArrays.length; i++) {
            Object target = stateArrays[i].get(generated);
            System.arraycopy(arrays[i], 0, target, 0, Array.getLength(arrays[i]));
        }
        turnField.setInt(generated, state.turn);
        maxTurnsField.setInt(generated, state.maxTurns);
        invoke(updateMethod, null, new Object[]{generated, pathConstructor.newInstance(state.size)});
        return generated;
    }

    private Map<String, Object> diagnostics() throws Exception {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (Field f : diagnosticFields) {
            Object value = f.get(delegate);
            if (value instanceof Double number) {
                if (!Double.isFinite(number)) value = null;
            } else if (value instanceof Float number) {
                if (!Float.isFinite(number)) value = null;
            } else if (value instanceof double[] values) {
                Double[] copy = new Double[values.length];
                for (int i = 0; i < values.length; i++) copy[i] = Double.isFinite(values[i]) ? values[i] : null;
                value = copy;
            } else if (value instanceof float[] values) {
                Float[] copy = new Float[values.length];
                for (int i = 0; i < values.length; i++) copy[i] = Float.isFinite(values[i]) ? values[i] : null;
                value = copy;
            } else if (value != null && value.getClass().isArray()) {
                int length = Array.getLength(value);
                Object copy = Array.newInstance(value.getClass().getComponentType(), length);
                System.arraycopy(value, 0, copy, 0, length);
                value = copy;
            }
            snapshot.put(f.getName(), value);
        }
        for (var entry : diagnosticMethods.entrySet()) {
            snapshot.put(entry.getKey(), invoke(entry.getValue(), delegate, new Object[0]));
        }
        return Collections.unmodifiableMap(snapshot);
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Field result = type.getField(name);
        result.setAccessible(true);
        return result;
    }

    private static Method method(Class<?> type, String name, Class<?>... arguments) throws NoSuchMethodException {
        Method result = type.getMethod(name, arguments);
        result.setAccessible(true);
        return result;
    }

    private static Object invoke(Method method, Object receiver, Object[] arguments) throws Exception {
        try {
            return method.invoke(receiver, arguments);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception exception) throw exception;
            if (cause instanceof Error error) throw error;
            throw failure;
        }
    }

    private static void checkSeat(int me) {
        if (me != 0 && me != 1) throw new IllegalArgumentException("Invalid player: " + me);
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Generated policy is closed");
    }

    private void ensureInitialized() {
        ensureOpen();
        if (!initialized) throw new IllegalStateException("Initialize the generated policy first");
    }

    @Override public void close() throws IOException {
        if (!closed) {
            closed = true;
            loader.close();
        }
    }
}
