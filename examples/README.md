# Examples

Three very small programs, each one step further than the one before — a class or two, a page in plain JavaScript, a
test. Built and tested with the framework, so that they never go stale; never published.

| Step | Example | What it shows | Port |
|---|---|---|---|
| 1 | [`hello/`](hello) | **the hello world**: a `WebApp` with one route; its page, the presence that stops the server once the page is closed, Quit; a test starting the program on a free port | 8089 |
| 2 | [`todo/`](todo) | **the server holds the business**: a to-do list in a class of its own (`Todos`, pure Java), its HTTP face (`TodoApp`), a rule broken refused as `{"error"}` and shown by the page as it is; the page holds no state | 8088 |
| 3 | [`greeter/`](greeter) | **what a program brings of its own**: its texts in English and French, answered in the page's language; an option (`--greeting Howdy`); a setting kept from one run to the next (`SettingsFields`) | 8087 |

## Run one

```bash
scripts/install.sh                                             # once, from the root: the framework and its examples
external/jdk/bin/java -jar examples/hello/target/hello.jar     # the page opens in the browser; close it, it stops
external/jdk/bin/java -jar examples/todo/target/todo.jar
external/jdk/bin/java -jar examples/greeter/target/greeter.jar --greeting Howdy
```

`--help` lists what each takes — the framework's options, and the greeter's own `--greeting`.

## Read one

Start from the program's class (`Hello`, `TodoApp`, `Greeter`), then its page (`src/main/resources/web/index.html`),
then its test. The Angular kind of page is the generated project's: `jtools new` makes one
([../doc/GETTING-STARTED.md](../doc/GETTING-STARTED.md)).
