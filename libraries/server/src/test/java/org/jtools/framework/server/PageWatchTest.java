package org.jtools.framework.server;

/*-
 * #%L
 * JToolsFramework server
 * %%
 * Copyright (C) 2026 jtools.org
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

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
        watch.onLastPageGone(stopped::countDown);
        assertFalse(stopped.await(GRACE.toMillis() * 3, TimeUnit.MILLISECONDS));
    }

    @Test
    void stopsOnceTheLastPageHasGone() throws Exception {
        PageWatch watch = new PageWatch(GRACE);
        CountDownLatch stopped = new CountDownLatch(1);
        watch.onLastPageGone(stopped::countDown);
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
        watch.onLastPageGone(stopped::countDown);
        watch.opened("a");
        watch.closed("a");
        watch.opened("a");   // a reload
        assertFalse(stopped.await(GRACE.toMillis() * 3, TimeUnit.MILLISECONDS));
        assertEquals(1, watch.count());
    }

    @Test
    void neverStopsWhenDisabled() throws Exception {
        PageWatch watch = new PageWatch(GRACE);
        watch.setAutoStop(false);
        CountDownLatch stopped = new CountDownLatch(1);
        watch.onLastPageGone(stopped::countDown);
        watch.opened("a");
        watch.closed("a");
        assertFalse(stopped.await(GRACE.toMillis() * 3, TimeUnit.MILLISECONDS));
    }
}
