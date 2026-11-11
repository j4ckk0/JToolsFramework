# JToolsFramework 1.6 — release

## 1. What changes

The devkit gets the step after `package`: `scripts/linux/release.sh` and `scripts\windows\release.bat`, managed like
the others. They publish the version the root pom names, once its tag `v<version>` exists and `package` has made its
archives:

- **the notes** are `CHANGELOG.md`'s `## <version>` section (a date may follow the version on its line), its blank
  lines at either end left out, the archives' SHA-256 added beneath; `release` refuses a version with no section;
- **with `RELEASE_GITHUB=<owner>/<repository>`** in `jtools.conf`, a GitHub Release from the tag, which must be pushed
  there first — GitHub would otherwise make one from the default branch —, every `releases/<id>-<version>*` file
  attached. The token: `GITHUB_TOKEN`, else the one line of `~/.config/github/<id>-release-token` (on Windows, under
  `%USERPROFILE%`), a fine-grained token with *Contents: read and write* on that repository. It goes to curl through a
  headers file of curl's own, never on a command line. A refused token (401, 403) is said as such;
- **without it**, a local release: the tag checked here (when the project has git), then
  `releases/<id>-<version>.sha256` — `sha256sum`'s format, which `sha256sum -c` reads — and `-notes.md` beside the
  archives;
- `--draft` makes a GitHub draft, `--dry-run` prints the notes and writes the request a GitHub Release would send to
  `releases/<id>-<version>-release.json`, publishing nothing.

`jtools new`'s projects get a `CHANGELOG.md` with their `0.1.0` section, and `jtools.conf` shows `RELEASE_GITHUB`,
commented out.

## 2. Shell and batch, no other tool

`release.sh` uses `curl` and `sha256sum`; `release.bat` Windows' own `curl.exe` and `certutil`. Neither needs Python
or PowerShell: the request's JSON is written by the scripts themselves — the notes escaped line by line (`\` and `"`,
tabs as `\t`; in batch spaces as `\u0020` too, since `set /p`, which writes a line without ending it, drops its
leading spaces) — and GitHub's answer, indented one field a line, is read for the release's id and page. Both scripts
write the same request: the same notes, the same JSON string.

## 3. What changes for a project

`jtools sync` brings the two scripts. A project that published its releases by its own script — XsdViewer's
`scripts/release.sh` — names its repository in `jtools.conf` and takes the devkit's; its notes keep coming from
`CHANGELOG.md`.

The workflow `delivery` releases its trial project on Linux and on Windows: a local release, whose checksums are
checked against the archives; a GitHub one's request, read as JSON and compared with the notes — a changelog section
of quotes, a backslash, `%`, `&`, `<`, `|`, `^`, `!`, a tab, indented lines and accents; and a token GitHub refuses.

## 4. 1.6.1: what Maven Central's 1.6.0 lacks

Maven Central's 1.6.0 — the tag `v1.6.0` is its commit — was uploaded before its `release.bat` worked, and Central
neither removes nor replaces a version. Its `release.bat` stops at once: the lines that read the version, copied from
`package.bat`, stopped short of their `:versioned` label; and past that it would find no changelog section, its search
for the non-blank lines, `findstr /r "[^ ]"`, being read as two words — a space in a plain `findstr` search string means
"or", so it takes `/c:`. Everything else of 1.6.0 is 1.6.1's. 1.6.1 is the first version whose `release.bat` works; a
project on 1.6.0 takes it with `JTOOLS_VERSION=1.6.1` and `jtools sync`.

The workflow's release step on Windows ends with `exit 0`: GitHub ends a PowerShell step with the exit code of its last
command, which there is the deliberately refused token's.
