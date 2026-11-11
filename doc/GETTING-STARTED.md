# Getting started

Eight steps, about fifteen minutes: the examples run, your own project made, run, a feature added from the business to
the page, tested, packaged, kept up to date. Each step says what to type, what you should see, and what to do when you
don't.

## 0. What you need

Linux, macOS, or Windows with Git Bash; `bash`, `curl`, `unzip`, `tar` and `git`. **Nothing else** — no Java, Maven
nor Node.js to install: a project downloads its own into its `external/` folder, the first time, about 450 MB.

```bash
for tool in bash curl unzip tar git; do command -v $tool >/dev/null || echo "missing: $tool"; done
```

*You should see*: nothing. A `missing:` line names what to install first, with your system's package manager.

## 1. Install JToolsFramework

One archive, the framework and its installer — no clone, nothing built:

```bash
mkdir jtools-start && cd jtools-start
curl -fsSLO https://github.com/j4ckk0/JToolsFramework/releases/download/v1.3.0/jtools-1.3.0.tar.gz
tar xzf jtools-1.3.0.tar.gz
jtools-1.3.0/install.sh --no-project
jtools --help
```

*You should see* `== JToolsFramework 1.3.0 in …/.m2/repository`, `== jtools in …/.local/bin`, then `jtools`'s commands:
`sync`, `version`, `new`. *If `jtools` is not found*: `~/.local/bin` is not on your `PATH` — the installer says the
line to add to `~/.bashrc`; until then, `~/.local/bin/jtools`.

`install.sh` without `--no-project` goes on with step 3 at once: it asks the project's name and the folder its files go
in (`install.sh Bookshelf --dir ~/work/bookshelf` says them).

*To read and change the framework too*, take it from a clone instead, installed into your local Maven repository:

```bash
git clone https://github.com/j4ckk0/JToolsFramework.git
(cd JToolsFramework && scripts/setup.sh && scripts/install.sh)   # the framework built, tested, installed into ~/.m2
alias jtools=$PWD/JToolsFramework/devkit/scripts/jtools
export JTOOLS_DOWNLOADS=$PWD/JToolsFramework/external/downloads   # your project takes the clone's downloads: 450 MB once, not twice
```

## 2. Run the examples (optional, five minutes)

