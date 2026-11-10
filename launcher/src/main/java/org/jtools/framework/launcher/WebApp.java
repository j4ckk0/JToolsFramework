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

import static org.jtools.framework.launcher.LaunchOptions.*;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.net.BindException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import javax.swing.JOptionPane;

import org.jtools.framework.server.DesktopOpener;
import org.jtools.framework.server.PageWatch;
import org.jtools.framework.server.WebServer;
import org.jtools.framework.support.Installation;

/**
 * A local web application's program: the browser is its window. It reads the command line and the installation's
 * {@code <id>.ini}, configures the logging, starts a {@link WebServer} with the application's routes, opens the page,
 * and stops once the last page has been closed ({@link PageWatch}) — unless {@code --keep-alive} or {@code --no-browser}.
 * A startup failure is said on the console, and in a dialog when there is no console to read it (the {@code .exe}).
 * <pre>{@code
 * public static void main(String[] args) {
 *     Simulator simulator = new Simulator();
 *     WebApp.named("SensorSimulator").defaultPort(8094)
 *           .routes(server -> SimulatorApi.routes(server, simulator))
 *           .launch(args);
 * }
 * }</pre>
 */
public final class WebApp {

    private static final Logger LOG = Logger.getLogger(WebApp.class.getName());
    /** The parent of every org.jtools logger, the framework's and the program's: what --log-level sets, held so that it is not garbage-collected away. */
    private static final Logger JTOOLS_LOG = Logger.getLogger("org.jtools");
    /** The program's own logging configuration on the classpath, else the framework's. */
    private static final String APP_LOGGING = "/logging.properties", DEFAULT_LOGGING = "default-logging.properties",
            LOGGING_CONFIG_FILE = "java.util.logging.config.file", LOGGING_CONFIG_CLASS = "java.util.logging.config.class";
    private static final String INI_EXTENSION = ".ini";
    private static final int EXIT_USAGE = 2, PORT_MAX = 65535;

    private final String name;
    private String id;
    private int defaultPort;
    private String pageRoot = WebServer.DEFAULT_PAGE_ROOT;
    private Duration grace = PageWatch.GRACE;
    private Consumer<WebServer> routes = server -> {};
    private Runnable onStop = () -> {};
    /** The program's own options, by name: the value they take (null for a flag), their help, what they do. */
    private final Map<String, ExtraOption> extraOptions = new LinkedHashMap<>();
    private String operandsUsage;
    private String operandsHelp;
    private Consumer<List<String>> operands;

    private record ExtraOption(String valueName, String help, Consumer<String> action) {}

    /** A command line that cannot be run: the message, and the usage after it. */
    static final class UsageException extends Exception {
        UsageException(String message) { super(message); }
    }

    private WebApp(String name) {
        this.name = name;
        this.id = name.toLowerCase(Locale.ROOT);
    }

    /** The program, by the name people read; its id — the jar's, the .ini's — is that name in lower case unless {@link #id} says another. */
    public static WebApp named(String name) {
        return new WebApp(name);
    }

    public WebApp id(String id) {
        this.id = id;
        return this;
    }

    /** The port when neither the command line nor the .ini names one (doc/PORTS.md in JToolsFramework keeps the programs apart). */
    public WebApp defaultPort(int port) {
        this.defaultPort = port;
        return this;
    }

    /** The classpath folder of the page: static/ (the frontend module's jar) unless said otherwise. */
    public WebApp pageRoot(String classpathFolder) {
        this.pageRoot = classpathFolder;
        return this;
    }

    /** How long the server waits with no page open before it stops. */
    public WebApp grace(Duration grace) {
        this.grace = grace;
        return this;
    }

    /** The application's API: registers its routes on the server before it starts. */
    public WebApp routes(Consumer<WebServer> routes) {
        this.routes = routes;
        return this;
    }

    /** What the program closes as it stops, whatever stops it: the last page gone, Ctrl+C, a SIGTERM. */
    public WebApp onStop(Runnable onStop) {
        this.onStop = onStop;
        return this;
    }

    /** An option of the program's own taking a value: {@code --data DIR}. */
    public WebApp option(String option, String valueName, String help, Consumer<String> action) {
        extraOptions.put(option, new ExtraOption(valueName, help, action));
        return this;
    }

    /** An option of the program's own taking no value: {@code --offline}. */
    public WebApp flag(String option, String help, Runnable action) {
        extraOptions.put(option, new ExtraOption(null, help, value -> action.run()));
        return this;
    }

    /** What the command line holds besides the options — files to open — handed over before the server starts; without this, any is refused. */
    public WebApp operands(String usage, String help, Consumer<List<String>> action) {
        this.operandsUsage = usage;
        this.operandsHelp = help;
        this.operands = action;
        return this;
    }

    // ---- the run --------------------------------------------------------------------------------------------

    /** Runs the program; on a command line or a start it cannot run, says why and exits with 2. */
    public void launch(String[] args) {
        // the program's own class: its jar is the one whose folder holds the .ini and whose manifest says the version
        Class<?> program = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
        configureLogging();
        LaunchOptions options;
        try {
            options = options(args, Installation.ini(Installation.home(program).resolve(id + INI_EXTENSION)));
        } catch (UsageException e) {
            fail(e.getMessage() + System.lineSeparator() + usage());
            return;
        } catch (IOException e) {
            fail(id + INI_EXTENSION + ": " + e.getMessage());
            return;
        }
        if (options.help()) {
            System.out.println(usage());
            return;
        }
        JTOOLS_LOG.setLevel(options.logLevel());
        if (operands != null) operands.accept(options.operands());

        WebServer server = new WebServer(pageRoot, new PageWatch(grace));
        routes.accept(server);
        try {
            server.start(options.host(), options.port());
        } catch (BindException e) {
            fail("Port " + options.port() + " is in use: is " + name + " already running? Open http://" + options.host() + ":" + options.port()
                    + "/ or choose another port with " + OPT_PORT + " N.");
            return;
        } catch (IOException e) {
            fail(name + " cannot start: " + e.getMessage());
            return;
        }
        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().unstarted(() -> {
            server.close();
            onStop.run();
        }));
        server.pages().setEnabled(!options.keepAlive());
        server.pages().watch(() -> {
            LOG.info("The last page has been closed for " + grace.toSeconds() + " s: " + name + " stops.");
            System.exit(0);
        });

