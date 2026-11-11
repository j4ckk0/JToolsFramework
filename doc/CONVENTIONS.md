# The build conventions: jtools-parent, jtools-bom

A project's root pom names `org.jtools.framework:jtools-parent` as its parent and says **five things of its program**;
everything else of the build is the parent's. A generated project has all of it already (`jtools new`).

```xml
<parent>
  <groupId>org.jtools.framework</groupId>
  <artifactId>jtools-parent</artifactId>
  <version>1.2.0</version>
  <relativePath/>
</parent>
<properties>
  <revision>0.1.0</revision>
  <jtools.app.id>sensormonitor</jtools.app.id>                                    <!-- its jar, .ini, launchers, releases -->
  <jtools.app.name>SensorMonitor</jtools.app.name>                                <!-- as people read it; its .exe -->
  <jtools.app.mainClass>org.jtools.sensormonitor.app.Launcher</jtools.app.mainClass>
  <jtools.app.port>8090</jtools.app.port>                                         <!-- what its .ini says -->
  <jtools.app.windowsVersion>0.1.0.0</jtools.app.windowsVersion>                  <!-- the version in four parts -->
</properties>
```

And in the program's module, `<jtools.app.skip>false</jtools.app.skip>`: that module's jar is the program's.
`jtools.app.description` — what Windows says of the `.exe` — is the name unless said; `jtools.app.copyright` likewise.

## What every module gets

Java 25 (`maven.compiler.release`), UTF-8, JUnit 5 (a test dependency of every module), the plugins' versions, the
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

npm is the one of `PATH`: `external/node`'s when the scripts run Maven. `jtools.weblib.skip` and `jtools.page.skip` turn
the unpacking and the copy off (the framework's own `web` module does both: it is what gets unpacked).

`@jtools/web` is shipped as sources, not as an npm package, on purpose: the page compiles them with its own Angular,
no package to publish nor any `package-lock.json` integrity to keep — the Maven repository is the one channel.

## The program's module (`jtools.app.skip` = false)

- **`jtools-embed`** (shade, `package`): the module's jar becomes the program's one jar — `java -jar <id>.jar` —
  embedding every module and library, the page included, with `Main-Class: ${jtools.app.mainClass}`; a modular
  library's `module-info.class` stays out, the program's jar being a plain classpath one. The module's
  `<finalName>` is `${jtools.app.id}`.
- **with `-Pdist`** (profile `dist`, what `scripts/package.sh` runs): `jtools-dist` unpacked into
  `target/jtools-dist`, `<Name>.exe` (launch4j), then — once the program's jar is made — `jtools-dist`'s `build.xml`:
  1. the modules: what `jdeps` finds in the jar, and `jtools.dist.modules.extra` besides (`jdk.charsets,jdk.crypto.ec`
     by default: what no class names — files in any encoding, the elliptic curves TLS needs);
  2. a runtime per platform whose jmods are in `external/` — `jmods-linux.tar.gz`, `jmods-windows.zip`,
     `jmods-macos.tar.gz`, which `scripts/setup.sh` deploys (a JDK from 24 on carries no jmods: Temurin publishes them
     apart), or Temurin's own names, `*jmods*linux*.tar.gz`…, in the folder `jtools.jdk.archives` names; for a JDK up to
     23, its JDK archive's `jmods/` (`jdk-linux.tar.gz`…, `*jdk*linux*.tar.gz`…) —, linked by the `jlink` of this
     machine's platform's JDK archive (`jdk-linux.tar.gz`) — of the jmods' version by construction, whatever JDK runs
     Maven —, else of the JDK running it (`--strip-debug --no-man-pages --no-header-files --compress zip-6`) — all of one
     version, which `externals.conf` says once;
  3. the archives into `releases/`: `<id>-<version>-linux.tar.gz` and `-macos.tar.gz` (`<id>.sh`, the executable bits
     set), `-windows.zip` (`<Name>.exe`, `<id>.bat`), each with the jar, `<id>.ini`, the README, `LICENSE` and
     `NOTICE` when the project has them, what `jtools.dist.include` names of the repository under the same path
     (Ant patterns from its root: `samples/**`), the module's `src/dist/files/` as it is, and the runtime as `jre/`.
  A platform whose JDK is not in `external/` gets no archive. About 35 MB each, against 50 MB with a whole JRE.
  **What jdeps cannot see stays out**: a program that formats dates or numbers in a language other than English
  (`DateTimeFormatter`, `NumberFormat`, `String.format("%,d")`, `MessageFormat`) adds `jdk.localedata` to
  `jtools.dist.modules.extra` in its root pom — without it, the trimmed runtime formats them as in English.

The shade execution lives in the parent's build rather than in the module, on purpose: in one phase the build's own
plugins run before its profiles', and the distributions must find the embedded jar, not the module's own.

A program's tests start its server as `launch` would with `WebApp.start(args…)` — the arguments, the operands, the
Settings menu's port, the routes —, and nothing of a process: no `.ini` read, no log, no browser, no exit.

## jtools-bom: the versions, for a build that does not inherit jtools-parent

`org.jtools.framework:jtools-bom` holds the versions of the framework's artifacts and nothing else — jtools-parent
imports it. A Gradle build, or a Maven project with a parent of its own, imports it and names the libraries without
a version:

```kotlin
dependencies {
    implementation(platform("org.jtools.framework:jtools-bom:1.2.0"))
    implementation("org.jtools.framework:jtools-app")
}
```

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.jtools.framework</groupId>
      <artifactId>jtools-bom</artifactId>
      <version>1.2.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

Such a build has the libraries, not the conventions: the one jar and the distributions are jtools-parent's.

## The libraries

What each gives, and the class to start from: [LIBRARIES.md](LIBRARIES.md). Each jar names an `Automatic-Module-Name`
(`org.jtools.framework.support`…), for a project with a `module-info.java`.

## Sharing the framework

`scripts/install.sh` installs the framework into `~/.m2`: enough on this machine. For another machine, or a CI, deploy
it to the Artifactory the externals already use, then name that repository in the projects' Maven settings:

```bash
mvn deploy -DaltDeploymentRepository=jtools::https://<artifactory>/artifactory/libs-release-local
```

**Maven Central**, for everyone: the profile `publish` (sources, javadoc, GPG, the Central Portal) is jtools-parent's,
so the framework's modules publish with it and a project publishing its own artifacts can too.
[PUBLISHING.md](../PUBLISHING.md) says what it takes. A project whose CI builds on another machine — a hosted CI
service — resolves the framework from Central once it is published there.

## The licence

The framework is under the Apache License 2.0 (`LICENSE`, `NOTICE`). jtools-parent carries what Maven Central
requires of every pom it publishes — `<url>`, `<licenses>`, `<organization>`, `<developers>`, `<scm>` — and, as with
any parent, **a project inheriting it inherits those too**: one that publishes its own artifacts declares its own
`<url>`, `<licenses>` and `<scm>`. The modules' url and scm are the repository's, not the repository's with the
artifactId appended (`child.*.inherit.append.path="false"`).

`license-maven-plugin` puts the Apache header at the top of every Java source, main and test, at `process-sources`:
it is configured in jtools-parent's `pluginManagement` and a module gets it by naming the plugin — the framework's
libraries do; a project does not, unless it is published under the same licence.

## Not in this version

**Gradle conventions**: the framework is Maven-first. A Gradle build gets the libraries' versions from `jtools-bom`;
the one jar and the distributions — a Gradle plugin mirroring jtools-parent — are not written yet.

---

Next: the scripts a project carries, and how they stay up to date — [DEVKIT.md](DEVKIT.md).
