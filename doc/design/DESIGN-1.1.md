# JToolsFramework 1.1 — design

What a program on the framework must not have to write again: its server's plumbing — the request, the answers, the
refusals, the checks, the page's files —, the program around it — the command line, the settings, the log, the texts,
the built-in routes —, native file dialogs, and its distributions. 1.0 was a server, a launcher and a template; 1.1
makes them what a real program needs. §9 says where the implementation departed from this design, and why.

## 0. Two decisions first

1. **1.0.0 is never published.** Nothing consumes 1.0 but the template and the trials the framework's CI generates, so
   1.1 may change 1.0's API where it was wrong — the route's parameter, the static files' fallback, the protocol's
   answers — instead of carrying both.
2. **The inclusion rule**: a feature is in 1.1 when every program would otherwise write it again. What one program
   needs and another does not stays the program's — a candidate for later at most (§7).

What the framework must **not** take, said once: a program's domain — its business classes, its own texts, its own
JSON writers. A domain library that promises "the JDK and nothing else" to its own users keeps that promise: it plugs
into the framework through a request filter (§2.5), a JSON already written (`Json.raw`, §2.2), a raw handler for a
protocol of its own (§2.4).

## 1. The twelve items

| # | Item | Module | What a program no longer writes |
|---|---|---|---|
| 1 | `Exchange`: the route's parameter, with the request's helpers and language | server | reading the body, the JSON, the query, the page's language |
| 2 | Answers: trees, `Json.raw`, `Answer` (bytes, XML, text, status) | server, support | writing the answer, its content type |
| 3 | Errors: `{"error"}` for every refusal; exceptions mapped to statuses | server | the catch around every route, the error's shape |
| 4 | Prefix routes and raw handlers | server | splitting a path by hand; a second server for a protocol of its own |
| 5 | Request filters (JDK `Filter`) | server | wrapping every route to set or time something |
| 6 | Host and Origin checks on every request | server | the defence against another site and DNS rebinding |
| 7 | Static files: ETag, 304, cache, extension-less fallback | server | serving its page |
| 8 | `UserSettings` and the port's order; `/api/settings` built in, with the program's own fields | app | the Settings menu's storage and route |
| 9 | `/api/quit`, `/api/about`, `/api/log/open` built in | app | Quit, About, Logs ▸ Open |
| 10 | Logging: `--log-level`, `--verbose`, `--log-file`, `.ini`, a rotating file | app | the log's setup |
| 11 | `I18n` and the launcher's own texts in en/fr; `--lang` | support, app | its texts' loading and `Accept-Language` |
| 12 | `FileDialogs` | desktop (new) | native open, save and folder dialogs |

## 2. The server (`jtools-server`)

### 2.1 `Exchange`

The route's parameter is the framework's `Exchange`, a subclass of the JDK's `HttpExchange` delegating its 17
abstract methods to the exchange it wraps — so everything that took an `HttpExchange` still takes it, and a route
written for 1.0 (`ex -> about(...)`) compiles unchanged. (`Request` was the obvious name, and is taken: the JDK's
`HttpExchange` implements an interface `com.sun.net.httpserver.Request`.)

```java
@FunctionalInterface
public interface Route {
    Object answer(Exchange exchange) throws Exception;   // a tree, Json.raw(...), an Answer, or null (204)
}

public final class Exchange extends HttpExchange {
    public String text() throws IOException;              // the body, UTF-8 (read once, kept)
    public byte[] bytes() throws IOException;
    public Object json() throws IOException;              // Json.parse(text()); malformed: a 400 Refusal
    public Map<String, Object> jsonObject() throws IOException;  // an object, or a 400 Refusal
    public String query(String name);                     // decoded; null when absent
    public String query(String name, String fallback);
    public String rest();                                 // the path below a prefix route's own (§2.4)
    public Locale language();                             // Accept-Language among the program's languages (§3.4)
    public I18n i18n();                                   // the program's texts in that language
    public boolean fromThisComputer();                    // loopback remote address (/api/log/open)
}
```

### 2.2 Answers

A route's return value is its answer:

| Returned | Sent |
|---|---|
| a Map / List / String / Number / Boolean tree | 200, `application/json; charset=utf-8`, `Json.write` |
| `Json.raw(String)` — JSON already written | 200, the text as it is; **nestable**: a `Json.raw` inside a tree is written verbatim, so a domain library's own writer's output can sit inside an object of the route's |
| `Answer` | its status, type, headers and bytes |
| `null` | 204 |

