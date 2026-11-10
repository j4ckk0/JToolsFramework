#!/usr/bin/env bash
# Managed by JToolsFramework (scripts/jtools-sync.sh refreshes it): change it there, not here.
#
# Build the self-contained distributions (jar + bundled JRE + launchers; <name>.exe without a console), into releases/:
#   releases/<id>-<version>-linux.tar.gz, releases/<id>-<version>-windows.zip
#
#   scripts/package.sh
#   scripts/package.sh -DskipTests      # extra arguments are passed to mvn
#
# The JRE archives come from external/ (scripts/setup.sh deploys them). The version is the root pom's <revision>.
# The releases of the previous versions are removed once the new one is built: releases/ holds the release of the
# version pom.xml names, and whatever else you put there is left alone.
#
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")/.."   # the project root
. scripts/externals.sh   # jtools.conf; setup done? (offers it otherwise); the externals first
require_program "package"
running_instance "packaging"

# shellcheck disable=SC2086 # BUILD_GOALS is a list of goals
mvn $BUILD_GOALS -Pdist "$@"

version=$(sed -n 's:.*<revision>\(.*\)</revision>.*:\1:p' pom.xml | head -1)   # said once, in the root pom
[ -n "$version" ] || { echo "pom.xml has no <revision>: the scripts read the version there" >&2; exit 1; }

# The releases of the previous versions go, now that the new one is there (only ours, named after another version).
for archive in "releases/$APP_ID"-*-windows.zip "releases/$APP_ID"-*-linux.tar.gz; do
  [ -f "$archive" ] || continue
  case "$archive" in *"-$version-"*) continue ;; esac
  rm -f "$archive"
  echo "== $archive removed (previous release)"
done

# What every distribution must carry: the jar — with the page inside it, when the project has one —, its launchers,
# the .ini and the JRE. Every check gives grep the entries whole, never through a pipe it may leave early: under
# pipefail the writer it leaves behind would fail the pipeline, and the check with it.
has_page=false
compgen -G "*/angular.json" >/dev/null && has_page=true
check_jar=$(mktemp)
trap 'rm -f "$check_jar"' EXIT
base="$APP_ID-$version"
for archive in "releases/$base-windows.zip" "releases/$base-linux.tar.gz"; do
  [ -f "$archive" ] || { echo "no $archive: the build did not make it" >&2; exit 1; }
  case "$archive" in
    *.zip) entries=$(unzip -Z1 "$archive"); unzip -p "$archive" "$base/$APP_ID.jar" > "$check_jar"
           needed=("$APP_ID.jar" "$APP_NAME.exe" "$APP_ID.bat" "$APP_ID.ini" README.md jre/bin/java.exe) ;;
    *)     entries=$(tar tzf "$archive"); tar xzOf "$archive" "$base/$APP_ID.jar" > "$check_jar"
           needed=("$APP_ID.jar" "$APP_ID.sh" "$APP_ID.ini" README.md jre/bin/java) ;;
  esac
  for file in "${needed[@]}"; do
    grep -qx "$base/$file" <<< "$entries" || { echo "$archive: no $file" >&2; exit 1; }
  done
  if $has_page; then
    jar_entries=$(unzip -Z1 "$check_jar")
    grep -qx 'static/index.html' <<< "$jar_entries" || { echo "$archive: the jar carries no page (static/index.html)" >&2; exit 1; }
  fi
done
echo "== releases/ checked: $base-windows.zip and $base-linux.tar.gz, each with its JRE and launchers$($has_page && echo ', the page in the jar')"
