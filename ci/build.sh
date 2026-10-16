#!/usr/bin/env bash
set -euo pipefail

version=1.0-SNAPSHOT
if [ "${GITHUB_REF_TYPE:-}" = tag ]; then
  if [[ ! "${GITHUB_REF_NAME:-}" =~ ^v[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9.-]+)?$ ]]; then
    echo "Expected a vMAJOR.MINOR.PATCH tag with optional prerelease suffix" >&2
    exit 1
  fi
  version="${GITHUB_REF_NAME#v}"
fi
bash ./gradlew build :web-ui:buildStorybook :binkp-root:binkp-netty:prepareCiIntegration \
  :binkp-root:binkp-mina2:prepareCiIntegration :jtoss:prepareCiIntegration \
  "-PreleaseVersion=$version" --warning-mode fail --no-daemon --console=plain

git rev-parse HEAD > build/ci-integration/revision.txt
(
  cd build/ci-integration
  find . -type f ! -name SHA256SUMS -print0 | sort -z | xargs -0 sha256sum > SHA256SUMS
)
mkdir -p build/ci-release
cp "jftn-bundle/build/distributions/jftn-$version-no-jre.zip" build/ci-release/
printf '%s\n' "$version" > build/ci-release/version.txt
git rev-parse HEAD > build/ci-release/revision.txt
(
  cd build/ci-release
  sha256sum ./*.zip version.txt revision.txt > SHA256SUMS
)
# A tar preserves permissions and hidden resources across artifact upload/download.
tar -cf build/compiled-release.tar -C build ci-integration ci-release
