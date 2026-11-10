# JToolsFramework 1.1 — design

What the framework must gain so that **XsdViewer** and **DCSBuilder** can move their infrastructure onto it. Written
from a survey of both programs' code (November 2026); nothing here is implemented yet.

## 0. Two decisions first

1. **The first release on Maven Central is 1.1.0; 1.0.0 is never published.** Nothing consumes 1.0 but the project
   template and the trials the framework's CI generates, so 1.1 may change 1.0's API where the two programs show it
   was wrong (the route's parameter, the static files' fallback, the protocol answers) instead of carrying both. If
   1.0.0 must go to Central first after all, §9 says what would then need keeping.
2. **The inclusion rule**: a feature is in 1.1 when it deletes code in *both* programs. What only one needs, or
   neither yet, is a 1.2 candidate (§8). That keeps 1.1 to twelve items.

What the framework must **not** take, said once: XsdViewer's `Messages`, `MessageKey` and JSON writers live in its
`core`, a named module whose promise to its Central users is "the JDK and nothing else" — they stay there, and plug
into the framework through the request filter (§3.5). DCSBuilder's MCP server, store, assistant and `Finding` stay
DCSBuilder's; MCP mounts as a raw handler (§3.4).

## 1. What the two programs share today

Both are the same program written twice: the JDK's `HttpServer` on virtual threads, a page in vanilla JS, en/fr,
`/api/alive` + `/api/bye` with a 15 s grace and a 5 s ping, `/api/quit`, `/api/settings` (`autoStop`, `port`, in
`java.util.prefs`), version from the manifest, a `.ini` beside the jar, a failure dialog when there is no console.
Where they differ, one of them is usually missing something the other has:

| | XsdViewer | DCSBuilder | 1.1 takes |
|---|---|---|---|
| route signature | `HttpHandler` per route, 20 classes | private `(HttpExchange, I18n) -> Object` | an `Exchange` route returning an answer (§3.1) |
| JSON answers | strings written by core's `JsonWriter` | `Json` trees; strings sent as they are | trees **and** pre-written JSON, nestable (§3.2) |
| non-JSON answers | none | zip, XML, Markdown | `Answer` (§3.2) |
| errors | `{"error"}`; 405 as text | `{"error"}` 400 for **any** exception; 405, 404, 403 as text | `{"error"}` always; exceptions mapped to statuses (§3.3) |
| per-request language | `localized` wrapper → `Messages` thread-local | `Http.i18n(ex)` argument | a JDK `Filter` (§3.5) and `Exchange.language()` |
| cross-site | **no Origin check** | Origin check on POST; Host check on `/mcp` only | both checks, on every request (§3.6) |
| static files | `no-cache`, no ETag, 404 for unknown | ETag + 304, cache from jar, 404 for unknown | DCSBuilder's (§3.7) |
| port order | `--port` > Settings > `.ini` > 8091 | `--port` > Settings > `.ini` > 8092 | the same order (§4.1) |
| logging | own `Log` on JUL, `--verbose`, `$TMPDIR`, 2 × 1 MB | `--log-level`, `--log-file`, `~/.dcsbuilder/logs`, 5 MiB rename; **`--log-level` misses every module but `app`** | §4.3 |
| texts | `MessageFormat`, bundles in core | plain `{0}`, `tn` plurals, q-values parsed | DCSBuilder's `I18n`, generalised (§4.4) |
| native file dialogs | `FileDialogs`, 366 lines | — | `jtools-desktop` (§5) |

## 2. The twelve items of 1.1

