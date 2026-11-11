# __APP_NAME__ — architecture

```
 browser                               the program (one JVM)
 ┌──────────────────────┐   HTTP     ┌──────────────────────────────────────────────────────┐
 │ Angular page (view)  │ ─────────▶ │ WebServer ── /api/* ──▶ api ──▶ core                 │
 │  ApiService          │   JSON     │    │                 (translation) (business)       │
 │  @jtools/web         │ ─ alive ─▶ │    └─ PageWatch: no page left → stop                │
 └──────────────────────┘ ◀─ page ─  │    └─ static/ (the frontend's jar) → the page       │
                                     └──────────────────────────────────────────────────────┘
```

**The rule**: every decision is the server's. The page holds no business — no rule, no computation, no state
beyond what it displays; it asks `api/…` and shows the answer. A rule found in the page, or in `api`, belongs in
`core`. That keeps the business testable without HTTP nor a browser, and usable from any other Java program.

## The modules

| Module | Package | What it is |
|---|---|---|
| `core` | `org.jtools.__APP_ID__.core` | the business: `__APP_NAME__`, the one entry point |
| `api` | `org.jtools.__APP_ID__.api` | the HTTP API over core: `__APP_NAME__Api.routes(server, application)` |
| `frontend` | — (Angular) | the page, as a jar of `static/` resources |
| `app` | `org.jtools.__APP_ID__.app` | the program: `Launcher`, a `WebApp` serving the API |

What is not here is JToolsFramework's: `WebServer` (the routes, the page, the presence, the Host and Origin checks),
`Exchange` (what a route reads of a request), `PageWatch` (the stop once the last page has gone), `WebApp` (the command
line, the Settings menu's port, the `.ini`, the log, the browser, and the routes `/api/settings`, `/api/about`,
`/api/quit`, `/api/log/open`), `@jtools/web` (the page's presence, the server's errors), the build (`jtools-parent`:
the page, the program's jar, the jlink distributions) and the scripts. Its `doc/PATTERN.md` says the conventions.

## The API

| Route | Answer | core |
|---|---|---|
| `GET /api/info` | `{"name", "version"}` | `__APP_NAME__.about()` |

Its TypeScript mirror is `frontend/src/app/api.service.ts`.

## Where a new feature goes

1. **core**: the business — the model, the rules, the state — with its tests (`core/src/test`), no HTTP.
2. **api**: a route in `__APP_NAME__Api` reading the request, calling core, returning a Map/List tree (a refusal is a
   `WebServer.Refusal`, which the page reads with `apiErrorMessage`); tested against a `WebServer` on port 0.
3. **frontend**: a method in `ApiService`, a component showing its answer, a route in `app.routes.ts`; tested with
   vitest (`*.spec.ts`), the HTTP calls faked by `HttpTestingController`.
