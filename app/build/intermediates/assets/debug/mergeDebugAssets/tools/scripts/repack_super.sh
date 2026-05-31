#!/system/bin/sh
# repack_super.sh - Repacks super.img using lpmake
# Args: $1=project_dir, $2=super_size_gb, $3=group_name, $4=partition_type(a|ab|vab)

TOOLS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BIN="$TOOLS_DIR/bin"

PROJECT_DIR="$1"
SUPER_SIZE_GB="${2:-9}"
GROUP_NAME="${3:-qti_dynamic_partitions}"
PARTITION_TYPE="${4:-vab}"

if [ -z "$PROJECT_DIR" ]; then
    echo "ERROR: project_dir argument required"
    exit 1
fi

if [ ! -d "$PROJECT_DIR" ]; then
    echo "ERROR: Project directory not found: $PROJECT_DIR"
    exit 1
fi

LPMAKE="$BIN/lpmake"
if [ ! -f "$LPMAKE" ]; then
    echo "ERROR: lpmake binary not found: $LPMAKE"
    exit 1
fi

SUPER_SIZE=$(( SUPER_SIZE_GB * 1024 * 1024 * 1024 ))
METADATA_SIZE=65536
METADATA_SLOTS=3

echo "INFO: Super size: ${SUPER_SIZE_GB}GB (${SUPER_SIZE} bytes)"
echo "INFO: Group name: $GROUP_NAME"
echo "INFO: Partition type: $PARTITION_TYPE"

OUTPUT_DIR="$PROJECT_DIR/output"
mkdir -p "$OUTPUT_DIR"
OUTPUT_SUPER="$OUTPUT_DIR/super.img"

# Scan for .img files in output dir and super_extracted
IMG_DIRS="$OUTPUT_DIR $PROJECT_DIR/super_extracted $PROJECT_DIR/extracted"

PARTITIONS=""
PARTITION_ARGS=""

for SEARCH_DIR in $IMG_DIRS; do
    if [ ! -d "$SEARCH_DIR" ]; then
        continue
    fi
    for IMG in "$SEARCH_DIR"/*.img; do
        [ -f "$IMG" ] || continue
        PNAME="$(basename "$IMG" .img)"
        # Skip super itself and temp files
        case "$PNAME" in
            super|super_raw|*_sparse) continue ;;
        esac
        IMG_SIZE=$(wc -c < "$IMG" 2>/dev/null)
        if [ -z "$IMG_SIZE" ] || [ "$IMG_SIZE" -eq 0 ]; then
            continue
        fi
        echo "INFO: Found partition: $PNAME (${IMG_SIZE} bytes)"
        PARTITIONS="$PARTITIONS $PNAME"
        PARTITION_ARGS="$PARTITION_ARGS --partition ${PNAME}:readonly:${IMG_SIZE}:${GROUP_NAME} --image ${PNAME}=$IMG"
    done
done

if [ -z "$PARTITIONS" ]; then
    echo "ERROR: No .img partitions found for repacking"
    exit 1
fi

echo "INFO: Partitions to pack:$PARTITIONS"

# Group size = sum of all partition sizes + small buffer
GROUP_SIZE=0
for SEARCH_DIR in $IMG_DIRS; do
    [ -d "$SEARCH_DIR" ] || continue
    for IMG in "$SEARCH_DIR"/*.img; do
        [ -f "$IMG" ] || continue
        PNAME="$(basename "$IMG" .img)"
        case "$PNAME" in
            super|super_raw|*_sparse) continue ;;
        esac
        SZ=$(wc -c < "$IMG" 2>/dev/null)
        [ -n "$SZ" ] && GROUP_SIZE=$(( GROUP_SIZE + SZ ))
    done
done
echo "INFO: Group size: $GROUP_SIZE bytes"

# Build lpmake command
CMD="$LPMAKE"
CMD="$CMD --metadata-size $METADATA_SIZE"
CMD="$CMD --super-name super"
CMD="$CMD --metadata-slots $METADATA_SLOTS"
CMD="$CMD --device super:$SUPER_SIZE"
CMD="$CMD --group ${GROUP_NAME}:${GROUP_SIZE}"

case "$PARTITION_TYPE" in
    vab|ab)
        CMD="$CMD --virtual-ab"
        ;;
esac

CMD="$CMD $PARTITION_ARGS"
CMD="$CMD --sparse"
CMD="$CMD --output $OUTPUT_SUPER"

echo "INFO: Running lpmake..."
eval $CMD
RET=$?

if [ $RET -ne 0 ]; then
    echo "ERROR: lpmake failed with exit code $RET"
    exit 1
fi

echo "INFO: Super image repacked: $OUTPUT_SUPER"
ls -lh "$OUTPUT_SUPER" 2>/dev/null
exit 0
