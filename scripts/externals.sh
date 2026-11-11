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

# The URL an external was deployed from (external/.<name>.url, which setup writes): one externals.conf no longer names
# — a new version — is as good as missing.
url_of() { sed -n "s|^$1=||p" externals.conf | head -1; }
current() { [ "$(cat "$ext/.$1.url" 2>/dev/null)" = "$(url_of "$2")" ]; }

# What setup deploys and is not there, or is of another version than externals.conf's; empty when all is.
missing_externals() {
  local m=
  wants JDK_LINUX && { [ -x "$ext/jdk/bin/java" ] && current jdk JDK_LINUX || m="$m jdk/"; }
  wants MAVEN_LINUX && { [ -x "$ext/maven/bin/mvn" ] && current maven MAVEN_LINUX || m="$m maven/"; }
  wants GRADLE && { [ -x "$ext/gradle/bin/gradle" ] && current gradle GRADLE || m="$m gradle/"; }
  wants NODE_LINUX && { [ -x "$ext/node/bin/node" ] && current node NODE_LINUX || m="$m node/"; }
  wants JDK_LINUX && { [ -s "$ext/jdk-linux.tar.gz" ] && current jdk-linux.tar.gz JDK_LINUX || m="$m jdk-linux.tar.gz"; }
  wants JMODS_LINUX && { [ -s "$ext/jmods-linux.tar.gz" ] && current jmods-linux.tar.gz JMODS_LINUX || m="$m jmods-linux.tar.gz"; }
  wants JMODS_WINDOWS && { [ -s "$ext/jmods-windows.zip" ] && current jmods-windows.zip JMODS_WINDOWS || m="$m jmods-windows.zip"; }
  wants JMODS_MACOS && { [ -s "$ext/jmods-macos.tar.gz" ] && current jmods-macos.tar.gz JMODS_MACOS || m="$m jmods-macos.tar.gz"; }
  # a JDK up to 23 carries its jmods: its archive, for a platform with no JMODS_*
  wants JDK_WINDOWS && ! wants JMODS_WINDOWS && { [ -s "$ext/jdk-windows.zip" ] && current jdk-windows.zip JDK_WINDOWS || m="$m jdk-windows.zip"; }
  wants JDK_MACOS && ! wants JMODS_MACOS && { [ -s "$ext/jdk-macos.tar.gz" ] && current jdk-macos.tar.gz JDK_MACOS || m="$m jdk-macos.tar.gz"; }
  if declare -F missing_local_externals >/dev/null; then m="$m$(missing_local_externals)"; fi
  echo "$m"
}

missing=$(missing_externals)
if [ -n "$missing" ]; then
  echo "external/ is not set up: missing, or of another version than externals.conf's:$missing (scripts/setup.sh deploys them)" >&2
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
