#!/usr/bin/env bash
# ==============================================================================
# MyAI / Jarvis AI — Git-Aware Coding Workspace Disaster Recovery Backup
# Scans $HOME for coding workspaces, strips heavy build/cache artifacts in git
# mode, and writes resilient archives to /sdcard/Download/MyAI/backups.
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

BACKUP_MODE="git"
CUSTOM_BACKUP_DIR="/sdcard/Download/MyAI/backups"
SPECIFIC_PROJECT=""

usage() {
    cat <<EOF
Usage: $(basename "$0") [OPTIONS]

Backs up coding projects in \$HOME to /sdcard/Download/MyAI/backups.

Options:
  --mode <git|full>     'git' excludes node_modules/build/.gradle/.git/objects (default: git)
  --dest <path>         Custom destination directory (default: /sdcard/Download/MyAI/backups)
  --project <name>      Backup only a specific project directory under \$HOME
  -h, --help            Display this help message
EOF
}

while [ $# -gt 0 ]; do
    case "$1" in
        --mode)
            BACKUP_MODE="${2:-git}"
            shift 2
            ;;
        --dest)
            CUSTOM_BACKUP_DIR="$2"
            shift 2
            ;;
        --project)
            SPECIFIC_PROJECT="$2"
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

# Ensure Scoped-Storage resilient destination exists with fallback
if ! mkdir -p "${CUSTOM_BACKUP_DIR}" 2>/dev/null; then
    CUSTOM_BACKUP_DIR="${HOME}/MyAI_Backups/projects"
    mkdir -p "${CUSTOM_BACKUP_DIR}"
fi
mkdir -p "${TMPDIR}"

TIMESTAMP="$(date +"%Y%m%d_%H%M%S")"
ARCHIVE_NAME="projects_backup_${TIMESTAMP}.zip"
ARCHIVE_PATH="${CUSTOM_BACKUP_DIR}/${ARCHIVE_NAME}"
MANIFEST_FILE="${TMPDIR}/myai_backup_manifest_${TIMESTAMP}.json"

# Discover candidate project directories in $HOME
declare -a PROJECT_DIRS=()

if [ -n "${SPECIFIC_PROJECT}" ]; then
    if [ -d "${HOME}/${SPECIFIC_PROJECT}" ]; then
        PROJECT_DIRS+=("${SPECIFIC_PROJECT}")
    else
        echo "[ERROR] Project directory '${HOME}/${SPECIFIC_PROJECT}' does not exist." >&2
        exit 1
    fi
else
    while IFS= read -r -d '' dir_path; do
        dir_name="$(basename "${dir_path}")"
        case "${dir_name}" in
            .*|storage|downloads|MyAI_Backups|tmp)
                continue
                ;;
        esac
        PROJECT_DIRS+=("${dir_name}")
    done < <(find "${HOME}" -mindepth 1 -maxdepth 1 -type d -print0 2>/dev/null || true)
fi

if [ "${#PROJECT_DIRS[@]}" -eq 0 ]; then
    echo "[WARN] No project directories found under ${HOME} to back up."
    exit 0
fi

# Generate JSON metadata manifest for restoration integrity
{
    printf "{\n"
    printf '  "timestamp": "%s",\n' "${TIMESTAMP}"
    printf '  "mode": "%s",\n' "${BACKUP_MODE}"
    printf '  "homeDir": "%s",\n' "${HOME}"
    printf '  "projectCount": %d,\n' "${#PROJECT_DIRS[@]}"
    printf '  "projects": [\n'
    for i in "${!PROJECT_DIRS[@]}"; do
        proj="${PROJECT_DIRS[$i]}"
        git_branch=""
        git_remote=""
        if [ -d "${HOME}/${proj}/.git" ] && command -v git >/dev/null 2>&1; then
            git_branch="$(git -C "${HOME}/${proj}" rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown")"
            git_remote="$(git -C "${HOME}/${proj}" config --get remote.origin.url 2>/dev/null || echo "")"
        fi
        comma=","
        if [ "$i" -eq $(( ${#PROJECT_DIRS[@]} - 1 )) ]; then
            comma=""
        fi
        printf '    {"name": "%s", "gitBranch": "%s", "gitRemote": "%s"}%s\n' \
            "${proj}" "${git_branch}" "${git_remote}" "${comma}"
    done
    printf "  ]\n}\n"
} > "${MANIFEST_FILE}"

echo "=================================================================="
echo " MyAI Workspace Disaster Recovery Backup"
echo " Source Home : ${HOME}"
echo " Mode        : ${BACKUP_MODE}"
echo " Projects    : ${PROJECT_DIRS[*]}"
echo " Destination : ${ARCHIVE_PATH}"
echo "=================================================================="

cd "${HOME}"
cp -f "${MANIFEST_FILE}" "${HOME}/.myai_backup_manifest.json"

if command -v zip >/dev/null 2>&1; then
    if [ "${BACKUP_MODE}" = "git" ]; then
        zip -r -q "${ARCHIVE_PATH}" ".myai_backup_manifest.json" "${PROJECT_DIRS[@]}" \
            -x "*/node_modules/*" \
            -x "*/.gradle/*" \
            -x "*/build/*" \
            -x "*/.cache/*" \
            -x "*/venv/*" \
            -x "*/.venv/*" \
            -x "*/__pycache__/*" \
            -x "*/target/*" \
            -x "*/.next/*" \
            -x "*/dist/*" \
            -x "*/.git/objects/pack/*"
    else
        zip -r -q "${ARCHIVE_PATH}" ".myai_backup_manifest.json" "${PROJECT_DIRS[@]}"
    fi
elif command -v python3 >/dev/null 2>&1; then
    python3 - "${ARCHIVE_PATH}" "${BACKUP_MODE}" ".myai_backup_manifest.json" "${PROJECT_DIRS[@]}" <<'PYEOF'
import os
import sys
import zipfile

archive_path = sys.argv[1]
mode = sys.argv[2]
items = sys.argv[3:]

EXCLUDED_DIRS = {
    "node_modules", ".gradle", "build", ".cache",
    "venv", ".venv", "__pycache__", "target", ".next", "dist"
}

with zipfile.ZipFile(archive_path, "w", compression=zipfile.ZIP_DEFLATED) as zf:
    for item in items:
        if os.path.isfile(item):
            zf.write(item, item)
            continue
        for root, dirs, files in os.walk(item):
            if mode == "git":
                dirs[:] = [
                    d for d in dirs
                    if d not in EXCLUDED_DIRS and not (root.endswith(".git/objects") and d == "pack")
                ]
            for f in files:
                full_path = os.path.join(root, f)
                if os.path.islink(full_path):
                    continue
                zf.write(full_path, os.path.relpath(full_path, "."))
PYEOF
else
    rm -f "${HOME}/.myai_backup_manifest.json" "${MANIFEST_FILE}"
    echo "[ERROR] Neither 'zip' nor 'python3' is available to create the backup archive." >&2
    exit 1
fi

rm -f "${HOME}/.myai_backup_manifest.json" "${MANIFEST_FILE}"

ARCHIVE_SIZE="$(wc -c < "${ARCHIVE_PATH}" | tr -d ' ')"
echo "[OK] Workspace backup completed: ${ARCHIVE_PATH} (${ARCHIVE_SIZE} bytes)"
