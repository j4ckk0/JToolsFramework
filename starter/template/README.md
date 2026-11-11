# __APP_NAME__

A local web application: a **Java server** holds the whole business, and an **Angular page** in the browser is its
view — it shows what the server answers and sends what the user asks, and decides nothing.

The browser is the program's window: `scripts/linux/run.sh` starts the server on `http://127.0.0.1:__APP_PORT__/` and opens
the page; **closing the last page stops the server** (15 seconds later, so that a reload does not). `--keep-alive`
keeps it serving.

Built on [JToolsFramework](https://github.com/j4ckk0/JToolsFramework) __JTOOLS_VERSION__: its parent pom carries the build,
its libraries the server and the program (`jtools-app`), `@jtools/web` the page's side, and its devkit the scripts
(see [Build and run](#build-and-run)).

## First steps

1. **Run it**: `scripts/linux/run.sh` — the page opens; close it, and the program stops.
2. **Add a feature**: a rule in `core` (`__APP_NAME__.java`), an endpoint in `api` (`__APP_NAME__Api.java`) that asks it, a
   method in `frontend/src/app/api.service.ts` that calls the endpoint, a view that shows it — JToolsFramework's
   `doc/GETTING-STARTED.md`, step 5, walks through one; `doc/ARCHITECTURE.md` here says where each kind of code goes.
3. **Test it**: `scripts/linux/build.sh` — every Java test and the page's.
4. **Ship it**: `scripts/linux/package.sh` — the Linux, macOS and Windows archives in `releases/`, each with its own Java.
5. **Keep it up to date**: `scripts/linux/jtools sync`, at the `JTOOLS_VERSION` of `jtools.conf`.

## Build and run

Everything the build needs comes from `external/` (see [Externals](#externals)): run `scripts/linux/setup.sh` once — the
other scripts offer to when it has not been run. JToolsFramework comes from Maven Central, or from the local Maven
repository where a clone of it puts it (its `scripts/install-locally.sh`).

```bash
scripts/linux/setup.sh                 # the externals: JDK 25, Maven, Node.js, each platform's jmods (once)
scripts/linux/build.sh                 # mvn clean package: app/target/__APP_ID__.jar, every test (Java and the page's)
scripts/linux/build.sh -DskipTests     # extra arguments go to mvn
scripts/linux/ci.sh                    # quiet, the page's dependencies exactly as package-lock.json has them (npm ci)
scripts/linux/run.sh                   # build if needed, start, open the browser
scripts/linux/stop.sh                  # stop the servers still running (--keep-alive, --no-browser)
scripts/linux/package.sh               # the distributions, into releases/
```

On Windows, the same scripts end in `.bat`. The CI runs before every push once the hooks are enabled:
`git config core.hooksPath .githooks`.

### Running

```
scripts/linux/run.sh [--rebuild] [--port N] [--host H] [--no-browser] [--keep-alive] [--log-level LEVEL | --verbose]
               [--log-file FILE] [--lang LANG]
```

`scripts/linux/run.sh --help` says what each does. The port is the command line's, else the one chosen from the page's
Settings menu (`/api/settings`), else `__APP_ID__.ini`'s next to the jar (`port=__APP_PORT__`). The log goes to
`~/.__APP_ID__/logs/__APP_ID__.0.log` (`--log-file`, or `log.folder` in the `.ini`), its lines in English.

The framework answers four endpoints of its own beside the application's: `/api/settings` (the automatic stop, the
port), `/api/about` (name, version, log file), `/api/quit` and `/api/log/open`.

**The page alone, live-reloaded**: start the server (`scripts/linux/run.sh --no-browser`), then `cd frontend && npm start`
serves the page on `http://localhost:4200/`, sending `/api` to the server (`frontend/proxy.conf.json`).

## Externals

Nothing the build needs is under source control nor taken from the machine: `scripts/linux/setup.sh` downloads it into
`external/` (ignored by git), as `externals.conf` names it — the versions live there and nowhere else.

| `external/` | What it is |
|---|---|
| `jdk/` | Temurin JDK 25 (every script's `JAVA_HOME`) |
| `maven/` | Maven, the build |
| `node/` | Node.js and npm, the page's build (run by Maven) |
| `jdk-linux.tar.gz` | the same JDK as its archive: the `jlink` that links the distributions' runtimes |
| `jmods-linux.tar.gz`, `jmods-windows.zip`, `jmods-macos.tar.gz` | each platform's jmods, which the runtimes are linked from |

## Packaging

`scripts/linux/package.sh` builds three self-contained distributions — nothing to install, a Java runtime inside, which
jlink trims to the modules the program needs: `releases/__APP_ID__-<version>-linux.tar.gz` and `-macos.tar.gz` (Apple
silicon; `__APP_ID__.sh`), and `releases/__APP_ID__-<version>-windows.zip` (`__APP_NAME__.exe` without a console,
`__APP_ID__.bat` with one). A program formatting dates or numbers in French adds `jdk.localedata` to
`jtools.dist.modules.extra` in `pom.xml`: jdeps cannot see it, and without it the runtime formats them as in English. The version is said once, as `<revision>` in
`pom.xml`, with its four-part form `jtools.app.windowsVersion` for the `.exe`. Files the distributions carry besides
go in `app/src/dist/files/`.

## Layout

```
pom.xml                  the modules, the program's jtools.app.* properties (the rest: jtools-parent)
jtools.conf              what the devkit's scripts know of this project, and the devkit's version
externals.conf           the externals' versions and URLs
scripts/, .githooks/     the devkit's (jtools sync refreshes them): scripts/linux/ for Linux, scripts/windows/ for Windows;
                         their jtools and jtools.bat, which fetch the devkit, are yours
doc/ARCHITECTURE.md      the modules, and where a new feature goes
core/      __APP_NAME__: the business, pure Java
api/       __APP_NAME__Api: the HTTP API over core
frontend/  the Angular page (src/app/api.service.ts: the API's TypeScript mirror)
app/       Launcher: the program
```
