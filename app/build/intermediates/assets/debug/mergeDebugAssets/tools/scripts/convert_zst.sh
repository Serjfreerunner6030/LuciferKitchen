#!/system/bin/sh
# convert_zst.sh — ZST-IMG conversion
# Args: $1=input_file, $2=direction(compress|decompress)

TOOLS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ZSTD="$TOOLS_DIR/bin/zstd"
INPUT="$1"
DIRECTION="$2"

[ -z "$INPUT" ] && echo "ERROR: No input file" && exit 1
[ ! -f "$INPUT" ] && echo "ERROR: Input file not found: $INPUT" && exit 1
[ -z "$DIRECTION" ] && DIRECTION="decompress"

if [ ! -x "$ZSTD" ]; then
    echo "ERROR: zstd binary not found at $ZSTD"
    exit 1
fi

if [ "$DIRECTION" = "compress" ]; then
    OUTPUT="${INPUT}.zst"
    echo "INFO: Compressing $INPUT..."
    "$ZSTD" -T0 "$INPUT" -o "$OUTPUT"
elif [ "$DIRECTION" = "decompress" ]; then
    OUTPUT="${INPUT%.zst}"
    [ "$OUTPUT" = "$INPUT" ] && OUTPUT="${INPUT}.img"
    echo "INFO: Decompressing $INPUT..."
    "$ZSTD" -d "$INPUT" -o "$OUTPUT"
else
    echo "ERROR: Unknown direction: $DIRECTION (use compress or decompress)"
    exit 1
fi

RET=$?
if [ $RET -eq 0 ] && [ -f "$OUTPUT" ]; then
    SIZE=$(stat -c%s "$OUTPUT" 2>/dev/null || ls -l "$OUTPUT" | awk '{print $5}')
    echo "INFO: Output: $OUTPUT ($SIZE bytes)"
else
    echo "ERROR: Conversion failed (exit code $RET)"
    exit 1
fi

exit 0
