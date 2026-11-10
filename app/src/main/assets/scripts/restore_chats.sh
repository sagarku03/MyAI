#!/data/data/com.termux/files/usr/bin/bash
# ==============================================================================
# Jarvis AI Chats & Memory Restorer with Atomic Rollback
# ==============================================================================
set -euo pipefail

INPUT_ZIP="${1:-}"
TIMESTAMP="$(date +"%Y-%m-%d-%H-%M-%S")"
HOME_DIR="${HOME:-/data/data/com.termux/files/home}"
GEMINI_DIR="${HOME_DIR}/.gemini"
GEMINI_BAK="${HOME_DIR}/.gemini.bak.${TIMESTAMP}"

if [ -z "$INPUT_ZIP" ] || [ ! -f "$INPUT_ZIP" ]; then
    echo "❌ Error: Backup archive not found at '$INPUT_ZIP'!"
    exit 1
fi

echo "[1/4] Verifying archive integrity..."
unzip -t -q "$INPUT_ZIP"

if [ -d "$GEMINI_DIR" ]; then
    echo "[2/4] Safety backup of existing memory to $GEMINI_BAK..."
    mv "$GEMINI_DIR" "$GEMINI_BAK"
fi

echo "[3/4] Extracting snapshot into $HOME_DIR..."
if ! unzip -o -q "$INPUT_ZIP" -d "$HOME_DIR"; then
    echo "❌ Restore failed! Rolling back to original state..."
    if [ -d "$GEMINI_BAK" ]; then
        mv "$GEMINI_BAK" "$GEMINI_DIR"
    fi
    exit 1
fi

echo "[4/4] Securing permissions..."
chmod -R 700 "$GEMINI_DIR" 2>/dev/null || true

echo "🎉 Restore Complete! Cognitive memory and chats restored."
