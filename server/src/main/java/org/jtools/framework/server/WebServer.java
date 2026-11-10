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
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A local web application's server, on the JDK's own HTTP server: the application's JSON routes under {@link #API},
 * the page from the classpath and the pages' presence. Generic — the application hands it its routes and the
 * classpath folder of its page:
 * <ul>
 *   <li>{@code GET|POST /api/...}: a {@link Route} the application registered; what it returns is the JSON answer
 *       (null: 204), what it throws the error ({@link Refusal}: its status, {@link IllegalArgumentException}: 400).
 *       A {@code POST} from another site is refused (403): nothing but the page drives the server.</li>
 *   <li>{@code GET /api/alive?id=} and {@code POST /api/bye?id=}: the presence of a page ({@link PageWatch}).</li>
 *   <li>any other {@code GET}: the page's file under the classpath folder; a path with no such file gets the page's
 *       {@code index.html}, since the page's routes are the page's business (a single-page application).</li>
 * </ul>
 * Each request has a virtual thread of its own: a page's presence stream holds one for the page's whole life.
 */
public final class WebServer implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(WebServer.class.getName());

    /** Where the application's routes live; everything else is the page's. */
    public static final String API = "/api";
    public static final String ALIVE = API + "/alive", BYE = API + "/bye";
    /** The page id the presence routes take. */
    public static final String PARAM_PAGE = "id";
    /** The classpath folder of the page by default: the frontend's jar puts its build there. */
    public static final String DEFAULT_PAGE_ROOT = "static";
    private static final String INDEX = "index.html", ROOT = "/", PARENT = "..";
    /** How often a presence stream is written to: how soon a crashed browser's page is noticed. */
    private static final long PING_MILLIS = 5_000;
    private static final byte[] PING = ": ping\n\n".getBytes(StandardCharsets.UTF_8);
    private static final String NOT_FOUND = "not found", PAGE_EXPECTED = "?id= expected", METHOD_EXPECTED = "GET or POST expected",
            INTERNAL = "internal error", REQUEST_LOG = "%s %s -> %d", FAILED_LOG = "%s %s failed";

    /** An application's route: what it answers, as a tree {@link org.jtools.framework.support.Json} writes. */
    @FunctionalInterface
    public interface Route {
        Object answer(HttpExchange exchange) throws Exception;
    }

    /** A request refused with its status (4xx): the page receives {@code {"error": message}}. */
    public static final class Refusal extends RuntimeException {
        private final int status;

        public Refusal(int status, String message) {
            super(message);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    private final Map<String, Route> gets = new ConcurrentHashMap<>(), posts = new ConcurrentHashMap<>();
    private final String pageRoot;
    private final PageWatch pages;
    private final ClassLoader resources = WebServer.class.getClassLoader();
    private HttpServer http;
    private ExecutorService executor;
    private String host;

    /** A server for the page under {@code pageRoot} on the classpath, its presence kept by {@code pages}. */
    public WebServer(String pageRoot, PageWatch pages) {
        this.pageRoot = pageRoot.endsWith(ROOT) ? pageRoot.substring(0, pageRoot.length() - 1) : pageRoot;
        this.pages = pages;
    }

    public WebServer() {
        this(DEFAULT_PAGE_ROOT, new PageWatch());
    }

    /** Answers {@code GET path} (a path under {@link #API}). */
    public WebServer get(String path, Route route) {
        gets.put(apiPath(path), route);
        return this;
    }

    /** Answers {@code POST path} (a path under {@link #API}). */
    public WebServer post(String path, Route route) {
        posts.put(apiPath(path), route);
        return this;
    }

    private static String apiPath(String path) {
        if (!path.startsWith(API + ROOT)) throw new IllegalArgumentException("a route lives under " + API + ROOT + ": " + path);
        return path;
    }

    /** Listens on {@code host:port} (port 0: any free one, which {@link #port()} then tells). */
    public WebServer start(String host, int port) throws IOException {
        this.host = host;
        http = HttpServer.create(new InetSocketAddress(host, port), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        http.setExecutor(executor);
        http.createContext(ROOT, this::handle);
        http.start();
        return this;
    }

    public int port() {
        return http.getAddress().getPort();
    }

    /** The page's address: on this computer, {@code localhost} stands for every interface. */
    public String url() {
        String shown = host == null || host.equals("0.0.0.0") ? "localhost" : host;
        return Http.URL_SCHEME + shown + ":" + port() + ROOT;
    }

    public PageWatch pages() {
        return pages;
    }

    /** Stops listening, ends the presence streams (their threads are interrupted). */
    @Override
    public void close() {
        if (http == null) return;
        http.stop(0);
        executor.shutdownNow();
        http = null;
    }

    // ---- the dispatch ---------------------------------------------------------------------------------------

    private void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod(), path = ex.getRequestURI().getPath();
        try (ex) {
            if (path.equals(ALIVE) && method.equals(Http.METHOD_GET)) alive(ex);
            else if (path.equals(BYE) && method.equals(Http.METHOD_POST)) bye(ex);
            else if (path.startsWith(API + ROOT) || path.equals(API)) api(ex, method, path);
            else if (method.equals(Http.METHOD_GET)) page(ex, path);
            else Http.send(ex, 405, Http.TYPE_TEXT, METHOD_EXPECTED);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, String.format(FAILED_LOG, method, path), e);
        }
        LOG.fine(() -> String.format(REQUEST_LOG, method, path, ex.getResponseCode()));
    }

    private void api(HttpExchange ex, String method, String path) throws IOException {
        Route route = switch (method) {
            case Http.METHOD_GET -> gets.get(path);
            case Http.METHOD_POST -> posts.get(path);
            default -> null;
        };
        if (route == null) {
            Http.error(ex, 404, NOT_FOUND);
            return;
        }
        if (method.equals(Http.METHOD_POST) && Http.refusedCrossSite(ex)) return;
        Object answer;
        try {
            answer = route.answer(ex);
        } catch (Refusal e) {
            Http.error(ex, e.status(), e.getMessage());
            return;
        } catch (IllegalArgumentException e) {
            Http.error(ex, 400, e.getMessage());
            return;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, String.format(FAILED_LOG, method, path), e);
            Http.error(ex, 500, INTERNAL);
            return;
        }
        if (answer == null) Http.sendBytes(ex, 204, Http.TYPE_JSON, new byte[0]);
        else Http.sendJson(ex, 200, answer);
    }

    /** The page's file, else its index.html (the page's own routes); never a path climbing out of the page's folder. */
    private void page(HttpExchange ex, String path) throws IOException {
        String file = path.equals(ROOT) || path.endsWith(ROOT) || path.contains(PARENT) ? INDEX : path.substring(1);
        byte[] bytes = resource(file);
        if (bytes == null) {
            file = INDEX;
            bytes = resource(file);
        }
        if (bytes == null) {
            Http.send(ex, 404, Http.TYPE_TEXT, NOT_FOUND);
            return;
        }
        // index.html names the build's hashed files: always asked again, so that a new build is seen at once
        if (file.equals(INDEX)) ex.getResponseHeaders().set(Http.HEADER_CACHE, Http.NO_CACHE);
        Http.sendBytes(ex, 200, Http.contentType(file), bytes);
    }

    /** A file of the page's folder; null for none — and for a folder, which a classpath on disk would list. */
    private byte[] resource(String file) throws IOException {
        if (file.isEmpty() || file.lastIndexOf('.') < file.lastIndexOf('/')) return null;
        try (InputStream in = resources.getResourceAsStream(pageRoot + ROOT + file)) {
            return in == null ? null : in.readAllBytes();
        }
    }

    // ---- the presence of the pages --------------------------------------------------------------------------

    /**
     * {@code GET /api/alive?id=}: the page's presence, an event stream held open for its whole life — the browser drops
     * it when the page is closed or crashes. The pings notice a closed socket ({@link PageWatch} then counts the page gone).
     */
    private void alive(HttpExchange ex) throws IOException {
        String id = Http.query(ex, PARAM_PAGE);
        if (id == null || id.isEmpty()) {
            Http.send(ex, 400, Http.TYPE_TEXT, PAGE_EXPECTED);
            return;
        }
        ex.getResponseHeaders().set(Http.HEADER_CONTENT_TYPE, Http.TYPE_EVENT_STREAM);
        ex.getResponseHeaders().set(Http.HEADER_CACHE, Http.NO_CACHE);
        ex.sendResponseHeaders(200, 0);
        pages.opened(id);
        try (OutputStream out = ex.getResponseBody()) {
            while (true) {
                out.write(PING);
                out.flush();
                Thread.sleep(PING_MILLIS);
            }
        } catch (IOException | InterruptedException gone) {
            // the page is gone (or the server is stopping): nothing to answer any more
        } finally {
            pages.closed(id);
        }
    }

    /** {@code POST /api/bye?id=}: a page closing (a beacon from {@code pagehide}), so that the watch need not wait for its stream to break. */
    private void bye(HttpExchange ex) throws IOException {
        String id = Http.query(ex, PARAM_PAGE);
        if (id != null) pages.closed(id);
        Http.sendBytes(ex, 204, Http.TYPE_TEXT, new byte[0]);
    }
}