| # | Item | Module | Replaces in XsdViewer | Replaces in DCSBuilder |
|---|---|---|---|---|
| 1 | `Exchange`: the route's parameter, with the request's helpers and language | server | `QueryString`, `JsonRequest`, `HttpResponses.readBody`, `HttpMethod` | `Http.body`, `bytes`, `jsonBody`, `i18n`, `pageId` |
| 2 | Answers: trees, `Json.raw`, `Answer` (bytes, XML, text, status) | server, support | `HttpResponses`, `ContentType`, `HttpStatus` | `Http.send`, `sendBytes`, `contentType`, `post`'s answer writing |
| 3 | Errors: `{"error"}` for every refusal; exceptions mapped to statuses | server | `HttpResponses.error`, `requirePost`, the `localized` catch | `post`'s catch, `logged`'s catch, `Http.error` |
| 4 | Prefix routes and raw handlers | server | — (all routes exact) | context-prefix routes (`/api/store/…`), `route()` for `/mcp` |
| 5 | Request filters (JDK `Filter`) | server | the `localized` wrapper | `logged`'s timing and log line |
| 6 | Host and Origin checks on every request | server | — (gains both) | `Http.refusedCrossSite`, `McpHttp.allowedHost` |
| 7 | Static files: ETag, 304, cache, extension-less fallback | server | `StaticResourceHandler` | `StaticFiles` |
| 8 | `UserSettings` and the port order; `/api/settings` built in, with the program's own fields | launcher | `UserSettings`, `SettingsHandler` | `UserSettings` (port, autoStop), `UserPreferences`, `SettingsApi` (its generic half) |
| 9 | `/api/quit`, `/api/about`, `/api/log/open` built in | launcher | `QuitHandler`, `CapabilitiesHandler`, `BuildInfo` | `handleQuit`, `stopAndExit`, `SettingsApi.handleAbout`, `handleLogOpen` |
| 10 | Logging: `--log-level`, `--verbose`, `--log-file`, `.ini`, a rotating file | launcher | `Log` (all but its facade), `ConfigFile`'s log keys | `configureLogging`, `logLevel`, `logFile`, `defaultLogFile` |
| 11 | `I18n` and the launcher's own texts in en/fr; `--lang` | support, launcher | the launcher's `cli.*`/`server.*` messages | `I18n`, the launcher's `main.*` messages |
| 12 | `FileDialogs` | desktop (new) | `FileDialogs` | — (gains them: open/save without a browser download) |

Item 12 breaks the rule's letter — DCSBuilder has no dialogs today — and is in 1.1 because it is XsdViewer's largest
generic class and DCSBuilder's save currently goes through the browser's downloads; it is the one item to drop to 1.2
if 1.1 must shrink.

## 3. The server (`jtools-server`)

### 3.1 `Exchange`

