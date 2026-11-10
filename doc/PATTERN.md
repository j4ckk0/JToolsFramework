# The org.jtools pattern

What DCSBuilder, WhichPaint, XsdViewer, MythoStory and SensorSimulator have in common, said once. JToolsFramework
carries it as code where it can (the parent pom, the libraries, `@jtools/web`, the scripts, the template); this page
says the rest, and why.

## 1. A local web application, the browser as its window

- One program, one JVM: a **server on `127.0.0.1`** serves a page and its API; the program opens the browser on it.
- **The server holds the whole business.** The page is a view: it shows what the server answers and sends what the
  user asks — no rule, no computation, no state beyond what it displays. A rule found in the page belongs on the
  server. The business stays testable without HTTP nor a browser, and usable from any Java program.
- **The program stops once its last page is closed**, like a window's program — the browser is its only way out.
  `--keep-alive` keeps it serving; `--no-browser` does too (a page opened later, by hand, must find it).
- **The JDK and nothing else** on the Java side wherever it is enough: `com.sun.net.httpserver`, `java.util.logging`,
  a JSON of our own (`Json`). A dependency is a decision, not a default.

## 2. The modules

Each module is one area of responsibility, stated in one sentence without "and", and depends only on the modules
above it in the root pom's list:

| Module | What it is | Depends on |
|---|---|---|
| `core` (or several: `stanag`, `decision`, `store`…) | the business, pure Java, no HTTP | the framework's `jtools-support` |
| `api` (DCSBuilder: `web`) | the HTTP API over the business: a translation, no rule | core, `jtools-server` |
| `frontend` | the Angular page, as a jar of `static/` | — (`@jtools/web` at build time) |
| `app` | the program: `Launcher`, its one jar embedding everything, the distributions | api, frontend, `jtools-launcher` |

Naming: groupId `org.jtools`, artifactIds `<id>-<module>` (the program's jar is plain `<id>`), packages
`org.jtools.<id>.<module>`, the program's id in lower case (`dcsbuilder`), its name in CamelCase (`DCSBuilder`).

## 3. The page's presence, and the stop

The protocol every page speaks (`WebServer`, `PageWatch`; `@jtools/web`'s `PagePresenceService`; the vanilla-JS
pages of DCSBuilder, WhichPaint and XsdViewer speak it too):

| | |
|---|---|
| `GET /api/alive?id=<page>` | an event stream the page holds for its whole life; the server pings it every **5 s** |
| `POST /api/bye?id=<page>` | the page's beacon as it goes (`pagehide`) |
| the stop | no page left for **15 s** (a reload, a laptop waking up): the program exits |

A page that closes normally sends its beacon: the stop comes 15 s later. A crashed browser sends none; its stream
breaks, which a ping notices — the first write after a close usually still succeeds, so up to two pings (10 s) — then
the 15 s run. Nothing happens before a first page has opened.

## 4. Self-contained: a clone and `scripts/setup.sh`

Nothing the build needs is under source control **nor taken from the machine**: `externals.conf` names the JDK,
Maven, Node.js and the JREs the distributions bundle, by URL and version; `scripts/setup.sh` downloads them into
`external/` (ignored by git) under generic names (`external/jdk`, `external/maven`…), the only names the scripts and
the build know. A machine with no internet sets `MIRROR` (an Artifactory, any HTTP folder); its credentials go in
`externals.local.conf` or the environment, never in git. The versions live in `externals.conf` and nowhere else.

## 5. The scripts

The same names in every project, each in `.sh` and `.bat`: `setup`, `build`, `ci`, `run`, `stop`, `package`, with
`externals` sourced by all. `.githooks/pre-push` runs `scripts/ci.sh` (enable once: `git config core.hooksPath
.githooks`); `.gitattributes` keeps `.sh` in LF and `.bat` in CRLF. They are the framework's (doc/SCRIPTS.md):
a project changes them in JToolsFramework, never in its copy. `run.sh` runs a copy of the jar (`<id>-run.jar`), so a
build can replace the jar while the program runs.

## 6. Versions said once

The program's version is the root pom's `<revision>` (CI-friendly: the modules' `<parent>` refer to it), with its
four-part form `jtools.app.windowsVersion` that an `.exe` wants. The framework's version is the parent's. The
externals' versions are `externals.conf`'s. Nothing else repeats them; a test checks what must agree (the port).

## 7. The distributions

`scripts/package.sh` builds three self-contained archives into `releases/` (not tracked): a Linux and a macOS `tar.gz`
with `<id>.sh`, and a Windows `zip` with `<Name>.exe` (launch4j, no console) and `<id>.bat` (a console). Each holds the
program's one jar, its `<id>.ini` (the installation's settings: `port`, `host`, `log.level`, `log.folder`), the README and
a runtime jlink trims to the modules the program needs (about 35 MB an archive) — nothing
to install. The script checks what each carries and removes the previous versions' releases.

## 8. Ports

Each program has a port of its own (doc/PORTS.md), never 8080/8081, the defaults of so many servers. The order of
precedence: the command line, then the `.ini`, then the program's default. Tests use port 0 or 18000 and above.

## 9. Git

The working clone in `~/Developpement/git/<Project>`, its bare repository in `~/Developpement/git-bare/<Project>.git`
(its `origin`, by absolute path), branch `master`; `git-bare/push-to-nas.sh` copies every bare repository to the NAS.
A published project — on GitHub and Maven Central, under the Apache License 2.0: XsdViewer, DocComparator, this
framework — lives in `~/Developpement/github/<Project>` instead, its `origin` on GitHub
(`git@github.com:j4ckk0/<Project>.git`), no bare repository, and commits as `j4ckk057@gmail.com` (the repository's
own `user.email`), the address its poms name.
Commit messages are a feature title in plain English ("Settings: choose the voice that reads the stories").

## 10. Code and comments

From XsdViewer's review rules, which every project follows (the framework's CLAUDE.md has them in full):

- **A comment tells the intention**: why the code is as it is, or the constraint that forced it — never what the next
  line does, never the history ("was X", "since the refactor"): version control keeps the history.
- **Names say the subject and the kind of object** (a model, a writer, a view, a service); no `Utils`, `Manager`
  alone. **Method names are promises**: no `handle`/`process` without an object, no `get` that writes.
- **Dependencies point one way** (server → core, page → API). A file past 500 lines names its responsibilities;
  past 1000, it is a finding.
- Texts people read are constants or i18n keys, not literals scattered in the code (DCSBuilder, MythoStory: `en`/`fr`).
