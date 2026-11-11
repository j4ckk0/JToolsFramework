package org.jtools.framework.app;

/*-
 * #%L
 * JToolsFramework app
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

import static org.jtools.framework.app.LaunchOptions.*;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.net.BindException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.swing.JOptionPane;

import org.jtools.framework.server.DesktopOpener;
import org.jtools.framework.server.Exchange;
import org.jtools.framework.server.PageWatch;
import org.jtools.framework.server.WebServer;
import org.jtools.framework.support.Catalogue;
import org.jtools.framework.support.I18n;
import org.jtools.framework.support.Installation;
import org.jtools.framework.support.Json;

/**
 * A local web application's program: the browser is its window. It reads the command line, the user's settings and
 * the installation's {@code <id>.ini}, sets up the log, starts a {@link WebServer} with the application's routes and
 * its own ({@code /api/settings}, {@code /api/about}, {@code /api/quit}, {@code /api/log/open}), opens the page, and
 * stops once the last page has been closed ({@link PageWatch}) — unless {@code --keep-alive}, {@code --no-browser}, or
 * the user switched the automatic stop off. A startup failure is said in the user's language, on the console and in a
 * dialog when there is no console to read it (the {@code .exe}); the exit code says which: 2 for a command line that
 * cannot be run, 3 for a program that cannot start.
 * <pre>{@code
 * public static void main(String[] args) {
 *     SensorMonitor monitor = new SensorMonitor();
 *     WebApp.named("SensorMonitor").defaultPort(8090)
 *           .routes(server -> SensorMonitorApi.routes(server, monitor))
 *           .launch(args);
 * }
 * }</pre>
 */
public final class WebApp {

    private static final Logger LOG = Logger.getLogger(WebApp.class.getName());
    private static final Catalogue LAUNCHER_TEXTS = I18n.catalogue(WebApp.class, "org.jtools.framework.app.texts", Locale.ENGLISH, Locale.FRENCH);
    /** What the log says of a failure: English, whoever runs the program (a log is read by whoever helps, and searched). */
    private static final I18n ENGLISH = LAUNCHER_TEXTS.of(Locale.ENGLISH);

    /** The framework's own routes. */
    public static final String SETTINGS = WebServer.API + "/settings", ABOUT = WebServer.API + "/about", QUIT = WebServer.API + "/quit",
            LOG_OPEN = WebServer.API + "/log/open";
    private static final String J_AUTO_STOP = "autoStop", J_PORT = "port", J_DEFAULT_PORT = "defaultPort", J_CURRENT_PORT = "currentPort",
            J_NAME = "name", J_VERSION = "version", J_JAVA_VERSION = "javaVersion", J_LANGUAGE = "language", J_LOG_FILE = "logFile",
            J_OK = "ok", J_FILE = "file";
    private static final String INI_EXTENSION = ".ini";
    private static final int EXIT_USAGE = 2, EXIT_CANNOT_START = 3, PORT_MAX = 65535, QUIT_WAIT_SECONDS = 1;

    private final String name;
    private String id;
    private int defaultPort;
    private String pageRoot = WebServer.DEFAULT_PAGE_ROOT;
    private Duration grace = PageWatch.GRACE;
    private Catalogue texts;
    private Consumer<WebServer> routes = server -> {};
    private Runnable onStop = () -> {};
    private SettingsFields settingsFields;
    private Consumer<Map<String, Object>> aboutFields = about -> {};
    /** The program's own options, by name: the value they take (null for a flag), their help, what they do. */
    private final Map<String, ExtraOption> extraOptions = new LinkedHashMap<>();
    private String operandsUsage, operandsHelp;
    private Consumer<List<String>> operands;
    private boolean oneFile;
    private IntConsumer exit = System::exit;
    private Locale language = Locale.getDefault();

    private record ExtraOption(String valueName, String help, Consumer<String> action) {}

    /**
     * A command line that cannot be run: a text of the launcher's and its arguments, so that it is said twice — in English
     * as its message, what the log keeps, and in the person's language ({@link #in}), what they read.
     */
    static final class UsageException extends Exception {
        private final String key;
        private final transient Object[] args;

        UsageException(String key, Object... args) {
            super(ENGLISH.t(key, args));
            this.key = key;
            this.args = args;
        }

        String in(I18n say) {
            return say.t(key, args);
        }
    }

    private WebApp(String name) {
        this.name = name;
        this.id = name.toLowerCase(Locale.ROOT);
    }

