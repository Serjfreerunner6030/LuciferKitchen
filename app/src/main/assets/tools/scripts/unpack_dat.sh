#!/system/bin/sh
# unpack_dat.sh - Converts .dat files to .img using sdat2img
# Args: $1=project_dir, $2=partition_name (e.g. system, vendor, product)

TOOLS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BIN="$TOOLS_DIR/bin"
SCRIPTS="$TOOLS_DIR/scripts"

PROJECT_DIR="$1"
PARTITION="$2"

if [ -z "$PROJECT_DIR" ] || [ -z "$PARTITION" ]; then
    echo "ERROR: Usage: unpack_dat.sh <project_dir> <partition_name>"
    exit 1
fi

if [ ! -d "$PROJECT_DIR" ]; then
    echo "ERROR: Project directory not found: $PROJECT_DIR"
    exit 1
fi

SDAT2IMG="$SCRIPTS/sdat2img.py"
if [ ! -f "$SDAT2IMG" ]; then
    echo "ERROR: sdat2img.py not found: $SDAT2IMG"
    exit 1
fi

PYTHON=""
for py in /system/bin/python3 /system/bin/python /usr/bin/python3 /usr/bin/python; do
    if [ -x "$py" ]; then
        PYTHON="$py"
        break
    fi
done

if [ -z "$PYTHON" ]; then
    echo "ERROR: Python interpreter not found"
    exit 1
fi

# Find transfer list and dat file
TRANSFER_LIST=""
DAT_FILE=""

for dir in "$PROJECT_DIR" "$PROJECT_DIR/extracted"; do
    if [ -f "$dir/${PARTITION}.transfer.list" ]; then
        TRANSFER_LIST="$dir/${PARTITION}.transfer.list"
        if [ -f "$dir/${PARTITION}.new.dat.br" ]; then
            # Need to decompress first
            echo "INFO: Found compressed .dat.br, decompressing..."
            "$SCRIPTS/unpack_br.sh" "$PROJECT_DIR" "$dir/${PARTITION}.new.dat.br"
            if [ $? -ne 0 ]; then
                echo "ERROR: Failed to decompress ${PARTITION}.new.dat.br"
                exit 1
            fi
        fi
        if [ -f "$dir/${PARTITION}.new.dat" ]; then
            DAT_FILE="$dir/${PARTITION}.new.dat"
        fi
        break
    fi
done

if [ -z "$TRANSFER_LIST" ]; then
    TRANSFER_LIST="$(find "$PROJECT_DIR" -maxdepth 4 -name "${PARTITION}.transfer.list" 2>/dev/null | head -n 1)"
fi

if [ -z "$DAT_FILE" ]; then
    DAT_FILE="$(find "$PROJECT_DIR" -maxdepth 4 -name "${PARTITION}.new.dat" 2>/dev/null | head -n 1)"
fi

if [ -z "$TRANSFER_LIST" ] || [ ! -f "$TRANSFER_LIST" ]; then
    echo "ERROR: ${PARTITION}.transfer.list not found in $PROJECT_DIR"
    exit 1
fi

if [ -z "$DAT_FILE" ] || [ ! -f "$DAT_FILE" ]; then
    echo "ERROR: ${PARTITION}.new.dat not found in $PROJECT_DIR"
    exit 1
fi

OUTPUT_IMG="$(dirname "$DAT_FILE")/${PARTITION}.img"

echo "INFO: Converting $DAT_FILE to $OUTPUT_IMG"
echo "INFO: Transfer list: $TRANSFER_LIST"

"$PYTHON" "$SDAT2IMG" "$TRANSFER_LIST" "$DAT_FILE" "$OUTPUT_IMG"
RET=$?

if [ $RET -ne 0 ]; then
    echo "ERROR: sdat2img failed with exit code $RET"
    exit 1
fi

echo "INFO: Conversion complete: $OUTPUT_IMG"
ls -lh "$OUTPUT_IMG" 2>/dev/null
exit 0
