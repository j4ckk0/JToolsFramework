# __APP_NAME__

A local web application: a **Java server** holds the whole business, and an **Angular page** in the browser is its
view — it shows what the server answers and sends what the user asks, and decides nothing.

The browser is the program's window: `scripts/run.sh` starts the server on `http://127.0.0.1:__APP_PORT__/` and opens
the page; **closing the last page stops the server** (15 seconds later, so that a reload does not). `--keep-alive`
keeps it serving.

Built on [JToolsFramework](__JTOOLS_HOME__) __JTOOLS_VERSION__: its parent pom carries the build, its libraries the
server and the launcher, `@jtools/web` the page's side, and it owns the scripts (see [Scripts](#build-and-run)).

## Build and run

Everything the build needs comes from `external/` (see [Externals](#externals)): run `scripts/setup.sh` once — the
other scripts offer to when it has not been run. JToolsFramework must be in the local Maven repository: its
`scripts/install.sh` puts it there.

```bash
scripts/setup.sh                 # the externals: JDK 21, Maven, Node.js, the JREs (once)
scripts/build.sh                 # mvn clean package: app/target/__APP_ID__.jar, every test (Java and the page's)
scripts/build.sh -DskipTests     # extra arguments go to mvn
scripts/ci.sh                    # quiet, the page's dependencies exactly as package-lock.json has them (npm ci)
scripts/run.sh                   # build if needed, start, open the browser
scripts/stop.sh                  # stop the servers still running (--keep-alive, --no-browser)
scripts/package.sh               # the distributions, into releases/
```

On Windows, the same scripts end in `.bat`. The CI runs before every push once the hooks are enabled:
`git config core.hooksPath .githooks`.

### Running

```
scripts/run.sh [--rebuild] [--port N] [--host H] [--no-browser] [--keep-alive] [--log-level LEVEL | --verbose]
               [--log-file FILE] [--lang LANG]
```

`scripts/run.sh --help` says what each does. The port is the command line's, else the one chosen from the page's
Settings menu (`/api/settings`), else `__APP_ID__.ini`'s next to the jar (`port=__APP_PORT__`). The log goes to
`~/.__APP_ID__/logs/__APP_ID__.0.log` (`--log-file`, or `log.folder` in the `.ini`), its lines in English.

The framework answers four routes of its own beside the application's: `/api/settings` (the automatic stop, the
port), `/api/about` (name, version, log file), `/api/quit` and `/api/log/open`.

**The page alone, live-reloaded**: start the server (`scripts/run.sh --no-browser`), then `cd frontend && npm start`
serves the page on `http://localhost:4200/`, sending `/api` to the server (`frontend/proxy.conf.json`).

## Externals

Nothing the build needs is under source control nor taken from the machine: `scripts/setup.sh` downloads it into
`external/` (ignored by git), as `externals.conf` names it — the versions live there and nowhere else.

| `external/` | What it is |
|---|---|
| `jdk/` | Temurin JDK 21 (every script's `JAVA_HOME`) |
| `maven/` | Maven, the build |
| `node/` | Node.js and npm, the page's build (run by Maven) |
| `jdk-linux.tar.gz`, `jdk-windows.zip`, `jdk-macos.tar.gz` | the Temurin JDKs the distributions' runtimes are linked from (jlink) |

## Packaging

`scripts/package.sh` builds three self-contained distributions — nothing to install, a Java runtime inside, which
jlink trims to the modules the program needs: `releases/__APP_ID__-<version>-linux.tar.gz` and `-macos.tar.gz` (Apple
silicon; `__APP_ID__.sh`), and `releases/__APP_ID__-<version>-windows.zip` (`__APP_NAME__.exe` without a console,
`__APP_ID__.bat` with one). A program formatting dates or numbers in French adds `jdk.localedata` to
`jtools.dist.modules.extra` in `pom.xml`: jdeps cannot see it, and without it the runtime formats them as in English. The version is said once, as `<revision>` in
`pom.xml`, with its four-part form `jtools.app.windowsVersion` for the `.exe`. Files the distributions carry besides
go in `app/src/dist/files/`.

## Layout

```
pom.xml                  the modules, the program's jtools.app.* properties (the rest: jtools-parent)
jtools.conf              what the framework's scripts know of this project
externals.conf           the externals' versions and URLs
scripts/, .githooks/     the framework's (scripts/jtools-sync.sh in JToolsFramework refreshes them)
doc/ARCHITECTURE.md      the modules, and where a new feature goes
core/      __APP_NAME__: the business, pure Java
api/       __APP_NAME__Api: the HTTP API over core
frontend/  the Angular page (src/app/api.service.ts: the API's TypeScript mirror)
app/       Launcher: the program
```
