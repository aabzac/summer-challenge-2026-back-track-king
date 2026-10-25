package btk.bot.current;

/** Reflection helpers for RolloutBot mechanism tests (the historical ones moved to btk.bot.v10 by prune-v1). */
final class RolloutTestSupport {
    private RolloutTestSupport() {}

    static Object field(RolloutBot bot, String name) throws Exception {
        var f = RolloutBot.class.getDeclaredField(name); f.setAccessible(true); return f.get(bot);
    }

    static Object call(RolloutBot bot, String name, Class<?>[] types, Object... args) throws Exception {
        var m = RolloutBot.class.getDeclaredMethod(name, types); m.setAccessible(true); return m.invoke(bot, args);
    }

    static void pool(RolloutBot bot, int player, int[]... paths) throws Exception {
        int[][][] routes = (int[][][]) field(bot, "routes");
        int[][] lengths = (int[][]) field(bot, "lengths");
        ((int[]) field(bot, "counts"))[player] = paths.length;
        for (int i = 0; i < paths.length; i++) {
            System.arraycopy(paths[i], 0, routes[player][i], 0, paths[i].length);
            lengths[player][i] = paths[i].length;
        }
    }
}
