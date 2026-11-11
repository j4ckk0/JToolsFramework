# The devkit: a project's scripts

A project on JToolsFramework builds, runs and packages with the same scripts as every other — `setup`, `build`, `ci`,
`run`, `stop`, `package`, in `.sh` and `.bat` —, its pre-push hook and its `.gitattributes`. They are the
**devkit's**, published as `org.jtools.framework:jtools-devkit`: a project carries a copy and never edits it;
`scripts/jtools sync` refreshes the copy at the version the project names. What they know of a project is in its
`jtools.conf`, its tools in its `externals.conf`, its own extras in `scripts/*.local.sh`.

## The scripts

| Script | What it does |
|---|---|
| `scripts/setup.sh` | downloads the externals `externals.conf` names into `external/` — the JDK, Maven, Node.js, each platform's jmods the distributions' runtimes are linked from —, once, and again for what `externals.conf` names at another version; `--force` downloads everything again. The other scripts offer to run it when something is missing |
| `scripts/build.sh` | a clean build with every test (`BUILD_GOALS`); extra arguments go to Maven (`-DskipTests`) |
| `scripts/ci.sh` | the same, quiet, the page's dependencies installed exactly (`npm ci`), then `scripts/ci.local.sh` when there is one: what `.githooks/pre-push` runs before every push (`git config core.hooksPath .githooks`, which `jtools new` sets) |
| `scripts/run.sh` | builds when a source is newer than the jar, then runs a copy of it (so that a build can replace the jar meanwhile) with the arguments given: `scripts/run.sh --no-browser --port 9090`; `--rebuild` forces the build |
| `scripts/stop.sh` | stops the program running from this project |
| `scripts/package.sh` | `mvn package -Pdist`: the Linux, macOS and Windows archives into `releases/`; the previous versions' removed |
| `scripts/jtools` | the devkit itself: `sync`, `sync --check`, `version`, `new` (below) — the one script the devkit never refreshes |

Each in `.bat` for Windows but `jtools`, which is bash: on Windows, Git Bash runs it.

## scripts/jtools

```bash
scripts/jtools sync            # the managed files at JTOOLS_VERSION; what else differs said, not changed
scripts/jtools sync --check    # the same, changing nothing; exit 1 when anything differs (a project's CI may run it)
scripts/jtools version         # the devkit this project names, and where it was found
jtools new <Name> [--dir DIR] [--id ID] [--port N] [--bare DIR] [--no-git] [--no-build]   # a new project, from anywhere
```

`sync` refreshes the files `managed-files.list` names (the scripts, `.githooks/pre-push`, `.gitattributes`), and says
— without changing them, they are the project's to decide — where the project's pom names another `jtools-parent`
than the devkit's version, where its `externals.conf` names other versions than the devkit's default, and when its
own `scripts/jtools` differs from the devkit's (copy it by hand to take it). It commits nothing: read the diff, run
`scripts/ci.sh`, commit.

**Where the devkit comes from**, in this order — the Maven Wrapper's way:

1. `JTOOLS_HOME`, in `jtools.conf` or the environment: a clone of JToolsFramework, its working tree as it is — for whoever
   develops the framework and a project together;
2. `~/.m2/repository/org/jtools/framework/jtools-devkit/<version>/` (`M2_REPO` for another local repository): what the
   framework's `scripts/install.sh`, the delivery's `install.sh`, or any Maven build, put there;
3. the mirror, `<MIRROR>/jtools-devkit-<version>.jar`, with its authentication (below);
4. Maven Central.

A download is checked against the `.sha1` beside it before anything is unpacked. The devkit is unpacked into
`external/jtools-devkit-<version>/` — outside a project, `~/.cache/jtools/`; `JTOOLS_CACHE` names another folder —,
and again when `~/.m2` holds a newer jar of that version. It needs `curl` and `unzip`.

## jtools.conf

`KEY=VALUE` lines, `#` comments, a value is the rest of its line (spaces included, no quotes).

