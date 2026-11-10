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
 *     Simulator simulator = new Simulator();
 *     WebApp.named("SensorSimulator").defaultPort(8094)
 *           .routes(server -> SimulatorApi.routes(server, simulator))
 *           .launch(args);
 * }
 * }</pre>
 */
public final class WebApp {

    private static final Logger LOG = Logger.getLogger(WebApp.class.getName());
    private static final Catalogue LAUNCHER_TEXTS = I18n.catalogue(WebApp.class, "org.jtools.framework.launcher.texts", Locale.ENGLISH, Locale.FRENCH);

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

    private record ExtraOption(String valueName, String help, Consumer<String> action) {}

    /** A command line that cannot be run: what is wrong with it, in the user's language. */
    static final class UsageException extends Exception {
        UsageException(String message) { super(message); }
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

    /** The port when neither the command line, the Settings menu nor the .ini names one (doc/PORTS.md keeps the programs apart). */
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

    /** The program's own fields of {@code /api/about}: XsdViewer's {@code dialogs}. */
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
            fail(console.t("error.ini", id + INI_EXTENSION, e.getMessage()), EXIT_USAGE);
            return;
        }
        try {
            options = options(args, ini, UserSettings.of(id).port());
        } catch (UsageException e) {
            fail(e.getMessage() + System.lineSeparator() + usage(console), EXIT_USAGE);
            return;
        }
        console = console(options);
        if (options.help()) {
            System.out.println(usage(console));
            return;
        }
        Logging.level(options.logLevel());
        Logging.file(id, options.logFile(), options.logFolder());
        if (operands != null) operands.accept(options.operands());

        WebServer server;
        try {
            server = start(options, program);
        } catch (BindException e) {
            fail(console.t("error.portInUse", options.port(), name, "http://" + options.host() + ":" + options.port() + "/"), EXIT_CANNOT_START);
            return;
        } catch (IOException e) {
            fail(console.t("error.cannotStart", name, e.getMessage()), EXIT_CANNOT_START);
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
     * Starts the server: the application's routes and the framework's, the automatic stop armed unless the run or the
     * user said otherwise. What {@link #launch} does once the command line is read; the tests call it directly.
     */
    WebServer start(LaunchOptions options, Class<?> program) throws IOException {
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
        I18n say = LAUNCHER_TEXTS.of(Locale.getDefault());
        int port = defaultPort;
        String iniPort = ini.getProperty(INI_PORT);
        if (iniPort != null && !iniPort.isBlank()) port = port(say, iniPort.trim(), id + INI_EXTENSION + " " + INI_PORT);
        if (userPort.isPresent()) port = userPort.getAsInt();
        String host = ini.getProperty(INI_HOST, DEFAULT_HOST).trim();
        String iniLevel = ini.getProperty(INI_LOG_LEVEL);
        Level logLevel = iniLevel == null || iniLevel.isBlank() ? Level.INFO : level(say, iniLevel.trim(), id + INI_EXTENSION + " " + INI_LOG_LEVEL);
        String logFolder = ini.getProperty(INI_LOG_FOLDER);
        boolean browser = true, keepAlive = false, help = false;
        Path logFile = null;
        Locale lang = null;
        List<String> rest = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case OPT_PORT -> port = port(say, value(say, args, ++i, arg), arg);
                case OPT_HOST -> host = value(say, args, ++i, arg);
                case OPT_NO_BROWSER -> { browser = false; keepAlive = true; }
                case OPT_KEEP_ALIVE, OPT_KEEP_RUNNING -> keepAlive = true;
                case OPT_LOG_LEVEL -> logLevel = level(say, value(say, args, ++i, arg), arg);
                case OPT_VERBOSE -> logLevel = Level.FINE;
                case OPT_LOG_FILE -> logFile = Path.of(value(say, args, ++i, arg));
                case OPT_LANG -> lang = language(say, value(say, args, ++i, arg), arg);
                case OPT_HELP, OPT_HELP_SHORT -> help = true;
                default -> {
                    ExtraOption extra = extraOptions.get(arg);
                    if (extra != null) extra.action().accept(extra.valueName() == null ? null : value(say, args, ++i, arg));
                    else if (arg.startsWith("-") || operands == null) throw new UsageException(say.t("error.unknownOption", arg));
                    else rest.add(arg);
                }
            }
        }
        if (oneFile && !help) {
            if (rest.size() > 1) throw new UsageException(say.t("error.oneOperand", operandsUsage));
            if (rest.size() == 1 && !Files.isRegularFile(Path.of(rest.get(0)))) throw new UsageException(say.t("error.notAFile", rest.get(0)));
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

    private static String value(I18n say, String[] args, int i, String option) throws UsageException {
        if (i >= args.length) throw new UsageException(say.t("error.valueExpected", option));
        return args[i];
    }

    private static int port(I18n say, String text, String what) throws UsageException {
        try {
            int port = Integer.parseInt(text);
            if (port < 0 || port > PORT_MAX) throw new NumberFormatException();
            return port;
        } catch (NumberFormatException e) {
            throw new UsageException(say.t("error.notAPort", what, text));
        }
    }

    private static Level level(I18n say, String text, String what) throws UsageException {
        try {
            return Level.parse(text.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new UsageException(say.t("error.notALevel", what, text));
        }
    }

    private static Locale language(I18n say, String text, String what) throws UsageException {
        for (Locale language : LAUNCHER_TEXTS.languages()) if (language.getLanguage().equalsIgnoreCase(text)) return language;
        throw new UsageException(say.t("error.badLanguage", what + " " + text, languages()));
    }

    private void fail(String message, int code) {
        LOG.severe(message);
        if (System.console() == null && !GraphicsEnvironment.isHeadless()) {
            try {
                JOptionPane.showMessageDialog(null, message, name, JOptionPane.ERROR_MESSAGE);
            } catch (Exception noDialog) { /* the console said it */ }
        }
        exit.accept(code);
    }
}
