#!/usr/bin/env bash
# ==============================================================================
# MyAI / Jarvis AI — Go IDE Bridge & Hub Supervisor Cross-Compiler
# Cross-compiles agy_ide_bridge for Android arm64-v8a (GOOS=linux GOARCH=arm64
# CGO_ENABLED=0), strips DWARF/symbol tables, and compresses via XZ into
# app/src/main/assets/bin/agy_ide_bridge.xz.
# ==============================================================================

if [ -z "${BASH_VERSION:-}" ]; then
    if [ -x "/data/data/com.termux/files/usr/bin/bash" ]; then
        exec "/data/data/com.termux/files/usr/bin/bash" "$0" "$@"
    elif [ -n "${PREFIX:-}" ] && [ -x "${PREFIX}/bin/bash" ]; then
        exec "${PREFIX}/bin/bash" "$0" "$@"
    elif command -v bash >/dev/null 2>&1; then
        exec "$(command -v bash)" "$0" "$@"
    fi
fi

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
BRIDGE_SRC_DIR="${REPO_ROOT}/agy_ide_bridge"
ASSETS_BIN_DIR="${REPO_ROOT}/app/src/main/assets/bin"
BUILD_TMP_DIR="${REPO_ROOT}/.build-outputs/agy_ide_bridge"
OUTPUT_BINARY="${BUILD_TMP_DIR}/agy_ide_bridge"
OUTPUT_XZ="${ASSETS_BIN_DIR}/agy_ide_bridge.xz"

TARGET_GOOS="${TARGET_GOOS:-linux}"
TARGET_GOARCH="${TARGET_GOARCH:-arm64}"

echo "=================================================================="
echo " MyAI Go IDE Bridge Cross-Compilation Pipeline"
echo " Source Dir   : ${BRIDGE_SRC_DIR}"
echo " Target Arch  : GOOS=${TARGET_GOOS} GOARCH=${TARGET_GOARCH} CGO_ENABLED=0"
echo " Asset Output : ${OUTPUT_XZ}"
echo "=================================================================="

if ! command -v go >/dev/null 2>&1; then
    echo "[ERROR] Go toolchain ('go') is not installed or not in PATH." >&2
    exit 1
fi

mkdir -p "${BUILD_TMP_DIR}" "${ASSETS_BIN_DIR}"
cd "${BRIDGE_SRC_DIR}"

echo "[1/4] Resolving Go module dependencies..."
go mod tidy || true

echo "[2/4] Compiling static arm64 binary with -trimpath and -ldflags='-s -w'..."
CGO_ENABLED=0 GOOS="${TARGET_GOOS}" GOARCH="${TARGET_GOARCH}" \
    go build -trimpath -ldflags="-s -w -buildid=" -o "${OUTPUT_BINARY}" main.go

# Optional secondary symbol strip if llvm-strip or strip is available
echo "[3/4] Checking binary symbol stripper (llvm-strip / strip)..."
if command -v llvm-strip >/dev/null 2>&1; then
    llvm-strip --strip-all "${OUTPUT_BINARY}" 2>/dev/null || true
elif command -v aarch64-linux-android-strip >/dev/null 2>&1; then
    aarch64-linux-android-strip --strip-all "${OUTPUT_BINARY}" 2>/dev/null || true
elif command -v strip >/dev/null 2>&1 && [ "$(uname -m)" = "aarch64" ]; then
    strip --strip-all "${OUTPUT_BINARY}" 2>/dev/null || true
fi

RAW_SIZE="$(wc -c < "${OUTPUT_BINARY}" | tr -d ' ')"
echo "      Raw arm64 binary size: ${RAW_SIZE} bytes"

echo "[4/4] Compressing binary with XZ (-9e) into Android assets..."
if command -v xz >/dev/null 2>&1; then
    xz -9 -e -c "${OUTPUT_BINARY}" > "${OUTPUT_XZ}"
elif command -v python3 >/dev/null 2>&1; then
    python3 - "${OUTPUT_BINARY}" "${OUTPUT_XZ}" <<'PYEOF'
import lzma
import sys

src, dst = sys.argv[1], sys.argv[2]
with open(src, "rb") as f_in:
    compressed = lzma.compress(f_in.read(), format=lzma.FORMAT_XZ, preset=9 | lzma.PRESET_EXTREME)
with open(dst, "wb") as f_out:
    f_out.write(compressed)
PYEOF
else
    echo "[ERROR] Neither 'xz' nor 'python3' (lzma) is available for XZ compression." >&2
    exit 1
fi

XZ_SIZE="$(wc -c < "${OUTPUT_XZ}" | tr -d ' ')"
echo "=================================================================="
echo " [OK] Built & Packaged: ${OUTPUT_XZ}"
echo "      Uncompressed    : ${RAW_SIZE} bytes"
echo "      XZ Compressed   : ${XZ_SIZE} bytes"
echo "=================================================================="
