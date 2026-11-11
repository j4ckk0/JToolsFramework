#!/usr/bin/env bash
# Managed by JToolsFramework's devkit (scripts/jtools sync refreshes it): change it in its devkit/, not here.
#
# The project's CI, run locally: the build with every test, quiet, the pages' dependencies installed exactly as
# their package-lock.json has them (npm ci); then scripts/ci.local.sh, when the project has checks of its own.
#
#   scripts/ci.sh
#
# Run by .githooks/pre-push before every push; enable the hooks once with:
#   git config core.hooksPath .githooks
#
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")/.."   # the project root
. scripts/externals.sh   # jtools.conf; setup done? (offers it otherwise); the externals first
running_instance "the build"
# shellcheck disable=SC2086 # BUILD_GOALS is a list of goals
mvn -q $BUILD_GOALS -Dnpm.install=ci
if [ -f scripts/ci.local.sh ]; then bash scripts/ci.local.sh; fi