    /** The program, by the name people read; its id — the jar's, the .ini's, its folder {@code ~/.<id>} — is that name in lower case unless {@link #id} says another. */
    public static WebApp named(String name) {
        return new WebApp(name);
    }

    public WebApp id(String id) {
        this.id = id;
        return this;
    }

    /** The port when neither the command line, the Settings menu nor the .ini names one: one of the program's own, so that it runs beside others. */
    public WebApp defaultPort(int port) {
        this.defaultPort = port;
        return this;
    }

    /** The classpath folder of the page: static/ (the frontend module's jar) unless said otherwise (web/). */
    public WebApp pageRoot(String classpathFolder) {
        this.pageRoot = classpathFolder;
        return this;
    }

    /** How long the server waits with no page open before it stops. */
    public WebApp grace(Duration grace) {
        this.grace = grace;
        return this;
    }

    /** The program's texts and languages: the pages' languages ({@code Exchange.i18n()}), and the help of its options when they are keys of it. */
    public WebApp texts(Catalogue programTexts) {
        this.texts = programTexts;
        return this;
    }

    /** The application's API: registers its routes, filters and refusals on the server before it starts. */
    public WebApp routes(Consumer<WebServer> routes) {
        this.routes = routes;
        return this;
    }

    /** What the program closes as it stops, whatever stops it: the last page gone, Quit, Ctrl+C, a SIGTERM. */
    public WebApp onStop(Runnable onStop) {
        this.onStop = onStop;
        return this;
    }

    /** The program's own fields of {@code /api/settings}. */
    public WebApp settingsFields(SettingsFields fields) {
        this.settingsFields = fields;
        return this;
    }

    /** The program's own fields of {@code /api/about}: whether it can show native file dialogs, say. */
    public WebApp aboutFields(Consumer<Map<String, Object>> fields) {
        this.aboutFields = fields;
        return this;
    }

    /** An option of the program's own taking a value: {@code --data DIR}. Its help: a key of the program's texts, or the text itself. */
    public WebApp option(String option, String valueName, String help, Consumer<String> action) {
        extraOptions.put(option, new ExtraOption(valueName, help, action));
        return this;
    }

    /** An option of the program's own taking no value: {@code --offline}. */
    public WebApp flag(String option, String help, Runnable action) {
        extraOptions.put(option, new ExtraOption(null, help, value -> action.run()));
        return this;
    }

    /** What the command line holds besides the options, handed over before the server starts; without this or {@link #fileOperand}, any is refused. */
    public WebApp operands(String usage, String help, Consumer<List<String>> action) {
        this.operandsUsage = usage;
        this.operandsHelp = help;
        this.operands = action;
        this.oneFile = false;
        return this;
    }

    /** One optional file to open, refused when it is not a regular file — the policy, the schema the program starts on. */
    public WebApp fileOperand(String usage, String help, Consumer<Path> action) {
        operands(usage, help, files -> { if (!files.isEmpty()) action.accept(Path.of(files.get(0))); });
        this.oneFile = true;
        return this;
    }

    /** Tests: what the program does in place of exiting. */
    WebApp exitWith(IntConsumer exit) {
        this.exit = exit;
        return this;
    }

    /**
     * The language this run speaks where no page asks for one — its console, and what the program says to a client
     * that names none (a tool endpoint other programs call): {@code --lang}'s, else the machine's. Known once the command line is
     * read, so from {@link #routes} on.
     */
    public Locale language() {
        return language;
    }

    // ---- the run --------------------------------------------------------------------------------------------