```java
public record Answer(int status, String contentType, byte[] body, Map<String, String> headers) {
    public static Answer json(int status, Object tree);
    public static Answer bytes(String contentType, byte[] body);   // a zip, an image
    public static Answer xml(String xml);                            // a document as it is stored
    public static Answer text(int status, String text);
    public Answer withHeader(String name, String value);
}
```

`Json.raw` is in `jtools-support`'s `Json` (a `record Raw(String json)` its writer emits as it is).

### 2.3 Errors

Every refusal is `{"error": "<message>"}` JSON, protocol answers included — a page reads `json.error`, and a 405 or
404 in text would make its `resp.json()` throw instead of saying why:

| Case | Status | Message |
|---|---|---|
| `throw new WebServer.Refusal(status, message)` | its own | its message |
| `IllegalArgumentException` | 400 | its message |
| an exception the program mapped: `server.refuse(SchemaException.class, 400)` | the mapped one | its message |
| any other exception | 500 | the framework's "internal error: {0}" in the request's language; the trace logged |
| a method the route does not take | 405, `Allow:` header | "{0} expected" |
| no such route | 404 | "not found" |

A route registered with `get` answers GET, with `post` POST; `route(path, Route)` takes any method.
`refuse(Class, status)` says what of the program's exceptions is the user's problem — `server.refuse(Exception.class,
400).refuse(IOException.class, 500)`, the most specific mapping winning.

### 2.4 Prefix routes and raw handlers

- A path ending in `/` is a **prefix route**: `server.get("/api/store/", route)` answers `/api/store/items/X/parts`,
  `Exchange.rest()` giving `items/X/parts`. The longest match wins; exact routes win over prefixes.
- `server.handle(String path, HttpHandler handler)` mounts a plain JDK handler — the framework's checks (§2.6) and
  filters (§2.5) still run, its answer conventions do not: a protocol of the program's own (an MCP endpoint, say)
  keeps its own answers.

### 2.5 Request filters

`server.filter(com.sun.net.httpserver.Filter)` adds a JDK filter to the one root context, so it wraps **every**
request — routes, static files, raw handlers, and the `/api/alive` stream that lives as long as its page. A domain
library with texts of its own gets the page's language this way:

```java
server.filter(new Filter() {
    public void doFilter(HttpExchange ex, Chain chain) throws IOException {
        Messages.setRequestLocale(Messages.localeOf(ex.getRequestHeaders().getFirst("Accept-Language")));
        try { chain.doFilter(ex); } finally { Messages.clearRequestLocale(); }   // after a stream too, hours later
    }
    public String description() { return "the page's language for the library's messages"; }
});
```

The framework adds one filter of its own, first: the request line, its status and its duration at FINE — after the
checks of §2.6.

### 2.6 Host and Origin checks, on every request

- **Host**: a request whose `Host` header is not `localhost`, `127.0.0.1`, `[::1]` or the `--host` given is refused
  (403) — the DNS-rebinding defence; a wildcard `--host` (`0.0.0.0`, `::`) accepts any Host, since the program then
  serves the network on purpose. A route stricter than that keeps a rule of its own (`WebServer.host()`, §9).
- **Origin**: a request changing state whose `Origin` is not `http://<Host>` is refused (403): no other site's page
  may drive the program — its `/api/quit` first of all.

### 2.7 Static files

