package org.jtools.todo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

/** The business on its own, then the program over HTTP: its endpoints, and the business's refusals as {"error"}. */
class TodoTest {

    private final HttpClient client = HttpClient.newHttpClient();
    private String node;
    private WebServer server;

    @BeforeEach
    void start() throws Exception {
        node = "org/jtools/todo-test-" + UUID.randomUUID();   // the settings in a node of the test's own, removed after
        System.setProperty("todo.preferences", node);
        server = TodoApp.program().start("--port", "0", "--keep-alive");
    }

    @AfterEach
    void stop() throws Exception {
        server.close();
        Preferences.userRoot().node(node).removeNode();
        System.clearProperty("todo.preferences");
    }

    private HttpResponse<String> send(String path, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(server.url() + path));
        if (body != null) request.POST(HttpRequest.BodyPublishers.ofString(body));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void theRulesAreTheBusinesss() {
        Todos todos = new Todos();
        assertThrows(IllegalArgumentException.class, () -> todos.add(" "));
        assertEquals("milk", todos.add("  milk ").title());
        assertEquals(true, todos.done(1).done());
    }

    @Test
    void thePageAsksAndIsAnswered() throws Exception {
        assertEquals("[]", send("api/todos", null).body());
        assertEquals("{\"id\":1,\"title\":\"milk\",\"done\":false}", send("api/todos", "{\"title\": \"milk\"}").body());
        assertEquals("{\"id\":1,\"title\":\"milk\",\"done\":true}", send("api/todos/done?id=1", "{}").body());
        assertEquals("[{\"id\":1,\"title\":\"milk\",\"done\":true}]", send("api/todos", null).body());
    }

    @Test
    void aRuleBrokenIsARefusalThePageShows() throws Exception {
        HttpResponse<String> empty = send("api/todos", "{\"title\": \"\"}");
        assertEquals(400, empty.statusCode());
        assertEquals("{\"error\":\"a to-do needs a title\"}", empty.body());
        assertEquals(404, send("api/todos/done?id=7", "{}").statusCode());
    }
}
