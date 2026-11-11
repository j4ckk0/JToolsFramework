# Examples

Programs to read, built and tested with the framework (`scripts/install.sh`) so that they never go stale, and never
published.

| Example | What it shows |
|---|---|
| [`hello/`](hello) | the smallest program: `Hello.java`, a `WebApp` with one route; `index.html`, its page in plain JavaScript — the presence that stops the server once the page is closed, a request, its refusal, Quit; `HelloTest`, the program started on a free port and asked over HTTP |

The Angular kind of page is the generated project's: `scripts/jtools new` makes one (doc/GETTING-STARTED.md).

```bash
scripts/install.sh                                  # the framework, the examples with it
external/jdk/bin/java -jar examples/hello/target/hello.jar  # the page in the browser, on port 8089
```
