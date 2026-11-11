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
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

import org.jtools.framework.support.Catalogue;
import org.jtools.framework.support.Texts;
import org.jtools.framework.support.Json;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;

/**
 * A request to one of the application's endpoints ({@link WebServer.Endpoint}): the JDK's exchange it wraps — everything
 * that takes an {@link HttpExchange} takes it — and what an endpoint asks of a request: its body as text, bytes or JSON,
 * its query, the path below a prefix endpoint, the page's language. The body is read once and kept.
 */
public final class Exchange extends HttpExchange {

    private static final String HEADER_ACCEPT_LANGUAGE = "Accept-Language";

    private final HttpExchange exchange;
    private final Catalogue programCatalogue;
    private final Catalogue serverCatalogue;
    private final String rest;
    private byte[] body;

    Exchange(HttpExchange exchange, Catalogue programCatalogue, Catalogue serverCatalogue, String rest) {
        this.exchange = exchange;
        this.programCatalogue = programCatalogue;
        this.serverCatalogue = serverCatalogue;
        this.rest = rest;
    }

    // ---- what an endpoint asks of a request ---------------------------------------------------------------------

    /** The body, read once and kept. */
    public byte[] bytes() throws IOException {
        if (body == null) {
            try (InputStream in = exchange.getRequestBody()) {
                body = in.readAllBytes();
            }
        }
        return body;
    }

    /** The body as UTF-8 text. */
    public String text() throws IOException {
        return new String(bytes(), StandardCharsets.UTF_8);
    }

    /** The body as a JSON tree; malformed, a 400 refusal saying where. */
    public Object json() throws IOException {
        try {
            return Json.parse(text());
        } catch (IllegalArgumentException e) {
            throw new WebServer.Refusal(400, serverTexts().t("json.invalid", e.getMessage()));
        }
    }

    /** The body as a JSON object; anything else, a 400 refusal. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> jsonObject() throws IOException {
        if (json() instanceof Map<?, ?> object) return (Map<String, Object>) object;
        throw new WebServer.Refusal(400, serverTexts().t("json.objectExpected"));
    }

    /** A parameter of the query, decoded; null when the query does not have it. */
    public String query(String name) {
        return Http.query(exchange, name);
    }

    /** A parameter of the query, or the fallback when the query does not have it. */
    public String query(String name, String fallback) {
        String value = query(name);
        return value == null ? fallback : value;
    }

    /** For a prefix endpoint ({@code /api/store/}), the path below it ({@code reports/2026.json}); empty for an exact endpoint. */
    public String rest() {
        return rest;
    }

    /** The page's language among the program's (its {@code Accept-Language}), the program's default when none. */
    public Locale language() {
        return programCatalogue.preferred(exchange.getRequestHeaders().getFirst(HEADER_ACCEPT_LANGUAGE));
    }

    /** The program's texts in the page's language ({@code WebApp.catalogue}); the framework's own when it named none. */
    public Texts texts() {
        return programCatalogue.of(language());
    }

    /** Whether the request comes from this computer: an endpoint acting on its desktop (opening a file) answers only then. */
    public boolean fromThisComputer() {
        InetSocketAddress remote = exchange.getRemoteAddress();
        return remote != null && remote.getAddress() != null && remote.getAddress().isLoopbackAddress();
    }

    /** The framework's own texts in the page's language: the server's refusals. */
    Texts serverTexts() {
        return serverCatalogue.of(language());
    }

    // ---- the JDK's exchange, as it is -----------------------------------------------------------------------

    @Override public Headers getRequestHeaders() { return exchange.getRequestHeaders(); }
    @Override public Headers getResponseHeaders() { return exchange.getResponseHeaders(); }
    @Override public URI getRequestURI() { return exchange.getRequestURI(); }
    @Override public String getRequestMethod() { return exchange.getRequestMethod(); }
    @Override public HttpContext getHttpContext() { return exchange.getHttpContext(); }
    @Override public void close() { exchange.close(); }
    @Override public InputStream getRequestBody() { return exchange.getRequestBody(); }
    @Override public OutputStream getResponseBody() { return exchange.getResponseBody(); }
    @Override public void sendResponseHeaders(int code, long length) throws IOException { exchange.sendResponseHeaders(code, length); }
    @Override public InetSocketAddress getRemoteAddress() { return exchange.getRemoteAddress(); }
    @Override public int getResponseCode() { return exchange.getResponseCode(); }
    @Override public InetSocketAddress getLocalAddress() { return exchange.getLocalAddress(); }
    @Override public String getProtocol() { return exchange.getProtocol(); }
    @Override public Object getAttribute(String name) { return exchange.getAttribute(name); }
    @Override public void setAttribute(String name, Object value) { exchange.setAttribute(name, value); }
    @Override public void setStreams(InputStream in, OutputStream out) { exchange.setStreams(in, out); }
    @Override public HttpPrincipal getPrincipal() { return exchange.getPrincipal(); }
}
