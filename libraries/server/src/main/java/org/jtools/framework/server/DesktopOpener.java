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

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/** What the desktop opens for the program, as a double-click would: the page in the browser, a file in its viewer. */
public final class DesktopOpener {

    private static final Logger LOG = Logger.getLogger(DesktopOpener.class.getName());
    private static final String OPEN_LINUX = "xdg-open", OPEN_MAC = "open", OS_LINUX = "linux", OS_MAC = "mac", OS_NAME = "os.name";

    private DesktopOpener() {}

    /** The address in the browser: the desktop's own, else xdg-open on Linux; false when neither could. */
    public static boolean browse(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return true;
            }
        } catch (Exception e) { LOG.log(Level.FINE, "Desktop.browse failed", e); }
        try {
            if (os().contains(OS_LINUX)) {
                new ProcessBuilder(OPEN_LINUX, url).start();
                return true;
            }
        } catch (Exception e) { LOG.log(Level.FINE, "xdg-open failed", e); }
        return false;
    }

    /** A file in the application its type opens with: xdg-open on Linux, open on macOS, the desktop's association elsewhere. */
    public static void open(Path file) throws IOException {
        String os = os();
        if (os.contains(OS_LINUX)) new ProcessBuilder(OPEN_LINUX, file.toString()).start();
        else if (os.contains(OS_MAC)) new ProcessBuilder(OPEN_MAC, file.toString()).start();
        else Desktop.getDesktop().open(file.toFile());
    }

    private static String os() {
        return System.getProperty(OS_NAME, "").toLowerCase(Locale.ROOT);
    }
}
