#!/usr/bin/env bash
set -euo pipefail
repository_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
bootstrap_python=${JFTN_BOOTSTRAP_PYTHON:-python3}
if ! command -v "$bootstrap_python" >/dev/null 2>&1; then
  echo 'Install Python 3.12+ first, or set JFTN_BOOTSTRAP_PYTHON to its executable.' >&2
  exit 1
fi
exec "$bootstrap_python" "$repository_root/tools/bootstrap.py" "$@"
