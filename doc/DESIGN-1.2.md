# JToolsFramework 1.2 — design

1.1 does what the org.jtools programs need: XsdViewer and DCSBuilder run on it, on a branch each, their own test
suites green (140 and 250). 1.2 is about the developer who meets it for the first time and must understand **what it
is and how to use it** — the libraries their code calls, the build their project inherits, the development
environment their project carries, the way a new project begins. It adds almost no feature: it gives each of those a
place, a name, a door in the README and a page of documentation. Written in November 2026, after the two migrations;
§13 will say where the implementation departed from it.

## 0. Decisions first

1. **1.1.0 is never published; the first release on Maven Central is 1.2.0.** The renames below cost nothing before a
   first publication and are impossible after it: what Central publishes stays there for ever, and a
   `jtools-launcher` 1.1.0 would be the first thing a search finds, beside a `jtools-app` nothing explains. 1.1 made
   the same move for 1.0.0. XsdViewer's and DCSBuilder's branches move to 1.2.0 before they are merged (§11).
2. **`jtools-launcher` becomes `jtools-app`, its artifact and its package** (`org.jtools.framework.app`). The module
   is the one a developer depends on — it holds `WebApp`, the program — and "launcher" says the least of it. Half a
   rename (the artifact, not the package) would be worse than none.
3. **The development environment becomes a versioned artifact, `jtools-devkit`**, fetched by a project rather than
   copied from a clone of the framework next to it (§6). `JTOOLS_HOME` stays, for whoever develops the framework.
4. **A startup failure is logged in English, and said to the person in their language** — 1.1 §4.3's rule, which
   1.1's `WebApp.fail` breaks (it logs the person's sentence): the log file keeps the English line; the console and,
   without a console, the dialog show the person's (§10).

**The inclusion rule**: an item is in 1.2 when it makes the framework easier to understand, to adopt or to keep up
to date. A feature only one program needs is a 1.3 candidate (§10), as 1.1 §0.2 had it.

## 1. What a developer meets today

The repository's top level, as a newcomer reads it:

| Path | What it is | What the reader cannot tell |
|---|---|---|
| `parent/` | the parent pom every project inherits | that it is the build, not a library |
| `support/`, `server/`, `launcher/`, `desktop/` | the Java libraries | which one to depend on (`launcher`, which brings the other two) |
| `dist/` | the distributions' Ant file and launchers, unpacked by the parent | that no project ever names it |
| `web/` | `@jtools/web`, the Angular side, shipped as TypeScript in a jar | that it is for Angular pages only |
| `templates/webapp/` | the project `new-project.sh` writes | that it is the way to start |
| `scripts/` | the framework's own scripts **and** the sixteen files copied into every project (`managed-files.list`), side by side | which are which |
| `doc/` | PATTERN, BUILD, SCRIPTS, ADOPTING, PORTS, DESIGN-1.1, in the order they were written | where to begin |

And four things in the way of anyone but the author:

- **`new-project.sh` writes into the author's layout**: `../../git/<Name>`, a bare repository in `../../git-bare/`,
  a port taken from `doc/PORTS.md` — the registry of the author's programs —, a push that runs its CI.
- **Keeping a project's scripts up to date needs a clone of the framework beside it** (`JTOOLS_HOME` in `jtools.conf`)
  and says nothing of which version a project carries.
- **No example is smaller than the generated project**, four modules and an Angular page; a page in plain JavaScript —
  XsdViewer's and DCSBuilder's kind — has no example at all.
- **The README is behind the code**: it still says the distributions carry a whole JRE and are built for Linux and
  Windows; 1.1 links a runtime with jlink, for macOS too.

## 2. The parts, and the layout that shows them

One repository, one version for everything — `jtools-app` 1.2.0 works with `jtools-parent` 1.2.0 and the devkit
1.2.0, and nothing else needs saying. Splitting it into repositories with versions of their own would give its users a
compatibility matrix to read, and its one maintainer a matrix to keep. What changes is the layout: **a folder per
part, named for what the developer does with it**.