From the classpath folder the program names (`static/` by default, an Angular build's; `web/` for a page of its own),
`Cache-Control: no-cache` and an ETag on every file, 304 on `If-None-Match`, the files kept in memory when they come
from a jar (re-read from a classes folder, while developing). A path with `..` or outside `[A-Za-z0-9._/-]` is a 404.
**The fallback to `index.html` is for extension-less paths only** — an Angular page's own routes (`/settings`) get
the page, a missing `main.js` gets a 404, not an HTML page that makes the browser report a syntax error. Types add
`.md` (`text/markdown`), `.xml`, `.zip`, `.woff2`, `.map`.

## 3. The program (`jtools-app`)

### 3.1 `UserSettings` and the port's order

```java
public final class UserSettings {          // one per program: UserSettings.of(id)
    public boolean autoStop();             // default true
    public void setAutoStop(boolean on);
    public OptionalInt port();             // the Settings menu's choice; empty when none
    public void setPort(int port);         // 0 clears it
    public Preferences node();             // the program's own keys beside
}
```

Its node is `org/jtools/<id>` in `Preferences.userRoot()`, or the system property `<id>.preferences` (the tests').
The port of a run: **`--port` > the Settings menu > the `.ini` > the program's default**. The automatic stop is on
when `!--keep-alive && !--no-browser && settings.autoStop()`.

`/api/settings`, built in: GET `{"autoStop", "port", "defaultPort", "currentPort"}` (`autoStop` is the watch's state
now); POST any of `autoStop` (applied at once and kept) and `port` (kept, for the next run; 0 clears), each validated,
a bad one refused in `{"error"}`. A program adds its own fields:

```java
public interface SettingsFields {
    void read(Map<String, Object> answer);                                  // GET: its fields into the answer
    void write(Map<String, Object> asked, Exchange exchange) throws Exception;  // POST: validate and keep its own; a Refusal on a bad one
}
WebApp.named("Bookshelf").settingsFields(new ShelfSettings())...
```

### 3.2 `/api/quit`, `/api/about`, `/api/log/open`

- `POST /api/quit`: `{"ok": true}`, the log line, then the server closed and the program exited, on a thread of its
  own so that the answer leaves first.
- `GET /api/about`: `{"name", "version", "javaVersion", "language" (the machine's), "logFile"}` plus what the program
  adds (`WebApp.aboutFields(Map -> void)`): whether it can show native dialogs, say.
- `POST /api/log/open`: the log file in the system's viewer (`DesktopOpener.open`), refused when the request does not
  come from this computer.

### 3.3 Logging

| | 1.1 |
|---|---|
| level | `--log-level LEVEL`; `--verbose` is `--log-level FINE`; `.ini` `log.level` |
| applies to | the logger `org.jtools` and everything under it — the framework's and the program's, whichever module they are in (a level set on one module's logger alone would leave the others silent) |
| file | `--log-file FILE`, else `.ini` `log.folder` (`none`: the console only), else **`~/.<id>/logs/<id>.0.log`** — not the temporary folder, which the system empties and nobody finds |
| rotation | JUL's own `FileHandler`: `<id>.%g.log`, 2 files of 5 MB |
| format | `2026-11-10 21:34:38 INFO    org.jtools… - message` |
| language | **logs in English** (a log is read by whoever helps, and searched); what the user reads — the console's usage and errors, the failure dialog — in their language (§3.4) |

### 3.4 Texts: `I18n`

`jtools-support` holds a program's texts, one catalogue per program:

```java
public final class I18n {
    public static Catalogue catalogue(Class<?> anchor, String bundle, Locale... languages);  // the first is the default
    public String t(String key, Object... args);       // plain {0} replacement: no MessageFormat, so "l'arbre" needs no ''
    public String tn(String key, int n, Object... args);  // key.one / key.other, by a per-language rule (fr: 0 and 1)
    public boolean has(String key);
    public Locale locale();
    public static String message(Throwable e);         // getMessage(), else toString()
}
public final class Catalogue {
    public I18n of(Locale locale);                     // unsupported: the default; cached
    public I18n of(String acceptLanguage);             // q-values honoured, fr-CA is fr
    public List<Locale> languages();
}
```

`WebApp.texts(catalogue)` names the program's: `Exchange.i18n()` and the help of its options use it. The launcher's own
texts (usage, port in use, cannot start, not a file, the last page closed…) are a framework catalogue in en and fr; the
console's language is `--lang`, else the machine's. A program with hundreds of calls to a texts class of its own keeps
that class as a facade over its catalogue.

### 3.5 The command line

`--port`, `--host`, `--no-browser`, `--keep-alive`, `--log-level`, `--verbose`, `--log-file`, `--lang`, `--help`, and
the program's own (`option`, `flag`: `--data DIR`, say). A file operand: `WebApp.fileOperand(usage, help,
Consumer<Path>)` — one optional file, refused when it is not a regular file. An unknown `--option` is refused, not
taken for a file name. Exit codes: **2** the command line cannot be run (usage, not a file), **3** the program cannot
start (port in use, I/O).

## 4. Native file dialogs (`jtools-desktop`, new)

kdialog or zenity on Linux (by `XDG_CURRENT_DESKTOP` and the PATH), AWT's `FileDialog` on Windows and macOS, Swing's
`JFileChooser` as the fallback; `available()` false when headless (a route then answers 409, "no display"); the last
folder remembered. Its titles and filters are the caller's. A module of its own, so that a program without dialogs
does not carry Swing in its jar's dependencies.

```java
public static boolean available();
public static List<Path> chooseFilesToOpen(String title, boolean multiple, Filter filter);   // empty: cancelled
public static Path chooseFileToSave(String title, Path folder, String name, Filter filter);   // null: cancelled
public static Path chooseFolder(String title);                                                // null: cancelled
public record Filter(String description, List<String> globs) {}
```

## 5. The distributions

jtools-dist's `build.xml`, run by jtools-parent's `dist` profile: jdeps on the program's jar, plus
`jtools.dist.modules.extra` (`jdk.charsets,jdk.crypto.ec`); one runtime per platform whose JDK archive is there
(`JDK_LINUX`, `JDK_WINDOWS`, `JDK_MACOS` in `externals.conf`); Linux, macOS (Apple silicon) and Windows archives of
about 35 MB, against 50 MB with a whole JRE.

## 6. What a program sees change from 1.0

`Route` takes an `Exchange` (source-compatible for lambdas, not binary); the static fallback serves `index.html` for
extension-less paths only; protocol answers are JSON; the port's order gains the Settings step; the log goes to a file
by default.

## 7. Candidates, for later

- **A second launch opens the running one**: probe `/api/about` on the port; if it is the same program, open the
  browser on it and hand it the file operand.
- **A plain-JavaScript page module** (`jtools-page.js`): presence, `request()` with the page's language, quit,
  settings, about, `t()`.
- **`jtools-testing`**: the catalogue checks (same keys and placeholders in every language, server and page), a test
  server on port 0 with JSON helpers.
- **@jtools/web sends the page's language** (an `HttpInterceptor`).
- **A served-files allow-list**: files the user chose are the only ones a route may read.
- **Gradle conventions** mirroring jtools-parent.

## 8. The order of the work

1. `Exchange`, answers, errors, prefix routes, raw handlers, filters, checks, static files (items 1–7).
2. `I18n` and the launcher's catalogue (11), then `UserSettings`, the built-in routes and logging (8–10).
3. `jtools-desktop` (12).
4. The template follows, the docs; 1.1.0.
5. **The acceptance**: real programs built on 1.1, their own test suites passing — a test of theirs that fails is a
   design fault here before it is a fault there.

## 9. What the implementation changed

- **The jlink distributions are in 1.1** (§5), replacing the assembly descriptors; the `JRE_*` keys are gone.
- **`get` answers GET only**, not HEAD: the JDK's server wants a HEAD answered without a body, which no route writes
  for; no page asks HEAD.
- **`FileDialogs` returns null or an empty list when cancelled**, not `Optional`; its two texts (the overwrite question,
  the failure) are in the machine's language, the desktop it opens on.
- **The log has its own formatter** (`LogFormatter`): the JDK's `SimpleFormatter` writes the level in the machine's
  language (`INFOS`, `AVERTISSEMENT`) and `MessageFormat` groups digits (`8,093`, `8 093`); the log's levels are
  English and its numbers plain.
- **`Exchange.i18n()`** answers the framework's own texts when the program named no catalogue, rather than failing.
- **The server's refusals and the launcher's messages** are each a catalogue of the framework in en and fr.
- **A program's route replaces a built-in one** of the same path and method — the program's are mounted after the
  framework's, and the replacement is logged: a route kept on purpose must win, not vanish.
- **`jdk.localedata` is not in the runtimes by default**: jdeps cannot see it; a program formatting dates or numbers in
  another language than English adds it to `jtools.dist.modules.extra`.

**Found by building programs on it** — the acceptance doing its job:

- `/api/alive` and `/api/bye` answer 405 to another method, with `Allow`, as every route does.
- `WebApp.start(args…)`: a program's tests start its server as `launch` would, but read no `.ini`, set up no log,
  open no browser and never exit.
- The publish plugins' versions are in jtools-parent's `pluginManagement`, so that a module keeping itself off Maven
  Central names the portal's plugin with `skipPublishing` and no version.
- The program's jar leaves out a modular library's `module-info.class`: the program's jar is a plain classpath one.
- `build.xml` finds a platform's JDK by Temurin's own names too (`*jdk*linux*.tar.gz`, case-sensitive: a JRE's do not
  match), in the folder `jtools.jdk.archives` names, and `jtools.dist.include` adds folders of the repository to the
  archives (`samples/**`).
- `jdeps` and `jlink` are those of the build machine's platform's JDK archive, unpacked whole, else the running JDK's:
  jlink links only jmods of its own version, and the Java on a machine's PATH may be another (a 25 building for 21).
- `WebApp.language()`: the run's language — `--lang`'s, else the machine's — known from the routes on, for what a
  program says to a client that names none (a tool endpoint other programs call).
- `WebServer.host()`: the address the server listens on, as given — for a route stricter than §2.6, answering this
  computer only even on a wildcard address.
- A startup failure logged in the console's language, where §3.3 wants English: fixed in 1.2.
