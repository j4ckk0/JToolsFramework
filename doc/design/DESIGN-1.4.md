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
