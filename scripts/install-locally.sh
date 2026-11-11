#!/usr/bin/env bash
#
# Builds the framework, every test included, and installs it into the local Maven repository (~/.m2), where the
# projects find jtools-parent, the libraries, jtools-dist and jtools-web. Run it after every change to the framework,
# before building a project against it.
#
#   scripts/install-locally.sh
#   scripts/install-locally.sh -DskipTests     # extra arguments are passed to mvn
#
# To share the framework with other machines, deploy it to the Artifactory the externals' MIRROR names
# (doc/CONVENTIONS.md, "Sharing the framework").
#
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")/.."   # the framework's root
scripts/check-versions.sh
exec scripts/linux/build.sh "$@"   # jtools.conf: BUILD_GOALS=clean install
