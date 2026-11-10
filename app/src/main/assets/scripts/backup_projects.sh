#!/data/data/com.termux/files/usr/bin/bash
# ==============================================================================
# Jarvis AI Developer Projects Git-Aware Backup Packager
# ==============================================================================
set -euo pipefail

PREFIX_DIR="${PREFIX:-/data/data/com.termux/files/usr}"
HOME_DIR="${HOME:-/data/data/com.termux/files/home}"
TMP_ROOT="${TMPDIR:-${PREFIX_DIR}/tmp}"
mkdir -p "$TMP_ROOT"

BACKUP_DIR="/sdcard/Download/Jarvis/backups"
TIMESTAMP="$(date +"%Y-%m-%d-%H-%M-%S")"
ZIP_BASENAME="jarvis_projects_backup_${TIMESTAMP}.zip"
OUTPUT_ZIP="${1:-${BACKUP_DIR}/${ZIP_BASENAME}}"
mkdir -p "$(dirname "$OUTPUT_ZIP")"

echo "================================================"
echo "      Jarvis AI Projects Backup Packager        "
echo "================================================"
echo "Output Archive : $OUTPUT_ZIP"
echo "Timestamp      : $TIMESTAMP"
echo "================================================"

PROJECTS_DIR="${HOME_DIR}/projects"
if [ ! -d "$PROJECTS_DIR" ]; then
    echo "⚠️ Projects directory not found at $PROJECTS_DIR. Creating empty folder."
    mkdir -p "$PROJECTS_DIR"
fi

cd "$HOME_DIR"
TEMP_ZIP="${TMP_ROOT}/jarvis_proj_stage_${TIMESTAMP}.zip"

EXCLUDE_PATTERNS=(
    "*/node_modules/*"
    "*/.gradle/*"
    "*/build/*"
    "*/.cache/*"
    "*/dist/*"
    "*/__pycache__/*"
    "*.log"
    "*.sock"
)

echo "[1/2] Creating compressed git-aware project archive..."
zip -r -y "$TEMP_ZIP" "projects" -x "${EXCLUDE_PATTERNS[@]}"

echo "[2/2] Exporting to storage..."
mv "$TEMP_ZIP" "$OUTPUT_ZIP"

echo "🎉 Backup Complete: $OUTPUT_ZIP"
