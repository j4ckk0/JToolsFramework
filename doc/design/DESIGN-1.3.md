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

## 2. Why a version of its own

1.2.0 was tagged, and its delivery published on GitHub, before the rename: the names a program writes changed after a
release. 1.2.0 stays as it was published; 1.3.0 carries the new names. Maven Central's first release is 1.3.0.

## 3. What changes for a program on 1.2

`jtools-parent` 1.3.0 in its pom and `JTOOLS_VERSION=1.3.0` in its `jtools.conf`, then `scripts/jtools sync`; in its
code, the renames of the table — `.routes(` → `.endpoints(`, `Route` → `Endpoint`, `server.route(` →
`server.endpoint(` —, which the compiler points out one by one. Its own test suite is the acceptance.
