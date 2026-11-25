#!/usr/bin/env bash
# POSIX twin of tools/build-core.ps1 — used by GitHub Actions and Linux/macOS.
#
# Usage: tools/build-core.sh [--release]
set -euo pipefail
cd "$(dirname "$0")/.."

PROFILE=debug
PROFILE_ARGS=()
if [[ "${1:-}" == "--release" ]]; then
  PROFILE=release
  PROFILE_ARGS=(--release)
fi

JNI_LIBS=android/app/src/main/jniLibs
BINDINGS=android/app/src/main/kotlin

echo "==> cargo ndk build ($PROFILE)"
cargo ndk -t arm64-v8a -t x86_64 -o "$JNI_LIBS" build -p hori-core "${PROFILE_ARGS[@]}"

echo "==> host build for bindgen ($PROFILE)"
cargo build -p hori-core "${PROFILE_ARGS[@]}"

case "$(uname -s)" in
  Linux*)  HOST_LIB="target/$PROFILE/libhori_core.so" ;;
  Darwin*) HOST_LIB="target/$PROFILE/libhori_core.dylib" ;;
  *)       HOST_LIB="target/$PROFILE/hori_core.dll" ;;
esac

echo "==> uniffi-bindgen generate (kotlin)"
cargo run -p hori-core --bin uniffi-bindgen "${PROFILE_ARGS[@]}" -- generate \
  --library "$HOST_LIB" --language kotlin --out-dir "$BINDINGS"

echo "==> done"