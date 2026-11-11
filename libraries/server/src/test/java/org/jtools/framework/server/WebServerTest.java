package org.jtools.framework.server;

/*-
 * #%L
 * JToolsFramework server
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.jtools.framework.support.I18n;
import org.jtools.framework.support.Json;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

class WebServerTest {

    /** A domain exception of the kind a program maps to a status. */
    static final class SchemaException extends Exception {
        SchemaException(String message) { super(message); }
    }

    private final HttpClient client = HttpClient.newHttpClient();
    private final CountDownLatch stopped = new CountDownLatch(1);
    private final List<String> filtered = new CopyOnWriteArrayList<>();
    private WebServer server;

    @BeforeEach
    void start() throws Exception {
        server = new WebServer("test-page", new PageWatch(Duration.ofMillis(200)))
                .texts(I18n.catalogue(WebServerTest.class, "org.jtools.framework.server.program-texts", Locale.ENGLISH, Locale.FRENCH))
                .refuse(SchemaException.class, 422)
                .get("/api/echo", ex -> Map.of("said", ex.query("say", "nothing")))
                .get("/api/hello", ex -> Map.of("text", ex.i18n().t("hello"), "lang", ex.language().getLanguage()))
                .get("/api/refused", ex -> { throw new WebServer.Refusal(409, "no"); })
                .get("/api/illegal", ex -> { throw new IllegalArgumentException("not like that"); })
                .get("/api/schema", ex -> { throw new SchemaException("line 3: no such type"); })
                .get("/api/broken", ex -> { throw new IOException("disk on fire"); })
                .get("/api/raw", ex -> Map.of("graph", Json.raw("{\"nodes\":[1]}")))
                .get("/api/zip", ex -> Answer.bytes(Http.TYPE_ZIP, new byte[] {'P', 'K', 3, 4}))
                .get("/api/store/", ex -> Map.of("rest", ex.rest()))
                .get("/api/store/count", ex -> Map.of("count", 3))
                .post("/api/nothing", ex -> null)
                .post("/api/parse", ex -> ex.jsonObject())
                .route("/api/any", ex -> Map.of("method", ex.getRequestMethod()))
                .handle("/mcp", ex -> Http.send(ex, 202, null, new byte[0]))
                .filter(new Filter() {
                    @Override public void doFilter(HttpExchange ex, Chain chain) throws IOException {
                        filtered.add(ex.getRequestURI().getPath());
                        chain.doFilter(ex);
                    }
                    @Override public String description() { return "records every path"; }
                })
                .start("127.0.0.1", 0);
        server.pages().watch(stopped::countDown);
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private HttpResponse<String> get(String path, String... headers) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(server.url() + path.substring(1)));
        if (headers.length > 0) request.headers(headers);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body, String origin) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(server.url() + path.substring(1))).POST(HttpRequest.BodyPublishers.ofString(body));
        if (origin != null) request.header("Origin", origin);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String error(HttpResponse<String> response) {
        return String.valueOf(((Map<?, ?>) Json.parse(response.body())).get("error"));
    }

    // ---- the page ------------------------------------------------------------------------------------------

    @Test
    void servesThePageAndItsFiles() throws Exception {
        assertTrue(get("/").body().contains("the test page"));
        HttpResponse<String> script = get("/main.js");
        assertEquals(200, script.statusCode());
        assertTrue(script.headers().firstValue("Content-Type").orElseThrow().startsWith("text/javascript"));
        assertEquals("text/markdown; charset=utf-8", get("/docs/guide.md").headers().firstValue("Content-Type").orElseThrow());
    }

    @Test
    void answersNotModifiedWhenTheFileIsTheSame() throws Exception {
        HttpResponse<String> first = get("/main.js");
        String etag = first.headers().firstValue("ETag").orElseThrow();
        assertEquals("no-cache", first.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals(304, get("/main.js", "If-None-Match", etag).statusCode());
        assertEquals(200, get("/main.js", "If-None-Match", "\"another\"").statusCode());
    }

    @Test
    void givesThePageItsOwnRoutesButNotMissingFiles() throws Exception {
        HttpResponse<String> route = get("/settings/advanced");
        assertEquals(200, route.statusCode(), "an extension-less path: the page's own route");
        assertTrue(route.body().contains("<title>test page</title>"));
        assertEquals(404, get("/missing.js").statusCode(), "a file that is not there is not answered with HTML");
        assertTrue(get("/docs").body().contains("<title>test page</title>"), "a folder: the page, never a listing of it");
        assertEquals(405, post("/index.html", "", null).statusCode());
    }

    // ---- the routes ----------------------------------------------------------------------------------------

    @Test
    void answersTreesRawJsonAndBytes() throws Exception {
        assertEquals("{\"said\":\"hello world\"}", get("/api/echo?say=hello%20world").body());
        assertEquals("{\"graph\":{\"nodes\":[1]}}", get("/api/raw").body());
        HttpResponse<byte[]> zip = client.send(HttpRequest.newBuilder(URI.create(server.url() + "api/zip")).build(), HttpResponse.BodyHandlers.ofByteArray());
        assertEquals("application/zip", zip.headers().firstValue("Content-Type").orElseThrow());
        assertArrayEquals(new byte[] {'P', 'K', 3, 4}, zip.body());
        assertEquals(204, post("/api/nothing", "", null).statusCode());
        assertEquals("{\"a\":1}", post("/api/parse", "{\"a\":1}", null).body());
    }

    @Test
    void refusesInJsonWithTheRightStatus() throws Exception {
        assertEquals(409, get("/api/refused").statusCode());
        assertEquals("no", error(get("/api/refused")));
        assertEquals(400, get("/api/illegal").statusCode());
        assertEquals("not like that", error(get("/api/illegal")));
        assertEquals(422, get("/api/schema").statusCode(), "the exception the program mapped");
        assertEquals("line 3: no such type", error(get("/api/schema")));
        HttpResponse<String> broken = get("/api/broken");
        assertEquals(500, broken.statusCode(), "what nobody mapped is the server's fault");
        assertEquals("internal error: disk on fire", error(broken));
        assertEquals(400, post("/api/parse", "{\"a\":", null).statusCode());
        assertTrue(error(post("/api/parse", "{\"a\":", null)).startsWith("invalid JSON"));
        assertEquals("a JSON object expected", error(post("/api/parse", "[1]", null)));
    }

    @Test
    void saysWhatMethodItTakes() throws Exception {
        HttpResponse<String> wrong = post("/api/echo", "", null);
        assertEquals(405, wrong.statusCode());
        assertEquals("GET", wrong.headers().firstValue("Allow").orElseThrow());
        assertEquals("GET expected", error(wrong));
        assertEquals("{\"method\":\"POST\"}", post("/api/any", "", null).body(), "a route taking any method");
        HttpResponse<String> unknown = get("/api/unknown");
        assertEquals(404, unknown.statusCode());
        assertEquals("not found", error(unknown));
    }

    @Test
    void answersEveryPathUnderAPrefixRoute() throws Exception {
        assertEquals("{\"rest\":\"spifs/X/labels\"}", get("/api/store/spifs/X/labels").body());
        assertEquals("{\"count\":3}", get("/api/store/count").body(), "an exact route wins over the prefix");
    }

    @Test
    void speaksThePagesLanguage() throws Exception {
        assertEquals(Map.of("text", "bonjour", "lang", "fr"), Json.parse(get("/api/hello", "Accept-Language", "fr-FR,fr;q=0.9").body()));
        assertEquals(Map.of("text", "hello", "lang", "en"), Json.parse(get("/api/hello", "Accept-Language", "de").body()));
        assertEquals("introuvable", error(get("/api/unknown", "Accept-Language", "fr")), "the server's own refusals too");
    }

    @Test
    void mountsPlainHandlers() throws Exception {
        assertEquals(202, post("/mcp", "{}", null).statusCode());
        assertEquals(403, post("/mcp", "{}", "http://elsewhere.example").statusCode(), "the checks run for handlers too");
    }

    @Test
    void runsTheFiltersAroundEverything() throws Exception {
        get("/api/echo");
        get("/main.js");
        post("/mcp", "{}", null);
        assertEquals(List.of("/api/echo", "/main.js", "/mcp"), filtered);
    }

    // ---- the checks ----------------------------------------------------------------------------------------

    @Test
    void refusesAnotherSite() throws Exception {
        HttpResponse<String> refused = post("/api/nothing", "", "http://elsewhere.example");
        assertEquals(403, refused.statusCode());
        assertEquals("cross-site request refused", error(refused));
        assertEquals(204, post("/api/nothing", "", "http://127.0.0.1:" + server.port()).statusCode());
        assertEquals(200, get("/api/echo", "Origin", "http://elsewhere.example").statusCode(), "a GET changes nothing");
    }

    @Test
    void refusesAForeignHost() throws Exception {
        assertTrue(server.allowedHost("localhost:" + server.port()));
        assertTrue(server.allowedHost("127.0.0.1"));
        assertTrue(server.allowedHost("[::1]:8080"));
        assertFalse(server.allowedHost("attacker.example:" + server.port()));
        // HttpClient will not send a Host of its own: a request by hand, as a rebinding page's browser would send it
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            OutputStream out = socket.getOutputStream();
            out.write(("GET /api/echo HTTP/1.1\r\nHost: attacker.example\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            InputStream in = socket.getInputStream();
            String answer = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(answer.startsWith("HTTP/1.1 403"), answer);
            assertTrue(answer.contains("host refused: attacker.example"), answer);
        }
    }

    @Test
    void takesAnyHostWhenServingTheNetwork() throws Exception {
        try (WebServer open = new WebServer("test-page", new PageWatch()).start("0.0.0.0", 0)) {
            assertTrue(open.allowedHost("192.168.1.20:" + open.port()));
            assertTrue(open.url().startsWith("http://localhost:"));
            assertEquals("0.0.0.0", open.host(), "the address as given, for a route of stricter rules");
        }
    }

    // ---- the presence --------------------------------------------------------------------------------------

    @Test
    void stopsOnceTheLastPageHasGone() throws Exception {
        HttpRequest alive = HttpRequest.newBuilder(URI.create(server.url() + "api/alive?id=page-1")).build();
        HttpResponse<InputStream> stream = client.send(alive, HttpResponse.BodyHandlers.ofInputStream());
        assertEquals(200, stream.statusCode());
        assertEquals("text/event-stream", stream.headers().firstValue("Content-Type").orElseThrow());
        stream.body().read();   // the first ping: the page is counted
        assertEquals(1, server.pages().count());
        assertEquals(1, stopped.getCount(), "no stop while a page is open");
        assertEquals(204, post("/api/bye?id=page-1", "", null).statusCode());
        assertTrue(stopped.await(5, TimeUnit.SECONDS), "the stop once the grace has elapsed");
        stream.body().close();
        assertEquals(400, get("/api/alive").statusCode(), "a stream without a page id");
        assertEquals(405, get("/api/bye?id=page-1").statusCode(), "the presence routes say which method they take");
        assertEquals(405, post("/api/alive?id=page-1", "", null).statusCode());
    }
}
