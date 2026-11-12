#!/usr/bin/env bash
# ==============================================================================
# MyAI / Jarvis AI — SQLite Room Database & Agent Transcript Restore Utility
# Restores jarvis_database (-wal, -shm) and agent transcripts from
# /sdcard/Download/MyAI/backups/chats_backup_<timestamp>.zip.
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

while [ $# -gt 0 ]; do
    case "$1" in
        --archive)
            ARCHIVE_PATH="$2"
            shift 2
            ;;
        -h|--help)
            echo "Usage: $(basename "$0") [--archive /sdcard/Download/MyAI/backups/chats_backup_*.zip]"
            exit 0
            ;;
        *)
            echo "[ERROR] Unknown argument: $1" >&2
            exit 1
            ;;
    esac
done

if [ -z "${ARCHIVE_PATH}" ] && [ -d "${BACKUP_DIR}" ]; then
    ARCHIVE_PATH="$(find "${BACKUP_DIR}" -maxdepth 1 -type f -name "chats_backup_*.zip" | sort | tail -n 1 || true)"
fi

if [ -z "${ARCHIVE_PATH}" ] || [ ! -f "${ARCHIVE_PATH}" ]; then
    echo "[ERROR] No chat backup archive found." >&2
    exit 1
fi

TIMESTAMP="$(date +"%Y%m%d_%H%M%S")"
STAGE_DIR="${TMPDIR}/myai_chats_restore_${TIMESTAMP}"
mkdir -p "${STAGE_DIR}"

if command -v unzip >/dev/null 2>&1; then
    unzip -q "${ARCHIVE_PATH}" -d "${STAGE_DIR}"
elif command -v python3 >/dev/null 2>&1; then
    python3 -c "import sys, zipfile; zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])" "${ARCHIVE_PATH}" "${STAGE_DIR}"
else
    rm -rf "${STAGE_DIR}"
    echo "[ERROR] Neither 'unzip' nor 'python3' available." >&2
    exit 1
fi

for app_root in "/data/data/com.jarvisai.assistant" "/data/data/com.termux"; do
    if [ -d "${app_root}" ] && [ -w "${app_root}" ]; then
        mkdir -p "${app_root}/databases"
        if [ -d "${STAGE_DIR}/databases" ]; then
            cp -rf "${STAGE_DIR}/databases/." "${app_root}/databases/" 2>/dev/null || true
        fi
    fi
done

if [ -d "${STAGE_DIR}/transcripts/.claude_projects" ]; then
    mkdir -p "${HOME}/.claude/projects"
    cp -rf "${STAGE_DIR}/transcripts/.claude_projects/." "${HOME}/.claude/projects/" 2>/dev/null || true
fi

if [ -d "${STAGE_DIR}/transcripts/.gemini_jarvis-antigravity" ]; then
    mkdir -p "${HOME}/.gemini/jarvis-antigravity"
    cp -rf "${STAGE_DIR}/transcripts/.gemini_jarvis-antigravity/." "${HOME}/.gemini/jarvis-antigravity/" 2>/dev/null || true
fi

rm -rf "${STAGE_DIR}"
echo "[OK] Restored Room databases and agent transcripts from ${ARCHIVE_PATH}"
