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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * The program's log: {@code java.util.logging}, configured once at start. The level applies to the logger
 * {@code org.jtools} — the framework's classes and the program's alike, whichever module they are in. The log goes to
 * the console and to a file: the one given, else {@code ~/.<id>/logs/<id>.0.log}, rotated by the JDK (two files of
 * 5 MB). Its lines are in English, whatever the user's language: a log is read by whoever helps, and searched.
 */
final class Logging {

    private static final Logger LOG = Logger.getLogger(Logging.class.getName());
    /** The parent of every org.jtools logger, held so that the level set on it is not garbage-collected away. */
    private static final Logger JTOOLS = Logger.getLogger("org.jtools");
    private static final String APP_LOGGING = "/logging.properties", DEFAULT_LOGGING = "default-logging.properties",
            LOGGING_CONFIG_FILE = "java.util.logging.config.file", LOGGING_CONFIG_CLASS = "java.util.logging.config.class";
    private static final int FILE_LIMIT = 5 * 1024 * 1024, FILE_COUNT = 2;
    /** What {@code log.folder} says for "the console only". */
    static final String NO_FILE = "none";

    private static Path file;
    private static Handler fileHandler;

    private Logging() {}

    /** The program's logging.properties, else the framework's — unless the JVM was given its own. */
    static void configure() {
        if (System.getProperty(LOGGING_CONFIG_FILE) != null || System.getProperty(LOGGING_CONFIG_CLASS) != null) return;
        InputStream config = Logging.class.getResourceAsStream(APP_LOGGING);
        if (config == null) config = Logging.class.getResourceAsStream(DEFAULT_LOGGING);
        try (InputStream in = config) {
            if (in != null) LogManager.getLogManager().readConfiguration(in);
        } catch (IOException e) {
            System.err.println(APP_LOGGING + ": " + e.getMessage());
        }
    }

    static void level(Level level) {
        JTOOLS.setLevel(level);
    }

    /**
     * The log file: the one given ({@code --log-file}), else {@code <folder>/<id>.%g.log} — the folder of the
     * {@code .ini}'s {@code log.folder}, else {@code ~/.<id>/logs} — or none when the folder is {@link #NO_FILE}.
     * A file that cannot be opened leaves the log on the console, with a warning.
     */
    static void file(String programId, Path given, String folder) {
        if (given == null && NO_FILE.equalsIgnoreCase(folder)) return;
        try {
            Handler handler;
            if (given != null) {
                if (given.getParent() != null) Files.createDirectories(given.getParent());
                handler = new FileHandler(given.toString(), true);
                file = given.toAbsolutePath();
            } else {
                Path dir = folder != null && !folder.isBlank() ? Path.of(folder) : Path.of(System.getProperty("user.home"), "." + programId, "logs");
                Files.createDirectories(dir);
                handler = new FileHandler(dir.resolve(programId + ".%g.log").toString(), FILE_LIMIT, FILE_COUNT, true);
                file = dir.resolve(programId + ".0.log").toAbsolutePath();
            }
            handler.setFormatter(new LogFormatter());
            handler.setLevel(Level.ALL);
            Logger.getLogger("").addHandler(handler);
            fileHandler = handler;
        } catch (IOException | RuntimeException e) {
            LOG.warning("no log file, the console only: " + e.getMessage());
            file = null;
        }
    }

    /**
     * A record for the log file alone, not the console: a failure the console says in the person's language
     * ({@code WebApp.fail}) is kept in English there. Nothing when there is no log file (yet).
     */
    static void toFileOnly(Level level, String loggerName, String message) {
        Handler handler = fileHandler;
        if (handler == null) return;
        LogRecord record = new LogRecord(level, message);
        record.setLoggerName(loggerName);
        handler.publish(record);
        handler.flush();
    }

    /** The log file of this run; null when there is none. */
    static Path file() {
        return file;
    }
}
