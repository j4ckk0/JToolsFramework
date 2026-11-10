#!/usr/bin/env bash
#
# Creates a new org.jtools project from templates/webapp: a Java server holding the business (core, api), an Angular
# page as its view (frontend), the program and its distributions (app), the framework's scripts, its externals.conf
# and a port of its own; then, unless told otherwise, its externals, a first build, a git repository with its first
# commit, its bare repository, and the first push (which runs the new project's CI).
#
#   scripts/new-project.sh SensorSimulator                # ~/Developpement/git/SensorSimulator, git-bare/SensorSimulator.git
#   scripts/new-project.sh SensorSimulator --port 8094    # the port of its .ini (default: the next free one, doc/PORTS.md)
#   scripts/new-project.sh SensorSimulator --id sensors   # the program's id (default: the name in lower case)
#   scripts/new-project.sh Demo --dir /tmp/Demo --no-git  # elsewhere, files only (a try, the framework's CI)
#   scripts/new-project.sh Demo --no-build                # the files and git, no externals nor build (no push either)
#
# The name is the Java class of the business (core) and the program's name: CamelCase, a letter first. The port is
# entered in doc/PORTS.md, the registry that keeps the programs apart — commit it in the framework afterwards.
# A new project is a private one: git/ and git-bare/ beside the folder holding the framework (github/, where the
# published projects are), whence git-bare/push-to-nas.sh takes it to the NAS.
#
set -euo pipefail
framework="$(cd "$(dirname "$(readlink -f "$0")")/.." && pwd)"
cd "$framework"

usage() { sed -n '3,17p' "$0" | sed 's/^# \{0,1\}//' >&2; exit 2; }

name= id= port= dir= bare= git=true build=true
while [ $# -gt 0 ]; do
  case "$1" in
    --port) port=${2:?--port takes a number}; shift 2 ;;
    --id) id=${2:?--id takes a name}; shift 2 ;;
    --dir) dir=${2:?--dir takes a folder}; shift 2 ;;
    --bare) bare=${2:?--bare takes a folder}; shift 2 ;;
    --no-git) git=false; shift ;;
    --no-build) build=false; shift ;;
    -h|--help) usage ;;
    -*) echo "unknown option: $1" >&2; usage ;;
    *) [ -z "$name" ] || { echo "one name only: $1" >&2; usage; }; name=$1; shift ;;
  esac
done
[ -n "$name" ] || usage
[[ $name =~ ^[A-Z][A-Za-z0-9]*$ ]] || { echo "$name: a CamelCase name, a letter first (it is a Java class)" >&2; exit 2; }
id=${id:-$(tr '[:upper:]' '[:lower:]' <<< "$name")}
[[ $id =~ ^[a-z][a-z0-9]*$ ]] || { echo "$id: an id of lower-case letters and digits (it is a Java package)" >&2; exit 2; }
developpement=$(dirname "$(dirname "$framework")")   # the folder holding github/, git/ and git-bare/
dir=${dir:-$developpement/git/$name}
bare=${bare:-$developpement/git-bare/$name.git}
[ ! -e "$dir" ] || { echo "$dir exists: choose another name or --dir" >&2; exit 1; }
if $git; then [ ! -e "$bare" ] || { echo "$bare exists: choose another name or --bare" >&2; exit 1; }; fi

# The port: one no other program of doc/PORTS.md has, the next free one by default.
ports=$(sed -n 's/^| *\([0-9][0-9]*\) *|.*/\1/p' doc/PORTS.md)
if [ -z "$port" ]; then
  port=$(( $(sort -n <<< "$ports" | tail -1) + 1 ))
  while grep -qx "$port" <<< "$ports"; do port=$((port + 1)); done
fi
[[ $port =~ ^[0-9]+$ ]] && [ "$port" -ge 1024 ] && [ "$port" -le 65535 ] || { echo "$port: a port from 1024 to 65535" >&2; exit 2; }
if grep -qx "$port" <<< "$ports"; then
  echo "port $port is taken in doc/PORTS.md ($(grep -E "^\| *$port *\|" doc/PORTS.md | cut -d"|" -f3 | xargs | sed "s/ /, /g")): choose another with --port" >&2; exit 1