        String url = server.url();
        LOG.info(name + " " + Installation.version(program) + " is ready on " + url + "; "
                + (options.keepAlive() ? "Ctrl+C to stop" : "it stops when its last page is closed (or Ctrl+C)"));
        if (options.browser() && !DesktopOpener.browse(url)) LOG.info("Open " + url + " in your browser.");
    }

    /** The options of a run: the command line's first, then the .ini's port and host, then the defaults. The program's own options run as they are read. */
    LaunchOptions options(String[] args, Properties ini) throws UsageException {
        int port = defaultPort;
        String iniPort = ini.getProperty(INI_PORT);
        if (iniPort != null && !iniPort.isBlank()) port = port(iniPort.trim(), id + INI_EXTENSION + " " + INI_PORT);
        String host = ini.getProperty(INI_HOST, DEFAULT_HOST).trim();
        boolean browser = true, keepAlive = false, help = false;
        Level logLevel = Level.INFO;
        List<String> rest = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case OPT_PORT -> port = port(value(args, ++i, arg), arg);
                case OPT_HOST -> host = value(args, ++i, arg);
                case OPT_NO_BROWSER -> { browser = false; keepAlive = true; }
                case OPT_KEEP_ALIVE, OPT_KEEP_RUNNING -> keepAlive = true;
                case OPT_LOG_LEVEL -> logLevel = level(value(args, ++i, arg), arg);
                case OPT_HELP, OPT_HELP_SHORT -> help = true;
                default -> {
                    ExtraOption extra = extraOptions.get(arg);
                    if (extra != null) extra.action().accept(extra.valueName() == null ? null : value(args, ++i, arg));
                    else if (arg.startsWith("-") || operands == null) throw new UsageException("unknown argument: " + arg);
                    else rest.add(arg);
                }
            }
        }
        return new LaunchOptions(port, host, browser, keepAlive, logLevel, help, List.copyOf(rest));
    }

    /** The help --help prints: the framework's options, then the program's own. */
    String usage() {
        StringBuilder usage = new StringBuilder("Usage: ").append(id)
                .append(" [--port N] [--host H] [--no-browser] [--keep-alive] [--log-level LEVEL]");
        extraOptions.forEach((option, extra) -> usage.append(" [").append(option).append(extra.valueName() == null ? "" : " " + extra.valueName()).append(']'));
        if (operands != null) usage.append(' ').append(operandsUsage);
        usage.append(System.lineSeparator());
        line(usage, OPT_PORT + " N", "the port to serve on (default: " + id + INI_EXTENSION + "'s port, else " + defaultPort + "; 0 for any free one)");
        line(usage, OPT_HOST + " H", "the interface to listen on (default " + DEFAULT_HOST + ": this computer only)");
        line(usage, OPT_NO_BROWSER, "do not open the browser, and keep running: open the page by hand");
        line(usage, OPT_KEEP_ALIVE, "keep running once the last page is closed (by default, " + name + " then stops)");
        line(usage, OPT_LOG_LEVEL + " LEVEL", "SEVERE, WARNING, INFO (the default), FINE (every request), ...");
        extraOptions.forEach((option, extra) -> line(usage, option + (extra.valueName() == null ? "" : " " + extra.valueName()), extra.help()));
        if (operands != null) line(usage, operandsUsage, operandsHelp);
        return usage.toString().stripTrailing();
    }

    private static void line(StringBuilder usage, String option, String help) {
        usage.append(String.format("  %-20s %s%n", option, help));
    }

    private static String value(String[] args, int i, String option) throws UsageException {
        if (i >= args.length) throw new UsageException(option + " takes a value");
        return args[i];
    }

    private static int port(String text, String what) throws UsageException {
        try {
            int port = Integer.parseInt(text);
            if (port < 0 || port > PORT_MAX) throw new NumberFormatException();
            return port;
        } catch (NumberFormatException e) {
            throw new UsageException(what + ": not a port: " + text);
        }
    }

    private static Level level(String text, String what) throws UsageException {
        try {
            return Level.parse(text.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new UsageException(what + ": not a level: " + text);
        }
    }

    /** The program's logging.properties, else the framework's — unless the JVM was given its own. */
    private static void configureLogging() {
        if (System.getProperty(LOGGING_CONFIG_FILE) != null || System.getProperty(LOGGING_CONFIG_CLASS) != null) return;
        InputStream config = WebApp.class.getResourceAsStream(APP_LOGGING);
        if (config == null) config = WebApp.class.getResourceAsStream(DEFAULT_LOGGING);
        try (InputStream in = config) {
            if (in != null) LogManager.getLogManager().readConfiguration(in);
        } catch (IOException e) {
            System.err.println(APP_LOGGING + ": " + e.getMessage());
        }
    }

    private void fail(String message) {
        System.err.println(message);
        if (System.console() == null && !GraphicsEnvironment.isHeadless()) {
            try {
                JOptionPane.showMessageDialog(null, message, name, JOptionPane.ERROR_MESSAGE);
            } catch (Exception noDialog) { /* the console said it */ }
        }
        System.exit(EXIT_USAGE);
    }
}
