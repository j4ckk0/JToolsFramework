# __APP_NAME__ — orientation for Claude

The documentation is `README.md` (build, run, externals, packaging) and `doc/ARCHITECTURE.md` (modules, API, where a
feature goes). This project is built on JToolsFramework __JTOOLS_VERSION__ (https://github.com/j4ckk0/JToolsFramework): its
`doc/CONCEPTS.md` holds the conventions every project on it keeps.

## What it is

A local web application: a Java 25 server (JToolsFramework's `WebServer`, the JDK's own HttpServer) holds the whole
business; an Angular page is its view only. It serves `http://127.0.0.1:__APP_PORT__/`, opens the browser, and stops
15 s after its last page is closed (`--keep-alive` or `--no-browser` keep it running).

## Where things are

| Module | Key files |
|---|---|
| `core/` the business, pure Java | `__APP_NAME__.java` (the one entry point) |
| `api/` | `__APP_NAME__Api.java` (`endpoints(server, application)`: core → JSON) |
| `frontend/` Angular, signals, standalone, zoneless | `src/app/api.service.ts` (TS mirror of the API), `app.ts`, `app.routes.ts`, `home/`; `@jtools/web` is unpacked in `target/jtools-web` |
| `app/` | `Launcher.java` (a `WebApp` with the API's endpoints) |

## Commands

```bash
scripts/setup.sh [--force]     # externals into external/ (JDK 25, Maven, Node 24, each platform's jmods)
scripts/build.sh [-DskipTests] # clean build, every test (JUnit + vitest)
scripts/ci.sh                  # quiet, npm ci; the pre-push hook runs it
scripts/run.sh [--rebuild] [--port N] [--no-browser] [--keep-alive]
scripts/stop.sh [--port N]
scripts/package.sh             # releases/__APP_ID__-<version>-{linux.tar.gz,windows.zip}
```

## Conventions

- **The page decides nothing**: business goes in `core`, `api` only translates, the page only shows.
- **The scripts, `.githooks/` and `.gitattributes` are the devkit's**: never edited here — `scripts/jtools sync`
  refreshes them at the `JTOOLS_VERSION` of `jtools.conf`. Project-only extras go in `scripts/*.local.sh`.
- **Versions** are set once: the program's in the root `pom.xml` (`<revision>`, `jtools.app.windowsVersion`), the
  framework's as the parent's version, the externals' in `externals.conf`.
- **Port** __APP_PORT__: `Launcher.DEFAULT_PORT`, `jtools.app.port`, `frontend/proxy.conf.json` (LauncherTest checks).
- **Style**: comments and docs in plain explanatory prose stating intent. Commit messages: a feature title in plain
  English.
- `tmp/` is gitignored scratch space. Test servers use port 0 or 18000+.
