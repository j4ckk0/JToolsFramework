package org.jtools.__APP_ID__.api;

import java.util.Map;

import org.jtools.__APP_ID__.core.__APP_NAME__;
import org.jtools.framework.server.WebServer;
import org.jtools.framework.support.Json;

/**
 * __APP_NAME__'s HTTP API: each route reads the request, asks {@link __APP_NAME__}, and turns its answer into JSON.
 * A translation and nothing more — a rule found here belongs in core. The page's TypeScript mirror of these routes
 * is frontend/src/app/api.service.ts.
 */
public final class __APP_NAME__Api {

    /** {@code GET}: {@code {"name", "version"}}. */
    public static final String INFO = WebServer.API + "/info";
    private static final String J_NAME = "name", J_VERSION = "version";

    private __APP_NAME__Api() {}

    /** Registers the routes on the server, answered by the application. */
    public static WebServer routes(WebServer server, __APP_NAME__ application) {
        return server.get(INFO, ex -> about(application.about()));
    }

    static Map<String, Object> about(__APP_NAME__.About about) {
        Map<String, Object> out = Json.obj();
        out.put(J_NAME, about.name());
        out.put(J_VERSION, about.version());
        return out;
    }
}