fi

version=$(sed -n 's:.*<jtools.version>\(.*\)</jtools.version>.*:\1:p' parent/pom.xml)
mkdir -p "$(dirname "$dir")"
home=$(realpath --relative-to="$dir" "$framework" 2>/dev/null || echo "$framework")
case "$dir" in /tmp/*|"${TMPDIR:-/nonexistent}"/*) home=$framework ;; esac   # a try elsewhere: the framework by its full path

echo "== $name ($id, port $port) in $dir, from JToolsFramework $version"

# The template, its placeholders filled in — in the files and in the paths.
substitute() {
  sed -e "s/__APP_NAME__/$name/g" -e "s/__APP_ID__/$id/g" -e "s/__APP_PORT__/$port/g" \
      -e "s/__JTOOLS_VERSION__/$version/g" -e "s#__JTOOLS_HOME__#$home#g"
}
(cd templates/webapp && find . -type f) | while read -r file; do
  target="$dir/$(substitute <<< "${file#./}")"
  mkdir -p "$(dirname "$target")"
  substitute < "templates/webapp/$file" > "$target"
done

# What the framework owns: the managed scripts, its externals.conf, the page's dependencies and editor settings
# (the same Angular as @jtools/web's, which the framework's CI builds).
grep -v -E '^(#|$)' scripts/managed-files.list | while read -r file; do
  mkdir -p "$dir/$(dirname "$file")"
  cp -p "$file" "$dir/$file"
done
cp externals.conf "$dir/"
for file in package.json package-lock.json tsconfig.spec.json .editorconfig .prettierrc .gitignore; do
  cp "web/$file" "$dir/frontend/$file"
done
sed -i "s/\"name\": \"jtools-web\"/\"name\": \"$id\"/" "$dir/frontend/package.json" "$dir/frontend/package-lock.json"

# The port, entered in the registry (kept sorted by port) — by a real project, with its repository; a try is not one.
if $git; then
  # where it is: git/<Name> under the folder holding git/ and git-bare/, its full path elsewhere
  root=$(dirname "$(dirname "$framework")")
  case "$dir" in "$root"/*) where=${dir#"$root"/} ;; *) where=$dir ;; esac
  {
    sed -n '1,/^|---/p' doc/PORTS.md
    { sed -n '/^|---/,$p' doc/PORTS.md | sed 1d | grep '^|'; echo "| $port | $name | $where |"; } | sort -s -t'|' -k2,2n
    sed -n '/^|---/,$p' doc/PORTS.md | sed 1d | grep -v '^|' || true
  } > doc/PORTS.md.new && mv doc/PORTS.md.new doc/PORTS.md
  echo "== port $port entered in doc/PORTS.md (commit it in JToolsFramework)"
fi

cd "$dir"
if $build; then
  # the externals, from the framework's downloads when it has them: nothing downloaded twice
  [ -d "$framework/external/downloads" ] && export JTOOLS_DOWNLOADS="$framework/external/downloads"
  scripts/setup.sh
  scripts/build.sh -q
  echo "== built: app/target/$id.jar"
fi

if $git; then
  git init -q -b master
  git config core.hooksPath .githooks
  git add -A
  git commit -q -m "Initial commit: $name, generated by JToolsFramework $version

A Java server holding the business (core, api), an Angular page as its view (frontend), the program
stopping once its last page is closed and its distributions (app); the framework's scripts and externals."
  mkdir -p "$(dirname "$bare")"
  git init -q --bare -b master "$bare"
  git remote add origin "$bare"
  if $build; then
    git push -q -u origin master   # the pre-push hook runs scripts/ci.sh: the first push is the first CI
    echo "== pushed to $bare"
  else
    echo "== $bare created; push once built: scripts/build.sh && git push -u origin master"
  fi
fi
echo "== $name ready in $dir: scripts/run.sh starts it"
