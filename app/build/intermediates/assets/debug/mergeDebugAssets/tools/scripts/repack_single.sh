#!/system/bin/sh
# repack_single.sh - Repacks a single partition image
# Args: $1=project_dir, $2=partition_name, $3=fs_type(ext4|erofs|f2fs),
#       $4=format(raw|sparse), $5=rw(0|1), $6=auto_size(0|1)

TOOLS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BIN="$TOOLS_DIR/bin"

PROJECT_DIR="$1"
PARTITION="$2"
FS_TYPE="${3:-ext4}"
FORMAT="${4:-sparse}"
RW="${5:-0}"
AUTO_SIZE="${6:-1}"

if [ -z "$PROJECT_DIR" ] || [ -z "$PARTITION" ]; then
    echo "ERROR: Usage: repack_single.sh <project_dir> <partition_name> [fs_type] [format] [rw] [auto_size]"
    exit 1
fi

if [ ! -d "$PROJECT_DIR" ]; then
    echo "ERROR: Project directory not found: $PROJECT_DIR"
    exit 1
fi

# Source directory (extracted partition contents)
SRC_DIR=""
for candidate in "$PROJECT_DIR/${PARTITION}" \
                 "$PROJECT_DIR/${PARTITION}_extracted" \
                 "$PROJECT_DIR/extracted/${PARTITION}" \
                 "$PROJECT_DIR/super_extracted/${PARTITION}"; do
    if [ -d "$candidate" ]; then
        SRC_DIR="$candidate"
        break
    fi
done

if [ -z "$SRC_DIR" ] || [ ! -d "$SRC_DIR" ]; then
    echo "ERROR: Source directory for partition '$PARTITION' not found in $PROJECT_DIR"
    exit 1
fi

echo "INFO: Source directory: $SRC_DIR"
echo "INFO: Filesystem type: $FS_TYPE"
echo "INFO: Output format: $FORMAT"

OUTPUT_DIR="$PROJECT_DIR/output"
mkdir -p "$OUTPUT_DIR"
OUTPUT_IMG="$OUTPUT_DIR/${PARTITION}.img"

# Calculate size
if [ "$AUTO_SIZE" = "1" ]; then
    SRC_SIZE=$(du -sk "$SRC_DIR" 2>/dev/null | awk '{print $1}')
    # Add 20% overhead and align to 4096
    IMG_SIZE_KB=$(( (SRC_SIZE * 120 / 100 + 4) & ~3 ))
    IMG_SIZE_BYTES=$(( IMG_SIZE_KB * 1024 ))
    echo "INFO: Auto-calculated image size: ${IMG_SIZE_KB}KB (${IMG_SIZE_BYTES} bytes)"
else
    # Default 2GB
    IMG_SIZE_BYTES=2147483648
    echo "INFO: Using default image size: ${IMG_SIZE_BYTES} bytes"
fi

CONTEXT_FILE="$PROJECT_DIR/configs/${PARTITION}_file_contexts"
if [ ! -f "$CONTEXT_FILE" ]; then
    CONTEXT_FILE="$(find "$PROJECT_DIR" -maxdepth 3 -name "${PARTITION}_file_contexts" 2>/dev/null | head -n 1)"
fi

