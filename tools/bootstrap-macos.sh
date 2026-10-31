#!/usr/bin/env bash
# Prepare Homebrew prerequisites, then delegate pinned tools to the shared bootstrap.
set -euo pipefail

repository_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
install_prerequisites=false
bootstrap_arguments=()
for argument in "$@"; do
  case "$argument" in
    --install-prerequisites) install_prerequisites=true ;;
    --help|-h)
      echo 'Usage: bash tools/bootstrap-macos.sh [--install-prerequisites] [bootstrap options]'
      echo 'Requires macOS, Xcode Command Line Tools and Homebrew.'
      echo 'Use --install-prerequisites to install missing Homebrew build dependencies.'
      echo 'Other options are forwarded to tools/bootstrap.sh (for example --frontend --browsers).'
      exit 0
      ;;
    *) bootstrap_arguments+=("$argument") ;;
  esac
done

if [[ $(uname -s) != Darwin ]]; then
  echo 'This entry point requires macOS; use tools/bootstrap.ps1 or tools/bootstrap.sh.' >&2
  exit 1
fi
if ! xcode-select -p >/dev/null 2>&1; then
  echo 'Install Xcode Command Line Tools with xcode-select --install, then rerun.' >&2
  exit 1
fi
# Fresh Terminal sessions may not have Homebrew in PATH yet.
if ! command -v brew >/dev/null 2>&1; then
  for brew_directory in /opt/homebrew/bin /usr/local/bin; do
    if [[ -x "$brew_directory/brew" ]]; then
      export PATH="$brew_directory:$PATH"
      break
    fi
  done
fi
if ! command -v brew >/dev/null 2>&1; then
  echo 'Install Homebrew from https://brew.sh, then rerun this command.' >&2
  exit 1
fi

packages=(python@3.13 boost bison flex libtool pkg-config)
missing_packages=()
for package in "${packages[@]}"; do
  if ! brew list --versions "$package" >/dev/null 2>&1; then
    missing_packages+=("$package")
  fi
done
if [[ ${#missing_packages[@]} -gt 0 ]]; then
  if [[ "$install_prerequisites" != true ]]; then
    echo "Missing Homebrew dependencies: ${missing_packages[*]}" >&2
    echo 'Rerun with --install-prerequisites to install them.' >&2
    exit 1
  fi
  brew install "${missing_packages[@]}"
fi

export PATH="$(brew --prefix bison)/bin:$(brew --prefix flex)/bin:$(brew --prefix libtool)/bin:$PATH"
export CPPFLAGS="-I$(brew --prefix boost)/include${CPPFLAGS:+ $CPPFLAGS}"
export LDFLAGS="-L$(brew --prefix boost)/lib${LDFLAGS:+ $LDFLAGS}"
export PKG_CONFIG_PATH="$(brew --prefix boost)/lib/pkgconfig${PKG_CONFIG_PATH:+:$PKG_CONFIG_PATH}"
export JFTN_BOOTSTRAP_PYTHON="${JFTN_BOOTSTRAP_PYTHON:-$(brew --prefix python@3.13)/bin/python3.13}"
if [[ ${#bootstrap_arguments[@]} -gt 0 ]]; then
  exec bash "$repository_root/tools/bootstrap.sh" "${bootstrap_arguments[@]}"
fi
exec bash "$repository_root/tools/bootstrap.sh"
