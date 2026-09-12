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

    public long elapsedMs() {
        return (System.nanoTime() - startNs) / 1_000_000L;
    }

    public long leftMs() {
        return (deadlineNs - System.nanoTime()) / 1_000_000L;
    }
}
