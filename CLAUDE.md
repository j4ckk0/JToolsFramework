# JToolsFramework — orientation for Claude

The framework of the org.jtools projects: what DCSBuilder, WhichPaint, XsdViewer, MythoStory and SensorSimulator
share, as a parent pom, Java libraries, the page's TypeScript, the scripts and a project template. `README.md` is the
map, `doc/PATTERN.md` the conventions, `doc/BUILD.md` the parent pom, `doc/SCRIPTS.md` the scripts,
`doc/ADOPTING.md` the existing projects, `doc/PORTS.md` the ports.

## Where things are

| Path | What |
|---|---|
| `parent/pom.xml` | jtools-parent: properties `jtools.app.*`, `jtools.program.skip`, profiles `jtools-frontend` (an `angular.json` switches it on) and `dist` |
| `support/`, `server/`, `launcher/` | `Json`, `Installation`; `WebServer`, `PageWatch`, `Http`, `DesktopOpener`; `WebApp`, `LaunchOptions` — the JDK only |
| `dist/src/main/resources/jtools-dist/` | the assembly descriptors and the launchers, `${jtools.app.*}` left for the project's build |
| `web/src/lib/` | `@jtools/web` (shipped as sources in `jtools-web.jar`); `web/src/main.ts` is a harness, not shipped |
| `scripts/` | the managed scripts (`managed-files.list`) — the very files projects get — and the framework's tools |
| `templates/webapp/` | the generated project; `__APP_NAME__`, `__APP_ID__`, `__APP_PORT__`, `__JTOOLS_VERSION__`, `__JTOOLS_HOME__` in contents and paths |

## Commands

```bash
scripts/install.sh                  # versions agree, build, test, install into ~/.m2
scripts/ci.sh                       # quiet install with npm ci, then ci.local.sh: a project from the template and its CI
scripts/new-project.sh Name [--port N] [--id id] [--dir D] [--no-git] [--no-build]
scripts/jtools-sync.sh ../Project [--check]
```

## Conventions

- **Never edit the existing projects** from here unasked; doc/ADOPTING.md says what each could take.
- **A change to a managed script** is made in `scripts/` here, verified by `scripts/ci.sh` (which runs a generated
  project's CI), then synced into the projects with `jtools-sync.sh`.
- **The libraries stay generic**: nothing of any application in `support`, `server`, `launcher`, `web`.
- **One version** for every module (`scripts/check-versions.sh`); a project names it as its parent's version.
- **Apache License 2.0** (`LICENSE`, `NOTICE`): the build writes the header on every Java source of the libraries
  (`license-maven-plugin`) — a new class gets it at its first build. Never on `templates/webapp`: the projects
  generated from it are their owners', under their owners' terms.
- **Template trials** go under `tmp/` or `--dir /tmp/...` with `--no-git` (no port registered, no repository).
  A trial run of a program: `--port 18000+`, and nothing that opens the desktop's browser — `--no-browser`, or
  `BROWSER=true` with no `DISPLAY` to test the stop.

## Review rules (every org.jtools project; first written for XsdViewer)

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
