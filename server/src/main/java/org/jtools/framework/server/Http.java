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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import org.jtools.framework.support.Json;

import com.sun.net.httpserver.HttpExchange;

/** The HTTP plumbing: reading a request, answering it, the content types. */
public final class Http {

    private static final Logger LOG = Logger.getLogger(Http.class.getName());
    private static final String CROSS_SITE = "cross-site request refused", CROSS_SITE_LOG = "%s refused: %s from %s";

    public static final String URL_SCHEME = "http://";
    public static final String METHOD_GET = "GET", METHOD_POST = "POST";
    static final String HEADER_HOST = "Host", HEADER_ORIGIN = "Origin", HEADER_CONTENT_TYPE = "Content-Type",
            HEADER_CACHE = "Cache-Control", NO_CACHE = "no-cache";
    public static final String TYPE_TEXT = "text/plain; charset=utf-8", TYPE_JSON = "application/json; charset=utf-8";
    static final String TYPE_EVENT_STREAM = "text/event-stream";
    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("html", "text/html; charset=utf-8"),
            Map.entry("js", "text/javascript; charset=utf-8"),
            Map.entry("mjs", "text/javascript; charset=utf-8"),
            Map.entry("css", "text/css; charset=utf-8"),
            Map.entry("json", TYPE_JSON),
            Map.entry("txt", TYPE_TEXT),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("woff2", "font/woff2"),
            Map.entry("map", TYPE_JSON));
    private static final String TYPE_BINARY = "application/octet-stream";
    private static final String J_ERROR = "error";

    private Http() {}

    public static String body(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** The request's body as JSON (a Map / List / String / Number / Boolean / null tree, {@link Json}). */
    public static Object jsonBody(HttpExchange ex) throws IOException {
        return Json.parse(body(ex));
    }

    /** A parameter of the query, decoded; null when the query does not have it. */
    public static String query(HttpExchange ex, String name) {
        String query = ex.getRequestURI().getRawQuery();
        if (query == null) return null;
        for (String param : query.split("&")) {
            int eq = param.indexOf('=');
            String key = eq < 0 ? param : param.substring(0, eq);
            if (key.equals(name)) return eq < 0 ? "" : URLDecoder.decode(param.substring(eq + 1), StandardCharsets.UTF_8);
        }
        return null;
    }

    public static void send(HttpExchange ex, int status, String type, String body) throws IOException {
        sendBytes(ex, status, type, body.getBytes(StandardCharsets.UTF_8));
    }

    public static void sendJson(HttpExchange ex, int status, Object value) throws IOException {
        send(ex, status, TYPE_JSON, Json.write(value));
    }

    /** {@code {"error": reason}} with the status: what the page shows. */
    public static void error(HttpExchange ex, int status, String reason) throws IOException {
        Map<String, Object> out = Json.obj();
        out.put(J_ERROR, reason);
        sendJson(ex, status, out);
    }

    static void sendBytes(HttpExchange ex, int status, String type, byte[] bytes) throws IOException {
        ex.getResponseHeaders().set(HEADER_CONTENT_TYPE, type);
        // 204 and 304 carry no body: -1 says so to the JDK's server
        ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    static String contentType(String path) {
        int dot = path.lastIndexOf('.');
        if (dot < 0) return TYPE_BINARY;
        return TYPES.getOrDefault(path.substring(dot + 1).toLowerCase(Locale.ROOT), TYPE_BINARY);
    }

    /**
     * Answers 403 when the request comes from another site, for every route that changes state (a browser sends
     * its {@code Origin}, which must be this server): nothing but the page may drive the server.
     */
    static boolean refusedCrossSite(HttpExchange ex) throws IOException {
        String origin = ex.getRequestHeaders().getFirst(HEADER_ORIGIN);
        if (origin == null || origin.equalsIgnoreCase(URL_SCHEME + ex.getRequestHeaders().getFirst(HEADER_HOST))) return false;
        LOG.warning(String.format(CROSS_SITE_LOG, ex.getRequestURI().getPath(), CROSS_SITE, origin));
        send(ex, 403, TYPE_TEXT, CROSS_SITE);
        return true;
    }
}
