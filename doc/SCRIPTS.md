# The scripts

The framework owns a project's scripts: `scripts/managed-files.list` names them — `setup`, `externals`, `build`, `ci`,
`run`, `stop`, `package` in `.sh` and `.bat`, `.githooks/pre-push`, `.gitattributes`. `scripts/new-project.sh` copies
them into a new project, `scripts/jtools-sync.sh` refreshes them in an existing one. Each says in its header that it
is managed: a change goes into JToolsFramework, is tested by its CI, then synced into the projects. The framework's
own `scripts/` folder holds the very files the projects get — there is no second copy to drift.

They are generic: what they know of a project is in its `jtools.conf`, its tools in its `externals.conf`, its own
extras in `scripts/*.local.sh`.

## jtools.conf

`KEY=VALUE` lines, `#` comments, a value is the rest of its line (spaces included, no quotes).

| Key | Default | What it is |
|---|---|---|
| `APP_ID` | — | the program's id: `<APP_MODULE>/target/<APP_ID>.jar`, `<APP_ID>.ini`, the releases' names, the process `stop.sh` looks for. Empty: a library — `run`, `stop` and `package` say there is nothing to run |
| `APP_NAME` | — | its name, as messages say it, and its `.exe` |
| `APP_MODULE` | `app` | the module building the program's jar |
| `BUILD_GOALS` | `clean package` | what `build`, `ci`, `run` (with `-DskipTests`) and `package` (with `-Pdist`) run; the framework says `clean install` |
| `JTOOLS_HOME` | — | where JToolsFramework is, relative to the project: for `jtools-sync.sh` and the docs |

## externals.conf

Each tool is deployed only when `externals.conf` names it: `JDK_*` → `external/jdk`, `MAVEN_*` → `external/maven`,
`NODE_*` → `external/node`, `GRADLE` → `external/gradle`; `JDK_LINUX`, `JDK_WINDOWS`, `JDK_MACOS` → also the archives
`external/jdk-linux.tar.gz`, `jdk-windows.zip`, `jdk-macos.tar.gz` the distributions' runtimes are linked from (hard
links to the downloads). A project
without a page drops `NODE_*`; one that wants Gradle adds `GRADLE`. The framework's `externals.conf` is the
reference: `new-project.sh` copies it, `jtools-sync.sh` reports where a project's versions differ (it never changes
them: they are the project's to decide). `JTOOLS_DOWNLOADS=<dir>` in the environment shares one downloads folder
between projects.

## The project's own extras: scripts/*.local.sh

Not managed, sourced or run by the managed scripts when present:

| File | When | What it may do |
|---|---|---|
| `scripts/setup.local.sh` | at the end of `setup.sh`, sourced | deploy its own externals with `fetch URL` and `deploy FILE DIR` (`$force` set, the options `setup.sh` does not know in `$local_args`) — DCSBuilder's model, MythoStory's Piper and Android SDK |
| `scripts/externals.local.sh` | at the start of `externals.sh`, sourced | define `missing_local_externals` (prints what of its extras is missing), export what its tools need |
| `scripts/ci.local.sh` | after the build in `ci.sh` | checks of its own (the framework's: the template still builds) |

On Windows: `scripts\setup.local.bat` and `scripts\ci.local.bat`, called with the same arguments.

## The framework's own tools (not managed, Linux)

| Script | What it does |
|---|---|
| `install.sh` | checks the versions agree, builds and installs the framework into `~/.m2` |
| `new-project.sh <Name>` | a new project from `templates/webapp`, its port entered in doc/PORTS.md, its externals, its first build, its git repository, bare repository and first push |
| `jtools-sync.sh <project> [--check]` | refreshes the managed files of a project; `--check` reports only and fails when anything differs |
| `check-versions.sh` | every pom names the same framework version |
| `ci.local.sh` | the framework's CI after its build: the versions, and a project generated from the template passing its own CI |