case "$FS_TYPE" in
    ext4)
        MAKE_EXT4="$BIN/make_ext4fs"
        MKE2FS="$BIN/mke2fs"
        E2FSDROID="$BIN/e2fsdroid"

        if [ -f "$MAKE_EXT4" ]; then
            echo "INFO: Building ext4 image with make_ext4fs..."
            CMD="$MAKE_EXT4 -l $IMG_SIZE_BYTES"
            if [ -n "$CONTEXT_FILE" ] && [ -f "$CONTEXT_FILE" ]; then
                CMD="$CMD -S $CONTEXT_FILE"
            fi
            if [ "$RW" = "0" ]; then
                CMD="$CMD -a /$PARTITION"
            fi
            CMD="$CMD $OUTPUT_IMG $SRC_DIR"
            eval $CMD
            RET=$?
        elif [ -f "$MKE2FS" ] && [ -f "$E2FSDROID" ]; then
            echo "INFO: Building ext4 image with mke2fs + e2fsdroid..."
            "$MKE2FS" -t ext4 -b 4096 -L "$PARTITION" "$OUTPUT_IMG" $(( IMG_SIZE_BYTES / 4096 ))
            RET=$?
            if [ $RET -eq 0 ]; then
                E2_CMD="$E2FSDROID -e -T 0 -S $CONTEXT_FILE -f $SRC_DIR $OUTPUT_IMG"
                if [ ! -f "$CONTEXT_FILE" ]; then
                    E2_CMD="$E2FSDROID -e -T 0 -f $SRC_DIR $OUTPUT_IMG"
                fi
                eval $E2_CMD
                RET=$?
            fi
        else
            echo "ERROR: No ext4 build tool found (make_ext4fs or mke2fs+e2fsdroid)"
            exit 1
        fi
        ;;
    erofs)
        MKFS_EROFS="$BIN/mkfs.erofs"
        if [ ! -f "$MKFS_EROFS" ]; then
            echo "ERROR: mkfs.erofs not found: $MKFS_EROFS"
            exit 1
        fi
        echo "INFO: Building erofs image with mkfs.erofs..."
        CMD="$MKFS_EROFS -zlz4hc,9"
        if [ -n "$CONTEXT_FILE" ] && [ -f "$CONTEXT_FILE" ]; then
            CMD="$CMD --file-contexts=$CONTEXT_FILE"
        fi
        CMD="$CMD $OUTPUT_IMG $SRC_DIR"
        eval $CMD
        RET=$?
        ;;
    f2fs)
        MAKE_F2FS="$BIN/make_f2fs"
        SLOAD_F2FS="$BIN/sload_f2fs"
        if [ ! -f "$MAKE_F2FS" ] || [ ! -f "$SLOAD_F2FS" ]; then
            echo "ERROR: make_f2fs or sload_f2fs not found"
            exit 1
        fi
        echo "INFO: Building f2fs image with make_f2fs + sload_f2fs..."
        dd if=/dev/zero of="$OUTPUT_IMG" bs=1024 count=$(( IMG_SIZE_BYTES / 1024 )) 2>/dev/null
        "$MAKE_F2FS" -l "$PARTITION" "$OUTPUT_IMG"
        RET=$?
        if [ $RET -eq 0 ]; then
            "$SLOAD_F2FS" -t "/$PARTITION" -f "$SRC_DIR" "$OUTPUT_IMG"
            RET=$?
        fi
        ;;
    *)
        echo "ERROR: Unsupported filesystem type: $FS_TYPE"
        exit 1
        ;;
esac

if [ $RET -ne 0 ]; then
    echo "ERROR: Image creation failed with exit code $RET"
    exit 1
fi

echo "INFO: Raw image created: $OUTPUT_IMG"

# Convert to sparse if requested
if [ "$FORMAT" = "sparse" ] && [ "$FS_TYPE" = "ext4" ]; then
    IMG2SIMG="$BIN/img2simg"
    if [ -f "$IMG2SIMG" ]; then
        SPARSE_IMG="$OUTPUT_DIR/${PARTITION}_sparse.img"
        echo "INFO: Converting to sparse format..."
        "$IMG2SIMG" "$OUTPUT_IMG" "$SPARSE_IMG"
        if [ $? -eq 0 ]; then
            mv "$SPARSE_IMG" "$OUTPUT_IMG"
            echo "INFO: Converted to sparse: $OUTPUT_IMG"
        else
            echo "WARN: Sparse conversion failed, keeping raw image"
        fi
    fi
fi

echo "INFO: Repack complete: $OUTPUT_IMG"
ls -lh "$OUTPUT_IMG" 2>/dev/null
exit 0
