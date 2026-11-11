# JToolsFramework — orientation for Claude

A framework for local web applications in Java: libraries (`libraries/`), build conventions (`conventions/`), a
development environment (`devkit/`) and a project template (`starter/`). `README.md` is the map; `doc/README.md` the
reading path — GETTING-STARTED, CONCEPTS, LIBRARIES, CONVENTIONS, DEVKIT, ADOPTING —; `doc/design/` why it is as it is.

**It stands on its own**: nothing in it names or links a program built on it — no project's name, path or port, in
code, comments, tests, poms or docs. An example is a generic one (`Bookshelf`, `examples/hello`).

## Where things are

| Path | What |
|---|---|
| `conventions/parent/pom.xml` | jtools-parent: properties `jtools.app.*`, `jtools.app.skip`, profiles `jtools-frontend` (an `angular.json` switches it on), `dist`, `publish`; imports jtools-bom |
| `conventions/bom/pom.xml` | jtools-bom: the artifacts' versions; no parent (jtools-parent imports it), its own Central metadata and publish profile |
| `conventions/dist/src/main/resources/jtools-dist/` | `build.xml` (jdeps, jlink, the archives) and the launchers, `${jtools.app.*}` left for the project's build |
| `libraries/{support,server,app,desktop}/` | `Json`, `Catalogue`, `Texts`, `Installation`; `WebServer`, `Exchange`, `Answer`, `PageWatch`, `Http`; `WebApp`, `CommandLine` and `DesktopOpener` (package-private), `UserSettings`, `SettingsFields`, `LaunchOptions`, `LogFormatter`; `FileDialogs` — the JDK only; a `package-info.java` each |
| `libraries/web/src/lib/` | `@jtools/web` (shipped as sources in `jtools-web.jar`); `src/main.ts` is a harness, not shipped |
| `devkit/` | what a project carries — `managed-files.list`, `scripts/`, `.githooks/`, `.gitattributes`, `externals.conf` — plus `scripts/linux/jtools` and `scripts/windows/jtools.bat` (the stubs, never managed) and `jtools-sync.sh` / `.bat`; the starter has `new-project.sh` / `.bat`: every Windows twin is .bat only, kept in step with its shell script; `pom.xml` packs it with the starter as jtools-devkit |
| `starter/` | `new-project.sh` and `template/` (`__APP_NAME__`, `__APP_ID__`, `__APP_PORT__`, `__JTOOLS_VERSION__` in contents and paths) |
| `examples/hello/` | the smallest program; in the reactor, never published |
| `.github/workflows/delivery.yml` | at every push: the delivery built, then installed on Linux and on Windows, a project made, built, packaged, synced; Windows' project compared with Linux's |
| `.github/workflows/release.yml` | on a pushed tag, on GitHub's machines: `scripts/delivery.sh`, then the tag's release with the delivery attached (or *Run workflow* for an older tag) |
| `delivery/` | the delivery's `install.sh` and `install.bat` (Windows, .bat only) and README; `scripts/delivery.sh` packs them with the nine artifacts and the stub into `releases/delivery/jtools-<version>.tar.gz` |
| `scripts/` | the framework's own (`install-locally.sh`, `check-versions.sh`, `delivery.sh`; `linux/ci.local.sh`) and copies of the devkit's in `linux/` and `windows/`, `JTOOLS_HOME=.` |

## Commands

```bash
scripts/install-locally.sh          # versions agree, build, test, install into ~/.m2
scripts/linux/ci.sh                       # quiet install with npm ci, then ci.local.sh: scripts/ equal devkit/, and a
                                    # project made by a lone scripts/linux/jtools from the installed devkit passes its CI
scripts/linux/jtools sync [--check]       # the framework's own copies of the managed files, from devkit/
starter/new-project.sh Name [--port N] [--id id] [--dir D] [--bare D] [--no-git] [--no-build]
. scripts/linux/externals.sh && mvn -Ppublish clean verify -Dgpg.skip   # what Maven Central would get (PUBLISHING.md); never deploy unasked
```

## Conventions

- **A change to a managed file** is made in `devkit/`, copied into `scripts/linux/` and `scripts/windows/` by `scripts/linux/jtools sync`, verified by
  `scripts/linux/ci.sh`; projects take it with a new version.
- **The libraries stay generic**: nothing of any application in them.
- **One version** for every module, jtools-bom and `scripts/linux/jtools` (`scripts/check-versions.sh`).
- **Apache License 2.0** (`LICENSE`, `NOTICE`): the build writes the header on every Java source of the libraries
  (`license-maven-plugin`) — a new class gets it at its first build; a `package-info.java` carries it by hand, above
  its javadoc. Never on `starter/template`: the projects generated from it are their owners', under their terms.
- **Trials** go under `tmp/` or a scratch folder, with `--no-git`; the stub with `JTOOLS_CACHE` there, never the
  user's `~/.cache`. A trial run of a program: `--port 18000+`, `--log-file` in the scratch folder, its settings in a
  node of its own (`-D<id>.preferences=…`, removed after), and nothing that opens the desktop: `--no-browser`,
  `-Djava.awt.headless=true` for a failure's dialog, and never `POST /api/log/open`.

## Review rules

A code review answers one question: can a developer who has never seen this code read it and know where things belong?

1. **Modules with clear responsibilities.** Each unit does one thing that can be stated in one sentence without "and".
   Business rules live in the library (`core`), not in the HTTP plumbing that serves them.
2. **Objects a developer understands at a glance.** A name says the subject and the kind of object (a model class, a
   helper, a controller, a view, a query service, a writer). Junk-drawer names (`Utils`, `Common`, `Manager` alone)
   are a finding. One concept has one name across Java, TypeScript and the JSON contract.
3. **Method names are promises.** No `handle`, `process`, `run` without an object; no `validateAndSave`; no
   `get`/`is`/`find` that writes or mutates.
4. **Relationships are predictable.** Dependencies point one way (server → core, page → API).
5. **File size is a signal.** Over 500 lines, name the responsibilities inside; past 1000, report it regardless.

Comments: **a comment tells the intention** (why the code is as it is, the constraint that forced it), never what the
next line does; **a comment never tells the history** (no "was X", "now uses Y", dates, tickets). One or two lines
above a code group, up to about five for a class header; longer explanations go to `doc/` with a pointer.
Commit messages: a feature title in plain English.
