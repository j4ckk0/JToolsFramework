#!/usr/bin/env bash
#
# Brings a project in line with the devkit this script belongs to: refreshes the files the devkit owns in it
# (managed-files.list: the scripts, the pre-push hook, .gitattributes) and says what else differs — the framework version
# its pom names, the externals' versions, its scripts/jtools — without changing those, which are the project's to decide.
# Commits nothing: read the diff in the project, run its CI, commit there. Run by the project's scripts/jtools:
#
#   scripts/jtools sync            refresh the managed files, report the rest
#   scripts/jtools sync --check    report only; exit 1 when anything differs (a project's CI)
#
# The devkit is the unpacked jtools-devkit artifact (a file "version" beside this script), or devkit/ in a clone of
# JToolsFramework (its version in conventions/parent/pom.xml).
#
set -euo pipefail
devkit="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"
project=${1:?"the folder of the project: jtools-sync.sh <project> [--check]"}
check=false
[ "${2:-}" = "--check" ] && check=true
project="$(cd "$project" && pwd)"
[ -f "$project/jtools.conf" ] || { echo "$project has no jtools.conf: not a project of JToolsFramework's (doc/ADOPTING.md)" >&2; exit 1; }
if [ -f "$devkit/version" ]; then version=$(cat "$devkit/version")
else version=$(sed -n 's:.*<jtools.version>\(.*\)</jtools.version>.*:\1:p' "$devkit/../conventions/parent/pom.xml"); fi

differs=0
echo "== the managed files (jtools-devkit $version)"
while read -r file; do
  if cmp -s "$devkit/$file" "$project/$file"; then continue; fi
  differs=1
  if $check; then
    echo "   differs: $file"
  else
    mkdir -p "$project/$(dirname "$file")"
    cp -p "$devkit/$file" "$project/$file"
    echo "   refreshed: $file"
  fi
done < <(grep -v -E '^(#|$)' "$devkit/managed-files.list")

echo "== the framework version"
used=$(grep -A1 '<artifactId>jtools-parent</artifactId>' "$project/pom.xml" | sed -n 's:.*<version>\(.*\)</version>.*:\1:p')
if [ "$used" = "$version" ]; then echo "   $version, the devkit's"
else echo "   the project's pom names jtools-parent ${used:-(none)}, the devkit is $version: change it in $project/pom.xml"; differs=1; fi

echo "== the externals (externals.conf: the project's to change)"
while IFS='=' read -r key value; do
  case "$key" in ''|\#*|MIRROR*) continue ;; esac
  theirs=$(sed -n "s|^$key=||p" "$project/externals.conf")
  [ "$theirs" = "$value" ] || { echo "   $key: ${theirs:-(absent)} -> $value"; differs=1; }
done < "$devkit/externals.conf"

# scripts/jtools runs this very sync: it is never replaced by it, only said to differ — copied by hand when wanted;
# likewise its Windows twin, jtools.bat
for stub in jtools jtools.bat; do
  [ -f "$devkit/scripts/$stub" ] || continue
  if ! cmp -s "$devkit/scripts/$stub" "$project/scripts/$stub"; then
    echo "== scripts/$stub differs from the devkit's: cp $devkit/scripts/$stub $project/scripts/$stub to take it"
  fi
done

if [ $differs = 0 ]; then echo "== $project is in line with JToolsFramework $version"
elif $check; then exit 1
else echo "== read the diff in $project, run its scripts/ci.sh, commit there"; fi
