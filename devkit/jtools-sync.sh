#!/usr/bin/env bash
#
# Brings a project in line with the framework: refreshes the files the framework owns in it (scripts/managed-files.list:
# the scripts, the pre-push hook, .gitattributes) and says what else differs — the framework version its pom names,
# the externals' versions — without changing those, which are the project's to decide. Commits nothing: read the
# diff in the project, run its CI, commit there.
#
#   scripts/jtools-sync.sh ../../git/SensorSimulator           # refresh the managed files, report the rest
#   scripts/jtools-sync.sh ../../git/SensorSimulator --check   # report only; exit 1 when anything differs (a project's CI)
#
set -euo pipefail
framework="$(cd "$(dirname "$(readlink -f "$0")")/.." && pwd)"
project=${1:?"the folder of the project: scripts/jtools-sync.sh ../../git/SomeProject [--check]"}
check=false
[ "${2:-}" = "--check" ] && check=true
project="$(cd "$project" && pwd)"
[ -f "$project/jtools.conf" ] || { echo "$project has no jtools.conf: not a project of the framework's (doc/ADOPTING.md)" >&2; exit 1; }
cd "$framework"

differs=0
echo "== the managed files"
while read -r file; do
  if cmp -s "devkit/$file" "$project/$file"; then continue; fi
  differs=1
  if $check; then
    echo "   differs: $file"
  else
    mkdir -p "$project/$(dirname "$file")"
    cp -p "devkit/$file" "$project/$file"
    echo "   refreshed: $file"
  fi
done < <(grep -v -E '^(#|$)' devkit/managed-files.list)

echo "== the framework version"
version=$(sed -n 's:.*<jtools.version>\(.*\)</jtools.version>.*:\1:p' conventions/parent/pom.xml)
used=$(grep -A1 '<artifactId>jtools-parent</artifactId>' "$project/pom.xml" | sed -n 's:.*<version>\(.*\)</version>.*:\1:p')
if [ "$used" = "$version" ]; then echo "   $version, the framework's"
else echo "   the project's pom names jtools-parent ${used:-(none)}, the framework is $version: change it in $project/pom.xml"; differs=1; fi

echo "== the externals (externals.conf: the project's to change)"
while IFS='=' read -r key value; do
  case "$key" in ''|\#*|MIRROR*) continue ;; esac
  theirs=$(sed -n "s|^$key=||p" "$project/externals.conf")
  [ "$theirs" = "$value" ] || { echo "   $key: ${theirs:-(absent)} -> $value"; differs=1; }
done < devkit/externals.conf

if [ $differs = 0 ]; then echo "== $project is in line with JToolsFramework $version"
elif $check; then exit 1
else echo "== read the diff in $project, run its scripts/ci.sh, commit there"; fi
