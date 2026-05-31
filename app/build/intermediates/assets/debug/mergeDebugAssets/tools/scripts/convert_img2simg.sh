#!/system/bin/sh
# convert_img2simg.sh — Convert raw IMG to sparse IMG
# Args: $1=input.img, $2=output.simg (optional)

TOOLS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
IMG2SIMG="$TOOLS_DIR/bin/img2simg"
INPUT="$1"
OUTPUT="$2"

[ -z "$INPUT" ] && echo "ERROR: No input file" && exit 1
[ ! -f "$INPUT" ] && echo "ERROR: Input file not found: $INPUT" && exit 1

if [ -z "$OUTPUT" ]; then
    OUTPUT="${INPUT%.img}.simg"
fi

if [ ! -x "$IMG2SIMG" ]; then
    echo "ERROR: img2simg binary not found at $IMG2SIMG"
    exit 1
fi

echo "INFO: Converting $INPUT to sparse image..."
"$IMG2SIMG" "$INPUT" "$OUTPUT"
RET=$?

if [ $RET -eq 0 ] && [ -f "$OUTPUT" ]; then
    SIZE=$(stat -c%s "$OUTPUT" 2>/dev/null || ls -l "$OUTPUT" | awk '{print $5}')
    echo "INFO: Sparse image created: $OUTPUT ($SIZE bytes)"
else
    echo "ERROR: Conversion failed (exit code $RET)"
    exit 1
fi

exit 0
