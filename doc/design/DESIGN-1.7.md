# JToolsFramework 1.7 — the menu bar

## 1. What changes

`@jtools/web` gets the menu bar every program's page had to write for itself, over the endpoints `WebApp` already
answers for every program:

- **`MenuBar`**, `<jtools-menu-bar name="…" (stopped)="…">`: **File** ▸ Quit (`api/quit`, once confirmed; then
  `stopped` is emitted and the page says the program has stopped); **Settings** ▸ Dark theme, Automatic stop, Server
  port… (`api/settings`); **Logs** ▸ Open the log (`api/log/open`), the log file under it; **Help** ▸ Embedded Java
  runtime… and About (`api/about`). The program's name, an input, is at the right of the bar and in the texts. A line
  under the bar says how the last action went.
- **`Menu`**, `<jtools-menu label="…">`: one menu, its entries the content given — buttons, `<hr>` between groups,
  `role="menuitemcheckbox"` with `aria-checked` for a switch. The menu bar uses it for its own menus; a program puts
  its own inside the bar, between File and Settings. One menu is open at a time (`MenuBarState`, provided by each bar).
- **The program's own entries**: `fileItems` above File ▸ Quit, `helpItems` above Help's own, `about` in About's dialog.
- **`ProgramApiService`**: `about()`, `settings()`, `saveSettings()`, `quit()`, `openLog()`, typed by `ProgramAbout` and
  `ProgramSettings`, and `PORT_RANGE`.
- **`ThemeService`**: the light or dark palette, stamped as `data-theme` on `<html>` — the user's choice, kept per
  browser, else the browser's preference.

The starter's page uses it: `app.html`'s menu bar, the page saying the program has stopped, `styles.css`'s palette
switching on `data-theme`. Its `ApiService` keeps the business's API alone; the program's endpoints are the library's.

## 2. Choices

- **Projection, not configuration**: a program's menus and entries are its own template, with its own bindings — no
  array of menu descriptions to keep in step with the code that runs them.
- **Unencapsulated styles, every rule under `jtools-menu-bar`**: the entries a program gives are projected, so outside
  the bar's view, and an encapsulated style would not reach them. A switch's tick is drawn from `aria-checked`, so a
  program's switch needs no markup of the library's.
- **The page's colours**: the styles read the page's CSS variables (`--background`, `--surface`, `--text`, `--muted`,
  `--border`, `--hover`, `--accent`, `--error-fill`, `--error-text`, `--ok-fill`, `--ok-text`), each with the light
  palette's value as a fallback, so that a page without them still shows a readable bar.
- **English texts**, as the page's other library texts (`apiErrorMessage`); the server's own refusals stay in the
  request's language.
- **The runtime dialog follows the server's Java**: its Temurin and OpenJDK links are named after `javaVersion`'s major
  version, since the JDK is each project's `externals.conf`, not the framework's.
- **One storage key, `jtools.theme`**: the browser keeps storage per origin, and each program has its own port.

## 3. What changes for a project

`jtools sync` does not touch the page: a project made before 1.7 takes the menu bar by hand — `<jtools-menu-bar>` in
its `app.html`, `MenuBar` in its imports, and, for Dark theme to switch its palette, a `:root[data-theme='dark']` block
in its `styles.css` (doc/ADOPTING.md). A page that keeps its own menus loses nothing: the library only adds.

## 4. 1.7.1

Settings' switch of the server's stop is named **Automatic stop**; its tooltip says what on and off do and that the
choice is kept. `scripts/check-versions.sh` reads `devkit/pom.xml` too, which its globs missed: a version written
everywhere but there was reported as agreeing, and the devkit was built at the older version. 1.7.0 was not published.