The route's parameter becomes the framework's `Exchange`, a subclass of the JDK's `HttpExchange` delegating its
17 abstract methods to the exchange it wraps — so everything that took an `HttpExchange` still takes it, and a route
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
    public String rest();                                 // the path below a prefix route's own (§3.4)
    public Locale language();                             // Accept-Language among the program's languages (§4.4)
    public I18n i18n();                                   // the program's texts in that language
    public boolean fromThisComputer();                    // loopback remote address (/api/log/open)
}
```

### 3.2 Answers

A route's return value is its answer:

| Returned | Sent |
|---|---|
| a Map / List / String / Number / Boolean tree | 200, `application/json; charset=utf-8`, `Json.write` |
| `Json.raw(String)` — JSON already written | 200, the text as it is; **nestable**: a `Json.raw` inside a tree is written verbatim, so XsdViewer's `CompareDeclarations` keeps assembling core's `BoxJsonWriter` output inside an object of its own |
| `Answer` | its status, type, headers and bytes |
| `null` | 204 |

```java
public record Answer(int status, String contentType, byte[] body, Map<String, String> headers) {
    public static Answer json(int status, Object tree);
    public static Answer bytes(String contentType, byte[] body);   // DCSBuilder's workspace zip
    public static Answer xml(String xml);                            // DCSBuilder's store documents
    public static Answer text(int status, String text);
    public Answer withHeader(String name, String value);
}
```

`Json.raw` goes into `jtools-support`'s `Json` (a `record Raw(String json)` its writer emits as it is), which is the
only change to `Json` — DCSBuilder's `Json` is the framework's already (1.0 copied it), so DCSBuilder swaps the import.

### 3.3 Errors

Every refusal is `{"error": "<message>"}` JSON, protocol answers included — both pages read `json.error`, and today
a 405 or 404 in text makes their `resp.json()` throw instead of saying why:

| Case | Status | Message |
|---|---|---|
| `throw new WebServer.Refusal(status, message)` | its own | its message |
| `IllegalArgumentException` | 400 | its message |
| an exception the program mapped: `server.refuse(SchemaException.class, 400)` | the mapped one | its message |
| any other exception | 500 | the framework's "internal error: {0}" in the request's language; the trace logged |
| a method the route does not take | 405, `Allow:` header | "{0} expected" |
| no such route | 404 | "not found" |

A route registered with `get` answers GET (and HEAD), with `post` POST; `route(path, Route)` takes any method — what
DCSBuilder's `logged` routes and XsdViewer's `/api/capabilities` do today. `refuse(Class, status)` is how DCSBuilder
keeps its "a domain exception is the user's problem: 400" (`server.refuse(Exception.class, 400)`, the most specific
mapping winning) while an `IOException` becomes the 500 it is.

### 3.4 Prefix routes and raw handlers

- A path ending in `/` is a **prefix route**: `server.get("/api/store/", route)` answers `/api/store/spifs/X/labels`,
  `Exchange.rest()` giving `spifs/X/labels`. The longest match wins; exact routes win over prefixes.
- `server.handle(String path, HttpHandler handler)` mounts a plain JDK handler — the framework's checks (§3.6) and
  filters (§3.5) still run, its answer conventions do not. DCSBuilder's `/mcp` mounts this way, `McpHttp` keeping its
  own JSON-RPC answers and losing its own Host check.

### 3.5 Request filters

`server.filter(com.sun.net.httpserver.Filter)` adds a JDK filter to the one root context, so it wraps **every**
request — routes, static files, raw handlers, and the `/api/alive` stream that lives as long as its page. XsdViewer's
`localized` wrapper becomes a filter:

```java
server.filter(new Filter() {
    public void doFilter(HttpExchange ex, Chain chain) throws IOException {
        Messages.setRequestLocale(Messages.localeOf(ex.getRequestHeaders().getFirst("Accept-Language")));
        try { chain.doFilter(ex); } finally { Messages.clearRequestLocale(); }   // after a stream too, hours later
    }
    public String description() { return "the page's language for core's Messages"; }
});
```

The framework adds one filter of its own, first: the request line, its status and its duration at FINE — what
DCSBuilder's `logged` does — after the checks of §3.6.

### 3.6 Host and Origin checks, on every request

- **Host**: a request whose `Host` header is not `localhost`, `127.0.0.1`, `[::1]` or the `--host` given is refused
  (403) — the DNS-rebinding defence DCSBuilder has on `/mcp` only, McpHttp's rule: a wildcard `--host` (`0.0.0.0`,
  `::`) accepts any Host, since the program then serves the network on purpose.
- **Origin**: a POST whose `Origin` is not `http://<Host>` is refused (403), as 1.0 and DCSBuilder do. XsdViewer gains
  both: today any web page the user visits can POST to its `/api/quit`.

### 3.7 Static files

DCSBuilder's `StaticFiles`, generalised: from the classpath folder the program names (`static/` by default;
XsdViewer and DCSBuilder say `web/`), `Cache-Control: no-cache` and an ETag on every file, 304 on `If-None-Match`,
the files kept in memory when they come from a jar (re-read from a classes folder, while developing). A path with
`..` or outside `[A-Za-z0-9._/-]` is a 404. **The fallback to `index.html` is for extension-less paths only** —
an Angular page's own routes (`/settings`) get the page, a missing `main.js` gets a 404, not an HTML page that makes
the browser report a syntax error. Types add `.md` (`text/markdown`), `.xml`, `.zip`, `.woff2`, `.map`.

## 4. The program (`jtools-launcher`)

### 4.1 `UserSettings` and the port's order

```java
public final class UserSettings {          // one per program: WebApp.settings()
    public boolean autoStop();             // default true
    public void setAutoStop(boolean on);
    public OptionalInt port();             // the Settings menu's choice; empty when none
    public void setPort(int port);         // 0 clears it
    public Preferences node();             // the program's own keys beside (DCSBuilder: markingOrders, the chat model)
}
```

Its node is `org/jtools/<id>` in `Preferences.userRoot()`, or the system property `<id>.preferences` (the tests' — both
programs already have it, under these very names). The port of a run: **`--port` > the Settings menu > the `.ini` >
the program's default** — the order both programs have; 1.0's `WebApp` lacked the second step. The automatic stop is
on when `!--keep-alive && !--no-browser && settings.autoStop()`, both programs' rule.

