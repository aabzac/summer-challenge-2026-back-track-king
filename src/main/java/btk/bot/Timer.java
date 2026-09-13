package btk.bot;

/** Wall-clock turn budget. */
public final class Timer {

    private long startNs;
    private long deadlineNs;

    public void start(long budgetMs) {
        startNs = System.nanoTime();
        deadlineNs = startNs + budgetMs * 1_000_000L;
    }

    public boolean up() {
        return System.nanoTime() >= deadlineNs;
    }

    /** Earlier phase deadline without restarting/extending the overall turn clock. */
    public boolean up(long reserveMs) {
        return System.nanoTime() >= deadlineNs - Math.max(0, reserveMs) * 1_000_000L;
    }

    public long elapsedMs() {
        return (System.nanoTime() - startNs) / 1_000_000L;
    }

    public long leftMs() {
        return (deadlineNs - System.nanoTime()) / 1_000_000L;
    }
}
