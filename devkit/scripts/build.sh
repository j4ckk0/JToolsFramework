#!/usr/bin/env bash
# Managed by JToolsFramework (scripts/jtools-sync.sh refreshes it): change it there, not here.
#
# The build, every test included: mvn with jtools.conf's BUILD_GOALS ("clean package" unless it says otherwise).
#
#   scripts/build.sh
#   scripts/build.sh -DskipTests        # extra arguments are passed to mvn
#
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")/.."   # the project root
. scripts/externals.sh   # jtools.conf; setup done? (offers it otherwise); the externals first
running_instance "building"
# shellcheck disable=SC2086 # BUILD_GOALS is a list of goals
exec mvn $BUILD_GOALS "$@"