`/api/settings`, built in: GET `{"autoStop", "port", "defaultPort", "currentPort"}` (`autoStop` is the watch's state
now, as both answer); POST any of `autoStop` (applied at once and kept) and `port` (kept, for the next run; 0 clears),
each validated, a bad one refused in `{"error"}`. A program adds its own fields:

```java
public interface SettingsFields {
    void read(Map<String, Object> answer);                                  // GET: its fields into the answer
    void write(Map<String, Object> asked, Exchange exchange) throws Exception;  // POST: validate and keep its own; a Refusal on a bad one
}
WebApp.named("DCSBuilder").settingsFields(new AssistantSettings())...
```

### 4.2 `/api/quit`, `/api/about`, `/api/log/open`

- `POST /api/quit`: `{"ok": true}`, the log line, then the server closed and the program exited, on a thread of its
  own so that the answer leaves first — both programs' behaviour.
- `GET /api/about`: `{"name", "version", "javaVersion", "language" (the machine's), "logFile"}` plus what the program
  adds (`WebApp.aboutFields(Map -> void)`): XsdViewer adds `"dialogs"`, which its page reads to enable its workspace
  menu. XsdViewer's `/api/capabilities` becomes `/api/about` — one constant in its page's `constants.js`.
- `POST /api/log/open`: the log file in the system's viewer (`DesktopOpener.open`), refused when the request does not
  come from this computer — DCSBuilder's, which XsdViewer's About can then offer too.

### 4.3 Logging

