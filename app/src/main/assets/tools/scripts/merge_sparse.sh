#!/system/bin/sh
# merge_sparse.sh — Merge sparse image segments
# Args: $1=project_dir, $2=partition_name

TOOLS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SIMG2IMG="$TOOLS_DIR/bin/simg2img"
PROJECT="$1"
PARTITION="$2"

[ -z "$PROJECT" ] && echo "ERROR: No project dir" && exit 1
[ ! -d "$PROJECT" ] && echo "ERROR: Project dir not found" && exit 1
[ -z "$PARTITION" ] && echo "ERROR: No partition name" && exit 1

# Find segments
SEGMENTS=""
INDEX=0
while true; do
    SEG="$PROJECT/${PARTITION}.img.${INDEX}"
    if [ -f "$SEG" ]; then
        SEGMENTS="$SEGMENTS $SEG"
        INDEX=$((INDEX + 1))
    else
        # Also try _0, _1 pattern
        SEG="$PROJECT/${PARTITION}_${INDEX}.img"
        if [ -f "$SEG" ]; then
            SEGMENTS="$SEGMENTS $SEG"
            INDEX=$((INDEX + 1))
        else
            break
        fi
    fi
done

if [ -z "$SEGMENTS" ]; then
    echo "ERROR: No sparse segments found for $PARTITION"
    exit 1
fi

echo "INFO: Found $INDEX segments for $PARTITION"
OUTPUT="$PROJECT/${PARTITION}.img"

if [ -x "$SIMG2IMG" ]; then
    echo "INFO: Merging with simg2img..."
    $SIMG2IMG $SEGMENTS "$OUTPUT"
    RET=$?
else
    echo "INFO: simg2img not found, merging with cat..."
    cat $SEGMENTS > "$OUTPUT"
    RET=$?
fi

if [ $RET -eq 0 ] && [ -f "$OUTPUT" ]; then
    SIZE=$(stat -c%s "$OUTPUT" 2>/dev/null || ls -l "$OUTPUT" | awk '{print $5}')
    echo "INFO: Merged image: $OUTPUT ($SIZE bytes)"
    # Optionally remove segments
    # for seg in $SEGMENTS; do rm -f "$seg"; done
else
    echo "ERROR: Merge failed"
    exit 1
fi

exit 0
