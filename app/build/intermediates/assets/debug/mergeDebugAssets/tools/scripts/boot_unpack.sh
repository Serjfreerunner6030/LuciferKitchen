#!/system/bin/sh
# Lucifer Kitchen - Unpack boot/recovery/vendor_boot image
# Uses AIK (Android Image Kitchen) binaries
TOOLS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BIN="$TOOLS_DIR/bin"
BOOT_IMG="$1"
OUTPUT_DIR="${2:-}"

[ -z "$BOOT_IMG" ] && echo "ERROR: Usage: boot_unpack.sh <boot.img> [output_dir]" && exit 1
[ ! -f "$BOOT_IMG" ] && echo "ERROR: File not found: $BOOT_IMG" && exit 1

export PATH="$BIN:$PATH"
export LD_LIBRARY_PATH="$BIN/lib:$LD_LIBRARY_PATH"
bb="$BIN/busybox"
[ ! -f "$bb" ] && bb="busybox"

BOOT_IMG="$(readlink -f "$BOOT_IMG")"
BOOT_NAME="$($bb basename "$BOOT_IMG" .img)"
[ -z "$OUTPUT_DIR" ] && OUTPUT_DIR="$(dirname "$BOOT_IMG")/${BOOT_NAME}_unpacked"

echo "INFO: Unpacking boot image: $BOOT_IMG"
echo "INFO: Output: $OUTPUT_DIR"

# Save original image size for repack padding
ORIG_SIZE=$(wc -c < "$BOOT_IMG" 2>/dev/null)
echo "INFO: Original image size: $ORIG_SIZE bytes"

mkdir -p "$OUTPUT_DIR/split_img" "$OUTPUT_DIR/ramdisk"

# Save original size to file for boot_repack.sh
echo "$ORIG_SIZE" > "$OUTPUT_DIR/split_img/boot-origsize"

cd "$OUTPUT_DIR"

# Detect image type
imgtest="$($BIN/file -m $BIN/androidbootimg.magic "$BOOT_IMG" 2>/dev/null | $bb cut -d: -f2-)"
echo "INFO: Image type: $imgtest"

# Check if it's a valid boot image
imgtype="$(echo $imgtest | $bb awk '{ print $2 }' | $bb cut -d, -f1)"
if [ "$imgtype" != "bootimg" ]; then
    echo "INFO: Trying unboot for modern formats..."
    $BIN/unboot --boot_img "$BOOT_IMG" --out "$OUTPUT_DIR/split_img/config" --format mkbootimg > "$OUTPUT_DIR/split_img/conf.txt" 2>&1
    if [ $? -eq 0 ]; then
        echo "INFO: Successfully parsed with unboot"
    else
        echo "WARN: unboot failed, trying unpackbootimg..."
    fi
fi

# Use unpackbootimg for standard AOSP format
$BIN/unpackbootimg -i "$BOOT_IMG" -o "$OUTPUT_DIR/split_img" 2>&1
UNPACK_RESULT=$?

if [ $UNPACK_RESULT -ne 0 ]; then
    echo "WARN: unpackbootimg failed, trying magiskboot..."
    $BIN/magiskboot unpack -h "$BOOT_IMG" 2>&1
    if [ $? -eq 0 ]; then
        # magiskboot extracts to current dir
        [ -f kernel ] && mv kernel "$OUTPUT_DIR/split_img/"
        [ -f ramdisk.cpio ] && mv ramdisk.cpio "$OUTPUT_DIR/split_img/"
        [ -f dtb ] && mv dtb "$OUTPUT_DIR/split_img/"
        [ -f header ] && mv header "$OUTPUT_DIR/split_img/"
        echo "INFO: Unpacked with magiskboot"
        UNPACK_RESULT=0
    fi
fi

if [ $UNPACK_RESULT -ne 0 ]; then
    echo "ERROR: Failed to unpack boot image"
    exit 1
fi

# Detect and extract ramdisk
RAMDISK=$(find "$OUTPUT_DIR/split_img" -name "*ramdisk*" -type f | head -1)
if [ -n "$RAMDISK" ]; then
    echo "INFO: Extracting ramdisk..."
    
    # Detect compression
    COMP="$($BIN/file -m $BIN/magic "$RAMDISK" 2>/dev/null | $bb cut -d: -f2 | $bb awk '{ print $1 }')"
    echo "INFO: Ramdisk compression: $COMP"
    
    cd "$OUTPUT_DIR/ramdisk"
    case "$COMP" in
        gzip)    $bb gzip -dc "$RAMDISK" | cpio -i -d 2>/dev/null ;;
        lz4)     $BIN/lz4 -dc "$RAMDISK" | cpio -i -d 2>/dev/null ;;
        xz)      $BIN/xz -dc "$RAMDISK" | cpio -i -d 2>/dev/null ;;
        lzma)    $BIN/xz -dc "$RAMDISK" | cpio -i -d 2>/dev/null ;;
        bzip2)   $bb bzip2 -dc "$RAMDISK" | cpio -i -d 2>/dev/null ;;
        lzop)    $bb lzop -dc "$RAMDISK" | cpio -i -d 2>/dev/null ;;
        *)
            # Try bootpatch for unknown formats
            $BIN/bootpatch decompress "$RAMDISK" "$RAMDISK.cpio" 2>/dev/null && \
            $BIN/bootpatch cpio "$RAMDISK.cpio" extract 2>/dev/null || \
            cpio -i -d < "$RAMDISK" 2>/dev/null
            ;;
    esac
    
    RCOUNT=$($bb find . -type f | wc -l)
    echo "INFO: Ramdisk extracted ($RCOUNT files)"
    cd "$OUTPUT_DIR"
fi

# Show info
echo ""
echo "=== Boot Image Info ==="
[ -f split_img/*-cmdline ] && echo "cmdline: $(cat split_img/*-cmdline)"
[ -f split_img/*-base ] && echo "base: $(cat split_img/*-base)"
[ -f split_img/*-pagesize ] && echo "pagesize: $(cat split_img/*-pagesize)"
[ -f split_img/*-header_version ] && echo "header_version: $(cat split_img/*-header_version)"
[ -f split_img/*-os_version ] && echo "os_version: $(cat split_img/*-os_version)"
[ -f split_img/*-kernel ] && echo "kernel: $(ls -lh split_img/*-kernel | awk '{print $5}')"
echo "ramdisk files: $RCOUNT"

echo ""
echo "INFO: Boot image unpacked successfully"
echo "INFO: Kernel: $OUTPUT_DIR/split_img/"
echo "INFO: Ramdisk: $OUTPUT_DIR/ramdisk/"
exit 0
