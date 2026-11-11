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

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/**
 * What a run was asked for ({@link WebApp#options}): the command line over the Settings menu's port over the
 * installation's {@code .ini} over the program's defaults. {@code keepAlive} is true with {@code --keep-alive}, and with
 * {@code --no-browser}: a page opened later, by hand, must not find the server gone. {@code logFile} is the one given,
 * or null; {@code logFolder} the {@code .ini}'s, or null; {@code lang} the console's language, or null for the machine's.
 * Public for its names — the options ({@code OPT_*}) and the {@code .ini}'s keys ({@code INI_*}) —, the command line's
 * vocabulary; a program is handed none of it, {@link WebApp} reads it.
 */
public record LaunchOptions(int port, String host, boolean browser, boolean keepAlive, Level logLevel, Path logFile,
                            String logFolder, Locale lang, boolean help, List<String> operands) {

    public static final String OPT_PORT = "--port", OPT_HOST = "--host", OPT_NO_BROWSER = "--no-browser",
            OPT_KEEP_ALIVE = "--keep-alive", OPT_LOG_LEVEL = "--log-level", OPT_VERBOSE = "--verbose", OPT_LOG_FILE = "--log-file",
            OPT_LANG = "--lang", OPT_HELP = "--help", OPT_HELP_SHORT = "-h";
    /** The name MythoStory and SensorSimulator first gave to --keep-alive, still understood. */
    public static final String OPT_KEEP_RUNNING = "--keep-running";
    /** The installation's settings a run reads from the {@code .ini}. */
    public static final String INI_PORT = "port", INI_HOST = "host", INI_LOG_LEVEL = "log.level", INI_LOG_FOLDER = "log.folder";
    /** This computer only: the page and its API are nobody else's. */
    public static final String DEFAULT_HOST = "127.0.0.1";
}
