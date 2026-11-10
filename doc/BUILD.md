# The build: jtools-parent

A project's root pom names `org.jtools.framework:jtools-parent` as its parent and sets the properties of its program;
everything else of the build is the parent's.

```xml
<parent>
  <groupId>org.jtools.framework</groupId>
  <artifactId>jtools-parent</artifactId>
  <version>1.1.0</version>
  <relativePath/>
</parent>
<properties>
  <revision>0.1.0</revision>
  <jtools.app.id>sensorsimulator</jtools.app.id>
  <jtools.app.name>SensorSimulator</jtools.app.name>
  <jtools.app.mainClass>org.jtools.sensorsimulator.app.Launcher</jtools.app.mainClass>
  <jtools.app.port>8094</jtools.app.port>
  <jtools.app.windowsVersion>0.1.0.0</jtools.app.windowsVersion>
</properties>
```

## What every module gets

Java 21 (`maven.compiler.release`), UTF-8, JUnit 5 (a test dependency of every module), the plugins' versions, the
tests run from the root of the repository (`workingDirectory`: they read `pom.xml`, `frontend/…`, `samples/` by
relative paths), `Implementation-Title`/`-Version` in every jar's manifest.

## A module with an `angular.json`: the page (profile `jtools-frontend`)

Switched on by the file itself — the frontend's pom is its coordinates and nothing else:

