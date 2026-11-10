#!/usr/bin/env bash
#
# The framework's own checks, run by scripts/ci.sh once the framework is built, tested and installed in ~/.m2:
#   - every pom says the same version (scripts/check-versions.sh);
#   - the template still makes a project that passes its own CI: a project generated into tmp/ci/, built against the
#     framework just installed, its Java and page tests run, its page's dependencies installed with npm ci.
# The project borrows the framework's external/ (a link): nothing is downloaded.
#
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")/.."   # the framework's root

scripts/check-versions.sh

trial=tmp/ci/CiTrial
rm -rf tmp/ci
mkdir -p tmp/ci
scripts/new-project.sh CiTrial --port 18099 --dir "$PWD/$trial" --no-git --no-build >/dev/null
ln -s "$PWD/external" "$trial/external"
(cd "$trial" && scripts/ci.sh)
echo "== the template makes a project that passes its CI"
rm -rf tmp/ci
