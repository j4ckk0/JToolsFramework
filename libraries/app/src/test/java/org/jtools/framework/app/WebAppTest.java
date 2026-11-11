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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.Properties;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The command line, the Settings menu's port and the .ini, in their order; the help. */
class WebAppTest {

    private static final OptionalInt NO_USER_PORT = OptionalInt.empty();

    private final WebApp app = WebApp.named("Demo").defaultPort(8099);

    private static Properties ini(String... pairs) {
        Properties ini = new Properties();
        for (int i = 0; i < pairs.length; i += 2) ini.setProperty(pairs[i], pairs[i + 1]);
        return ini;
    }

    @Test
    void runsOnTheDefaults() throws Exception {
        LaunchOptions options = app.options(new String[0], new Properties(), NO_USER_PORT);
        assertEquals(8099, options.port());
        assertEquals(LaunchOptions.DEFAULT_HOST, options.host());
        assertTrue(options.browser());
        assertFalse(options.keepAlive());
        assertEquals(Level.INFO, options.logLevel());
        assertNull(options.logFile());
        assertNull(options.lang());
    }

    @Test
    void thePortsOrderIsTheCommandLineTheSettingsMenuTheIniTheDefault() throws Exception {
        Properties ini = ini(LaunchOptions.INI_PORT, "9000", LaunchOptions.INI_HOST, "0.0.0.0");
        assertEquals(9000, app.options(new String[0], ini, NO_USER_PORT).port());
        assertEquals(9500, app.options(new String[0], ini, OptionalInt.of(9500)).port(), "the Settings menu over the .ini");
        assertEquals(9100, app.options(new String[] {"--port", "9100"}, ini, OptionalInt.of(9500)).port(), "the command line over all");
        assertEquals("0.0.0.0", app.options(new String[0], ini, NO_USER_PORT).host());
    }

    @Test
    void readsTheLogsSettings() throws Exception {
        Properties ini = ini(LaunchOptions.INI_LOG_LEVEL, "warning", LaunchOptions.INI_LOG_FOLDER, "none");
        LaunchOptions fromIni = app.options(new String[0], ini, NO_USER_PORT);
        assertEquals(Level.WARNING, fromIni.logLevel());
        assertEquals("none", fromIni.logFolder());
        assertEquals(Level.FINE, app.options(new String[] {"--verbose"}, ini, NO_USER_PORT).logLevel(), "--verbose is FINE");
        assertEquals(Level.FINER, app.options(new String[] {"--log-level", "finer"}, ini, NO_USER_PORT).logLevel());
        assertEquals(Path.of("/tmp/demo.log"), app.options(new String[] {"--log-file", "/tmp/demo.log"}, ini, NO_USER_PORT).logFile());
        assertEquals(Locale.FRENCH, app.options(new String[] {"--lang", "fr"}, ini, NO_USER_PORT).lang());
    }

    @Test
    void withoutABrowserItKeepsRunning() throws Exception {
        LaunchOptions options = app.options(new String[] {"--no-browser"}, new Properties(), NO_USER_PORT);
        assertFalse(options.browser());
        assertTrue(options.keepAlive(), "a page opened by hand later must find the server");
        assertTrue(app.options(new String[] {"--keep-running"}, new Properties(), NO_USER_PORT).keepAlive(), "the older name still works");
    }

    @Test
    void takesTheProgramsOwnOptionsAndOperands() throws Exception {
        List<String> seen = new ArrayList<>();
        WebApp withOwn = WebApp.named("Demo")
                .option("--data", "DIR", "where the data is kept", dir -> seen.add("data=" + dir))
                .flag("--offline", "no network", () -> seen.add("offline"))
                .operands("[file ...]", "the files to open", files -> {});
        LaunchOptions options = withOwn.options(new String[] {"--data", "/tmp/d", "a.xml", "--offline", "b.xml"}, new Properties(), NO_USER_PORT);
        assertEquals(List.of("data=/tmp/d", "offline"), seen);
        assertEquals(List.of("a.xml", "b.xml"), options.operands());
        String usage = withOwn.usage(org.jtools.framework.support.Catalogue.load(WebApp.class, "org.jtools.framework.app.texts", Locale.ENGLISH).of(Locale.ENGLISH));
        assertTrue(usage.contains("[--data DIR]") && usage.contains("[file ...]") && usage.contains("where the data is kept"), usage);
    }

