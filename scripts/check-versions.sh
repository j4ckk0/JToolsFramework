#!/usr/bin/env bash
#
# Checks that the framework says one version everywhere: each module's <parent> version, jtools-parent's own
# <version>, and its jtools.version property (what the projects' dependencies on the framework use); and that the two
# stubs fetch the devkit from the same place. Run by
# scripts/install-locally.sh and scripts/linux/ci.local.sh; a new version is set in all of them at once:
#   sed -i 's/1.5.0/1.5.0/' pom.xml */*/pom.xml   (then read the diff: only the framework's versions may change)
#
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")/.."   # the framework's root

version=$(sed -n 's:.*<jtools.version>\(.*\)</jtools.version>.*:\1:p' conventions/parent/pom.xml)
[ -n "$version" ] || { echo "conventions/parent/pom.xml: no <jtools.version>" >&2; exit 1; }
status=0
# the version right after each mention of jtools-parent (its own coordinates, and every module's <parent>)
for pom in pom.xml */*/pom.xml; do
  case "$pom" in starter/*) continue ;; esac   # the template's pom names __JTOOLS_VERSION__
  while read -r found; do
    if [ "$found" != "$version" ]; then echo "$pom: jtools-parent $found, but jtools.version is $version" >&2; status=1; fi
  done < <(grep -A1 '<artifactId>jtools-parent</artifactId>' "$pom" | sed -n 's:.*<version>\(.*\)</version>.*:\1:p')
done
# scripts/linux/jtools: the version `jtools new` makes outside a project
for stub in devkit/scripts/linux/jtools scripts/linux/jtools devkit/scripts/windows/jtools.bat scripts/windows/jtools.bat; do
  said=$(sed -n 's/^\(set "\)\{0,1\}JTOOLS_DEFAULT_VERSION=\([0-9][^" \r]*\).*/\2/p' "$stub")
  [ "$said" = "$version" ] || { echo "$stub: JTOOLS_DEFAULT_VERSION=$said, but jtools.version is $version" >&2; status=1; }
done
# and where the stubs fetch the devkit from: one Maven Central URL for Linux and Windows alike
central=
for stub in devkit/scripts/linux/jtools devkit/scripts/windows/jtools.bat; do
  said=$(sed -n 's/^\(set "\)\{0,1\}CENTRAL=\([^" \r]*\).*/\2/p' "$stub")
  [ -n "$said" ] || { echo "$stub: no CENTRAL=" >&2; status=1; continue; }
  [ -z "$central" ] || [ "$said" = "$central" ] || { echo "$stub: CENTRAL=$said, but the other stub says $central" >&2; status=1; }
  central=$said
done
# jtools-bom inherits nothing: its own version, said once in its pom
bom=$(grep -A1 '<artifactId>jtools-bom</artifactId>' conventions/bom/pom.xml | sed -n 's:.*<version>\(.*\)</version>.*:\1:p' | head -1)
[ "$bom" = "$version" ] || { echo "conventions/bom/pom.xml: jtools-bom $bom, but jtools.version is $version" >&2; status=1; }
[ $status = 0 ] && echo "== JToolsFramework $version, said alike in every pom"
exit $status
