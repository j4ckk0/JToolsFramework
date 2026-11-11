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
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import com.sun.net.httpserver.HttpExchange;

/**
 * The page's files, from a folder of the classpath: each with {@code Cache-Control: no-cache} and an ETag, so that the
 * browser asks again every time and is answered 304 when nothing changed. Kept in memory when they come from a jar
 * (they cannot change); read again on every request from a classes folder, while the page is being written.
 * <p>
 * A path with no file falls back on {@code index.html} when it has no extension — a single-page application's own
 * routes ({@code /settings}) — and is a 404 when it has one: a missing {@code main.js} must not come back as HTML.
 */
final class StaticFiles {

    private static final String INDEX = "index.html", ROOT = "/", FILE_PROTOCOL = "file", JAR_PROTOCOL = "jar";
    /** Folders and names of letters, digits, dots, dashes and underscores; nothing climbing out (no "..": checked apart). */
    private static final Pattern SAFE_PATH = Pattern.compile("(/[A-Za-z0-9._-]+)+");

    private record File(byte[] body, String type, String etag) {}

    private final String root;
    private final ClassLoader loader;
    private final Map<String, File> cache = new ConcurrentHashMap<>();

    StaticFiles(String root, ClassLoader loader) {
        this.root = root.endsWith(ROOT) ? root.substring(0, root.length() - 1) : root;
        this.loader = loader;
    }

    /** Answers a GET of the page's files; false when there is no such file and no fallback: the caller says 404. */
    boolean serve(HttpExchange ex, String path) throws IOException {
        String name = path.equals(ROOT) ? INDEX : path.substring(1);
        boolean safe = path.equals(ROOT) || (SAFE_PATH.matcher(path).matches() && !path.contains(".."));
        File file = safe ? file(name) : null;
        if (file == null && safe && !lastSegment(name).contains(".")) file = file(INDEX);
        if (file == null) return false;
        ex.getResponseHeaders().set(Http.HEADER_CACHE, Http.NO_CACHE);
        ex.getResponseHeaders().set(Http.HEADER_ETAG, file.etag());
        if (file.etag().equals(ex.getRequestHeaders().getFirst(Http.HEADER_IF_NONE_MATCH))) {
            Http.send(ex, 304, null, new byte[0]);
        } else {
            Http.send(ex, 200, file.type(), file.body());
        }
        return true;
    }

    private static String lastSegment(String name) {
        return name.substring(name.lastIndexOf('/') + 1);
    }

    /** The file of that name under the root, kept when it comes from a jar; null when there is none — or a folder. */
    private File file(String name) throws IOException {
        File kept = cache.get(name);
        if (kept != null) return kept;
        URL url = loader.getResource(root + ROOT + name);
        if (url == null || isFolder(url)) return null;
        byte[] body;
        try (InputStream in = url.openStream()) {
            body = in.readAllBytes();
        }
        File file = new File(body, Http.contentType(name), "\"" + body.length + "-" + Integer.toHexString(Arrays.hashCode(body)) + "\"");
        if (JAR_PROTOCOL.equals(url.getProtocol())) cache.put(name, file);
        return file;
    }

    /** A classes folder on disk lists a folder's names when asked for it as a resource: that is not a file of the page. */
    private static boolean isFolder(URL url) {
        if (!FILE_PROTOCOL.equals(url.getProtocol())) return false;
        try {
            return !Files.isRegularFile(Path.of(url.toURI()));
        } catch (URISyntaxException | IllegalArgumentException e) {
            return true;
        }
    }
}
