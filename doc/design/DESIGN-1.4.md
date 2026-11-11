# JToolsFramework 1.4 — a project's scripts, by system

## 1. What changes

A project's scripts were side by side in `scripts/`, `run.sh` beside `run.bat`. 1.4 puts each system's in a folder
of its own, so that a developer sees the scripts of their system, and those only:

```
scripts/
  linux/    setup.sh  build.sh  ci.sh  run.sh  stop.sh  package.sh  externals.sh  jtools
  windows/  setup.bat build.bat ci.bat run.bat stop.bat package.bat externals.bat jtools.bat
```

The commands follow: `scripts/linux/run.sh`, `scripts\windows\run.bat`, `scripts/linux/jtools sync`. A project's own
extras go with their system's scripts: `scripts/linux/*.local.sh`, `scripts\windows\*.local.bat`. The stubs find their
project two folders up, and only from their own place in it — `scripts/linux/`, `scripts\windows\` — so that the copy
installed in `~/.local/bin` takes no `jtools.conf` above it for a project's.

The framework carries its copies of the devkit's the same way; its own tools stay in `scripts/` (`install.sh`,
`check-versions.sh`, `delivery.sh`), but for `ci.local.sh`, which `ci.sh` looks for beside itself: `scripts/linux/`.

## 2. run.bat, batch alone

1.3's `run.bat` asked PowerShell whether a source was newer than the jar: the one PowerShell left in the framework.
Batch cannot compare two files' times — `cmd` says them in the machine's format, to the minute —, but Windows marks
every file it writes "to archive" (attribute A): `run.bat` asks `robocopy` for the project's files so marked, the
generated folders left out, builds when there is one, and clears the marks once the build is done. A build of
`build.bat`'s leaves them: `run.bat` builds once more after it.

## 3. What changes for a project on 1.3

`jtools-parent` 1.4.0 in its pom, `JTOOLS_VERSION=1.4.0` in its `jtools.conf`, then its `scripts/jtools sync`: the
sync moves its scripts by `moved-files.list` — the devkit's own copies into `linux/` and `windows/`, the old ones
removed; the project's `*.local.*` moved, never overwritten; the new stubs put in place, and the old two left for the
developer to remove (the sync may be running from one). `sync --check` lists what is still to move. Its documents —
its README, its CLAUDE.md — name its scripts where 1.3 had them: they are the project's to update.

The workflow `delivery` tries it: it makes a project with the 1.3.1 delivery, moves it to 1.4.0 with its old stub, and
builds it with the new scripts, on Linux and on Windows.

## 4. 1.4.1: what both systems need, said once

1.4.1 changes no project and no API; it acts on the 1.4.0 architecture review.
- **`devkit/new-project.list`**: what `jtools new` adds to the template and the managed files — the default port, the
  two stubs, `externals.conf`, the page's npm files. `new-project.sh` and `new-project.bat` read it, and both `jtools-sync`
  scripts take the stubs from it; Linux and Windows were each writing the list themselves, and a change made on one side
  only gave the two systems different projects. `scripts/check-versions.sh` also checks that the two stubs fetch the
  devkit from the same Maven Central URL.
- **`scripts/install-locally.sh`**, the framework's own installer, was `scripts/install.sh`: one name with the
  delivery's `install.sh`, which does something else.
- **`AppTexts`** holds jtools-app's texts, and `LaunchOptions` the `.ini`'s extension: `CommandLine` and `Logging` no
  longer reach back into `WebApp`, which holds them.
- **`.github/report-failure.bat`** makes a failed Windows step's log its error annotation, where the workflow repeated
  the same PowerShell line in each step; the workflow tests it first.

## 5. 1.4.2: a project that writes its versions out

The devkit's `package.sh` and `package.bat` read the program's version from the root pom's `<revision>`, which
`jtools new`'s projects have; a program adopting the devkit with its versions written out in each pom
(`<version>5.7.0</version>`) had its archives built, then `package` failed, finding no version. From 1.4.2, without
a `<revision>` they read the root pom's own `<version>` — the first one outside its `<parent>` —, and ADOPTING.md
says so. The workflow `delivery` packages its trial project once more with its poms rewritten that way, on Linux
and on Windows.
