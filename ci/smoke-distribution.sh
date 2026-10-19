#!/usr/bin/env bash
set -euo pipefail

archive=$(realpath "$1")
temporary=$(mktemp -d)
pid=
cleanup() {
  if [ -n "$pid" ]; then
    kill "$pid" 2>/dev/null || true
    wait "$pid" 2>/dev/null || true
  fi
  # Print diagnostics before discarding this disposable test installation.
  cat "$temporary"/jftn-*/smoke.log 2>/dev/null || true
  rm -rf -- "$temporary"
}
trap cleanup EXIT
unzip -q "$archive" -d "$temporary"
mapfile -t roots < <(find "$temporary" -mindepth 1 -maxdepth 1 -type d)
test "${#roots[@]}" = 1
cd "${roots[0]}"
test -x jftn.sh
test -x jre/linux-x64/bin/java
test -f jre/windows-x64/bin/java.exe
./jftn.sh --version | grep -F 'JFtn '
rmdir mail/inbound mail/outbound mail/tmp mail/echo database logs lists fileecho
JAVA_TOOL_OPTIONS=-Djftn.setup.port=0 ./jftn.sh > smoke.log 2>&1 &
pid=$!
for attempt in {1..60}; do
  if grep -Fq 'Configure JFtn: http://' smoke.log; then
    break
  fi
  kill -0 "$pid"
  sleep 1
done
url=$(sed -n 's/.*Configure JFtn: \(http[^ ]*\).*/\1/p' smoke.log | head -n 1)
test -n "$url"
origin="${url%%/#*}"
curl --fail --silent --show-error "$origin/" | grep -F '<div id="app"></div>'
curl --fail --silent --show-error -H 'Content-Type: application/vnd.apache.thrift.json' \
  --data '[1,"status",1,1,{}]' "$origin/rpc/settings" | grep -F '"tf":1'
for directory in mail/inbound mail/outbound mail/tmp mail/echo database logs lists fileecho; do
  test -d "$directory"
done
echo 'Bundled JRE, launcher, first-run directories and embedded UI verified'