```
JToolsFramework/
├── README.md              the three doors: start an app · use the libraries · build, run and ship it
├── doc/                   getting started, concepts, a guide per part, the designs
├── libraries/             what the program's code calls                        → Maven Central
│   ├── support/  server/  app/  desktop/   jtools-support, -server, -app, -desktop
│   └── web/                                 jtools-web: @jtools/web, for an Angular page
├── conventions/           what the project's build inherits                    → Maven Central
│   ├── parent/  bom/  dist/                 jtools-parent, jtools-bom, jtools-dist
├── devkit/                what the project carries: its scripts, its externals → Maven Central (jtools-devkit)
├── starter/               how a project begins: the template and its generator → in jtools-devkit
├── examples/              hello — the smallest program; built and tested with the rest, never published
├── scripts/               the framework's own (install, check-versions, ci.local) and the devkit's, as any project carries them
└── pom.xml                the reactor
```

| Part | For whom | How a project takes it |
|---|---|---|
| libraries | the program's code | a dependency: `jtools-app`, which brings `jtools-server` and `jtools-support`; `jtools-desktop` for file dialogs, `jtools-web` for an Angular page |
| conventions | the project's build | the root pom's `<parent>`, five `jtools.app.*` properties; or, for Gradle and a project with a parent of its own, `jtools-bom` |
| devkit | whoever builds and runs the project | sixteen files the project carries and does not edit, refreshed by `scripts/jtools sync` at the version `jtools.conf` names |
| starter | whoever starts a project | once: `jtools new <Name>` |
| examples | whoever learns the libraries | read; copy what helps |

**Not `build/`** for the conventions, though it is the word: Gradle writes its output there, and most `.gitignore`
files — DCSBuilder's among them — drop every folder of that name. **`doc/` stays `doc/`**: it is the family's name.

## 3. The moves

No artifact changes its name but `jtools-launcher` (§4.1); every move is a `git mv` and a path in a pom.

| Today | 1.2 | Artifact |
|---|---|---|
| `support/`, `server/`, `desktop/`, `web/` | `libraries/support/` … `libraries/web/` | unchanged |
| `launcher/` | `libraries/app/` | `jtools-launcher` → **`jtools-app`** |
| `parent/`, `dist/` | `conventions/parent/`, `conventions/dist/` | unchanged |
| — | `conventions/bom/` | **`jtools-bom`**, new (§5) |
| the sixteen files of `scripts/managed-files.list`, `externals.conf`'s defaults | `devkit/` | **`jtools-devkit`**, new (§6) |
| `templates/webapp/`, `scripts/new-project.sh` | `starter/template/`, `starter/new-project.sh` | in `jtools-devkit` |
| `scripts/jtools-sync.sh` | `devkit/jtools-sync.sh`, run by the stub `scripts/jtools` (§6.2) | in `jtools-devkit` |
| `scripts/` | the framework's own scripts (`install.sh`, `check-versions.sh`, `ci.local.sh`), and the devkit's managed files as a copy, the framework being a project of its own (`JTOOLS_HOME=.`); its CI runs `scripts/jtools sync --check`, so that the copy never drifts from `devkit/` | — |
| — | `examples/hello/` | none: never published (§8) |
| `doc/*.md` | `doc/`, renamed and regrouped (§9) | — |

**What the compiler does not check** — the names spelled as text, to change with the moves, each found by a grep
over `*.properties`, `*.xml`, `*.sh`, `*.bat`, `*.md`, `*.json`, `*.ts`:

- `default-logging.properties` names `org.jtools.framework.launcher.LogFormatter`: missed, the console's levels come
  back in the machine's language (`INFOS`), the very bug `LogFormatter` exists for;
- the launcher's texts, `org/jtools/framework/launcher/texts*.properties`, and the bundle name in `WebApp`;
- `Automatic-Module-Name` in the launcher's pom;
- the root pom's `<module>` paths, `templates/webapp` in `new-project.sh`, the template's own pom
  (`jtools-launcher`), the parent's `jtools.app.mainClass` comment, CLAUDE.md, PUBLISHING.md's artifact table, BUILD.md,
  PATTERN.md, ADOPTING.md, SCRIPTS.md.

