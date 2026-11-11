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
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

import org.jtools.framework.support.Json;

import com.sun.net.httpserver.HttpExchange;

/** The HTTP plumbing below the routes: the query, sending an answer, the types of the files, the refusals' shape. */
public final class Http {

    public static final String URL_SCHEME = "http://";
    public static final String METHOD_GET = "GET", METHOD_POST = "POST";
    static final String HEADER_HOST = "Host", HEADER_ORIGIN = "Origin", HEADER_CONTENT_TYPE = "Content-Type",
            HEADER_CACHE = "Cache-Control", NO_CACHE = "no-cache", HEADER_ALLOW = "Allow",
            HEADER_ETAG = "ETag", HEADER_IF_NONE_MATCH = "If-None-Match";
    public static final String TYPE_TEXT = "text/plain; charset=utf-8", TYPE_JSON = "application/json; charset=utf-8",
            TYPE_XML = "application/xml; charset=utf-8", TYPE_ZIP = "application/zip";
    static final String TYPE_EVENT_STREAM = "text/event-stream";
    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("html", "text/html; charset=utf-8"),
            Map.entry("js", "text/javascript; charset=utf-8"),
            Map.entry("mjs", "text/javascript; charset=utf-8"),
            Map.entry("css", "text/css; charset=utf-8"),
            Map.entry("json", TYPE_JSON),
            Map.entry("txt", TYPE_TEXT),
            Map.entry("md", "text/markdown; charset=utf-8"),
            Map.entry("xml", TYPE_XML),
            Map.entry("xsd", TYPE_XML),
            Map.entry("zip", TYPE_ZIP),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("woff2", "font/woff2"),
            Map.entry("map", TYPE_JSON));
    private static final String TYPE_BINARY = "application/octet-stream";
    private static final String J_ERROR = "error";

    private Http() {}

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

    /** {@code {"error": reason}} with the status: every refusal's shape, which the pages read as {@code json.error}. */
    public static void error(HttpExchange ex, int status, String reason) throws IOException {
        Map<String, Object> out = Json.obj();
        out.put(J_ERROR, reason);
        send(ex, status, TYPE_JSON, Json.write(out).getBytes(StandardCharsets.UTF_8));
    }

    /** An answer: its type, its bytes — none for 204 and 304, which carry no body (-1 tells the JDK's server). */
    public static void send(HttpExchange ex, int status, String type, byte[] bytes) throws IOException {
        if (type != null) ex.getResponseHeaders().set(HEADER_CONTENT_TYPE, type);
        boolean noBody = status == 204 || status == 304 || bytes.length == 0;
        ex.sendResponseHeaders(status, noBody ? -1 : bytes.length);
        if (noBody) return;
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** The type of a file, by its extension. */
    static String contentType(String path) {
        int dot = path.lastIndexOf('.');
        if (dot < 0) return TYPE_BINARY;
        return TYPES.getOrDefault(path.substring(dot + 1).toLowerCase(Locale.ROOT), TYPE_BINARY);
    }
}
