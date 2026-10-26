#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LLAMA_DIR="${LLAMA_DIR:-$ROOT/third_party/llama.cpp}"
BUILD_DIR="${BUILD_DIR:-$ROOT/build/android-arm64}"
NDK="${ANDROID_NDK:-${ANDROID_NDK_HOME:-}}"
ABI="arm64-v8a"
PLATFORM="${ANDROID_PLATFORM:-android-28}"
JOBS="${JOBS:-$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 4)}"

if [[ -z "$NDK" ]]; then
  echo "Set ANDROID_NDK to an Android NDK 27 path" >&2
  exit 1
fi

if [[ ! -d "$LLAMA_DIR" ]]; then
  echo "llama.cpp not found at $LLAMA_DIR" >&2
  echo "Set LLAMA_DIR or clone https://github.com/ggml-org/llama.cpp there" >&2
  exit 1
fi

cmake -S "$LLAMA_DIR" -B "$BUILD_DIR" -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI="$ABI" \
  -DANDROID_PLATFORM="$PLATFORM" \
  -DCMAKE_BUILD_TYPE=Release \
  -DBUILD_SHARED_LIBS=OFF \
  -DGGML_RPC=ON \
  -DGGML_RPC_RDMA=OFF \
  -DLLAMA_BUILD_SERVER=ON \
  -DLLAMA_BUILD_TESTS=OFF

cmake --build "$BUILD_DIR" --target llama-server ggml-rpc-server -j "$JOBS"

OUT="$ROOT/app/src/main/jniLibs/$ABI"
mkdir -p "$OUT"
cp "$BUILD_DIR/bin/llama-server" "$OUT/libpc_llama_server.so"
cp "$BUILD_DIR/bin/ggml-rpc-server" "$OUT/libpc_ggml_rpc_server.so"

if command -v llvm-strip >/dev/null 2>&1; then
  llvm-strip --strip-all "$OUT/libpc_llama_server.so" "$OUT/libpc_ggml_rpc_server.so"
elif [[ -x "$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" ]]; then
  STRIP="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
  "$STRIP" --strip-all "$OUT/libpc_llama_server.so" "$OUT/libpc_ggml_rpc_server.so"
fi

echo "wrote $OUT"
