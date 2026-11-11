# JToolsFramework

For a **local web application in Java**: a server on your computer holds the whole business, a page in the browser is
its view, and the program stops once its last page is closed — like any program whose window you close. It ships as
self-contained archives for Linux, macOS and Windows, a Java runtime inside.

JToolsFramework is four things, each with a door below:

- **a small framework** — the libraries a program's code calls: the server, the routes, the command line, the
  settings, the log, the stop (`jtools-app`, the JDK and nothing else);
- **its build conventions** — the parent pom a project inherits: Java 21, the tests, the program's one jar, the
  distributions with a runtime jlink trims (`jtools-parent`);
- **a development environment** — the scripts a project carries (setup, build, run, package…), its tools downloaded
  into the project, never taken from the machine (`jtools-devkit`);
- **a project template** — the way a new project begins (`jtools new`).

Apache License 2.0. Version 1.2.0; on Maven Central once published (until then, `scripts/install.sh` puts it in
`~/.m2`). [doc/CONCEPTS.md](doc/CONCEPTS.md) says the idea behind it in one page.

## Start an app

```bash
jtools new SensorMonitor        # ./SensorMonitor: four modules, its scripts, a git repository, built
cd SensorMonitor
scripts/run.sh                  # the page in the browser; close it, and the program stops
```

`jtools` is one script, the devkit's [`scripts/jtools`](devkit/scripts/jtools): download it, or take it from a clone of
this repository. [doc/GETTING-STARTED.md](doc/GETTING-STARTED.md) walks through the generated project — where the
business goes, a feature added from core to the page, tested, packaged — in ten minutes.

## Use the libraries

A program depends on **`jtools-app`**, which brings `jtools-server` and `jtools-support`:

```java
public static void main(String[] args) {
    WebApp.named("Hello").defaultPort(8089).pageRoot("web")
          .routes(server -> server.get("/api/hello", ex -> Map.of("message", "Hello, " + ex.query("name", "world"))))
          .launch(args);
}
```

That is a whole program: `--port`, `--help`, its `.ini`, the log, the Settings menu, Quit and the stop come with it.
[examples/hello](examples/hello) is that program with its page in plain JavaScript; [doc/LIBRARIES.md](doc/LIBRARIES.md)
says what each library gives — `jtools-desktop` for native file dialogs, `jtools-web` for an Angular page.

## Build, run and ship it

A project's root pom names **`jtools-parent`** as its parent and says five things of its program — `jtools.app.id`,
`name`, `mainClass`, `port`, `windowsVersion`:

```xml
<parent>
  <groupId>org.jtools.framework</groupId>
  <artifactId>jtools-parent</artifactId>
  <version>1.2.0</version>
  <relativePath/>
</parent>
```

The build then makes the program's one jar, and with `-Pdist` its distributions ([doc/CONVENTIONS.md](doc/CONVENTIONS.md));
a Gradle build takes `jtools-bom`'s versions instead. The project's scripts — `setup`, `build`, `ci`, `run`, `stop`,
`package`, in `.sh` and `.bat` — are the devkit's, kept up to date by `scripts/jtools sync` at the version
`jtools.conf` names ([doc/DEVKIT.md](doc/DEVKIT.md)).

## What is where

| Folder | What | Published as |
|---|---|---|
| [`libraries/`](libraries) | `support`, `server`, `app`, `desktop`: the Java libraries; `web`: `@jtools/web`, for an Angular page | `jtools-support`, `-server`, `-app`, `-desktop`, `-web` |
| [`conventions/`](conventions) | `parent`: the build; `bom`: the versions alone; `dist`: the distributions' build (jdeps, jlink, the archives) and launchers | `jtools-parent`, `jtools-bom`, `jtools-dist` |
| [`devkit/`](devkit) | what a project carries: its scripts, its pre-push hook, `.gitattributes`, the default `externals.conf`; `scripts/jtools` | `jtools-devkit`, with the starter |
| [`starter/`](starter) | the template and `new-project.sh`, which `jtools new` runs | in `jtools-devkit` |
| [`examples/`](examples) | `hello`, the smallest program | never: built and tested with the rest |
| [`doc/`](doc) | [GETTING-STARTED](doc/GETTING-STARTED.md), [CONCEPTS](doc/CONCEPTS.md), [LIBRARIES](doc/LIBRARIES.md), [CONVENTIONS](doc/CONVENTIONS.md), [DEVKIT](doc/DEVKIT.md), [ADOPTING](doc/ADOPTING.md) (moving an existing program onto it), [design/](doc/design) (why it is as it is) | — |
| `scripts/` | the framework's own — `install`, `check-versions`, `ci.local` — and the devkit's, which the framework carries like any project | — |

## Developing the framework

```bash
scripts/setup.sh       # the externals: JDK 21, Maven, Node.js, the JDK archives (once)
scripts/install.sh     # versions agree, build, test, install into ~/.m2
scripts/ci.sh          # the same quietly, then: the framework's scripts equal the devkit's, and a project made by a lone
                       # scripts/jtools from the installed devkit passes its own CI
```

`git config core.hooksPath .githooks` runs `scripts/ci.sh` before every push. A project developed together with the
framework names the clone in its `jtools.conf` (`JTOOLS_HOME`): its `scripts/jtools` then takes the devkit from the
working tree. Publishing to Maven Central: [PUBLISHING.md](PUBLISHING.md).
