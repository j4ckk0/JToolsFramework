#!/bin/sh
# Run ${jtools.app.name} with the bundled JRE. Arguments are passed to the program (./${jtools.app.id}.sh --help).
dir=$(dirname "$(readlink -f "$0")")
exec "$dir/jre/bin/java" -jar "$dir/${jtools.app.id}.jar" "$@"
