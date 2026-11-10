package org.jtools.framework.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebServerTest {

    private final HttpClient client = HttpClient.newHttpClient();
    private final CountDownLatch stopped = new CountDownLatch(1);
    private WebServer server;

    @BeforeEach
    void start() throws Exception {
        server = new WebServer("test-page", new PageWatch(Duration.ofMillis(200)))
                .get("/api/echo", ex -> Map.of("said", String.valueOf(Http.query(ex, "say"))))
                .get("/api/refused", ex -> { throw new WebServer.Refusal(409, "no"); })
                .post("/api/nothing", ex -> null)
                .start("127.0.0.1", 0);
        server.pages().watch(stopped::countDown);
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(server.url() + path.substring(1))).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String origin) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(server.url() + path.substring(1))).POST(HttpRequest.BodyPublishers.noBody());
        if (origin != null) request.header("Origin", origin);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void servesThePageAndItsFiles() throws Exception {
        assertTrue(get("/").body().contains("the test page"));
        HttpResponse<String> script = get("/main.js");
        assertEquals(200, script.statusCode());
        assertTrue(script.headers().firstValue("Content-Type").orElseThrow().startsWith("text/javascript"));
    }

    @Test
    void givesThePageItsOwnRoutes() throws Exception {
        HttpResponse<String> route = get("/some/route/of/the/page");
        assertEquals(200, route.statusCode());
        assertTrue(route.body().contains("<title>test page</title>"));
        // never a file out of the page's folder
        assertTrue(get("/../logging.properties").body().contains("<title>test page</title>"));
    }

    @Test
    void answersTheApplicationsRoutes() throws Exception {
        assertEquals("{\"said\":\"hello world\"}", get("/api/echo?say=hello%20world").body());
        HttpResponse<String> refused = get("/api/refused");
        assertEquals(409, refused.statusCode());
        assertEquals("{\"error\":\"no\"}", refused.body());
        assertEquals(404, get("/api/unknown").statusCode());
        assertEquals(204, post("/api/nothing", null).statusCode());
    }

    @Test
    void refusesAnotherSite() throws Exception {
        assertEquals(403, post("/api/nothing", "http://elsewhere.example").statusCode());
        assertEquals(204, post("/api/nothing", "http://127.0.0.1:" + server.port()).statusCode());
    }

    @Test
    void stopsOnceTheLastPageHasGone() throws Exception {
        HttpRequest alive = HttpRequest.newBuilder(URI.create(server.url() + "api/alive?id=page-1")).build();
        HttpResponse<InputStream> stream = client.send(alive, HttpResponse.BodyHandlers.ofInputStream());
        assertEquals(200, stream.statusCode());
        assertEquals("text/event-stream", stream.headers().firstValue("Content-Type").orElseThrow());
        stream.body().read();   // the first ping: the page is counted
        assertEquals(1, server.pages().count());
        assertEquals(1, stopped.getCount(), "no stop while a page is open");
        assertEquals(204, post("/api/bye?id=page-1", null).statusCode());
        assertTrue(stopped.await(5, TimeUnit.SECONDS), "the stop once the grace has elapsed");
        stream.body().close();
    }
}
