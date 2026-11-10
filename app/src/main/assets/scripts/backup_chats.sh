#!/data/data/com.termux/files/usr/bin/bash
# ==============================================================================
# Jarvis AI Cognitive Memory & Chats Packager
# ==============================================================================
set -euo pipefail

HOME_DIR="${HOME:-/data/data/com.termux/files/home}"
BACKUP_DIR="/sdcard/Download/Jarvis/backups"
TIMESTAMP="$(date +"%Y-%m-%d-%H-%M-%S")"
OUTPUT_ZIP="${1:-${BACKUP_DIR}/jarvis_chats_backup_${TIMESTAMP}.zip}"
mkdir -p "$(dirname "$OUTPUT_ZIP")"

cd "$HOME_DIR"
TEMP_ZIP="${HOME_DIR}/jarvis_chats_temp.zip"
rm -f "$TEMP_ZIP"

echo "[1/2] Packaging ~/.gemini & Jarvis Memory vaults..."
zip -r -y "$TEMP_ZIP" ".gemini" -x "*.sock" "*/cache/*" "*/log/*" "*.log" || true

echo "[2/2] Moving archive to $OUTPUT_ZIP..."
mv "$TEMP_ZIP" "$OUTPUT_ZIP"
echo "🎉 Memory & Chats Backup Complete: $OUTPUT_ZIP"
