# JToolsFramework 1.3 — endpoint, the word an API's readers expect

## 1. What changes

What a client calls on the server is an **endpoint**: the word API documentation uses, and the one the main page
uses. 1.2 called it a *route* in its code and its guides; 1.3 says endpoint everywhere — the code, its javadoc, the
guides, the template, the examples. Nothing else changes: the endpoints a program declares answer as they did.

| 1.2 | 1.3 |
|---|---|
| `WebServer.Route` | `WebServer.Endpoint` |
| `server.route(path, …)` — any method | `server.endpoint(path, …)` |
| `server.get(path, Route)`, `server.post(path, Route)` | the same, taking an `Endpoint` |
| `WebApp.routes(server -> …)` | `WebApp.endpoints(server -> …)` |
| the template's `<Name>Api.routes(server, application)` | `<Name>Api.endpoints(server, application)` |

**A route stays a route in the page**: Angular's `app.routes.ts`, `provideRouter(routes)` and a single-page
application's own paths (`/settings`, which the server answers with `index.html`) are the page's navigation, not the
API's — the page has routes, the server has endpoints.

## 2. Windows, natively — and Linux; not macOS

1.2's delivery worked on Linux alone: its shell scripts took the Linux JDK, Maven and Node.js whatever the computer,
so on Windows (Git Bash) or macOS a new project's first build failed. 1.3 develops on **Linux, or Windows 10 and
later**, each with scripts of its own:

| Linux | Windows |
|---|---|
| `install.sh`, from `jtools-1.3.0.tar.gz` | `install.bat`, from `jtools-1.3.0.zip`: double-clicked, it asks the project's name and folder |
| `scripts/jtools` (`sync`, `version`, `new`) | `scripts\jtools.bat` — and `jtools.bat` on the `PATH`, which the installer offers to add |
| `jtools-sync.sh`, `new-project.sh` | `jtools-sync.bat`, `new-project.bat` |
| `scripts/*.sh` | `scripts\*.bat`, which take Windows' own `curl` and `tar` |

The Windows scripts are `.bat` files and nothing else: no Git Bash, no PowerShell script. Windows 10 has the tools they
need (`curl`, `tar`, `certutil`); a project's repository needs git, Git for Windows. The shell scripts still run in Git
Bash, where they take the Windows tools. A project made on Windows is the one made on Linux, but for its line endings:
the workflow `delivery` checks it, installing the delivery on a Linux and a Windows machine at every push.

**macOS** is a platform the programs ship to — its distribution is built from Linux or Windows —, not one they are
developed on.

## 3. Why a version of its own

1.2.0 was tagged, and its delivery published on GitHub, before the rename: the names a program writes changed after a
release. 1.2.0 stays as it was published; 1.3.0 carries the new names. Maven Central's first release is 1.3.0.

## 4. What changes for a program on 1.2

`jtools-parent` 1.3.0 in its pom and `JTOOLS_VERSION=1.3.0` in its `jtools.conf`, then `scripts/jtools sync`; in its
code, the renames of the table — `.routes(` → `.endpoints(`, `Route` → `Endpoint`, `server.route(` →
`server.endpoint(` —, which the compiler points out one by one. Its own test suite is the acceptance.
