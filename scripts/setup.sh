#!/usr/bin/env bash
# Managed by JToolsFramework's devkit (scripts/jtools sync refreshes it): change it in its devkit/, not here.
#
# Deploys the project's externals into external/ (not under source control), from externals.conf — each tool only
# when externals.conf names it:
#   external/jdk, maven, gradle, node                    JDK_*, MAVEN_*, GRADLE, NODE_*: extracted, their top directory stripped
#   external/jdk-linux.tar.gz, jdk-windows.zip, jdk-macos.tar.gz  JDK_*: the JDKs the distributions' runtimes are linked
#                                                                 from, kept as archives (hard links to the downloads)
#   external/downloads/                                  the archives as downloaded (kept: a second run downloads nothing)
# then the project's own extras: scripts/setup.local.sh, when it has one, is sourced with fetch and deploy at hand,
# $force set, and the options this script does not know in $local_args.
#
#   scripts/setup.sh            deploys what is missing
#   scripts/setup.sh --force    downloads and deploys everything again
#
# MIRROR=<url> in externals.conf, or in the environment, fetches <MIRROR>/<file name> instead of the URLs;
# MIRROR_TOKEN (+ MIRROR_TOKEN_HEADER) or MIRROR_USER / MIRROR_PASSWORD authenticate there — from
# externals.local.conf (not under source control) or the environment rather than externals.conf.
# JTOOLS_DOWNLOADS=<dir> in the environment keeps the downloads there instead, shared by every project.
# Needs curl, tar and unzip.
#
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")/.."   # the project root

conf=externals.conf
local_conf=externals.local.conf
ext=external
dl=${JTOOLS_DOWNLOADS:-$ext/downloads}
force=false
local_args=()
for arg in "$@"; do
  case "$arg" in
    --force) force=true ;;
    *) local_args+=("$arg") ;;
  esac
done
if [ ${#local_args[@]} -gt 0 ] && [ ! -f scripts/setup.local.sh ]; then
  echo "unknown option: ${local_args[0]} (try --force)" >&2; exit 1
fi

for tool in curl tar unzip; do
  command -v "$tool" >/dev/null || { echo "$tool not found: install it, then run setup again" >&2; exit 1; }
done

# externals.local.conf then externals.conf: KEY=VALUE lines; what is set already (the environment, then the
# local file) wins
read_conf() {
  while IFS='=' read -r key value; do
    case "$key" in ''|\#*) continue ;; esac
    if [ -z "${!key:-}" ]; then printf -v "$key" '%s' "$value"; fi
  done < "$1"
}
[ -f "$local_conf" ] && read_conf "$local_conf"
read_conf "$conf"

# The authentication of the mirror, as curl arguments (none for the public URLs).
auth=()
if [ -n "${MIRROR:-}" ]; then
  if [ -n "${MIRROR_TOKEN:-}" ]; then auth=(-H "${MIRROR_TOKEN_HEADER:-Authorization: Bearer} $MIRROR_TOKEN"); fi
  if [ -n "${MIRROR_USER:-}" ]; then auth+=(-u "$MIRROR_USER:${MIRROR_PASSWORD:-}"); fi
fi

# Downloads the archive of a URL into the downloads folder unless it is there; prints its path.
fetch() {
  local url=$1 name=${1##*/}
  local src=$url
  if [ -n "${MIRROR:-}" ]; then src="${MIRROR%/}/$name"; fi
  if [ ! -s "$dl/$name" ] || $force; then
    echo "== downloading $name from ${src%/*}/" >&2
    # explicit failures: set -e is off inside a function called under || (the callers' "|| exit 1")
    curl -fL --progress-bar ${auth[@]+"${auth[@]}"} -o "$dl/$name.part" "$src" || { echo "download failed: $src" >&2; rm -f "${dl:?}/${name:?}.part"; return 1; }
    mv "$dl/$name.part" "$dl/$name" || return 1
  fi
  [ -s "$dl/$name" ] || { echo "no $dl/$name" >&2; return 1; }
  echo "$dl/$name"
}

# Extracts an archive into a directory, the archive's single top-level directory stripped.
deploy() {
  local file=$1 dir=$2
  rm -rf "${dir:?}"
  mkdir -p "$dir"
  case "$file" in
    *.tar.gz|*.tgz) tar -xzf "$file" -C "$dir" --strip-components 1 ;;
    *.zip)
      local tmp
      tmp=$(mktemp -d "$ext/unzip.XXXXXX")
      unzip -q "$file" -d "$tmp"
      local top
      top=$(find "$tmp" -mindepth 1 -maxdepth 1)
      if [ "$(printf '%s\n' "$top" | wc -l)" = 1 ] && [ -d "$top" ]; then (shopt -s dotglob; mv "$top"/* "$dir"/); else (shopt -s dotglob; mv "$tmp"/* "$dir"/); fi
      rm -rf "${tmp:?}" ;;
    *) echo "$file: not a .tar.gz or .zip" >&2; return 1 ;;
  esac
}

mkdir -p "$ext" "$dl"

# the tools, under their generic names: present when their launcher is (a failed deployment leaves none)
for spec in "jdk JDK_LINUX bin/java" "maven MAVEN_LINUX bin/mvn" "gradle GRADLE bin/gradle" "node NODE_LINUX bin/node"; do
  set -- $spec
  [ -n "${!2:-}" ] || continue   # a tool this project does without
  dir=$ext/$1
  if [ -x "$dir/$3" ] && ! $force; then echo "== $dir present"; continue; fi
  file=$(fetch "${!2}") || exit 1   # downloaded before the directory is touched
  deploy "$file" "$dir"
  echo "== $dir deployed"
done

# the JDKs the distributions' runtimes are linked from, kept as archives under generic names: a hard link to the
# download when both are on one file system (they weigh 200 MB each), a copy otherwise
for spec in "jdk-linux.tar.gz JDK_LINUX" "jdk-windows.zip JDK_WINDOWS" "jdk-macos.tar.gz JDK_MACOS"; do
  set -- $spec
  [ -n "${!2:-}" ] || continue
  target=$ext/$1
  if [ -s "$target" ] && ! $force; then echo "== $target present"; continue; fi
  file=$(fetch "${!2}") || exit 1
  rm -f "$target"
  ln "$file" "$target" 2>/dev/null || cp "$file" "$target"
  echo "== $target deployed"
done

# the project's own extras
if [ -f scripts/setup.local.sh ]; then . scripts/setup.local.sh; fi

echo "== external/ ready:"
"$ext/jdk/bin/java" -version 2>&1 | head -1
JAVA_HOME="$PWD/$ext/jdk" "$ext/maven/bin/mvn" -v 2>/dev/null | head -1
if [ -x "$ext/gradle/bin/gradle" ]; then echo "Gradle: $ext/gradle"; fi
if [ -x "$ext/node/bin/node" ]; then echo "Node.js $("$ext/node/bin/node" --version), npm $(PATH="$PWD/$ext/node/bin:$PATH" "$ext/node/bin/npm" --version)"; fi
