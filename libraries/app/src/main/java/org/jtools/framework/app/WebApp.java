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

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.net.BindException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JOptionPane;

import org.jtools.framework.server.Exchange;
import org.jtools.framework.server.PageWatch;
import org.jtools.framework.server.WebServer;
import org.jtools.framework.support.Catalogue;
import org.jtools.framework.support.Texts;
import org.jtools.framework.support.Installation;
import org.jtools.framework.support.Json;

/**
 * A local web application's program: the browser is its window. It reads the command line, the user's settings and
 * the installation's {@code <id>.ini}, sets up the log, starts a {@link WebServer} with the application's endpoints and
 * its own ({@code /api/settings}, {@code /api/about}, {@code /api/quit}, {@code /api/log/open}), opens the page, and
 * stops once the last page has been closed ({@link PageWatch}) — unless {@code --keep-alive}, {@code --no-browser}, or
 * the user switched the automatic stop off. A startup failure is said in the user's language, on the console and in a
 * dialog when there is no console to read it (the {@code .exe}); the exit code says which: 2 for a command line that
 * cannot be run, 3 for a program that cannot start.
 * <pre>{@code
 * public static void main(String[] args) {
 *     Bookshelf shelf = new Bookshelf();
 *     WebApp.named("Bookshelf").defaultPort(8090)
 *           .endpoints(server -> BookshelfApi.endpoints(server, shelf))
 *           .launch(args);
 * }
 * }</pre>
 */
public final class WebApp {

    private static final Logger LOG = Logger.getLogger(WebApp.class.getName());

    /** The framework's own endpoints. */
    public static final String SETTINGS = WebServer.API + "/settings", ABOUT = WebServer.API + "/about", QUIT = WebServer.API + "/quit",
            LOG_OPEN = WebServer.API + "/log/open";
    private static final String J_AUTO_STOP = "autoStop", J_PORT = "port", J_DEFAULT_PORT = "defaultPort", J_CURRENT_PORT = "currentPort",
            J_NAME = "name", J_VERSION = "version", J_JAVA_VERSION = "javaVersion", J_LANGUAGE = "language", J_LOG_FILE = "logFile",
            J_OK = "ok", J_FILE = "file";
    private static final int EXIT_USAGE = 2, EXIT_CANNOT_START = 3, QUIT_WAIT_SECONDS = 1;

    private final String name;
    private String id;
    private int defaultPort;
    private String pageRoot = WebServer.DEFAULT_PAGE_ROOT;
    private Duration grace = PageWatch.GRACE;
    private Catalogue catalogue;
    private Consumer<WebServer> endpoints = server -> {};
    private Runnable onStop = () -> {};
    private SettingsFields settingsFields;
    private Consumer<Map<String, Object>> aboutFields = about -> {};
    private final CommandLine commandLine = new CommandLine();
    private IntConsumer exit = System::exit;
    private Locale language = Locale.getDefault();

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

    /** The program's texts and languages: the pages' languages ({@code Exchange.texts()}), and the help of its options when they are keys of it. */
    public WebApp catalogue(Catalogue programCatalogue) {
        this.catalogue = programCatalogue;
        return this;
    }

    /** The application's API: registers its endpoints, filters and refusals on the server before it starts. */
    public WebApp endpoints(Consumer<WebServer> endpoints) {
        this.endpoints = endpoints;
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
        commandLine.option(option, valueName, help, action);
        return this;
    }

    /** An option of the program's own taking no value: {@code --offline}. */
    public WebApp flag(String option, String help, Runnable action) {
        commandLine.flag(option, help, action);
        return this;
    }

    /** What the command line holds besides the options, handed over before the server starts; without this or {@link #fileOperand}, any is refused. */
    public WebApp operands(String usage, String help, Consumer<List<String>> action) {
        commandLine.operands(usage, help, action);
        return this;
    }

