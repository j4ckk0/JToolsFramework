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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.prefs.Preferences;

import org.jtools.framework.server.Exchange;
import org.jtools.framework.server.WebServer;
import org.jtools.framework.support.Json;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The framework's own routes, on a running server; the user's preferences in a node of the test's own. */
class BuiltInRoutesTest {

    private static final String ID = "jtoolstest";
    private final HttpClient client = HttpClient.newHttpClient();
    private final CountDownLatch exited = new CountDownLatch(1);
    private final AtomicInteger exitCode = new AtomicInteger(-1);
    private String node;
    private WebServer server;

    /** DCSBuilder's kind of field: the program's own setting, validated and kept by the program. */
    private final SettingsFields theme = new SettingsFields() {
        private String value = "light";
        @Override public void read(Map<String, Object> answer) { answer.put("theme", value); }
        @Override public void write(Map<String, Object> asked, Exchange exchange) {
            Object theme = asked.get("theme");
            if (theme == null) return;
            if (!List.of("light", "dark").contains(theme)) throw new WebServer.Refusal(400, "theme: light or dark");
            value = (String) theme;
        }
    };

    @BeforeEach
    void start() throws Exception {
        node = "org/jtools/" + ID + "-" + UUID.randomUUID();   // removed after: nothing left in the user's preferences
        System.setProperty(ID + ".preferences", node);
        WebApp app = WebApp.named("JToolsTest").id(ID).defaultPort(18990)
                .settingsFields(theme)
                .aboutFields(about -> about.put("dialogs", false))
                .exitWith(code -> { exitCode.set(code); exited.countDown(); });
        LaunchOptions options = app.options(new String[] {"--port", "0"}, new Properties(), OptionalInt.empty());
        server = app.startServer(options, BuiltInRoutesTest.class);
    }

    @AfterEach
    void stop() throws Exception {
        server.close();
        Preferences.userRoot().node(node).removeNode();
        System.clearProperty(ID + ".preferences");
    }

    private Map<?, ?> json(HttpResponse<String> response) {
        return (Map<?, ?>) Json.parse(response.body());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(server.url() + path.substring(1))).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(server.url() + path.substring(1))).POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void readsTheSettings() throws Exception {
        Map<?, ?> settings = json(get(WebApp.SETTINGS));
        assertEquals(true, settings.get("autoStop"));
        assertEquals(0L, settings.get("port"), "no port chosen");
        assertEquals(18990L, settings.get("defaultPort"));
        assertEquals((long) server.port(), settings.get("currentPort"));
        assertEquals("light", settings.get("theme"), "the program's own field");
    }

    @Test
    void switchesTheAutomaticStopAtOnceAndKeepsIt() throws Exception {
        assertTrue(server.pages().isEnabled());
        Map<?, ?> answer = json(post(WebApp.SETTINGS, "{\"autoStop\":false}"));
        assertEquals(false, answer.get("autoStop"));
        assertFalse(server.pages().isEnabled(), "at once");
        assertFalse(UserSettings.of(ID).autoStop(), "and for the next runs");
    }

    @Test
    void keepsThePortForTheNextRun() throws Exception {
        assertEquals(18500L, json(post(WebApp.SETTINGS, "{\"port\":18500}")).get("port"));
        assertEquals(OptionalInt.of(18500), UserSettings.of(ID).port());
        assertEquals((long) server.port(), json(get(WebApp.SETTINGS)).get("currentPort"), "the run's port does not change");
        post(WebApp.SETTINGS, "{\"port\":0}");
        assertEquals(OptionalInt.empty(), UserSettings.of(ID).port(), "0 forgets it");
    }

    @Test
    void refusesABadSettingAndKeepsNothing() throws Exception {
        HttpResponse<String> badPort = post(WebApp.SETTINGS, "{\"port\":70000,\"autoStop\":false}");
        assertEquals(400, badPort.statusCode());
        assertEquals("port: 0, or 1 to 65535", json(badPort).get("error"));
        assertTrue(server.pages().isEnabled(), "nothing of a refused request is applied");
        assertEquals(400, post(WebApp.SETTINGS, "{\"autoStop\":\"no\"}").statusCode());
        HttpResponse<String> badTheme = post(WebApp.SETTINGS, "{\"theme\":\"pink\",\"autoStop\":false}");
        assertEquals("theme: light or dark", json(badTheme).get("error"));
        assertTrue(server.pages().isEnabled());
        assertEquals("dark", json(post(WebApp.SETTINGS, "{\"theme\":\"dark\"}")).get("theme"));
    }

    @Test
    void saysWhatItIs() throws Exception {
        Map<?, ?> about = json(get(WebApp.ABOUT));
        assertEquals("JToolsTest", about.get("name"));
        assertEquals(System.getProperty("java.version"), about.get("javaVersion"));
        assertFalse(String.valueOf(about.get("version")).isBlank());
        assertEquals("", about.get("logFile"), "no log file in a test");
        assertEquals(false, about.get("dialogs"), "the program's own field");
    }

    @Test
    void opensNoLogWhenThereIsNone() throws Exception {
        assertEquals(409, post(WebApp.LOG_OPEN, "").statusCode());
    }

    @Test
    void letsTheProgramReplaceOneOfThem() throws Exception {
        WebApp own = WebApp.named("JToolsTest").id(ID).routes(s -> s.get(WebApp.ABOUT, ex -> Map.of("capabilities", "its own")));
        try (WebServer other = own.start("--port", "0")) {
            HttpResponse<String> about = client.send(HttpRequest.newBuilder(URI.create(other.url() + "api/about")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals("{\"capabilities\":\"its own\"}", about.body());
        }
    }

    /** --lang is the run's language, known to the program's routes: what it says to a client naming none. */
    @Test
    void tellsTheProgramTheRunsLanguage() throws Exception {
        java.util.concurrent.atomic.AtomicReference<java.util.Locale> seen = new java.util.concurrent.atomic.AtomicReference<>();
        WebApp[] app = new WebApp[1];
        app[0] = WebApp.named("JToolsTest").id(ID).routes(s -> seen.set(app[0].language()));
        try (WebServer other = app[0].start("--port", "0", "--lang", "fr")) {
            assertEquals(java.util.Locale.FRENCH, seen.get(), "known when the routes are registered");
            assertEquals(java.util.Locale.FRENCH, app[0].language());
        }
        app[0] = WebApp.named("JToolsTest").id(ID);
        try (WebServer other = app[0].start("--port", "0")) {
            assertEquals(java.util.Locale.getDefault(), app[0].language(), "the machine's without --lang");
        }
    }

    @Test
    void quitsWhenThePageSaysSo() throws Exception {
        HttpResponse<String> answer = post(WebApp.QUIT, "");
        assertEquals("{\"ok\":true}", answer.body(), "the answer leaves before the program does");
        assertTrue(exited.await(5, TimeUnit.SECONDS));
        assertEquals(0, exitCode.get());
    }
}
