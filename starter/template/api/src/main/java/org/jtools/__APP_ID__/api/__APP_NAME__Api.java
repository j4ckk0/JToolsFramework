package org.jtools.__APP_ID__.api;

import java.util.Map;

import org.jtools.__APP_ID__.core.__APP_NAME__;
import org.jtools.framework.server.WebServer;
import org.jtools.framework.support.Json;

/**
 * __APP_NAME__'s HTTP API: each route reads the request, asks {@link __APP_NAME__}, and turns its answer into JSON.
 * A translation and nothing more — a rule found here belongs in core. The page's TypeScript mirror of these routes
 * is frontend/src/app/api.service.ts. The program's name and version are the framework's {@code GET /api/about}.
 */
public final class __APP_NAME__Api {

    /** {@code GET}: {@code {"summary"}}. */
    public static final String STATUS = WebServer.API + "/status";
    private static final String J_SUMMARY = "summary";

    private __APP_NAME__Api() {}

    /** Registers the routes on the server, answered by the application. */
    public static WebServer routes(WebServer server, __APP_NAME__ application) {
        return server.get(STATUS, ex -> status(application.status()));
    }

    static Map<String, Object> status(__APP_NAME__.Status status) {
        Map<String, Object> out = Json.obj();
        out.put(J_SUMMARY, status.summary());
        return out;
    }
}
