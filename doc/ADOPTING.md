# Adopting the framework in an existing project

None of the existing projects has been changed: each moves when its owner decides, one step at a time, its CI run
after each. The steps, in the order that pays first:

1. **The scripts** — `jtools.conf` at the root, then `scripts/jtools-sync.sh <project>` from the framework: the
   managed scripts replace the project's copies (read the diff: what the project's own did beyond them goes into
   `scripts/*.local.sh`). The cheapest step, and the one that ends the drift between copies.
2. **The parent pom** — `jtools-parent` as the root pom's parent, the `jtools.app.*` properties; the program's module
   sets `jtools.program.skip` to `false` and loses its shade, launch4j, antrun and assembly configuration; the
   frontend's pom loses its npm executions. Then `src/assembly/` and `src/dist/` go, but for what the distributions
   carry of the project's own (`src/dist/files/`).
3. **The libraries** — `PageWatch`, the HTTP plumbing, the browser, `Json`, the installation, the launcher: replaced
   by `jtools-server`, `jtools-support`, `jtools-launcher`. The page keeps working as it is: the presence protocol is
   the same (`/api/alive`, `/api/bye`).
4. **The page** — an Angular page takes `@jtools/web` (`provideJTools()`) in place of its own presence code.

What each project can take, and what holds it back:

## SensorSimulator — everything, at once

It was written yesterday on this pattern, and its `support/` and `server/` modules are where `jtools-support` and
`jtools-server` come from: it is the template without the framework. Adopting means deleting `support/` and `server/`,
depending on the framework's, its `Launcher` becoming a `WebApp`, its `app/pom.xml` shrinking to the template's.
Two changes come with it: **its port, 8093, is WhichPaint's** (doc/PORTS.md: 8094 is free), and it loses its Gradle
build (the framework is Maven-first). Regenerating it with `scripts/new-project.sh` and moving nothing is as good —
it has no business yet.

## DCSBuilder — the scripts, the parent, most of the server; the model distributions stay its own

What its server and launcher need of the framework first, and what then goes: [DESIGN-1.1.md](DESIGN-1.1.md) §7.

- Takes: the scripts (its `--model` setup goes into `scripts/setup.local.sh`, the model's checks into
  `externals.local.sh`), the parent (its eight modules keep their order), `PageWatch`, `Http`, `DesktopOpener`,
  `Json`, `Installation`, the launcher's command line (`--keep-alive` is the framework's name already).
- Keeps: its "with model" distributions (a second assembly beside the parent's, in its own profile), its MCP server,
  its `--data` and `--lang` options (`WebApp.option`), its vanilla-JS page (the presence protocol is the same).
- Candidates for the framework's next version, found here first: `I18n` (texts in English and French, the page's
  language from `Accept-Language`), `UserSettings` (the user's choices in `java.util.prefs`, as XsdViewer has too),
  `StaticFiles` with ETags.
- Loses: its Gradle build, until the framework has a Gradle convention plugin.

## WhichPaint — the scripts and the server; one module to split first

A single Maven module (and an Android app): the parent applies as it is, but the pattern's separation —
business in `core`, the program in `app` — means splitting it first. Its `PageWatch`, `PageRoutes`, `ApiJson`,
`Options` and `DesktopOpener` are the framework's libraries. Its vanilla-JS page keeps working. Its Android build is
Gradle's and stays so. **Port 8093** is its own; SensorSimulator is the one to move.

## MythoStory — the scripts, the parent's conventions and `@jtools/web`'s ideas; not the server

Kotlin on Ktor: `WebServer` and `WebApp` are not for it, and its presence speaks another protocol
(`/api/session`, SSE + `/api/session/{id}/closed`). It can take the scripts (Piper, the voices, the Android SDK and the
emulator into `scripts/*.local.sh`), the parent's frontend profile (its `frontend/pom.xml` is the same npm build)
and its dist profile once its desktop module sets `jtools.program.skip`. `@jtools/web` would need its server to speak
`/api/alive` and `/api/bye` first. It is also the one on Java 17 bytecode (Android): `maven.compiler.release` is a
property it overrides.

## XsdViewer — the patterns only, until the framework is on Maven Central

What its server and launcher need of the framework first, and what then goes: [DESIGN-1.1.md](DESIGN-1.1.md) §6.

Public on GitHub, Apache-2.0, organisation jtools.org, built by GitHub Actions with the runner's Maven (no
`externals.conf`), JPMS (`core/module-info.java`). The licences agree — the framework is Apache-2.0 too, under the
same organisation — but its build can only depend on what its CI resolves: the framework must reach Maven Central
first. The jars name an `Automatic-Module-Name` for its `module-info.java`; jtools-parent's `license-maven-plugin`
configuration is the one XsdViewer already uses.
Its `PageWatch`, `AliveHandler`, `ByeHandler`, `BrowserLauncher`, `CommandLineOptions`, `ConfigFile` are the
framework's libraries, in a finer split (a handler per route); its review rules are the framework's (CLAUDE.md).
