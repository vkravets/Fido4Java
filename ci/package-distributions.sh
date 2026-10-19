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

bash ci/smoke-distribution.sh "jftn-bundle/build/distributions/jftn-$version-full-with-jre.zip"
output="$PWD/build/release"
mkdir -p "$output"
for profile in full headless legacy transit; do
  for runtime in no-jre with-jre; do
    cp "jftn-bundle/build/distributions/jftn-$version-$profile-$runtime.zip" "$output/"
  done
done
cp gradle/runtime-lock.json "$output/"
(cd "$output" && sha256sum ./*.zip runtime-lock.json > SHA256SUMS)
cp "$snapshot/revision.txt" "$output/revision.txt"
