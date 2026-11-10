package org.jtools.framework.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class PageWatchTest {

    private static final Duration GRACE = Duration.ofMillis(150);

    @Test
    void waitsForAFirstPage() throws Exception {
        PageWatch watch = new PageWatch(GRACE);
        CountDownLatch stopped = new CountDownLatch(1);
        watch.watch(stopped::countDown);
        assertFalse(stopped.await(GRACE.toMillis() * 3, TimeUnit.MILLISECONDS));
    }

    @Test
    void stopsOnceTheLastPageHasGone() throws Exception {
        PageWatch watch = new PageWatch(GRACE);
        CountDownLatch stopped = new CountDownLatch(1);
        watch.watch(stopped::countDown);
        watch.opened("a");
        watch.opened("b");
        watch.closed("a");
        assertFalse(stopped.await(GRACE.toMillis() * 3, TimeUnit.MILLISECONDS), "a page is still open");
        watch.closed("b");
        assertTrue(stopped.await(2, TimeUnit.SECONDS));
    }

    @Test
    void aPageBackWithinTheGraceKeepsItRunning() throws Exception {
        PageWatch watch = new PageWatch(GRACE);
        CountDownLatch stopped = new CountDownLatch(1);
        watch.watch(stopped::countDown);
        watch.opened("a");
        watch.closed("a");
        watch.opened("a");   // a reload
        assertFalse(stopped.await(GRACE.toMillis() * 3, TimeUnit.MILLISECONDS));
        assertEquals(1, watch.count());
    }

    @Test
    void neverStopsWhenDisabled() throws Exception {
        PageWatch watch = new PageWatch(GRACE);
        watch.setEnabled(false);
        CountDownLatch stopped = new CountDownLatch(1);
        watch.watch(stopped::countDown);
        watch.opened("a");
        watch.closed("a");
        assertFalse(stopped.await(GRACE.toMillis() * 3, TimeUnit.MILLISECONDS));
    }
}
