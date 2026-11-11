#!/usr/bin/env bash
#
# Builds the delivery, what a developer downloads to start without a clone: releases/delivery/jtools-<version>.tar.gz,
# and .zip, the same, for Windows. It holds the framework's nine artifacts as a Maven repository (jtools-bom's seven,
# jtools-parent and jtools-bom), the stubs (scripts/linux/jtools, scripts/windows/jtools.bat) and the installers
# (delivery/install.sh and install.bat), which install them and make a first project. Every test runs first.
#
#   scripts/delivery.sh                 the delivery of the tagged version (v<version> must hold what is built)
#   scripts/delivery.sh --untagged      a try, before the tag
#
# Attached to the version's GitHub release (PUBLISHING.md).
#
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")/.."   # the framework's root
tagged=true
[ "${1:-}" = "--untagged" ] && { tagged=false; shift; }
scripts/check-versions.sh
version=$(sed -n 's:.*<jtools.version>\(.*\)</jtools.version>.*:\1:p' conventions/parent/pom.xml)
# what the delivery carries is what its tag says: the parts it is built from, committed or not, are the tag's
shipped="libraries conventions devkit starter"
if $tagged; then
  git rev-parse -q --verify "refs/tags/v$version" >/dev/null || { echo "no tag v$version: tag it first, or --untagged for a try" >&2; exit 1; }
  # shellcheck disable=SC2086 # a list of folders
  git diff --quiet "v$version" -- $shipped || { echo "$shipped differ from v$version: the delivery of $version would not be its tag" >&2; exit 1; }
fi

. scripts/linux/externals.sh
deployed="$PWD/tmp/delivery-repository"
rm -rf "$deployed"
mvn -q -B clean deploy "-DaltDeploymentRepository=delivery::file://$deployed" "$@"

out=releases/delivery
stage=$out/jtools-$version
rm -rf "$out"
artifacts="jtools-parent jtools-bom $(sed -n '/<dependencyManagement>/,/<\/dependencyManagement>/s:.*<artifactId>\(.*\)</artifactId>.*:\1:p' conventions/bom/pom.xml)"
for artifact in $artifacts; do
  from=$deployed/org/jtools/framework/$artifact/$version
  [ -d "$from" ] || { echo "$artifact $version was not deployed" >&2; exit 1; }
  to=$stage/repository/org/jtools/framework/$artifact/$version
  mkdir -p "$to"
  # the jar and the pom, with their .sha1; no maven-metadata.xml, no .md5: a local repository has no use for them
  find "$from" -maxdepth 1 -type f ! -name '*.md5' ! -name 'maven-metadata*' -exec cp {} "$to/" \;
done
cp devkit/scripts/linux/jtools delivery/install.sh LICENSE "$stage/"
for bat in delivery/install.bat devkit/scripts/windows/jtools.bat; do sed 's/\r*$/\r/' "$bat" > "$stage/${bat##*/}"; done   # cmd reads its labels only with CRLF
sed "s/__VERSION__/$version/g" delivery/README.md > "$stage/README.md"
echo "$version" > "$stage/VERSION"
chmod +x "$stage/jtools" "$stage/install.sh"
tar czf "$out/jtools-$version.tar.gz" -C "$out" "jtools-$version"
(cd "$out" && "$JAVA_HOME/bin/jar" --create --no-manifest --file "jtools-$version.zip" "jtools-$version")   # a zip Explorer opens
(cd "$out" && for archive in "jtools-$version.tar.gz" "jtools-$version.zip"; do sha256sum "$archive" > "$archive.sha256"; done)
rm -rf "$deployed"
echo "== $out/jtools-$version.tar.gz and .zip ($(du -h "$out/jtools-$version.tar.gz" | cut -f1)), $(echo $artifacts | wc -w) artifacts:"
(cd "$stage" && find repository -name '*.jar' -o -name '*.pom' | sort | sed 's/^/   /')
