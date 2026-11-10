# JToolsFramework

What the org.jtools projects — DCSBuilder, WhichPaint, XsdViewer, MythoStory, SensorSimulator — have in common,
written once instead of copied into each: **a Java server holding the whole business, an Angular page in the browser
as its view, the program stopping once its last page is closed, self-contained distributions with a JRE inside**.

## In one minute

```bash
scripts/setup.sh                          # the externals: JDK 21, Maven, Node.js, the JREs (once)
scripts/install.sh                        # build, test and install the framework into ~/.m2
scripts/new-project.sh SensorMonitor      # a new project in ../SensorMonitor, on the next free port, its bare
                                          # repository in ../../git-bare/SensorMonitor.git, built, committed, pushed
cd ../SensorMonitor && scripts/run.sh     # the page in the browser; close it and the program stops
```

The generated project is four modules — `core` (the business), `api` (its HTTP face), `frontend` (the Angular view),
`app` (the program) — whose poms say only what is theirs; its `Launcher` is a few lines.

## What is in it

| | What | Where it came from |
|---|---|---|
| `parent/` | **jtools-parent**: Java 21, JUnit 5, the plugins; the page built by npm in any module with an `angular.json`; the program's one jar; with `-Pdist` the Linux and Windows distributions, each with its JRE, `.exe`, launchers and `.ini` ([doc/BUILD.md](doc/BUILD.md)) | the poms of DCSBuilder, MythoStory, WhichPaint |
| `support/` | **jtools-support**: `Json`, `Installation` (home, version, `.ini`) | DCSBuilder, SensorSimulator |
| `server/` | **jtools-server**: `WebServer` (JSON routes, the single-page application's files, the presence), `PageWatch` (the stop), `Http`, `DesktopOpener` | the four `PageWatch`es, the three servers |
| `launcher/` | **jtools-launcher**: `WebApp` — `--port --host --no-browser --keep-alive --log-level --help`, the program's own options, the `.ini`, the logging, the browser, the stop, the failure dialog of an `.exe` | the four launchers |
| `dist/` | **jtools-dist**: the assembly descriptors, `app.sh`, `app.bat`, `app.ini` | the five `src/assembly` and `src/dist` |
| `web/` | **@jtools/web**: `provideJTools()`, `PagePresenceService`, `apiErrorMessage()` — TypeScript, compiled with the project's page | SensorSimulator, MythoStory |
| `scripts/` | the managed scripts every project gets — `setup build ci run stop package` (`.sh`, `.bat`) — and the framework's tools: `install`, `new-project`, `jtools-sync` ([doc/SCRIPTS.md](doc/SCRIPTS.md)) | the five `scripts/` folders, which had drifted apart |
| `templates/webapp/` | the project `new-project.sh` writes | SensorSimulator |
| `doc/` | [PATTERN.md](doc/PATTERN.md) (the conventions, and why), [BUILD.md](doc/BUILD.md), [SCRIPTS.md](doc/SCRIPTS.md), [ADOPTING.md](doc/ADOPTING.md) (what each existing project can take), [PORTS.md](doc/PORTS.md) (the registry) | |

## Decisions of this version

- **One distribution channel, Maven**: the framework is in `~/.m2` (`scripts/install.sh`); another machine gets it
  from the Artifactory the externals' `MIRROR` already names ([doc/BUILD.md](doc/BUILD.md)). `@jtools/web` travels in
  a Maven jar, as sources — no npm package to publish.
- **The scripts are copied into each project, not fetched**: a clone plus `scripts/setup.sh` is enough, as always.
  `jtools-sync.sh` keeps the copies equal to the framework's; the framework's CI regenerates a project from the
  template and runs that project's CI, so the template never rots.
- **One name for each thing that had several**: `--keep-alive` (`--keep-running` still understood), a 15 s grace,
  a 5 s ping, `--no-browser` keeping the program running — what three of the five projects did.
- **Maven-first**: no Gradle convention plugin yet; a project wanting Gradle keeps its own build beside.
- **Apache License 2.0**, like XsdViewer and DocComparator: [LICENSE](LICENSE), [NOTICE](NOTICE), the licence and
  the project's metadata in jtools-parent (what Maven Central requires), the Apache header on every Java source
  (`license-maven-plugin`, as in the published jtools projects). It is to be published on GitHub
  (<https://github.com/j4ckk0/JToolsFramework>) and Maven Central; until then the projects find it in `~/.m2`.

## Build and run

```bash
scripts/setup.sh       # the externals (externals.conf: the reference new projects copy)
scripts/install.sh     # check the versions, build, test, install into ~/.m2
scripts/ci.sh          # the same quietly, with npm ci, then a project generated from the template and its CI
```

`git config core.hooksPath .githooks` runs `scripts/ci.sh` before every push. The `.bat` scripts are the projects'
(Windows developers); the framework's own tools are Linux scripts.
