#!/system/bin/sh
# unpack_img.sh - Extracts ext4/erofs .img files
# Args: $1=project_dir, $2=image.img

TOOLS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BIN="$TOOLS_DIR/bin"

PROJECT_DIR="$1"
IMAGE_ARG="$2"

if [ -z "$PROJECT_DIR" ] || [ -z "$IMAGE_ARG" ]; then
    echo "ERROR: Usage: unpack_img.sh <project_dir> <image.img>"
    exit 1
fi

if [ ! -d "$PROJECT_DIR" ]; then
    echo "ERROR: Project directory not found: $PROJECT_DIR"
    exit 1
fi

# Resolve image path
if [ -f "$IMAGE_ARG" ]; then
    IMAGE_PATH="$IMAGE_ARG"
elif [ -f "$PROJECT_DIR/$IMAGE_ARG" ]; then
    IMAGE_PATH="$PROJECT_DIR/$IMAGE_ARG"
else
    IMAGE_PATH="$(find "$PROJECT_DIR" -maxdepth 4 -name "$IMAGE_ARG" 2>/dev/null | head -n 1)"
fi

if [ -z "$IMAGE_PATH" ] || [ ! -f "$IMAGE_PATH" ]; then
    echo "ERROR: Image file not found: $IMAGE_ARG"
    exit 1
fi

echo "INFO: Processing image: $IMAGE_PATH"

# Detect filesystem type by reading magic bytes
MAGIC="$(dd if="$IMAGE_PATH" bs=1 skip=1080 count=2 2>/dev/null | od -An -tx1 | tr -d ' \n')"
MAGIC2="$(dd if="$IMAGE_PATH" bs=1 skip=1024 count=4 2>/dev/null | od -An -tx1 | tr -d ' \n')"
EROFS_MAGIC="$(dd if="$IMAGE_PATH" bs=1 skip=0 count=4 2>/dev/null | od -An -tx1 | tr -d ' \n')"

FS_TYPE="unknown"

# ext4 magic: 0x53EF at offset 1080
if [ "$MAGIC" = "53ef" ] || [ "$MAGIC" = "ef53" ]; then
    FS_TYPE="ext4"
# erofs magic: E2E1F5E0 at offset 1024
elif [ "$MAGIC2" = "e2e1f5e0" ] || [ "$EROFS_MAGIC" = "e2e1f5e0" ]; then
    FS_TYPE="erofs"
else
    # Try file command
    FILE_OUT="$(file "$IMAGE_PATH" 2>/dev/null)"
    if echo "$FILE_OUT" | grep -qi "ext4\|ext2\|ext3"; then
        FS_TYPE="ext4"
    elif echo "$FILE_OUT" | grep -qi "erofs"; then
        FS_TYPE="erofs"
    else
        echo "WARN: Could not detect filesystem type, assuming ext4"
        FS_TYPE="ext4"
    fi
fi

echo "INFO: Detected filesystem type: $FS_TYPE"

BASENAME="$(basename "$IMAGE_PATH" .img)"
OUTPUT_DIR="$PROJECT_DIR/${BASENAME}_extracted"
mkdir -p "$OUTPUT_DIR"

case "$FS_TYPE" in
    ext4)
        EXTRACT_EXT4="$BIN/extract.ext4"
        if [ -f "$EXTRACT_EXT4" ]; then
            echo "INFO: Extracting ext4 with extract.ext4..."
            "$EXTRACT_EXT4" "$IMAGE_PATH" "$OUTPUT_DIR"
            RET=$?
        else
            # Fallback: use debugfs
            DEBUGFS="$BIN/debugfs"
            if [ -f "$DEBUGFS" ]; then
                echo "INFO: Extracting ext4 with debugfs..."
                "$DEBUGFS" -R "rdump / $OUTPUT_DIR" "$IMAGE_PATH"
                RET=$?
            else
                echo "ERROR: No ext4 extraction tool found (extract.ext4 or debugfs)"
                exit 1
            fi
        fi
        ;;
    erofs)
        FSCK_EROFS="$BIN/fsck.erofs"
        if [ ! -f "$FSCK_EROFS" ]; then
            echo "ERROR: fsck.erofs binary not found: $FSCK_EROFS"
            exit 1
        fi
        echo "INFO: Extracting erofs with fsck.erofs..."
        "$FSCK_EROFS" --extract="$OUTPUT_DIR" "$IMAGE_PATH"
        RET=$?
        ;;
    *)
        echo "ERROR: Unsupported filesystem type: $FS_TYPE"
        exit 1
        ;;
esac

if [ $RET -ne 0 ]; then
    echo "ERROR: Extraction failed with exit code $RET"
    exit 1
fi

echo "INFO: Extraction complete: $OUTPUT_DIR"
ls "$OUTPUT_DIR" 2>/dev/null | head -20
exit 0
