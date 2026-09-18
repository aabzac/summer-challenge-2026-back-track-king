package btk.tools;

import java.nio.file.Files;
import java.nio.file.Path;

/** Read-only compatibility for replay paths recorded before the bot directory became boss. */
public final class ReplayPaths {
    private ReplayPaths() {}

    /** Prefer an existing explicit path; only remap the two known historical replay directories. */
    public static Path resolve(Path path) {
        if (Files.exists(path)) return path;
        Path normalized = path.normalize();
        int category = categoryIndex(normalized);
        if (category < 0 || !normalized.getName(category).toString().equalsIgnoreCase("bot")) return path;
        Path renamed = normalized.getRoot() == null ? Path.of("") : normalized.getRoot();
        for (int i = 0; i < normalized.getNameCount(); i++)
            renamed = renamed.resolve(i == category ? "boss" : normalized.getName(i).toString());
        return Files.exists(renamed) ? renamed : path;
    }

    static boolean inBossTree(Path path) {
        Path normalized = path.normalize();
        int category = categoryIndex(normalized);
        return category >= 0 && normalized.getName(category).toString().equalsIgnoreCase("boss");
    }

    static boolean canonicalBossFile(Path file) {
        Path normalized = file.normalize();
        for (int i = categoryIndex(normalized) + 1; i + 1 < normalized.getNameCount(); i++) {
            String part = normalized.getName(i).toString();
            if (part.equalsIgnoreCase("raw") || part.equalsIgnoreCase("_metadata")) return false;
        }
        return file.getFileName().toString().matches("[0-9]+\\.json");
    }

    private static int categoryIndex(Path path) {
        for (int i = 0; i + 2 < path.getNameCount(); i++) {
            if (!path.getName(i).toString().equalsIgnoreCase("replays")) continue;
            if (path.getName(i + 1).toString().equalsIgnoreCase("cg")) return i + 2;
            if (i + 3 < path.getNameCount() && path.getName(i + 1).toString().equalsIgnoreCase("rendered")
                    && path.getName(i + 2).toString().equalsIgnoreCase("cg")) return i + 3;
        }
        return -1;
    }
}
