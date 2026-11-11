#!/usr/bin/env bash
#
# Brings a project in line with the devkit this script belongs to: refreshes the files the devkit owns in it
# (managed-files.list: the scripts, the pre-push hook, .gitattributes) and says what else differs — the framework version
# its pom names, the externals' versions, its scripts/linux/jtools — without changing those, which are the project's to decide.
# Commits nothing: read the diff in the project, run its CI, commit there. Run by the project's scripts/linux/jtools:
#
#   scripts/linux/jtools sync            refresh the managed files, report the rest
#   scripts/linux/jtools sync --check    report only; exit 1 when anything differs (a project's CI)
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

# 1.3's layout, scripts/ alone, moved to 1.4's (moved-files.list)
if [ -f "$devkit/moved-files.list" ]; then
  while read -r old new; do
    [ -e "$project/$old" ] || continue
    differs=1
    if $check; then echo "   to move: $old -> $new"; continue; fi
    mkdir -p "$project/$(dirname "$new")"
    case "$new" in
      */jtools|*/jtools.bat)
        [ -e "$project/$new" ] || cp -p "$devkit/$new" "$project/$new"
        echo "   $new in place; remove $old by hand (it may be the one running this sync)" ;;
      *.local.*)
        if [ -e "$project/$new" ]; then echo "   $old and $new are both there: keep one"
        else mv "$project/$old" "$project/$new"; echo "   moved: $old -> $new"; fi ;;
      *)
        rm -f "$project/$old"   # the devkit's own copy: its new one came with the managed files
        echo "   moved: $old -> $new" ;;
    esac
  done < <(grep -v -E '^(#|$)' "$devkit/moved-files.list")
fi

echo "== the framework version"
used=$(grep -A1 '<artifactId>jtools-parent</artifactId>' "$project/pom.xml" | sed -n 's:.*<version>\(.*\)</version>.*:\1:p')
if [ "$used" = "$version" ]; then echo "   $version, the devkit's"
else echo "   the project's pom names jtools-parent ${used:-(none)}, the devkit is $version: change it in $project/pom.xml"; differs=1; fi

echo "== the externals (externals.conf: the project's to change)"
while IFS='=' read -r key value; do
  key=${key%$'\r'} value=${value%$'\r'}   # a file written on Windows (CRLF) reads the same
  case "$key" in ''|\#*|MIRROR*) continue ;; esac
  theirs=$(sed -n "s|^$key=||p" "$project/externals.conf" | tr -d '\r')
  [ "$theirs" = "$value" ] || { echo "   $key: ${theirs:-(absent)} -> $value"; differs=1; }
done < "$devkit/externals.conf"

# The stubs (new-project.list): scripts/linux/jtools runs this very sync, so they are never replaced by it, only said
# to differ — copied by hand when wanted
for stub in $(tr -d '\r' < "$devkit/new-project.list" | sed -n 's/^stub //p'); do
  [ -f "$devkit/$stub" ] && [ -f "$project/$stub" ] || continue
  if ! cmp -s "$devkit/$stub" "$project/$stub"; then
    echo "== $stub differs from the devkit's: cp $devkit/$stub $project/$stub to take it"
  fi
done

if [ $differs = 0 ]; then echo "== $project is in line with JToolsFramework $version"
elif $check; then exit 1
else echo "== read the diff in $project, run its scripts/linux/ci.sh, commit there"; fi
