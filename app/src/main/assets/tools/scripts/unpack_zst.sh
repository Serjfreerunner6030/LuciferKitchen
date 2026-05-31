#!/system/bin/sh
# unpack_zst.sh - Decompresses .zst files using zstd
# Args: $1=project_dir, $2=file.zst

TOOLS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BIN="$TOOLS_DIR/bin"

PROJECT_DIR="$1"
ZST_FILE="$2"

if [ -z "$PROJECT_DIR" ] || [ -z "$ZST_FILE" ]; then
    echo "ERROR: Usage: unpack_zst.sh <project_dir> <file.zst>"
    exit 1
fi

if [ ! -d "$PROJECT_DIR" ]; then
    echo "ERROR: Project directory not found: $PROJECT_DIR"
    exit 1
fi

ZSTD="$BIN/zstd"
if [ ! -f "$ZSTD" ]; then
    echo "ERROR: zstd binary not found: $ZSTD"
    exit 1
fi

# Resolve file path
if [ -f "$ZST_FILE" ]; then
    FULL_PATH="$ZST_FILE"
elif [ -f "$PROJECT_DIR/$ZST_FILE" ]; then
    FULL_PATH="$PROJECT_DIR/$ZST_FILE"
else
    FULL_PATH="$(find "$PROJECT_DIR" -maxdepth 4 -name "$ZST_FILE" 2>/dev/null | head -n 1)"
fi

if [ -z "$FULL_PATH" ] || [ ! -f "$FULL_PATH" ]; then
    echo "ERROR: File not found: $ZST_FILE"
    exit 1
fi

echo "INFO: Decompressing: $FULL_PATH"

# Output: strip .zst extension
OUTPUT="${FULL_PATH%.zst}"

"$ZSTD" --decompress --force -o "$OUTPUT" "$FULL_PATH"
RET=$?

if [ $RET -ne 0 ]; then
    echo "ERROR: zstd decompression failed with exit code $RET"
    exit 1
fi

echo "INFO: Decompressed to: $OUTPUT"
ls -lh "$OUTPUT" 2>/dev/null
exit 0
