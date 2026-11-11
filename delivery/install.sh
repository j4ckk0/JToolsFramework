#!/usr/bin/env bash
#
# Installs JToolsFramework on this computer from this folder — nothing to clone, nothing to build — then, when asked,
# makes a first project: its folder, its development environment (JDK, Maven, Node.js in its external/), built.
#
#   ./install.sh                                   asks the project's name and folder (at a terminal)
#   ./install.sh Bookshelf                         the project ./Bookshelf, beside this folder
#   ./install.sh Bookshelf --dir ~/work/shelf      the project in ~/work/shelf (a folder that does not exist yet)
#   ./install.sh Bookshelf --port 8094 …           any other option of jtools new (jtools new --help)
#   ./install.sh --no-project                      the framework and jtools alone
#
# The framework goes into the local Maven repository (~/.m2/repository; M2_REPO says another), jtools into
# ~/.local/bin (JTOOLS_BIN says another). Needs bash, curl, unzip, tar and git — on Windows, Git Bash.
#
set -euo pipefail
here="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"
version=$(cat "$here/VERSION")

case "${1:-}" in
  -h|--help) sed -n '3,13p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
esac

missing=""
for tool in curl unzip tar git; do command -v "$tool" >/dev/null || missing="$missing $tool"; done
[ -z "$missing" ] || { echo "install.sh: missing:$missing — install them first, with your system's package manager" >&2; exit 1; }

# the framework's artifacts: added beside what the repository holds, this version's replaced when already there
m2=${M2_REPO:-$HOME/.m2/repository}
for artifact in "$here"/repository/org/jtools/framework/*/"$version"; do
  name=$(basename "$(dirname "$artifact")")
  target="$m2/org/jtools/framework/$name/$version"
  [ -d "$target" ] && echo "== $name $version was in $m2 already: replaced"
  mkdir -p "$target"
  cp "$artifact"/* "$target/"
done
echo "== JToolsFramework $version in $m2"

bin=${JTOOLS_BIN:-$HOME/.local/bin}
mkdir -p "$bin"
cp "$here/jtools" "$bin/jtools"
chmod +x "$bin/jtools"
echo "== jtools in $bin"
case ":$PATH:" in
  *":$bin:"*) ;;
  *) echo "   $bin is not on your PATH: add it, in ~/.bashrc (or ~/.zshrc):  export PATH=\"$bin:\$PATH\"" ;;
esac

# The project: named on the command line, else asked at a terminal. By default beside this folder, never inside it.
base=$PWD
[ "$PWD" = "$here" ] && base=$(dirname "$here")
if [ "${1:-}" = "--no-project" ]; then set --; no_project=true; else no_project=false; fi
if [ $# -eq 0 ] && ! $no_project && [ -t 0 ]; then
  read -r -p "The project's name (CamelCase, a letter first; empty: no project now): " name
  if [ -n "$name" ]; then
    read -r -p "The folder its files go in [$base/$name]: " dir
    dir=${dir:-$base/$name}
    case "$dir" in "~"|"~/"*) dir=$HOME${dir#\~} ;; esac
    set -- "$name" --dir "$dir"
  fi
elif [ $# -gt 0 ] && [ "$base" != "$PWD" ] && [[ " $* " != *" --dir "* ]]; then
  set -- "$@" --dir "$base/$1"
fi
if [ $# -eq 0 ]; then
  echo "== ready: jtools new <Name> [--dir DIR] makes a project (jtools new --help)"
  exit 0
fi
exec "$bin/jtools" new "$@"
