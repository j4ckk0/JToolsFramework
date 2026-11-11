package org.jtools.greeter;

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

/** The greeting in the page's language, the name kept as a setting of the program's own, the option on the command line. */
class GreeterTest {

    private final HttpClient client = HttpClient.newHttpClient();
    private String node;
    private WebServer server;

    @BeforeEach
    void start() throws Exception {
        node = "org/jtools/greeter-test-" + UUID.randomUUID();   // the settings in a node of the test's own, removed after
        System.setProperty("greeter.preferences", node);
        server = Greeter.program().start("--port", "0", "--keep-alive");
    }

    @AfterEach
    void stop() throws Exception {
        server.close();
        Preferences.userRoot().node(node).removeNode();
        System.clearProperty("greeter.preferences");
    }

    private HttpResponse<String> send(String path, String language, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(server.url() + path)).header("Accept-Language", language);
        if (body != null) request.POST(HttpRequest.BodyPublishers.ofString(body));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void greetsInThePagesLanguage() throws Exception {
        assertEquals("{\"text\":\"Hello, you!\"}", send("api/greeting", "en", null).body());
        assertEquals("{\"text\":\"Bonjour, vous !\"}", send("api/greeting", "fr-CA,fr;q=0.9", null).body());
    }

    @Test
    void keepsTheNameAsASettingOfItsOwn() throws Exception {
        assertEquals(200, send("api/settings", "en", "{\"name\": \"Ada\"}").statusCode());
        assertTrue(send("api/settings", "en", null).body().contains("\"name\":\"Ada\""));
        assertEquals("{\"text\":\"Hello, Ada!\"}", send("api/greeting", "en", null).body());
        HttpResponse<String> refused = send("api/settings", "fr", "{\"name\": 42}");
        assertEquals(400, refused.statusCode());
        assertEquals("{\"error\":\"name : un texte\"}", refused.body());
    }

    @Test
    void takesAnOptionOfItsOwn() throws Exception {
        try (WebServer howdy = Greeter.program().start("--port", "0", "--keep-alive", "--greeting", "Howdy")) {
            String said = client.send(HttpRequest.newBuilder(URI.create(howdy.url() + "api/greeting")).build(), HttpResponse.BodyHandlers.ofString()).body();
            assertEquals("{\"text\":\"Howdy, you!\"}", said);
        }
    }
}
