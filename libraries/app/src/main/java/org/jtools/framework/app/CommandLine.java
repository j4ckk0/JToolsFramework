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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.jtools.framework.support.Catalogue;
import org.jtools.framework.support.Texts;

/**
 * The command line of a {@link WebApp}: the framework's options ({@link LaunchOptions}), the program's own and its
 * operands, read into a {@link LaunchOptions} over the {@code .ini} and the Settings menu's port, and the help that
 * {@code --help} prints.
 */
final class CommandLine {

    static final int PORT_MAX = 65535;

    /** What the command line says of the program: its texts and the defaults they name. */
    record Program(String name, String id, int defaultPort, Catalogue catalogue) {}

    private record ExtraOption(String valueName, String help, Consumer<String> action) {}

    /**
     * A command line that cannot be run, kept as a text key and its arguments so that it is said twice: in English as its
     * message, what the log keeps, and in the person's language ({@link #in}), what they read.
     */
    static final class UsageException extends Exception {
        private final String key;
        private final transient Object[] args;

        UsageException(String key, Object... args) {
            super(AppTexts.ENGLISH.t(key, args));
            this.key = key;
            this.args = args;
        }

        String in(Texts say) {
            return say.t(key, args);
        }
    }

    /** The program's own options, by name: the value they take (null for a flag), their help, what they do. */
    private final Map<String, ExtraOption> extraOptions = new LinkedHashMap<>();
    private String operandsUsage, operandsHelp;
    private Consumer<List<String>> operands;
    private boolean oneFile;

    void option(String option, String valueName, String help, Consumer<String> action) {
        extraOptions.put(option, new ExtraOption(valueName, help, action));
    }

    void flag(String option, String help, Runnable action) {
        extraOptions.put(option, new ExtraOption(null, help, value -> action.run()));
    }

    void operands(String usage, String help, Consumer<List<String>> action) {
        operandsUsage = usage;
        operandsHelp = help;
        operands = action;
        oneFile = false;
    }

    void fileOperand(String usage, String help, Consumer<Path> action) {
        operands(usage, help, files -> { if (!files.isEmpty()) action.accept(Path.of(files.get(0))); });
        oneFile = true;
    }

    /** Hands the run's operands to the program, when it takes any. */
    void handOver(List<String> runOperands) {
        if (operands != null) operands.accept(runOperands);
    }

    /**
     * The options of a run: the command line's first, then the user's port (the Settings menu), then the .ini's port,
     * host and log, then the defaults. The program's own options run as they are read.
     */
    LaunchOptions read(String[] args, Properties ini, OptionalInt userPort, Program program) throws UsageException {
        String iniName = program.id() + INI_EXTENSION;
        int port = program.defaultPort();
        String iniPort = ini.getProperty(INI_PORT);
        if (iniPort != null && !iniPort.isBlank()) port = port(iniPort.trim(), iniName + " " + INI_PORT);
        if (userPort.isPresent()) port = userPort.getAsInt();
        String host = ini.getProperty(INI_HOST, DEFAULT_HOST).trim();
        String iniLevel = ini.getProperty(INI_LOG_LEVEL);
        Level logLevel = iniLevel == null || iniLevel.isBlank() ? Level.INFO : level(iniLevel.trim(), iniName + " " + INI_LOG_LEVEL);
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

    /** The help --help prints, in the console's language: the framework's options, then the program's own. */
    String usage(Texts say, Program program) {
        String id = program.id();
        StringBuilder line = new StringBuilder("[--port N] [--host H] [--no-browser] [--keep-alive] [--log-level LEVEL] [--log-file FILE] [--lang LANG]");
        extraOptions.forEach((option, extra) -> line.append(" [").append(option).append(extra.valueName() == null ? "" : " " + extra.valueName()).append(']'));
        if (operands != null) line.append(' ').append(operandsUsage);
        StringBuilder usage = new StringBuilder(say.t("usage", id, line)).append(System.lineSeparator());
        line(usage, OPT_PORT + " N", say.t("option.port", id + INI_EXTENSION, program.defaultPort()));
        line(usage, OPT_HOST + " H", say.t("option.host", DEFAULT_HOST));
        line(usage, OPT_NO_BROWSER, say.t("option.noBrowser"));
        line(usage, OPT_KEEP_ALIVE, say.t("option.keepAlive", program.name()));
        line(usage, OPT_LOG_LEVEL + " LEVEL", say.t("option.logLevel"));
        line(usage, OPT_VERBOSE, say.t("option.verbose"));
        line(usage, OPT_LOG_FILE + " FILE", say.t("option.logFile", id));
        line(usage, OPT_LANG + " LANG", say.t("option.lang", languages()));
        extraOptions.forEach((option, extra) -> line(usage, option + (extra.valueName() == null ? "" : " " + extra.valueName()), programText(say, extra.help(), program)));
        if (operands != null) line(usage, operandsUsage, programText(say, operandsHelp, program));
        line(usage, OPT_HELP, say.t("option.help"));
        return usage.toString().stripTrailing();
    }

    /** The help of a program's option: a key of its texts, in the console's language, or the text as it was given. */
    private static String programText(Texts say, String help, Program program) {
        if (program.catalogue() == null) return help;
        Texts programSay = program.catalogue().of(say.locale());
        return programSay.has(help) ? programSay.t(help) : help;
    }

    private static String languages() {
        return AppTexts.CATALOGUE.languages().stream().map(Locale::getLanguage).collect(Collectors.joining(", "));
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
        for (Locale language : AppTexts.CATALOGUE.languages()) if (language.getLanguage().equalsIgnoreCase(text)) return language;
        throw new UsageException("error.badLanguage", what + " " + text, languages());
    }
}
