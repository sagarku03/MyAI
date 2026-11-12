#!/usr/bin/env bash
# ==============================================================================
# MyAI / Jarvis AI — Automated Multi-Flavor APK Builder & Release Packager
# Supports Standard (com.jarvisai.assistant), Termux (com.termux), and
# Termux-Shared-UID builds with SHA-256 release verification.
# ==============================================================================

# Dynamic Termux / Linux Shebang Fallback
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
OUTPUT_DIR="${REPO_ROOT}/output"
VERSION_FILE="${REPO_ROOT}/version.json"
APP_GRADLE_FILE="${REPO_ROOT}/app/build.gradle.kts"

BUILD_MODE="debug"
CLEAN_FIRST=0
SKIP_SHARED=0

usage() {
    cat <<EOF
Usage: $(basename "$0") [OPTIONS]

Automated multi-flavor APK builder & packager for MyAI.

Options:
  --debug            Build Debug APKs (default)
  --release          Build Release APKs
  --clean            Run clean task before building
  --skip-shared      Skip Termux Shared-UID variant
  -h, --help         Show this help message
EOF
}

while [ $# -gt 0 ]; do
    case "$1" in
        --debug)
            BUILD_MODE="debug"
            shift
            ;;
        --release)
            BUILD_MODE="release"
            shift
            ;;
        --clean)
            CLEAN_FIRST=1
            shift
            ;;
        --skip-shared)
            SKIP_SHARED=1
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "[ERROR] Unknown argument: $1" >&2
            usage
            exit 1
            ;;
    esac
done

# Resolve Gradle runner (prefer system gradle in cloud container or ./gradlew on workstation)
resolve_gradle_cmd() {
    if command -v gradle >/dev/null 2>&1; then
        echo "gradle"
    elif [ -x "${REPO_ROOT}/gradlew" ]; then
        echo "${REPO_ROOT}/gradlew"
    else
        echo "[ERROR] Neither 'gradle' nor './gradlew' found in PATH or repository root." >&2
        exit 1
    fi
}

# Extract version string from version.json or fallback to app/build.gradle.kts
extract_version() {
    local ver=""
    if [ -f "${VERSION_FILE}" ]; then
        ver="$(grep -E '"versionName"\s*:' "${VERSION_FILE}" | head -n 1 | sed -E 's/.*"versionName"\s*:\s*"([^"]+)".*/\1/' || true)"
    fi
    if [ -z "${ver}" ] && [ -f "${APP_GRADLE_FILE}" ]; then
        ver="$(grep -E 'versionName\s*=\s*"' "${APP_GRADLE_FILE}" | head -n 1 | sed -E 's/.*versionName\s*=\s*"([^"]+)".*/\1/' || true)"
    fi
    if [ -z "${ver}" ]; then
        ver="4.0.0"
    fi
    echo "${ver}"
}

compute_sha256() {
    local target_file="$1"
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "${target_file}" | awk '{print $1}'
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "${target_file}" | awk '{print $1}'
    elif command -v openssl >/dev/null 2>&1; then
        openssl dgst -sha256 "${target_file}" | awk '{print $NF}'
    else
        echo "sha256-unavailable"
    fi
}

find_built_apk() {
    local mode="$1"
    local search_dir="${REPO_ROOT}/app/build/outputs/apk"
    if [ ! -d "${search_dir}" ]; then
        return 1
    fi
    find "${search_dir}" -type f -name "*-${mode}.apk" -o -name "*${mode}*.apk" | sort | head -n 1
}

GRADLE_CMD="$(resolve_gradle_cmd)"
APP_VERSION="$(extract_version)"
CAPITALIZED_MODE="$(tr '[:lower:]' '[:upper:]' <<< "${BUILD_MODE:0:1}")${BUILD_MODE:1}"

mkdir -p "${OUTPUT_DIR}"
cd "${REPO_ROOT}"

echo "=================================================================="
echo " MyAI Multi-Flavor APK Packaging Pipeline"
echo " Version    : v${APP_VERSION}"
echo " Build Mode : ${BUILD_MODE} (${CAPITALIZED_MODE})"
echo " Gradle Cmd : ${GRADLE_CMD}"
echo " Output Dir : ${OUTPUT_DIR}"
echo "=================================================================="

if [ "${CLEAN_FIRST}" -eq 1 ]; then
    echo "[1/4] Cleaning previous build outputs..."
    "${GRADLE_CMD}" :app:clean --no-daemon
fi

build_and_package_variant() {
    local variant_slug="$1"
    local gradle_task="$2"
    local flavor_prop="$3"
    local shared_uid_prop="$4"

    local target_apk_name="myAI-${variant_slug}-v${APP_VERSION}-${BUILD_MODE}.apk"
    local target_apk_path="${OUTPUT_DIR}/${target_apk_name}"

    echo ""
    echo "------------------------------------------------------------------"
    echo ">>> Building Variant: ${variant_slug} (${gradle_task})"
    echo "------------------------------------------------------------------"

    "${GRADLE_CMD}" "${gradle_task}" \
        -PflavorMode="${flavor_prop}" \
        -PsharedUid="${shared_uid_prop}" \
        --build-cache

    local built_apk
    built_apk="$(find_built_apk "${BUILD_MODE}" || true)"
    if [ -z "${built_apk}" ] || [ ! -f "${built_apk}" ]; then
        echo "[ERROR] Could not locate compiled APK for variant '${variant_slug}' under app/build/outputs/apk/" >&2
        exit 1
    fi

    cp -f "${built_apk}" "${target_apk_path}"
    local size_bytes
    size_bytes="$(wc -c < "${target_apk_path}" | tr -d ' ')"
    local sha256_hash
    sha256_hash="$(compute_sha256 "${target_apk_path}")"

    echo "[OK] Packaged: ${target_apk_name} (${size_bytes} bytes)"
    echo "     SHA-256 : ${sha256_hash}"
}

# 1. Standard Flavor (com.jarvisai.assistant)
build_and_package_variant \
    "standard" \
    ":app:assembleStandard${CAPITALIZED_MODE}" \
    "standard" \
    "false"

# 2. Termux Flavor (com.termux)
build_and_package_variant \
    "termux" \
    ":app:assembleTermux${CAPITALIZED_MODE}" \
    "termux" \
    "false"

# 3. Termux Shared-UID Flavor (com.termux + sharedUserId)
if [ "${SKIP_SHARED}" -eq 0 ]; then
    build_and_package_variant \
        "termux-shared" \
        ":app:assembleTermuxShared${CAPITALIZED_MODE}" \
        "termux" \
        "true"
fi

# Generate SHA256SUMS.txt
CHECKSUM_FILE="${OUTPUT_DIR}/SHA256SUMS.txt"
rm -f "${CHECKSUM_FILE}"
(
    cd "${OUTPUT_DIR}"
    for apk in myAI-*-v"${APP_VERSION}"-"${BUILD_MODE}".apk; do
        if [ -f "${apk}" ]; then
            hash_val="$(compute_sha256 "${apk}")"
            printf "%s  %s\n" "${hash_val}" "${apk}" >> "${CHECKSUM_FILE}"
        fi
    done
)

echo ""
echo "=================================================================="
echo " Build Complete! Artifacts written to ${OUTPUT_DIR}:"
cat "${CHECKSUM_FILE}"
echo "=================================================================="
