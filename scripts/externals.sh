#!/usr/bin/env bash
# Managed by JToolsFramework's devkit (scripts/jtools sync refreshes it): change it in its devkit/, not here.
#
# Sourced by the scripts, from the project root. Reads jtools.conf — what the scripts know of this project — and
# makes sure scripts/setup.sh has been run: every tool externals.conf names is deployed in external/ (offered at a
# terminal when something is missing; elsewhere, the CI or a hook, it stops with the way out). Then puts the
# externals in front: external/jdk as JAVA_HOME, its java, Maven's mvn, and Gradle's and Node's when deployed.
#
# scripts/externals.local.sh, when the project has one, is sourced first: it may define missing_local_externals
# (prints what of its own extras is missing) and export what its tools need.
#
# Sets, for the scripts: APP_ID, APP_NAME, APP_MODULE, BUILD_GOALS (jtools.conf), $jar (the program's jar, when
# APP_ID names one), require_program and running_instance.
#
ext="$PWD/external"

# jtools.conf: KEY=VALUE lines, # comments; a value is the rest of its line, spaces included
APP_ID= APP_NAME= APP_MODULE=app BUILD_GOALS="clean package"
[ -f jtools.conf ] || { echo "no jtools.conf in $PWD: not a project of JToolsFramework's" >&2; exit 1; }
while IFS='=' read -r key value; do
  case "$key" in ''|\#*) continue ;; esac
  printf -v "$key" '%s' "$value"
done < jtools.conf

# Whether externals.conf names a tool: what it does not name, this project does without.
wants() { grep -q "^$1=" externals.conf; }

[ -f scripts/externals.local.sh ] && . scripts/externals.local.sh

# What setup deploys and is not there; empty when all is.
missing_externals() {
  local m=
  wants JDK_LINUX && { [ -x "$ext/jdk/bin/java" ] || m="$m jdk/"; }
  wants MAVEN_LINUX && { [ -x "$ext/maven/bin/mvn" ] || m="$m maven/"; }
  wants GRADLE && { [ -x "$ext/gradle/bin/gradle" ] || m="$m gradle/"; }
  wants NODE_LINUX && { [ -x "$ext/node/bin/node" ] || m="$m node/"; }
  wants JDK_LINUX && { [ -s "$ext/jdk-linux.tar.gz" ] || m="$m jdk-linux.tar.gz"; }
  wants JDK_WINDOWS && { [ -s "$ext/jdk-windows.zip" ] || m="$m jdk-windows.zip"; }
  wants JDK_MACOS && { [ -s "$ext/jdk-macos.tar.gz" ] || m="$m jdk-macos.tar.gz"; }
  if declare -F missing_local_externals >/dev/null; then m="$m$(missing_local_externals)"; fi
  echo "$m"
}

missing=$(missing_externals)
if [ -n "$missing" ]; then
  echo "external/ is not set up: missing$missing (scripts/setup.sh deploys them, see README: Externals)" >&2
  if [ -t 0 ] && [ -t 2 ]; then
    read -r -p "Run scripts/setup.sh now? [Y/n] " answer
    case "$answer" in ''|[Yy]*) scripts/setup.sh ;; *) exit 1 ;; esac
    missing=$(missing_externals)
    [ -z "$missing" ] || { echo "still missing$missing" >&2; exit 1; }
  else
    exit 1
  fi
fi

export JAVA_HOME="$ext/jdk"
export PATH="$ext/jdk/bin:$ext/maven/bin:$PATH"
[ -d "$ext/gradle/bin" ] && export PATH="$ext/gradle/bin:$PATH"
[ -d "$ext/node/bin" ] && export PATH="$ext/node/bin:$PATH"

jar=
[ -n "$APP_ID" ] && jar="$APP_MODULE/target/$APP_ID.jar"

# Stops a script that runs or packages the program when the project has none (a library: APP_ID empty).
require_program() {
  [ -n "$APP_ID" ] || { echo "jtools.conf names no program (APP_ID): there is nothing to ${1:-run} in this project" >&2; exit 1; }
}

# Refuses to go on while the program runs from the jar a build is about to replace: Maven would otherwise fail
# deleting it, with an error saying nothing of the cause. $1 is what the caller is about to do.
running_instance() {
  [ -n "$APP_ID" ] || return 0
  local pids
  # anchored on the process's own java: a shell command line that merely names the jar is not an instance
  pids=$(pgrep -f "^[^[:space:]]*java[^[:space:]]* .*-jar[[:space:]].*$APP_ID\\.jar" || true)
  [ -n "$pids" ] || return 0
  echo "$APP_NAME is running (pid$( [ "$(echo "$pids" | wc -l)" -gt 1 ] && echo s) $(echo "$pids" | tr '\n' ' ')): close its pages or run scripts/stop.sh before ${1:-building}, or it holds the jar the build replaces." >&2
  exit 1
}