## 4. The libraries

### 4.1 `jtools-app`

`org.jtools.framework.launcher` becomes `org.jtools.framework.app`: `WebApp`, `SettingsFields`, `UserSettings`,
`LaunchOptions`, `LogFormatter`, `Logging`, the texts. No class changes otherwise. A program's sources change by one
import line per file, its pom by one artifactId (§11).

### 4.2 One dependency, said once

The README and each guide begin with the same sentence: **a program depends on `jtools-app`**; it brings `jtools-server`
and `jtools-support`. `jtools-desktop` is added for native file dialogs (it carries Swing's weight in the dependency
tree, which is why it is apart); `jtools-web` by an Angular page, nothing else.

### 4.3 What is API

Every public class is API, or public only because another module or the JDK needs it — said in its javadoc's first
line, so that a reader of the reference knows what to call:

| Library | API | Public for a technical reason |
|---|---|---|
| `jtools-support` | `Json`, `I18n`, `Catalogue`, `Installation` | — |
| `jtools-server` | `WebServer` (and `Route`, `Refusal`), `Exchange`, `Answer`, `PageWatch` (what `pages()` answers) | `Http` (the content types a route may name: keep, document the constants), `DesktopOpener` (`jtools-app` calls it) |
| `jtools-app` | `WebApp`, `SettingsFields`, `UserSettings` | `LaunchOptions` (its option and `.ini` names: document them as the command line's vocabulary), `LogFormatter` (java.util.logging instantiates it by name) |
| `jtools-desktop` | `FileDialogs` (and `Filter`) | — |

A **`package-info.java` per library** opens its javadoc: what it gives, the one class to start from, five lines of
code. No `module-info`: each library is one package, and the classpath programs — all of them — would gain nothing.

## 5. The conventions

- **`jtools-parent`** keeps everything it does; its `dependencyManagement` becomes an import of `jtools-bom`.
- **`jtools-bom`** is new: the libraries' and `jtools-dist`'s versions, nothing else. For a Gradle build —
  `implementation(platform("org.jtools.framework:jtools-bom:1.2.0"))`, then the libraries without a version — and for a
  Maven project whose parent is its own. DCSBuilder's Gradle build reads the framework's version from its root pom by
  hand today; the BOM does not remove that (Gradle still needs the version once) but removes the per-module versions.
- **`jtools-dist`** gains `jtools.app.description`, the Windows `.exe`'s file description, defaulting to
  `${jtools.app.name}` — DCSBuilder's says what it is ("Data Centric Security: …") and lost it in 1.1.
- **The guide says the five properties first** (`jtools.app.id`, `name`, `mainClass`, `port`, `windowsVersion`) and
  `jtools.program.skip` in the program's module, then everything else as reference.

## 6. The devkit

### 6.1 What it holds, and how it is published

The sixteen files a project carries and never edits — the scripts `setup`, `externals`, `build`, `ci`, `run`, `stop`,
`package` in `.sh` and `.bat`, `.githooks/pre-push`, `.gitattributes` —, the default `externals.conf` a new project
starts from, `jtools-sync.sh`, and the starter (§7). Published as **`org.jtools.framework:jtools-devkit`**, a jar of
those files under `jtools-devkit/`, the way `jtools-dist` is published already (its javadoc a README, the placeholder
the parent provides). One artifact, so that the scripts and the template that expects them are always of one version.

### 6.2 How a project takes it: `scripts/jtools`

A project's `jtools.conf` names a version — **`JTOOLS_VERSION=1.2.0`**, in place of `JTOOLS_HOME` — and carries one file
the devkit never refreshes: **`scripts/jtools`** (and `jtools.bat`), a stub of about forty lines, Maven Wrapper's
pattern. The stub cannot be a managed file: the sync it runs would be replacing the script running it.

```
scripts/jtools sync            the managed files at JTOOLS_VERSION; what else differs said (externals, the pom's version)
scripts/jtools sync --check    the same, changing nothing; exit 1 when anything differs (a project's CI)
scripts/jtools version         the devkit a project carries, and the newest one found
```

The stub finds the devkit, in this order, and unpacks it into `external/jtools-devkit-<version>/` (`external/` is
already every project's untracked folder of downloads):

1. `JTOOLS_HOME`, when `jtools.conf` or the environment names a clone of the framework — its working tree, for whoever
   develops the framework and a project together;
2. `~/.m2/repository/org/jtools/framework/jtools-devkit/<version>/` — what `scripts/install.sh` puts there, and what
   Maven already downloaded;
3. `MIRROR`, then Maven Central (`https://repo1.maven.org/maven2/…`), with `curl`, the `.sha1` Central publishes beside
   every file checked before anything is unpacked. The mirror and its authentication are `externals.conf`'s and
   `externals.local.conf`'s keys (`MIRROR`, `MIRROR_TOKEN`, `MIRROR_USER`…), which setup reads already: whoever builds
   behind a company's proxy fetches the devkit the way they fetch the JDK.

The stub changes nothing it does not manage and commits nothing, as `jtools-sync.sh` today.

### 6.3 Opt-in

A project with scripts of its own keeps them: XsdViewer and DCSBuilder do, the latter with its `--model` and
`--gradle` targets and its release checks. The devkit is what a new project gets, and what an existing one may take
when its scripts have nothing the devkit lacks — doc/ADOPTING.md says, project by project, what that would cost.

## 7. The starter

- **`jtools new <Name>`** (the stub's third command), or `starter/new-project.sh <Name>` from a clone. By default it
  writes **`./<Name>`**, in the current folder, with the devkit's files, a `jtools.conf` naming the devkit's version, a
  git repository and its first commit; then the externals and a first build unless `--no-build`.
- **The author's layout becomes options**: `--dir DIR` for another place, `--bare DIR` for a bare repository and a first
  push (the family's way), `--no-git` for the files alone (a try, the framework's CI).
- **The port**: `--port N`, else 8090 — no org.jtools program's. `doc/PORTS.md` — the registry of the org.jtools programs —
  is consulted only when the starter runs from a clone of the framework (the family's projects), never through the
  stub: another developer's programs are not in it.
- **The generated project is the tutorial**: its README walks through it (the business in `core`, its HTTP face in
  `api`, the view in `frontend`, the program in `app`; where a route goes; how to run, test, package), and every file
  has a comment saying what belongs in it. doc/GETTING-STARTED.md (§9) follows the same walk.

## 8. The example

**`examples/hello/`**: one class — a `WebApp` with one route —, one `index.html` in plain JavaScript under
`pageRoot("web")` (the presence protocol in ten lines), one test starting it with `WebApp.start` and calling the route.
The page style the generated project does not show, XsdViewer's and DCSBuilder's; the Angular style is shown, and
built by the CI, through the generated project. A module of the reactor, so that it is built and tested with the rest
and can never go stale; `skipPublishing`, so that it never reaches Central.

## 9. The documentation

**The README** opens on what JToolsFramework is — a small framework, its build conventions, a development environment,
a project template —, then three doors, each a paragraph, a snippet and a link: *start an app* (`jtools new`),
*use the libraries* (`jtools-app`, the hello example), *build, run and ship it* (the parent, the scripts, `-Pdist`). The
table of modules goes to the guides.

| Today | 1.2 | What changes |
|---|---|---|
| — | `doc/GETTING-STARTED.md` | new: a project generated, run, a route added, tested, packaged — ten minutes |
| `PATTERN.md` | `doc/CONCEPTS.md` | the pattern — the server holds the business, the page is a view, the program stops with its last page — and the words the other pages use |
| — | `doc/LIBRARIES.md` | new: a section per library — what it gives, the class to start from, a snippet —, the javadoc for the rest |
| `BUILD.md` | `doc/CONVENTIONS.md` | the five properties first; the parent, the BOM, `-Pdist`, jlink |
| `SCRIPTS.md` | `doc/DEVKIT.md` | the stub, `jtools.conf`, the managed files, the hooks (`*.local.sh`) |
| `ADOPTING.md` | `doc/ADOPTING.md` | split in two: moving an existing program onto the framework, step by step (generic); then the family's projects, one by one (what it was) |
| `PORTS.md` | `doc/PORTS.md` | said to be the org.jtools programs' registry, not a rule for every project |
| `DESIGN-1.1.md` | `doc/design/DESIGN-1.1.md`, `DESIGN-1.2.md` | where the decisions are kept |

CLAUDE.md and PUBLISHING.md follow the moves; PUBLISHING.md's artifact table gains `jtools-bom` and `jtools-devkit`.

## 10. Riding along, and left for 1.3

**Riding along** — faults the migrations found, each small:

- **The startup failure's languages** (§0.4). `WebApp.fail` logs the English text — the launcher's catalogue in English —
  at `SEVERE`, on a logger whose records the console handler leaves out; the person's text goes to `System.err` and,
  without a console, to the dialog. The console then reads the person's language once, the log file English once.
  `UsageException` carries its key and arguments rather than a sentence, so that both texts can be made.
- **`jtools.app.description`** (§5).
- **The README's distributions**: jlink's runtimes, Linux, macOS and Windows.

**Left for 1.3** — functional, so outside the rule, in the order they would pay:

1. **`jtools-page.js`**, the plain-JavaScript twin of `@jtools/web`: presence, `request()` with the page's language,
   quit, settings, about. XsdViewer and DCSBuilder each carry their own three hundred lines of it. The one candidate that
   arguably meets the rule — it is what `examples/hello` would use — and the first to pull into 1.2 if it is wanted there.
2. `jtools-web` on npm, so that an Angular page installs it rather than unpacking it from a jar.
3. A Maven archetype beside `jtools new`; a Gradle plugin mirroring `jtools-parent`.
4. The rest of 1.1 §8: a second launch opening the running one, `jtools-testing`, the served-files allow-list.

## 11. What changes for the projects

| Project | Change | Acceptance |
|---|---|---|
| XsdViewer, branch `jtools-1.1` | `jtools-parent` 1.2.0; `jtools-launcher` → `jtools-app` in `app/pom.xml`; the import in `XsdViewerApplication`, `ApiPath`, `XsdViewerServerTest` | its 140 tests green; the branch renamed `jtools-1.2` |
| DCSBuilder, branch `jtools-1.1` | the same in `app/pom.xml`, `web/pom.xml`, `web/build.gradle.kts` (or the BOM); the import in `Launcher`, `SettingsApi`, `ApiNames`, `DcsBuilderServerTest` | its 250 tests green in Maven and Gradle |
| SensorSimulator | none: still on its own copy (its port, 8093, is WhichPaint's); regenerating it with `jtools new` is the easy way | — |
| a project generated from 1.1 | only the framework's CI trials: none to keep | — |

Neither program takes the devkit (§6.3).

## 12. The order of the work

Each step with its tests, the framework's CI — the generated project built, run, packaged — green after each:

1. **The moves** (§3) and the new README: no API change, the same artifacts but one, nothing for a project to do.
2. **`jtools-app`** (§4.1) and **`jtools-bom`** (§5); `package-info.java` and the API audit (§4.3); the two branches
   updated (§11), 140 and 250 green.
3. **The devkit** (§6): the artifact, the stub, `JTOOLS_VERSION`; the starter's options (§7); the CI generates its trial
   project through the stub, from `~/.m2`.
4. **`examples/hello`** (§8).
5. **The documentation** (§9), each page read against the tree it describes.
6. **The ride-along fixes** (§10).
7. **1.2.0 on Maven Central** — by the owner, with their key and token, PUBLISHING.md's routine: the libraries, the
   conventions, the devkit.

## 13. What the implementation changed

Nothing yet: written when 1.2 is implemented.
