# JToolsFramework 1.5 — package's own options

## 1. What changes

The devkit's `package` gives a project options of its own, declared in its `jtools.conf`, one line each, read alike
by `package.sh` and `package.bat`:

```
PACKAGE_OPTION=with-model -Pdist-model
PACKAGE_ARCHIVES=with-model -linux-with-model.tar.gz -windows-with-model.zip
```

- `--<name>` on `package`'s command line adds the option's Maven arguments to its build (`mvn clean package -Pdist
  -Pdist-model`); every other argument goes to Maven as it was written.
- `PACKAGE_ARCHIVES` names what the option makes in `releases/` besides the standard archives, as
  `<APP_ID>-<version><suffix>`: once the standard archives are checked, `package` checks that these are there; and it
  removes their previous versions, as it does the standard archives', whether the option was asked for or not.
- An option takes no value; a name the file does not declare is an argument for Maven.

## 2. Why declared, and not a hook

A program whose distributions come in more than one flavour — DCSBuilder's, with a language model and without — had
to keep packaging scripts of its own, and so could not take the devkit's. A `package.local.sh` hook would have let it
do anything, but written twice, in shell and in batch; a declaration says the same facts once, for both systems, and
needs no code in the project. What the declaration does not say — the contents of a flavour's archives — is the
project's to check, in its own CI (`scripts/linux/ci.local.sh`).

## 3. jtools.conf: the environment wins, in every script

The stubs (`jtools`, `jtools.bat`) kept a value the environment already set — `JTOOLS_HOME=… jtools sync` takes the
devkit from a clone for one run —, where the other scripts let `jtools.conf` override it. From 1.5 every script
keeps the environment's value, and `DEVKIT.md` says who reads the file, that it is the project's own, and that a key
written twice counts by its first line. On Windows, `build.bat`, `ci.bat` and `package.bat` now open a `setlocal` of
their own, as the other scripts did: what `externals.bat` sets — `APP_ID`, `JAVA_HOME`, the `PATH` lengthened by the
externals — no longer outlives them in the command prompt, where one project's `APP_ID` would otherwise win over the
next project's `jtools.conf`.

## 4. What changes for a project

Nothing, unless it declares options: `package` without them is 1.4's. `jtools sync` brings the new `package.sh` and
`package.bat`; `jtools new`'s `jtools.conf` shows the two keys, commented out.

The workflow `delivery` declares an option in its trial project, on Linux and on Windows: `--quick` (`-DskipTests`),
with an archive of its own. It checks that the Maven argument reached the build (no test ran) and that a
`-Dkey=value` beside it went through whole; that the option's archive of this version is kept and the previous
version's removed; and that a missing declared archive fails `package`. And that `BUILD_GOALS=clean`, set in the
environment, wins over the trial project's `jtools.conf`.
