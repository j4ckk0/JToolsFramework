package org.jtools.framework.server;

import java.awt.Desktop;
import java.net.URI;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/** The page in the browser, as a double-click would open it. */
public final class DesktopOpener {

    private static final Logger LOG = Logger.getLogger(DesktopOpener.class.getName());
    private static final String OPEN_LINUX = "xdg-open", OS_LINUX = "linux", OS_NAME = "os.name";

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
            if (System.getProperty(OS_NAME, "").toLowerCase(Locale.ROOT).contains(OS_LINUX)) {
                new ProcessBuilder(OPEN_LINUX, url).start();
                return true;
            }
        } catch (Exception e) { LOG.log(Level.FINE, "xdg-open failed", e); }
        return false;
    }
}
