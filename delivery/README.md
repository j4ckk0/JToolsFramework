# JToolsFramework __VERSION__ — the delivery

Everything a developer needs to start a project on JToolsFramework, without cloning or building it:

```bash
tar xzf jtools-__VERSION__.tar.gz
jtools-__VERSION__/install.sh                       # asks the project's name and the folder its files go in
jtools-__VERSION__/install.sh Bookshelf --dir ~/work/bookshelf   # or says them
```

`install.sh` puts the framework into your local Maven repository (`~/.m2/repository`) and `jtools` into
`~/.local/bin`, then makes the project with `jtools new`: its files in the folder you chose, its development
environment — a JDK, Maven and Node.js of its own, in its `external/` folder — downloaded, and a first build.
`./install.sh --help` lists its options; `jtools new --help`, the project's.

**What you need**: Linux, macOS, or Windows with Git Bash; `bash`, `curl`, `unzip`, `tar` and `git`. And the internet
the first time: the project's tools (about 450 MB) and the libraries its build uses (Maven plugins, npm packages)
are downloaded — the framework itself is in this archive.

| In this folder | What |
|---|---|
| `install.sh` | the installer |
| `jtools` | the script that makes a project and keeps it up to date (`jtools new`, `scripts/jtools sync`) |
| `repository/` | the framework's artifacts, as Maven lays them out: jtools-parent, jtools-bom, the libraries, jtools-dist, jtools-devkit |
| `VERSION`, `LICENSE` | its version, its licence (Apache 2.0) |

Then: https://github.com/j4ckk0/JToolsFramework/blob/v__VERSION__/doc/GETTING-STARTED.md, from step 4.
