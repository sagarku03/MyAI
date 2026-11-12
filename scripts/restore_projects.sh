#!/usr/bin/env bash
# ==============================================================================
# MyAI / Jarvis AI — Coding Workspace Disaster Recovery Restore Utility
# Unpacks project archives from /sdcard/Download/MyAI/backups into $HOME with
# safe conflict resolution ('bak' renames existing folders to .bak.<timestamp>).
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

export PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"
export HOME="${HOME:-/data/data/com.termux/files/home}"
export TMPDIR="${TMPDIR:-${PREFIX}/tmp}"

BACKUP_DIR="/sdcard/Download/MyAI/backups"
ARCHIVE_PATH=""
CONFLICT_POLICY="bak"

usage() {
    cat <<EOF
Usage: $(basename "$0") [OPTIONS]

Restores coding workspaces into \$HOME from a MyAI backup zip archive.

Options:
  --archive <file>           Path to specific projects_backup_*.zip (default: latest)
  --conflict <bak|overwrite> Conflict resolution policy (default: bak)
  -h, --help                 Display this help message
EOF
}

while [ $# -gt 0 ]; do
    case "$1" in
        --archive)
            ARCHIVE_PATH="$2"
            shift 2
            ;;
        --conflict)
            CONFLICT_POLICY="$2"
            shift 2
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

if [ -z "${ARCHIVE_PATH}" ]; then
    if [ -d "${BACKUP_DIR}" ]; then
        ARCHIVE_PATH="$(find "${BACKUP_DIR}" -maxdepth 1 -type f -name "projects_backup_*.zip" | sort | tail -n 1 || true)"
    fi
fi

if [ -z "${ARCHIVE_PATH}" ] || [ ! -f "${ARCHIVE_PATH}" ]; then
    echo "[ERROR] No project backup archive found. Specify one with --archive <path>." >&2
    exit 1
fi

TIMESTAMP="$(date +"%Y%m%d_%H%M%S")"
STAGE_DIR="${TMPDIR}/myai_restore_stage_${TIMESTAMP}"
mkdir -p "${STAGE_DIR}" "${HOME}"

echo "=================================================================="
echo " MyAI Workspace Restore Utility"
echo " Archive         : ${ARCHIVE_PATH}"
echo " Target Home     : ${HOME}"
echo " Conflict Policy : ${CONFLICT_POLICY}"
echo "=================================================================="

if command -v unzip >/dev/null 2>&1; then
    unzip -q "${ARCHIVE_PATH}" -d "${STAGE_DIR}"
elif command -v python3 >/dev/null 2>&1; then
    python3 -c "import sys, zipfile; zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])" "${ARCHIVE_PATH}" "${STAGE_DIR}"
else
    rm -rf "${STAGE_DIR}"
    echo "[ERROR] Neither 'unzip' nor 'python3' is available to extract the backup." >&2
    exit 1
fi

RESTORED_COUNT=0
while IFS= read -r -d '' entry_path; do
    proj_name="$(basename "${entry_path}")"
    if [ "${proj_name}" = ".myai_backup_manifest.json" ]; then
        continue
    fi

    dest_path="${HOME}/${proj_name}"
    if [ -e "${dest_path}" ]; then
        if [ "${CONFLICT_POLICY}" = "bak" ]; then
            bak_path="${dest_path}.bak.${TIMESTAMP}"
            echo "[SAFEGUARD] Backing up existing '${proj_name}' -> '$(basename "${bak_path}")'"
            mv "${dest_path}" "${bak_path}"
        elif [ "${CONFLICT_POLICY}" = "overwrite" ]; then
            echo "[OVERWRITE] Replacing existing '${proj_name}'"
            rm -rf "${dest_path}"
        else
            echo "[SKIP] Skipping conflicting directory '${proj_name}'"
            continue
        fi
    fi

    mv "${entry_path}" "${dest_path}"
    RESTORED_COUNT=$((RESTORED_COUNT + 1))
    echo "[OK] Restored workspace: ${dest_path}"
done < <(find "${STAGE_DIR}" -mindepth 1 -maxdepth 1 -print0)

rm -rf "${STAGE_DIR}"
echo "[DONE] Successfully restored ${RESTORED_COUNT} project workspace(s) to ${HOME}."
