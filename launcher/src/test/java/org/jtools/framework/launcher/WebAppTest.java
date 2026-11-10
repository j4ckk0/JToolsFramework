package org.jtools.framework.launcher;

/*-
 * #%L
 * JToolsFramework launcher
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;

class WebAppTest {

    private final WebApp app = WebApp.named("Demo").defaultPort(8099);

    @Test
    void runsOnTheDefaults() throws Exception {
        LaunchOptions options = app.options(new String[0], new Properties());
        assertEquals(8099, options.port());
        assertEquals(LaunchOptions.DEFAULT_HOST, options.host());
        assertTrue(options.browser());
        assertFalse(options.keepAlive());
        assertEquals(Level.INFO, options.logLevel());
    }

    @Test
    void theCommandLineComesBeforeTheIni() throws Exception {
        Properties ini = new Properties();
        ini.setProperty(LaunchOptions.INI_PORT, "9000");
        ini.setProperty(LaunchOptions.INI_HOST, "0.0.0.0");
        assertEquals(9000, app.options(new String[0], ini).port());
        assertEquals("0.0.0.0", app.options(new String[0], ini).host());
        LaunchOptions options = app.options(new String[] {"--port", "9100", "--keep-alive", "--log-level", "fine"}, ini);
        assertEquals(9100, options.port());
        assertTrue(options.keepAlive());
        assertEquals(Level.FINE, options.logLevel());
    }

    @Test
    void withoutABrowserItKeepsRunning() throws Exception {
        LaunchOptions options = app.options(new String[] {"--no-browser"}, new Properties());
        assertFalse(options.browser());
        assertTrue(options.keepAlive(), "a page opened by hand later must find the server");
        assertTrue(app.options(new String[] {"--keep-running"}, new Properties()).keepAlive(), "the older name still works");
    }

    @Test
    void takesTheProgramsOwnOptionsAndOperands() throws Exception {
        List<String> seen = new ArrayList<>();
        WebApp withOwn = WebApp.named("Demo")
                .option("--data", "DIR", "where the data is kept", dir -> seen.add("data=" + dir))
                .flag("--offline", "no network", () -> seen.add("offline"))
                .operands("[file ...]", "the files to open", files -> {});
        LaunchOptions options = withOwn.options(new String[] {"--data", "/tmp/d", "a.xml", "--offline", "b.xml"}, new Properties());
        assertEquals(List.of("data=/tmp/d", "offline"), seen);
        assertEquals(List.of("a.xml", "b.xml"), options.operands());
        String usage = withOwn.usage();
        assertTrue(usage.contains("[--data DIR]") && usage.contains("[file ...]") && usage.contains("where the data is kept"), usage);
    }

    @Test
    void refusesWhatItCannotRun() {
        Properties none = new Properties();
        assertThrows(WebApp.UsageException.class, () -> app.options(new String[] {"--unknown"}, none));
        assertThrows(WebApp.UsageException.class, () -> app.options(new String[] {"file.xml"}, none), "no operands declared");
        assertThrows(WebApp.UsageException.class, () -> app.options(new String[] {"--port"}, none));
        assertThrows(WebApp.UsageException.class, () -> app.options(new String[] {"--port", "http"}, none));
        assertThrows(WebApp.UsageException.class, () -> app.options(new String[] {"--log-level", "loud"}, none));
        Properties badIni = new Properties();
        badIni.setProperty(LaunchOptions.INI_PORT, "eighty");
        assertThrows(WebApp.UsageException.class, () -> app.options(new String[0], badIni));
    }

    @Test
    void itsIdIsItsNameInLowerCase() {
        assertTrue(app.usage().startsWith("Usage: demo "), app.usage());
        assertTrue(WebApp.named("Demo").id("demo-tool").usage().startsWith("Usage: demo-tool "));
    }
}
