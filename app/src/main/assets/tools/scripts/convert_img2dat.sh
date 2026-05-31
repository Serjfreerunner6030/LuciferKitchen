#!/system/bin/sh
# convert_img2dat.sh — Convert IMG to DAT/BR format
# Args: $1=input.img, $2=partition_name

TOOLS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BROTLI="$TOOLS_DIR/bin/brotli"
IMG2SDAT="$TOOLS_DIR/scripts/img2sdat.py"
INPUT="$1"
PARTITION="$2"

[ -z "$INPUT" ] && echo "ERROR: No input file" && exit 1
[ ! -f "$INPUT" ] && echo "ERROR: Input file not found: $INPUT" && exit 1
[ -z "$PARTITION" ] && PARTITION="system"

OUTDIR="$(dirname "$INPUT")"

echo "INFO: Converting $INPUT to DAT format..."

# Find python
PYTHON=""
for p in python3 python; do
    if command -v "$p" >/dev/null 2>&1; then
        PYTHON="$p"
        break
    fi
done

if [ -z "$PYTHON" ]; then
    echo "ERROR: Python not found. Install python3."
    exit 1
fi

if [ ! -f "$IMG2SDAT" ]; then
    echo "ERROR: img2sdat.py not found at $IMG2SDAT"
    exit 1
fi

"$PYTHON" "$IMG2SDAT" "$INPUT" -o "$OUTDIR" -p "$PARTITION" -v 4
RET=$?

if [ $RET -ne 0 ]; then
    echo "ERROR: img2sdat conversion failed"
    exit 1
fi

DAT_FILE="$OUTDIR/${PARTITION}.new.dat"
TRANSFER_LIST="$OUTDIR/${PARTITION}.transfer.list"

if [ ! -f "$DAT_FILE" ]; then
    echo "ERROR: DAT file not created"
    exit 1
fi

# Compress with brotli
if [ -x "$BROTLI" ]; then
    echo "INFO: Compressing with brotli..."
    "$BROTLI" --quality=6 "$DAT_FILE" -o "${DAT_FILE}.br"
    if [ $? -eq 0 ] && [ -f "${DAT_FILE}.br" ]; then
        echo "INFO: Created ${DAT_FILE}.br"
        rm -f "$DAT_FILE"
    fi
else
    echo "WARNING: brotli not found, skipping compression"
fi

echo "INFO: Conversion complete"
echo "INFO: Transfer list: $TRANSFER_LIST"
exit 0