    /** Runs the program; on a command line or a start it cannot run, says why and exits (2, 3). */
    public void launch(String[] args) {
        // the program's own class: its jar is the one whose folder holds the .ini and whose manifest says the version
        Class<?> program = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
        Logging.configure();
        I18n console = LAUNCHER_TEXTS.of(Locale.getDefault());
        Properties ini;
        LaunchOptions options;
        try {
            ini = Installation.ini(Installation.home(program).resolve(id + INI_EXTENSION));
        } catch (IOException e) {
            fail(console, EXIT_USAGE, "error.ini", id + INI_EXTENSION, e.getMessage());
            return;
        }
        try {
            options = options(args, ini, UserSettings.of(id).port());
        } catch (UsageException e) {
            fail(e.getMessage(), e.in(console) + System.lineSeparator() + usage(console), EXIT_USAGE);
            return;
        }
        console = console(options);
        language = options.lang() != null ? options.lang() : Locale.getDefault();
        if (options.help()) {
            System.out.println(usage(console));
            return;
        }
        Logging.level(options.logLevel());
        Logging.file(id, options.logFile(), options.logFolder());
        if (operands != null) operands.accept(options.operands());

        WebServer server;
        try {
            server = startServer(options, program);
        } catch (BindException e) {
            fail(console, EXIT_CANNOT_START, "error.portInUse", options.port(), name, "http://" + options.host() + ":" + options.port() + "/");
            return;
        } catch (IOException e) {
            fail(console, EXIT_CANNOT_START, "error.cannotStart", name, e.getMessage());
            return;
        }
        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().unstarted(() -> {
            server.close();
            onStop.run();
        }));
        String url = server.url();
        LOG.info(name + " " + Installation.version(program) + " is ready on " + url + "; "
                + (server.pages().isEnabled() ? "it stops when its last page is closed (or Ctrl+C)" : "Ctrl+C to stop")
                + (Logging.file() == null ? "" : "; log: " + Logging.file()));
        if (options.browser() && !DesktopOpener.browse(url)) LOG.info("Open " + url + " in your browser.");
    }

    /**
     * The program's server for its tests, started as {@link #launch} would start it: the arguments read (a usage error
     * is an {@link IllegalArgumentException}), the operands handed over, the Settings menu's port, the routes, the
     * automatic stop. And nothing of a process: <b>no {@code .ini} is read</b> — a stray one in the repository must not
     * change a test run —, no log is set up, no browser opened, nothing ever exits (the stop and {@code /api/quit} close
     * the server only). {@code start("--port", "0", "--keep-alive", "samples/a.xsd")}; close the server after.
     */
    public WebServer start(String... args) throws IOException {
        Class<?> program = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
        LaunchOptions options;
        try {
            options = options(args, new Properties(), UserSettings.of(id).port());
        } catch (UsageException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
        language = options.lang() != null ? options.lang() : Locale.getDefault();
        if (operands != null) operands.accept(options.operands());
        exit = code -> {};
        return startServer(options, program);
    }

    /**
     * Starts the server: the application's routes and the framework's, the automatic stop armed unless the run or the
     * user said otherwise. What {@link #launch} and {@link #start(String...)} do once the command line is read.
     */
    WebServer startServer(LaunchOptions options, Class<?> program) throws IOException {
        UserSettings settings = UserSettings.of(id);
        WebServer server = new WebServer(pageRoot, new PageWatch(grace));
        if (texts != null) server.texts(texts);
        builtInRoutes(server, settings, program);
        routes.accept(server);   // after the framework's: a route of the program's own replaces one of them (logged)
        server.start(options.host(), options.port());
        server.pages().setEnabled(!options.keepAlive() && settings.autoStop());
        server.pages().watch(() -> {
            LOG.info("The last page has been closed for " + grace.toSeconds() + " s: " + name + " stops.");
            stopAndExit(server);
        });
        return server;
    }

    private void stopAndExit(WebServer server) {
        Thread.ofPlatform().name(name + " stop").start(() -> {
            server.stop(QUIT_WAIT_SECONDS);
            exit.accept(0);
        });
    }

    private void builtInRoutes(WebServer server, UserSettings settings, Class<?> program) {
        server.get(SETTINGS, ex -> settings(server, settings))
              .post(SETTINGS, ex -> {
                  Map<String, Object> asked = ex.jsonObject();
                  I18n say = LAUNCHER_TEXTS.of(ex.language());
                  if (asked.containsKey(J_AUTO_STOP) && !(asked.get(J_AUTO_STOP) instanceof Boolean)) throw new WebServer.Refusal(400, say.t("settings.autoStop"));
                  if (asked.containsKey(J_PORT) && !(asked.get(J_PORT) instanceof Number n && isPortOrUnset(n))) throw new WebServer.Refusal(400, say.t("settings.port"));
                  if (settingsFields != null) settingsFields.write(asked, ex);   // the program's own refusals before anything is kept
                  if (asked.get(J_AUTO_STOP) instanceof Boolean on) {
                      server.pages().setEnabled(on);
                      settings.setAutoStop(on);
                  }
                  if (asked.get(J_PORT) instanceof Number port) settings.setPort(port.intValue());
                  return settings(server, settings);
              })
              .get(ABOUT, ex -> {
                  Map<String, Object> about = Json.obj();
                  about.put(J_NAME, name);
                  about.put(J_VERSION, Installation.version(program));
                  about.put(J_JAVA_VERSION, System.getProperty("java.version"));
                  about.put(J_LANGUAGE, Locale.getDefault().getLanguage());
                  about.put(J_LOG_FILE, Logging.file() == null ? "" : Logging.file().toString());
                  aboutFields.accept(about);
                  return about;
              })
              .post(QUIT, ex -> {
                  LOG.info("Quit asked from the page: " + name + " stops.");
                  stopAndExit(server);
                  return Map.of(J_OK, true);
              })
              .post(LOG_OPEN, ex -> openLog(ex));
    }

    private Map<String, Object> settings(WebServer server, UserSettings settings) {
        Map<String, Object> answer = Json.obj();
        answer.put(J_AUTO_STOP, server.pages().isEnabled());
        answer.put(J_PORT, settings.port().orElse(UserSettings.PORT_UNSET));
        answer.put(J_DEFAULT_PORT, defaultPort);
        answer.put(J_CURRENT_PORT, server.port());
        if (settingsFields != null) settingsFields.read(answer);
        return answer;
    }

    private static boolean isPortOrUnset(Number n) {
        double d = n.doubleValue();
        return d == Math.rint(d) && d >= UserSettings.PORT_UNSET && d <= PORT_MAX;
    }

    private static Object openLog(Exchange ex) throws IOException {
        I18n say = LAUNCHER_TEXTS.of(ex.language());
        if (!ex.fromThisComputer()) throw new WebServer.Refusal(403, say.t("log.notHere"));
        Path file = Logging.file();
        if (file == null || !Files.isRegularFile(file)) throw new WebServer.Refusal(409, say.t("log.none"));
        DesktopOpener.open(file);
        return Map.of(J_OK, true, J_FILE, file.toString());
    }

    // ---- the command line -----------------------------------------------------------------------------------

    /**
     * The options of a run: the command line's first, then the user's port (the Settings menu), then the .ini's port,
     * host and log, then the defaults. The program's own options run as they are read.
     */
    LaunchOptions options(String[] args, Properties ini, OptionalInt userPort) throws UsageException {
        int port = defaultPort;
        String iniPort = ini.getProperty(INI_PORT);
        if (iniPort != null && !iniPort.isBlank()) port = port(iniPort.trim(), id + INI_EXTENSION + " " + INI_PORT);
        if (userPort.isPresent()) port = userPort.getAsInt();
        String host = ini.getProperty(INI_HOST, DEFAULT_HOST).trim();
        String iniLevel = ini.getProperty(INI_LOG_LEVEL);
        Level logLevel = iniLevel == null || iniLevel.isBlank() ? Level.INFO : level(iniLevel.trim(), id + INI_EXTENSION + " " + INI_LOG_LEVEL);
        String logFolder = ini.getProperty(INI_LOG_FOLDER);
        boolean browser = true, keepAlive = false, help = false;
        Path logFile = null;
        Locale lang = null;
        List<String> rest = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case OPT_PORT -> port = port(value(args, ++i, arg), arg);
                case OPT_HOST -> host = value(args, ++i, arg);
                case OPT_NO_BROWSER -> { browser = false; keepAlive = true; }
                case OPT_KEEP_ALIVE, OPT_KEEP_RUNNING -> keepAlive = true;
                case OPT_LOG_LEVEL -> logLevel = level(value(args, ++i, arg), arg);
                case OPT_VERBOSE -> logLevel = Level.FINE;
                case OPT_LOG_FILE -> logFile = Path.of(value(args, ++i, arg));
                case OPT_LANG -> lang = language(value(args, ++i, arg), arg);
                case OPT_HELP, OPT_HELP_SHORT -> help = true;
                default -> {
                    ExtraOption extra = extraOptions.get(arg);
                    if (extra != null) extra.action().accept(extra.valueName() == null ? null : value(args, ++i, arg));
                    else if (arg.startsWith("-") || operands == null) throw new UsageException("error.unknownOption", arg);
                    else rest.add(arg);
                }
            }
        }
        if (oneFile && !help) {
            if (rest.size() > 1) throw new UsageException("error.oneOperand", operandsUsage);
            if (rest.size() == 1 && !Files.isRegularFile(Path.of(rest.get(0)))) throw new UsageException("error.notAFile", rest.get(0));
        }
        return new LaunchOptions(port, host, browser, keepAlive, logLevel, logFile, logFolder, lang, help, List.copyOf(rest));
    }

    /** The console's texts: in the language --lang named, else the machine's. */
    private I18n console(LaunchOptions options) {
        return LAUNCHER_TEXTS.of(options.lang() != null ? options.lang() : Locale.getDefault());
    }

    /** The help --help prints, in the console's language: the framework's options, then the program's own. */
    String usage(I18n say) {
        StringBuilder line = new StringBuilder("[--port N] [--host H] [--no-browser] [--keep-alive] [--log-level LEVEL] [--log-file FILE] [--lang LANG]");
        extraOptions.forEach((option, extra) -> line.append(" [").append(option).append(extra.valueName() == null ? "" : " " + extra.valueName()).append(']'));
        if (operands != null) line.append(' ').append(operandsUsage);
        StringBuilder usage = new StringBuilder(say.t("usage", id, line)).append(System.lineSeparator());
        line(usage, OPT_PORT + " N", say.t("option.port", id + INI_EXTENSION, defaultPort));
        line(usage, OPT_HOST + " H", say.t("option.host", DEFAULT_HOST));
        line(usage, OPT_NO_BROWSER, say.t("option.noBrowser"));
        line(usage, OPT_KEEP_ALIVE, say.t("option.keepAlive", name));
        line(usage, OPT_LOG_LEVEL + " LEVEL", say.t("option.logLevel"));
        line(usage, OPT_VERBOSE, say.t("option.verbose"));
        line(usage, OPT_LOG_FILE + " FILE", say.t("option.logFile", id));
        line(usage, OPT_LANG + " LANG", say.t("option.lang", languages()));
        extraOptions.forEach((option, extra) -> line(usage, option + (extra.valueName() == null ? "" : " " + extra.valueName()), programText(say, extra.help())));
        if (operands != null) line(usage, operandsUsage, programText(say, operandsHelp));
        line(usage, OPT_HELP, say.t("option.help"));
        return usage.toString().stripTrailing();
    }

    /** The help of a program's option: a key of its texts, in the console's language, or the text as it was given. */
    private String programText(I18n say, String help) {
        if (texts == null) return help;
        I18n programSay = texts.of(say.locale());
        return programSay.has(help) ? programSay.t(help) : help;
    }

    private static String languages() {
        return LAUNCHER_TEXTS.languages().stream().map(Locale::getLanguage).collect(Collectors.joining(", "));
    }

    private static void line(StringBuilder usage, String option, String help) {
        usage.append(String.format("  %-20s %s%n", option, help));
    }

    private static String value(String[] args, int i, String option) throws UsageException {
        if (i >= args.length) throw new UsageException("error.valueExpected", option);
        return args[i];
    }

    private static int port(String text, String what) throws UsageException {
        try {
            int port = Integer.parseInt(text);
            if (port < 0 || port > PORT_MAX) throw new NumberFormatException();
            return port;
        } catch (NumberFormatException e) {
            throw new UsageException("error.notAPort", what, text);
        }
    }

    private static Level level(String text, String what) throws UsageException {
        try {
            return Level.parse(text.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new UsageException("error.notALevel", what, text);
        }
    }

    private static Locale language(String text, String what) throws UsageException {
        for (Locale language : LAUNCHER_TEXTS.languages()) if (language.getLanguage().equalsIgnoreCase(text)) return language;
        throw new UsageException("error.badLanguage", what + " " + text, languages());
    }

    /** A failure said with a text of the launcher's: English in the log, the console's language to the person. */
    private void fail(I18n console, int code, String key, Object... args) {
        fail(ENGLISH.t(key, args), console.t(key, args), code);
    }

    /**
     * A command line or a start that cannot be run: the English text in the log file, when there is one yet; the
     * person's on the console and, when there is no console to read it (the .exe), in a dialog; then the exit.
     */
    private void fail(String logged, String shown, int code) {
        Logging.toFileOnly(Level.SEVERE, WebApp.class.getName(), logged);
        System.err.println(shown);
        if (System.console() == null && !GraphicsEnvironment.isHeadless()) {
            try {
                JOptionPane.showMessageDialog(null, shown, name, JOptionPane.ERROR_MESSAGE);
            } catch (Exception noDialog) { /* the console said it */ }
        }
        exit.accept(code);
    }
}
