#!/system/bin/sh
# Lucifer Kitchen - Extract partition from device
TOOLS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PARTITION="$1"
OUTPUT_DIR="$2"

[ -z "$PARTITION" ] && echo "ERROR: Usage: extract_partition.sh <partition_name> [output_dir]" && exit 1

# Must be root
id | grep -q "uid=0" || { echo "ERROR: Root access required"; exit 1; }

[ -z "$OUTPUT_DIR" ] && OUTPUT_DIR="/sdcard/LuciferKitchen/extracted"
mkdir -p "$OUTPUT_DIR"

# Detect slot
SLOT_SUFFIX=$(getprop ro.boot.slot_suffix 2>/dev/null)
PART_NAME="${PARTITION}${SLOT_SUFFIX}"

# Find partition
BLOCK_DEV=""
for path in \
    "/dev/block/by-name/$PART_NAME" \
    "/dev/block/bootdevice/by-name/$PART_NAME" \
    "/dev/block/platform/*/by-name/$PART_NAME" \
    "/dev/block/platform/*/*/by-name/$PART_NAME"; do
    found=$(ls $path 2>/dev/null | head -1)
    if [ -n "$found" ]; then
        BLOCK_DEV="$found"
        break
    fi
done

# Try without slot suffix
if [ -z "$BLOCK_DEV" ]; then
    for path in \
        "/dev/block/by-name/$PARTITION" \
        "/dev/block/bootdevice/by-name/$PARTITION" \
        "/dev/block/platform/*/by-name/$PARTITION" \
        "/dev/block/platform/*/*/by-name/$PARTITION"; do
        found=$(ls $path 2>/dev/null | head -1)
        if [ -n "$found" ]; then
            BLOCK_DEV="$found"
            break
        fi
    done
fi

[ -z "$BLOCK_DEV" ] && echo "ERROR: Block device for $PARTITION not found" && exit 1

OUTPUT_FILE="$OUTPUT_DIR/${PARTITION}.img"
echo "INFO: Extracting $PARTITION from $BLOCK_DEV"
echo "INFO: Output: $OUTPUT_FILE"

# Get partition size
PART_SIZE=$(blockdev --getsize64 "$BLOCK_DEV" 2>/dev/null)
if [ -n "$PART_SIZE" ]; then
    SIZE_MB=$((PART_SIZE / 1024 / 1024))
    echo "INFO: Partition size: ${SIZE_MB}MB"
fi

dd if="$BLOCK_DEV" of="$OUTPUT_FILE" bs=4096 2>&1
EXIT_CODE=$?

if [ $EXIT_CODE -eq 0 ]; then
    ACTUAL_SIZE=$(ls -lh "$OUTPUT_FILE" | awk '{print $5}')
    echo "INFO: Extraction complete ($ACTUAL_SIZE)"
else
    echo "ERROR: Extraction failed"
fi
exit $EXIT_CODE