    /** One optional file to open, refused when it is not a regular file. */
    public WebApp fileOperand(String usage, String help, Consumer<Path> action) {
        commandLine.fileOperand(usage, help, action);
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
     * read, so from {@link #endpoints} on.
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
        Texts console = AppTexts.CATALOGUE.of(Locale.getDefault());
        Properties ini;
        LaunchOptions options;
        try {
            ini = Installation.ini(Installation.home(program).resolve(id + LaunchOptions.INI_EXTENSION));
        } catch (IOException e) {
            fail(console, EXIT_USAGE, "error.ini", id + LaunchOptions.INI_EXTENSION, e.getMessage());
            return;
        }
        try {
            options = options(args, ini, UserSettings.of(id).port());
        } catch (CommandLine.UsageException e) {
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
        commandLine.handOver(options.operands());

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
                + (server.pages().autoStops() ? "it stops when its last page is closed (or Ctrl+C)" : "Ctrl+C to stop")
                + (Logging.file() == null ? "" : "; log: " + Logging.file()));
        if (options.browser() && !DesktopOpener.browse(url)) LOG.info("Open " + url + " in your browser.");
    }

    /**
     * The program's server for its tests, started as {@link #launch} would start it: the arguments read (a usage error
     * is an {@link IllegalArgumentException}), the operands handed over, the Settings menu's port, the endpoints, the
     * automatic stop. And nothing of a process: <b>no {@code .ini} is read</b> — a stray one in the repository must not
     * change a test run —, no log is set up, no browser opened, nothing ever exits (the stop and {@code /api/quit} close
     * the server only). {@code start("--port", "0", "--keep-alive", "data/books.csv")}; close the server after.
     */
    public WebServer start(String... args) throws IOException {
        Class<?> program = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
        LaunchOptions options;
        try {
            options = options(args, new Properties(), UserSettings.of(id).port());
        } catch (CommandLine.UsageException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
        language = options.lang() != null ? options.lang() : Locale.getDefault();
        commandLine.handOver(options.operands());
        exit = code -> {};
        return startServer(options, program);
    }

    /**
     * Starts the server: the application's endpoints and the framework's, the automatic stop armed unless the run or the
     * user said otherwise. What {@link #launch} and {@link #start(String...)} do once the command line is read.
     */
    WebServer startServer(LaunchOptions options, Class<?> program) throws IOException {
        UserSettings settings = UserSettings.of(id);
        WebServer server = new WebServer(pageRoot, new PageWatch(grace));
        if (catalogue != null) server.catalogue(catalogue);
        builtInEndpoints(server, settings, program);
        endpoints.accept(server);   // after the framework's: an endpoint of the program's own replaces one of them (logged)
        server.start(options.host(), options.port());
        server.pages().setAutoStop(!options.keepAlive() && settings.autoStop());
        server.pages().onLastPageGone(() -> {
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

    private void builtInEndpoints(WebServer server, UserSettings settings, Class<?> program) {
        server.get(SETTINGS, ex -> settings(server, settings))
              .post(SETTINGS, ex -> {
                  Map<String, Object> asked = ex.jsonObject();
                  Texts say = AppTexts.CATALOGUE.of(ex.language());
                  if (asked.containsKey(J_AUTO_STOP) && !(asked.get(J_AUTO_STOP) instanceof Boolean)) throw new WebServer.Refusal(400, say.t("settings.autoStop"));
                  if (asked.containsKey(J_PORT) && !(asked.get(J_PORT) instanceof Number n && isPortOrUnset(n))) throw new WebServer.Refusal(400, say.t("settings.port"));
                  if (settingsFields != null) settingsFields.write(asked, ex);   // the program's own refusals before anything is kept
                  if (asked.get(J_AUTO_STOP) instanceof Boolean on) {
                      server.pages().setAutoStop(on);
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
        answer.put(J_AUTO_STOP, server.pages().autoStops());
        answer.put(J_PORT, settings.port().orElse(UserSettings.PORT_UNSET));
        answer.put(J_DEFAULT_PORT, defaultPort);
        answer.put(J_CURRENT_PORT, server.port());
        if (settingsFields != null) settingsFields.read(answer);
        return answer;
    }

    private static boolean isPortOrUnset(Number n) {
        double d = n.doubleValue();
        return d == Math.rint(d) && d >= UserSettings.PORT_UNSET && d <= CommandLine.PORT_MAX;
    }

    private static Object openLog(Exchange ex) throws IOException {
        Texts say = AppTexts.CATALOGUE.of(ex.language());
        if (!ex.fromThisComputer()) throw new WebServer.Refusal(403, say.t("log.notHere"));
        Path file = Logging.file();
        if (file == null || !Files.isRegularFile(file)) throw new WebServer.Refusal(409, say.t("log.none"));
        DesktopOpener.open(file);
        return Map.of(J_OK, true, J_FILE, file.toString());
    }

    // ---- the command line -----------------------------------------------------------------------------------

    /** The options of a run, read by its {@link CommandLine} with this program's defaults. */
    LaunchOptions options(String[] args, Properties ini, OptionalInt userPort) throws CommandLine.UsageException {
        return commandLine.read(args, ini, userPort, program());
    }

    /** The help --help prints, in the console's language. */
    String usage(Texts say) {
        return commandLine.usage(say, program());
    }

    private CommandLine.Program program() {
        return new CommandLine.Program(name, id, defaultPort, catalogue);
    }

    /** The console's texts: in the language --lang named, else the machine's. */
    private Texts console(LaunchOptions options) {
        return AppTexts.CATALOGUE.of(options.lang() != null ? options.lang() : Locale.getDefault());
    }

    /** A failure said with one of jtools-app's texts: English in the log, the console's language to the person. */
    private void fail(Texts console, int code, String key, Object... args) {
        fail(AppTexts.ENGLISH.t(key, args), console.t(key, args), code);
    }

    /**
     * A command line or a start that cannot be run: the English text in the log file, when there is one yet; the
     * person's on the console and, when there is no console to read it (the .exe), in a dialog; then the exit.
     */
    private void fail(String logged, String shown, int code) {
        Logging.toFileOnly(Level.SEVERE, WebApp.class.getName(), logged);
        System.err.println(shown);
        // no terminal to read it — an .exe, a double-click —: a console that is not a terminal counts as none (a JDK's
        // System.console() may answer one even with its output redirected)
        java.io.Console console = System.console();
        if ((console == null || !console.isTerminal()) && !GraphicsEnvironment.isHeadless()) {
            try {
                JOptionPane.showMessageDialog(null, shown, name, JOptionPane.ERROR_MESSAGE);
            } catch (Exception noDialog) { /* the console said it */ }
        }
        exit.accept(code);
    }
}
