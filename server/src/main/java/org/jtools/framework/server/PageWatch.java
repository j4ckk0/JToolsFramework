package org.jtools.framework.server;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * The pages open on the server, and the automatic stop once the last one has gone: each holds a
 * {@code GET /api/alive?id=} stream and sends {@code POST /api/bye?id=} as it closes ({@link WebServer}); none left for
 * the grace (a reload, a laptop waking up) runs the stop. Nothing before a first page is seen — a server started
 * without a browser waits as long as it takes —, nothing while {@link #setEnabled disabled}; the check is re-verified
 * when it fires.
 */
public final class PageWatch {

    /** How long the server waits, with no page open, before stopping. */
    public static final Duration GRACE = Duration.ofSeconds(15);

    /** Open streams per page id: the browser reconnects a dropped stream with the same id, and the stale stream's late close must not discard the page. */
    private final ConcurrentHashMap<String, Integer> pages = new ConcurrentHashMap<>();
    private final Duration grace;
    private volatile boolean seenOnce;
    private volatile boolean enabled = true;
    private volatile Instant emptySince;
    private ScheduledExecutorService scheduler;
    private Runnable stop;
    private ScheduledFuture<?> pending;

    public PageWatch() {
        this(GRACE);
    }

    /** A watch with its own grace (the tests: a short one). */
    public PageWatch(Duration grace) {
        this.grace = grace;
    }

    public Duration grace() {
        return grace;
    }

    void opened(String pageId) {
        pages.merge(pageId, 1, Integer::sum);
        seenOnce = true;
        emptySince = null;
    }

    void closed(String pageId) {
        pages.computeIfPresent(pageId, (id, streams) -> streams > 1 ? streams - 1 : null);
        arm();
    }

    /** How many pages are open. */
    public int count() {
        return pages.size();
    }

    /** Whether the automatic stop is armed; off, pages are still counted but nothing ever stops. */
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) arm(); else emptySince = null;
    }

    /** Keeps the stop; it runs once, from the check's thread, when the grace has elapsed with no page. */
    public synchronized void watch(Runnable stop) {
        if (this.stop != null) return;
        this.stop = stop;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> Thread.ofPlatform().daemon().name(getClass().getSimpleName()).unstarted(r));
        arm();
    }

    /** Schedules the check when no page is left; a page back within the grace clears {@code emptySince}, which disarms it. */
    private synchronized void arm() {
        if (stop == null || scheduler.isShutdown() || !enabled || !seenOnce || !pages.isEmpty()) return;
        if (emptySince == null) emptySince = Instant.now();
        if (pending == null || pending.isDone()) pending = scheduler.schedule(this::check, grace.toMillis(), TimeUnit.MILLISECONDS);
    }

    private synchronized void check() {
        Instant since = emptySince;
        if (!enabled || !seenOnce || !pages.isEmpty() || since == null) return;
        Duration left = grace.minus(Duration.between(since, Instant.now()));
        if (left.isPositive()) {
            // the pages left again after coming back while this check was pending: wait out the new grace
            pending = scheduler.schedule(this::check, left.toMillis() + 1, TimeUnit.MILLISECONDS);
        } else {
            scheduler.shutdown();
            stop.run();
        }
    }
}
