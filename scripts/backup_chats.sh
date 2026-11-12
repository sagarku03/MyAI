#!/usr/bin/env bash
# ==============================================================================
# MyAI / Jarvis AI — SQLite Room Database & Agent Transcript Backup Utility
# Backs up Room SQLite databases (jarvis_database, -wal, -shm), Claude/Codex/
# Antigravity JSONL transcripts, and shared preferences to
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
APP_DATA_CANDIDATES=(
    "/data/data/com.jarvisai.assistant"
    "/data/user/0/com.jarvisai.assistant"
    "/data/data/com.termux"
    "/data/user/0/com.termux"
)

while [ $# -gt 0 ]; do
    case "$1" in
        --dest)
            BACKUP_DIR="$2"
            shift 2
            ;;
        -h|--help)
            echo "Usage: $(basename "$0") [--dest /sdcard/Download/MyAI/backups]"
            exit 0
            ;;
        *)
            echo "[ERROR] Unknown argument: $1" >&2
            exit 1
            ;;
    esac
done

if ! mkdir -p "${BACKUP_DIR}" 2>/dev/null; then
    BACKUP_DIR="${HOME}/MyAI_Backups/chats"
    mkdir -p "${BACKUP_DIR}"
fi
mkdir -p "${TMPDIR}"

TIMESTAMP="$(date +"%Y%m%d_%H%M%S")"
STAGE_DIR="${TMPDIR}/myai_chats_stage_${TIMESTAMP}"
ARCHIVE_PATH="${BACKUP_DIR}/chats_backup_${TIMESTAMP}.zip"

mkdir -p "${STAGE_DIR}/databases" "${STAGE_DIR}/transcripts" "${STAGE_DIR}/shared_prefs"

COPIED_FILES=0

# 1. Collect Room SQLite Database files (jarvis_database, -wal, -shm)
for app_root in "${APP_DATA_CANDIDATES[@]}"; do
    db_dir="${app_root}/databases"
    if [ -d "${db_dir}" ] && [ -r "${db_dir}" ]; then
        for db_file in "${db_dir}"/jarvis_database* "${db_dir}"/*.db "${db_dir}"/*.db-wal "${db_dir}"/*.db-shm; do
            if [ -f "${db_file}" ]; then
                cp -f "${db_file}" "${STAGE_DIR}/databases/"
                COPIED_FILES=$((COPIED_FILES + 1))
            fi
        done
    fi

    # Collect session JSON stores in files/
    files_dir="${app_root}/files"
    if [ -d "${files_dir}" ] && [ -r "${files_dir}" ]; then
        for session_item in "${files_dir}"/chat_sessions* "${files_dir}"/*.json; do
            if [ -e "${session_item}" ]; then
                cp -rf "${session_item}" "${STAGE_DIR}/transcripts/" 2>/dev/null || true
                COPIED_FILES=$((COPIED_FILES + 1))
            fi
        done
    fi
done

# 2. Collect CLI Agent Transcripts from $HOME (.claude, .gemini/jarvis-antigravity, .codex)
for agent_dir in ".claude/projects" ".claude/todos" ".gemini/jarvis-antigravity" ".config/opencode"; do
    if [ -d "${HOME}/${agent_dir}" ]; then
        target_sub="${STAGE_DIR}/transcripts/$(echo "${agent_dir}" | tr '/' '_')"
        mkdir -p "${target_sub}"
        cp -rf "${HOME}/${agent_dir}/." "${target_sub}/" 2>/dev/null || true
        COPIED_FILES=$((COPIED_FILES + 1))
    fi
done

# Write metadata header
cat > "${STAGE_DIR}/backup_info.json" <<EOF
{
  "timestamp": "${TIMESTAMP}",
  "type": "chats_and_databases",
  "homeDir": "${HOME}",
  "copiedItems": ${COPIED_FILES}
}
EOF

echo "=================================================================="
echo " MyAI Chat & Room Database Backup Utility"
echo " Collected Items : ${COPIED_FILES}"
echo " Destination     : ${ARCHIVE_PATH}"
echo "=================================================================="

cd "${STAGE_DIR}"
if command -v zip >/dev/null 2>&1; then
    zip -r -q "${ARCHIVE_PATH}" .
elif command -v python3 >/dev/null 2>&1; then
    python3 - "${ARCHIVE_PATH}" <<'PYEOF'
import os
import sys
import zipfile

archive_path = sys.argv[1]
with zipfile.ZipFile(archive_path, "w", compression=zipfile.ZIP_DEFLATED) as zf:
    for root, _, files in os.walk("."):
        for f in files:
            full = os.path.join(root, f)
            zf.write(full, os.path.relpath(full, "."))
PYEOF
else
    rm -rf "${STAGE_DIR}"
    echo "[ERROR] Neither 'zip' nor 'python3' found." >&2
    exit 1
fi

rm -rf "${STAGE_DIR}"
echo "[OK] Chat & database backup saved to: ${ARCHIVE_PATH}"