| | 1.1 |
|---|---|
| level | `--log-level LEVEL`; `--verbose` is `--log-level FINE` (XsdViewer's option, kept); `.ini` `log.level` |
| applies to | the logger `org.jtools` and everything under it — the framework's and the program's (DCSBuilder's bug: only `org.jtools.dcsbuilder.app` today). XsdViewer's `Log` keeps its facade (`debug`, `info`, `warn`) over `Logger.getLogger("org.jtools.xsdviewer")` instead of a logger named `xsdviewer` |
| file | `--log-file FILE`, else `.ini` `log.folder` (`none`: the console only — XsdViewer's key), else **`~/.<id>/logs/<id>.log`** — DCSBuilder's place; XsdViewer's `$TMPDIR` is emptied by the system and harder to find |
| rotation | JUL's own `FileHandler`: `<id>.%g.log`, 2 files of 5 MB |
| format | `2026-11-10 21:34:38 INFO    org.jtools… - message` (both programs' format) |
| language | **logs in English** (DCSBuilder's rule: a log is read by whoever helps, and searched); what the user reads — the console's usage and errors, the failure dialog — in their language (§4.4) |

### 4.4 Texts: `I18n`

DCSBuilder's `I18n` moves to `jtools-support`, generalised from one catalogue to one per program:

```java
public final class I18n {
    public static Catalogue catalogue(String bundle, Locale... languages);  // the first is the default
    public String t(String key, Object... args);       // plain {0} replacement: no MessageFormat, so "l'arbre" needs no ''
    public String tn(String key, int n, Object... args);  // key.one / key.other, by a per-language rule (fr: n <= 1)
    public boolean has(String key);
    public Locale locale();
    public static String message(Exception e);         // getMessage(), else toString()
}
public final class Catalogue {
    public I18n of(Locale locale);                     // unsupported: the default; cached
    public I18n of(String acceptLanguage);             // q-values honoured, fr-CA is fr — DCSBuilder's parsing
    public List<Locale> languages();
}
```

`WebApp.texts(catalogue)` names the program's: `Exchange.i18n()` and the console use it. The launcher's own texts
(usage, port in use, cannot start, not a file, the last page closed…) are a framework catalogue in en and fr; the
console's language is `--lang`, else the machine's. DCSBuilder keeps a two-line `I18n` facade of its own over its
catalogue so that its hundreds of `i18n.t(...)` calls stay as they are; XsdViewer's core keeps `Messages` (§0).

### 4.5 The command line

1.0's options, plus `--verbose`, `--log-file FILE`, `--lang LANG`, and the program's own (`option`, `flag`):
DCSBuilder's `--data DIR`, XsdViewer's none. A file operand: `WebApp.fileOperand(usage, help, Consumer<Path>)` — one
optional file, refused when it is not a regular file, both programs' rule. An unknown `--option` is refused, as
DCSBuilder does (XsdViewer takes it as a file name, then says it is not a file). Exit codes: **2** the command line
cannot be run (usage, not a file), **3** the program cannot start (port in use, I/O) — XsdViewer's, the more telling.

## 5. Native file dialogs (`jtools-desktop`, new)

XsdViewer's `FileDialogs`, as it is: kdialog or zenity on Linux (by `XDG_CURRENT_DESKTOP` and the PATH), AWT's
`FileDialog` on Windows and macOS, Swing's `JFileChooser` as the fallback; `available()` false when headless (a route
answers 409, both programs' convention for "no display"); the last folder remembered. Its titles and filters are the
caller's. A module of its own, so that a program without dialogs does not carry Swing in its jar's dependencies.

```java
public static boolean available();
public static List<Path> chooseFilesToOpen(String title, boolean multiple, Filter filter);
public static Optional<Path> chooseFileToSave(String title, Path folder, String name, Filter filter);
public static Optional<Path> chooseFolder(String title);
public record Filter(String description, List<String> globs) {}
```

## 6. XsdViewer on 1.1

**What goes** (lines measured, licence headers excluded: 1,175 in the files that go whole, about 200 more from the
three that shrink, estimated): `PageWatch` 93, `AliveHandler` 55,
`ByeHandler` 24, `QuitHandler` 28, `SettingsHandler` 83, `CapabilitiesHandler` 26, `StaticResourceHandler` 39,
`HttpResponses` 59, `HttpStatus` 13, `HttpMethod` 8, `ContentType` 36, `QueryString` 35, `JsonRequest` 29,
`BrowserLauncher` 28, `CommandLineOptions` 79, `ConfigFile` 129, `UserSettings` 45, `BuildInfo` 21, `FileDialogs` 345;
`Log` shrinks to its facade; `XsdViewerServer` (121) and `XsdViewerApplication` (98) to the sketch below.

**What stays**: core whole; the 15 schema handlers (their bodies change little: `requirePost` and `readBody` become
`post` and `ex.text()`); `ApiPath`'s schema routes; `ServedSchemaFiles`, `SchemaText`, `SchemaFolder`, `Workspace`;
its `dist` profile (jlink, three platforms — the parent's stays dormant: 1.2 candidate); its page, but for one
constant.

```java
public static void main(String[] args) {
    ServedSchemaFiles files = new ServedSchemaFiles();
    Path[] initial = new Path[1];
    WebApp.named("XsdViewer").defaultPort(8091).pageRoot("web")
          .fileOperand("[file.xsd]", Messages.get(MessageKey.CLI_FILE), file -> initial[0] = file)
          .aboutFields(about -> about.put("dialogs", FileDialogs.available()))
          .routes(server -> server
                  .filter(new CoreMessagesFilter())                    // §3.5
                  .refuse(SchemaException.class, 400)
                  .post(ApiPath.PARSE, ex -> Json.raw(SchemaParser.parse(ex.text()).toJson()))
                  .get(ApiPath.INITIAL, new InitialFileRoute(files, initial[0]))
                  /* … the 13 other schema routes … */)
          .launch(args);
}
```

**What its users will see change**: POSTs from another site and requests under a foreign `Host` are refused (new
protection); an unknown `--option` is refused instead of "not a file"; the log moves from `$TMPDIR` to
`~/.xsdviewer/logs` unless `log.folder` says otherwise, and its lines are in English; a 405 is JSON; static files get
ETags (a second load is mostly 304s); `/api/capabilities` is `/api/about`; exit codes: unchanged (2, 3; "not a file" 1
becomes 2).

## 7. DCSBuilder on 1.1

**What goes** (about 1,100 lines: 669 measured in the files that go whole, the share of the mixed files
estimated): `PageWatch` 92, `DesktopOpener` 47,
`Http` 104, `StaticFiles` 63, `UserPreferences` 20, `Installation` 30, `Json` 204 (the framework's is the same),
`I18n` 109 (to a facade); from `DcsBuilderServer` its `post`, `logged`, `handleAlive`, `handleBye`, `handleQuit`,
`stopAndExit`, `pageId` (about 120 of 273); from `Launcher` everything but `--data`, the MCP route and the language
model's log line (about 180 of 253); from `UserSettings` and `SettingsApi` their port and auto-stop halves (about 130).

**What stays**: every domain module; `ApiNames`' domain routes; `DocumentApi`, `SpifApi`, `StoreApi` (a prefix route),
`AgentApi`; `SettingsApi`'s assistant and marking-order fields (a `SettingsFields`); MCP (`server.handle("/mcp", …)`);
`Finding`; its "with model" distributions; its page, but for its GET helper (below).

```java
public static void main(String[] args) {
    WebApp.named("DCSBuilder").defaultPort(8092).pageRoot("web").texts(I18n.CATALOGUE)
          .option("--data", "DIR", ..., dir -> StoreApi.setStore(Path.of(dir)))
          .fileOperand("[policy.xml]", ..., DcsBuilderServer::setInitialFile)
          .settingsFields(new AssistantSettings())
          .routes(server -> { DcsBuilderServer.routes(server.refuse(Exception.class, 400));
                              server.handle(McpHttp.PATH, McpHttp.handler(new McpServer(StoreApi.store(), ...))); })
          .launch(args);
}
```

**What its users will see change**: `--log-level FINE` now shows the requests and their timings (the bug fixed); a
failure that is not the user's (an `IOException`) is a 500, the rest still 400; protocol answers and a bad
`/api/settings` are JSON; the log rotates by JUL (2 × 5 MB) instead of one rename at start. **A page fix rides along**:
its GETs (`/api/settings`, `/api/about`, `/api/store/…`) go through `fetch` without the page's language, so they
answer in the browser's — `state.js`'s `api()` should send `Accept-Language` on every call; with 1.1 the server side
of it is already right. Its Gradle build gets the framework's jars from Central, not jtools-parent's conventions (no
Gradle plugin yet).

## 8. 1.2 candidates — one-sided, or neither yet

- **jlink runtimes and macOS** in jtools-dist: XsdViewer's `runtimes.xml` (trimmed runtimes from the three JDKs'
  jmods), so that every program's distributions shrink and gain a macOS archive — XsdViewer could then drop its own
  `dist` profile.
- **A second launch opens the running one**: today both say "port in use" and exit. Probe `/api/about` on the port; if
  it is the same program, open the browser on it and hand it the file operand.
- **A vanilla-JS page module** (`jtools-page.js`): presence, `request()` with the page's language, quit, settings,
  about, `t()` — both pages carry their own 300 lines of it.
- **`jtools-testing`**: the catalogue checks both have (same keys and placeholders in en and fr, server and page), a
  test server on port 0 with JSON helpers.
- **@jtools/web sends the page's language** (an `HttpInterceptor`) — the Angular template's side of §4.4.
- **The served-files allow-list** (XsdViewer's `ServedSchemaFiles`): files the user chose are the only ones a route
  may read.
- **Gradle conventions** mirroring jtools-parent (DCSBuilder, WhichPaint).

## 9. Compatibility with 1.0, and the order of the work

If 1.0.0 is never published (§0.1), 1.1 is free; the changes a 1.0 user would meet are: `Route` takes an `Exchange`
(source-compatible for lambdas, not binary), the static fallback serves `index.html` for extension-less paths only,
protocol answers are JSON, `WebApp`'s port order gains the Settings step, the log goes to a file by default. If 1.0.0
must be published first, `Route(HttpExchange)` would have to stay beside a new route type, and the fallback rule
behind a switch.

The work, each step with its tests, the framework's CI (the generated project) green after each:

1. `Exchange`, answers, errors, prefix routes, raw handlers, filters, checks, static files (items 1–7) — `jtools-server`.
2. `I18n` and the launcher's catalogue (11), then `UserSettings`, the built-in routes and logging (8–10) — `jtools-launcher`.
3. `jtools-desktop` (12).
4. The template follows (its routes, its `.ini`), the docs (BUILD.md, ADOPTING.md), 1.1.0.
5. **The acceptance tests are the two programs' own**: XsdViewer moved on a branch passes its JUnit and its page's
   JS tests; DCSBuilder moved on a branch passes `DcsBuilderServerTest` (its protocol answers, ETags, the language of
   a 400), `I18nTest` and `FrontendTest`. A test of theirs that fails is a design fault here before it is a fault there.
