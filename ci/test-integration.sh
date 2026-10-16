#!/usr/bin/env bash
set -euo pipefail

test "$(cat build/ci-integration/revision.txt)" = "$(git rev-parse HEAD)"
(cd build/ci-integration && sha256sum --check SHA256SUMS --quiet)
docker info
case "${1:-}" in
  binkd)
    bash ./gradlew :binkp-root:binkp-netty:binkdDockerTest \
      :binkp-root:binkp-mina2:binkdDockerTest \
      -PciIntegrationSnapshot --continue --warning-mode fail --no-daemon --console=plain
    ;;
  hpt)
    docker build -t jftn-hpt-interop:ci jtoss/src/integrationTest/resources/hpt
    bash ./gradlew :jtoss:hptInteropTest -Dhpt.image=jftn-hpt-interop:ci \
      -PciIntegrationSnapshot --warning-mode fail --no-daemon --console=plain
    ;;
  *) echo 'Expected binkd or hpt integration suite' >&2; exit 1 ;;
esac
