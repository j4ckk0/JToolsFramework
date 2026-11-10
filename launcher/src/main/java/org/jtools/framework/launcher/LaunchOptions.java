package org.jtools.framework.launcher;

import java.util.List;
import java.util.logging.Level;

/**
 * What a run was asked for, the command line over the installation's {@code .ini} over the program's defaults
 * ({@link WebApp#options}). {@code keepAlive} is true with {@code --keep-alive}, and with {@code --no-browser}: a
 * page opened later, by hand, must not find the server gone.
 */
public record LaunchOptions(int port, String host, boolean browser, boolean keepAlive, Level logLevel, boolean help, List<String> operands) {

    public static final String OPT_PORT = "--port", OPT_HOST = "--host", OPT_NO_BROWSER = "--no-browser",
            OPT_KEEP_ALIVE = "--keep-alive", OPT_LOG_LEVEL = "--log-level", OPT_HELP = "--help", OPT_HELP_SHORT = "-h";
    /** The name MythoStory and SensorSimulator first gave to --keep-alive, still understood. */
    public static final String OPT_KEEP_RUNNING = "--keep-running";
    /** The installation's settings a run reads from the {@code .ini}. */
    public static final String INI_PORT = "port", INI_HOST = "host";
    /** This computer only: the page and its API are nobody else's. */
    public static final String DEFAULT_HOST = "127.0.0.1";
}
