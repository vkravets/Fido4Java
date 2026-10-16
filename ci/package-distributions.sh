#!/usr/bin/env bash
set -euo pipefail

snapshot="$PWD/build/ci-release"
(cd "$snapshot" && sha256sum --check SHA256SUMS)
test "$(cat "$snapshot/revision.txt")" = "$(git rev-parse HEAD)"
version=$(cat "$snapshot/version.txt")
bash ./gradlew :jftn-bundle:releaseDistributions \
  "-PreleaseVersion=$version" \
  "-PdistributionInput=$snapshot/jftn-$version-no-jre.zip" \
  --warning-mode fail --no-daemon --console=plain

bash ci/smoke-distribution.sh "jftn-bundle/build/distributions/jftn-$version-linux-x64.zip"
output="$PWD/build/release"
mkdir -p "$output"
for suffix in no-jre linux-x64 windows-x64; do
  cp "jftn-bundle/build/distributions/jftn-$version-$suffix.zip" "$output/"
done
cp gradle/runtime-lock.json "$output/"
(cd "$output" && sha256sum ./*.zip runtime-lock.json > SHA256SUMS)
cp "$snapshot/revision.txt" "$output/revision.txt"
