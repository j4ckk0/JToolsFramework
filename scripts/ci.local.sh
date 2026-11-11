#!/usr/bin/env bash
#
# The framework's own checks, run by scripts/ci.sh once the framework is built, tested and installed in ~/.m2:
#   - every pom, and scripts/jtools, say the same version (scripts/check-versions.sh);
#   - the framework's own copies of the managed files are the devkit's (scripts/jtools sync --check, JTOOLS_HOME=.);
#   - a developer's way in works: scripts/jtools alone, outside any project, makes a project from the devkit just
#     installed in ~/.m2 (unpacked into tmp/ci/cache, not the user's ~/.cache); that project is in line with its devkit
#     (its own scripts/jtools sync --check), and passes its own CI — its Java and page tests, npm ci —, its jtools.conf
#     and externals.conf written with Windows' line endings, as a project made on Windows has them.
# The project borrows the framework's external/ (a link): nothing is downloaded.
#
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")/.."   # the framework's root

scripts/check-versions.sh
scripts/jtools sync --check >/dev/null || { scripts/jtools sync --check; echo "scripts/, .githooks/ or .gitattributes differ from devkit/: scripts/jtools sync, then commit" >&2; exit 1; }
echo "== the framework's managed files are the devkit's"

version=$(sed -n 's:.*<jtools.version>\(.*\)</jtools.version>.*:\1:p' conventions/parent/pom.xml)
rm -rf tmp/ci
mkdir -p tmp/ci
cp devkit/scripts/jtools tmp/ci/jtools
export JTOOLS_CACHE="$PWD/tmp/ci/cache"
(cd tmp/ci && env -u JTOOLS_HOME JTOOLS_VERSION="$version" ./jtools new CiTrial --port 18099 --no-git --no-build >/dev/null)
trial=tmp/ci/CiTrial
ln -s "$PWD/external" "$trial/external"
# its settings as a project made on Windows has them, CRLF: the shell scripts read them alike
sed -i 's/$/\r/' "$trial/jtools.conf" "$trial/externals.conf"
(cd "$trial" && env -u JTOOLS_HOME scripts/jtools sync --check >/dev/null && scripts/ci.sh)
echo "== scripts/jtools new makes, from the installed devkit, a project in line with it that passes its CI"
rm -rf tmp/ci
