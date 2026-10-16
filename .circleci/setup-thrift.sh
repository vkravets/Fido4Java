#!/usr/bin/env bash
set -euo pipefail

thrift_version=0.25.0
thrift_cache="$HOME/.cache/thrift/$thrift_version"
thrift_cached_bin="$thrift_cache/bin/thrift"
thrift_cached_runtime="$thrift_cache/browser/thrift.js"
thrift_build_dir=

compiler_is_cached() {
  [ -x "$thrift_cached_bin" ] &&
    [ "$("$thrift_cached_bin" -version)" = "Thrift version $thrift_version" ]
}

runtime_is_cached() {
  [ -s "$thrift_cached_runtime" ] &&
    grep -Fq "Version: '$thrift_version'" "$thrift_cached_runtime"
}

prepare_sources() {
  thrift_build_dir=$(mktemp -d)
  trap 'rm -rf -- "$thrift_build_dir"' EXIT
  curl -fsSLo "$thrift_build_dir/thrift.tar.gz" \
    "https://archive.apache.org/dist/thrift/$thrift_version/thrift-$thrift_version.tar.gz"
  tar -xzf "$thrift_build_dir/thrift.tar.gz" -C "$thrift_build_dir"
}

install_compiler() {
  if compiler_is_cached; then
    echo "Using cached Thrift compiler"
    return
  fi
  sudo apt-get update
  sudo apt-get install -y build-essential bison flex libtool pkg-config libboost-dev
  (
    cd "$thrift_build_dir/thrift-$thrift_version"
    ./configure --disable-libs --disable-tests --disable-tutorial
    make -C compiler/cpp -j2
    test "$(compiler/cpp/thrift -version)" = "Thrift version $thrift_version"
    mkdir -p "$(dirname "$thrift_cached_bin")"
    install compiler/cpp/thrift "$thrift_cached_bin"
  )
}

install_browser_runtime() {
  if runtime_is_cached; then
    return
  fi
  mkdir -p "$(dirname "$thrift_cached_runtime")"
  install -m 644 "$thrift_build_dir/thrift-$thrift_version/lib/js/src/thrift.js" \
    "$thrift_cached_runtime"
  runtime_is_cached
}

if ! compiler_is_cached || ! runtime_is_cached; then
  prepare_sources
  install_compiler
  install_browser_runtime
fi

sudo install "$thrift_cached_bin" /usr/local/bin/thrift
printf 'export THRIFT_BROWSER_RUNTIME=%q\n' "$thrift_cached_runtime" >> "$BASH_ENV"
thrift -version