| Key | Default | What it is |
|---|---|---|
| `APP_ID` | — | the program's id: `<APP_MODULE>/target/<APP_ID>.jar`, `<APP_ID>.ini`, the releases' names, the process `stop.sh` looks for. Empty: a library — `run`, `stop` and `package` say there is nothing to run |
| `APP_NAME` | — | its name, as messages say it, and its `.exe` |
| `APP_MODULE` | `app` | the module building the program's jar |
| `BUILD_GOALS` | `clean package` | what `build`, `ci`, `run` (with `-DskipTests`) and `package` (with `-Pdist`) run |
| `JTOOLS_VERSION` | — | the devkit `scripts/jtools` takes the managed files from |
| `JTOOLS_HOME` | — | a clone of JToolsFramework to take them from instead (relative to the project, or absolute) |

## externals.conf

Each tool is deployed only when `externals.conf` names it: `JDK_*` → `external/jdk`, `MAVEN_*` → `external/maven`,
`NODE_*` → `external/node`, `GRADLE` → `external/gradle`; the host's JDK (`JDK_LINUX`, on Windows `JDK_WINDOWS`)
also as its archive, `external/jdk-linux.tar.gz`, whose `jlink` links the distributions' runtimes; `JMODS_LINUX`,
`JMODS_WINDOWS`, `JMODS_MACOS` → `external/jmods-linux.tar.gz`, `jmods-windows.zip`, `jmods-macos.tar.gz`, the jmods each
platform's runtime is linked from (a JDK from 24 on carries none: Temurin publishes them apart, of the same version).
All are hard links to the downloads. A project without a page drops `NODE_*`; one without a macOS distribution drops
`JMODS_MACOS`. A JDK up to 23 needs no `JMODS_*`: its archive carries its jmods (`JDK_WINDOWS`, `JDK_MACOS` → its
archives then).

The versions live there and nowhere else; `jtools new` copies the devkit's, `sync` reports where they differ. **A new
version is a line changed there, then `scripts/setup.sh`**: each external remembers the URL it was deployed from
(`external/.<name>.url`), and one deployed from another URL is deployed again — the other scripts say it is out of
date until then.
`JTOOLS_DOWNLOADS=<dir>` in the environment shares one downloads folder between projects.

**Behind a company's proxy**: `MIRROR=<url>` — an Artifactory, any HTTP folder holding the same file names —, and
everything is fetched as `<MIRROR>/<file name>`, the devkit too. Its authentication: `MIRROR_TOKEN` (sent in the header
`MIRROR_TOKEN_HEADER`, `Authorization: Bearer` by default; `X-JFrog-Art-Api:` for an Artifactory API key), or
`MIRROR_USER` and `MIRROR_PASSWORD`. Keep them out of `externals.conf`: put them in `externals.local.conf` beside it
(same syntax, ignored by git) or in the environment, which wins over the local file, which wins over `externals.conf`.

## A project's own extras: scripts/*.local.sh

Not managed, sourced or run by the managed scripts when present:

| File | When | What it may do |
|---|---|---|
| `scripts/setup.local.sh` | at the end of `setup.sh`, sourced | deploy externals of its own with `fetch URL` and `deploy FILE DIR` (`$force` set, the options `setup.sh` does not know in `$local_args`) — a language model, a speech engine, an Android SDK |
| `scripts/externals.local.sh` | at the start of `externals.sh`, sourced | define `missing_local_externals` (prints what of its extras is missing), export what its tools need |
| `scripts/ci.local.sh` | after the build in `ci.sh` | checks of its own |

On Windows: `scripts\setup.local.bat` and `scripts\ci.local.bat`, called with the same arguments.

## Changing a managed file

In JToolsFramework, never in a project: the source is the framework's `devkit/`; its CI runs a project made by a lone
`scripts/jtools` from the devkit just installed, and checks that the framework's own copies are the devkit's. A new
version of the framework carries the change; a project takes it with `JTOOLS_VERSION` and `scripts/jtools sync`.

The framework's own tools, in its `scripts/`: `install.sh` (the versions agree, build, test, install into `~/.m2`),
`check-versions.sh` (every pom and `scripts/jtools` name one version), `ci.local.sh` (the checks above).

---

Next: moving an existing program onto the framework — [ADOPTING.md](ADOPTING.md). Back to the start:
[GETTING-STARTED.md](GETTING-STARTED.md).
