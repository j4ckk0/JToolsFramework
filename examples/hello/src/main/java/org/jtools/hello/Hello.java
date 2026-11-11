package org.jtools.hello;

import java.util.Map;

import org.jtools.framework.app.WebApp;

/**
 * The smallest program on JToolsFramework: one route, {@code GET /api/hello?name=}, and its page,
 * {@code src/main/resources/web/index.html}. {@code java -jar hello.jar} starts the server on port 8089, opens the
 * page in the browser, and stops once the page is closed — the command line, the log, the settings and the stop are
 * {@link WebApp}'s; {@code --help} lists them.
 */
public final class Hello {

    private Hello() {}

    /** The program: what {@link #main} launches and what the test starts. */
    public static WebApp program() {
        return WebApp.named("Hello").defaultPort(8089).pageRoot("web")
                .routes(server -> server.get("/api/hello", ex -> Map.of("message", "Hello, " + ex.query("name", "world") + "!")));
    }

    public static void main(String[] args) {
        program().launch(args);
    }
}
