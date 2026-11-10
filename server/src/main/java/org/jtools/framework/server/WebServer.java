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
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jtools.framework.support.Catalogue;
import org.jtools.framework.support.I18n;
import org.jtools.framework.support.Json;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * A local web application's server, on the JDK's own HTTP server: the application's routes under {@link #API}, plain
 * handlers it mounts, the page from the classpath, and the pages' presence. Every request, whatever serves it:
 * <ol>
 *   <li>is refused (403) when its {@code Host} is not this computer's or the {@code --host} given — the defence
 *       against DNS rebinding; a wildcard host serves the network on purpose and takes any — and, when it changes
 *       state (not a GET), when its {@code Origin} is another site's: nothing but the page drives the program;</li>
 *   <li>goes through the application's filters ({@link #filter}): the JDK's own {@link Filter}s, around routes,
 *       handlers, files and the presence streams alike;</li>
 *   <li>is logged at FINE with its status and its duration.</li>
 * </ol>
 * A route's answer is a JSON tree, {@link Json#raw}, an {@link Answer}, or null (204). Every refusal is
 * {@code {"error": reason}}: a {@link Refusal}, an {@link IllegalArgumentException} (400), an exception the
 * application mapped ({@link #refuse}), a wrong method (405), an unknown route (404); anything else is a 500, its
 * trace logged. Each request has a virtual thread of its own: a page's presence stream holds one for its whole life.
 */
public final class WebServer implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(WebServer.class.getName());

    /** Where the application's routes live; everything else is the page's, but for the handlers it mounts. */
    public static final String API = "/api";
    public static final String ALIVE = API + "/alive", BYE = API + "/bye";
    /** The page id the presence routes take. */
    public static final String PARAM_PAGE = "id";
    /** The classpath folder of the page by default: the frontend's jar puts its build there. */
    public static final String DEFAULT_PAGE_ROOT = "static";
    private static final String ROOT = "/", ANY_METHOD = "*";
    /** How often a presence stream is written to: how soon a crashed browser's page is noticed. */
    private static final long PING_MILLIS = 5_000;
    private static final byte[] PING = ": ping\n\n".getBytes(StandardCharsets.UTF_8);
    /** The hosts a request may name besides the one given: this computer's. */
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]", "::1");
    private static final Set<String> WILDCARD_HOSTS = Set.of("0.0.0.0", "::", "[::]", "");
    private static final Catalogue SERVER_TEXTS = I18n.catalogue(WebServer.class, "org.jtools.framework.server.texts", Locale.ENGLISH, Locale.FRENCH);

    /** An application's route: what it answers (a tree, {@link Json#raw}, an {@link Answer}, or null for 204). */
    @FunctionalInterface
    public interface Route {
        Object answer(Exchange exchange) throws Exception;
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

    /** What answers a path: a route taking one method (or any), or a plain handler; a path ending in / is a prefix. */
    private record Mount(String path, String method, Route route, HttpHandler handler) {
        boolean prefix() { return path.endsWith(ROOT); }
    }

    private final Map<String, Map<String, Mount>> mounts = new ConcurrentHashMap<>();
    private final Map<Class<? extends Throwable>, Integer> refusals = new LinkedHashMap<>();
    private final List<Filter> filters = new ArrayList<>();
    private final StaticFiles files;
    private final PageWatch pages;
    private Catalogue texts = SERVER_TEXTS;
    private HttpServer http;
    private HttpContext context;
    private ExecutorService executor;
    private String host;

    /** A server for the page under {@code pageRoot} on the classpath, its presence kept by {@code pages}. */
    public WebServer(String pageRoot, PageWatch pages) {
        this.files = new StaticFiles(pageRoot, WebServer.class.getClassLoader());
        this.pages = pages;
        refusals.put(IllegalArgumentException.class, 400);
    }

    public WebServer() {
        this(DEFAULT_PAGE_ROOT, new PageWatch());
    }

    // ---- what the application gives the server --------------------------------------------------------------

    /** The program's texts and languages: what {@link Exchange#language()} chooses among, {@link Exchange#i18n()} answers in. */
    public WebServer texts(Catalogue programTexts) {
        this.texts = programTexts;
        return this;
    }

    /** Answers {@code GET path}; a path ending in / answers every path under it ({@link Exchange#rest()}). */
    public WebServer get(String path, Route route) {
        return mount(new Mount(apiPath(path), Http.METHOD_GET, route, null));
    }

    /** Answers {@code POST path}. */
    public WebServer post(String path, Route route) {
        return mount(new Mount(apiPath(path), Http.METHOD_POST, route, null));
    }

    /** Answers any method on {@code path}: the route looks at the method itself, if it cares. */
    public WebServer route(String path, Route route) {
        return mount(new Mount(apiPath(path), ANY_METHOD, route, null));
    }

    /**
     * Mounts a plain JDK handler on a path, anywhere but the page's root — DCSBuilder's {@code /mcp}. The checks and the
     * filters still run; the answers are the handler's own.
     */
    public WebServer handle(String path, HttpHandler handler) {
        if (!path.startsWith(ROOT) || path.equals(ROOT)) throw new IllegalArgumentException("a handler lives on a path of its own: " + path);
        return mount(new Mount(path, ANY_METHOD, null, handler));
    }

    /** Adds a JDK filter around every request, in the order they are added: the page's language, a log of the program's. */
    public WebServer filter(Filter filter) {
        filters.add(filter);
        if (context != null) context.getFilters().add(filter);
        return this;
    }

    /**
     * Answers a route's exception of that class (or of a subclass) with that status and its message —
     * {@code refuse(SchemaException.class, 400)}. The most specific mapping wins; {@link IllegalArgumentException} is
     * 400 already. An exception mapped to nothing is a 500.
     */
    public WebServer refuse(Class<? extends Throwable> exception, int status) {
        synchronized (refusals) {
            refusals.put(exception, status);
        }
        return this;
    }

    /** A later mount of the same path and method replaces the earlier: said in the log, since it is easily unmeant. */
    private WebServer mount(Mount mount) {
        Mount replaced = mounts.computeIfAbsent(mount.path(), p -> new ConcurrentHashMap<>()).put(mount.method(), mount);
        if (replaced != null) LOG.info(() -> "a new " + mount.method() + " " + mount.path() + " replaces the one mounted before");
        return this;
    }

    private static String apiPath(String path) {
        if (!path.startsWith(API + ROOT)) throw new IllegalArgumentException("a route lives under " + API + ROOT + ": " + path);
        return path;
    }

    // ---- the server's life ----------------------------------------------------------------------------------

    /** Listens on {@code host:port} (port 0: any free one, which {@link #port()} then tells). */
    public WebServer start(String host, int port) throws IOException {
        this.host = host;
        http = HttpServer.create(new InetSocketAddress(host, port), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        http.setExecutor(executor);
        context = http.createContext(ROOT, this::dispatch);
        context.getFilters().add(new Checks());
        context.getFilters().addAll(filters);
        http.start();
        return this;
    }

    public int port() {
        return http.getAddress().getPort();
    }

    /** The page's address: on this computer, {@code localhost} stands for every interface. */
    public String url() {
        String shown = host == null || WILDCARD_HOSTS.contains(host) ? "localhost" : host;
        return Http.URL_SCHEME + shown + ":" + port() + ROOT;
    }

    public PageWatch pages() {
        return pages;
    }

    /** Stops listening, ends the presence streams (their threads are interrupted). */
    @Override
    public void close() {
        stop(0);
    }

    /**
     * Stops listening, lets the requests under way finish for up to {@code seconds} — the answer of {@code /api/quit}
     * leaves before the program does — then ends the rest, the presence streams among them.
     */
    public synchronized void stop(int seconds) {
        if (http == null) return;
        http.stop(seconds);
        executor.shutdownNow();
        http = null;
    }

    // ---- every request: the checks and the log -------------------------------------------------------------

    /** The first filter: the Host and Origin checks, then the request's line, status and duration logged at FINE. */
    private final class Checks extends Filter {
        @Override
        public void doFilter(HttpExchange ex, Chain chain) throws IOException {
            long start = System.nanoTime();
            try {
                if (refused(ex)) return;
                chain.doFilter(ex);
            } finally {
                LOG.fine(() -> String.format("%s %s -> %d in %d ms", ex.getRequestMethod(), ex.getRequestURI().getPath(),
                        ex.getResponseCode(), (System.nanoTime() - start) / 1_000_000));
            }
        }

        @Override
        public String description() {
            return "the Host and Origin checks, the request's log line";
        }
    }

    private boolean refused(HttpExchange ex) throws IOException {
        I18n say = SERVER_TEXTS.of(ex.getRequestHeaders().getFirst("Accept-Language"));
        String hostHeader = ex.getRequestHeaders().getFirst(Http.HEADER_HOST);
        if (hostHeader != null && !allowedHost(hostHeader)) {
            LOG.warning(() -> ex.getRequestURI().getPath() + " refused: host " + hostHeader);
            Http.error(ex, 403, say.t("http.hostRefused", hostHeader));
            return true;
        }
        String origin = ex.getRequestHeaders().getFirst(Http.HEADER_ORIGIN);
        boolean changesState = !Http.METHOD_GET.equals(ex.getRequestMethod()) && !"HEAD".equals(ex.getRequestMethod());
        if (changesState && origin != null && !origin.equalsIgnoreCase(Http.URL_SCHEME + hostHeader)) {
            LOG.warning(() -> ex.getRequestURI().getPath() + " refused: cross-site request from " + origin);
            Http.error(ex, 403, say.t("http.crossSite"));
            return true;
        }
        return false;
    }

    /** This computer's names, the --host given, or anything when that host is a wildcard; the port is not looked at. */
    boolean allowedHost(String hostHeader) {
        if (host == null || WILDCARD_HOSTS.contains(host)) return true;
        String name = hostHeader.startsWith("[") ? hostHeader.substring(0, hostHeader.indexOf(']') + 1)
                : hostHeader.contains(":") ? hostHeader.substring(0, hostHeader.lastIndexOf(':')) : hostHeader;
        name = name.toLowerCase(Locale.ROOT);
        return LOCAL_HOSTS.contains(name) || name.equalsIgnoreCase(host);
    }

    // ---- the dispatch ---------------------------------------------------------------------------------------

    private void dispatch(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod(), path = ex.getRequestURI().getPath();
        try (ex) {
            if (path.equals(ALIVE)) {
                if (method.equals(Http.METHOD_GET)) alive(ex); else wrongMethod(ex, Http.METHOD_GET);
            } else if (path.equals(BYE)) {
                if (method.equals(Http.METHOD_POST)) bye(ex); else wrongMethod(ex, Http.METHOD_POST);
            } else {
                Map<String, Mount> byMethod = mounted(path);
                if (byMethod != null) mounted(ex, path, method, byMethod);
                else if (path.startsWith(API + ROOT) || path.equals(API)) refuse(ex, 404, "http.notFound");
                else if (!method.equals(Http.METHOD_GET)) refuse(ex, 405, "http.methodExpected", Http.METHOD_GET);
                else if (!files.serve(ex, path)) refuse(ex, 404, "http.notFound");
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, method + " " + path + " failed", e);
        }
    }

    /** The mounts of a path: its own, else the longest prefix above it. */
    private Map<String, Mount> mounted(String path) {
        Map<String, Mount> exact = mounts.get(path);
        if (exact != null) return exact;
        String best = null;
        for (String mounted : mounts.keySet()) {
            if (mounted.endsWith(ROOT) && path.startsWith(mounted) && (best == null || mounted.length() > best.length())) best = mounted;
        }
        return best == null ? null : mounts.get(best);
    }

    private void mounted(HttpExchange ex, String path, String method, Map<String, Mount> byMethod) throws Exception {
        Mount mount = byMethod.getOrDefault(method, byMethod.get(ANY_METHOD));
        if (mount == null) {
            wrongMethod(ex, String.join(", ", byMethod.keySet()));
            return;
        }
        if (mount.handler() != null) {
            mount.handler().handle(ex);
            return;
        }
        Exchange exchange = new Exchange(ex, texts, SERVER_TEXTS, mount.prefix() ? path.substring(mount.path().length()) : "");
        Object answer;
        try {
            answer = mount.route().answer(exchange);
        } catch (Refusal e) {
            refused(exchange, e, e.status());
            return;
        } catch (Exception e) {
            Integer status = statusOf(e);
            if (status != null) {
                refused(exchange, e, status);
            } else {
                LOG.log(Level.SEVERE, method + " " + path + " failed", e);
                Http.error(ex, 500, exchange.serverTexts().t("http.internalError", I18n.message(e)));
            }
            return;
        }
        send(ex, answer);
    }

    /** The status the application mapped for this exception's class, the most specific first; null when none. */
    private Integer statusOf(Throwable e) {
        synchronized (refusals) {
            for (Class<?> type = e.getClass(); type != null; type = type.getSuperclass()) {
                Integer status = refusals.get(type);
                if (status != null) return status;
            }
        }
        return null;
    }

    private static void refused(Exchange ex, Exception e, int status) throws IOException {
        LOG.info(() -> ex.getRequestMethod() + " " + ex.getRequestURI().getPath() + " refused: " + I18n.message(e));
        LOG.log(Level.FINE, "the refusal's trace", e);
        Http.error(ex, status, I18n.message(e));
    }

    /** 405, with the methods the path takes in {@code Allow} and in the refusal. */
    private static void wrongMethod(HttpExchange ex, String allowed) throws IOException {
        ex.getResponseHeaders().set(Http.HEADER_ALLOW, allowed);
        refuse(ex, 405, "http.methodExpected", allowed);
    }

    private static void refuse(HttpExchange ex, int status, String key, Object... args) throws IOException {
        Http.error(ex, status, SERVER_TEXTS.of(ex.getRequestHeaders().getFirst("Accept-Language")).t(key, args));
    }

    private static void send(HttpExchange ex, Object answer) throws IOException {
        if (answer == null) Http.send(ex, 204, null, new byte[0]);
        else if (answer instanceof Answer a) {
            a.headers().forEach((name, value) -> ex.getResponseHeaders().set(name, value));
            Http.send(ex, a.status(), a.contentType(), a.body());
        } else Http.send(ex, 200, Http.TYPE_JSON, Json.write(answer).getBytes(StandardCharsets.UTF_8));
    }

    // ---- the presence of the pages --------------------------------------------------------------------------

    /**
     * {@code GET /api/alive?id=}: the page's presence, an event stream held open for its whole life — the browser drops
     * it when the page is closed or crashes. The pings notice a closed socket ({@link PageWatch} then counts the page gone).
     */
    private void alive(HttpExchange ex) throws IOException {
        String id = Http.query(ex, PARAM_PAGE);
        if (id == null || id.isEmpty()) {
            refuse(ex, 400, "http.pageExpected");
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
        Http.send(ex, 204, null, new byte[0]);
    }
}