From a clone, after `scripts/install.sh` (step 1's second way): three very small programs, each one step further —
[examples/README.md](../examples/README.md).

```bash
JToolsFramework/external/jdk/bin/java -jar JToolsFramework/examples/hello/target/hello.jar
```

*You should see* a page in your browser, "Hello"; close it, and 15 seconds later the program has stopped. `todo.jar`
and `greeter.jar` beside it are the next two.

## 3. Make your project

```bash
jtools new Bookshelf
```

*You should see*, after a minute or two the first time (the downloads):

```
== Bookshelf (bookshelf, port 8090) in …/jtools-start/Bookshelf, from JToolsFramework 1.3.0
== a git repository, its first commit made
== external/jdk deployed
== external/maven deployed
== external/node deployed
…
== built: app/target/bookshelf.jar
== Bookshelf ready in …/jtools-start/Bookshelf: scripts/run.sh starts it
```

`--port N` for another port than 8090, `--dir DIR` for another folder, `--no-build` to build later: `jtools new --help`.

What it made:

```
Bookshelf/
├── core/       the business: Bookshelf.java, pure Java — every rule, every computation, every state
├── api/        its HTTP face: BookshelfApi.java — each endpoint asks core and turns its answer into JSON
├── frontend/   the Angular page: a view, it decides nothing — api.service.ts is its one door to the server
├── app/        the program: Launcher.java, a WebApp serving the API; its one jar and its distributions
├── pom.xml     the modules and five jtools.app.* properties; everything else is jtools-parent's
├── jtools.conf, externals.conf     what the scripts know of the project, the externals' versions
└── scripts/    setup, build, ci, run, stop, package (the devkit's) and jtools
```

## 4. Run it

```bash
cd Bookshelf
scripts/run.sh
```

*You should see* the page in your browser, and on the console:

```
… INFO    org.jtools.framework.app.WebApp - Bookshelf 0.1.0 is ready on http://127.0.0.1:8090/; it stops when its last page is closed (or Ctrl+C); log: ~/.bookshelf/logs/bookshelf.0.log
```

Close the page: 15 seconds later the program stops. `scripts/run.sh --keep-alive` keeps it serving (`scripts/stop.sh`
stops it then); `scripts/run.sh --help` lists every option.

*If "port 8090 is in use"*: another program has it — `scripts/run.sh --port 8091`, or the Settings menu's port.

## 5. Add a feature, from the business to the page

The page will show how many books there are. Each layer does its part, and only its part.

**The business** decides — `core/src/main/java/org/jtools/bookshelf/core/Bookshelf.java`, a method beside
`status()`:

```java
public int bookCount() {
    return 3;   // the business decides; here, a stand-in
}
```

**The API** translates — `api/src/main/java/org/jtools/bookshelf/api/BookshelfApi.java`: a path beside `STATUS`,
an endpoint beside its own.

```java
public static final String BOOKS = WebServer.API + "/books";

public static WebServer endpoints(WebServer server, Bookshelf application) {
    return server.get(STATUS, ex -> status(application.status()))
                 .get(BOOKS, ex -> Map.of("count", application.bookCount()));
}
```

An endpoint answers with what it returns — a `Map` or a `List` as JSON, `null` as 204. A request it refuses throws
`new WebServer.Refusal(400, reason)`, and the page reads `{"error": reason}` — [examples/todo](../examples/todo)
shows one.

**The page** shows — its one door to the server, `frontend/src/app/api.service.ts`, a method beside `status()`:

```typescript
books(): Observable<{ count: number }> {
  return this.http.get<{ count: number }>('api/books');
}
```

and the view, `frontend/src/app/home/home.ts`, in place of what it holds:

```typescript
import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';
import { ApiService } from '../api.service';

@Component({ selector: 'app-home', template: `<p>{{ count() }} books</p>` })
export class Home {
  readonly count = toSignal(inject(ApiService).books().pipe(map((b) => b.count)));
}
```

`scripts/run.sh` again: *you should see* "3 books" on the page — `scripts/run.sh` rebuilds when a source is newer.

## 6. Test it

```bash
scripts/build.sh
```

*You should see* `BUILD SUCCESS`, the Java tests of `core`, `api` and `app`, and the page's (vitest) passed. The
generated `BookshelfApiTest` shows an endpoint's test: the API's endpoints on a `WebServer` started on a free port, asked
over HTTP. The whole program starts the same way, as `main` would but never exiting —
`WebApp.start("--port", "0", "--keep-alive")`, as the examples' tests do.

## 7. Ship it

```bash
scripts/package.sh
```

*You should see*, a minute later, in `releases/`:

```
bookshelf-0.1.0-linux.tar.gz         about 38 MB
bookshelf-0.1.0-macos.tar.gz         about 34 MB
bookshelf-0.1.0-windows.zip          about 34 MB
```

Each holds the jar, its launchers (`bookshelf.sh`; `Bookshelf.exe` and `bookshelf.bat`), its `.ini` and a
Java runtime trimmed to what the program needs: unpack, run, nothing to install.

## 8. Keep up to date

```bash
scripts/jtools sync
```

*You should see* `… is in line with JToolsFramework 1.3.0`. A new version of the framework: `JTOOLS_VERSION` in
`jtools.conf` and the parent's `<version>` in `pom.xml`, then `scripts/jtools sync` (the scripts) and `scripts/ci.sh`
(every test) — and commit.

## If something goes wrong

| What you see | What to do |
|---|---|
| `jtools: download failed: … (JTOOLS_VERSION=1.3.0: is there such a version?)` | the framework is neither in `~/.m2` nor on Maven Central: step 1's `install.sh` |
| a download that never ends, or is refused, behind a company's proxy | a mirror: `MIRROR=` in `externals.local.conf`, its token beside it ([DEVKIT.md](DEVKIT.md)) |
| `external/ is not set up: missing …` | `scripts/setup.sh`, which the scripts offer to run |
| `Port 8090 is in use: is Bookshelf already running?` | it is — open the page it names, or `scripts/stop.sh`; or `--port` |
| a build refused because the program is running | quit it from its page, or `scripts/stop.sh` |

## Next

[CONCEPTS.md](CONCEPTS.md) — the idea behind what you just did, in five minutes. Then [LIBRARIES.md](LIBRARIES.md),
[CONVENTIONS.md](CONVENTIONS.md), [DEVKIT.md](DEVKIT.md): the path is [README.md](README.md).
