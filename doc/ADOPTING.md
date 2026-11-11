# Adopting the framework in an existing program

For a program already written — a Java server and a page — that wants what JToolsFramework gives. A new program needs
none of this: `jtools new` makes it on the framework from the start ([GETTING-STARTED.md](GETTING-STARTED.md)).

## Step by step

Each step stands alone, the program's tests green after it — they are the acceptance; take the steps in this order,
the cheapest first.

### 1. The parent pom

The root pom's `<parent>` becomes `jtools-parent`, at the framework's version, and loses what the parent says:
`maven.compiler.release`, the encoding, JUnit's dependency and version, the plugins' versions, the surefire working
directory. The project's coordinates, modules and its own dependencies stay.

*Check*: the same tests run, the same jar is made (`unzip -p … META-INF/MANIFEST.MF`).

### 2. The server and the program

The program's module depends on `jtools-app`, sets `jtools.program.skip` to `false` and the `jtools.app.*`
properties ([CONVENTIONS.md](CONVENTIONS.md)), and loses its shade and jar configuration. Then, in the code:

1. **The `main`** becomes a `WebApp` ([LIBRARIES.md](LIBRARIES.md)): its name, its default port, the page's folder
   (`pageRoot("web")` for a page outside `static/`), the program's own options (`option`, `fileOperand`), and
   `routes(server -> …)`. What the old launcher did — the command line, the `.ini`, the log, the browser, the stop —
   goes.
2. **Each route** registers on the `WebServer` with `get` or `post` and returns its answer: a `Map` or `List` tree,
   `Json.raw(text)` for JSON the program's own writer made, an `Answer` for bytes or XML. A refusal throws
   `WebServer.Refusal(status, reason)`; the program's exceptions map to statuses with `refuse(MyException.class, 400)`.
   What the old server did — the static files, `/api/alive`, `/api/bye`, `/api/quit`, `/api/settings`, `/api/about`,
   the Origin check — goes.
3. **Settings of the program's own** (beside the stop and the port) become a `SettingsFields`; an `/api/about` field
   of its own, `aboutFields`.
4. **The tests** start the program's server as `main` does: `program().start("--port", "0", "--keep-alive")`, the
   preferences in a node of the test's own (the system property `<id>.preferences`).

A library module of the program's, used by other programs, may stay free of the framework: only the modules serving
the program need it.

*Check*: the program's own tests, adapted only where the protocol changes — `/api/bye` answers 204, every refusal is
`{"error"}` JSON, a request from another site or naming a foreign host is refused (403). The page keeps working: the
presence protocol is the same.

### 3. The distributions

With `-Pdist`, jtools-parent's profile builds the Linux, macOS and Windows archives with a runtime jlink trims, from the
JDK archives in `external/` (`jdk-linux.tar.gz`… or Temurin's own names; `jtools.jdk.archives` names another folder).
The program's assembly descriptors, launch4j configuration and launchers go; what the archives carry of the repository
is `jtools.dist.include` (`samples/**`), of the module `src/dist/files/`. A flavour of the program's own — archives
carrying more, a large model or a data set — stays its own profile, made of what the parent's has just made
(`target/runtime/`, `target/jtools-dist-work/`).

*Check*: the archives hold the same files as before (`tar tzf`, `unzip -Z1`) but `jre/`, and the program runs on the
runtime of one.

### 4. The scripts (optional)

`jtools.conf` at the root (APP_ID, APP_NAME, APP_MODULE, BUILD_GOALS, `JTOOLS_VERSION`), `scripts/jtools` copied from
the devkit, then `scripts/jtools sync`: the devkit's scripts replace the project's. What the project's own did beyond
them goes into `scripts/*.local.sh` ([DEVKIT.md](DEVKIT.md)). A program whose scripts do much of their own keeps them: the
devkit is a convenience, not a condition.

### 5. The page (an Angular page)

`@jtools/web`'s `provideJTools()` in place of the page's own presence code. A page in plain JavaScript keeps its own:
the protocol is the same three lines ([examples/hello](../examples/hello)).

---

Next: why the framework is as it is — [design/](design). Back to the start: [GETTING-STARTED.md](GETTING-STARTED.md).
