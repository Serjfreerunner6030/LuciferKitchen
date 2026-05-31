#!/system/bin/sh
# Lucifer Kitchen - Flash partition to device
TOOLS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
IMAGE="$1"
PARTITION="$2"
SLOT="${3:-auto}"
REBOOT="${4:-0}"

[ -z "$IMAGE" ] || [ -z "$PARTITION" ] && echo "ERROR: Usage: flash_partition.sh <image> <partition> [slot] [reboot]" && exit 1
[ ! -f "$IMAGE" ] && echo "ERROR: Image not found: $IMAGE" && exit 1

# Must be root
id | grep -q "uid=0" || { echo "ERROR: Root access required"; exit 1; }

echo "INFO: Flashing $PARTITION with $IMAGE"

# Detect slot
if [ "$SLOT" = "auto" ]; then
    CURRENT_SLOT=$(getprop ro.boot.slot_suffix 2>/dev/null)
    if [ -n "$CURRENT_SLOT" ]; then
        SLOT="$CURRENT_SLOT"
        echo "INFO: Auto-detected slot: $SLOT"
    else
        SLOT=""
        echo "INFO: Non-A/B device, no slot suffix"
    fi
elif [ "$SLOT" = "a" ]; then
    SLOT="_a"
elif [ "$SLOT" = "b" ]; then
    SLOT="_b"
fi

PART_NAME="${PARTITION}${SLOT}"

# Find partition block device
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

[ -z "$BLOCK_DEV" ] && echo "ERROR: Block device for $PART_NAME not found" && exit 1
echo "INFO: Block device: $BLOCK_DEV"

# Get sizes
IMG_SIZE=$(stat -c%s "$IMAGE" 2>/dev/null || wc -c < "$IMAGE")
echo "INFO: Image size: $IMG_SIZE bytes"

# Flash
echo "INFO: Writing to $BLOCK_DEV..."
dd if="$IMAGE" of="$BLOCK_DEV" bs=4096 2>&1
EXIT_CODE=$?

if [ $EXIT_CODE -eq 0 ]; then
    sync
    echo "INFO: Flash completed successfully"
    if [ "$REBOOT" = "1" ]; then
        echo "INFO: Rebooting in 3 seconds..."
        sleep 3
        reboot
    fi
else
    echo "ERROR: Flash failed (exit code: $EXIT_CODE)"
fi
exit $EXIT_CODE
