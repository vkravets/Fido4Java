#!/usr/bin/env sh
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$APP_HOME"
if [ -x "$APP_HOME/jre/bin/java" ]; then
    JAVA="$APP_HOME/jre/bin/java"
elif [ -n "${JAVA_HOME:-}" ]; then
    JAVA="$JAVA_HOME/bin/java"
else
    JAVA=java
fi
exec "$JAVA" -Dfile.encoding=UTF-8 -cp "$APP_HOME/scripts:$APP_HOME/lib/*:$APP_HOME/lib/groovy/*:$APP_HOME/plugins/*" org.fidonet.jftn.JFtnRunner "$@"
