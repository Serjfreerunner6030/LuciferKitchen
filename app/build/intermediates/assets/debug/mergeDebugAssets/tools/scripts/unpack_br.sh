#!/system/bin/sh
# unpack_br.sh - Decompresses .dat.br files using brotli
# Args: $1=project_dir, $2=file.dat.br, $3=delete(optional, "delete" to remove original)

TOOLS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BIN="$TOOLS_DIR/bin"

PROJECT_DIR="$1"
BR_FILE="$2"
DELETE_ORIG="$3"

if [ -z "$PROJECT_DIR" ] || [ -z "$BR_FILE" ]; then
    echo "ERROR: Usage: unpack_br.sh <project_dir> <file.dat.br> [delete]"
    exit 1
fi

if [ ! -d "$PROJECT_DIR" ]; then
    echo "ERROR: Project directory not found: $PROJECT_DIR"
    exit 1
fi

BROTLI="$BIN/brotli"
if [ ! -f "$BROTLI" ]; then
    echo "ERROR: brotli binary not found: $BROTLI"
    exit 1
fi

# Resolve full path
if [ -f "$BR_FILE" ]; then
    FULL_PATH="$BR_FILE"
elif [ -f "$PROJECT_DIR/$BR_FILE" ]; then
    FULL_PATH="$PROJECT_DIR/$BR_FILE"
else
    FULL_PATH="$(find "$PROJECT_DIR" -maxdepth 3 -name "$BR_FILE" 2>/dev/null | head -n 1)"
fi

if [ -z "$FULL_PATH" ] || [ ! -f "$FULL_PATH" ]; then
    echo "ERROR: File not found: $BR_FILE"
    exit 1
fi

echo "INFO: Decompressing: $FULL_PATH"

# Output file: strip .br extension
OUTPUT="${FULL_PATH%.br}"

"$BROTLI" --decompress --input "$FULL_PATH" --output "$OUTPUT"
RET=$?

if [ $RET -ne 0 ]; then
    echo "ERROR: brotli decompression failed with exit code $RET"
    exit 1
fi

echo "INFO: Decompressed to: $OUTPUT"

if [ "$DELETE_ORIG" = "delete" ] || [ "$DELETE_ORIG" = "1" ]; then
    echo "INFO: Removing original: $FULL_PATH"
    rm -f "$FULL_PATH"
fi

ls -lh "$OUTPUT" 2>/dev/null
exit 0
