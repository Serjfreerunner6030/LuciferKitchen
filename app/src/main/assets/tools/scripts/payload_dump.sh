#!/system/bin/sh
# payload_dump.sh - Unpacks payload.bin using payload-dumper-go
# Args: $1=project_dir, $2=partition (optional, empty=all)

TOOLS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BIN="$TOOLS_DIR/bin"

PROJECT_DIR="$1"
PARTITION="$2"

if [ -z "$PROJECT_DIR" ]; then
    echo "ERROR: project_dir argument required"
    exit 1
fi

if [ ! -d "$PROJECT_DIR" ]; then
    echo "ERROR: Project directory not found: $PROJECT_DIR"
    exit 1
fi

PAYLOAD_DUMPER="$BIN/payload-dumper-go"
if [ ! -f "$PAYLOAD_DUMPER" ]; then
    echo "ERROR: payload-dumper-go binary not found: $PAYLOAD_DUMPER"
    exit 1
fi

# Find payload.bin
PAYLOAD_BIN=""
if [ -f "$PROJECT_DIR/payload.bin" ]; then
    PAYLOAD_BIN="$PROJECT_DIR/payload.bin"
else
    PAYLOAD_BIN="$(find "$PROJECT_DIR" -maxdepth 3 -name "payload.bin" 2>/dev/null | head -n 1)"
fi

if [ -z "$PAYLOAD_BIN" ] || [ ! -f "$PAYLOAD_BIN" ]; then
    echo "ERROR: payload.bin not found in $PROJECT_DIR"
    exit 1
fi

echo "INFO: Found payload.bin: $PAYLOAD_BIN"

OUTPUT_DIR="$PROJECT_DIR/extracted"
mkdir -p "$OUTPUT_DIR"

if [ -n "$PARTITION" ]; then
    echo "INFO: Extracting partition: $PARTITION"
    "$PAYLOAD_DUMPER" -partitions "$PARTITION" -output "$OUTPUT_DIR" "$PAYLOAD_BIN"
else
    echo "INFO: Extracting all partitions"
    "$PAYLOAD_DUMPER" -output "$OUTPUT_DIR" "$PAYLOAD_BIN"
fi

RET=$?
if [ $RET -ne 0 ]; then
    echo "ERROR: payload-dumper-go failed with exit code $RET"
    exit 1
fi

echo "INFO: Extraction complete. Output: $OUTPUT_DIR"
ls -lh "$OUTPUT_DIR" 2>/dev/null
exit 0