| Phase | What happens |
|---|---|
| `initialize` | `jtools-web` (the framework's TypeScript) unpacked into `target/jtools-web`, where `tsconfig.json`'s `"paths"` maps `@jtools/web` |
| `generate-resources` | `npm install` (`-Dnpm.install=ci`: `npm ci`, what `scripts/ci.sh` passes), `npm run build` into `target/angular` |
| `process-resources` | `target/angular/browser` copied to `static/` in the module's jar, `3rdpartylicenses.txt` to `static/licences/` |
| `test` | `npm test -- --watch=false` (vitest); `-DskipTests` skips it with the Java tests |

npm is the one of `PATH`: `external/node`'s when the scripts run Maven. `jtools.web.skip` and `jtools.page.skip` turn
the unpacking and the copy off (the framework's own `web` module does both: it is what gets unpacked).

`@jtools/web` is shipped as sources, not as an npm package, on purpose: the page compiles them with its own Angular,
no package to publish nor any `package-lock.json` integrity to keep — the Maven repository is the one channel.

## The program's module (`jtools.program.skip` = false)

- **`jtools-embed`** (shade, `package`): the module's jar becomes the program's one jar — `java -jar <id>.jar` —
  embedding every module and library, the page included, with `Main-Class: ${jtools.app.mainClass}`. The module's
  `<finalName>` is `${jtools.app.id}`.
- **with `-Pdist`** (profile `dist`, what `scripts/package.sh` runs): `jtools-dist` unpacked into
  `target/jtools-dist`, `<Name>.exe` (launch4j), then — once the program's jar is made — `jtools-dist`'s `build.xml`:
  1. the modules: what `jdeps` finds in the jar, and `jtools.dist.modules.extra` besides (`jdk.charsets,jdk.crypto.ec`
     by default: what no class names — files in any encoding, the elliptic curves TLS needs);
  2. a runtime per platform whose JDK is in `external/` (`jdk-linux.tar.gz`, `jdk-windows.zip`, `jdk-macos.tar.gz`,
     which `scripts/setup.sh` deploys): its `jmods` unpacked, linked by this machine's own `jlink` (`--strip-debug
     --no-man-pages --no-header-files --compress zip-6`) — all of one version, which `externals.conf` says once;
  3. the archives into `releases/`: `<id>-<version>-linux.tar.gz` and `-macos.tar.gz` (`<id>.sh`, the executable bits
     set), `-windows.zip` (`<Name>.exe`, `<id>.bat`), each with the jar, `<id>.ini`, the README, `LICENSE` and
     `NOTICE` when the project has them, the module's `src/dist/files/` as it is, and the runtime as `jre/`.
  A platform whose JDK is not in `external/` gets no archive. About 35 MB each, against 50 MB with a whole JRE.
  **What jdeps cannot see stays out**: a program that formats dates or numbers in a language other than English
  (`DateTimeFormatter`, `NumberFormat`, `String.format("%,d")`, `MessageFormat`) adds `jdk.localedata` to
  `jtools.dist.modules.extra` in its root pom — without it, the trimmed runtime formats them as in English.

The shade execution lives in the parent's build rather than in the module, on purpose: in one phase the build's own
plugins run before its profiles', and the distributions must find the embedded jar, not the module's own.

## The framework's libraries

| Artifact | Package | What it is |
|---|---|---|
| `jtools-support` | `org.jtools.framework.support` | `Json` (Map/List trees ⇄ text, `Json.raw` for JSON already written), `I18n` and `Catalogue` (texts per language, plain `{0}`, plurals, `Accept-Language`), `Installation` (home, version, `.ini`) |
| `jtools-server` | `org.jtools.framework.server` | `WebServer` (routes under `/api`, exact or prefix, by method; plain handlers; JDK filters; the Host and Origin checks; `{"error"}` refusals and exceptions mapped to statuses; the page with ETags, `index.html` for extension-less paths; the presence routes), `Exchange` (a route's request: body, JSON, query, language), `Answer` (bytes, XML, a status), `PageWatch`, `Http`, `DesktopOpener` |
| `jtools-launcher` | `org.jtools.framework.launcher` | `WebApp` (the command line and the program's options, the port's order — command line, Settings menu, `.ini`, default —, the log, the server, `/api/settings`, `/api/about`, `/api/quit`, `/api/log/open`, the browser, the stop, the failure dialog, in en/fr), `UserSettings`, `SettingsFields`, `LaunchOptions`, `LogFormatter` |
| `jtools-desktop` | `org.jtools.framework.desktop` | `FileDialogs`: open, save, choose a folder with the desktop's own dialogs (kdialog, zenity, AWT, Swing) |
| `jtools-dist` | — | `build.xml` (jdeps, jlink, the archives), `app.sh`, `app.bat`, `app.ini` |
| `jtools-web` | `@jtools/web` | `PagePresenceService`, `provideJTools()`, `apiErrorMessage()` |

Each jar names an `Automatic-Module-Name` (`org.jtools.framework.support`…), for a project with a `module-info.java`.

## Sharing the framework

`scripts/install.sh` installs the framework into `~/.m2`: enough on this machine. For another machine, or a CI, deploy
it to the Artifactory the externals already use, then name that repository in the projects' Maven settings:

```bash
mvn deploy -DaltDeploymentRepository=jtools::https://<artifactory>/artifactory/libs-release-local
```

**Maven Central**, for everyone: the profile `publish` (sources, javadoc, GPG, the Central Portal — the same as
DocComparator's and XsdViewer's) is jtools-parent's, so the framework's modules publish with it and a published
project can too. [PUBLISHING.md](../PUBLISHING.md) says what it takes. A project published on GitHub (XsdViewer) can
depend on the framework once it is there: its CI resolves from Central — doc/ADOPTING.md.

## The licence

The framework is under the Apache License 2.0 (`LICENSE`, `NOTICE`). jtools-parent carries what Maven Central
requires of every pom it publishes — `<url>`, `<licenses>`, `<organization>`, `<developers>`, `<scm>` — and, as with
any parent, **a project inheriting it inherits those too**: one that publishes its own artifacts declares its own
`<url>`, `<licenses>` and `<scm>`. The modules' url and scm are the repository's, not the repository's with the
artifactId appended (`child.*.inherit.append.path="false"`).

`license-maven-plugin` puts the Apache header at the top of every Java source, main and test, at `process-sources`:
it is configured in jtools-parent's `pluginManagement` and a module gets it by naming the plugin — the framework's
libraries do; a project does not, unless it is published under the same licence (XsdViewer's configuration is the same).

## Not in this version

**Gradle**: three projects carry a Gradle build beside Maven's. The framework is Maven-first; a Gradle convention
plugin mirroring jtools-parent is the way to bring it, and is not written yet.
