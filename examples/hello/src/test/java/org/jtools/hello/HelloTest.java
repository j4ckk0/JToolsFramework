package org.jtools.hello;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.prefs.Preferences;

import org.jtools.framework.server.WebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The program started as main starts it — on a free port, kept running —, its endpoint and its page asked over HTTP. */
class HelloTest {

    private final HttpClient client = HttpClient.newHttpClient();
    private String node;
    private WebServer server;

    @BeforeEach
    void start() throws Exception {
        node = "org/jtools/hello-test-" + UUID.randomUUID();   // the settings in a node of the test's own, removed after
        System.setProperty("hello.preferences", node);
        server = HelloApp.program().start("--port", "0", "--keep-alive");
    }

    @AfterEach
    void stop() throws Exception {
        server.close();
        Preferences.userRoot().node(node).removeNode();
        System.clearProperty("hello.preferences");
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(server.url() + path)).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void answersItsEndpoint() throws Exception {
        assertEquals("{\"message\":\"Hello, Ada!\"}", get("api/hello?name=Ada").body());
        assertEquals("{\"message\":\"Hello, world!\"}", get("api/hello").body());
    }

    @Test
    void servesItsPage() throws Exception {
        HttpResponse<String> page = get("");
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("/api/alive?id="), "the page holds its presence");
    }
}
