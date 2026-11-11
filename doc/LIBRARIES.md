# The libraries

**A program depends on `jtools-app`**: it brings `jtools-server` and `jtools-support`. `jtools-desktop` is added for
native file dialogs; `jtools-web` by an Angular page, and nothing else. The JDK and nothing else, all of them. Under
`jtools-parent` no version is written; elsewhere, `jtools-bom` says them (CONVENTIONS.md).

```xml
<dependency>
  <groupId>org.jtools.framework</groupId>
  <artifactId>jtools-app</artifactId>
</dependency>
```

Each library's javadoc opens on its package page: what it gives, the class to start from, a few lines of code. Below,
the same in short, and what is public only because another library or the JDK needs it.

## jtools-app — the program

`org.jtools.framework.app`. **Start from `WebApp`**: the program around the server.

```java
public static void main(String[] args) {
    WebApp.named("Bookshelf").defaultPort(8090)               // its id is "bookshelf": its jar, .ini, ~/.bookshelf
          .pageRoot("web")                                  // the page's classpath folder (static/ by default, an Angular build's)
          .option("--data", "DIR", "the folder of the data", dir -> data = Path.of(dir))
          .fileOperand("[file.xml]", "a file to open", file -> initial = file)
          .settingsFields(new MyFields())                   // the program's own fields of /api/settings
          .aboutFields(about -> about.put("dialogs", FileDialogs.available()))
          .routes(server -> MyApi.routes(server, business))
          .launch(args);
}
```

What `launch` does, so that the program does not have to:

- **the command line**: `--port N`, `--host H`, `--no-browser`, `--keep-alive`, `--log-level LEVEL`, `--verbose`,
  `--log-file FILE`, `--lang en|fr`, `--help`, and the program's own (`option`, `flag`, `operands`, `fileOperand`); an
  unknown option refused with the usage (exit 2), a server that cannot start (exit 3) — said in the person's language,
  in a dialog when there is no console, and in English in the log;
- **the port**, in this order: `--port`, the Settings menu's choice, `<id>.ini` beside the jar, the program's default;
- **the log**: the console and `~/.<id>/logs/<id>.0.log` (two files of 5 MB), in English — `org.jtools` and every logger
  under it at the level asked;
- **the built-in routes**: `GET`/`POST /api/settings` (`autoStop`, `port`, `defaultPort`, `currentPort`, and the program's
  fields), `GET /api/about` (`name`, `version`, `javaVersion`, `language`, `logFile`, and the program's), `POST /api/quit`,
  `POST /api/log/open` (the log in the system's viewer, for a page of this computer only);
- **the browser**, opened on the page; **the stop**, once the last page has been closed for 15 s.

`UserSettings` is what the Settings menu keeps (`java.util.prefs`, the node `org/jtools/<id>`, or the system property
`<id>.preferences` — the tests'); `SettingsFields` is how a program adds its own. `WebApp.start(args…)` starts the
program's server for its tests, as `launch` would, but reads no `.ini`, sets up no log, opens no browser and never exits.

Public for a technical reason: `LaunchOptions` (the options' and the `.ini`'s names), `LogFormatter`
(`java.util.logging` makes it from its name).

## jtools-server — the server

`org.jtools.framework.server`. **Start from `WebServer`**; a program built with jtools-app gets it in `routes`.

```java
server.get("/api/books", ex -> books.list())                         // a List, a Map: JSON
      .post("/api/books", ex -> books.add(ex.jsonObject()))          // the body as a JSON object; malformed: 400
      .get("/api/store/", ex -> store.read(ex.rest()))               // a prefix route: ex.rest() is what follows
      .get("/api/report", ex -> Answer.xml(report.xml()))           // anything else: an Answer
      .refuse(UnknownBookException.class, 404)                       // an exception of the domain, mapped to a status
      .handle("/mcp", rawHandler)                                    // a plain JDK handler, the checks still applied
      .filter(languageFilter);                                       // a JDK Filter around every request
```

- **A route answers with what it returns**: a `Map` / `List` / `String` / `Number` tree as JSON, `Json.raw(text)` for JSON
  already written, an `Answer` (bytes, XML, text, a status, headers), `null` for 204.
- **A refusal is `{"error": reason}`**, whatever refuses: `throw new WebServer.Refusal(status, reason)`, an
  `IllegalArgumentException` (400), an exception mapped with `refuse`, a wrong method (405, with `Allow`), no such
  route (404); anything else is a 500, its trace in the log.
- **`Exchange`**, a route's request: `text()`, `bytes()`, `json()`, `jsonObject()`, `query(name[, fallback])`, `rest()`,
  `language()` (the page's, from `Accept-Language`), `texts()` (the program's texts in it), `fromThisComputer()`.
- **Every request is checked first**: a `Host` that is not this computer's (or the `--host` given) is refused — the
  defence against DNS rebinding —, and so is a request changing state from another site's `Origin`.
- **The page**: the files of the page's classpath folder, with an ETag (304 when unchanged); `index.html` for a path
  without an extension (an Angular page's own routes), a 404 for a missing file.
- **The presence**: `GET /api/alive?id=` (a stream the page holds), `POST /api/bye?id=` (its beacon); `PageWatch`
  counts the pages and runs the stop.

Public for a technical reason: `Http` (the content types an `Answer` names: `Http.TYPE_JSON`, `TYPE_XML`, `TYPE_ZIP`…).

## jtools-support — what every module leans on

`org.jtools.framework.support`. The JDK and nothing else, so that a project's business module can use it too.

- **`Json`**: a `Map` / `List` / `String` / `Number` / `Boolean` / `null` tree to text and back; `Json.obj()` for an
  ordered map, `Json.raw(text)` for JSON another writer already wrote.
- **`Catalogue`** and **`Texts`**: a program's texts in each of its languages — the catalogue loads them, `of`
  gives one language's `Texts` —, `i18n/messages.properties` and
  `messages_fr.properties` in UTF-8; `{0}` placeholders without `MessageFormat`'s quoting, `tn` for plurals, an
  `Accept-Language` header honoured (q-values, `fr-CA` is `fr`), a missing key answered as itself.

```java
Catalogue catalogue = Catalogue.load(MyApp.class, "i18n.messages", Locale.ENGLISH, Locale.FRENCH);
catalogue.of(ex.language()).tn("books.count", n);   // books.count.one / books.count.other
```

- **`Installation`**: the folder of the program's jar, its version (the manifest's), its `.ini`.

## jtools-desktop — native file dialogs

`org.jtools.framework.desktop`. **`FileDialogs`**: open, save and folder dialogs on the program's own desktop —
kdialog or zenity on Linux, the system's on Windows and macOS, Swing's otherwise. `available()` is false without a
display: a route then answers 409, and the page offers the browser's own. A library of its own: choosing files is a feature
a program opts into.

```java
List<Path> chosen = FileDialogs.chooseFilesToOpen("Open a book list", true, new FileDialogs.Filter("Book lists", List.of("*.csv")));
```

## jtools-web — the Angular page's side

`@jtools/web`, TypeScript compiled with the project's page (jtools-parent unpacks it into `frontend/target/jtools-web`,
`tsconfig.json`'s `paths` maps it): `provideJTools()` (the presence, started with the page), `PagePresenceService`,
`apiErrorMessage()` (a refusal's `{"error"}` as a sentence). A page in plain JavaScript needs none of it — the
presence is three lines ([examples/hello](../examples/hello)).

---

The three examples use these libraries step by step: [../examples](../examples). Next: what your build inherits —
[CONVENTIONS.md](CONVENTIONS.md).