    @Test
    void opensOneFileThatIsThere(@TempDir Path dir) throws Exception {
        Path schema = Files.writeString(dir.resolve("a.xsd"), "<schema/>");
        Path[] opened = new Path[1];
        WebApp withFile = WebApp.named("Demo").fileOperand("[file.xsd]", "the schema to open", file -> opened[0] = file);
        assertEquals(List.of(schema.toString()), withFile.options(new String[] {schema.toString()}, new Properties(), NO_USER_PORT).operands());
        assertThrows(CommandLine.UsageException.class, () -> withFile.options(new String[] {dir.resolve("missing.xsd").toString()}, new Properties(), NO_USER_PORT));
        assertThrows(CommandLine.UsageException.class, () -> withFile.options(new String[] {dir.toString()}, new Properties(), NO_USER_PORT), "a folder is not a file");
        assertThrows(CommandLine.UsageException.class, () -> withFile.options(new String[] {schema.toString(), schema.toString()}, new Properties(), NO_USER_PORT));
    }

    @Test
    void refusesWhatItCannotRun() {
        Properties none = new Properties();
        assertThrows(CommandLine.UsageException.class, () -> app.options(new String[] {"--unknown"}, none, NO_USER_PORT));
        assertThrows(CommandLine.UsageException.class, () -> app.options(new String[] {"file.xml"}, none, NO_USER_PORT), "no operands declared");
        assertThrows(CommandLine.UsageException.class, () -> app.options(new String[] {"--port"}, none, NO_USER_PORT));
        assertThrows(CommandLine.UsageException.class, () -> app.options(new String[] {"--port", "http"}, none, NO_USER_PORT));
        assertThrows(CommandLine.UsageException.class, () -> app.options(new String[] {"--log-level", "loud"}, none, NO_USER_PORT));
        assertThrows(CommandLine.UsageException.class, () -> app.options(new String[] {"--lang", "de"}, none, NO_USER_PORT));
        assertThrows(CommandLine.UsageException.class, () -> app.options(new String[0], ini(LaunchOptions.INI_PORT, "eighty"), NO_USER_PORT));
    }

    /** A command line refused is said twice: in English, what the log keeps; in the person's language, what they read. */
    @Test
    void refusesInEnglishForTheLogAndInTheirLanguageForThePerson() {
        var texts = org.jtools.framework.support.Catalogue.load(WebApp.class, "org.jtools.framework.app.texts", Locale.ENGLISH, Locale.FRENCH);
        CommandLine.UsageException refused = assertThrows(CommandLine.UsageException.class, () -> app.options(new String[] {"--bogus"}, new Properties(), NO_USER_PORT));
        assertEquals("unknown option: --bogus", refused.getMessage());
        assertEquals("option inconnue : --bogus", refused.in(texts.of(Locale.FRENCH)));
    }

    @Test
    void speaksTheUsersLanguage() {
        var texts = org.jtools.framework.support.Catalogue.load(WebApp.class, "org.jtools.framework.app.texts", Locale.ENGLISH, Locale.FRENCH);
        assertTrue(app.usage(texts.of(Locale.ENGLISH)).startsWith("Usage: demo "), app.usage(texts.of(Locale.ENGLISH)));
        assertTrue(app.usage(texts.of(Locale.FRENCH)).startsWith("Usage : demo "));
        assertTrue(app.usage(texts.of(Locale.FRENCH)).contains("ne pas ouvrir le navigateur"));
        assertTrue(WebApp.named("Demo").id("demo-tool").usage(texts.of(Locale.ENGLISH)).startsWith("Usage: demo-tool "));
    }
}
